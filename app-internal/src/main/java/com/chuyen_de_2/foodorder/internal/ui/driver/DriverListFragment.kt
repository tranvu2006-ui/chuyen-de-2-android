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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.FirebaseRepository
import com.chuyen_de_2.foodorder.core.data.model.Chef
import com.chuyen_de_2.foodorder.core.data.model.Driver
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.internal.R
import com.chuyen_de_2.foodorder.internal.databinding.FragmentDriverListBinding
import com.chuyen_de_2.foodorder.internal.ui.chef.ChefAdapter
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quản lý Đội ngũ Nhân sự Của Quán (Staff Management):
 * - Tab 1: Đội Tài Xế (Drivers): Thống kê, giám sát tình trạng trực/giao đơn, tạo tài khoản tài xế.
 * - Tab 2: Đội Đầu Bếp (Chefs): Thống kê, giám sát tình trạng nấu món, tạo tài khoản đầu bếp trực thuộc quán.
 */
@AndroidEntryPoint
class DriverListFragment : Fragment() {

    private var _binding: FragmentDriverListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var firebaseRepository: FirebaseRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private lateinit var driverAdapter: DriverAdapter
    private lateinit var chefAdapter: ChefAdapter

    private var currentStoreId = ""
    private var allDriversList: List<Driver> = emptyList()
    private var currentFilter: String = "ALL"
    private var isChefTabSelected = false

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

