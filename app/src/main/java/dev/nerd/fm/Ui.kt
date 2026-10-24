@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

val LCfg = staticCompositionLocalOf<Cfg> { error("cfg") }
val LClock = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }
private val Back = CubicBezierEasing(.34f, 1.56f, .64f, 1f)
private val INK = Color(0xFF3B2A38)
private val HP = Path()

@Immutable class Snap(val dir: File, val items: List<E>)
class Pane(d: File) {
  var snap by mutableStateOf(Snap(d, emptyList()))
  var sel by mutableStateOf(setOf<String>())
  var job: Job? = null
  val dir: File get() = snap.dir
}
class Dg(val title: String, val ok: String, val onOk: (() -> Unit)?, val body: @Composable () -> Unit)

@Composable fun rememberClock(on: Boolean): State<Float> =
  if (on) rememberInfiniteTransition(label = "clk").animateFloat(0f, 628.3185f, infiniteRepeatable(tween(628318, easing = LinearEasing)), label = "c")
  else remember { mutableStateOf(0f) }

fun DrawScope.drawHeart(cx: Float, cy: Float, s: Float, c: Color, a: Float = 1f) {
  HP.reset()
  HP.moveTo(cx, cy + s * .9f)
  HP.cubicTo(cx - s * 1.6f, cy - s * .2f, cx - s * .7f, cy - s * 1.5f, cx, cy - s * .5f)
  HP.cubicTo(cx + s * .7f, cy - s * 1.5f, cx + s * 1.6f, cy - s * .2f, cx, cy + s * .9f)
  HP.close()
  drawPath(HP, c, a)
}
fun DrawScope.drawSpark(cx: Float, cy: Float, s: Float, c: Color, a: Float = 1f) {
  HP.reset()
  HP.moveTo(cx, cy - s)
  HP.quadraticBezierTo(cx, cy, cx + s, cy)
  HP.quadraticBezierTo(cx, cy, cx, cy + s)
  HP.quadraticBezierTo(cx, cy, cx - s, cy)
  HP.quadraticBezierTo(cx, cy, cx, cy - s)
  HP.close()
  drawPath(HP, c, a)
}

// ───────── text & controls ─────────
@Composable fun T(s: String, c: Color = Th.fg, sz: Float = 13f, w: FontWeight? = null, m: Modifier = Modifier, ml: Int = 1) {
  val cf = LCfg.current
  Text(s, m, color = c, fontSize = (sz * cf.fs).sp, fontFamily = cf.ff, fontWeight = w, maxLines = ml, overflow = TextOverflow.Ellipsis)
}

@Composable fun Bt(s: String, on: Boolean = false, m: Modifier = Modifier, f: () -> Unit) {
  val cf = LCfg.current; val ac = cf.ac; val hp = LocalHapticFeedback.current; val fx = cf.fx
  val src = remember { MutableInteractionSource() }
  val pr by src.collectIsPressedAsState()
  val sx by animateFloatAsState(if (pr && fx > 0) .86f else 1f, spring(.35f, Spring.StiffnessMedium), label = "bx")
  val sy by animateFloatAsState(if (pr && fx > 0) .78f else 1f, spring(.35f, Spring.StiffnessMedium), label = "by")
  val sh = RoundedCornerShape(cf.rad.dp)
  Box(m.graphicsLayer { scaleX = sx; scaleY = sy }.clip(sh)
    .background(if (on) ac.copy(alpha = .28f) else Th.fg.copy(alpha = .08f))
    .border(1.5.dp, if (on) ac else Th.fg.copy(alpha = .14f), sh)
    .clickable(src, null) { if (cf.haptic) hp.performHapticFeedback(HapticFeedbackType.TextHandleMove); f() }
    .padding(horizontal = 11.dp, vertical = 6.dp)) { T(s, if (on) ac else Th.fg, 12f, FontWeight.Medium) }
}

