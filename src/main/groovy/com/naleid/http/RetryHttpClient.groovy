package com.naleid.http

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import ratpack.exec.Promise
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import rx.Observable
import rx.Subscriber
import rx.functions.Func1

import javax.inject.Inject
import java.util.concurrent.TimeUnit

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

      Long pauseMillis = (2 ** retryAttempts) * 5 as Long
      log.warn("retryAttemptNumber: $retryAttempts, pauseMillis: ${pauseMillis}, lastError: ${error.message}")
      Observable.timer(pauseMillis, TimeUnit.MILLISECONDS)

    } as Func1)
  }
}
