#! /usr/bin/env groovy
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.ListenableFuture
import com.ning.http.client.Response
import com.ning.http.client.extra.ListenableFutureAdapter
import ratpack.exec.Blocking
import ratpack.exec.Downstream
import ratpack.exec.ExecInterceptor
import ratpack.exec.Execution
import ratpack.exec.Promise
import ratpack.func.Block
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.http.client.HttpClient
import ratpack.rx.RxRatpack
import ratpack.service.Service
import ratpack.service.StartEvent
import ratpack.test.embed.EmbeddedApp
import rx.Observable
import rx.functions.Func2
import rx.plugins.RxJavaPlugins
import rx.schedulers.Schedulers

import static ratpack.groovy.Groovy.ratpack

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('io.ratpack:ratpack-groovy:1.3.3')
@Grab('io.ratpack:ratpack-rx:1.3.3')
@Grab('io.ratpack:ratpack-groovy-test:1.3.3')
@Grab('org.slf4j:slf4j-simple:1.7.12')
//@Grab('org.asynchttpclient:async-http-client:2.0.6')
//@Grab('org.asynchttpclient:async-http-client-extras-guava:2.0.6')
@Grab('com.ning:async-http-client:1.9.36')

// stub application that sleeps for the number of seconds in the path you give it
// ex: http://localhost:<port>/2  sleeps for 2 seconds before returning the value "2"
EmbeddedApp stubApp = GroovyEmbeddedApp.of {
    handlers {
        get(":sleepFor") {
            Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1
            Blocking.exec { ->
                println "Stub Sleep App GET Request, sleep for: $sleepFor seconds"
                sleep(sleepFor * 1000)
                context.render sleepFor.toString()
            }
        }
    }
}

public class ExecutionTimer {
    private ExecutionTimer() {}
    final UUID id = UUID.randomUUID()
    final Long executionStart = System.currentTimeMillis()
    Long segmentStart

    ExecutionTimer startSegment() {
        segmentStart = System.currentTimeMillis()
        this
    }

    Long getExecutionTime() { System.currentTimeMillis() - executionStart }

    Long getSegmentTime() { System.currentTimeMillis() - segmentStart }

    @Override
    String toString() {
        "$id - segment time: ${segmentTime} execution time: ${executionTime}ms"
    }

    public static ExecutionTimer startExecutionSegment(Execution execution) {
        ExecutionTimer timer = execution.maybeGet(ExecutionTimer).orElse(null)
        if (!timer) {
            timer = new ExecutionTimer()
            execution.add(ExecutionTimer, timer)
        }
        timer.startSegment()
    }
}

public class LoggingExecInterceptor implements ExecInterceptor {
    @Override
    void intercept(Execution execution, ExecInterceptor.ExecType execType, Block executionSegment) throws Exception {
        ExecutionTimer timer = ExecutionTimer.startExecutionSegment(execution)
        try {
            executionSegment.execute()
        } finally {
            println "${Thread.currentThread().name} - $timer - ${execType}"
        }
    }
}

ratpack {
    serverConfig {
        development false
    }

    bindings {
        bindInstance(new LoggingExecInterceptor())
        bindInstance Service, new Service() {
            @Override
            void onStart(StartEvent event) throws Exception {
                if (!RxJavaPlugins.getInstance().getObservableExecutionHook()) RxRatpack.initialize()
            }
        }
    }

    handlers {
        all { Context context ->
            HttpClient httpClient = context.get(HttpClient)
            final String executionId = context.get(ExecutionTimer).id.toString()

            Closure sleepForUrl = { Integer sleepFor ->
//        URI.create("http://${stubApp.address.host}:${stubApp.address.port}/${sleepFor}")
                "http://${stubApp.address.host}:${stubApp.address.port}/${sleepFor}"
            }


            println "${Thread.currentThread().name} - $executionId - A. Original compute thread"

            AsyncHttpClient asyncHttpClient = new AsyncHttpClient()

            // this was Promise.of in RP 1.2
            Observable<String> first = Promise
                .async({ Downstream downstream ->
                    ListenableFuture<Response> future = asyncHttpClient.prepareGet(sleepForUrl(3)).execute()
                    downstream.accept(ListenableFutureAdapter.asGuavaFuture(future))
                })
                .observe().map { Response response -> "first: " + response.responseBody }

            Observable<String> second = Promise
                .async({ Downstream downstream ->
                    ListenableFuture<Response> future = asyncHttpClient.prepareGet(sleepForUrl(2)).execute()
                    downstream.accept(ListenableFutureAdapter.asGuavaFuture(future))
                })
               .observe().map { Response response -> "second: " + response.responseBody }


            Observable.zip(first, second, { String firstVal, String secondVal -> firstVal + " " + secondVal } as Func2)
               .subscribe({ String response ->
                   println "${Thread.currentThread().name} - $executionId - C. Subscribe final result"
                   context.render response
               })

//            Observable.zip( first, second, { String firstResult, String secondResult -> firstResult + secondResult } as Func2)
//                .subscribeOn(Schedulers.io())
//                .bindExec()
//                .subscribe({ String response ->
//                    println "${Thread.currentThread().name} - $executionId - C. Subscribe final result"
//                    context.render response
//                })
        }
    }
}