@Composable fun Inp(v: String, ch: (String) -> Unit, hint: String, m: Modifier = Modifier, one: Boolean = true) {
  val cf = LCfg.current
  val sh = RoundedCornerShape(cf.rad.dp)
  Box(m.clip(sh).background(Th.fg.copy(alpha = .07f)).border(1.5.dp, cf.ac.copy(alpha = .45f), sh).padding(horizontal = 12.dp, vertical = 9.dp)) {
    if (v.isEmpty()) T(hint, Th.dim, 11f)
    BasicTextField(v, ch, Modifier.fillMaxWidth(), singleLine = one,
      textStyle = TextStyle(color = Th.fg, fontSize = (13 * cf.fs).sp, fontFamily = cf.ff), cursorBrush = SolidColor(cf.ac))
  }
}

@Composable fun Sl(l: String, v: Float, r: ClosedFloatingPointRange<Float>, f: (Float) -> Unit) {
  val cf = LCfg.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    T(l, Th.dim, 11f, null, Modifier.width(70.dp))
    Slider(v, f, Modifier.weight(1f), valueRange = r,
      colors = SliderDefaults.colors(thumbColor = cf.ac, activeTrackColor = cf.ac, inactiveTrackColor = Th.fg.copy(alpha = .12f)))
    T(String.format("%.1f", v), Th.dim, 10f, null, Modifier.width(38.dp))
  }
}

@Composable fun Wave(t: String, sz: Float = 20f) {
  val cf = LCfg.current; val ck = LClock.current
  Row {
    t.forEachIndexed { i, ch ->
      val col = Color.hsv((cf.hue + i * 9f) % 360f, if (Th.pal.light) .6f else cf.sat.coerceIn(0f, 1f), if (Th.pal.light) .85f else 1f)
      T(ch.toString(), col, sz, FontWeight.Bold, Modifier.graphicsLayer {
        val p = ck.value * 6.2832f / cf.spd
        translationY = sin(p + i * .7f) * cf.amp * (if (cf.fx == 1) .5f else 1f)
        alpha = .78f + .22f * cos(p + i * .7f)
      })
    }
  }
}

