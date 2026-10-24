@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File
import java.util.Calendar

private val TILES = listOf(
  Triple("clock", "Recent", "q:file age<3d"), Triple("photo", "Photos", "q:type:img"), Triple("video", "Videos", "q:type:vid"),
  Triple("music", "Music", "q:type:aud"), Triple("doc", "Docs", "q:type:doc"), Triple("box", "Archives", "q:type:arc"),
  Triple("droid", "APKs", "q:type:apk"), Triple("big", "Big files", "q:size>100m"), Triple("down", "Downloads", "go:Download"),
  Triple("broom", "Cleaner", "nav:clean"), Triple("trash", "Trash", "nav:trash"), Triple("dup", "Dupes", "nav:dupe")
)

@Composable fun Tile(ic: String, lb: String, i: Int, m: Modifier, subs: Subs, f: () -> Unit) {
  val cf = LCfg.current
  val src = remember { MutableInteractionSource() }
  val pr by src.collectIsPressedAsState()
  val sx by animateFloatAsState(if (pr) .88f else 1f, spring(.35f, 400f), label = "t")
  val hue = (cf.hue + i * 31f) % 360f; val lt = Th.pal.light
  val sh = RoundedCornerShape(Sk.f("tile.round", 20f).dp)
  var menu by remember { mutableStateOf(false) }
  val tint = Sk.f("tile.tint", .4f)
  Pop(i, m) {
    Column(Modifier.fillMaxWidth().graphicsLayer { scaleX = sx; scaleY = sx }.ed("tile").clip(sh)
      .background(Color.hsv(hue, tint, if (lt) .97f else .3f))
      .border(1.5.dp, Color.hsv(hue, (tint + .1f).coerceAtMost(1f), if (lt) .8f else .9f).copy(alpha = .6f), sh)
      .combinedClickable(src, null, onClick = { f() }, onLongClick = if (subs.isEmpty()) null else ({ menu = true }))
      .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Gly(ic, Sk.f("tile.icon", 40f).dp, pr)
      T(lb, Th.fg, 11f, FontWeight.Medium)
    }
    if (menu) SubMenu(subs) { menu = false }
  }
}

