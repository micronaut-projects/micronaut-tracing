package io.micronaut.tracing.nativetest;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

import java.util.Locale;

/**
 * The endpoint called by {@link DownstreamClient}.
 */
@Controller("/downstream")
public class DownstreamController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/{name}")
    public String shout(String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}
