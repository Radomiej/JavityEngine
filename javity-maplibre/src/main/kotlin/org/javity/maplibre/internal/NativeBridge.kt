package org.javity.maplibre.internal

import org.maplibre.nativeffi.Maplibre
import org.maplibre.nativeffi.runtime.NetworkStatus
import org.maplibre.nativeffi.runtime.RuntimeEventBatch
import org.maplibre.nativeffi.runtime.RuntimeEventType
import org.maplibre.nativeffi.render.*

/** Java interop for the upstream Kotlin inline-value-class API; not gameplay API. */
internal object NativeBridge {
    @JvmStatic fun checkErrors(batch: RuntimeEventBatch) {
        for (event in batch.events) {
            if (event.type == RuntimeEventType.MAP_LOADING_FAILED || event.type == RuntimeEventType.MAP_RENDER_ERROR)
                throw IllegalStateException("MapLibre: " + event.message)
        }
    }
    @JvmStatic fun offline() { Maplibre.setNetworkStatus(NetworkStatus.OFFLINE) }
    @JvmStatic fun rendered(update: RenderUpdate): Boolean = update.result == RenderResult.RENDERED
    @JvmStatic fun egl(display: Long, config: Long, context: Long, proc: Long): EglContextDescriptor =
        EglContextDescriptor(NativePointer.ofAddress(display), NativePointer.ofAddress(config),
            NativePointer.ofAddress(context), NativePointer.ofAddress(proc))
}