@Composable fun HomeScr(act: (String) -> Unit, mochiTap: () -> Unit) {
  val cf = LCfg.current; val ac = cf.ac; val a2 = cf.ac2
  val h = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
  val greet = when { h < 5 -> "still awake? (・ω・)"; h < 11 -> "good morning!"; h < 17 -> "hello hello!"; h < 21 -> "good evening~"; else -> "sleepy time soon…" }
  val tips = remember { listOf("long-press a file to pick it ♡", "try size>100m age>180d in FIND", "dual panes: copy with ⇉", "tap me! i like it", "trash keeps deleted things safe", "squish photos to save space", "pinch to zoom images", "☆ saves a folder as bookmark") }
  var tip by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) { while (true) { delay(6000); tip = (tip + 1) % tips.size } }
  val (tot, fr) = remember { Fx.stor() }
  val used = if (tot > 0) (tot - fr).toFloat() / tot else 0f
  val grow = remember { Animatable(0f) }
  LaunchedEffect(Unit) { grow.animateTo(used, spring(.6f, 80f)) }
  val sh = RoundedCornerShape(cf.rad.dp + 6.dp)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (Sk.b("mochi.show", true)) { Mochi(Sk.f("mochi.home", 104f).dp, if (used > .9f) 2 else 0, Modifier, mochiTap, mochiSubs(cf)); Spacer(Modifier.width(8.dp)) }
      Column(Modifier.weight(1f).clip(sh).background(Th.fg.copy(alpha = .07f)).border(1.5.dp, ac.copy(alpha = .3f), sh).padding(12.dp)) {
        T(greet, ac, 15f, FontWeight.Bold)
        AnimatedContent(tip, label = "tip") { T(tips[it], Th.dim, 11f, ml = 3) }
      }
    }
    Row(Modifier.fillMaxWidth().clip(sh).background(Th.fg.copy(alpha = .05f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(104.dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
          val sw = 14.dp.toPx(); val sz = Size(size.width - sw, size.height - sw)
          drawArc(Th.fg.copy(alpha = .1f), -90f, 360f, false, Offset(sw / 2, sw / 2), sz, style = Stroke(sw, cap = StrokeCap.Round))
          drawArc(Brush.sweepGradient(listOf(a2, ac, a2)), -90f, 360f * grow.value, false, Offset(sw / 2, sw / 2), sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { T("${(used * 100).toInt()}%", ac, 20f, FontWeight.Bold); T("used", Th.dim, 10f) }
      }
      Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        T("storage", Th.dim, 10f); T(Fx.sz(tot - fr) + " used", Th.fg, 14f, FontWeight.Bold)
        T(Fx.sz(fr) + " free", a2, 12f); T("of " + Fx.sz(tot), Th.dim, 10f)
        T("Lv ${cf.level} · ${cf.streak}d streak ♡", ac, 11f, FontWeight.Medium)
      }
    }
    TILES.chunked(3).forEachIndexed { r, row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEachIndexed { c, t -> Tile(t.first, t.second, r * 3 + c, Modifier.weight(1f), if (t.third.startsWith("q:")) listOf<Pair<String, () -> Unit>>("this folder only" to { act("q:~" + t.third.substring(2)) }) else emptyList()) { act(t.third) } }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable fun ToolsScr(act: (String) -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Wave("TOOLS", 18f)
    Cd("broom", "CLEANER", "junk · leftover APKs · big & old · caches · empty folders, one tap each") { act("nav:clean") }
    Cd("chart", "BREAKDOWN", "where did my storage go? images / videos / audio / docs…") { act("nav:break") }
    Cd("lens", "ANALYZER", "biggest items in the current folder, live bars") { act("nav:ana") }
    Cd("dup", "DUPLICATES", "size → MD5 → SHA-1, auto-mark extras") { act("nav:dupe") }
    Cd("folderx", "EMPTY FOLDERS", "find & purge empty directory trees") { act("nav:empty") }
    Cd("trash", "TRASH", "restore deleted things, or empty it for good") { act("nav:trash") }
    Cd("cmp", "COMPARE PANES", "diff both panes, sync A→B or B→A") { act("nav:cmp") }
    Cd("edit", "BATCH RENAME", "regex + {n} counter on selected files") { act("batch") }
    Cd("squish", "SQUISH PHOTOS", "shrink selected images (JPG/WEBP, quality + size)") { act("squish") }
    Cd("star", "BOOKMARKS", "saved folders") { act("bm") }
    Cd("edit", "ROOT EDITOR", "restyle every part of the UI · templates · save presets") { act("edit") }
  }
}

@Composable fun MeScr(mochiTap: () -> Unit) {
  val cf = LCfg.current; val ac = cf.ac
  val lv = cf.level; val base = (lv - 1) * (lv - 1) * 12; val next = lv * lv * 12
  val fr by animateFloatAsState(((cf.xp - base).toFloat() / (next - base)).coerceIn(0f, 1f), tween(700), label = "xp")
  val sh = RoundedCornerShape(cf.rad.dp + 6.dp)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.fillMaxWidth().clip(sh).background(Th.fg.copy(alpha = .06f)).border(1.5.dp, ac.copy(alpha = .3f), sh).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Mochi(84.dp, 0, Modifier, mochiTap, mochiSubs(cf))
      Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        T("Mochi · Lv $lv", ac, 15f, FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Th.fg.copy(alpha = .1f))) {
          Box(Modifier.fillMaxWidth(fr.coerceAtLeast(.03f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(cf.ac2, ac))))
        }
        T("${cf.xp} xp · next at $next", Th.dim, 10f)
        T("${cf.streak}-day streak · cleaned ${Fx.sz(cf.cleaned)}", Th.dim, 10f)
      }
    }
    Bt("✎ ROOT EDITOR · customize everything", true) { Ed.on = true }
    Sec("TRANSITIONS & FX")
    TokControls("motion")
    TokControls("fx")
    Sec("THEME")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Th.pals.forEachIndexed { i, p -> Bt(p.name, cf.preset == i) { cf.applyPreset(i) } } }
    Sl("hue", cf.hue, 0f..360f) { cf.hue = it }
    Sl("sat", cf.sat, 0f..1f) { cf.sat = it }
    Sl("font x", cf.fs, .7f..1.6f) { cf.fs = it }
    Sl("pad", cf.pad, 0f..18f) { cf.pad = it }
    Sl("round", cf.rad, 0f..28f) { cf.rad = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("ROUND", "CASUAL", "SANS", "MONO", "SERIF").forEachIndexed { i, s -> Bt(s, cf.font == i) { cf.font = i } } }
    Sec("MOTION" + if (cf.fx < cf.anim) "  (auto-capped for smoothness)" else "")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("OFF", "LOW", "HIGH").forEachIndexed { i, s -> Bt(s, cf.anim == i) { cf.anim = i } } }
    Sl("wave", cf.amp, 0f..12f) { cf.amp = it }
    Sl("speed s", cf.spd, .6f..6f) { cf.spd = it }
    Sl("stagger", cf.stag, 0f..60f) { cf.stag = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Bt("SPARKLE TAPS", cf.sparkle) { cf.sparkle = !cf.sparkle }
      Bt("HAPTIC", cf.haptic) { cf.haptic = !cf.haptic }
      Bt("AUTO QUALITY", cf.auto) { cf.auto = !cf.auto }
    }
    Sec("VIEW")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Bt("HIDDEN", cf.hidden) { cf.hidden = !cf.hidden }
      Bt("SIZE", cf.cSize) { cf.cSize = !cf.cSize }
      Bt("DATE", cf.cDate) { cf.cDate = !cf.cDate }
      Bt("PERM", cf.cPerm) { cf.cPerm = !cf.cPerm }
      Bt("DUAL", cf.dual) { cf.dual = !cf.dual }
      Bt("GRID", cf.grid) { cf.grid = !cf.grid }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      listOf("NAME", "SIZE", "DATE", "EXT").forEachIndexed { i, s -> Bt(s, cf.sort == i) { cf.sort = i } }
      Bt("DESC", cf.desc) { cf.desc = !cf.desc }
    }
    Spacer(Modifier.height(8.dp))
    Bt("RESET LOOK") { cf.reset() }
    Spacer(Modifier.height(8.dp))
  }
}

