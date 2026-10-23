package com.lubrcapp.lubrcapp.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lubrcapp.lubrcapp.R

/**
 * Phase 13.4 — the shell's bottom navigation (master spec §13 bottom-navigation shape).
 *
 * The bar is registry-driven, not a hardcoded destination list: [bottomNavModules]
 * comes from [AppModuleRegistry.bottomNavModules], so a slot is shown only while
 * its module passes the configuration and capability gates, and a future module
 * (Transactions, spec §14) appears automatically once its registry entry declares
 * a [BottomNavSlot]. "More" is a shell surface, not a registry module — it opens
 * the overflow list of the remaining visible modules.
 *
 * Bar taps route through the same [AppModuleRegistry] dispatch as the module
 * list: selecting a slotted module hands its id to the shell's existing
 * `selectedFeature` state, which the same composed visibility guards protect,
 * so the bar cannot open a module the gates hide.
 *
 * The spec's floating action button "+" is intentionally NOT rendered here: no
 * create-flows exist yet (§12 quick actions are Package 13.5), and a dead "+"
 * would be worse than none. 13.5 wires the FAB slot against the quick actions.
 */
enum class BottomNavSelection {
    HOME,
    MORE,
}

@Composable
fun ShellBottomBar(
    bottomNavModules: List<AppModule>,
    selected: BottomNavSelection,
    onSelectHome: () -> Unit,
    onSelectModule: (AppModule) -> Unit,
    onSelectMore: () -> Unit,
) {
    NavigationBar {
        bottomNavModules.forEach { module ->
            val icon = when (module.bottomNavSlot) {
                BottomNavSlot.HOME -> Icons.Filled.Home
                BottomNavSlot.ITEMS -> Icons.Filled.Inventory
                BottomNavSlot.PARTIES -> Icons.Filled.People
                // Unreachable: the caller only passes slotted modules.
                null -> Icons.Filled.MoreHoriz
            }
            val isHome = module.bottomNavSlot == BottomNavSlot.HOME
            NavigationBarItem(
                selected = isHome && selected == BottomNavSelection.HOME,
                onClick = {
                    if (isHome) {
                        onSelectHome()
                    } else {
                        onSelectModule(module)
                    }
                },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(module.titleRes)) },
            )
        }
        NavigationBarItem(
            selected = selected == BottomNavSelection.MORE,
            onClick = onSelectMore,
            icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_more_title)) },
        )
    }
}
