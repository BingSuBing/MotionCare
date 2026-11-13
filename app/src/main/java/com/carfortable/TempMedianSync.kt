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

// passengers 구독 → 3초 디바운스 → 중앙값 계산 → 운전자 확인 대기(pending)
// 운전자가 [적용]을 누르면 두 구역 온도 적용 → applied, [나중에]를 누르면 driver_set
// (docs/firestore-schema.md v0.3.1, 3.2)
class TempMedianSync(
    private val sessionId: String,
    private val hvac: HvacController
) {
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var listener: ListenerRegistration? = null
    private var pending: Runnable? = null

    // 운전자 확인을 기다리는 중앙값 (null이면 제안 없음)
    private var proposedMedian: Double? = null
    private var proposedVoteCount: Int = 0

    // 운전자가 [나중에]를 누른 중앙값 (같은 값이면 다시 묻지 않음)
    private var declinedMedian: Double? = null

    // 화면 표시용 상태 문구 (로그용)
    var onStatus: ((String) -> Unit)? = null

    // 운전자에게 보여줄 제안 (중앙값, 인원). null이면 제안 없음
    var onProposal: ((Double?, Int) -> Unit)? = null

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

    // 운전자 [적용]
    fun approve(onDone: (Boolean) -> Unit) {
        val median = proposedMedian
        if (median == null) {
            onDone(false)
            return
        }
        val voteCount = proposedVoteCount
        hvac.setCabinTemperature(median.toFloat()) { result ->
            hvac.readFrontTemperatures { driver, passenger ->
                when (result) {
                    is HvacResult.Success -> {
                        declinedMedian = null
                        setProposal(null, 0)
                        writeState(
                            median, voteCount,
                            driverTemp = driver?.toDouble(),
                            passengerTemp = passenger?.toDouble(),
                            tempStatus = TempStatus.APPLIED
                        )
                        onStatus?.invoke("중앙값 $median℃ 적용 (${voteCount}명)")
                        onDone(true)
                    }
                    is HvacResult.Failure -> {
                        // 실패하면 pending 그대로 (다시 [적용]을 누를 수 있게)
                        writeState(
                            median, voteCount,
                            driverTemp = driver?.toDouble(),
                            passengerTemp = passenger?.toDouble()
                        )
                        onStatus?.invoke("중앙값 $median℃ 적용 실패: ${result.reason}")
                        onDone(false)
                    }
                }
            }
        }
    }

    // 운전자 [나중에]: 공조는 그대로, 운전자가 정한 온도 유지
    fun decline() {
        val median = proposedMedian ?: return
        declinedMedian = median
        setProposal(null, 0)
        writeState(median, proposedVoteCount, tempStatus = STATUS_DRIVER_SET)
        onStatus?.invoke("중앙값 $median℃ 보류 (운전자 설정 유지)")
    }

    // 마지막 변경 후 3초 뒤에 한 번만 계산
    private fun schedule(passengers: List<Passenger>) {
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { evaluate(passengers) }
        pending = task
        handler.postDelayed(task, Limits.APPLY_DEBOUNCE_MS)
    }

    private fun evaluate(passengers: List<Passenger>) {
        val median = medianTemp(passengers)
        val voteCount = passengers.count { it.desiredTemp != null }

        // 희망 온도가 없으면 제안 없음
        if (median == null) {
            declinedMedian = null
            setProposal(null, 0)
            writeState(median = null, voteCount = 0, tempStatus = TempStatus.APPLIED)
            onStatus?.invoke("희망 온도 없음 (공조 유지)")
            return
        }

        // 운전자가 이미 보류한 값이면 다시 묻지 않음
        if (median == declinedMedian) {
            setProposal(null, 0)
            writeState(median, voteCount)
            return
        }

        // 차량 온도가 이미 중앙값이면 바로 applied
        hvac.readFrontTemperatures { driver, passenger ->
            val alreadySet = driver != null && passenger != null &&
                    driver.toDouble() == median && passenger.toDouble() == median
            if (alreadySet) {
                setProposal(null, 0)
                writeState(
                    median, voteCount,
                    driverTemp = driver?.toDouble(),
                    passengerTemp = passenger?.toDouble(),
                    tempStatus = TempStatus.APPLIED
                )
                onStatus?.invoke("중앙값 $median℃ 이미 적용됨 (${voteCount}명)")
            } else {
                setProposal(median, voteCount)
                writeState(median, voteCount, tempStatus = TempStatus.PENDING)
                onStatus?.invoke("중앙값 $median℃ 운전자 확인 대기 (${voteCount}명)")
            }
        }
    }

    private fun setProposal(median: Double?, voteCount: Int) {
        proposedMedian = median
        proposedVoteCount = voteCount
        onProposal?.invoke(median, voteCount)
    }

    // hvacState 안의 필드만 바꿔요 (airIntake 등 다른 값은 유지, null인 값은 건드리지 않음)
    private fun writeState(
        median: Double?,
        voteCount: Int,
        driverTemp: Double? = null,
        passengerTemp: Double? = null,
        tempStatus: String? = null
    ) {
        val data = hashMapOf<String, Any?>(
            "hvacState.medianTemp" to median,
            "hvacState.voteCount" to voteCount,
            "hvacState.updatedAt" to FieldValue.serverTimestamp()
        )
        driverTemp?.let { data["hvacState.driverTemp"] = it }
        passengerTemp?.let { data["hvacState.passengerTemp"] = it }
        tempStatus?.let { data["hvacState.tempStatus"] = it }

        db.document(Paths.session(sessionId)).update(data)
            .addOnFailureListener { e -> Log.e(TAG, "hvacState 기록 실패", e) }
    }

    companion object {
        private const val TAG = "MotionCare"

        // docs/firestore-schema.md: 운전자가 직접 정한 온도 유지
        private const val STATUS_DRIVER_SET = "driver_set"
    }
}