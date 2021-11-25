package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@InternalCoroutinesApi
class SeriesCancelTest {
    @Test
    fun defaultSeriesCancel() = runTest {
        val series = DefaultSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { cancel("Task 1 canceled"); delay(20); output += 1 }
        delay(5)

        assertEquals(false, job1.isActive, "Check task 1 is inactive")
        assertEquals(true, job1.isCancelled, "Check task 1 is cancelled")
        assertEquals(
            "Task 1 canceled", job1.getCancellationException().message,
            "Check the cancellation message of task 1 matches the thrown message"
        )
        assertEquals(true, job1.isCompleted, "Check task 1 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 1 has not been executed")

        val job2 = series.addJob { delay(20); output += 2 }
        series.cancel("Task 2 canceled")
        delay(5)

        assertEquals(false, job2.isActive, "Check task 2 is inactive")
        assertEquals(true, job2.isCancelled, "Check task 2 is cancelled")
        assertEquals(
            "Task 2 canceled", job2.getCancellationException().message,
            "Check the cancellation message of task 2 matches the thrown message"
        )
        assertEquals(true, job2.isCompleted, "Check task 2 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 2 has not been executed")

        var job3: Job = Job()
        val scope = CoroutineScope(coroutineContext + Job())
        scope.launch {
            val scopeSeries = DefaultSeries()
            job3 = scopeSeries.addJob { delay(20); output += 3 }
            delay(20)
        }
        delay(5)
        scope.cancel("Task 3 canceled")
        delay(5)

        assertEquals(false, job3.isActive, "Check task 3 is inactive")
        assertEquals(true, job3.isCancelled, "Check task 3 is cancelled")
        assertEquals(
            "Task 3 canceled", job3.getCancellationException().message,
            "Check the cancellation message of task 3 matches the thrown message"
        )
        assertEquals(true, job3.isCompleted, "Check task 3 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 3 has not been executed")
    }

    @Test
    fun queueSeriesCancel() = runTest {
        val series = QueueSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { cancel("Task 1 canceled"); delay(20); output += 1 }
        val job2 = series.addJob { delay(20); output += 2 }
        val job3 = series.addJob { delay(20); output += 3 }
        delay(5)

        job1.join()

        assertEquals(false, job1.isActive, "Check task 1 is inactive")
        assertEquals(true, job1.isCancelled, "Check task 1 is cancelled")
        assertEquals(
            "Task 1 canceled", job1.getCancellationException().message,
            "Check the cancellation message of task 1 matches the thrown message"
        )
        assertEquals(true, job1.isCompleted, "Check task 1 is completed")
        job2.join()
        assertEquals(listOf(2), output, "Check task 2 has been executed")
        job3.join()
        assertEquals(listOf(2, 3), output, "Check task 3 has been executed")


        val job4 = series.addJob { delay(20); output += 4 }
        val job5 = series.addJob { delay(20); output += 5 }
        val job6 = series.addJob { delay(20); output += 6 }
        var jobs = listOf(job4, job5, job6)
        series.cancel("Tasks 4, 5, 6 canceled")
        delay(5)

        assertEquals(
            listOf(false, false, false), jobs.map { it.isActive },
            "Check tasks 4, 5, 6 are inactive"
        )
        assertEquals(
            listOf(true, true, true), jobs.map { it.isCancelled },
            "Check tasks 4, 5, 6 are cancelled"
        )
        assertEquals(
            List(3) { "Tasks 4, 5, 6 canceled" },
            jobs.map { it.getCancellationException().message },
            "Check the cancellation messages of tasks 4, 5, 6 match the thrown message"
        )
        assertEquals(
            listOf(true, true, true), jobs.map { it.isCompleted },
            "Check tasks 4, 5, 6 are completed"
        )
        delay(60)
        assertEquals(listOf(2, 3), output, "Check tasks 4, 5, 6 have not been executed")

        var job7: Job = Job()
        var job8: Job = Job()
        var job9: Job = Job()

        val scope = CoroutineScope(coroutineContext + Job())
        scope.launch {
            val scopeSeries = QueueSeries()
            job7 = scopeSeries.addJob { delay(20); output += 7 }
            job8 = scopeSeries.addJob { delay(20); output += 8 }
            job9 = scopeSeries.addJob { delay(20); output += 9 }
            delay(20)
        }
        delay(5)
        scope.cancel("Tasks 7, 8, 9 canceled")
        delay(5)

        jobs = listOf(job7, job8, job9)
        assertEquals(
            listOf(false, false, false), jobs.map { it.isActive },
            "Check tasks 7, 8, 9 are inactive"
        )
        assertEquals(
            listOf(true, true, true), jobs.map { it.isCancelled },
            "Check tasks 7, 8, 9 are cancelled"
        )
        assertEquals(
            List(3) { "Tasks 7, 8, 9 canceled" },
            jobs.map { it.getCancellationException().message },
            "Check the cancellation messages of tasks 7, 8, 9 match the thrown message"
        )
        assertEquals(
            listOf(true, true, true), jobs.map { it.isCompleted },
            "Check tasks 7, 8, 9 are completed"
        )
        delay(60)
        assertEquals(listOf(2, 3), output, "Check tasks 7, 8, 9 have not been executed")
    }

