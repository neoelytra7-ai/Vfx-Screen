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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
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

private val LeftPos = object : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
    val x = (anchorBounds.left - popupContentSize.width - 10).coerceAtLeast(8)
    val y = (anchorBounds.center.y - popupContentSize.height / 2).coerceIn(8, (windowSize.height - popupContentSize.height - 8).coerceAtLeast(8))
    return IntOffset(x, y)
  }
}

@Composable fun SubChip(g: String, lb: String, i: Int, f: () -> Unit) {
  val cf = LCfg.current; val sh = RoundedCornerShape(14.dp)
  Pop(i) {
    Row(Modifier.clip(sh).background(cf.ac.copy(alpha = .10f)).border(1.dp, cf.ac.copy(alpha = .4f), sh).clickable { f() }.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (g.isNotEmpty()) { Gly(g, 20.dp); Spacer(Modifier.width(5.dp)) }
      T(lb, Th.fg, 11f, FontWeight.Medium)
    }
  }
}

@Composable fun SubMenu(subs: Subs, left: Boolean = false, close: () -> Unit) {
  val cf = LCfg.current
  val s = remember { Animatable(.6f) }
  LaunchedEffect(Unit) { s.animateTo(1f, spring(.55f, 600f)) }
  Popup(popupPositionProvider = if (left) LeftPos else AbovePos, onDismissRequest = close, properties = PopupProperties(focusable = true)) { CompositionLocalProvider(LNoEd provides true) {
    val sh = RoundedCornerShape(22.dp)
    val cols = if (subs.size > 3) 2 else 1
    Column(Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value.coerceIn(0f, 1f) }
      .clip(sh).background(Th.bg).border(1.5.dp, cf.ac.copy(alpha = .55f), sh).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      subs.chunked(cols).forEachIndexed { r, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          row.forEachIndexed { c, (n, f) -> SubChip(n.substringBefore('|', ""), n.substringAfter('|'), r * cols + c) { close(); f() } }
        }
      }
    }
  } }
}

class SItem(val g: String, val label: String, val on: Boolean = false, val subs: Subs = emptyList(), val tapMenu: Boolean = false, val click: () -> Unit)

@Composable fun SideItem(si: SItem) {
  val cf = LCfg.current; val hp = LocalHapticFeedback.current
  val src = remember { MutableInteractionSource() }
  val pr by src.collectIsPressedAsState()
  val sq by animateFloatAsState(if (pr && cf.fx > 0) .88f else 1f, spring(.4f, 600f), label = "si")
  var menu by remember { mutableStateOf(false) }
  val sh = RoundedCornerShape(14.dp)
  Box(Modifier.fillMaxWidth().graphicsLayer { scaleX = sq; scaleY = sq }.clip(sh)
    .background(if (si.on) cf.ac.copy(alpha = .22f) else Th.fg.copy(alpha = .04f))
    .border(1.dp, if (si.on) cf.ac.copy(alpha = .7f) else cf.ac.copy(alpha = .22f), sh)
    .combinedClickable(src, null, onClick = { if (cf.haptic) hp.performHapticFeedback(HapticFeedbackType.TextHandleMove); if (si.tapMenu) menu = true else si.click() },
      onLongClick = if (si.subs.isEmpty()) null else ({ if (cf.haptic) hp.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }))
    .padding(vertical = 4.dp)) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Gly(si.g, 24.dp, si.on)
      T(si.label, if (si.on) cf.ac else Th.dim, 8f)
    }
    if (menu) SubMenu(si.subs, true) { menu = false }
  }
}

