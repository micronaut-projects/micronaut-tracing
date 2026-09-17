from micronaut.context.annotation import Requires

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.tracing.opentelemetry import ResourceProvider

try:
    from io.opentelemetry.contrib.aws.resource import Ec2Resource
    from io.opentelemetry.sdk.resources import Resource
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from opentelemetry.contrib.aws.resource import Ec2Resource
    from opentelemetry.sdk.resources import Resource
# end::imports[]


@Requires(property="spec.name", value="AwsResourceProviderTest")
# tag::clazz[]
@Singleton
class AwsResourceProvider(ResourceProvider):

    def resource(self) -> Resource:
        return Resource.getDefault().merge(Ec2Resource.get())
# end::clazz[]
