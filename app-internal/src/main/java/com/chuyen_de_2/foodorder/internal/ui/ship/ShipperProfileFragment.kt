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

/**
 * Tên chức năng: Quản Lý Hồ Sơ Tài Xế & Cấu Hình Định Vị GPS Nền (Shipper Profile & Background Location Service)
 * Cách hoạt động:
 *  - Hiển thị thông tin cá nhân của tài xế (Tên, Email, Trạng thái hoạt động).
 *  - Cung cấp công tắc bật/tắt dịch vụ chạy nền [LocationForegroundService] để gửi tọa độ GPS định kỳ lên Firebase cho khách hàng theo dõi.
 *  - Cung cấp số hotline hỗ trợ khẩn cấp liên hệ tổng đài / quản lý.
 *  - Cho phép tài xế đăng xuất an toàn và dừng GPS nền.
 * Luồng hướng tới:
 *  - Bật GPS -> Khởi động [LocationForegroundService] cập nhật vị trí lên Realtime Database.
 *  - Đăng xuất -> Chuyển hướng về [LoginActivity].
 */
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

    /**
     * Tên chức năng: Khởi tạo View hồ sơ tài xế
     * Cách hoạt động: Cập nhật thông tin tài xế và gắn sự kiện công tắc GPS, nút gọi tổng đài, nút đăng xuất.
     * Luồng hướng tới: Hiển thị giao diện cài đặt tài xế.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    /**
     * Tên chức năng: Hiển thị thông tin định danh tài xế
     * Cách hoạt động: Lấy thông tin từ FirebaseAuth (displayName, email) và bind vào giao diện.
     * Luồng hướng tới: Hiển thị tên và email tài xế.
     */
    private fun setupUI() {
        val user = auth.currentUser
        val name = user?.displayName?.ifBlank { user.email?.substringBefore("@") ?: "Tài xế" } ?: "Tài xế"
        val email = user?.email ?: ""
        binding.tvProfileName.text = name
        binding.tvProfileSub.text = email
    }

    /**
     * Tên chức năng: Thiết lập các sự kiện tương tác
     * Cách hoạt động: Bắt sự kiện bật GPS nền, bấm số gọi tổng đài điều phối, và xác nhận đăng xuất.
     * Luồng hướng tới: Điều phối tương ứng.
     */
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
                data = Uri.parse("tel:19001234")
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

    /**
     * Tên chức năng: Kiểm tra và yêu cầu cấp quyền truy cập vị trí chính xác (GPS Permission)
     * Cách hoạt động: Kiểm tra ACCESS_FINE_LOCATION; nếu chưa có thì xin quyền người dùng qua ActivityResultLauncher.
     * Luồng hướng tới: Cấp quyền thành công -> [startLocationService].
     */
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

    /**
     * Tên chức năng: Khởi động Foreground Service theo dõi GPS
     * Cách hoạt động: Chạy [LocationForegroundService] với Notification chạy ngầm để đảm bảo hệ điều hành không tiêu diệt tiến trình khi tắt màn hình.
     * Luồng hướng tới: Cập nhật vị trí GPS liên tục cho khách hàng và hệ thống.
     */
    private fun startLocationService() {
        val intent = Intent(requireContext(), LocationForegroundService::class.java).apply {
            putExtra(LocationForegroundService.EXTRA_ORDER_ID, "DRIVER_ACTIVE_DUTY")
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        binding.tvProfileGpsStatus.text = "🟢 GPS đang hoạt động định vị nền"
    }

    /**
     * Tên chức năng: Dừng Foreground Service theo dõi GPS
     * Cách hoạt động: Gửi tín hiệu stopService giải phóng cảm biến GPS giúp tiết kiệm pin khi tài xế nghỉ ca.
     * Luồng hướng tới: Hủy Foreground Service.
     */
    private fun stopLocationService() {
        requireContext().stopService(Intent(requireContext(), LocationForegroundService::class.java))
        binding.tvProfileGpsStatus.text = "GPS đã tắt"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
