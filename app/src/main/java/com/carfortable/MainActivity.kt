package com.carfortable

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ai.pleos.playground.vehicle.Vehicle
import com.carfortable.shared.Discomfort
import com.carfortable.shared.Seat
import com.carfortable.ui.theme.MotionCareTheme

class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null
    private var session: SessionManager? = null
    private var tempSync: TempMedianSync? = null
    private var requestSync: CareRequestSync? = null

    // 화면에 보여줄 상태
    private var sessionStatus by mutableStateOf("세션 준비 중...")
    private var joinUrl by mutableStateOf("")
    private var tempStatus by mutableStateOf("희망 온도 대기 중...")
    private var pendingRequests by mutableStateOf<List<PendingRequest>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1) Vehicle SDK 연결
        val v = Vehicle(this)
        v.initialize()
        vehicle = v

        // 2) 세션 생성 (개발 세션 ID는 local.properties의 devSessionId)
        val hvac = HvacController(v)
        val s = SessionManager(hvac)
        session = s
        s.start(devSessionId = BuildConfig.DEV_SESSION_ID.ifBlank { null }) { result ->
            runOnUiThread {
                result
                    .onSuccess { sid ->
                        sessionStatus = "세션: $sid ✅"
                        joinUrl = SessionManager.joinUrl(sid)
                        startTempSync(sid, hvac)
                        startRequestSync(sid)
                    }
                    .onFailure { e ->
                        Log.e(TAG, "세션 생성 실패", e)
                        sessionStatus = "세션 실패: ${e.message}"
                    }
            }
        }

        setContent {
            MotionCareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        StatusText(text = sessionStatus)
                        if (joinUrl.isNotEmpty()) {
                            StatusText(text = joinUrl)
                        }
                        StatusText(text = tempStatus)
                        StatusText(text = "불편 알림 ${pendingRequests.size}건")
                        pendingRequests.forEach { req ->
                            RequestRow(
                                text = "${Seat.label(req.seat)} · ${Discomfort.label(req.value)}",
                                onConfirm = { requestSync?.acknowledge(req.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 3) 희망 온도 중앙값 적용 시작
    private fun startTempSync(sid: String, hvac: HvacController) {
        val sync = TempMedianSync(sid, hvac)
        sync.onStatus = { text -> runOnUiThread { tempStatus = text } }
        sync.start()
        tempSync = sync
    }

    // 4) 불편 알림 구독 시작
    private fun startRequestSync(sid: String) {
        val sync = CareRequestSync(sid)
        sync.onChange = { items -> runOnUiThread { pendingRequests = items } }
        sync.start()
        requestSync = sync
    }

    override fun onDestroy() {
        // 구독·SDK 해제 (세션 종료는 나중에 종료 버튼으로)
        tempSync?.stop()
        tempSync = null
        requestSync?.stop()
        requestSync = null
        vehicle?.release()
        vehicle = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MotionCare"
    }
}

@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier)
}

// 불편 알림 한 줄 (임시 화면, 다음 작업에서 피그마 레이아웃으로 교체)
@Composable
fun RequestRow(text: String, onConfirm: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text)
        Spacer(modifier = Modifier.width(12.dp))
        Button(onClick = onConfirm) {
            Text(text = "확인")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusTextPreview() {
    MotionCareTheme {
        RequestRow(text = "뒷좌석 왼쪽 · 메스꺼워요", onConfirm = {})
    }
}