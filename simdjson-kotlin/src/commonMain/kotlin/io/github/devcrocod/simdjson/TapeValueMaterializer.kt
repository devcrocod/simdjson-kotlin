package io.github.devcrocod.simdjson

internal fun materializeTapeValue(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray): JsonValue {
    return when (tape.getType(tapeIdx)) {
        Tape.START_OBJECT -> JsonObject(tape, tapeIdx, stringBuffer)
        Tape.START_ARRAY -> JsonArray(tape, tapeIdx, stringBuffer)
        Tape.STRING -> JsonString(readStringFromTape(tape, tapeIdx, stringBuffer))
        Tape.INT64 -> JsonNumber(longValue = tape.getInt64Value(tapeIdx))
        Tape.UINT64 -> JsonNumber.ofULong(tape.getInt64Value(tapeIdx).toULong())
        Tape.DOUBLE -> JsonNumber(doubleValue = tape.getDouble(tapeIdx))
        Tape.TRUE_VALUE -> JsonBoolean(true)
        Tape.FALSE_VALUE -> JsonBoolean(false)
        Tape.NULL_VALUE -> JsonNull
        else -> throw JsonParsingException("Unknown tape type '${tape.getType(tapeIdx)}' at index $tapeIdx")
    }
}

internal fun readStringFromTape(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray): String {
    val stringBufferIdx = tape.getValue(tapeIdx).toInt()
    val len = IntegerUtils.toInt(stringBuffer, stringBufferIdx)
    return stringBuffer.decodeToString(stringBufferIdx + Int.SIZE_BYTES, stringBufferIdx + Int.SIZE_BYTES + len)
}
