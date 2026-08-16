package cc.sunglint.weekclip.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
// Not androidx.hilt.navigation.compose: that one is deprecated in
// hilt-navigation-compose 1.4.0 and the compiler says so.
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.sunglint.weekclip.R
import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.domain.model.Studio
import cc.sunglint.weekclip.domain.model.StudioRole
import cc.sunglint.weekclip.ui.components.WeekclipLogoLockup
import cc.sunglint.weekclip.ui.theme.WeekclipTheme

/**
 * Stateful entry point. This is the only layer that knows a ViewModel exists;
 * everything below it takes plain values (compose-ui skill §1).
 */
@Composable
fun DashboardRoute(
  onStudioClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DashboardViewModel = hiltViewModel()
) {
  // collectAsStateWithLifecycle, not collectAsState: the latter keeps collecting
  // while the app is in the background, which for a paging list means work and
  // sockets nobody is looking at.
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  DashboardScreen(
    uiState = uiState,
    onStudioClick = onStudioClick,
    onRetry = viewModel::refresh,
    modifier = modifier
  )
}

/**
 * Stateless. Previewable, and testable without Hilt or a coroutine scheduler.
 */
@Composable
fun DashboardScreen(
  uiState: DashboardUiState,
  onStudioClick: (String) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    // The start destination, so this is the app's masthead. It sits above
    // `screen-title` rather than replacing it: that tag is a maestro selector
    // and the smoke flow also asserts the literal text "Studios".
    WeekclipLogoLockup(modifier = Modifier.padding(top = 16.dp))

    Text(
      text = stringResource(R.string.dashboard_title),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .padding(vertical = 16.dp)
        // `heading()` lets TalkBack jump between sections; `testTag` is what
        // maestro/ selects on (surfaced as resource-id by the
        // testTagsAsResourceId opt-in at the Scaffold root).
        .semantics { heading() }
        .testTag("screen-title")
    )

    when {
      uiState.showFullScreenLoader -> StateMessage(
        tag = "dashboard-loading",
        content = { CircularProgressIndicator() }
      )

      uiState.error != null -> ErrorState(error = uiState.error, onRetry = onRetry)

      uiState.isEmpty -> StateMessage(
        tag = "dashboard-empty",
        content = { Text(stringResource(R.string.dashboard_empty)) }
      )

      else -> LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("dashboard-list")
      ) {
        // Keyed so a refresh that reorders the list moves rows instead of
        // rebuilding every one of them.
        items(items = uiState.studios, key = { it.id }) { studio ->
          StudioRow(studio = studio, onClick = { onStudioClick(studio.id) })
        }
      }
    }
  }
}

@Composable
private fun StudioRow(
  studio: Studio,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      // 48dp is the accessibility minimum for anything tappable
      // (android-accessibility skill §2); the card is taller, but stating it
      // means padding changes cannot quietly drop below the floor.
      .sizeIn(minHeight = 48.dp)
      .clickable(onClick = onClick)
      // One node instead of two, so TalkBack announces "Name, Owner" rather
      // than stopping on each Text separately (skill §4, Grouping).
      .semantics(mergeDescendants = true) {}
      .testTag("studio-row")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(text = studio.name, style = MaterialTheme.typography.titleMedium)
      Text(
        text = stringResource(studio.role.labelRes()),
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}

@Composable
private fun ErrorState(
  error: AppError,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  StateMessage(tag = "dashboard-error", modifier = modifier) {
    Text(
      text = stringResource(error.messageRes()),
      style = MaterialTheme.typography.bodyLarge
    )
    Button(onClick = onRetry, modifier = Modifier.testTag("dashboard-retry")) {
      Text(stringResource(R.string.action_retry))
    }
  }
}

@Composable
private fun StateMessage(
  tag: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Column(
    modifier = modifier.fillMaxSize().testTag(tag),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    content()
  }
}

/**
 * Error -> copy.
 *
 * A `when` over the sealed set, so a new [AppError] case fails to compile here
 * instead of falling through to a generic message nobody notices is wrong.
 *
 * PRD-0008 D3: none of these name a way to pay. Capacity is not in this set at
 * all yet — when it is, the copy states the fact and stops there.
 */
private fun AppError.messageRes(): Int = when (this) {
  AppError.Offline -> R.string.error_offline
  AppError.Timeout -> R.string.error_timeout
  AppError.Unauthorized -> R.string.error_unauthorized
  AppError.NotFound -> R.string.error_not_found
  AppError.MalformedResponse -> R.string.error_generic
  is AppError.Server -> R.string.error_generic
  is AppError.Unexpected -> R.string.error_generic
}

private fun StudioRole.labelRes(): Int = when (this) {
  StudioRole.OWNER -> R.string.role_owner
  StudioRole.EDITOR -> R.string.role_editor
  StudioRole.VIEWER -> R.string.role_viewer
  StudioRole.UNKNOWN -> R.string.role_member
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
  WeekclipTheme {
    DashboardScreen(
      uiState = DashboardUiState(
        isLoading = false,
        studios = listOf(
          Studio("1", "family", "Family", "u1", StudioRole.OWNER, null, null),
          Studio("2", "trip", "Trip 2026", "u2", StudioRole.VIEWER, null, null)
        )
      ),
      onStudioClick = {},
      onRetry = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun DashboardErrorPreview() {
  WeekclipTheme {
    DashboardScreen(
      uiState = DashboardUiState(isLoading = false, error = AppError.Offline),
      onStudioClick = {},
      onRetry = {}
    )
  }
}
