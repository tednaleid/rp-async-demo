#! /usr/bin/env groovy
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.test.embed.EmbeddedApp
import rx.Subscriber
import rx.functions.Func1
import rx.Observable

import javax.inject.Inject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static ratpack.groovy.Groovy.ratpack

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('io.ratpack:ratpack-groovy:1.4.0-rc-2')
@Grab('io.ratpack:ratpack-rx:1.4.0-rc-2')
@Grab('io.ratpack:ratpack-groovy-test:1.4.0-rc-2')
@Grab('org.slf4j:slf4j-simple:1.7.12')

// try 1000 requests where each one fails 4 times and succeeds on the 5th (retries wait 20ms, 40ms, 80ms, 160ms)
// ab -n 1000 -c 10 http://localhost:5050/retryGet\?failUntil=5\&maxAttempts=5

// try 1000 requests where each one fails just once (retries wait 20ms)
// ab -n 1000 -c 10 http://localhost:5050/retryGet\?failUntil=2\&maxAttempts=2


// stub application that only returns a valid 200 response on the :failUntil attempt
// ex:
// http://localhost:<port>/<uuid>/1 will always return a 200
// http://localhost:<port>/<uuid>/2 will alternately 500 then 200
// http://localhost:<port>/<uuid>/10 will only return 200 on after 9 other 500 calls<uuid>/
Map<String, AtomicInteger> requestCountMap = [:].withDefault { key -> new AtomicInteger(1) }

EmbeddedApp stubApp = GroovyEmbeddedApp.of {
  handlers {
    get(":requestId/:failUntil") {
      Integer failUntil = context.pathTokens['failUntil'].toInteger() ?: 1
      String requestId = context.pathTokens['requestId']

      Integer currentAttempt = requestCountMap[requestId].andIncrement

      if (currentAttempt >= failUntil) {
        context.render "success on request attempt $currentAttempt as it is >= $failUntil required attempts for success"
      } else {
        context.clientError(500)
      }
    }
  }
}

//RxRatpack.initialize()   // the @Grab annotations force this to happen because of a groovy bug, no need here

ratpack {
  serverConfig {
    development false
  }
  bindings {
    bindInstance(new RetryHttpClient())
  }

  handlers {
    get("retryGet") { Context context ->
      RetryHttpClient retryHttpClient = context.get(RetryHttpClient)

      // add UUID in url so that we know we're retrying on an url that's unique to this retryGet request
      String baseUri = "http://${stubApp.address.host}:${stubApp.address.port}/${UUID.randomUUID().toString()}/"

      Integer failUntil = context.request.queryParams.failUntil?.toInteger() ?: 1
      Integer maxAttempts = context.request.queryParams.maxAttempts?.toInteger() ?: 1

      URI uriThatWillFailUntilNCalls = new URI(baseUri + failUntil.toString())
      retryHttpClient.get(uriThatWillFailUntilNCalls, maxAttempts)
                .map({ ReceivedResponse response -> response.body.text } as Func1)
                .onErrorReturn { it.message }
                .subscribe { String result ->
                    println Thread.currentThread().name
                    context.render result
                }
    }
  }
}

@Slf4j
@CompileStatic
class RetryHttpClient {
  @Inject
  HttpClient httpClient

  Observable<ReceivedResponse> get(URI uri, Integer maxAttempts) {
    retryHttpPromise(httpClient.get(uri), maxAttempts)
  }

  Observable<ReceivedResponse> retryHttpPromise(Promise<ReceivedResponse> httpPromise, Integer maxAttempts) {
    Observable<ReceivedResponse> responseObservable = Observable.create({ Subscriber<ReceivedResponse> subscriber ->
      connectHttpPromiseToSubscriber(httpPromise, subscriber)
    } as Observable.OnSubscribe<ReceivedResponse>)

    if (maxAttempts <= 1) {
      return responseObservable
    }

//    return responseObservable.retry(maxAttempts - 1)
    return responseObservable.retryWhen(this.&retryWithDecay.rcurry(maxAttempts) as Func1)
  }

  private static void connectHttpPromiseToSubscriber(Promise<ReceivedResponse> httpPromise, Subscriber<ReceivedResponse> subscriber) {
    httpPromise
            .onError { Throwable t -> subscriber.onError(t) }
            .then { ReceivedResponse response ->
                if (response.statusCode in 500..599) {
                    subscriber.onError(new RetryableHttpException(response))
                } else {
                    subscriber.onNext(response)
                    subscriber.onCompleted()
                }
            }
  }

  Observable<Integer> retryWithDecay(Observable<Throwable> attemptErrors, Integer maxAttempts) {
    Integer attempts = 0

    return attemptErrors.flatMap({ Throwable error ->
      attempts += 1

      if (!(error instanceof RetryableHttpException)) {
        return Observable.error(error)
      }

      ReceivedResponse response = (error as RetryableHttpException).response

      if (attempts >= maxAttempts) {
        log.warn("stoppedAtAttempt: $attempts")
        return Observable.error(new ExhaustedHttpRetriesException(maxAttempts, response))
      }

      Long pauseMillis = (2 ** attempts) * 10 as Long
      log.warn("attempts: $attempts, pauseMillis: ${pauseMillis}, lastStatusCode: ${response.statusCode}")
      Observable.timer(pauseMillis, TimeUnit.MILLISECONDS)
                .bindExec()
    } as Func1)
  }
}

@CompileStatic
class RetryableHttpException extends Throwable {
  ReceivedResponse response
  RetryableHttpException(ReceivedResponse response) {
    this.response = response
  }

  @Override
  synchronized Throwable fillInStackTrace() {
    this
  }
}

@CompileStatic
class ExhaustedHttpRetriesException extends Exception {
  ReceivedResponse response
  Integer maxAttempts
  ExhaustedHttpRetriesException(Integer maxAttempts, ReceivedResponse response) {
    this.maxAttempts = maxAttempts
    this.response = response
  }

  @Override
  String getMessage() {
    return "Exhausted ${maxAttempts} allowed retries, last status code: ${response.statusCode}"
  }
}