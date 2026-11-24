package com.pritom.nova

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Routes a chat to the engine chosen in settings. */
object Brain {
    val engines = listOf("Gemini (Google)", "OpenRouter (ফ্রি DeepSeek)", "DeepSeek (সরাসরি)")

    fun chat(s: Store, system: String, hist: List<Pair<String, String>>): String = when (s.engine) {
        1 -> openAi(
            s, "https://openrouter.ai/api/v1", s.orKey,
            listOf(s.orModel, "deepseek/deepseek-v4-flash:free", "deepseek/deepseek-chat-v3.1:free"),
            system, hist, "OpenRouter"
        )
        2 -> openAi(
            s, "https://api.deepseek.com", s.dsKey,
            listOf(s.dsModel, "deepseek-flash", "deepseek-v4-flash", "deepseek-chat"),
            system, hist, "DeepSeek"
        )
        else -> Gemini.chat(s, system, hist)
    }

    private fun openAi(
        s: Store, base: String, key: String, candidates: List<String>,
        system: String, hist: List<Pair<String, String>>, name: String
    ): String {
        if (key.isBlank()) return "⚠️ $name-এর API key বসানো হয়নি। ⚙ সেটিংসে বসাও।"
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        var started = false
        for ((r, t) in hist.takeLast(24)) {
            if (!started && r != "user") continue
            started = true
            msgs.put(JSONObject().put("role", if (r == "user") "user" else "assistant").put("content", t))
        }
        val models = candidates.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
        if (name == "DeepSeek") try { listDeepSeek(base, key).forEach { if (it !in models) models.add(it) } } catch (_: Exception) { }

        var lastErr = "অজানা সমস্যা"
        for (m in models.take(6)) {
            try {
                val body = JSONObject().put("model", m).put("messages", msgs).put("temperature", 0.8)
                val c = URL("$base/chat/completions").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 20000
                c.readTimeout = 180000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setRequestProperty("Authorization", "Bearer $key")
                c.setRequestProperty("X-Title", "Nova")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                c.disconnect()
                if (code == 200) {
                    val out = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content")?.trim() ?: ""
                    if (out.isNotBlank()) return out
                    lastErr = "খালি উত্তর এসেছে, আবার চেষ্টা করো।"
                    continue
                }
                var msg = ""
                try { msg = (JSONObject(text).optJSONObject("error")?.optString("message") ?: "").take(150) } catch (_: Exception) { }
                lastErr = when (code) {
                    401 -> "$name-এর API key সঠিক নয়। ⚙ সেটিংসে আবার বসাও।"
                    402 -> "$name অ্যাকাউন্টে টাকা নেই, রিচার্জ করতে হবে।"
                    429 -> "$name-এ এখন সীমা শেষ বা খুব ব্যস্ত। একটু পরে চেষ্টা করো। ($msg)"
                    else -> "$name-এ সমস্যা ($code): $msg"
                }
                if (code == 401 || code == 402) return "⚠️ $lastErr"
            } catch (e: Exception) {
                lastErr = "ইন্টারনেট সংযোগে সমস্যা (${e.javaClass.simpleName})।"
            }
        }
        return "⚠️ $lastErr"
    }

    private fun listDeepSeek(base: String, key: String): List<String> {
        val c = URL("$base/models").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Authorization", "Bearer $key")
        if (c.responseCode != 200) return emptyList()
        val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data") ?: return emptyList()
        val ids = (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }
        return ids.sortedBy { if (it.contains("flash")) 0 else 1 }
    }
}
