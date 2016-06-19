package com.naleid

import groovy.transform.CompileStatic
import ratpack.exec.Downstream
import ratpack.exec.Execution
import ratpack.exec.Promise
import ratpack.func.Action
import ratpack.func.Pair

import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
class PromisePair {
    public static <T, U> Promise<Pair<T, U>> parallel(Promise<T> first, Promise<U> second) {
        AtomicInteger counter = new AtomicInteger(2)

        T firstValue
        U secondValue

        return Promise.async { Downstream<Pair<T, U>> downstream ->
            Action<Execution> checkCompletion = { Execution e ->
                if (counter.decrementAndGet() == 0) {
                    downstream.success(Pair.of(firstValue, secondValue))
                }
            } as Action

            Execution.fork().onComplete(checkCompletion).start {
                first.onError { Throwable t -> downstream.error(t) }.then { firstValue = it }
             }

            Execution.fork().onComplete(checkCompletion).start {
                second.onError { Throwable t -> downstream.error(t) }.then { secondValue = it }
            }
        }
    }
}