class Cat(val ic: String, val name: String, val files: List<File>, val bytes: Long)

@Composable fun CleanerScr(back: () -> Unit, root: File, rev: Int, onClean: (String, List<File>, Long) -> Unit) {
  val cf = LCfg.current; val sh = RoundedCornerShape(cf.rad.dp + 4.dp)
  val cats = produceState<List<Cat>?>(null, rev) {
    value = null
    value = withContext(Dispatchers.IO) {
      val q = listOf(
        Triple("🧽", "Junk files", "file ext:tmp,temp,log,bak,old,cache,dmp,crdownload,part,thumbdata"),
        Triple("🤖", "Leftover APKs", "type:apk"),
        Triple("🐘", "Big & old (>100M, >180d)", "size>100m age>180d"),
        Triple("🖼️", "Thumbnail caches", "dir .thumbnails")
      ).map { (ic, n, p) -> val fs = Fx2.collect(root, p, true); Cat(ic, n, fs, fs.sumOf { if (it.isDirectory) Fx.du(it) else it.length() }) }
      q + Cat("📂", "Empty folders", Fx.empties(root), 0L)
    }
  }.value
  Column(Modifier.fillMaxSize()) {
    Hdr("CLEANER", back)
    if (cats == null) { Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Mochi(100.dp, 3); Wave("sniffing for crumbs…", 13f) } } }
    else {
      T("reclaimable: " + Fx.sz(cats.sumOf { it.bytes }), cf.ac2, 12f, FontWeight.Bold, Modifier.padding(bottom = 6.dp))
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.forEachIndexed { i, c ->
          Pop(i) {
            Row(Modifier.fillMaxWidth().clip(sh).background(Th.fg.copy(alpha = .06f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              T(c.ic, Th.fg, 24f)
              Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { T(c.name, Th.fg, 13f, FontWeight.Bold); T("${c.files.size} items · ${Fx.sz(c.bytes)}", Th.dim, 11f) }
              if (c.files.isEmpty()) T("✨ clean", cf.ac, 11f) else Bt("CLEAN", true) { onClean(c.name, c.files, c.bytes) }
            }
          }
        }
      }
    }
  }
}

@Composable fun TrashScr(back: () -> Unit, rev: Int, onRestore: (TItem) -> Unit, onEmpty: () -> Unit) {
  val cf = LCfg.current
  val l = produceState<List<TItem>?>(null, rev) { value = withContext(Dispatchers.IO) { Trash.list() } }.value
  Column(Modifier.fillMaxSize()) {
    Hdr("TRASH", back) { if (!l.isNullOrEmpty()) Bt("EMPTY ${l.size}", true) { onEmpty() } }
    if (l == null) Wave("rummaging…", 13f)
    else if (l.isEmpty()) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Mochi(110.dp, 0); T("trash is empty ♡", Th.dim) }
    else LazyColumn(Modifier.weight(1f)) {
      items(count = l.size, key = { l[it].dir.name }) { i ->
        val t = l[i]
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) { T(t.item.name, Th.fg, 13f, FontWeight.Medium); T(t.orig, Th.dim, 9f) }
          Bt("RESTORE") { onRestore(t) }
        }
      }
    }
  }
}

