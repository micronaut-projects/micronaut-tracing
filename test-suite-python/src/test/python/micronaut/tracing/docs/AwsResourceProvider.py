from micronaut.context.annotation import Requires

# tag::imports[]
from io.opentelemetry.contrib.aws.resource import Ec2Resource
from io.opentelemetry.sdk.resources import Resource
from jakarta.inject import Singleton
from micronaut.tracing.opentelemetry import ResourceProvider
# end::imports[]


@Requires(property="spec.name", value="AwsResourceProviderTest")
# tag::clazz[]
@Singleton
class AwsResourceProvider(ResourceProvider):

    def resource(self) -> Resource:
        return Resource.getDefault().merge(Ec2Resource.get())
# end::clazz[]