@Composable fun SideBar(items: List<SItem>, m: Modifier = Modifier) {
  val cf = LCfg.current; val sh = RoundedCornerShape(18.dp)
  Column(m.width(54.dp).clip(sh).background(Th.fg.copy(alpha = .03f)).border(1.5.dp, cf.ac.copy(alpha = .35f), sh).verticalScroll(rememberScrollState()).padding(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    items.forEach { SideItem(it) }
  }
}

@Composable fun IconBtn(g: String, subs: Subs = emptyList(), tapMenu: Boolean = false, on: Boolean = false, click: () -> Unit) {
  val cf = LCfg.current; val hp = LocalHapticFeedback.current
  val src = remember { MutableInteractionSource() }
  val pr by src.collectIsPressedAsState()
  val sq by animateFloatAsState(if (pr && cf.fx > 0) .85f else 1f, spring(.4f, 600f), label = "ib")
  var menu by remember { mutableStateOf(false) }
  val sh = RoundedCornerShape(14.dp)
  Box(Modifier.graphicsLayer { scaleX = sq; scaleY = sq }.clip(sh).background(if (on) cf.ac.copy(alpha = .22f) else Th.fg.copy(alpha = .04f))
    .border(1.dp, if (on) cf.ac.copy(alpha = .7f) else cf.ac.copy(alpha = .3f), sh)
    .combinedClickable(src, null, onClick = { if (cf.haptic) hp.performHapticFeedback(HapticFeedbackType.TextHandleMove); if (tapMenu) menu = true else click() },
      onLongClick = if (subs.isEmpty()) null else ({ if (cf.haptic) hp.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }))
    .padding(5.dp)) {
    Gly(g, 24.dp, on)
    if (menu) SubMenu(subs) { menu = false }
  }
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
    val e: Easing = if (b < .3f) FastOutSlowInEasing else CubicBezierEasing(.34f, 1f + 1.1f * b, .64f, 1f)
    val d = (300 / spd).toInt().coerceAtLeast(60); val d2 = d * 2 / 3; val d3 = d / 2
    return when (style) {
      1 -> (scaleIn(tween(d, easing = e), .92f) + fadeIn(tween(d, delayMillis = d3 / 2))) togetherWith fadeOut(tween(d3))
      2 -> (scaleIn(tween(d, easing = e), 1.08f) + fadeIn(tween(d, delayMillis = d3 / 2))) togetherWith (scaleOut(tween(d2), .96f) + fadeOut(tween(d3)))
      3 -> (slideInVertically(tween(d, easing = e)) { -it / 10 * dir } + fadeIn(tween(d, delayMillis = d3 / 2))) togetherWith (slideOutVertically(tween(d2)) { it / 12 * dir } + fadeOut(tween(d3)))
      4 -> fadeIn(tween(d, delayMillis = d3 / 2)) togetherWith fadeOut(tween(d3))
      else -> (slideInHorizontally(tween(d, easing = e)) { it * dir / 7 } + fadeIn(tween(d, delayMillis = d3 / 2))) togetherWith (slideOutHorizontally(tween(d2)) { -it * dir / 12 } + fadeOut(tween(d3)))
    }
  }
  fun tab(fx: Int, dir: Int): ContentTransform = pick("tr.tab", fx, dir)
  fun folder(fx: Int, dir: Int): ContentTransform = pick("tr.dir", fx, dir)
  private fun pick(k: String, fx: Int, dir: Int): ContentTransform =
    if (fx == 0) EnterTransition.None togetherWith ExitTransition.None
    else if (fx == 1) fadeIn(tween(140)) togetherWith fadeOut(tween(90))
    else make(Sk.f(k, 0f).toInt(), dir, Sk.f("tr.speed", 1f), Sk.f("tr.bounce", .15f))
}

