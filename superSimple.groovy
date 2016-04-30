#! /usr/bin/env groovy
import ratpack.exec.Blocking
import ratpack.exec.Execution
import ratpack.exec.ExecInterceptor
import ratpack.guice.Guice
import ratpack.func.Block
import ratpack.handling.Context
import ratpack.groovy.test.embed.GroovyEmbeddedApp

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('org.slf4j:slf4j-simple:1.7.12')

@Grab('io.ratpack:ratpack-groovy:1.3.3')
@Grab('io.ratpack:ratpack-rx:1.3.3')
@Grab('io.ratpack:ratpack-groovy-test:1.3.3')

public class LoggingExecInterceptor implements ExecInterceptor {
  @Override
  void intercept(Execution execution, ExecInterceptor.ExecType execType, Block executionSegment) throws Exception {
    Long start = System.currentTimeMillis()
    executionSegment.execute()
    println "${execType} on ${Thread.currentThread().name} took: ${System.currentTimeMillis() - start}ms\n"
  }
}

GroovyEmbeddedApp app = GroovyEmbeddedApp.of {
  registry Guice.registry {
    it.bindInstance(new LoggingExecInterceptor())
  }
  handlers {
    all { Context context ->

      println "A. Original compute thread: ${Thread.currentThread().name}"

      Blocking.exec { ->
        context.render "hello from blocking" // pretend blocking work
        println "B. Blocking thread : ${Thread.currentThread().name}"
      }

      println "C. Original compute thread: ${Thread.currentThread().name}"
    }
  }
}.test {
    assert getText() == "hello from handler"
}
