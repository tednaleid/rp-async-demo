#! /usr/bin/env groovy
import ratpack.exec.Blocking
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.http.client.HttpClient
import ratpack.rx.RxRatpack
import ratpack.test.embed.EmbeddedApp
import rx.Observable

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('org.slf4j:slf4j-simple:1.7.12')

@Grab('io.ratpack:ratpack-groovy:1.3.3')
@Grab('io.ratpack:ratpack-rx:1.3.3')
@Grab('io.ratpack:ratpack-groovy-test:1.3.3')

// stub application that sleeps for the number of seconds in the path you give it
// ex: http://localhost:<port>/2  sleeps for 2 seconds before returning the value "2"
EmbeddedApp stubApp = GroovyEmbeddedApp.of {
  handlers {
    get(":sleepFor") {
      Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1
      Blocking.exec { ->
        sleep(sleepFor * 1000)
        context.render sleepFor.toString()
      }
    }
  }
}

// create a List of URIs to the stub app above that will ask it to sleep
// for N seconds before returning the number of seconds it was asked to sleep
final List<URI> REQUEST_SLEEP_URIS = [3, 2, 1].collect {
  URI.create("http://${stubApp.address.host}:${stubApp.address.port}/${it}")
}
final Long startTime = System.currentTimeMillis()

GroovyEmbeddedApp.of {
  handlers {
    all { Context context ->
      HttpClient httpClient = context.get(HttpClient)

      // default serial async version
      Observable.from(REQUEST_SLEEP_URIS)
              .doOnNext { println "Async GET (${it}) thread: ${Thread.currentThread().name}" }
              .flatMap { uri -> RxRatpack.observe(httpClient.get(uri)) }
              .map { result -> result.body.text.toInteger() }
              .doOnNext { println "Reduce result (${it}) thread: ${Thread.currentThread().name}" }
              .reduce(0) { acc, val -> acc + val }  // sum up all sleep times
              .single()
              .subscribe({ Integer sum ->
                println "Subscribe thread: ${Thread.currentThread().name}"
                context.render sum.toString() // render out cumulative sleep time
              })

      // parallel async version using forkEach/bindExec
//      Observable.from(REQUEST_SLEEP_URIS)
//          .forkEach()  // fork execution across ratpack-compute threads
//          .doOnNext { println "Async GET (${it}) thread: ${Thread.currentThread().name}" }
//          .flatMap { uri -> RxRatpack.observe(httpClient.get(uri)) }
//          .map { result -> result.body.text.toInteger() }
//          .bindExec()  // join forked executions back to original ratpack-compute thread
//          .doOnNext { println "Reduce result (${it}) thread: ${Thread.currentThread().name}" }
//          .reduce(0) { acc, val -> acc + val }  // sum up all sleep times
//          .single()
//          .subscribe({ Integer sum ->
//            println "Subscribe thread: ${Thread.currentThread().name}"
//            context.render sum.toString() // render out cumulative sleep time
//          })
    }
  }
}.test {
  Integer cumulativeSleepTime = getText().toInteger()
  assert response.status.code == 200

  final BigDecimal totalTime = (System.currentTimeMillis() - startTime)/1000
  println "Total time: ${totalTime}s, if run serially would have been >= ${cumulativeSleepTime}s"
  System.exit(0)
}


