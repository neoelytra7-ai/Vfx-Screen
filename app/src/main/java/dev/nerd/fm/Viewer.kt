@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.nerd.fm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import android.graphics.Canvas as ACanvas
import android.graphics.ColorMatrix as AColorMatrix
import android.graphics.ColorMatrixColorFilter as AColorFilter
import android.graphics.Matrix as AMatrix
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import android.graphics.Rect as ARect

class Stk(val color: Int, val w: Float, val a: Float) { val pts = ArrayList<Offset>() }
class Stc(var x: Float, var y: Float, val s: String, val size: Float)
data class CropR(val l: Float, val t: Float, val r: Float, val b: Float)
class Fil(val name: String, val m: FloatArray)

object Img {
  val ID = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
  fun mul(a: FloatArray, b: FloatArray): FloatArray {
    val r = FloatArray(20)
    for (i in 0..3) for (j in 0..4) { var s = 0f; for (k in 0..3) s += a[i * 5 + k] * b[k * 5 + j]; if (j == 4) s += a[i * 5 + 4]; r[i * 5 + j] = s }
    return r
  }
  fun sat(s: Float) = floatArrayOf(.213f + .787f * s, .715f - .715f * s, .072f - .072f * s, 0f, 0f, .213f - .213f * s, .715f + .285f * s, .072f - .072f * s, 0f, 0f, .213f - .213f * s, .715f - .715f * s, .072f + .928f * s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
  fun con(c: Float): FloatArray { val o = 128f * (1f - c); return floatArrayOf(c, 0f, 0f, 0f, o, 0f, c, 0f, 0f, o, 0f, 0f, c, 0f, o, 0f, 0f, 0f, 1f, 0f) }
  fun bri(b: Float): FloatArray { val o = b * 2.55f; return floatArrayOf(1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f) }
  fun warm(w: Float) = floatArrayOf(1f + .2f * w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - .2f * w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
  val FILTERS = listOf(
    Fil("Original", ID),
    Fil("Pastel", mul(bri(10f), mul(con(.88f), sat(.75f)))),
    Fil("Sakura", mul(bri(6f), mul(floatArrayOf(1.08f, 0f, 0f, 0f, 6f, 0f, .97f, 0f, 0f, 0f, 0f, 0f, 1.05f, 0f, 4f, 0f, 0f, 0f, 1f, 0f), sat(1.1f)))),
    Fil("Mint", mul(bri(4f), mul(floatArrayOf(.94f, 0f, 0f, 0f, 0f, 0f, 1.05f, 0f, 0f, 6f, 0f, 0f, 1.05f, 0f, 6f, 0f, 0f, 0f, 1f, 0f), sat(.95f)))),
    Fil("Cocoa", floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    Fil("Dream", mul(bri(16f), mul(con(.85f), sat(1.2f)))),
    Fil("Pop", mul(con(1.15f), sat(1.5f))),
    Fil("Fade", mul(bri(12f), mul(con(.8f), sat(.85f)))),
    Fil("Sunny", mul(bri(6f), warm(1f))),
    Fil("Frost", mul(sat(.9f), warm(-1f))),
    Fil("Noir", mul(con(1.3f), sat(0f))),
    Fil("Invert", floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
  )
  fun matrix(b: Float, c: Float, s: Float, w: Float, fil: Int): FloatArray {
    var m = FILTERS[fil.coerceIn(0, FILTERS.size - 1)].m
    m = mul(warm(w), m); m = mul(sat(s), m); m = mul(con(c), m); m = mul(bri(b), m)
    return m
  }

  private fun exifM(f: File): AMatrix? {
    val o = try { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (e: Exception) { 1 }
    val m = AMatrix()
    when (o) { 6 -> m.postRotate(90f); 3 -> m.postRotate(180f); 8 -> m.postRotate(270f); 2 -> m.postScale(-1f, 1f); 4 -> m.postScale(1f, -1f); 5 -> { m.postRotate(90f); m.postScale(-1f, 1f) }; 7 -> { m.postRotate(270f); m.postScale(-1f, 1f) }; else -> return null }
    return m
  }
  fun load(f: File, mx: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / s > mx * 3 / 2 || o.outHeight / s > mx * 3 / 2) s *= 2
    var b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val m = exifM(f)
    if (m != null) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    val big = max(b.width, b.height)
    if (big > mx) { val r = mx.toFloat() / big; b = Bitmap.createScaledBitmap(b, (b.width * r).toInt().coerceAtLeast(1), (b.height * r).toInt().coerceAtLeast(1), true) }
    return b
  }
  fun exif(f: File): List<String> {
    val o = ArrayList<String>()
    try {
      val e = ExifInterface(f.path)
      val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(f.path, bo)
      o.add("size  ${bo.outWidth}×${bo.outHeight} · ${Fx.sz(f.length())}")
      e.getAttribute(ExifInterface.TAG_DATETIME)?.let { o.add("taken  $it") }
      val cam = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ")
      if (cam.isNotBlank()) o.add("camera  $cam")
      e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { o.add("aperture  f/$it") }
      e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { o.add("shutter  ${it}s") }
      e.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { o.add("iso  $it") }
      e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { o.add("focal  $it") }
      val ll = FloatArray(2)
      if (e.getLatLong(ll)) o.add("gps  " + String.format("%.4f, %.4f", ll[0], ll[1]))
    } catch (e: Exception) { o.add("no exif data") }
    return o
  }
  fun palette(b: Bitmap, n: Int): List<Int> {
    val s = Bitmap.createScaledBitmap(b, 48, 48, true); val m = HashMap<Int, Int>()
    for (y in 0 until 48) for (x in 0 until 48) {
      val c = s.getPixel(x, y)
      val k = (((c shr 20) and 15) shl 8) or (((c shr 12) and 15) shl 4) or ((c shr 4) and 15)
      m[k] = (m[k] ?: 0) + 1
    }
    return m.entries.sortedByDescending { it.value }.take(n * 3).map { val k = it.key; (0xFF shl 24) or (((k shr 8) and 15) * 17 shl 16) or (((k shr 4) and 15) * 17 shl 8) or ((k and 15) * 17) }.distinct().take(n)
  }
  fun auto(b: Bitmap): FloatArray {
    val s = Bitmap.createScaledBitmap(b, 64, 64, true); val h = IntArray(256)
    for (y in 0 until 64) for (x in 0 until 64) { val c = s.getPixel(x, y); h[((((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000).coerceIn(0, 255)]++ }
    val tot = 64 * 64; var acc = 0; var lo = 0; var hi = 255
    for (i in 0..255) { acc += h[i]; if (acc > tot / 100) { lo = i; break } }
    acc = 0
    for (i in 255 downTo 0) { acc += h[i]; if (acc > tot / 100) { hi = i; break } }
    val c = (255f / (hi - lo).coerceAtLeast(40)).coerceIn(.85f, 1.7f)
    return floatArrayOf(((128f - (lo + hi) / 2f) / 2.55f).coerceIn(-30f, 30f), c)
  }
  private fun layers(c: ACanvas, w: Int, h: Int, strokes: List<Stk>, stc: List<Stc>) {
    val p = APaint(APaint.ANTI_ALIAS_FLAG).apply { style = APaint.Style.STROKE; strokeCap = APaint.Cap.ROUND; strokeJoin = APaint.Join.ROUND }
    for (s in strokes) {
      if (s.pts.isEmpty()) continue
      p.color = s.color; p.alpha = (s.a * 255).toInt(); p.strokeWidth = s.w * w
      if (s.pts.size == 1) { p.style = APaint.Style.FILL; c.drawCircle(s.pts[0].x * w, s.pts[0].y * h, s.w * w / 2, p); p.style = APaint.Style.STROKE; continue }
      val path = APath()
      s.pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x * w, o.y * h) else path.lineTo(o.x * w, o.y * h) }
      c.drawPath(path, p)
    }
    val t = APaint(APaint.ANTI_ALIAS_FLAG).apply { textAlign = APaint.Align.CENTER }
    for (s in stc) { t.textSize = s.size * w; c.drawText(s.s, s.x * w, s.y * h + t.textSize * .35f, t) }
  }
  fun bake(src: Bitmap, strokes: List<Stk>, stc: List<Stc>): Bitmap {
    if (strokes.isEmpty() && stc.isEmpty()) return src
    val b = src.copy(Bitmap.Config.ARGB_8888, true)
    layers(ACanvas(b), b.width, b.height, strokes, stc)
    return b
  }
  fun render(src: Bitmap, mat: FloatArray, strokes: List<Stk>, stc: List<Stc>, scale: Float): Bitmap {
    val w = (src.width * scale).toInt().coerceAtLeast(1); val h = (src.height * scale).toInt().coerceAtLeast(1)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = ACanvas(out)
    val p = APaint(APaint.FILTER_BITMAP_FLAG).apply { colorFilter = AColorFilter(AColorMatrix(mat)) }
    c.drawBitmap(src, null, ARect(0, 0, w, h), p)
    layers(c, w, h, strokes, stc)
    return out
  }
  fun xform(b: Bitmap, m: AMatrix): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
  fun fit(bw: Int, bh: Int, w: Float, h: Float): FloatArray { val s = min(w / bw, h / bh); val dw = bw * s; val dh = bh * s; return floatArrayOf((w - dw) / 2f, (h - dh) / 2f, dw, dh) }
}

private val SW = listOf(0xFFFF6B9AL, 0xFFFFB3D1L, 0xFFFFD166L, 0xFF7EE787L, 0xFF6BD6FFL, 0xFFB69CFFL, 0xFFFFFFFFL, 0xFF1A1A1AL).map { it.toInt() }
private val EMO = listOf("💖", "⭐", "✨", "🌸", "🐱", "🐰", "🍓", "🌈", "☁️", "🎀", "😊", "😎", "🔥", "❤️", "👑", "🦋", "🍡", "🌙", "💫", "🍀")

@Composable fun ZoomImg(f: File, onTap: () -> Unit, onZoom: (Boolean) -> Unit) {
  val bm = produceState<ImageBitmap?>(null, f) { value = withContext(Dispatchers.IO) { Img.load(f, 2048)?.asImageBitmap() } }.value
  var sc by remember(f) { mutableFloatStateOf(1f) }
  var off by remember(f) { mutableStateOf(Offset.Zero) }
  LaunchedEffect(sc) { onZoom(sc > 1.02f) }
  Box(Modifier.fillMaxSize()
    .pointerInput(f) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (sc > 1.02f) { sc = 1f; off = Offset.Zero } else sc = 2.5f }) }
    .pointerInput(f) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
          val ev = awaitPointerEvent()
          if (ev.changes.count { it.pressed } >= 2 || sc > 1.02f) {
            val z = ev.calculateZoom(); val p = ev.calculatePan()
            sc = (sc * z).coerceIn(1f, 6f); off = if (sc <= 1.02f) Offset.Zero else off + p
            ev.changes.forEach { if (it.positionChanged()) it.consume() }
          }
        } while (ev.changes.any { it.pressed })
      }
    }, Alignment.Center) {
    if (bm == null) Mochi(70.dp, 1)
    else Image(bm, null, Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; translationX = off.x; translationY = off.y }, contentScale = ContentScale.Fit)
  }
}

