from micronaut.context.annotation import Requires

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.scheduling.annotation import Scheduled
# end::imports[]


@Requires(property="spec.name", value="ReportJobTest")
# tag::clazz[]
@Singleton
class ReportJob:

    @Scheduled(fixedDelay="5m")
    def generate(self) -> None:  # <1>
        pass  # generate the report
# end::clazz[]
