package com.naleid

import com.google.inject.Inject
import com.naleid.http.RetryHttpClient
import groovy.transform.CompileStatic
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.http.client.HttpClient
import ratpack.http.client.ReceivedResponse
import ratpack.rx.RxRatpack
import rx.Observable
import rx.functions.Func1

@CompileStatic
class RetryHandler extends GroovyHandler {

    @Inject
    AppProperties appProperties

    @Inject
    RetryHttpClient retryHttpClient

    @Override
    protected void handle(GroovyContext context) {
        Integer failFirst = context.request.queryParams.failFirst?.toInteger() ?: 0
        Integer maxRetries = context.request.queryParams.maxRetries?.toInteger() ?: 0

        println Thread.currentThread().name

        URI resetUri = createUri("reset")
        retryHttpClient.httpClient.get(resetUri)
                .observe()
                .flatMap {
                    URI uriThatFailsNTimesBeforeSucceeding = createUri(failFirst.toString())
                    retryHttpClient.get(uriThatFailsNTimesBeforeSucceeding, maxRetries)
                }
                .bindExec()
                .subscribe { ReceivedResponse response ->
                    println Thread.currentThread().name
                    String text = response.body.text
                    context.render text
                }
    }

    URI createUri(String path) {
        URI.create(appProperties.otherAppUrl + "/" + path)
    }
}
