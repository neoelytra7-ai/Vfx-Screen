@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File

private val CHIPS = listOf("type:img", "type:vid", "type:aud", "type:doc", "type:arc", "type:apk", "type:code", "ext:jpg,png", "ext:pdf", "ext:zip,rar,7z",
  "size>10m", "size>100m", "size<10k", "age<1d", "age<7d", "age>365d", "dir", "file", "depth:1", "depth:3")
private val SMART = listOf(
  Triple("clock", "Recent 3d", "file age<3d"), Triple("photo", "Photos", "type:img"), Triple("video", "Videos", "type:vid"), Triple("music", "Music", "type:aud"),
  Triple("big", "Big >50M", "size>50m"), Triple("box", "Archives", "type:arc"), Triple("droid", "APKs", "type:apk"), Triple("doc", "Docs", "type:doc"),
  Triple("trash", "Junk", "file ext:tmp,log,bak,cache,part"), Triple("sort", "Screenshots", "screenshot type:img"), Triple("down", "Old >1y", "file age>365d"), Triple("dup", "Empty files", "file size<2"))

@Composable fun ResRow(e: E, sel: Boolean, tap: () -> Unit, lng: () -> Unit) {
  val cf = LCfg.current
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) cf.ac.copy(alpha = .2f) else Color.Transparent)
    .combinedClickable(onClick = tap, onLongClick = lng).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Ico(e, 28.dp, sel)
    Column(Modifier.weight(1f).padding(start = 8.dp)) {
      T(e.n, if (e.dir) cf.ac else Th.fg, 13f, FontWeight.Medium)
      T(e.f.parent ?: "", Th.dim, 9f)
    }
    if (!e.dir) T(Fx.sz(e.sz), Th.dim, 10f)
  }
}

