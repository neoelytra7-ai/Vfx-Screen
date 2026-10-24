@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object Ed { var on by mutableStateOf(false); var sel by mutableStateOf("btn") }
object Hub {
  var cheer: (Int) -> Unit = {}
  var act: (String) -> Unit = {}
  var toast: (String) -> Unit = {}
}

object Sk {
  val m = mutableStateMapOf<String, Float>()
  private var cfg: Cfg? = null
  private val hist = ArrayList<String>()
  private var last = 0L
  fun f(k: String, d: Float): Float = m[k] ?: d
  fun b(k: String, d: Boolean): Boolean = (m[k] ?: if (d) 1f else 0f) > .5f
  fun ser(): String = m.entries.joinToString(";") { it.key + "=" + it.value }
  fun parse(s: String): Map<String, Float> {
    val o = HashMap<String, Float>()
    for (p in s.split(';')) { val i = p.indexOf('='); if (i < 1) continue; val v = p.substring(i + 1).toFloatOrNull() ?: continue; o[p.substring(0, i)] = v }
    return o
  }
  fun bind(c: Cfg) { cfg = c; m.clear(); m.putAll(parse(c.sk)) }
  private fun save() { cfg?.sk = ser() }
  fun load(s: String) { m.clear(); m.putAll(parse(s)); save() }
  fun put(x: Map<String, Float>) { m.putAll(x); save() }
  fun set(k: String, v: Float) {
    val n = System.currentTimeMillis()
    if (n - last > 900) { hist.add(ser()); if (hist.size > 30) hist.removeAt(0) }
    last = n; m[k] = v; save()
  }
  fun undo(): Boolean {
    if (hist.isEmpty()) return false
    val s = hist.removeAt(hist.lastIndex); m.clear(); m.putAll(parse(s)); save(); last = 0L; return true
  }
  fun reset(keys: List<String>) { hist.add(ser()); keys.forEach { m.remove(it) }; save() }
  fun resetAll() { hist.add(ser()); m.clear(); save() }
}

class Tok(val k: String, val label: String, val lo: Float, val hi: Float, val d: Float, val kind: Int = 0, val opts: List<String> = emptyList())
private val TRS = listOf("SLIDE", "POP", "ZOOM", "DROP", "FADE")

val SCHEMA: Map<String, List<Tok>> = linkedMapOf(
  "btn" to listOf(Tok("btn.round", "round", 0f, 30f, 16f), Tok("btn.padx", "pad x", 4f, 24f, 11f), Tok("btn.pady", "pad y", 2f, 14f, 6f),
    Tok("btn.border", "border", 0f, 4f, 1.5f), Tok("btn.fill", "fill", 0f, .4f, .08f), Tok("btn.squish", "squish", 0f, 1.5f, 1f), Tok("btn.text", "text x", .7f, 1.6f, 1f)),
  "nav" to listOf(Tok("nav.round", "round", 0f, 36f, 26f), Tok("nav.fill", "fill", 0f, .4f, .07f), Tok("nav.icon", "icon x", .6f, 1.6f, 1f),
    Tok("nav.labels", "labels", 0f, 1f, 1f, 1), Tok("nav.top", "on top", 0f, 1f, 0f, 1)),
  "row" to listOf(Tok("row.round", "round", 0f, 30f, 14f), Tok("row.pad", "pad +", -4f, 14f, 0f), Tok("row.icon", "icon px", 22f, 60f, 38f),
    Tok("row.sel", "selected", 0f, .6f, .22f), Tok("row.sub", "subtitle", 0f, 1f, 1f, 1)),
  "pane" to listOf(Tok("pane.round", "round", 0f, 36f, 16f), Tok("pane.fill", "fill", 0f, .3f, .035f), Tok("pane.border", "border", 0f, 5f, 1.5f), Tok("crumb.show", "breadcrumb", 0f, 1f, 1f, 1)),
  "tile" to listOf(Tok("tile.round", "round", 0f, 36f, 20f), Tok("tile.tint", "tint", 0f, .8f, .4f), Tok("tile.icon", "icon px", 24f, 64f, 40f)),
  "icon" to listOf(Tok("icon.face", "faces", 0f, 1f, 1f, 1), Tok("icon.idle", "idle anim", 0f, 2f, 1f), Tok("icon.tone", "saturation", 0f, 1.8f, 1f)),
  "mochi" to listOf(Tok("mochi.home", "home size", 60f, 180f, 104f), Tok("mochi.show", "show on home", 0f, 1f, 1f, 1)),
  "stor" to listOf(Tok("stor.show", "show bar", 0f, 1f, 1f, 1), Tok("stor.h", "height", 3f, 18f, 9f)),
  "bg" to listOf(Tok("bg.grad", "gradient", 0f, 1f, 0f), Tok("bg.decor", "floaties", 0f, 1f, 0f, 1)),
  "dialog" to listOf(Tok("dlg.round", "round", 0f, 40f, 28f)),
  "motion" to listOf(Tok("tr.tab", "tab transition", 0f, 4f, 0f, 2, TRS), Tok("tr.dir", "folder transition", 0f, 4f, 0f, 2, TRS),
    Tok("tr.speed", "speed", .4f, 2.5f, 1f), Tok("tr.bounce", "bounce", 0f, 1.6f, .5f), Tok("tr.wipe", "wipe", 0f, 3f, 0f, 2, listOf("BUBBLES", "HEARTS", "SPARKLES", "OFF"))),
  "fx" to listOf(Tok("fx.spark", "tap sparkles", 0f, 8f, 3f), Tok("fx.jelly", "jelly scroll", 0f, 1f, 1f, 1))
)

