/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.benchmarks;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

/**
 * A trivial controller with a templated route (so the {@code http.route} attribute is resolved), used by
 * {@link HttpServerBenchmark}.
 */
@Controller("/bench")
public class BenchController {

    /**
     * @param name a path variable
     * @return a constant-size body
     */
    @Get("/hello/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(String name) {
        return "Hello " + name;
    }
}
