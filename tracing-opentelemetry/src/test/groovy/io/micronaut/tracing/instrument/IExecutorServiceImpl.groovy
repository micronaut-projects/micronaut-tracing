package io.micronaut.tracing.instrument

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Secondary
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

import static io.micronaut.core.util.StringUtils.TRUE

@Singleton
@Named("custom")
@Requires(property = PROP, value = TRUE)
@Secondary
class IExecutorServiceImpl extends AbstractExecutorService implements IExecutorService {

    public static final String PROP = 'iexecutor.enabled'

    @Override
    void newMethod() {
    }

    @Override
    void shutdown() {
    }

    @Override
    List<Runnable> shutdownNow() {
        []
    }

    @Override
    boolean isShutdown() {
        false
    }

    @Override
    boolean isTerminated() {
        false
    }

    @Override
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        false
    }

    @Override
    void execute(Runnable command) {
        command.run()
    }
}
