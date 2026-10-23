@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

object Ev { var t by mutableIntStateOf(0) }

class MainActivity : ComponentActivity() {
  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    window.statusBarColor = android.graphics.Color.BLACK
    window.navigationBarColor = android.graphics.Color.BLACK
    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
    if (Build.VERSION.SDK_INT >= 30) {
      if (!Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
    } else requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    val cf = Cfg(this)
    setContent {
      CompositionLocalProvider(LCfg provides cf) {
        MaterialTheme(darkColorScheme(primary = cf.ac, background = Color.Black, surface = Color.Black)) { App(cf) }
      }
    }
  }
  override fun onResume() { super.onResume(); Ev.t++ }
}

@Composable fun App(cf: Cfg) {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val root = remember { Environment.getExternalStorageDirectory() }
  val pa = remember { Pane(root) }
  val pb = remember { Pane(root) }
  var act by remember { mutableIntStateOf(0) }
  var scr by remember { mutableStateOf("fm") }
  var vf by remember { mutableStateOf<File?>(null) }
  var busy by remember { mutableStateOf<String?>(null) }
  var dg by remember { mutableStateOf<Dg?>(null) }
  var clip by remember { mutableStateOf(listOf<File>()) }
  var cut by remember { mutableStateOf(false) }
  var rev by remember { mutableIntStateOf(0) }
  val dual = cf.dual
  val P = if (act == 0 || !dual) pa else pb
  val O = if (P === pa) pb else pa

  fun toast(s: String) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() }
  fun load(p: Pane, d: File) {
    p.job?.cancel()
    p.job = scope.launch {
      val l = withContext(Dispatchers.IO) { Fx.list(d, cf.hidden, cf.sort, cf.desc) }
      p.sel = emptySet(); p.snap = Snap(d, l)
    }
  }
  LaunchedEffect(rev, Ev.t, cf.sort, cf.desc, cf.hidden) { load(pa, pa.dir); load(pb, pb.dir) }

  fun sf(p: Pane): List<File> = p.snap.items.filter { it.f.path in p.sel }.map { it.f }
  fun bg(msg: String, work: () -> String?) {
    scope.launch {
      busy = msg
      val r = try { withContext(Dispatchers.IO) { work() } } catch (t: Throwable) { "✕ " + (t.message ?: t.javaClass.simpleName) }
      busy = null; rev++
      if (r != null) toast(r)
    }
  }
  fun uri(f: File): Uri = FileProvider.getUriForFile(ctx, "dev.nerd.fm.fp", f)
  fun opn(f: File) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), Fx.mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast("no app can open this") }
  }
  fun share(fs: List<File>) {
    try {
      val us = ArrayList(fs.filter { it.isFile }.map { uri(it) })
      if (us.isEmpty()) { toast("select files"); return }
      val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, us).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      ctx.startActivity(Intent.createChooser(i, "share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { toast("✕ " + e.message) }
  }
  fun ask(title: String, init: String, ok: (String) -> Unit) {
    val tx = mutableStateOf(init)
    dg = Dg(title, "OK", { ok(tx.value) }) { Inp(tx.value, { tx.value = it }, "…", Modifier.fillMaxWidth()) }
  }
  fun delDlg(fs: List<File>) {
    if (fs.isEmpty()) { toast("select first"); return }
    dg = Dg("DELETE ${fs.size}?", "DELETE", { bg("deleting…") { fs.forEach { it.deleteRecursively() }; "deleted ${fs.size}" } }) {
      T(fs.take(8).joinToString("\n") { it.name } + (if (fs.size > 8) "\n…" else ""), Color.Gray, 11f, ml = 9)
    }
  }
  fun infoDlg(fs: List<File>) {
    if (fs.isEmpty()) { toast("select first"); return }
    dg = Dg(if (fs.size == 1) fs[0].name else "${fs.size} items", "OK", null) { InfoBody(fs) }
  }
  fun unDlg(f: File) {
    val k = Ar.kind(f)
    if (k.isEmpty()) { toast("not an archive"); return }
    val nm = mutableStateOf(Ar.stem(f)); val ow = mutableStateOf(false); val here = mutableStateOf(false)
    dg = Dg("EXTRACT ${f.name}", "GO", {
      val par = f.parentFile ?: root
      val d = if (here.value) par else File(par, nm.value)
      bg("extracting…") {
        val n = when (k) {
          "zip" -> Ar.unzip(f, d, ow.value)
          "tgz" -> Ar.untar(f, d, ow.value, true)
          "tar" -> Ar.untar(f, d, ow.value, false)
          else -> { Ar.gunzip(f, Fx.uniq(File(par, f.nameWithoutExtension))); 1 }
        }
        "$n file(s) → ${d.name}"
      }
    }) {
      Inp(nm.value, { nm.value = it }, "folder", Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("OVERWRITE", ow.value) { ow.value = !ow.value }
        Bt("HERE (no folder)", here.value) { here.value = !here.value }
      }
    }
  }
  fun zipDlg() {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select first"); return }
    val d = P.dir
    val nm = mutableStateOf(if (fs.size == 1) fs[0].nameWithoutExtension else d.name.ifEmpty { "archive" })
    val fmt = mutableIntStateOf(0); val lv = mutableFloatStateOf(6f)
    val st = mutableStateOf(false); val hd = mutableStateOf(cf.hidden)
    val ex = mutableStateOf(".git,*.tmp,Thumbs.db,.DS_Store"); val cm = mutableStateOf("")
    dg = Dg("COMPRESS ${fs.size} item(s)", "GO", {
      val level = lv.floatValue.roundToInt(); val z = fmt.intValue == 0
      val out = Fx.uniq(File(d, nm.value + (if (z) ".zip" else ".tar.gz")))
      val ef = Ar.exr(ex.value); val store = st.value; val hid = hd.value; val com = cm.value
      bg("compressing…") {
        val sk = if (z) { Ar.zip(fs, out, level, store, ef, hid, com); 0 } else Ar.tgz(fs, out, level, ef, hid)
        val src = fs.sumOf { Fx.du(it) }
        out.name + ": " + Fx.sz(src) + " → " + Fx.sz(out.length()) + " (" + (if (src > 0) out.length() * 100 / src else 0L) + "%)" + (if (sk > 0) " · skipped $sk" else "")
      }
    }) {
      Inp(nm.value, { nm.value = it }, "name", Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("ZIP", fmt.intValue == 0) { fmt.intValue = 0 }
        Bt("TAR.GZ", fmt.intValue == 1) { fmt.intValue = 1 }
        Bt("STORE (zip)", st.value) { st.value = !st.value }
        Bt("HIDDEN", hd.value) { hd.value = !hd.value }
      }
      Sl("level", lv.floatValue, 0f..9f) { lv.floatValue = it.roundToInt().toFloat() }
      Inp(ex.value, { ex.value = it }, "exclude: glob,glob", Modifier.fillMaxWidth())
      Inp(cm.value, { cm.value = it }, "zip comment (optional)", Modifier.fillMaxWidth())
    }
  }
  fun paste() {
    if (clip.isEmpty()) { toast("clipboard empty"); return }
    val d = P.dir; val c = clip; val mv = cut
    bg(if (mv) "moving…" else "copying…") {
      c.forEach { s -> val t = Fx.uniq(File(d, s.name)); if (mv) Fx.mv(s, t) else Fx.cp(s, t) }
      "${c.size} → ${d.name}"
    }
    if (mv) clip = emptyList()
  }
  fun xfer(mv: Boolean) {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select first"); return }
    val d = O.dir
    bg(if (mv) "moving…" else "copying…") {
      fs.forEach { s -> val t = Fx.uniq(File(d, s.name)); if (mv) Fx.mv(s, t) else Fx.cp(s, t) }
      "${fs.size} → ${d.name}"
    }
  }
  fun batch() {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select files first (long-press)"); scr = "fm"; return }
    val fi = mutableStateOf("(.*)"); val re = mutableStateOf("\$1")
    fun nn(f: File, i: Int): String = Regex(fi.value).replace(f.name, re.value.replace("{n}", (i + 1).toString()))
    dg = Dg("BATCH RENAME ${fs.size}", "APPLY", {
      bg("renaming…") {
        var c = 0
        fs.forEachIndexed { i, f -> val n = nn(f, i); if (n.isNotEmpty() && n != f.name && f.renameTo(File(f.parentFile, n))) c++ }
        "renamed $c"
      }
    }) {
      Inp(fi.value, { fi.value = it }, "find (regex)", Modifier.fillMaxWidth())
      Inp(re.value, { re.value = it }, "replace  \$1 {n}", Modifier.fillMaxWidth())
      fs.take(6).forEachIndexed { i, f -> T(f.name + " → " + (runCatching { nn(f, i) }.getOrDefault("?")), Color.Gray, 10f, ml = 2) }
    }
  }
  fun bms(): List<String> = cf.bm.split('\n').filter { it.isNotEmpty() }
  fun bmDlg() {
    dg = Dg("BOOKMARKS", "OK", null) {
      val l = bms()
      if (l.isEmpty()) T("none · tap ☆ to add", Color.Gray)
      l.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          T(p, cf.ac, 12f, null, Modifier.weight(1f).clickable { dg = null; scr = "fm"; load(P, File(p)) }, 2)
          Bt("✕") { cf.bm = bms().filter { it != p }.joinToString("\n"); dg = null }
        }
      }
    }
  }
  fun fileMenu(e: E) {
    val f = e.f; val k = Ar.kind(f)
    dg = Dg(f.name, "CLOSE", null) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("OPEN") { dg = null; opn(f) }
        if (e.sz <= 262144) Bt("EDIT") { dg = null; vf = f; scr = "edit" }
        Bt("HEX") { dg = null; vf = f; scr = "hex" }
        if (e.ex in Fx.IMG) Bt("IMAGE") { dg = null; vf = f; scr = "img" }
        if (k.isNotEmpty()) Bt("EXTRACT") { dg = null; unDlg(f) }
        Bt("HASH") { dg = Dg("CHECKSUMS · " + f.name, "OK", null) { HashBody(f) } }
        Bt("SHARE") { dg = null; share(listOf(f)) }
        Bt("INFO") { infoDlg(listOf(f)) }
        Bt("SHOW") { dg = null; scr = "fm"; load(P, f.parentFile ?: root) }
      }
    }
  }
  fun togg(p: Pane, e: E) { p.sel = if (e.f.path in p.sel) p.sel - e.f.path else p.sel + e.f.path }
  fun tapE(p: Pane, e: E) { if (p.sel.isNotEmpty()) togg(p, e) else if (e.dir) load(p, e.f) else fileMenu(e) }

  BackHandler(scr != "fm" || P.sel.isNotEmpty() || P.dir.path != root.path) {
    if (scr != "fm") scr = "fm" else if (P.sel.isNotEmpty()) P.sel = emptySet() else load(P, P.dir.parentFile ?: root)
  }

  Column(Modifier.fillMaxSize().background(cf.bgc).padding(6.dp)) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      AnimatedContent(scr, transitionSpec = {
        if (cf.anim == 0) EnterTransition.None togetherWith ExitTransition.None
        else (fadeIn(tween(170)) + slideInVertically(tween(220)) { it / 14 }) togetherWith fadeOut(tween(90))
      }, label = "scr") { s ->
        Column(Modifier.fillMaxSize()) {
          when (s) {
            "fm" -> {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Wave("NERD//FM", 18f); Spacer(Modifier.weight(1f))
                Bt("⌕") { scr = "search" }; Spacer(Modifier.width(4.dp))
                Bt("⚒") { scr = "tools" }; Spacer(Modifier.width(4.dp))
                Bt("⚙") { scr = "set" }
              }
              Spacer(Modifier.height(4.dp)); Stor(); Spacer(Modifier.height(4.dp))
              PaneView(pa, act == 0 || !dual, Modifier.weight(1f), { act = 0; load(pa, it) }, { e -> act = 0; tapE(pa, e) }, { e -> act = 0; togg(pa, e) })
              if (dual) {
                Spacer(Modifier.height(4.dp))
                PaneView(pb, act == 1, Modifier.weight(1f), { act = 1; load(pb, it) }, { e -> act = 1; tapE(pb, e) }, { e -> act = 1; togg(pb, e) })
              }
              val b = busy
              if (b != null) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(Modifier.weight(1f), color = cf.ac, trackColor = Color.White.copy(alpha = .1f))
                T(b, cf.ac, 11f, null, Modifier.padding(start = 8.dp))
              }
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (P.sel.isNotEmpty()) {
                  Bt("COPY") { clip = sf(P); cut = false; P.sel = emptySet(); toast("${clip.size} copied") }
                  Bt("CUT") { clip = sf(P); cut = true; P.sel = emptySet(); toast("${clip.size} cut") }
                  if (clip.isNotEmpty()) Bt("PASTE ${clip.size}", true) { paste() }
                  Bt("DEL") { delDlg(sf(P)) }
                  if (P.sel.size == 1) Bt("REN") {
                    val f = sf(P)[0]
                    ask("RENAME", f.name) { n -> bg("renaming…") { if (n.isNotBlank() && !File(f.parentFile, n).exists()) { f.renameTo(File(f.parentFile, n)); null } else "✕ invalid or exists" } }
                  }
                  Bt("ZIP") { zipDlg() }
                  Bt("SHARE") { share(sf(P)) }
                  Bt("INFO") { infoDlg(sf(P)) }
                  Bt("BATCH") { batch() }
                  if (dual) { Bt("⇉COPY") { xfer(false) }; Bt("⇉MOVE") { xfer(true) } }
                  Bt("ALL") { P.sel = P.snap.items.map { it.f.path }.toSet() }
                  Bt("INV") { P.sel = P.snap.items.map { it.f.path }.filter { it !in P.sel }.toSet() }
                  Bt("✕") { P.sel = emptySet() }
                } else {
                  val d = P.dir
                  Bt("+DIR") { ask("NEW FOLDER", "") { n -> bg("mkdir…") { File(d, n).mkdirs(); null } } }
                  Bt("+FILE") { ask("NEW FILE", "") { n -> bg("touch…") { File(d, n).createNewFile(); null } } }
                  if (clip.isNotEmpty()) Bt("PASTE ${clip.size}", true) { paste() }
                  Bt("ALL") { P.sel = P.snap.items.map { it.f.path }.toSet() }
                  Bt(listOf("NAME", "SIZE", "DATE", "EXT")[cf.sort.coerceIn(0, 3)] + (if (cf.desc) "↓" else "↑")) { cf.sort = (cf.sort + 1) % 4 }
                  Bt("↕") { cf.desc = !cf.desc }
                  Bt(".HID", cf.hidden) { cf.hidden = !cf.hidden }
                  Bt("DUAL", cf.dual) { cf.dual = !cf.dual }
                  if (dual) Bt("⇄") { load(O, P.dir) }
                  val cur = d.path
                  Bt(if (cur in bms()) "★" else "☆", cur in bms()) { cf.bm = (if (cur in bms()) bms() - cur else bms() + cur).joinToString("\n") }
                  Bt("BM") { bmDlg() }
                }
              }
            }
            "search" -> SearchScr({ scr = "fm" }, P.dir, cf.hidden) { e -> if (e.dir) { load(P, e.f); scr = "fm" } else fileMenu(e) }
            "set" -> SetScr { scr = "fm" }
            "tools" -> ToolsScr({ scr = "fm" }, { scr = it }, { a -> if (a == "bm") bmDlg() else batch() })
            "ana" -> AnaScr({ scr = "fm" }, P.dir) { f -> load(P, f); scr = "fm" }
            "dupe" -> DupeScr({ scr = "fm" }, P.dir, cf.hidden, rev) { fs -> delDlg(fs) }
            "empty" -> EmpScr({ scr = "fm" }, P.dir, rev, { fs -> delDlg(fs) }) { f -> load(P, f); scr = "fm" }
            "edit" -> vf?.let { f -> EditScr({ scr = "fm" }, f) { m -> toast(m); rev++ } }
            "hex" -> vf?.let { f -> HexScr({ scr = "fm" }, f) }
            "img" -> vf?.let { f -> ImgScr({ scr = "fm" }, f) }
            else -> {}
          }
        }
      }
    }
  }
  dg?.let { d -> Dlg(d) { dg = null } }
}
