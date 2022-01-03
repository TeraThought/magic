package enchant.magic.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import enchant.magic.ViewModel
import enchant.magic.*
//Allows status keys to accessed by just their name
import enchant.magic.sample.SampleViewModel.Key.*

// --------------- Add this utility to your project! --------------------
/**
 * Observes the provided [ViewModel] and will refresh the Compose UI hierarchy whenever a state or
 * status changes occurs within the ViewModel.
 * @param calculation A lambda that creates the ViewModel that will update the UI
 * @return The same ViewModel passed into [viewModel], to be stored and used
 *
 * ```
 * val viewModel = viewModel { MyViewModel() }
 * ```
 */
@Composable
fun <T : ViewModel> viewModel(calculation: () -> T): T {
    val scope: RecomposeScope = currentRecomposeScope
    return remember { calculation().also { it.addRefresh { scope.invalidate() } } }
}
// ----------------------------------------------------------------------

@Composable
fun InfoView() = Box(Modifier.fillMaxSize(), Alignment.Center) {

    //Access the current ViewModel using the [viewModel] utility
    val viewModel = viewModel { SampleViewModel() }

    Column {
        Button(onClick = { viewModel.counter++ }) {
            Text(viewModel.counter.toString())
        }
        TextField(value = viewModel.input, onValueChange = { viewModel.input = it })
        Button(onClick = { viewModel.submit() }) {
            Text(
                //Status keys can be shortened when imported properly (see above)
                when (viewModel[Submit]) {
                    //When imported properly, status variants can be referenced without "Status."
                    is NotStarted -> "Haven't started"
                    is Loading -> "Loading..."
                    is Issue -> "Issue: ${viewModel[Submit].message}"
                    is Success -> "Yay! You passed."
                }
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InfoView()
        }
    }
}