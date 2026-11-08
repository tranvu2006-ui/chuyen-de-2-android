package com.chuyen_de_2.foodorder.admin.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.FragmentAdminDashboardBinding
import com.chuyen_de_2.foodorder.admin.ui.main.AdminMainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminDashboardViewModel by viewModels()
    private val currencyFormatter = DecimalFormat("#,###")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.btnRestoreCategories.setOnClickListener {
            viewModel.restoreDefaultCategories()
        }

        binding.btnConfigureCountdown.setOnClickListener {
            showCountdownConfigDialog()
        }

        setupNavigationClicks()
        observeViewModel()
    }

    private fun setupNavigationClicks() {
        val mainActivity = activity as? AdminMainActivity

        binding.cardRevenue.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_monitor)
        }

        binding.cardOrders.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_monitor)
        }

        binding.cardComplaints.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_complaints)
        }

        binding.cardStores.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_stores)
        }

        binding.cardUsers.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_monitor)
        }

        binding.actionQuickApproveStore.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_stores)
        }

        binding.actionQuickComplaints.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_complaints)
        }

        binding.actionQuickAddCategory.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_categories)
        }

        binding.actionQuickMonitor.setOnClickListener {
            mainActivity?.selectNavigationTab(R.id.menu_monitor)
        }

        binding.btnQuickHandleUrgent.setOnClickListener {
            val kpi = viewModel.kpi.value
            if (kpi.pendingStores > 0) {
                mainActivity?.selectNavigationTab(R.id.menu_stores)
            } else if (kpi.pendingComplaints > 0) {
                mainActivity?.selectNavigationTab(R.id.menu_complaints)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.kpi.collect { kpi ->
                        binding.tvRevenue.text = "${currencyFormatter.format(kpi.totalRevenue)} đ"
                        binding.tvCompletedOrdersChip.text = "🟢 ${kpi.completedOrders} Đơn hoàn tất"
                        binding.tvBoomOrdersChip.text = "🔴 ${kpi.boomOrders} Đơn huỷ/boom"
                        binding.tvTotalOrders.text = kpi.totalOrders.toString()
                        binding.tvOrderSubtext.text = "${kpi.completedOrders} hoàn thành • ${kpi.boomOrders} huỷ/boom"
                        binding.tvPendingComplaints.text = kpi.pendingComplaints.toString()
                        binding.tvActiveStores.text = kpi.activeStores.toString()
                        binding.tvPendingStores.text = "${kpi.pendingStores} chờ duyệt CCCD"
                        binding.tvTotalUsers.text = (kpi.totalCustomers + kpi.totalDrivers).toString()
                        binding.tvUserSubtext.text = "${kpi.totalCustomers} Khách • ${kpi.totalDrivers} Tài xế"

                        // Sync alert badge with MainActivity TopBar
                        (activity as? AdminMainActivity)?.updateAlertBadge(kpi.pendingStores + kpi.pendingComplaints)

                        // Urgent attention banner
                        if (kpi.pendingStores > 0 || kpi.pendingComplaints > 0) {
                            binding.cardUrgentBanner.visibility = View.VISIBLE
                            val parts = mutableListOf<String>()
                            if (kpi.pendingStores > 0) parts.add("${kpi.pendingStores} quán chờ duyệt CCCD")
                            if (kpi.pendingComplaints > 0) parts.add("${kpi.pendingComplaints} khiếu nại chờ giải quyết")
                            binding.tvUrgentMessage.text = "Cần chú ý: ${parts.joinToString(" và ")}."
                        } else {
                            binding.cardUrgentBanner.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.actionMessage.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearActionMessage()
                        }
                    }
                }

                launch {
                    viewModel.countdownConfig.collect { config ->
                        binding.tvCurrentCountdownConfig.text =
                            "Lần 1: ${config.round1Minutes} phút • Lần 2 (gia hạn): ${config.round2Minutes} phút"
                    }
                }
            }
        }
    }

    private fun showCountdownConfigDialog() {
        val dialogBinding = com.chuyen_de_2.foodorder.admin.databinding.DialogEditCountdownConfigBinding.inflate(layoutInflater)
        val currentConfig = viewModel.countdownConfig.value

        dialogBinding.etRound1Minutes.setText(currentConfig.round1Minutes.toString())
        dialogBinding.etRound2Minutes.setText(currentConfig.round2Minutes.toString())

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnResetDefault.setOnClickListener {
            dialogBinding.etRound1Minutes.setText("15")
            dialogBinding.etRound2Minutes.setText("15")
        }

        dialogBinding.btnSaveConfig.setOnClickListener {
            val r1Str = dialogBinding.etRound1Minutes.text?.toString()?.trim().orEmpty()
            val r2Str = dialogBinding.etRound2Minutes.text?.toString()?.trim().orEmpty()

            val r1 = r1Str.toIntOrNull()
            val r2 = r2Str.toIntOrNull()

            if (r1 == null || r1 <= 0 || r1 > 120) {
                dialogBinding.tilRound1Minutes.error = "Vui lòng nhập từ 1 đến 120 phút"
                return@setOnClickListener
            } else {
                dialogBinding.tilRound1Minutes.error = null
            }

            if (r2 == null || r2 <= 0 || r2 > 120) {
                dialogBinding.tilRound2Minutes.error = "Vui lòng nhập từ 1 đến 120 phút"
                return@setOnClickListener
            } else {
                dialogBinding.tilRound2Minutes.error = null
            }

            viewModel.saveCountdownConfig(r1, r2)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
