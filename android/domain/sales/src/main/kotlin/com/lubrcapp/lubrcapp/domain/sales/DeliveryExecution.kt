package com.lubrcapp.lubrcapp.domain.sales

/**
 * Phase 27.5 — delivery execution (§161 Today's
 * Deliveries, §162 Delivery Proof, §163 Failed /
 * Partial Delivery). Pure model: the outcome recorded
 * against a challan once the delivery user acts.
 * Persistence is a later package decision, like 27.4.
 *
 * §161 actions are DELIVERED / FAILED; §163 adds the
 * partial case as a reason, so the outcome is three
 * states: DELIVERED, FAILED, PARTIAL. Reasons are
 * exactly the §163 list: Partial Delivery, Customer
 * Rejected, Shop Closed, Incorrect Order, Other.
 * A reason is REQUIRED for FAILED and PARTIAL and
 * FORBIDDEN for DELIVERED — a delivered challan with
 * a failure reason is contradictory data, so the ops
 * reject it rather than storing it.
 *
 * §162 proof is OPTIONAL: Customer Signature, Photo,
 * Receiver Name, Delivery Note. Signature and photo
 * are URI references only (Phase 15 precedent — no
 * rendering, no bitmap handling here); the screen
 * captures them and stores the URIs.
 *
 * The "today's deliveries" list itself is a screen
 * concern (27.7): schedules/challans filtered by date
 * at display time, not a stored list.
 */
enum class DeliveryOutcome {
    DELIVERED,
    FAILED,
    PARTIAL,
}

enum class DeliveryFailureReason {
    PARTIAL_DELIVERY,
    CUSTOMER_REJECTED,
    SHOP_CLOSED,
    INCORRECT_ORDER,
    OTHER,
}

data class DeliveryProof(
    val receiverName: String = "",
    val signatureUri: String = "",
    val photoUri: String = "",
    val deliveryNote: String = "",
)

data class DeliveryExecution(
    val challanId: String,
    val outcome: DeliveryOutcome,
    val reason: DeliveryFailureReason? = null,
    val proof: DeliveryProof = DeliveryProof(),
)

object DeliveryExecutionOps {
    fun delivered(challanId: String, proof: DeliveryProof = DeliveryProof()): DeliveryExecution =
        DeliveryExecution(challanId = challanId, outcome = DeliveryOutcome.DELIVERED, proof = proof)

    fun failed(
        challanId: String,
        reason: DeliveryFailureReason,
        note: String = "",
    ): DeliveryExecution = DeliveryExecution(
        challanId = challanId,
        outcome = DeliveryOutcome.FAILED,
        reason = reason,
        proof = DeliveryProof(deliveryNote = note),
    )

    fun partial(
        challanId: String,
        reason: DeliveryFailureReason,
        note: String = "",
    ): DeliveryExecution = DeliveryExecution(
        challanId = challanId,
        outcome = DeliveryOutcome.PARTIAL,
        reason = reason,
        proof = DeliveryProof(deliveryNote = note),
    )

    fun validate(execution: DeliveryExecution) {
        when (execution.outcome) {
            DeliveryOutcome.DELIVERED -> require(execution.reason == null) {
                "Delivered challan cannot carry a failure reason: ${execution.challanId}"
            }
            DeliveryOutcome.FAILED,
            DeliveryOutcome.PARTIAL -> require(execution.reason != null) {
                "Failed or partial delivery needs a reason: ${execution.challanId}"
            }
        }
    }
}
