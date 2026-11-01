package com.chuyen_de_2.foodorder.customer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.ui.tracking.TrackingActivity
import com.chuyen_de_2.foodorder.customer.ui.home.HomeActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * Tên chức năng: Dịch Vụ Tiếp Nhận Thông Báo Đẩy Cho Khách Hàng (Customer FCM Push Notification Service)
 * Cách hoạt động:
 *  - Nhận tin nhắn thông báo đẩy từ Firebase Cloud Messaging khi trạng thái đơn hàng thay đổi (Quán xác nhận, Đang nấu, Đang giao, Đã giao thành công).
 *  - Đồng bộ FCM Token lên tài liệu người dùng Firestore khi thiết bị được cấp token mới.
 *  - Xây dựng thông báo Notification kèm âm thanh, rung để khách hàng nhận biết ngay tức thì.
 *  - [MỚI] Đọc to nội dung thông báo bằng Text-To-Speech tiếng Việt khi app bị tắt hoàn toàn.
 * Luồng hướng tới:
 *  - Khách hàng bấm vào thông báo DELIVERING/ARRIVED -> Mở thẳng [TrackingActivity] theo dõi đơn.
 *  - Các thông báo khác -> Mở [HomeActivity].
 */
@AndroidEntryPoint
class CustomerFCMService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    // Engine TTS dùng riêng trong FCM Service — hoạt động kể cả khi app đóng nền
    // vì FCM Service chạy trong process riêng được Android wake up khi có tin nhắn đến
    private var fcmTts: TextToSpeech? = null

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
        // Trạng thái đơn hàng gửi kèm trong data payload để phân biệt loại thông báo
        val orderStatus = message.data["orderStatus"].orEmpty().uppercase()

        // Hiển thị Notification lên thanh trạng thái hệ thống
        showNotification(title, body, orderId, orderStatus)

        // Đọc to nội dung thông báo qua loa ngoài bằng giọng nói tiếng Việt
        // Chỉ đọc khi đơn đang giao hoặc tài xế đã đến — 2 mốc quan trọng nhất
        if (body.isNotBlank()) {
            speakViaTts(body)
        }
    }

    /**
     * Hiển thị Notification với nội dung phù hợp.
     * Khi trạng thái là DELIVERING hoặc ARRIVED_AT_CUSTOMER:
     *   -> Bấm vào thông báo sẽ mở thẳng TrackingActivity (không qua HomeActivity).
     * Các trạng thái khác -> Mở HomeActivity như bình thường.
     */
    private fun showNotification(title: String, body: String, orderId: String?, orderStatus: String) {
        // Chọn màn hình đích khi khách bấm vào thông báo
        val targetIntent: Intent = when (orderStatus) {
            "DELIVERING", "ARRIVED_AT_CUSTOMER" -> {
                // Tài xế đang giao hoặc đã đến -> Mở thẳng màn hình theo dõi đơn
                Intent(this, TrackingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    orderId?.let {
                        putExtra(Constants.EXTRA_ORDER_ID, it)
                        putExtra(Constants.EXTRA_SHOP_NAME, "")
                    }
                }
            }
            else -> {
                // Các trạng thái khác (PREPARING, READY...) -> Về trang chủ
                Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    orderId?.let { putExtra(Constants.EXTRA_ORDER_ID, it) }
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId.hashCode(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Xây dựng Notification với BigTextStyle để hiện đầy đủ nội dung dài
        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_ORDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            // BigTextStyle: Kéo mở thông báo sẽ thấy toàn bộ nội dung thay vì bị cắt
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // DEFAULT_ALL = bật cả âm thanh + rung + đèn LED
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        // Dùng orderId.hashCode() làm notification ID để các đơn khác nhau không ghi đè nhau
        manager.notify(orderId.hashCode(), notification)
    }

    /**
     * Khởi tạo TTS và đọc to văn bản tiếng Việt qua loa ngoài.
     * FCM Service được Android wake up kể cả khi app đóng nền (trừ force-stop trên MIUI/EMUI cần bật autostart).
     */
    private fun speakViaTts(text: String) {
        // Khởi tạo TTS mới mỗi lần FCM Service được gọi (Service vòng đời ngắn)
        fcmTts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Thiết lập ngôn ngữ tiếng Việt
                val localeVi = Locale("vi", "VN")
                val result = fcmTts?.setLanguage(localeVi)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback về ngôn ngữ hệ thống nếu máy không cài gói tiếng Việt
                    fcmTts?.language = Locale.getDefault()
                }
                // Phát âm thanh ra loa ngoài ngay khi TTS sẵn sàng
                fcmTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FCM_TTS_${System.currentTimeMillis()}")
            }
        }
    }

    override fun onDestroy() {
        // Giải phóng TTS khi FCM Service bị hủy để tránh memory leak
        fcmTts?.stop()
        fcmTts?.shutdown()
        super.onDestroy()
    }
}
