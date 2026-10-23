@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

val LCfg = staticCompositionLocalOf<Cfg> { error("cfg") }

class Snap(val dir: File, val items: List<E>)
class Pane(d: File) {
  var snap by mutableStateOf(Snap(d, emptyList()))
  var sel by mutableStateOf(setOf<String>())
  var job: Job? = null
  val dir: File get() = snap.dir
}
class Dg(val title: String, val ok: String, val onOk: (() -> Unit)?, val body: @Composable () -> Unit)

@Composable fun T(s: String, c: Color = Color.White, sz: Float = 13f, w: FontWeight? = null, m: Modifier = Modifier, ml: Int = 1) {
  val cf = LCfg.current
  Text(s, m, color = c, fontSize = (sz * cf.fs).sp, fontFamily = cf.ff, fontWeight = w, maxLines = ml, overflow = TextOverflow.Ellipsis)
}

@Composable fun Bt(s: String, on: Boolean = false, m: Modifier = Modifier, f: () -> Unit) {
  val cf = LCfg.current; val ac = cf.ac
  val src = remember { MutableInteractionSource() }
  val pr by src.collectIsPressedAsState()
  val sc by animateFloatAsState(if (pr && cf.anim > 0) .86f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "bt")
  val sh = RoundedCornerShape(cf.rad.dp)
  Box(m.graphicsLayer { scaleX = sc; scaleY = sc }.clip(sh)
    .background(if (on) ac.copy(alpha = .25f) else Color.White.copy(alpha = .06f))
    .border(1.dp, if (on) ac else Color.White.copy(alpha = .12f), sh)
    .clickable(src, null) { f() }.padding(horizontal = 9.dp, vertical = 5.dp)) { T(s, if (on) ac else Color.White, 12f) }
}

@Composable fun Inp(v: String, ch: (String) -> Unit, hint: String, m: Modifier = Modifier, one: Boolean = true) {
  val cf = LCfg.current
  val sh = RoundedCornerShape(cf.rad.dp)
  Box(m.clip(sh).background(Color.White.copy(alpha = .07f)).border(1.dp, cf.ac.copy(alpha = .4f), sh).padding(8.dp)) {
    if (v.isEmpty()) T(hint, Color.Gray, 11f)
    BasicTextField(v, ch, Modifier.fillMaxWidth(), singleLine = one,
      textStyle = TextStyle(color = Color.White, fontSize = (13 * cf.fs).sp, fontFamily = cf.ff), cursorBrush = SolidColor(cf.ac))
  }
}

@Composable fun Sl(l: String, v: Float, r: ClosedFloatingPointRange<Float>, f: (Float) -> Unit) {
  val cf = LCfg.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    T(l, Color.LightGray, 11f, null, Modifier.width(70.dp))
    Slider(v, f, Modifier.weight(1f), valueRange = r,
      colors = SliderDefaults.colors(thumbColor = cf.ac, activeTrackColor = cf.ac, inactiveTrackColor = Color.White.copy(alpha = .12f)))
    T(String.format("%.1f", v), Color.Gray, 10f, null, Modifier.width(38.dp))
  }
}

@Composable fun Wave(t: String, sz: Float = 20f) {
  val cf = LCfg.current
  val ph = if (cf.anim > 0) rememberInfiniteTransition(label = "w").animateFloat(0f, 6.2832f,
    infiniteRepeatable(tween((cf.spd * 1000).toInt().coerceAtLeast(300), easing = LinearEasing)), label = "p") else null
  Row {
    t.forEachIndexed { i, ch ->
      val col = Color.hsv((cf.hue + i * 14f) % 360f, cf.sat.coerceIn(0f, 1f), 1f)
      T(ch.toString(), col, sz, FontWeight.Bold, Modifier.graphicsLayer {
        val p = ph?.value ?: 0f
        translationY = sin(p + i * .7f) * cf.amp * (if (cf.anim == 1) .5f else 1f)
        alpha = .72f + .28f * cos(p + i * .7f)
      })
    }
  }
}

@Composable fun Scr(t: String, c: Color, sz: Float) {
  val an = LCfg.current.anim
  var s by remember { mutableStateOf(t) }
  LaunchedEffect(t) {
    if (an == 0) s = t else {
      val g = "01<>/\\#%&*+=?"
      for (k in 0..7) { s = t.mapIndexed { i, ch -> if (i < k * t.length / 8 || ch == ' ') ch else g.random() }.joinToString(""); delay(30) }
      s = t
    }
  }
  T(s, c, sz)
}

@Composable fun Sec(t: String) { T(t, LCfg.current.ac2, 11f, FontWeight.Bold, Modifier.padding(top = 10.dp)) }

@Composable fun Hdr(t: String, back: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
  Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Bt("‹", false, Modifier, back); Spacer(Modifier.width(8.dp)); Wave(t, 16f); Spacer(Modifier.weight(1f)); extra()
  }
}

@Composable fun Cd(t: String, d: String, f: () -> Unit) {
  val cf = LCfg.current; val sh = RoundedCornerShape(cf.rad.dp)
  Column(Modifier.fillMaxWidth().clip(sh).border(1.dp, cf.ac.copy(alpha = .25f), sh).clickable { f() }.padding(10.dp)) {
    T(t, cf.ac, 13f, FontWeight.Bold); T(d, Color.Gray, 11f, ml = 2)
  }
}

