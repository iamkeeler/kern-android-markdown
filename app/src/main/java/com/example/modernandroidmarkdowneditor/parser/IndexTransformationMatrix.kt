package com.example.modernandroidmarkdowneditor.parser

data class IndexRange(val start: Int, val end: Int) {
    val length: Int get() = end - start
}

class IndexTransformationMatrix(val strippedRanges: List<IndexRange>) {

    // Precalculate shifts for each range to enable binary search
    private val shifts = IntArray(strippedRanges.size)

    init {
        var currentShift = 0
        for (i in strippedRanges.indices) {
            shifts[i] = currentShift
            currentShift += strippedRanges[i].length
        }
    }

    /**
     * Translates an index in the raw (original) string to the corresponding index
     * in the stripped (transformed) string.
     */
    fun originalToTransformed(orig: Int): Int {
        if (strippedRanges.isEmpty()) return orig
        if (orig <= strippedRanges[0].start) return orig

        var low = 0
        var high = strippedRanges.size - 1
        var bestIdx = -1

        while (low <= high) {
            val mid = (low + high) / 2
            val range = strippedRanges[mid]

            if (orig < range.start) {
                high = mid - 1
            } else if (orig >= range.end) {
                bestIdx = mid
                low = mid + 1
            } else {
                // orig is inside range
                return range.start - shifts[mid]
            }
        }

        if (bestIdx == -1) {
            return orig
        } else {
            return orig - (shifts[bestIdx] + strippedRanges[bestIdx].length)
        }
    }

    /**
     * Translates an index in the stripped (transformed) string to the corresponding index
     * in the raw (original) string.
     */
    fun transformedToOriginal(trans: Int): Int {
        if (strippedRanges.isEmpty()) return trans

        var low = 0
        var high = strippedRanges.size - 1
        var bestIdx = -1

        while (low <= high) {
            val mid = (low + high) / 2
            val range = strippedRanges[mid]
            val transStart = range.start - shifts[mid]

            if (trans >= transStart) {
                bestIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (bestIdx == -1) {
            return trans
        } else {
            return trans + shifts[bestIdx] + strippedRanges[bestIdx].length
        }
    }
}
