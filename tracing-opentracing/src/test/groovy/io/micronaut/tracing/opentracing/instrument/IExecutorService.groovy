package io.micronaut.tracing.opentracing.instrument

import java.util.concurrent.ExecutorService

interface IExecutorService extends ExecutorService {
    void newMethod()
}
