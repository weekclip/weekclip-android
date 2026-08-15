package cc.sunglint.weekclip.core.session

/**
 * Wall-clock seconds, as a seam.
 *
 * Expiry logic that reads `System.currentTimeMillis()` directly can only be
 * tested by sleeping, and a test that sleeps for an hour is a test nobody runs.
 * Every "is this token still good" decision goes through here.
 */
fun interface SessionClock {
  fun nowEpochSeconds(): Long

  companion object {
    val System: SessionClock = SessionClock { java.lang.System.currentTimeMillis() / 1000 }
  }
}
