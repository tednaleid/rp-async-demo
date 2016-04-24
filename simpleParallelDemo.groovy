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

// works fine with RP 1.2
@Grab('io.ratpack:ratpack-groovy:1.2.0')
@Grab('io.ratpack:ratpack-rx:1.2.0')
@Grab('io.ratpack:ratpack-groovy-test:1.2.0')

// fails with RP 1.3 with "java.io.IOException: Connection reset by peer"
//@Grab('io.ratpack:ratpack-groovy:1.3.0')
//@Grab('io.ratpack:ratpack-rx:1.3.0')
//@Grab('io.ratpack:ratpack-groovy-test:1.3.0')

// stub application that sleeps for the number of milliseconds in the path you give it
// ex: http://localhost:<port>/2000  sleeps for 2 seconds before returning
EmbeddedApp stubApp = GroovyEmbeddedApp.of {
    handlers {
        get(":sleepFor") {
            Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1000
            Blocking.exec { ->
                sleep(sleepFor)
                context.render sleepFor.toString()
            }
        }
    }
}

final List<Integer> SLEEP_TIMES = (1..2000).collect { 100 }
final Long startTime = System.currentTimeMillis()

GroovyEmbeddedApp.of {
    handlers {
        all { Context context ->
            HttpClient httpClient = context.get(HttpClient)

            Observable.from(SLEEP_TIMES)
                    .forkEach()  // fork execution across ratpack-compute threads
                    .flatMap {   // async get request to stub app that sleeps for `it` milliseconds before returning
                        URI uri = URI.create("http://${stubApp.address.host}:${stubApp.address.port}/${it}")
                        RxRatpack.observe(httpClient.get(uri)).map { it.body.text.toInteger() }
                    }
                    .bindExec()  // join forked executions back to original ratpack-compute thread
                    .reduce(0) { Integer acc, Integer val -> acc + val }  // sum up all sleep times
                    .single()
                    .subscribe({ Integer cumulativeSleepTime ->
                        Long totalTime = System.currentTimeMillis() - startTime
                        context.render "Total time: ${totalTime/1000}s, if run serially would have been: ${cumulativeSleepTime/1000}s"
                    })
        }
    }
}.test {
    String bodyText = getText()
    assert response.status.code == 200
    assert System.currentTimeMillis() - startTime <= SLEEP_TIMES.sum()
    println bodyText
    System.exit(0)
}


