package com.motrix.android.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.motrix.android.app.di.RpcUrl
import com.motrix.android.app.di.WsUrl
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2RpcClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @RpcUrl private val rpcUrl: String,
    @WsUrl private val wsUrl: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var rpcSecret = ""
    private val requestIdCounter = AtomicInteger(0)

    fun configure(rpcSecret: String) {
        this.rpcSecret = rpcSecret
    }

    fun getRpcUrl(): String = rpcUrl

    fun getWsUrl(): String = wsUrl

    suspend fun <T> call(
        method: String,
        params: List<Any?> = emptyList(),
        responseParser: (JsonElement) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val id = requestIdCounter.incrementAndGet()
            val allParams = buildList {
                if (rpcSecret.isNotEmpty()) {
                    add(JsonPrimitive("token:$rpcSecret"))
                }
                addAll(params.filterNotNull().map { param ->
                    when (param) {
                        is String -> JsonPrimitive(param)
                        is Int -> JsonPrimitive(param)
                        is Long -> JsonPrimitive(param)
                        is Boolean -> JsonPrimitive(param)
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val stringMap = param as Map<String, String>
                            kotlinx.serialization.json.buildJsonObject {
                                stringMap.forEach { (k, v) ->
                                    put(k, JsonPrimitive(v))
                                }
                            }
                        }
                        is List<*> -> {
                            kotlinx.serialization.json.buildJsonArray {
                                param.forEach { item ->
                                    when (item) {
                                        is String -> add(JsonPrimitive(item))
                                        is Int -> add(JsonPrimitive(item))
                                        is Long -> add(JsonPrimitive(item))
                                        is Boolean -> JsonPrimitive(item)
                                        else -> add(JsonPrimitive(item.toString()))
                                    }
                                }
                            }
                        }
                        else -> JsonPrimitive(param.toString())
                    }
                })
            }

            val request = RpcRequest(
                id = id,
                method = method,
                params = allParams
            )

            val requestJson = json.encodeToString(RpcRequest.serializer(), request)
            val requestBody = requestJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

            val httpRequest = Request.Builder()
                .url(rpcUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Empty response body from RPC call")

            if (!response.isSuccessful) {
                throw IllegalStateException("RPC HTTP error: ${response.code} - $responseBody")
            }

            val rpcResponse = json.decodeFromString<RpcResponse>(responseBody)

            if (rpcResponse.error != null) {
                val error = rpcResponse.error
                throw RpcException(error.code, error.message)
            }

            responseParser(rpcResponse.result ?: JsonObject(emptyMap()))
        }
    }

    suspend fun callString(method: String, params: List<Any?> = emptyList()): Result<String> {
        return call(method, params) { element ->
            element.jsonPrimitive.content
        }
    }

    suspend fun callUnit(method: String, params: List<Any?> = emptyList()): Result<Unit> {
        return call(method, params) { Unit }
    }

    suspend fun callStringList(method: String, params: List<Any?> = emptyList()): Result<List<String>> {
        return call(method, params) { element ->
            element.jsonArray.map { it.jsonPrimitive.content }
        }
    }

    @Serializable
    data class RpcRequest(
        val jsonrpc: String = "2.0",
        val id: Int,
        val method: String,
        val params: List<JsonElement>
    )

    @Serializable
    data class RpcResponse(
        val id: Int? = null,
        val jsonrpc: String? = null,
        val result: JsonElement? = null,
        val error: RpcError? = null
    )

    @Serializable
    data class RpcError(
        val code: Int,
        val message: String
    )

    class RpcException(val code: Int, message: String) : Exception("Aria2 RPC Error [$code]: $message")

    companion object {
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    }
}
