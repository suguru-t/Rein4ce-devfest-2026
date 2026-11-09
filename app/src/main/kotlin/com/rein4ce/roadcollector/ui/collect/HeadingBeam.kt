package com.rein4ce.roadcollector.ui.collect

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rein4ce.roadcollector.collect.LocationRecordingService
import com.rein4ce.roadcollector.ui.inventory.Fieldbook

/*
 * 記録画面の地図に出す、スマホの向いている方位（Google マップの青い扇と同じ考え方）。
 * - 方位はスマホの向きのセンサー（TYPE_ROTATION_VECTOR）から取り、磁北と真北のずれ（偏角）を直す。止まっていても、体を回せば扇が回る
 * - 今いる場所に青い点と扇（記録していない間も、記録中も同じ印）。デモ散歩の再生中は、青い点だけで扇は出さない（スマホの向きと見本の道は関係がない）
 * - センサーと位置は、記録画面が前に出ている間（RESUMED）だけ動かす。裏に回ったり、ほかのタブに移ったりしたら止める
 */

/** 扇の半径（中心から先まで）。青い点のまわりに、指先ほどの長さで開く */
internal val BeamRadius = 56.dp

/** 扇の開き（左右合わせて） [度] */
private const val BEAM_SPREAD_DEG = 70f

/** 今いる場所の青い点の半径 */
internal val MyDotRadius = 7.dp

/** 記録していない間に位置を聞く間隔 [ms]。記録（LocationRecordingService）と同じくらいの細かさ */
private const val IDLE_INTERVAL_MS = 2_000L

/** 記録していない間の、今いる場所 */
internal data class IdleFix(val lat: Double, val lng: Double, val accuracyM: Float)

/** 記録画面が前に出ているか（RESUMED）。センサーと位置は、出ている間だけ動かす */
@Composable
private fun isResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * スマホの上側が向いている方位 [度]（真北が 0、時計回り）。センサーが無ければ null。
 * 値は 0〜360 に丸めず、近い回り方でつながった値にする（359° → 1° を 358° 戻らずに 2° 進む。地図の印をなめらかに回すため）。
 * 偏角は [lat], [lng] の所で求める（東京ではおよそ西へ 7〜8°）。場所が分からなければ磁北のまま
 */
@Composable
internal fun rememberCompassHeading(enabled: Boolean, lat: Double?, lng: Double?): State<Float?> {
    val context = LocalContext.current
    val view = LocalView.current
    val heading = remember { mutableStateOf<Float?>(null) }
    val declination by rememberUpdatedState(
        if (lat != null && lng != null) {
            GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination
        } else {
            0f
        },
    )
    val active = enabled && isResumed()
    DisposableEffect(active) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!active || manager == null || sensor == null) {
            if (!active) heading.value = null
            return@DisposableEffect onDispose { }
        }
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                // 画面を横にしているときも、画面の上側の方位にする
                val (x, y) = when (view.display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation, x, y, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val target = Math.toDegrees(orientation[0].toDouble()).toFloat() + declination
                heading.value = smoothHeading(heading.value, target)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            manager.unregisterListener(listener)
            heading.value = null
        }
    }
    return heading
}

/**
 * 方位をならす：前の値から、近い回り方で [target] へ少しだけ寄せる（手の小さな震えで扇が震えないように）。
 * 前の値が無ければ [target] そのもの。1° より小さい動きは無視して、地図の印を毎回描き直さない
 */
internal fun smoothHeading(previous: Float?, target: Float): Float {
    if (previous == null) return ((target % 360f) + 360f) % 360f
    val delta = ((target - previous) % 360f + 540f) % 360f - 180f
    if (kotlin.math.abs(delta) < 1f) return previous
    return previous + delta * 0.25f
}

/**
 * 記録していない間の今いる場所。[enabled] で、位置の許可があり、記録画面が前に出ている間だけ聞き続ける。
 * 聞き始めは、スマホが覚えている最後の位置をすぐ出す（地図を開いてから点が出るまで待たせない）
 */
@SuppressLint("MissingPermission") // hasLocationPermission で確かめている
@Composable
internal fun rememberIdleFix(enabled: Boolean): State<IdleFix?> {
    val context = LocalContext.current
    val fix = remember { mutableStateOf<IdleFix?>(null) }
    val active = enabled && isResumed() && LocationRecordingService.hasLocationPermission(context)
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation ?: return
                fix.value = IdleFix(l.latitude, l.longitude, l.accuracy)
            }
        }
        try {
            fused.lastLocation.addOnSuccessListener { l ->
                if (l != null && fix.value == null) fix.value = IdleFix(l.latitude, l.longitude, l.accuracy)
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, IDLE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(IDLE_INTERVAL_MS / 2)
                .build()
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w("HeadingBeam", "location permission was revoked", e)
        }
        onDispose { fused.removeLocationUpdates(callback) }
    }
    return fix
}

/**
 * 向いている方位の扇（地図の印）。上（北）へ開いた扇を描き、地図の印の rotation で方位へ回す（flat なので、地図を回しても方位のまま）。
 * 中心ほど濃く、先へ行くほど消える測量の青
 */
@Composable
internal fun HeadingBeamMarker() {
    val blue = Fieldbook.Survey
    Canvas(Modifier.size(BeamRadius * 2)) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawArc(
            brush = Brush.radialGradient(
                0f to blue.copy(alpha = 0.7f),
                0.5f to blue.copy(alpha = 0.4f),
                1f to blue.copy(alpha = 0f),
                center = center,
                radius = r,
            ),
            startAngle = -90f - BEAM_SPREAD_DEG / 2,
            sweepAngle = BEAM_SPREAD_DEG,
            useCenter = true,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
        )
    }
}

/** 今いる場所の青い点（白い縁取りで、地図のどの色の上でも見える） */
@Composable
internal fun MyDotMarker() {
    val blue = Fieldbook.Survey
    Canvas(Modifier.size(MyDotRadius * 2 + 6.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = MyDotRadius.toPx() + 3.dp.toPx(), center = center + Offset(0f, 1.dp.toPx()))
        drawCircle(Color.White, radius = MyDotRadius.toPx() + 2.5.dp.toPx(), center = center)
        drawCircle(blue, radius = MyDotRadius.toPx(), center = center)
    }
}
