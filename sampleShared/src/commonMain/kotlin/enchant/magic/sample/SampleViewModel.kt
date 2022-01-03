package enchant.magic.sample

import enchant.magic.StatusViewModel
import enchant.magic.issue
import kotlinx.coroutines.delay

class SampleViewModel: StatusViewModel<SampleViewModel.Key>(
    // debug = true  (Enables debug mode)
) {

    enum class Key { Submit } //A key for the "Submit" ViewModel status

    //Integer state
    var counter: Int by state(0)
    //String state with custom setter that makes the input all lowercase
    var input: String by state("", set = { value = it.lowercase()})

    fun submit() = series.add {
        //Observing the progress of the following code inside the "Submit" ViewModel status
        status(Key.Submit){
            delay(500)
            //Throwing issues to be caught by the status builder when input is invalid
            if(counter % 2 != 0) issue("Counter needs to be even", 1)
            if(input.isEmpty()) issue("Input needs to be filled in")
            //When the code in the status builder completes, it will output [Success] to the status
        }
    }

    init {
        // printChanges = true  (Prints all state and status changes when debug = true)
        series = CancelTentativeSeries() //Overrides the default series to prevent tapjacking
    }
}