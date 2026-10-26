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
}
