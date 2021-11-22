package enchant.magic

import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesAddTest {

    @Test
    fun defaultSeriesAdd() = runTest {
        val series = DefaultSeries()
        val output = mutableListOf<Int>()

        series.add { delay(10); output += 1 }
        series.add { delay(10); output += 2 }
        series.add { delay(10); output += 3 }
        delay(2) //Ensures tasks have been scheduled

        assertEquals(listOf(), output, "Check no series tasks have finished yet")
        delay(10)
        assertEquals(3, output.size, "Check all series tasks have finished")
    }

    @Test
    fun queueSeriesAdd() = runTest {
        val series = QueueSeries()
        val output = mutableListOf<Int>()

        series.add { delay(10); output += 1 }
        series.add { delay(10); output += 2 }
        series.add { delay(10); output += 3 }
        delay(5) //Ensures tasks have been scheduled

        assertEquals(listOf(), output, "Check no series tasks have finished yet")
        delay(10)
        assertEquals(listOf(1), output, "Check the only finished task is task 1")
        delay(10)
        assertEquals(listOf(1, 2), output, "Check task 2 has finished after task 1")
        delay(15)
        assertEquals(listOf(1, 2, 3), output, "Check task 3 has finished after task 2 and task 1")
    }

    @Test
    fun cancelRunningSeriesAdd() = runTest {
        val series = CancelRunningSeries()
        val output = mutableListOf<Int>()

        series.add { delay(10); output += 1 }
        series.add { delay(10); output += 2 }
        series.add { delay(10); output += 3 }
        delay(2) //Ensures tasks have been scheduled

        assertEquals(listOf(), output, "Check no series tasks have finished yet")
        delay(10)
        assertEquals(listOf(3), output, "Check the only finished task is task 3")
    }

    @Test
    fun cancelTentativeSeriesAdd() = runTest {
        val series = CancelTentativeSeries()
        val output = mutableListOf<Int>()

        series.add { delay(10); output += 1 }
        series.add { delay(10); output += 2 }
        series.add { delay(10); output += 3 }
        delay(2) //Ensures tasks have been scheduled

        assertEquals(listOf(), output, "Check no series tasks have finished yet")
        delay(10)
        assertEquals(listOf(1), output, "Check the only finished task is task 1")
    }
}