class Tpl(val name: String, val preset: Int, val font: Int, val m: Map<String, Float>)
val TPLS = listOf(
  Tpl("MARSHMALLOW", 0, 0, mapOf("btn.round" to 28f, "row.round" to 24f, "pane.round" to 30f, "tile.round" to 28f, "nav.round" to 34f, "row.pad" to 3f, "btn.border" to 1f, "row.icon" to 42f)),
  Tpl("TERMINAL", 1, 3, mapOf("btn.round" to 3f, "row.round" to 2f, "pane.round" to 4f, "tile.round" to 4f, "nav.round" to 6f, "btn.border" to 1f, "btn.fill" to .04f, "row.icon" to 28f, "icon.face" to 0f, "icon.idle" to .3f, "fx.spark" to 0f, "tr.tab" to 4f)),
  Tpl("CANDY", 0, 1, mapOf("btn.round" to 22f, "btn.squish" to 1.4f, "fx.spark" to 6f, "tr.bounce" to 1.2f, "tr.tab" to 1f, "row.icon" to 44f, "icon.tone" to 1.4f)),
  Tpl("GLASS", 4, 2, mapOf("btn.fill" to .03f, "btn.border" to 1f, "pane.fill" to .02f, "pane.border" to 1f, "nav.fill" to .03f, "row.sel" to .15f, "tile.tint" to .15f)),
  Tpl("COMPACT", 2, 2, mapOf("btn.padx" to 8f, "btn.pady" to 3f, "row.pad" to -3f, "row.icon" to 28f, "row.sub" to 0f, "stor.h" to 5f, "nav.labels" to 0f, "btn.text" to .9f)),
  Tpl("CHUNKY", 3, 0, mapOf("btn.round" to 14f, "btn.border" to 3f, "btn.padx" to 16f, "btn.pady" to 9f, "row.pad" to 8f, "row.icon" to 48f, "pane.border" to 3f, "nav.round" to 18f, "btn.text" to 1.15f)),
  Tpl("SAKURA TEA", 5, 0, mapOf("bg.grad" to .6f, "bg.decor" to 1f, "btn.round" to 24f, "tile.tint" to .5f)),
  Tpl("NIGHT OWL", 4, 2, mapOf("bg.decor" to 1f, "fx.spark" to 5f, "tr.tab" to 3f, "tr.dir" to 2f))
)

fun applyTpl(cf: Cfg, t: Tpl) { Sk.resetAll(); Sk.put(t.m); cf.applyPreset(t.preset); cf.font = t.font }
fun presetList(cf: Cfg): List<Pair<String, String>> = cf.presets.split('\n').filter { it.contains('\t') }.map { it.substringBefore('\t') to it.substringAfter('\t') }
fun presetSave(cf: Cfg, name: String, data: String = cf.snap()) {
  val l = presetList(cf).filter { it.first != name } + (name to data)
  cf.presets = l.joinToString("\n") { it.first + "\t" + it.second }
}
fun presetDel(cf: Cfg, name: String) { cf.presets = presetList(cf).filter { it.first != name }.joinToString("\n") { it.first + "\t" + it.second } }

