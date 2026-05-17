package com.lizongying.mytv0


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.lizongying.mytv0.requests.HttpClient
import com.lizongying.mytv0.requests.ReleaseRequest
import com.lizongying.mytv0.requests.ReleaseResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UpdateManager(
    private val context: Context,
    private val versionCode: Long
) : ConfirmationFragment.ConfirmationListener {

    private var releaseRequest = ReleaseRequest()
    private var release: ReleaseResponse? = null
    private val okHttpClient = HttpClient.okHttpClient
    private var downloadJob: Job? = null
    private var lastLoggedProgress = -1
    private var isChecking = false

    fun checkAndUpdate() {
        if (isChecking) {
            Log.w(TAG, "Already checking for updates, ignoring duplicate request")
            return
        }
        isChecking = true
        Log.i(TAG, "checkAndUpdate")

        CoroutineScope(Dispatchers.Main).launch {
            "开始获取版本".showToast()
            var text = "版本获取失败"
            var update = false
            try {
                release = releaseRequest.getRelease()
                Log.i(TAG, "versionCode $versionCode ${release?.version_code}")
                if (release?.version_code != null) {
                    if (release?.version_code!! >= versionCode) {
                        text = "最新版本：${release?.version_name}"
                        update = true
                    } else {
                        text = "已是最新版本，不需要更新"
                    }
                } else {
                    "版本获取失败".showToast()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred: ${e.message}", e)
                "版本获取失败".showToast()
            } finally {
                isChecking = false
            }
            if (update) {
                updateUI(text, update)
            } else {
                text.showToast()
            }
        }
    }

    private fun updateUI(text: String, update: Boolean) {
        val dialog = ConfirmationFragment(this@UpdateManager, text, update)
        dialog.show((context as FragmentActivity).supportFragmentManager, TAG)
    }

    private fun startDownload(release: ReleaseResponse) {
        if (downloadJob?.isActive == true) {
            Log.w(TAG, "Download already in progress, ignoring")
            return
        }
        val apkName = "my-tv-0"
        val apkFileName = "$apkName-${release.version_name}.apk"
        val urls = HttpClient.DOWNLOAD_HOSTS.map { host ->
            "${host}${HttpClient.BUILD_BRANCH}/$apkName.apk"
        }
        var downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir == null) {
            downloadDir = File(context.filesDir, "downloads")
        }

        cleanupDownloadDirectory(downloadDir, apkName)
        val file = File(downloadDir, apkFileName)
        file.parentFile?.mkdirs()

        downloadJob = GlobalScope.launch(Dispatchers.IO) {
            downloadWithRetry(urls, file)
        }
    }

    private fun cleanupDownloadDirectory(directory: File?, apkNamePrefix: String) {
        directory?.let { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith(apkNamePrefix) && file.name.endsWith(".apk")) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.i(TAG, "Deleted old APK file: ${file.name}")
                    } else {
                        Log.e(TAG, "Failed to delete old APK file: ${file.name}")
                    }
                }
            }
        }
    }

    private suspend fun downloadWithRetry(urls: List<String>, file: File, maxRetries: Int = 2) {
        for ((index, url) in urls.withIndex()) {
            var retries = 0
            while (retries <= maxRetries) {
                try {
                    // Remove partial file before each fresh attempt
                    if (file.exists()) file.delete()
                    downloadFile(url, file)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    val errorType = when (e) {
                        is UnknownHostException -> "DNS解析失败"
                        is SocketTimeoutException -> "连接超时"
                        else -> "网络错误"
                    }
                    Log.e(TAG, "Download failed from $url ($errorType): ${e.message}")
                    retries++
                    if (retries > maxRetries) {
                        Log.e(TAG, "Host $url exhausted retries")
                        break
                    }
                    val delayMs = RETRY_DELAY_MS + (retries * 10_000L)
                    Log.i(TAG, "Retrying download from $url (${retries}/$maxRetries) after ${delayMs / 1000}s")
                    delay(delayMs)
                }
            }
        }
        withContext(Dispatchers.Main) {
            updateUI("下载失败，请检查网络连接后重试", false)
        }
    }

    private suspend fun downloadFile(url: String, file: File) {
        val request = okhttp3.Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected HTTP status ${response.code()}")

        val body = response.body() ?: throw IOException("Null response body")
        val contentLength = body.contentLength()
        var bytesRead = 0L

        try {
            body.byteStream().use { inputStream ->
                file.outputStream().use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytes: Int
                    while (inputStream.read(buffer).also { bytes = it } != -1) {
                        outputStream.write(buffer, 0, bytes)
                        bytesRead += bytes
                        val progress =
                            if (contentLength > 0) (bytesRead * 100 / contentLength).toInt() else -1
                        withContext(Dispatchers.Main) {
                            updateDownloadProgress(progress)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // Clean up partial file on failure
            if (file.exists()) file.delete()
            throw e
        }

        if (contentLength > 0 && bytesRead != contentLength) {
            if (file.exists()) file.delete()
            throw IOException("Download incomplete: expected $contentLength bytes, got $bytesRead")
        }

        withContext(Dispatchers.Main) {
            installNewVersion(file)
        }
    }

    private fun updateDownloadProgress(progress: Int) {
        if (progress == -1) {
            Log.i(TAG, "Download in progress, size unknown")
        } else if (progress % 10 == 0 && progress != lastLoggedProgress) {
            Log.i(TAG, "Download progress: $progress%")
            lastLoggedProgress = progress
            "升级文件已经下载：${progress}%".showToast()
        }
    }

    private fun installNewVersion(apkFile: File) {
        if (apkFile.exists()) {
            val apkUri = Uri.fromFile(apkFile)
            Log.i(TAG, "apkUri $apkUri")
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } else {
            Log.e(TAG, "APK file does not exist!")
        }
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val BUFFER_SIZE = 8192
        private const val RETRY_DELAY_MS = 30_000L
    }

    override fun onConfirm() {
        Log.i(TAG, "onConfirm $release")
        release?.let { startDownload(it) }
    }

    override fun onCancel() {
    }

    fun destroy() {
        downloadJob?.cancel()
    }
}