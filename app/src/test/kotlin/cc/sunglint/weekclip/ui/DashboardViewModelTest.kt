package cc.sunglint.weekclip.ui

import app.cash.turbine.test
import cc.sunglint.weekclip.core.result.AppError
import cc.sunglint.weekclip.core.result.AppResult
import cc.sunglint.weekclip.domain.model.Studio
import cc.sunglint.weekclip.domain.model.StudioRole
import cc.sunglint.weekclip.domain.repository.StudioRepository
import cc.sunglint.weekclip.domain.usecase.GetStudiosUseCase
import cc.sunglint.weekclip.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * State transitions, driven by a fake repository.
 *
 * A fake and not MockWebServer here: what is under test is the sequence of
 * states the screen sees, and routing that through a socket would make the test
 * slower and able to fail for reasons that have nothing to do with the
 * ViewModel. The wire format is covered by `StudioRepositoryContractTest`.
 */
class DashboardViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  /**
   * `viewModelScope` is hardwired to `Dispatchers.Main`, which does not exist
   * in a JVM unit test — without this every test dies on "Module with the Main
   * dispatcher had failed to initialize".
   */
  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class FakeStudioRepository(
    var response: AppResult<List<Studio>>
  ) : StudioRepository {
    var callCount = 0
      private set

    override suspend fun getStudios(): AppResult<List<Studio>> {
      callCount++
      return response
    }
  }

  private fun studio(id: String, name: String, updatedAt: String?) =
    Studio(id, id, name, "owner", StudioRole.OWNER, null, updatedAt)

  @Test
  fun `starts loading and lands on the loaded list`() = runTest(dispatcher) {
    val repo = FakeStudioRepository(AppResult.Success(listOf(studio("1", "Family", null))))
    val viewModel = DashboardViewModel(GetStudiosUseCase(repo))

    viewModel.uiState.test {
      // The init block has launched but not run: the first emission is the
      // initial state, and it must already say "loading" or the screen flashes
      // an empty list before the first frame of data.
      val initial = awaitItem()
      assertTrue(initial.isLoading)
      assertTrue(initial.studios.isEmpty())

      runCurrent()

      val loaded = awaitItem()
      assertFalse(loaded.isLoading)
      assertEquals(listOf("Family"), loaded.studios.map { it.name })
      assertEquals(null, loaded.error)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a failure clears loading and exposes the error`() = runTest(dispatcher) {
    val repo = FakeStudioRepository(AppResult.Failure(AppError.Offline))
    val viewModel = DashboardViewModel(GetStudiosUseCase(repo))

    runCurrent()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(AppError.Offline, state.error)
  }

  @Test
  fun `a failed refresh keeps the rows that are already on screen`() = runTest(dispatcher) {
    val repo = FakeStudioRepository(AppResult.Success(listOf(studio("1", "Family", null))))
    val viewModel = DashboardViewModel(GetStudiosUseCase(repo))
    runCurrent()
    assertEquals(1, viewModel.uiState.value.studios.size)

    repo.response = AppResult.Failure(AppError.Timeout)
    viewModel.refresh()
    runCurrent()

    val state = viewModel.uiState.value
    assertEquals(AppError.Timeout, state.error)
    // The regression this guards: wiping real content because a background
    // reload failed.
    assertEquals(1, state.studios.size)
    assertFalse(state.isRefreshing)
  }

  @Test
  fun `refresh does not blank the screen with the full-page loader`() = runTest(dispatcher) {
    val repo = FakeStudioRepository(AppResult.Success(listOf(studio("1", "Family", null))))
    val viewModel = DashboardViewModel(GetStudiosUseCase(repo))
    runCurrent()

    // The in-flight state cannot be read off `.value`: the fake returns without
    // suspending, so by the time the dispatcher yields, the refresh is already
    // finished. Collecting the flow is what makes the intermediate emission
    // observable at all.
    viewModel.uiState.test {
      assertFalse(awaitItem().isRefreshing)

      viewModel.refresh()
      runCurrent()

      val inFlight = awaitItem()
      assertTrue(inFlight.isRefreshing)
      assertFalse("refresh must not set isLoading — that hides the list", inFlight.isLoading)
      // The list is still there while the reload runs.
      assertEquals(1, inFlight.studios.size)

      val settled = awaitItem()
      assertFalse(settled.isRefreshing)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `isEmpty distinguishes no studios from not loaded yet`() = runTest(dispatcher) {
    val repo = FakeStudioRepository(AppResult.Success(emptyList()))
    val viewModel = DashboardViewModel(GetStudiosUseCase(repo))

    assertFalse("still loading — not empty", viewModel.uiState.value.isEmpty)

    runCurrent()

    assertTrue(viewModel.uiState.value.isEmpty)
  }

  @Test
  fun `the use case orders most-recently-updated first`() = runTest(dispatcher) {
    val repo = FakeStudioRepository(
      AppResult.Success(
        listOf(
          studio("1", "Older", "2026-08-01T00:00:00.000Z"),
          studio("2", "Newer", "2026-08-10T00:00:00.000Z")
        )
      )
    )
    val viewModel = DashboardViewModel(GetStudiosUseCase(repo))

    runCurrent()

    assertEquals(listOf("Newer", "Older"), viewModel.uiState.value.studios.map { it.name })
  }
}
