package org.bothubclient.infrastructure.logging

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FileLogger {
    private val logFile = File("app.log")
    private val writer = PrintWriter(FileWriter(logFile, false))
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun log(tag: String, message: String) {
        val timestamp = LocalDateTime.now().format(formatter)
        val logLine = "$timestamp [$tag] $message"

        writer.println(logLine)
        writer.flush()

        println(logLine)
    }

    fun section(title: String) {
        val line = "\n${"=".repeat(20)} $title ${"=".repeat(20)}"
        writer.println(line)
        writer.flush()
        println(line)
    }

    fun close() {
        writer.close()
    }
}
