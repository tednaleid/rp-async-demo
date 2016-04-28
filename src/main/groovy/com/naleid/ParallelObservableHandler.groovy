package com.naleid

import com.google.inject.Inject
import groovy.transform.CompileStatic
import ratpack.func.Action
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import rx.Observable
import rx.functions.Action1
import rx.functions.Func1

@CompileStatic
class ParallelObservableHandler extends GroovyHandler {

    // list of milliseconds that each request will ask the other service to sleep for before returning
    public static final List<Integer> WAIT_TIMES = (1..10).collect { 1000 }

    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        println "Handle Thread: ${Thread.currentThread().name}"
        Long timeMillis = System.currentTimeMillis()
        makeSomeObservableCallsInParallel()
            .single()
            .subscribe({ Integer sumOfWaiting ->
                Long totalTime = System.currentTimeMillis() - timeMillis
                println "Thread: ${Thread.currentThread().name}: real time: ${totalTime}ms"
                println "Thread: ${Thread.currentThread().name}: serial time would have been: ${sumOfWaiting}ms"
                context.render "Total time: ${totalTime/1000}s, if run serially would have been: ${sumOfWaiting/1000}s"
            } as Action1)
    }

    Observable<Integer> makeSomeObservableCallsInParallel() {
        Observable.from(WAIT_TIMES)
                .forkEach()
                .flatMap(this.&waitTimeRequest as Func1)
                .doOnNext { println "Forked Thread: ${Thread.currentThread().name}: adding $it" }
                .bindExec()
                .doOnNext { println "Joined Thread: ${Thread.currentThread().name}: adding $it" }
                .reduce(0) { Integer acc, Integer val -> acc + val }
    }

    Observable<Integer> waitTimeRequest(Integer waitTime) {
        URI uri = createUri(waitTime)
        println "Thread: ${Thread.currentThread().name}: wait time request: $waitTime"

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
