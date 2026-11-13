package com.carfortable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carfortable.shared.Discomfort
import com.carfortable.shared.Seat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 피그마 색상
object CarColors {
    val Background = Color(0xFFF5F5F5)
    val Card = Color.White
    val Green = Color(0xFF1E4D2B)
    val GreenLight = Color(0xFFE3EDDF)
    val Text = Color(0xFF222222)
    val SubText = Color(0xFF777777)
    val MapGray = Color(0xFFDADADA)
    val Alert = Color(0xFFFCF0E3)
}

enum class CarTab(val label: String, val symbol: String) {
    HOME("홈", "⌂"),
    PASSENGERS("탑승자", "☺"),
    CLIMATE("실내 조율", "≡"),
    JOURNEY("여정 케어", "➜"),
    SETTINGS("설정", "⚙")
}

// ───────────────────────── 1. 메인 화면 (QR + 탑승자 확인) ─────────────────────────

@Composable
fun LobbyScreen(
    sessionId: String?,
    joinUrl: String,
    errorText: String?,
    driverName: String,
    passengers: List<PassengerItem>,
    onRemove: (PassengerItem) -> Unit,
    onStart: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽: 브랜드
        Column(modifier = Modifier.weight(0.8f)) {
            Text("🌱", fontSize = 32.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("편안타", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = CarColors.Green)
                Spacer(Modifier.width(10.dp))
                Text("Pyeonanta", fontSize = 18.sp, color = CarColors.Green,
                    modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("동승자를 위한 맞춤형 차량 케어", fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, color = CarColors.Text)
        }

        // 오른쪽: QR + 탑승자
        Column(modifier = Modifier.weight(1.4f)) {
            Text("오늘 함께할 멤버를 초대해요", fontSize = 26.sp,
                fontWeight = FontWeight.Bold, color = CarColors.Text)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CarColors.Card)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                QrPanel(
                    sessionId = sessionId,
                    joinUrl = joinUrl,
                    errorText = errorText,
                    modifier = Modifier.weight(1f)
                )
                PassengerPanel(
                    driverName = driverName,
                    passengers = passengers,
                    onRemove = onRemove,
                    onStart = onStart,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QrPanel(sessionId: String?, joinUrl: String, errorText: String?, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Pill(text = "휴대폰 카메라로 스캔")
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(200.dp)
                .border(1.dp, CarColors.SubText, RoundedCornerShape(12.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                errorText != null -> Text(errorText, color = Color.Red, textAlign = TextAlign.Center)
                joinUrl.isEmpty() -> Text("세션 준비 중...", color = CarColors.SubText)
                else -> {
                    val qr = remember(joinUrl) { QrCode.bitmap(joinUrl).asImageBitmap() }
                    Image(bitmap = qr, contentDescription = "참여 QR 코드", modifier = Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "스캔이 되지 않으면 편안타 웹에서\n아래 코드를 입력해 주세요",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = CarColors.Text, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .widthIn(min = 200.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CarColors.GreenLight)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(sessionId ?: "------", fontSize = 22.sp,
                fontWeight = FontWeight.Bold, color = CarColors.Text, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun PassengerPanel(
    driverName: String,
    passengers: List<PassengerItem>,
    onRemove: (PassengerItem) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier
) {
    var removeTarget by remember { mutableStateOf<PassengerItem?>(null) }
    val submitted = passengers.count { it.submitted }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${driverName}의 편안타", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("함께하는 탑승자", fontWeight = FontWeight.SemiBold, color = CarColors.Text)
            Spacer(Modifier.weight(1f))
            Text("$submitted / ${passengers.size}명", fontWeight = FontWeight.SemiBold, color = CarColors.Text)
        }
        Spacer(Modifier.height(10.dp))

        if (passengers.isEmpty()) {
            Text(
                "아직 참가한 탑승자가 없어요",
                color = CarColors.SubText,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        passengers.forEach { p ->
            PassengerRow(p, onRemoveClick = { removeTarget = p })
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = CarColors.Green),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("모두의 편안함 시작하기 →", fontWeight = FontWeight.Bold)
        }
    }

    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("탑승자 내보내기") },
            text = { Text("${target.name} 님(${Seat.label(target.seat)})을 이번 여정에서 뺄까요?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(target)
                    removeTarget = null
                }) { Text("내보내기") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun PassengerRow(p: PassengerItem, onRemoveClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (p.submitted) CarColors.GreenLight else CarColors.Card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CarColors.Card)
                .clickable(onClick = onRemoveClick),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", fontWeight = FontWeight.Bold, color = CarColors.Text)
        }
        Spacer(Modifier.width(10.dp))
        Text(p.name, fontWeight = FontWeight.Bold, color = CarColors.Text)
        Text(" · ${Seat.label(p.seat)}", fontSize = 13.sp, color = CarColors.SubText)
        Spacer(Modifier.weight(1f))
        Text(if (p.submitted) "✓" else "입력 중", fontSize = 13.sp,
            color = if (p.submitted) CarColors.Green else CarColors.SubText)
    }
}

// ───────────────────────── 공통 틀 (상단바 + 왼쪽 메뉴 + 하단바) ─────────────────────────

@Composable
fun CarShell(
    tab: CarTab,
    onTab: (CarTab) -> Unit,
    cabin: CabinState,
    airBusy: Boolean,
    onToggleAir: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(CarColors.Background)) {
        // 상단바
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌱 편안타", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CarColors.Green)
            Spacer(Modifier.width(8.dp))
            Text("PYEONANTA", fontSize = 11.sp, color = CarColors.SubText)
            Spacer(Modifier.weight(1f))
            ClockText()
        }

        // 가운데: 메뉴 + 내용
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.width(96.dp).fillMaxHeight().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CarTab.entries.forEach { t ->
                    NavItem(t, selected = t == tab, onClick = { onTab(t) })
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 20.dp, bottom = 8.dp)) {
                content()
            }
        }

        // 하단바: 현재 차량 환경
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✻", fontSize = 18.sp, color = CarColors.Green)
            Spacer(Modifier.width(10.dp))
            Text(cabin.driverTemp?.let { "${formatTemp(it)}°" } ?: "--°",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
            Spacer(Modifier.width(16.dp))
            AirChip(recirc = cabin.recirc, busy = airBusy, onClick = onToggleAir)
            Spacer(Modifier.width(16.dp))
            Text(
                cabin.targetTemp?.let { "추천 온도 ${formatTemp(it)}°" } ?: "추천 온도 대기 중",
                fontSize = 13.sp, color = CarColors.SubText
            )
        }
    }
}

@Composable
private fun NavItem(tab: CarTab, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) CarColors.GreenLight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tab.symbol, fontSize = 20.sp, color = CarColors.Green)
        Spacer(Modifier.height(4.dp))
        Text(tab.label, fontSize = 12.sp, color = CarColors.Text)
    }
}

