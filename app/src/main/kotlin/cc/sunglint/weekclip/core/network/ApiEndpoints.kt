package cc.sunglint.weekclip.core.network

/**
 * Service base URLs.
 *
 * weekclip is split across three Workers services and the app talks to all of
 * them, exactly as the web client does (`getApiBaseUrl` / `getUserApiBaseUrl` /
 * `getBillingApiBaseUrl` in weekclip-web `src/shared/api/client.ts`).
 *
 * Billing is deliberately absent: PRD-0008 D3 requires zero payment strings in
 * the app binary, and N6 makes CI check for it. Capacity *reads* that the app
 * does need are served by weekclip-api, not the billing service.
 */
data class ApiEndpoints(
  val apiBaseUrl: String,
  val userApiBaseUrl: String
) {
  companion object {
    // Placeholder values. Phase 2 wires these per build variant from Gradle;
    // hardcoding production hosts in the skeleton would be the kind of thing
    // that silently ships.
    val UNCONFIGURED = ApiEndpoints(
      apiBaseUrl = "",
      userApiBaseUrl = ""
    )
  }
}
