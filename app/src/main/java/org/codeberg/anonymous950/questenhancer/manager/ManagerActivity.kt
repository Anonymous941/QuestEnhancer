package org.codeberg.anonymous950.questenhancer.manager

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Quest 1 Manager, laid out like Quest Settings: areas on the left, rows on the right.
 * Read only for now: switches show the state read from the headset and can't be flipped.
 */
class ManagerActivity : Activity() {
    private val bg = Color.rgb(0x1C, 0x1E, 0x21)
    private val panel = Color.rgb(0x26, 0x29, 0x2D)
    private val selected = Color.rgb(0x3A, 0x3E, 0x44)
    private val dim = Color.rgb(0xA8, 0xAD, 0xB4)

    private var tweaks: List<Tweak> = emptyList()
    private val states = mutableMapOf<String, RootShell.Result>()
    private lateinit var nav: LinearLayout
    private lateinit var content: LinearLayout
    private var area = ""
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(bg)
        tweaks = Catalog.load(this)
        area = savedInstanceState?.getString("area") ?: tweaks.firstOrNull()?.area ?: ""

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(12))
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ScrollView(this@ManagerActivity).apply {
                setBackgroundColor(panel)
                addView(nav)
            }, LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(ScrollView(this@ManagerActivity).apply { addView(content) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        setContentView(root)
        drawNav()
        drawContent()
        refresh(retryRoot = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("area", area)
    }

    private fun drawNav() {
        nav.removeAllViews()
        nav.addView(label("Quest Enhancer", 20f, Color.WHITE, bold = true).apply {
            setPadding(dp(12), 0, dp(12), dp(16))
        })
        for (a in tweaks.map { it.area }.distinct()) {
            nav.addView(label(a, 16f, Color.WHITE).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = rounded(if (a == area) selected else Color.TRANSPARENT)
                setOnClickListener { area = a; drawNav(); drawContent() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            })
        }
        nav.addView(label("Refresh", 16f, dim).apply {
            setPadding(dp(16), dp(24), dp(16), dp(12))
            setOnClickListener { refresh(retryRoot = true) }
        })
    }

    private fun drawContent() {
        content.removeAllViews()
        content.addView(label(area, 24f, Color.WHITE, bold = true).apply { setPadding(0, 0, 0, dp(4)) })
        content.addView(label(
            if (loading) "Reading from the headset..." else "Read only: shows what is set on this headset.",
            13f, dim).apply { setPadding(0, 0, 0, dp(16)) })
        for (t in tweaks.filter { it.area == area }) content.addView(row(t))
    }

    private fun row(t: Tweak): View {
        val r = states[t.id]
        // No switch for a check that failed (root refused, timed out): its state is unknown.
        val on = r?.takeIf { it.code == 0 }?.let { t.onIf?.containsMatchIn(it.output) }
        val status = when {
            r == null -> "Checking..."
            r.code != 0 -> r.output.ifEmpty { "Couldn't read (exit ${r.code})" }
            on == null -> r.output.lines().joinToString(" · ")
            on -> "On"
            else -> "Off"
        }
        val details = label(buildString {
            append(t.summary).append("\n\n")
            append("By ").append(t.author).append(" · ").append(t.licence).append(" · risk: ").append(t.risk)
            if (t.url.isNotEmpty()) append('\n').append(t.url)
            if (r != null && r.output.isNotEmpty()) append("\n\n").append(r.output)
        }, 13f, dim).apply { visibility = View.GONE; setPadding(0, dp(8), 0, 0) }

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(t.name, 17f, Color.WHITE))
            addView(label(status, 13f, dim))
            addView(details)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = rounded(panel)
            addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (on != null) addView(Switch(this@ManagerActivity).apply {
                isChecked = on
                isEnabled = false
            })
            setOnClickListener {
                details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    /** All checks on one background thread through the one root shell. */
    private fun refresh(retryRoot: Boolean) {
        if (loading) return
        loading = true
        states.clear()
        drawContent()
        Thread {
            if (retryRoot) RootShell.retry()
            for (t in tweaks) {
                val r = RootShell.run(t.check)
                runOnUiThread { states[t.id] = r }
            }
            runOnUiThread {
                loading = false
                if (!isDestroyed) drawContent()
            }
        }.start()
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
