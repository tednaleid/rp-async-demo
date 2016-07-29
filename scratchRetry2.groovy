#! /usr/bin/env groovy
@Grab('io.ratpack:ratpack-groovy:1.3.3')
@Grab('io.ratpack:ratpack-rx:1.3.3')

import static ratpack.groovy.Groovy.ratpack
import rx.plugins.RxJavaPlugins

ratpack {
  handlers {
    get {
      rx.Observable.from([1, 2, 3])
        .forkEach()
        .flatMap { rx.Observable.just(it + 1) }
        .bindExec()
        .reduce { acc, val -> acc + val }
        .promiseSingle()
        .then {
          render "${it} : ${RxJavaPlugins.getInstance().getObservableExecutionHook().toString()}"
        }
    }
  }
}