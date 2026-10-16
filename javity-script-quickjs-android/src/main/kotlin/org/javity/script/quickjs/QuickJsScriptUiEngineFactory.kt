package org.javity.script.quickjs

import org.javity.script.ScriptUiEngine
import org.javity.script.ScriptUiEngineFactory

/** Android provider selected explicitly by the Android launcher. */
class QuickJsScriptUiEngineFactory : ScriptUiEngineFactory {
    override fun create(): ScriptUiEngine = QuickJsScriptUiEngine()
}
