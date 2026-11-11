package com.islamicapp.noor

import org.json.JSONArray
import org.json.JSONObject

/**
 * Uniform envelope for every bridge answer:
 * { requestId, success, data, error, permissionRequired }
 */
object BridgeResponse {

    fun ok(requestId: String, data: Any?): JSONObject {
        val json = JSONObject()
        json.put("requestId", requestId)
        json.put("success", true)
        json.put("data", normalize(data))
        json.put("error", JSONObject.NULL)
        json.put("permissionRequired", JSONObject.NULL)
        return json
    }

    fun error(requestId: String, message: String, permission: String? = null): JSONObject {
        val json = JSONObject()
        json.put("requestId", requestId)
        json.put("success", false)
        json.put("data", JSONObject.NULL)
        json.put("error", message)
        json.put("permissionRequired", permission ?: JSONObject.NULL)
        return json
    }

    private fun normalize(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject -> value
            is JSONArray -> value
            is Map<*, *> -> {
                val obj = JSONObject()
                for ((key, item) in value) {
                    obj.put(key.toString(), normalize(item))
                }
                obj
            }
            is Collection<*> -> {
                val array = JSONArray()
                for (item in value) array.put(normalize(item))
                array
            }
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }
    }
}
