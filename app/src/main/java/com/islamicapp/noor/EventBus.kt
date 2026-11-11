package com.islamicapp.noor

import org.json.JSONObject

/**
 * Single fan-out point for native state changes that must reach the WebView.
 * MainActivity registers a listener that forwards events into JavaScript.
 */
object EventBus {

    private var listener: ((String, Any?) -> Unit)? = null

    fun attach(target: (String, Any?) -> Unit) {
        listener = target
    }

    fun detach() {
        listener = null
    }

    fun emit(type: String, payload: Any?) {
        listener?.invoke(type, payload)
    }

    fun emitJson(type: String, payload: JSONObject) {
        listener?.invoke(type, payload)
    }
}
