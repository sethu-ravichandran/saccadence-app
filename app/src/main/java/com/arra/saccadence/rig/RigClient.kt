package com.arra.saccadence.rig

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class RigConnectionState { CONNECTING, CONNECTED, JOINED, DISCONNECTED }

/**
 * Connects to the rig's relay over the clinic LAN and joins its session.
 * Mirrors `public/js/wsClient.js` on the rig side: reconnect-with-backoff on
 * drop, and re-sends `join` on every (re)connect so a mid-demo reconnect
 * doesn't require the operator to do anything — the relay's session
 * membership is otherwise lost per-connection, not per-device.
 */
class RigClient(
    private val host: String,
    private val port: Int,
    private val sessionCode: String,
    private val onEvent: (RigEvent) -> Unit,
    private val onStateChange: (RigConnectionState) -> Unit = {},
    /** Called after each failed connect attempt, with the running streak count —
     *  lets the UI warn ("can't reach the rig") instead of showing "waiting" forever. */
    private val onConnectFailure: (attempt: Int) -> Unit = {},
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WS is long-lived; no read timeout.
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var socket: WebSocket? = null
    private var closedByCaller = false
    private var reconnectDelayMs = 500L
    private var failureStreak = 0

    fun connect() {
        closedByCaller = false
        openSocket()
    }

    fun close() {
        closedByCaller = true
        socket?.close(1000, "client closing")
        socket = null
    }

    fun send(raw: String) {
        socket?.send(raw)
    }

    private fun openSocket() {
        val request = Request.Builder().url("ws://$host:$port/").build()
        setState(RigConnectionState.CONNECTING)

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelayMs = 500L
                failureStreak = 0
                setState(RigConnectionState.CONNECTED)
                webSocket.send(joinMessage(sessionCode))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = parseRigEvent(text)
                if (event is RigEvent.JoinAck && event.ok) setState(RigConnectionState.JOINED)
                mainHandler.post { onEvent(event) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.w("RigClient", "closed: code=$code reason=$reason host=$host:$port")
                setState(RigConnectionState.DISCONNECTED)
                reportFailure()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.w("RigClient", "connect failed to $host:$port: ${t.javaClass.simpleName}: ${t.message}")
                setState(RigConnectionState.DISCONNECTED)
                reportFailure()
                scheduleReconnect()
            }
        })
    }

    private fun reportFailure() {
        failureStreak++
        val attempt = failureStreak
        mainHandler.post { onConnectFailure(attempt) }
    }

    private fun scheduleReconnect() {
        if (closedByCaller) return
        mainHandler.postDelayed({ if (!closedByCaller) openSocket() }, reconnectDelayMs)
        reconnectDelayMs = min(reconnectDelayMs * 2, 5000L)
    }

    private fun setState(state: RigConnectionState) {
        mainHandler.post { onStateChange(state) }
    }
}
