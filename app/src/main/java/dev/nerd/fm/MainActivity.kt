@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.media.MediaScannerConnection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

object Ev { var t by mutableIntStateOf(0) }
class Boot { var loaded by mutableIntStateOf(0) }
private val BackE = CubicBezierEasing(.34f, 1.56f, .64f, 1f)
fun hasAccess(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

class MainActivity : ComponentActivity() {
  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
    try {
      val m = windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate }
      if (m != null) window.attributes = window.attributes.also { it.preferredDisplayModeId = m.modeId }
    } catch (e: Throwable) { }
    if (Build.VERSION.SDK_INT < 30) requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    val cf = Cfg(this)
    setContent { Root(cf) }
  }
  override fun onResume() { super.onResume(); Ev.t++ }
}

@Composable fun Root(cf: Cfg) {
  val act = LocalContext.current as Activity
  val vc = LocalViewConfiguration.current
  val fastVc = remember(vc) { object : ViewConfiguration by vc { override val longPressTimeoutMillis: Long get() = 230L } }
  val ck = rememberClock(cf.fx > 0)
  var entered by remember { mutableStateOf(false) }
  val boot = remember { Boot() }
  var access by remember { mutableStateOf(hasAccess()) }
  LaunchedEffect(Ev.t) { access = hasAccess() }
  val aura = Sk.f("aura.drift", 0f) > 0f
  LaunchedEffect(aura) { while (true) { if (aura) Aura.tick(); delay(60000L) } }
  SideEffect {
    act.window.statusBarColor = Th.bg.toArgb(); act.window.navigationBarColor = Th.bg.toArgb()
    WindowCompat.getInsetsController(act.window, act.window.decorView).apply { isAppearanceLightStatusBars = Th.pal.light; isAppearanceLightNavigationBars = Th.pal.light }
  }
  val rv = remember { Animatable(0f) }
  LaunchedEffect(entered) { if (entered) rv.animateTo(1f, spring(.6f, 200f)) }
  CompositionLocalProvider(LCfg provides cf, LClock provides ck, LocalViewConfiguration provides fastVc) {
    MaterialTheme(darkColorScheme(primary = cf.ac, background = Th.bg, surface = Th.bg)) {
      val gr = Sk.f("bg.grad", 0f)
      Box(Modifier.fillMaxSize().background(if (gr > 0f) Brush.verticalGradient(listOf(cf.bgc, cf.ac.copy(alpha = .22f * gr).compositeOver(cf.bgc))) else SolidColor(cf.bgc))) {
        if (Sk.b("bg.decor", false)) Decor()
        Box(Modifier.fillMaxSize().graphicsLayer { val s = .94f + .06f * rv.value; scaleX = s; scaleY = s; alpha = rv.value.coerceIn(0f, 1f) }) { App(cf, boot) }
        AnimatedVisibility(!entered, exit = fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.2f)) {
          Welcome(boot.loaded > 0, access, {
            try { act.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + act.packageName))) }
            catch (e: Exception) { act.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
          }) { entered = true }
        }
      }
    }
  }
}

