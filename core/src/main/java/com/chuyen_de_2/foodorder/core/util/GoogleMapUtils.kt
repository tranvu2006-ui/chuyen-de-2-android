package com.chuyen_de_2.foodorder.core.util

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Tiện ích Google Maps & Geocoding cho toàn bộ dự án Oishi Food
 * - Geocoding: Chuyển đổi tên đường / địa chỉ sang tọa độ LatLng
 * - Reverse Geocoding: Chuyển tọa độ GPS sang địa chỉ người dùng
 * - Tính khoảng cách km thực tế và tính cước phí giao hàng động
 * - Mở Google Maps điều hướng dẫn đường cho Shipper & Khách hàng
 */
object GoogleMapUtils {

    // Tọa độ trung tâm mặc định TP.HCM (Chợ Bến Thành, Q.1)
    val DEFAULT_HCMC_LATLNG = LatLng(10.7725, 106.6980)

    // Bảng tọa độ các địa điểm / quận huyện TP.HCM chuẩn
    val DISTRICT_COORDINATES = mapOf(
        "Quận 1" to LatLng(10.7769, 106.7009),
        "Quận 3" to LatLng(10.7844, 106.6843),
        "Quận 4" to LatLng(10.7630, 106.7058),
        "Quận 5" to LatLng(10.7554, 106.6669),
        "Quận 6" to LatLng(10.7471, 106.6353),
        "Quận 7" to LatLng(10.7340, 106.7218),
        "Quận 8" to LatLng(10.7226, 106.6284),
        "Quận 10" to LatLng(10.7674, 106.6667),
        "Quận 11" to LatLng(10.7651, 106.6508),
        "Quận 12" to LatLng(10.8672, 106.6413),
        "Bình Thạnh" to LatLng(10.8039, 106.6974),
        "Phú Nhuận" to LatLng(10.7992, 106.6803),
        "Tân Bình" to LatLng(10.8015, 106.6545),
        "Tân Phú" to LatLng(10.7904, 106.6282),
        "Gò Vấp" to LatLng(10.8388, 106.6658),
        "Bình Tân" to LatLng(10.7654, 106.6039),
        "Thủ Đức" to LatLng(10.8494, 106.7537),
        "Bình Chánh" to LatLng(10.6874, 106.5938),
        "Hóc Môn" to LatLng(10.8842, 106.5913),
        "Nhà Bè" to LatLng(10.6953, 106.7274)
    )

    /**
     * Tìm tên Quận / Huyện TP.HCM gần toạ độ GPS nhất dựa trên bảng toạ độ chuẩn
     */
    fun findNearestDistrict(lat: Double, lng: Double): String {
        val target = LatLng(lat, lng)
        return DISTRICT_COORDINATES.minByOrNull {
            calculateDistanceKm(target, it.value)
        }?.key ?: "TP.HCM"
    }
    /**
     * Chuyển địa chỉ chữ sang LatLng bằng Google Geocoder
     */
    suspend fun geocodeAddress(context: Context, addressText: String): LatLng = withContext(Dispatchers.IO) {
        if (addressText.isBlank()) return@withContext DEFAULT_HCMC_LATLNG

        try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine { cont ->
                    geocoder.getFromLocationName(addressText, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val addr = addresses[0]
                            cont.resume(LatLng(addr.latitude, addr.longitude))
                        } else {
                            cont.resume(findFallbackDistrict(addressText))
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(addressText, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    LatLng(addr.latitude, addr.longitude)
                } else {
                    findFallbackDistrict(addressText)
                }
            }
        } catch (e: Exception) {
            findFallbackDistrict(addressText)
        }
    }

