package org.codeberg.anonymous950.questenhancer.manager

import android.content.Context
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
)

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
                onIf = o.optString("onIf", "").takeIf { it.isNotEmpty() }?.let { Regex(it, RegexOption.MULTILINE) },
            )
        }
    }
}