@Composable fun App(cf: Cfg, boot: Boot) {
  val ctx = LocalContext.current
  val scope = rememberCoroutineScope()
  val root = remember { Environment.getExternalStorageDirectory() }
  val panes = remember { mutableStateListOf<Pane>().also { l -> val sv = cf.tabs.split('\n').filter { it.isNotEmpty() }.map { File(it) }.filter { it.isDirectory }; if (sv.isEmpty()) l.add(Pane(root)) else sv.forEach { l.add(Pane(it)) } } }
  val burst = remember { Burst() }
  val lt = remember { floatArrayOf(300f, 500f) }
  val pager = rememberPagerState(pageCount = { panes.size })
  var tab by remember { mutableIntStateOf(0) }
  var sub by remember { mutableStateOf<String?>(null) }
  var vf by remember { mutableStateOf<File?>(null) }
  var pendingQ by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf<String?>(null) }
  var snack by remember { mutableStateOf<String?>(null) }
  var snackAct by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
  val clipb = LocalClipboardManager.current
  var dg by remember { mutableStateOf<Dg?>(null) }
  var clip by remember { mutableStateOf(listOf<File>()) }
  var cut by remember { mutableStateOf(false) }
  var rev by remember { mutableIntStateOf(0) }
  val act = pager.currentPage.coerceIn(0, panes.size - 1)
  val P = panes[act]
  var navVis by remember { mutableStateOf(true) }
  val navConn = remember { object : NestedScrollConnection { override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset { if (cf.navHide) { if (available.y < -14f) navVis = false else if (available.y > 14f) navVis = true }; return Offset.Zero } } }

  LaunchedEffect(snack) { if (snack != null) { delay(if (snackAct != null) 5000L else 2400L); snack = null; snackAct = null } }
  fun toast(s: String) { snack = s; snackAct = null }
  fun okNm(n: String) = n.isNotBlank() && '/' !in n && n.trim() != "." && n.trim() != ".."
  fun cheer(n: Int) { burst.emit(lt[0], lt[1].coerceAtLeast(200f), n, -1, cf.ac) }
  fun load(p: Pane, d: File, push: Boolean = true) {
    if (push && p.hist.lastOrNull() != d) { while (p.hist.size > p.hi + 1) p.hist.removeAt(p.hist.lastIndex); p.hist.add(d); p.hi = p.hist.lastIndex }
    p.job?.cancel()
    p.job = scope.launch {
      val l = withContext(Dispatchers.IO) { Fx.list(d, cf.hidden, cf.sort, cf.desc) }
      val ps = HashSet<String>(); if (p.sel.isNotEmpty() && p.snap.dir == d) l.forEach { ps.add(it.f.path) }
      p.sel = if (ps.isEmpty()) emptySet() else p.sel.filterTo(HashSet<String>()) { it in ps }; p.snap = Snap(d, l); boot.loaded++; cf.tabs = panes.joinToString("\n") { it.dir.path }
    }
  }
  LaunchedEffect(rev, Ev.t, cf.sort, cf.desc, cf.hidden) { panes.toList().forEach { load(it, it.dir, false) } }

  fun sf(p: Pane): List<File> = p.snap.items.filter { it.f.path in p.sel }.map { it.f }
  fun bg(msg: String, xp: Int = 0, undo: (() -> Unit)? = null, work: () -> String?) {
    scope.launch {
      busy = msg
      val r = try { withContext(Dispatchers.IO) { work() } } catch (t: Throwable) { "✕ " + (t.message ?: t.javaClass.simpleName) }
      busy = null; panes.forEach { it.sel = emptySet() }; rev++
      val before = cf.level; cf.addXp(xp)
      if (r != null) { snack = r; snackAct = if (undo != null && !r.startsWith("✕")) ("UNDO" to undo) else null; if (!r.startsWith("✕")) cheer(10) }
      if (cf.level > before) { snack = "level up! Lv ${cf.level} ♡"; cheer(28) }
    }
  }
  fun uri(f: File): Uri = FileProvider.getUriForFile(ctx, "dev.nerd.fm.fp", f)
  fun opn(f: File, mime: String = Fx.mime(f)) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
  fun delDlg(fs: List<File>, m0: Int = 0) {
    if (fs.isEmpty()) { toast("select first"); return }
    val mode = mutableIntStateOf(if (fs.any { it.path.startsWith(Trash.root.path) }) 1 else m0)
    dg = Dg("DELETE ${fs.size}?", "GO", {
      val m = mode.intValue; val tr = java.util.concurrent.CopyOnWriteArrayList<File>()
      bg(if (m == 0) "tidying away…" else "deleting…", fs.size * 2, if (m == 0) ({ bg("restoring…") { Trash.undo(tr); "restored ${tr.size}" } }) else null) {
        for (f in fs) { if (m == 0) tr.add(Trash.put(f)) else if (m == 1) f.deleteRecursively() else Fx2.shred(f) }
        if (m == 0) "moved ${fs.size} to trash" else "deleted ${fs.size}"
      }
    }) {
      T(fs.take(8).joinToString("\n") { it.name } + (if (fs.size > 8) "\n…" else ""), Th.dim, 11f, ml = 9)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("TRASH", mode.intValue == 0) { mode.intValue = 0 }
        Bt("FOREVER", mode.intValue == 1) { mode.intValue = 1 }
        Bt("SHRED", mode.intValue == 2) { mode.intValue = 2 }
      }
    }
  }
  fun cleanDlg(name: String, fs: List<File>, bytes: Long) {
    val mode = mutableIntStateOf(1)
    dg = Dg("CLEAN $name", "SWEEP", {
      val m = mode.intValue; cf.cleaned += bytes
      bg("sweeping…", (bytes / 1048576L).toInt().coerceIn(2, 200)) {
        for (f in fs) { if (m == 0) Trash.put(f) else f.deleteRecursively() }
        "swept ${fs.size} · ${Fx.sz(bytes)} ♡"
      }
    }) {
      T("${fs.size} items · ${Fx.sz(bytes)}", Th.fg, 12f)
      T(fs.take(6).joinToString("\n") { it.name }, Th.dim, 10f, ml = 7)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Bt("FOREVER", mode.intValue == 1) { mode.intValue = 1 }; Bt("TO TRASH", mode.intValue == 0) { mode.intValue = 0 } }
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
      bg("extracting…", 4) {
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
      bg("compressing…", 5) {
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
  fun squishDlg() {
    val fs = sf(P).filter { it.extension.lowercase() in Fx.IMG }
    if (fs.isEmpty()) { toast("select photos in FILES first"); return }
    val mx = mutableFloatStateOf(1280f); val q = mutableFloatStateOf(80f); val wp = mutableStateOf(false); val del = mutableStateOf(false)
    dg = Dg("SQUISH ${fs.size} photo(s)", "SQUISH", {
      val m = mx.floatValue.roundToInt(); val qq = q.floatValue.roundToInt(); val w = wp.value; val dl = del.value
      bg("squishing…", fs.size * 3) {
        var before = 0L; var after = 0L
        for (f in fs) { before += f.length(); val o = Fx2.squish(f, m, qq, w); after += o.length(); if (dl) f.delete() }
        "saved ${Fx.sz((before - after).coerceAtLeast(0L))} ♡"
      }
    }) {
      Sl("max px", mx.floatValue, 256f..4096f) { mx.floatValue = (it / 64f).roundToInt() * 64f }
      Sl("quality", q.floatValue, 30f..100f) { q.floatValue = it.roundToInt().toFloat() }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("JPG", !wp.value) { wp.value = false }; Bt("WEBP", wp.value) { wp.value = true }; Bt("DELETE ORIGINALS", del.value) { del.value = !del.value }
      }
    }
  }
  fun paste(p: Pane = P) {
    if (clip.isEmpty()) { toast("clipboard empty"); return }
    val d = p.dir; val c = clip; val mv = cut
    bg(if (mv) "moving…" else "copying…", 3) {
      c.forEach { s -> val t = Fx.uniq(File(d, s.name)); if (mv) Fx.mv(s, t) else Fx.cp(s, t) }
      "${c.size} → ${d.name}"
    }
    if (mv) clip = emptyList()
  }
  fun xferTo(t: Pane, mv: Boolean) {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select first"); return }
    val d = t.dir
    bg(if (mv) "moving…" else "copying…", 3) {
      fs.forEach { s -> val x = Fx.uniq(File(d, s.name)); if (mv) Fx.mv(s, x) else Fx.cp(s, x) }
      "${fs.size} → ${d.name}"
    }
  }
  fun batch() {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select files in FILES first (long-press)"); return }
    val fi = mutableStateOf("(.*)"); val re = mutableStateOf("\$1"); val tf = mutableIntStateOf(0)
    fun nn(f: File, i: Int): String {
      val dt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(f.lastModified()))
      val n = Regex(fi.value).replace(f.name, re.value.replace("{n}", (i + 1).toString()).replace("{date}", dt).replace("{ext}", f.extension))
      return when (tf.intValue) {
        1 -> n.uppercase(); 2 -> n.lowercase()
        3 -> n.split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
        4 -> n.replace(' ', '_'); 5 -> n.replace(Regex("[^A-Za-z0-9._-]+"), "_")
        else -> n
      }
    }
    dg = Dg("BATCH RENAME ${fs.size}", "APPLY", {
      bg("renaming…", 2) {
        var c = 0
        fs.forEachIndexed { i, f -> val n = nn(f, i); if (okNm(n) && n != f.name) { val t = File(f.parentFile, n); if (f.renameTo(if (t.exists()) Fx.uniq(t) else t)) c++ } }
        "renamed $c"
      }
    }) {
      Inp(fi.value, { fi.value = it }, "find (regex)", Modifier.fillMaxWidth())
      Inp(re.value, { re.value = it }, "replace  \$1 {n} {date} {ext}", Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("{n}") { re.value += "{n}" }; Bt("{date}") { re.value += "{date}" }; Bt("{ext}") { re.value += "{ext}" }
        listOf("as is", "UPPER", "lower", "Title", "a_b", "clean").forEachIndexed { i, s -> Bt(s, tf.intValue == i) { tf.intValue = i } }
      }
      fs.take(6).forEachIndexed { i, f -> T(f.name + " → " + (runCatching { nn(f, i) }.getOrDefault("?")), Th.dim, 10f, ml = 2) }
    }
  }
  fun bms(): List<String> = cf.bm.split('\n').filter { it.isNotEmpty() }
  fun bmDlg() {
    dg = Dg("BOOKMARKS", "OK", null) {
      val l = bms()
      if (l.isEmpty()) T("none · tap ☆ in FILES to add", Th.dim)
      l.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          T(p, cf.ac, 12f, null, Modifier.weight(1f).clickable { dg = null; sub = null; tab = 1; load(P, File(p)) }, 2)
          Bt("✕") { cf.bm = bms().filter { it != p }.joinToString("\n"); dg = null }
        }
      }
    }
  }
  fun otherPane(): Pane? = if (panes.size > 1) panes[(act + 1) % panes.size] else null
  fun saveTabs() { cf.tabs = panes.joinToString("\n") { it.dir.path } }
  fun addTab(d: File) { val np = Pane(d); panes.add(np); load(np, d, false); scope.launch { delay(80); pager.animateScrollToPage(panes.size - 1) } }
  fun closeTab(i: Int) { if (panes.size > 1) { panes.removeAt(i); saveTabs() } else toast("last tab") }
  fun closeOthers(i: Int) { val k = panes[i]; panes.clear(); panes.add(k); saveTabs() }
  fun closeRight(i: Int) { while (panes.size > i + 1) panes.removeAt(panes.lastIndex); saveTabs() }
  fun dupFiles(fs: List<File>) { if (fs.isEmpty()) { toast("select first"); return }; bg("duplicating…", 2) { fs.forEach { Fx.cp(it, Fx.uniq(File(it.parentFile, it.name))) }; "duplicated ${fs.size}" } }
  fun scanMedia(fs: List<File>) { val ps = fs.filter { it.isFile }.map { it.path }.toTypedArray(); if (ps.isEmpty()) { toast("select files"); return }; MediaScannerConnection.scanFile(ctx, ps, null, null); toast("scanning ${ps.size} file(s)") }
  fun newFromClip() { val t = clipb.getText()?.text ?: ""; if (t.isEmpty()) toast("clipboard is empty") else { val d = P.dir; ask("FILE NAME", "clip.txt") { n -> bg("writing…") { File(d, n).writeText(t); null } } } }
  fun permsDlg(f: File) { dg = Dg("PERMISSIONS · " + f.name, "OK", null) { PermBody(f) } }
  fun compareDlg(fs: List<File>) { if (fs.size == 2 && fs.all { it.isFile }) dg = Dg("COMPARE FILES", "OK", null) { CmpFiles(fs[0], fs[1]) } else toast("select exactly 2 files") }
  fun fileMenu(e: E) {
    val f = e.f; val k = Ar.kind(f)
    dg = Dg(f.name, "CLOSE", null) {
      if (e.ex in Fx.TXT && e.sz in 1..200000) {
        val pk = remember(f) { runCatching { f.useLines { it.take(4).joinToString("\n") } }.getOrDefault("") }
        if (pk.isNotEmpty()) T(pk, Th.dim, 10f, ml = 4)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("OPEN") { dg = null; opn(f) }
        if (e.sz <= 262144) Bt("EDIT") { dg = null; vf = f; sub = "edit" }
        Bt("HEX") { dg = null; vf = f; sub = "hex" }
        if (e.ex in Fx.IMG) Bt("IMAGE") { dg = null; vf = f; sub = "img" }
        if (k.isNotEmpty()) { Bt("EXTRACT") { dg = null; unDlg(f) }; Bt("PEEK") { dg = Dg("PEEK · " + f.name, "OK", null) { PeekBody(f) } } }
        Bt("HASH") { dg = Dg("CHECKSUMS · " + f.name, "OK", null) { HashBody(f) } }
        Bt("DUPLICATE") { dg = null; dupFiles(listOf(f)) }
        Bt("OPEN AS", subs = listOf<Pair<String, () -> Unit>>("doc|as text" to { dg = null; opn(f, "text/*") }, "photo|as image" to { dg = null; opn(f, "image/*") },
          "music|as audio" to { dg = null; opn(f, "audio/*") }, "video|as video" to { dg = null; opn(f, "video/*") }, "tools|as anything" to { dg = null; opn(f, "*/*") })) { dg = null; opn(f) }
        Bt("PERMS") { permsDlg(f) }
        Bt("SCAN MEDIA") { dg = null; scanMedia(listOf(f)) }
        Bt("SHARE") { dg = null; share(listOf(f)) }
        Bt("INFO", subs = listOf<Pair<String, () -> Unit>>(
          "copy path" to { clipb.setText(AnnotatedString(f.path)); toast("path copied") },
          "copy name" to { clipb.setText(AnnotatedString(f.name)); toast("name copied") },
          "touch (now)" to { f.setLastModified(System.currentTimeMillis()); rev++; toast("touched") })) { infoDlg(listOf(f)) }
        Bt("SHOW") { dg = null; sub = null; tab = 1; load(P, f.parentFile ?: root) }
      }
    }
  }
  fun togg(p: Pane, e: E) { p.sel = if (e.f.path in p.sel) p.sel - e.f.path else p.sel + e.f.path; cheer(3) }
  fun tapE(p: Pane, e: E) { if (p.sel.isNotEmpty()) togg(p, e) else if (e.dir) load(p, e.f) else if (e.ex in Fx.IMG) { vf = e.f; sub = "img" } else fileMenu(e) }
  fun sb(vararg p: Pair<String, () -> Unit>): Subs = p.toList()
  fun renameFile(f: File) { ask("RENAME", f.name) { n0 -> val n = n0.trim(); bg("renaming…") { val t = File(f.parentFile, n); if (!okNm(n)) "✕ invalid name" else if (n == f.name) null else if (t.exists()) "✕ already exists" else if (f.renameTo(t)) "renamed" else "✕ rename failed" } } }
  fun newFolder(p: Pane = P) { val d = p.dir; ask("NEW FOLDER", "") { n0 -> val n = n0.trim(); bg("mkdir…") { val t = File(d, n); if (!okNm(n)) "✕ invalid name" else if (t.exists()) "✕ already exists" else if (t.mkdirs()) "created ${t.name}" else "✕ mkdir failed" } } }
  fun newFile(p: Pane, def: String) { val d = p.dir; ask("NEW FILE", def) { n0 -> val n = n0.trim(); bg("touch…") { val t = File(d, n); if (!okNm(n)) "✕ invalid name" else if (t.exists()) "✕ already exists" else if (t.createNewFile()) "created ${t.name}" else "✕ create failed" } } }
  fun upDir(p: Pane) { val par = p.dir.parentFile; if (par != null) load(p, par) else toast("top of storage") }
  fun trashNow(fs: List<File>) {
    if (fs.isEmpty()) { toast("select first"); return }
    val tr = java.util.concurrent.CopyOnWriteArrayList<File>()
    bg("tidying away…", fs.size * 2, { bg("restoring…") { Trash.undo(tr); "restored ${tr.size}" } }) { fs.forEach { tr.add(Trash.put(it)) }; "moved ${fs.size} to trash" }
  }
  fun zipNow(z: Boolean, level: Int, store: Boolean) {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select first"); return }
    val d = P.dir; val nm = if (fs.size == 1) fs[0].nameWithoutExtension else d.name.ifEmpty { "archive" }
    val out = Fx.uniq(File(d, nm + (if (z) ".zip" else ".tar.gz"))); val ef = Ar.exr(".git,*.tmp,Thumbs.db,.DS_Store"); val hid = cf.hidden
    bg("compressing…", 5) {
      if (z) Ar.zip(fs, out, level, store, ef, hid, "") else Ar.tgz(fs, out, level, ef, hid)
      out.name + ": " + Fx.sz(fs.sumOf { Fx.du(it) }) + " → " + Fx.sz(out.length())
    }
  }
  fun crumbSubs(f: File): Subs = sb(
    "copy|copy path" to { clipb.setText(AnnotatedString(f.path)); toast("path copied") },
    (if (f.path in bms()) "star|unbookmark" else "star|bookmark") to { cf.bm = (if (f.path in bms()) bms() - f.path else bms() + f.path).joinToString("\n") },
    "tab|open in new tab" to { addTab(f) },
    "chart|analyze folder" to { load(P, f); sub = "ana" },
    "folderplus|new folder here" to { ask("NEW FOLDER", "") { n -> bg("mkdir…") { File(f, n).mkdirs(); null } } })
  fun quick(p: Pane, e: E): Subs = sb(
    (if (e.dir) "folder|open" else "doc|open") to { if (e.dir) load(p, e.f) else fileMenu(e) },
    "copy|copy" to { clip = sf(p); cut = false; p.sel = emptySet(); toast("${clip.size} copied") },
    "cut|cut" to { clip = sf(p); cut = true; p.sel = emptySet(); toast("${clip.size} cut") },
    "trash|to trash" to { trashNow(sf(p)) },
    "edit|rename" to { renameFile(e.f) },
    "share|share" to { share(sf(p)) },
    "box|zip" to { zipNow(true, 6, false) },
    "info|info" to { infoDlg(sf(p)) })
  fun histGo(p: Pane, d: Int) { val n = p.hi + d; if (n in p.hist.indices) { p.hi = n; load(p, p.hist[n], false) } else toast("no more history") }
  fun histSubs(p: Pane): Subs = p.hist.indices.reversed().take(6).map { i -> ((if (i == p.hi) "• " else "") + p.hist[i].name.ifEmpty { "/" }) to { p.hi = i; load(p, p.hist[i], false) } }
  fun selSubs(): Subs = sb(
    "x|none" to { P.sel = emptySet() },
    "inv|invert" to { P.sel = P.snap.items.map { it.f.path }.filter { it !in P.sel }.toSet() },
    "photo|images only" to { P.sel = P.snap.items.filter { it.ex in Fx.IMG }.map { it.f.path }.toSet() },
    "folder|folders only" to { P.sel = P.snap.items.filter { it.dir }.map { it.f.path }.toSet() },
    "doc|files only" to { P.sel = P.snap.items.filter { !it.dir }.map { it.f.path }.toSet() })
  val sortSubs: Subs = sb("sort|by name" to { cf.sort = 0 }, "big|by size" to { cf.sort = 1 }, "clock|by date" to { cf.sort = 2 }, "doc|by type" to { cf.sort = 3 },
    "sort|descending" to { cf.desc = !cf.desc }, "grid|grid / list" to { cf.grid = !cf.grid })
  fun mochiTap() { cheer(12); snack = listOf("hehe!", "yay~", "nya!", "(≧▽≦)", "more pats pls", "i love folders ♡").random() }
  fun doAct(a: String) {
    when {
      a.startsWith("q:") -> { pendingQ = a.substring(2); sub = null; tab = 2 }
      a.startsWith("go:") -> { val d = File(root, a.substring(3)); if (d.isDirectory) { load(P, d); sub = null; tab = 1 } else toast("no ${a.substring(3)} folder yet") }
      a.startsWith("nav:") -> sub = a.substring(4)
      a == "batch" -> batch()
      a == "squish" -> squishDlg()
      a == "bm" -> bmDlg()
      a == "edit" -> Ed.on = true
    }
  }
  SideEffect { Hub.act = { a -> doAct(a) } ; Hub.cheer = { n -> cheer(n) }; Hub.toast = { s -> toast(s) } }
  fun footFor(pn: Pane): Subs = sb("folderplus|folder" to { newFolder(pn) }, "fileplus|file" to { newFile(pn, "") },
    (if (clip.isNotEmpty()) "paste|paste ${clip.size}" else "all|select all") to { if (clip.isNotEmpty()) paste(pn) else pn.sel = pn.snap.items.map { it.f.path }.toSet() }, "up|up" to { upDir(pn) })
  fun tabSubs(i: Int): Subs = sb(
    "tab|duplicate" to { addTab(panes[i].dir) }, "x|close" to { closeTab(i) }, "x|close others" to { closeOthers(i) }, "x|close right" to { closeRight(i) },
    "copy|send selection here (copy)" to { if (i != act) xferTo(panes[i], false) else toast("pick another tab") },
    "cut|send selection here (move)" to { if (i != act) xferTo(panes[i], true) else toast("pick another tab") })
  fun tabTargets(mv: Boolean): Subs = panes.filter { it !== P }.map { pn -> ("tab|→ " + pn.dir.name.ifEmpty { "storage" }) to { xferTo(pn, mv) } }
  val findSubs: Subs = sb("clock|recent here" to { doAct("q:~file age<3d") }, "photo|photos here" to { doAct("q:~type:img") }, "video|videos here" to { doAct("q:~type:vid") },
    "big|big files here" to { doAct("q:~size>50m") }, "doc|docs here" to { doAct("q:~type:doc") }, "box|archives here" to { doAct("q:~type:arc") })
  val moreSubs: Subs = sb("refresh|reload" to { rev++ }, "eye|hidden files" to { cf.hidden = !cf.hidden }, "tab|new tab" to { addTab(P.dir) },
    "star|bookmarks" to { bmDlg() }, "chart|disk map" to { sub = "ana" }, "broom|cleaner" to { sub = "clean" }, "trash|trash" to { sub = "trash" },
    "paste|new from clipboard" to { newFromClip() }, "cmp|compare tabs" to { sub = "cmp" }, "edit|root editor" to { Ed.on = true }, "all|select all" to { P.sel = P.snap.items.map { it.f.path }.toSet() })
  fun sideItems(): List<SItem> {
    val fs = sf(P); val has = fs.isNotEmpty(); val l = ArrayList<SItem>()
    val cp = sb("copy|copy paths" to { clipb.setText(AnnotatedString(fs.joinToString("\n") { it.path })); toast("paths copied") }, "copy|copy names" to { clipb.setText(AnnotatedString(fs.joinToString("\n") { it.name })); toast("names copied") })
    if (has) {
      l.add(SItem("copy", "Copy", false, tabTargets(false) + cp) { clip = fs; cut = false; P.sel = emptySet(); toast("${fs.size} copied") })
      l.add(SItem("cut", "Cut", false, tabTargets(true)) { clip = fs; cut = true; P.sel = emptySet(); toast("${fs.size} cut") })
      if (clip.isNotEmpty()) l.add(SItem("paste", "Paste", true, sb("x|clear clipboard" to { clip = emptyList() })) { paste() })
      if (fs.size == 1) l.add(SItem("edit", "Rename", false, sb("edit|batch rename" to { batch() })) { renameFile(fs[0]) })
      else l.add(SItem("edit", "Batch") { batch() })
      l.add(SItem("trash", "Delete", false, sb("trash|to trash" to { trashNow(fs) }, "x|forever…" to { delDlg(fs, 1) }, "trash|shred…" to { delDlg(fs, 2) })) { trashNow(fs) })
      l.add(SItem("box", "Zip", false, sb("box|zip fast" to { zipNow(true, 1, false) }, "box|zip max" to { zipNow(true, 9, false) }, "box|zip store" to { zipNow(true, 0, true) },
        "box|tar.gz" to { zipNow(false, 6, false) }, "tools|options…" to { zipDlg() })) { zipNow(true, 6, false) })
      l.add(SItem("share", "Share", false, sb("share|share files" to { share(fs) })) { share(fs) })
      val hf = fs.firstOrNull { it.isFile }
      l.add(SItem("info", "Info", false, sb("info|checksums" to { if (hf != null) dg = Dg("CHECKSUMS · " + hf.name, "OK", null) { HashBody(hf) } else toast("select a file") },
        "tools|permissions" to { if (hf != null) permsDlg(hf) else toast("select a file") })) { infoDlg(fs) })
      l.add(SItem("more", "More", false, sb("copy|duplicate" to { dupFiles(fs) }, "cmp|compare 2 files" to { compareDlg(fs) }, "edit|batch rename" to { batch() }, "squish|squish photos" to { squishDlg() },
        "box|extract" to { val ar = fs.firstOrNull { Ar.kind(it).isNotEmpty() }; if (ar != null) unDlg(ar) else toast("select an archive") },
        "refresh|scan media" to { scanMedia(fs) }, "all|select all" to { P.sel = P.snap.items.map { it.f.path }.toSet() }), true) {})
      l.add(SItem("all", "Select", false, selSubs()) { P.sel = P.snap.items.map { it.f.path }.toSet() })
      l.add(SItem("x", "Clear") { P.sel = emptySet() })
    } else {
      val cur = P.dir.path
      l.add(SItem("up", "Up", false, histSubs(P)) { upDir(P) })
      l.add(SItem("folderplus", "New", false, sb("folderplus|new folder" to { newFolder() }, "fileplus|new file" to { newFile(P, "") }, "fileplus|new .txt" to { newFile(P, "note.txt") }, "paste|from clipboard" to { newFromClip() })) { newFolder() })
      l.add(SItem("tab", "Tab", false, sb("plus|new tab here" to { addTab(P.dir) }, "x|close tab" to { closeTab(act) }, "x|close others" to { closeOthers(act) })) { addTab(P.dir) })
      l.add(SItem("search", "Find", false, findSubs) { pendingQ = "~"; tab = 2 })
      if (clip.isNotEmpty()) l.add(SItem("paste", "Paste", true, sb("x|clear clipboard" to { clip = emptyList() })) { paste() })
      else l.add(SItem("all", "Select", false, selSubs()) { P.sel = P.snap.items.map { it.f.path }.toSet() })
      l.add(SItem("chart", "Map", false, sb("chart|breakdown" to { sub = "break" }, "broom|cleaner" to { sub = "clean" }, "dup|duplicates" to { sub = "dupe" }, "folderx|empty folders" to { sub = "empty" }, "trash|trash" to { sub = "trash" })) { sub = "ana" })
      l.add(SItem("star", "Fav", cur in bms(), sb("star|bookmarks" to { bmDlg() })) { cf.bm = (if (cur in bms()) bms() - cur else bms() + cur).joinToString("\n") })
      l.add(SItem(if (cf.grid) "list" else "grid", "View", false, sortSubs) { cf.grid = !cf.grid })
      l.add(SItem("eye", "Hidden", cf.hidden) { cf.hidden = !cf.hidden })
      l.add(SItem("cmp", "Diff", false, sb("cmp|compare tabs" to { sub = "cmp" })) { sub = "cmp" })
      l.add(SItem("refresh", "Reload", false, sb("trash|open trash" to { sub = "trash" })) { rev++ })
    }
    return l
  }
  val navSubs: (Int) -> Subs = { i ->
    when (i) {
      0 -> sb("broom|cleaner" to { doAct("nav:clean") }, "chart|breakdown" to { doAct("nav:break") }, "trash|trash" to { doAct("nav:trash") },
        "dup|duplicates" to { doAct("nav:dupe") }, "clock|recent files" to { doAct("q:file age<3d") }, "edit|root editor" to { Ed.on = true })
      1 -> sb("grid|grid / list" to { cf.grid = !cf.grid }, "eye|hidden files" to { cf.hidden = !cf.hidden }, "tab|new tab" to { addTab(P.dir) },
        "refresh|reload" to { rev++ }, "star|bookmarks" to { bmDlg() }, "up|go up" to { upDir(P) })
      2 -> sb("clock|recent" to { doAct("q:file age<3d") }, "photo|photos" to { doAct("q:type:img") }, "video|videos" to { doAct("q:type:vid") }, "music|music" to { doAct("q:type:aud") },
        "doc|docs" to { doAct("q:type:doc") }, "box|archives" to { doAct("q:type:arc") }, "droid|apks" to { doAct("q:type:apk") }, "big|big files" to { doAct("q:size>100m") })
      3 -> sb("edit|batch rename" to { doAct("batch") }, "squish|squish photos" to { doAct("squish") }, "star|bookmarks" to { doAct("bm") },
        "cmp|compare panes" to { doAct("nav:cmp") }, "chart|disk map" to { doAct("nav:ana") }, "folderx|empty folders" to { doAct("nav:empty") })
      else -> sb("edit|root editor" to { Ed.on = true }, "refresh|next theme" to { cf.applyPreset((cf.preset + 1) % Th.pals.size) }, "x|reset look" to { cf.reset() })
    }
  }
  BackHandler(sub != null || tab != 0) {
    if (sub != null) sub = null
    else if (tab == 1 && P.sel.isNotEmpty()) P.sel = emptySet()
    else if (tab == 1 && P.dir.path != root.path) load(P, P.dir.parentFile ?: root)
    else tab = 0
  }

  val route = if (sub != null) "s:$sub" else "t:$tab"
  LaunchedEffect(route) { navVis = true }
  Box(Modifier.fillMaxSize().pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        if (ev.type == PointerEventType.Press) {
          val pos = ev.changes.first().position; lt[0] = pos.x; lt[1] = pos.y
          val nsp = Sk.f("fx.spark", 3f).toInt()
          if (cf.sparkle && cf.fx == 2 && nsp > 0) burst.emit(pos.x, pos.y, nsp, 1, cf.ac)
        }
      }
    }
  }) {
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp).padding(top = 6.dp, bottom = 4.dp)) {
      if (Sk.b("nav.top", false)) NavBar(tab, navSubs) { i -> sub = null; if (i != 2) pendingQ = ""; tab = i }
      Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(navConn)) {
        AnimatedContent(route, transitionSpec = {
          val o = { r: String -> if (r.startsWith("t:")) r.substring(2).toInt() else 10 }
          Tr.tab(cf.fx, if (o(targetState) >= o(initialState)) 1 else -1)
        }, label = "route") { r ->
          Column(Modifier.fillMaxSize()) {
            when (r) {
              "t:0" -> HomeScr({ a -> doAct(a) }, { mochiTap() }, { e -> if (e.ex in Fx.IMG) { vf = e.f; sub = "img" } else fileMenu(e) }, { d -> load(P, d); tab = 1 }, root)
              "t:1" -> {
                val strip = rememberScrollState()
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                  IconBtn("up", histSubs(P)) { upDir(P) }
                  Row(Modifier.weight(1f).horizontalScroll(strip), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    panes.forEachIndexed { i, pn ->
                      val on = i == act; val shp = RoundedCornerShape(12.dp)
                      Hold(tabSubs(i), Modifier.clip(shp).background(if (on) cf.ac.copy(alpha = .2f) else Th.fg.copy(alpha = .05f))
                        .border(1.dp, if (on) cf.ac.copy(alpha = .65f) else cf.ac.copy(alpha = .22f), shp).padding(horizontal = 9.dp, vertical = 5.dp), { scope.launch { pager.animateScrollToPage(i) } }) {
                        T((if (busy != null && on) "… " else "") + pn.dir.name.ifEmpty { "storage" }, if (on) cf.ac else Th.dim, 11f, if (on) FontWeight.Bold else null)
                      }
                    }
                  }
                  IconBtn("plus", sb("tab|new tab here" to { addTab(P.dir) }, "tab|new tab at storage" to { addTab(root) }, "x|close others" to { closeOthers(act) })) { addTab(P.dir) }
                  IconBtn("more", moreSubs, true) { }
                }
                if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = cf.ac, trackColor = Th.fg.copy(alpha = .1f))
                if (P.sel.isNotEmpty()) {
                  val selSz = remember(P.sel, P.snap) { P.snap.items.filter { it.f.path in P.sel }.sumOf { it.sz } }
                  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(cf.ac.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    T("♡ ${P.sel.size} selected · ${Fx.sz(selSz)}", cf.ac, 11f, FontWeight.Bold, Modifier.weight(1f))
                    Bt("ALL", subs = selSubs()) { P.sel = P.snap.items.map { it.f.path }.toSet() }
                    Bt("INV") { P.sel = P.snap.items.map { it.f.path }.filter { it !in P.sel }.toSet() }
                    Bt("✕") { P.sel = emptySet() }
                  }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                  HorizontalPager(pager, Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(end = if (panes.size > 1) 20.dp else 0.dp), pageSpacing = 8.dp, key = { panes.getOrNull(it)?.id ?: it }) { page ->
                    val pn = panes.getOrNull(page) ?: panes[0]
                    PaneView(pn, act == page, Modifier.fillMaxSize(), { f -> load(pn, f) }, { e -> tapE(pn, e) }, { e -> togg(pn, e) }, { f -> crumbSubs(f) }, { e -> quick(pn, e) }, { footFor(pn) })
                  }
                  Spacer(Modifier.width(5.dp))
                  SideBar(sideItems())
                }
              }
              "t:2" -> SearchScr(P.dir, root, cf.hidden, pendingQ, { e -> if (e.dir) { load(P, e.f); tab = 1 } else if (e.ex in Fx.IMG) { vf = e.f; sub = "img" } else fileMenu(e) }) { a, fs ->
                when (a) {
                  "copy" -> { clip = fs; cut = false; toast("${fs.size} copied → paste in FILES") }
                  "cut" -> { clip = fs; cut = true; toast("${fs.size} cut → paste in FILES") }
                  "trash" -> trashNow(fs)
                  "share" -> share(fs)
                }
              }
              "t:3" -> ToolsScr { a -> doAct(a) }
              "t:4" -> MeScr { mochiTap() }
              "s:ana" -> AnaScr({ sub = null }, P.dir) { f -> load(P, f); sub = null; tab = 1 }
              "s:dupe" -> DupeScr({ sub = null }, P.dir, cf.hidden, rev) { fs -> delDlg(fs) }
              "s:empty" -> EmpScr({ sub = null }, P.dir, rev, { fs -> delDlg(fs) }) { f -> load(P, f); sub = null; tab = 1 }
              "s:clean" -> CleanerScr({ sub = null }, root, rev) { n, fs, by -> cleanDlg(n, fs, by) }
              "s:trash" -> TrashScr({ sub = null }, rev, { t -> bg("restoring…", 1) { "restored " + Trash.restore(t) } }) {
                dg = Dg("EMPTY TRASH?", "EMPTY", { bg("emptying…") { Trash.empty(); "trash emptied" } }) { T("this cannot be undone", Th.dim) }
              }
              "s:break" -> BreakdownScr({ sub = null }, root)
              "s:cmp" -> CompareScr({ sub = null }, P.dir, (otherPane()?.dir ?: P.dir), rev) { keys, a, b -> bg("syncing…", 6) { Fx2.sync(a, b, keys); "synced ${keys.size} files" } }
              "s:edit" -> vf?.let { f -> EditScr({ sub = null }, f) { m -> toast(m); rev++ } }
              "s:hex" -> vf?.let { f -> HexScr({ sub = null }, f) }
              "s:img" -> vf?.let { f ->
                val imgs = P.snap.items.filter { it.ex in Fx.IMG }.map { it.f }.let { l -> if (f in l) l else listOf(f) }
                ImgScr({ sub = null }, imgs, imgs.indexOf(f).coerceAtLeast(0), { share(listOf(it)) }, { trashNow(listOf(it)) }, { infoDlg(listOf(it)) }, { rev++ })
              }
              else -> {}
            }
          }
        }
      }
      if (Ed.on) EditorPanel(cf, tab, { i -> sub = null; tab = i }, Modifier)
      if (!Sk.b("nav.top", false)) AnimatedVisibility(navVis || Ed.on || !cf.navHide, enter = expandVertically(tween(180)) + fadeIn(tween(180)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
        NavBar(tab, navSubs) { i -> sub = null; if (i != 2) pendingQ = ""; tab = i }
      }
    }
    Wipe(tab)
    ParticleLayer(burst)
    Snack(snack, snackAct?.first) { snackAct?.second?.invoke(); snack = null; snackAct = null }
  }
  dg?.let { d -> Dlg(d) { dg = null } }
}
