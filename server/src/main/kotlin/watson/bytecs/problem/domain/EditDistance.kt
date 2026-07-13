package watson.bytecs.problem.domain

/**
 * 두 문자열 사이의 Levenshtein 편집거리를 계산하는 순수 함수.
 * 근접(오탈자) 신호를 결정적으로 판정하는 데 사용한다.
 */
object EditDistance {

    fun levenshtein(source: String, target: String): Int {
        if (source.isEmpty()) return target.length
        if (target.isEmpty()) return source.length

        // 이전 행과 현재 행만 유지해 O(min) 공간으로 계산한다.
        var previousRow = IntArray(target.length + 1) { it }
        var currentRow = IntArray(target.length + 1)

        for (i in 1..source.length) {
            currentRow[0] = i
            for (j in 1..target.length) {
                val substitutionCost = if (source[i - 1] == target[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,      // 삽입
                    previousRow[j] + 1,         // 삭제
                    previousRow[j - 1] + substitutionCost, // 교체
                )
            }

            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        return previousRow[target.length]
    }
}
