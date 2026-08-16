package cc.sunglint.weekclip.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher icon is the one piece of UI that ships to a home screen and is
 * never rendered by a test, a preview or a maestro flow. The version this
 * replaced — a blue square in a colour no token names — sat in `main` from the
 * first skeleton commit precisely because nothing looked at it.
 *
 * So look at it here. These are plain JVM tests: they read the vector XML off
 * disk and do the arithmetic the launcher would do. No Robolectric, no device.
 *
 * What they cannot see: whether the result is *pretty*. That still needs eyes
 * on a home screen. What they can see is the failure mode that actually
 * happens — a transform that pushes the mark out of the crop, or three
 * drawables that drift apart.
 */
class BrandAssetsTest {

  // ---------------------------------------------------------------------------
  // Adaptive-icon geometry
  //
  // Android masks the 108x108 foreground to a launcher-chosen shape. The
  // guaranteed-visible region is the central 72dp; the 66dp "keyline" circle
  // (18..90 is the 72dp window, 21..87 the 66dp one) is where content is safe
  // on every shape including the tightest circular masks.
  // ---------------------------------------------------------------------------
  private val viewport = 108.0
  private val keylineMin = 21.0
  private val keylineMax = 87.0

  @Test
  fun `launcher foreground keeps the mark inside the 66dp keyline`() {
    assertInsideKeyline("ic_launcher_foreground.xml")
  }

  @Test
  fun `launcher monochrome keeps the mark inside the 66dp keyline`() {
    assertInsideKeyline("ic_launcher_monochrome.xml")
  }

  private fun assertInsideKeyline(fileName: String) {
    val xml = drawable(fileName).readText()
    val box = pathBounds(pathData(xml))
    val (sx, sy) = groupScale(xml)
    val (tx, ty) = groupTranslate(xml)

    // A <group> with no pivot applies translate o scale, so p -> s*p + t.
    val minX = box.minX * sx + tx
    val maxX = box.maxX * sx + tx
    val minY = box.minY * sy + ty
    val maxY = box.maxY * sy + ty

    assertTrue(
      "$fileName: mark spans x $minX..$maxX, outside the keyline $keylineMin..$keylineMax",
      minX >= keylineMin && maxX <= keylineMax
    )
    assertTrue(
      "$fileName: mark spans y $minY..$maxY, outside the keyline $keylineMin..$keylineMax",
      minY >= keylineMin && maxY <= keylineMax
    )

    // Off-centre is the failure this file exists for: the source path's ink is
    // NOT centred in its own 687 viewBox (its centre is 343.211, 344.141 against
    // a viewBox centre of 343.5), so a transform that only scales looks fine in
    // isolation and sits low-left under a circular mask.
    assertEquals("$fileName: not horizontally centred", viewport / 2, (minX + maxX) / 2, 0.5)
    assertEquals("$fileName: not vertically centred", viewport / 2, (minY + maxY) / 2, 0.5)
  }

  @Test
  fun `all three drawables draw the same mark`() {
    val foreground = pathData(drawable("ic_launcher_foreground.xml").readText())
    val monochrome = pathData(drawable("ic_launcher_monochrome.xml").readText())
    val inApp = pathData(drawable("ic_weekclip_logo.xml").readText())

    // Whitespace differs because the files wrap the attribute differently; the
    // geometry must not.
    assertEquals("monochrome silhouette drifted from the colour layer", foreground, monochrome)
    assertEquals("in-app mark drifted from the launcher icon", foreground, inApp)
  }

  @Test
  fun `the in-app mark is drawn at its source viewBox`() {
    val xml = drawable("ic_weekclip_logo.xml").readText()
    // 687 is logo.svg's viewBox. Nothing crops this drawable, so re-fitting it
    // into an adaptive-icon safe zone would only inset it for no reason.
    assertTrue("expected viewportWidth 687", xml.contains("android:viewportWidth=\"687\""))
    assertTrue("expected viewportHeight 687", xml.contains("android:viewportHeight=\"687\""))
    assertTrue(
      "the in-app mark must carry no <group> transform",
      !xml.contains("<group")
    )
  }

