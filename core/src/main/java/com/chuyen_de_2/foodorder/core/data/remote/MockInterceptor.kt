package com.chuyen_de_2.foodorder.core.data.remote

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor trả về mock JSON data.
 * Sử dụng khi chưa có REST API backend thực tế.
 * (Giả thiết 1 trong doc-adr Section 2: Backend REST API chưa sẵn sàng)
 *
 * Khi có backend thật, xóa interceptor này khỏi OkHttpClient trong NetworkModule.
 */
@Singleton
class MockInterceptor @Inject constructor() : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val uri = chain.request().url.toUri().toString()

        val responseString = when {
            (uri.contains("restaurants") || uri.contains("stores")) && (uri.contains("menu") || uri.contains("dishes")) -> getMockMenuJson()
            uri.contains("restaurants") || uri.contains("stores") -> getMockRestaurantsJson()
            else -> """{"data": [], "message": "Unknown endpoint", "success": false}"""
        }

        // Giả lập network delay 300ms
        Thread.sleep(300)

        return Response.Builder()
            .code(200)
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .message("OK (Mock)")
            .body(responseString.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun getMockRestaurantsJson(): String {
        return """
        [
            {
                "id": "shop_01",
                "name": "Cơm Tấm Sài Gòn",
                "phone": "0901234567",
                "address": "123 Nguyễn Trãi, Q.1, TP.HCM",
                "district": "Quận 1",
                "imageUrl": "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400",
                "rating": 4.5,
                "ratingAvg": 4.5,
                "category": "Cơm",
                "deliveryTime": "25-35 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            },
            {
                "id": "shop_02",
                "name": "Phở Hà Nội 36",
                "phone": "0902345678",
                "address": "456 Lê Lợi, Q.3, TP.HCM",
                "district": "Quận 3",
                "imageUrl": "https://images.unsplash.com/photo-1555126634-323283e090fa?w=400",
                "rating": 4.8,
                "ratingAvg": 4.8,
                "category": "Phở",
                "deliveryTime": "20-30 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            },
            {
                "id": "shop_03",
                "name": "Bún Bò Huế Cô Ba",
                "phone": "0903456789",
                "address": "789 Hai Bà Trưng, Q.Bình Thạnh, TP.HCM",
                "district": "Quận Bình Thạnh",
                "imageUrl": "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400",
                "rating": 4.3,
                "ratingAvg": 4.3,
                "category": "Bún",
                "deliveryTime": "30-40 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            },
            {
                "id": "shop_04",
                "name": "Bánh Mì Huỳnh Hoa",
                "phone": "0904567890",
                "address": "26 Lê Thị Riêng, Q.1, TP.HCM",
                "district": "Quận 1",
                "imageUrl": "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=400",
                "rating": 4.9,
                "ratingAvg": 4.9,
                "category": "Bánh mì",
                "deliveryTime": "15-25 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            },
            {
                "id": "shop_05",
                "name": "Trà Sữa ToCoToCo",
                "phone": "0905678901",
                "address": "321 Cách Mạng Tháng 8, Q.10, TP.HCM",
                "district": "Quận 10",
                "imageUrl": "https://images.unsplash.com/photo-1558857563-b371033873b8?w=400",
                "rating": 4.2,
                "ratingAvg": 4.2,
                "category": "Trà sữa",
                "deliveryTime": "20-30 phút",
                "isOpen": false,
                "storeStatus": "CLOSED"
            },
            {
                "id": "shop_06",
                "name": "Pizza Hut Express",
                "phone": "0906789012",
                "address": "55 Võ Văn Tần, Q.3, TP.HCM",
                "district": "Quận 3",
                "imageUrl": "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=400",
                "rating": 4.1,
                "ratingAvg": 4.1,
                "category": "Pizza",
                "deliveryTime": "30-45 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            }
        ]
        """.trimIndent()
    }

    private fun getMockMenuJson(): String {
        return """
        [
            {
                "id": "item_01",
                "storeId": "shop_01",
                "name": "Cơm sườn nướng",
                "description": "Cơm tấm sườn nướng than hoa kèm bì, chả, trứng ốp la",
                "price": 55000,
                "basePrice": 55000,
                "imageUrl": "https://images.unsplash.com/photo-1512058564366-18510be2db19?w=400",
                "category": "Món chính",
                "isAvailable": true
            },
            {
                "id": "item_02",
                "storeId": "shop_01",
                "name": "Cơm tấm bì chả",
                "description": "Cơm tấm bì chả truyền thống với nước mắm pha",
                "price": 40000,
                "basePrice": 40000,
                "imageUrl": "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400",
                "category": "Món chính",
                "isAvailable": true
            },
            {
                "id": "item_03",
                "storeId": "shop_01",
                "name": "Sườn cốt lết chiên",
                "description": "Sườn heo cốt lết chiên giòn ăn kèm cơm trắng",
                "price": 60000,
                "basePrice": 60000,
                "imageUrl": "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400",
                "category": "Món chính",
                "isAvailable": true
            },
            {
                "id": "item_04",
                "storeId": "shop_01",
                "name": "Canh chua cá lóc",
                "description": "Canh chua nấu cá lóc tươi với rau thơm",
                "price": 35000,
                "basePrice": 35000,
                "imageUrl": "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=400",
                "category": "Canh",
                "isAvailable": true
            },
            {
                "id": "item_05",
                "storeId": "shop_01",
                "name": "Gỏi cuốn tôm thịt",
                "description": "Gỏi cuốn tươi với tôm, thịt heo luộc, rau sống",
                "price": 30000,
                "basePrice": 30000,
                "imageUrl": "https://images.unsplash.com/photo-1562967916-eb82221dfb92?w=400",
                "category": "Khai vị",
                "isAvailable": true
            },
            {
                "id": "item_06",
                "storeId": "shop_01",
                "name": "Trà đá",
                "description": "Trà đá truyền thống",
                "price": 5000,
                "basePrice": 5000,
                "imageUrl": "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=400",
                "category": "Nước uống",
                "isAvailable": true
            },
            {
                "id": "item_07",
                "storeId": "shop_01",
                "name": "Nước ngọt Pepsi",
                "description": "Pepsi lon 330ml",
                "price": 15000,
                "basePrice": 15000,
                "imageUrl": "https://images.unsplash.com/photo-1629203851122-3726ecdf080e?w=400",
                "category": "Nước uống",
                "isAvailable": true
            },
            {
                "id": "item_08",
                "storeId": "shop_01",
                "name": "Chè ba màu",
                "description": "Chè đậu xanh, đậu đỏ, rau câu với nước cốt dừa",
                "price": 20000,
                "basePrice": 20000,
                "imageUrl": "https://images.unsplash.com/photo-1563805042-7684c019e1cb?w=400",
                "category": "Tráng miệng",
                "isAvailable": true
            }
        ]
        """.trimIndent()
    }
}
