package com.naleid

import com.google.inject.AbstractModule
import com.google.inject.Scopes

class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ForkHandler).in(Scopes.SINGLETON)
        bind(ParallelObservableHandler).in(Scopes.SINGLETON)
        bind(ObservableHandler).in(Scopes.SINGLETON)
        bind(PromiseHandler).in(Scopes.SINGLETON)
        bind(AppProperties).in(Scopes.SINGLETON)
    }
}

