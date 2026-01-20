package com.location.simulator.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.location.simulator.R
import com.location.simulator.data.model.LocationData
import com.location.simulator.databinding.DialogAddLocationBinding
import com.location.simulator.utils.GeocodingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddLocationDialog : DialogFragment() {
    
    companion object {
        private const val ARG_LATLNG = "latlng"
        
        fun newInstance(latLng: LatLng? = null): AddLocationDialog {
            return AddLocationDialog().apply {
                arguments = Bundle().apply {
                    latLng?.let {
                        putDouble("latitude", it.latitude)
                        putDouble("longitude", it.longitude)
                    }
                }
            }
        }
    }
    
    private var _binding: DialogAddLocationBinding? = null
    private val binding get() = _binding!!
    
    private var latitude: Double? = null
    private var longitude: Double? = null
    
    var onLocationSaved: ((LocationData) -> Unit)? = null
    
    private val categories = listOf(
        "家庭", "工作", "学校", "商场", "餐厅", "医院", "公园", "酒店", "机场", "车站", "自定义"
    )
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddLocationBinding.inflate(layoutInflater)
        
        arguments?.let {
            latitude = it.getDouble("latitude", 0.0)
            longitude = it.getDouble("longitude", 0.0)
            
            if (latitude != 0.0 && longitude != 0.0) {
                binding.etLatitude.setText(latitude.toString())
                binding.etLongitude.setText(longitude.toString())
                reverseGeocode(latitude!!, longitude!!)
            }
        }
        
        // 设置分类下拉菜单
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加位置")
            .setView(binding.root)
            .setPositiveButton("保存") { _, _ ->
                saveLocation()
            }
            .setNegativeButton("取消", null)
            .create()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置按钮点击监听
        binding.btnGetCurrent.setOnClickListener {
            getCurrentLocation()
        }
        
        binding.btnSearch.setOnClickListener {
            searchAddress()
        }
        
        binding.btnClear.setOnClickListener {
            clearFields()
        }
    }
    
    private fun getCurrentLocation() {
        // 这里应该调用位置管理器获取当前位置
        // 由于是对话框，我们只显示提示
        binding.etLatitude.setText("")
        binding.etLongitude.setText("")
        binding.etAddress.setText("")
        showMessage("请在主界面获取当前位置")
    }
    
    private fun searchAddress() {
        val query = binding.etAddress.text.toString().trim()
        if (query.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                binding.progressBar.visibility = View.VISIBLE
                
                val locations = withContext(Dispatchers.IO) {
                    GeocodingManager(requireContext()).geocodeAddress(query)
                }
                
                binding.progressBar.visibility = View.GONE
                
                if (locations.isNotEmpty()) {
                    val location = locations.first()
                    binding.etLatitude.setText(location.latitude.toString())
                    binding.etLongitude.setText(location.longitude.toString())
                    binding.etName.setText(location.name)
                    
                    // 填充详细地址信息
                    location.buildingName?.let { binding.etBuilding.setText(it) }
                    location.streetNumber?.let { binding.etStreetNumber.setText(it) }
                    location.route?.let { binding.etStreet.setText(it) }
                    location.locality?.let { binding.etCity.setText(it) }
                    location.administrativeArea?.let { binding.etState.setText(it) }
                    location.country?.let { binding.etCountry.setText(it) }
                    location.postalCode?.let { binding.etPostalCode.setText(it) }
                } else {
                    showMessage("未找到地址")
                }
            }
        } else {
            showMessage("请输入地址")
        }
    }
    
    private fun reverseGeocode(latitude: Double, longitude: Double) {
        CoroutineScope(Dispatchers.Main).launch {
            binding.progressBar.visibility = View.VISIBLE
            
            val location = withContext(Dispatchers.IO) {
                GeocodingManager(requireContext()).reverseGeocode(latitude, longitude)
            }
            
            binding.progressBar.visibility = View.GONE
            
            location?.let {
                binding.etName.setText(it.name)
                binding.etAddress.setText(it.getFormattedAddress())
                
                // 填充详细地址信息
                it.buildingName?.let { building -> binding.etBuilding.setText(building) }
                it.streetNumber?.let { number -> binding.etStreetNumber.setText(number) }
                it.route?.let { street -> binding.etStreet.setText(street) }
                it.locality?.let { city -> binding.etCity.setText(city) }
                it.administrativeArea?.let { state -> binding.etState.setText(state) }
                it.country?.let { country -> binding.etCountry.setText(country) }
                it.postalCode?.let { code -> binding.etPostalCode.setText(code) }
            } ?: run {
                showMessage("无法获取地址信息")
            }
        }
    }
    
    private fun saveLocation() {
        val name = binding.etName.text.toString().trim()
        val latitudeStr = binding.etLatitude.text.toString().trim()
        val longitudeStr = binding.etLongitude.text.toString().trim()
        
        if (name.isEmpty()) {
            showMessage("请输入位置名称")
            return
        }
        
        if (latitudeStr.isEmpty() || longitudeStr.isEmpty()) {
            showMessage("请输入经纬度")
            return
        }
        
        try {
            val latitude = latitudeStr.toDouble()
            val longitude = longitudeStr.toDouble()
            
            val locationData = LocationData(
                name = name,
                address = binding.etAddress.text.toString().trim(),
                latitude = latitude,
                longitude = longitude,
                buildingName = binding.etBuilding.text.toString().trim().takeIf { it.isNotEmpty() },
                streetNumber = binding.etStreetNumber.text.toString().trim().takeIf { it.isNotEmpty() },
                route = binding.etStreet.text.toString().trim().takeIf { it.isNotEmpty() },
                locality = binding.etCity.text.toString().trim().takeIf { it.isNotEmpty() },
                administrativeArea = binding.etState.text.toString().trim().takeIf { it.isNotEmpty() },
                country = binding.etCountry.text.toString().trim().takeIf { it.isNotEmpty() },
                postalCode = binding.etPostalCode.text.toString().trim().takeIf { it.isNotEmpty() },
                phone = binding.etPhone.text.toString().trim().takeIf { it.isNotEmpty() },
                website = binding.etWebsite.text.toString().trim().takeIf { it.isNotEmpty() },
                description = binding.etDescription.text.toString().trim().takeIf { it.isNotEmpty() },
                category = binding.spinnerCategory.selectedItem.toString()
            )
            
            onLocationSaved?.invoke(locationData)
            dismiss()
            
        } catch (e: NumberFormatException) {
            showMessage("经纬度格式不正确")
        }
    }
    
    private fun clearFields() {
        binding.etName.setText("")
        binding.etAddress.setText("")
        binding.etLatitude.setText("")
        binding.etLongitude.setText("")
        binding.etBuilding.setText("")
        binding.etStreetNumber.setText("")
        binding.etStreet.setText("")
        binding.etCity.setText("")
        binding.etState.setText("")
        binding.etCountry.setText("")
        binding.etPostalCode.setText("")
        binding.etPhone.setText("")
        binding.etWebsite.setText("")
        binding.etDescription.setText("")
    }
    
    private fun showMessage(message: String) {
        binding.tvMessage.text = message
        binding.tvMessage.visibility = View.VISIBLE
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}