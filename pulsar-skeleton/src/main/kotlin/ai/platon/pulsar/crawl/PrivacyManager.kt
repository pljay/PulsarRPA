package ai.platon.pulsar.crawl

import ai.platon.pulsar.common.PreemptChannelSupport
import ai.platon.pulsar.common.config.ImmutableConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

abstract class PrivacyManager(
        val immutableConfig: ImmutableConfig
): PreemptChannelSupport("PrivacyManager"), AutoCloseable {
    protected val log = LoggerFactory.getLogger(PrivacyManager::class.java)

    private val closed = AtomicBoolean()
    val isActive get() = !closed.get()

    val zombieContexts = ConcurrentLinkedDeque<PrivacyContext>()
    val activeContexts = ConcurrentHashMap<PrivacyContextId, PrivacyContext>()
    val nextActiveContext: PrivacyContext
        get() = activeContexts.values.firstOrNull { it.isActive } ?: computeIfAbsent(PrivacyContextId.generate())

    open fun computeIfAbsent(id: PrivacyContextId) = activeContexts.computeIfAbsent(id) { create() }

    abstract fun create(): PrivacyContext

    abstract fun computeIfNotActive(id: PrivacyContextId): PrivacyContext

    inline fun <reified C: PrivacyContext> computeIfLeaked(context: C, crossinline mappingFunction: () -> C): C {
        synchronized(PrivacyContext::class.java) {
            if (!context.isLeaked) {
                return context
            }

            // Refresh the context if privacy leaked
            return preempt {
                // normal tasks must wait until all preemptive tasks are finished, but no new task enters the
                // critical section

                if (context.isLeaked) {
                    // close the current context
                    // until the old context is closed entirely
                    activeContexts.remove(context.id)
                    zombieContexts.add(context)
                    context.close()
                }

                mappingFunction()
            }
        }
    }

    fun healthyCheck() {
        zombieContexts.forEach {
            kotlin.runCatching { it.close() }.onFailure {
                log.error("Failed to close privacy context", it)
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            activeContexts.values.forEach { zombieContexts.add(it) }
            activeContexts.clear()

            zombieContexts.forEach {
                kotlin.runCatching { it.close() }.onFailure {
                    log.error("Failed to close privacy context", it)
                }
            }
        }
    }
}
