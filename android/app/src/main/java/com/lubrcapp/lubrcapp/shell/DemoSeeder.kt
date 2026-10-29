package com.lubrcapp.lubrcapp.shell

import com.lubrcapp.lubrcapp.core.repository.datasource.BusinessLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.BusinessSetting
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupSettingKeys
import com.lubrcapp.lubrcapp.core.repository.OperationalScope
import com.lubrcapp.lubrcapp.core.repository.datasource.SettingsLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.datasource.UserLocalDataSource
import com.lubrcapp.lubrcapp.core.repository.isDemoScopeId
import com.lubrcapp.lubrcapp.domain.accounting.AccountType
import com.lubrcapp.lubrcapp.domain.business.Business
import com.lubrcapp.lubrcapp.domain.business.Tenant
import com.lubrcapp.lubrcapp.domain.common.BusinessId
import com.lubrcapp.lubrcapp.domain.common.CurrencyCode
import com.lubrcapp.lubrcapp.domain.common.DeviceId
import com.lubrcapp.lubrcapp.domain.common.EntityMetadata
import com.lubrcapp.lubrcapp.domain.common.InvoiceId
import com.lubrcapp.lubrcapp.domain.common.ItemId
import com.lubrcapp.lubrcapp.domain.common.Money
import com.lubrcapp.lubrcapp.domain.common.PartyId
import com.lubrcapp.lubrcapp.domain.common.PaymentId
import com.lubrcapp.lubrcapp.domain.common.PaymentMethodId
import com.lubrcapp.lubrcapp.domain.common.Percentage
import com.lubrcapp.lubrcapp.domain.common.PurchaseId
import com.lubrcapp.lubrcapp.domain.common.Quantity
import com.lubrcapp.lubrcapp.domain.common.SettingId
import com.lubrcapp.lubrcapp.domain.common.TenantId
import com.lubrcapp.lubrcapp.domain.common.TransactionLineId
import com.lubrcapp.lubrcapp.domain.common.UserBusinessId
import com.lubrcapp.lubrcapp.domain.common.UserId
import com.lubrcapp.lubrcapp.domain.payment.Payment
import com.lubrcapp.lubrcapp.domain.payment.PaymentDirection
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoice
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseInvoiceLine
import com.lubrcapp.lubrcapp.domain.purchase.PurchaseStatus
import com.lubrcapp.lubrcapp.domain.user.UserBusiness
import com.lubrcapp.lubrcapp.feature.accounts.AccountRecord
import com.lubrcapp.lubrcapp.feature.accounts.AccountsViewModel
import com.lubrcapp.lubrcapp.feature.accounts.CashDirection
import com.lubrcapp.lubrcapp.feature.accounts.CashPosting
import com.lubrcapp.lubrcapp.feature.accounts.CashPostingAccounts
import com.lubrcapp.lubrcapp.feature.inventory.InventoryViewModel
import com.lubrcapp.lubrcapp.feature.inventory.MovementType
import com.lubrcapp.lubrcapp.feature.inventory.StockMovementRecord
import com.lubrcapp.lubrcapp.feature.items.Brand
import com.lubrcapp.lubrcapp.feature.items.Item
import com.lubrcapp.lubrcapp.feature.items.ItemCategory
import com.lubrcapp.lubrcapp.feature.items.ItemUnit
import com.lubrcapp.lubrcapp.feature.items.ItemsViewModel
import com.lubrcapp.lubrcapp.feature.parties.PartiesViewModel
import com.lubrcapp.lubrcapp.feature.parties.Party
import com.lubrcapp.lubrcapp.feature.parties.PartyGroup
import com.lubrcapp.lubrcapp.feature.parties.PartyType
import com.lubrcapp.lubrcapp.feature.purchases.PurchasePaymentPostingAccounts
import com.lubrcapp.lubrcapp.feature.purchases.PurchasePostResult
import com.lubrcapp.lubrcapp.feature.purchases.PurchasesViewModel
import com.lubrcapp.lubrcapp.feature.sales.InvoicePostingAccounts
import com.lubrcapp.lubrcapp.feature.sales.PaymentPostingAccounts
import com.lubrcapp.lubrcapp.feature.sales.SalesDocument
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentLine
import com.lubrcapp.lubrcapp.feature.sales.SalesDocumentType
import com.lubrcapp.lubrcapp.feature.sales.SalesPostResult
import com.lubrcapp.lubrcapp.feature.sales.SalesViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase 32.3 — demo companies. Two fictional businesses, pre-loaded
 * so Umar can check every tab, and deletable as a unit afterwards.
 *
 * Every id is deterministic (`demo-<company>-<kind>-<n>`), so seeding
 * is idempotent: record saves upsert, postings overwrite their
 * journal entry, the sales handler skips stock movements it already
 * posted, and the money moves (receipt, supplier payment, cash) are
 * guarded by the state they produce. A re-run after an interrupted
 * seed therefore converges instead of duplicating.
 */
