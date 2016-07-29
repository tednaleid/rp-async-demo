#! /usr/bin/env groovy
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
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

// stub application that only returns a valid 200 response after :failFirst failed attempts
// ex:
// http://localhost:<port>/0 will always return a 200
// http://localhost:<port>/1 will alternately 500 then 200
// http://localhost:<port>/9 will only return 200 on after 9 other 500 calls
AtomicInteger requestCounts = new AtomicInteger(0)

EmbeddedApp stubApp = GroovyEmbeddedApp.of {
  handlers {
    get(":failFirst") {
      Integer failFirst = context.pathTokens['failFirst'].toInteger() ?: 0

      Integer currentAttempt = requestCounts.andIncrement

      if (currentAttempt >= failFirst) {
        context.render "success on request attempt $currentAttempt as its >= $failFirst attempts that failed first"
      } else {
        context.clientError(500)
      }
    }
  }
}

//RxRatpack.initialize()   // the @Grab annotations force this to happen because of a groovy bug, no need here

ratpack {
  bindings {
    bindInstance(new RetryHttpClient())
  }

  handlers {
    get("retryGet") { Context context ->
      RetryHttpClient retryHttpClient = context.get(RetryHttpClient)

      String baseUri = "http://${stubApp.address.host}:${stubApp.address.port}/"

      Integer failFirst = context.request.queryParams.failFirst?.toInteger() ?: 0
      Integer maxRetries = context.request.queryParams.maxRetries?.toInteger() ?: 0

      requestCounts.set(0)

      URI uriThatFailsNTimesBeforeSucceeding = new URI(baseUri + failFirst.toString())
      retryHttpClient.get(uriThatFailsNTimesBeforeSucceeding, maxRetries)
                .subscribe { ReceivedResponse response ->
                    println Thread.currentThread().name
                    String text = response.body.text
                    context.render text
                }
    }
  }
}

@Slf4j
@CompileStatic
class RetryHttpClient {
  @Inject
  HttpClient httpClient

  Observable<ReceivedResponse> get(URI uri, Integer maxRetries) {
    retryHttpPromise(httpClient.get(uri), maxRetries)
  }

  Observable<ReceivedResponse> retryHttpPromise(Promise<ReceivedResponse> httpPromise, Integer maxRetries) {
    Observable<ReceivedResponse> responseObservable = Observable.create({ Subscriber<ReceivedResponse> subscriber ->
      connectHttpPromiseToSubscriber(httpPromise, subscriber)
    } as Observable.OnSubscribe<ReceivedResponse>)

    if (maxRetries <= 0) {
      return responseObservable
    }

    return responseObservable.retryWhen(this.&retryWithDecay.rcurry(maxRetries) as Func1)
  }

  private static void connectHttpPromiseToSubscriber(Promise<ReceivedResponse> httpPromise, Subscriber<ReceivedResponse> subscriber) {
    httpPromise
            .onError { Throwable t -> subscriber.onError(t) }
            .then { ReceivedResponse response ->
                if (response.statusCode in 500..599) {
                    subscriber.onError(new RetryableHttpException("${response.statusCode}"))
                } else {
                    subscriber.onNext(response)
                    subscriber.onCompleted()
                }
            }
  }

  Observable<Integer> retryWithDecay(Observable<Throwable> attemptErrors, Integer maxRetries) {
    Integer retryAttempts = 0

    return attemptErrors.flatMap({ Throwable error ->

      retryAttempts += 1

      if (retryAttempts > maxRetries || !(error instanceof RetryableHttpException)) {
        log.warn("stoppedAtRetry: $retryAttempts", error)
        return Observable.error(error)
      }

      Long pauseMillis = (2 ** retryAttempts) * 10 as Long
      log.warn("retryAttemptNumber: $retryAttempts, pauseMillis: ${pauseMillis}, lastError: ${error.message}")
      Observable.timer(pauseMillis, TimeUnit.MILLISECONDS)
                .bindExec()

    } as Func1)
  }
}

@CompileStatic
class RetryableHttpException extends Exception {
  RetryableHttpException(String message) {
    super(message)
  }

  @Override
  synchronized Throwable fillInStackTrace() {
    return this
  }
}