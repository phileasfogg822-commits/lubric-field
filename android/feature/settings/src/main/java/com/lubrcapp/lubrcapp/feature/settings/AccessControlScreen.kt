package com.lubrcapp.lubrcapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lubrcapp.lubrcapp.core.repository.BusinessMember
import com.lubrcapp.lubrcapp.core.repository.BusinessRole
import com.lubrcapp.lubrcapp.core.repository.TenantContext
import com.lubrcapp.lubrcapp.core.designsystem.AppCard
import com.lubrcapp.lubrcapp.core.designsystem.LubrcTheme
import com.lubrcapp.lubrcapp.domain.common.RoleId
import com.lubrcapp.lubrcapp.domain.user.Capabilities
import com.lubrcapp.lubrcapp.domain.user.CapabilityKey

@Composable
fun AccessControlScreen(
    context: TenantContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessControlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreateUser by remember { mutableStateOf(false) }
    var showCreateRole by remember { mutableStateOf(false) }
    var roleEditor by remember { mutableStateOf<BusinessRole?>(null) }
    var memberEditor by remember { mutableStateOf<BusinessMember?>(null) }

    LaunchedEffect(context) {
        viewModel.start(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LubrcTheme.tokens.contentPadding),
        verticalArrangement = Arrangement.spacedBy(LubrcTheme.tokens.contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.access_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.access_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.access_back))
            }
        }

        state.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        AccessSectionHeader(
            title = stringResource(R.string.access_users),
            action = if (state.canManageUsers) stringResource(R.string.access_add_user) else null,
            onAction = { showCreateUser = true },
        )

        if (state.members.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(R.string.access_no_users),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.members.forEach { member ->
            MemberCard(
                member = member,
                roles = state.roles,
                canManageUsers = state.canManageUsers,
                canManageRoles = state.canManageRoles,
                onActiveChange = { active ->
                    viewModel.setMemberActive(member.membershipId, active)
                },
                onEditRoles = { memberEditor = member },
            )
        }

        HorizontalDivider()

        AccessSectionHeader(
            title = stringResource(R.string.access_roles),
            action = if (state.canManageRoles) stringResource(R.string.access_add_role) else null,
            onAction = { showCreateRole = true },
        )

        if (state.roles.isEmpty() && !state.isLoading) {
            Text(
                text = stringResource(R.string.access_no_roles),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.roles.forEach { role ->
            RoleCard(
                role = role,
                canManageRoles = state.canManageRoles,
                onActiveChange = { active ->
                    viewModel.setRoleActive(role.roleId, active)
                },
                onEditPermissions = { roleEditor = role },
            )
        }
    }

    if (showCreateUser) {
        CreateUserDialog(
            onDismiss = { showCreateUser = false },
            onCreate = { name, phone, email ->
                viewModel.createUser(name, phone, email.takeIf(String::isNotBlank))
                showCreateUser = false
            },
        )
    }

    if (showCreateRole) {
        CreateRoleDialog(
            onDismiss = { showCreateRole = false },
            onCreate = { name ->
                viewModel.createRole(name, emptySet())
                showCreateRole = false
            },
        )
    }

    roleEditor?.let { role ->
        CapabilityDialog(
            role = role,
            onDismiss = { roleEditor = null },
            onSave = { capabilities ->
                viewModel.setRoleCapabilities(role.roleId, capabilities)
                roleEditor = null
            },
        )
    }

    memberEditor?.let { member ->
        RoleAssignmentDialog(
            member = member,
            roles = state.roles.filter(BusinessRole::active),
            onDismiss = { memberEditor = null },
            onSave = { roles ->
                viewModel.assignRoles(member.membershipId, roles)
                memberEditor = null
            },
        )
    }
}

@Composable
private fun AccessSectionHeader(
    title: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (action != null) {
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MemberCard(
    member: BusinessMember,
    roles: List<BusinessRole>,
    canManageUsers: Boolean,
    canManageRoles: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onEditRoles: () -> Unit,
) {
    val roleNames = roles
        .filter { it.roleId in member.roleIds }
        .map(BusinessRole::name)
        .sorted()

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LubrcTheme.tokens.contentPadding),
            verticalArrangement = Arrangement.spacedBy(LubrcTheme.tokens.contentPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                    val contact = member.email ?: member.phone
                    if (contact.isNotBlank()) {
                        Text(
                            contact,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canManageUsers) {
                    Switch(
                        checked = member.active,
                        onCheckedChange = onActiveChange,
                    )
                }
            }
            Text(
                text = if (roleNames.isEmpty()) {
                    stringResource(R.string.access_no_role_assigned)
                } else {
                    roleNames.joinToString()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (canManageRoles) {
                OutlinedButton(onClick = onEditRoles) {
                    Text(stringResource(R.string.access_assign_roles))
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    role: BusinessRole,
    canManageRoles: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onEditPermissions: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LubrcTheme.tokens.contentPadding),
            verticalArrangement = Arrangement.spacedBy(LubrcTheme.tokens.contentPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(role.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (role.systemTemplate) {
                            stringResource(R.string.access_system_role)
                        } else {
                            stringResource(R.string.access_custom_role)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canManageRoles && !role.systemTemplate) {
                    Switch(
                        checked = role.active,
                        onCheckedChange = onActiveChange,
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.access_capability_count,
                    role.capabilities.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (canManageRoles && !role.systemTemplate) {
                OutlinedButton(onClick = onEditPermissions) {
                    Text(stringResource(R.string.access_edit_permissions))
                }
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_add_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LubrcTheme.tokens.contentPadding)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.access_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.access_phone)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.access_email)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), phone.trim(), email.trim()) },
            ) {
                Text(stringResource(R.string.access_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.access_cancel))
            }
        },
    )
}

@Composable
private fun CreateRoleDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_add_role)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.access_role_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) {
                Text(stringResource(R.string.access_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.access_cancel))
            }
        },
    )
}

@Composable
private fun CapabilityDialog(
    role: BusinessRole,
    onDismiss: () -> Unit,
    onSave: (Set<CapabilityKey>) -> Unit,
) {
    var selected by remember(role.roleId) { mutableStateOf(role.capabilities) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_edit_permissions_for, role.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Capabilities.DEFINITIONS.forEach { definition ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = definition.key in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) {
                                    selected + definition.key
                                } else {
                                    selected - definition.key
                                }
                            },
                        )
                        Column {
                            Text(definition.key.value, style = MaterialTheme.typography.labelLarge)
                            Text(
                                definition.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.access_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.access_cancel))
            }
        },
    )
}

@Composable
private fun RoleAssignmentDialog(
    member: BusinessMember,
    roles: List<BusinessRole>,
    onDismiss: () -> Unit,
    onSave: (Set<RoleId>) -> Unit,
) {
    var selected by remember(member.membershipId) { mutableStateOf(member.roleIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_assign_roles_to, member.name)) },
        text = {
            Column {
                roles.forEach { role ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = role.roleId in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) {
                                    selected + role.roleId
                                } else {
                                    selected - role.roleId
                                }
                            },
                        )
                        Text(role.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.access_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.access_cancel))
            }
        },
    )
}
