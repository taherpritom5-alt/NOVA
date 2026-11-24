package com.pritom.nova

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var s: Store
    private lateinit var col: LinearLayout
    private lateinit var voiceSpinner: Spinner
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var voiceNames = listOf<String>()

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG

        val scroll = ScrollView(this).apply { setBackgroundColor(Ui.BG) }
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        scroll.addView(col)

        col.addView(TextView(this).apply {
            text = "⚙ Nova সেটিংস"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.ACCENT)
        })

        label("🔑 Gemini API key")
        val key = field(s.apiKey, "এখানে API key পেস্ট করো", password = true)
        col.addView(Ui.button(this, "API key কোথায় পাব?", Ui.CARD) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
        }, wrap())

        label("🧠 মূল মডেল")
        val model = field(s.model, "gemini-flash-lite-latest")
        label("🧠 বিকল্প মডেল (মূলটা কাজ না করলে)")
        val backup = field(s.backupModel, "gemini-flash-latest")

        label("👤 আমার সম্পর্কে")
        val about = field(s.aboutMe, "", multi = true)
        label("💾 Nova যা মনে রেখেছে")
        val facts = field(s.facts, "(খালি)", multi = true)

        label("🔊 গলা")
        val speak = switch("উত্তর মুখে বলে শোনাবে", s.speak)
        voiceSpinner = Spinner(this)
        col.addView(voiceSpinner)
        val vRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        vRow.addView(Ui.button(this, "▶ শুনে দেখো") { testVoice() })
        vRow.addView(TextView(this), LinearLayout.LayoutParams(dp(10), 1))
        vRow.addView(Ui.button(this, "⬇ বাংলা ভয়েস নামাও", Ui.CARD) {
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (_: Exception) {
                toast("ফোনের Settings থেকে Text-to-speech-এ গিয়ে বাংলা ভয়েস নামাও")
            }
        })
        col.addView(vRow, wrap())

        label("🌐 ইন্টারনেট")
        val search = switch("Google সার্চ করে উত্তর দেবে (সব মডেলে কাজ নাও করতে পারে)", s.search)

        label("📩 মেসেজ পড়ার অনুমতি")
        status = TextView(this).apply { setTextColor(Ui.SUB); textSize = 14f }
        col.addView(status)
        col.addView(Ui.button(this, "Notification access চালু করো") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, wrap())

        label("🧹 পরিষ্কার")
        val cRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cRow.addView(Ui.button(this, "চ্যাট মুছে ফেলো", Ui.CARD) { s.clearHistory(); toast("চ্যাট মুছে ফেলা হলো") })
        cRow.addView(TextView(this), LinearLayout.LayoutParams(dp(10), 1))
        cRow.addView(Ui.button(this, "জমা মেসেজ মুছে ফেলো", Ui.CARD) { s.clearMessages(); toast("মেসেজ মুছে ফেলা হলো") })
        col.addView(cRow, wrap())

        col.addView(TextView(this), LinearLayout.LayoutParams(1, dp(20)))
        col.addView(Ui.button(this, "💾 সেভ করো", Ui.GREEN) {
            s.apiKey = key.text.toString()
            s.model = model.text.toString().ifBlank { "gemini-flash-lite-latest" }
            s.backupModel = backup.text.toString()
            s.aboutMe = about.text.toString()
            s.facts = facts.text.toString()
            s.speak = speak.isChecked
            s.search = search.isChecked
            val i = voiceSpinner.selectedItemPosition
            if (i in voiceNames.indices) s.voiceName = voiceNames[i]
            toast("সেভ হয়েছে ✅")
            finish()
        }, LinearLayout.LayoutParams(-1, -2))

        setContentView(scroll)

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("bn", "BD"))
                voiceNames = tts?.voices
                    ?.filter { it.locale.language == "bn" || it.locale.language == "ben" }
                    ?.map { it.name }?.sorted() ?: emptyList()
                val items = if (voiceNames.isEmpty()) listOf("বাংলা ভয়েস পাওয়া যায়নি — নামাও") else voiceNames
                voiceSpinner.adapter = Ui.spinnerAdapter(this, items)
                val idx = voiceNames.indexOf(s.voiceName)
                if (idx >= 0) voiceSpinner.setSelection(idx)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val on = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        status.text = if (on) "✅ চালু আছে — নতুন মেসেজ Nova পড়তে পারবে"
        else "❌ বন্ধ আছে। নিচের বোতাম চেপে Nova-কে চালু করো।"
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun testVoice() {
        val t = tts ?: return
        val i = voiceSpinner.selectedItemPosition
        if (i in voiceNames.indices) t.voices?.firstOrNull { it.name == voiceNames[i] }?.let { t.voice = it }
        t.speak("হ্যালো প্রিতম, আমি নোভা। আমার গলা কেমন লাগছে?", TextToSpeech.QUEUE_FLUSH, null, "test")
    }

    private fun wrap() = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) }

    private fun label(t: String) {
        col.addView(TextView(this).apply {
            text = t
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.ACCENT)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(6) })
    }

    private fun field(v: String, hint: String, multi: Boolean = false, password: Boolean = false): EditText {
        val e = EditText(this).apply {
            setText(v)
            this.hint = hint
            setHintTextColor(Ui.SUB)
            setTextColor(Ui.TEXT)
            textSize = 15f
            gravity = Gravity.TOP or Gravity.START
            background = Ui.round(Ui.CARD, dp(12).toFloat())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multi) minLines = 3
        }
        col.addView(e, LinearLayout.LayoutParams(-1, -2))
        return e
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun switch(t: String, checked: Boolean): Switch {
        val sw = Switch(this).apply {
            text = t
            isChecked = checked
            setTextColor(Ui.TEXT)
            textSize = 14f
        }
        col.addView(sw, LinearLayout.LayoutParams(-1, -2))
        return sw
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
