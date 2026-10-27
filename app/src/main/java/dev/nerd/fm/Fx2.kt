package dev.nerd.fm

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

object Thumbs {
  private val cache = LruCache<String, Bitmap>(80)
  fun ok(e: E) = e.ex in Fx.IMG || e.ex == "apk"
  fun get(pm: PackageManager, f: File, px: Int): Bitmap? {
    val k = f.path + f.lastModified()
    cache.get(k)?.let { return it }
    val b: Bitmap? = try {
      if (f.extension.lowercase() == "apk") {
        val ai = pm.getPackageArchiveInfo(f.path, 0)?.applicationInfo
        if (ai == null) null else {
          ai.sourceDir = f.path; ai.publicSourceDir = f.path
          val d = ai.loadIcon(pm)
          val bm = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
          d.setBounds(0, 0, px, px); d.draw(android.graphics.Canvas(bm)); bm
        }
      } else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > px * 2 || o.outHeight / s > px * 2) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
      }
    } catch (e: Throwable) { null }
    if (b != null) cache.put(k, b)
    return b
  }
}

class TItem(val dir: File, val item: File, val orig: String)
object Trash {
  val root: File get() = File(Environment.getExternalStorageDirectory(), ".nerdfm_trash")
  fun put(f: File): File {
    val b = System.currentTimeMillis().toString() + "-" + (f.name.hashCode() and 0xffff)
    var d = File(root, b); var k = 0
    while (d.exists()) d = File(root, b + "-" + (++k))
    d.mkdirs(); File(d, ".orig").writeText(f.parent ?: ""); Fx.mv(f, File(d, f.name))
    return d
  }
  fun undo(ds: List<File>) { val n = ds.map { it.name }.toSet(); list().filter { it.dir.name in n }.forEach { restore(it) } }
  fun list(): List<TItem> = (root.listFiles() ?: emptyArray()).mapNotNull { d ->
    val x = d.listFiles()?.firstOrNull { it.name != ".orig" }
    if (x == null) null else TItem(d, x, runCatching { File(d, ".orig").readText() }.getOrDefault(""))
  }.sortedByDescending { it.dir.name }
  fun restore(t: TItem): String {
    val par = if (t.orig.isEmpty()) Environment.getExternalStorageDirectory() else File(t.orig)
    par.mkdirs()
    val to = Fx.uniq(File(par, t.item.name)); Fx.mv(t.item, to); t.dir.deleteRecursively()
    return to.name
  }
  fun empty() { root.deleteRecursively() }
}

object Idx {
  @Volatile var all: Array<String> = emptyArray()
  var count by mutableIntStateOf(0)
  suspend fun build(root: File, hid: Boolean) {
    val out = ArrayList<String>(); val st = ArrayList<File>(); st.add(root)
    while (st.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val d = st.removeAt(st.lastIndex)
      for (f in d.listFiles() ?: continue) {
        if (!hid && f.name.startsWith(".")) continue
        if (f.isDirectory) { out.add("D" + f.path); if (!Fx.sym(f)) st.add(f) } else out.add("F" + f.path)
      }
    }
    all = out.toTypedArray(); count = all.size
  }
  fun clear() { all = emptyArray(); count = 0 }
}

class Cmp(val onlyA: List<String>, val onlyB: List<String>, val diff: List<String>, val same: Int)

object Fx2 {
  suspend fun searchIdx(rootPath: String, q: Q, max: Int, hit: suspend (E) -> Unit, prog: (Int) -> Unit) {
    val a = Idx.all; val now = System.currentTimeMillis(); var h = 0; var n = 0
    val pre = if (rootPath.endsWith("/")) rootPath else "$rootPath/"
    val baseDepth = pre.count { it == '/' }
    for (s in a) {
      n++
      if ((n and 2047) == 0) { currentCoroutineContext().ensureActive(); prog(n) }
      if (!s.startsWith(pre, 1)) continue
      if (q.depth < 64 && s.count { it == '/' } - baseDepth > q.depth) continue
      val f = File(s.substring(1))
      if (q.ok(f, s[0] == 'D', now)) { hit(E(f)); h++; if (h >= max) break }
    }
    prog(n)
  }
  fun recent(root: File, n: Int): List<File> {
    val dirs = listOf("Download", "DCIM", "Pictures", "Documents", "Movies", "Music", "Telegram", "WhatsApp/Media").map { File(root, it) }.filter { it.isDirectory }
    val out = ArrayList<File>()
    for (d in dirs) for (f in d.walkTopDown().maxDepth(3)) if (f.isFile && !f.name.startsWith(".")) out.add(f)
    return out.sortedByDescending { it.lastModified() }.take(n)
  }

