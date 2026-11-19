package org.codeberg.anonymous950.questenhancer.manager

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** One entry of assets/catalog/tweaks.json. */
data class Tweak(
    val id: String,
    val name: String,
    val area: String,
    val summary: String,
    val author: String,
    val url: String,
    val licence: String,
    val risk: String,
    /** Shell run as root to read the tweak's state from the headset. */
    val check: String,
    /** Regex on the check's output that means "on"; null shows the output only. */
    val onIf: Regex?,
    /** Settings shown under the tweak once its check has read the state. */
    val controls: List<Control> = emptyList(),
    /** Shown instead of the controls when the check fails (module not installed). */
    val install: String = "",
    /** "reboot": changes show only after a restart, so offer one (with a warning). */
    val after: String = "",
)

/** One setting of a tweak. Commands run as root; `{value}` is the chosen option's value. */
data class Control(
    /** "header" (a sub-heading), "switch", "choice" or "button". */
    val type: String,
    val label: String,
    /** Smaller text under the label. */
    val hint: String = "",
    /** Regex on the check output. Switch: a match means on. Choice: group 1 is the current value. */
    val state: Regex?,
    val on: String = "",
    val off: String = "",
    val run: String = "",
    val options: List<Option> = emptyList(),
    /** Choice: add a "Custom" chip that opens a colour wheel; `{value}` gets #RRGGBB. */
    val wheel: Boolean = false,
    /** Asked (OK / Cancel) before running: a button's or choice's command, a switch turned on, or off. */
    val confirm: String = "",
    val confirmOn: String = "",
    val confirmOff: String = "",
)

/** A choice's option. `swatch` draws a colour dot (for colour choices). */
data class Option(val label: String, val value: String, val swatch: Int?)

object Catalog {
    fun load(context: Context): List<Tweak> {
        val text = context.assets.open("catalog/tweaks.json").bufferedReader().use { it.readText() }
        val list = JSONObject(text).getJSONArray("tweaks")
        return (0 until list.length()).map { i ->
            val o = list.getJSONObject(i)
            Tweak(
                id = o.getString("id"),
                name = o.getString("name"),
                area = o.getString("area"),
                summary = o.getString("summary"),
                author = o.getString("author"),
                url = o.optString("url", ""),
                licence = o.getString("licence"),
                risk = o.getString("risk"),
                check = o.getString("check"),
                onIf = regex(o.optString("onIf", "")),
                controls = controls(o.optJSONArray("controls")),
                install = o.optString("install", ""),
                after = o.optString("after", ""),
            )
        }
    }

    private fun regex(s: String) = s.takeIf { it.isNotEmpty() }?.let { Regex(it, RegexOption.MULTILINE) }

    private fun controls(a: JSONArray?): List<Control> = if (a == null) emptyList() else (0 until a.length()).map { i ->
        val c = a.getJSONObject(i)
        val opts = c.optJSONArray("options")
        Control(
            type = c.getString("type"),
            label = c.getString("label"),
            hint = c.optString("hint", ""),
            wheel = c.optBoolean("wheel", false),
            confirm = c.optString("confirm", ""),
            confirmOn = c.optString("confirmOn", ""),
            confirmOff = c.optString("confirmOff", ""),
            state = regex(c.optString("state", "")),
            on = c.optString("on", ""),
            off = c.optString("off", ""),
            run = c.optString("run", ""),
            options = if (opts == null) emptyList() else (0 until opts.length()).map { j ->
                val o = opts.getJSONObject(j)
                Option(
                    label = o.getString("label"),
                    value = o.getString("value"),
                    swatch = o.optString("swatch", "").takeIf { it.isNotEmpty() }?.let { Color.parseColor(it) },
                )
            },
        )
    }
}
