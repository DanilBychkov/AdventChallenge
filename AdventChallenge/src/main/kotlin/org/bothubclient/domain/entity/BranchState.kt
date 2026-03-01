package org.bothubclient.domain.entity

data class BranchState(
    val messages: MutableList<Message> = mutableListOf(),
    val facts: LinkedHashMap<String, LinkedHashMap<String, String>> = LinkedHashMap()
)
