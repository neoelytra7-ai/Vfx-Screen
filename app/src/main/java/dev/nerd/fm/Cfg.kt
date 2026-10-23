package dev.nerd.fm

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlin.reflect.KProperty

class Pv<T : Any>(private val sp: SharedPreferences, private val k: String, private val d: T) {
  private var s by mutableStateOf(read())
  @Suppress("UNCHECKED_CAST")
  private fun read(): T = when (d) {
    is Float -> sp.getFloat(k, d) as T
    is Int -> sp.getInt(k, d) as T
    is Boolean -> sp.getBoolean(k, d) as T
    is String -> (sp.getString(k, d) ?: d) as T
    else -> d
  }
  fun put(v: T) {
    s = v
    val e = sp.edit()
    when (v) {
      is Float -> e.putFloat(k, v)
      is Int -> e.putInt(k, v)
      is Boolean -> e.putBoolean(k, v)
      is String -> e.putString(k, v)
    }
    e.apply()
  }
  fun reset() = put(d)
  operator fun getValue(t: Any?, p: KProperty<*>): T = s
  operator fun setValue(t: Any?, p: KProperty<*>, v: T) = put(v)
}

class Cfg(ctx: Context) {
  private val sp = ctx.getSharedPreferences("cfg", 0)
  private val all = ArrayList<Pv<*>>()
  private fun <T : Any> pv(k: String, d: T): Pv<T> = Pv(sp, k, d).also { all.add(it) }

  var hue by pv("hue", 165f)
  var sat by pv("sat", 1f)
  var bg by pv("bg", 0f)
  var font by pv("font", 0)
  var fs by pv("fs", 1f)
  var pad by pv("pad", 7f)
  var rad by pv("rad", 8f)
  var anim by pv("anim", 2)
  var amp by pv("amp", 4f)
  var spd by pv("spd", 2.4f)
  var stag by pv("stag", 16f)
  var hidden by pv("hidden", false)
  var sort by pv("sort", 0)
  var desc by pv("desc", false)
  var cSize by pv("cSize", true)
  var cDate by pv("cDate", true)
  var cPerm by pv("cPerm", false)
  var dual by pv("dual", true)
  var bm by Pv(sp, "bm", "")

  val ac: Color get() = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), 1f)
  val ac2: Color get() = Color.hsv((hue + 150f) % 360f, sat.coerceIn(0f, 1f) * .8f, 1f)
  val bgc: Color get() = Color(bg, bg, bg)
  val ff: FontFamily get() = when (font) { 0 -> FontFamily.Monospace; 1 -> FontFamily.SansSerif; else -> FontFamily.Serif }
  fun reset() { all.forEach { it.reset() } }
}
