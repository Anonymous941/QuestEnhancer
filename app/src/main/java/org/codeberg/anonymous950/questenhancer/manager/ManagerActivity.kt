package org.codeberg.anonymous950.questenhancer.manager

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Quest 1 Manager, first step: a read-only list of tweaks with their live state.
 * Nothing here changes the headset.
 */
class ManagerActivity : Activity() {
    private lateinit var list: LinearLayout
    private val stateViews = mutableMapOf<String, TextView>()
    private var tweaks: List<Tweak> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(16)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        list.addView(text("Quest 1 Manager", 24f, bold = true))
        list.addView(text("Read only: shows each tweak's state on this headset. Nothing is changed.", 14f))
        list.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        })

        tweaks = Catalog.load(this)
        var area = ""
        for (t in tweaks) {
            if (t.area != area) {
                area = t.area
                list.addView(text(area, 18f, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
            }
            list.addView(card(t))
        }
        setContentView(ScrollView(this).apply { addView(list) })
        refresh()
    }

    private fun card(t: Tweak): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.argb(40, 128, 128, 128))
        }
        box.addView(text("${t.name}   [${t.risk}]", 16f, bold = true))
        box.addView(text(t.summary, 14f))
        val state = text("checking...", 14f).apply { typeface = Typeface.MONOSPACE }
        stateViews[t.id] = state
        box.addView(state)
        val credit = buildString {
            append("By ").append(t.author).append(" · ").append(t.licence)
            if (t.url.isNotEmpty()) append("\n").append(t.url)
        }
        box.addView(text(credit, 12f).apply { alpha = 0.7f })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(box)
        }
    }

    private fun refresh() {
        for (t in tweaks) stateViews[t.id]?.text = "checking..."
        Thread {
            for (t in tweaks) {
                val r = RootShell.run(t.check)
                val label = when {
                    r.code != 0 && r.output.isEmpty() -> "Unknown (exit ${r.code})"
                    t.onIf == null -> r.output
                    t.onIf.containsMatchIn(r.output) -> "On\n${r.output}"
                    else -> "Off\n${r.output}"
                }
                runOnUiThread { stateViews[t.id]?.text = label }
            }
        }.start()
    }

    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
