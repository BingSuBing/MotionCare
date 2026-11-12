// docs/firestore-schema.md 와 1:1로 맞춘 모델 (초안: B 작성, A가 패키지명/위치 조정)
// Firestore toObject()를 쓰려면 모든 필드에 기본값이 있어야 해요.
package com.motioncare.shared // ⚠️ A: 실제 패키지명으로 변경

import com.google.firebase.Timestamp

object Seat {
    const val FRONT_RIGHT = "front_right"
    const val REAR_LEFT = "rear_left"
    const val REAR_RIGHT = "rear_right"
    val ALL = listOf(FRONT_RIGHT, REAR_LEFT, REAR_RIGHT)
}

object RequestType {
    const val SET_TEMP = "SET_TEMP"
    const val SET_FAN = "SET_FAN"
}

object RequestStatus {
    const val PENDING = "pending"
    const val DONE = "done"
    const val FAILED = "failed"
}

object FailReason {
    const val OUT_OF_RANGE = "out_of_range"
    const val SDK_ERROR = "sdk_error"
    const val SUPERSEDED = "superseded"
    const val UNSUPPORTED = "unsupported"
}

object SicknessLevel {
    const val OK = "ok"
    const val MILD = "mild"
    const val SEVERE = "severe"
}

object Limits { // ⚠️ A: Vehicle SDK 실제 범위로 확정
    const val TEMP_MIN = 18.0
    const val TEMP_MAX = 26.0
    const val FAN_MIN = 0
    const val FAN_MAX = 8
}

data class HvacState(
    val temp: Double = 22.0,
    val fan: Int = 3,
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
    val sensitivity: String = "medium",   // low | medium | high
    val sicknessLevel: String = SicknessLevel.OK,
    val visualMode: String = "off",       // off | dot | horizon
    val updatedAt: Timestamp? = null,
)

data class CareRequest(
    val seat: String = "",
    val uid: String = "",
    val type: String = "",
    val value: Double = 0.0,              // 절댓값
    val status: String = RequestStatus.PENDING,
    val reason: String? = null,
    val createdAt: Timestamp? = null,
    val handledAt: Timestamp? = null,
)

object Paths {
    fun session(sid: String) = "sessions/$sid"
    fun passenger(sid: String, seat: String) = "sessions/$sid/passengers/$seat"
    fun requests(sid: String) = "sessions/$sid/requests"
    const val DEV_SESSION_ID = "test01"
}
