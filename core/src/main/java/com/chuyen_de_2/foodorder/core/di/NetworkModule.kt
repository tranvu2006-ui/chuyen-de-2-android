package com.chuyen_de_2.foodorder.core.di

import com.chuyen_de_2.foodorder.core.data.remote.MockInterceptor
import com.chuyen_de_2.foodorder.core.data.remote.RestBackendApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt Module cung cấp OkHttpClient, Retrofit, RestBackendApi
 *
 * Lưu ý: MockInterceptor được thêm vào OkHttpClient để giả lập REST API.
 * Khi có backend thật, thay BASE_URL và xóa MockInterceptor.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Base URL placeholder — thay bằng URL backend thật khi có
    private const val BASE_URL = "https://api.foodorder.example.com/"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        mockInterceptor: MockInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(mockInterceptor) // Giả lập API — xóa khi có backend thật
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideRestBackendApi(retrofit: Retrofit): RestBackendApi {
        return retrofit.create(RestBackendApi::class.java)
    }
}
