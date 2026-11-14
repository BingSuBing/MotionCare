package com.carfortable

import android.util.Log
import com.carfortable.shared.Paths
import com.carfortable.shared.TempStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// 차량 TempRecommender가 계산한 추천 온도(멀미 민감도 가중치)를 운전자 확인 후 차량에 적용해요
// TempRecommender: hvacState.medianTemp = 추천 온도, tempStatus = "pending" 기록
// 차량: [적용] → 두 구역 온도 적용 → "applied" / [나중에] → "driver_set"
class TempApprovalSync(
    private val sessionId: String,
    private val hvac: HvacController
) {
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    // 운전자 확인을 기다리는 추천 온도 (null이면 제안 없음)
    private var proposedTemp: Double? = null

    // 로그용 상태 문구
    var onStatus: ((String) -> Unit)? = null

    // 운전자에게 보여줄 추천 온도. null이면 제안 없음
    var onProposal: ((Double?) -> Unit)? = null

    fun start() {
        listener = db.document(Paths.session(sessionId))
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "세션 구독 실패", e)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val target = (snap.get(TARGET_TEMP_FIELD) as? Number)?.toDouble()
                val status = snap.getString("hvacState.tempStatus")
                evaluate(target, status)
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
    }

    // 운전자 [적용]
    fun approve(onDone: (Boolean) -> Unit) {
        val target = proposedTemp
        if (target == null) {
            onDone(false)
            return
        }
        hvac.setCabinTemperature(target.toFloat()) { result ->
            hvac.readFrontTemperatures { driver, passenger ->
                when (result) {
                    is HvacResult.Success -> {
                        setProposal(null)
                        writeState(driver?.toDouble(), passenger?.toDouble(), TempStatus.APPLIED)
                        onStatus?.invoke("추천 온도 $target℃ 적용")
                        onDone(true)
                    }
                    is HvacResult.Failure -> {
                        // 실패하면 pending 그대로 (다시 [적용]을 누를 수 있게)
                        writeState(driver?.toDouble(), passenger?.toDouble(), null)
                        onStatus?.invoke("추천 온도 $target℃ 적용 실패: ${result.reason}")
                        onDone(false)
                    }
                }
            }
        }
    }

    // 운전자 [나중에]: 공조는 그대로, 운전자가 정한 온도 유지
    fun decline() {
        val target = proposedTemp ?: return
        setProposal(null)
        writeState(null, null, STATUS_DRIVER_SET)
        onStatus?.invoke("추천 온도 $target℃ 보류 (운전자 설정 유지)")
    }

    private fun evaluate(target: Double?, status: String?) {
        // 새 추천 온도가 올라왔을 때만 운전자에게 물어봐요
        if (target == null || status != TempStatus.PENDING) {
            setProposal(null)
            return
        }
        if (target == proposedTemp) return

        // 차량 온도가 이미 추천 온도면 묻지 않고 applied
        hvac.readFrontTemperatures { driver, passenger ->
            val alreadySet = driver != null && passenger != null &&
                    driver.toDouble() == target && passenger.toDouble() == target
            if (alreadySet) {
                setProposal(null)
                writeState(driver?.toDouble(), passenger?.toDouble(), TempStatus.APPLIED)
                onStatus?.invoke("추천 온도 $target℃ 이미 적용됨")
            } else {
                setProposal(target)
                onStatus?.invoke("추천 온도 $target℃ 운전자 확인 대기")
            }
        }
    }

    private fun setProposal(target: Double?) {
        if (proposedTemp == target) return
        proposedTemp = target
        onProposal?.invoke(target)
    }

    // hvacState 안의 필드만 바꿔요 (추천 온도는 TempRecommender가 쓰는 값이라 건드리지 않음)
    private fun writeState(driverTemp: Double?, passengerTemp: Double?, tempStatus: String?) {
        val data = hashMapOf<String, Any>(
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

        // 추천 온도 필드 (스키마 v0.3.3: 이름은 medianTemp 유지, 의미는 추천 온도)
        const val TARGET_TEMP_FIELD = "hvacState.medianTemp"

        // docs/firestore-schema.md: 운전자가 직접 정한 온도 유지
        private const val STATUS_DRIVER_SET = "driver_set"
    }
}