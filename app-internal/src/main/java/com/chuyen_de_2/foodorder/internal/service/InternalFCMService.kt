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
 * FCM Service cho Internal App — đánh thức khi có đơn mới (Section 4.1 - doc-adr)
 *
 * Test Case 4 (Section 10 - doc-adr):
 * App Shop đã bị vuốt tắt hẳn đa nhiệm, nhận đơn hàng mới.
 * Expected: FCM Payload kích hoạt máy sáng màn hình, rung và đổ chuông.
 *
 * Mitigation (Section 12 - doc-adr):
 * FCM làm kênh liên lạc dự phòng.
 * Khi nhận Data Push, ép client gọi FirebaseDatabase.goOnline() để refresh socket.
 */
@AndroidEntryPoint
class InternalFCMService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        kotlinx.coroutines.runBlocking {
            try {
                authRepository.updateFcmToken(token)
            } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Mitigation: Refresh Firebase socket khi nhận push (Section 12 - doc-adr)
        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL).goOnline()

        val title = message.notification?.title ?: message.data["title"] ?: "Đơn hàng mới!"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        showHighPriorityNotification(title, body)
    }

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
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Rung + Chuông + LED
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // Đánh thức màn hình
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