// 외기/내기 토글 (누르면 차량에 바로 적용)
@Composable
private fun AirChip(recirc: Boolean?, busy: Boolean, onClick: () -> Unit) {
    val text = when {
        busy -> "적용 중..."
        recirc == true -> "내기 순환"
        recirc == false -> "외기 유입"
        else -> "외기/내기"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.GreenLight)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Green)
    }
}

@Composable
private fun ClockText() {
    var now by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentTime()
            delay(15_000)
        }
    }
    Text(now, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Text)
}

private fun currentTime(): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date())

private fun formatTemp(t: Double): String =
    if (t % 1.0 == 0.0) t.toInt().toString() else t.toString()

// ───────────────────────── 2. 홈 (지도 자리 + 알림 팝업) ─────────────────────────

// 운전자 확인을 기다리는 온도 제안
data class TempProposal(val target: Double)

@Composable
fun HomeScreen(
    requests: List<PendingRequest>,
    passengers: List<PassengerItem>,
    onConfirm: (PendingRequest) -> Unit,
    proposal: TempProposal?,
    approving: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 지도 자리 (내비게이션은 다음 작업)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CarColors.MapGray),
            contentAlignment = Alignment.Center
        ) {
            Text("지도 영역 (내비게이션 연동 예정)", color = CarColors.SubText)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1) 가장 오래된 불편 알림 하나
            requests.firstOrNull()?.let { req ->
                val name = passengers.firstOrNull { it.seat == req.seat }?.name ?: Seat.label(req.seat)
                val more = if (requests.size > 1) " · 대기 알림 ${requests.size - 1}건 더" else ""
                AlertBanner(
                    symbol = "♡",
                    background = CarColors.Alert,
                    title = "$name 님이 '${Discomfort.label(req.value)}' 알림을 보냈어요.",
                    subtitle = "${Seat.label(req.seat)}$more"
                ) {
                    GreenButton(text = "확인", onClick = { onConfirm(req) })
                }
            }

            // 2) 희망 온도 제안 (운전자 확인)
            proposal?.let { p ->
                TempProposalBanner(p, approving, onApprove, onDecline)
            }
        }
    }
}

