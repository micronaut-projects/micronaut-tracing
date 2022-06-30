package io.micronaut.tracing.opentelemetry.xray

import io.micronaut.context.BeanContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class SdkClientBuilderListenerSpec extends Specification {

    @Inject
    BeanContext beanContext

    void "bean of type SdkClientBuilderListener exists if aws sdk core and opentelemetry-aws-sdk-2.2 dependencies are present"() {
        expect:
        beanContext.containsBean(SdkClientBuilderListener)
    }

}
