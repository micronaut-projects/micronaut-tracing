package io.micronaut.tracing.nativetest;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * Calls a traced service and, through a declarative client, a second endpoint of the same server.
 */
@Controller("/greet")
public class GreetingController {

    private final GreetingService greetingService;
    private final DownstreamClient downstreamClient;

    public GreetingController(GreetingService greetingService, DownstreamClient downstreamClient) {
        this.greetingService = greetingService;
        this.downstreamClient = downstreamClient;
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_PLAIN)
    @Get("/{name}")
    public String greet(String name) {
        return greetingService.greet(name) + " / " + downstreamClient.shout(name);
    }
}
