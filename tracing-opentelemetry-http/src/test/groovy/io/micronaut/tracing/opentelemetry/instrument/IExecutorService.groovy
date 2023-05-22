package io.micronaut.tracing.opentelemetry.instrument

import java.util.concurrent.ExecutorService

interface IExecutorService extends ExecutorService {
    void newMethod()
}
