#! /usr/bin/env groovy
import java.util.concurrent.atomic.AtomicInteger

@GrabResolver(name = 'jcenter', root = 'http://jcenter.bintray.com/')
@GrabExclude('org.codehaus.groovy:groovy-all')
@Grab('io.ratpack:ratpack-groovy:1.4.0-rc-2')
@Grab('org.slf4j:slf4j-simple:1.7.12')
import static ratpack.groovy.Groovy.ratpack

// stub application that only returns a valid 200 response after :failFirst failed attempts
// ex:
// http://localhost:<port>/0 will always return a 200
// http://localhost:<port>/1 will alternately 500 then 200
// http://localhost:<port>/9 will only return 200 on after 9 other 500 calls

AtomicInteger requestCounts = new AtomicInteger(1)

ratpack {
    serverConfig {
        port 5150
        threads 8
    }
    handlers {
        get("reset") {
            requestCounts.set(1)
            context.render "reset request count"
        }
        get(":failFirst") {
            Integer failFirst = context.pathTokens['failFirst'].toInteger() ?: 0

            Integer currentAttempt = requestCounts.andIncrement

            if (currentAttempt > failFirst) {
                requestCounts.set(1)
                context.render "success on request attempt $currentAttempt as its >= $failFirst attempts that failed first"
            } else {
                context.clientError(500)
            }
        }
    }
}