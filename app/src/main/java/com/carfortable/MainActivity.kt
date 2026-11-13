package com.carfortable

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ai.pleos.playground.vehicle.Vehicle
import com.carfortable.ui.theme.MotionCareTheme

class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null
    private var session: SessionManager? = null
    private var tempSync: TempMedianSync? = null

    // 화면에 보여줄 상태
    private var sessionStatus by mutableStateOf("세션 준비 중...")
    private var joinUrl by mutableStateOf("")
    private var tempStatus by mutableStateOf("희망 온도 대기 중...")

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
        s.start(devSessionId = BuildConfig.DEV_SESSION_ID) { result ->
            runOnUiThread {
                result
                    .onSuccess { sid ->
                        sessionStatus = "세션: $sid ✅"
                        joinUrl = SessionManager.joinUrl(sid)
                        startTempSync(sid, hvac)
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

    override fun onDestroy() {
        // 구독·SDK 해제 (세션 종료는 나중에 종료 버튼으로)
        tempSync?.stop()
        tempSync = null
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

@Preview(showBackground = true)
@Composable
fun StatusTextPreview() {
    MotionCareTheme {
        StatusText("세션: test01 ✅")
    }
}