package com.carfortable

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carfortable.shared.CareRequest
import com.carfortable.shared.Passenger
import com.carfortable.shared.Paths
import com.carfortable.shared.RecentReport
import com.carfortable.shared.RecommendRule
import com.carfortable.shared.ReviewTrigger
import com.carfortable.shared.TempStatus
import com.carfortable.shared.isNewReport
import com.carfortable.shared.recommendTemp
import com.carfortable.shared.reviewTrigger
import com.carfortable.shared.toRecentReport
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Date

// 추천 온도 계산 (docs/temp-recommendation.md, shared/TempRecommendation.kt)
// passengers + requests 구독 → recommendTemp() → hvacState 기록
// 계산 시점: 처음 제출·새 불편 요청 → 바로 / 희망 온도 변경·탑승자 나감 → 3초 뒤 / 30분마다
// 적용은 운전자 확인 후 (TempApprovalSync)
class TempRecommender(
    private val sessionId: String,
    private val hvac: HvacController
) {
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private var passengerListener: ListenerRegistration? = null
    private var requestListener: ListenerRegistration? = null

    // 최신 상태 (좌석 → 탑승자, 요청 ID → 불편 요청)
    private var passengers: Map<String, Passenger> = emptyMap()
    private val reports = mutableMapOf<String, RecentReport>()
    private var passengersLoaded = false
    private var requestsLoaded = false

    private val delayedReview = Runnable { review("희망 온도 변경") }
    private val periodicReview = Runnable { review("30분 정기") }

    // 로그용 상태 문구
    var onStatus: ((String) -> Unit)? = null

    fun start() {
        passengerListener = db.collection(Paths.passengers(sessionId))
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "passengers 구독 실패", e)
                    return@addSnapshotListener
                }
                val next = snap?.documents
                    ?.mapNotNull { doc ->
                        runCatching { doc.toObject(Passenger::class.java) }
                            .onFailure { Log.w(TAG, "passenger 변환 실패: ${doc.id}", it) }
                            .getOrNull()
                            ?.let { doc.id to it }
                    }
                    ?.toMap()
                    ?: emptyMap()
                val trigger = reviewTrigger(passengers, next)
                passengers = next
                passengersLoaded = true

                when (trigger) {
                    ReviewTrigger.FIRST_SUBMIT -> review("처음 제출")
                    ReviewTrigger.DESIRED_TEMP_CHANGED -> {
                        // 그 사이 또 바뀌면 다시 3초
                        handler.removeCallbacks(delayedReview)
                        handler.postDelayed(delayedReview, RecommendRule.DESIRED_CHANGE_DELAY_MS)
                    }
                    ReviewTrigger.NONE -> Unit
                }
            }

        requestListener = db.collection(Paths.requests(sessionId))
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "requests 구독 실패", e)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                var newReport = false
                for (change in snap.documentChanges) {
                    val id = change.document.id
                    if (change.type == DocumentChange.Type.REMOVED) {
                        reports.remove(id)
                        continue
                    }
                    val req = runCatching { change.document.toObject(CareRequest::class.java) }
                        .getOrNull() ?: continue
                    req.toRecentReport()?.let { reports[id] = it }
                    // 첫 구독 때 이미 있던 요청은 "새 요청"으로 보지 않아요
                    if (requestsLoaded && change.type == DocumentChange.Type.ADDED && isNewReport(req)) {
                        newReport = true
                    }
                }
                requestsLoaded = true
                if (newReport) review("새 불편 요청")
            }

        schedulePeriodic()
    }

    fun stop() {
        passengerListener?.remove()
        passengerListener = null
        requestListener?.remove()
        requestListener = null
        handler.removeCallbacks(delayedReview)
        handler.removeCallbacks(periodicReview)
    }

    private fun schedulePeriodic() {
        handler.removeCallbacks(periodicReview)
        handler.postDelayed(periodicReview, RecommendRule.REVIEW_INTERVAL_MS)
    }

    private fun review(reason: String) {
        if (!passengersLoaded) return
        handler.removeCallbacks(delayedReview)
        schedulePeriodic() // 계산할 때마다 다음 정기 계산은 다시 30분 뒤

        val snapshotPassengers = passengers
        val snapshotReports = reports.values.toList()
        hvac.readFrontTemperatures { driver, passenger ->
            val now = System.currentTimeMillis()
            val current = (driver ?: passenger)?.toDouble()
            val rec = recommendTemp(snapshotPassengers, snapshotReports, now, current)
            val nextReviewAt = Timestamp(Date(now + RecommendRule.REVIEW_INTERVAL_MS))

            val data = hashMapOf<String, Any?>(
                "hvacState.reviewedAt" to FieldValue.serverTimestamp(),
                "hvacState.nextReviewAt" to nextReviewAt
            )
            if (rec != null && rec.shouldPropose) {
                data["hvacState.medianTemp"] = rec.temp
                data["hvacState.voteCount"] = rec.votes.size
                data["hvacState.leadSeat"] = rec.leadSeat
                data["hvacState.tempStatus"] = TempStatus.PENDING
                data["hvacState.updatedAt"] = FieldValue.serverTimestamp()
            }
            db.document(Paths.session(sessionId)).update(data)
                .addOnFailureListener { e -> Log.e(TAG, "추천 온도 기록 실패", e) }

            val text = when {
                rec == null -> "[$reason] 희망 온도 없음"
                rec.shouldPropose -> "[$reason] 추천 ${rec.temp}℃ 제안 (현재 $current℃, 가중평균 ${"%.2f".format(rec.rawTemp)}, 1위 ${rec.leadSeat})"
                else -> "[$reason] 추천 ${rec.temp}℃ = 현재와 비슷해서 제안 안 함"
            }
            Log.d(TAG, "추천 온도: $text")
            onStatus?.invoke(text)
        }
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}