@Composable fun Stor() {
  val cf = LCfg.current; val ac = cf.ac; val a2 = cf.ac2
  val (tot, fr) = remember { Fx.stor() }
  val used = if (tot > 0) (tot - fr).toFloat() / tot else 0f
  val ph = if (cf.anim > 0) rememberInfiniteTransition(label = "s").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "x") else null
  Row(verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))) {
      drawRect(Color.White.copy(alpha = .08f))
      val w = size.width * used
      drawRect(Brush.horizontalGradient(listOf(a2, ac)), size = Size(w, size.height))
      val p = ph?.value
      if (p != null && w > 0f) {
        val x = p * (w + 90f) - 90f
        clipRect(right = w) { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.Transparent), startX = x, endX = x + 90f), Offset(x, 0f), Size(90f, size.height)) }
      }
    }
    T("${Fx.sz(tot - fr)} / ${Fx.sz(tot)}", Color.Gray, 10f, null, Modifier.padding(start = 6.dp))
  }
}

@Composable fun Rw(e: E, i: Int, sel: Boolean, tap: () -> Unit, lng: () -> Unit) {
  val cf = LCfg.current; val ac = cf.ac
  var on by remember { mutableStateOf(i >= 16 || cf.anim == 0) }
  if (!on) LaunchedEffect(Unit) { delay((i * cf.stag).toLong()); on = true }
  val a by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "a")
  val col = if (e.dir) ac else if (e.ex in Fx.MEDIA) cf.ac2 else Color.White
  Row(Modifier.fillMaxWidth().graphicsLayer { alpha = a; translationX = (1f - a) * 56f }
    .background(if (sel) ac.copy(alpha = .2f) else Color.Transparent)
    .combinedClickable(onClick = tap, onLongClick = lng).padding(horizontal = 6.dp, vertical = cf.pad.dp),
    verticalAlignment = Alignment.CenterVertically) {
    T(Fx.gl(e), col, 14f, null, Modifier.width(24.dp))
    T(e.n, col, 13f, null, Modifier.weight(1f))
    if (cf.cPerm) T(e.pm, Color.Gray, 10f, null, Modifier.padding(start = 6.dp))
    if (cf.cSize && !e.dir) T(Fx.sz(e.sz), Color.Gray, 10f, null, Modifier.padding(start = 6.dp))
    if (cf.cDate) T(Fx.dt(e.mt), Color.Gray, 10f, null, Modifier.padding(start = 6.dp))
  }
}

@Composable fun PaneView(p: Pane, on: Boolean, m: Modifier, go: (File) -> Unit, tap: (E) -> Unit, lng: (E) -> Unit) {
  val cf = LCfg.current; val ac = cf.ac
  val sw = remember { Animatable(1f) }
  LaunchedEffect(p.snap.dir) { if (cf.anim == 2) { sw.snapTo(0f); sw.animateTo(1f, tween(420)) } }
  val sh = RoundedCornerShape(cf.rad.dp)
  Column(m.fillMaxWidth().clip(sh).border(1.dp, if (on) ac.copy(alpha = .6f) else Color.White.copy(alpha = .08f), sh)) {
    val parts = remember(p.snap.dir) { val l = ArrayList<File>(); var f: File? = p.snap.dir; while (f != null) { l.add(0, f); f = f.parentFile }; l }
    val sc = rememberScrollState()
    LaunchedEffect(parts) { delay(60); sc.animateScrollTo(sc.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(sc).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
      parts.forEach { f -> Bt(f.name.ifEmpty { "/" }, f == p.snap.dir) { go(f) }; Spacer(Modifier.width(3.dp)) }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
      AnimatedContent(p.snap, transitionSpec = {
        if (cf.anim == 0 || initialState.dir == targetState.dir) EnterTransition.None togetherWith ExitTransition.None
        else {
          val d = if (targetState.dir.path.length < initialState.dir.path.length) -1 else 1
          if (cf.anim == 1) fadeIn(tween(140)) togetherWith fadeOut(tween(90))
          else (slideInHorizontally(tween(260)) { it * d / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(220)) { -it * d / 3 } + fadeOut(tween(120)))
        }
      }, label = "nav", contentKey = { it.dir }) { s ->
        if (s.items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { T("∅ empty", Color.Gray) }
        else LazyColumn(Modifier.fillMaxSize()) {
          itemsIndexed(s.items, key = { _, e -> e.f.path }) { i, e -> Rw(e, i, e.f.path in p.sel, { tap(e) }, { lng(e) }) }
        }
      }
      if (cf.anim == 2) Canvas(Modifier.fillMaxSize()) {
        val v = sw.value
        if (v < 1f) {
          val y = size.height * v
          drawRect(Brush.verticalGradient(listOf(Color.Transparent, ac.copy(alpha = .45f * (1f - v)), Color.Transparent), startY = y - 36f, endY = y + 36f), Offset(0f, y - 36f), Size(size.width, 72f))
        }
      }
    }
    val st = remember(p.snap) { p.snap.items.size.toString() + " items · " + Fx.sz(p.snap.items.sumOf { it.sz }) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
      Scr(st, ac.copy(alpha = .85f), 10f)
      Spacer(Modifier.weight(1f))
      if (p.sel.isNotEmpty()) T("${p.sel.size} sel", cf.ac2, 10f)
    }
  }
}

@Composable fun Dlg(d: Dg, close: () -> Unit) {
  val cf = LCfg.current
  val ok = d.onOk
  AlertDialog(onDismissRequest = close, containerColor = Color(0xFF0B0B0B), shape = RoundedCornerShape(cf.rad.dp),
    title = { T(d.title, cf.ac, 15f, FontWeight.Bold) },
    text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { d.body() } },
    confirmButton = { if (ok != null) Bt(d.ok, true) { ok(); close() } else Bt(d.ok, true) { close() } },
    dismissButton = { if (ok != null) Bt("✕") { close() } })
}
