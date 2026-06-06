package com.motrix.android.core.network

import com.motrix.android.core.network.model.Aria2Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import com.motrix.android.app.di.WsUrl
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @WsUrl private val wsUrl: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<Aria2Event>(extraBufferCapacity = 64)
    val events: Flow<Aria2Event> = _events.asSharedFlow()

    private var isConnected = false

    fun connect() {
        connect(wsUrl)
    }

    fun connect(url: String) {
        if (isConnected) {
            Timber.w("WebSocket already connected, disconnecting first")
            disconnect()
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Timber.i("WebSocket connected to $url")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = parseWebSocketMessage(text)
                    if (event != null) {
                        val offered = _events.tryEmit(event)
                        if (!offered) {
                            Timber.w("WebSocket event buffer full, dropping event: $event")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse WebSocket message: $text")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WebSocket closing: code=$code reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Timber.i("WebSocket closed: code=$code reason=$reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Timber.e(t, "WebSocket failure")
            }
        }

        val wsClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        webSocket = wsClient.newWebSocket(request, listener)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        isConnected = false
        Timber.i("WebSocket disconnected")
    }

    fun isConnectionActive(): Boolean = isConnected

    fun observeEvents(): Flow<Aria2Event> = _events.asSharedFlow()

    private fun parseWebSocketMessage(text: String): Aria2Event? {
        return try {
            val jsonObject = json.parseToJsonElement(text).jsonObject
            val method = jsonObject["method"]?.jsonPrimitive?.contentOrNull ?: return null

            val params = jsonObject["params"]?.jsonArray ?: return null
            val gid = params.getOrNull(0)?.jsonObject?.get("gid")?.jsonPrimitive?.contentOrNull
                ?: return null

            when (method) {
                "aria2.onDownloadStart" -> Aria2Event.DownloadStart(gid)
                "aria2.onDownloadPause" -> Aria2Event.DownloadPause(gid)
                "aria2.onDownloadStop" -> Aria2Event.DownloadStop(gid)
                "aria2.onDownloadComplete" -> Aria2Event.DownloadComplete(gid)
                "aria2.onDownloadError" -> Aria2Event.DownloadError(gid)
                "aria2.onBtDownloadComplete" -> Aria2Event.BtDownloadComplete(gid)
                else -> {
                    Timber.w("Unknown WebSocket event method: $method")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WebSocket message")
            null
        }
    }
}
