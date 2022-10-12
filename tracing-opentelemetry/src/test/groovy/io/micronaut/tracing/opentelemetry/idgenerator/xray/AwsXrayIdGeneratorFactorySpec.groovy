package io.micronaut.tracing.opentelemetry.idgenerator.xray

import io.micronaut.context.BeanContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.sdk.trace.IdGenerator
import jakarta.inject.Inject
import spock.lang.Specification
import io.opentelemetry.contrib.awsxray.AwsXrayIdGenerator

@MicronautTest(startApplication = false)
class AwsXrayIdGeneratorFactorySpec  extends Specification {

    @Inject
    BeanContext beanContext

    void "if you have the class AwsXrayIdGenerator a custom IdGenerator is registered"() {
        expect:
        beanContext.containsBean(IdGenerator)

        when:
        IdGenerator idGenerator = beanContext.getBean(IdGenerator)

        then:
        idGenerator instanceof AwsXrayIdGenerator
    }

}