@Composable fun Scr(t: String, c: Color, sz: Float) {
  val an = LCfg.current.fx
  var s by remember { mutableStateOf(t) }
  LaunchedEffect(t) {
    if (an == 0) s = t else {
      val g = "01<>/\\#%&*+=?♡"
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
  Column(Modifier.fillMaxWidth().clip(sh).background(Th.fg.copy(alpha = .05f)).border(1.5.dp, cf.ac.copy(alpha = .3f), sh).clickable { f() }.padding(12.dp)) {
    T(t, cf.ac, 13f, FontWeight.Bold); T(d, Th.dim, 11f, ml = 2)
  }
}

@Composable fun Pop(i: Int, m: Modifier = Modifier, c: @Composable BoxScope.() -> Unit) {
  val fx = LCfg.current.fx
  val s = remember { Animatable(if (fx == 0) 1f else 0f) }
  LaunchedEffect(Unit) { if (fx > 0) { delay(i * 45L); s.animateTo(1f, spring(.45f, 260f)) } }
  Box(m.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value.coerceIn(0f, 1f) }, content = c)
}

// ───────── particles ─────────
class Prt(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val kind: Int, val c: Color, val s: Float)
class Burst {
  val ps = ArrayList<Prt>()
  var tick by mutableLongStateOf(0L)
  var ver by mutableIntStateOf(0)
  fun emit(x: Float, y: Float, n: Int, kind: Int, c: Color) {
    repeat(n) {
      val a = Random.nextFloat() * 6.2832f; val v = 110f + Random.nextFloat() * 280f
      ps.add(Prt(x, y, cos(a) * v, sin(a) * v - 170f, 1f, if (kind < 0) Random.nextInt(2) else kind, c, 7f + Random.nextFloat() * 9f))
    }
    while (ps.size > 90) ps.removeAt(0)
    ver++
  }
}

@Composable fun ParticleLayer(b: Burst) {
  val v = b.ver
  LaunchedEffect(v) {
    var last = withFrameNanos { it }
    while (b.ps.isNotEmpty()) {
      val now = withFrameNanos { it }
      val dt = ((now - last) / 1e9f).coerceAtMost(.05f); last = now
      for (p in b.ps) { p.life -= dt * .85f; p.vy += 560f * dt; p.x += p.vx * dt; p.y += p.vy * dt }
      b.ps.removeAll { it.life <= 0f }
      b.tick = now
    }
  }
  Canvas(Modifier.fillMaxSize()) {
    val tk = b.tick
    if (tk >= 0L) for (p in b.ps) {
      val a = p.life.coerceIn(0f, 1f); val s = p.s * (.6f + .4f * a)
      if (p.kind == 0) drawHeart(p.x, p.y, s, p.c, a) else drawSpark(p.x, p.y, s * 1.2f, p.c, a)
    }
  }
}

// ───────── mascot ─────────
@Composable fun Mochi(sz: Dp, mood: Int = 0, m: Modifier = Modifier, tap: () -> Unit = {}) {
  val ck = LClock.current; val cf = LCfg.current; val ac = cf.ac; val ac2 = cf.ac2
  val jump = remember { Animatable(0f) }; val sc = rememberCoroutineScope()
  val src = remember { MutableInteractionSource() }
  Canvas(m.size(sz).clickable(src, null) { sc.launch { jump.snapTo(0f); jump.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }; tap() }) {
    val t = ck.value; val w = size.width; val h = size.height; val j = jump.value
    val hop = if (j > 0f && j < 1f) sin(j * 3.1416f) * h * .24f else 0f
    val sq = if (j > 0f && j < .12f) 1f else 0f
    val br = sin(t * 2.2f) * .03f
    val bw = w * .74f * (1f + br + sq * .08f); val bh = h * .6f * (1f - br - sq * .06f)
    val cx = w / 2f; val by = h * .88f - hop
    drawOval(Color.Black.copy(alpha = .15f * (1f - hop / h * 2f).coerceIn(.3f, 1f)), Offset(cx - bw * .45f, h * .86f), Size(bw * .9f, h * .07f))
    drawCircle(ac2, w * .07f, Offset(cx + bw * .52f, by - bh * .22f + sin(t * 5f) * h * .025f))
    val er = w * .115f
    for (sg in listOf(-1f, 1f)) {
      drawCircle(ac, er, Offset(cx + sg * bw * .3f, by - bh * .96f))
      drawCircle(ac2, er * .5f, Offset(cx + sg * bw * .3f, by - bh * .96f))
    }
    drawRoundRect(ac, Offset(cx - bw / 2, by - bh), Size(bw, bh), CornerRadius(bw * .46f))
    drawOval(Color.White.copy(alpha = .22f), Offset(cx - bw * .32f, by - bh * .94f), Size(bw * .3f, bh * .18f))
    val ey = by - bh * .56f; val ex = bw * .2f; val r = w * .05f
    val bl = t % 3.6f; val open = if (bl > 3.46f) .12f else 1f
    for (sg in listOf(-1f, 1f)) {
      val x = cx + sg * ex
      if (mood == 1) drawArc(INK, 200f, 140f, false, Offset(x - r, ey - r * .4f), Size(r * 2, r * 1.3f), style = Stroke(w * .02f, cap = StrokeCap.Round))
      else {
        val rr = if (mood == 3) r * 1.25f else r
        drawOval(INK, Offset(x - rr, ey - rr * 1.3f * open), Size(rr * 2, rr * 2.6f * open))
        if (open > .5f) drawCircle(Color.White, rr * .38f, Offset(x - rr * .25f, ey - rr * .55f))
      }
      drawCircle(Color(0xFFFF8FB1).copy(alpha = .55f), w * .05f, Offset(cx + sg * bw * .33f, by - bh * .36f))
    }
    val my = by - bh * .42f
    when (mood) {
      2 -> drawArc(INK, 200f, 140f, false, Offset(cx - w * .04f, my + w * .01f), Size(w * .08f, w * .06f), style = Stroke(w * .018f, cap = StrokeCap.Round))
      3 -> drawOval(INK, Offset(cx - w * .03f, my - w * .01f), Size(w * .06f, w * .07f))
      else -> drawArc(INK, 20f, 140f, false, Offset(cx - w * .04f, my - w * .03f), Size(w * .08f, w * .06f), style = Stroke(w * .018f, cap = StrokeCap.Round))
    }
  }
}

// ───────── procedural cute file icons ─────────
@Composable fun Ico(e: E, sz: Dp, sel: Boolean = false, m: Modifier = Modifier) {
  val ck = LClock.current; val cf = LCfg.current; val ctx = LocalContext.current
  val kind = Fx.kind(e)
  val hue = floatArrayOf(cf.hue, 330f, 265f, 195f, 40f, 125f, 215f, 20f, 0f)[kind]
  val lt = Th.pal.light
  val col = Color.hsv(hue, if (kind == 8) .12f else if (lt) .55f else .5f, if (lt) .92f else 1f)
  val dk = Color.hsv(hue, if (kind == 8) .15f else .6f, if (lt) .7f else .82f)
  val th = if (Thumbs.ok(e)) produceState<ImageBitmap?>(null, e.f.path) { value = withContext(Dispatchers.IO) { Thumbs.get(ctx.packageManager, e.f, 96)?.asImageBitmap() } }.value else null
  val wh = Color.White.copy(alpha = .9f)
  Canvas(m.size(sz).graphicsLayer { rotationZ = if (sel && cf.fx > 0) sin(ck.value * 9f) * 6f else 0f }) {
    val w = size.width; val h = size.height; val t = ck.value
    if (th != null) {
      val s = min(th.width, th.height)
      drawRoundRect(col, Offset.Zero, size, CornerRadius(w * .24f))
      drawImage(th, IntOffset((th.width - s) / 2, (th.height - s) / 2), IntSize(s, s), IntOffset(w.toInt() / 16, w.toInt() / 16), IntSize(w.toInt() * 7 / 8, h.toInt() * 7 / 8))
      return@Canvas
    }
    val bob = if (cf.fx > 0) sin(t * 3f + kind) * h * .015f else 0f
    var fy = h * .64f
    if (kind == 0) {
      drawRoundRect(dk, Offset(w * .06f, h * .16f + bob), Size(w * .42f, h * .2f), CornerRadius(w * .09f))
      drawRoundRect(col, Offset(w * .06f, h * .27f + bob), Size(w * .88f, h * .62f), CornerRadius(w * .18f))
      fy = h * .6f + bob
    } else {
      drawRoundRect(col, Offset(w * .17f, h * .08f + bob), Size(w * .66f, h * .84f), CornerRadius(w * .14f))
      drawRoundRect(dk, Offset(w * .6f, h * .08f + bob), Size(w * .23f, h * .22f), CornerRadius(w * .08f))
      val cx = w * .5f; val y = h * .31f + bob
      when (kind) {
        1 -> { drawCircle(wh, w * .06f, Offset(cx + w * .1f, y - h * .04f)); HP.reset(); HP.moveTo(cx - w * .2f, y + h * .1f); HP.lineTo(cx - w * .05f, y - h * .05f); HP.lineTo(cx + w * .1f, y + h * .1f); HP.close(); drawPath(HP, wh) }
        2 -> { HP.reset(); HP.moveTo(cx - w * .07f, y - h * .09f); HP.lineTo(cx + w * .1f, y); HP.lineTo(cx - w * .07f, y + h * .09f); HP.close(); drawPath(HP, wh) }
        3 -> { drawCircle(wh, w * .05f, Offset(cx - w * .04f, y + h * .07f)); drawLine(wh, Offset(cx + w * .01f, y + h * .07f), Offset(cx + w * .01f, y - h * .1f), w * .025f); drawLine(wh, Offset(cx + w * .01f, y - h * .1f), Offset(cx + w * .1f, y - h * .05f), w * .025f) }
        4 -> for (k in 0..2) drawRect(wh, Offset(cx - w * .03f + (k % 2) * w * .06f, y - h * .11f + k * h * .08f), Size(w * .06f, h * .05f))
        5 -> { drawArc(wh, 180f, 180f, true, Offset(cx - w * .12f, y - h * .02f), Size(w * .24f, h * .2f)); drawLine(wh, Offset(cx - w * .08f, y - h * .02f), Offset(cx - w * .12f, y - h * .1f), w * .02f); drawLine(wh, Offset(cx + w * .08f, y - h * .02f), Offset(cx + w * .12f, y - h * .1f), w * .02f) }
        6 -> { drawLine(wh, Offset(cx - w * .04f, y - h * .07f), Offset(cx - w * .11f, y), w * .03f); drawLine(wh, Offset(cx - w * .11f, y), Offset(cx - w * .04f, y + h * .07f), w * .03f); drawLine(wh, Offset(cx + w * .04f, y - h * .07f), Offset(cx + w * .11f, y), w * .03f); drawLine(wh, Offset(cx + w * .11f, y), Offset(cx + w * .04f, y + h * .07f), w * .03f) }
        7 -> for (k in 0..2) drawLine(wh, Offset(cx - w * .13f, y - h * .08f + k * h * .08f), Offset(cx + w * .13f - (k % 2) * w * .08f, y - h * .08f + k * h * .08f), w * .025f)
        else -> drawSpark(cx, y, w * .1f, wh)
      }
      fy = h * .68f + bob
    }
    val cx = w * .5f; val op = if ((t + kind * 1.3f) % 4f > 3.85f) .15f else 1f
    for (sg in listOf(-1f, 1f)) drawOval(INK, Offset(cx + sg * w * .12f - w * .04f, fy - w * .05f * op), Size(w * .08f, w * .1f * op))
    drawArc(INK, 20f, 140f, false, Offset(cx - w * .05f, fy + w * .02f), Size(w * .1f, w * .07f), style = Stroke(w * .025f, cap = StrokeCap.Round))
    if (sel) drawHeart(w * .88f, h * .14f, w * .09f, Color(0xFFFF6B9A))
  }
}

// ───────── rows, grid cells, pane ─────────
@Composable fun Rw(e: E, i: Int, sel: Boolean, jelly: () -> Float, tap: () -> Unit, lng: () -> Unit) {
  val cf = LCfg.current; val ac = cf.ac; val fx = cf.fx
  var on by remember { mutableStateOf(i >= 14 || fx == 0) }
  if (!on) LaunchedEffect(Unit) { delay((i * cf.stag).toLong()); on = true }
  val a by animateFloatAsState(if (on) 1f else 0f, spring(.6f, 300f), label = "a")
  val sh = RoundedCornerShape((cf.rad * .8f).dp)
  val sub = remember(e, cf.cSize, cf.cDate, cf.cPerm) { buildList { if (cf.cSize && !e.dir) add(Fx.sz(e.sz)); if (cf.cDate) add(Fx.dt(e.mt)); if (cf.cPerm) add(e.pm) }.joinToString(" · ") }
  val pop by animateFloatAsState(if (sel) 1f else 0f, spring(.4f, 500f), label = "p")
  Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.5.dp)
    .graphicsLayer {
      val k = a.coerceIn(0f, 1f)
      alpha = k; scaleX = .9f + .1f * k; scaleY = .9f + .1f * k
      translationX = (1f - a) * 70f + (if (fx == 2) jelly() * (if (i % 2 == 0) .35f else -.35f) else 0f)
    }
    .clip(sh).background(if (sel) ac.copy(alpha = .22f) else Color.Transparent)
    .combinedClickable(onClick = tap, onLongClick = lng).padding(horizontal = 6.dp, vertical = cf.pad.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Ico(e, 38.dp, sel)
    Column(Modifier.weight(1f).padding(start = 10.dp)) {
      T(e.n, if (e.dir) ac else Th.fg, 13f, FontWeight.Medium)
      if (sub.isNotEmpty()) T(sub, Th.dim, 10f)
    }
    if (pop > .01f) Canvas(Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop }) { drawHeart(size.width / 2, size.height / 2, size.width * .3f, Color(0xFFFF6B9A)) }
  }
}

