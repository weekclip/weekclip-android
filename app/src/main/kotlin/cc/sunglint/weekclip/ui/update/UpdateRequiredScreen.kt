package cc.sunglint.weekclip.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.sunglint.weekclip.R
import cc.sunglint.weekclip.ui.components.WeekclipLogoMark
import cc.sunglint.weekclip.ui.components.WeekclipLogoSize

/**
 * The terminal screen for a build the server no longer accepts.
 *
 * Deliberately has no way past it. A dismissible banner would leave people
 * running a client the server has declared incompatible, which is the situation
 * the gate exists to end.
 *
 * The store button is absent when there is no URL — iOS has no App Store record
 * yet and Android could lose its listing. Stating the fact without offering a
 * button that goes nowhere is the honest version.
 */
@Composable
fun UpdateRequiredScreen(
  storeUrl: String?,
  onOpenStore: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // The only full-screen moment the app currently owns, and the one screen a
    // blocked user is guaranteed to see — so it is where the mark earns its
    // place. Above the title, not replacing it: `screen-title` is what
    // maestro/ selects on.
    WeekclipLogoMark(size = WeekclipLogoSize.ExtraLarge)
    Text(
      text = stringResource(R.string.update_required_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag("screen-title")
    )
    Text(
      text = stringResource(R.string.update_required_body),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
    if (storeUrl != null) {
      Button(
        onClick = { onOpenStore(storeUrl) },
        modifier = Modifier.testTag("update-open-store")
      ) {
        Text(text = stringResource(R.string.update_required_action))
      }
    }
  }
}
