package io.github.devcrocod.simdjson.benchmarks

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.devcrocod.simdjson.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = ["--add-modules=jdk.incubator.vector", "-Dsimdjson.species=256"])
open class ParseAndSelectBenchmark {

    private lateinit var simdJsonParser: SimdJsonParser
    private lateinit var objectMapper: ObjectMapper
    private lateinit var buffer: ByteArray
    private lateinit var bufferPadded: ByteArray

    @Setup(Level.Trial)
    fun setup() {
        buffer = ParseAndSelectBenchmark::class.java
            .getResourceAsStream("/twitter.json")!!.readAllBytes()
        bufferPadded = padded(buffer)
        simdJsonParser = SimdJsonParser()
        objectMapper = ObjectMapper()
    }

    @Benchmark
    fun simdjsonDom(): Int {
        val root = simdJsonParser.parse(buffer, buffer.size) as JsonObject
        val statuses = root["statuses"] as JsonArray
        val defaultUsers = mutableSetOf<String>()
        for (tweet in statuses) {
            val user = (tweet as JsonObject)["user"] as JsonObject
            if ((user["default_profile"] as JsonBoolean).value) {
                defaultUsers.add((user["screen_name"] as JsonString).value)
            }
        }
        return defaultUsers.size
    }

    @Benchmark
    fun simdjsonOnDemand(): Int {
        val doc = simdJsonParser.iterate(buffer, buffer.size)
        val statuses = doc.getObject().findField("statuses").getArray()
        val defaultUsers = mutableSetOf<String>()
        for (statusVal in statuses) {
            val user = statusVal.getObject().findField("user").getObject()
            // Access in JSON field order: screen_name comes before default_profile
            val screenName = user.findField("screen_name").getString()
            val defaultProfile = user.findField("default_profile").getBoolean()
            if (defaultProfile) {
                defaultUsers.add(screenName)
            }
        }
        return defaultUsers.size
    }

    @Benchmark
    fun jackson(): Int {
        val tree = objectMapper.readTree(buffer)
        val defaultUsers = mutableSetOf<String>()
        for (tweet in tree["statuses"]) {
            val user = tweet["user"]
            if (user["default_profile"].asBoolean()) {
                defaultUsers.add(user["screen_name"].textValue())
            }
        }
        return defaultUsers.size
    }
}