@Composable fun Gc(e: E, sel: Boolean, tap: () -> Unit, lng: () -> Unit) {
  val cf = LCfg.current; val sh = RoundedCornerShape(cf.rad.dp)
  Column(Modifier.padding(3.dp).clip(sh).background(if (sel) cf.ac.copy(alpha = .22f) else Th.fg.copy(alpha = .04f))
    .combinedClickable(onClick = tap, onLongClick = lng).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Ico(e, 54.dp, sel)
    Text(e.n, color = if (e.dir) cf.ac else Th.fg, fontSize = (10 * cf.fs).sp, fontFamily = cf.ff, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
  }
}

@Composable fun Stor() {
  val cf = LCfg.current; val ac = cf.ac; val a2 = cf.ac2; val ck = LClock.current
  val (tot, fr) = remember { Fx.stor() }
  val used = if (tot > 0) (tot - fr).toFloat() / tot else 0f
  val grow = remember { Animatable(0f) }
  LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = Back)) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(5.dp))) {
      drawRect(Th.fg.copy(alpha = .1f))
      val w = size.width * used * grow.value.coerceAtMost(1.04f)
      drawRect(Brush.horizontalGradient(listOf(a2, ac)), size = Size(w, size.height))
      if (cf.fx > 0 && w > 0f) {
        val x = ((ck.value * .5f) % 1f) * (w + 90f) - 90f
        clipRect(right = w) { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .5f), Color.Transparent), startX = x, endX = x + 90f), Offset(x, 0f), Size(90f, size.height)) }
      }
    }
    T("${Fx.sz(tot - fr)} / ${Fx.sz(tot)}", Th.dim, 10f, null, Modifier.padding(start = 6.dp))
  }
}

