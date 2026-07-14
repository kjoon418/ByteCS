package watson.bytecs.session.domain

import watson.bytecs.common.error.ByteCsException
import watson.bytecs.common.error.ErrorCode

/**
 * 아직 도달하지 않은(통과하지 않은) 세션 칸을 조회했을 때 던지는 예외.
 * 지난 문제 다시 보기는 이미 통과한 칸만 열 수 있고, 앞으로 풀 칸은 노출하지 않는다(no-leak, → 403 Forbidden).
 */
class ItemNotViewableException private constructor(
    message: String,
) : ByteCsException(ErrorCode.ITEM_NOT_VIEWABLE, message) {

    companion object {
        fun at(position: Int): ItemNotViewableException =
            ItemNotViewableException("아직 볼 수 없는 문제입니다. position = $position")
    }
}
