package cc.sunglint.weekclip.core.session

/**
 * Persistence for the profile session. Survives process death; does not survive
 * uninstall or a wiped key.
 *
 * Reads return `null` for "nothing stored" **and** for "stored but no longer
 * readable" (the Keystore key was invalidated, the blob was truncated). The
 * caller cannot act differently on those two, and an implementation that threw
 * on the second would turn a recoverable sign-out into a crash loop on launch.
 */
interface SessionStore {
  suspend fun read(): ProfileSession?

  suspend fun write(session: ProfileSession)

  suspend fun clear()
}
