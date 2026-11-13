// docs/firestore-schema.md (v0.3) 와 1:1로 맞춘 모델 (초안: B 작성, A가 패키지명/위치 조정)
// Firestore toObject()를 쓰려면 모든 필드에 기본값이 있어야 해요.
package com.motioncare.shared // A: 실제 패키지명으로 변경

import com.google.firebase.Timestamp
import kotlin.math.roundToInt

object Seat {
    const val FRONT_RIGHT = "front_right"
    const val REAR_LEFT = "rear_left"
    const val REAR_RIGHT = "rear_right"
    val ALL = listOf(FRONT_RIGHT, REAR_LEFT, REAR_RIGHT)
}

object AirIntake {
    const val OUTSIDE = "outside" // 외기 유입 (환기)
    const val RECIRC = "recirc"   // 내기 순환
}

object RequestType {
    const val SET_AIR_INTAKE = "SET_AIR_INTAKE"
}

object RequestStatus {
    const val PENDING = "pending"
    const val DONE = "done"
    const val FAILED = "failed"
}

object FailReason {
    const val SDK_ERROR = "sdk_error"
    const val SUPERSEDED = "superseded"
    const val UNSUPPORTED = "unsupported"
    const val DECLINED = "declined" // 2단계: 운전자가 승인하지 않음
}

object SicknessLevel {
    const val OK = "ok"
    const val MILD = "mild"
    const val SEVERE = "severe"
}

/** 희망 온도 허용 범위 (SDK 범위 16~30℃보다 좁게) */
object Limits {
    const val TEMP_MIN = 18.0
    const val TEMP_MAX = 26.0
    const val TEMP_STEP = 0.5
    const val APPLY_DEBOUNCE_MS = 3_000L // 마지막 변경 후 3초 뒤 적용
}

/** 0.5 단위 반올림 + 허용 범위로 제한 */
fun clampTemp(t: Double): Double =
    ((t / Limits.TEMP_STEP).roundToInt() * Limits.TEMP_STEP)
        .coerceIn(Limits.TEMP_MIN, Limits.TEMP_MAX)

/** 희망 온도 중앙값. 희망 온도가 없으면 null (웹 types.ts 의 medianTemp 와 같은 규칙) */
fun medianTemp(passengers: List<Passenger>): Double? {
    val v = passengers.mapNotNull { it.desiredTemp }.sorted()
    if (v.isEmpty()) return null
    val mid = v.size / 2
    val m = if (v.size % 2 == 1) v[mid] else (v[mid - 1] + v[mid]) / 2
    return clampTemp(m)
}

data class HvacState(
    val driverTemp: Double = 22.0,
    val passengerTemp: Double = 22.0,
    val airIntake: String = AirIntake.RECIRC,
    val medianTemp: Double? = null,
    val voteCount: Int = 0,
    val updatedAt: Timestamp? = null,
)

data class RestStop(
    val name: String = "",
    val etaMin: Int = 0,
)

data class RouteInfo( // 2단계
    val destinationName: String = "",
    val eta: Timestamp? = null,
    val remainingMin: Int = 0,
    val restIntervalMin: Int = 50,
    val nextRestStop: RestStop? = null,
    val updatedAt: Timestamp? = null,
)

data class Session(
    val createdAt: Timestamp? = null,
    val active: Boolean = true,
    val hvacState: HvacState? = null,
    val route: RouteInfo? = null,
)

data class Passenger(
    val uid: String = "",
    val name: String = "",
    val desiredTemp: Double? = null,      // 희망 온도, null이면 투표 안 함
    val sensitivity: String = "medium",   // low | medium | high
    val sicknessLevel: String = SicknessLevel.OK,
    val visualMode: String = "off",       // off | dot | horizon
    val updatedAt: Timestamp? = null,
)

data class CareRequest(
    val seat: String = "",
    val uid: String = "",
    val type: String = "",
    val value: String = "",               // outside | recirc
    val status: String = RequestStatus.PENDING,
    val reason: String? = null,
    val createdAt: Timestamp? = null,
    val handledAt: Timestamp? = null,
)

object Paths {
    fun session(sid: String) = "sessions/$sid"
    fun passengers(sid: String) = "sessions/$sid/passengers"
    fun passenger(sid: String, seat: String) = "sessions/$sid/passengers/$seat"
    fun requests(sid: String) = "sessions/$sid/requests"
    const val DEV_SESSION_ID = "test01"
}
