package io.micronaut.tracing.opentelemetry;

import io.micronaut.core.annotation.NonNull;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.inject.Singleton;
import io.opentelemetry.sdk.extension.aws.resource.Ec2Resource;

@Singleton
public class AwsResourceProvider implements ResourceProvider {
    @Override
    @NonNull
    public Resource resource() {
        return Resource.getDefault()
            .merge(Ec2Resource.get());
    }
}
