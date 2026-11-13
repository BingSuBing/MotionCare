// docs/firestore-schema.md (v0.3.1) 와 1:1로 맞춘 모델 (초안: B 작성, A가 패키지명/위치 조정)
// Firestore toObject()를 쓰려면 모든 필드에 기본값이 있어야 해요.
package com.carfortable.shared

import com.google.firebase.Timestamp
import kotlin.math.roundToInt

object Seat {
    const val FRONT_RIGHT = "front_right"
    const val REAR_LEFT = "rear_left"
    const val REAR_RIGHT = "rear_right"
    val ALL = listOf(FRONT_RIGHT, REAR_LEFT, REAR_RIGHT)

    fun label(seat: String) = when (seat) {
        FRONT_RIGHT -> "조수석"
        REAR_LEFT -> "뒷좌석 왼쪽"
        REAR_RIGHT -> "뒷좌석 오른쪽"
        else -> seat
    }
}

object AirIntake {
    const val OUTSIDE = "outside" // 외기 유입
    const val RECIRC = "recirc"   // 내기 순환
}

object TempStatus {
    const val APPLIED = "applied"       // 중앙값 적용됨
    const val PENDING = "pending"       // 운전자 적용 대기 (2단계)
    const val DRIVER_SET = "driver_set" // 운전자가 직접 설정
}

object RequestType {
    const val REPORT_DISCOMFORT = "REPORT_DISCOMFORT"
}

object RequestStatus {
    const val PENDING = "pending" // 전달됨
    const val DONE = "done"       // 운전자 확인
    const val FAILED = "failed"
}

object Discomfort {
    const val NAUSEA = "nausea" // 메스꺼워요 → 알림 + TTS, 외기 제안
    const val COLD = "cold"     // 너무 추워요 → +1℃ 제안
    const val HOT = "hot"       // 너무 더워요 → -1℃ 제안
    const val STUFFY = "stuffy" // 답답해요 → 외기 제안
    const val REST = "rest"     // 잠깐 쉬고 싶어요 → 알림 + TTS, (2단계) 휴게소 추천

    fun label(v: String) = when (v) {
        NAUSEA -> "메스꺼워요"
        COLD -> "너무 추워요"
        HOT -> "너무 더워요"
        STUFFY -> "답답해요"
        REST -> "잠깐 쉬고 싶어요"
        else -> v
    }
}

object Limits {
    const val TEMP_MIN = 18.0
    const val TEMP_MAX = 26.0
    const val TEMP_ROUND_STEP = 0.5
    const val APPLY_DEBOUNCE_MS = 3_000L // 마지막 변경 후 3초 뒤 계산
}

/** 0.5 단위 반올림 + 허용 범위로 제한 (웹 types.ts 의 clampTemp 와 같은 규칙) */
fun clampTemp(t: Double): Double =
    ((t / Limits.TEMP_ROUND_STEP).roundToInt() * Limits.TEMP_ROUND_STEP)
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
    val tempStatus: String = TempStatus.APPLIED,
    val updatedAt: Timestamp? = null,
)

data class RestStop(
    val name: String = "",
    val distanceKm: Double = 0.0,
    val etaMin: Int = 0,
)

data class RouteInfo( // 2단계
    val destinationName: String = "",
    val eta: Timestamp? = null,
    val remainingMin: Int = 0,
    val nextRestStop: RestStop? = null,
    val updatedAt: Timestamp? = null,
)

data class Session(
    val carUid: String = "",              // 세션을 만든 차량 앱의 익명 uid. 생성 시 FirebaseAuth.getInstance().uid 를 넣어요
    val createdAt: Timestamp? = null,
    val active: Boolean = true,
    val tripTitle: String = "",
    val driverName: String = "",
    val hvacState: HvacState? = null,
    val route: RouteInfo? = null,
)

data class Passenger(
    val uid: String = "",
    val name: String = "",
    val sensitivity: String? = null,                 // none | low | medium | high
    val sicknessTriggers: List<String> = emptyList(), // video | screen
    val desiredTemp: Double? = null,                 // 18~26, null = 아직 안 정함
    val visualMode: String = "dot",                  // off | dot | horizon | minimal
    val visualIntensity: Int = 30,                   // 0~100
    val restIntervalMin: Int? = 60,                  // 30 | 60 | 90, null = 꺼짐
    val joinedAt: Timestamp? = null,
    val submittedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

data class CareRequest(
    val seat: String = "",
    val uid: String = "",
    val type: String = "",
    val value: String = "",                // Discomfort 값
    val status: String = RequestStatus.PENDING,
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
