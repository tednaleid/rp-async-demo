package com.naleid

import groovy.transform.CompileStatic
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

@CompileStatic
class AllHandler extends GroovyHandler {

    @Override
    protected void handle(GroovyContext context) {
        String fromRegistry = context.get(String)
        context.byContent {
            json {
                context.render("hello ${fromRegistry}!".toString())
            }
        }
    }
}
