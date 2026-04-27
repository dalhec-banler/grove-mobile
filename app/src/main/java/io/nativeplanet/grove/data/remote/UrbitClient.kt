package io.nativeplanet.grove.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class UrbitClient(
    private val baseUrl: String = "http://127.0.0.1:80"
) {
    companion object {
        private const val TAG = "UrbitClient"
    }

    private val gson = Gson()
    private val eventId = AtomicLong(1)
    private var channelId: String? = null
    private var authCookie: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _connectionState = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _shipName = MutableStateFlow<String?>(null)
    val shipName: StateFlow<String?> = _shipName.asStateFlow()

    suspend fun authenticate(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = "password=$code".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/~/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    authCookie = response.headers("Set-Cookie")
                        .find { it.startsWith("urbauth") }
                        ?.split(";")?.firstOrNull()
                    fetchShipName()
                    _connectionState.value = true
                    true
                } else {
                    Log.e(TAG, "Auth failed: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth error", e)
            false
        }
    }

    private suspend fun fetchShipName() {
        try {
            val request = requestBuilder("/~/name").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _shipName.value = response.body?.string()?.trim()?.trim('"')
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ship name", e)
        }
    }

    private fun requestBuilder(path: String): Request.Builder {
        return Request.Builder()
            .url("$baseUrl$path")
            .apply {
                authCookie?.let { header("Cookie", it) }
            }
    }

    suspend fun <T> scry(path: String, parser: (String) -> T): T? = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("/~/scry/grove$path.json").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { parser(it) }
                } else {
                    Log.e(TAG, "Scry failed: $path -> ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scry error: $path", e)
            null
        }
    }

    suspend fun poke(action: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        ensureChannel()
        val id = channelId ?: return@withContext false

        try {
            val payload = listOf(
                mapOf(
                    "id" to eventId.getAndIncrement(),
                    "action" to "poke",
                    "ship" to (_shipName.value?.removePrefix("~") ?: return@withContext false),
                    "app" to "grove",
                    "mark" to "grove-action",
                    "json" to action
                )
            )

            val request = requestBuilder("/~/channel/$id")
                .put(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Poke failed", e)
            false
        }
    }

    private suspend fun ensureChannel() {
        if (channelId == null) {
            channelId = "grove-${System.currentTimeMillis()}"
        }
    }

    fun subscribeUpdates(): Flow<JsonObject> = callbackFlow {
        ensureChannel()
        val id = channelId ?: return@callbackFlow

        val subscribePayload = listOf(
            mapOf(
                "id" to eventId.getAndIncrement(),
                "action" to "subscribe",
                "ship" to (_shipName.value?.removePrefix("~") ?: ""),
                "app" to "grove",
                "path" to "/updates"
            )
        )

        val subscribeRequest = requestBuilder("/~/channel/$id")
            .put(gson.toJson(subscribePayload).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(subscribeRequest).execute().close()

        val sseRequest = requestBuilder("/~/channel/$id")
            .header("Accept", "text/event-stream")
            .get()
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    val responseData = json.getAsJsonObject("json")
                    if (responseData != null) {
                        trySend(responseData)
                    }
                    json.get("id")?.asInt?.let { ackEvent(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "SSE parse error", e)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE failure", t)
                _connectionState.value = false
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE closed")
                _connectionState.value = false
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(sseRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    private fun ackEvent(eventIdToAck: Int) {
        val id = channelId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = listOf(
                    mapOf(
                        "id" to eventId.getAndIncrement(),
                        "action" to "ack",
                        "event-id" to eventIdToAck
                    )
                )

                val request = requestBuilder("/~/channel/$id")
                    .put(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Ack failed", e)
            }
        }
    }

    suspend fun downloadFile(fileId: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = requestBuilder("/grove-file/$fileId").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } else {
                    Log.e(TAG, "Download failed: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            false
        }
    }

    suspend fun uploadFile(
        name: String,
        fileMark: String,
        data: ByteArray,
        tags: List<String>
    ): Boolean {
        val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
        return poke(mapOf(
            "upload" to mapOf(
                "name" to name,
                "file-mark" to fileMark,
                "data" to base64,
                "tags" to tags
            )
        ))
    }

    fun disconnect() {
        _connectionState.value = false
        channelId = null
        authCookie = null
    }
}
