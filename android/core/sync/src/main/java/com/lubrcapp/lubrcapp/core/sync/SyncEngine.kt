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
    suspend fun applyRemoteAndSaveCursor(
        identity: SyncIdentity,
        changes: List<SyncChange>,
        nextCursor: String?,
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
        // Persist incoming changes and cursor before acknowledging outgoing changes.
        queue.applyRemoteAndSaveCursor(identity, response.remoteChanges, response.nextCursor)
        if (accepted.isNotEmpty()) queue.markAccepted(identity, accepted)
        if (retry.isNotEmpty()) queue.markRetry(identity, retry)
        return SyncRoundResult(accepted.size, response.remoteChanges.size, retry.size)
    }
}
