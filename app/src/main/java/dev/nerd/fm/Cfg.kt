package dev.nerd.fm

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.sqrt
import kotlin.reflect.KProperty

class Pal(val name: String, val bg: Color, val fg: Color, val dim: Color, val hue: Float, val sat: Float, val light: Boolean = false)

object Th {
  val pals = listOf(
    Pal("MOCHI", Color(0xFF1C1423), Color(0xFFFFEEF6), Color(0xFFB59AB0), 335f, .45f),
    Pal("AMOLED", Color.Black, Color.White, Color(0xFF9A9A9A), 165f, 1f),
    Pal("MINT", Color(0xFF0E1F1C), Color(0xFFEFFFFA), Color(0xFF8FB5AC), 160f, .5f),
    Pal("PEACH", Color(0xFF231815), Color(0xFFFFF1E8), Color(0xFFBFA092), 20f, .5f),
    Pal("LAVENDER", Color(0xFF16132A), Color(0xFFF2EEFF), Color(0xFFA39BC7), 260f, .45f),
    Pal("SAKURA", Color(0xFFFFF4F8), Color(0xFF4A3040), Color(0xFF9C7F8F), 335f, .55f, true),
    Pal("CREAM", Color(0xFFFFF9EC), Color(0xFF45392C), Color(0xFF9A8B78), 28f, .6f, true)
  )
  var pal by mutableStateOf(pals[0])
  var cap by mutableIntStateOf(2)
  val fg: Color get() = pal.fg
  val dim: Color get() = pal.dim
  val bg: Color get() = pal.bg
}

class Pv<T : Any>(private val sp: SharedPreferences, private val k: String, private val d: T) {
  private var s by mutableStateOf(read())
  @Suppress("UNCHECKED_CAST")
  private fun read(): T = when (d) {
    is Float -> sp.getFloat(k, d) as T
    is Int -> sp.getInt(k, d) as T
    is Long -> sp.getLong(k, d) as T
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
      is Long -> e.putLong(k, v)
      is Boolean -> e.putBoolean(k, v)
      is String -> e.putString(k, v)
    }
    e.apply()
  }
  fun reset() = put(d)
  operator fun getValue(t: Any?, p: KProperty<*>): T = s
  operator fun setValue(t: Any?, p: KProperty<*>, v: T) = put(v)
}

private val fRound = FontFamily(Typeface.create("sans-serif-rounded", Typeface.NORMAL))
private val fCasual = FontFamily(Typeface.create("casual", Typeface.NORMAL))

class Cfg(ctx: Context) {
  private val sp = ctx.getSharedPreferences("cfg", 0)
  private val all = ArrayList<Pv<*>>()
  private fun <T : Any> pv(k: String, d: T): Pv<T> = Pv(sp, k, d).also { all.add(it) }

  var hue by pv("hue", 335f)
  var sat by pv("sat", .45f)
  var font by pv("font", 0)
  var fs by pv("fs", 1f)
  var pad by pv("pad", 3f)
  var rad by pv("rad", 16f)
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
  var grid by pv("grid", false)
  var navHide by pv("navHide", true)
  var haptic by pv("haptic", true)
  var sparkle by pv("sparkle", true)
  var auto by pv("auto", true)
  var sk by Pv(sp, "sk", "")
  var likes by Pv(sp, "likes", "")
  var tabs by Pv(sp, "tabs", "")
  var presets by Pv(sp, "presets", "")
  var qh by Pv(sp, "qh", "")
  var preset by Pv(sp, "preset", 0)
  var bm by Pv(sp, "bm", "")
  var xp by Pv(sp, "xp", 0)
  var cleaned by Pv(sp, "cleaned", 0L)
  var streak by Pv(sp, "streak", 0)
  var lastDay by Pv(sp, "lastDay", 0)

  val fx: Int get() = minOf(anim, Th.cap)
  private val lt: Boolean get() = Th.pal.light
  val ac: Color get() = Color.hsv((hue.coerceIn(0f, 360f) + Aura.deg * Sk.f("aura.drift", 0f) + 360f) % 360f, sat.coerceIn(0f, 1f).let { if (lt) maxOf(it, .5f) else it }, if (lt) .86f else 1f)
  val ac2: Color get() = Color.hsv((hue + Aura.deg * Sk.f("aura.drift", 0f) + 388f) % 360f, (sat.coerceIn(0f, 1f) * .9f).let { if (lt) maxOf(it, .45f) else it }, if (lt) .88f else 1f)
  val bgc: Color get() = Th.bg
  val ff: FontFamily get() = when (font) { 0 -> fRound; 1 -> fCasual; 2 -> FontFamily.SansSerif; 3 -> FontFamily.Monospace; else -> FontFamily.Serif }
  val level: Int get() = sqrt(xp / 12.0).toInt() + 1

  fun applyPreset(i: Int) { preset = i; Th.pal = Th.pals[i]; hue = Th.pal.hue; sat = Th.pal.sat }
  fun addXp(n: Int) { if (n > 0) xp += n }
  fun snap(): String = listOf("hue" to hue, "sat" to sat, "font" to font.toFloat(), "fs" to fs, "pad" to pad, "rad" to rad, "anim" to anim.toFloat(), "amp" to amp, "spd" to spd, "stag" to stag, "preset" to preset.toFloat())
    .joinToString(";") { it.first + "=" + it.second } + "|" + Sk.ser()
  fun restore(s: String) {
    val m = Sk.parse(s.substringBefore('|'))
    m["preset"]?.let { applyPreset(it.toInt().coerceIn(0, Th.pals.size - 1)) }
    m["hue"]?.let { hue = it }; m["sat"]?.let { sat = it }; m["font"]?.let { font = it.toInt() }; m["fs"]?.let { fs = it }
    m["pad"]?.let { pad = it }; m["rad"]?.let { rad = it }; m["anim"]?.let { anim = it.toInt() }; m["amp"]?.let { amp = it }
    m["spd"]?.let { spd = it }; m["stag"]?.let { stag = it }
    Sk.load(s.substringAfter('|', ""))
  }
  fun reset() { all.forEach { it.reset() }; applyPreset(0); Sk.resetAll() }

  init {
    Sk.bind(this)
    Th.pal = Th.pals[preset.coerceIn(0, Th.pals.size - 1)]
    val today = (System.currentTimeMillis() / 86400000L).toInt()
    if (lastDay != today) { streak = if (lastDay == today - 1) streak + 1 else 1; lastDay = today }
  }
}
