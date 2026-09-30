package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.tracing.opentelemetry.ResourceProvider
import io.opentelemetry.contrib.aws.resource.Ec2Resource
import io.opentelemetry.sdk.resources.Resource
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "AwsResourceProviderTest")
// tag::clazz[]
@Singleton
class AwsResourceProvider : ResourceProvider {

    override fun resource(): Resource {
        return Resource.getDefault()
            .merge(Ec2Resource.get())
    }
}
// end::clazz[]
