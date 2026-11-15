package com.carfortable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 휴식처 찾기 화면 상태
data class RestUiState(
    val searching: Boolean = false,
    val result: RestSearchResult? = null,
    val addedId: String? = null,
    val addingId: String? = null   // 내비 응답을 기다리는 휴식처
)

// 지도에 찍을 점: 현재 위치 + 휴식처 후보(번호)
fun mapPins(navi: NaviState, rest: RestUiState): List<MapPin> {
    val pins = mutableListOf<MapPin>()
    if (navi.lat != null && navi.lng != null) pins += MapPin(navi.lat, navi.lng, isCar = true)
    rest.result?.stops?.forEachIndexed { i, s -> pins += MapPin(s.lat, s.lng, number = i + 1) }
    return pins
}

// ───────────────────────── 2. 홈 (지도) ─────────────────────────

@Composable
fun HomeScreen(navi: NaviState, rest: RestUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.MapGray)
    ) {
        CarMap(pins = mapPins(navi, rest), modifier = Modifier.fillMaxSize())

        // 오른쪽 아래: 지금 경로 요약
        RouteSummaryChip(
            navi = navi,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun RouteSummaryChip(navi: NaviState, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (navi.guiding && navi.destinationName != null) {
            Text("${navi.destinationName}까지", fontSize = 15.sp, color = CarColors.SubText)
            Text(routeText(navi), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
            if (navi.waypoints.isNotEmpty()) {
                Text("경유 ${navi.waypoints.joinToString(", ")}", fontSize = 15.sp, color = CarColors.Green)
            }
        } else {
            Text(
                if (navi.connected) "안내 중인 경로 없음" else "내비 연결 중...",
                fontSize = 14.sp, color = CarColors.SubText
            )
        }
    }
}

// "86km · 1시간 12분 · 12:40 도착"
private fun routeText(navi: NaviState): String {
    val parts = mutableListOf<String>()
    navi.remainDistanceM?.let { parts += "${"%.0f".format(it / 1000.0)}km" }
    navi.remainTimeS?.let { s ->
        val min = s / 60
        parts += if (min >= 60) "${min / 60}시간 ${min % 60}분" else "${min}분"
        val arrive = Date(System.currentTimeMillis() + s * 1000L)
        parts += "${SimpleDateFormat("HH:mm", Locale.KOREA).format(arrive)} 도착"
    }
    return parts.joinToString(" · ").ifBlank { "경로 정보를 받는 중..." }
}

// ───────────────────────── 5. 여정 케어 (휴식처 찾기 → 경유지 추가) ─────────────────────────

@Composable
fun JourneyScreen(
    navi: NaviState,
    rest: RestUiState,
    onSearch: () -> Unit,
    onAdd: (RestStop) -> Unit,
    onOpenNaviSearch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (rest.addedId != null) "휴식을 위해 쉼터에 가는 중" else "가까운 휴식처 찾기",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CarColors.Text
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 왼쪽: 지도 + 지금 경로
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CarColors.Card)
                    .padding(16.dp)
            ) {
                Text("지금 경로", fontWeight = FontWeight.SemiBold, color = CarColors.Text)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !navi.connected -> "내비 연결 중..."
                        navi.guiding && navi.destinationName != null -> "${navi.destinationName} · ${routeText(navi)}"
                        else -> "안내 중인 경로 없음 (휴식처를 고르면 그곳으로 바로 안내해요)"
                    },
                    fontSize = 15.sp, color = CarColors.Text
                )
                if (navi.waypoints.isNotEmpty()) {
                    Text("경유지: ${navi.waypoints.joinToString(", ")}", fontSize = 14.sp, color = CarColors.Green)
                }
                Spacer(Modifier.height(12.dp))
                CarMap(
                    pins = mapPins(navi, rest),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                )
                navi.message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        fontSize = 14.sp, color = CarColors.Green, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarColors.GreenLight)
                            .padding(12.dp)
                    )
                }
            }

            // 오른쪽: 휴식처 목록
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("가까운 휴식처", fontWeight = FontWeight.SemiBold, color = CarColors.Text)
                        Text(sourceText(rest), fontSize = 14.sp, color = CarColors.SubText)
                    }
                    OutlinedButton(onClick = onSearch, enabled = !rest.searching) {
                        Text(if (rest.searching) "찾는 중..." else if (rest.result == null) "휴식처 찾기" else "다시 찾기")
                    }
                }

                val stops = rest.result?.stops.orEmpty()
                when {
                    rest.result == null && !rest.searching -> EmptyCard("[휴식처 찾기]를 누르면 현재 위치 20km 안의 휴게소·졸음쉼터를 찾아요.")
                    rest.result != null && stops.isEmpty() -> {
                        EmptyCard("휴식처를 찾지 못했어요. 내비에서 직접 찾아볼 수 있어요.")
                        OutlinedButton(onClick = onOpenNaviSearch) { Text("내비에서 '휴게소' 검색") }
                    }
                }
                stops.forEachIndexed { i, s ->
                    RestStopCard(
                        number = i + 1,
                        stop = s,
                        added = rest.addedId == s.id,
                        adding = rest.addingId == s.id,
                        enabled = rest.addingId == null,
                        onAdd = { onAdd(s) }
                    )
                }
            }
        }
    }
}

private fun sourceText(rest: RestUiState): String = when (rest.result?.source) {
    RestSource.KAKAO -> "카카오 장소 검색 · 현재 위치 기준 직선거리"
    RestSource.CACHE -> "검색이 안 돼서 최근 검색 결과를 보여줘요"
    RestSource.NONE -> "검색 실패: ${rest.result?.error ?: "알 수 없음"}"
    null -> "동승자가 쉬고 싶다고 하면 여기서 찾아요"
}

@Composable
private fun EmptyCard(text: String) {
    Text(
        text,
        fontSize = 14.sp, color = CarColors.SubText,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Card)
            .padding(20.dp)
    )
}

@Composable
private fun RestStopCard(
    number: Int,
    stop: RestStop,
    added: Boolean,
    adding: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (added) CarColors.GreenLight else CarColors.Card)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFD9822B))
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text("$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stop.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
            Text(
                "약 ${"%.1f".format(stop.distanceM / 1000.0)}km · ${stop.address}",
                fontSize = 15.sp, color = CarColors.SubText
            )
        }
        Spacer(Modifier.width(10.dp))
        if (added) {
            Text("✓ 추가됨", color = CarColors.Green, fontWeight = FontWeight.Bold)
        } else if (adding) {
            Text("내비 확인 중...", color = CarColors.SubText, fontWeight = FontWeight.SemiBold)
        } else {
            Button(
                onClick = onAdd,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = CarColors.Green),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("경유지로 추가")
            }
        }
    }
}