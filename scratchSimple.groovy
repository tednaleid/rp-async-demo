#! /usr/bin/env groovy
import ratpack.exec.Blocking
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.rx.RxRatpack
import ratpack.service.Service
import ratpack.service.StartEvent
import ratpack.test.embed.EmbeddedApp
import rx.Observable
import rx.functions.Func1
import rx.plugins.RxJavaPlugins

import static ratpack.groovy.Groovy.ratpack

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('io.ratpack:ratpack-groovy:1.3.3')
@Grab('io.ratpack:ratpack-rx:1.3.3')
@Grab('io.ratpack:ratpack-groovy-test:1.3.3')
@Grab('org.slf4j:slf4j-simple:1.7.12')

EmbeddedApp stubApp = GroovyEmbeddedApp.of {
  handlers {
    get(":sleepFor") {
      Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1
      Blocking.exec { ->
        println "Stub Sleep App GET Request, sleep for: $sleepFor ms"
        sleep(sleepFor)
        context.render sleepFor.toString()
      }
    }
  }
}
ratpack {
  serverConfig {
    threads 1
    development false
  }

  bindings {
    bindInstance Service, new Service() {
      @Override
      void onStart(StartEvent event) throws Exception {
        if (!RxJavaPlugins.getInstance().getObservableExecutionHook()) RxRatpack.initialize()
      }
    }
  }

  handlers {
    all { Context context ->
      Observable.from((1..20).collect { 200 })
          .map { Integer sleepMillis -> "http://${stubApp.address.host}:${stubApp.address.port}/${sleepMillis}".toURL() }
          .forkEach()   // put this and bindExec in if you want the requests to be made concurrently
          .flatMap({ URL url -> RxRatpack.observe( Blocking.get { url.text } ) } as Func1)
          .doOnNext({ println Thread.currentThread().name })
          .bindExec()
          .toList()
          .subscribe({ List<String> responses -> context.render responses.join(", ") })
    }
  }
}