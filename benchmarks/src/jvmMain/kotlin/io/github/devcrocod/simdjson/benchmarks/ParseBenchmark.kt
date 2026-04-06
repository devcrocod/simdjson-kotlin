package io.github.devcrocod.simdjson.benchmarks

import io.github.devcrocod.simdjson.JsonValue
import io.github.devcrocod.simdjson.SimdJsonParser
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = ["--add-modules=jdk.incubator.vector", "-Dsimdjson.species=256"])
open class ParseBenchmark {

    @Param("/twitter.json")
    lateinit var fileName: String

    private lateinit var simdJsonParser: SimdJsonParser
    private lateinit var buffer: ByteArray
    private lateinit var bufferPadded: ByteArray

    @Setup(Level.Trial)
    fun setup() {
        buffer = ParseBenchmark::class.java.getResourceAsStream(fileName)!!.readAllBytes()
        bufferPadded = padded(buffer)
        simdJsonParser = SimdJsonParser()
    }

    @Benchmark
    fun simdjson(): JsonValue {
        return simdJsonParser.parse(buffer, buffer.size)
    }

    @Benchmark
    fun simdjsonPadded(): JsonValue {
        return simdJsonParser.parse(bufferPadded, buffer.size)
    }
}
