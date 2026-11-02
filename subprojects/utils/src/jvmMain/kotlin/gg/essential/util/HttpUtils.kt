/*
 * Copyright (c) 2024 ModCore Inc. All rights reserved.
 *
 * This code is part of ModCore Inc.'s Essential Mod repository and is protected
 * under copyright registration # TX0009138511. For the full license, see:
 * https://github.com/EssentialGG/Essential/blob/main/LICENSE
 *
 * You may not use, copy, reproduce, modify, sell, license, distribute,
 * commercialize, or otherwise exploit, or create derivative works based
 * upon, this file or any other in this repository, all of which is reserved by Essential.
 */
@file:JvmName("HttpUtils")
package gg.essential.util

import gg.essential.data.VersionInfo
import gg.essential.handlers.CertChain
import gg.essential.util.har.HarFile
import gg.essential.util.har.HarFileLogger
import gg.essential.util.har.HttpLoggingEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resumeWithException
import kotlin.io.path.outputStream

private val harFileLoggerShutdownHook: Thread
private var harFileLoggerJob: Job? = SupervisorJob().also { job ->
    harFileLoggerShutdownHook = Thread {
        job.cancel()
        runBlocking { job.join() }
    }
    Runtime.getRuntime().addShutdownHook(harFileLoggerShutdownHook)
}
@Synchronized
fun takeOwnershipOfHarFileLoggerJob(): Job {
    Runtime.getRuntime().removeShutdownHook(harFileLoggerShutdownHook)
    val job = harFileLoggerJob ?: throw IllegalStateException("Ownership was already transferred")
    harFileLoggerJob = null
    return job
}
private val harFileLogger = HarFileLogger(
    CoroutineScope(harFileLoggerJob!!),
    HarFile.Creator("Essential", VersionInfo().essentialVersion),
)

val httpClient: CompletableFuture<OkHttpClient> = CompletableFuture.supplyAsync {
    val (sslContext, trustManagers) = CertChain().loadEmbedded().done()
    val trustManager = trustManagers[0] as X509TrustManager
    OkHttpClient.Builder()
        .dispatcher(Dispatcher(ExecutorAsAService(Dispatchers.IO.asExecutor())))
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .readTimeout(60, TimeUnit.SECONDS)
        .eventListenerFactory(HttpLoggingEventListener.Factory(
            LoggerFactory.getLogger("Essential - HTTP"),
            harFileLogger,
        ))
        .addInterceptor(HttpLoggingEventListener.Interceptor())
        .addInterceptor {
            it.proceed(
                it.request().newBuilder()
                    .header("User-Agent", "Essential/${VersionInfo().essentialVersion} (https://essential.gg)")
                    .build()
            )
        }
        // FIXME temporary workaround, not to be included in release builds
        .apply {  }
        .build()
}

private val httpClientDeferred = httpClient.asDeferred()

suspend fun httpClient(): OkHttpClient =
    httpClientDeferred.await()

suspend fun Call.executeAwait(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
        @OptIn(ExperimentalCoroutinesApi::class) // https://github.com/Kotlin/kotlinx.coroutines/issues/4088#issuecomment-2039429277
        override fun onResponse(call: Call, response: Response) = continuation.resume(response) { response.close() }
    })
}

suspend fun httpCall(request: Request): Response =
    httpClient().newCall(request).executeAwait()

@JvmOverloads
@Throws(IOException::class)
suspend fun httpGet(url: String, request: (Request.Builder) -> Unit = {}): Response {
    val response = httpCall(Request.Builder().url(url).apply(request).build())
    if(!response.isSuccessful) {
        response.close()
        throw IOException("Error from '$url': ${response.code} ${response.message}")
    }
    return response
}

@JvmOverloads
@Throws(IOException::class)
suspend fun httpGetToString(url: String, request: (Request.Builder) -> Unit = {}): String =
    httpGet(url, request).body.string()

@JvmOverloads
@Throws(IOException::class)
fun httpGetToStringBlocking(url: String, request: (Request.Builder) -> Unit = {}): String =
    runBlocking { httpGetToString(url, request) }

@JvmOverloads
@Throws(IOException::class)
suspend fun httpGetToBytes(url: String, request: (Request.Builder) -> Unit = {}): ByteArray =
    httpGet(url, request).body.bytes()

@JvmOverloads
@Throws(IOException::class)
fun httpGetToBytesBlocking(url: String, request: (Request.Builder) -> Unit = {}): ByteArray =
    runBlocking { httpGetToBytes(url, request) }

@JvmOverloads
@Throws(IOException::class)
suspend fun httpGetToInputStream(url: String, request: (Request.Builder) -> Unit = {}): InputStream =
    httpGet(url, request).body.byteStream()

@JvmOverloads
@Throws(IOException::class)
fun httpGetToInputStreamBlocking(url: String, request: (Request.Builder) -> Unit = {}): InputStream =
    runBlocking { httpGetToInputStream(url, request) }

@JvmOverloads
@Throws(IOException::class)
suspend fun httpGetToFile(url: String, path: Path, request: (Request.Builder) -> Unit = {}) {
    withContext(Dispatchers.IO) {
        httpGetToInputStream(url, request).use { input ->
            path.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

@JvmOverloads
@Throws(IOException::class)
fun httpGetToFileBlocking(url: String, path: Path, request: (Request.Builder) -> Unit = {}) =
    runBlocking { httpGetToFile(url, path, request) }