import com.naleid.AllHandler

import static ratpack.groovy.Groovy.ratpack

ratpack {
    bindings {
        bindInstance(AllHandler, new AllHandler())
        bindInstance(String, "'Ratpack.groovy bindings'")
    }
    handlers {
        all registry.get(AllHandler)
    }
}