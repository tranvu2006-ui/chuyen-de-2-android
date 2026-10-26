package com.chuyen_de_2.foodorder.admin.ui.monitor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.admin.databinding.FragmentAdminMonitorBinding
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminMonitorFragment : Fragment() {

    private var _binding: FragmentAdminMonitorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminMonitorViewModel by viewModels()
    private lateinit var orderAdapter: AdminOrderAdapter
    private lateinit var userAdapter: AdminUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminMonitorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        orderAdapter = AdminOrderAdapter()
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = orderAdapter

        userAdapter = AdminUserAdapter { user ->
            val actionText = if (user.isActive) "khóa" else "mở khóa"
            AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận thay đổi tài khoản")
                .setMessage("Bạn có chắc chắn muốn $actionText tài khoản '${user.name.ifBlank { user.email }}'?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đồng ý") { _, _ ->
                    viewModel.toggleUserLock(user)
                }
                .show()
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = userAdapter
    }

    private fun setupListeners() {
        binding.swipeRefreshMonitor.setOnRefreshListener {
            binding.swipeRefreshMonitor.isRefreshing = false
        }

        binding.etSearchMonitor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.tabLayoutMonitor.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.rvOrders.visibility = View.VISIBLE
                    binding.rvUsers.visibility = View.GONE
                    binding.etSearchMonitor.hint = "Tìm theo mã đơn, quán, khách hàng..."
                    binding.tvEmptyMonitor.visibility = if (orderAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    binding.rvOrders.visibility = View.GONE
                    binding.rvUsers.visibility = View.VISIBLE
                    binding.etSearchMonitor.hint = "Tìm theo tên người dùng, email, vai trò..."
                    binding.tvEmptyMonitor.visibility = if (userAdapter.currentList.isEmpty()) View.VISIBLE else View.GONE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.orders.collect { orderList ->
                        orderAdapter.submitList(orderList)
                        if (binding.tabLayoutMonitor.selectedTabPosition == 0) {
                            binding.tvEmptyMonitor.visibility = if (orderList.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.users.collect { userList ->
                        userAdapter.submitList(userList)
                        if (binding.tabLayoutMonitor.selectedTabPosition == 1) {
                            binding.tvEmptyMonitor.visibility = if (userList.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.message.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearMessage()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
