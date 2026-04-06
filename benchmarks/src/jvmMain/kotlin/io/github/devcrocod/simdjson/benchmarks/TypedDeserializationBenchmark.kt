package io.github.devcrocod.simdjson.benchmarks

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.devcrocod.simdjson.SimdJsonParser
import io.github.devcrocod.simdjson.parse
import io.github.devcrocod.simdjson.serialization.SimdJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = ["--add-modules=jdk.incubator.vector", "-Dsimdjson.species=256"])
open class TypedDeserializationBenchmark {

    private lateinit var simdJsonParser: SimdJsonParser
    private lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    private lateinit var simdjsonSerialization: SimdJson
    private lateinit var kotlinxJson: Json
    private lateinit var buffer: ByteArray
    private lateinit var bufferPadded: ByteArray
    private lateinit var jsonString: String

    @Setup(Level.Trial)
    fun setup() {
        buffer = TypedDeserializationBenchmark::class.java
            .getResourceAsStream("/twitter.json")!!.readAllBytes()
        bufferPadded = padded(buffer)
        jsonString = String(buffer, Charsets.UTF_8)
        simdJsonParser = SimdJsonParser()
        objectMapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        simdjsonSerialization = SimdJson { ignoreUnknownKeys = true }
        kotlinxJson = Json { ignoreUnknownKeys = true }
    }

    @Benchmark
    fun simdjsonSchemaParser(): Int {
        val twitter = simdJsonParser.parse<SimdJsonTwitterData>(buffer)
        return countDefaultUsers(twitter.statuses) { it.user.default_profile to it.user.screen_name }
    }

    @Benchmark
    fun simdjsonSchemaParserPadded(): Int {
        val twitter = simdJsonParser.parse<SimdJsonTwitterData>(bufferPadded, buffer.size)
        return countDefaultUsers(twitter.statuses) { it.user.default_profile to it.user.screen_name }
    }

    @Benchmark
    fun simdjsonSerialization(): Int {
        val twitter = simdjsonSerialization.decodeFromString(serializer<TwitterData>(), jsonString)
        return countDefaultUsers(twitter.statuses) { it.user.defaultProfile to it.user.screenName }
    }

    @Benchmark
    fun jackson(): Int {
        val twitter = objectMapper.readValue(buffer, JacksonTwitterData::class.java)
        return countDefaultUsers(twitter.statuses) { it.user.default_profile to it.user.screen_name }
    }

    @Benchmark
    fun kotlinxSerializationJson(): Int {
        val twitter = kotlinxJson.decodeFromString(serializer<TwitterData>(), jsonString)
        return countDefaultUsers(twitter.statuses) { it.user.defaultProfile to it.user.screenName }
    }

    private inline fun <T> countDefaultUsers(
        statuses: List<T>,
        extract: (T) -> Pair<Boolean, String>
    ): Int {
        val defaultUsers = mutableSetOf<String>()
        for (status in statuses) {
            val (isDefault, screenName) = extract(status)
            if (isDefault) defaultUsers.add(screenName)
        }
        return defaultUsers.size
    }
}
