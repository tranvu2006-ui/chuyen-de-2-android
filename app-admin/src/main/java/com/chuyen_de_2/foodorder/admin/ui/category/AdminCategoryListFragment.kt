package com.chuyen_de_2.foodorder.admin.ui.category

import android.os.Bundle
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
import com.chuyen_de_2.foodorder.admin.databinding.FragmentAdminCategoryListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminCategoryListFragment : Fragment() {

    private var _binding: FragmentAdminCategoryListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminCategoryViewModel by viewModels()
    private lateinit var adapter: AdminCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminCategoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = AdminCategoryAdapter(
            onEdit = { category ->
                val dialog = AddEditCategoryDialogFragment(categoryToEdit = category) { updatedCat ->
                    viewModel.saveCategory(updatedCat)
                }
                dialog.show(childFragmentManager, AddEditCategoryDialogFragment.TAG)
            },
            onDelete = { category ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Xóa danh mục")
                    .setMessage("Bạn có chắc chắn muốn xóa danh mục '${category.name}' khỏi sàn?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xóa") { _, _ ->
                        viewModel.deleteCategory(category.categoryId)
                    }
                    .show()
            },
            onToggleActive = { category, isChecked ->
                viewModel.toggleCategoryActive(category, isChecked)
            }
        )

        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshCategory.setOnRefreshListener {
            binding.swipeRefreshCategory.isRefreshing = false
        }

        binding.btnAddCategory.setOnClickListener {
            val dialog = AddEditCategoryDialogFragment { newCat ->
                viewModel.saveCategory(newCat)
            }
            dialog.show(childFragmentManager, AddEditCategoryDialogFragment.TAG)
        }

        binding.btnRestoreCategoryDefaults.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Khôi phục danh mục mẫu")
                .setMessage("Thao tác này sẽ đồng bộ lại 8 danh mục mặc định của Oishi Food lên cơ sở dữ liệu.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đồng ý") { _, _ ->
                    viewModel.restoreDefaults()
                }
                .show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { list ->
                        adapter.submitList(list)
                        val activeCount = list.count { it.isActive }
                        binding.tvCategoryStats.text = "Tổng: ${list.size} nhóm món • $activeCount đang hiển thị trên app Khách"
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
