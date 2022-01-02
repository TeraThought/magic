package enchant.magic.sample

import enchant.magic.StatusViewModel
import enchant.magic.issue
import kotlinx.coroutines.delay

class InfoViewModel: StatusViewModel<InfoViewModel.Key>(debug = true) {
    enum class Key { Submit }

    var counter: Int by state(0)
    var input: String by state("")

    fun submit() = series.add {
        singleStatus(Key.Submit){
            delay(500)
            if(counter % 2 != 0) issue("Counter needs to be even", 1)
            if(input.isEmpty()) issue("Input needs to be filled in")
        }
    }

    init {
        printChanges = true
        series = CancelTentativeSeries()
    }
    companion object {
        val instance by lazy { InfoViewModel() }
    }
}