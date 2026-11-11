package com.chuyen_de_2.foodorder.internal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity

/**
 * Foreground Service giữ tiến trình Quán luôn hoạt động ở chế độ Tiền cảnh (Foreground).
 * Ngăn chặn triệt để các trình tối ưu pin của mọi hãng máy (Xiaomi, Vivo, OPPO, Samsung...)
 * đảm bảo BankNotificationListenerService luôn kết nối và bắt thông báo ngân hàng 24/7.
 */
class MerchantKeepAliveService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 9002
        private const val CHANNEL_ID = "channel_merchant_keep_alive"

        fun start(context: Context) {
            try {
                val intent = Intent(context, MerchantKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("MerchantKeepAlive", "Lỗi khởi động service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MerchantKeepAliveService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.e("MerchantKeepAlive", "Lỗi dừng service: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("MerchantKeepAlive", "Lỗi startForeground: ${e.message}")
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kênh Giám Sát Đơn Quán Ăn",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối tự động duyệt thanh toán VietQR và đơn mới 24/7"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("🟢 Oishi Merchant: Đang Mở Bán")
            .setContentText("Hệ thống tự động duyệt VietQR & đón đơn mới 24/7")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

