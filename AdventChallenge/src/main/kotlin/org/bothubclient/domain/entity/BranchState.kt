package org.bothubclient.domain.entity

data class BranchState(
    val messages: MutableList<Message> = mutableListOf(),
    val workingMemory: LinkedHashMap<WmCategory, LinkedHashMap<String, FactEntry>> =
        LinkedHashMap()
)
