package com.lubrcapp.lubrcapp.core.repository

/**
 * Phase 32.3 — demo-company identity. Demo tenants and businesses
 * carry ids starting with this prefix, so the dashboard can label
 * them DEMO and the demo wipe can refuse any non-demo scope. No
 * schema change: the prefix is the marker.
 */
const val DEMO_SCOPE_PREFIX = "demo-"

/** True when this tenant or business id belongs to a demo company. */
fun isDemoScopeId(id: String): Boolean = id.startsWith(DEMO_SCOPE_PREFIX)
