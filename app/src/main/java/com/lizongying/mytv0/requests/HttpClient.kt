package com.lizongying.mytv0.requests

import android.util.Log
import com.lizongying.mytv0.MyTVApplication
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.conscrypt.Conscrypt
import retrofit2.Retrofit
import java.security.Security
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object HttpClient {
    const val TAG = "HttpClient"

    // The branch whose directory holds APK and version.json on the release branch
    const val BUILD_BRANCH = "kk"

    val RELEASE_HOSTS = listOf(
        "https://cdn.jsdelivr.net/gh/vrichv/my-tv-0@release/",
        "https://raw.githubusercontent.com/vrichv/my-tv-0/refs/heads/release/",
    )

    val DOWNLOAD_HOSTS = listOf(
        "https://cdn.jsdelivr.net/gh/vrichv/my-tv-0@release/",
        "https://raw.githubusercontent.com/vrichv/my-tv-0/refs/heads/release/",
    )

    val okHttpClient: OkHttpClient by lazy {
        getSafeOkHttpClient()
    }

    val configService: ConfigService by lazy {
        Retrofit.Builder()
            .client(okHttpClient)
            .build().create(ConfigService::class.java)
    }

    private fun getSafeOkHttpClient(): OkHttpClient {
        // Init Conscrypt
        val conscrypt = Conscrypt.newProvider()
        // Add as provider
        Security.insertProviderAt(conscrypt, 1)
        // OkHttp 3.12.x
        // ConnectionSpec.COMPATIBLE_TLS = TLS1.0
        // ConnectionSpec.MODERN_TLS = TLS1.0 + TLS1.1 + TLS1.2 + TLS 1.3
        // ConnectionSpec.RESTRICTED_TLS = TLS 1.2 + TLS 1.3
        val okHttpBuilder = OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(ConnectionSpec.RESTRICTED_TLS))

        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 4.4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36"
                )
                .build()
            chain.proceed(requestWithUserAgent)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        try {
            val tm = InternalX509TrustManager(MyTVApplication.getInstance().applicationContext)
            val sslContext = SSLContext.getInstance("TLS", conscrypt)
            sslContext.init(null, arrayOf(tm), null)
            okHttpBuilder.sslSocketFactory(InternalSSLSocketFactory(sslContext.socketFactory), tm)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up OkHttpClient", e)
        }

        return okHttpBuilder.dns(DnsCache()).retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS).addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor).build()
    }
}
