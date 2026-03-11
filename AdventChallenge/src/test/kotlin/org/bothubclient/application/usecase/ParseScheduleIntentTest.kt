package org.bothubclient.application.usecase

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseScheduleIntentTest {

    private val useCase = ParseScheduleIntentUseCase(llmExtractor = null)

    @Test
    fun `присылай раз в 5 минут чем заняться`() = runTest {
        val result = useCase("присылай раз в 5 минут чем заняться")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(5, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `каждые 10 минут подсказывай что делать`() = runTest {
        val result = useCase("каждые 10 минут подсказывай что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(10, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `каждый час подсказывай что делать`() = runTest {
        val result = useCase("каждый час подсказывай что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(60, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `раз в 15 минут пиши что делать`() = runTest {
        val result = useCase("раз в 15 минут пиши что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(15, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `отправляй раз в 30 минут активности`() = runTest {
        val result = useCase("отправляй раз в 30 минут активности")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(30, result.intervalMinutes)
    }

    @Test
    fun `напоминай каждые 3 минуты чем заняться`() = runTest {
        val result = useCase("напоминай каждые 3 минуты чем заняться")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(3, result.intervalMinutes)
    }

    @Test
    fun `присылай каждые 20 минут рекомендации`() = runTest {
        val result = useCase("присылай каждые 20 минут рекомендации")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(20, result.intervalMinutes)
    }

    @Test
    fun `раз в 1 минуту присылай что делать`() = runTest {
        val result = useCase("раз в 1 минуту присылай что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(1, result.intervalMinutes)
    }

    @Test
    fun `каждые 60 минут пиши активность`() = runTest {
        val result = useCase("каждые 60 минут пиши активность")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(60, result.intervalMinutes)
    }

    @Test
    fun `через каждые 5 минут напоминай чем заняться`() = runTest {
        val result = useCase("через каждые 5 минут напоминай чем заняться")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(5, result.intervalMinutes)
    }

    @Test
    fun `привет как дела returns NONE`() = runTest {
        val result = useCase("привет как дела")
        assertEquals(ScheduleIntent.NONE, result.intent)
    }

    @Test
    fun `расскажи анекдот returns NONE`() = runTest {
        val result = useCase("расскажи анекдот")
        assertEquals(ScheduleIntent.NONE, result.intent)
    }

    @Test
    fun `what is the weather returns NONE`() = runTest {
        val result = useCase("what is the weather")
        assertEquals(ScheduleIntent.NONE, result.intent)
    }

    @Test
    fun `empty string returns NONE`() = runTest {
        val result = useCase("")
        assertEquals(ScheduleIntent.NONE, result.intent)
    }

    @Test
    fun `plain question returns NONE`() = runTest {
        val result = useCase("Сколько будет 2+2?")
        assertEquals(ScheduleIntent.NONE, result.intent)
    }

    @Test
    fun `case insensitive matching`() = runTest {
        val result = useCase("ПРИСЫЛАЙ РАЗ В 5 МИНУТ ЧЕМ ЗАНЯТЬСЯ")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(5, result.intervalMinutes)
    }

    @Test
    fun `раз в час`() = runTest {
        val result = useCase("раз в час подсказывай что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(60, result.intervalMinutes)
    }

    @Test
    fun `5 минут присылай чем заняться`() = runTest {
        val result = useCase("5 минут присылай чем заняться")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(5, result.intervalMinutes)
    }

    @Test
    fun `каждую минуту подсказывай что делать`() = runTest {
        val result = useCase("каждую минуту подсказывай что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(1, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `каждую минуту, подсказывая, что делать`() = runTest {
        val result = useCase("каждую минуту, подсказывая, что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(1, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `каждую секунду returns NONE or low confidence`() = runTest {
        val result = useCase("каждую секунду пиши что делать")
        if (result.intent == ScheduleIntent.SCHEDULE_BORED_REPORT) {
            assertTrue(result.intervalMinutes != null && result.intervalMinutes!! >= 1)
        }
    }

    @Test
    fun `каждые полчаса подсказывай`() = runTest {
        val result = useCase("каждые полчаса подсказывай чем заняться")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(30, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `каждую минуту показывай что мне делать`() = runTest {
        val result = useCase("каждую минуту показывай что мне делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(1, result.intervalMinutes)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `показывай раз в 10 минут чем заняться`() = runTest {
        val result = useCase("показывай раз в 10 минут чем заняться")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(10, result.intervalMinutes)
    }

    @Test
    fun `рекомендуй каждые 5 минут что делать`() = runTest {
        val result = useCase("рекомендуй каждые 5 минут что делать")
        assertEquals(ScheduleIntent.SCHEDULE_BORED_REPORT, result.intent)
        assertEquals(5, result.intervalMinutes)
    }
}
