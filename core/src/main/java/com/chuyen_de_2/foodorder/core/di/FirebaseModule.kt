package com.chuyen_de_2.foodorder.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module cung cấp các Firebase instances đã được tối ưu hóa bộ nhớ đệm (Disk Persistence)
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val db = FirebaseDatabase.getInstance(com.chuyen_de_2.foodorder.core.util.Constants.FIREBASE_DATABASE_URL)
        try {
            // Bật lưu trữ cục bộ (Disk Persistence) 50MB để tăng tốc tải dữ liệu và giảm băng thông
            db.setPersistenceCacheSizeBytes(50L * 1024L * 1024L)
            db.setPersistenceEnabled(true)
        } catch (_: Exception) {
            // Đã khởi tạo trước đó trong cùng process
        }
        return db
    }

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging {
        return FirebaseMessaging.getInstance()
    }
}
