package com.naleid

import com.google.inject.Inject
import groovy.transform.CompileStatic
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import ratpack.util.Exceptions
import rx.Observable
import rx.functions.Func1

import java.util.concurrent.CyclicBarrier

@CompileStatic
class ParallelObservableHandler extends GroovyHandler {
    public static final List<Integer> WAIT_TIMES = (1..200).asList()

    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        context.byContent {
            json {
                makeSomeObservableCallsInParallel()
                    .promiseSingle().then { Integer sumOfWaiting ->
                        println "Thread: ${Thread.currentThread().name}"
                        println "waited for $sumOfWaiting"
                        context.render "waited for $sumOfWaiting"
                    }
            }
        }
    }

    Observable<Integer> makeSomeObservableCallsInParallel() {
        Observable.from(WAIT_TIMES.collect { waitResponseValue(it) })
                .compose(RxRatpack.&forkEach as Observable.Transformer)
                .flatMap { it }
                .reduce { Integer acc, Integer val ->
                    println "Thread: ${Thread.currentThread().name}: adding $val"
                    acc + val
                }
    }

    Observable<Integer> waitResponseValue(Integer waitTime) {
        URI uri = createUri(waitTime)

        return RxRatpack.observe(httpClient.get(uri)).map { ReceivedResponse response ->
            Integer val = response.body.text.toInteger()
            println "Thread: ${Thread.currentThread().name}: got response body: $val"
            val
        }
    }

    URI createUri(Integer waitTime) {
        URI.create(appProperties.otherAppUrl + "/" + waitTime )
    }

}
