package com.carfortable

import android.util.Log
import com.carfortable.shared.AirIntake
import com.carfortable.shared.Paths
import com.carfortable.shared.TempStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// 세션 생성/종료 (docs/firestore-schema.md v0.3.1, 4장)
class SessionManager(private val hvac: HvacController) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var sessionId: String? = null
        private set

    // devSessionId가 있으면 그 ID로, 없으면 6자리 무작위 ID로 세션 생성 (carUid = 내 uid)
    fun start(devSessionId: String?, onResult: (Result<String>) -> Unit) {
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val carUid = result.user?.uid
                if (carUid == null) {
                    onResult(Result.failure(IllegalStateException("익명 로그인 uid 없음")))
                    return@addOnSuccessListener
                }
                val sid = devSessionId ?: newSessionId()
                // 개발 세션은 지난 테스트 데이터를 비우고 시작 (공용 test01은 비우지 않음)
                val needClear = devSessionId != null && devSessionId != Paths.DEV_SESSION_ID
                clearSubcollectionsIf(needClear, sid) {
                    prepareHvac { driverTemp, passengerTemp, airIntake ->
                        writeSession(sid, carUid, driverTemp, passengerTemp, airIntake, onResult)
                    }
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

    private fun writeSession(
        sid: String,
        carUid: String,
        driverTemp: Double,
        passengerTemp: Double,
        airIntake: String,
        onResult: (Result<String>) -> Unit
    ) {
        val data = hashMapOf<String, Any?>(
            "carUid" to carUid,
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

    // passengers, requests 하위 문서 삭제 (실패해도 세션 생성은 계속)
    // ※ Firestore 테스트 모드에서만 동작해요. 보안 규칙(스키마 8장)을 적용하면
    //   차량 앱은 남의 passengers나 requests를 지울 수 없어서 경고 로그만 남아요.
    private fun clearSubcollectionsIf(enabled: Boolean, sid: String, next: () -> Unit) {
        if (!enabled) {
            next()
            return
        }
        val paths = listOf(Paths.passengers(sid), Paths.requests(sid))
        var remaining = paths.size
        paths.forEach { path ->
            db.collection(path).get()
                .continueWithTask { task ->
                    val batch = db.batch()
                    task.result?.documents?.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "하위 문서 비우기 실패(보안 규칙 적용 후에는 정상): $path", task.exception)
                    }
                    remaining -= 1
                    if (remaining == 0) next()
                }
        }
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