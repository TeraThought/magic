package enchant.magic

import enchant.magic.SampleStatusViewModel.Key.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StatusViewModelTest {

    var viewModel = SampleStatusViewModel()

    @Test
    fun getStatuses() {
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        assertIs<NotStarted>(viewModel[Name], "Check the name status is NotStarted")
        assertIs<NotStarted>(viewModel[Upload], "Check the upload status is NotStarted")
        assertEquals(0, refreshes, "Check no refreshes happened")

    }

    @Test
    fun runEvent() = runTest {
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = "Ethan"
        viewModel.uploadName()
        delay(10)
        assertIs<Success>(viewModel[Name], "Check the name status is Success")
        assertIs<Loading>(viewModel[Upload], "Check the upload status is Loading")
        delay(50)
        assertIs<Success>(viewModel[Upload], "Check the upload status is Success")
        assertEquals(4, refreshes, "Check that 4 refreshes happened")
    }

    @Test
    fun runIssueEvent() = runTest {
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = ""
        viewModel.uploadName()
        delay(20)
        assertTrue(
            viewModel[Name].code == 1,
            "Check the name status is Issue and has the error code 1"
        )
        assertIs<NotStarted>(viewModel[Upload], "Check the upload status is NotStarted")

        viewModel.name = "Vikram"
        viewModel.uploadName()
        delay(10)
        assertEquals(
            2, viewModel[Name].code,
            "Check the name status is Issue and has the error code 2"
        )
        assertIs<NotStarted>(viewModel[Upload], "Check the upload status is NotStarted")

        assertEquals(4, refreshes, "Check that 4 refreshes happened")
    }

    @Test
    fun runSingleStatusEvent() = runTest {
        var refreshes = 0
        viewModel.addRefresh { refreshes++ }

        viewModel.name = ""
        viewModel.age = -1

        viewModel.validateInfo()
        delay(10)

        assertIs<Issue>(viewModel[Name], "Check the name status is Issue")
        assertIs<Issue>(viewModel[Age], "Check the age status is Issue")

        viewModel.name = "Ethan"
        viewModel.age = 1
        viewModel.validateInfo()
        delay(10)

        assertIs<Success>(viewModel[Name], "Check the name status is Success")
        assertIs<Success>(viewModel[Age], "Check the age status is Success")

        assertEquals(8, refreshes, "Check that 8 refreshes happened")
    }

    @Test
    fun onClose() = runTest {
        viewModel = SampleStatusViewModel()
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
        viewModel.name = "Jeff"
        viewModel.age = 20
        viewModel.validateInfo()
        viewModel.uploadName()
        delay(40)
        var string = viewModel.toString()
        println(string)
        val objectLabel = string.takeWhile { it != ' ' }
        val seriesLabel = string.split("\n")[6].takeWhile { it != ' ' }
        val time = string.takeLast(6).take(4).trim()
        string = string.replace(time, "**")
        assertEquals(
            "$objectLabel states and statuses:\nname = Jeff\nage = 20\n[Age] = Success\n" +
                    "[Name] = Success\n" + "[Upload] = Loading(progress=-1.0)\n$seriesLabel current " +
                    "tasks:\n\"noId\" - ** ms",
            string,
            "Check that the ViewModel is converted to string properly"
        )
    }
}

class SampleStatusViewModel : StatusViewModel<SampleStatusViewModel.Key>(true) {

    var name by state("")
    var age by state(-1)

    enum class Key {
        // Input validation for the name
        Name,

        // Input validation for the age
        Age,

        // Tracks uploading the name to the database
        Upload
    }

    fun uploadName() = series.add {
        status(Name, setLoading = false) {
            if (name.isEmpty()) error("Name is invalid")
            if (name.length > 5) error("Name is too long")
        }
        status(Upload) {
            delay(50)
            //Always completes successfully
        }
    }

    fun validateInfo() = series.add {
        singleStatus(Name, setLoading = false) {
            if (name.isEmpty()) error("Name is invalid")
        }
        singleStatus(Age, setLoading = false) {
            if (age < 0) error("Age is invalid")
        }
    }

    /* Customizing the result mapping; this is usually done in a "parent" ViewModel that other
    * ViewModels extend (so that mapResult does not need to be overridden for all functions).
    * */
    override fun mapResult(result: Result<Unit>): Status = when (true) {
        result.isSuccess -> Success()
        result.exceptionOrNull()!!.message == "Name is invalid" -> Issue(code = 1)
        else -> Issue(code  = 2)
    }
}
