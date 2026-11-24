package com.pritom.nova

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

class NovaListener : NotificationListenerService() {

    companion object {
        /** conversation key -> reply action (works while the app process is alive) */
        val replyActions = ConcurrentHashMap<String, Notification.Action>()

        val APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.facebook.orca" to "Messenger",
            "com.facebook.mlite" to "Messenger Lite",
            "com.facebook.katana" to "Facebook",
            "com.facebook.lite" to "Facebook Lite",
            "com.instagram.android" to "Instagram",
            "org.telegram.messenger" to "Telegram",
            "com.imo.android.imoim" to "imo",
            "com.google.android.gm" to "Gmail",
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val app = APPS[sbn.packageName] ?: return
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val ex = n.extras
            val title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim() ?: return
            var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            @Suppress("DEPRECATION")
            val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (msgs != null && msgs.isNotEmpty()) {
                val last = msgs.last() as? Bundle
                val t = last?.getCharSequence("text")
                val sender = last?.getCharSequence("sender")?.toString()
                if (t != null) text = if (sender != null && sender != title) "$sender: $t" else t.toString()
            }
            text = text.trim()
            if (text.isEmpty() || title.isEmpty()) return
            if (Regex("^\\d+\\s+(new\\s+)?messages?", RegexOption.IGNORE_CASE).containsMatchIn(text)) return
            if (text.contains("নতুন মেসেজ") && text.length < 25) return

            val key = sbn.packageName + "|" + title
            n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }?.let { replyActions[key] = it }
            Store(this).addMessage(app, title, text, key)
        } catch (_: Exception) {
        }
    }
}
