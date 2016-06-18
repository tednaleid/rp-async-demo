package com.naleid

import com.google.common.util.concurrent.ListenableFuture
import com.google.inject.Inject
import groovy.transform.CompileStatic
import ratpack.exec.Downstream
import ratpack.exec.Execution
import ratpack.exec.Promise
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient

@CompileStatic
class ScratchHandler extends GroovyHandler {
    @Inject
    AppProperties appProperties

    @Inject
    HttpClient httpClient

    @Override
    protected void handle(GroovyContext context) {
        println "Handle Thread: ${Thread.currentThread().name}"

        Promise.async({ Downstream downstream ->

            // ask for an execution to be scheduled on another compute thread
            Execution.fork().start({ forkedExec ->
                println "Fork Thread: ${Thread.currentThread().name}"
                downstream.success("hello from fork")
            })

        }).then { result ->
            println "Then Thread: ${Thread.currentThread().name}"
            context.render result
        }
    }
}
