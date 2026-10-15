package com.chuyen_de_2.foodorder.internal.ui.ship

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.internal.databinding.FragmentShipperProfileBinding
import com.chuyen_de_2.foodorder.internal.service.LocationForegroundService
import com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ShipperProfileFragment : Fragment() {

    private var _binding: FragmentShipperProfileBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocation) {
            startLocationService()
        } else {
            binding.switchProfileGps.isChecked = false
            Toast.makeText(requireContext(), "Cần quyền vị trí để kích hoạt GPS nền", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShipperProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val user = auth.currentUser
        val name = user?.displayName?.ifBlank { user.email?.substringBefore("@") ?: "Tài xế" } ?: "Tài xế"
        val email = user?.email ?: ""
        binding.tvProfileName.text = name
        binding.tvProfileSub.text = email
    }

    private fun setupListeners() {
        binding.switchProfileGps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkLocationPermission()
            } else {
                stopLocationService()
            }
        }

        binding.layoutCallManager.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:0912345678")
            }
            startActivity(intent)
        }

        binding.btnProfileLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("🚪 Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất ca trực tài xế?")
                .setPositiveButton("Đăng xuất") { _, _ ->
                    stopLocationService()
                    authRepository.logout()
                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("LOGOUT", true)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationService()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationService() {
        val intent = Intent(requireContext(), LocationForegroundService::class.java).apply {
            putExtra(LocationForegroundService.EXTRA_ORDER_ID, "DRIVER_ACTIVE_DUTY")
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        binding.tvProfileGpsStatus.text = "🟢 GPS đang hoạt động định vị nền"
    }

    private fun stopLocationService() {
        requireContext().stopService(Intent(requireContext(), LocationForegroundService::class.java))
        binding.tvProfileGpsStatus.text = "GPS đã tắt"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
