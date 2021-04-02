import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun main() {
    println("main thread run over here")
    repeat(3) {
        println("job1")
    }
    repeat(3) {
        println("job2")
    }
}

public fun changeCount(name: String, count: Count) {

    println("$name: ${count.count++}")
}

class Count(var count: Int)