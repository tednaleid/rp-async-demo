package com.naleid

import groovy.transform.CompileStatic
import ratpack.groovy.test.GroovyRatpackMainApplicationUnderTest
import ratpack.impose.ForceServerListenPortImposition
import ratpack.impose.ImpositionsSpec
import ratpack.server.RatpackServer
import ratpack.server.internal.DefaultRatpackServer

@CompileStatic
class ApiUnderTest extends GroovyRatpackMainApplicationUnderTest {

    protected DefaultRatpackServer server

    @Override
    protected RatpackServer createServer() throws Exception {
        return server = super.createServer() as DefaultRatpackServer
    }

    @Override
    protected void addDefaultImpositions(ImpositionsSpec impositionsSpec) {
        impositionsSpec.add(ForceServerListenPortImposition.ephemeral())
    }
}

