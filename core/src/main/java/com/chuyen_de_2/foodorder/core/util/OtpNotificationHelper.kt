package com.chuyen_de_2.foodorder.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.R
import kotlin.random.Random

/**
 * Tiện ích gửi mã xác thực OTP thông qua Thông báo đẩy (Status Bar Notification / FCM).
 * Giải pháp miễn phí 100%, không phụ thuộc vào gói cước SMS của Firebase hay thẻ thanh toán.
 */
object OtpNotificationHelper {
    const val CHANNEL_ID_OTP = "channel_otp_notification"
    const val CHANNEL_NAME_OTP = "Mã xác thực OTP"
    const val NOTIFICATION_ID_OTP = 9999

    /**
     * Sinh mã OTP ngẫu nhiên gồm đúng 6 chữ số.
     */
    fun generateOtp(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    /**
     * Hiển thị thông báo đẩy chứa mã xác thực OTP trên thanh trạng thái (Status bar).
     */
    fun showOtpNotification(context: Context, otpCode: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_OTP,
                CHANNEL_NAME_OTP,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh thông báo nhận mã OTP xác thực tài khoản Oishi Food"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_OTP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Oishi Food")
            .setContentText("Mã xác thực Oishi Food của bạn là: $otpCode")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Mã xác thực Oishi Food của bạn là: $otpCode (Hiệu lực trong 5 phút). Vui lòng không chia sẻ mã này cho bất kỳ ai.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            manager.notify(NOTIFICATION_ID_OTP, notification)
        } catch (_: SecurityException) {
            // Android 13+ khi chưa cấp POST_NOTIFICATIONS
        }
    }
}
