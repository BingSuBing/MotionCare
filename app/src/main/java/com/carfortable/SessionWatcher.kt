package com.carfortable

import android.util.Log
import com.carfortable.shared.Paths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// 화면 표시용: 참가한 탑승자 목록
data class PassengerItem(
    val seat: String,
    val name: String,
    val submitted: Boolean,
    val desiredTemp: Double? = null,
    val sensitivity: String? = null,
    val restIntervalMin: Int? = null
)

// 화면 표시용: 현재 실내 상태 (하단바)
data class CabinState(
    val driverTemp: Double? = null,
    val passengerTemp: Double? = null,
    val tempStatus: String? = null,
    val recirc: Boolean? = null,
    val targetTemp: Double? = null   // 웹이 계산한 추천 온도
)

// passengers, 세션 문서(hvacState)를 구독해서 화면에 넘겨요
class SessionWatcher(private val sessionId: String) {

    private val db = FirebaseFirestore.getInstance()
    private var passengerListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    var onPassengers: ((List<PassengerItem>) -> Unit)? = null
    var onCabin: ((CabinState) -> Unit)? = null

    fun start() {
        passengerListener = db.collection(Paths.passengers(sessionId))
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "탑승자 구독 실패", e)
                    return@addSnapshotListener
                }
                val items = snap?.documents
                    ?.map { doc ->
                        PassengerItem(
                            seat = doc.id,
                            name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: "탑승자",
                            submitted = doc.get("submittedAt") != null,
                            desiredTemp = (doc.get("desiredTemp") as? Number)?.toDouble(),
                            sensitivity = doc.get("sensitivity")?.toString(),
                            restIntervalMin = (doc.get("restIntervalMin") as? Number)?.toInt()
                        )
                    }
                    ?.sortedBy { seatOrder(it.seat) }
                    ?: emptyList()
                onPassengers?.invoke(items)
            }

        sessionListener = db.document(Paths.session(sessionId))
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "세션 구독 실패", e)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val state = CabinState(
                    driverTemp = (snap.get("hvacState.driverTemp") as? Number)?.toDouble(),
                    passengerTemp = (snap.get("hvacState.passengerTemp") as? Number)?.toDouble(),
                    tempStatus = snap.getString("hvacState.tempStatus"),
                    recirc = snap.getString("hvacState.airIntake")?.let { it == AIR_RECIRC },
                    targetTemp = (snap.get(TempApprovalSync.TARGET_TEMP_FIELD) as? Number)?.toDouble()
                )
                onCabin?.invoke(state)
            }
    }

    // 운전자가 탑승자를 내보낼 때 (passengers/{seat} 삭제)
    fun removePassenger(seat: String) {
        db.document("${Paths.passengers(sessionId)}/$seat").delete()
            .addOnFailureListener { e -> Log.e(TAG, "탑승자 삭제 실패: $seat", e) }
    }

    // 차량에 외기/내기 적용이 끝난 뒤 기록
    fun writeAirIntake(recirc: Boolean) {
        val data = hashMapOf<String, Any>(
            "hvacState.airIntake" to if (recirc) AIR_RECIRC else AIR_OUTSIDE,
            "hvacState.updatedAt" to FieldValue.serverTimestamp()
        )
        db.document(Paths.session(sessionId)).update(data)
            .addOnFailureListener { e -> Log.e(TAG, "airIntake 기록 실패", e) }
    }

    fun stop() {
        passengerListener?.remove()
        passengerListener = null
        sessionListener?.remove()
        sessionListener = null
    }

    private fun seatOrder(seat: String): Int =
        SEAT_ORDER.indexOf(seat).let { if (it < 0) SEAT_ORDER.size else it }

    companion object {
        private const val TAG = "MotionCare"

        // docs/firestore-schema.md 값과 같아요
        private const val AIR_RECIRC = "recirc"
        private const val AIR_OUTSIDE = "outside"
        private val SEAT_ORDER = listOf("front_right", "rear_left", "rear_right")
    }
}