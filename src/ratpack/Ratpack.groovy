import com.naleid.ObservableHandler
import com.naleid.AppModule
import com.naleid.PromiseHandler
import ratpack.rx.RxRatpack
import ratpack.server.Service
import ratpack.server.StartEvent

import static ratpack.groovy.Groovy.ratpack

ratpack {
    bindings {
        module(AppModule)
        bindInstance Service, new Service() {
            @Override
            void onStart(StartEvent event) throws Exception {
                RxRatpack.initialize()
            }
        }
    }

    handlers {
        get {
            render "Go to http://localhost:5050/observable or http://localhost:5050/promise"
        }
        get "observable", registry.get(ObservableHandler)
        get "promise", registry.get(PromiseHandler)
    }
}