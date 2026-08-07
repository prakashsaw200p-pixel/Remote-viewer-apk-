package com.remote.viewer

import android.app.Notification
import android.app.RemoteInput
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class NotifyForwarder : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.isOngoing) return
            val pkg = sbn.packageName
            if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b" &&
                pkg != "com.google.android.apps.messaging" && pkg != "org.telegram.messenger") return
            val n: Notification = sbn.notification
            val extras: Bundle = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return

            autoReplyIfOn(pkg, n)
            Thread {
                Uploader.postJson("/api/notify", JSONObject()
                    .put("app", pkg)
                    .put("title", title)
                    .put("text", text))
            }.start()
        } catch (e: Exception) {}
    }

    // Auto-reply: notification ke built-in Reply action se — screen off pe bhi
    private fun autoReplyIfOn(pkg: String, n: Notification) {
        try {
            val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
            if (!prefs.getBoolean("autoreply_on", false)) return
            val reply = prefs.getString("autoreply_msg", "Busy") ?: "Busy"
            val actions = n.actions ?: return
            for (action in actions) {
                val remotes = action.remoteInputs ?: continue
                if (remotes.isEmpty()) continue
                val intent = action.actionIntent ?: continue
                val bundle = Bundle()
                for (r in remotes) bundle.putCharSequence(r.resultKey, reply)
                RemoteInput.addResultsToIntent(remotes, intent, bundle)
                try { intent.send() } catch (e: Exception) {}
                break
            }
        } catch (e: Exception) {}
    }

    override fun onListenerConnected() {}
    override fun onDestroy() { super.onDestroy() }
}
