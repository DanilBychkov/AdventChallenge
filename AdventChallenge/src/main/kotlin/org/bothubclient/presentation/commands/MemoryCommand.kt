package org.bothubclient.presentation.commands

import org.bothubclient.domain.entity.WmCategory

sealed interface MemoryCommand {
    sealed interface MemoryPanel : MemoryCommand {
        data object Show : MemoryPanel
        data object Hide : MemoryPanel
        data object Toggle : MemoryPanel
    }

    sealed interface Stm : MemoryCommand {
        data object Clear : Stm
        data class Last(val n: Int) : Stm
    }

    sealed interface Wm : MemoryCommand {
        data class Set(
            val category: WmCategory,
            val key: String,
            val value: String,
            val confidence: Float
        ) : Wm

        data class Get(val category: WmCategory, val key: String) : Wm

        data class Delete(val category: WmCategory, val key: String) : Wm

        data class List(val category: WmCategory?) : Wm
    }

    sealed interface Ltm : MemoryCommand {
        data class Save(val category: WmCategory, val key: String, val value: String) : Ltm
        data class Find(val query: String) : Ltm
        data class Delete(val key: String) : Ltm
    }
}

sealed interface MemoryParseResult {
    data class Command(val command: MemoryCommand) : MemoryParseResult
    data class Error(val message: String) : MemoryParseResult
}