@Composable fun PaneView(p: Pane, on: Boolean, m: Modifier, go: (File) -> Unit, tap: (E) -> Unit, lng: (E) -> Unit) {
  val cf = LCfg.current; val ac = cf.ac; val fx = cf.fx
  val sw = remember { Animatable(1f) }
  LaunchedEffect(p.snap.dir) { if (fx == 2) { sw.snapTo(0f); sw.animateTo(1f, tween(480)) } }
  val jl = remember { Animatable(0f) }; val sc = rememberCoroutineScope()
  val conn = remember {
    object : NestedScrollConnection {
      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f && cf.fx == 2) sc.launch { jl.snapTo((jl.value * .7f + consumed.y * .3f).coerceIn(-40f, 40f)); jl.animateTo(0f, spring(.45f, 120f)) }
        return Offset.Zero
      }
    }
  }
  val sh = RoundedCornerShape(cf.rad.dp)
  Column(m.fillMaxWidth().clip(sh).background(Th.fg.copy(alpha = .035f)).border(1.5.dp, if (on) ac.copy(alpha = .65f) else Th.fg.copy(alpha = .08f), sh)) {
    val parts = remember(p.snap.dir) { val l = ArrayList<File>(); var f: File? = p.snap.dir; while (f != null) { l.add(0, f); f = f.parentFile }; l }
    val bs = rememberScrollState()
    LaunchedEffect(parts) { delay(60); bs.animateScrollTo(bs.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(bs).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
      parts.forEach { f -> Bt(f.name.ifEmpty { "/" }, f == p.snap.dir) { go(f) }; Spacer(Modifier.width(3.dp)) }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
      AnimatedContent(p.snap, transitionSpec = {
        if (fx == 0 || initialState.dir == targetState.dir) EnterTransition.None togetherWith ExitTransition.None
        else {
          val d = if (targetState.dir.path.length < initialState.dir.path.length) -1 else 1
          if (fx == 1) fadeIn(tween(140)) togetherWith fadeOut(tween(90))
          else (slideInHorizontally(tween(340, easing = Back)) { it * d / 3 } + fadeIn(tween(220)) + scaleIn(tween(320, easing = Back), .92f)) togetherWith
            (slideOutHorizontally(tween(220)) { -it * d / 3 } + fadeOut(tween(120)))
        }
      }, label = "nav", contentKey = { it.dir }) { s ->
        if (s.items.isEmpty()) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Mochi(90.dp, 1); T("nothing here~ zzz", Th.dim) }
        else if (cf.grid) LazyVerticalGrid(GridCells.Adaptive(84.dp), Modifier.fillMaxSize().nestedScroll(conn)) {
          items(count = s.items.size, key = { s.items[it].f.path }) { i -> val e = s.items[i]; Gc(e, e.f.path in p.sel, { tap(e) }, { lng(e) }) }
        }
        else LazyColumn(Modifier.fillMaxSize().nestedScroll(conn)) {
          items(count = s.items.size, key = { s.items[it].f.path }, contentType = { if (s.items[it].dir) 0 else 1 }) { i ->
            val e = s.items[i]
            Rw(e, i, e.f.path in p.sel, { jl.value }, { tap(e) }, { lng(e) })
          }
        }
      }
      if (fx == 2) Canvas(Modifier.fillMaxSize()) {
        val v = sw.value
        if (v < 1f) {
          val y = size.height * v
          drawRect(Brush.verticalGradient(listOf(Color.Transparent, ac.copy(alpha = .4f * (1f - v)), Color.Transparent), startY = y - 36f, endY = y + 36f), Offset(0f, y - 36f), Size(size.width, 72f))
          for (k in 0..4) drawSpark(size.width * (k + .5f) / 5f, y + sin(k * 2f + v * 9f) * 12f, 7f, cf.ac2, 1f - v)
        }
      }
    }
    val st = remember(p.snap) { p.snap.items.size.toString() + " items · " + Fx.sz(p.snap.items.sumOf { it.sz }) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Scr(st, ac.copy(alpha = .9f), 10f)
      Spacer(Modifier.weight(1f))
      if (p.sel.isNotEmpty()) T("♡ ${p.sel.size}", cf.ac2, 11f, FontWeight.Bold)
    }
  }
}

