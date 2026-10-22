package com.lubrcapp.lubrcapp

import androidx.lifecycle.ViewModel
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.whitelist.featureflags.FeatureFlagService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Live white-label configuration gating for the app shell (Phase 13.2).
 *
 * The enabled-module keys come from the production configuration path —
 * [FeatureFlagService], whose implementation resolves
 * `WhiteLabelConfigService` (deployment `includedModules` from
 * `white-label/deployment.properties` via `AppDeploymentConfigModule`,
 * layered with the persisted business module overrides by
 * `WhiteLabelConfigResolver`). Nothing here re-implements resolution.
 *
 * The flow emits the resolved key set for the given business, and null
 * whenever resolution fails (for example the business is not yet visible
 * to the configuration repositories during bootstrap or a switch). The
 * shell treats null as "no gating information": every module stays
 * visible, which is exactly the pre-gating behavior — gating may only
 * ever hide a module on the strength of a successfully resolved
 * configuration, never on an error or a missing business.
 */
@HiltViewModel
class ShellConfigViewModel @Inject constructor(
    private val featureFlagService: FeatureFlagService,
) : ViewModel() {

    fun enabledModuleKeys(context: TenantContext): Flow<Set<String>?> =
        featureFlagService
            .observeEnabledFeatures(context.tenantId, context.businessId)
            .map<Set<String>, Set<String>?> { enabled -> enabled }
            .catch { emit(null) }
}
