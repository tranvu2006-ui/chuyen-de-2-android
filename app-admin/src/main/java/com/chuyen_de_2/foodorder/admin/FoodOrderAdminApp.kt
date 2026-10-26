package com.chuyen_de_2.foodorder.admin

import android.app.Application
import com.chuyen_de_2.foodorder.core.util.Constants
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FoodOrderAdminApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL).setPersistenceEnabled(true)
        } catch (_: Exception) {}
    }
}
