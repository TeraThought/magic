package enchant.magic

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesPrintTest {
    @Test
    fun seriesNonDebuggable() = runTest {
        listOf(
            "DefaultSeries" to DefaultSeries(),
            "QueueSeries" to QueueSeries(),
            "CancelRunningSeries" to CancelRunningSeries(),
            "CancelTentativeSeries" to CancelTentativeSeries()
        ).forEach {
            val (label, series) = it
            series.add("Task 1") { delay(10) }
            val string = series.toString()
            assertEquals(
                "enchant.magic.$label@", string.substring(0, 15 + label.length),
                "Check the $label string contains the full package"
            )
        }
    }

    @Test
    fun defaultSeriesToString() = runTest {
        val series = DefaultSeries(true)
        val objectId = series.toString()
            .dropWhile { it != '@' }.substring(1).takeWhile { it != ' ' }
        var string = series.toString()
        assertEquals(
            "DefaultSeries@$objectId has no running tasks", string,
            "Check the series has no tasks running"
        )

        series.add("Task 1") { delay(70) }
        delay(30)
        string = series.toString()
        var time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "DefaultSeries@$objectId current tasks:\n\"Task 1\" - ** ms", string,
            "Check Task 1 is displayed correctly"
        )

        series.add { delay(85) }
        delay(30)
        string = series.toString()
        val (task1Output, noIdOutput) = string.split("\n").run { get(1) to get(2) }

        time = task1Output.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")

        time = noIdOutput.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")

        assertEquals(
            "DefaultSeries@$objectId current tasks:\n\"Task 1\" - ** ms\n\"noId\" - ** ms",
            string, "Check Task 1 and noId are displayed correctly"
        )

        delay(40)

        string = series.toString()
        time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "DefaultSeries@$objectId current tasks:\n\"noId\" - ** ms",
            string, "Check noId is displayed correctly"
        )
        series.addJob { }.cancel()
        delay(30)

        string = series.toString()
        assertEquals(
            "DefaultSeries@$objectId has no running tasks", string,
            "Check the series has no tasks running"
        )
    }

    @Test
    fun queueSeriesToString() = runTest {
        val series = QueueSeries(true)
        val objectId = series.toString()
            .dropWhile { it != '@' }.substring(1).takeWhile { it != ' ' }
        var string = series.toString()
        assertEquals(
            "QueueSeries@$objectId has no queued tasks", string,
            "Check the series has no tasks queued"
        )

        val task1Job = series.addJob("Task 1") { delay(60) }
        delay(30)

        string = series.toString()
        var time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "QueueSeries@$objectId queued tasks:\n\"Task 1\" - ** ms", string,
            "Check that Task 1 is displayed correctly"
        )

        series.add { delay(60) }
        val task3Job = series.addJob("Task 3") { }
        delay(20)

        string = series.toString()
        val task1Output = string.split("\n")[1]

        time = task1Output.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "QueueSeries@$objectId queued tasks:\n\"Task 1\" - ** ms\n\"noId\"\n\"Task 3\"",
            string,
            "Check that Task 1, noId and Task 3 are displayed correctly"
        )

        task1Job.cancel()
        delay(50)

        string = series.toString()
        val noIdOutput = string.split("\n")[1]
        time = noIdOutput.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "QueueSeries@$objectId queued tasks:\n\"noId\" - ** ms\n\"Task 3\"",
            string,
            "Check that noId and Task 3 are displayed correctly"
        )
        task3Job.cancel()
        delay(40)

        string = series.toString()
        assertEquals(
            "QueueSeries@$objectId has no queued tasks", string,
            "Check the series has no tasks queued"
        )
    }

    @Test
    fun cancelRunningSeriesToString() = runTest {
        val series = CancelRunningSeries(true)
        val objectId = series.toString()
            .dropWhile { it != '@' }.substring(1).takeWhile { it != ' ' }
        var string = series.toString()
        assertEquals(
            "CancelRunningSeries@$objectId has no running task", string,
            "Check the series has no tasks running"
        )
        series.add("Task 1") { delay(40) }
        delay(20)

        string = series.toString()
        var time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "CancelRunningSeries@$objectId running task:\n\"Task 1\" - ** ms", string,
            "Check Task 1 is displayed properly"
        )

        val noIdJob = series.addJob { delay(40) }
        delay(20)
        string = series.toString()
        time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "CancelRunningSeries@$objectId running task:\n\"noId\" - ** ms", string,
            "Check noId is displayed properly"
        )

        noIdJob.cancel()
        delay(20)

        string = series.toString()
        assertEquals(
            "CancelRunningSeries@$objectId has no running task", string,
            "Check the series has no tasks running"
        )
    }

    @Test
    fun cancelTentativeSeriesToString() = runTest {
        val series = CancelTentativeSeries(true)
        val objectId = series.toString()
            .dropWhile { it != '@' }.substring(1).takeWhile { it != ' ' }
        var string = series.toString()
        assertEquals(
            "CancelTentativeSeries@$objectId has no running task", string,
            "Check the series has no tasks running"
        )

        series.add("Task 1") { delay(60) }
        delay(20)

        string = series.toString()
        var time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "CancelTentativeSeries@$objectId running task:\n\"Task 1\" - ** ms", string,
            "Check Task 1 is displayed properly"
        )

        series.add { delay(40) }
        delay(20)
        string = series.toString()
        time = string.takeLast(6).take(4).filter { it.isDigit() }.toInt()
        string = string.replace(" $time ", " ** ")
        assertEquals(
            "CancelTentativeSeries@$objectId running task:\n\"Task 1\" - ** ms", string,
            "Check Task 1 is displayed properly"
        )
        delay(30)

        string = series.toString()
        assertEquals(
            "CancelTentativeSeries@$objectId has no running task", string,
            "Check the series has no tasks running"
        )

        series.addJob("Task 3") { delay(10) }.cancel()
        delay(10)
        string = series.toString()
        assertEquals(
            "CancelTentativeSeries@$objectId has no running task", string,
            "Check the series has no tasks running"
        )
    }


}