@Composable
private fun TempProposalBanner(
    proposal: TempProposal,
    approving: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    AlertBanner(
        symbol = "🌡",
        background = CarColors.GreenLight,
        title = "탑승자 추천 온도 ${formatTemp(proposal.target)}℃로 맞출까요?",
        subtitle = "멀미 민감도를 반영한 추천 온도 · 운전석·조수석에 함께 적용"
    ) {
        TextButton(onClick = onDecline, enabled = !approving) {
            Text("나중에", color = CarColors.SubText)
        }
        Spacer(Modifier.width(6.dp))
        GreenButton(text = if (approving) "적용 중..." else "적용", enabled = !approving, onClick = onApprove)
    }
}

@Composable
private fun AlertBanner(
    symbol: String,
    background: Color,
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, fontSize = 24.sp, color = Color(0xFF9A6A3A))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CarColors.Text)
            Text(subtitle, fontSize = 13.sp, color = CarColors.SubText)
        }
        Spacer(Modifier.width(12.dp))
        actions()
    }
}

@Composable
private fun GreenButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = CarColors.Green),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text)
    }
}

// ───────────────────────── 4. 실내 조율 ─────────────────────────

@Composable
fun ClimateScreen(
    passengers: List<PassengerItem>,
    cabin: CabinState,
    proposal: TempProposal?,
    approving: Boolean,
    airBusy: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onToggleAir: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("최적의 실내환경 조성하기", fontSize = 26.sp,
            fontWeight = FontWeight.Bold, color = CarColors.Text)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 왼쪽: 탑승자별 희망 온도
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("탑승자별 희망 설정", fontWeight = FontWeight.SemiBold, color = CarColors.Text)
                if (passengers.isEmpty()) {
                    Text("참가한 탑승자가 없어요", color = CarColors.SubText)
                }
                passengers.forEach { p -> DesireRow(p) }
            }

            // 오른쪽: 현재 차량 상태 + 제안
            Column(
                modifier = Modifier
                    .weight(1.6f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CarColors.GreenLight)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(climateHeadline(cabin, proposal), fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = CarColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TempCard("운전석", cabin.driverTemp, "현재 설정", Modifier.weight(1f))
                    TempCard("조수석", cabin.passengerTemp, "현재 설정", Modifier.weight(1f))
                    TempCard(
                        "추천 온도", cabin.targetTemp,
                        "멀미 민감도 반영 · ${passengers.count { it.desiredTemp != null }}명 기준", Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CarColors.Card)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("공기 순환", fontWeight = FontWeight.SemiBold, color = CarColors.Text)
                        Text(
                            when (cabin.recirc) {
                                true -> "내기 순환 중 · 답답하면 외기로 바꿔 주세요"
                                false -> "외기 유입 중"
                                null -> "상태 확인 중"
                            },
                            fontSize = 13.sp, color = CarColors.SubText
                        )
                    }
                    GreenButton(
                        text = when {
                            airBusy -> "적용 중..."
                            cabin.recirc == false -> "내기로 바꾸기"
                            else -> "외기로 바꾸기"
                        },
                        enabled = !airBusy,
                        onClick = onToggleAir
                    )
                }
                if (proposal != null) {
                    TempProposalBanner(proposal, approving, onApprove, onDecline)
                }
            }
        }
    }
}

