package io.github.devcrocod.simdjson

internal class VectorObjectImpl(
    private val iter: OnDemandJsonIterator,
    private val stringBuffer: ByteArray,
    private val result: OnDemandJsonIterator.IteratorResult
) : ObjectImpl {

    private var startPosition: Int = -1
    private var entryDepth: Int = -1
    private var consumed = (result == OnDemandJsonIterator.IteratorResult.NULL)
    private var lastValue: OnDemandValue? = null
    private var needsSeparatorAdvance = false

    init {
        if (result == OnDemandJsonIterator.IteratorResult.NOT_EMPTY) {
            startPosition = iter.indexer.getPosition()
            entryDepth = iter.getDepth()
        }
    }

    override fun findField(name: String): OnDemandValue {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        if (result != OnDemandJsonIterator.IteratorResult.NOT_EMPTY) {
            throw JsonParsingException("No such field: '$name'")
        }
        val nameBytes = name.encodeToByteArray()
        advancePastPreviousValue()
        return searchForward(nameBytes)
            ?: throw JsonParsingException("No such field: '$name'")
    }

    override fun get(name: String): OnDemandValue {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        if (result != OnDemandJsonIterator.IteratorResult.NOT_EMPTY) {
            throw JsonParsingException("No such field: '$name'")
        }
        val nameBytes = name.encodeToByteArray()
        advancePastPreviousValue()

        if (!consumed) {
            val searchStart = iter.indexer.getPosition()

            val found = searchForward(nameBytes)
            if (found != null) return found

            if (searchStart != startPosition) {
                consumed = false
                iter.indexer.setPosition(startPosition)
                iter.setDepth(entryDepth)
                needsSeparatorAdvance = false
                val wrapped = searchForwardUntil(nameBytes, searchStart)
                if (wrapped != null) return wrapped
            }
        } else {
            consumed = false
            iter.indexer.setPosition(startPosition)
            iter.setDepth(entryDepth)
            needsSeparatorAdvance = false
            val found = searchForward(nameBytes)
            if (found != null) return found
        }

        throw JsonParsingException("No such field: '$name'")
    }

    private fun advancePastPreviousValue() {
        lastValue?.ensureConsumed()
        lastValue = null
        if (needsSeparatorAdvance) {
            if (!iter.nextObjectField()) {
                consumed = true
                return
            }
            needsSeparatorAdvance = false
        }
    }

    private fun searchForward(nameBytes: ByteArray): OnDemandValue? {
        while (true) {
            val nameLen = iter.getFieldName(stringBuffer)
            if (fieldMatches(nameBytes, nameLen)) {
                iter.moveToFieldValue()
                val value = OnDemandValue().also {
                    it.impl = VectorValueImpl(iter, stringBuffer)
                }
                lastValue = value
                needsSeparatorAdvance = true
                return value
            }
            iter.moveToFieldValue()
            iter.skipChild()
            if (!iter.nextObjectField()) {
                consumed = true
                return null
            }
        }
    }

    private fun searchForwardUntil(nameBytes: ByteArray, stopPosition: Int): OnDemandValue? {
        while (iter.indexer.getPosition() < stopPosition) {
            val nameLen = iter.getFieldName(stringBuffer)
            if (fieldMatches(nameBytes, nameLen)) {
                iter.moveToFieldValue()
                val value = OnDemandValue().also {
                    it.impl = VectorValueImpl(iter, stringBuffer)
                }
                lastValue = value
                needsSeparatorAdvance = true
                return value
            }
            iter.moveToFieldValue()
            iter.skipChild()
            if (!iter.nextObjectField()) {
                consumed = true
                return null
            }
        }
        return null
    }

    private fun fieldMatches(nameBytes: ByteArray, parsedLen: Int): Boolean {
        if (parsedLen != nameBytes.size) return false
        for (i in nameBytes.indices) {
            if (stringBuffer[i] != nameBytes[i]) return false
        }
        return true
    }

    override fun iterator(document: JsonDocument?): Iterator<OnDemandField> {
        if (consumed) throw JsonIterationException("Object has already been consumed.")
        consumed = true
        return VectorObjectFieldIterator(document)
    }

    override fun close() {}

    private inner class VectorObjectFieldIterator(
        private val document: JsonDocument?
    ) : Iterator<OnDemandField> {
        private var state = if (result == OnDemandJsonIterator.IteratorResult.NOT_EMPTY) ITER_FIRST else ITER_DONE
        private var prevValue: OnDemandValue? = null

        override fun hasNext(): Boolean = when (state) {
            ITER_FIRST -> true
            ITER_NEED_CHECK -> {
                prevValue?.ensureConsumed()
                val more = iter.nextObjectField()
                state = if (more) ITER_HAS_MORE else ITER_DONE
                more
            }
            ITER_HAS_MORE -> true
            ITER_DONE -> false
            else -> false
        }

        override fun next(): OnDemandField {
            if (!hasNext()) throw NoSuchElementException()
            state = ITER_NEED_CHECK
            val nameLen = iter.getFieldName(stringBuffer)
            val fieldName = String(stringBuffer, 0, nameLen, Charsets.UTF_8)
            iter.moveToFieldValue()
            val value = OnDemandValue().also {
                it.impl = VectorValueImpl(iter, stringBuffer)
                it.document = document
                document?.registerChild(it)
            }
            prevValue = value
            return OnDemandField().also { it.init(fieldName, value) }
        }
    }
}

private const val ITER_FIRST = 0
private const val ITER_NEED_CHECK = 1
private const val ITER_HAS_MORE = 2
private const val ITER_DONE = 3
