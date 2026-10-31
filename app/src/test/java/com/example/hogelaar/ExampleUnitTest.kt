package com.example.hogelaar

import org.junit.Test

import org.junit.Assert.*
import java.time.LocalDate

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun weekStartsOnNextMondayFromSaturday() {
        assertEquals(
            LocalDate.of(2026, 11, 2),
            weekStartFor(LocalDate.of(2026, 10, 31))
        )
    }

    @Test
    fun weekStartsOnCurrentMondayThroughFriday() {
        assertEquals(
            LocalDate.of(2026, 10, 26),
            weekStartFor(LocalDate.of(2026, 10, 30))
        )
    }
}