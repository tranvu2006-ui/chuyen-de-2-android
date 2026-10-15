package com.chuyen_de_2.foodorder.internal.ui.driver

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Driver
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentDriverListBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DriverListFragment — Quản lý Đội ngũ Tài xế Quán
 * Giao diện và nghiệp vụ chuẩn theo Quán/1.html:
 * - Thống kê nhanh đội xe (Tổng tài xế, Đang rảnh, Đang giao, Nghỉ ca)
 * - Danh sách thẻ thông tin từng tài xế (Xe máy, Đơn đã giao, Đánh giá uy tín)
 * - Nút gọi điện liên hệ tài xế qua ACTION_DIAL
 * - Nút Thêm TX mở hộp thoại thêm tài xế mới vào Firebase
 */
@AndroidEntryPoint
class DriverListFragment : Fragment() {

    private var _binding: FragmentDriverListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var adapter: DriverAdapter
    private var currentStoreId = "shop_01"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriverListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        resolveCurrentStoreIdAndObserve()
    }

    private fun resolveCurrentStoreIdAndObserve() {
        val uid = auth.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            observeDrivers()
        }
    }

    private fun setupRecyclerView() {
        adapter = DriverAdapter(
            onCallClick = { phone ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phone")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Không thể mở ứng dụng gọi điện", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvDrivers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDrivers.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnAddDriver.setOnClickListener {
            showAddDriverDialog()
        }
    }

    private fun observeDrivers() {
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            firebaseRepository.observeStoreDrivers(currentStoreId).collectLatest { drivers ->
                binding.progressBar.visibility = View.GONE

                val total = drivers.size
                val available = drivers.count { it.dutyStatus == Driver.DutyStatus.AVAILABLE }
                val delivering = drivers.count { it.dutyStatus == Driver.DutyStatus.DELIVERING }
                val offDuty = drivers.count { it.dutyStatus == Driver.DutyStatus.OFF_DUTY }

                binding.tvTotalDrivers.text = total.toString()
                binding.tvAvailableDrivers.text = available.toString()
                binding.tvDeliveringDrivers.text = delivering.toString()
                binding.tvOffDutyDrivers.text = offDuty.toString()

                if (drivers.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvDrivers.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvDrivers.visibility = View.VISIBLE
                    adapter.submitList(drivers)
                }
            }
        }
    }

    private fun showAddDriverDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_driver)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<EditText>(R.id.etDriverName)
        val etPhone = dialog.findViewById<EditText>(R.id.etDriverPhone)
        val etCode = dialog.findViewById<EditText>(R.id.etDriverCode)
        val etPlate = dialog.findViewById<EditText>(R.id.etDriverPlate)
        val etModel = dialog.findViewById<EditText>(R.id.etVehicleModel)
        val etEmail = dialog.findViewById<EditText>(R.id.etDriverEmail)
        val etPassword = dialog.findViewById<EditText>(R.id.etDriverPassword)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseDialog)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialog.findViewById<MaterialButton>(R.id.btnSubmitDriver)

        // Tự động tạo mã tài xế và gợi ý email
        val randomNum = (10..99).random()
        etCode.setText("TX-$randomNum")
        etEmail.setText("driver$randomNum@oishi.vn")
        etPassword.setText("123456")

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val code = etCode.text.toString().trim().ifBlank { "TX-$randomNum" }
            val plate = etPlate.text.toString().trim()
            val model = etModel.text.toString().trim().ifBlank { "Honda Wave Alpha" }
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (name.isBlank()) {
                etName.error = "Vui lòng nhập họ và tên tài xế"
                return@setOnClickListener
            }
            if (phone.isBlank()) {
                etPhone.error = "Vui lòng nhập số điện thoại"
                return@setOnClickListener
            }
            if (plate.isBlank()) {
                etPlate.error = "Vui lòng nhập biển số xe"
                return@setOnClickListener
            }
            if (email.isBlank() || !email.contains("@")) {
                etEmail.error = "Vui lòng nhập email hợp lệ"
                return@setOnClickListener
            }
            if (pass.length < 6) {
                etPassword.error = "Mật khẩu đăng nhập tối thiểu 6 ký tự"
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Đang tạo..."

            viewLifecycleOwner.lifecycleScope.launch {
                val result = firebaseRepository.createDriverAccount(
                    context = requireContext(),
                    storeId = currentStoreId,
                    name = name,
                    phone = phone,
                    driverCode = code,
                    vehiclePlate = plate,
                    vehicleModel = model,
                    email = email,
                    pass = pass
                )

                if (result.isSuccess) {
                    Toast.makeText(
                        requireContext(),
                        "✓ Đã tạo tài khoản tài xế [$name - $code] thành công!\nEmail: $email",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Lỗi tạo tài khoản: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Tạo tài khoản"
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