    /**
     * Chuyển toạ độ GPS sang tên địa chỉ người dùng (Reverse Geocoding)
     */
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        cont.resume(formatAddress(addresses))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                formatAddress(addresses)
            }
        } catch (e: Exception) {
            "TP. Hồ Chí Minh"
        }
    }

    private fun formatAddress(addresses: List<Address>?): String {
        if (addresses.isNullOrEmpty()) return "TP. Hồ Chí Minh"
        val addr = addresses[0]
        val feature = addr.thoroughfare ?: addr.featureName
        val subAdmin = addr.subAdminArea ?: addr.subLocality ?: addr.locality
        val admin = addr.adminArea ?: "TP.HCM"

        val parts = mutableListOf<String>()
        if (!feature.isNullOrBlank()) parts.add(feature)
        if (!subAdmin.isNullOrBlank()) parts.add(subAdmin)
        if (!admin.isNullOrBlank()) parts.add(admin)

        return if (parts.isNotEmpty()) parts.joinToString(", ") else addr.getAddressLine(0) ?: "TP. Hồ Chí Minh"
    }

    /**
     * Tìm toạ độ gần đúng theo tên Quận / Huyện nếu Geocoder không tìm thấy
     */
    fun findFallbackDistrict(addressText: String): LatLng {
        for ((district, latLng) in DISTRICT_COORDINATES) {
            if (addressText.contains(district, ignoreCase = true)) {
                return latLng
            }
        }
        return DEFAULT_HCMC_LATLNG
    }

    /**
     * Tính khoảng cách đường chim bay / thực tế theo Km
     */
    fun calculateDistanceKm(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0] / 1000f
    }

    /**
     * Tính cước phí giao hàng động theo khoảng cách Km thực tế
     * - Dưới hoặc bằng 3km: 15.000 đ
     * - Mỗi km tiếp theo: +5.000 đ/km
     */
    fun calculateShippingFeeByDistance(distanceKm: Float): Long {
        if (distanceKm <= 3f) return 15000L
        val extraKm = (distanceKm - 3f).coerceAtLeast(0f)
        val extraFee = Math.ceil(extraKm.toDouble()).toLong() * 5000L
        return 15000L + extraFee
    }

    /**
     * Mở ứng dụng Google Maps điều hướng dẫn đường
     */
    fun openGoogleMapsNavigation(context: Context, destLat: Double, destLng: Double, destinationName: String = "Điểm giao") {
        try {
            // Intent điều hướng Google Maps trực tiếp
            val gmmIntentUri = Uri.parse("google.navigation:q=$destLat,$destLng&mode=d")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                // Fallback mở web browser Google Maps Directions
                val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destLat,$destLng")
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở Google Maps: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mở Google Maps tìm kiếm địa chỉ
     */
    fun openGoogleMapsSearch(context: Context, address: String) {
        try {
            val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address))
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở bản đồ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Tạo URL gọi API Google Maps Static Maps để tải trực tiếp ảnh bản đồ
     * Bao gồm Marker Tài xế (Đỏ), Marker Quán (Xanh) và đường nối Polyline (Xanh dương)
     */
    fun getGoogleStaticMapUrl(
        driverLat: Double,
        driverLng: Double,
        destLat: Double,
        destLng: Double,
        width: Int = 640,
        height: Int = 400,
        apiKey: String = ""
    ): String {
        val baseUrl = "https://maps.googleapis.com/maps/api/staticmap"
        val driverMarker = "color:red%7Clabel:D%7C$driverLat,$driverLng"
        val destMarker = "color:blue%7Clabel:S%7C$destLat,$destLng"
        val path = "color:0x1a73e8ff%7Cweight:5%7C$driverLat,$driverLng%7C$destLat,$destLng"
        val keyParam = if (apiKey.isNotBlank()) "&key=$apiKey" else ""
        return "$baseUrl?size=${width}x${height}&scale=2&maptype=roadmap&markers=$driverMarker&markers=$destMarker&path=$path$keyParam"
    }

    /**
     * Tạo URL lấy ảnh bản đồ tĩnh OpenStreetMap (Miễn phí 100%, không cần API Key)
     */
    fun getFreeStaticMapUrl(
        driverLat: Double,
        driverLng: Double,
        destLat: Double,
        destLng: Double,
        width: Int = 640,
        height: Int = 400
    ): String {
        val midLat = (driverLat + destLat) / 2
        val midLng = (driverLng + destLng) / 2
        return "https://staticmap.openstreetmap.de/staticmap.php?center=$midLat,$midLng&zoom=15&size=${width}x${height}&markers=$driverLat,$driverLng,ol-marker%7C$destLat,$destLng,ol-marker"
    }

    /**
     * Chuẩn hoá chuỗi tiếng Việt bỏ dấu để tìm kiếm không phân biệt dấu và hoa thường
     */
    fun removeAccent(s: String): String {
        val temp = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("").replace("đ", "d").replace("Đ", "D").lowercase().trim()
    }

    /**
     * Danh mục các tuyến đường và địa điểm nổi tiếng phổ biến tại TP.HCM
     * Giúp trả kết quả gợi ý TỨC THÌ (0ms) khi người dùng gõ bất kỳ ký tự nào
     */
    val POPULAR_LOCATIONS = listOf(
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Sư Vạn Hạnh", "Phường 12, Quận 10, TP.HCM", "Đường Sư Vạn Hạnh, Quận 10, TP. Hồ Chí Minh", "Quận 10", 10.7719, 106.6698),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Song Hành", "Phường An Phú, TP. Thủ Đức", "Đường Song Hành, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8035, 106.7554),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Chợ Bến Thành (Sài Gòn)", "Đường Lê Lợi, Bến Thành, Quận 1", "Chợ Bến Thành, Quận 1, TP. Hồ Chí Minh", "Quận 1", 10.7725, 106.6980),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Saigon Centre / Takashimaya", "65 Lê Lợi, Bến Nghé, Quận 1", "65 Lê Lợi, Bến Nghé, Quận 1, TP. Hồ Chí Minh", "Quận 1", 10.7735, 106.7006),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Võ Văn Ngân", "Phường Linh Chiểu, TP. Thủ Đức", "Đường Võ Văn Ngân, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8512, 106.7720),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Kha Vạn Cân", "Phường Linh Đông, TP. Thủ Đức", "Đường Kha Vạn Cân, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8496, 106.7562),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Quang Trung", "Phường 10, Quận Gò Vấp", "Đường Quang Trung, Quận Gò Vấp, TP. Hồ Chí Minh", "Gò Vấp", 10.8355, 106.6662),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Phan Xích Long", "Phường 2, Quận Phú Nhuận", "Đường Phan Xích Long, Quận Phú Nhuận, TP. Hồ Chí Minh", "Phú Nhuận", 10.7986, 106.6872),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Điện Biên Phủ", "Phường 15, Quận Bình Thạnh", "Đường Điện Biên Phủ, Quận Bình Thạnh, TP. Hồ Chí Minh", "Bình Thạnh", 10.8016, 106.7088),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Nguyễn Thị Minh Khai", "Phường Bến Nghé, Quận 1", "Đường Nguyễn Thị Minh Khai, Quận 1, TP. Hồ Chí Minh", "Quận 1", 10.7794, 106.6953),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Cách Mạng Tháng 8", "Phường 5, Quận Tân Bình", "Đường Cách Mạng Tháng 8, Tân Bình, TP. Hồ Chí Minh", "Tân Bình", 10.7885, 106.6622),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Cộng Hòa", "Phường 13, Quận Tân Bình", "Đường Cộng Hòa, Quận Tân Bình, TP. Hồ Chí Minh", "Tân Bình", 10.8038, 106.6433),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Nguyễn Tri Phương", "Phường 8, Quận 10", "Đường Nguyễn Tri Phương, Quận 10, TP. Hồ Chí Minh", "Quận 10", 10.7634, 106.6675),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Nguyễn Trãi", "Phường 3, Quận 5", "Đường Nguyễn Trãi, Quận 5, TP. Hồ Chí Minh", "Quận 5", 10.7540, 106.6670),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Nguyễn Văn Cừ", "Phường 4, Quận 5", "Đường Nguyễn Văn Cừ, Quận 5, TP. Hồ Chí Minh", "Quận 5", 10.7578, 106.6828),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Nguyễn Hữu Thọ", "Phường Tân Phong, Quận 7", "Đường Nguyễn Hữu Thọ, Quận 7, TP. Hồ Chí Minh", "Quận 7", 10.7328, 106.7022),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Huỳnh Tấn Phát", "Phường Phú Thuận, Quận 7", "Đường Huỳnh Tấn Phát, Quận 7, TP. Hồ Chí Minh", "Quận 7", 10.7351, 106.7329),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Phạm Văn Đồng", "Phường 1, Quận Gò Vấp", "Đường Phạm Văn Đồng, Gò Vấp, TP. Hồ Chí Minh", "Gò Vấp", 10.8242, 106.6885),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Trường Chinh", "Phường Tây Thạnh, Quận Tân Phú", "Đường Trường Chinh, Tân Phú, TP. Hồ Chí Minh", "Tân Phú", 10.8122, 106.6294),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Tô Ngọc Vân", "Phường Linh Tây, TP. Thủ Đức", "Đường Tô Ngọc Vân, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8624, 106.7525),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Đặng Văn Bi", "Phường Trường Thọ, TP. Thủ Đức", "Đường Đặng Văn Bi, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8415, 106.7667),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Hoàng Diệu 2", "Phường Linh Trung, TP. Thủ Đức", "Đường Hoàng Diệu 2, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8564, 106.7712),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Đường Lê Văn Việt", "Phường Tăng Nhơn Phú A, TP. Thủ Đức", "Đường Lê Văn Việt, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8480, 106.7865),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Landmark 81 (Vinhomes Central Park)", "720A Điện Biên Phủ, Phường 22, Bình Thạnh", "Landmark 81, Bình Thạnh, TP. Hồ Chí Minh", "Bình Thạnh", 10.7950, 106.7218),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Chợ Thủ Đức", "Võ Văn Ngân, Phường Linh Tây, TP. Thủ Đức", "Chợ Thủ Đức, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8504, 106.7587),
        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion("Làng Đại học Quốc gia TP.HCM", "Linh Trung, TP. Thủ Đức", "Làng Đại học Quốc gia, TP. Thủ Đức, TP. Hồ Chí Minh", "Thủ Đức", 10.8753, 106.8007)
    )

    /**
     * Gọi Photon OpenStreetMap REST API tìm kiếm địa chỉ chính xác không cần Google Play Services
     */
    private fun fetchPhotonSuggestions(query: String): List<com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion> {
        val results = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion>()
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = java.net.URL("https://photon.komoot.io/api/?q=$encoded&lat=10.7769&lon=106.7009&limit=6")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.setRequestProperty("User-Agent", "FoodOrderApp/1.0")
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(responseText)
                val features = json.optJSONArray("features") ?: return results
                for (i in 0 until features.length()) {
                    val feat = features.getJSONObject(i)
                    val geom = feat.optJSONObject("geometry")
                    val coords = geom?.optJSONArray("coordinates")
                    val lng = coords?.optDouble(0) ?: 0.0
                    val lat = coords?.optDouble(1) ?: 0.0
                    if (lat == 0.0 || lng == 0.0) continue

                    val props = feat.optJSONObject("properties") ?: continue
                    val name = props.optString("name")
                    val street = props.optString("street")
                    val district = props.optString("district").ifBlank { props.optString("city") }
                    val city = props.optString("city", "TP. Hồ Chí Minh")

                    val title = if (name.isNotBlank()) name else street
                    if (title.isBlank()) continue

                    val subtitleParts = mutableListOf<String>()
                    if (street.isNotBlank() && street != title) subtitleParts.add(street)
                    if (district.isNotBlank()) subtitleParts.add(district)
                    if (city.isNotBlank()) subtitleParts.add(city)
                    val subtitle = subtitleParts.joinToString(", ")
                    val fullAddress = if (subtitle.isNotBlank()) "$title, $subtitle" else title

                    val detectedDistrict = findNearestDistrict(lat, lng)
                    results.add(
                        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion(
                            title = title,
                            subtitle = if (subtitle.isNotBlank()) subtitle else "$detectedDistrict, TP.HCM",
                            fullAddress = fullAddress,
                            district = detectedDistrict,
                            latitude = lat,
                            longitude = lng
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return results
    }

    /**
     * Tìm kiếm gợi ý danh sách địa chỉ tự động dựa trên từ khóa người dùng nhập (Address Autocomplete)
     * Kết hợp 4 tầng tìm kiếm: Offline siêu tốc (0ms) -> Danh mục 20 Quận -> Photon API -> Android Geocoder
     */
    suspend fun searchAddressSuggestions(context: Context, query: String): List<com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext POPULAR_LOCATIONS.take(6)
        }

        val queryNormalized = removeAccent(trimmed)
        val results = mutableListOf<com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion>()

        // 1. Tầng 1: Lọc tức thì (0ms) từ danh mục địa điểm phổ biến & đường lớn TP.HCM
        POPULAR_LOCATIONS.forEach { loc ->
            val titleNorm = removeAccent(loc.title)
            val subNorm = removeAccent(loc.subtitle)
            val fullNorm = removeAccent(loc.fullAddress)
            if (titleNorm.contains(queryNormalized) || subNorm.contains(queryNormalized) || fullNorm.contains(queryNormalized)) {
                results.add(loc)
            }
        }

        // 2. Tầng 2: Khớp danh sách 20 Quận/Huyện TP.HCM
        DISTRICT_COORDINATES.forEach { (distName, latLng) ->
            val distNorm = removeAccent(distName)
            if (distNorm.contains(queryNormalized) || queryNormalized.contains(distNorm)) {
                if (results.none { removeAccent(it.district) == distNorm && it.title == distName }) {
                    results.add(
                        com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion(
                            title = distName,
                            subtitle = "Thành phố Hồ Chí Minh",
                            fullAddress = "$distName, TP. Hồ Chí Minh",
                            district = distName,
                            latitude = latLng.latitude,
                            longitude = latLng.longitude
                        )
                    )
                }
            }
        }

        // 3. Tầng 3: Gọi Photon OpenStreetMap REST API (miễn phí, độc lập, hỗ trợ mọi tên đường / số nhà VN)
        val photonResults = fetchPhotonSuggestions(trimmed)
        results.addAll(photonResults)

        // 4. Tầng 4: Gọi Geocoder nội tại của Android (nếu có Google Play Services)
        try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val searchQuery = if (trimmed.contains("TP", true) || trimmed.contains("Hồ Chí Minh", true) || trimmed.contains("HCM", true)) {
                trimmed
            } else {
                "$trimmed, TP. Hồ Chí Minh"
            }

            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine<List<Address>?> { cont ->
                    geocoder.getFromLocationName(searchQuery, 5) { list ->
                        cont.resume(list)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(searchQuery, 5)
            }

            addresses?.forEach { addr ->
                val feature = addr.thoroughfare ?: addr.featureName.orEmpty()
                val subAdmin = addr.subAdminArea ?: addr.subLocality ?: addr.locality.orEmpty()
                val admin = addr.adminArea ?: "TP.HCM"
                val full = addr.getAddressLine(0) ?: "$feature, $subAdmin, $admin"
                val cleanFull = full.replace(", Việt Nam", "").trim()

                val district = findNearestDistrict(addr.latitude, addr.longitude)
                val title = if (feature.isNotBlank()) feature else cleanFull.split(",").firstOrNull()?.trim() ?: cleanFull
                val subtitle = cleanFull.removePrefix(title).removePrefix(",").trim()

                results.add(
                    com.chuyen_de_2.foodorder.core.data.model.AddressSuggestion(
                        title = title,
                        subtitle = if (subtitle.isNotBlank()) subtitle else "$district, TP.HCM",
                        fullAddress = cleanFull,
                        district = district,
                        latitude = addr.latitude,
                        longitude = addr.longitude
                    )
                )
            }
        } catch (_: Exception) {}

        return@withContext results.distinctBy {
            "${String.format(Locale.US, "%.4f", it.latitude)},${String.format(Locale.US, "%.4f", it.longitude)}"
        }.take(8)
    }
}