data class DemoCompanyDef(
    val tenantId: String,
    val businessId: String,
    val name: String,
    val businessType: String,
)

val DEMO_COMPANIES: List<DemoCompanyDef> = listOf(
    DemoCompanyDef(
        tenantId = "demo-tenant-store",
        businessId = "demo-business-store",
        name = "Demo General Store",
        businessType = "General Store",
    ),
    DemoCompanyDef(
        tenantId = "demo-tenant-pharmacy",
        businessId = "demo-business-pharmacy",
        name = "Demo Pharmacy Traders",
        businessType = "Pharmacy",
    ),
)

/** The feature ViewModels the seeder drives, supplied by the shell. */
data class DemoViewModels(
    val parties: PartiesViewModel,
    val items: ItemsViewModel,
    val inventory: InventoryViewModel,
    val sales: SalesViewModel,
    val purchases: PurchasesViewModel,
    val accounts: AccountsViewModel,
)

object DemoSeeder {
    private val pkr: CurrencyCode = CurrencyCode.of("PKR")
    private fun money(amount: String): Money = Money.of(BigDecimal(amount), pkr)
    private fun dec(amount: String): BigDecimal = BigDecimal(amount)

    suspend fun seedCompany(
        def: DemoCompanyDef,
        userId: UserId,
        deviceId: DeviceId,
        businessLocal: BusinessLocalDataSource,
        userLocal: UserLocalDataSource,
        settingsLocal: SettingsLocalDataSource,
        viewModels: DemoViewModels,
    ) {
        require(isDemoScopeId(def.tenantId) && isDemoScopeId(def.businessId)) {
            "DemoSeeder only seeds demo scopes"
        }
        val scope = OperationalScope(def.tenantId, def.businessId, deviceId.value)
        val now = Instant.now()
        val metadata = EntityMetadata.newLocal(now, deviceId, userId)
        val p = "demo-${def.businessId.removePrefix("demo-business-")}"

        businessLocal.saveTenant(
            Tenant(
                id = TenantId(def.tenantId),
                name = def.name,
                metadata = metadata,
            ),
        )
        businessLocal.saveBusiness(
            Business(
                id = BusinessId(def.businessId),
                tenantId = TenantId(def.tenantId),
                name = def.name,
                businessType = def.businessType,
                currency = pkr,
                timezone = ZoneId.of("Asia/Karachi"),
                metadata = metadata,
            ),
        )
        userLocal.saveMembership(
            UserBusiness(
                id = UserBusinessId("$p-membership"),
                tenantId = TenantId(def.tenantId),
                businessId = BusinessId(def.businessId),
                userId = userId,
                metadata = metadata,
            ),
        )
        settingsLocal.saveSetting(
            BusinessSetting(
                id = SettingId("$p-setting-owner"),
                tenantId = TenantId(def.tenantId),
                businessId = BusinessId(def.businessId),
                key = BusinessSetupSettingKeys.OWNER_USER_ID,
                value = userId.value,
                metadata = metadata,
            ),
        )

        seedAccounts(scope, viewModels, p)
        seedParties(scope, viewModels, p)
        seedItems(scope, viewModels, p)
        seedStock(scope, viewModels, p)
        seedSales(scope, viewModels, p)
        seedPurchases(scope, viewModels, p)
        seedCash(scope, viewModels, p)
    }

    private suspend fun seedAccounts(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        val accounts = listOf(
            AccountRecord(id = "$p-acct-cash", code = "1001", name = "Cash", type = AccountType.ASSET),
            AccountRecord(id = "$p-acct-receivable", code = "1101", name = "Receivables", type = AccountType.ASSET),
            AccountRecord(id = "$p-acct-payable", code = "2101", name = "Payables", type = AccountType.LIABILITY),
            AccountRecord(id = "$p-acct-revenue", code = "4001", name = "Sales Revenue", type = AccountType.INCOME),
            AccountRecord(id = "$p-acct-expense", code = "5001", name = "Purchase Expense", type = AccountType.EXPENSE),
            AccountRecord(id = "$p-acct-other-income", code = "4101", name = "Other Income", type = AccountType.OTHER_INCOME),
        )
        accounts.forEach { viewModels.accounts.saveAccount(scope, it) }
    }

