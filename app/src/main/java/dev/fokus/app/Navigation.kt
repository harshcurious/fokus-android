package dev.fokus.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.fokus.app.ui.settings.SettingsScreen
import dev.fokus.app.ui.timer.TimerScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Timer)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Timer> { TimerScreen(onOpenSettings = { backStack.add(Settings) }) }
        entry<Settings> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
      },
  )
}