  fun shred(f: File) {
    if (f.isDirectory) { f.listFiles()?.forEach { shred(it) }; f.delete(); return }
    try {
      RandomAccessFile(f, "rw").use { r ->
        val b = ByteArray(65536); java.util.Random().nextBytes(b)
        var left = f.length(); r.seek(0)
        while (left > 0) { val n = min(left, 65536L).toInt(); r.write(b, 0, n); left -= n }
      }
    } catch (e: Exception) { }
    f.delete()
  }

  suspend fun breakdown(root: File): LinkedHashMap<String, Long> {
    val m = linkedMapOf("images" to 0L, "videos" to 0L, "audio" to 0L, "docs" to 0L, "archives" to 0L, "apps" to 0L, "code" to 0L, "other" to 0L)
    val st = ArrayList<File>(); st.add(root)
    while (st.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val d = st.removeAt(st.lastIndex)
      for (f in d.listFiles() ?: continue) {
        if (f.isDirectory) { if (!Fx.sym(f)) st.add(f) }
        else {
          val x = f.extension.lowercase()
          val k = when {
            x in Fx.IMG -> "images"; x in Fx.VID -> "videos"; x in Fx.AUD -> "audio"; x in Fx.ARC -> "archives"
            x == "apk" || x == "xapk" -> "apps"; x in Fx.CODE -> "code"; x in Fx.DOC -> "docs"; else -> "other"
          }
          m[k] = (m[k] ?: 0L) + f.length()
        }
      }
    }
    return m
  }

  suspend fun collect(root: File, q: String, hid: Boolean, max: Int = 4000): List<File> {
    val out = ArrayList<File>()
    Fx.search(root, Q(q, false, false), hid, max, { out.add(it.f) }, { })
    return out
  }

  fun squish(f: File, maxSide: Int, q: Int, webp: Boolean): File {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / s > maxSide * 2 || o.outHeight / s > maxSide * 2) s *= 2
    var b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: throw IOException("decode failed")
    val m = max(b.width, b.height)
    if (m > maxSide) {
      val r = maxSide.toFloat() / m
      b = Bitmap.createScaledBitmap(b, (b.width * r).toInt().coerceAtLeast(1), (b.height * r).toInt().coerceAtLeast(1), true)
    }
    val out = Fx.uniq(File(f.parentFile, f.nameWithoutExtension + "_squish." + (if (webp) "webp" else "jpg")))
    FileOutputStream(out).use { b.compress(if (webp) Bitmap.CompressFormat.WEBP else Bitmap.CompressFormat.JPEG, q, it) }
    return out
  }

  private fun rel(root: File): Map<String, File> {
    val m = HashMap<String, File>(); val base = root.path.length + 1
    for (f in root.walkTopDown().maxDepth(12)) if (f.isFile) m[f.path.substring(base)] = f
    return m
  }
  fun compare(a: File, b: File): Cmp {
    val ma = rel(a); val mb = rel(b)
    val oa = ma.keys.filter { it !in mb }.sorted(); val ob = mb.keys.filter { it !in ma }.sorted()
    val df = ArrayList<String>(); var same = 0
    for ((k, fa) in ma) {
      val fb = mb[k] ?: continue
      if (fa.length() != fb.length() || kotlin.math.abs(fa.lastModified() - fb.lastModified()) > 2000) df.add(k) else same++
    }
    return Cmp(oa, ob, df.sorted(), same)
  }
  fun sync(src: File, dst: File, keys: List<String>) { for (k in keys) Fx.cp(File(src, k), File(dst, k)) }
}