    @Test
    fun cancelRunningSeriesCancel() = runTest {
        val series = CancelRunningSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { cancel("Task 1 canceled"); delay(20); output += 1 }
        delay(5)

        assertEquals(false, job1.isActive, "Check task 1 is inactive")
        assertEquals(true, job1.isCancelled, "Check task 1 is cancelled")
        assertEquals(
            "Task 1 canceled", job1.getCancellationException().message,
            "Check the cancellation message of task 1 matches the thrown message"
        )
        assertEquals(true, job1.isCompleted, "Check task 1 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 1 has not been executed")

        val job2 = series.addJob { delay(20); output += 2 }
        series.cancel("Task 2 canceled")
        delay(5)

        assertEquals(false, job2.isActive, "Check task 2 is inactive")
        assertEquals(true, job2.isCancelled, "Check task 2 is cancelled")
        assertEquals(
            "Task 2 canceled", job2.getCancellationException().message,
            "Check the cancellation message of task 2 matches the thrown message"
        )
        assertEquals(true, job2.isCompleted, "Check task 2 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 2 has not been executed")

        var job3: Job = Job()
        val scope = CoroutineScope(coroutineContext + Job())
        scope.launch {
            val scopeSeries = CancelRunningSeries()
            job3 = scopeSeries.addJob { delay(20); output += 3 }
            delay(20)
        }
        delay(5)
        scope.cancel("Task 3 canceled")
        delay(5)

        assertEquals(false, job3.isActive, "Check task 3 is inactive")
        assertEquals(true, job3.isCancelled, "Check task 3 is cancelled")
        assertEquals(
            "Task 3 canceled", job3.getCancellationException().message,
            "Check the cancellation message of task 3 matches the thrown message"
        )
        assertEquals(true, job3.isCompleted, "Check task 3 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 3 has not been executed")
    }

    @Test
    fun cancelTentativeSeriesCancel() = runTest {
        val series = CancelTentativeSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { cancel("Task 1 canceled"); delay(20); output += 1 }
        delay(5)

        assertEquals(false, job1.isActive, "Check task 1 is inactive")
        assertEquals(true, job1.isCancelled, "Check task 1 is cancelled")
        assertEquals(
            "Task 1 canceled", job1.getCancellationException().message,
            "Check the cancellation message of task 1 matches the thrown message"
        )
        assertEquals(true, job1.isCompleted, "Check task 1 is completed")
        delay(20)
        assertEquals(listOf(), output, "Check task 1 has not been executed")

        series.addJob { delay(20); output += 2 }
        delay(25)
        assertEquals(listOf(2), output, "Check task 2 has been executed")

        val job3 = series.addJob { delay(20); output += 3 }
        series.cancel("Task 3 canceled")
        delay(5)

        assertEquals(false, job3.isActive, "Check task 3 is inactive")
        assertEquals(true, job3.isCancelled, "Check task 3 is cancelled")
        assertEquals(
            "Task 3 canceled", job3.getCancellationException().message,
            "Check the cancellation message of task 3 matches the thrown message"
        )
        assertEquals(true, job3.isCompleted, "Check task 3 is completed")
        delay(20)
        assertEquals(listOf(2), output, "Check task 3 has not been executed")

        var job4: Job = Job()
        val scope = CoroutineScope(coroutineContext + Job())
        scope.launch {
            val scopeSeries = CancelTentativeSeries()
            job4 = scopeSeries.addJob { delay(20); output += 4 }
            delay(20)
        }
        delay(5)
        scope.cancel("Task 4 canceled")
        delay(5)

        assertEquals(false, job4.isActive, "Check task 4 is inactive")
        assertEquals(true, job4.isCancelled, "Check task 4 is cancelled")
        assertEquals(
            "Task 4 canceled", job4.getCancellationException().message,
            "Check the cancellation message of task 4 matches the thrown message"
        )
        assertEquals(true, job3.isCompleted, "Check task 4 is completed")
        delay(20)
        assertEquals(listOf(2), output, "Check task 4 has not been executed")
    }
}