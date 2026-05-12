package io.micronaut.tracing.opentelemetry;

//tag::imports[]
import io.micronaut.context.annotation.Factory;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.View;
import jakarta.inject.Singleton;

import java.util.List;
//end::imports[]

//tag::histogramViewCustomizer[]
@Factory
class OpenTelemetryBuilderCustomizerExample {

    private static final List<Double> HISTOGRAM_BOUNDARIES = List.of(1.0d, 5.0d, 10.0d);

    @Singleton
    OpenTelemetryBuilderCustomizer histogramViewCustomizer() {
        return builder -> builder.addMeterProviderCustomizer((meterProviderBuilder, configProperties) -> {
            meterProviderBuilder.registerView(
                InstrumentSelector.builder()
                    .setType(InstrumentType.HISTOGRAM)
                    .setName("http.server.request.duration")
                    .build(),
                View.builder()
                    .setAggregation(Aggregation.explicitBucketHistogram(HISTOGRAM_BOUNDARIES))
                    .build()
            );
            return meterProviderBuilder;
        });
    }
}
//end::histogramViewCustomizer[]
