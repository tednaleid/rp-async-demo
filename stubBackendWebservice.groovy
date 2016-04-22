#! /usr/bin/env groovy
import ratpack.exec.Blocking

@Grapes([
        @Grab('io.ratpack:ratpack-groovy:1.2.0'),
        @Grab('org.slf4j:slf4j-simple:1.7.12')
])
import static ratpack.groovy.Groovy.ratpack

ratpack {
    serverConfig {
        port 5150
    }
    handlers {
        get {
            render "Go to http://localhost:5150/2000 to sleep for 2 seconds"
        }
        get(":sleepFor") {
            Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1000
            Blocking.exec { ->
                sleep(sleepFor)
                context.render sleepFor.toString()
            }
        }
    }
}