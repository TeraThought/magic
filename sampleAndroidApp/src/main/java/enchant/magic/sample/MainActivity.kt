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
import enchant.magic.Status
import enchant.magic.ViewModel
import enchant.magic.sample.InfoViewModel.Key.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.fillMaxSize(), Alignment.Center) {

                val viewModel = viewModel(InfoViewModel.instance)

                Column {
                    Button(onClick = { viewModel.counter++ }) {
                        Text(viewModel.counter.toString())
                    }
                    TextField(value = viewModel.input, onValueChange = { viewModel.input = it })
                    Button(onClick = { viewModel.submit() }) {
                        Text(
                            when (viewModel[Submit]) {
                                is Status.NotStarted -> "Haven't started"
                                is Status.Loading -> "Loading..."
                                is Status.Issue -> "Issue: ${viewModel[Submit].message}"
                                is Status.Success -> "Yay! You passed."
                            }
                        )
                    }
                }
            }
        }
    }
}

//Wrapper
@Composable
fun <T : ViewModel> viewModel(viewModel: T): T {
    val scope: RecomposeScope = currentRecomposeScope
    return remember(viewModel) { viewModel.also { it.addRefresh { scope.invalidate() } } }
}