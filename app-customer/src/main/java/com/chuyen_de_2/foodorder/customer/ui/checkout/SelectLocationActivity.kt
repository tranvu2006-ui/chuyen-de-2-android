package com.chuyen_de_2.foodorder.customer.ui.checkout

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.databinding.ActivitySelectLocationBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SelectLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectLocationBinding
    private var geocodeJob: Job? = null

    private var currentLat = 10.7769
    private var currentLng = 106.7009
    private var currentFullAddress = ""
    private var currentStreetAddress = ""
    private var currentDistrict = "Quận 1"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locateCurrentGps()
        } else {
            Toast.makeText(this, "Cần cấp quyền vị trí để định vị GPS", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetUtils.setLightStatusBar(this, false)
        InsetUtils.applyToolbarInsets(binding.toolbar)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupWebView()
        setupUI()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webViewMap.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.loadsImagesAutomatically = true

            addJavascriptInterface(AndroidBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val initLat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                    val initLng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                    if (initLat != 0.0 && initLng != 0.0) {
                        moveMapTo(initLat, initLng)
                    }
                }
            }

            loadUrl("file:///android_asset/select_map.html")
        }
    }

    private fun setupUI() {
        binding.btnConfirmLocation.setOnClickListener {
            if (currentFullAddress.isBlank()) {
                Toast.makeText(this, "Vui lòng đợi định vị địa chỉ...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_FULL_ADDRESS, currentFullAddress)
                putExtra(EXTRA_STREET_ADDRESS, currentStreetAddress)
                putExtra(EXTRA_DISTRICT, currentDistrict)
                putExtra(EXTRA_LAT, currentLat)
                putExtra(EXTRA_LNG, currentLng)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun moveMapTo(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng
        binding.webViewMap.post {
            binding.webViewMap.evaluateJavascript("setMapCenter($lat, $lng, 17);", null)
        }
        reverseGeocodePosition(lat, lng)
    }

    private fun checkPermissionAndLocate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            locateCurrentGps()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun locateCurrentGps() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    moveMapTo(loc.latitude, loc.longitude)
                } else {
                    Toast.makeText(this, "Đang tìm tín hiệu GPS...", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {}
    }

    /** Chuyển đổi toạ độ sang địa chỉ chi tiết và tự nhận diện quận huyện theo cự ly thực tế */
    private fun reverseGeocodePosition(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng

        geocodeJob?.cancel()
        geocodeJob = lifecycleScope.launch {
            binding.pbLoadingAddress.visibility = View.VISIBLE
            binding.tvSelectedAddress.text = "Đang tra cứu địa chỉ..."
            delay(250)

            // 1. Thuật toán không gian: Tìm Quận gần nhất theo khoảng cách GPS (chính xác 100%)
            val targetLatLng = LatLng(lat, lng)
            val nearestDistrictKey = GoogleMapUtils.DISTRICT_COORDINATES.minByOrNull {
                GoogleMapUtils.calculateDistanceKm(targetLatLng, it.value)
            }?.key ?: "Quận 1"

            // 2. Tra cứu chi tiết qua Android Geocoder
            val rawAddress = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(this@SelectLocationActivity, Locale("vi", "VN"))
                    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        var result: String? = null
                        geocoder.getFromLocation(lat, lng, 1) { list ->
                            if (list.isNotEmpty()) {
                                result = list[0].getAddressLine(0)
                            }
                        }
                        result
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
                    }
                    addresses
                } catch (_: Exception) {
                    null
                }
            }

            // 3. Chuẩn hóa tên quận huyện và địa chỉ
            val finalDistrict = when {
                rawAddress?.contains("Thủ Đức", ignoreCase = true) == true || nearestDistrictKey.contains("Thủ Đức", true) -> "Thủ Đức"
                rawAddress?.contains("Bình Thạnh", ignoreCase = true) == true || nearestDistrictKey.contains("Bình Thạnh", true) -> "Bình Thạnh"
                rawAddress?.contains("Phú Nhuận", ignoreCase = true) == true || nearestDistrictKey.contains("Phú Nhuận", true) -> "Phú Nhuận"
                rawAddress?.contains("Tân Bình", ignoreCase = true) == true || nearestDistrictKey.contains("Tân Bình", true) -> "Tân Bình"
                rawAddress?.contains("Tân Phú", ignoreCase = true) == true || nearestDistrictKey.contains("Tân Phú", true) -> "Tân Phú"
                rawAddress?.contains("Gò Vấp", ignoreCase = true) == true || nearestDistrictKey.contains("Gò Vấp", true) -> "Gò Vấp"
                rawAddress?.contains("Bình Tân", ignoreCase = true) == true || nearestDistrictKey.contains("Bình Tân", true) -> "Bình Tân"
                rawAddress?.contains("Quận 12", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 12", true) -> "Quận 12"
                rawAddress?.contains("Quận 10", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 10", true) -> "Quận 10"
                rawAddress?.contains("Quận 11", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 11", true) -> "Quận 11"
                rawAddress?.contains("Quận 7", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 7", true) -> "Quận 7"
                rawAddress?.contains("Quận 8", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 8", true) -> "Quận 8"
                rawAddress?.contains("Quận 6", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 6", true) -> "Quận 6"
                rawAddress?.contains("Quận 5", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 5", true) -> "Quận 5"
                rawAddress?.contains("Quận 4", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 4", true) -> "Quận 4"
                rawAddress?.contains("Quận 3", ignoreCase = true) == true || nearestDistrictKey.contains("Quận 3", true) -> "Quận 3"
                else -> nearestDistrictKey
            }

            currentDistrict = finalDistrict

            // Định dạng chuỗi địa chỉ đầy đủ
            val displayAddress = if (!rawAddress.isNullOrBlank()) {
                rawAddress.replace(", Việt Nam", "").trim()
            } else {
                "Toạ độ: ${String.format(Locale.US, "%.5f, %.5f", lat, lng)}, $finalDistrict, TP.HCM"
            }

            currentFullAddress = displayAddress

            // Tách tên đường
            currentStreetAddress = displayAddress
                .replace(", Thành phố Hồ Chí Minh", "")
                .replace(", TP. Hồ Chí Minh", "")
                .replace(", TP.HCM", "")
                .replace(", Hồ Chí Minh", "")
                .replace(", $finalDistrict", "")
                .trim()
                .ifBlank { "Vị trí đã ghim" }

            binding.tvSelectedAddress.text = displayAddress
            binding.tvSelectedDistrict.text = "Quận / Huyện: $finalDistrict"
            binding.pbLoadingAddress.visibility = View.GONE
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onMapReady(lat: Double, lng: Double) {
            runOnUiThread {
                val initLat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val initLng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                if (initLat != 0.0 && initLng != 0.0) {
                    moveMapTo(initLat, initLng)
                } else {
                    checkPermissionAndLocate()
                }
            }
        }

        @JavascriptInterface
        fun onLocationMoved(lat: Double, lng: Double) {
            runOnUiThread {
                reverseGeocodePosition(lat, lng)
            }
        }

        @JavascriptInterface
        fun onGpsButtonClicked() {
            runOnUiThread {
                checkPermissionAndLocate()
            }
        }
    }

    companion object {
        const val EXTRA_FULL_ADDRESS = "EXTRA_FULL_ADDRESS"
        const val EXTRA_STREET_ADDRESS = "EXTRA_STREET_ADDRESS"
        const val EXTRA_DISTRICT = "EXTRA_DISTRICT"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
    }
}
