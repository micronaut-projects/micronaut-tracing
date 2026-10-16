package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "ReportJobSpec")
// tag::clazz[]
@Singleton
class ReportJob {

    @Scheduled(fixedDelay = "5m")
    void generate() { // <1>
        // generate the report
    }
}
// end::clazz[]
