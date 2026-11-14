package com.carfortable.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 추천 온도 알고리즘 테스트: ./gradlew :shared:test
class TempRecommendationTest {

    private val now = 10_000_000L
    private fun p(sens: String?, t: Double?, trig: List<String> = emptyList()) =
        Passenger(uid = "u", name = "n", sensitivity = sens, sicknessTriggers = trig, desiredTemp = t)

    @Test fun 가중치가_같으면_일반_평균() {
        val r = recommendTemp(mapOf("a" to p("low", 20.0), "b" to p("low", 23.0)), emptyList(), now, null)!!
        assertEquals(21.5, r.temp, 0.0)
        assertNull(r.leadSeat)
    }

    @Test fun 멀미가_큰_사람에게_가까운_온도() {
        val r = recommendTemp(
            mapOf("a" to p("high", 22.0), "b" to p("none", 25.0), "c" to p("none", 26.0)), emptyList(), now, null,
        )!!
        // (22×3 + 25×1 + 26×1) ÷ 5 = 23.4 → 23.5 (일반 평균 24.3보다 22℃ 쪽)
        assertEquals(23.5, r.temp, 0.0)
        assertEquals("a", r.leadSeat)
    }

    @Test fun 최근_메스꺼워요는_가중치를_올린다() {
        val reps = listOf(RecentReport("a", Discomfort.NAUSEA, now - 5 * 60_000))
        val r = recommendTemp(mapOf("a" to p("medium", 20.0), "b" to p("high", 25.0)), reps, now, null)!!
        // (20×4 + 25×3) ÷ 7 = 22.1 → 22
        assertEquals(22.0, r.temp, 0.0)
        assertEquals("a", r.leadSeat)
    }

    @Test fun 삼십분_지난_요청은_무시() {
        val reps = listOf(RecentReport("a", Discomfort.NAUSEA, now - 31 * 60_000))
        val r = recommendTemp(mapOf("a" to p("medium", 20.0), "b" to p("high", 25.0)), reps, now, null)!!
        // (20×2 + 25×3) ÷ 5 = 23
        assertEquals(23.0, r.temp, 0.0)
    }

    @Test fun 한번에_최대_2도() {
        val r = recommendTemp(mapOf("a" to p("high", 20.0)), emptyList(), now, 26.0)!!
        assertEquals(24.0, r.temp, 0.0)
        assertEquals(20.0, r.rawTemp, 0.0)
        assertTrue(r.shouldPropose)
    }

    @Test fun 차이가_작으면_제안하지_않음() {
        val r = recommendTemp(mapOf("a" to p("high", 23.0)), emptyList(), now, 23.0)!!
        assertFalse(r.shouldPropose)
    }

    @Test fun 추워요는_희망_온도를_올려서_계산() {
        val reps = listOf(RecentReport("a", Discomfort.COLD, now - 60_000))
        val r = recommendTemp(mapOf("a" to p("low", 22.0)), reps, now, null)!!
        assertEquals(23.0, r.temp, 0.0)
    }

    @Test fun 희망_온도가_없으면_추천_없음() {
        assertNull(recommendTemp(mapOf("a" to p("high", null)), emptyList(), now, null))
    }

    @Test fun 범위는_18에서_26() {
        val reps = listOf(RecentReport("a", Discomfort.COLD, now))
        val r = recommendTemp(mapOf("a" to p("high", 26.0)), reps, now, null)!!
        assertEquals(26.0, r.temp, 0.0)
    }
}
