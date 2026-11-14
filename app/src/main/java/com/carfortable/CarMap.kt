package com.carfortable

import android.graphics.Paint
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// 지도에 찍을 점: 현재 차량 위치, 휴식처(번호)
data class MapPin(val lat: Double, val lng: Double, val number: Int? = null, val isCar: Boolean = false)

// MapLibre(OpenGL) + OpenFreeMap 지도 (API 키 필요 없음)
// 손으로 움직이지 않는 지도: 점들이 다 보이게 카메라를 맞추고, 점은 Compose로 위에 그려요
@Composable
fun CarMap(pins: List<MapPin>, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(CarColors.MapGray))
        return
    }
    val ctx = LocalContext.current
    // MainActivity(ComponentActivity)의 생명주기에 맞춰 지도를 켜고 꺼요
    val lifecycle = (ctx as LifecycleOwner).lifecycle
    val latestPins by rememberUpdatedState(pins)
    var failed by remember { mutableStateOf(false) }
    var projected by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val mapHolder = remember { arrayOfNulls<MapLibreMap>(1) }

    val view = remember {
        MapLibre.getInstance(ctx)
        MapView(
            ctx,
            MapLibreMapOptions.createFromAttributes(ctx)
                .textureMode(true)
                .logoEnabled(false)
                .attributionEnabled(false)
                .compassEnabled(false)
        ).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            onCreate(null)
            addOnDidFailLoadingMapListener { failed = true }
        }
    }

    fun project(map: MapLibreMap) {
        projected = latestPins.map { p ->
            val pt = map.projection.toScreenLocation(LatLng(p.lat, p.lng))
            Offset(pt.x, pt.y)
        }
    }

    fun fit(map: MapLibreMap) {
        val points = latestPins.map { LatLng(it.lat, it.lng) }.distinct()
        if (points.isEmpty() || view.width == 0) return
        if (points.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 12.0))
        } else {
            val pad = 80
            val cam = map.getCameraForLatLngBounds(
                LatLngBounds.Builder().includes(points).build(), intArrayOf(pad, pad, pad, pad)
            )
            if (cam != null) map.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
            if (map.cameraPosition.zoom > 14.0) map.moveCamera(CameraUpdateFactory.zoomTo(14.0))
        }
        project(map)
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            view.onPause(); view.onStop(); view.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        view.getMapAsync { map ->
            mapHolder[0] = map
            map.uiSettings.setAllGesturesEnabled(false)
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { fit(map) }
        }
    }

    // 크기가 정해지면(처음 그려질 때 포함) 카메라 다시 맞추기
    DisposableEffect(view) {
        val onLayout = android.view.View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or2, ob ->
            if (r - l != or2 - ol || b - t != ob - ot) {
                mapHolder[0]?.let { m -> if (m.style?.isFullyLoaded == true) fit(m) }
            }
        }
        view.addOnLayoutChangeListener(onLayout)
        onDispose { view.removeOnLayoutChangeListener(onLayout) }
    }

    // 점이 바뀌면 카메라 다시 맞추기
    LaunchedEffect(pins) {
        mapHolder[0]?.let { if (it.style?.isFullyLoaded == true) fit(it) }
    }

    Box(modifier) {
        AndroidView({ view }, Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val textPaint = Paint().apply {
                color = Color.White.toArgb()
                textSize = 13.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            pins.forEachIndexed { i, p ->
                val c = projected.getOrNull(i) ?: return@forEachIndexed
                if (p.isCar) {
                    drawCircle(Color.White, 12.dp.toPx(), c)
                    drawCircle(Color(0xFF2F6FED), 8.dp.toPx(), c)
                } else {
                    drawCircle(Color.White, 15.dp.toPx(), c)
                    drawCircle(Color(0xFFD9822B), 12.dp.toPx(), c)
                    p.number?.let { n ->
                        drawContext.canvas.nativeCanvas.drawText(
                            "$n", c.x, c.y + textPaint.textSize / 3, textPaint
                        )
                    }
                }
            }
        }

        Text(
            text = when {
                failed -> "지도를 불러오지 못했어요"
                pins.isEmpty() -> "현재 위치를 기다리는 중..."
                else -> "© OpenFreeMap © OpenStreetMap"
            },
            fontSize = 11.sp,
            color = CarColors.SubText,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"