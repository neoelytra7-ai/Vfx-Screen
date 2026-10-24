@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.sin

typealias Subs = List<Pair<String, () -> Unit>>
val LNoEd = compositionLocalOf { false }

private val AbovePos = object : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
    val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
    val above = anchorBounds.top - popupContentSize.height - 8
    return IntOffset(x, if (above > 8) above else anchorBounds.bottom + 8)
  }
}

@Composable fun SubMenu(subs: Subs, close: () -> Unit) {
  val cf = LCfg.current
  val s = remember { Animatable(.4f) }
  LaunchedEffect(Unit) { s.animateTo(1f, spring(.45f, 420f)) }
  Popup(popupPositionProvider = AbovePos, onDismissRequest = close, properties = PopupProperties(focusable = true)) { CompositionLocalProvider(LNoEd provides true) {
    val sh = RoundedCornerShape(20.dp)
    Column(Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value.coerceIn(0f, 1f) }
      .clip(sh).background(Th.bg).border(1.5.dp, cf.ac.copy(alpha = .6f), sh).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      subs.forEachIndexed { i, (n, f) -> Pop(i) { Bt(n) { close(); f() } } }
    }
  } }
}

@Composable fun Hold(subs: Subs, m: Modifier = Modifier, click: () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
  var open by remember { mutableStateOf(false) }
  val hp = LocalHapticFeedback.current; val cf = LCfg.current
  val lc: (() -> Unit)? = if (subs.isEmpty()) null else ({ if (cf.haptic) hp.performHapticFeedback(HapticFeedbackType.LongPress); open = true })
  Box(m.combinedClickable(onClick = click, onLongClick = lc)) {
    content()
    if (open) SubMenu(subs) { open = false }
  }
}

