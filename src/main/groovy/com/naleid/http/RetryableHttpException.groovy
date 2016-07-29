package com.naleid.http

import groovy.transform.CompileStatic

@CompileStatic
class RetryableHttpException extends Exception {
  RetryableHttpException(String message) {
    super(message)
  }

  @Override
  synchronized Throwable fillInStackTrace() {
    return this
  }
}
