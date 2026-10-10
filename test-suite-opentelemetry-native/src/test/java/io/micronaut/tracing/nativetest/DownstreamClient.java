package io.micronaut.tracing.nativetest;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

/**
 * Declarative client of {@link DownstreamController}, traced by the HTTP client filter.
 */
@Client("/downstream")
public interface DownstreamClient {

    @Consumes(MediaType.TEXT_PLAIN)
    @Get("/{name}")
    String shout(String name);
}
