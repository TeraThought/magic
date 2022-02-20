package enchant.magic

import enchant.magic.SampleStatusViewModel.Key.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

class StatusViewModelTest {

    @BeforeTest
    fun setUp(){
        Dispatchers.setMain(StandardTestDispatcher())
    }
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getStatuses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        assertIs<NotStarted>(viewModel[Name], "Check the name status is NotStarted")
        assertIs<NotStarted>(viewModel[Upload], "Check the upload status is NotStarted")
        assertEquals(0, refreshes, "Check no refreshes happened")
    }

    @Test
    fun runEvent() = runTest {
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = "Ethan"
        viewModel.uploadName()
        viewModel.await()
        assertIs<Success>(viewModel[Name], "Check the name status is Success")
        assertIs<Loading>(viewModel[Upload], "Check the upload status is Loading")
        viewModel.await()
        assertIs<Success>(viewModel[Upload], "Check the upload status is Success")
        assertEquals(4, refreshes, "Check that 4 refreshes happened")
    }

    @Test
    fun runIssueEvent() = runTest {
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = ""
        viewModel.uploadName()
        viewModel.await()
        assertTrue(
            viewModel[Name].code == 1,
            "Check the name status is Issue and has the error code 1"
        )
        assertIs<NotStarted>(viewModel[Upload], "Check the upload status is NotStarted")

        viewModel.name = "Vikram"
        viewModel.uploadName()
        viewModel.await()
        assertEquals(
            2, viewModel[Name].code,
            "Check the name status is Issue and has the error code 2"
        )
        assertIs<NotStarted>(viewModel[Upload], "Check the upload status is NotStarted")

    }

    @Test
    fun runSingleStatusEvent() = runTest {
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = ""
        viewModel.age = -1

        viewModel.validateInfo()
        viewModel.await()

        assertIs<Issue>(viewModel[Name], "Check the name status is Issue")
        assertIs<Issue>(viewModel[Age], "Check the age status is Issue")

        viewModel.name = "Ethan"
        viewModel.age = 1
        viewModel.validateInfo()
        viewModel.await()

        assertIs<Success>(viewModel[Name], "Check the name status is Success")
        assertIs<Success>(viewModel[Age], "Check the age status is Success")
    }

    @Test
    fun onClose() = runTest {
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = "Ethan"
        viewModel.uploadName()
        delay(5)
        viewModel.cancel()
        delay(80)

        assertIs<Loading>(
            viewModel[Upload],
            "Check the upload status is Loading (what it originally was before the cancellation)"
        )
        assertEquals(3, refreshes, "Check that 2 refreshes happened")
    }

    @Test
    fun printToString() = runTest {
        val viewModel = SampleStatusViewModel()
        viewModel.name = "Jeff"
        viewModel.age = 20
        viewModel.validateInfo()
        viewModel.uploadName()
        delay(40)
        var string = viewModel.toString()
        println(string)
        val objectLabel = string.takeWhile { it != ' ' }
        val seriesLabel = string.split("\n")[6].takeWhile { it != ' ' }
        val time = string.takeLast(5).take(2).trim()
        println("time = ${time}")
        string = string.replace("\\s$time\\s".toRegex()," ** ")
        assertEquals(
            "$objectLabel states and statuses:\nname = Jeff\nage = 20\n[Age] = Success\n" +
                    "[Name] = Success\n" + "[Upload] = Loading(progress=-1.0)\n$seriesLabel current " +
                    "tasks:\n\"noId\" - ** ms",
            string,
            "Check that the ViewModel is converted to string properly"
        )
    }

    @Test
    fun equalSet() = runTest {
        val viewModel = SampleStatusViewModel()
        viewModel.name = "Dan"
        viewModel.age = 12
        viewModel.validateInfo()
        yield()
        var refreshes = 0
        viewModel.addRefresh {
            refreshes++
        }
        viewModel.validateInfo()
        yield()
        assertEquals(0, refreshes, "ViewModel should not refresh is a status is set to the same value")
    }

    @Test
    fun commit() = runTest {
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        viewModel.commit(Success(), Success())
        assertEquals(1, refreshes, "ViewModel should refresh once after commit")
        refreshes = 0
        viewModel.commit(Success(), Success())
        assertEquals(0, refreshes, "ViewModel should not refresh after no committed changes")
    }

    @Test
    fun stateFlowStatus() = runTest {
        val viewModel = SampleStatusViewModel()
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }
        viewModel.signUp()
        yield()
        assertIs<Issue>(viewModel[SignedUp], "Check the status state is Issue")
        assertEquals(2, refreshes, "ViewModel should have refreshed twice")
    }
}

class SampleStatusViewModel : StatusViewModel<SampleStatusViewModel.Key>(true) {
    var name by state("")
    var age by state(-1)
    private val signedUp = MutableStateFlow<Status>(NotStarted())

    enum class Key {
        // Input validation for the name
        Name,

        // Input validation for the age
        Age,

        // Tracks uploading the name to the database
        Upload,

        //Tracked via StateFlow
        SignedUp
    }


    fun uploadName() = series.add {
        status(Name, loading = false) {
            if (name.isEmpty()) error("Name is invalid")
            if (name.length > 5) error("Name is too long")
        }
        status(Upload) {
            delay(50)
            //Always completes successfully
        }
    }

    fun validateInfo() = series.add {
        status(Name, loading = false, throws = false) {
            if (name.isEmpty()) error("Name is invalid")
        }
        status(Age, loading = false, throws = false) {
            if (age < 0) error("Age is invalid")
        }
    }

    fun signUp() = series.add {
        status(SignedUp) {
            issue("Invalid sign up",2)
        }
    }

    /* Customizing the result mapping; this is usually done in a "parent" ViewModel that other
    * ViewModels extend (so that mapResult does not need to be overridden for all functions).
    * */
    override fun mapResult(result: Result<Unit>): Status = when (true) {
        result.isSuccess -> Success()
        result.exceptionOrNull()!!.message == "Name is invalid" -> Issue(code = 1)
        else -> Issue(code = 2)
    }

    fun commit(name: Status, age: Status) {
        commit(Name, Age) {
            statuses[Name] = name
            statuses[Age] = age
        }
    }

    init {
        statuses.setStateFlows(mapOf(SignedUp to signedUp))
    }
}
