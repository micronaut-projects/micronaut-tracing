package io.micronaut.tracing.opentelemetry;

//tag::imports[]
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.View;
import jakarta.inject.Singleton;

import java.util.Arrays;
//end::imports[]

class OpenTelemetryBuilderCustomizerExample {

    //tag::histogramViewCustomizer[]
    @Singleton
    OpenTelemetryBuilderCustomizer histogramViewCustomizer() {
        return builder -> builder.addMeterProviderCustomizer((meterProviderBuilder, configProperties) -> {
            meterProviderBuilder.registerView(
                InstrumentSelector.builder()
                    .setType(InstrumentType.HISTOGRAM)
                    .setName("http.server.request.duration")
                    .build(),
                View.builder()
                    .setAggregation(Aggregation.explicitBucketHistogram(Arrays.asList(0.005, 0.01, 0.025, 0.05, 0.1)))
                    .build()
            );
            return meterProviderBuilder;
        });
    }
    //end::histogramViewCustomizer[]
}
