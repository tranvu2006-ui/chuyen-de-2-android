package com.chuyen_de_2.foodorder.customer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Tên chức năng: Dịch Vụ Tiếp Nhận Thông Báo Đẩy Cho Khách Hàng (Customer FCM Push Notification Service)
 * Cách hoạt động:
 *  - Nhận tin nhắn thông báo đẩy từ Firebase Cloud Messaging khi trạng thái đơn hàng thay đổi (Quán xác nhận, Đang nấu, Đang giao, Đã giao thành công).
 *  - Đồng bộ FCM Token lên tài liệu người dùng Firestore khi thiết bị được cấp token mới.
 *  - Xây dựng thông báo Notification kèm âm thanh, rung để khách hàng nhận biết ngay tức thì.
 * Luồng hướng tới:
 *  - Khách hàng bấm vào thông báo -> Khởi chạy ứng dụng và điều hướng vào [HomeActivity] hoặc xem chi tiết đơn hàng tương ứng.
 */
@AndroidEntryPoint
class CustomerFCMService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        kotlinx.coroutines.runBlocking {
            try {
                authRepository.updateFcmToken(token)
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "Thông báo"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val orderId = message.data["orderId"]

        showNotification(title, body, orderId)
    }

    private fun showNotification(title: String, body: String, orderId: String?) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            orderId?.let { putExtra(Constants.EXTRA_ORDER_ID, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_ORDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
