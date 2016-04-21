package com.naleid

import com.google.inject.Inject
import groovy.transform.CompileStatic
import ratpack.exec.Promise
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import rx.Observable

@CompileStatic
class ObservableHandler extends GroovyHandler {
    public static final List<Integer> WAIT_TIMES = [1000, 3000, 3500]

    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        context.byContent {
            json {
                makeSomeObservableCalls()
                    .single()
                    .subscribe { Integer sumOfWaiting ->
                        println "Thread: ${Thread.currentThread().name}"
                        println "waited for $sumOfWaiting"
                        context.render "waited for $sumOfWaiting"
                    }
            }
        }
    }

    Observable<Integer> makeSomeObservableCalls() {
        Observable.from(WAIT_TIMES)
                .map { createUri(it) }
                .flatMap { RxRatpack.observe(httpClient.get(it)) }
                .map { ReceivedResponse response -> response.body.text.toInteger() }
                .reduce { Integer acc, Integer val -> acc + val }
    }

    URI createUri(Integer waitTime) {
        URI.create(appProperties.otherAppUrl + "/" + waitTime )
    }

}
