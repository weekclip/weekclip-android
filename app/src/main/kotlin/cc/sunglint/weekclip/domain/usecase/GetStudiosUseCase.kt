package cc.sunglint.weekclip.domain.usecase

import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.core.result.map
import cc.sunglint.weekclip.domain.model.Studio
import cc.sunglint.weekclip.domain.repository.StudioRepository
import javax.inject.Inject

/**
 * Studios for the dashboard, ordered the way the dashboard wants them.
 *
 * This is the one thing a use case earns over calling the repository directly:
 * ordering is a product rule, and it would otherwise be duplicated into every
 * caller (and re-decided differently each time). The repository stays a plain
 * pass-through of what the server sent.
 *
 * Most recently touched first, since that is what the web dashboard shows and
 * the two are meant to feel like one product.
 */
class GetStudiosUseCase @Inject constructor(
  private val repository: StudioRepository
) {
  suspend operator fun invoke(): AppResult<List<Studio>> =
    repository.getStudios().map { studios ->
      // Nulls last: a studio with no timestamp is older than one that has any.
      studios.sortedWith(
        compareByDescending<Studio> { it.updatedAt ?: "" }.thenBy { it.name }
      )
    }
}
