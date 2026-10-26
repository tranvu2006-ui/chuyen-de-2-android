package com.chuyen_de_2.foodorder.admin.ui.store

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chuyen_de_2.foodorder.admin.R
import com.chuyen_de_2.foodorder.admin.databinding.FragmentAdminStoreListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminStoreListFragment : Fragment() {

    private var _binding: FragmentAdminStoreListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminStoreViewModel by viewModels()
    private lateinit var adapter: AdminStoreAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminStoreListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = AdminStoreAdapter { store ->
            val bottomSheet = AdminStoreDetailBottomSheetDialogFragment(
                store = store,
                onApprove = { s -> viewModel.approveStore(s.id) },
                onLock = { s -> viewModel.lockStore(s.id) },
                onApplyPenalty = { sId, tier, reason -> viewModel.applyPenalty(sId, tier, reason) }
            )
            bottomSheet.show(childFragmentManager, AdminStoreDetailBottomSheetDialogFragment.TAG)
        }

        binding.rvStores.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStores.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshStore.setOnRefreshListener {
            binding.swipeRefreshStore.isRefreshing = false
        }

        binding.etSearchStore.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupStoreFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipPendingStores -> viewModel.setFilter(StoreFilter.PENDING)
                R.id.chipOpenStores -> viewModel.setFilter(StoreFilter.OPEN)
                R.id.chipLockedStores -> viewModel.setFilter(StoreFilter.LOCKED)
                else -> viewModel.setFilter(StoreFilter.ALL)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredStores.collect { stores ->
                        adapter.submitList(stores)
                        binding.tvEmptyStore.visibility = if (stores.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.counts.collect { c ->
                        binding.chipAllStores.text = "Tất cả (${c.total})"
                        binding.chipPendingStores.text = "Chờ duyệt (${c.pending})"
                        binding.chipOpenStores.text = "Đang mở (${c.open})"
                        binding.chipLockedStores.text = "Bị khóa (${c.locked})"
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
