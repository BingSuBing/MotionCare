package com.carfortable

import android.util.Log
import com.carfortable.shared.AirIntake
import com.carfortable.shared.Paths
import com.carfortable.shared.TempStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// 세션 생성/종료 (docs/firestore-schema.md v0.3, 4장)
class SessionManager(private val hvac: HvacController) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var sessionId: String? = null
        private set

    // 익명 로그인 → 공조 초기 상태 확인 → sessions/{id} 생성
    fun start(useDevSession: Boolean, onResult: (Result<String>) -> Unit) {
        auth.signInAnonymously()
            .addOnSuccessListener {
                val sid = if (useDevSession) Paths.DEV_SESSION_ID else newSessionId()
                prepareHvac { driverTemp, passengerTemp, airIntake ->
                    val data = hashMapOf<String, Any?>(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "active" to true,
                        "tripTitle" to TRIP_TITLE,
                        "driverName" to DRIVER_NAME,
                        "hvacState" to hashMapOf<String, Any?>(
                            "driverTemp" to driverTemp,
                            "passengerTemp" to passengerTemp,
                            "airIntake" to airIntake,
                            "medianTemp" to null,
                            "voteCount" to 0,
                            "tempStatus" to TempStatus.APPLIED,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    db.document(Paths.session(sid)).set(data)
                        .addOnSuccessListener {
                            sessionId = sid
                            onResult(Result.success(sid))
                        }
                        .addOnFailureListener { e -> onResult(Result.failure(e)) }
                }
            }
            .addOnFailureListener { e -> onResult(Result.failure(e)) }
    }

    // 이동 종료: active = false (종료 버튼에서 호출 예정)
    fun end(onDone: () -> Unit = {}) {
        val sid = sessionId ?: return onDone()
        db.document(Paths.session(sid)).update("active", false)
            .addOnCompleteListener { onDone() }
    }

    // 외기/내기는 읽는 함수가 없어서, 시작할 때 내기로 맞추고 그 값을 기록
    private fun prepareHvac(onReady: (Double, Double, String) -> Unit) {
        hvac.setAirIntake(recirc = true) { result ->
            if (result is HvacResult.Failure) {
                Log.w(TAG, "초기 내기 설정 실패: ${result.message}")
            }
            hvac.readFrontTemperatures { driver, passenger ->
                onReady(
                    driver?.toDouble() ?: DEFAULT_TEMP,
                    passenger?.toDouble() ?: DEFAULT_TEMP,
                    AirIntake.RECIRC
                )
            }
        }
    }

    // 6자리, 헷갈리는 문자(0 o 1 l i) 제외
    private fun newSessionId(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    companion object {
        private const val TAG = "MotionCare"
        private const val DEFAULT_TEMP = 22.0
        const val TRIP_TITLE = "우리 가족 · 서울 → 강릉"
        const val DRIVER_NAME = "건호"
        private const val JOIN_BASE = "https://motioncare-fec56.web.app/join?session="

        fun joinUrl(sid: String) = JOIN_BASE + sid
    }
}