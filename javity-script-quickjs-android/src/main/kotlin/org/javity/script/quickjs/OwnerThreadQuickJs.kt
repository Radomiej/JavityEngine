package org.javity.script.quickjs

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.FunctionBinding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * Synchronous owner-thread adapter for the existing eval/tick API. This first
 * bridge exposes only synchronous host bindings; async host work is excluded.
 */
internal class OwnerThreadQuickJs(
    private val owner: Thread = Thread.currentThread(),
) : Closeable {
    private val dispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            checkOwner()
            block.run()
        }
    }
    private val quickJs = QuickJs.create(dispatcher).apply {
        memoryLimit = 64L * 1024L * 1024L
        maxStackSize = 512L * 1024L
        evaluationTimeoutMillis = 2_000L
    }
    private var inBinding = false
    private var evaluating = false
    private var closeAfterBoundary = false
    private var closed = false

    fun defineSync(name: String, callback: (Array<Any?>) -> Any?) {
        checkOwner()
        quickJs.defineBinding(name, FunctionBinding<Any?> { args ->
            check(!closed) { "QuickJS runtime is closed" }
            check(!inBinding) { "Nested QuickJS host binding" }
            inBinding = true
            try {
                callback(args)
            } finally {
                inBinding = false
            }
        })
    }

    /** Syntax-checks a source unit before a caller begins destructive reload. */
    fun compile(source: String): ByteArray {
        checkOwner()
        check(!closed) { "QuickJS runtime is closed" }
        return quickJs.compile(source, "javity-ui.js")
    }

    /** Evaluates a complete source unit in the persistent QuickJS global realm. */
    fun evaluate(source: String): Any? {
        checkOwner()
        checkCanEvaluate()
        evaluating = true
        try {
            return runBlocking(dispatcher) { quickJs.evaluate<Any?>(source, "javity-ui.js") }
        } finally {
            evaluating = false
            if (closeAfterBoundary) closeNative()
        }
    }

    /** Requests close safely when called from a binding; JNI close waits for unwind. */
    fun requestClose() {
        checkOwner()
        if (inBinding || evaluating) closeAfterBoundary = true else closeNative()
    }

    fun isClosed(): Boolean = closed || quickJs.isClosed

    private fun checkCanEvaluate() {
        check(!closed) { "QuickJS runtime is closed" }
        check(!inBinding) { "Recursive QuickJS evaluation from a binding is forbidden" }
        check(!evaluating) { "QuickJS evaluation is not reentrant" }
    }

    private fun checkOwner() {
        check(Thread.currentThread() === owner) { "QuickJS and Scene2D must stay on their owner thread" }
    }

    private fun closeNative() {
        if (closed) return
        checkOwner()
        check(!inBinding) { "QuickJS close must wait until the binding callback unwinds" }
        closeAfterBoundary = false
        closed = true
        quickJs.close()
    }

    override fun close() = requestClose()
}