@Composable fun HBtn(g: String, lb: String, on: Boolean = false, m: Modifier = Modifier, click: () -> Unit) {
  val cf = LCfg.current
  Column(m.clip(RoundedCornerShape(14.dp)).clickable { click() }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Gly(g, 24.dp, on); T(lb, if (on) cf.ac else Th.dim, 8f)
  }
}

@Composable fun ImgScr(back: () -> Unit, files: List<File>, start: Int, onShare: (File) -> Unit, onTrash: (File) -> Unit, onInfo: (File) -> Unit, onSaved: () -> Unit) {
  var edit by remember { mutableStateOf(false) }
  val pager = rememberPagerState(initialPage = start.coerceIn(0, (files.size - 1).coerceAtLeast(0)), pageCount = { files.size })
  val f = files[pager.currentPage.coerceIn(0, files.size - 1)]
  if (edit) { ImgEditor(f, { edit = false }) { onSaved(); edit = false }; return }
  val cf = LCfg.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
  var ui by remember { mutableStateOf(true) }
  var zoomed by remember { mutableStateOf(false) }
  var info by remember { mutableStateOf(false) }
  var play by remember { mutableStateOf(false) }
  val liked = cf.likes.split('\n').contains(f.path)
  LaunchedEffect(play) { while (play) { delay(3000); pager.animateScrollToPage((pager.currentPage + 1) % files.size) } }
  Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xFF0A0710))) {
    HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 12.dp, userScrollEnabled = !zoomed) { i -> ZoomImg(files[i], { ui = !ui }) { z -> if (i == pager.currentPage) zoomed = z } }
    AnimatedVisibility(ui, Modifier.align(Alignment.TopCenter), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
      Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent))).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Bt("‹") { back() }
        Column(Modifier.weight(1f)) { T(f.name, Color.White, 13f, FontWeight.Bold); T("${pager.currentPage + 1} / ${files.size}", Color(0xFFCCBBCC), 9f) }
        IconBtn("heart", on = liked) { cf.likes = (if (liked) cf.likes.split('\n').filter { it != f.path } else cf.likes.split('\n').filter { it.isNotEmpty() } + f.path).joinToString("\n"); if (!liked) Hub.cheer(14) }
      }
    }
    AnimatedVisibility(ui, Modifier.align(Alignment.BottomCenter), enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
      Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))).padding(8.dp)) {
        if (info) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Th.bg.copy(alpha = .92f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          val ex = produceState(listOf<String>(), f) { value = withContext(Dispatchers.IO) { Img.exif(f) } }.value
          val pal = produceState(listOf<Int>(), f) { value = withContext(Dispatchers.IO) { Img.load(f, 128)?.let { Img.palette(it, 6) } ?: emptyList() } }.value
          val clip = LocalClipboardManager.current
          ex.forEach { T(it, Th.fg, 10f, ml = 2) }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            pal.forEach { c -> Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color(c)).clickable { clip.setText(AnnotatedString(String.format("#%06X", c and 0xFFFFFF))); Hub.toast("color copied") }) }
          }
        }
        Row(Modifier.fillMaxWidth()) {
          HBtn("wand", "Edit", false, Modifier.weight(1f)) { edit = true }
          HBtn("share", "Share", false, Modifier.weight(1f)) { onShare(f) }
          HBtn("wall", "Wallpaper", false, Modifier.weight(1f)) {
            scope.launch { val ok = withContext(Dispatchers.IO) { runCatching { android.app.WallpaperManager.getInstance(ctx).setBitmap(Img.load(f, 2048)) }.isSuccess }; Hub.toast(if (ok) "wallpaper set ♡" else "couldn't set wallpaper") }
          }
          HBtn("play", if (play) "Stop" else "Slides", play, Modifier.weight(1f)) { play = !play }
          HBtn("info", "Info", info, Modifier.weight(1f)) { info = !info }
          HBtn("trash", "Trash", false, Modifier.weight(1f)) { onTrash(f) }
        }
      }
    }
  }
}

