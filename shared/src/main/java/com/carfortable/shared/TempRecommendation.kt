// 추천 온도 알고리즘 (docs/firestore-schema.md v0.3.3, 3.2)
// 30분마다 탑승자별 가중치를 다시 계산하고, 희망 온도의 가중평균으로 추천 온도를 정해요.
// 멀미를 많이 느끼는 사람일수록 가중치가 커서 추천 온도가 그 사람 쪽으로 더 가까워져요.
// 계산은 차량 앱이 하고(웹은 결과만 읽음), 적용은 운전자 확인 후(TempApprovalSync).
package com.carfortable.shared

import kotlin.math.abs

object RecommendRule {
    /**
     * 정기 재계산 주기: 30분.
     * 불편 요청으로 올라간 가중치는 30분이 지나면 빠지므로, 이 주기로 다시 계산해서 원래대로 돌려놔요.
     * (계산할 때마다 다음 정기 계산은 30분 뒤로 다시 맞춰요)
     */
    const val REVIEW_INTERVAL_MS = 30 * 60_000L

    /** 누군가 희망 온도를 바꾸면 "바로" 다시 계산. 연속으로 누르는 걸 한 번으로 묶으려고 3초만 기다려요 */
    const val DESIRED_CHANGE_DELAY_MS = 3_000L

    /** 최근 불편 요청을 반영하는 기간: 30분 */
    const val WINDOW_MS = 30 * 60_000L

    /** 멀미 민감도 기본 가중치 (멀미가 클수록 크게) */
    val BASE_WEIGHT: Map<String, Double> = mapOf(
        "none" to 1.0,
        "low" to 1.5,
        "medium" to 2.0,
        "high" to 3.0,
    )
    const val DEFAULT_WEIGHT = 1.0

    /** "영상 시청 시 멀미" 같은 추가 항목 1개당 */
    const val TRIGGER_BOOST = 0.25

    /** 최근 30분 "메스꺼워요" 1회당, 최대치 */
    const val NAUSEA_BOOST = 2.0
    const val NAUSEA_BOOST_MAX = 4.0

    /** 최근 30분 "잠깐 쉬고 싶어요" */
    const val REST_BOOST = 1.0

    /** 최근 30분 "너무 추워요"(+) / "너무 더워요"(-) 이면 그 사람의 희망 온도를 이만큼 옮겨서 계산 */
    const val TEMP_NUDGE = 1.0

    /** 지금 온도와 이 이상 차이 날 때만 운전자에게 제안 */
    const val MIN_CHANGE = 0.5

    /** 한 번에 바꾸는 최대 폭 (급격한 변화 방지) */
    const val MAX_STEP = 2.0
}

/** 최근 불편 요청 (requests 문서에서 만든 값) */
data class RecentReport(val seat: String, val value: String, val atMs: Long)

/** 계산에 쓰인 좌석별 값 (로그·화면 표시용) */
data class SeatVote(val seat: String, val temp: Double, val weight: Double)

data class Recommendation(
    /** 추천 온도 (0.5 단위, 18~26, 한 번에 최대 2℃ 변화) */
    val temp: Double,
    /** 변화 폭 제한 전 가중평균 */
    val rawTemp: Double,
    val votes: List<SeatVote>,
    /** 가중치가 가장 큰 좌석 (단독 1위일 때만, 아니면 null) */
    val leadSeat: String?,
    /** 지금 온도와 충분히 달라서 운전자에게 제안할지 */
    val shouldPropose: Boolean,
)

/** 탑승자의 이번 회차 가중치 = 민감도 + 추가 항목 + 최근 불편 요청 */
fun sicknessWeight(p: Passenger, recent: List<RecentReport>): Double {
    val base = RecommendRule.BASE_WEIGHT[p.sensitivity] ?: RecommendRule.DEFAULT_WEIGHT
    val triggers = p.sicknessTriggers.size * RecommendRule.TRIGGER_BOOST
    val nausea = (recent.count { it.value == Discomfort.NAUSEA } * RecommendRule.NAUSEA_BOOST)
        .coerceAtMost(RecommendRule.NAUSEA_BOOST_MAX)
    val rest = if (recent.any { it.value == Discomfort.REST }) RecommendRule.REST_BOOST else 0.0
    return base + triggers + nausea + rest
}

