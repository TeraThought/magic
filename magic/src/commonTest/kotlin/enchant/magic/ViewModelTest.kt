package enchant.magic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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


    @Test
    fun stateChange() {
        val viewModel = SampleViewModel()
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
        val viewModel = SampleViewModel()
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
        val viewModel = SampleViewModel()
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
        val viewModel = SampleViewModel()
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
        val viewModel = SampleViewModel()
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
        val viewModel = SampleViewModel()
        viewModel.name = ""
        viewModel.await {
            viewModel.name = "Ethan"
        }
        viewModel.name = "Ethan"
        viewModel.reverseName()
        viewModel.await()
        assertEquals("nahtE", viewModel.name)

        viewModel.await(3) {
            viewModel.reverseName()
            viewModel.reverseName()
            viewModel.reverseName()
        }
    }

    @Test
    fun equalSet() = runTest {
        val viewModel = SampleViewModel()
        viewModel.await {
            viewModel.name = "hi"
        }
        var updates = 0
        viewModel.addRefresh {
            updates++
        }
        viewModel.name = "hi"
        assertEquals(0, updates, "ViewModel should not refresh after state is set to same value")
    }

    @Test
    fun commit() = runTest {
        val viewModel = SampleViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        viewModel.sampleCommit("myname", "mycustomname")
        assertEquals(1, refreshes, "ViewModel should refresh once after a commit")
        refreshes = 0
        viewModel.sampleCommit("myname", "mycustomname")
        assertEquals(0, refreshes, "ViewModel should not refresh when no states have changed")
    }

    @Test
    fun flow() = runTest {
        val viewModel = SampleViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        assertEquals(viewModel.friends, 0, "Check the default value is zero")
        yield()
        assertEquals(
            1, viewModel.friends,
            "Check state's value matches the last emitted flow value"
        )
        assertEquals(1, refreshes, "Check that a refresh happened")
        yield()
        assertEquals(
            2, viewModel.friends,
            "Check state's value matches the last emitted flow value"
        )
        assertEquals(2, refreshes, "Check that a refresh happened")
    }

    @Test
    fun stateFlow() = runTest {
        val viewModel = SampleViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        assertEquals(viewModel.age, 0, "Check the default value is zero")
        viewModel.age = 10
        assertEquals(
            10, viewModel.ageStateFlow.value,
            "Check the StateFlow's value matches the state's value"
        )
        assertEquals(1, refreshes, "Check that a refresh happened")

        viewModel.ageStateFlow.value = 20
        assertEquals(
            20, viewModel.age,
            "Check the state's value matches the StateFlow's value"
        )
        yield()
        assertEquals(2, refreshes, "Check that a refresh happened")
    }
}

class SampleViewModel : ViewModel(true) {

    var onCloseActionRan = false //Not a state, used to track when onClose {} runs
    var name by state("")
    var customName by state("", get = { it.lowercase() }, set = { _, new -> "Mr.$new" })

    val ageStateFlow = MutableStateFlow(0)
    var age by state(ageStateFlow)

    val friends by state(flow { emit(1); yield(); emit(2) }, 0)

    fun reverseName() = series.add {
        delay(25)
        name = name.reversed()
    }

    fun sampleCommit(name: String, customName: String) {
        commit("name", "customName") {
            this.name = name
            this.customName = customName
        }
    }

    init {
        coroutineContext.job.invokeOnCompletion { onCloseActionRan = true }
    }
}