package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Requires;

// tag::imports[]
import io.micronaut.core.annotation.NonNull;
import io.micronaut.tracing.opentelemetry.ResourceProvider;
import io.opentelemetry.contrib.aws.resource.Ec2Resource;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.inject.Singleton;
// end::imports[]

@Requires(property = "spec.name", value = "AwsResourceProviderTest")
// tag::clazz[]
@Singleton
public class AwsResourceProvider implements ResourceProvider {

    @Override
    @NonNull
    public Resource resource() {
        return Resource.getDefault()
            .merge(Ec2Resource.get());
    }
}
// end::clazz[]
