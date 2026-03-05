package org.bothubclient.presentation.commands

import org.bothubclient.domain.entity.WmCategory

class MemoryCommandParser {
    fun parse(raw: String): MemoryParseResult? {
        val text = raw.trim()
        if (!text.startsWith("!")) return null

        val head = text.substringBefore(' ').trim().lowercase()
        return when (head) {
            "!memory" -> parseMemory(text)
            "!stm" -> parseStm(text)
            "!wm" -> parseWm(text)
            "!ltm" -> parseLtm(text)
            else -> null
        }
    }

    private fun parseMemory(text: String): MemoryParseResult {
        val parts = text.split(Regex("\\s+"))
        if (parts.size >= 3 && parts[1].lowercase() == "panel") {
            return when (parts[2].lowercase()) {
                "show" -> MemoryParseResult.Command(MemoryCommand.MemoryPanel.Show)
                "hide" -> MemoryParseResult.Command(MemoryCommand.MemoryPanel.Hide)
                "toggle" -> MemoryParseResult.Command(MemoryCommand.MemoryPanel.Toggle)
                else -> MemoryParseResult.Error("Синтаксис: !memory panel show|hide|toggle")
            }
        }
        return MemoryParseResult.Error("Синтаксис: !memory panel show|hide|toggle")
    }

    private fun parseStm(text: String): MemoryParseResult {
        val parts = text.split(Regex("\\s+"))
        if (parts.size >= 2 && parts[1].lowercase() == "clear") {
            return MemoryParseResult.Command(MemoryCommand.Stm.Clear)
        }
        if (parts.size >= 3 && parts[1].lowercase() == "last") {
            val n = parts[2].toIntOrNull() ?: 10
            return MemoryParseResult.Command(MemoryCommand.Stm.Last(n))
        }
        return MemoryParseResult.Error("Синтаксис: !stm clear | !stm last <n>")
    }

    private fun parseWm(text: String): MemoryParseResult {
        val parts = text.split(Regex("\\s+"))
        if (parts.size < 2) return MemoryParseResult.Error("Синтаксис: !wm <set|get|delete|list> ...")

        return when (parts[1].lowercase()) {
            "set" -> parseWmSet(text)
            "get" -> parseWmGet(parts)
            "delete" -> parseWmDelete(parts)
            "list" -> parseWmList(parts)
            else -> MemoryParseResult.Error("Синтаксис: !wm <set|get|delete|list> ...")
        }
    }

    private fun parseWmSet(text: String): MemoryParseResult {
        val parts = text.split(Regex("\\s+"), limit = 4)
        if (parts.size < 4) {
            return MemoryParseResult.Error("Синтаксис: !wm set <CATEGORY> <key>=<value> [confidence]")
        }
        val category = parseCategory(parts[2])
            ?: return MemoryParseResult.Error("Ошибка: CATEGORY должен быть одним из USER_INFO/TASK/CONTEXT/PROGRESS")

        val tail = parts[3].trim()
        val eqIndex = tail.indexOf('=')
        if (eqIndex <= 0) return MemoryParseResult.Error("Ошибка: ожидается key=value")

        val key = tail.substring(0, eqIndex).trim()
        val rest = tail.substring(eqIndex + 1).trim()
        if (key.isBlank() || rest.isBlank()) return MemoryParseResult.Error("Ошибка: ожидается key=value")

        val tokens = rest.split(Regex("\\s+"))
        val lastAsConf = tokens.lastOrNull()?.toFloatOrNull()
        val value =
            if (lastAsConf != null && tokens.size >= 2) {
                rest.removeSuffix(tokens.last()).trim()
            } else {
                rest
            }
        val conf = lastAsConf ?: 1.0f
        if (value.isBlank()) return MemoryParseResult.Error("Ошибка: значение пустое")

        return MemoryParseResult.Command(
            MemoryCommand.Wm.Set(category = category, key = key, value = value, confidence = conf)
        )
    }

