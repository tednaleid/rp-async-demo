package com.naleid

import com.google.inject.Inject
import groovy.transform.CompileStatic
import ratpack.exec.Promise
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import ratpack.stream.Streams
import rx.Observable

@CompileStatic
class PromiseHandler extends GroovyHandler {
    public static final List<Integer> WAIT_TIMES = [1000, 1100, 1200]

    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        println "Handle Thread: ${Thread.currentThread().name}"
        context.byContent {
            json {
                makeSomePromiseCalls().then { Integer sumOfWaiting ->
                    println "Thread: ${Thread.currentThread().name}"
                    println "waited for $sumOfWaiting"
                    context.render "waited for $sumOfWaiting"
                }
            }
        }
    }

    Promise<Integer> makeSomePromiseCalls() {
        Streams.publish(WAIT_TIMES)
                .map { createUri(it) }
                .flatMap { httpClient.get(it) }
                .map { it.body.text.toInteger() }
                .toList()
                .map({ List<Integer> waitTimes -> waitTimes.sum() } ) as Promise<Integer>
    }

    URI createUri(Integer waitTime) {
        URI.create(appProperties.otherAppUrl + "/" + waitTime )
    }

}
