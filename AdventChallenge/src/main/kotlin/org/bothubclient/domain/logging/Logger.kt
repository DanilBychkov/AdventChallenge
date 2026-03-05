package org.bothubclient.domain.logging

interface Logger {
    fun log(tag: String, message: String)
}

object NoOpLogger : Logger {
    override fun log(tag: String, message: String) = Unit
}
