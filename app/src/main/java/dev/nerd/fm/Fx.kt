package dev.nerd.fm

import android.os.Build
import androidx.compose.runtime.Immutable
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.*
import kotlin.math.min

@Immutable class E(val f: File) {
  val dir = f.isDirectory
  val n: String = f.name
  val sz = if (dir) 0L else f.length()
  val mt = f.lastModified()
  val ex = if (dir) "" else n.substringAfterLast('.', "").lowercase()
  val pm = (if (f.canRead()) "r" else "-") + (if (f.canWrite()) "w" else "-") + (if (f.canExecute()) "x" else "-")
}

class Q(raw: String, rx: Boolean, private val cs: Boolean) {
  private val inc = ArrayList<(String) -> Boolean>()
  private val exc = ArrayList<(String) -> Boolean>()
  var minS = 0L; var maxS = Long.MAX_VALUE
  var newer = Long.MAX_VALUE; var older = 0L
  val exts = HashSet<String>()
  var type = ""
  var content: String? = null
  var depth = 64
  var onlyD = false; var onlyF = false; var bad = false

  private fun mk(b: String, rx: Boolean): (String) -> Boolean {
    val opt = if (cs) emptySet() else setOf(RegexOption.IGNORE_CASE)
    if (rx) { val r = Regex(b, opt); return { r.containsMatchIn(it) } }
    if (b.contains('*') || b.contains('?')) { val r = Regex(Fx.glob(b), opt); return { r.matches(it) } }
    return { it.contains(b, !cs) }
  }

  init {
    val num = Regex("^(size|age)([<>])(\\d+(?:\\.\\d+)?)([a-z]*)$")
    for (t in raw.trim().split(Regex("\\s+"))) {
      if (t.isEmpty()) continue
      val m = num.find(t)
      if (m != null) {
        val (k, o, nv, u) = m.destructured
        val v = nv.toDouble()
        if (k == "size") {
          val mul = when (u) { "k", "kb" -> 1024.0; "m", "mb" -> 1048576.0; "g", "gb" -> 1073741824.0; else -> 1.0 }
          val b = (v * mul).toLong()
          if (o == ">") minS = b else maxS = b
        } else {
          val mul = when (u) { "min" -> 60e3; "h" -> 3.6e6; "w" -> 6.048e8; "mo" -> 2.592e9; "y" -> 3.1536e10; else -> 8.64e7 }
          val ms = (v * mul).toLong()
          if (o == "<") newer = ms else older = ms
        }
        continue
      }
      when {
        t.startsWith("ext:") -> exts.addAll(t.substring(4).lowercase().split(',').map { it.trim('.') })
        t.startsWith("type:") -> type = t.substring(5).lowercase()
        t.startsWith("c:") -> content = t.substring(2)
        t.startsWith("depth:") -> depth = t.substring(6).toIntOrNull() ?: 64
        t == "dir" -> onlyD = true
        t == "file" -> onlyF = true
        else -> {
          val neg = t.length > 1 && t[0] == '-'
          val b = if (neg) t.substring(1) else t
          val fn = try { mk(b, rx) } catch (e: Exception) { bad = true; null }
          if (fn != null) (if (neg) exc else inc).add(fn)
        }
      }
    }
  }

  fun ok(f: File, d: Boolean, now: Long): Boolean {
    if (onlyD && !d) return false
    if (onlyF && d) return false
    val n = f.name
    for (p in inc) if (!p(n)) return false
    for (p in exc) if (p(n)) return false
    if (exts.isNotEmpty() || type.isNotEmpty()) {
      if (d) return false
      val x = f.extension.lowercase()
      if (exts.isNotEmpty() && x !in exts) return false
      if (type.isNotEmpty() && x !in (Fx.TYPES[type] ?: emptySet())) return false
    }
    if (minS > 0 || maxS != Long.MAX_VALUE) { if (d) return false; val s = f.length(); if (s < minS || s > maxS) return false }
    if (newer != Long.MAX_VALUE || older > 0) { val a = now - f.lastModified(); if (a > newer || a < older) return false }
    val c = content
    if (c != null) { if (d || f.length() > 4_000_000) return false; if (!Fx.grep(f, c, cs)) return false }
    return true
  }
}