    private suspend fun seedParties(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        viewModels.parties.saveGroup(
            scope,
            PartyGroup(id = "$p-group-regular", name = "Regular Customers"),
        )
        viewModels.parties.saveParty(
            scope,
            Party(
                id = "$p-cust-ali",
                name = "Ali Ahmed",
                type = PartyType.CUSTOMER,
                mobile = "0300-1234567",
                groupId = "$p-group-regular",
                openingBalance = dec("500"),
            ),
        )
        viewModels.parties.saveParty(
            scope,
            Party(
                id = "$p-cust-fatima",
                name = "Fatima Khan",
                type = PartyType.CUSTOMER,
                mobile = "0321-7654321",
            ),
        )
        viewModels.parties.saveParty(
            scope,
            Party(
                id = "$p-supp-united",
                name = "United Suppliers",
                type = PartyType.SUPPLIER,
                businessName = "United Suppliers (Pvt) Ltd",
                mobile = "0300-9999999",
            ),
        )
    }

    private suspend fun seedItems(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        viewModels.items.saveCategory(scope, ItemCategory(id = "$p-cat-grocery", name = "Grocery"))
        viewModels.items.saveCategory(scope, ItemCategory(id = "$p-cat-dairy", name = "Dairy"))
        viewModels.items.saveBrand(scope, Brand(id = "$p-brand-daily", name = "Daily Fresh"))
        viewModels.items.saveBrand(scope, Brand(id = "$p-brand-pure", name = "Pure Foods"))
        viewModels.items.saveUnit(scope, ItemUnit(id = "$p-unit-piece", name = "Piece"))
        val items = listOf(
            Item(
                id = "$p-item-rice", name = "Basmati Rice 5kg",
                categoryId = "$p-cat-grocery", brandId = "$p-brand-daily",
                unit = "Piece", unitId = "$p-unit-piece",
                purchaseRate = dec("1400"), saleRate = dec("1650"),
            ),
            Item(
                id = "$p-item-oil", name = "Cooking Oil 1L",
                categoryId = "$p-cat-grocery", brandId = "$p-brand-pure",
                unit = "Piece", unitId = "$p-unit-piece",
                purchaseRate = dec("520"), saleRate = dec("640"),
            ),
            Item(
                id = "$p-item-milk", name = "Fresh Milk 1L",
                categoryId = "$p-cat-dairy", brandId = "$p-brand-daily",
                unit = "Piece", unitId = "$p-unit-piece",
                purchaseRate = dec("180"), saleRate = dec("220"),
            ),
            Item(
                id = "$p-item-soap", name = "Bath Soap",
                categoryId = "$p-cat-grocery", brandId = "$p-brand-pure",
                unit = "Piece", unitId = "$p-unit-piece",
                purchaseRate = dec("90"), saleRate = dec("130"),
            ),
        )
        items.forEach { viewModels.items.saveItem(scope, it) }
    }

    private suspend fun seedStock(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        val opening = listOf(
            "$p-item-rice" to "50",
            "$p-item-oil" to "100",
            "$p-item-milk" to "40",
            "$p-item-soap" to "200",
        )
        opening.forEach { (itemId, qty) ->
            viewModels.inventory.saveMovement(
                scope,
                StockMovementRecord(
                    id = "$p-stock-$itemId",
                    itemId = itemId,
                    type = MovementType.OPENING,
                    quantity = dec(qty),
                    referenceType = "OPENING",
                    date = "2026-10-18",
                ),
            )
        }
    }