// ───────── navigation, transitions, feedback ─────────
@Composable fun NavBar(tab: Int, go: (Int) -> Unit) {
  val cf = LCfg.current; val ac = cf.ac
  val idx = remember { Animatable(tab.toFloat()) }
  LaunchedEffect(tab) { if (cf.fx > 0) idx.animateTo(tab.toFloat(), spring(.5f, 380f)) else idx.snapTo(tab.toFloat()) }
  val items = listOf("🏠" to "home", "📁" to "files", "🔍" to "find", "🧰" to "tools", "🐾" to "me")
  val sh = RoundedCornerShape(26.dp)
  Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(sh).background(Th.fg.copy(alpha = .07f)).border(1.5.dp, Th.fg.copy(alpha = .1f), sh)
    .drawBehind {
      val iw = size.width / 5f; val pd = 5.dp.toPx()
      drawRoundRect(ac.copy(alpha = .3f), Offset(idx.value * iw + pd, pd), Size(iw - pd * 2, size.height - pd * 2), CornerRadius(22.dp.toPx()))
    }) {
    items.forEachIndexed { i, (ic, lb) ->
      val s by animateFloatAsState(if (i == tab && cf.fx > 0) 1.28f else 1f, spring(.35f, 400f), label = "n")
      Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { go(i) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }) { T(ic, Th.fg, 19f) }
        T(lb, if (i == tab) ac else Th.dim, 9f, if (i == tab) FontWeight.Bold else null)
      }
    }
  }
}

