# Overview
Learn a bit about more of Magic's innovative APIs 

## Series
Kotlin CoroutineScopes by default are useful, but cannot control the order of execution of added coroutines and do not have much debugging behavior. Series
solves these problems by providing cohesive APIs for executing coroutines in different orders and viewing running coroutines.

```kotlin
val series = QueueSeries() //Executes added coroutines one at a time
val firstJob = series.addJob { delay(20) println("#1 done") }
val secondJob = series.addJob { delay(10) println("#2 done") }

//prints #1 done, then #2 done

```


## ViewModel

Most ViewModel libraries focus more on state abstraction than developer efficiency. However, Magic's ViewModel API simplifies the process of making a
ViewModel down to short, readable lines of code. It comes with its own CoroutineScope, observable refresh system, and embeds a series for more efficient
workflows.

```kotlin
class MyViewModel: ViewModel() {
  var name by state("")
  var age by state(0)

  fun uploadInfo() = series.add {
    model.uploadInfo(name, data)
  }
  
  init {
    series = CancelTentativeSeries() //Prevents multiple calls from starting multiple operations
  }
}
```

## Status

A opinionated data structure for tracking the progress of operations. It comes with four different variants: `NotStarted`, `Loading`, `Success` and `Issue`.

## StatusViewModel

An extension of `ViewModel` that smoothly integrates `Status`. `StatusViewModel` automatically bundles status states together (via the status map)
for simple maintainability and readability. `StatusViewModel` also automatically creates `Status` objects from running code and updates status states.

```kotlin
class MyStatusViewModel: StatusViewModel<MyStatusViewModel.Key>() {

  //Status states can be accessed by the View with viewModel[Key.Name] or viewModel.get(Key.Name)
  enum class Key { Name, Upload }

  var name by state("")
  var age by state(0)

  //Validates the user's name and records the result (without intermediary loading) in the "Name" status
  fun validateName() = series.add {
    status(Key.Name, setLoading = false) {
      if(name.isEmpty()) error("Name cannot be empty")
    }
  }

  //Uploads user data and tracks its progress in the "Upload" status
  fun upload() = series.add {
    if(get(Key.Name) is Success) status(Upload) {
      model.uploadInfo(name, age) //Assumes model function throws an error if it fails
    }
  }
}
```
## Coming Soon!
* Native bindings to use ViewModels within iOS and Android apps
* More examples and use-cases to demonstrate `Series`, `ViewModel`, `Status` and `StatusViewModel`
* More primitives to solve structural problems in Kotlin Multiplatform
