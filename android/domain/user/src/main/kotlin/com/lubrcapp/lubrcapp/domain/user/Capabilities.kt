package com.lubrcapp.lubrcapp.domain.user

@JvmInline
value class CapabilityKey(val value: String) {
    init {
        require(value.matches(Regex("[a-z][a-z0-9_.-]*"))) {
            "Capability key must be a lowercase dotted identifier"
        }
    }

    override fun toString(): String = value
}

data class CapabilityDefinition(
    val key: CapabilityKey,
    val description: String,
)

object Capabilities {
    val BUSINESS_MANAGE = CapabilityKey("business.manage")
    val USERS_VIEW = CapabilityKey("users.view")
    val USERS_MANAGE = CapabilityKey("users.manage")
    val ROLES_MANAGE = CapabilityKey("roles.manage")
    val SETTINGS_MANAGE = CapabilityKey("settings.manage")
    val PARTIES_VIEW = CapabilityKey("parties.view")
    val PARTIES_MANAGE = CapabilityKey("parties.manage")
    val ITEMS_VIEW = CapabilityKey("items.view")
    val ITEMS_MANAGE = CapabilityKey("items.manage")
    val INVENTORY_VIEW = CapabilityKey("inventory.view")
    val INVENTORY_ADJUST = CapabilityKey("inventory.adjust")
    val INVENTORY_TRANSFER = CapabilityKey("inventory.transfer")
    val SALES_VIEW = CapabilityKey("sales.view")
    val SALES_CREATE = CapabilityKey("sales.create")
    val SALES_EDIT = CapabilityKey("sales.edit")
    val SALES_POST = CapabilityKey("sales.post")
    val DISCOUNT_OVERRIDE = CapabilityKey("invoice.discount.override")
    val PURCHASES_VIEW = CapabilityKey("purchases.view")
    val PURCHASES_MANAGE = CapabilityKey("purchases.manage")
    val PAYMENTS_VIEW = CapabilityKey("payments.view")
    val PAYMENTS_MANAGE = CapabilityKey("payments.manage")
    val ACCOUNTING_VIEW = CapabilityKey("accounting.view")
    val ACCOUNTING_MANAGE = CapabilityKey("accounting.manage")
    val BANKING_MANAGE = CapabilityKey("banking.manage")
    val COST_VIEW = CapabilityKey("cost.view")
    val REPORTS_VIEW = CapabilityKey("reports.view")
    val PROFIT_REPORTS_VIEW = CapabilityKey("reports.profit.view")
    val HISTORY_EDIT = CapabilityKey("history.edit")
    val SYNC_MANAGE = CapabilityKey("sync.manage")
    val AUDIT_VIEW = CapabilityKey("audit.view")
    val PRINTING_VIEW = CapabilityKey("printing.view")

    val DEFINITIONS: List<CapabilityDefinition> = listOf(
        CapabilityDefinition(BUSINESS_MANAGE, "Manage business identity and lifecycle"),
        CapabilityDefinition(USERS_VIEW, "View business users and memberships"),
        CapabilityDefinition(USERS_MANAGE, "Create, activate and deactivate business users"),
        CapabilityDefinition(ROLES_MANAGE, "Create roles and assign capabilities"),
        CapabilityDefinition(SETTINGS_MANAGE, "Change business settings and configuration"),
        CapabilityDefinition(PARTIES_VIEW, "View customers, suppliers and other parties"),
        CapabilityDefinition(PARTIES_MANAGE, "Create and edit parties"),
        CapabilityDefinition(ITEMS_VIEW, "View items and services"),
        CapabilityDefinition(ITEMS_MANAGE, "Create and edit items and services"),
        CapabilityDefinition(INVENTORY_VIEW, "View stock and inventory history"),
        CapabilityDefinition(INVENTORY_ADJUST, "Post inventory adjustments"),
        CapabilityDefinition(INVENTORY_TRANSFER, "Transfer stock between locations"),
        CapabilityDefinition(SALES_VIEW, "View sales documents"),
        CapabilityDefinition(SALES_CREATE, "Create quotations, orders and invoices"),
        CapabilityDefinition(SALES_EDIT, "Edit eligible sales documents"),
        CapabilityDefinition(SALES_POST, "Post/finalize sales documents"),
        CapabilityDefinition(DISCOUNT_OVERRIDE, "Override configured invoice discount limits"),
        CapabilityDefinition(PURCHASES_VIEW, "View purchase documents"),
        CapabilityDefinition(PURCHASES_MANAGE, "Create and post purchase documents"),
        CapabilityDefinition(PAYMENTS_VIEW, "View receipts, payments and allocations"),
        CapabilityDefinition(PAYMENTS_MANAGE, "Record and allocate money movements"),
        CapabilityDefinition(ACCOUNTING_VIEW, "View accounting ledgers and journals"),
        CapabilityDefinition(ACCOUNTING_MANAGE, "Post accounting transactions"),
        CapabilityDefinition(BANKING_MANAGE, "Manage cash and bank accounts"),
        CapabilityDefinition(COST_VIEW, "View item cost and cost-sensitive information"),
        CapabilityDefinition(REPORTS_VIEW, "View operational reports"),
        CapabilityDefinition(PROFIT_REPORTS_VIEW, "View profit-sensitive financial reports"),
        CapabilityDefinition(HISTORY_EDIT, "Correct history through permitted reversal/edit workflows"),
        CapabilityDefinition(SYNC_MANAGE, "Manage synchronization and conflict operations"),
        CapabilityDefinition(AUDIT_VIEW, "View audit and activity history"),
        CapabilityDefinition(PRINTING_VIEW, "View printable documents and previews"),
    )

    val ALL: Set<CapabilityKey> = DEFINITIONS.mapTo(linkedSetOf()) { it.key }

    fun definition(key: CapabilityKey): CapabilityDefinition? =
        DEFINITIONS.firstOrNull { it.key == key }
}
