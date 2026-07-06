package io.github.devcrocod.simdjson

internal fun materializeTapeValue(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray): JsonValue {
    return when (tape.getType(tapeIdx)) {
        Tape.START_OBJECT -> materializeObject(tape, tapeIdx, stringBuffer)
        Tape.START_ARRAY -> materializeArray(tape, tapeIdx, stringBuffer)
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

private fun materializeObject(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray): JsonObject {
    val entries = ArrayList<Pair<String, JsonValue>>(tape.getScopeCount(tapeIdx))
    var idx = tapeIdx + 1
    val endIdx = tape.getMatchingBraceIndex(tapeIdx) - 1
    while (idx < endIdx) {
        val key = readStringFromTape(tape, idx, stringBuffer)
        idx = tape.computeNextIndex(idx)
        entries.add(key to materializeTapeValue(tape, idx, stringBuffer))
        idx = tape.computeNextIndex(idx)
    }
    return JsonObject(entries)
}

private fun materializeArray(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray): JsonArray {
    val elements = ArrayList<JsonValue>(tape.getScopeCount(tapeIdx))
    var idx = tapeIdx + 1
    val endIdx = tape.getMatchingBraceIndex(tapeIdx) - 1
    while (idx < endIdx) {
        elements.add(materializeTapeValue(tape, idx, stringBuffer))
        idx = tape.computeNextIndex(idx)
    }
    return JsonArray(elements)
}

private fun readStringFromTape(tape: Tape, tapeIdx: Int, stringBuffer: ByteArray): String {
    val stringBufferIdx = tape.getValue(tapeIdx).toInt()
    val len = IntegerUtils.toInt(stringBuffer, stringBufferIdx)
    return stringBuffer.decodeToString(stringBufferIdx + Int.SIZE_BYTES, stringBufferIdx + Int.SIZE_BYTES + len)
}
