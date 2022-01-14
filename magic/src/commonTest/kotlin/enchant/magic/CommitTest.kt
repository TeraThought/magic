package enchant.magic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CommitTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class MyViewModel : StatusViewModel<MyViewModel.Key>() {
        enum class Key { Key1, Key2, Key3 }

        var state1: Int by state(0)
        var state2: Int by state(0)
        var state3: Int by state(0)

        fun event1() = series.add {
            commit("state1", "state2", "state3") {
                delay(50)
                state1 = 1
                delay(50)
                state2 = 2
                delay(50)
                state3 = 3
                delay(50)
            }
        }

        fun event2() = series.add {
            commit("state1", Key.Key1, Key.Key2, "state2") {
                status(Key.Key1) {
                    state1 = 1
                }
                delay(50)
                status(Key.Key2) {
                    state2 = 2
                    issue("hi")
                }
                delay(50)
            }
        }
    }

    @Test
    fun testCommit() = runTest {
        val viewModel = MyViewModel()
        viewModel.event1()
        viewModel.await()
        assertEquals(listOf(1, 2, 3), listOf(viewModel.state1, viewModel.state2, viewModel.state3))

    }

    @Test
    fun testStatusCommit() = runTest {
        val viewModel = MyViewModel()
        viewModel.event2()
        viewModel.await()
        assertEquals(
            listOf(1, Success(), Issue("hi"), 0),
            listOf(
                viewModel.state1,
                viewModel[MyViewModel.Key.Key1],
                viewModel[MyViewModel.Key.Key2],
                viewModel.state3
            )
        )

    }

}