package org.bothubclient.infrastructure.eval

import org.bothubclient.domain.entity.ContextConfig
import org.bothubclient.domain.entity.ContextStrategy
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageRole
import org.bothubclient.infrastructure.context.ConcurrentSummaryStorage
import org.bothubclient.infrastructure.context.DefaultContextComposer
import org.bothubclient.infrastructure.context.HeuristicFactsExtractor
import org.bothubclient.infrastructure.logging.FileLogger

object ContextStrategyScenarioRunner {
    private val scenarioMessages =
        listOf(
            "Нужен сервис для бронирования переговорных.",
            "Команда 200 человек, 4 офиса, нужно SSO.",
            "Важна интеграция с Google Calendar и Outlook.",
            "Доступы по ролям: админ/менеджер/сотрудник.",
            "SLA 99.9% и аудит действий.",
            "Бюджет до 15k$/мес, желательно 10k.",
            "Хочу мобильное приложение и веб.",
            "Нужна локализация на EN/RU.",
            "Срок MVP 6 недель.",
            "Без хранения персональных данных дольше 30 дней.",
            "Нужны отчёты по загрузке комнат.",
            "Предложи архитектуру и план релиза."
        )

    @JvmStatic
    fun main(args: Array<String>) {
        runDry()
    }

    fun runDry() {
        FileLogger.section("CONTEXT STRATEGY DRY RUN")
        FileLogger.log("ScenarioRunner", "Messages=${scenarioMessages.size}")

        val extractor = HeuristicFactsExtractor()
        val summaryStorage = ConcurrentSummaryStorage()
        val composer = DefaultContextComposer(summaryStorage = summaryStorage)

        runLinear(
            strategy = ContextStrategy.SLIDING_WINDOW,
            config =
                ContextConfig.DEFAULT.copy(
                    strategy = ContextStrategy.SLIDING_WINDOW,
                    enableAutoCompression = false
                ),
            composer = composer,
            extractor = extractor
        )

        runLinear(
            strategy = ContextStrategy.STICKY_FACTS,
            config =
                ContextConfig.DEFAULT.copy(
                    strategy = ContextStrategy.STICKY_FACTS,
                    enableAutoCompression = false
                ),
            composer = composer,
            extractor = extractor
        )

        runBranching(
            config =
                ContextConfig.DEFAULT.copy(
                    strategy = ContextStrategy.BRANCHING,
                    enableAutoCompression = false
                ),
            composer = composer,
            extractor = extractor
        )
    }

    private fun runLinear(
        strategy: ContextStrategy,
        config: ContextConfig,
        composer: DefaultContextComposer,
        extractor: HeuristicFactsExtractor
    ) {
        val tag = "Run-$strategy"
        FileLogger.section("RUN $strategy")
        val history = mutableListOf<Message>()
        var facts: LinkedHashMap<String, LinkedHashMap<String, String>> = LinkedHashMap()
        val sessionId = "dry::$strategy"

        scenarioMessages.forEachIndexed { index, userMsg ->
            val updated = extractor.extractUpdatedGroupsHeuristic(userMsg, facts)
            facts = toMutableGroups(updated)
            trimFacts(facts, config.maxFacts)

            val ctx =
                composer.compose(
                    sessionId = sessionId,
                    systemPrompt = "SYSTEM",
                    userMessage = userMsg,
                    historyMessages = history.toList(),
                    facts = facts.mapValues { (_, v) -> v.toMap() }.toMap(),
                    config = config
                )

            FileLogger.log(
                tag,
                "turn=${index + 1} recent=${ctx.recentMessages.size} facts=${ctx.facts.values.sumOf { it.size }} tokens~=${ctx.totalEstimatedTokens}"
            )

            history.add(Message.user(userMsg))
        }

        FileLogger.log(
            tag,
            "final_history=${history.size} final_facts=${facts.values.sumOf { it.size }}"
        )
    }

