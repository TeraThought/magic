package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    val viewModel = SampleViewModel()

    @Test
    fun stateChange() {
        val output = mutableListOf<String>()
        viewModel.addRefresh { output += viewModel.name }
        for (i in "Ethan".indices) {
            viewModel.name = "Ethan".substring(0..i)
        }
        assertEquals(
            listOf("E", "Et", "Eth", "Etha", "Ethan"),
            output,
            "Check that the name state properly updated over time"
        )
    }

    @Test
    fun customStateChange() {
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        viewModel.customName = "ETHAN"
        assertEquals(1, refreshes, "Check that a refresh happened")
        assertEquals(
            "mr.ethan", viewModel.customName,
            "Check the  setter added Mr. to the name and the getter returned the string as lowercase"
        )
    }

    @Test
    fun eventRun() = runTest {
        var refreshes = 0
        viewModel.addRefresh { refreshes += 1 }
        viewModel.name = "Ethan"
        viewModel.reverseName()
        viewModel.await()
        assertEquals(2, refreshes, "Check two refreshes happened")
        assertEquals(
            "nahtE", viewModel.name,
            "Check the reverseName event only ran once under the default tentative series"
        )
    }

    @Test
    fun printToString() = runTest {
        viewModel.name = ""
        viewModel.customName = "Ethan"
        val string = viewModel.toString()
        val objectLabel = string.takeWhile { it != ' ' }
        val seriesLabel = string.split("\n")[3].takeWhile { it != ' ' }
        assertEquals(
            "$objectLabel states:\nname = \ncustomName = Mr.Ethan\n" +
                    "$seriesLabel has no running tasks", string,
            "Check the ViewModel converts to a string properly"
        )
    }

    @Test
    fun onClose() = runTest {
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        viewModel.name = "Ethan"
        viewModel.reverseName()
        viewModel.cancel()
        delay(50)

        assertEquals(1, refreshes, "Check a refresh happened")
        assertEquals(true, viewModel.onCloseActionRan, "Check the custom onClose action ran")
        assertEquals("Ethan", viewModel.name, "Check the reverseName event was canceled")
    }

    @Test
    fun await() = runTest {
        viewModel.name = ""
        viewModel.await {
            viewModel.name = "Ethan"
        }
        viewModel.name = "Ethan"
        viewModel.reverseName()
        viewModel.await()
        assertEquals("nahtE", viewModel.name)

        viewModel.reverseName()
        viewModel.await()
        assertEquals("Ethan", viewModel.name)
    }
}

class SampleViewModel : ViewModel(true) {

    var onCloseActionRan = false //Not a state, used to track when onClose {} runs
    var name by state("")
    var customName by state("", get = { value.lowercase() }, set = { value = "Mr.$it" })

    fun reverseName() {
        series.add {
            delay(25)
            name = name.reversed()
        }
    }

    init {
        coroutineContext.job.invokeOnCompletion { onCloseActionRan = true }
    }
}