@Composable fun BreakdownScr(back: () -> Unit, root: File) {
  val cf = LCfg.current
  val scope = rememberCoroutineScope()
  var data by remember { mutableStateOf<Map<String, Long>?>(null) }
  var run by remember { mutableStateOf(false) }
  val g = remember { Animatable(0f) }
  LaunchedEffect(data) { if (data != null) { g.snapTo(0f); g.animateTo(1f, tween(800)) } }
  Column(Modifier.fillMaxSize()) {
    Hdr("BREAKDOWN", back) { Bt(if (run) "…" else "SCAN", true) { if (!run) scope.launch { run = true; data = withContext(Dispatchers.IO) { Fx2.breakdown(root) }; run = false } } }
    val d = data
    if (d == null) { if (run) Wave("measuring everything…", 13f) else T("tap SCAN to see where space goes ♡", Th.dim) }
    else {
      val tot = d.values.sum().coerceAtLeast(1L)
      Canvas(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp))) {
        var x = 0f
        d.entries.forEachIndexed { i, e -> val w = size.width * (e.value.toFloat() / tot) * g.value; drawRect(Color.hsv((cf.hue + i * 45f) % 360f, .55f, 1f), Offset(x, 0f), Size(w, size.height)); x += w }
      }
      Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        d.entries.forEachIndexed { i, e ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.hsv((cf.hue + i * 45f) % 360f, .55f, 1f)))
            T(e.key, Th.fg, 13f, null, Modifier.weight(1f).padding(start = 8.dp))
            T(Fx.sz(e.value) + "  " + (e.value * 100 / tot) + "%", Th.dim, 11f)
          }
        }
        T("total " + Fx.sz(tot), cf.ac2, 12f, FontWeight.Bold)
      }
    }
  }
}

@Composable fun CompareScr(back: () -> Unit, a: File, b: File, rev: Int, onSync: (List<String>, File, File) -> Unit) {
  val cf = LCfg.current
  val c = produceState<Cmp?>(null, a, b, rev) { value = null; value = withContext(Dispatchers.IO) { Fx2.compare(a, b) } }.value
  Column(Modifier.fillMaxSize()) {
    Hdr("COMPARE", back)
    T("A  " + a.path, Th.dim, 10f); T("B  " + b.path, Th.dim, 10f)
    if (c == null) Wave("comparing…", 13f)
    else {
      T("${c.same} same · ${c.diff.size} differ · ${c.onlyA.size} only in A · ${c.onlyB.size} only in B", cf.ac2, 11f, FontWeight.Bold, Modifier.padding(vertical = 6.dp), 2)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Bt("A → B (${c.onlyA.size + c.diff.size})", true) { onSync(c.onlyA + c.diff, a, b) }
        Bt("B → A (${c.onlyB.size + c.diff.size})", true) { onSync(c.onlyB + c.diff, b, a) }
      }
      LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
        val rows = c.onlyA.map { "A+ " + it } + c.onlyB.map { "B+ " + it } + c.diff.map { "≠  " + it }
        items(count = rows.size.coerceAtMost(300), key = { it }) { i -> T(rows[i], Th.fg, 11f, ml = 2) }
      }
    }
  }
}

@Composable fun HashBody(f: File) {
  val cf = LCfg.current
  var exp by remember { mutableStateOf("") }
  val r = produceState(listOf<Pair<String, String>>(), f) {
    val o = ArrayList<Pair<String, String>>()
    for (a in listOf("MD5", "SHA-1", "SHA-256")) { o.add(a to withContext(Dispatchers.IO) { Fx.hash(f, a, Long.MAX_VALUE) }); value = o.toList() }
  }.value
  Inp(exp, { exp = it }, "paste expected hash to verify", Modifier.fillMaxWidth())
  if (r.isEmpty()) Wave("hashing…", 12f)
  r.forEach { (a, h) ->
    val hit = exp.isNotBlank() && h.equals(exp.trim(), true)
    T(a + (if (hit) "  ✓ match!" else ""), if (hit) cf.ac else cf.ac2, 10f, FontWeight.Bold); T(h, Th.fg, 10f, ml = 3)
  }
}

@Composable fun PeekBody(f: File) {
  val l = produceState<List<Pair<String, Long>>?>(null, f) { value = withContext(Dispatchers.IO) { runCatching { Ar.list(f) }.getOrDefault(emptyList()) } }.value
  if (l == null) Wave("peeking inside…", 12f)
  else {
    T("${l.size}${if (l.size >= 1000) "+" else ""} entries · " + Fx.sz(l.sumOf { it.second.coerceAtLeast(0L) }), Th.dim, 10f)
    l.take(150).forEach { (n, s) -> T(n + "  " + Fx.sz(s.coerceAtLeast(0L)), Th.fg, 10f) }
  }
}
