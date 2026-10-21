package com.chuyen_de_2.foodorder.internal.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.ui.dashboard.DashboardActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Tên chức năng: Dịch Vụ Thông Báo Đẩy Firebase Cloud Messaging (Internal FCM Push Service)
 * Cách hoạt động:
 *  - Nhận tin nhắn thông báo đẩy (Push Notification) từ Cloud Functions / Backend khi có đơn hàng mới phát sinh hoặc có cập nhật trạng thái đơn.
 *  - Tự động gọi `FirebaseDatabase.goOnline()` để đánh thức kết nối WebSocket của Firebase ngay cả khi ứng dụng đang chạy nền.
 *  - Hiển thị thông báo ưu tiên cao (High Priority Notification), rung, chuông báo và sáng màn hình để nhân viên quán hoặc tài xế không bị lỡ đơn.
 *  - Quản lý đồng bộ FCM Device Token lên tài khoản người dùng trên Firestore.
 * Luồng hướng tới:
 *  - Người dùng bấm vào thông báo -> Mở ngay [DashboardActivity] để xử lý đơn hàng.
 */
@AndroidEntryPoint
class InternalFCMService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    /**
     * Tên chức năng: Tiếp nhận và lưu trữ mã định danh thiết bị FCM Token mới
     * Cách hoạt động: Được kích hoạt tự động khi Firebase cấp mới hoặc xoay vòng Token thiết bị; lưu token vào tài liệu user trên Firestore.
     * Luồng hướng tới: Cập nhật FCM token phục vụ bắn thông báo đơn mục tiêu.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        kotlinx.coroutines.runBlocking {
            try {
                authRepository.updateFcmToken(token)
            } catch (_: Exception) {}
        }
    }

    /**
     * Tên chức năng: Tiếp nhận tin nhắn thông báo đẩy từ Firebase
     * Cách hoạt động: Trích xuất tiêu đề, nội dung và kích hoạt chuỗi hành động đánh thức socket Firebase rồi hiển thị thông báo khẩn cấp.
     * Luồng hướng tới: Gọi [showHighPriorityNotification].
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Refresh Firebase socket khi nhận push
        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL).goOnline()

        val title = message.notification?.title ?: message.data["title"] ?: "Đơn hàng mới!"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        showHighPriorityNotification(title, body)
    }

    /**
     * Tên chức năng: Hiển thị thông báo đẩy khẩn cấp (Heads-up Notification)
     * Cách hoạt động: Xây dựng Notification với kênh CHANNEL_ID_ORDER, thiết lập PRIORITY_HIGH, rung, chuông và FullScreenIntent để đánh thức màn hình điện thoại.
     * Luồng hướng tới: Kích hoạt màn hình thông báo và điều hướng sang [DashboardActivity] khi người dùng nhấn chọn.
     */
    private fun showHighPriorityNotification(title: String, body: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            .setFullScreenIntent(pendingIntent, true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
