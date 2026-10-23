@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.max

private val CHIPS = listOf("type:img", "type:vid", "type:aud", "type:doc", "type:arc", "type:apk", "type:code",
  "size>10m", "size>100m", "size<10k", "age<1d", "age<7d", "age>365d", "dir", "file", "depth:1", "depth:3")

@Composable fun SearchScr(back: () -> Unit, root: File, hid: Boolean, go: (E) -> Unit) {
  val cf = LCfg.current
  var q by remember { mutableStateOf("") }
  var rx by remember { mutableStateOf(false) }
  var cs by remember { mutableStateOf(false) }
  var res by remember { mutableStateOf(listOf<E>()) }
  var scanned by remember { mutableIntStateOf(0) }
  var ms by remember { mutableLongStateOf(0L) }
  var run by remember { mutableStateOf(false) }
  var bad by remember { mutableStateOf(false) }
  LaunchedEffect(q, rx, cs) {
    res = emptyList(); scanned = 0; bad = false; run = false
    if (q.isBlank()) return@LaunchedEffect
    delay(400)
    val qq = Q(q, rx, cs)
    bad = qq.bad
    if (bad) return@LaunchedEffect
    run = true
    val t0 = System.currentTimeMillis()
    val buf = ArrayList<E>()
    withContext(Dispatchers.IO) {
      Fx.search(root, qq, hid, 3000, { e ->
        buf.add(e)
        if (buf.size >= 40) { val s = buf.toList(); buf.clear(); withContext(Dispatchers.Main) { res = res + s } }
      }, { scanned = it })
    }
    res = res + buf.toList()
    ms = System.currentTimeMillis() - t0
    run = false
  }
  fun tog(t: String) { val l = q.split(" ").filter { it.isNotEmpty() }; q = (if (t in l) l - t else l + t).joinToString(" ") }
  Column(Modifier.fillMaxSize()) {
    Hdr("SEARCH", back)
    Inp(q, { q = it }, "name* -skip  ext:jpg,png  size>10m  age<7d  c:text  type:img  depth:3", Modifier.fillMaxWidth())
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      Bt(".*", rx) { rx = !rx }; Bt("Aa", cs) { cs = !cs }
      if (run) Wave("scan", 11f)
      T(if (bad) "bad regex" else "$scanned scanned · ${res.size} hits · ${ms}ms", Color.Gray, 10f)
    }
    val toks = q.split(" ")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      CHIPS.forEach { c -> Bt(c, c in toks) { tog(c) } }
    }
    LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
      items(res, key = { it.f.path }) { e ->
        Column(Modifier.fillMaxWidth().clickable { go(e) }.padding(vertical = 4.dp, horizontal = 4.dp)) {
          Row {
            T(Fx.gl(e) + " " + e.n, if (e.dir) cf.ac else Color.White, 13f, null, Modifier.weight(1f))
            if (!e.dir) T(Fx.sz(e.sz), Color.Gray, 10f)
          }
          T(e.f.parent ?: "", Color.Gray, 9f)
        }
      }
    }
  }
}