private fun climateHeadline(cabin: CabinState, proposal: TempProposal?): String = when {
    proposal != null -> "추천 온도 ${formatTemp(proposal.target)}℃ 적용을 기다리고 있어요"
    cabin.targetTemp == null -> "탑승자들의 희망 온도를 기다리고 있어요"
    cabin.tempStatus == "driver_set" -> "운전자가 정한 온도를 유지하고 있어요"
    else -> "모두가 편안하도록 실내환경을 맞췄어요"
}

@Composable
private fun DesireRow(p: PassengerItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarColors.Card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${p.name} · ${Seat.label(p.seat)}", fontWeight = FontWeight.Bold, color = CarColors.Text)
            Text("멀미 민감도 ${sensitivityLabel(p.sensitivity)}", fontSize = 13.sp, color = CarColors.SubText)
        }
        Text(
            p.desiredTemp?.let { "${formatTemp(it)}°" } ?: "미입력",
            fontSize = if (p.desiredTemp != null) 24.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (p.desiredTemp != null) CarColors.Green else CarColors.SubText
        )
    }
}

@Composable
private fun TempCard(title: String, temp: Double?, caption: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarColors.Card)
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = CarColors.Text)
        Spacer(Modifier.height(8.dp))
        Text(temp?.let { "${formatTemp(it)}℃" } ?: "--", fontSize = 34.sp,
            fontWeight = FontWeight.Bold, color = CarColors.Text)
        Spacer(Modifier.height(4.dp))
        Text(caption, fontSize = 12.sp, color = CarColors.SubText)
    }
}

// 웹에서 보내는 값이 정해지면 shared로 옮겨요
private fun sensitivityLabel(value: String?): String = when (value?.lowercase()) {
    null, "" -> "미입력"
    "none" -> "없음"
    "low" -> "낮음"
    "mid", "medium", "normal" -> "보통"
    "high" -> "높음"
    else -> value.orEmpty()
}

// ───────────────────────── 아직 안 만든 탭 (빈 화면) ─────────────────────────

@Composable
fun ComingSoonScreen(tab: CarTab) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.Card)
    )
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarColors.GreenLight)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CarColors.Green)
    }
}

// ───────────────────────── 미리보기 ─────────────────────────

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun LobbyPreview() {
    LobbyScreen(
        sessionId = "test02",
        joinUrl = "https://motioncare-fec56.web.app/join?session=test02",
        errorText = null,
        driverName = "건호",
        passengers = listOf(
            PassengerItem("front_right", "윤지", true),
            PassengerItem("rear_left", "현아", true),
            PassengerItem("rear_right", "성윤", false)
        ),
        onRemove = {},
        onStart = {}
    )
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomePreview() {
    CarShell(
        tab = CarTab.HOME,
        onTab = {},
        cabin = CabinState(driverTemp = 22.0, recirc = true, targetTemp = 23.0),
        airBusy = false,
        onToggleAir = {}
    ) {
        HomeScreen(
            requests = listOf(PendingRequest("r1", "rear_left", "nausea", null)),
            passengers = listOf(PassengerItem("rear_left", "현아", true)),
            onConfirm = {},
            proposal = TempProposal(23.0),
            approving = false,
            onApprove = {},
            onDecline = {}
        )
    }
}