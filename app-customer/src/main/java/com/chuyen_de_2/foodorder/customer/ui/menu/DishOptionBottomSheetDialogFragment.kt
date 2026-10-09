package com.chuyen_de_2.foodorder.customer.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.chuyen_de_2.foodorder.core.data.model.CartItem
import com.chuyen_de_2.foodorder.core.data.model.Dish
import com.chuyen_de_2.foodorder.core.util.toVndCurrency
import com.chuyen_de_2.foodorder.customer.R
import com.chuyen_de_2.foodorder.customer.databinding.DialogDishOptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheetDialogFragment tùy chọn món ăn theo chuẩn b6.html
 * (Size, Độ ngọt, Lượng đá, Topping trân châu/thạch/pudding, Số lượng & Tổng tiền động)
 */
class DishOptionBottomSheetDialogFragment(
    private val dish: Dish,
    private val onConfirmAddToCart: (CartItem) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogDishOptionBinding? = null
    private val binding get() = _binding!!

    private var quantity = 1

    override fun getTheme(): Int = R.style.Theme_FoodOrder_Customer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDishOptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDishInfo()
        setupListeners()
        updatePrice()
    }

    private fun setupDishInfo() {
        binding.tvDishName.text = dish.name
        binding.tvDishDesc.text = if (dish.description.isNotBlank()) dish.description else "Món ngon đặc sản Oishi Food"
        binding.tvDishBasePrice.text = dish.price.toVndCurrency()

        Glide.with(this)
            .load(dish.imageUrl)
            .transform(CenterCrop(), RoundedCorners(14))
            .placeholder(R.drawable.placeholder_food)
            .error(R.drawable.placeholder_food)
            .into(binding.ivDishThumb)

        binding.tvModalQty.text = quantity.toString()
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        // Size radio group
        binding.rgSize.setOnCheckedChangeListener { _, _ -> updatePrice() }

        // Sugar radio group
        binding.rgSugar.setOnCheckedChangeListener { _, _ -> updatePrice() }

        // Ice radio group
        binding.rgIce.setOnCheckedChangeListener { _, _ -> updatePrice() }

        // Topping checkboxes
        binding.cbToppingPearl.setOnCheckedChangeListener { _, _ -> updatePrice() }
        binding.cbToppingJelly.setOnCheckedChangeListener { _, _ -> updatePrice() }
        binding.cbToppingPudding.setOnCheckedChangeListener { _, _ -> updatePrice() }

        // Quantity controls
        binding.btnModalMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                binding.tvModalQty.text = quantity.toString()
                updatePrice()
            }
        }

        binding.btnModalPlus.setOnClickListener {
            quantity++
            binding.tvModalQty.text = quantity.toString()
            updatePrice()
        }

        // Add to cart button
        binding.btnAddToCartModal.setOnClickListener {
            val (unitPrice, optionDesc) = calculateUnitPriceAndDescription()
            val customItemId = if (optionDesc.isNotBlank()) "${dish.id}_${optionDesc.hashCode()}" else dish.id

            val cartItem = CartItem(
                itemId = customItemId,
                name = dish.name,
                price = unitPrice,
                quantity = quantity,
                imageUrl = dish.imageUrl,
                options = optionDesc
            )

            onConfirmAddToCart(cartItem)
            dismiss()
        }
    }

    private fun calculateUnitPriceAndDescription(): Pair<Long, String> {
        var unitPrice = dish.price
        val optionsList = mutableListOf<String>()

        // 1. Size
        if (binding.rbSizeL.isChecked) {
            unitPrice += 6000
            optionsList.add("Size L (+6k)")
        } else {
            optionsList.add("Size M")
        }

        // 2. Sugar
        when {
            binding.rbSugar70.isChecked -> optionsList.add("70% Đường")
            binding.rbSugar50.isChecked -> optionsList.add("50% Đường")
            binding.rbSugar0.isChecked -> optionsList.add("Không đường")
            else -> optionsList.add("100% Đường")
        }

        // 3. Ice
        when {
            binding.rbIce70.isChecked -> optionsList.add("70% Đá")
            binding.rbIce50.isChecked -> optionsList.add("50% Đá")
            binding.rbIceSeparate.isChecked -> {
                unitPrice += 2000
                optionsList.add("Đá riêng (+2k)")
            }
            else -> optionsList.add("100% Đá")
        }

        // 4. Topping
        if (binding.cbToppingPearl.isChecked) {
            unitPrice += 5000
            optionsList.add("Trân châu đen")
        }
        if (binding.cbToppingJelly.isChecked) {
            unitPrice += 7000
            optionsList.add("Thạch củ năng")
        }
        if (binding.cbToppingPudding.isChecked) {
            unitPrice += 8000
            optionsList.add("Pudding trứng")
        }

        return Pair(unitPrice, optionsList.joinToString(" • "))
    }

    private fun updatePrice() {
        val (unitPrice, _) = calculateUnitPriceAndDescription()
        val total = unitPrice * quantity
        binding.btnAddToCartModal.text = "Thêm Vào Giỏ • ${total.toVndCurrency()}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DishOptionBottomSheet"
    }
}
