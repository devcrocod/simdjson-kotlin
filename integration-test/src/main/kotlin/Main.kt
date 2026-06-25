import io.github.devcrocod.simdjson.JsonNumber
import io.github.devcrocod.simdjson.JsonObject
import io.github.devcrocod.simdjson.JsonString
import io.github.devcrocod.simdjson.SimdJsonParser

fun main() {
    SimdJsonParser().use { parser ->
        val root = parser.parse("""{"hello":"world","n":42}""") as JsonObject
        val hello = (root["hello"] as JsonString).value
        val n = (root["n"] as JsonNumber).toInt()
        check(hello == "world") { "hello mismatch: $hello" }
        check(n == 42) { "n mismatch: $n" }
        println("integration OK")
    }
}
