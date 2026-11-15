package com.chuyen_de_2.foodorder.internal.ui.chef

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.core.data.firebase.AuthRepository
import com.chuyen_de_2.foodorder.core.data.model.Order
import com.chuyen_de_2.foodorder.core.util.QRCodeUtils
import com.chuyen_de_2.foodorder.internal.databinding.FragmentChefBinding
import com.chuyen_de_2.foodorder.internal.ui.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Màn hình Bếp (Kitchen / Chef Dashboard):
 * - Xem danh sách đơn đang chờ đầu bếp nhận nấu (PREPARING, chefId rỗng).
 * - Bấm "Nhận nấu" tương tự như shipper nhận đơn (atomic lock chống nhận trùng).
 * - Xem danh sách các đơn mà chính đầu bếp này đang nấu.
 * - Thao tác duy nhất sau khi nấu xong: Bấm "Đã nấu xong" -> chuyển sang READY để tài xế nhận giao.
 * - Quản lý (Merchant) không thể xác nhận nấu xong thay cho đầu bếp.
 */
@AndroidEntryPoint
class ChefFragment : Fragment() {

    private var _binding: FragmentChefBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var authRepository: AuthRepository

    private val viewModel: ChefViewModel by activityViewModels()

    private lateinit var activeAdapter: ChefActiveOrdersAdapter
    private lateinit var pendingAdapter: ChefPendingOrdersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChefBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupLogout()
        observeData()
    }

    private fun setupLogout() {
        binding.btnChefLogoutHeader.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("🚪 Đăng Xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi ca làm việc bếp không?")
                .setPositiveButton("Đăng xuất") { _, _ ->
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

    private fun setupAdapters() {
        activeAdapter = ChefActiveOrdersAdapter(
            onMarkDone = { order ->
                confirmMarkDone(order)
            },
            onCancelOrder = { order ->
                confirmCancelOrder(order)
            }
        )

        pendingAdapter = ChefPendingOrdersAdapter(
            onAcceptOrder = { order ->
                viewModel.acceptOrder(order.orderId) { success, msg ->
                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "👨‍🍳 Đã nhận nấu đơn #${QRCodeUtils.getShortOrderCode(order.orderId)}!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        binding.rvChefActiveOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activeAdapter
        }

        binding.rvChefPendingOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pendingAdapter
        }
    }

    private fun confirmMarkDone(order: Order) {
        val shortCode = QRCodeUtils.getShortOrderCode(order.orderId)
        AlertDialog.Builder(requireContext())
            .setTitle("🍳 Xác nhận nấu xong")
            .setMessage("Bạn đã nấu xong các món cho đơn #$shortCode chưa?\nĐơn sẽ được chuyển sang 'Sẵn sàng giao' cho tài xế.")
            .setPositiveButton("Đã nấu xong") { _, _ ->
                viewModel.markCookingDone(order.orderId) { success, msg ->
                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "✓ Đơn #$shortCode đã nấu xong! Chờ tài xế lấy món.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Chưa xong", null)
            .show()
    }

    private fun confirmCancelOrder(order: Order) {
        val shortCode = QRCodeUtils.getShortOrderCode(order.orderId)
        AlertDialog.Builder(requireContext())
            .setTitle("Hủy nhận nấu")
            .setMessage("Bạn có chắc muốn hủy nhận đơn #$shortCode?\nĐơn sẽ được hoàn về danh sách chờ cho đầu bếp khác nhận.")
            .setPositiveButton("Hủy nhận") { _, _ ->
                viewModel.cancelOrder(order.orderId) { success, msg ->
                    if (success) {
                        Toast.makeText(
                            requireContext(),
                            "Đã hủy nhận đơn #$shortCode",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Giữ lại", null)
            .show()
    }

    private fun observeData() {
        viewModel.chefStore.observe(viewLifecycleOwner) { storeInfo ->
            val storeName = storeInfo.second
            if (storeName.isNotBlank()) {
                binding.tvChefStoreBadge.text = "Bếp • $storeName"
            } else {
                binding.tvChefStoreBadge.text = "Bếp nội bộ quán"
            }
        }

        viewModel.chefName.observe(viewLifecycleOwner) { name ->
            binding.tvChefName.text = name.ifBlank { "Đầu Bếp" }
        }

        viewModel.activeOrders.observe(viewLifecycleOwner) { orders ->
            activeAdapter.submitList(orders)
            binding.lblChefActiveBadge.text = "${orders.size} đơn"
            binding.tvStatActiveChef.text = "${orders.size} đơn"
            binding.tvEmptyChefActive.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.pendingOrders.observe(viewLifecycleOwner) { orders ->
            pendingAdapter.submitList(orders)
            binding.lblChefPendingBadge.text = "${orders.size} đơn"
            binding.tvStatPendingChef.text = "${orders.size} đơn"
            binding.tvEmptyChefPending.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.todayDoneCount.observe(viewLifecycleOwner) { count ->
            binding.tvStatDoneChef.text = "$count đơn"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
