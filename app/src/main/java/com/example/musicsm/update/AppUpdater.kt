package com.example.musicsm.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.musicsm.domain.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app updater for the sideloaded build (there's no Play Store channel). Reads the latest GitHub
 * release, compares it to the installed version, and — if newer — downloads the APK via
 * [DownloadManager] and launches the system installer. Anything that goes wrong falls back to just
 * opening the release page in a browser, so the user can always get the update by hand.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {

    /** The newest release if it is strictly newer than what's installed, else null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = currentVersionName()
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Hymn")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val tag = json.optString("tag_name").ifBlank { return@use null }
                if (!isNewer(tag, current)) return@use null

                val assets = json.optJSONArray("assets") ?: return@use null
                val apkUrl = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    // The Wear OS app ships in the same release; a phone must never install it.
                    .firstOrNull { isPhoneApk(it.optString("name")) }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@use null

                UpdateInfo(
                    versionName = tag,
                    notes = json.optString("body"),
                    apkUrl = apkUrl,
                    releaseUrl = json.optString("html_url"),
                )
            }
        }.getOrNull()
    }

    /** Downloads the APK and opens the installer when it finishes; falls back to the browser. */
    fun downloadAndInstall(info: UpdateInfo) {
        val ok = runCatching {
            val manager = context.getSystemService(DownloadManager::class.java)
                ?: error("no DownloadManager")
            val fileName = "Hymn-${info.versionName}.apk"
            val request = DownloadManager.Request(info.apkUrl.toUri())
                .setTitle("Hymn ${info.versionName}")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            val downloadId = manager.enqueue(request)
            registerInstallOnComplete(manager, downloadId, info)
        }.isSuccess
        if (!ok) openInBrowser(info.releaseUrl)
    }

    private fun registerInstallOnComplete(manager: DownloadManager, downloadId: Long, info: UpdateInfo) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val finished = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (finished != downloadId) return
                runCatching { context.unregisterReceiver(this) }
                val uri = manager.getUriForDownloadedFile(downloadId)
                if (uri == null) {
                    openInBrowser(info.releaseUrl)
                    return
                }
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(install) }
                    .onFailure { openInBrowser(info.releaseUrl) }
            }
        }
        // ACTION_DOWNLOAD_COMPLETE is a system broadcast, so it must be registered as exported.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun openInBrowser(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** Compares dotted version numbers component-by-component, ignoring any non-digit prefix. */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }

    private companion object {
        const val REPO = "workingpayload/Hymn"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

/** A release asset that installs on a phone: an APK that isn't the Wear OS build. */
internal fun isPhoneApk(assetName: String): Boolean =
    assetName.endsWith(".apk", ignoreCase = true) && !assetName.contains("wear", ignoreCase = true)
