package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

sealed class UpdateResult {
    data class NewVersionAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String
    ) : UpdateResult()
    data class UpToDate(val currentVersion: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object UpdateChecker {

    const val INDUS_APPSTORE_PACKAGE = "com.indus.appstore"
    const val DEFAULT_GITHUB_OWNER = "hariom2008h"
    const val DEFAULT_GITHUB_REPO = "Block-scroll"
    const val WEBSITE_VERSION_URL = "https://hariom2008h.github.io/Shorts-blocker-pages/version.json"
    private const val PREFS_NAME = "shorts_blocker_prefs"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check_ms"
    private const val KEY_DISMISSED_VERSION = "dismissed_update_version"
    private const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 hours

    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun isInstalledFromIndusAppstore(context: Context): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer != null && (installer == INDUS_APPSTORE_PACKAGE || installer.contains("indus", ignoreCase = true))
        } catch (e: Exception) {
            false
        }
    }

    fun isIndusAppstoreInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(INDUS_APPSTORE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(INDUS_APPSTORE_PACKAGE, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openInIndusAppstore(context: Context): Boolean {
        val pkg = context.packageName
        val candidateIntents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                setPackage(INDUS_APPSTORE_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.indusappstore.com/apps/details?id=$pkg")).apply {
                setPackage(INDUS_APPSTORE_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.packageManager.getLaunchIntentForPackage(INDUS_APPSTORE_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.indusappstore.com/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ).filterNotNull()

        for (intent in candidateIntents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try next fallback intent
            }
        }
        return false
    }

    fun dismissUpdateVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_VERSION, version)
            .apply()
    }

    fun checkForUpdateOnLaunch(
        context: Context,
        onUpdateAvailable: (UpdateResult.NewVersionAvailable) -> Unit
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, now).apply()

        checkForUpdate(context, DEFAULT_GITHUB_OWNER, DEFAULT_GITHUB_REPO) { result ->
            if (result is UpdateResult.NewVersionAvailable) {
                val dismissed = prefs.getString(KEY_DISMISSED_VERSION, "") ?: ""
                if (dismissed != result.latestVersion) {
                    onUpdateAvailable(result)
                }
            }
        }
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val curClean = current.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val latClean = latest.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        
        val curParts = curClean.split(".")
        val latParts = latClean.split(".")
        
        val maxLength = maxOf(curParts.size, latParts.size)
        for (i in 0 until maxLength) {
            val curNum = curParts.getOrNull(i)?.toIntOrNull() ?: 0
            val latNum = latParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (latNum > curNum) return true
            if (curNum > latNum) return false
        }
        return false
    }

    private fun tryFetchWebsiteVersion(currentVersion: String): UpdateResult.NewVersionAvailable? {
        return try {
            val conn = (URL(WEBSITE_VERSION_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Android-ShortsBlocker-UpdateChecker")
                connectTimeout = 4000
                readTimeout = 4000
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                val latestVersion = json.optString("latestVersion", json.optString("version", ""))
                val releaseNotes = json.optString("releaseNotes", json.optString("notes", "New update available on Indus Appstore!"))
                val downloadUrl = json.optString("downloadUrl", "")
                if (latestVersion.isNotBlank() && isNewerVersion(currentVersion, latestVersion)) {
                    UpdateResult.NewVersionAvailable(currentVersion, latestVersion, downloadUrl, releaseNotes)
                } else {
                    null
                }
            } else {
                conn.disconnect()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun checkForUpdate(
        context: Context,
        owner: String = DEFAULT_GITHUB_OWNER,
        repo: String = DEFAULT_GITHUB_REPO,
        onResult: (UpdateResult) -> Unit
    ) {
        val currentVersion = getCurrentVersion(context)
        
        thread {
            val handler = Handler(Looper.getMainLooper())
            try {
                // 1. Check optional website version.json (if hosted on GitHub Pages for instant Indus Appstore version bumps)
                val websiteUpdate = tryFetchWebsiteVersion(currentVersion)
                if (websiteUpdate != null) {
                    handler.post { onResult(websiteUpdate) }
                    return@thread
                }

                // 2. Check GitHub Releases API
                val cleanOwner = owner.trim()
                val cleanRepo = repo.trim()
                if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
                    handler.post { onResult(UpdateResult.Error("Repository cannot be empty.")) }
                    return@thread
                }

                val apiUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest"
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", "Android-ShortsBlocker-UpdateChecker")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val stream = conn.inputStream
                    val responseText = stream.bufferedReader().use { it.readText() }
                    
                    val json = JSONObject(responseText)
                    val latestVersion = json.getString("tag_name")
                    val releaseNotes = json.optString("body", "No release notes provided.")
                    
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    
                    // Look for the first asset that ends with .apk
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    // Fallback to checking the main release if no assets are found
                    if (downloadUrl.isEmpty()) {
                        downloadUrl = json.optString("html_url", "")
                    }

                    handler.post {
                        if (isNewerVersion(currentVersion, latestVersion)) {
                            onResult(UpdateResult.NewVersionAvailable(currentVersion, latestVersion, downloadUrl, releaseNotes))
                        } else {
                            onResult(UpdateResult.UpToDate(currentVersion))
                        }
                    }
                } else {
                    handler.post { onResult(UpdateResult.Error("No release found or repository not accessible (Error $responseCode).")) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { onResult(UpdateResult.Error("Network error: ${e.localizedMessage ?: "Could not check for updates"}")) }
            }
        }
    }

    fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            val handler = Handler(Looper.getMainLooper())
            try {
                if (downloadUrl.isBlank()) {
                    handler.post { onError("No direct APK download link provided. Please update via Indus Appstore.") }
                    return@thread
                }
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                
                // Handle redirection if any
                var redirectConn = conn
                var status = conn.responseCode
                var redirectCount = 0
                while (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                       status == HttpURLConnection.HTTP_MOVED_PERM || 
                       status == HttpURLConnection.HTTP_SEE_OTHER) {
                    
                    if (redirectCount > 5) break
                    val newUrl = redirectConn.getHeaderField("Location")
                    redirectConn.disconnect()
                    
                    val nextUrl = URL(newUrl)
                    redirectConn = nextUrl.openConnection() as HttpURLConnection
                    redirectConn.connectTimeout = 15000
                    redirectConn.readTimeout = 15000
                    status = redirectConn.responseCode
                    redirectCount++
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    handler.post { onError("Could not download file (Server returned $status)") }
                    redirectConn.disconnect()
                    return@thread
                }

                val fileLength = redirectConn.contentLength
                val cacheFile = File(context.cacheDir, "shorts_blocker_update.apk")
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                val input = BufferedInputStream(redirectConn.inputStream)
                val output = FileOutputStream(cacheFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        handler.post { onProgress(((total * 100) / fileLength).toInt()) }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()
                redirectConn.disconnect()

                handler.post {
                    installApk(context, cacheFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { onError("Download failed: ${e.localizedMessage ?: "Network error"}") }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val uri = Uri.fromFile(file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
