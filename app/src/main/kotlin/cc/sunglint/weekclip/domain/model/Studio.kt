package cc.sunglint.weekclip.domain.model

/**
 * A studio as the app reasons about it.
 *
 * Pure Kotlin on purpose — no `android.*`, no serialization annotations, no
 * Retrofit types. That is what lets this be the shape both the UI and the tests
 * agree on while the wire format stays free to change (android-architecture
 * skill, "Domain Layer").
 *
 * Timestamps stay as the raw ISO-8601 strings the API sends. Parsing them into
 * `Instant` here would force a formatting decision (locale, timezone, relative
 * vs absolute) into the domain, and that decision belongs to the screen.
 */
data class Studio(
  val id: String,
  val slug: String,
  val name: String,
  val ownerId: String,
  val role: StudioRole,
  val createdAt: String?,
  val updatedAt: String?
)

/**
 * Membership role.
 *
 * The API sends these capitalised ("Owner"/"Editor"/"Viewer") — see
 * `presentStudioMembership` in weekclip-api and `StudioRole` in weekclip-web.
 * [UNKNOWN] exists so that a role added server-side degrades to read-only
 * instead of crashing the list.
 */
enum class StudioRole {
  OWNER,
  EDITOR,
  VIEWER,
  UNKNOWN;

  /** Everything except viewing is gated on this. */
  val canEdit: Boolean get() = this == OWNER || this == EDITOR

  companion object {
    fun fromWire(value: String?): StudioRole = when (value?.lowercase()) {
      "owner" -> OWNER
      "editor" -> EDITOR
      "viewer" -> VIEWER
      else -> UNKNOWN
    }
  }
}