object Fx {
  val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")
  val VID = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv")
  val AUD = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma")
  val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "odt", "epub", "csv")
  val ARC = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "jar", "cbz")
  val CODE = setOf("kt", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go", "sh", "json", "xml", "yml", "yaml", "html", "css", "gradle", "kts", "toml", "sql")
  val TXT = CODE + setOf("txt", "md", "csv", "log", "ini", "conf", "cfg", "properties", "gitignore")
  val MEDIA = IMG + VID + AUD
  val TYPES = mapOf("img" to IMG, "vid" to VID, "aud" to AUD, "doc" to DOC, "arc" to ARC, "apk" to setOf("apk", "xapk", "apks"), "code" to CODE)
  private val df = SimpleDateFormat("yy-MM-dd HH:mm", Locale.US)

  fun dt(t: Long): String = df.format(Date(t))
  fun sz(n: Long): String {
    if (n < 1024) return "${n}B"
    var v = n.toDouble(); var i = -1
    while (v >= 1024 && i < 3) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f%c", v, "KMGT"[i])
  }
  fun kind(e: E): Int = if (e.dir) 0 else when { e.ex in IMG -> 1; e.ex in VID -> 2; e.ex in AUD -> 3; e.ex in ARC -> 4; e.ex == "apk" -> 5; e.ex in CODE -> 6; e.ex in DOC -> 7; else -> 8 }
  fun gl(e: E): String = if (e.dir) "▸" else when (e.ex) {
    in IMG -> "◧"; in VID -> "▶"; in AUD -> "♪"; in ARC -> "▤"; "apk" -> "◆"; in CODE -> "‹›"; in DOC -> "≡"; else -> "·"
  }
  fun glob(g: String): String = buildString {
    for (c in g) when (c) { '*' -> append(".*"); '?' -> append('.'); else -> append(Regex.escape(c.toString())) }
  }
  fun mime(f: File): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
  fun stor(): Pair<Long, Long> { val s = StatFs(Environment.getExternalStorageDirectory().path); return s.totalBytes to s.availableBytes }
  fun sym(f: File): Boolean = Build.VERSION.SDK_INT >= 26 && java.nio.file.Files.isSymbolicLink(f.toPath())

  fun list(d: File, hid: Boolean, sort: Int, desc: Boolean): List<E> {
    val a = d.listFiles() ?: return emptyList()
    val l = ArrayList<E>(a.size)
    for (f in a) if (hid || !f.name.startsWith(".")) l.add(E(f))
    val c: Comparator<E> = when (sort) {
      1 -> compareBy<E> { it.sz }
      2 -> compareBy<E> { it.mt }
      3 -> compareBy<E>({ it.ex }, { it.n.lowercase() })
      else -> compareBy<E> { it.n.lowercase() }
    }
    return l.sortedWith(compareBy<E> { !it.dir }.then(if (desc) c.reversed() else c))
  }

  fun uniq(f: File): File {
    if (!f.exists()) return f
    val b = f.nameWithoutExtension
    val x = if (f.extension.isEmpty()) "" else "." + f.extension
    for (i in 1..9999) { val c = File(f.parentFile, "$b ($i)$x"); if (!c.exists()) return c }
    return File(f.parentFile, "$b-${System.currentTimeMillis()}$x")
  }

  fun cp(s: File, d: File) {
    if (s.isDirectory && d.canonicalPath.startsWith(s.canonicalPath + File.separator)) throw IOException("into itself")
    cp0(s, d)
  }
  private fun cp0(s: File, d: File) {
    if (s.isDirectory) { d.mkdirs(); s.listFiles()?.forEach { cp0(it, File(d, it.name)) } }
    else {
      d.parentFile?.mkdirs()
      BufferedInputStream(FileInputStream(s), 65536).use { i -> BufferedOutputStream(FileOutputStream(d), 65536).use { o -> i.copyTo(o, 65536) } }
      d.setLastModified(s.lastModified())
    }
  }
  fun mv(s: File, d: File) { if (!s.renameTo(d)) { cp(s, d); s.deleteRecursively() } }

  fun du(f: File, depth: Int = 0): Long {
    if (!f.isDirectory) return f.length()
    if (depth > 40) return 0L
    var t = 0L
    f.listFiles()?.forEach { t += du(it, depth + 1) }
    return t
  }

  fun hash(f: File, alg: String, lim: Long): String = try {
    val m = MessageDigest.getInstance(alg)
    var left = lim
    f.inputStream().use { i ->
      val b = ByteArray(65536)
      while (left > 0) { val n = i.read(b, 0, min(b.size.toLong(), left).toInt()); if (n < 0) break; m.update(b, 0, n); left -= n }
    }
    m.digest().joinToString("") { "%02x".format(it) }
  } catch (e: Exception) { "!" + f.path }

  fun grep(f: File, c: String, cs: Boolean): Boolean = try {
    f.inputStream().buffered().use { i ->
      val h = ByteArray(512)
      val n = i.read(h)
      var bin = false
      for (k in 0 until n) if (h[k].toInt() == 0) bin = true
      if (bin) false else f.bufferedReader().useLines { s -> s.any { it.contains(c, !cs) } }
    }
  } catch (e: Exception) { false }

  suspend fun search(root: File, q: Q, hid: Boolean, max: Int, hit: suspend (E) -> Unit, prog: (Int) -> Unit) {
    val st = ArrayList<Pair<File, Int>>(); st.add(root to 0)
    var n = 0; var h = 0
    val now = System.currentTimeMillis()
    while (st.isNotEmpty() && h < max) {
      currentCoroutineContext().ensureActive()
      val (d, lv) = st.removeAt(st.lastIndex)
      val ch = d.listFiles() ?: continue
      for (f in ch) {
        if (!hid && f.name.startsWith(".")) continue
        n++; if ((n and 255) == 0) prog(n)
        val dr = f.isDirectory
        if (dr && lv < q.depth && !sym(f)) st.add(f to lv + 1)
        if (q.ok(f, dr, now)) { hit(E(f)); h++; if (h >= max) break }
      }
    }
    prog(n)
  }

  suspend fun dupes(root: File, hid: Boolean, minSz: Long): List<List<File>> {
    val by = HashMap<Long, ArrayList<File>>()
    val st = ArrayList<File>(); st.add(root)
    while (st.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val d = st.removeAt(st.lastIndex)
      for (f in d.listFiles() ?: continue) {
        if (!hid && f.name.startsWith(".")) continue
        if (f.isDirectory) { if (!sym(f)) st.add(f) }
        else { val l = f.length(); if (l >= minSz) by.getOrPut(l) { ArrayList() }.add(f) }
      }
    }
    val out = ArrayList<List<File>>()
    for ((_, g) in by) {
      if (g.size < 2) continue
      currentCoroutineContext().ensureActive()
      for ((_, g2) in g.groupBy { hash(it, "MD5", 65536L) }) {
        if (g2.size < 2) continue
        for ((_, g3) in g2.groupBy { hash(it, "SHA-1", Long.MAX_VALUE) }) if (g3.size > 1) out.add(g3)
      }
    }
    return out.sortedByDescending { it[0].length() * it.size }
  }

  fun empties(root: File): List<File> {
    val r = ArrayList<File>()
    fun w(d: File, depth: Int): Boolean {
      val c = d.listFiles() ?: return false
      var empty = true
      for (f in c) { if (f.isDirectory && !sym(f) && depth < 40) { if (!w(f, depth + 1)) empty = false } else empty = false }
      if (empty && d != root) r.add(d)
      return empty
    }
    w(root, 0)
    val s = r.toSet()
    return r.filter { it.parentFile !in s }
  }

  fun hex(f: File, off: Long, n: Int): List<String> {
    val b = ByteArray(n)
    val r = RandomAccessFile(f, "r").use { it.seek(off); it.read(b) }
    if (r <= 0) return emptyList()
    return (0 until r step 16).map { o ->
      val l = min(16, r - o)
      val sb = StringBuilder(String.format(Locale.US, "%08X ", off + o))
      for (k in 0 until 16) sb.append(if (k < l) String.format(Locale.US, "%02X ", b[o + k]) else "   ")
      sb.append(' ')
      for (k in 0 until l) { val c = b[o + k].toInt(); sb.append(if (c in 32..126) c.toChar() else '.') }
      sb.toString()
    }
  }
}

