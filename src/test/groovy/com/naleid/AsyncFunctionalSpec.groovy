package com.naleid

import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.registry.Registry
import ratpack.test.embed.EmbeddedApp
import ratpack.test.http.TestHttpClient
import spock.lang.Shared
import spock.lang.Specification

class AsyncFunctionalSpec extends Specification {

    @Delegate
    TestHttpClient client

    @Shared
    ApiUnderTest aut

    @Shared
    Long startTime

    @Shared
    EmbeddedApp stubApp = GroovyEmbeddedApp.of {
        handlers {
            get(":sleepFor") {
                Integer sleepFor = context.pathTokens['sleepFor'].toInteger() ?: 1000
                sleep(sleepFor)
                context.render sleepFor.toString()
            }
        }
    }

    def setupSpec() {
        aut = new ApiUnderTest()
        URI address = aut.address // force it to start
        startTime = System.currentTimeMillis()
    }

    def setup() {
        client = aut.httpClient
        registry.get(AppProperties).otherAppUrl = "http://${stubApp.address.host}:${stubApp.address.port}"
    }

    protected Registry getRegistry() {
        return aut.server.serverRegistry
    }

    def cleanupSpec() {
        aut.close()
    }

    def "test async observable get"() {
        when:
        get("/observable")

        then:

        Long waitTimeSum = ObservableHandler.WAIT_TIMES.sum() as Long
        Long totalTestTime = System.currentTimeMillis() - startTime

        response.statusCode == 200
        response.body.text == 'waited for ' + waitTimeSum
        totalTestTime < waitTimeSum
    }

    def "test async promise get"() {
        when:
        get("/promise")

        then:
        Long waitTimeSum = PromiseHandler.WAIT_TIMES.sum() as Long
        Long totalTestTime = System.currentTimeMillis() - startTime

        response.statusCode == 200
        response.body.text == 'waited for ' + waitTimeSum
        totalTestTime < waitTimeSum
    }
}
