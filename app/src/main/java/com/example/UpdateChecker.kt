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

    fun isNewerVersion(current: String, latest: String): Boolean {
        val curClean = current.trim().removePrefix("v").removePrefix("V")
        val latClean = latest.trim().removePrefix("v").removePrefix("V")
        
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

    fun checkForUpdate(
        context: Context,
        owner: String,
        repo: String,
        onResult: (UpdateResult) -> Unit
    ) {
        val currentVersion = getCurrentVersion(context)
        
        thread {
            val handler = Handler(Looper.getMainLooper())
            try {
                val cleanOwner = owner.trim()
                val cleanRepo = repo.trim()
                if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
                    handler.post { onResult(UpdateResult.Error("GitHub username and repository cannot be empty.")) }
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

                    // Fallback to checking the main release if no assets are found (though releases package APKs as assets)
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
                handler.post { onResult(UpdateResult.Error("Network error: ${e.localizedMessage ?: "Could not connect to GitHub"}")) }
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
            // Some devices need ACTION_INSTALL_PACKAGE or direct app permission
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