@Composable fun Wipe(tab: Int) {
  val cf = LCfg.current
  val pr = remember { Animatable(1f) }
  var last by remember { mutableIntStateOf(tab) }
  var dir by remember { mutableIntStateOf(1) }
  val rnd = remember { List(16) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
  LaunchedEffect(tab) {
    if (tab != last && cf.fx == 2) { dir = if (tab > last) 1 else -1; last = tab; pr.snapTo(0f); pr.animateTo(1f, tween(640, easing = LinearEasing)) } else last = tab
  }
  Canvas(Modifier.fillMaxSize()) {
    val p = pr.value
    if (p < 1f) for ((k, r) in rnd.withIndex()) {
      val q = (p * 1.35f - r.first * .35f).coerceIn(0f, 1f)
      val x = if (dir > 0) size.width * (1.1f - q * 1.3f) else size.width * (-.1f + q * 1.3f)
      val y = r.second * size.height; val bell = sin(q * 3.1416f)
      val c = if (k % 2 == 0) cf.ac else cf.ac2
      if (k % 4 == 3) drawHeart(x, y, size.width * .05f * (.5f + bell), c, bell * .6f)
      else drawCircle(c.copy(alpha = bell * .4f), size.width * .1f * (.4f + r.third) * (.5f + bell), Offset(x, y))
    }
  }
}

@Composable fun Snack(msg: String?) {
  val cf = LCfg.current
  var last by remember { mutableStateOf("") }
  if (msg != null && msg != last) SideEffect { last = msg }
  Box(Modifier.fillMaxWidth().padding(top = 10.dp), Alignment.TopCenter) {
    AnimatedVisibility(msg != null, enter = slideInVertically(spring(.5f, 400f)) { -it * 2 } + fadeIn(), exit = slideOutVertically { -it * 2 } + fadeOut()) {
      val sh = RoundedCornerShape(24.dp)
      Row(Modifier.clip(sh).background(Th.bg).border(1.5.dp, cf.ac, sh).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T("♡ ", cf.ac, 13f); T(msg ?: last, Th.fg, 12f, FontWeight.Medium, ml = 2)
      }
    }
  }
}

@Composable fun Dlg(d: Dg, close: () -> Unit) {
  val cf = LCfg.current
  val ok = d.onOk
  AlertDialog(onDismissRequest = close, containerColor = Th.bg, shape = RoundedCornerShape(28.dp), tonalElevation = 0.dp,
    title = { T(d.title, cf.ac, 15f, FontWeight.Bold) },
    text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { d.body() } },
    confirmButton = { if (ok != null) Bt(d.ok, true) { ok(); close() } else Bt(d.ok, true) { close() } },
    dismissButton = { if (ok != null) Bt("✕") { close() } })
}