    private fun parseWmGet(parts: List<String>): MemoryParseResult {
        if (parts.size < 4) return MemoryParseResult.Error("Синтаксис: !wm get <CATEGORY> <key>")
        val category = parseCategory(parts[2]) ?: return MemoryParseResult.Error("Синтаксис: !wm get <CATEGORY> <key>")
        val key = parts.drop(3).joinToString(" ").trim()
        if (key.isBlank()) return MemoryParseResult.Error("Синтаксис: !wm get <CATEGORY> <key>")
        return MemoryParseResult.Command(MemoryCommand.Wm.Get(category, key))
    }

    private fun parseWmDelete(parts: List<String>): MemoryParseResult {
        if (parts.size < 4) return MemoryParseResult.Error("Синтаксис: !wm delete <CATEGORY> <key>")
        val category =
            parseCategory(parts[2]) ?: return MemoryParseResult.Error("Синтаксис: !wm delete <CATEGORY> <key>")
        val key = parts.drop(3).joinToString(" ").trim()
        if (key.isBlank()) return MemoryParseResult.Error("Синтаксис: !wm delete <CATEGORY> <key>")
        return MemoryParseResult.Command(MemoryCommand.Wm.Delete(category, key))
    }

    private fun parseWmList(parts: List<String>): MemoryParseResult {
        if (parts.size == 2) return MemoryParseResult.Command(MemoryCommand.Wm.List(category = null))
        val category = parseCategory(parts[2]) ?: return MemoryParseResult.Error("Синтаксис: !wm list [CATEGORY]")
        return MemoryParseResult.Command(MemoryCommand.Wm.List(category = category))
    }

    private fun parseLtm(text: String): MemoryParseResult {
        val parts = text.split(Regex("\\s+"), limit = 3)
        if (parts.size < 2) return MemoryParseResult.Error("Синтаксис: !ltm <save|find|delete> ...")
        return when (parts[1].lowercase()) {
            "save" -> parseLtmSave(text)
            "find" -> {
                val query = text.substringAfter("!ltm find").trim()
                if (query.isBlank()) MemoryParseResult.Error("Синтаксис: !ltm find <query>")
                else MemoryParseResult.Command(MemoryCommand.Ltm.Find(query))
            }

            "delete" -> {
                val key = text.substringAfter("!ltm delete").trim()
                if (key.isBlank()) MemoryParseResult.Error("Синтаксис: !ltm delete <key>")
                else MemoryParseResult.Command(MemoryCommand.Ltm.Delete(key))
            }

            else -> MemoryParseResult.Error("Синтаксис: !ltm <save|find|delete> ...")
        }
    }

    private fun parseLtmSave(text: String): MemoryParseResult {
        val tail = text.substringAfter("!ltm save").trim()
        val eqIndex = tail.indexOf('=')
        if (eqIndex <= 0) return MemoryParseResult.Error("Синтаксис: !ltm save <key>=<value> [CATEGORY]")

        val key = tail.substring(0, eqIndex).trim()
        val after = tail.substring(eqIndex + 1).trim()
        if (key.isBlank() || after.isBlank()) return MemoryParseResult.Error("Синтаксис: !ltm save <key>=<value> [CATEGORY]")

        val tokens = after.split(Regex("\\s+"))
        val categoryText = tokens.lastOrNull()
        val category = categoryText?.let { parseCategory(it) }
        val value =
            if (category != null && tokens.size >= 2) {
                after.removeSuffix(categoryText!!).trim()
            } else {
                after
            }
        if (value.isBlank()) return MemoryParseResult.Error("Синтаксис: !ltm save <key>=<value> [CATEGORY]")

        return MemoryParseResult.Command(
            MemoryCommand.Ltm.Save(category = category ?: WmCategory.CONTEXT, key = key, value = value)
        )
    }

    private fun parseCategory(text: String): WmCategory? {
        return runCatching { WmCategory.valueOf(text.trim().uppercase()) }.getOrNull()
    }
}
