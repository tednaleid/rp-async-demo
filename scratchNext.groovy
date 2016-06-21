#! /usr/bin/env groovy
import groovy.transform.CompileStatic
import ratpack.exec.*
import ratpack.func.Action
import ratpack.func.Block
import ratpack.func.Pair
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import ratpack.service.Service
import ratpack.service.StartEvent
import ratpack.test.embed.EmbeddedApp
import rx.Observable
import rx.functions.Func2
import rx.plugins.RxJavaPlugins

import javax.inject.Inject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import static ratpack.groovy.Groovy.ratpack

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('io.ratpack:ratpack-groovy:1.3.3')
@Grab('io.ratpack:ratpack-rx:1.3.3')
@Grab('io.ratpack:ratpack-groovy-test:1.3.3')
@Grab('org.slf4j:slf4j-simple:1.7.12')

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
    bindInstance(new SomeService("http://${stubApp.address.host}:${stubApp.address.port}/"))
    bindInstance Service, new Service() {
      @Override
      void onStart(StartEvent event) throws Exception {
        if (!RxJavaPlugins.getInstance().getObservableExecutionHook()) RxRatpack.initialize()
      }
    }
  }

  handlers {
    get("zip") { Context context ->
      SomeService someService = context.get(SomeService)

      someService.makeZipRequest()
                .subscribe({ String response ->
                    context.render response
                })
    }
    get("parallel") { Context context ->
      SomeService someService = context.get(SomeService)

      someService.makeParallelRequest()
                .subscribe({ String response ->
                    context.render response
                })
    }
    get("parallelSameType") { Context context ->
      SomeService someService = context.get(SomeService)

      someService.makeParallelSameTypeRequest()
                .subscribe({ String response ->
                    context.render response
                })
    }
  }
}

@CompileStatic
class SomeService implements Service {
  @Inject
  HttpClient httpClient

  String baseUri

  SomeService(String baseUri) {
    this.baseUri = baseUri
  }

  Observable<String> makeParallelRequest() {
    Observable<String> first = fourSecondRequest()
    Observable<Integer> second = threeSecondRequest() // different type than the first observable

    parallelPair(first, second).map { Pair<String, Integer> pair ->
      "took 4 seconds because it's done in parallel instead of ${pair.left} + ${pair.right}"
    }
  }

  Observable<String> makeParallelSameTypeRequest() {
    Observable<String> first = fourSecondRequest()
    Observable<String> second = fourSecondRequest()

    // because they are both Observables of the same type (String), you can do this:
    Observable.from([first, second]) // Observable of Observables
        .forkEach()
        .flatMap { it }  // unwrap the two levels of observables
        .bindExec()
        .reduce { String firstResult, String secondResult ->
          return "took 4 seconds because it's done in parallel, instead of $firstResult + $secondResult"
        }
  }

  Observable<String> makeZipRequest() {
    Observable.zip(fourSecondRequest(), threeSecondRequest(), { String firstResult, Integer secondResult ->
      return "took $firstResult + $secondResult seconds because they are executed in serial"
    } as Func2)
  }

  Observable<String> fourSecondRequest() {
    URI uri = new URI(baseUri + "4")
    httpClient.get(uri).map { ReceivedResponse response -> response.body.text }.observe()
  }

  Observable<Integer> threeSecondRequest() {
    URI uri = new URI(baseUri + "3")
    httpClient.get(uri).map { ReceivedResponse response -> response.body.text.toInteger() }.observe()
  }

  // this is gross in < ratpack 1.4, but will be much easier when this PR is merged: https://github.com/ratpack/ratpack/pull/1014
  public <U, V> Observable<Pair<U, V>> parallelPair(Observable<U> firstObservable, Observable<V> secondObservable) {
    AtomicInteger counter = new AtomicInteger(2)
    AtomicBoolean hasError = new AtomicBoolean(false)

    U firstValue
    V secondValue

    return Promise.async { Downstream<Pair<U, V>> downstream ->
      Action<Execution> checkCompletion = { Execution e ->
        if (counter.decrementAndGet() == 0 && !hasError.get()) {
          downstream.success(Pair.of(firstValue, secondValue) as Pair<U, V>)
        }
      } as Action

      Action<Throwable> handleError = { Throwable t ->
        if (hasError.compareAndSet(false, true)) {
          downstream.error(t)
        }
      } as Action

      Execution.fork()
               .onComplete(checkCompletion)
               .start { firstObservable.promiseSingle().onError(handleError).then { firstValue = it } }

      Execution.fork()
               .onComplete(checkCompletion)
               .start { secondObservable.promiseSingle().onError(handleError).then { secondValue = it } }
    }.observe()
  }
}
