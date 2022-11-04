package io.micronaut.tracing.opentelemetry;

import io.micronaut.core.annotation.NonNull;
import io.opentelemetry.contrib.aws.resource.Ec2Resource;
import io.opentelemetry.sdk.resources.Resource;

import jakarta.inject.Singleton;

@Singleton
public class AwsResourceProvider implements ResourceProvider {

    @Override
    @NonNull
    public Resource resource() {
        return Resource.getDefault()
            .merge(Ec2Resource.get());
    }
}
