package com.naleid

import groovy.transform.CompileStatic

@CompileStatic
class AppProperties {

    //Outside of test, run a sample app on localhost:5150 with the stubBackendWebservice.groovy file
    String otherAppUrl = 'http://localhost:5150'
}
