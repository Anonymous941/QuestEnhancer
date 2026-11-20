package org.codeberg.anonymous950.questenhancer.manager

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Quest 1 Manager, laid out like Quest Settings: areas on the left, rows on the right.
 * Tweaks with controls (catalog "controls") can be changed; the rest only show their state.
 */
class ManagerActivity : Activity() {
    private val bg = Color.rgb(0x1C, 0x1E, 0x21)
    private val panel = Color.rgb(0x26, 0x29, 0x2D)
    private val selected = Color.rgb(0x3A, 0x3E, 0x44)
    private val dim = Color.rgb(0xA8, 0xAD, 0xB4)
    private val accent = Color.rgb(0x1A, 0x73, 0xE8)

    private var tweaks: List<Tweak> = emptyList()
    private val states = mutableMapOf<String, RootShell.Result>()
    private var bundled: Map<String, Bundled> = emptyMap()
    /** Bundled tweaks: the module's versionCode on the headset; absent = not installed. */
    private val installed = mutableMapOf<String, Installed>()
    private lateinit var nav: LinearLayout
    private lateinit var content: LinearLayout
    private var area = ""
    private var loading = false
    /** A change was made that only shows after a restart (catalog "after": "reboot"). */
    private var rebootNeeded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(bg)
        tweaks = Catalog.load(this)
        bundled = Catalog.bundled(this)
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
            if (loading) "Reading from the headset..." else "What is set on this headset now.",
            13f, dim).apply { setPadding(0, 0, 0, dp(16)) })
        if (rebootNeeded) content.addView(rebootRow())
        for (t in tweaks.filter { it.area == area }) content.addView(row(t))
    }

    private fun row(t: Tweak): View {
        val r = states[t.id]
        // No switch for a check that failed (root refused, timed out): its state is unknown.
        val on = r?.takeIf { it.code == 0 }?.let { t.onIf?.containsMatchIn(it.output) }
        val b = bundled[t.bundle]
        val have = installed[t.id]
        val status = when {
            r == null -> "Checking..."
            b != null && have?.pending == true -> "Installed ${b.version}: restart the headset to finish."
            b != null && have == null && r.code != 0 -> "Not installed. Install adds it with Magisk."
            r.code != 0 && t.install.isNotEmpty() -> t.install
            r.code != 0 -> r.output.ifEmpty { "Couldn't read (exit ${r.code})" }
            t.controls.isNotEmpty() && on == null -> ""
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
            if (status.isNotEmpty()) addView(label(status, 13f, dim))
            addView(details)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (on != null) addView(Switch(this@ManagerActivity).apply {
                isChecked = on
                isEnabled = false
            })
            setOnClickListener {
                details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = rounded(panel)
            addView(header)
            if (r != null && b != null) installButton(t, b, have)?.let { addView(it) }
            // Controls only once the state is read, so they never show a guess.
            if (r != null && r.code == 0) for (c in t.controls) addView(control(t, c, r.output))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
    }

    /** One setting: a sub-heading, a switch on the right, a row of choices, or a button. */
    private fun control(t: Tweak, c: Control, output: String): View {
        if (c.type == "header") return label(c.label, 13f, dim, bold = true).apply {
            setPadding(0, dp(22), 0, 0)
            isAllCaps = true
        }
        val inline = c.type == "switch" || c.type == "button"
        val box = LinearLayout(this).apply {
            orientation = if (inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (inline) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            setPadding(0, dp(14), 0, 0)
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(c.label, 15f, Color.WHITE))
            if (c.hint.isNotEmpty()) addView(label(c.hint, 12f, dim))
        }
        box.addView(text,
            if (inline) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        when (c.type) {
            "switch" -> {
                val isOn = c.state?.containsMatchIn(output) == true
                box.addView(Switch(this).apply {
                    isChecked = isOn
                    setOnCheckedChangeListener { _, checked ->
                        // Cancelled: redraw, which puts the switch back.
                        confirmThen(if (checked) c.confirmOn else c.confirmOff, { drawContent() }) {
                            act(t, if (checked) c.on else c.off)
                        }
                    }
                })
            }
            "button" -> box.addView(label(c.on.ifEmpty { "Run" }, 14f, Color.WHITE, bold = true).apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = rounded(accent)
                setOnClickListener { confirmThen(c.confirm) { act(t, c.run, showOutput = true) } }
            })
            "choice" -> {
                val current = c.state?.find(output)?.groupValues?.getOrNull(1)
                val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val lp = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) } }
                var matched = false
                for (o in c.options) {
                    val chosen = o.value.equals(current, ignoreCase = true)
                    matched = matched || chosen
                    chips.addView(chip(o, chosen) {
                        confirmThen(c.confirm) { act(t, c.run.replace("{value}", o.value)) }
                    }, lp())
                }
                if (c.wheel) {
                    // A colour not among the presets shows on the Custom chip.
                    val now = current?.takeIf { !matched }?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                    chips.addView(chip(Option("Custom", "", now ?: Color.GRAY), now != null) {
                        pickColor(now ?: Color.WHITE) { hex -> act(t, c.run.replace("{value}", hex)) }
                    }, lp())
                }
                box.addView(HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    setPadding(0, dp(8), 0, 0)
                    addView(chips)
                })
            }
        }
        return box
    }

    /**
     * Install when the module isn't on the headset, Update when the APK carries a newer
     * versionCode; nothing while an install waits for a restart, or when it's current.
     */
    private fun installButton(t: Tweak, b: Bundled, have: Installed?): View? {
        if (have?.pending == true || (have != null && have.versionCode >= b.versionCode)) return null
        val update = have != null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        box.addView(label(
            if (update) "Update available: ${b.version}" else "Version ${b.version} is in this app.",
            15f, Color.WHITE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(label(if (update) "Update" else "Install", 14f, Color.WHITE, bold = true).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(accent)
            setOnClickListener {
                confirmThen("${if (update) "Update" else "Install"} ${t.name} (${b.version}) with Magisk? " +
                    "Risk: ${t.risk}. It works after a restart.") { install(t, b) }
            }
        })
        return box
    }

    /** Copies the bundled zip out of the APK and hands it to Magisk, then reads the state again. */
    private fun install(t: Tweak, b: Bundled) {
        Thread {
            val zip = java.io.File(cacheDir, "${b.fix}.zip")
            val r = try {
                assets.open("modules/${b.fix}.zip").use { i -> zip.outputStream().use { i.copyTo(it) } }
                zip.setReadable(true, false)
                RootShell.run("magisk --install-module '${zip.absolutePath}'", timeoutSeconds = 180)
            } catch (e: Exception) {
                RootShell.Result(-1, "couldn't copy the module: ${e.message}")
            } finally {
                zip.delete()
            }
            val v = installedVersion(b)
            val s = RootShell.run(t.check)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                states[t.id] = s
                if (v != null) installed[t.id] = v else installed.remove(t.id)
                if (r.code != 0) {
                    // Magisk's installer prints why (wrong device, missing kernel feature...).
                    Toast.makeText(this, r.output.lines().takeLast(4).joinToString("\n").ifEmpty { "install failed (exit ${r.code})" }, Toast.LENGTH_LONG).show()
                } else rebootNeeded = true
                drawContent()
            }
        }.start()
    }

    /** The module's versionCode: pending in modules_update (installed, not yet restarted) or active. */
    private fun installedVersion(b: Bundled): Installed? {
        val r = RootShell.run(
            "u=/data/adb/modules_update/${b.id}/module.prop; m=/data/adb/modules/${b.id}; " +
                "if [ -f \$u ]; then echo \"pending \$(sed -n 's/^versionCode=//p' \$u)\"; " +
                "elif [ -f \$m/module.prop ] && [ ! -f \$m/remove ]; then echo \"active \$(sed -n 's/^versionCode=//p' \$m/module.prop)\"; " +
                "else echo none; fi")
        val parts = r.output.trim().split(' ')
        if (r.code != 0 || parts.size != 2) return null
        return Installed(parts[1].trim().toIntOrNull() ?: 0, pending = parts[0] == "pending")
    }

    /** Asks first when [message] is set; [onCancel] runs if the answer is no. */
    private fun confirmThen(message: String, onCancel: () -> Unit = {}, run: () -> Unit) {
        if (message.isEmpty()) return run()
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> run() }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    /** The colour wheel, with a swatch of the pick; answers #RRGGBB. */
    private fun pickColor(start: Int, done: (String) -> Unit) {
        val swatch = View(this).apply { background = rounded(start) }
        val wheel = ColorWheel(this).apply {
            color = start
            onColor = { swatch.background = rounded(it) }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(wheel)
            addView(swatch, LinearLayout.LayoutParams(dp(200), dp(36)).apply { topMargin = dp(16) })
        }
        AlertDialog.Builder(this)
            .setTitle("Pick a colour")
            .setView(box)
            .setPositiveButton("Use it") { _, _ ->
                done(String.format("#%06X", wheel.color and 0xFFFFFF))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chip(o: Option, chosen: Boolean, onTap: () -> Unit) = label(o.label, 14f, Color.WHITE).apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = rounded(if (chosen) accent else selected)
        if (o.swatch != null) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(o.swatch)
                setSize(dp(14), dp(14))
            }, null, null, null)
            compoundDrawablePadding = dp(8)
        }
        setOnClickListener { onTap() }
    }

    /** Runs a control's command, then reads that tweak's state again. */
    private fun act(t: Tweak, command: String, showOutput: Boolean = false) {
        if (command.isEmpty()) return
        Thread {
            // Changes get longer than checks: ptcolor rewrites part of an APK copy.
            val r = RootShell.run(command, timeoutSeconds = 180)
            val s = RootShell.run(t.check)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                states[t.id] = s
                if (r.code != 0) Toast.makeText(this, r.output.ifEmpty { "failed (exit ${r.code})" }, Toast.LENGTH_LONG).show()
                else {
                    if (showOutput && r.output.isNotEmpty()) Toast.makeText(this, r.output, Toast.LENGTH_LONG).show()
                    if (t.after == "reboot") rebootNeeded = true
                }
                drawContent()
            }
        }.start()
    }

    private fun rebootRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(14), dp(20), dp(14))
        background = rounded(selected)
        addView(label("Restart the headset to see the change.", 15f, Color.WHITE),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(label("Restart now", 15f, Color.WHITE, bold = true).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(accent)
            setOnClickListener {
                AlertDialog.Builder(this@ManagerActivity)
                    .setTitle("Restart the headset?")
                    .setMessage("The headset restarts now. Anything open closes.")
                    .setPositiveButton("Restart") { _, _ -> Thread { RootShell.run("svc power reboot || reboot") }.start() }
                    .setNegativeButton("Not now", null)
                    .show()
            }
        })
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
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
                val v = bundled[t.bundle]?.let { installedVersion(it) }
                val r = RootShell.run(t.check)
                runOnUiThread {
                    if (v != null) installed[t.id] = v else installed.remove(t.id)
                    states[t.id] = r
                }
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

/** A bundled module on the headset; [pending] = installed, waiting for a restart. */
data class Installed(val versionCode: Int, val pending: Boolean)
