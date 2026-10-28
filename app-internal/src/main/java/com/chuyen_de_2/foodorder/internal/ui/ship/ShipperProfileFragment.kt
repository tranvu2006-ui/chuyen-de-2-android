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
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Driver
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentShipperProfileBinding
import com.chuyen_de_2.foodorder.internal.service.LocationForegroundService
import com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    private var currentDriver: Driver? = null

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
        val uid = user?.uid.orEmpty()
        val defaultName = user?.displayName?.ifBlank { user.email?.substringBefore("@") ?: "Tài xế" } ?: "Tài xế"
        val email = user?.email ?: ""
        val phone = user?.phoneNumber.orEmpty()

        binding.tvProfileName.text = defaultName
        binding.tvProfileSub.text = if (phone.isNotBlank()) "$phone • Sirius 59-T1 882.91" else email

        // Lắng nghe dữ liệu hồ sơ Driver thời gian thực
        if (uid.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                firebaseRepository.observeDriver(uid).collectLatest { driver ->
                    if (driver != null) {
                        currentDriver = driver
                        bindDriverData(driver)
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val storeInfo = firebaseRepository.resolveStoreForDriver(uid)
                if (storeInfo.first.isNotBlank()) {
                    binding.tvAffiliationStoreName.text = storeInfo.second.ifBlank { "Quán ăn trực thuộc" }
                    val store = firebaseRepository.getStoreOnce(storeInfo.first)
                    if (store != null && store.address.isNotBlank()) {
                        binding.tvAffiliationStoreAddress.text = store.address
                    }
                }
            }
        }
    }

    private fun bindDriverData(driver: Driver) {
        val name = driver.name.ifBlank { auth.currentUser?.displayName ?: "Tài xế" }
        val phone = driver.phone.ifBlank { auth.currentUser?.phoneNumber ?: "Chưa cập nhật" }
        val model = driver.vehicleModel.ifBlank { "Sirius FI" }
        val plate = driver.vehiclePlate.ifBlank { "59-T1 882.91" }
        val cccd = driver.cccdNumber.ifBlank { "Chưa cập nhật" }
        val license = driver.licenseNumber.ifBlank { "Chưa cập nhật" }

        binding.tvProfileName.text = name
        binding.tvProfileSub.text = "$phone • $model $plate"
        if (driver.driverCode.isNotBlank()) {
            binding.tvDriverBadgeId.text = "MÃ TX: ${driver.driverCode}"
        }

        binding.tvDetailDriverPhone.text = phone
        binding.tvDetailVehicleModel.text = model
        binding.tvDetailVehiclePlate.text = plate
        binding.tvDetailCccd.text = cccd
        binding.tvDetailLicense.text = license
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

        binding.btnEditDriverProfile.setOnClickListener {
            showEditDriverDialog()
        }

        binding.btnEditDriverDetails.setOnClickListener {
            showEditDriverDialog()
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
     * Tên chức năng: Hộp thoại chỉnh sửa thông tin hồ sơ tài xế
     * Cách hoạt động: Cho phép tài xế chỉnh sửa họ tên, SĐT, dòng xe, biển kiểm soát, CCCD, GPLX và lưu lên Firebase.
     * Luồng hướng tới: Cập nhật node drivers/{uid} và users/{uid} đồng bộ ngay lập tức.
     */
    private fun showEditDriverDialog() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_driver_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<EditText>(R.id.etEditDriverName)
        val etPhone = dialog.findViewById<EditText>(R.id.etEditDriverPhone)
        val etVehicleModel = dialog.findViewById<EditText>(R.id.etEditVehicleModel)
        val etVehiclePlate = dialog.findViewById<EditText>(R.id.etEditVehiclePlate)
        val etCccd = dialog.findViewById<EditText>(R.id.etEditDriverCccd)
        val etLicense = dialog.findViewById<EditText>(R.id.etEditDriverLicense)

        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseEditDriverDialog)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelEditDriver)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveEditDriver)

        // Đổ dữ liệu hiện tại vào dialog
        val driver = currentDriver
        val currentName = driver?.name?.ifBlank { user.displayName.orEmpty() } ?: user.displayName.orEmpty()
        val currentPhone = driver?.phone?.ifBlank { user.phoneNumber.orEmpty() } ?: user.phoneNumber.orEmpty()
        val currentModel = driver?.vehicleModel?.ifBlank { "Sirius" } ?: "Sirius"
        val currentPlate = driver?.vehiclePlate?.ifBlank { "59-T1 882.91" } ?: "59-T1 882.91"

        etName.setText(currentName)
        etPhone.setText(currentPhone)
        etVehicleModel.setText(currentModel)
        etVehiclePlate.setText(currentPlate)
        etCccd.setText(driver?.cccdNumber.orEmpty())
        etLicense.setText(driver?.licenseNumber.orEmpty())

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()
            val newModel = etVehicleModel.text.toString().trim()
            val newPlate = etVehiclePlate.text.toString().trim().uppercase()
            val newCccd = etCccd.text.toString().trim()
            val newLicense = etLicense.text.toString().trim()

            if (newName.isBlank()) {
                etName.error = "Vui lòng nhập họ và tên tài xế"
                etName.requestFocus()
                return@setOnClickListener
            }
            if (newPhone.isBlank()) {
                etPhone.error = "Vui lòng nhập số điện thoại"
                etPhone.requestFocus()
                return@setOnClickListener
            }
            if (newModel.isBlank()) {
                etVehicleModel.error = "Vui lòng nhập dòng xe máy"
                etVehicleModel.requestFocus()
                return@setOnClickListener
            }
            if (newPlate.isBlank()) {
                etVehiclePlate.error = "Vui lòng nhập biển số xe"
                etVehiclePlate.requestFocus()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val result = firebaseRepository.updateDriverProfile(
                    driverId = uid,
                    name = newName,
                    phone = newPhone,
                    vehicleModel = newModel,
                    vehiclePlate = newPlate,
                    cccdNumber = newCccd,
                    licenseNumber = newLicense
                )

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "✅ Cập nhật hồ sơ tài xế thành công!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "❌ Lỗi: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    btnSave.isEnabled = true
                }
            }
        }

        dialog.show()
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
