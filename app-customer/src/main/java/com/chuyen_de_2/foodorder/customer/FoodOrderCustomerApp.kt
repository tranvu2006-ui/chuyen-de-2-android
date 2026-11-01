package com.chuyen_de_2.foodorder.customer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import com.chuyen_de_2.foodorder.core.util.AccountLockWatcher
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.customer.service.OrderTrackingForegroundService
import com.chuyen_de_2.foodorder.customer.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import androidx.core.content.ContextCompat
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

        // Giám sát trạng thái khóa tài khoản thời gian thực qua WebSocket Firebase
        AccountLockWatcher.init(this, LoginActivity::class.java)

        // Ghim Notification bar cố định & giám sát trạng thái đơn hàng thời gian thực
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val serviceIntent = Intent(this, OrderTrackingForegroundService::class.java)
            if (auth.currentUser != null) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
        }
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
