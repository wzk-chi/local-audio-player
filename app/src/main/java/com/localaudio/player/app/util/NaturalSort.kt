package com.localaudio.player.app.util

/** 按数字片段排序：1、2、100；P1、P2、P100。 */
fun compareNatural(left: String, right: String): Int {
    var leftIndex = 0
    var rightIndex = 0

    while (leftIndex < left.length && rightIndex < right.length) {
        val leftDigit = left[leftIndex].isDigit()
        val rightDigit = right[rightIndex].isDigit()
        if (leftDigit && rightDigit) {
            val leftStart = leftIndex
            val rightStart = rightIndex
            while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex++
            while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex++

            var leftSignificant = leftStart
            while (leftSignificant < leftIndex && left[leftSignificant] == '0') leftSignificant++
            var rightSignificant = rightStart
            while (rightSignificant < rightIndex && right[rightSignificant] == '0') rightSignificant++

            val leftLength = leftIndex - leftSignificant
            val rightLength = rightIndex - rightSignificant
            if (leftLength != rightLength) return leftLength - rightLength
            var numericCompare = 0
            var digitIndex = 0
            while (digitIndex < leftLength) {
                numericCompare = left[leftSignificant + digitIndex]
                    .compareTo(right[rightSignificant + digitIndex])
                if (numericCompare != 0) break
                digitIndex++
            }
            if (numericCompare != 0) return numericCompare
            if (leftIndex - leftStart != rightIndex - rightStart) {
                return (leftIndex - leftStart) - (rightIndex - rightStart)
            }
        } else {
            val compare = left[leftIndex].lowercaseChar().compareTo(right[rightIndex].lowercaseChar())
            if (compare != 0) return compare
            leftIndex++
            rightIndex++
        }
    }

    return (left.length - leftIndex) - (right.length - rightIndex)
}
