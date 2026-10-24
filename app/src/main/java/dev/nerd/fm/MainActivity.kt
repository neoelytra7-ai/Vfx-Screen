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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
  val ck = rememberClock(cf.fx > 0)
  var entered by remember { mutableStateOf(false) }
  val boot = remember { Boot() }
  var access by remember { mutableStateOf(hasAccess()) }
  LaunchedEffect(Ev.t) { access = hasAccess() }
  SideEffect {
    act.window.statusBarColor = Th.bg.toArgb(); act.window.navigationBarColor = Th.bg.toArgb()
    WindowCompat.getInsetsController(act.window, act.window.decorView).apply { isAppearanceLightStatusBars = Th.pal.light; isAppearanceLightNavigationBars = Th.pal.light }
  }
  val rv = remember { Animatable(0f) }
  LaunchedEffect(entered) { if (entered) rv.animateTo(1f, spring(.6f, 200f)) }
  CompositionLocalProvider(LCfg provides cf, LClock provides ck) {
    MaterialTheme(darkColorScheme(primary = cf.ac, background = Th.bg, surface = Th.bg)) {
      Box(Modifier.fillMaxSize().background(cf.bgc)) {
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
  val pa = remember { Pane(root) }
  val pb = remember { Pane(root) }
  val burst = remember { Burst() }
  val lt = remember { floatArrayOf(300f, 500f) }
  var act by remember { mutableIntStateOf(0) }
  var tab by remember { mutableIntStateOf(0) }
  var sub by remember { mutableStateOf<String?>(null) }
  var vf by remember { mutableStateOf<File?>(null) }
  var pendingQ by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf<String?>(null) }
  var snack by remember { mutableStateOf<String?>(null) }
  var dg by remember { mutableStateOf<Dg?>(null) }
  var clip by remember { mutableStateOf(listOf<File>()) }
  var cut by remember { mutableStateOf(false) }
  var rev by remember { mutableIntStateOf(0) }
  val dual = cf.dual
  val P = if (act == 0 || !dual) pa else pb
  val O = if (P === pa) pb else pa

  LaunchedEffect(snack) { if (snack != null) { delay(2400); snack = null } }
  fun toast(s: String) { snack = s }
  fun cheer(n: Int) { burst.emit(lt[0], lt[1].coerceAtLeast(200f), n, -1, cf.ac) }
  fun load(p: Pane, d: File) {
    p.job?.cancel()
    p.job = scope.launch {
      val l = withContext(Dispatchers.IO) { Fx.list(d, cf.hidden, cf.sort, cf.desc) }
      p.sel = emptySet(); p.snap = Snap(d, l); boot.loaded++
    }
  }
  LaunchedEffect(rev, Ev.t, cf.sort, cf.desc, cf.hidden) { load(pa, pa.dir); load(pb, pb.dir) }

  fun sf(p: Pane): List<File> = p.snap.items.filter { it.f.path in p.sel }.map { it.f }
  fun bg(msg: String, xp: Int = 0, work: () -> String?) {
    scope.launch {
      busy = msg
      val r = try { withContext(Dispatchers.IO) { work() } } catch (t: Throwable) { "✕ " + (t.message ?: t.javaClass.simpleName) }
      busy = null; rev++
      val before = cf.level; cf.addXp(xp)
      if (r != null) { snack = r; if (!r.startsWith("✕")) cheer(10) }
      if (cf.level > before) { snack = "level up! Lv ${cf.level} ♡"; cheer(28) }
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
    val mode = mutableIntStateOf(if (fs.any { it.path.startsWith(Trash.root.path) }) 1 else 0)
    dg = Dg("DELETE ${fs.size}?", "GO", {
      val m = mode.intValue
      bg(if (m == 0) "tidying away…" else "deleting…", fs.size * 2) {
        for (f in fs) { if (m == 0) Trash.put(f) else if (m == 1) f.deleteRecursively() else Fx2.shred(f) }
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
  fun paste() {
    if (clip.isEmpty()) { toast("clipboard empty"); return }
    val d = P.dir; val c = clip; val mv = cut
    bg(if (mv) "moving…" else "copying…", 3) {
      c.forEach { s -> val t = Fx.uniq(File(d, s.name)); if (mv) Fx.mv(s, t) else Fx.cp(s, t) }
      "${c.size} → ${d.name}"
    }
    if (mv) clip = emptyList()
  }
  fun xfer(mv: Boolean) {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select first"); return }
    val d = O.dir
    bg(if (mv) "moving…" else "copying…", 3) {
      fs.forEach { s -> val t = Fx.uniq(File(d, s.name)); if (mv) Fx.mv(s, t) else Fx.cp(s, t) }
      "${fs.size} → ${d.name}"
    }
  }
  fun batch() {
    val fs = sf(P)
    if (fs.isEmpty()) { toast("select files in FILES first (long-press)"); return }
    val fi = mutableStateOf("(.*)"); val re = mutableStateOf("\$1")
    fun nn(f: File, i: Int): String = Regex(fi.value).replace(f.name, re.value.replace("{n}", (i + 1).toString()))
    dg = Dg("BATCH RENAME ${fs.size}", "APPLY", {
      bg("renaming…", 2) {
        var c = 0
        fs.forEachIndexed { i, f -> val n = nn(f, i); if (n.isNotEmpty() && n != f.name && f.renameTo(File(f.parentFile, n))) c++ }
        "renamed $c"
      }
    }) {
      Inp(fi.value, { fi.value = it }, "find (regex)", Modifier.fillMaxWidth())
      Inp(re.value, { re.value = it }, "replace  \$1 {n}", Modifier.fillMaxWidth())
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
  fun fileMenu(e: E) {
    val f = e.f; val k = Ar.kind(f)
    dg = Dg(f.name, "CLOSE", null) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bt("OPEN") { dg = null; opn(f) }
        if (e.sz <= 262144) Bt("EDIT") { dg = null; vf = f; sub = "edit" }
        Bt("HEX") { dg = null; vf = f; sub = "hex" }
        if (e.ex in Fx.IMG) Bt("IMAGE") { dg = null; vf = f; sub = "img" }
        if (k.isNotEmpty()) { Bt("EXTRACT") { dg = null; unDlg(f) }; Bt("PEEK") { dg = Dg("PEEK · " + f.name, "OK", null) { PeekBody(f) } } }
        Bt("HASH") { dg = Dg("CHECKSUMS · " + f.name, "OK", null) { HashBody(f) } }
        Bt("SHARE") { dg = null; share(listOf(f)) }
        Bt("INFO") { infoDlg(listOf(f)) }
        Bt("SHOW") { dg = null; sub = null; tab = 1; load(P, f.parentFile ?: root) }
      }
    }
  }
  fun togg(p: Pane, e: E) { p.sel = if (e.f.path in p.sel) p.sel - e.f.path else p.sel + e.f.path; cheer(3) }
  fun tapE(p: Pane, e: E) { if (p.sel.isNotEmpty()) togg(p, e) else if (e.dir) load(p, e.f) else fileMenu(e) }
  fun mochiTap() { cheer(12); snack = listOf("hehe!", "yay~", "nya!", "(≧▽≦)", "more pats pls", "i love folders ♡").random() }
  fun doAct(a: String) {
    when {
      a.startsWith("q:") -> { pendingQ = a.substring(2); sub = null; tab = 2 }
      a.startsWith("go:") -> { val d = File(root, a.substring(3)); if (d.isDirectory) { load(P, d); sub = null; tab = 1 } else toast("no ${a.substring(3)} folder yet") }
      a.startsWith("nav:") -> sub = a.substring(4)
      a == "batch" -> batch()
      a == "squish" -> squishDlg()
      a == "bm" -> bmDlg()
    }
  }

  BackHandler(sub != null || tab != 0) {
    if (sub != null) sub = null
    else if (tab == 1 && P.sel.isNotEmpty()) P.sel = emptySet()
    else if (tab == 1 && P.dir.path != root.path) load(P, P.dir.parentFile ?: root)
    else tab = 0
  }

  val route = if (sub != null) "s:$sub" else "t:$tab"
  Box(Modifier.fillMaxSize().pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        if (ev.type == PointerEventType.Press) {
          val pos = ev.changes.first().position; lt[0] = pos.x; lt[1] = pos.y
          if (cf.sparkle && cf.fx == 2) burst.emit(pos.x, pos.y, 3, 1, cf.ac)
        }
      }
    }
  }) {
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp).padding(top = 6.dp, bottom = 4.dp)) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        AnimatedContent(route, transitionSpec = {
          val o = { r: String -> if (r.startsWith("t:")) r.substring(2).toInt() else 10 }
          val d = if (o(targetState) >= o(initialState)) 1 else -1
          if (cf.fx == 0) EnterTransition.None togetherWith ExitTransition.None
          else if (cf.fx == 1) fadeIn(tween(160)) togetherWith fadeOut(tween(100))
          else (slideInHorizontally(tween(420, easing = BackE)) { it * d / 2 } + fadeIn(tween(220)) + scaleIn(tween(420, easing = BackE), .9f)) togetherWith
            (slideOutHorizontally(tween(260)) { -it * d / 3 } + fadeOut(tween(160)) + scaleOut(tween(260), .94f))
        }, label = "route") { r ->
          Column(Modifier.fillMaxSize()) {
            when (r) {
              "t:0" -> HomeScr({ a -> doAct(a) }) { mochiTap() }
              "t:1" -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Wave("FILES", 18f); Spacer(Modifier.weight(1f))
                  Bt(if (cf.grid) "☰" else "▦", cf.grid) { cf.grid = !cf.grid }
                  Bt(listOf("NAME", "SIZE", "DATE", "EXT")[cf.sort.coerceIn(0, 3)] + (if (cf.desc) "↓" else "↑")) { cf.sort = (cf.sort + 1) % 4 }
                  Bt("↕") { cf.desc = !cf.desc }
                  Bt(".", cf.hidden) { cf.hidden = !cf.hidden }
                }
                Spacer(Modifier.height(4.dp)); Stor(); Spacer(Modifier.height(4.dp))
                PaneView(pa, act == 0 || !dual, Modifier.weight(1f), { act = 0; load(pa, it) }, { e -> act = 0; tapE(pa, e) }, { e -> act = 0; togg(pa, e) })
                if (dual) {
                  Spacer(Modifier.height(4.dp))
                  PaneView(pb, act == 1, Modifier.weight(1f), { act = 1; load(pb, it) }, { e -> act = 1; tapE(pb, e) }, { e -> act = 1; togg(pb, e) })
                }
                val b = busy
                if (b != null) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                  LinearProgressIndicator(Modifier.weight(1f), color = cf.ac, trackColor = Th.fg.copy(alpha = .1f))
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
                    Bt("SQUISH") { squishDlg() }
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
                    Bt("DUAL", cf.dual) { cf.dual = !cf.dual }
                    if (dual) { Bt("⇄") { load(O, P.dir) }; Bt("DIFF") { sub = "cmp" } }
                    val cur = d.path
                    Bt(if (cur in bms()) "★" else "☆", cur in bms()) { cf.bm = (if (cur in bms()) bms() - cur else bms() + cur).joinToString("\n") }
                    Bt("BM") { bmDlg() }
                  }
                }
              }
              "t:2" -> SearchScr(P.dir, root, cf.hidden, pendingQ) { e -> if (e.dir) { load(P, e.f); tab = 1 } else fileMenu(e) }
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
              "s:cmp" -> CompareScr({ sub = null }, pa.dir, pb.dir, rev) { keys, a, b -> bg("syncing…", 6) { Fx2.sync(a, b, keys); "synced ${keys.size} files" } }
              "s:edit" -> vf?.let { f -> EditScr({ sub = null }, f) { m -> toast(m); rev++ } }
              "s:hex" -> vf?.let { f -> HexScr({ sub = null }, f) }
              "s:img" -> vf?.let { f -> ImgScr({ sub = null }, f) }
              else -> {}
            }
          }
        }
      }
      NavBar(tab) { i -> sub = null; if (i != 2) pendingQ = ""; tab = i }
    }
    Wipe(tab)
    ParticleLayer(burst)
    Snack(snack)
  }
  dg?.let { d -> Dlg(d) { dg = null } }
}
