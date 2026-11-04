package com.chuyen_de_2.foodorder.core.data.remote

import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.data.model.MenuItem
import com.chuyen_de_2.foodorder.core.data.model.Restaurant
import com.chuyen_de_2.foodorder.core.data.model.Store
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Retrofit Interface — API Contract (Section 5 & 14 - doc-adr)
 *
 * Gọi dữ liệu tĩnh (ít thay đổi) qua REST API để giảm tải Firebase.
 * Auth: Bearer Token JWT
 */
interface RestBackendApi {

    /**
     * Lấy danh sách cửa hàng / quán ăn
     * GET /api/v1/stores
     */
    @GET("api/v1/stores")
    suspend fun getStores(
        @Header("Authorization") token: String = ""
    ): Response<List<Store>>

    /**
     * Lấy danh sách món ăn theo cửa hàng
     * GET /api/v1/stores/{id}/dishes
     */
    @GET("api/v1/stores/{id}/dishes")
    suspend fun getDishesByStore(
        @Path("id") storeId: String,
        @Header("Authorization") token: String = ""
    ): Response<List<Dish>>

    // ==========================================
    // Legacy Endpoints (giữ nguyên để tránh break code cũ)
    // ==========================================

    /**
     * Lấy danh sách nhà hàng (Legacy)
     * GET /api/v1/restaurants
     */
    @GET("api/v1/restaurants")
    suspend fun getRestaurants(
        @Header("Authorization") token: String = ""
    ): Response<List<Restaurant>>

    /**
     * Lấy menu món ăn theo nhà hàng (Legacy)
     * GET /api/v1/restaurants/{id}/menu
     */
    @GET("api/v1/restaurants/{id}/menu")
    suspend fun getMenuByRestaurant(
        @Path("id") shopId: String,
        @Header("Authorization") token: String = ""
    ): Response<List<MenuItem>>
}
