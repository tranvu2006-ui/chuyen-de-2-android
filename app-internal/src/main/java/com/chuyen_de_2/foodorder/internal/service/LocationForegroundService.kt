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
 * Tên chức năng: Dịch Vụ Chạy Ngầm Theo Dõi Vị Trí GPS Tài Xế (Android Foreground Location Service)
 * Cách hoạt động:
 *  - Chạy dưới dạng Foreground Service với Notification thường trực (Ongoing) để chống bị Android OS tắt khi khóa màn hình (Doze Mode).
 *  - Dùng FusedLocationProviderClient lấy tọa độ GPS chính xác cao mỗi 5 giây (`GPS_UPDATE_INTERVAL_MS`).
 *  - Đẩy trực tiếp tọa độ (Latitude, Longitude, Timestamp) lên nút Firebase Realtime Database `tracking/{orderId}`.
 * Luồng hướng tới:
 *  - Ứng dụng Khách hàng [TrackingActivity] lắng nghe realtime nút này để vẽ biểu tượng xe máy di chuyển trực tiếp trên bản đồ.
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

    /**
     * Tên chức năng: Tiếp nhận lệnh khởi động dịch vụ (Start Command)
     * Cách hoạt động: Lấy orderId từ Intent, gọi [startForeground] hiển thị notification và kích hoạt luồng GPS.
     * Luồng hướng tới: Bắt đầu phát tọa độ vị trí.
     */
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

        return START_STICKY
    }

    /**
     * Tên chức năng: Tạo Notification thông báo trạng thái giao hàng
     * Cách hoạt động: Tạo thông báo thường trực trên khay hệ thống, gắn PendingIntent mở lại [DashboardActivity].
     * Luồng hướng tới: Hiển thị thông báo cố định cho người dùng.
     */
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
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Tên chức năng: Kích hoạt lắng nghe cập nhật tọa độ GPS (Location Updates)
     * Cách hoạt động: Đăng ký FusedLocationProviderClient với độ ưu tiên cao nhất PRIORITY_HIGH_ACCURACY, mỗi lần có tọa độ mới sẽ bắn lên Firebase Realtime Database.
     * Luồng hướng tới: Ghi dữ liệu vào Firebase Realtime Database node `tracking/{orderId}`.
     */
    @Suppress("MissingPermission")
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

    /**
     * Tên chức năng: Giải phóng tài nguyên GPS khi dừng dịch vụ
     * Cách hoạt động: Hủy đăng ký callback vị trí để ngắt phần cứng GPS giúp tiết kiệm pin.
     * Luồng hướng tới: Dừng Service an toàn.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