@Composable fun SetScr(back: () -> Unit) {
  val cf = LCfg.current
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Hdr("SETTINGS", back)
    Wave("PREVIEW//Ab12", 22f)
    Sec("THEME")
    Sl("hue", cf.hue, 0f..360f) { cf.hue = it }
    Sl("sat", cf.sat, 0f..1f) { cf.sat = it }
    Sl("bg", cf.bg, 0f..0.15f) { cf.bg = it }
    Sl("font x", cf.fs, .7f..1.6f) { cf.fs = it }
    Sl("pad", cf.pad, 0f..18f) { cf.pad = it }
    Sl("radius", cf.rad, 0f..24f) { cf.rad = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("MONO", "SANS", "SERIF").forEachIndexed { i, s -> Bt(s, cf.font == i) { cf.font = i } } }
    Sec("MOTION")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("OFF", "LOW", "HIGH").forEachIndexed { i, s -> Bt(s, cf.anim == i) { cf.anim = i } } }
    Sl("wave", cf.amp, 0f..12f) { cf.amp = it }
    Sl("speed s", cf.spd, .6f..6f) { cf.spd = it }
    Sl("stagger", cf.stag, 0f..60f) { cf.stag = it }
    Sec("VIEW")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Bt("HIDDEN", cf.hidden) { cf.hidden = !cf.hidden }
      Bt("SIZE", cf.cSize) { cf.cSize = !cf.cSize }
      Bt("DATE", cf.cDate) { cf.cDate = !cf.cDate }
      Bt("PERM", cf.cPerm) { cf.cPerm = !cf.cPerm }
      Bt("DUAL", cf.dual) { cf.dual = !cf.dual }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      listOf("NAME", "SIZE", "DATE", "EXT").forEachIndexed { i, s -> Bt(s, cf.sort == i) { cf.sort = i } }
      Bt("DESC", cf.desc) { cf.desc = !cf.desc }
    }
    Spacer(Modifier.height(8.dp))
    Bt("RESET ALL") { cf.reset() }
  }
}

@Composable fun ToolsScr(back: () -> Unit, nav: (String) -> Unit, act: (String) -> Unit) {
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Hdr("TOOLS", back)
    Cd("⌕ SEARCH", "regex/glob · size · age · type · content grep · depth") { nav("search") }
    Cd("▤ ANALYZER", "biggest items in current folder, live bars") { nav("ana") }
    Cd("⧉ DUPLICATES", "size → MD5(64K) → SHA-1, auto-mark extras") { nav("dupe") }
    Cd("∅ EMPTY FOLDERS", "find & purge empty directory trees") { nav("empty") }
    Cd("✎ BATCH RENAME", "regex + {n} counter on selection") { act("batch") }
    Cd("★ BOOKMARKS", "saved folders") { act("bm") }
    Cd("⚙ SETTINGS", "theme, motion, view — all live") { nav("set") }
  }
}

