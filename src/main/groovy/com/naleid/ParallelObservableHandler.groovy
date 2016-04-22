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

@CompileStatic
class ParallelObservableHandler extends GroovyHandler {
    // list of milliseconds that each request will ask the other service to sleep for before returning
    public static final List<Integer> WAIT_TIMES = (1..200).collect { 100 }.asList()

    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        context.byContent {
            json {
                Long timeMillis = System.currentTimeMillis()
                makeSomeObservableCallsInParallel()
                    .promiseSingle().then({ Integer sumOfWaiting ->
                        println "Thread: ${Thread.currentThread().name}: real time: ${System.currentTimeMillis() - timeMillis}ms"
                        println "Thread: ${Thread.currentThread().name}: serial time would have been: ${sumOfWaiting}ms"
                        context.render "waited for $sumOfWaiting"
                    } as Action<Integer>)
            }
        }
    }

    Observable<Integer> makeSomeObservableCallsInParallel() {
        Observable.from(WAIT_TIMES)
                .forkEach()
                .flatMap(this.&waitTimeRequest)
                .doOnNext { println "Thread: ${Thread.currentThread().name}: adding $it" }
                .reduce(0) { Integer acc, Integer val -> acc + val }
    }

    Observable<Integer> waitTimeRequest(Integer waitTime) {
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
