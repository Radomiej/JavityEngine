package org.javity.script.quickjs

import org.javity.script.ScriptUiEngine
import org.javity.script.ScriptEngineException

/** QuickJS-KT transport implementation for the shared ScriptUiHostSession. */
class QuickJsScriptUiEngine : ScriptUiEngine {
    private val owner = OwnerThreadQuickJs()

    override fun defineFunction(name: String, function: ScriptUiEngine.HostFunction) {
        try {
            owner.defineSync(name) { arguments -> function.invoke(arguments) }
        } catch (failure: Exception) {
            throw ScriptEngineException("QuickJS binding failed: ${failure.message}", failure)
        }
    }

    override fun evaluate(source: String): Any? = try {
        owner.evaluate(source)
    } catch (failure: Exception) {
        throw ScriptEngineException("QuickJS evaluation failed: ${failure.message}", failure)
    }

    /** QuickJS-KT awaits a promise returned as the eval completion value. The shared host discards
     * completion values for ordinary eval/callback units, so append a harmless final empty value
     * while still evaluating the original source directly in the persistent global realm. This
     * leaves guest promises pending/settling normally and lets owner-thread ticks make progress. */
    override fun evaluateDiscardingResult(source: String) {
        evaluate("$source\n;void 0;")
    }

    override fun checkSyntax(source: String) {
        try {
            owner.compile(source)
        } catch (failure: Exception) {
            throw ScriptEngineException("QuickJS syntax check failed: ${failure.message}", failure)
        }
    }

    override fun close() {
        try {
            owner.close()
        } catch (failure: Exception) {
            throw ScriptEngineException("QuickJS close failed: ${failure.message}", failure)
        }
    }
}
