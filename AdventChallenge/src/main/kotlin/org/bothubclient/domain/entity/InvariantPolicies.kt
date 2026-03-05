package org.bothubclient.domain.entity

import org.bothubclient.domain.memory.MemoryItem

fun List<ProfileInvariant>.forbidsAddressingUserByName(): Boolean {
    return this.any { inv ->
        if (!inv.isActive) return@any false
        if (inv.severity != InvariantSeverity.HARD) return@any false

        val text =
            buildString {
                append(inv.description)
                append('\n')
                append(inv.rationale)
            }
                .lowercase()
                .replace('ё', 'е')

        val hasNegation = text.contains("никогда") || text.contains("не ")
        val hasVerb =
            text.contains("назыв") ||
                    text.contains("упомина") ||
                    text.contains("обращ") ||
                    text.contains("говор") ||
                    text.contains("пиши") ||
                    text.contains("call") ||
                    text.contains("address") ||
                    text.contains("mention") ||
                    text.contains("say")
        val hasTarget =
            text.contains("по имени") ||
                    text.contains("имя") ||
                    text.contains("username") ||
                    text.contains("user name")

        hasNegation && hasVerb && hasTarget
    }
}

fun Map<WmCategory, Map<String, FactEntry>>.withoutUserNameFact():
        Map<WmCategory, Map<String, FactEntry>> {
    val out = LinkedHashMap<WmCategory, Map<String, FactEntry>>()
    this.forEach { (category, group) ->
        if (category != WmCategory.USER_INFO) {
            out[category] = group
            return@forEach
        }
        val filtered = group.filterKeys { it.lowercase() != "user_name" }
        if (filtered.isNotEmpty()) {
            out[category] = filtered
        }
    }
    return out
}

fun List<MemoryItem>.withoutUserNameMemoryItem(): List<MemoryItem> =
    this.filterNot { it.category == WmCategory.USER_INFO && it.key.equals("user_name", ignoreCase = true) }

