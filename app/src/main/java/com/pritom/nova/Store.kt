package com.pritom.nova

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("nova", Context.MODE_PRIVATE)

    private fun str(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()

    var apiKey: String
        get() = str("apiKey").trim()
        set(v) = put("apiKey", v.trim())
    var model: String
        get() = str("model", "gemini-flash-lite-latest")
        set(v) = put("model", v.trim())
    var backupModel: String
        get() = str("backupModel", "gemini-flash-latest")
        set(v) = put("backupModel", v.trim())
    var lastGood: String
        get() = str("lastGood")
        set(v) = put("lastGood", v)
    var aboutMe: String
        get() = str(
            "aboutMe",
            "আমার নাম প্রিতম। রংপুর মেডিকেল কলেজে MBBS ৫ম বর্ষে পড়ি। পাইপ ক্লিনার ও ফুল বানানোর ক্রাফট সামগ্রীর ছোট একটা ব্যবসা আছে। একটা ফেসবুক পেজে বাংলায় স্বাস্থ্য-বিষয়ক ভিডিও বানাই।"
        )
        set(v) = put("aboutMe", v)
    var facts: String
        get() = str("facts")
        set(v) = put("facts", v)
    var voiceName: String
        get() = str("voiceName")
        set(v) = put("voiceName", v)
    var speak: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()
    var search: Boolean
        get() = p.getBoolean("search", false)
        set(v) = p.edit().putBoolean("search", v).apply()
    var role: Int
        get() = p.getInt("role", 0)
        set(v) = p.edit().putInt("role", v).apply()

    fun addFact(f: String) {
        facts = (facts.trim() + "\n• " + f.trim()).trim()
    }

    // ---------- chat history ----------
    fun history(): List<Pair<String, String>> {
        val a = JSONArray(str("history", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            o.getString("r") to o.getString("t")
        }
    }

    @Synchronized
    fun addHistory(r: String, t: String) {
        val a = JSONArray(str("history", "[]"))
        a.put(JSONObject().put("r", r).put("t", t))
        val out = JSONArray()
        for (i in maxOf(0, a.length() - 60) until a.length()) out.put(a.get(i))
        p.edit().putString("history", out.toString()).apply()
    }

    fun clearHistory() = put("history", "[]")

    // ---------- captured messages ----------
    fun messages(): List<JSONObject> {
        val a = JSONArray(str("msgs", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    @Synchronized
    fun addMessage(app: String, title: String, text: String, key: String) {
        val a = JSONArray(str("msgs", "[]"))
        for (i in maxOf(0, a.length() - 15) until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("key") == key && o.optString("text") == text) return
        }
        val id = p.getInt("msgId", 0) + 1
        a.put(
            JSONObject().put("id", id).put("app", app).put("title", title)
                .put("text", text).put("key", key).put("time", System.currentTimeMillis())
        )
        val out = JSONArray()
        for (i in maxOf(0, a.length() - 150) until a.length()) out.put(a.get(i))
        p.edit().putString("msgs", out.toString()).putInt("msgId", id).commit()
    }

    fun findMessage(id: Int): JSONObject? = messages().lastOrNull { it.optInt("id") == id }

    fun clearMessages() = put("msgs", "[]")

    fun recentMessagesText(limit: Int = 40): String {
        val f = SimpleDateFormat("d MMM h:mm a", Locale.US)
        return messages().takeLast(limit).joinToString("\n") {
            "#${it.getInt("id")} [${it.getString("app")}] ${it.getString("title")}: " +
                "${it.getString("text").take(300)} (${f.format(Date(it.getLong("time")))})"
        }
    }
}
