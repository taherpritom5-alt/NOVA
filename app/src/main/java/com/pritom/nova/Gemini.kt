package com.pritom.nova

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {

    fun chat(s: Store, system: String, hist: List<Pair<String, String>>): String {
        if (s.apiKey.isBlank()) return "⚠️ এখনো API key বসানো হয়নি। উপরের ⚙ চেপে সেটিংসে বসাও।"

        val contents = JSONArray()
        var started = false
        for ((r, t) in hist.takeLast(24)) {
            if (!started && r != "user") continue
            started = true
            contents.put(
                JSONObject().put("role", if (r == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t)))
            )
        }

        val base = listOf(s.lastGood, s.model, s.backupModel).map { it.trim() }.filter { it.isNotEmpty() }
        var lastErr = "অজানা সমস্যা"
        var discovered = false
        val models = base.distinct().toMutableList()
        var idx = 0
        while (idx < models.size || !discovered) {
            if (idx >= models.size) {
                discovered = true
                try {
                    listModels(s.apiKey).forEach { if (it !in models) models.add(it) }
                } catch (_: Exception) { }
                if (idx >= models.size) break
            }
            val m = models[idx++]
            if (idx > 8) break
            val tries = if (s.search) listOf(true, false) else listOf(false)
            for (useSearch in tries) {
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", contents)
                    .put("generationConfig", JSONObject().put("temperature", 0.8))
                if (useSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                try {
                    val (code, resp) = post(m, s.apiKey, body.toString())
                    if (code == 200) {
                        val text = parse(resp)
                        if (text.isNotBlank()) {
                            s.lastGood = m
                            return text
                        }
                        lastErr = "খালি উত্তর এসেছে, আবার চেষ্টা করো।"
                        continue
                    }
                    lastErr = friendly(code, resp)
                    if (useSearch && code == 400) continue
                    if (resp.contains("API key", true) || code == 401 || code == 403) return "⚠️ $lastErr"
                    break
                } catch (e: Exception) {
                    lastErr = "ইন্টারনেট সংযোগে সমস্যা (${e.javaClass.simpleName})।"
                    break
                }
            }
        }
        return "⚠️ $lastErr"
    }

    private fun post(model: String, key: String, body: String): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        c.disconnect()
        return code to text
    }

    /** Ask Google which Gemini text models this key can use; lighter ones first. */
    private fun listModels(key: String): List<String> {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        c.setRequestProperty("x-goog-api-key", key)
        if (c.responseCode != 200) return emptyList()
        val js = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val arr = js.optJSONArray("models") ?: return emptyList()
        val bad = listOf("image", "tts", "audio", "live", "embedding", "vision", "robotics", "computer", "native")
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").removePrefix("models/")
            val methods = o.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            if (!methods.contains("generateContent")) continue
            if (!name.startsWith("gemini") || !name.contains("flash")) continue
            if (bad.any { name.contains(it) }) continue
            out.add(name)
        }
        return out.sortedWith(compareBy<String>({ if (it.contains("lite")) 0 else 1 }, { if (it.contains("preview") || it.contains("exp")) 1 else 0 }).thenByDescending { it })
    }

    private fun parse(resp: String): String {
        val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text", ""))
        }
        return sb.toString().trim()
    }

    private fun friendly(code: Int, resp: String): String {
        var msg = ""
        try {
            msg = (JSONObject(resp).optJSONObject("error")?.optString("message") ?: "").take(200)
        } catch (_: Exception) {
        }
        return when {
            code == 429 -> "কোনো মডেলেই এখন ফ্রি সীমা পাওয়া যাচ্ছে না। একটু পরে আবার চেষ্টা করো।\n(Google: ${msg.take(150)})"
            code == 404 -> "মডেলের নাম ভুল বা বন্ধ হয়ে গেছে। ⚙ সেটিংসে মডেলের নাম বদলাও। ($msg)"
            msg.contains("API key", true) || code == 401 || code == 403 -> "API key সঠিক নয়। ⚙ সেটিংসে আবার বসাও।"
            code >= 500 -> "Google-এর সার্ভার এখন ব্যস্ত, একটু পরে চেষ্টা করো।"
            else -> "সমস্যা হয়েছে ($code): $msg"
        }
    }
}