fun sanitizeAssistantTextNoUserName(
    text: String,
    forbiddenNames: Set<String>
): Pair<String, Boolean> {
    var out = text

    val ruGreeting =
        Regex(
            """(?m)^(\s*(?:[Пп]ривет|[Зз]дравствуй|[Зз]дравствуйте|[Дд]обрый\s+(?:[Дд]ень|[Вв]ечер|[Уу]тро))\s*)([,!?.:;…—–-])\s*\(?(?:[А-ЯЁ][а-яё-]{1,30})\)?\s*([!?.…])?(?=$|[^\p{L}])"""
        )
    out =
        out.replace(ruGreeting) { m ->
            val greet = m.groupValues[1].trimEnd()
            val sep = m.groupValues[2].trim()
            val trailing = m.groupValues[3].trim()
            val kept =
                if (trailing.isNotBlank()) {
                    trailing
                } else if (sep.any { it == '!' || it == '?' || it == '.' || it == '…' }) {
                    sep
                } else {
                    ""
                }
            greet + kept
        }

    val ruNameFirst =
        Regex(
            """(?m)^(\s*)\(?([А-ЯЁ][а-яё-]{1,30})\)?\s*[,!?.:;…—–-]\s*(?=(?:[Пп]ривет|[Зз]дравствуй|[Зз]дравствуйте|[Дд]обрый\s+(?:[Дд]ень|[Вв]ечер|[Уу]тро))(?=$|[^\p{L}]))"""
        )
    out = out.replace(ruNameFirst) { m -> m.groupValues[1] }

    val ruVocative = Regex("""(?m)([,;:—–-])\s*\(?([А-ЯЁ][а-яё-]{1,30})\)?(?=\s*[,!?.:;…])""")
    out = out.replace(ruVocative) { m -> m.groupValues[1] }

    val ruLeadingDash =
        Regex("""(?m)^(\s*[—–-]\s*)\(?([А-ЯЁ][а-яё-]{1,30})\)?\s*(?:,\s*|[!?.…]\s*)""")
    out = out.replace(ruLeadingDash) { m -> m.groupValues[1] }

    val enGreeting =
        Regex(
            """(?m)^(\s*(?:Hi|Hello|hi|hello)\s*)([,!?.:;…—–-])\s*\(?(?:[A-Z][a-z-]{1,30})\)?\s*([!?.…])?(?=$|[^\p{L}])"""
        )
    out =
        out.replace(enGreeting) { m ->
            val greet = m.groupValues[1].trimEnd()
            val sep = m.groupValues[2].trim()
            val trailing = m.groupValues[3].trim()
            val kept =
                if (trailing.isNotBlank()) {
                    trailing
                } else if (sep.any { it == '!' || it == '?' || it == '.' || it == '…' }) {
                    sep
                } else {
                    ""
                }
            greet + kept
        }

    val enNameFirst =
        Regex(
            """(?m)^(\s*)\(?([A-Z][a-z-]{1,30})\)?\s*[,!?.:;…—–-]\s*(?=(?:Hi|Hello|hi|hello)(?=$|[^\p{L}]))"""
        )
    out = out.replace(enNameFirst) { m -> m.groupValues[1] }

    val enVocative = Regex("""(?m)([,;:—–-])\s*\(?([A-Z][a-z-]{1,30})\)?(?=\s*[,!?.:;…])""")
    out = out.replace(enVocative) { m -> m.groupValues[1] }

    val enLeadingDash = Regex("""(?m)^(\s*[—–-]\s*)\(?([A-Z][a-z-]{1,30})\)?\s*(?:,\s*|[!?.…]\s*)""")
    out = out.replace(enLeadingDash) { m -> m.groupValues[1] }

    forbiddenNames
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .flatMap { name -> sequenceOf(name) + name.split(Regex("""\s+""")).asSequence() }
        .map { it.trim(' ', '.', ',', '!', '?', ':', ';', '"', '«', '»', '(', ')') }
        .filter { it.length >= 2 }
        .distinct()
        .forEach { name ->
            val variants = linkedSetOf(name, name.lowercase(), name.uppercase())
            variants.forEach { v ->
                val re = Regex("""(?<!\p{L})${Regex.escape(v)}(?!\p{L})""")
                out = out.replace(re, "")
            }
        }

    out = out.replace(Regex("""([,;:])\1+"""), "$1")
    out = out.replace(Regex(""",\s*,\s*(?=\p{L})"""), ", ")
    out = out.replace(Regex(""",\s*,"""), ",")
    out = out.replace(Regex("""([—–-])\s*,\s*"""), "$1 ")
    out = out.replace(Regex("""([—–-])(\p{L})"""), "$1 $2")
    out = out.replace(Regex("""\s+([,!.?:;…])"""), "$1")
    out = out.replace(Regex("""([,;:])([.!?])"""), "$2")
    out = out.replace(Regex("""\s{2,}"""), " ")
    out = out.replace(Regex("""\s+,\s+"""), ", ")
    out = out.replace(Regex("""\s+,(\R|$)"""), "$1")
    out = out.replace(Regex(""",(\R|$)"""), "$1")
    out = out.trim()

    if (out.isBlank()) out = "Привет!"

    return out to (out != text)
}

fun extractPossibleUserNames(text: String): Set<String> {
    val msg = text.trim()
    if (msg.isBlank()) return emptySet()

    val lower = msg.lowercase().replace('ё', 'е')
    val out = linkedSetOf<String>()

    fun parseAfterMarker(marker: String) {
        val idx = lower.indexOf(marker)
        if (idx < 0) return
        val raw = msg.substring((idx + marker.length).coerceAtMost(msg.length)).trim()
        val cleaned = raw.trim(' ', '.', ',', '!', '?', ':', ';', '"', '«', '»', '(', ')')
        val tokens =
            cleaned.split(Regex("""\s+"""))
                .asSequence()
                .map { it.trim('-', '—', '–') }
                .filter { it.isNotBlank() }
                .map { it.trim(' ', '.', ',', '!', '?', ':', ';', '"', '«', '»') }
                .filter { it.any { ch -> ch.isLetter() } }
                .take(3)
                .toList()
        if (tokens.isNotEmpty()) out += tokens.joinToString(" ")
    }

    parseAfterMarker("меня зовут")
    parseAfterMarker("my name is")

    return out
}
