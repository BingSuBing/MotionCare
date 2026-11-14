package com.carfortable

import android.util.Log
import com.carfortable.shared.CareRequest
import com.carfortable.shared.Paths
import com.carfortable.shared.RequestStatus
import com.carfortable.shared.RequestType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// 화면에 보여줄 대기 중인 불편 알림
data class PendingRequest(
    val id: String,
    val seat: String,
    val value: String,
    val createdAt: Timestamp?
)

// requests 구독 (pending만) → 알림 목록 전달, 운전자 확인 시 done + handledAt
// (docs/firestore-schema.md v0.3.1, 6장)
class CareRequestSync(private val sessionId: String) {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    // 대기 중인 알림 목록 (오래된 것부터)
    var onChange: ((List<PendingRequest>) -> Unit)? = null

    fun start() {
        listener = db.collection(Paths.requests(sessionId))
            .whereEqualTo("status", RequestStatus.PENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "requests 구독 실패", e)
                    return@addSnapshotListener
                }
                val items = snap?.documents
                    ?.mapNotNull { doc ->
                        val req = runCatching { doc.toObject(CareRequest::class.java) }
                            .onFailure { Log.w(TAG, "request 변환 실패: ${doc.id}", it) }
                            .getOrNull()
                        if (req == null || req.type != RequestType.REPORT_DISCOMFORT) {
                            null
                        } else {
                            PendingRequest(doc.id, req.seat, req.value, req.createdAt)
                        }
                    }
                    ?.sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    ?: emptyList()
                onChange?.invoke(items)
            }
    }

    // 운전자 확인: status = done, handledAt = 서버 시간
    fun acknowledge(requestId: String) {
        db.collection(Paths.requests(sessionId)).document(requestId)
            .update(
                mapOf(
                    "status" to RequestStatus.DONE,
                    "handledAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { e -> Log.e(TAG, "요청 확인 처리 실패: $requestId", e) }
    }

    fun stop() {
        listener?.remove()
        listener = null
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}