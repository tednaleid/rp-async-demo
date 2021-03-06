#! /usr/bin/env groovy
import ratpack.exec.Downstream
import ratpack.exec.ExecInterceptor
import ratpack.exec.Execution
import ratpack.exec.Promise
import ratpack.func.Block
import ratpack.handling.Context

import static ratpack.groovy.Groovy.ratpack

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('io.ratpack:ratpack-groovy:1.3.0')
@Grab('org.slf4j:slf4j-simple:1.7.12')


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
  }
  handlers {
    all { Context context ->
      final String executionId = context.get(ExecutionTimer).id.toString()

      println "${Thread.currentThread().name} - $executionId - A. Original compute thread"

      Promise.async({ Downstream downstream ->
        println "${Thread.currentThread().name} - $executionId - B1. Inside async promise, same thread still"

        // ask for an execution to be scheduled on another compute thread
        Execution.fork().start({ forkedExec ->
          println "${Thread.currentThread().name} - $executionId - C. Forked work on another thread"
          downstream.success("hello from fork")
        })

        println "${Thread.currentThread().name} - $executionId - B2. After fork().start()"


      }).then { result ->
        println "${Thread.currentThread().name} - $executionId - D. `then` notifies original compute thread"
        context.render result
      }
    }
  }
}