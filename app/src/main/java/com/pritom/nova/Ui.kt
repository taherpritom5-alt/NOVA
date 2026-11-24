package com.pritom.nova

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView

object Ui {
    const val BG = 0xFF0F1020.toInt()
    const val CARD = 0xFF1E2140.toInt()
    const val USER = 0xFF6C5CE7.toInt()
    const val ACCENT = 0xFFA29BFE.toInt()
    const val TEXT = 0xFFF1F1F6.toInt()
    const val SUB = 0xFF9A9CC0.toInt()
    const val GREEN = 0xFF27AE60.toInt()
    const val RED = 0xFFC0392B.toInt()

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun round(color: Int, r: Float, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = r
        if (strokeW > 0) setStroke(strokeW, stroke)
    }

    fun button(ctx: Context, text: String, color: Int = USER, onClick: () -> Unit) = Button(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        isAllCaps = false
        textSize = 15f
        background = round(color, dp(ctx, 20).toFloat())
        setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        setOnClickListener { onClick() }
    }

    fun spinnerAdapter(ctx: Context, items: List<String>): ArrayAdapter<String> {
        val ad = object : ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(TEXT)
                v.textSize = 14f
                return v
            }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return ad
    }
}