@Composable fun SearchScr(here: File, all: File, hid: Boolean, initQ: String, go: (E) -> Unit, act: (String, List<File>) -> Unit) {
  val cf = LCfg.current
  val scope = rememberCoroutineScope()
  var q by remember { mutableStateOf(initQ.removePrefix("~")) }
  var wide by remember { mutableStateOf(initQ.isNotEmpty() && !initQ.startsWith("~")) }
  var rx by remember { mutableStateOf(false) }
  var cs by remember { mutableStateOf(false) }
  var fz by remember { mutableStateOf(false) }
  var useIdx by remember { mutableStateOf(Idx.count > 0) }
  var building by remember { mutableStateOf(false) }
  var res by remember { mutableStateOf(listOf<E>()) }
  var scanned by remember { mutableIntStateOf(0) }
  var ms by remember { mutableLongStateOf(0L) }
  var run by remember { mutableStateOf(false) }
  var bad by remember { mutableStateOf(false) }
  var sortMode by remember { mutableIntStateOf(0) }
  var sel by remember { mutableStateOf(setOf<String>()) }
  val root = if (wide) all else here
  LaunchedEffect(q, rx, cs, fz, wide, useIdx) {
    res = emptyList(); sel = emptySet(); scanned = 0; bad = false; run = false
    if (q.isBlank()) return@LaunchedEffect
    delay(350)
    val qq = Q(q, rx, cs, fz)
    bad = qq.bad
    if (bad) return@LaunchedEffect
    run = true
    val t0 = System.currentTimeMillis()
    val buf = ArrayList<E>()
    val hit: suspend (E) -> Unit = { e ->
      buf.add(e)
      if (buf.size >= 40) { val s = buf.toList(); buf.clear(); withContext(Dispatchers.Main) { res = res + s } }
    }
    withContext(Dispatchers.IO) {
      if (useIdx && Idx.count > 0) Fx2.searchIdx(root.path, qq, 4000, hit) { scanned = it }
      else Fx.search(root, qq, hid, 4000, hit) { scanned = it }
    }
    res = res + buf.toList()
    ms = System.currentTimeMillis() - t0
    run = false
    val qt = q.trim()
    if (qt.length > 2 && res.isNotEmpty()) cf.qh = (listOf(qt) + cf.qh.split('\n').filter { it.isNotEmpty() && it != qt }).take(6).joinToString("\n")
  }
  fun tog(t: String) { val l = q.split(" ").filter { it.isNotEmpty() }; q = (if (t in l) l - t else l + t).joinToString(" ") }
  val shown = remember(res, sortMode) { when (sortMode) { 1 -> res.sortedBy { it.n.lowercase() }; 2 -> res.sortedByDescending { it.sz }; 3 -> res.sortedByDescending { it.mt }; else -> res } }
  fun picked(): List<File> = shown.filter { it.f.path in sel }.map { it.f }
  Column(Modifier.fillMaxSize()) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
      T("FIND", cf.ac, 15f, FontWeight.Bold)
      T(if (bad) "bad regex" else if (q.isBlank()) (if (Idx.count > 0) "indexed ${Idx.count}" else "type or tap a shortcut") else "$scanned scanned · ${shown.size} hits · ${ms}ms" + (if (run) " …" else ""), Th.dim, 10f, null, Modifier.weight(1f))
    }
    Inp(q, { q = it }, "name* -skip  ext:jpg,png  size>10m  age<7d  c:text  type:img  depth:3", Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      Bt(".*", rx) { rx = !rx }; Bt("Aa", cs) { cs = !cs }; Bt("~ fuzzy", fz) { fz = !fz }
      Bt(if (wide) "ALL" else "HERE", wide) { wide = !wide }
      Bt(if (building) "indexing…" else "⚡ IDX", useIdx && Idx.count > 0, subs = listOf<Pair<String, () -> Unit>>(
        "refresh|rebuild index" to { if (!building) scope.launch { building = true; withContext(Dispatchers.IO) { Idx.build(all, hid) }; building = false; useIdx = true } },
        "x|clear index" to { Idx.clear(); useIdx = false })) {
        if (building) return@Bt
        if (Idx.count == 0) scope.launch { building = true; withContext(Dispatchers.IO) { Idx.build(all, hid) }; building = false; useIdx = true } else useIdx = !useIdx
      }
      if (shown.isNotEmpty()) { T("│", Th.dim); listOf("order", "A-Z", "size", "date").forEachIndexed { i, s -> Bt(s, sortMode == i) { sortMode = i } } }
    }
    if (sel.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      T("♡ ${sel.size}", cf.ac2, 12f, FontWeight.Bold)
      Bt("COPY", true) { act("copy", picked()); sel = emptySet() }; Bt("CUT") { act("cut", picked()); sel = emptySet() }
      Bt("TRASH") { act("trash", picked()); sel = emptySet() }; Bt("SHARE") { act("share", picked()) }
      Bt("ALL") { sel = shown.map { it.f.path }.toSet() }; Bt("✕") { sel = emptySet() }
    }
    if (q.isBlank()) {
      val hq = cf.qh.split('\n').filter { it.isNotEmpty() }
      if (hq.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { hq.forEach { h -> Bt("↺ $h") { q = h } } }
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SMART.chunked(3).forEachIndexed { r, row ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { c, t -> Tile(t.first, t.second, r * 3 + c, Modifier.weight(1f), emptyList()) { q = t.third } }
          }
        }
        T("tip: hold ⚡ IDX for instant name search · ~ fuzzy finds 'mnrft' → minecraft", Th.dim, 9f, ml = 2)
      }
    } else {
      val toks = q.split(" ")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { CHIPS.forEach { c -> Bt(c, c in toks) { tog(c) } } }
      LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
        items(count = shown.size, key = { shown[it].f.path }) { i ->
          val e = shown[i]
          ResRow(e, e.f.path in sel, { if (sel.isNotEmpty()) sel = if (e.f.path in sel) sel - e.f.path else sel + e.f.path else go(e) }, { sel = if (e.f.path in sel) sel - e.f.path else sel + e.f.path })
        }
      }
    }
  }
}
