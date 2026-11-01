package com.chuyen_de_2.foodorder.internal.ui.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.model.Voucher
import com.chuyen_de_2.foodorder.core.util.Constants
import com.chuyen_de_2.foodorder.internal.databinding.BottomSheetManageVouchersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * BottomSheet Quản Lý Mã Giảm Giá Của Quán (Store Voucher Manager)
 * Cho phép Chủ Quán:
 *  - Xem danh sách mã giảm giá của quán mình
 *  - Tạo mã ưu đãi mới (mã code, số tiền giảm, giá trị đơn tối thiểu)
 *  - Xóa mã giảm giá khi hết chương trình
 */
class ManageVouchersBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetManageVouchersBinding? = null
    private val binding get() = _binding!!

    private var storeId: String = ""
    private lateinit var adapter: ManageVouchersAdapter
    private var vouchersListener: ValueEventListener? = null
    // Lưu lại ID của voucher đang được chỉnh sửa (nếu null là đang tạo mới)
    private var editingVoucherId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storeId = arguments?.getString(ARG_STORE_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetManageVouchersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener { dismiss() }

        setupRecyclerView()
        setupListeners()
        listenVouchersFromFirebase()
    }

    private fun setupRecyclerView() {
        adapter = ManageVouchersAdapter(
            onEditClick = { voucher ->
                // Đưa thông tin voucher lên form để chỉnh sửa
                startEditingVoucher(voucher)
            },
            onDeleteClick = { voucher ->
                deleteVoucher(voucher)
            }
        )
        binding.rvVouchers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVouchers.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnCreateVoucher.setOnClickListener {
            val code = binding.etVoucherCode.text.toString().trim().uppercase()
            val discountRaw = binding.etDiscountAmount.text.toString().trim()
            val minOrderRaw = binding.etMinOrder.text.toString().trim()

            if (code.isBlank()) {
                binding.etVoucherCode.error = "Nhập mã voucher"
                return@setOnClickListener
            }

            // Loại bỏ dấu chấm, phẩy nếu người dùng nhập theo định dạng tiền (VD: 10.000)
            val cleanDiscount = discountRaw.replace("[^0-9]".toRegex(), "")
            val discountAmt = cleanDiscount.toLongOrNull() ?: 0L
            if (discountAmt <= 0L) {
                binding.etDiscountAmount.error = "Số tiền giảm phải > 0"
                return@setOnClickListener
            }

            val cleanMinOrder = minOrderRaw.replace("[^0-9]".toRegex(), "")
            val minOrderAmt = cleanMinOrder.toLongOrNull() ?: 0L

            if (editingVoucherId != null) {
                // Đang trong chế độ CHỈNH SỬA
                updateVoucher(editingVoucherId!!, code, discountAmt, minOrderAmt)
            } else {
                // Đang trong chế độ TẠO MỚI
                createVoucher(code, discountAmt, minOrderAmt)
            }
        }

        binding.btnCancelEdit.setOnClickListener {
            resetForm()
        }
    }

    private fun startEditingVoucher(voucher: Voucher) {
        editingVoucherId = voucher.voucherId
        binding.etVoucherCode.setText(voucher.code)
        binding.etDiscountAmount.setText(voucher.discountAmount.toString())
        binding.etMinOrder.setText(voucher.minOrderAmount.toString())

        binding.tvFormTitle.text = "CHỈNH SỬA MÃ ${voucher.code}"
        binding.btnCreateVoucher.text = "Lưu Sửa"
        binding.btnCancelEdit.visibility = View.VISIBLE
    }

    private fun resetForm() {
        editingVoucherId = null
        binding.etVoucherCode.text?.clear()
        binding.etDiscountAmount.text?.clear()
        binding.etMinOrder.text?.clear()

        binding.tvFormTitle.text = "TẠO MÃ MỚI"
        binding.btnCreateVoucher.text = "Thêm Mã"
        binding.btnCancelEdit.visibility = View.GONE
    }

    private fun updateVoucher(voucherId: String, code: String, discountAmount: Long, minOrderAmount: Long) {
        if (storeId.isBlank()) return

        binding.btnCreateVoucher.isEnabled = false
        val updatedVoucher = Voucher(
            voucherId = voucherId,
            storeId = storeId,
            code = code,
            discountAmount = discountAmount,
            minOrderAmount = minOrderAmount,
            title = "Giảm $discountAmount đ cho đơn từ $minOrderAmount đ",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
            .getReference("vouchers")
            .child(storeId)
            .child(voucherId)
            .setValue(updatedVoucher)
            .addOnSuccessListener {
                binding.btnCreateVoucher.isEnabled = true
                Toast.makeText(requireContext(), "✓ Đã cập nhật mã $code thành công!", Toast.LENGTH_SHORT).show()
                resetForm()
            }
            .addOnFailureListener { e ->
                binding.btnCreateVoucher.isEnabled = true
                Toast.makeText(requireContext(), "Lỗi cập nhật mã: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createVoucher(code: String, discountAmount: Long, minOrderAmount: Long) {
        if (storeId.isBlank()) {
            Toast.makeText(requireContext(), "Không tìm thấy mã quán", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnCreateVoucher.isEnabled = false
        val voucherId = "vouch_${System.currentTimeMillis()}"
        val voucher = Voucher(
            voucherId = voucherId,
            storeId = storeId,
            code = code,
            discountAmount = discountAmount,
            minOrderAmount = minOrderAmount,
            title = "Giảm $discountAmount đ cho đơn từ $minOrderAmount đ",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
            .getReference("vouchers")
            .child(storeId)
            .child(voucherId)
            .setValue(voucher)
            .addOnSuccessListener {
                binding.btnCreateVoucher.isEnabled = true
                Toast.makeText(requireContext(), "✓ Đã tạo mã giảm giá $code thành công!", Toast.LENGTH_SHORT).show()
                binding.etVoucherCode.text?.clear()
                binding.etDiscountAmount.text?.clear()
                binding.etMinOrder.text?.clear()
            }
            .addOnFailureListener { e ->
                binding.btnCreateVoucher.isEnabled = true
                Toast.makeText(requireContext(), "Lỗi khi lưu mã: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteVoucher(voucher: Voucher) {
        if (storeId.isBlank() || voucher.voucherId.isBlank()) return

        FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
            .getReference("vouchers")
            .child(storeId)
            .child(voucher.voucherId)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Đã xóa mã ${voucher.code}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenVouchersFromFirebase() {
        if (storeId.isBlank()) return

        val ref = FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
            .getReference("vouchers")
            .child(storeId)

        vouchersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Voucher>()
                for (child in snapshot.children) {
                    val v = child.getValue(Voucher::class.java)
                    if (v != null) {
                        list.add(v)
                    }
                }
                adapter.submitList(list)
                binding.tvEmptyVouchers.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Lỗi kết nối voucher: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ref.addValueEventListener(vouchersListener!!)
    }

    override fun onDestroyView() {
        vouchersListener?.let {
            if (storeId.isNotBlank()) {
                FirebaseDatabase.getInstance(Constants.FIREBASE_DATABASE_URL)
                    .getReference("vouchers")
                    .child(storeId)
                    .removeEventListener(it)
            }
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STORE_ID = "arg_store_id"

        fun newInstance(storeId: String): ManageVouchersBottomSheetDialogFragment {
            return ManageVouchersBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STORE_ID, storeId)
                }
            }
        }
    }
}
