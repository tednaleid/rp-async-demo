package com.naleid

import com.google.inject.Inject
import groovy.transform.CompileStatic
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import rx.Observable
import rx.functions.Func1

@CompileStatic
class ObservableHandler extends GroovyHandler {
    public static final List<Integer> WAIT_TIMES = [1000, 1100, 1200]

    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        println "Handle Thread: ${Thread.currentThread().name}"
        makeSomeObservableCalls()
            .single()
            .subscribe { Integer sumOfWaiting ->
                println "Thread: ${Thread.currentThread().name}"
                println "waited for $sumOfWaiting"
                context.render "waited for $sumOfWaiting"
            }
    }

    Observable<Integer> makeSomeObservableCalls() {
        Observable.from(WAIT_TIMES)
                .map { Integer waitTime -> createUri(waitTime) }
                .flatMap({ URI uri ->
                     println "Thread: ${Thread.currentThread().name}: getting uri: $uri"
                     RxRatpack.observe(httpClient.get(uri))
                } as Func1)
                .map { ReceivedResponse response ->
                    Integer val = response.body.text.toInteger()
                    println "Thread: ${Thread.currentThread().name}: got response body: $val"
                    val
                }
                .reduce { Integer acc, Integer val ->
                    println "Thread: ${Thread.currentThread().name}: adding $val"
                    acc + val
                }
    }

    URI createUri(Integer waitTime) {
        URI.create(appProperties.otherAppUrl + "/" + waitTime )
    }

}