// ───────── welcome ─────────
@Composable fun Welcome(ready: Boolean, access: Boolean, ask: () -> Unit, done: () -> Unit) {
  val cf = LCfg.current; val ck = LClock.current
  val tm = remember { Animatable(0f) }
  val cap = rememberUpdatedState(if (ready && access) 1f else if (access) .9f else .5f)
  val pop = remember { Animatable(0f) }
  LaunchedEffect(Unit) { pop.animateTo(1f, spring(.4f, 150f)) }
  LaunchedEffect(Unit) { tm.animateTo(1f, tween(2600, easing = LinearEasing)) }
  LaunchedEffect(Unit) {
    var n = 0; var tot = 0L; var last = withFrameNanos { it }
    while (n < 50) { val t = withFrameNanos { it }; if (n >= 15) tot += t - last; last = t; n++ }
    val avg = tot / 35 / 1e6
    if (cf.auto) Th.cap = if (avg > 40) 0 else if (avg > 26) 1 else 2
  }
  LaunchedEffect(ready, access) { if (ready && access) { snapshotFlow { tm.value }.first { it >= 1f }; delay(300); done() } }
  val steps = listOf("waking mochi up…", "sniffing your folders…", "counting storage crumbs…", "sprinkling sparkles…", "all set! ♡")
  val step by remember { derivedStateOf { (minOf(tm.value, cap.value) * 4.99f).toInt().coerceIn(0, 4) } }
  val h = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
  val greet = when { h < 5 -> "still awake? (・ω・)"; h < 11 -> "good morning!"; h < 17 -> "hello hello!"; h < 21 -> "good evening~"; else -> "sleepy time soon…" }
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Th.bg, cf.ac.copy(alpha = .22f).compositeOver(Th.bg)))).pointerInput(Unit) { detectTapGestures { } }) {
    Canvas(Modifier.fillMaxSize()) {
      val t = ck.value
      for (k in 0 until 16) {
        val x = size.width * (((k * 37) % 100) / 100f) + sin(t * .8f + k) * 18f
        val y = size.height - ((t * (18f + (k % 5) * 9f) + k * 90f) % (size.height + 80f)) + 40f
        val s = 5f + (k % 4) * 3f; val c = if (k % 2 == 0) cf.ac else cf.ac2
        when (k % 3) { 0 -> drawHeart(x, y, s, c, .35f); 1 -> drawSpark(x, y, s * 1.3f, c, .4f); else -> drawCircle(c.copy(alpha = .25f), s, Offset(x, y)) }
      }
    }
    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Mochi(160.dp, if (step < 1) 1 else if (step == 4) 3 else 0, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
      Wave("NerdFM", 32f)
      T(greet, cf.ac2, 14f, FontWeight.Medium)
      T(steps[step], Th.dim, 12f)
      Canvas(Modifier.size(220.dp, 30.dp)) {
        val p = minOf(tm.value, cap.value)
        for (i in 0 until 7) {
          val cx = size.width * (i + .5f) / 7f; val f = (p * 7f - i).coerceIn(0f, 1f)
          drawHeart(cx, size.height / 2, 9f, Th.fg, .15f)
          if (f > 0f) drawHeart(cx, size.height / 2, 9f * (.4f + .6f * f), cf.ac, f)
        }
      }
      if (!access) {
        T("mochi needs all-files access to peek into your folders ♡", Th.fg, 12f, null, Modifier.padding(top = 8.dp), 3)
        Bt("GIVE ACCESS ♡", true, Modifier, ask)
      }
    }
  }
}