// ───────── procedural glyphs (nav, tiles, tools) ─────────
@Composable fun Gly(n: String, sz: Dp, on: Boolean = false, m: Modifier = Modifier) {
  val cf = LCfg.current; val ck = LClock.current
  val col = if (on) cf.ac else Th.fg; val soft = cf.ac2
  val seed = (n.hashCode() and 7).toFloat()
  val slow = LSlow.current
  Canvas(m.size(sz)) {
    val u = size.width; val t = if (on) ck.value else slow.value; val sw = u * .08f
    val bob = if (cf.fx > 0 && on) sin(t * 2f + seed) * u * .02f else 0f
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
        "up" -> { l(.5f, .78f, .5f, .28f); pl(.3f, .46f, .5f, .26f, .7f, .46f) }
        "folderplus" -> { rr(.18f, .34f, .64f, .46f, .1f); l(.5f, .46f, .5f, .68f); l(.39f, .57f, .61f, .57f) }
        "fileplus" -> { rr(.28f, .2f, .44f, .6f, .06f); l(.5f, .4f, .5f, .62f); l(.39f, .51f, .61f, .51f) }
        "paste" -> { rr(.26f, .26f, .48f, .54f, .06f); rr(.4f, .18f, .2f, .12f, .04f); l(.36f, .5f, .64f, .5f); l(.36f, .62f, .56f, .62f) }
        "copy" -> { rr(.22f, .22f, .42f, .42f, .08f); rr(.36f, .36f, .42f, .42f, .08f) }
        "cut" -> { l(.3f, .26f, .62f, .68f); l(.7f, .26f, .38f, .68f); o(.32f, .74f, .07f); o(.68f, .74f, .07f) }
        "share" -> { dot(.3f, .5f, .07f, col); dot(.7f, .3f, .07f, col); dot(.7f, .7f, .07f, col); l(.3f, .5f, .7f, .3f); l(.3f, .5f, .7f, .7f) }
        "info" -> { o(.5f, .5f, .27f); l(.5f, .46f, .5f, .64f); dot(.5f, .36f, .035f, col) }
        "all" -> { rr(.24f, .24f, .52f, .52f, .08f); pl(.36f, .5f, .46f, .6f, .66f, .4f) }
        "inv" -> { rr(.24f, .24f, .52f, .52f, .08f); l(.3f, .7f, .7f, .3f) }
        "x" -> { l(.32f, .32f, .68f, .68f); l(.68f, .32f, .32f, .68f) }
        "refresh" -> { drawArc(col, 40f, 270f, false, Offset(.28f * u, .28f * u), Size(.44f * u, .44f * u), style = Stroke(sw, cap = StrokeCap.Round)); pl(.64f, .22f, .72f, .34f, .58f, .38f) }
        "grid" -> { rr(.24f, .24f, .2f, .2f, .04f); rr(.56f, .24f, .2f, .2f, .04f); rr(.24f, .56f, .2f, .2f, .04f); rr(.56f, .56f, .2f, .2f, .04f) }
        "list" -> { l(.26f, .3f, .74f, .3f); l(.26f, .5f, .74f, .5f); l(.26f, .7f, .74f, .7f) }
        "sort" -> { l(.3f, .72f, .3f, .3f); pl(.2f, .4f, .3f, .28f, .4f, .4f); l(.55f, .32f, .78f, .32f); l(.55f, .5f, .72f, .5f); l(.55f, .68f, .66f, .68f) }
        "eye" -> { drawOval(col, Offset(.2f * u, .34f * u), Size(.6f * u, .32f * u), style = Stroke(sw)); dot(.5f, .5f, .07f, soft) }
        "dual" -> { rr(.2f, .26f, .26f, .48f, .05f); rr(.54f, .26f, .26f, .48f, .05f) }
        "more" -> { dot(.3f, .5f, .05f, col); dot(.5f, .5f, .05f, col); dot(.7f, .5f, .05f, col) }
        "check" -> pl(.28f, .52f, .44f, .68f, .74f, .34f)
        "heart" -> drawHeart(.5f * u, .5f * u, u * .22f, col)
        "wand" -> { l(.3f, .75f, .62f, .43f); drawSpark(.7f * u, .3f * u, u * .12f, soft); dot(.3f, .3f, .03f, soft) }
        "rotr" -> { drawArc(col, -60f, 280f, false, Offset(.26f * u, .28f * u), Size(.48f * u, .48f * u), style = Stroke(sw, cap = StrokeCap.Round)); pl(.66f, .2f, .74f, .32f, .6f, .36f) }
        "crop" -> { pl(.3f, .2f, .3f, .7f, .8f, .7f); pl(.2f, .3f, .7f, .3f, .7f, .8f) }
        "sliders" -> { l(.25f, .3f, .75f, .3f); l(.25f, .5f, .75f, .5f); l(.25f, .7f, .75f, .7f); dot(.4f, .3f, .06f, soft); dot(.62f, .5f, .06f, soft); dot(.35f, .7f, .06f, soft) }
        "brush" -> { l(.28f, .74f, .6f, .42f); l(.6f, .42f, .72f, .3f); dot(.26f, .76f, .07f, soft) }
        "sticker" -> { o(.5f, .5f, .27f); dot(.42f, .44f, .035f, col); dot(.58f, .44f, .035f, col); drawArc(col, 20f, 140f, false, Offset(.4f * u, .46f * u), Size(.2f * u, .16f * u), style = Stroke(sw, cap = StrokeCap.Round)) }
        "wall" -> { rr(.26f, .2f, .48f, .6f, .08f); pl(.3f, .66f, .42f, .5f, .52f, .6f, .6f, .52f, .7f, .66f) }
        "play" -> { HP.reset(); HP.moveTo(.38f * u, .28f * u); HP.lineTo(.7f * u, .5f * u); HP.lineTo(.38f * u, .72f * u); HP.close(); drawPath(HP, col, style = Stroke(sw, join = StrokeJoin.Round)) }
        "save" -> { rr(.24f, .24f, .52f, .52f, .06f); rr(.34f, .24f, .32f, .16f, .02f); rr(.34f, .52f, .32f, .24f, .02f) }
        "undo" -> { pl(.4f, .3f, .26f, .44f, .4f, .58f); pl(.26f, .44f, .64f, .44f, .74f, .54f, .74f, .7f) }
        "plus" -> { l(.5f, .28f, .5f, .72f); l(.28f, .5f, .72f, .5f) }
        "tab" -> { rr(.2f, .3f, .6f, .44f, .06f); l(.2f, .4f, .5f, .4f) }
        else -> drawSpark(.5f * u, .5f * u, u * .28f, col)
      }
    }
    if (on) drawSpark(u * .82f, u * .18f, u * .07f * (1f + sin(t * 5f) * .3f), soft)
  }
}

fun mochiSubs(cf: Cfg): Subs = listOf(
  "star|pat ♡" to { Hub.cheer(14) },
  "edit|edit mode" to { Ed.on = true },
  "refresh|next theme" to { cf.applyPreset((cf.preset + 1) % Th.pals.size) },
  "broom|cleaner" to { Hub.act("nav:clean") },
  "chart|storage" to { Hub.act("nav:break") },
  "trash|trash" to { Hub.act("nav:trash") })

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