object Ar {
  fun exr(s: String): List<Regex> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { Regex(Fx.glob(it), RegexOption.IGNORE_CASE) }
  private fun skip(f: File, ex: List<Regex>, hid: Boolean) = (!hid && f.name.startsWith(".")) || ex.any { it.matches(f.name) }
  private fun crc(f: File): Long {
    val c = CRC32()
    f.inputStream().use { i -> val b = ByteArray(65536); while (true) { val n = i.read(b); if (n < 0) break; c.update(b, 0, n) } }
    return c.value
  }
  private fun gzo(out: File, level: Int): GZIPOutputStream = object : GZIPOutputStream(BufferedOutputStream(FileOutputStream(out), 65536)) {
    init { def.setLevel(level.coerceIn(1, 9)) }
  }

  fun kind(f: File): String {
    val n = f.name.lowercase()
    return when {
      n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "tgz"
      n.endsWith(".tar") -> "tar"
      n.endsWith(".gz") -> "gz"
      n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".cbz") -> "zip"
      else -> ""
    }
  }
  fun stem(f: File): String {
    val n = f.name
    for (s in listOf(".tar.gz", ".tgz", ".tar", ".gz", ".zip", ".jar", ".apk", ".cbz")) if (n.lowercase().endsWith(s)) return n.dropLast(s.length)
    return n + "_x"
  }

  fun zip(src: List<File>, out: File, level: Int, store: Boolean, ex: List<Regex>, hid: Boolean, cm: String) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(out), 65536)).use { z ->
      z.setLevel(level.coerceIn(0, 9))
      if (cm.isNotEmpty()) z.setComment(cm)
      fun add(f: File, p: String) {
        if (skip(f, ex, hid) || f == out) return
        if (f.isDirectory) {
          val c = f.listFiles() ?: return
          if (c.isEmpty()) { val e = ZipEntry("$p/"); e.time = f.lastModified(); z.putNextEntry(e); z.closeEntry() }
          for (x in c) add(x, p + "/" + x.name)
        } else {
          val e = ZipEntry(p); e.time = f.lastModified()
          if (store) { e.method = ZipEntry.STORED; e.size = f.length(); e.compressedSize = f.length(); e.crc = crc(f) }
          z.putNextEntry(e); f.inputStream().use { it.copyTo(z, 65536) }; z.closeEntry()
        }
      }
      for (f in src) add(f, f.name)
    }
  }

  private fun hdr(nm: String, size: Long, mt: Long, dir: Boolean): ByteArray? {
    var n = nm; var pre = ""
    if (n.toByteArray().size > 100) {
      val i = n.indexOf('/', maxOf(0, n.length - 99))
      if (i < 1 || i > 155) return null
      pre = n.substring(0, i); n = n.substring(i + 1)
      if (n.toByteArray().size > 100) return null
    }
    val h = ByteArray(512)
    fun put(s: String, o: Int, w: Int) { val b = s.toByteArray(); System.arraycopy(b, 0, h, o, min(b.size, w)) }
    fun oct(v: Long, o: Int, w: Int) = put(java.lang.Long.toOctalString(v).padStart(w - 1, '0'), o, w - 1)
    put(n, 0, 100); put(if (dir) "0000755" else "0000644", 100, 7); put("0000000", 108, 7); put("0000000", 116, 7)
    oct(size, 124, 12); oct(mt / 1000, 136, 12)
    for (i in 148 until 156) h[i] = 32
    h[156] = (if (dir) '5' else '0').code.toByte()
    put("ustar", 257, 5); put("00", 263, 2); put(pre, 345, 155)
    var s = 0
    for (b in h) s += b.toInt() and 255
    put(s.toString(8).padStart(6, '0'), 148, 6); h[154] = 0; h[155] = 32
    return h
  }

  fun tgz(src: List<File>, out: File, level: Int, ex: List<Regex>, hid: Boolean): Int {
    var skipped = 0
    gzo(out, level).use { o ->
      fun add(f: File, p: String) {
        if (skip(f, ex, hid) || f == out) return
        val dr = f.isDirectory
        val h = hdr(if (dr) "$p/" else p, if (dr) 0L else f.length(), f.lastModified(), dr)
        if (h == null) { skipped++; return }
        o.write(h)
        if (dr) { f.listFiles()?.forEach { add(it, p + "/" + it.name) } }
        else {
          val size = f.length(); var left = size
          f.inputStream().use { i ->
            val b = ByteArray(65536)
            while (left > 0) { val n = i.read(b, 0, min(b.size.toLong(), left).toInt()); if (n < 0) break; o.write(b, 0, n); left -= n }
          }
          while (left > 0) { val c = min(8192L, left).toInt(); o.write(ByteArray(c)); left -= c }
          val pad = ((512 - size % 512) % 512).toInt()
          if (pad > 0) o.write(ByteArray(pad))
        }
      }
      for (f in src) add(f, f.name)
      o.write(ByteArray(1024))
    }
    return skipped
  }

  fun gz(f: File, out: File, level: Int) { gzo(out, level).use { z -> f.inputStream().use { it.copyTo(z, 65536) } } }
  fun gunzip(f: File, out: File) { GZIPInputStream(f.inputStream().buffered(65536)).use { i -> out.outputStream().buffered(65536).use { i.copyTo(it, 65536) } } }

  fun unzip(zf: File, dest: File, ow: Boolean): Int {
    var n = 0
    dest.mkdirs()
    val base = dest.canonicalPath
    ZipFile(zf).use { z ->
      val en = z.entries()
      while (en.hasMoreElements()) {
        val e = en.nextElement()
        val t = File(dest, e.name)
        if (!t.canonicalPath.startsWith(base + File.separator)) continue
        if (e.isDirectory) { t.mkdirs(); continue }
        if (!ow && t.exists()) continue
        t.parentFile?.mkdirs()
        z.getInputStream(e).use { i -> BufferedOutputStream(FileOutputStream(t), 65536).use { i.copyTo(it, 65536) } }
        if (e.time > 0) t.setLastModified(e.time)
        n++
      }
    }
    return n
  }

  private fun str(h: ByteArray, o: Int, w: Int) = String(h, o, w, Charsets.UTF_8).substringBefore('\u0000')
  private fun skipN(i: InputStream, n: Long) {
    var l = n; val b = ByteArray(8192)
    while (l > 0) { val r = i.read(b, 0, min(b.size.toLong(), l).toInt()); if (r < 0) throw EOFException(); l -= r }
  }
  private fun copyN(i: InputStream, o: OutputStream, n: Long) {
    var l = n; val b = ByteArray(65536)
    while (l > 0) { val r = i.read(b, 0, min(b.size.toLong(), l).toInt()); if (r < 0) throw EOFException(); o.write(b, 0, r); l -= r }
  }

  fun untar(f: File, dest: File, ow: Boolean, gzip: Boolean): Int {
    var cnt = 0
    dest.mkdirs()
    val base = dest.canonicalPath
    val raw = BufferedInputStream(FileInputStream(f), 65536)
    val i = DataInputStream(if (gzip) BufferedInputStream(GZIPInputStream(raw, 65536), 65536) else raw)
    i.use {
      val h = ByteArray(512)
      var ln: String? = null
      try {
        while (true) {
          i.readFully(h)
          if (h.all { it.toInt() == 0 }) break
          val nm0 = str(h, 0, 100); val pre = str(h, 345, 155)
          val size = str(h, 124, 12).trim().toLongOrNull(8) ?: 0L
          val mt = str(h, 136, 12).trim().toLongOrNull(8) ?: 0L
          val ty = h[156].toInt().toChar()
          val pad = (512 - size % 512) % 512
          if (ty == 'L') {
            val b = ByteArray(size.toInt()); i.readFully(b); skipN(i, pad)
            ln = String(b, Charsets.UTF_8).trimEnd('\u0000'); continue
          }
          val name = ln ?: (if (pre.isNotEmpty()) "$pre/$nm0" else nm0)
          ln = null
          val t = File(dest, name)
          val safe = t.canonicalPath.startsWith(base + File.separator)
          if (ty == '5') { if (safe) t.mkdirs(); continue }
          val want = (ty == '0' || ty == '\u0000') && safe && (ow || !t.exists())
          if (want) {
            t.parentFile?.mkdirs()
            BufferedOutputStream(FileOutputStream(t), 65536).use { o -> copyN(i, o, size) }
            if (mt > 0) t.setLastModified(mt * 1000)
            cnt++
          } else skipN(i, size)
          skipN(i, pad)
        }
      } catch (e: EOFException) { }
    }
    return cnt
  }

  fun list(f: File): List<Pair<String, Long>> {
    val out = ArrayList<Pair<String, Long>>()
    val k = kind(f)
    if (k == "zip") ZipFile(f).use { z -> val en = z.entries(); while (en.hasMoreElements() && out.size < 1000) { val e = en.nextElement(); out.add(e.name to e.size) } }
    else if (k == "tgz" || k == "tar") {
      val raw = BufferedInputStream(FileInputStream(f), 65536)
      val i = DataInputStream(if (k == "tgz") BufferedInputStream(GZIPInputStream(raw, 65536), 65536) else raw)
      i.use {
        val h = ByteArray(512)
        var ln: String? = null
        try {
          while (out.size < 1000) {
            i.readFully(h)
            if (h.all { it.toInt() == 0 }) break
            val size = str(h, 124, 12).trim().toLongOrNull(8) ?: 0L
            val ty = h[156].toInt().toChar()
            val pre = str(h, 345, 155); val nm0 = str(h, 0, 100)
            val pad = (512 - size % 512) % 512
            if (ty == 'L') { val b = ByteArray(size.toInt()); i.readFully(b); skipN(i, pad); ln = String(b, Charsets.UTF_8).trimEnd('\u0000'); continue }
            val name = ln ?: (if (pre.isNotEmpty()) "$pre/$nm0" else nm0)
            ln = null
            if (ty != 'x' && ty != 'g') out.add(name to size)
            skipN(i, size + pad)
          }
        } catch (e: EOFException) { }
      }
    } else if (k == "gz") out.add(f.nameWithoutExtension to f.length())
    return out
  }
}
