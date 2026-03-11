package org.bothubclient.domain.repository

interface ReportGenerator {
    suspend fun generateReport(activity: String): String
}
