from typing import Annotated

from io.opentelemetry.api.common import AttributeKey
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.tracing.opentelemetry import ResourceProvider
from org.junit.jupiter.api import Test

from .AwsResourceProvider import AwsResourceProvider


@Property(name="spec.name", value="AwsResourceProviderTest")
@MicronautTest(startApplication=False)
class AwsResourceProviderTest:

    resource_provider: Annotated[ResourceProvider, Inject]

    @Test
    def the_resource_provider_merges_the_aws_resource_into_the_default_resource(self) -> None:
        assert isinstance(self.resource_provider, AwsResourceProvider)

        resource = self.resource_provider.resource()

        # the default resource attributes are kept when no EC2 metadata endpoint is reachable
        assert resource.getAttribute(AttributeKey.stringKey("telemetry.sdk.name")) == "opentelemetry"
        assert resource.getAttribute(AttributeKey.stringKey("telemetry.sdk.language")) == "java"
