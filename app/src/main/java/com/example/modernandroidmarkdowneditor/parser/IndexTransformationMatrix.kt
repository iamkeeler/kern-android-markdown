package com.example.modernandroidmarkdowneditor.parser

data class IndexRange(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

class IndexTransformationMatrix(val strippedRanges: List<IndexRange>) {

    /**
     * Translates an index in the raw (original) string to the corresponding index
     * in the stripped (transformed) string.
     */
    fun originalToTransformed(orig: Int): Int {
        var shift = 0
        for (range in strippedRanges) {
            if (orig <= range.start) {
                break
            } else if (orig < range.end) {
                // Inside a stripped token, snap to the transformed index corresponding
                // to the start of this stripped token.
                return range.start - shift
            } else {
                shift += range.length
            }
        }
        return orig - shift
    }

    /**
     * Translates an index in the stripped (transformed) string to the corresponding index
     * in the raw (original) string.
     */
    fun transformedToOriginal(trans: Int): Int {
        var shift = 0
        for (range in strippedRanges) {
            val transStart = range.start - shift
            if (trans >= transStart) {
                shift += range.length
            } else {
                break
            }
        }
        return trans + shift
    }
}
