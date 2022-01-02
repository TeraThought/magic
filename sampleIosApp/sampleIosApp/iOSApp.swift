import SwiftUI
import sampleShared

@main
struct iOSApp: App {
	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}

struct ContentView: View {
    
    @ViewModel var viewModel = InfoViewModel.Companion().instance
    
    var body: some View {
        Button("\(viewModel.counter)"){
            viewModel.counter += 1
        }
        TextField("Input", text: Binding(get: { viewModel.input }, set: { viewModel.input = $0} ))
            .multilineTextAlignment(.center)
        Button(submitString(viewModel.get(key: InfoViewModel.Key.submit))){
            viewModel.submit()
        }
        
    }
    func submitString(_ status: MagicStatus) -> String{
        let str: String
        switch status.type {
        case MagicStatusType.notstarted:
            str = "Haven't started"
        case MagicStatusType.loading:
            str = "Loading..."
        case MagicStatusType.issue:
            str = "Issue: \(status.message)"
        case MagicStatusType.success:
            str = "Yay! You passed."
        default:
            str = "impossible"
        }
        return str
    }
}

//Wrapper
@propertyWrapper struct ViewModel<T: MagicViewModel>: DynamicProperty {
    private var value: T
    @ObservedObject private var flager: Flager = Flager()

    var wrappedValue: T {
        get { return value }
        set { flager.add(wrappedValue); value = newValue }
    }
    init(wrappedValue: T){
        value = wrappedValue; flager.add(wrappedValue)
    }
}
class Flager: ObservableObject {
    @Published var flag: Bool = false
    func add(_ viewModel: MagicViewModel) {

        viewModel.addRefresh {self.flag = !self.flag }

    }
}
