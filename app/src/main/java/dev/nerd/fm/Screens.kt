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
    T(dir.path, Th.dim, 10f)
    if (r == null) Wave("measuring…", 14f) else {
      val tot = r.sumOf { it.second }.coerceAtLeast(1L)
      T("total " + Fx.sz(tot), cf.ac2, 12f)
      LazyColumn(Modifier.weight(1f)) {
        items(r, key = { it.first.path }) { (f, s) ->
          val fr by animateFloatAsState(if (go0) s.toFloat() / tot else 0f, tween(600), label = "fr")
          Box(Modifier.fillMaxWidth().clickable(enabled = f.isDirectory) { go(f) }.padding(vertical = 2.dp)) {
            Box(Modifier.fillMaxWidth(fr.coerceIn(0.005f, 1f)).height(26.dp).background(cf.ac.copy(alpha = .25f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
              T((if (f.isDirectory) "▸ " else "· ") + f.name, Th.fg, 12f, null, Modifier.weight(1f))
              T(Fx.sz(s) + "  " + (s * 100 / tot) + "%", Th.dim, 10f)
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
      T("min", Th.dim, 11f)
      listOf(1024L to ">1K", 102400L to ">100K", 1048576L to ">1M").forEach { (v, l) -> Bt(l, mn == v) { mn = v } }
    }
    if (g == null) Wave("hashing…", 14f) else {
      Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Bt("AUTO") { mk = g.flatMap { it.drop(1) }.map { it.path }.toSet() }
        Bt("CLEAR") { mk = emptySet() }
        Bt("DEL ${mk.size}", mk.isNotEmpty()) { onDel(g.flatten().filter { it.path in mk }) }
        T("${g.size} grp · ${Fx.sz(g.sumOf { (it.size - 1) * it[0].length() })} wasted", Th.dim, 10f)
      }
      LazyColumn(Modifier.weight(1f)) {
        g.forEachIndexed { gi, grp ->
          item(key = "h$gi") { T(Fx.sz(grp[0].length()) + " × " + grp.size, cf.ac2, 11f, FontWeight.Bold, Modifier.padding(top = 6.dp)) }
          items(grp, key = { it.path }) { f ->
            val on = f.path in mk
            Box(Modifier.fillMaxWidth().background(if (on) Color.Red.copy(alpha = .22f) else Color.Transparent)
              .clickable { mk = if (on) mk - f.path else mk + f.path }.padding(horizontal = 4.dp, vertical = 3.dp)) {
              T(f.path.removePrefix(root.path), if (on) Color(0xFFFF8080) else Th.fg, 11f, ml = 2)
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
    else if (l.isEmpty()) T("∅ none", Th.dim)
    else LazyColumn(Modifier.weight(1f)) {
      items(l, key = { it.path }) { f -> Box(Modifier.fillMaxWidth().clickable { go(f.parentFile ?: f) }.padding(4.dp)) { T(f.path.removePrefix(root.path), Th.fg, 11f, ml = 2) } }
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
    if (big) T("file > 256 KB · use HEX", Th.dim)
    else if (init == null) Wave("loading…", 14f)
    else Box(Modifier.weight(1f).fillMaxWidth().background(Th.fg.copy(alpha = .04f)).padding(6.dp)) {
      BasicTextField(tx, { tx = it }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        textStyle = TextStyle(color = Th.fg, fontSize = (12 * cf.fs).sp, fontFamily = FontFamily.Monospace), cursorBrush = SolidColor(cf.ac))
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
      items(lines) { Text(it, color = Th.fg, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, softWrap = false, maxLines = 1) }
    }
  }
}

@Composable fun InfoBody(fs: List<File>) {
  val cf = LCfg.current
  val sz = produceState(-1L, fs) { value = withContext(Dispatchers.IO) { fs.sumOf { Fx.du(it) } } }.value
  val n = produceState(-1, fs) { value = withContext(Dispatchers.IO) { fs.sumOf { if (it.isDirectory) it.walkTopDown().count() - 1 else 1 } } }.value
  @Composable fun kv(k: String, v: String) { Row { T(k, Th.dim, 11f, null, Modifier.width(52.dp)); T(v, Th.fg, 11f, ml = 4) } }
  val f = fs[0]
  kv("path", f.parent ?: "/")
  kv("size", if (sz < 0) "…" else Fx.sz(sz) + " (" + sz + " B)")
  kv("files", if (n < 0) "…" else n.toString())
  if (fs.size == 1 && f.isFile && f.extension.lowercase() in Fx.TXT && f.length() < 2_000_000) {
    val st = produceState("", f) { value = withContext(Dispatchers.IO) { runCatching { val tx = f.readText(); "${tx.count { it == '\n' } + 1} lines · ${tx.split(Regex("\\s+")).count { it.isNotEmpty() }} words · ${tx.length} chars" }.getOrDefault("") } }.value
    if (st.isNotEmpty()) kv("text", st)
  }
  if (fs.size == 1) { kv("mod", Fx.dt(f.lastModified())); kv("perm", E(f).pm + if (f.isHidden) " hidden" else ""); kv("mime", if (f.isDirectory) "dir" else Fx.mime(f)) }
}
