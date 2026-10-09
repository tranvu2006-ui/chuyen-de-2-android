package com.chuyen_de_2.foodorder.internal.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.data.model.TrackingLocation
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.database.FirebaseDatabase

/**
 * Android Foreground Location Service — GPS Shipper (Section 3.3 - doc-adr)
 *
 * Xây dựng Android Foreground Location Service truyền GPS:
 * - Persistent notification (High Priority)
 * - FusedLocationProviderClient cập nhật GPS mỗi 5 giây
 * - Push tọa độ lên tracking/{orderId} Firebase
 * - Xử lý Doze Mode: IGNORE_BATTERY_OPTIMIZATIONS
 *
 * Risks & Mitigations (Section 12 - doc-adr):
 * - Xin cấp quyền IGNORE_BATTERY_OPTIMIZATIONS
 * - Dùng chuông báo notification High Priority
 * - Foreground Service không bị Android OS "kill"
 */
class LocationForegroundService : Service() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var orderId: String = ""

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: ""

        if (orderId.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Bắt đầu Foreground Service với Notification
        startForeground(Constants.FOREGROUND_SERVICE_ID, createNotification())

        // Bắt đầu lắng nghe GPS
        startLocationUpdates()

        return START_STICKY // Khởi động lại nếu bị kill
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_TRACKING)
            .setContentTitle("🛵 Đang giao hàng")
            .setContentText("GPS đang hoạt động — Đơn #${orderId.takeLast(6)}")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Không thể vuốt tắt
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    @Suppress("MissingPermission") // Permission checked in Fragment
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.GPS_UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(Constants.GPS_FASTEST_INTERVAL_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Push tọa độ lên Firebase tracking/{orderId}
                    val tracking = TrackingLocation(
                        lat = location.latitude,
                        lng = location.longitude,
                        updatedAt = System.currentTimeMillis()
                    )

                    FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
                        .getReference("tracking")
                        .child(orderId)
                        .setValue(tracking)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dừng cập nhật GPS khi service bị hủy
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