// edit-mode overlay: dashed outline + captures taps to select the element (parent-first pass, consumes)
@Composable fun Modifier.ed(id: String): Modifier {
  if (!Ed.on || LNoEd.current) return this
  val sel = Ed.sel == id
  val ac = LCfg.current.ac
  return this.drawWithContent {
    drawContent()
    drawRoundRect(ac.copy(alpha = if (sel) .95f else .5f), cornerRadius = CornerRadius(12f), style = Stroke(if (sel) 3.5f else 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
  }.pointerInput(id) {
    awaitPointerEventScope {
      while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        ev.changes.forEach { it.consume() }
        if (ev.type == PointerEventType.Press) Ed.sel = id
      }
    }
  }
}

object Tr {
  fun make(style: Int, dir: Int, spd: Float, b: Float): ContentTransform {
    val e = CubicBezierEasing(.34f, 1f + 1.1f * b, .64f, 1f)
    val d = (360 / spd).toInt().coerceAtLeast(60); val d2 = d * 2 / 3; val d3 = d / 2
    return when (style) {
      1 -> (scaleIn(tween(d, easing = e), .75f) + fadeIn(tween(d2))) togetherWith (scaleOut(tween(d2), 1.12f) + fadeOut(tween(d3)))
      2 -> (scaleIn(tween(d, easing = e), 1.25f) + fadeIn(tween(d2))) togetherWith (scaleOut(tween(d2), .8f) + fadeOut(tween(d3)))
      3 -> (slideInVertically(tween(d, easing = e)) { -it / 2 * dir } + fadeIn(tween(d2))) togetherWith (slideOutVertically(tween(d2)) { it / 3 * dir } + fadeOut(tween(d3)))
      4 -> fadeIn(tween(d2)) togetherWith fadeOut(tween(d3))
      else -> (slideInHorizontally(tween(d, easing = e)) { it * dir / 2 } + fadeIn(tween(d2)) + scaleIn(tween(d, easing = e), .9f)) togetherWith
        (slideOutHorizontally(tween(d2)) { -it * dir / 3 } + fadeOut(tween(d3)) + scaleOut(tween(d2), .94f))
    }
  }
  fun tab(fx: Int, dir: Int): ContentTransform = pick("tr.tab", fx, dir)
  fun folder(fx: Int, dir: Int): ContentTransform = pick("tr.dir", fx, dir)
  private fun pick(k: String, fx: Int, dir: Int): ContentTransform =
    if (fx == 0) EnterTransition.None togetherWith ExitTransition.None
    else if (fx == 1) fadeIn(tween(160)) togetherWith fadeOut(tween(100))
    else make(Sk.f(k, 0f).toInt(), dir, Sk.f("tr.speed", 1f), Sk.f("tr.bounce", .5f))
}

// ───────── procedural glyphs (nav, tiles, tools) ─────────
@Composable fun Gly(n: String, sz: Dp, on: Boolean = false, m: Modifier = Modifier) {
  val cf = LCfg.current; val ck = LClock.current
  val col = if (on) cf.ac else Th.fg; val soft = cf.ac2
  val seed = (n.hashCode() and 7).toFloat()
  Canvas(m.size(sz)) {
    val u = size.width; val t = ck.value; val sw = u * .08f
    val bob = if (cf.fx > 0) sin(t * 2f + seed) * u * .02f else 0f
    drawCircle(cf.ac.copy(alpha = if (on) .22f else .10f), u * .48f, Offset(u / 2, u / 2))
    translate(0f, bob) {
      fun l(a: Float, b: Float, c: Float, d: Float) = drawLine(col, Offset(a * u, b * u), Offset(c * u, d * u), sw, StrokeCap.Round)
      fun pl(vararg p: Float) {
        HP.reset(); HP.moveTo(p[0] * u, p[1] * u)
        var i = 2
        while (i < p.size) { HP.lineTo(p[i] * u, p[i + 1] * u); i += 2 }
        drawPath(HP, col, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
      fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(col, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = Stroke(sw))
      fun o(x: Float, y: Float, r: Float) = drawCircle(col, r * u, Offset(x * u, y * u), style = Stroke(sw))
      fun dot(x: Float, y: Float, r: Float, c: Color) = drawCircle(c, r * u, Offset(x * u, y * u))
      when (n) {
        "home" -> { pl(.2f, .5f, .5f, .22f, .8f, .5f); pl(.28f, .46f, .28f, .78f, .72f, .78f, .72f, .46f); dot(.5f, .6f, .06f, soft) }
        "folder" -> { rr(.18f, .34f, .64f, .46f, .1f); pl(.18f, .34f, .18f, .28f, .4f, .28f, .48f, .36f) }
        "search", "lens" -> { o(.45f, .45f, .2f); l(.6f, .6f, .78f, .78f); if (n == "lens") l(.36f, .45f, .54f, .45f) }
        "tools" -> { rr(.2f, .4f, .6f, .38f, .1f); pl(.38f, .4f, .38f, .28f, .62f, .28f, .62f, .4f); l(.2f, .56f, .8f, .56f); dot(.5f, .56f, .05f, soft) }
        "paw" -> { dot(.5f, .64f, .14f, col); dot(.3f, .46f, .06f, soft); dot(.43f, .34f, .06f, soft); dot(.57f, .34f, .06f, soft); dot(.7f, .46f, .06f, soft) }
        "clock" -> { o(.5f, .5f, .27f); val a = t * .8f; l(.5f, .5f, .5f + sin(a) * .14f, .5f - kotlin.math.cos(a) * .14f); l(.5f, .5f, .6f, .5f) }
        "photo" -> { rr(.2f, .28f, .6f, .44f, .06f); dot(.64f, .42f, .05f, soft); pl(.24f, .68f, .42f, .48f, .56f, .62f, .62f, .56f, .76f, .68f) }
        "video" -> { rr(.18f, .3f, .64f, .4f, .06f); HP.reset(); HP.moveTo(.45f * u, .4f * u); HP.lineTo(.62f * u, .5f * u); HP.lineTo(.45f * u, .6f * u); HP.close(); drawPath(HP, soft) }
        "music" -> { dot(.38f, .7f, .08f, col); dot(.66f, .64f, .08f, col); l(.45f, .7f, .45f, .28f); l(.73f, .64f, .73f, .24f); l(.45f, .28f, .73f, .24f) }
        "doc" -> { rr(.28f, .2f, .44f, .6f, .06f); l(.37f, .4f, .63f, .4f); l(.37f, .52f, .63f, .52f); l(.37f, .64f, .54f, .64f) }
        "box" -> { rr(.2f, .34f, .6f, .44f, .06f); l(.2f, .46f, .8f, .46f); l(.5f, .34f, .5f, .58f); dot(.5f, .62f, .04f, soft) }
        "droid" -> { drawArc(col, 180f, 180f, false, Offset(.28f * u, .3f * u), Size(.44f * u, .4f * u), style = Stroke(sw)); rr(.28f, .5f, .44f, .26f, .05f); l(.38f, .3f, .33f, .22f); l(.62f, .3f, .67f, .22f); dot(.42f, .42f, .03f, soft); dot(.58f, .42f, .03f, soft) }
        "big" -> { pl(.3f, .74f, .4f, .4f, .6f, .4f, .7f, .74f, .3f, .74f); o(.5f, .3f, .08f) }
        "down" -> { l(.5f, .22f, .5f, .6f); pl(.34f, .46f, .5f, .62f, .66f, .46f); l(.28f, .76f, .72f, .76f) }
        "broom" -> { l(.72f, .22f, .44f, .56f); pl(.44f, .56f, .3f, .78f, .58f, .72f, .44f, .56f); drawSpark(.3f * u, .3f * u, u * .07f * (1f + .3f * sin(t * 4f)), soft) }
        "trash" -> { rr(.3f, .36f, .4f, .44f, .06f); l(.24f, .3f, .76f, .3f); pl(.42f, .3f, .42f, .2f, .58f, .2f, .58f, .3f); l(.42f, .46f, .42f, .7f); l(.58f, .46f, .58f, .7f) }
        "dup" -> { rr(.22f, .22f, .42f, .42f, .08f); rr(.36f, .36f, .42f, .42f, .08f) }
        "chart" -> { rr(.24f, .5f, .12f, .28f, .03f); rr(.44f, .34f, .12f, .44f, .03f); rr(.64f, .22f, .12f, .56f, .03f) }
        "folderx" -> { rr(.18f, .34f, .64f, .46f, .1f); l(.42f, .48f, .58f, .66f); l(.58f, .48f, .42f, .66f) }
        "cmp" -> { pl(.22f, .38f, .76f, .38f, .64f, .28f); pl(.78f, .62f, .24f, .62f, .36f, .72f) }
        "edit" -> { l(.28f, .72f, .64f, .36f); l(.64f, .36f, .72f, .44f); l(.72f, .44f, .36f, .8f); l(.36f, .8f, .26f, .82f) }
        "squish" -> { l(.2f, .5f, .4f, .5f); pl(.32f, .42f, .4f, .5f, .32f, .58f); l(.8f, .5f, .6f, .5f); pl(.68f, .42f, .6f, .5f, .68f, .58f) }
        else -> drawSpark(.5f * u, .5f * u, u * .28f, col)
      }
    }
    if (on) drawSpark(u * .82f, u * .18f, u * .07f * (1f + sin(t * 5f) * .3f), soft)
  }
}

fun mochiSubs(cf: Cfg): Subs = listOf(
  "pat ♡" to { Hub.cheer(14) },
  "edit mode ✎" to { Ed.on = true },
  "next theme" to { cf.applyPreset((cf.preset + 1) % Th.pals.size) },
  "cleaner" to { Hub.act("nav:clean") })

@Composable fun Decor() {
  val cf = LCfg.current; val ck = LClock.current
  Canvas(Modifier.fillMaxSize()) {
    val t = ck.value
    for (k in 0 until 14) {
      val x = size.width * (((k * 41) % 100) / 100f) + sin(t * .7f + k) * 16f
      val y = size.height - ((t * (14f + (k % 5) * 7f) + k * 100f) % (size.height + 80f)) + 40f
      val s = 5f + (k % 4) * 3f; val c = if (k % 2 == 0) cf.ac else cf.ac2
      when (k % 3) { 0 -> drawHeart(x, y, s, c, .2f); 1 -> drawSpark(x, y, s * 1.3f, c, .25f); else -> drawCircle(c.copy(alpha = .14f), s, Offset(x, y)) }
    }
  }
}
