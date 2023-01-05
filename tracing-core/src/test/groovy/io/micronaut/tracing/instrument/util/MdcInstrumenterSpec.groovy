package io.micronaut.tracing.instrument.util

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.instrument.InvocationInstrumenter
import org.slf4j.MDC
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

@Slf4j("LOG")
class MdcInstrumenterSpec extends Specification {

    private static final String key = 'foo'
    private static final String value = 'bar'

    private AsyncConditions conds = new AsyncConditions()
    private MdcInstrumenter mdcInstrumenter = new MdcInstrumenter()

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void cleanup() {
        LOG.info('Clearing MDC')
        MDC.clear()
    }

    void 'test MDC instrumenter'() {
        given:
        MDC.put(key, value)

        and:
        Runnable runnable = instrument {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }
        def thread = new Thread(runnable)

        when:
        thread.start()

        then:
        conds.await()
        MDC.get(key) == value

        cleanup:
        thread.join()
    }

    void 'async operation runs as blocking operation within calling thread'() {
        given:
        MDC.put(key, value)

        and:
        Runnable runnable = instrument {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }

        when:
        runnable.run()

        then:
        conds.await()
        MDC.get(key) == value
    }

    void 'old context map is preserved after instrumented execution'() {
        given:
        MDC.put(key, value)
        Runnable runnable1 = instrument {
            conds.evaluate {
                assert MDC.get(key) == value
            }
        }

        and:
        String value2 = 'baz'
        MDC.put(key, value2)
        Runnable runnable2 = instrument {
            conds.evaluate {
                assert MDC.get(key) == value2
            }
        }

        when:
        runnable1.run()

        then:
        conds.await()
        MDC.get(key) == value2

        when:
        runnable2.run()

        then:
        conds.await()
        MDC.get(key) == value2
    }

    void 'empty context should not clean MDC'() {
        when:
        MDC.put(key, value)
        MDC.remove(key) // Make empty context
        Map<String, String> mdcBeforeNew = MDC.copyOfContextMap

        def instrumenterWithEmptyContext = mdcInstrumenter.newInvocationInstrumenter()

        MDC.put('contextValue', 'contextValue')
        def instrumenterWithContext = mdcInstrumenter.newInvocationInstrumenter()

        MDC.clear()

        Runnable runnable = instrument(instrumenterWithContext) {

            MDC.put('inside1', 'inside1')

            instrument(instrumenterWithEmptyContext) {
                assert MDC.get('contextValue') == 'contextValue'
                assert MDC.get('inside1') == 'inside1'

                MDC.put('inside2', 'inside2')
            }.run()

            assert MDC.get('contextValue') == 'contextValue'
            assert MDC.get('inside1') == 'inside1'
            assert MDC.get('inside2') == null
        }

        runnable.run()

        then:
        mdcBeforeNew.isEmpty()
        MDC.get(key) == null
        MDC.get('XXX') == null
        MDC.get('aaa') == null
    }

    void 'test MDC instrumenter with Executor'() {

        given:
        MDC.setContextMap(foo: 'bar')
        ExecutorService executor = applicationContext.getBean(ExecutorService, Qualifiers.byName('io'))
        String val = null

        when:
        CompletableFuture.supplyAsync({ ->
            val = MDC.get('foo')
        }, executor).get()

        then:
        val == 'bar'
    }

    private Runnable instrument(InvocationInstrumenter instrumenter = mdcInstrumenter.newInvocationInstrumenter(),
                                Closure closure) {
        InvocationInstrumenter.instrument(closure as Runnable, instrumenter)
    }
}