@Composable fun TokControls(id: String) {
  val toks = SCHEMA[id] ?: return
  toks.forEach { t ->
    when (t.kind) {
      1 -> { val on = Sk.b(t.k, t.d > .5f); Bt(t.label, on) { Sk.set(t.k, if (on) 0f else 1f) } }
      2 -> Column { T(t.label, Th.dim, 10f); FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { t.opts.forEachIndexed { i, o -> Bt(o, Sk.f(t.k, t.d).toInt() == i) { Sk.set(t.k, i.toFloat()) } } } }
      else -> Sl(t.label, Sk.f(t.k, t.d), t.lo..t.hi) { Sk.set(t.k, it) }
    }
  }
}

@Composable fun EditorPanelInner(cf: Cfg, tab: Int, setTab: (Int) -> Unit, m: Modifier) {
  var pg by remember { mutableIntStateOf(0) }
  var small by remember { mutableStateOf(false) }
  val nm = remember { mutableStateOf("") }
  val clip = LocalClipboardManager.current
  val sh = RoundedCornerShape(24.dp)
  Column(m.fillMaxWidth().padding(top = 4.dp).clip(sh).background(Th.bg).border(1.5.dp, cf.ac.copy(alpha = .6f), sh).padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      T("✎ ROOT EDITOR", cf.ac, 13f, FontWeight.Bold, Modifier.weight(1f))
      Bt("UNDO") { if (!Sk.undo()) Hub.toast("nothing to undo") }
      Bt(if (small) "▴" else "▾") { small = !small }
      Bt("DONE", true) { Ed.on = false }
    }
    if (!small) {
      Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("ELEMENT", "TEMPLATES", "PRESETS").forEachIndexed { i, s -> Bt(s, pg == i) { pg = i } }
        T("│", Th.dim)
        listOf("home", "files", "find", "tools", "me").forEachIndexed { i, s -> Bt(s, tab == i) { setTab(i) } }
      }
      Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (pg) {
          0 -> {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { SCHEMA.keys.forEach { k -> Bt(k, Ed.sel == k) { Ed.sel = k } } }
            T("tap any dashed element to jump here · hold buttons for sub-options", Th.dim, 9f, ml = 2)
            TokControls(Ed.sel)
            Bt("RESET ${Ed.sel}") { Sk.reset(SCHEMA[Ed.sel]?.map { it.k } ?: emptyList()) }
          }
          1 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TPLS.forEach { t -> Bt(t.name) { applyTpl(cf, t); Hub.toast(t.name + " applied ♡") } }
          }
          else -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Inp(nm.value, { nm.value = it }, "preset name", Modifier.weight(1f))
              Bt("SAVE", true) {
                val n = nm.value.trim().replace('\t', ' ').replace('\n', ' ')
                if (n.isNotEmpty()) { presetSave(cf, n); Hub.toast("saved $n ♡") }
              }
            }
            presetList(cf).forEach { (n, d) ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                T(n, Th.fg, 12f, FontWeight.Medium, Modifier.weight(1f))
                Bt("LOAD", subs = listOf<Pair<String, () -> Unit>>(
                  "export (copy)" to { clip.setText(AnnotatedString("NFM1|$n|$d")); Hub.toast("preset copied") },
                  "overwrite w/ current" to { presetSave(cf, n); Hub.toast("overwritten") },
                  "delete" to { presetDel(cf, n) })) { cf.restore(d); Hub.toast("loaded $n") }
              }
            }
            Bt("IMPORT FROM CLIPBOARD") {
              val s = clip.getText()?.text ?: ""
              val p = s.split("|", limit = 3)
              if (p.size == 3 && p[0] == "NFM1") { presetSave(cf, p[1], p[2]); cf.restore(p[2]); Hub.toast("imported ${p[1]}") } else Hub.toast("clipboard has no preset")
            }
          }
        }
      }
    }
  }
}

@Composable fun EditorPanel(cf: Cfg, tab: Int, setTab: (Int) -> Unit, m: Modifier) { CompositionLocalProvider(LNoEd provides true) { EditorPanelInner(cf, tab, setTab, m) } }
