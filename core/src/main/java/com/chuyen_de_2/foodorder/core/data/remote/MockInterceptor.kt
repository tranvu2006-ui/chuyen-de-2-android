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
            (uri.contains("restaurants") || uri.contains("stores")) && (uri.contains("menu") || uri.contains("dishes")) -> getMockMenuJson(uri)
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
                "merchantId": "whjjZ5osJhTNtMCovwgp8JXdJK73",
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
                "merchantId": "AdBaJDWpzDYJRxVdl2qbWX8jAUR2",
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
                "merchantId": "c3AlIlYhEDcGKC9Z4EYOlWqfeCN2",
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
                "merchantId": "uNzJQswcLSNnuF4GpQAlMdQnS1P2",
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
                "merchantId": "QCXRaghsICbmi1Pngptv3s0mWO32",
                "name": "Trà Sữa Oishi Cha",
                "phone": "0905678901",
                "address": "321 Cách Mạng Tháng 8, Q.10, TP.HCM",
                "district": "Quận 10",
                "imageUrl": "https://images.unsplash.com/photo-1558857563-b371033873b8?w=400",
                "rating": 4.6,
                "ratingAvg": 4.6,
                "category": "Trà sữa",
                "deliveryTime": "20-30 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            },
            {
                "id": "shop_06",
                "merchantId": "J5Cz86Vxd8RRfwp0iK8F1b2Rslx1",
                "name": "Gà Rán Giòn Cay Hot Crispy",
                "phone": "0906789012",
                "address": "55 Võ Văn Tần, Q.3, TP.HCM",
                "district": "Quận 3",
                "imageUrl": "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=400",
                "rating": 4.8,
                "ratingAvg": 4.8,
                "category": "Gà rán",
                "deliveryTime": "25-35 phút",
                "isOpen": true,
                "storeStatus": "OPEN"
            }
        ]
        """.trimIndent()
    }

    private fun getMockMenuJson(uri: String = ""): String {
        return when {
            uri.contains("shop_02") -> getPhoMenuJson()
            uri.contains("shop_03") -> getBunBoMenuJson()
            uri.contains("shop_04") -> getBanhMiMenuJson()
            uri.contains("shop_05") -> getTraSuaMenuJson()
            uri.contains("shop_06") -> getGaRanMenuJson()
            else -> getComTamMenuJson()
        }
    }

    // Quán 1: Cơm Tấm Sài Gòn (Chủ quán: manager@oishifood.vn)
    private fun getComTamMenuJson(): String {
        return """
        [
            {
                "id": "dish_01_01",
                "storeId": "shop_01",
                "name": "Cơm sườn nướng than hoa",
                "description": "Cơm tấm sườn nướng than hoa thơm lừng kèm bì, chả, trứng ốp la",
                "price": 55000,
                "basePrice": 55000,
                "imageUrl": "https://images.unsplash.com/photo-1512058564366-18510be2db19?w=400",
                "category": "Món chính",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1420,
                "ratingAvg": 4.9
            },
            {
                "id": "dish_01_02",
                "storeId": "shop_01",
                "name": "Cơm tấm bì chả đặc biệt",
                "description": "Cơm tấm bì chả truyền thống với nước mắm chua ngọt pha kẹo",
                "price": 40000,
                "basePrice": 40000,
                "imageUrl": "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400",
                "category": "Món chính",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 890,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_01_03",
                "storeId": "shop_01",
                "name": "Sườn cốt lết chiên giòn",
                "description": "Sườn heo cốt lết chiên giòn rụm ăn kèm cơm tấm nóng hổi",
                "price": 60000,
                "basePrice": 60000,
                "imageUrl": "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400",
                "category": "Món chính",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 650,
                "ratingAvg": 4.7
            },
            {
                "id": "dish_01_04",
                "storeId": "shop_01",
                "name": "Canh chua cá lóc",
                "description": "Canh chua nấu cá lóc tươi cùng bạc hà, cà chua, giá đỗ thanh mát",
                "price": 35000,
                "basePrice": 35000,
                "imageUrl": "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=400",
                "category": "Canh",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 420,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_01_05",
                "storeId": "shop_01",
                "name": "Gỏi cuốn tôm thịt",
                "description": "Gỏi cuốn bánh tráng tôm tươi, thịt heo ba chỉ luộc, rau thơm",
                "price": 30000,
                "basePrice": 30000,
                "imageUrl": "https://images.unsplash.com/photo-1562967916-eb82221dfb92?w=400",
                "category": "Khai vị",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 760,
                "ratingAvg": 4.9
            },
            {
                "id": "dish_01_06",
                "storeId": "shop_01",
                "name": "Trà Ô Long Xí Muội",
                "description": "Trà Ô Long ủ lạnh vị xí muội chua thanh mặn ngọt giải nhiệt",
                "price": 28000,
                "basePrice": 28000,
                "imageUrl": "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400",
                "category": "Nước uống",
                "isAvailable": true,
                "hasOptions": true,
                "soldCount": 1200,
                "ratingAvg": 4.9,
                "specs": ["Size L (+6k)", "50% Đường", "70% Đá", "Trân châu đen (+5k)"]
            },
            {
                "id": "dish_01_07",
                "storeId": "shop_01",
                "name": "Trà Sữa Trân Châu Hoàng Kim",
                "description": "Trà sữa đậm vị thơm béo kèm trân châu hoàng kim dai giòn",
                "price": 32000,
                "basePrice": 32000,
                "imageUrl": "https://images.unsplash.com/photo-1558857563-b371033873b8?w=400",
                "category": "Nước uống",
                "isAvailable": true,
                "hasOptions": true,
                "soldCount": 980,
                "ratingAvg": 4.8,
                "specs": ["Size M", "70% Đường", "Ít đá", "Thạch củ năng (+7k)"]
            },
            {
                "id": "dish_01_08",
                "storeId": "shop_01",
                "name": "Chè ba màu nước cốt dừa",
                "description": "Chè đậu xanh, đậu đỏ, sương sa bánh lọt nước cốt dừa thơm béo",
                "price": 20000,
                "basePrice": 20000,
                "imageUrl": "https://images.unsplash.com/photo-1563805042-7684c019e1cb?w=400",
                "category": "Tráng miệng",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 310,
                "ratingAvg": 4.7
            }
        ]
        """.trimIndent()
    }

    // Quán 2: Phở Hà Nội 36
    private fun getPhoMenuJson(): String {
        return """
        [
            {
                "id": "dish_02_01",
                "storeId": "shop_02",
                "name": "Phở Bò Tái Nạm Gầu",
                "description": "Bánh phở tươi mềm, nước dùng xương hầm 12 tiếng thơm quế hồi",
                "price": 55000,
                "basePrice": 55000,
                "imageUrl": "https://images.unsplash.com/photo-1555126634-323283e090fa?w=400",
                "category": "Phở",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1850,
                "ratingAvg": 4.9
            },
            {
                "id": "dish_02_02",
                "storeId": "shop_02",
                "name": "Phở Bò Tái Lăn Hà Nội",
                "description": "Thịt bò tái xào tỏi thơm lừng ăn kèm nước dùng đậm đà",
                "price": 60000,
                "basePrice": 60000,
                "imageUrl": "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=400",
                "category": "Phở",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 940,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_02_03",
                "storeId": "shop_02",
                "name": "Phở Gà Ta Thảo Mộc",
                "description": "Gà ta thả vườn da giòn thịt ngọt, nước phở thanh ngọt tự nhiên",
                "price": 50000,
                "basePrice": 50000,
                "imageUrl": "https://images.unsplash.com/photo-1594998893017-36147cbcae05?w=400",
                "category": "Phở",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 730,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_02_04",
                "storeId": "shop_02",
                "name": "Quẩy Giòn Hà Nội (Dĩa 3 cái)",
                "description": "Quẩy rán vàng ươm giòn rụm chuẩn vị thủ đô ăn kèm phở",
                "price": 10000,
                "basePrice": 10000,
                "imageUrl": "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=400",
                "category": "Món thêm",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1200,
                "ratingAvg": 4.9
            },
            {
                "id": "dish_02_05",
                "storeId": "shop_02",
                "name": "Trà Đá Hoa Nhài",
                "description": "Trà xanh ướp hoa nhài thanh khiết mát lạnh",
                "price": 5000,
                "basePrice": 5000,
                "imageUrl": "https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=400",
                "category": "Nước uống",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 2100,
                "ratingAvg": 4.9
            }
        ]
        """.trimIndent()
    }

    // Quán 3: Bún Bò Huế Cô Ba
    private fun getBunBoMenuJson(): String {
        return """
        [
            {
                "id": "dish_03_01",
                "storeId": "shop_03",
                "name": "Bún Bò Huế Chả Cua",
                "description": "Sợi bún to, nước dùng sả ruốc đậm đà kèm chả cua thơm béo",
                "price": 50000,
                "basePrice": 50000,
                "imageUrl": "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=400",
                "category": "Bún",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1320,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_03_02",
                "storeId": "shop_03",
                "name": "Bún Bò Giò Heo Nạc Sa Tế",
                "description": "Khoanh giò nạc mềm béo ngậy kèm nước dùng sa tế cay nồng",
                "price": 55000,
                "basePrice": 55000,
                "imageUrl": "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400",
                "category": "Bún",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 980,
                "ratingAvg": 4.7
            },
            {
                "id": "dish_03_03",
                "storeId": "shop_03",
                "name": "Nước Mía Tươi Tắc Ép Lạnh",
                "description": "Nước mía tươi ép cùng quả tắc chua thơm giải nhiệt",
                "price": 15000,
                "basePrice": 15000,
                "imageUrl": "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=400",
                "category": "Nước uống",
                "isAvailable": true,
                "hasOptions": true,
                "soldCount": 890,
                "ratingAvg": 4.9,
                "specs": ["Ly Lớn", "Ít ngọt", "Nhiều đá"]
            }
        ]
        """.trimIndent()
    }

    // Quán 4: Bánh Mì Huỳnh Hoa
    private fun getBanhMiMenuJson(): String {
        return """
        [
            {
                "id": "dish_04_01",
                "storeId": "shop_04",
                "name": "Bánh Mì Pate Chả Lụa Đặc Biệt",
                "description": "Ổ bánh mì nóng giòn ngập tràn pate béo ngậy, giò thủ, chả lụa",
                "price": 45000,
                "basePrice": 45000,
                "imageUrl": "https://images.unsplash.com/photo-1509722747041-616f39b57569?w=400",
                "category": "Bánh mì",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 3100,
                "ratingAvg": 5.0
            },
            {
                "id": "dish_04_02",
                "storeId": "shop_04",
                "name": "Bánh Mì Thịt Nướng Sa Tế",
                "description": "Thịt xiên nướng than hoa mềm mọng sốt tương ngọt sa tế cay",
                "price": 35000,
                "basePrice": 35000,
                "imageUrl": "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400",
                "category": "Bánh mì",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1450,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_04_03",
                "storeId": "shop_04",
                "name": "Sữa Đậu Nành Tươi Lá Dứa",
                "description": "Sữa đậu nành nấu tươi mỗi sáng thơm lừng hương lá dứa",
                "price": 15000,
                "basePrice": 15000,
                "imageUrl": "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=400",
                "category": "Nước uống",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 980,
                "ratingAvg": 4.9
            }
        ]
        """.trimIndent()
    }

    // Quán 5: Trà Sữa Oishi Cha
    private fun getTraSuaMenuJson(): String {
        return """
        [
            {
                "id": "dish_05_01",
                "storeId": "shop_05",
                "name": "Trà Sữa Trân Châu Hoàng Kim",
                "description": "Vị trà sữa truyền thống đậm đà quyện cùng trân châu hoàng kim",
                "price": 32000,
                "basePrice": 32000,
                "imageUrl": "https://images.unsplash.com/photo-1558857563-b371033873b8?w=400",
                "category": "Trà sữa",
                "isAvailable": true,
                "hasOptions": true,
                "soldCount": 2400,
                "ratingAvg": 4.9,
                "specs": ["Size L (+6k)", "70% Đường", "70% Đá", "Trân châu đen"]
            },
            {
                "id": "dish_05_02",
                "storeId": "shop_05",
                "name": "Trà Đào Cam Sả Tươi",
                "description": "Trà đào thơm nức hương sả tươi và lát cam vàng mọng nước",
                "price": 35000,
                "basePrice": 35000,
                "imageUrl": "https://images.unsplash.com/photo-1556881286-fc6915169721?w=400",
                "category": "Trà trái cây",
                "isAvailable": true,
                "hasOptions": true,
                "soldCount": 1890,
                "ratingAvg": 4.8,
                "specs": ["Size L (+6k)", "Vị thanh", "Nhiều đá", "Miếng đào giòn"]
            },
            {
                "id": "dish_05_03",
                "storeId": "shop_05",
                "name": "Bánh Tráng Cuộn Sốt Bơ",
                "description": "Bánh tráng dẻo cuộn ruốc hành phi chấm sốt bơ béo ngậy",
                "price": 20000,
                "basePrice": 20000,
                "imageUrl": "https://images.unsplash.com/photo-1562967916-eb82221dfb92?w=400",
                "category": "Ăn vặt",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1100,
                "ratingAvg": 4.7
            }
        ]
        """.trimIndent()
    }

    // Quán 6: Gà Rán Giòn Cay Hot Crispy
    private fun getGaRanMenuJson(): String {
        return """
        [
            {
                "id": "dish_06_01",
                "storeId": "shop_06",
                "name": "Combo 2 Miếng Gà Rán Giòn Cay + Khoai",
                "description": "2 miếng gà rán giòn rụm ướp sốt cay kèm khoai tây chiên giòn",
                "price": 69000,
                "basePrice": 69000,
                "imageUrl": "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=400",
                "category": "Combo",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 1750,
                "ratingAvg": 4.9
            },
            {
                "id": "dish_06_02",
                "storeId": "shop_06",
                "name": "Burger Gà Tôm Phô Mai Lõi Chảy",
                "description": "Bánh burger kẹp phi lê gà chiên xù, tôm tươi và sốt phô mai chảy",
                "price": 45000,
                "basePrice": 45000,
                "imageUrl": "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=400",
                "category": "Burger",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 890,
                "ratingAvg": 4.8
            },
            {
                "id": "dish_06_03",
                "storeId": "shop_06",
                "name": "Pepsi Lon Ướp Lạnh 330ml",
                "description": "Pepsi giải khát lạnh sảng khoái",
                "price": 15000,
                "basePrice": 15000,
                "imageUrl": "https://images.unsplash.com/photo-1629203851122-3726ecdf080e?w=400",
                "category": "Nước uống",
                "isAvailable": true,
                "hasOptions": false,
                "soldCount": 2800,
                "ratingAvg": 4.9
            }
        ]
        """.trimIndent()
    }
}
