package com.chuyen_de_2.foodorder.customer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.chuyen_de_2.foodorder.core.util.Constants
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — Customer App
 *
 * Trade-off (Section 13 - doc-adr): Chọn Phương án 1 — Firebase Offline Persistence.
 * Bật setPersistenceEnabled(true) để Firebase tự cache disk,
 * giải quyết bài toán offline giỏ hàng mà không cần Room Database.
 */
@HiltAndroidApp
class FoodOrderCustomerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Phương án 1: Firebase Offline Persistence (Section 13 - doc-adr)
        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL).setPersistenceEnabled(true)

        // Tạo Notification Channels (Android 8.0+)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val orderChannel = NotificationChannel(
            Constants.CHANNEL_ID_ORDER,
            Constants.CHANNEL_NAME_ORDER,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo về trạng thái đơn hàng"
            enableVibration(true)
        }

        manager.createNotificationChannel(orderChannel)
    }
}