@Composable fun AnaScr(back: () -> Unit, dir: File, go: (File) -> Unit) {
  val cf = LCfg.current
  val r = produceState<List<Pair<File, Long>>?>(null, dir) {
    value = null
    value = withContext(Dispatchers.IO) { (dir.listFiles() ?: emptyArray()).map { it to Fx.du(it) }.sortedByDescending { it.second } }
  }.value
  var go0 by remember { mutableStateOf(false) }
  LaunchedEffect(r) { go0 = false; delay(30); go0 = true }
  Column(Modifier.fillMaxSize()) {
    Hdr("ANALYZER", back)
    T(dir.path, Color.Gray, 10f)
    if (r == null) Wave("measuring…", 14f) else {
      val tot = r.sumOf { it.second }.coerceAtLeast(1L)
      T("total " + Fx.sz(tot), cf.ac2, 12f)
      LazyColumn(Modifier.weight(1f)) {
        items(r, key = { it.first.path }) { (f, s) ->
          val fr by animateFloatAsState(if (go0) s.toFloat() / tot else 0f, tween(600), label = "fr")
          Box(Modifier.fillMaxWidth().clickable(enabled = f.isDirectory) { go(f) }.padding(vertical = 2.dp)) {
            Box(Modifier.fillMaxWidth(fr.coerceIn(0.005f, 1f)).height(26.dp).background(cf.ac.copy(alpha = .25f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
              T((if (f.isDirectory) "▸ " else "· ") + f.name, Color.White, 12f, null, Modifier.weight(1f))
              T(Fx.sz(s) + "  " + (s * 100 / tot) + "%", Color.Gray, 10f)
            }
          }
        }
      }
    }
  }
}

@Composable fun DupeScr(back: () -> Unit, root: File, hid: Boolean, rev: Int, onDel: (List<File>) -> Unit) {
  val cf = LCfg.current
  var mn by remember { mutableLongStateOf(1024L) }
  var mk by remember { mutableStateOf(setOf<String>()) }
  val g = produceState<List<List<File>>?>(null, mn, rev) { value = null; value = withContext(Dispatchers.IO) { Fx.dupes(root, hid, mn) } }.value
  Column(Modifier.fillMaxSize()) {
    Hdr("DUPLICATES", back)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      T("min", Color.Gray, 11f)
      listOf(1024L to ">1K", 102400L to ">100K", 1048576L to ">1M").forEach { (v, l) -> Bt(l, mn == v) { mn = v } }
    }
    if (g == null) Wave("hashing…", 14f) else {
      Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Bt("AUTO") { mk = g.flatMap { it.drop(1) }.map { it.path }.toSet() }
        Bt("CLEAR") { mk = emptySet() }
        Bt("DEL ${mk.size}", mk.isNotEmpty()) { onDel(g.flatten().filter { it.path in mk }) }
        T("${g.size} grp · ${Fx.sz(g.sumOf { (it.size - 1) * it[0].length() })} wasted", Color.Gray, 10f)
      }
      LazyColumn(Modifier.weight(1f)) {
        g.forEachIndexed { gi, grp ->
          item(key = "h$gi") { T(Fx.sz(grp[0].length()) + " × " + grp.size, cf.ac2, 11f, FontWeight.Bold, Modifier.padding(top = 6.dp)) }
          items(grp, key = { it.path }) { f ->
            val on = f.path in mk
            Box(Modifier.fillMaxWidth().background(if (on) Color.Red.copy(alpha = .22f) else Color.Transparent)
              .clickable { mk = if (on) mk - f.path else mk + f.path }.padding(horizontal = 4.dp, vertical = 3.dp)) {
              T(f.path.removePrefix(root.path), if (on) Color(0xFFFF8080) else Color.White, 11f, ml = 2)
            }
          }
        }
      }
    }
  }
}

@Composable fun EmpScr(back: () -> Unit, root: File, rev: Int, onDel: (List<File>) -> Unit, go: (File) -> Unit) {
  val l = produceState<List<File>?>(null, root, rev) { value = null; value = withContext(Dispatchers.IO) { Fx.empties(root) } }.value
  Column(Modifier.fillMaxSize()) {
    Hdr("EMPTY FOLDERS", back) { if (!l.isNullOrEmpty()) Bt("PURGE ${l.size}", true) { onDel(l) } }
    if (l == null) Wave("scanning…", 14f)
    else if (l.isEmpty()) T("∅ none", Color.Gray)
    else LazyColumn(Modifier.weight(1f)) {
      items(l, key = { it.path }) { f -> Box(Modifier.fillMaxWidth().clickable { go(f.parentFile ?: f) }.padding(4.dp)) { T(f.path.removePrefix(root.path), Color.White, 11f, ml = 2) } }
    }
  }
}

@Composable fun EditScr(back: () -> Unit, f: File, msg: (String) -> Unit) {
  val cf = LCfg.current
  val scope = rememberCoroutineScope()
  val big = f.length() > 262144
  val init = produceState<String?>(null, f) { value = if (big) "" else withContext(Dispatchers.IO) { runCatching { f.readText() }.getOrDefault("") } }.value
  var tx by remember(init) { mutableStateOf(init ?: "") }
  Column(Modifier.fillMaxSize()) {
    Hdr(f.name, back) {
      if (!big && init != null) Bt(if (tx != init) "SAVE ●" else "SAVE", tx != init) {
        scope.launch { withContext(Dispatchers.IO) { f.writeText(tx) }; msg("saved ${tx.length} chars") }
      }
    }
    if (big) T("file > 256 KB · use HEX", Color.Gray)
    else if (init == null) Wave("loading…", 14f)
    else Box(Modifier.weight(1f).fillMaxWidth().background(Color.White.copy(alpha = .04f)).padding(6.dp)) {
      BasicTextField(tx, { tx = it }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        textStyle = TextStyle(color = Color.White, fontSize = (12 * cf.fs).sp, fontFamily = FontFamily.Monospace), cursorBrush = SolidColor(cf.ac))
    }
  }
}