    private suspend fun seedSales(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        val order = SalesDocument(
            id = "$p-order-1",
            type = SalesDocumentType.SALES_ORDER,
            number = "SO-DEMO-1",
            partyId = "$p-cust-ali",
            date = "2026-10-26",
            status = "CONFIRMED",
        )
        viewModels.sales.saveDocumentWithLines(
            scope,
            order,
            listOf(
                SalesDocumentLine(
                    id = "$p-order-1-line-1", orderId = order.id, position = 0,
                    itemId = "$p-item-rice", description = "Basmati Rice 5kg",
                    quantity = dec("2"), rate = dec("1650"),
                ),
                SalesDocumentLine(
                    id = "$p-order-1-line-2", orderId = order.id, position = 1,
                    itemId = "$p-item-oil", description = "Cooking Oil 1L",
                    quantity = dec("3"), rate = dec("640"),
                ),
            ),
        )

        val invoice = SalesDocument(
            id = "$p-inv-1",
            type = SalesDocumentType.INVOICE,
            number = "INV-DEMO-1",
            partyId = "$p-cust-ali",
            date = "2026-10-18",
            dueDate = "2026-10-25",
            status = "SENT",
        )
        viewModels.sales.saveDocumentWithLines(
            scope,
            invoice,
            listOf(
                SalesDocumentLine(
                    id = "$p-inv-1-line-1", orderId = invoice.id, position = 0,
                    itemId = "$p-item-rice", description = "Basmati Rice 5kg",
                    quantity = dec("2"), rate = dec("1650"),
                ),
                SalesDocumentLine(
                    id = "$p-inv-1-line-2", orderId = invoice.id, position = 1,
                    itemId = "$p-item-milk", description = "Fresh Milk 1L",
                    quantity = dec("5"), rate = dec("220"),
                ),
            ),
        )
        val invoiceResult = SalesPostingHandler.postDocument(
            scope,
            invoice,
            InvoicePostingAccounts(
                receivableAccountId = "$p-acct-receivable",
                revenueAccountId = "$p-acct-revenue",
            ),
            pkr,
            viewModels.sales,
            viewModels.accounts,
            viewModels.inventory,
        )
        require(invoiceResult is SalesPostResult.Posted) { "Demo invoice did not post" }

        val salesReturn = SalesDocument(
            id = "$p-ret-1",
            type = SalesDocumentType.SALES_RETURN,
            number = "SR-DEMO-1",
            partyId = "$p-cust-ali",
            date = "2026-10-26",
            status = "CONFIRMED",
        )
        viewModels.sales.saveDocumentWithLines(
            scope,
            salesReturn,
            listOf(
                SalesDocumentLine(
                    id = "$p-ret-1-line-1", orderId = salesReturn.id, position = 0,
                    itemId = "$p-item-milk", description = "Fresh Milk 1L",
                    quantity = dec("1"), rate = dec("220"),
                ),
            ),
        )
        val returnResult = SalesPostingHandler.postDocument(
            scope,
            salesReturn,
            InvoicePostingAccounts(
                receivableAccountId = "$p-acct-receivable",
                revenueAccountId = "$p-acct-revenue",
            ),
            pkr,
            viewModels.sales,
            viewModels.accounts,
            viewModels.inventory,
        )
        require(returnResult is SalesPostResult.Posted) { "Demo return did not post" }

        val currentInvoice = viewModels.sales.loadDocuments(scope).firstOrNull { it.id == invoice.id }
        if (currentInvoice?.status == "SENT") {
            val receipt = Payment(
                id = PaymentId("$p-pay-rec-1"),
                tenantId = TenantId(scope.tenantId),
                businessId = BusinessId(scope.businessId),
                partyId = PartyId("$p-cust-ali"),
                direction = PaymentDirection.RECEIVED,
                methodId = PaymentMethodId("cash"),
                amount = money("2000"),
                relatedInvoiceId = InvoiceId(invoice.id),
                date = LocalDate.parse("2026-10-27"),
                metadata = EntityMetadata.newLocal(Instant.now(), DeviceId(scope.deviceId)),
            )
            viewModels.sales.applyReceipt(scope, receipt, pkr)
            val receiptResult = SalesPaymentHandler.postPayment(
                scope,
                receipt,
                "REC-DEMO-1",
                PaymentPostingAccounts(
                    cashAccountId = "$p-acct-cash",
                    receivableAccountId = "$p-acct-receivable",
                ),
                viewModels.accounts,
            )
            require(receiptResult is SalesPostResult.Posted) { "Demo receipt did not post" }
        }
    }

