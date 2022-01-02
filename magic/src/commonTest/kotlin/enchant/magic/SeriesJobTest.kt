package enchant.magic

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesJobTest {
    @Test
    fun defaultSeriesJob() = runTest {
        val series = DefaultSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { delay(20); output += 1 }
        val job2 = series.addJob { delay(20); output += 2 }
        val job3 = series.addJob { delay(20); output += 3 }
        val jobs = listOf(job1, job2, job3)
        delay(5) //Ensures tasks have been scheduled

        assertEquals(listOf(), output, "Check no series tasks have finished yet")
        assertEquals(
            listOf(true, true, true), jobs.map { it.isActive }, "Check all tasks are active"
        )
        assertEquals(
            listOf(false, false, false), jobs.map { it.isCancelled },
            "Check all tasks are not cancelled"
        )
        assertEquals(
            listOf(false, false, false), jobs.map { it.isCompleted },
            "Check all tasks are not completed"
        )

        job1.cancel()
        job2.cancel()
        delay(5) //Ensures cancellation happened

        assertEquals(
            listOf(false, false, true), jobs.map { it.isActive },
            "Check task 3 is active"
        )
        assertEquals(
            listOf(true, true, false), jobs.map { it.isCancelled },
            "Check task 1 and task 2 are cancelled"
        )
        assertEquals(
            listOf(true, true, false), jobs.map { it.isCompleted },
            "Check task 1 and task 2 are completed"
        )

        delay(20)
        assertEquals(listOf(3), output, "Check task 3 has finished executing")

        assertEquals(
            listOf(false, false, false), jobs.map { it.isActive },
            "Check all tasks are inactive"
        )
        assertEquals(
            listOf(true, true, false), jobs.map { it.isCancelled },
            "Check task 1 and task 2 have been cancelled and task 3 is not cancelled"
        )
        assertEquals(
            listOf(true, true, true), jobs.map { it.isCompleted },
            "Check all tasks are completed"
        )
    }

    @Test
    fun queueSeriesJob() = runTest {
        val series = QueueSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { delay(20); output += 1 }
        val job2 = series.addJob { delay(20); output += 2 }
        val job3 = series.addJob { delay(20); output += 3 }
        val jobs = listOf(job1, job2, job3)
        delay(5) //Ensures tasks have been scheduled

        assertEquals(listOf(), output, "Check no series tasks have finished yet")
        assertEquals(
            listOf(true, true, true), jobs.map { it.isActive },
            "Check all tasks are recorded as active"
        ) //Task 1 should only be active but due to limitations, all jobs have to be recorded "active"
        assertEquals(
            listOf(false, false, false), jobs.map { it.isCancelled },
            "Check all tasks are not cancelled"
        )
        assertEquals(
            listOf(false, false, false), jobs.map { it.isCompleted },
            "Check all tasks are not completed"
        )

        job1.cancel()
        job2.cancel()
        delay(5) //Ensures cancellation happened
        delay(5) //Ensures task 3 started

        assertEquals(
            listOf(false, false, true), jobs.map { it.isActive },
            "Check task 3 is active"
        )
        assertEquals(
            listOf(true, true, false), jobs.map { it.isCancelled },
            "Check task 1 and task 2 are cancelled"
        )
        assertEquals(
            listOf(true, true, false), jobs.map { it.isCompleted },
            "Check task 1 and task 2 are completed"
        )

        delay(20)
        assertEquals(listOf(3), output, "Check task 3 has finished executing")

        assertEquals(
            listOf(false, false, false), jobs.map { it.isActive },
            "Check all tasks are inactive"
        )
        assertEquals(
            listOf(true, true, false), jobs.map { it.isCancelled },
            "Check task 1 and task 2 are cancelled and task 3 is not cancelled"
        )
        assertEquals(
            listOf(true, true, true),
            jobs.map { it.isCompleted },
            "Check all tasks are completed"
        )
    }

    @Test
    fun cancelRunningSeriesJob() = runTest {
        val series = CancelRunningSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { delay(20); output += 1 }
        delay(5) //Ensures task has been scheduled

        assertEquals(listOf(), output, "Check task 1 has not been executed")
        assertEquals(true, job1.isActive, "Check task 1 is active")
        assertEquals(false, job1.isCancelled, "Check task 1 is not cancelled")
        assertEquals(false, job1.isCompleted, "Check task 1 is not completed")

        val job2 = series.addJob { delay(20); output += 2 }
        delay(5) //Ensures task has been scheduled

        assertEquals(listOf(), output, "Check task 1 has not been executed")
        assertEquals(false, job1.isActive, "Check task 1 is inactive")
        assertEquals(true, job1.isCancelled, "Check task 1 is cancelled")
        assertEquals(true, job1.isCompleted, "Check task 1 is completed")

        assertEquals(true, job2.isActive, "Check task 2 is active")
        assertEquals(false, job2.isCancelled, "Check task 2 is not cancelled")
        assertEquals(false, job2.isCompleted, "Check task 2 is not completed")

        job2.cancel()
        delay(5) //Ensures task has been canceled

        assertEquals(listOf(), output, "Check task 2 has not been executed")
        assertEquals(false, job2.isActive, "Check task 2 is inactive")
        assertEquals(true, job2.isCancelled, "Check task 2 is cancelled")
        assertEquals(true, job2.isCompleted, "Check task 2 is completed")

        val job3 = series.addJob { delay(20); output += 3 } //runs
        delay(5) //Ensures task has been scheduled

        assertEquals(true, job3.isActive, "Check task 3 is active")
        assertEquals(false, job3.isCancelled, "Check task 3 is not cancelled")
        assertEquals(false, job3.isCompleted, "Check task 3 is not completed")

        delay(20)
        assertEquals(listOf(3), output, "Check task 3 has executed")
        assertEquals(false, job3.isActive, "Check task 3 is not active")
        assertEquals(false, job3.isCancelled, "Check task 3 is not cancelled")
        assertEquals(true, job3.isCompleted, "Check task 3 is completed")
    }

    @Test
    fun cancelTentativeSeriesJob() = runTest {
        val series = CancelTentativeSeries()
        val output = mutableListOf<Int>()

        val job1 = series.addJob { delay(50); output += 1 }
        delay(5) //Ensures task has been scheduled

        assertEquals(listOf(), output, "Check task 1 has not been executed")
        assertEquals(true, job1.isActive, "Check task 1 is active")
        assertEquals(false, job1.isCancelled, "Check task 1 is not cancelled")
        assertEquals(false, job1.isCompleted, "Check task 1 is not completed")

        val job2 = series.addJob { delay(20); output += 2 }
        delay(5) //Ensures task has been scheduled

        assertEquals(listOf(), output, "Check task 2 has not been executed")
        assertEquals(false, job2.isActive, "Check task 2 is inactive")
        assertEquals(true, job2.isCancelled, "Check task 2 is cancelled")
        assertEquals(true, job2.isCompleted, "Check task 2 is completed")

        assertEquals(true, job1.isActive, "Check task 1 is active")
        assertEquals(false, job1.isCancelled, "Check task 1 is not cancelled")
        assertEquals(false, job1.isCompleted, "Check task 1 is not completed")

        job1.cancel()
        delay(5) //Ensures task has been canceled

        assertEquals(listOf(), output, "Check task 1 has not been executed")
        assertEquals(false, job1.isActive, "Check task 1 is inactive")
        assertEquals(true, job1.isCancelled, "Check task 1 is cancelled")
        assertEquals(true, job1.isCompleted, "Check task 1 is completed")

        val job3 = series.addJob { delay(20); output += 3 } //runs
        delay(5) //Ensures task has been scheduled

        assertEquals(true, job3.isActive, "Check task 3 is active")
        assertEquals(false, job3.isCancelled, "Check task 3 is not cancelled")
        assertEquals(false, job3.isCompleted, "Check task 3 is not completed")

        delay(20)
        assertEquals(listOf(3), output, "Check task 3 has executed")
        assertEquals(false, job3.isActive, "Check task 3 is not active")
        assertEquals(false, job3.isCancelled, "Check task 3 is not cancelled")
        assertEquals(true, job3.isCompleted, "Check task 3 is completed")
    }
}