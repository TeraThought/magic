package enchant.magic


class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}
