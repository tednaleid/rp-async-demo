#! /usr/bin/env groovy
import ratpack.exec.Blocking
import ratpack.exec.ExecInterceptor
import ratpack.exec.Execution
import ratpack.func.Block
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.http.client.HttpClient
import ratpack.rx.RxRatpack
import ratpack.service.Service
import ratpack.service.StartEvent
import ratpack.test.embed.EmbeddedApp
import rx.Observable
import rx.plugins.RxJavaPlugins

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
    bindInstance Service, new Service() {
      @Override
      void onStart(StartEvent event) throws Exception {
        if (!RxJavaPlugins.getInstance().getObservableExecutionHook()) RxRatpack.initialize()
      }
    }
  }
  handlers {
    get(":sleepFor") { Context context ->
      HttpClient httpClient = context.get(HttpClient)
      Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1

      URI uri = URI.create("http://${stubApp.address.host}:${stubApp.address.port}/${sleepFor}")
      httpClient.get(uri)
                .observe()
                .map { it.body.text } // get the body text for each http result
                .subscribe({ String response ->
                    context.render response
                })
    }
  }
}