package com.chuyen_de_2.foodorder.internal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.chuyen_de_2.foodorder.core.util.Constants
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FoodOrderInternalApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase Offline Persistence
        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL).setPersistenceEnabled(true)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Channel cho thông báo đơn hàng (chuông báo)
        val orderChannel = NotificationChannel(
            Constants.CHANNEL_ID_ORDER,
            Constants.CHANNEL_NAME_ORDER,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Cảnh báo đơn hàng mới cho Shop/Shipper"
            enableVibration(true)
            enableLights(true)
        }

        // Channel cho Foreground Service GPS
        val trackingChannel = NotificationChannel(
            Constants.CHANNEL_ID_TRACKING,
            Constants.CHANNEL_NAME_TRACKING,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Dịch vụ theo dõi vị trí GPS"
        }

        manager.createNotificationChannel(orderChannel)
        manager.createNotificationChannel(trackingChannel)
    }
}
