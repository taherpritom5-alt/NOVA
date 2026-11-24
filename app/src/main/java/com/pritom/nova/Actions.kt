package com.pritom.nova

import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Actions {
    private val apps = mapOf(
        "whatsapp" to "com.whatsapp", "messenger" to "com.facebook.orca",
        "facebook" to "com.facebook.katana", "youtube" to "com.google.android.youtube",
        "gmail" to "com.google.android.gm", "chrome" to "com.android.chrome",
        "telegram" to "org.telegram.messenger", "instagram" to "com.instagram.android"
    )

    /** Splits the model reply into visible text and ACTION objects. */
    fun parse(raw: String): Pair<String, List<JSONObject>> {
        val acts = mutableListOf<JSONObject>()
        val keep = StringBuilder()
        for (line in raw.lines()) {
            val t = line.trim().trim('`').trim()
            if (t.startsWith("ACTION")) {
                val js = t.removePrefix("ACTION").trim().removePrefix(":").trim()
                try { acts.add(JSONObject(js)) } catch (_: Exception) { }
            } else keep.append(line).append('\n')
        }
        val text = keep.toString().replace("**", "").replace("```", "")
            .replace(Regex("(?m)^#+\\s*"), "").trim()
        return text to acts
    }

    fun describe(a: JSONObject, s: Store): String {
        val text = a.optString("text")
        return when (a.optString("type")) {
            "reply" -> {
                val m = s.findMessage(a.optInt("id"))
                if (m == null) "এই উত্তরটা পাঠাব:\n“$text”"
                else "${m.optString("app")}-এ ${m.optString("title")}-কে উত্তর পাঠাব:\n“$text”"
            }
            "whatsapp" -> "WhatsApp-এ এই মেসেজটা পাঠানোর জন্য খুলব:\n“$text”"
            "reminder" -> "${a.optInt("minutes")} মিনিট পরে মনে করিয়ে দেব:\n“$text”"
            "open_app" -> "${a.optString("app")} খুলব"
            "open_url" -> "এই লিংকটা খুলব:\n${a.optString("url")}"
            else -> "অজানা কাজ: $a"
        }
    }

    fun execute(ctx: Context, a: JSONObject, s: Store): String = try {
        when (a.optString("type")) {
            "reply" -> reply(ctx, a, s)
            "whatsapp" -> {
                val text = a.optString("text")
                val phone = a.optString("phone").filter { it.isDigit() }
                val i = if (phone.isNotEmpty())
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=" + Uri.encode(text)))
                else Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text).setPackage("com.whatsapp")
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "✅ WhatsApp খুলেছি, এখন শুধু পাঠাও চাপো।"
            }
            "reminder" -> {
                val at = ReminderReceiver.schedule(ctx, a.optInt("minutes", 10), a.optString("text"))
                "✅ রিমাইন্ডার সেট হয়েছে: " + SimpleDateFormat("h:mm a", Locale.US).format(Date(at))
            }
            "open_app" -> {
                val pkg = apps[a.optString("app").lowercase()] ?: a.optString("app")
                val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    ?: return "⚠️ অ্যাপটা ফোনে পাইনি।"
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "✅ খুলছি।"
            }
            "open_url" -> {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.optString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "✅ লিংক খুলছি।"
            }
            else -> "⚠️ এই কাজটা এখনো পারি না।"
        }
    } catch (e: Exception) {
        "⚠️ করা গেল না: ${e.message}"
    }

    private fun reply(ctx: Context, a: JSONObject, s: Store): String {
        val text = a.optString("text")
        val m = s.findMessage(a.optInt("id")) ?: return "⚠️ মেসেজটা খুঁজে পাইনি।"
        val key = m.optString("key")
        val action = NovaListener.replyActions[key]
        if (action == null) {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Nova", text))
            ctx.packageManager.getLaunchIntentForPackage(key.substringBefore("|"))?.let {
                ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return "⚠️ সরাসরি উত্তর দেওয়া গেল না (মেসেজ আসার পর Nova বন্ধ হয়ে গিয়েছিল)। লেখাটা কপি করে অ্যাপটা খুলে দিলাম, পেস্ট করে পাঠাও।"
        }
        val intent = Intent()
        val bundle = Bundle()
        action.remoteInputs.forEach { bundle.putCharSequence(it.resultKey, text) }
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
        action.actionIntent.send(ctx, 0, intent)
        return "✅ ${m.optString("title")}-কে উত্তর পাঠানো হয়েছে।"
    }
}
