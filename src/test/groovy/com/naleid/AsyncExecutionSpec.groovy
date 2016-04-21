package com.naleid

import ratpack.test.http.TestHttpClient
import spock.lang.Shared
import spock.lang.Specification

class AsyncExecutionSpec extends Specification {

    @Delegate
    TestHttpClient client

    @Shared
    ApiUnderTest aut

    def setupSpec() {
        aut = new ApiUnderTest()
        URI address = aut.address // force it to start
    }

    def setup() {
        client = aut.httpClient
    }

    def cleanupSpec() {
        aut.close()
    }

}