@Composable fun ImgEditor(f: File, back: () -> Unit, done: () -> Unit) {
  val cf = LCfg.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val ac = cf.ac
  var bmp by remember { mutableStateOf<Bitmap?>(null) }
  val undo = remember { ArrayList<Bitmap>() }
  LaunchedEffect(f) { bmp = withContext(Dispatchers.IO) { Img.load(f, 2048) } }
  var tool by remember { mutableIntStateOf(0) }
  var br by remember { mutableFloatStateOf(0f) }; var ct by remember { mutableFloatStateOf(1f) }
  var sa by remember { mutableFloatStateOf(1f) }; var wm by remember { mutableFloatStateOf(0f) }
  var fil by remember { mutableIntStateOf(0) }
  val strokes = remember { mutableStateListOf<Stk>() }; val stickers = remember { mutableStateListOf<Stc>() }
  var tick by remember { mutableIntStateOf(0) }
  var crop by remember { mutableStateOf(CropR(0f, 0f, 1f, 1f)) }
  var ratio by remember { mutableFloatStateOf(0f) }
  var pen by remember { mutableIntStateOf(SW[0]) }; var penW by remember { mutableFloatStateOf(.012f) }; var penA by remember { mutableFloatStateOf(1f) }
  var pick by remember { mutableStateOf(false) }
  var emo by remember { mutableStateOf("💖") }; var emoS by remember { mutableFloatStateOf(.14f) }
  var fmt by remember { mutableIntStateOf(0) }; var q by remember { mutableFloatStateOf(90f) }; var scl by remember { mutableFloatStateOf(100f) }
  var over by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
  var orig by remember { mutableStateOf(false) }
  val mat = remember(br, ct, sa, wm, fil) { Img.matrix(br, ct, sa, wm, fil) }
  val b = bmp
  val ib = remember(b) { b?.asImageBitmap() }
  val thumb = remember(b) { b?.let { Bitmap.createScaledBitmap(it, 64, (64f * it.height / it.width).toInt().coerceAtLeast(1), true).asImageBitmap() } }
  val tp = remember { APaint(APaint.ANTI_ALIAS_FLAG).apply { textAlign = APaint.Align.CENTER } }

  fun bakeLayers(): Bitmap? { val x = bmp ?: return null; val r = Img.bake(x, strokes.toList(), stickers.toList()); strokes.clear(); stickers.clear(); return r }
  fun push(nb: Bitmap) { bmp?.let { undo.add(it); if (undo.size > 5) undo.removeAt(0) }; bmp = nb; crop = CropR(0f, 0f, 1f, 1f); tick++ }
  fun doUndo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) else if (stickers.isNotEmpty()) stickers.removeAt(stickers.lastIndex) else if (undo.isNotEmpty()) { bmp = undo.removeAt(undo.lastIndex); crop = CropR(0f, 0f, 1f, 1f) } else Hub.toast("nothing to undo"); tick++ }
  fun xf(m: AMatrix) { val x = bakeLayers() ?: return; push(Img.xform(x, m)) }
  fun setRatio(r: Float) {
    ratio = r; val x = bmp ?: return
    if (r <= 0f) { crop = CropR(0f, 0f, 1f, 1f); return }
    val ba = x.width.toFloat() / x.height
    if (r > ba) { val h = ba / r; crop = CropR(0f, (1f - h) / 2f, 1f, (1f + h) / 2f) } else { val w = r / ba; crop = CropR((1f - w) / 2f, 0f, (1f + w) / 2f, 1f) }
  }
  fun applyCrop() {
    val x = bakeLayers() ?: return
    val l = (crop.l * x.width).toInt().coerceIn(0, x.width - 2); val t = (crop.t * x.height).toInt().coerceIn(0, x.height - 2)
    val w = ((crop.r - crop.l) * x.width).toInt().coerceIn(2, x.width - l); val h = ((crop.b - crop.t) * x.height).toInt().coerceIn(2, x.height - t)
    push(Bitmap.createBitmap(x, l, t, w, h))
  }
  fun save(wall: Boolean) {
    val x = bmp ?: return
    scope.launch {
      busy = true
      val res = withContext(Dispatchers.IO) {
        runCatching {
          val out = Img.render(x, mat, strokes.toList(), stickers.toList(), scl / 100f)
          if (wall) { android.app.WallpaperManager.getInstance(ctx).setBitmap(out); null }
          else {
            val oe = f.extension.lowercase()
            val ext = if (over) oe else if (fmt == 1) "png" else if (fmt == 2) "webp" else "jpg"
            val cfm = when (ext) { "png" -> Bitmap.CompressFormat.PNG; "webp" -> Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.JPEG }
            val dst = if (over) f else Fx.uniq(File(f.parentFile, f.nameWithoutExtension + "_edit." + ext))
            FileOutputStream(dst).use { out.compress(cfm, q.toInt(), it) }
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(dst.path), null, null)
            dst
          }
        }
      }
      busy = false
      if (res.isSuccess) { Hub.toast(if (wall) "wallpaper set ♡" else "saved ${res.getOrNull()?.name ?: ""} ♡"); Hub.cheer(18); if (!wall) done() } else Hub.toast("✕ " + (res.exceptionOrNull()?.message ?: "save failed"))
    }
  }

  Column(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color(0xFF0A0710))) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Bt("‹") { back() }
      T("✎ " + f.name, Color.White, 12f, FontWeight.Bold, Modifier.weight(1f))
      Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Th.fg.copy(alpha = .08f)).border(1.5.dp, Th.fg.copy(alpha = .14f), RoundedCornerShape(16.dp))
        .pointerInput(Unit) { detectTapGestures(onPress = { orig = true; tryAwaitRelease(); orig = false }) }.padding(horizontal = 11.dp, vertical = 6.dp)) { T("hold ◐ original", Th.fg, 11f, FontWeight.Medium) }
      IconBtn("undo") { doUndo() }
      Bt(if (busy) "…" else "SAVE", true) { if (!busy) tool = 6 }
    }
    Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { }) {
      val bb = bmp
      if (bb == null || ib == null) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Mochi(80.dp, 1); T("opening…", Th.dim) }
      else Canvas(Modifier.fillMaxSize()
        .pointerInput(tool, bb, pick) {
          if (tool == 3 && !pick) detectDragGestures(
            onDragStart = { p -> val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat()); val s = Stk(pen, penW, penA); s.pts.add(Offset(((p.x - fr[0]) / fr[2]).coerceIn(0f, 1f), ((p.y - fr[1]) / fr[3]).coerceIn(0f, 1f))); strokes.add(s); tick++ },
            onDrag = { ch, _ -> val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat()); strokes.lastOrNull()?.pts?.add(Offset(((ch.position.x - fr[0]) / fr[2]).coerceIn(0f, 1f), ((ch.position.y - fr[1]) / fr[3]).coerceIn(0f, 1f))); tick++ })
        }
        .pointerInput(tool, bb, pick, emo, emoS) {
          if (tool == 3 && pick) detectTapGestures { p ->
            val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat())
            val x = (((p.x - fr[0]) / fr[2]) * bb.width).toInt().coerceIn(0, bb.width - 1); val y = (((p.y - fr[1]) / fr[3]) * bb.height).toInt().coerceIn(0, bb.height - 1)
            pen = bb.getPixel(x, y) or (0xFF shl 24); pick = false
          }
          if (tool == 4) detectTapGestures { p ->
            val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat())
            stickers.add(Stc(((p.x - fr[0]) / fr[2]).coerceIn(0f, 1f), ((p.y - fr[1]) / fr[3]).coerceIn(0f, 1f), emo, emoS)); tick++
          }
        }
        .pointerInput(tool, bb) {
          if (tool == 4) detectDragGestures { ch, d ->
            val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat())
            stickers.lastOrNull()?.let { s -> s.x = (s.x + d.x / fr[2]).coerceIn(0f, 1f); s.y = (s.y + d.y / fr[3]).coerceIn(0f, 1f); tick++ }; ch.consume()
          }
          if (tool == 2) {
            var h = 0
            detectDragGestures(
              onDragStart = { p ->
                val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat()); val c = crop
                val xs = floatArrayOf(fr[0] + c.l * fr[2], fr[0] + c.r * fr[2]); val ys = floatArrayOf(fr[1] + c.t * fr[3], fr[1] + c.b * fr[3])
                h = 0
                for ((i, pt) in listOf(Offset(xs[0], ys[0]), Offset(xs[1], ys[0]), Offset(xs[0], ys[1]), Offset(xs[1], ys[1])).withIndex()) if ((p - pt).getDistance() < 70f) { h = i + 1; break }
                if (h == 0 && p.x in xs[0]..xs[1] && p.y in ys[0]..ys[1]) h = 5
              },
              onDragEnd = { h = 0 },
              onDrag = { ch, d ->
                ch.consume()
                val fr = Img.fit(bb.width, bb.height, size.width.toFloat(), size.height.toFloat()); val dx = d.x / fr[2]; val dy = d.y / fr[3]; var c = crop
                val ba = bb.width.toFloat() / bb.height
                when (h) {
                  1 -> { val l = (c.l + dx).coerceIn(0f, c.r - .08f); var t = (c.t + dy).coerceIn(0f, c.b - .08f); if (ratio > 0f) t = (c.b - (c.r - l) * ba / ratio).coerceIn(0f, c.b - .08f); c = c.copy(l = l, t = t) }
                  2 -> { val r = (c.r + dx).coerceIn(c.l + .08f, 1f); var t = (c.t + dy).coerceIn(0f, c.b - .08f); if (ratio > 0f) t = (c.b - (r - c.l) * ba / ratio).coerceIn(0f, c.b - .08f); c = c.copy(r = r, t = t) }
                  3 -> { val l = (c.l + dx).coerceIn(0f, c.r - .08f); var b2 = (c.b + dy).coerceIn(c.t + .08f, 1f); if (ratio > 0f) b2 = (c.t + (c.r - l) * ba / ratio).coerceIn(c.t + .08f, 1f); c = c.copy(l = l, b = b2) }
                  4 -> { val r = (c.r + dx).coerceIn(c.l + .08f, 1f); var b2 = (c.b + dy).coerceIn(c.t + .08f, 1f); if (ratio > 0f) b2 = (c.t + (r - c.l) * ba / ratio).coerceIn(c.t + .08f, 1f); c = c.copy(r = r, b = b2) }
                  5 -> { val w = c.r - c.l; val hh = c.b - c.t; val l = (c.l + dx).coerceIn(0f, 1f - w); val t = (c.t + dy).coerceIn(0f, 1f - hh); c = CropR(l, t, l + w, t + hh) }
                }
                crop = c
              })
          }
        }) {
        val tk = tick
        val fr = Img.fit(bb.width, bb.height, size.width, size.height)
        drawImage(ib, dstOffset = IntOffset(fr[0].toInt(), fr[1].toInt()), dstSize = IntSize(fr[2].toInt(), fr[3].toInt()), colorFilter = if (orig) null else ColorFilter.colorMatrix(ColorMatrix(mat)))
        if (tk >= 0) for (s in strokes) {
          if (s.pts.isEmpty()) continue
          val c = Color(s.color)
          if (s.pts.size == 1) drawCircle(c, s.w * fr[2] / 2f, Offset(fr[0] + s.pts[0].x * fr[2], fr[1] + s.pts[0].y * fr[3]), s.a)
          else { val path = Path(); s.pts.forEachIndexed { i, o -> val x = fr[0] + o.x * fr[2]; val y = fr[1] + o.y * fr[3]; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, c, s.a, Stroke(s.w * fr[2], cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
        for (s in stickers) { tp.textSize = s.size * fr[2]; drawContext.canvas.nativeCanvas.drawText(s.s, fr[0] + s.x * fr[2], fr[1] + s.y * fr[3] + tp.textSize * .35f, tp) }
        if (tool == 2) {
          val c = crop; val l = fr[0] + c.l * fr[2]; val t = fr[1] + c.t * fr[3]; val r = fr[0] + c.r * fr[2]; val bt = fr[1] + c.b * fr[3]
          val dim = Color.Black.copy(alpha = .55f)
          drawRect(dim, Offset(fr[0], fr[1]), Size(fr[2], t - fr[1])); drawRect(dim, Offset(fr[0], bt), Size(fr[2], fr[1] + fr[3] - bt))
          drawRect(dim, Offset(fr[0], t), Size(l - fr[0], bt - t)); drawRect(dim, Offset(r, t), Size(fr[0] + fr[2] - r, bt - t))
          drawRect(ac, Offset(l, t), Size(r - l, bt - t), style = Stroke(3f))
          for (k in 1..2) { drawLine(Color.White.copy(alpha = .35f), Offset(l + (r - l) * k / 3f, t), Offset(l + (r - l) * k / 3f, bt), 1.5f); drawLine(Color.White.copy(alpha = .35f), Offset(l, t + (bt - t) * k / 3f), Offset(r, t + (bt - t) * k / 3f), 1.5f) }
          for (pt in listOf(Offset(l, t), Offset(r, t), Offset(l, bt), Offset(r, bt))) { drawCircle(Color.White, 14f, pt); drawCircle(ac, 9f, pt) }
        }
      }
    }
    // ── tool tabs ──
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      listOf(Triple("sliders", "Adjust", 0), Triple("wand", "Filters", 1), Triple("crop", "Crop", 2), Triple("brush", "Draw", 3), Triple("sticker", "Stickers", 4), Triple("rotr", "Rotate", 5), Triple("save", "Save", 6)).forEach { (g, lb, i) ->
        HBtn(g, lb, tool == i, Modifier.width(60.dp)) { tool = i; if (i == 2) setRatio(ratio) }
      }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      when (tool) {
        0 -> {
          Sl("light", br, -100f..100f) { br = it }; Sl("contrast", ct, .5f..1.8f) { ct = it }; Sl("color", sa, 0f..2f) { sa = it }; Sl("warmth", wm, -1f..1f) { wm = it }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Bt("✨ AUTO", true) { val x = bmp; if (x != null) { val a = Img.auto(x); br = a[0]; ct = a[1]; sa = 1.1f; Hub.cheer(8) } }
            Bt("RESET") { br = 0f; ct = 1f; sa = 1f; wm = 0f; fil = 0 }
          }
        }
        1 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Img.FILTERS.forEachIndexed { i, fl ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (fil == i) ac.copy(alpha = .25f) else Color.Transparent).clickable { fil = i }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              val th = thumb
              if (th != null) Image(th, null, Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(ColorMatrix(fl.m)))
              T(fl.name, if (fil == i) ac else Th.dim, 9f)
            }
          }
        }
        2 -> {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("FREE" to 0f, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to .75f, "16:9" to 16f / 9f, "9:16" to 9f / 16f).forEach { (n, r) -> Bt(n, ratio == r) { setRatio(r) } }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Bt("✂ APPLY CROP", true) { applyCrop() }; Bt("RESET") { setRatio(0f) } }
          T("drag the corners · drag inside to move", Th.dim, 9f)
        }
        3 -> {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SW.forEach { c -> Box(Modifier.size(if (pen == c) 30.dp else 24.dp).clip(RoundedCornerShape(15.dp)).background(Color(c)).border(2.dp, if (pen == c) Color.White else Color.Transparent, RoundedCornerShape(15.dp)).clickable { pen = c; pick = false }) }
            Bt("💧 PICK", pick) { pick = !pick }
          }
          Sl("size", penW, .004f..0.06f) { penW = it }; Sl("opacity", penA, .2f..1f) { penA = it }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Bt("UNDO") { doUndo() }; Bt("CLEAR") { strokes.clear(); tick++ } }
        }
        4 -> {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { EMO.forEach { e -> Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (emo == e) ac.copy(alpha = .25f) else Color.Transparent).clickable { emo = e }.padding(6.dp)) { T(e, Color.White, 22f) } } }
          Inp(emo, { emo = it }, "or type any text/emoji", Modifier.fillMaxWidth())
          Sl("size", emoS, .05f..0.5f) { emoS = it }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Bt("UNDO") { doUndo() }; Bt("CLEAR") { stickers.clear(); tick++ } }
          T("tap to place · drag moves the last one", Th.dim, 9f)
        }
        5 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Bt("↺ 90°") { xf(AMatrix().apply { postRotate(270f) }) }; Bt("↻ 90°") { xf(AMatrix().apply { postRotate(90f) }) }
          Bt("⇋ FLIP H") { xf(AMatrix().apply { postScale(-1f, 1f) }) }; Bt("⇅ FLIP V") { xf(AMatrix().apply { postScale(1f, -1f) }) }
        }
        else -> {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("JPG", "PNG", "WEBP").forEachIndexed { i, s -> Bt(s, fmt == i && !over) { fmt = i; over = false } }; Bt("OVERWRITE", over) { over = !over } }
          Sl("quality", q, 40f..100f) { q = it }; Sl("scale %", scl, 25f..100f) { scl = it }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Bt(if (busy) "saving…" else "💾 SAVE", true) { if (!busy) save(false) }; Bt("🖼 WALLPAPER") { if (!busy) save(true) } }
        }
      }
    }
  }
}