    private fun runBranching(
        config: ContextConfig,
        composer: DefaultContextComposer,
        extractor: HeuristicFactsExtractor
    ) {
        val tag = "Run-Branching"
        FileLogger.section("RUN BRANCHING")
        val sessionIdA = "dry::branching::A"
        val sessionIdB = "dry::branching::B"

        val mainHistory = mutableListOf<Message>()
        var mainFacts: LinkedHashMap<String, LinkedHashMap<String, String>> = LinkedHashMap()

        val checkpointAfter = 6
        scenarioMessages.take(checkpointAfter).forEach { userMsg ->
            val updated = extractor.extractUpdatedGroupsHeuristic(userMsg, mainFacts)
            mainFacts = toMutableGroups(updated)
            trimFacts(mainFacts, config.maxFacts)
            mainHistory.add(Message.user(userMsg))
        }

        val branchAHistory = mainHistory.toMutableList()
        val branchBHistory = mainHistory.toMutableList()

        val branchAFacts = recomputeFacts(extractor, branchAHistory, config.maxFacts)
        val branchBFacts = recomputeFacts(extractor, branchBHistory, config.maxFacts)

        FileLogger.log(
            tag,
            "checkpoint=$checkpointAfter branches=2 history=${mainHistory.size} facts=${mainFacts.size}"
        )

        scenarioMessages.drop(checkpointAfter).forEachIndexed { idx, userMsg ->
            val turn = checkpointAfter + idx + 1

            val updatedA = extractor.extractUpdatedGroupsHeuristic(userMsg, branchAFacts)
            branchAFacts.clear()
            branchAFacts.putAll(toMutableGroups(updatedA))
            trimFacts(branchAFacts, config.maxFacts)
            val ctxA =
                composer.compose(
                    sessionId = sessionIdA,
                    systemPrompt = "SYSTEM",
                    userMessage = userMsg,
                    historyMessages = branchAHistory.toList(),
                    facts = branchAFacts.mapValues { (_, v) -> v.toMap() }.toMap(),
                    config = config
                )
            FileLogger.log(
                tag,
                "branch=A turn=$turn recent=${ctxA.recentMessages.size} facts=${ctxA.facts.size} tokens~=${ctxA.totalEstimatedTokens}"
            )
            branchAHistory.add(Message.user(userMsg))

            val updatedB = extractor.extractUpdatedGroupsHeuristic(userMsg, branchBFacts)
            branchBFacts.clear()
            branchBFacts.putAll(toMutableGroups(updatedB))
            trimFacts(branchBFacts, config.maxFacts)
            val ctxB =
                composer.compose(
                    sessionId = sessionIdB,
                    systemPrompt = "SYSTEM",
                    userMessage = userMsg,
                    historyMessages = branchBHistory.toList(),
                    facts = branchBFacts.mapValues { (_, v) -> v.toMap() }.toMap(),
                    config = config
                )
            FileLogger.log(
                tag,
                "branch=B turn=$turn recent=${ctxB.recentMessages.size} facts=${ctxB.facts.size} tokens~=${ctxB.totalEstimatedTokens}"
            )
            branchBHistory.add(Message.user(userMsg))
        }

        FileLogger.log(
            tag,
            "final A: history=${branchAHistory.size} facts=${branchAFacts.size} | final B: history=${branchBHistory.size} facts=${branchBFacts.size}"
        )
    }

    private fun recomputeFacts(
        extractor: HeuristicFactsExtractor,
        messages: List<Message>,
        maxFacts: Int
    ): LinkedHashMap<String, LinkedHashMap<String, String>> {
        val facts: LinkedHashMap<String, LinkedHashMap<String, String>> = LinkedHashMap()
        messages.filter { it.role == MessageRole.USER }.forEach { msg ->
            val updated = extractor.extractUpdatedGroupsHeuristic(msg.content, facts)
            facts.clear()
            facts.putAll(toMutableGroups(updated))
            trimFacts(facts, maxFacts)
        }
        return facts
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
