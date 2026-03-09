package org.bothubclient.infrastructure.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.bothubclient.application.mcp.McpContextOrchestrator
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.agent.ChatAgentIntrospection
import org.bothubclient.domain.context.ContextComposer
import org.bothubclient.domain.context.SummaryGenerator
import org.bothubclient.domain.context.SummaryResult
import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.entity.*
import org.bothubclient.domain.logging.Logger
import org.bothubclient.domain.memory.LongTermMemoryStore
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.domain.repository.ChatHistoryStorage
import org.bothubclient.domain.repository.TaskContextStorage
import org.bothubclient.domain.statemachine.TaskStateMachine
import org.bothubclient.domain.usecase.UserProfilePromptBuilder
import org.bothubclient.infrastructure.context.HeuristicFactsExtractor
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger
import org.bothubclient.infrastructure.memory.FileLongTermMemoryStore
import org.bothubclient.infrastructure.memory.LtmRecaller
import org.bothubclient.infrastructure.persistence.FileChatHistoryStorage
import org.bothubclient.infrastructure.persistence.FileTaskContextStorage
import org.bothubclient.infrastructure.repository.UserProfileRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
    private val taskContextStorage: TaskContextStorage = FileTaskContextStorage(),
    private val chatHistoryStorage: ChatHistoryStorage = FileChatHistoryStorage(),
    private val mcpContextOrchestrator: McpContextOrchestrator? = null,
    initialConfig: ContextConfig = ContextConfig.DEFAULT
) : ChatAgent, ChatAgentIntrospection {
    private data class SessionMetrics(
        val compressionAttempts: AtomicLong = AtomicLong(0),
        val compressionSuccesses: AtomicLong = AtomicLong(0),
        val compressionFailures: AtomicLong = AtomicLong(0),
        val recallCandidates: AtomicLong = AtomicLong(0),
        val recallHits: AtomicLong = AtomicLong(0),
        val recallDuplicatesFiltered: AtomicLong = AtomicLong(0)
    )

    private data class SessionState(
        val createdAt: Long,
        var lastAccessedAt: Long,
        var activeBranchId: String = "main",
        val branches: MutableMap<String, BranchState>,
        val metrics: SessionMetrics = SessionMetrics()
    )

    private val sessions = ConcurrentHashMap<String, SessionState>()
    private val loadedHistories = ConcurrentHashMap<String, Boolean>()
    private val dirtyHistories = ConcurrentHashMap<String, Boolean>()

    private val _config = AtomicReference(initialConfig)
    val config: ContextConfig
        get() = _config.get()

    private val pendingCompressions = ConcurrentHashMap<String, Boolean>()

    private val _compressionEvents = MutableSharedFlow<CompressionEvent>(replay = 0)
    val compressionEvents: SharedFlow<CompressionEvent> = _compressionEvents.asSharedFlow()

    companion object {
        private const val TAG = "CompressingChatAgent"
        private const val MAX_SESSIONS = 64
        private const val SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val PROTECTED_SESSION_ID = "chat-ui"
    }

    private fun log(message: String) = FileLogger.log(TAG, message)
    private fun section(title: String) = FileLogger.section(title)
    private val fsmLogger: Logger =
        object : Logger {
            override fun log(tag: String, message: String) = FileLogger.log(tag, message)
        }

    private fun getSession(sessionId: String): SessionState {
        val now = System.currentTimeMillis()
        val session =
            sessions.computeIfAbsent(sessionId) {
                val activeProfile = userProfileRepository?.getCachedActiveProfile()
                SessionState(
                    createdAt = now,
                    lastAccessedAt = now,
                    branches =
                        mutableMapOf("main" to BranchState(userProfile = activeProfile))
                )
            }
        synchronized(session) { session.lastAccessedAt = now }
        return session
    }

    private suspend fun evictSessionsIfNeeded() {
        val now = System.currentTimeMillis()
        val snapshot =
            sessions.entries.mapNotNull { (id, s) ->
                val last = synchronized(s) { s.lastAccessedAt }
                Triple(id, s, last)
            }
        if (snapshot.size <= MAX_SESSIONS) {
            val ttlExpired =
                snapshot.any { (id, _, last) ->
                    id != PROTECTED_SESSION_ID && (now - last) > SESSION_TTL_MS
                }
            if (!ttlExpired) return
        }

        val ttlEvict =
            snapshot
                .filter { (id, _, last) ->
                    id != PROTECTED_SESSION_ID && (now - last) > SESSION_TTL_MS
                }
                .map { it.first }

        val lruEvict =
            if (snapshot.size <= MAX_SESSIONS) {
                emptyList()
            } else {
                val over = snapshot.size - MAX_SESSIONS
                snapshot
                    .filter { (id, _, _) -> id != PROTECTED_SESSION_ID }
                    .sortedBy { it.third }
                    .take(over)
                    .map { it.first }
            }

        val toEvict = (ttlEvict + lruEvict).distinct()
        if (toEvict.isEmpty()) return

        toEvict.forEach { id ->
            val removed = sessions.remove(id) ?: return@forEach
            removed.branches.forEach { (branchId, branch) ->
                val key = contextKey(id, branchId)
                val wasDirty = dirtyHistories.remove(key) != null
                if (wasDirty) {
                    val messages = synchronized(branch) { branch.messages.toList() }
                    runCatching { chatHistoryStorage.saveHistory(key, messages) }.onFailure { e ->
                        AppLogger.e(
                            TAG,
                            "Failed to persist history for evicted sessionId=$id branchId=$branchId",
                            e
                        )
                    }
                }
                loadedHistories.remove(key)
                summaryStorage.clear(key)
            }
            pendingCompressions.keys.removeIf { it.startsWith("$id::") }
        }
    }

    private fun getBranch(sessionId: String, branchId: String): BranchState =
        getSession(sessionId).let { session ->
            synchronized(session) {
                session.branches.getOrPut(branchId) {
                    BranchState(userProfile = userProfileRepository?.getCachedActiveProfile())
                }
            }
        }

    private fun getActiveBranchState(sessionId: String): BranchState {
        val session = getSession(sessionId)
        val branchId = synchronized(session) { session.activeBranchId }
        return getBranch(sessionId, branchId)
    }

    private fun currentBranchId(sessionId: String): String =
        getSession(sessionId).let { session ->
            synchronized(session) { session.activeBranchId }
        }

    private fun contextKey(sessionId: String, branchId: String): String = "$sessionId::$branchId"

    private suspend fun loadTaskContextIfNeeded(
        sessionId: String,
        branchId: String,
        branch: BranchState
    ) {
        val existing = synchronized(branch) { branch.taskContext }
        if (existing != null) return

        val loaded =
            runCatching { taskContextStorage.load(sessionId, branchId) }.getOrNull() ?: return
        synchronized(branch) {
            if (branch.taskContext == null) {
                branch.taskContext = loaded
            }
        }
    }

    private fun markHistoryDirty(sessionId: String, branchId: String) {
        dirtyHistories[contextKey(sessionId, branchId)] = true
    }

    private suspend fun loadHistoryIfNeeded(
        sessionId: String,
        branchId: String,
        branch: BranchState
    ) {
        val key = contextKey(sessionId, branchId)
        if (loadedHistories.putIfAbsent(key, true) != null) return
        val loaded =
            runCatching { chatHistoryStorage.loadHistory(key) }
                .onFailure { e -> AppLogger.e(TAG, "Failed to load history for $key", e) }
                .getOrElse { emptyList() }
        if (loaded.isEmpty()) return
        synchronized(branch) {
            if (branch.messages.isEmpty()) {
                branch.messages.addAll(loaded)
            }
        }
    }

    private suspend fun persistHistoryIfDirty(
        sessionId: String,
        branchId: String,
        branch: BranchState
    ) {
        val key = contextKey(sessionId, branchId)
        if (dirtyHistories.remove(key) == null) return
        val messages = synchronized(branch) { branch.messages.toList() }
        runCatching { chatHistoryStorage.saveHistory(key, messages) }.onFailure { e ->
            AppLogger.e(TAG, "Failed to persist history for $key", e)
        }
    }

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
        if (removed.isNotEmpty()) {
            markHistoryDirty(sessionId, branchId)
        }
        return removed
    }

    override suspend fun getHistory(sessionId: String): List<Message> {
        evictSessionsIfNeeded()
        val session = getSession(sessionId)
        val branchId = synchronized(session) { session.activeBranchId }
        val branch = getBranch(sessionId, branchId)
        loadHistoryIfNeeded(sessionId, branchId, branch)
        return synchronized(branch) { branch.messages.toList() }
    }

    override suspend fun getSessionMessages(sessionId: String): List<Message> {
        return getHistory(sessionId)
    }

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
            val key = contextKey(sessionId, branchId)
            summaryStorage.clear(key)
            loadedHistories.remove(key)
            dirtyHistories.remove(key)
            runCatching { chatHistoryStorage.deleteHistory(key) }
        }
        session?.branches?.keys?.forEach { branchId ->
            runCatching { taskContextStorage.delete(sessionId, branchId) }
        }
        pendingCompressions.keys.removeIf { it.startsWith("$sessionId::") }
        log("Reset session with summaries: $sessionId")
    }

    override fun truncateHistory(sessionId: String, keepLast: Int) {
        val branchId = currentBranchId(sessionId)
        val branch = getActiveBranchState(sessionId)
        synchronized(branch) {
            while (branch.messages.size > keepLast) {
                branch.messages.removeAt(0)
            }
        }
        markHistoryDirty(sessionId, branchId)
        val currentConfig = config
        val blocks = summaryStorage.getBlocks(contextKey(sessionId, currentBranchId(sessionId)))
        if (blocks.size > currentConfig.maxSummaryBlocks) {
            val toRemove = blocks.take(blocks.size - currentConfig.maxSummaryBlocks)
            val key = contextKey(sessionId, currentBranchId(sessionId))
            toRemove.forEach { summaryStorage.removeBlock(key, it.id) }
        }
    }

    override fun removeOldestMessages(sessionId: String, count: Int): List<Message> {
        val branchId = currentBranchId(sessionId)
        val branch = getActiveBranchState(sessionId)
        val removed = mutableListOf<Message>()
        synchronized(branch) {
            repeat(count) {
                if (branch.messages.isNotEmpty()) {
                    removed.add(branch.messages.removeAt(0))
                }
            }
        }
        if (removed.isNotEmpty()) {
            markHistoryDirty(sessionId, branchId)
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
        evictSessionsIfNeeded()
        val session = getSession(sessionId)
        val branchId = synchronized(session) { session.activeBranchId }
        val branch = getBranch(sessionId, branchId)
        val sessionKey = contextKey(sessionId, branchId)

        loadHistoryIfNeeded(sessionId, branchId, branch)
        loadTaskContextIfNeeded(sessionId, branchId, branch)
        try {
            if (currentConfig.enableTaskStateMachine) {
                log("TaskStateMachine: template=${currentConfig.stateMachineTemplate}")
                val fsm =
                    TaskStateMachine(
                        sessionId = sessionId,
                        branchId = branchId,
                        branchState = branch,
                        storage = taskContextStorage,
                        template = currentConfig.stateMachineTemplate,
                        logger = fsmLogger
                    )
                fsm.maybeStartPlanning(userMessage)
                fsm.handleUserMessage(userMessage)
                fsm.advance(userMessage = userMessage)
            }

            section("SEND START")
            log("sessionId=$sessionId, branchId=$branchId, model=$model")
            log("Config: $currentConfig")

            val cachedProfile = userProfileRepository?.getCachedActiveProfile()
            // Важно: профиль может быть null (режим "Без профиля") — в этом случае очищаем профиль
            // ветки,
            // иначе он "залипнет" со старого выбора.
            synchronized(branch) { branch.userProfile = cachedProfile }
            val activeProfile = synchronized(branch) { branch.userProfile }
            val forbidUserName = activeProfile?.invariants?.forbidsAddressingUserByName() == true
            val profilePrompt = activeProfile?.let { userProfilePromptBuilder.build(it) }.orEmpty()
            var systemPromptWithProfile =
                if (profilePrompt.isBlank()) {
                    systemPrompt
                } else {
                    systemPrompt + "\n\n" + profilePrompt
                }

            val orchestrator = mcpContextOrchestrator
            val mcpContext =
                if (orchestrator == null) {
                    null
                } else {
                    runCatching {
                            orchestrator.fetchEnrichedContext(
                                userMessage = userMessage,
                                sessionId = sessionId
                            )
                        }
                        .getOrElse { ex ->
                            log("MCP context fetch failed: ${ex.message}")
                            null
                        }
                }

            if (!mcpContext.isNullOrBlank()) {
                systemPromptWithProfile =
                    systemPromptWithProfile + "\n\n--- MCP context ---\n" + mcpContext
            }

            val previousUserNameFact =
                if (!forbidUserName) {
                    null
                } else {
                    synchronized(branch) {
                        branch.workingMemory[WmCategory.USER_INFO]?.entries
                            ?.firstOrNull { (k, _) ->
                                k.equals("user_name", ignoreCase = true)
                            }
                            ?.value
                            ?.value
                    }
                }

            if (forbidUserName) {
                synchronized(branch) {
                    val group = branch.workingMemory[WmCategory.USER_INFO]
                    if (group != null) {
                        val keysToRemove =
                            group.keys.filter { it.equals("user_name", ignoreCase = true) }
                        keysToRemove.forEach { group.remove(it) }
                        if (group.isEmpty()) branch.workingMemory.remove(WmCategory.USER_INFO)
                    }
                }
            }

            if (currentConfig.enableFactsMemory) {
                val existingWm = synchronized(branch) { snapshotWorkingMemory(branch) }
                val ops =
                    factsExtractor.extractOperations(userMessage, existingWm).let { extracted ->
                        if (!forbidUserName) extracted
                        else
                            extracted.filterNot { op ->
                                op.category == WmCategory.USER_INFO &&
                                        op.key.equals("user_name", ignoreCase = true)
                            }
                    }

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
                        _compressionEvents.emit(
                            CompressionEvent.Complete(compressionResult.newBlock)
                        )
                    }
                    is CompressionResult.Partial -> {
                        log("PARTIAL Compression: ${compressionResult.warning}")
                        persistImportantFactsFromWorkingMemory(branch, limit = 24)
                        _compressionEvents.emit(
                            CompressionEvent.Complete(compressionResult.newBlock)
                        )
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
            val factsSnapshotRaw = synchronized(branch) { snapshotWorkingMemory(branch) }
            val factsSnapshot =
                if (forbidUserName) factsSnapshotRaw.withoutUserNameFact() else factsSnapshotRaw
            val factsCount = factsSnapshot.values.sumOf { it.size }
            log(
                "Current messages count: ${historySnapshot.size}, facts=$factsCount (groups=${factsSnapshot.size})"
            )

            val ltmSnapshot = longTermMemoryStore.snapshot()
            val ltmSnapshotForRecall =
                if (forbidUserName) {
                    ltmSnapshot.withoutUserNameMemoryItem()
                } else {
                    ltmSnapshot
                }
            val ltmRecalled = ltmRecaller.recall(userMessage, ltmSnapshotForRecall, model)
            session.metrics.recallCandidates.addAndGet(ltmRecalled.size.toLong())
            val ltmFiltered0 =
                ltmRecalled.filterNot { item ->
                    factsSnapshot[item.category]?.get(item.key)?.value == item.entry.value
                }
            val dupFiltered = (ltmRecalled.size - ltmFiltered0.size).coerceAtLeast(0)
            if (dupFiltered > 0) {
                session.metrics.recallDuplicatesFiltered.addAndGet(dupFiltered.toLong())
            }
            val ltmFiltered =
                if (!forbidUserName) {
                    ltmFiltered0
                } else {
                    ltmFiltered0.filterNot { item ->
                        item.category == WmCategory.USER_INFO &&
                                item.key.equals("user_name", ignoreCase = true)
                    }
                }
            session.metrics.recallHits.addAndGet(ltmFiltered.size.toLong())
            val ltmText =
                if (ltmFiltered.isEmpty()) {
                    ""
                } else {
                    buildString {
                        append("[LONG_TERM_MEMORY]\n")
                        ltmFiltered.groupBy { it.category }.toSortedMap().forEach { (cat, items)
                            ->
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

            val forbiddenNamesForGate: Set<String> =
                if (!forbidUserName) {
                    emptySet()
                } else {
                    buildSet {
                        activeProfile
                            ?.identity
                            ?.displayName
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { add(it) }
                        previousUserNameFact?.trim()?.takeIf { it.isNotBlank() }?.let {
                            add(it)
                        }
                        addAll(extractPossibleUserNames(userMessage))
                        ltmSnapshot
                            .asSequence()
                            .filter { it.category == WmCategory.USER_INFO }
                            .filter { it.key.equals("user_name", ignoreCase = true) }
                            .map { it.entry.value.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { add(it) }
                    }
                }

            val composedContext =
                contextComposer
                    .compose(
                        sessionId = sessionKey,
                        systemPrompt = systemPromptWithLtm,
                        userMessage = userMessage,
                        historyMessages = historySnapshot,
                        facts = factsSnapshot,
                        config = config
                    )
                    .copy(taskContext = synchronized(branch) { branch.taskContext })

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

            val gatedResult: ChatResult =
                if (result is ChatResult.Success && forbidUserName) {
                    val (sanitized, changed) =
                        sanitizeAssistantTextNoUserName(
                            text = result.message.content,
                            forbiddenNames = forbiddenNamesForGate
                        )
                    if (changed) {
                        log("OutputGate: assistant message sanitized by invariant policy")
                        ChatResult.Success(
                            message = result.message.copy(content = sanitized),
                            metrics = result.metrics
                        )
                    } else {
                        result
                    }
                } else {
                    result
                }

            if (gatedResult is ChatResult.Success) {
                val stmOps = mutableListOf<MemoryOperation>()
                val removed = mutableListOf<Message>()
                synchronized(branch) {
                    branch.messages.add(Message.user(userMessage))
                    branch.messages.add(gatedResult.message)
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
                            value = gatedResult.message.content,
                            confidence = 1.0f
                        )
                    if (currentConfig.strategy == ContextStrategy.SLIDING_WINDOW) {
                        while (branch.messages.size > currentConfig.keepLastN) {
                            removed += branch.messages.removeAt(0)
                        }
                    }
                }
                markHistoryDirty(sessionId, branchId)
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
                advanceTask(sessionId, assistantMessage = gatedResult.message.content)
            }

            val m = session.metrics
            log(
                "Metrics: sessionId=$sessionId branchId=$branchId " +
                        "compression=${m.compressionSuccesses.get()}/${m.compressionAttempts.get()} fail=${m.compressionFailures.get()} " +
                        "recall=${m.recallHits.get()}/${m.recallCandidates.get()} dup=${m.recallDuplicatesFiltered.get()}"
            )
            return gatedResult
        } finally {
            persistHistoryIfDirty(sessionId, branchId, branch)
        }
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
            getSession(sessionId).metrics.compressionAttempts.incrementAndGet()

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
                    getSession(sessionId).metrics.compressionSuccesses.incrementAndGet()
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
                    getSession(sessionId).metrics.compressionFailures.incrementAndGet()
                    log("Summary generation ERROR: ${summaryResult.exception.message}")
                    CompressionResult.Failed(summaryResult.exception, messagesToCompress)
                }
            }
        } finally {
            pendingCompressions.remove(lockKey)
            log("Compression lock released for session $sessionId branch=$branchId")
        }
    }

    override fun updateConfig(newConfig: ContextConfig) {
        _config.set(newConfig)
        log("Config updated: $newConfig")
    }

    override fun getSummaryBlocks(sessionId: String): List<SummaryBlock> =
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

    override fun getWorkingMemory(sessionId: String): Map<WmCategory, Map<String, FactEntry>> {
        val branch = getActiveBranchState(sessionId)
        return synchronized(branch) { snapshotWorkingMemory(branch) }
    }

    override fun setWorkingMemoryEntry(
        sessionId: String,
        category: WmCategory,
        key: String,
        value: String,
        confidence: Float
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

    override fun deleteWorkingMemoryEntry(
        sessionId: String,
        category: WmCategory,
        key: String
    ): Boolean {
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

    override fun getStmCount(sessionId: String): Int {
        val branch = getActiveBranchState(sessionId)
        return synchronized(branch) { branch.messages.size }
    }

    override fun getShortTermMessages(sessionId: String): List<Message> {
        val branch = getActiveBranchState(sessionId)
        return synchronized(branch) { branch.messages.toList() }
    }

    override fun clearShortTermMemory(sessionId: String): Int {
        val branchId = currentBranchId(sessionId)
        val branch = getActiveBranchState(sessionId)
        var n = 0
        synchronized(branch) {
            n = branch.messages.size
            branch.messages.clear()
        }
        if (n > 0) {
            markHistoryDirty(sessionId, branchId)
        }
        return n
    }

    override fun getMetricsSnapshot(sessionId: String): AgentMetricsSnapshot {
        val session = getSession(sessionId)
        val branch = getActiveBranchState(sessionId)
        val wm = synchronized(branch) { snapshotWorkingMemory(branch) }
        return AgentMetricsSnapshot(
            sessionsCount = sessions.size,
            wmGroups = wm.size,
            wmFacts = wm.values.sumOf { it.size },
            compressionAttempts = session.metrics.compressionAttempts.get(),
            compressionSuccesses = session.metrics.compressionSuccesses.get(),
            compressionFailures = session.metrics.compressionFailures.get(),
            recallCandidates = session.metrics.recallCandidates.get(),
            recallHits = session.metrics.recallHits.get(),
            recallDuplicatesFiltered = session.metrics.recallDuplicatesFiltered.get()
        )
    }

    override suspend fun getLongTermMemorySnapshot(): List<MemoryItem> =
        longTermMemoryStore.snapshot()

    override suspend fun saveToLtm(
        category: WmCategory,
        key: String,
        value: String,
        confidence: Float
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

    override suspend fun deleteFromLtmByKey(key: String): Int {
        val k = key.trim()
        if (k.isBlank()) return 0
        return longTermMemoryStore.deleteWhere { it.key == k }
    }

    override suspend fun findInLtm(query: String, limit: Int): List<MemoryItem> =
        longTermMemoryStore.search(query, limit)

    override fun getBranchIds(sessionId: String): List<String> =
        getSession(sessionId).let { session ->
            synchronized(session) { session.branches.keys.toList().sorted() }
        }

    override fun getActiveBranchId(sessionId: String): String = currentBranchId(sessionId)

    override fun setActiveBranch(sessionId: String, branchId: String) {
        val session = getSession(sessionId)
        synchronized(session) {
            if (!session.branches.containsKey(branchId)) return
            session.activeBranchId = branchId
        }
        log("Active branch updated: sessionId=$sessionId -> $branchId")
    }

    override fun createBranchFromCheckpoint(sessionId: String, checkpointSize: Int): String {
        val session = getSession(sessionId)
        val sourceBranchId = synchronized(session) { session.activeBranchId }
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
        synchronized(session) {
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
        }
        log(
            "Branch created: sessionId=$sessionId source=$sourceBranchId checkpointSize=$size -> $id"
        )
        markHistoryDirty(sessionId, id)
        return id
    }

    override fun getComposedContext(
        sessionId: String,
        systemPrompt: String,
        userMessage: String
    ): ComposedContext {
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        val cachedProfile = userProfileRepository?.getCachedActiveProfile()
        synchronized(branch) { branch.userProfile = cachedProfile }
        val activeProfile = synchronized(branch) { branch.userProfile }
        val forbidUserName = activeProfile?.invariants?.forbidsAddressingUserByName() == true
        val profilePrompt = activeProfile?.let { userProfilePromptBuilder.build(it) }.orEmpty()
        val systemPromptWithProfile =
            if (profilePrompt.isBlank()) systemPrompt else systemPrompt + "\n\n" + profilePrompt
        val historySnapshot = synchronized(branch) { branch.messages.toList() }
        val factsSnapshotRaw = synchronized(branch) { snapshotWorkingMemory(branch) }
        val factsSnapshot =
            if (forbidUserName) factsSnapshotRaw.withoutUserNameFact() else factsSnapshotRaw
        val composed =
            contextComposer.compose(
                sessionId = contextKey(sessionId, branchId),
                systemPrompt = systemPromptWithProfile,
                userMessage = userMessage,
                historyMessages = historySnapshot,
                facts = factsSnapshot,
                config = config
            )
        val taskContextSnapshot = synchronized(branch) { branch.taskContext }
        return composed.copy(taskContext = taskContextSnapshot)
    }

    fun getTaskState(sessionId: String): TaskState {
        if (!config.enableTaskStateMachine) return TaskState.IDLE
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        return TaskStateMachine(
            sessionId = sessionId,
            branchId = branchId,
            branchState = branch,
            storage = taskContextStorage,
            template = config.stateMachineTemplate,
            logger = fsmLogger
        )
            .getState()
    }

    override fun getTaskContextSnapshot(sessionId: String): TaskContext? {
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        return synchronized(branch) { branch.taskContext }
    }

    fun getCurrentStep(sessionId: String): TaskStep? {
        if (!config.enableTaskStateMachine) return null
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        return TaskStateMachine(
            sessionId = sessionId,
            branchId = branchId,
            branchState = branch,
            storage = taskContextStorage,
            template = config.stateMachineTemplate,
            logger = fsmLogger
        )
            .getCurrentStep()
    }

    suspend fun advanceTask(
        sessionId: String,
        userMessage: String? = null,
        assistantMessage: String? = null
    ): TaskContext {
        if (!config.enableTaskStateMachine) return idleTaskContext()
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        loadTaskContextIfNeeded(sessionId, branchId, branch)

        val fsm =
            TaskStateMachine(
                sessionId = sessionId,
                branchId = branchId,
                branchState = branch,
                storage = taskContextStorage,
                template = config.stateMachineTemplate,
                logger = fsmLogger
            )

        if (userMessage != null) {
            fsm.handleUserMessage(userMessage)
        }
        val next = fsm.advance(userMessage = userMessage, assistantMessage = assistantMessage)
        return next ?: idleTaskContext()
    }

    override suspend fun resetTask(sessionId: String) {
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        if (!config.enableTaskStateMachine) {
            synchronized(branch) { branch.taskContext = null }
            runCatching { taskContextStorage.delete(sessionId, branchId) }
            return
        }
        TaskStateMachine(
            sessionId = sessionId,
            branchId = branchId,
            branchState = branch,
            storage = taskContextStorage,
            template = config.stateMachineTemplate,
            logger = fsmLogger
        )
            .reset()
    }

    override suspend fun approveTaskPlan(sessionId: String): TaskContext? {
        if (!config.enableTaskStateMachine) return null
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        loadTaskContextIfNeeded(sessionId, branchId, branch)
        val fsm =
            TaskStateMachine(
                sessionId = sessionId,
                branchId = branchId,
                branchState = branch,
                storage = taskContextStorage,
                template = config.stateMachineTemplate,
                logger = fsmLogger
            )
        fsm.advance()
        fsm.setArtifact("planApproved", "true")
        return fsm.advance()
    }

    override suspend fun approveTaskValidation(sessionId: String): TaskContext? {
        if (!config.enableTaskStateMachine) return null
        val branchId = getActiveBranchId(sessionId)
        val branch = getBranch(sessionId, branchId)
        loadTaskContextIfNeeded(sessionId, branchId, branch)
        val fsm =
            TaskStateMachine(
                sessionId = sessionId,
                branchId = branchId,
                branchState = branch,
                storage = taskContextStorage,
                template = config.stateMachineTemplate,
                logger = fsmLogger
            )
        fsm.advance()
        fsm.setArtifact("validationApproved", "true")
        return fsm.advance()
    }

    private fun idleTaskContext(): TaskContext {
        val now = System.currentTimeMillis()
        return TaskContext(
            taskId = "idle",
            state = TaskState.IDLE,
            originalRequest = "",
            plan = emptyList(),
            currentStepIndex = 0,
            artifacts = emptyMap(),
            error = null,
            createdAt = now,
            updatedAt = now
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
        val forbidUserName =
            synchronized(branch) { branch.userProfile }
                ?.invariants
                ?.forbidsAddressingUserByName() == true
        val snapshotRaw = synchronized(branch) { snapshotWorkingMemory(branch) }
        val snapshot = if (forbidUserName) snapshotRaw.withoutUserNameFact() else snapshotRaw
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
