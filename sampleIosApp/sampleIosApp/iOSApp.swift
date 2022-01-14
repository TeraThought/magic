import SwiftUI
import sampleShared

// --------------- Add these utilities to your project! --------------------
/**
 A property wrapper which observes the provided ``MagicViewModel`` and will refresh the SwiftUI hierarchy whenever a state or
 status changes occurs within the ``MagicViewModel``.
 
 ```
 @ViewModel var viewModel = MyViewModel()
 ```
 - parameter wrappedValue: The initial ``MagicViewModel`` to be observed
 */

@propertyWrapper public struct ViewModel<T: MagicViewModel>: DynamicProperty {
    private var value: T
    @ObservedObject private var flager: Flager = Flager()

    public var wrappedValue: T {
        get { return value }
        set { flager.add(wrappedValue); value = newValue }
    }
    public init(wrappedValue: T){
        value = wrappedValue; flager.add(wrappedValue)
    }
    private class Flager: ObservableObject {
        @Published var flag: Bool = false
        func add(_ viewModel: MagicViewModel) {
            viewModel.addRefresh { self.flag = !self.flag }
        }
    }
}

typealias Status = MagicStatus
typealias StatusViewModel = MagicStatusViewModel
// ----------------------------------------------------------------------

//Allows access to status keys to be shortened
typealias Key = SampleViewModel.Key

struct InfoView: View {
    
    //Observe a ViewModel using the ViewModel utility
    @ViewModel var viewModel = SampleViewModel()
    
    var body: some View {
        Button("\(viewModel.counter)"){
            viewModel.counter += 1
        }
        TextField("Input", text: Binding(get: { viewModel.input }, set: { viewModel.input = $0} ))
            .multilineTextAlignment(.center)
        Button(submitString(viewModel.get(k: Key.submit))){
            viewModel.submit()
        }
    }
    func submitString(_ status: MagicStatus) -> String{
        let str: String
        switch status.type {
        case .notstarted:
            str = "Haven't started"
        case .loading:
            str = "Loading..."
        case .issue:
            str = "Issue: \(status.message)"
        case .success:
            str = "Yay! You passed."
        default:
            str = "impossible"
        }
        return str
    }
}

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            InfoView()
        }
    }
}
