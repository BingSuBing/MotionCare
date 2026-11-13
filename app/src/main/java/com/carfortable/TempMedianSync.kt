package com.carfortable

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carfortable.shared.Limits
import com.carfortable.shared.Passenger
import com.carfortable.shared.Paths
import com.carfortable.shared.TempStatus
import com.carfortable.shared.medianTemp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// passengers 구독 → 3초 디바운스 → 중앙값 → 두 구역 온도 적용 → hvacState 기록
// (docs/firestore-schema.md v0.3, 3.2)
class TempMedianSync(
    private val sessionId: String,
    private val hvac: HvacController
) {
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var listener: ListenerRegistration? = null
    private var pending: Runnable? = null

    // 화면 표시용 상태 문구 (호출한 쪽에서 UI 스레드로 넘겨요)
    var onStatus: ((String) -> Unit)? = null

    fun start() {
        listener = db.collection(Paths.passengers(sessionId))
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "passengers 구독 실패", e)
                    onStatus?.invoke("희망 온도 구독 실패: ${e.message}")
                    return@addSnapshotListener
                }
                val passengers = snap?.documents
                    ?.mapNotNull { doc ->
                        runCatching { doc.toObject(Passenger::class.java) }
                            .onFailure { Log.w(TAG, "passenger 변환 실패: ${doc.id}", it) }
                            .getOrNull()
                    }
                    ?: emptyList()
                schedule(passengers)
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    // 마지막 변경 후 3초 뒤에 한 번만 계산
    private fun schedule(passengers: List<Passenger>) {
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { apply(passengers) }
        pending = task
        handler.postDelayed(task, Limits.APPLY_DEBOUNCE_MS)
    }

    private fun apply(passengers: List<Passenger>) {
        val median = medianTemp(passengers)
        val voteCount = passengers.count { it.desiredTemp != null }

        if (median == null) {
            // 희망 온도가 없으면 공조는 그대로 두고 기록만
            writeState(medianTemp = null, voteCount = 0, driverTemp = null, passengerTemp = null)
            onStatus?.invoke("희망 온도 없음 (공조 유지)")
            return
        }

        hvac.setCabinTemperature(median.toFloat()) { result ->
            hvac.readFrontTemperatures { driver, passenger ->
                writeState(median, voteCount, driver?.toDouble(), passenger?.toDouble())
                val text = when (result) {
                    is HvacResult.Success -> "중앙값 $median℃ 적용 (${voteCount}명)"
                    is HvacResult.Failure -> "중앙값 $median℃ 적용 실패: ${result.reason}"
                }
                onStatus?.invoke(text)
            }
        }
    }

    // hvacState 안의 필드만 바꿔요 (airIntake 등 다른 값은 유지)
    private fun writeState(
        medianTemp: Double?,
        voteCount: Int,
        driverTemp: Double?,
        passengerTemp: Double?
    ) {
        val data = hashMapOf<String, Any?>(
            "hvacState.medianTemp" to medianTemp,
            "hvacState.voteCount" to voteCount,
            "hvacState.tempStatus" to TempStatus.APPLIED,
            "hvacState.updatedAt" to FieldValue.serverTimestamp()
        )
        driverTemp?.let { data["hvacState.driverTemp"] = it }
        passengerTemp?.let { data["hvacState.passengerTemp"] = it }

        db.document(Paths.session(sessionId)).update(data)
            .addOnFailureListener { e -> Log.e(TAG, "hvacState 기록 실패", e) }
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}