/** 이번 회차에 쓸 희망 온도: 최근 "추워요/더워요" 중 가장 마지막 것만 반영 */
fun effectiveTemp(p: Passenger, recent: List<RecentReport>): Double? {
    val desired = p.desiredTemp ?: return null
    val last = recent
        .filter { it.value == Discomfort.COLD || it.value == Discomfort.HOT }
        .maxByOrNull { it.atMs }
    val nudge = when (last?.value) {
        Discomfort.COLD -> RecommendRule.TEMP_NUDGE
        Discomfort.HOT -> -RecommendRule.TEMP_NUDGE
        else -> 0.0
    }
    return desired + nudge
}

/**
 * 가중평균: (각자 희망 온도 × 가중치)의 합 ÷ 가중치 합.
 * 가중치가 모두 같으면 일반 평균과 같아요.
 */
fun weightedAverage(votes: List<SeatVote>): Double? {
    val total = votes.sumOf { it.weight }
    if (votes.isEmpty() || total <= 0.0) return null
    return votes.sumOf { it.temp * it.weight } / total
}

/**
 * 추천 온도 계산 (차량 앱이 30분마다, 그리고 즉시 재계산 조건에서 호출)
 * @param passengers 좌석 → 탑승자 (passengers 문서 ID가 좌석)
 * @param reports 불편 요청들 (기간 밖은 알아서 걸러요)
 * @param currentTemp 지금 실내 온도 (모르면 null)
 */
fun recommendTemp(
    passengers: Map<String, Passenger>,
    reports: List<RecentReport>,
    nowMs: Long,
    currentTemp: Double?,
): Recommendation? {
    val recent = reports.filter { it.atMs in (nowMs - RecommendRule.WINDOW_MS)..nowMs }
    val votes = passengers.mapNotNull { (seat, p) ->
        val mine = recent.filter { it.seat == seat }
        effectiveTemp(p, mine)?.let { SeatVote(seat, it, sicknessWeight(p, mine)) }
    }
    val raw = weightedAverage(votes)?.let { clampTemp(it) } ?: return null

    val target = if (currentTemp == null) raw else clampTemp(
        currentTemp + (raw - currentTemp).coerceIn(-RecommendRule.MAX_STEP, RecommendRule.MAX_STEP)
    )
    val shouldPropose = currentTemp == null || abs(target - currentTemp) >= RecommendRule.MIN_CHANGE

    val top = votes.maxOfOrNull { it.weight }
    val leaders = votes.filter { it.weight == top }
    val leadSeat = if (votes.size > 1 && leaders.size == 1) leaders.first().seat else null

    return Recommendation(target, raw, votes, leadSeat, shouldPropose)
}

/** 언제 다시 계산할지 (passengers 구독에서 이전 값과 비교) */
enum class ReviewTrigger {
    /** 누군가 처음 설정을 제출함 → 바로 계산 */
    FIRST_SUBMIT,

    /** 누군가 희망 온도를 바꿈 → 바로 계산 (DESIRED_CHANGE_DELAY_MS 3초 묶기) */
    DESIRED_TEMP_CHANGED,

    /** 계산할 필요 없음 (이름·시각 케어 등 다른 값만 바뀜) */
    NONE,
}

/**
 * passengers 변경 → 다시 계산할지 판단.
 * 불편 요청(바로 계산)은 requests 구독에서 isNewReport()로 따로 판단해요.
 * @param prev 직전 passengers (좌석 → 탑승자)
 * @param next 새 passengers
 */
fun reviewTrigger(prev: Map<String, Passenger>, next: Map<String, Passenger>): ReviewTrigger {
    val firstSubmit = next.any { (seat, p) -> p.submittedAt != null && prev[seat]?.submittedAt == null }
    if (firstSubmit) return ReviewTrigger.FIRST_SUBMIT
    val seats = prev.keys + next.keys
    val tempChanged = seats.any { prev[it]?.desiredTemp != next[it]?.desiredTemp }
    return if (tempChanged) ReviewTrigger.DESIRED_TEMP_CHANGED else ReviewTrigger.NONE
}

/**
 * 새 불편 요청이 들어오면 바로 다시 계산해요.
 * 그 사람의 가중치(메스꺼워요·쉬고 싶어요)나 희망 온도(추워요·더워요)가 즉시 바뀌고,
 * 바뀐 가중치로 모두의 추천 온도를 다시 계산해요.
 */
fun isNewReport(req: CareRequest): Boolean =
    req.type == RequestType.REPORT_DISCOMFORT && req.status == RequestStatus.PENDING

/** requests 문서 → 계산용 값 (불편 요청만) */
fun CareRequest.toRecentReport(): RecentReport? {
    if (type != RequestType.REPORT_DISCOMFORT) return null
    val at = createdAt?.toDate()?.time ?: return null
    return RecentReport(seat, value, at)
}
