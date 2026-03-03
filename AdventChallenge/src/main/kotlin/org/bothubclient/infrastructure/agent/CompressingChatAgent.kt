package org.bothubclient.infrastructure.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.context.ContextComposer
import org.bothubclient.domain.context.SummaryGenerator
import org.bothubclient.domain.context.SummaryResult
import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.entity.*
import org.bothubclient.domain.memory.LongTermMemoryStore
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.domain.usecase.UserProfilePromptBuilder
import org.bothubclient.infrastructure.context.HeuristicFactsExtractor
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger
import org.bothubclient.infrastructure.memory.FileLongTermMemoryStore
import org.bothubclient.infrastructure.memory.LtmRecaller
import org.bothubclient.infrastructure.repository.UserProfileRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

sealed class CompressionEvent {
    data class Complete(val block: SummaryBlock) : CompressionEvent()
    data class Error(val exception: Exception) : CompressionEvent()
    object NotNeeded : CompressionEvent()
}

class CompressingChatAgent(
    private val delegate: ChatAgent,
    private val summaryGenerator: SummaryGenerator,
    private val summaryStorage: SummaryStorage,
    private val contextComposer: ContextComposer,
    private val factsExtractor: HeuristicFactsExtractor = HeuristicFactsExtractor(),
    private val ltmRecaller: LtmRecaller,
    private val longTermMemoryStore: LongTermMemoryStore = FileLongTermMemoryStore(),
    private val userProfileRepository: UserProfileRepository? = null,
    private val userProfilePromptBuilder: UserProfilePromptBuilder = UserProfilePromptBuilder(),
    initialConfig: ContextConfig = ContextConfig.DEFAULT
) : ChatAgent {

    private data class SessionState(
        var activeBranchId: String = "main",
        val branches: MutableMap<String, BranchState>
    )

    private val sessions = ConcurrentHashMap<String, SessionState>()

    private val _config = AtomicReference(initialConfig)
    val config: ContextConfig
        get() = _config.get()

    private val pendingCompressions = ConcurrentHashMap<String, Boolean>()

    private val _compressionEvents = MutableSharedFlow<CompressionEvent>(replay = 0)
    val compressionEvents: SharedFlow<CompressionEvent> = _compressionEvents.asSharedFlow()

    companion object {
        private const val TAG = "CompressingChatAgent"
    }

    private fun log(message: String) = FileLogger.log(TAG, message)
    private fun section(title: String) = FileLogger.section(title)

    private fun getSession(sessionId: String): SessionState =
        sessions.computeIfAbsent(sessionId) {
            val activeProfile = userProfileRepository?.getCachedActiveProfile()
            SessionState(
                branches = mutableMapOf("main" to BranchState(userProfile = activeProfile))
            )
        }

    private fun getBranch(sessionId: String, branchId: String): BranchState =
        getSession(sessionId).branches.getOrPut(branchId) {
            BranchState(userProfile = userProfileRepository?.getCachedActiveProfile())
        }

    private fun getActiveBranchState(sessionId: String): BranchState {
        val session = getSession(sessionId)
        return getBranch(sessionId, session.activeBranchId)
    }

    private fun currentBranchId(sessionId: String): String = getSession(sessionId).activeBranchId

    private fun contextKey(sessionId: String, branchId: String): String = "$sessionId::$branchId"

    private fun removeOldestFromBranch(
        sessionId: String,
        branchId: String,
        count: Int
    ): List<Message> {
        val branch = getBranch(sessionId, branchId)
        val removed = mutableListOf<Message>()
        synchronized(branch) {
            repeat(count) {
                if (branch.messages.isNotEmpty()) {
                    removed.add(branch.messages.removeAt(0))
                }
            }
        }
        return removed
    }

    override suspend fun getHistory(sessionId: String): List<Message> =
        getActiveBranchState(sessionId).messages.toList()

    override suspend fun getSessionMessages(sessionId: String): List<Message> =
        getActiveBranchState(sessionId).messages.toList()

    override fun getSessionTokenStatistics(
        sessionId: String,
        model: String
    ): SessionTokenStatistics = delegate.getSessionTokenStatistics(sessionId, model)

    override fun getTotalHistoryTokens(sessionId: String): Int =
        delegate.getTotalHistoryTokens(sessionId)

    override fun isApproachingContextLimit(
        sessionId: String,
        model: String,
        threshold: Float
    ): Boolean = delegate.isApproachingContextLimit(sessionId, model, threshold)

    override suspend fun reset(sessionId: String) {
        delegate.reset(sessionId)
        val session = sessions.remove(sessionId)
        session?.branches?.keys?.forEach { branchId ->
            summaryStorage.clear(contextKey(sessionId, branchId))
        }
        pendingCompressions.keys.removeIf { it.startsWith("$sessionId::") }
        log("Reset session with summaries: $sessionId")
    }

    override fun truncateHistory(sessionId: String, keepLast: Int) {
        val branch = getActiveBranchState(sessionId)
        synchronized(branch) {
            while (branch.messages.size > keepLast) {
                branch.messages.removeAt(0)
            }
        }
        val currentConfig = config
        val blocks = summaryStorage.getBlocks(contextKey(sessionId, currentBranchId(sessionId)))
        if (blocks.size > currentConfig.maxSummaryBlocks) {
            val toRemove = blocks.take(blocks.size - currentConfig.maxSummaryBlocks)
            val key = contextKey(sessionId, currentBranchId(sessionId))
            toRemove.forEach { summaryStorage.removeBlock(key, it.id) }
        }
    }

    override fun removeOldestMessages(sessionId: String, count: Int): List<Message> {
        val branch = getActiveBranchState(sessionId)
        val removed = mutableListOf<Message>()
        synchronized(branch) {
            repeat(count) {
                if (branch.messages.isNotEmpty()) {
                    removed.add(branch.messages.removeAt(0))
                }
            }
        }
        return removed
    }

    override suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        val currentConfig = config
        val session = getSession(sessionId)
        val branchId = session.activeBranchId
        val branch = getBranch(sessionId, branchId)
        val sessionKey = contextKey(sessionId, branchId)

        section("SEND START")
        log("sessionId=$sessionId, branchId=$branchId, model=$model")
        log("Config: $currentConfig")

        val cachedProfile = userProfileRepository?.getCachedActiveProfile()
        // Важно: профиль может быть null (режим "Без профиля") — в этом случае очищаем профиль
        // ветки,
        // иначе он "залипнет" со старого выбора.
        synchronized(branch) { branch.userProfile = cachedProfile }
        val profilePrompt =
            synchronized(branch) { branch.userProfile }
                ?.let { userProfilePromptBuilder.build(it) }
                .orEmpty()
        val systemPromptWithProfile =
            if (profilePrompt.isBlank()) {
                systemPrompt
            } else {
                systemPrompt + "\n\n" + profilePrompt
            }

        if (currentConfig.enableFactsMemory) {
            val existingWm = synchronized(branch) { snapshotWorkingMemory(branch) }
            val ops = factsExtractor.extractOperations(userMessage, existingWm)

            if (ops.isNotEmpty()) {
                val forgetOps =
                    synchronized(branch) {
                        applyExtractOperations(branch.workingMemory, ops)
                        trimWorkingMemory(branch.workingMemory, currentConfig.maxFacts)
                    }
                (ops + forgetOps).forEach { log("MemoryOp: $it") }
                val totalFacts = synchronized(branch) { totalFacts(branch.workingMemory) }
                log("Facts updated: ops=${ops.size}, totalFacts=$totalFacts")
            }
        }

        if (currentConfig.enableAutoCompression) {
            log("Auto-compression ENABLED, checking...")
            val compressionResult = tryCompressIfNeeded(sessionId, branchId, currentConfig)
            when (compressionResult) {
                is CompressionResult.Success -> {
                    log("SUCCESS Compression: block ${compressionResult.newBlock.id}")
                    persistImportantFactsFromWorkingMemory(branch, limit = 24)
                    _compressionEvents.emit(CompressionEvent.Complete(compressionResult.newBlock))
                }
                is CompressionResult.Partial -> {
                    log("PARTIAL Compression: ${compressionResult.warning}")
                    persistImportantFactsFromWorkingMemory(branch, limit = 24)
                    _compressionEvents.emit(CompressionEvent.Complete(compressionResult.newBlock))
                }
                is CompressionResult.Failed -> {
                    log("FAILED Compression: ${compressionResult.error.message}")
                    _compressionEvents.emit(CompressionEvent.Error(compressionResult.error))
                }
                CompressionResult.NotNeeded -> {
                    log("Compression NOT NEEDED")
                }
            }
        } else {
            log("Auto-compression DISABLED")
        }

        val historySnapshot = synchronized(branch) { branch.messages.toList() }
        val factsSnapshot = synchronized(branch) { snapshotWorkingMemory(branch) }
        val factsCount = factsSnapshot.values.sumOf { it.size }
        log(
            "Current messages count: ${historySnapshot.size}, facts=$factsCount (groups=${factsSnapshot.size})"
        )

        val ltmSnapshot = longTermMemoryStore.snapshot()
        val ltmRecalled = ltmRecaller.recall(userMessage, ltmSnapshot, model)
        val ltmFiltered =
            ltmRecalled.filterNot { item ->
                factsSnapshot[item.category]?.get(item.key)?.value == item.entry.value
            }
        val ltmText =
            if (ltmFiltered.isEmpty()) {
                ""
            } else {
                buildString {
                    append("[LONG_TERM_MEMORY]\n")
                    ltmFiltered.groupBy { it.category }.toSortedMap().forEach { (cat, items) ->
                        append("[${cat.name}]\n")
                        items.sortedBy { it.key }.forEach { item ->
                            append(
                                "${item.key}: ${item.entry.value} (confidence=${"%.2f".format(item.entry.confidence)}, useCount=${item.entry.useCount})\n"
                            )
                        }
                    }
                    append("[END LONG_TERM_MEMORY]\n")
                }
            }
        if (ltmFiltered.isNotEmpty()) {
            ltmFiltered.forEach { item ->
                log(
                    "MemoryOp: " +
                            MemoryOperation(
                                op = "RECALL",
                                fromLayer = MemoryLayer.LTM,
                                toLayer = MemoryLayer.WM,
                                category = item.category,
                                key = item.key,
                                value = item.entry.value,
                                confidence = item.entry.confidence
                            )
                )
            }
        }
        val systemPromptWithLtm =
            if (ltmText.isBlank()) {
                systemPromptWithProfile
            } else {
                systemPromptWithProfile + "\n\n" + ltmText
            }

        val composedContext =
            contextComposer.compose(
                sessionId = sessionKey,
                systemPrompt = systemPromptWithLtm,
                userMessage = userMessage,
                historyMessages = historySnapshot,
                facts = factsSnapshot,
                config = config
            )

        log(
            "Composed: ${composedContext.summaryBlocks.size} summaries + ${composedContext.recentMessages.size} recent = ~${composedContext.totalEstimatedTokens} tokens"
        )

        val enhancedSystemPrompt = composedContext.buildSystemPromptWithContext()

        if (composedContext.summaryBlocks.isNotEmpty()) {
            log("Enhanced prompt length: ${enhancedSystemPrompt.length}")
            log("=== FULL SYSTEM PROMPT WITH CONTEXT ===")
            log(enhancedSystemPrompt.take(500))
            if (enhancedSystemPrompt.length > 500)
                log("... (truncated, total ${enhancedSystemPrompt.length} chars)")
            log("=== END SYSTEM PROMPT ===")
        }

        section("SEND END")

        val result =
            delegate.sendWithContext(
                sessionId = sessionId,
                contextMessages = composedContext.recentMessages,
                userMessage = userMessage,
                model = model,
                systemPrompt = enhancedSystemPrompt,
                temperature = temperature
            )

        if (result is ChatResult.Success) {
            val stmOps = mutableListOf<MemoryOperation>()
            val removed = mutableListOf<Message>()
            synchronized(branch) {
                branch.messages.add(Message.user(userMessage))
                branch.messages.add(result.message)
                stmOps +=
                    MemoryOperation(
                        op = "OBSERVE",
                        fromLayer = null,
                        toLayer = MemoryLayer.STM,
                        category = null,
                        key = "user_message",
                        value = userMessage,
                        confidence = 1.0f
                    )
                stmOps +=
                    MemoryOperation(
                        op = "OBSERVE",
                        fromLayer = null,
                        toLayer = MemoryLayer.STM,
                        category = null,
                        key = "assistant_message",
                        value = result.message.content,
                        confidence = 1.0f
                    )
                if (currentConfig.strategy == ContextStrategy.SLIDING_WINDOW) {
                    while (branch.messages.size > currentConfig.keepLastN) {
                        removed += branch.messages.removeAt(0)
                    }
                }
            }
            stmOps.forEach { log("MemoryOp: $it") }
            removed.forEach { msg ->
                log(
                    "MemoryOp: " +
                            MemoryOperation(
                                op = "FORGET",
                                fromLayer = MemoryLayer.STM,
                                toLayer = null,
                                category = null,
                                key = msg.role.name,
                                value = msg.content,
                                confidence = 1.0f
                            )
                )
            }
        }

        return result
    }

    private suspend fun tryCompressIfNeeded(
        sessionId: String,
        branchId: String,
        currentConfig: ContextConfig
    ): CompressionResult {
        log("tryCompressIfNeeded: checking pending...")
        val lockKey = contextKey(sessionId, branchId)
        if (pendingCompressions.putIfAbsent(lockKey, true) != null) {
            log("Compression already in progress for session $sessionId branch=$branchId")
            return CompressionResult.NotNeeded
        }

        try {
            val branch = getBranch(sessionId, branchId)
            val history = synchronized(branch) { branch.messages.toList() }
            log(
                "History size: ${history.size}, threshold: (historySize - keepLastN=${currentConfig.keepLastN}) >= blockSize=${currentConfig.compressionBlockSize}"
            )

            val shouldCompress = currentConfig.shouldCompress(history.size)
            log("shouldCompress result: $shouldCompress")

            if (!shouldCompress) {
                return CompressionResult.NotNeeded
            }

            log(
                "Compression condition MET! Taking ${currentConfig.compressionBlockSize} oldest messages..."
            )
            val messagesToCompress = history.take(currentConfig.compressionBlockSize)
            if (messagesToCompress.isEmpty()) {
                log("No messages to compress")
                return CompressionResult.NotNeeded
            }

            log("Messages to compress: ${messagesToCompress.size} messages")

            log("Calling LLM to generate summary (maxTokens=${currentConfig.summaryMaxTokens})...")
            val summaryResult =
                summaryGenerator.generateSummary(
                    messages = messagesToCompress,
                    maxTokens = currentConfig.summaryMaxTokens
                )

            return when (summaryResult) {
                is SummaryResult.Success -> {
                    log("Summary generated (${summaryResult.block.estimatedTokens} tokens)")

                    log(
                        "Removing ${currentConfig.compressionBlockSize} oldest messages from history..."
                    )
                    val removedMessages =
                        removeOldestFromBranch(
                            sessionId,
                            branchId,
                            currentConfig.compressionBlockSize
                        )
                    log("Actually removed ${removedMessages.size} messages")

                    if (removedMessages.size < currentConfig.compressionBlockSize) {
                        AppLogger.w(
                            TAG,
                            "Only removed ${removedMessages.size} messages instead of ${currentConfig.compressionBlockSize}"
                        )
                    }

                    summaryStorage.addBlock(contextKey(sessionId, branchId), summaryResult.block)
                    log("Summary block added to storage")

                    val blocks = summaryStorage.getBlocks(contextKey(sessionId, branchId))
                    log(
                        "Total summary blocks: ${blocks.size} (max: ${currentConfig.maxSummaryBlocks})"
                    )

                    if (blocks.size > currentConfig.maxSummaryBlocks) {
                        val removedBlock =
                            summaryStorage.removeBlock(
                                contextKey(sessionId, branchId),
                                blocks.first().id
                            )
                        log("Removed oldest summary block: ${removedBlock?.id}")
                    }

                    CompressionResult.Success(summaryResult.block)
                }
                is SummaryResult.Error -> {
                    log("Summary generation ERROR: ${summaryResult.exception.message}")
                    CompressionResult.Failed(summaryResult.exception, messagesToCompress)
                }
            }
        } finally {
            pendingCompressions.remove(lockKey)
            log("Compression lock released for session $sessionId branch=$branchId")
        }
    }

    fun updateConfig(newConfig: ContextConfig) {
        _config.set(newConfig)
        log("Config updated: $newConfig")
    }

    fun getSummaryBlocks(sessionId: String): List<SummaryBlock> =
        summaryStorage.getBlocks(contextKey(sessionId, currentBranchId(sessionId)))

    fun getFacts(sessionId: String): Map<String, Map<String, String>> {
        val branch = getActiveBranchState(sessionId)
        val wm = synchronized(branch) { snapshotWorkingMemory(branch) }
        val out = LinkedHashMap<String, Map<String, String>>()
        wm.forEach { (category, group) ->
            out[category.name] = group.mapValues { (_, v) -> v.value }
        }
        return out
    }

    fun getWorkingMemory(sessionId: String): Map<WmCategory, Map<String, FactEntry>> {
        val branch = getActiveBranchState(sessionId)
        return synchronized(branch) { snapshotWorkingMemory(branch) }
    }

    fun setWorkingMemoryEntry(
        sessionId: String,
        category: WmCategory,
        key: String,
        value: String,
        confidence: Float = 1.0f
    ) {
        val k = key.trim()
        val v = value.trim()
        if (k.isBlank() || v.isBlank()) return
        val branch = getActiveBranchState(sessionId)
        val now = System.currentTimeMillis()
        synchronized(branch) {
            val group =
                branch.workingMemory.getOrPut(category) { LinkedHashMap<String, FactEntry>() }
            val prev = group[k]
            group[k] =
                if (prev == null) {
                    FactEntry(
                        value = v,
                        confidence = confidence,
                        timestamp = now,
                        source = "manual",
                        useCount = 0,
                        lastUsed = now
                    )
                } else {
                    prev.copy(
                        value = v,
                        confidence = confidence,
                        timestamp = now,
                        source = "manual",
                        lastUsed = now
                    )
                }
            if (group.isEmpty()) branch.workingMemory.remove(category)
        }
    }

    fun deleteWorkingMemoryEntry(sessionId: String, category: WmCategory, key: String): Boolean {
        val k = key.trim()
        if (k.isBlank()) return false
        val branch = getActiveBranchState(sessionId)
        var removed = false
        synchronized(branch) {
            val group = branch.workingMemory[category] ?: return@synchronized
            removed = group.remove(k) != null
            if (group.isEmpty()) branch.workingMemory.remove(category)
        }
        return removed
    }

    fun getStmCount(sessionId: String): Int {
        val branch = getActiveBranchState(sessionId)
        return synchronized(branch) { branch.messages.size }
    }

    fun getShortTermMessages(sessionId: String): List<Message> {
        val branch = getActiveBranchState(sessionId)
        return synchronized(branch) { branch.messages.toList() }
    }

    fun clearShortTermMemory(sessionId: String): Int {
        val branch = getActiveBranchState(sessionId)
        var n = 0
        synchronized(branch) {
            n = branch.messages.size
            branch.messages.clear()
        }
        return n
    }

    suspend fun getLongTermMemorySnapshot(): List<MemoryItem> = longTermMemoryStore.snapshot()

    suspend fun saveToLtm(
        category: WmCategory,
        key: String,
        value: String,
        confidence: Float = 1.0f
    ): Boolean {
        val k = key.trim()
        val v = value.trim()
        if (k.isBlank() || v.isBlank()) return false
        val item =
            org.bothubclient.domain.memory.MemoryItem(
                category = category,
                key = k,
                entry =
                    FactEntry(
                        value = v,
                        confidence = confidence,
                        timestamp = System.currentTimeMillis(),
                        source = "manual",
                        useCount = 0,
                        lastUsed = System.currentTimeMillis(),
                        ttl = null
                    )
            )
        return longTermMemoryStore.upsert(item)
    }

    suspend fun deleteFromLtmByKey(key: String): Int {
        val k = key.trim()
        if (k.isBlank()) return 0
        return longTermMemoryStore.deleteWhere { it.key == k }
    }

    suspend fun findInLtm(query: String, limit: Int = 10): List<MemoryItem> =
        longTermMemoryStore.search(query, limit)

    fun getBranchIds(sessionId: String): List<String> = getSession(sessionId).branches.keys.sorted()

    fun getActiveBranchId(sessionId: String): String = currentBranchId(sessionId)

    fun setActiveBranch(sessionId: String, branchId: String) {
        val session = getSession(sessionId)
        if (!session.branches.containsKey(branchId)) return
        session.activeBranchId = branchId
        log("Active branch updated: sessionId=$sessionId -> $branchId")
    }

    fun createBranchFromCheckpoint(sessionId: String, checkpointSize: Int): String {
        val session = getSession(sessionId)
        val sourceBranchId = session.activeBranchId
        val sourceBranch = getBranch(sessionId, sourceBranchId)
        val sourceMessages = synchronized(sourceBranch) { sourceBranch.messages.toList() }
        val sourceProfile = synchronized(sourceBranch) { sourceBranch.userProfile }
        val size = checkpointSize.coerceIn(0, sourceMessages.size)
        val forkMessages = sourceMessages.take(size)

        val forkWm: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>> = LinkedHashMap()
        if (config.enableFactsMemory) {
            forkMessages.filter { it.role == MessageRole.USER }.forEach { m ->
                val snapshot = snapshotWorkingMemory(forkWm)
                val ops = factsExtractor.extractOperationsHeuristic(m.content, snapshot)
                if (ops.isNotEmpty()) {
                    applyExtractOperations(forkWm, ops)
                    trimWorkingMemory(forkWm, config.maxFacts)
                }
            }
        }

        val base = "branch"
        var i = 1
        var id = "$base-$i"
        while (session.branches.containsKey(id)) {
            i++
            id = "$base-$i"
        }

        session.branches[id] =
            BranchState(
                messages = forkMessages.toMutableList(),
                workingMemory = forkWm,
                userProfile = sourceProfile
            )
        log(
            "Branch created: sessionId=$sessionId source=$sourceBranchId checkpointSize=$size -> $id"
        )
        return id
    }

    fun getComposedContext(
        sessionId: String,
        systemPrompt: String,
        userMessage: String
    ): ComposedContext {
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        val cachedProfile = userProfileRepository?.getCachedActiveProfile()
        synchronized(branch) { branch.userProfile = cachedProfile }
        val profilePrompt =
            synchronized(branch) { branch.userProfile }
                ?.let { userProfilePromptBuilder.build(it) }
                .orEmpty()
        val systemPromptWithProfile =
            if (profilePrompt.isBlank()) systemPrompt else systemPrompt + "\n\n" + profilePrompt
        val historySnapshot = synchronized(branch) { branch.messages.toList() }
        val factsSnapshot = synchronized(branch) { snapshotWorkingMemory(branch) }
        return contextComposer.compose(
            sessionId = contextKey(sessionId, branchId),
            systemPrompt = systemPromptWithProfile,
            userMessage = userMessage,
            historyMessages = historySnapshot,
            facts = factsSnapshot,
            config = config
        )
    }

    private fun snapshotWorkingMemory(
        branch: BranchState
    ): Map<WmCategory, Map<String, FactEntry>> {
        return snapshotWorkingMemory(branch.workingMemory)
    }

    private fun snapshotWorkingMemory(
        workingMemory: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>
    ): Map<WmCategory, Map<String, FactEntry>> {
        val out = LinkedHashMap<WmCategory, Map<String, FactEntry>>()
        workingMemory.forEach { (category, group) ->
            out[category] = group.mapValues { (_, entry) -> entry.copy() }
        }
        return out
    }

    private fun applyExtractOperations(
        workingMemory: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>,
        ops: List<MemoryOperation>
    ) {
        val now = System.currentTimeMillis()
        ops.forEach { op ->
            if (op.op != "EXTRACT") return@forEach
            val category = op.category ?: return@forEach
            val key = op.key.trim()
            val value = op.value?.trim().orEmpty()
            if (key.isBlank() || value.isBlank()) return@forEach

            val group = workingMemory.getOrPut(category) { LinkedHashMap() }
            val prev = group[key]
            group[key] =
                if (prev == null) {
                    FactEntry(
                        value = value,
                        confidence = op.confidence,
                        timestamp = now,
                        source = "extract",
                        useCount = 0,
                        lastUsed = now
                    )
                } else {
                    prev.copy(
                        value = value,
                        confidence = op.confidence,
                        timestamp = now,
                        source = "extract",
                        lastUsed = now,
                        useCount = prev.useCount
                    )
                }
        }
        workingMemory.entries.removeIf { it.value.isEmpty() }
    }

    private suspend fun persistImportantFactsFromWorkingMemory(branch: BranchState, limit: Int) {
        val snapshot = synchronized(branch) { snapshotWorkingMemory(branch) }
        val candidates =
            snapshot.entries
                .flatMap { (category, group) ->
                    group.entries.map { (key, entry) -> MemoryItem(category, key, entry) }
                }
                .filter { it.entry.value.isNotBlank() && it.key.isNotBlank() }
                .sortedByDescending {
                    it.entry.confidence.toDouble() * (it.entry.useCount.toDouble() + 1.0)
                }
                .take(limit.coerceIn(1, 100))

        candidates.forEach { item ->
            val ok = longTermMemoryStore.upsert(item)
            if (ok) {
                log(
                    "MemoryOp: " +
                            MemoryOperation(
                                op = "PERSIST",
                                fromLayer = MemoryLayer.WM,
                                toLayer = MemoryLayer.LTM,
                                category = item.category,
                                key = item.key,
                                value = item.entry.value,
                                confidence = item.entry.confidence
                            )
                )
            }
        }
    }

    private fun totalFacts(
        groups: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>
    ): Int = groups.values.sumOf { it.size }

    private fun trimWorkingMemory(
        groups: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>>,
        maxFacts: Int
    ): List<MemoryOperation> {
        val removed = mutableListOf<MemoryOperation>()
        while (totalFacts(groups) > maxFacts) {
            var victimCategory: WmCategory? = null
            var victimKey: String? = null
            var victimScore = Double.POSITIVE_INFINITY
            var victimTimestamp = Long.MAX_VALUE
            var victimEntry: FactEntry? = null

            groups.forEach { (category, group) ->
                group.forEach { (key, entry) ->
                    val score = (entry.confidence.toDouble()) * (entry.useCount.toDouble() + 1.0)
                    if (score < victimScore ||
                        (score == victimScore && entry.timestamp < victimTimestamp)
                    ) {
                        victimScore = score
                        victimTimestamp = entry.timestamp
                        victimCategory = category
                        victimKey = key
                        victimEntry = entry
                    }
                }
            }

            val cat = victimCategory ?: break
            val key = victimKey ?: break
            val group = groups[cat] ?: break
            group.remove(key)
            victimEntry?.let { entry ->
                removed +=
                    MemoryOperation(
                        op = "FORGET",
                        fromLayer = MemoryLayer.WM,
                        toLayer = null,
                        category = cat,
                        key = key,
                        value = entry.value,
                        confidence = entry.confidence
                    )
            }
            if (group.isEmpty()) groups.remove(cat)
        }
        return removed
    }
}
