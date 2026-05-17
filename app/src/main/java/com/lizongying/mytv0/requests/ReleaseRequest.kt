package com.lizongying.mytv0.requests

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ReleaseRequest {

    suspend fun getRelease(): ReleaseResponse? {
        return withContext(Dispatchers.IO) {
            for (host in HttpClient.RELEASE_HOSTS) {
                var retries = 0
                while (retries <= MAX_RETRIES) {
                    try {
                        val result = fetchRelease(host)
                        if (result != null) return@withContext result
                        retries++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch release from $host (attempt ${retries + 1}): ${e.message}")
                        retries++
                        if (retries > MAX_RETRIES) break
                        delay(RETRY_DELAY_MS)
                    }
                }
                Log.w(TAG, "Host $host exhausted after $MAX_RETRIES retries")
            }
            null
        }
    }

    private fun fetchRelease(host: String): ReleaseResponse? {
        val url = "${host}${HttpClient.BUILD_BRANCH}/version.json"
        val request = Request.Builder().url(url).build()
        return try {
            val response = HttpClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body()?.string() ?: return null
                try {
                    Gson().fromJson(body, ReleaseResponse::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Invalid JSON from $host: ${e.message}")
                    null
                }
            } else {
                Log.w(TAG, "Release check failed from $host: HTTP ${response.code()}")
                null
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS resolution failed for $host")
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection/read timeout for $host")
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Network error for $host: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val TAG = "ReleaseRequest"
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 5000L
    }
}
