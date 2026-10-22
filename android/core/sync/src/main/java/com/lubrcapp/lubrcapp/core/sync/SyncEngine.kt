package com.lubrcapp.lubrcapp.core.sync

/**
 * Persistent-queue sync coordinator. The caller owns durable storage and the authenticated
 * transport; neither a failed upload nor a rejected change is silently discarded.
 */
interface SyncQueueStore {
    suspend fun pending(identity: SyncIdentity, limit: Int): List<SyncChange>
    suspend fun markAccepted(identity: SyncIdentity, operationIds: Set<String>)
    suspend fun markRetry(identity: SyncIdentity, operationIds: Set<String>)
    suspend fun cursor(identity: SyncIdentity): String?

    /**
     * Atomically persist incoming changes and the advanced cursor.
     *
     * [acknowledgedOperationIds] carries the operation IDs the server
     * accepted in the SAME exchange that produced [changes]. The pull in
     * that exchange can echo a just-pushed change back (the VPS pull has
     * no origin-device exclusion), and at apply time the accepted
     * operation is still marked PENDING locally — the engine persists
     * incoming state before acknowledging outgoing rows. Such operations
     * are no longer unsynchronized local edits, so stores must not let
     * their own conflict guard refuse the incoming change for those
     * entities; the change is applied normally (it is either our own echo
     * or a newer server state, both safe once the server accepted our
     * operation). Operations NOT acknowledged in this response keep the
     * guard exactly as before. Callers that apply outside an exchange
     * (tests seeding state) omit the set and get the unmodified guard.
     */
    suspend fun applyRemoteAndSaveCursor(
        identity: SyncIdentity,
        changes: List<SyncChange>,
        nextCursor: String?,
        acknowledgedOperationIds: Set<String> = emptySet(),
    )
}

interface SyncTransport {
    suspend fun exchange(batch: SyncBatch): SyncResponse
}

data class SyncRoundResult(
    val uploaded: Int,
    val downloaded: Int,
    val retryable: Int,
)

/** No transport invocation is permitted in LOCAL_ONLY mode. */
class SyncEngine(
    private val queue: SyncQueueStore,
    private val transport: SyncTransport,
) {
    suspend fun run(mode: StorageMode, identity: SyncIdentity): SyncRoundResult {
        if (!SyncContract.canTransfer(mode, identity)) return SyncRoundResult(0, 0, 0)
        val changes = queue.pending(identity, 200)
        val batch = SyncBatch(identity, changes, queue.cursor(identity))
        SyncContract.validateBatch(batch)
        val response = try {
            transport.exchange(batch)
        } catch (failure: java.io.IOException) {
            // Preserve durable operations for the next WorkManager retry.
            if (changes.isNotEmpty()) queue.markRetry(identity, changes.map { it.operationId }.toSet())
            throw failure
        }
        val sentIds = changes.map { it.operationId }.toSet()
        require(response.receipts.all { it.operationId in sentIds }) {
            "Unexpected sync receipt operation ID"
        }
        val accepted = response.receipts.filter {
            it.outcome == SyncOutcome.ACCEPTED || it.outcome == SyncOutcome.DUPLICATE
        }.map { it.operationId }.toSet()
        val retry = sentIds - accepted
        // Persist incoming changes and cursor before acknowledging outgoing
        // changes. The ordering is unchanged: if the process dies between
        // the apply and markAccepted, the still-pending operation is
        // re-presented with its exact ID next round and settles on the
        // server's DUPLICATE receipt. The accepted set is passed only so
        // the store's conflict guard does not mistake this exchange's own
        // pull echo for an unsynchronized local edit (12C-05).
        queue.applyRemoteAndSaveCursor(identity, response.remoteChanges, response.nextCursor, accepted)
        if (accepted.isNotEmpty()) queue.markAccepted(identity, accepted)
        if (retry.isNotEmpty()) queue.markRetry(identity, retry)
        return SyncRoundResult(accepted.size, response.remoteChanges.size, retry.size)
    }
}
