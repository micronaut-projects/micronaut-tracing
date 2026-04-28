package io.micronaut.tracing.opentelemetry;

//tag::imports[]
import io.micronaut.context.annotation.Factory;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.View;
import jakarta.inject.Singleton;
//end::imports[]

//tag::histogramViewCustomizer[]
@Factory
class OpenTelemetryBuilderCustomizerExample {

    @Singleton
    OpenTelemetryBuilderCustomizer histogramViewCustomizer() {
        return builder -> builder.addMeterProviderCustomizer((meterProviderBuilder, configProperties) -> {
            meterProviderBuilder.registerView(
                InstrumentSelector.builder()
                    .setType(InstrumentType.HISTOGRAM)
                    .setName("http.server.request.duration")
                    .build(),
                View.builder()
                    .setAggregation(Aggregation.explicitBucketHistogram())
                    .build()
            );
            return meterProviderBuilder;
        });
    }
}
//end::histogramViewCustomizer[]