  @Test
  fun `every bundled font is a real TrueType file`() {
    val fonts = File(resDir(), "font").listFiles().orEmpty().filter { it.extension == "ttf" }
    assertTrue("no fonts found under res/font", fonts.isNotEmpty())

    fonts.forEach { file ->
      // sfnt version 0x00010000 — what a .ttf starts with. Catches a Git LFS
      // pointer or an HTML error page saved under a .ttf name, which is what a
      // broken vendoring step actually produces.
      val header = file.inputStream().use { stream -> ByteArray(4).also { stream.read(it) } }
      assertEquals(
        "${file.name} does not start with the TrueType sfnt version",
        listOf(0x00, 0x01, 0x00, 0x00),
        header.map { it.toInt() and 0xFF }
      )
    }
  }

  // --- helpers ---------------------------------------------------------------

  /**
   * Gradle runs unit tests with the module directory as the working directory,
   * but IDE run configurations sometimes use the repo root. Try both rather
   * than fail with a path nobody can read.
   */
  private fun resDir(): File =
    listOf(File("src/main/res"), File("app/src/main/res"))
      .firstOrNull { it.isDirectory }
      ?: error("cannot locate res/ from working directory ${File(".").absolutePath}")

  private fun drawable(name: String) = File(resDir(), "drawable/$name")

  private fun pathData(xml: String): String {
    val raw = Regex("android:pathData=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
      ?: error("no android:pathData in the drawable")
    return raw.trim().replace(Regex("\\s+"), " ")
  }

  private fun groupScale(xml: String): Pair<Double, Double> =
    attr(xml, "scaleX") to attr(xml, "scaleY")

  private fun groupTranslate(xml: String): Pair<Double, Double> =
    attr(xml, "translateX") to attr(xml, "translateY")

  private fun attr(xml: String, name: String): Double =
    Regex("android:$name=\"([-0-9.]+)\"").find(xml)?.groupValues?.get(1)?.toDouble()
      ?: error("no android:$name on the <group>")

  private data class Bounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
  )

  /**
   * Bounds of an absolute M/L/C/Z path.
   *
   * Cubics are sampled rather than solved: the extremum of a Bezier can lie
   * between its control points, so using the control points alone overstates
   * the box and using only the endpoints understates it. 40 samples per curve
   * is far finer than the 0.5dp tolerance the assertions use.
   *
   * Anything other than M/L/C/Z raises instead of being skipped — a silently
   * ignored arc would move the mark and shrink the measured box at the same
   * time, which is the one way this check could pass while being wrong.
   */
  private fun pathBounds(d: String): Bounds {
    val tokens = Regex("[MLCZmlcz]|-?\\d*\\.?\\d+").findAll(d).map { it.value }.toList()
    val points = mutableListOf<Pair<Double, Double>>()

    var i = 0
    var command = ""
    var cx = 0.0
    var cy = 0.0
    var startX = 0.0
    var startY = 0.0

    fun next(): Double = tokens[i++].toDouble()

    while (i < tokens.size) {
      if (tokens[i].length == 1 && tokens[i][0].isLetter()) {
        command = tokens[i]
        i++
        if (command.equals("Z", ignoreCase = true)) {
          cx = startX
          cy = startY
          continue
        }
      }
      when (command) {
        "M" -> {
          cx = next(); cy = next()
          startX = cx; startY = cy
          points += cx to cy
          // An implicit repeat after M is a lineto, per the SVG grammar.
          command = "L"
        }
        "L" -> {
          cx = next(); cy = next()
          points += cx to cy
        }
        "C" -> {
          val x0 = cx
          val y0 = cy
          val x1 = next(); val y1 = next()
          val x2 = next(); val y2 = next()
          val x3 = next(); val y3 = next()
          for (step in 0..40) {
            val t = step / 40.0
            val u = 1 - t
            points += (u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3) to
              (u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3)
          }
          cx = x3; cy = y3
        }
        else -> error("unsupported path command '$command' — this parser handles M, L, C, Z only")
      }
    }

    return Bounds(
      minX = points.minOf { it.first },
      minY = points.minOf { it.second },
      maxX = points.maxOf { it.first },
      maxY = points.maxOf { it.second }
    )
  }
}