        setupRecyclerViews()
        setupTabs()
        setupListeners()
        resolveCurrentStoreIdAndObserve()
    }

    private fun resolveCurrentStoreIdAndObserve() {
        val uid = auth.currentUser?.uid.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            currentStoreId = firebaseRepository.resolveStoreIdForUser(uid)
            observeDrivers()
            observeChefs()
        }
    }

    private fun setupRecyclerViews() {
        // 1. Adapter Tài Xế
        driverAdapter = DriverAdapter(
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
        binding.rvDrivers.adapter = driverAdapter

        // 2. Adapter Đầu Bếp
        chefAdapter = ChefAdapter(
            onCallClick = { phone ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phone")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Không thể mở ứng dụng gọi điện", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteClick = { chef ->
                showDeleteChefDialog(chef)
            }
        )
        binding.rvChefs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChefs.adapter = chefAdapter
    }

    private fun setupTabs() {
        binding.btnTabDrivers.setOnClickListener {
            if (isChefTabSelected) {
                isChefTabSelected = false
                updateTabUI()
            }
        }

        binding.btnTabChefs.setOnClickListener {
            if (!isChefTabSelected) {
                isChefTabSelected = true
                updateTabUI()
            }
        }
    }

    private fun updateTabUI() {
        if (!isChefTabSelected) {
            // Hiển thị Tab Tài Xế
            binding.tvStaffHeaderTitle.text = "Đội Tài Xế Của Quán"
            binding.btnAddDriver.visibility = View.VISIBLE
            binding.btnAddChef.visibility = View.GONE
            binding.layoutDriverSection.visibility = View.VISIBLE
            binding.layoutChefSection.visibility = View.GONE

            binding.btnTabDrivers.apply {
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.brand_blue)
                setTextColor(Color.WHITE)
                strokeWidth = 0
            }
            binding.btnTabChefs.apply {
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.transparent)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                setStrokeColorResource(R.color.border)
                strokeWidth = 2
            }
        } else {
            // Hiển thị Tab Đầu Bếp
            binding.tvStaffHeaderTitle.text = "Đội Đầu Bếp Của Quán"
            binding.btnAddDriver.visibility = View.GONE
            binding.btnAddChef.visibility = View.VISIBLE
            binding.layoutDriverSection.visibility = View.GONE
            binding.layoutChefSection.visibility = View.VISIBLE

            binding.btnTabChefs.apply {
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.brand_orange)
                setTextColor(Color.WHITE)
                strokeWidth = 0
            }
            binding.btnTabDrivers.apply {
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.transparent)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                setStrokeColorResource(R.color.border)
                strokeWidth = 2
            }
        }
    }

    private fun setupListeners() {
        binding.btnAddDriver.setOnClickListener {
            showAddDriverDialog()
        }
        binding.btnAddChef.setOnClickListener {
            showAddChefDialog()
        }

        binding.layoutStatTotal.setOnClickListener {
            currentFilter = "ALL"
            applyFilter()
            Toast.makeText(requireContext(), "Hiển thị: Tất cả tài xế", Toast.LENGTH_SHORT).show()
        }
        binding.layoutStatAvailable.setOnClickListener {
            currentFilter = Driver.DutyStatus.AVAILABLE
            applyFilter()
            Toast.makeText(requireContext(), "Lọc: Tài xế Đang trực (Sẵn sàng)", Toast.LENGTH_SHORT).show()
        }
        binding.layoutStatDelivering.setOnClickListener {
            currentFilter = Driver.DutyStatus.DELIVERING
            applyFilter()
            Toast.makeText(requireContext(), "Lọc: Tài xế Đang đi giao đơn", Toast.LENGTH_SHORT).show()
        }
        binding.layoutStatOffDuty.setOnClickListener {
            currentFilter = Driver.DutyStatus.OFF_DUTY
            applyFilter()
            Toast.makeText(requireContext(), "Lọc: Tài xế Nghỉ ca (OFF)", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeDrivers() {
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                firebaseRepository.observeStoreDrivers(currentStoreId),
                firebaseRepository.observeAllOrders()
            ) { drivers, orders ->
                val activeOrders = orders.filter { order ->
                    !order.shipperId.isNullOrBlank() && (
                        order.status == Order.Status.ASSIGNED ||
                        order.status == Order.Status.ARRIVED_AT_SHOP ||
                        order.status == Order.Status.DELIVERING ||
                        order.status == Order.Status.ARRIVED_AT_CUSTOMER ||
                        order.status == Order.Status.RETURNING_TO_STORE
                    )
                }
                val activeOrdersByDriver = activeOrders.groupBy { it.shipperId }

                drivers.map { driver ->
                    val deliveringOrders = activeOrdersByDriver[driver.driverId].orEmpty()
                    if (deliveringOrders.isNotEmpty()) {
                        val primaryOrder = deliveringOrders.first()
                        val shortCode = QRCodeUtils.getShortOrderCode(primaryOrder.orderId)
                        val info = if (primaryOrder.status == Order.Status.RETURNING_TO_STORE) {
                            "#$shortCode • Trả hàng về quán"
                        } else if (deliveringOrders.size > 1) {
                            "#$shortCode (+${deliveringOrders.size - 1} đơn ghép)"
                        } else {
                            "#$shortCode • ${primaryOrder.customerName}"
                        }
                        driver.copy(
                            dutyStatus = Driver.DutyStatus.DELIVERING,
                            currentOrderCode = info
                        )
                    } else if (driver.dutyStatus == Driver.DutyStatus.OFF_DUTY ||
                        driver.dutyStatus?.equals("OFFLINE", ignoreCase = true) == true ||
                        driver.dutyStatus.isNullOrBlank()
                    ) {
                        driver.copy(dutyStatus = Driver.DutyStatus.OFF_DUTY)
                    } else {
                        driver.copy(dutyStatus = Driver.DutyStatus.AVAILABLE)
                    }
                }
            }.collectLatest { syncedDrivers ->
                binding.progressBar.visibility = View.GONE
                allDriversList = syncedDrivers

                val total = syncedDrivers.size
                val available = syncedDrivers.count { it.dutyStatus == Driver.DutyStatus.AVAILABLE }
                val delivering = syncedDrivers.count { it.dutyStatus == Driver.DutyStatus.DELIVERING }
                val offDuty = syncedDrivers.count { it.dutyStatus == Driver.DutyStatus.OFF_DUTY }

                binding.tvTotalDrivers.text = total.toString()
                binding.tvAvailableDrivers.text = available.toString()
                binding.tvDeliveringDrivers.text = delivering.toString()
                binding.tvOffDutyDrivers.text = offDuty.toString()

                applyFilter()
            }
        }
    }

    private fun observeChefs() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                firebaseRepository.observeStoreChefs(currentStoreId),
                firebaseRepository.observeAllOrders()
            ) { chefs, orders ->
                // Đếm số đơn PREPARING mà từng đầu bếp đang nấu
                val preparingOrders = orders.filter { it.status == Order.Status.PREPARING && !it.chefId.isNullOrBlank() }
                val cookingCounts = preparingOrders.groupBy { it.chefId }.mapValues { it.value.size }

                chefs.map { chef ->
                    val activeCount = cookingCounts[chef.chefId] ?: 0
                    chef.copy(activeOrdersCount = activeCount)
                }
            }.collectLatest { syncedChefs ->
                val totalChefs = syncedChefs.size
                val cookingChefs = syncedChefs.count { it.activeOrdersCount > 0 }
                val availableChefs = syncedChefs.count { it.activeOrdersCount == 0 }

                binding.tvTotalChefs.text = totalChefs.toString()
                binding.tvChefsCooking.text = cookingChefs.toString()
                binding.tvChefsAvailable.text = availableChefs.toString()

                if (syncedChefs.isEmpty()) {
                    binding.layoutEmptyChefs.visibility = View.VISIBLE
                    binding.rvChefs.visibility = View.GONE
                } else {
                    binding.layoutEmptyChefs.visibility = View.GONE
                    binding.rvChefs.visibility = View.VISIBLE
                    chefAdapter.submitList(syncedChefs)
                }
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            Driver.DutyStatus.AVAILABLE -> allDriversList.filter { it.dutyStatus == Driver.DutyStatus.AVAILABLE }
            Driver.DutyStatus.DELIVERING -> allDriversList.filter { it.dutyStatus == Driver.DutyStatus.DELIVERING }
            Driver.DutyStatus.OFF_DUTY -> allDriversList.filter { it.dutyStatus == Driver.DutyStatus.OFF_DUTY }
            else -> allDriversList
        }

        if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvDrivers.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvDrivers.visibility = View.VISIBLE
            driverAdapter.submitList(filtered)
        }
    }

    // ==================== DIALOGS ====================

    private fun showAddChefDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_chef)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etName = dialog.findViewById<EditText>(R.id.etChefName)
        val etPhone = dialog.findViewById<EditText>(R.id.etChefPhone)
        val etTitle = dialog.findViewById<EditText>(R.id.etChefTitle)
        val etEmail = dialog.findViewById<EditText>(R.id.etChefEmail)
        val etPassword = dialog.findViewById<EditText>(R.id.etChefPassword)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseDialog)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialog.findViewById<MaterialButton>(R.id.btnSubmitChef)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val title = etTitle.text.toString().trim().ifBlank { "Đầu Bếp" }
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (name.isBlank()) {
                etName.error = "Vui lòng nhập họ và tên đầu bếp"
                return@setOnClickListener
            }
            if (phone.isBlank()) {
                etPhone.error = "Vui lòng nhập số điện thoại"
                return@setOnClickListener
            }
            if (email.isBlank() || !email.contains("@")) {
                etEmail.error = "Vui lòng nhập email hợp lệ"
                return@setOnClickListener
            }
            if (pass.length < 6) {
                etPassword.error = "Mật khẩu tối thiểu 6 ký tự"
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Đang tạo tài khoản..."

            viewLifecycleOwner.lifecycleScope.launch {
                val result = firebaseRepository.createChefAccount(
                    context = requireContext(),
                    storeId = currentStoreId,
                    name = name,
                    phone = phone,
                    email = email,
                    pass = pass,
                    title = title
                )

                if (result.isSuccess) {
                    Toast.makeText(
                        requireContext(),
                        "✓ Đã tạo tài khoản Đầu Bếp [$name] thành công!\nEmail: $email",
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
                    btnSubmit.text = "Tạo tài khoản bếp"
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteChefDialog(chef: Chef) {
        AlertDialog.Builder(requireContext())
            .setTitle("Xóa tài khoản đầu bếp")
            .setMessage("Bạn có chắc chắn muốn xóa đầu bếp ${chef.name} khỏi danh sách nhân sự của quán không?")
            .setPositiveButton("Xóa") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = firebaseRepository.deleteChef(chef.chefId)
                    if (result.isSuccess) {
                        Toast.makeText(requireContext(), "Đã xóa đầu bếp ${chef.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Lỗi xóa: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
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

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val code = etCode.text.toString().trim()
            val plate = etPlate.text.toString().trim()
            val model = etModel.text.toString().trim()
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
