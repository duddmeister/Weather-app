package io.github.duddmeister.weather.core

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thrown for non-2xx responses. [body] is kept short so it can be logged. */
class HttpException(val code: Int, val url: String, val body: String) :
    IOException("HTTP $code for $url: ${body.take(300)}")

class HttpResult(val code: Int, val body: String, val headers: Headers) {
    val isSuccessful: Boolean get() = code in 200..299
}

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

/**
 * Small JSON-over-HTTP helper. Requests are cancellable, and the body is read on OkHttp's thread
 * so a cancelled coroutine never leaks an open response.
 */
class HttpFetcher(
    private val client: OkHttpClient,
    private val userAgent: String,
) {
    suspend fun get(url: HttpUrl, headers: Map<String, String> = emptyMap()): HttpResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).awaitResult()
    }

    /** GETs [url] and returns the body, throwing [HttpException] for anything but 2xx. */
    suspend fun getBody(url: HttpUrl, headers: Map<String, String> = emptyMap()): String {
        val result = get(url, headers)
        if (!result.isSuccessful) throw HttpException(result.code, url.redactedForLog(), result.body)
        return result.body
    }

    private fun HttpUrl.redactedForLog(): String = "$scheme://$host$encodedPath"
}

private suspend fun Call.awaitResult(): HttpResult = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { HttpResult(it.code, it.body?.string().orEmpty(), it.headers) }
            } catch (e: IOException) {
                continuation.resumeWithException(e)
                return
            }
            continuation.resume(result)
        }
    })
}