@Composable fun HexScr(back: () -> Unit, f: File) {
  val cf = LCfg.current
  var off by remember { mutableLongStateOf(0L) }
  val lines = produceState(listOf<String>(), f, off) { value = withContext(Dispatchers.IO) { runCatching { Fx.hex(f, off, 2048) }.getOrDefault(emptyList()) } }.value
  Column(Modifier.fillMaxSize()) {
    Hdr("HEX · " + f.name, back)
    Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      Bt("⏮") { off = 0L }; Bt("◂") { off = max(0L, off - 2048) }; Bt("▸") { if (off + 2048 < f.length()) off += 2048 }
      T("0x" + java.lang.Long.toHexString(off) + " / " + Fx.sz(f.length()), cf.ac2, 10f)
    }
    LazyColumn(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
      items(lines) { Text(it, color = Color.White, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, softWrap = false, maxLines = 1) }
    }
  }
}

@Composable fun ImgScr(back: () -> Unit, f: File) {
  val bm = produceState<ImageBitmap?>(null, f) {
    value = withContext(Dispatchers.IO) {
      runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > 1400 || o.outHeight / s > 1400) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
      }.getOrNull()
    }
  }.value
  var sc by remember { mutableFloatStateOf(1f) }
  var off by remember { mutableStateOf(Offset.Zero) }
  Column(Modifier.fillMaxSize()) {
    Hdr(f.name, back)
    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp)).pointerInput(Unit) {
      detectTransformGestures { _, pan, zoom, _ -> sc = (sc * zoom).coerceIn(1f, 8f); off += pan }
    }, Alignment.Center) {
      if (bm == null) Wave("decoding…", 14f)
      else Image(bm, f.name, Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; translationX = off.x; translationY = off.y }, contentScale = ContentScale.Fit)
    }
  }
}

@Composable fun InfoBody(fs: List<File>) {
  val cf = LCfg.current
  val sz = produceState(-1L, fs) { value = withContext(Dispatchers.IO) { fs.sumOf { Fx.du(it) } } }.value
  val n = produceState(-1, fs) { value = withContext(Dispatchers.IO) { fs.sumOf { if (it.isDirectory) it.walkTopDown().count() - 1 else 1 } } }.value
  @Composable fun kv(k: String, v: String) { Row { T(k, Color.Gray, 11f, null, Modifier.width(52.dp)); T(v, Color.White, 11f, ml = 4) } }
  val f = fs[0]
  kv("path", f.parent ?: "/")
  kv("size", if (sz < 0) "…" else Fx.sz(sz) + " (" + sz + " B)")
  kv("files", if (n < 0) "…" else n.toString())
  if (fs.size == 1) { kv("mod", Fx.dt(f.lastModified())); kv("perm", E(f).pm + if (f.isHidden) " hidden" else ""); kv("mime", if (f.isDirectory) "dir" else Fx.mime(f)) }
}

@Composable fun HashBody(f: File) {
  val cf = LCfg.current
  val r = produceState(listOf<Pair<String, String>>(), f) {
    val o = ArrayList<Pair<String, String>>()
    for (a in listOf("MD5", "SHA-1", "SHA-256")) { o.add(a to withContext(Dispatchers.IO) { Fx.hash(f, a, Long.MAX_VALUE) }); value = o.toList() }
  }.value
  if (r.isEmpty()) Wave("hashing…", 12f)
  r.forEach { (a, h) -> T(a, cf.ac2, 10f, FontWeight.Bold); T(h, Color.White, 10f, ml = 3) }
}
