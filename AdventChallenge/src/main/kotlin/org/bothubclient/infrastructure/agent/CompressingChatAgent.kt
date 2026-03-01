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
import org.bothubclient.infrastructure.context.HeuristicFactsExtractor
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger
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
    initialConfig: ContextConfig = ContextConfig.DEFAULT
) : ChatAgent {

    private data class SessionState(
        var activeBranchId: String = "main",
        val branches: MutableMap<String, BranchState> = mutableMapOf("main" to BranchState())
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
        sessions.computeIfAbsent(sessionId) { SessionState() }

    private fun getBranch(sessionId: String, branchId: String): BranchState =
        getSession(sessionId).branches.getOrPut(branchId) { BranchState() }

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

        if (currentConfig.enableFactsMemory) {
            val existingGroups =
                synchronized(branch) { branch.facts.mapValues { (_, v) -> v.toMap() }.toMap() }

            val updatedGroups =
                factsExtractor.extractUpdatedGroups(
                    userMessage = userMessage,
                    existingGroups = existingGroups
                )

            if (updatedGroups != existingGroups) {
                synchronized(branch) {
                    branch.facts.clear()
                    branch.facts.putAll(toMutableGroups(updatedGroups))
                    trimFacts(branch.facts, currentConfig.maxFacts)
                }
                val totalFacts = synchronized(branch) { totalFacts(branch.facts) }
                log("Facts updated: groups=${updatedGroups.size}, totalFacts=$totalFacts")
            }
        }

        if (currentConfig.enableAutoCompression) {
            log("Auto-compression ENABLED, checking...")
            val compressionResult = tryCompressIfNeeded(sessionId, branchId, currentConfig)
            when (compressionResult) {
                is CompressionResult.Success -> {
                    log("SUCCESS Compression: block ${compressionResult.newBlock.id}")
                    _compressionEvents.emit(CompressionEvent.Complete(compressionResult.newBlock))
                }
                is CompressionResult.Partial -> {
                    log("PARTIAL Compression: ${compressionResult.warning}")
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
        val factsSnapshot =
            synchronized(branch) { branch.facts.mapValues { (_, v) -> v.toMap() }.toMap() }
        val factsCount = factsSnapshot.values.sumOf { it.size }
        log(
            "Current messages count: ${historySnapshot.size}, facts=$factsCount (groups=${factsSnapshot.size})"
        )

        val composedContext =
            contextComposer.compose(
                sessionId = sessionKey,
                systemPrompt = systemPrompt,
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
            synchronized(branch) {
                branch.messages.add(Message.user(userMessage))
                branch.messages.add(result.message)
                if (currentConfig.strategy == ContextStrategy.SLIDING_WINDOW) {
                    while (branch.messages.size > currentConfig.keepLastN) {
                        branch.messages.removeAt(0)
                    }
                }
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
        return synchronized(branch) { branch.facts.mapValues { (_, v) -> v.toMap() }.toMap() }
    }

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
        val size = checkpointSize.coerceIn(0, sourceMessages.size)
        val forkMessages = sourceMessages.take(size)

        val extractor = factsExtractor
        var forkFacts: LinkedHashMap<String, LinkedHashMap<String, String>> = LinkedHashMap()
        if (config.enableFactsMemory) {
            forkMessages.filter { it.role == MessageRole.USER }.forEach { m ->
                val updated = extractor.extractUpdatedGroupsHeuristic(m.content, forkFacts)
                forkFacts = toMutableGroups(updated)
                trimFacts(forkFacts, config.maxFacts)
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
            BranchState(messages = forkMessages.toMutableList(), facts = forkFacts)
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
        val historySnapshot = synchronized(branch) { branch.messages.toList() }
        val factsSnapshot =
            synchronized(branch) { branch.facts.mapValues { (_, v) -> v.toMap() }.toMap() }
        return contextComposer.compose(
            sessionId = contextKey(sessionId, branchId),
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            historyMessages = historySnapshot,
            facts = factsSnapshot,
            config = config
        )
    }

    private fun toMutableGroups(
        groups: Map<String, Map<String, String>>
    ): LinkedHashMap<String, LinkedHashMap<String, String>> {
        val out = LinkedHashMap<String, LinkedHashMap<String, String>>()
        groups.forEach { (category, group) ->
            val inner = LinkedHashMap<String, String>()
            group.forEach { (k, v) ->
                val kk = k.trim()
                val vv = v.trim()
                if (kk.isNotBlank() && vv.isNotBlank()) inner[kk] = vv
            }
            if (inner.isNotEmpty()) out[category] = inner
        }
        return out
    }

    private fun totalFacts(groups: LinkedHashMap<String, LinkedHashMap<String, String>>): Int =
        groups.values.sumOf { it.size }

    private fun trimFacts(
        groups: LinkedHashMap<String, LinkedHashMap<String, String>>,
        maxFacts: Int
    ) {
        while (totalFacts(groups) > maxFacts) {
            val firstEntry = groups.entries.firstOrNull { it.value.isNotEmpty() } ?: break
            val firstKey = firstEntry.value.keys.firstOrNull()
            if (firstKey == null) {
                groups.remove(firstEntry.key)
            } else {
                firstEntry.value.remove(firstKey)
                if (firstEntry.value.isEmpty()) groups.remove(firstEntry.key)
            }
        }
    }
}