    private suspend fun seedPurchases(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        val purchaseId = "$p-po-1"
        viewModels.purchases.save(
            scope,
            purchaseId,
            buildJsonObject {
                put("description", "Purchase from United Suppliers — Demo stock")
                put("supplierId", "$p-supp-united")
                put("date", "2026-10-19")
                put("total", "24400")
            }.toString(),
        )
        val invoice = PurchaseInvoice(
            id = PurchaseId(purchaseId),
            tenantId = TenantId(scope.tenantId),
            businessId = BusinessId(scope.businessId),
            partyId = PartyId("$p-supp-united"),
            invoiceNumber = "PO-DEMO-1",
            date = LocalDate.parse("2026-10-19"),
            dueDate = LocalDate.parse("2026-11-02"),
            status = PurchaseStatus.RECEIVED,
            subtotal = money("24400"),
            taxTotal = money("0"),
            discount = money("0"),
            total = money("24400"),
            paidAmount = money("0"),
            metadata = EntityMetadata.newLocal(Instant.now(), DeviceId(scope.deviceId)),
        )
        val lines = listOf(
            PurchaseInvoiceLine(
                id = TransactionLineId("$purchaseId-line-1"),
                purchaseId = PurchaseId(purchaseId),
                itemId = ItemId("$p-item-rice"),
                description = "Basmati Rice 5kg",
                quantity = Quantity.of(dec("10")),
                unitPrice = money("1400"),
                taxRate = Percentage.ZERO,
                discount = money("0"),
                lineTotal = money("14000"),
            ),
            PurchaseInvoiceLine(
                id = TransactionLineId("$purchaseId-line-2"),
                purchaseId = PurchaseId(purchaseId),
                itemId = ItemId("$p-item-oil"),
                description = "Cooking Oil 1L",
                quantity = Quantity.of(dec("20")),
                unitPrice = money("520"),
                taxRate = Percentage.ZERO,
                discount = money("0"),
                lineTotal = money("10400"),
            ),
        )
        val posted = PurchasePostingHandler.postInvoice(
            scope,
            invoice,
            lines,
            PurchasePostingAccounts(
                payableAccountId = "$p-acct-payable",
                expenseAccountId = "$p-acct-expense",
            ),
            viewModels.purchases,
            viewModels.accounts,
        )
        require(posted is PurchasePostResult.Posted) { "Demo purchase did not post" }

        val payable = viewModels.purchases.loadPayableForPurchase(purchaseId)
        if (payable != null && payable.paidAmount < dec("10000")) {
            viewModels.purchases.applyPayment(scope, payable, dec("10000"))
            val payment = Payment(
                id = PaymentId("$p-pay-sup-1"),
                tenantId = TenantId(scope.tenantId),
                businessId = BusinessId(scope.businessId),
                partyId = PartyId("$p-supp-united"),
                direction = PaymentDirection.PAID,
                methodId = PaymentMethodId("cash"),
                amount = money("10000"),
                relatedInvoiceId = InvoiceId(purchaseId),
                date = LocalDate.parse("2026-10-28"),
                metadata = EntityMetadata.newLocal(Instant.now(), DeviceId(scope.deviceId)),
            )
            val paid = PurchasePostingHandler.postPayment(
                scope,
                payment,
                "SUP-PAY-DEMO-1",
                PurchasePaymentPostingAccounts(
                    cashAccountId = "$p-acct-cash",
                    payableAccountId = "$p-acct-payable",
                ),
                viewModels.accounts,
            )
            require(paid is PurchasePostResult.Posted) { "Demo supplier payment did not post" }
        }
    }

    private suspend fun seedCash(
        scope: OperationalScope,
        viewModels: DemoViewModels,
        p: String,
    ) {
        val postedDescriptions = viewModels.accounts.loadJournalEntries(scope).map { it.description }.toSet()
        if ("CASH CASH-IN-DEMO-1" !in postedDescriptions) {
            val result = viewModels.accounts.postDocument(
                scope = scope,
                posting = CashPosting.cashToPosting(
                    amount = money("5000"),
                    direction = CashDirection.IN,
                    accounts = CashPostingAccounts(
                        cashAccountId = "$p-acct-cash",
                        counterpartAccountId = "$p-acct-other-income",
                    ),
                    documentNumber = "CASH-IN-DEMO-1",
                ),
                date = LocalDate.parse("2026-10-28"),
                now = Instant.now(),
                deviceId = DeviceId(scope.deviceId),
            )
            require(result is AccountsViewModel.DocumentPostResult.Posted) { "Demo cash-in did not post" }
        }
        if ("CASH CASH-OUT-DEMO-1" !in postedDescriptions) {
            val result = viewModels.accounts.postDocument(
                scope = scope,
                posting = CashPosting.cashToPosting(
                    amount = money("1500"),
                    direction = CashDirection.OUT,
                    accounts = CashPostingAccounts(
                        cashAccountId = "$p-acct-cash",
                        counterpartAccountId = "$p-acct-expense",
                    ),
                    documentNumber = "CASH-OUT-DEMO-1",
                ),
                date = LocalDate.parse("2026-10-28"),
                now = Instant.now(),
                deviceId = DeviceId(scope.deviceId),
            )
            require(result is AccountsViewModel.DocumentPostResult.Posted) { "Demo cash-out did not post" }
        }
    }
}
