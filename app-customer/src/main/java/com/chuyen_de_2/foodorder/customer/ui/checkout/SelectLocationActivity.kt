package com.chuyen_de_2.foodorder.customer.ui.checkout

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.util.GoogleMapUtils
import com.chuyen_de_2.foodorder.core.util.InsetUtils
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.ActivitySelectLocationBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivitySelectLocationBinding
    private var googleMap: GoogleMap? = null
    private var geocodeJob: Job? = null

    private var currentLatLng = GoogleMapUtils.DEFAULT_HCMC_LATLNG
    private var currentFullAddress = ""
    private var currentStreetAddress = ""
    private var currentDistrict = "Quận 1"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            moveToCurrentGpsLocation()
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

        // Lấy toạ độ khởi đầu truyền vào (nếu có)
        val initialLat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val initialLng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        if (initialLat != 0.0 && initialLng != 0.0) {
            currentLatLng = LatLng(initialLat, initialLng)
        }

        setupMap()
        setupUI()
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }

        // Di chuyển camera đến toạ độ ban đầu
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))

        // Lắng nghe khi người dùng dừng kéo bản đồ
        map.setOnCameraIdleListener {
            val centerLatLng = map.cameraPosition.target
            currentLatLng = centerLatLng
            reverseGeocodePosition(centerLatLng)
        }

        // Nếu chưa có toạ độ cụ thể, tự động định vị GPS hiện tại
        if (intent.getDoubleExtra(EXTRA_LAT, 0.0) == 0.0) {
            checkPermissionAndLocate()
        } else {
            reverseGeocodePosition(currentLatLng)
        }
    }

    private fun setupUI() {
        binding.fabCurrentLocation.setOnClickListener {
            checkPermissionAndLocate()
        }

        binding.btnConfirmLocation.setOnClickListener {
            if (currentFullAddress.isBlank()) {
                Toast.makeText(this, "Vui lòng đợi định vị địa chỉ...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_FULL_ADDRESS, currentFullAddress)
                putExtra(EXTRA_STREET_ADDRESS, currentStreetAddress)
                putExtra(EXTRA_DISTRICT, currentDistrict)
                putExtra(EXTRA_LAT, currentLatLng.latitude)
                putExtra(EXTRA_LNG, currentLatLng.longitude)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun checkPermissionAndLocate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            moveToCurrentGpsLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun moveToCurrentGpsLocation() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    currentLatLng = latLng
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    reverseGeocodePosition(latLng)
                } else {
                    Toast.makeText(this, "Đang tìm tín hiệu GPS...", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {}
    }

    /** Chuyển đổi toạ độ tâm bản đồ sang địa chỉ chi tiết */
    private fun reverseGeocodePosition(latLng: LatLng) {
        geocodeJob?.cancel()
        geocodeJob = lifecycleScope.launch {
            binding.pbLoadingAddress.visibility = View.VISIBLE
            binding.tvSelectedAddress.text = "Đang tra cứu địa chỉ..."
            delay(300) // Debounce nhẹ

            val fullAddr = GoogleMapUtils.reverseGeocode(
                this@SelectLocationActivity,
                latLng.latitude,
                latLng.longitude
            )

            currentFullAddress = fullAddr
            val parsedDistrict = extractDistrictFromAddress(fullAddr)
            currentDistrict = parsedDistrict

            // Tách phần số nhà, tên đường
            currentStreetAddress = fullAddr
                .replace(", TP. Hồ Chí Minh", "")
                .replace(", TP.HCM", "")
                .replace(", $parsedDistrict", "")
                .trim()

            binding.tvSelectedAddress.text = fullAddr
            binding.tvSelectedDistrict.text = "Quận / Huyện: $currentDistrict"
            binding.pbLoadingAddress.visibility = View.GONE
        }
    }

    /** Trích xuất tên quận/huyện từ chuỗi địa chỉ */
    private fun extractDistrictFromAddress(addr: String): String {
        val knownDistricts = listOf(
            "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6",
            "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
            "Bình Thạnh", "Phú Nhuận", "Tân Bình", "Tân Phú",
            "Gò Vấp", "Bình Tân", "Thủ Đức", "Bình Chánh",
            "Hóc Môn", "Nhà Bè", "Củ Chi", "Cần Giờ"
        )
        for (district in knownDistricts) {
            if (addr.contains(district, ignoreCase = true)) {
                return district
            }
        }
        return "Quận 1"
    }

    companion object {
        const val EXTRA_FULL_ADDRESS = "EXTRA_FULL_ADDRESS"
        const val EXTRA_STREET_ADDRESS = "EXTRA_STREET_ADDRESS"
        const val EXTRA_DISTRICT = "EXTRA_DISTRICT"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
    }
}
