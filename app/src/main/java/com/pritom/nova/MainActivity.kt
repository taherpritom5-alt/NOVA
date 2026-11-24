package com.pritom.nova

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var s: Store
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private val h = Handler(Looper.getMainLooper())
    private var busy = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        buildUi()
        for ((r, t) in s.history().takeLast(30)) {
            val shown = if (r == "user") t else Actions.parse(t).first
            if (shown.isNotBlank()) bubble(shown, r == "user")
        }
        when {
            s.apiKey.isBlank() -> bubble(
                "হ্যালো প্রিতম! আমি Nova 💜\nশুরু করতে উপরের ⚙ চেপে তোমার Gemini API key বসাও। মেসেজ পড়তে দিতে চাইলে সেখান থেকে Notification access-ও চালু করো।",
                false
            )
            s.history().isEmpty() -> bubble("হ্যালো প্রিতম! আমি Nova 💜 বলো, আজ কী করব?", false)
        }
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) applyVoice() }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
    }

    override fun onResume() {
        super.onResume()
        applyVoice()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }

        // top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(8), dp(10))
        }
        top.addView(TextView(this).apply {
            text = "✨ Nova"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.ACCENT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val spinner = Spinner(this).apply {
            adapter = Ui.spinnerAdapter(this@MainActivity, Prompt.roles)
            setSelection(s.role)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { s.role = pos }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        top.addView(spinner)
        top.addView(iconBtn("📩") { askSummary() })
        top.addView(iconBtn("⚙") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(top)

        // chat area
        scroll = ScrollView(this)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(12))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // input bar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(12))
        }
        input = EditText(this).apply {
            hint = "Nova-কে কিছু বলো…"
            setHintTextColor(Ui.SUB)
            setTextColor(Ui.TEXT)
            textSize = 16f
            maxLines = 5
            background = Ui.round(Ui.CARD, dp(22).toFloat())
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(circleBtn("🎤") { listen() })
        bar.addView(circleBtn("➤") { send(input.text.toString()) })
        root.addView(bar)

        setContentView(root)
    }

    private fun iconBtn(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 22f
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClick() }
    }

    private fun circleBtn(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Ui.TEXT)
        background = Ui.round(Ui.USER, dp(24).toFloat())
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun bubble(text: String, user: Boolean): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Ui.TEXT)
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
            maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
            background = Ui.round(if (user) Ui.USER else Ui.CARD, dp(18).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        chat.addView(tv, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = if (user) Gravity.END else Gravity.START
            topMargin = dp(8)
        })
        scrollDown()
        return tv
    }

    private fun note(text: String) {
        chat.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Ui.SUB)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }, LinearLayout.LayoutParams(-1, -2))
        scrollDown()
    }

    private fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        tts?.stop()
        bubble(text, true)
        s.addHistory("user", text)
        input.setText("")
        busy = true
        val thinking = bubble("Nova ভাবছে…", false).apply { alpha = 0.6f }
        val sys = Prompt.build(s)
        val hist = s.history()
        Thread {
            val reply = Gemini.chat(s, sys, hist)
            h.post {
                chat.removeView(thinking)
                busy = false
                handleReply(reply)
            }
        }.start()
    }

    private fun handleReply(raw: String) {
        val (text, acts) = Actions.parse(raw)
        val shown = if (text.isBlank() && acts.isNotEmpty()) "ঠিক আছে, নিচে দেখো 👇" else text
        if (shown.isNotBlank()) bubble(shown, false)
        if (!raw.startsWith("⚠️")) s.addHistory("model", raw.trim())
        speak(shown)
        for (a in acts) {
            if (a.optString("type") == "remember") {
                val f = a.optString("fact")
                if (f.isNotBlank()) {
                    s.addFact(f)
                    note("💾 মনে রাখলাম: $f")
                }
            } else actionCard(a)
        }
    }

    private fun actionCard(a: JSONObject) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(Ui.CARD, dp(16).toFloat(), Ui.ACCENT, dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(TextView(this).apply {
            text = "🔔 " + Actions.describe(a, s)
            textSize = 15f
            setTextColor(Ui.TEXT)
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        fun finish(msg: String) {
            card.removeAllViews()
            card.addView(TextView(this).apply {
                text = msg
                textSize = 14f
                setTextColor(Ui.SUB)
            })
        }
        row.addView(Ui.button(this, "✅ হ্যাঁ, করো", Ui.GREEN) {
            finish(Actions.execute(this, a, s))
        })
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        row.addView(Ui.button(this, "❌ না", Ui.RED) { finish("বাতিল করা হলো।") })
        card.addView(row)
        chat.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        scrollDown()
    }

    private fun askSummary() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        if (!enabled) {
            note("📩 মেসেজ পড়তে হলে আগে Notification access চালু করতে হবে। ⚙ সেটিংসে গিয়ে চালু করো।")
            return
        }
        send("আমার নতুন মেসেজগুলোর সামারি দাও")
    }

    // ---------- voice ----------
    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "বলো, শুনছি…")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, 11)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "ফোনে Google ভয়েস টাইপিং পাওয়া যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }

    private fun applyVoice() {
        val t = tts ?: return
        try {
            val r = t.setLanguage(Locale("bn", "BD"))
            if (r < 0) t.setLanguage(Locale("bn", "IN"))
            val voices = t.voices?.filter { it.locale.language == "bn" || it.locale.language == "ben" } ?: emptyList()
            val v = voices.firstOrNull { it.name == s.voiceName }
                ?: voices.firstOrNull { it.name.contains("female", true) }
                ?: voices.firstOrNull { !it.isNetworkConnectionRequired }
                ?: voices.firstOrNull()
            if (v != null) t.voice = v
        } catch (_: Exception) {
        }
    }

    private fun speak(text: String) {
        if (!s.speak || text.isBlank() || text.startsWith("⚠️")) return
        val t = tts ?: return
        val clean = text.replace(Regex("[*#_`>•]"), " ")
        val parts = clean.split(Regex("(?<=[।?!\\n])"))
        val sb = StringBuilder()
        var first = true
        fun flush() {
            if (sb.isBlank()) return
            t.speak(sb.toString(), if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "nova${System.nanoTime()}")
            first = false
            sb.clear()
        }
        for (p in parts) {
            if (sb.length + p.length > 3000) flush()
            sb.append(p)
        }
        flush()
    }
}
