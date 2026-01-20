package com.location.simulator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.location.simulator.R
import com.location.simulator.data.model.LocationData
import com.location.simulator.databinding.ActivityMainBinding
import com.location.simulator.ui.dialog.AddLocationDialog
import com.location.simulator.ui.dialog.LocationDetailsDialog
import com.location.simulator.utils.GeocodingManager
import com.location.simulator.utils.LocationManager
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private lateinit var geocodingManager: GeocodingManager
    
    private var googleMap: GoogleMap? = null
    private var currentLocation: LocationData? = null
    
    // 权限请求
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                Timber.i("Fine location permission granted")
                initializeLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                Timber.i("Coarse location permission granted")
                initializeLocation()
            }
            else -> {
                Timber.i("Location permission denied")
                Toast.makeText(
                    this,
                    "位置权限被拒绝，部分功能可能无法使用",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "位置模拟器"
        
        // 初始化管理器
        locationManager = LocationManager.getInstance(this)
        geocodingManager = GeocodingManager(this)
        
        // 初始化地图
        initializeMap()
        
        // 设置点击监听器
        setupClickListeners()
        
        // 检查权限
        checkPermissions()
    }
    
    private fun initializeMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isCompassEnabled = true
            uiSettings.isMyLocationButtonEnabled = true
            uiSettings.isMapToolbarEnabled = true
            
            // 设置点击监听
            setOnMapClickListener { latLng ->
                showAddLocationDialog(latLng)
            }
            
            setOnMarkerClickListener { marker ->
                marker.tag?.let { locationData ->
                    showLocationDetailsDialog(locationData as LocationData)
                }
                true
            }
        }
        
        // 如果已经有当前位置，则显示在地图上
        currentLocation?.let {
            updateMapWithLocation(it)
        }
    }
    
    private fun setupClickListeners() {
        // 获取当前位置
        binding.fabCurrentLocation.setOnClickListener {
            getCurrentLocation()
        }
        
        // 搜索地址
        binding.fabSearch.setOnClickListener {
            showSearchDialog()
        }
        
        // 添加自定义位置
        binding.fabAdd.setOnClickListener {
            showAddLocationDialog(null)
        }
        
        // 开始模拟位置
        binding.fabStartMock.setOnClickListener {
            currentLocation?.let {
                startMockLocation(it)
            } ?: run {
                Toast.makeText(this, "请先选择或输入一个位置", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 停止模拟位置
        binding.fabStopMock.setOnClickListener {
            stopMockLocation()
        }
        
        // 打开位置设置
        binding.btnLocationSettings.setOnClickListener {
            openLocationSettings()
        }
        
        // 打开开发者选项
        binding.btnDeveloperSettings.setOnClickListener {
            openDeveloperSettings()
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasFineLocation && !hasCoarseLocation) {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                initializeLocation()
            }
        } else {
            initializeLocation()
        }
    }
    
    private fun initializeLocation() {
        if (locationManager.hasLocationPermission()) {
            if (locationManager.isLocationEnabled()) {
                getCurrentLocation()
            } else {
                binding.tvStatus.text = "位置服务未开启"
                Toast.makeText(
                    this,
                    "请开启位置服务以获取当前位置",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun getCurrentLocation() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        locationManager.getCurrentLocation(
            onSuccess = { location ->
                binding.progressBar.visibility = android.view.View.GONE
                
                // 转换为LocationData
                val locationData = LocationData.fromLocation(location, "当前位置")
                
                // 进行反向地理编码获取地址
                lifecycleScope.launch {
                    geocodingManager.reverseGeocode(
                        locationData.latitude,
                        locationData.longitude
                    )?.let { detailedLocation ->
                        currentLocation = detailedLocation
                        updateLocationInfo(detailedLocation)
                        updateMapWithLocation(detailedLocation)
                    } ?: run {
                        currentLocation = locationData
                        updateLocationInfo(locationData)
                        updateMapWithLocation(locationData)
                    }
                }
            },
            onError = { exception ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvStatus.text = "获取位置失败: ${exception.message}"
                Timber.e(exception, "Failed to get location")
            }
        )
    }
    
    private fun updateLocationInfo(locationData: LocationData) {
        binding.tvLocationName.text = locationData.name
        binding.tvCoordinates.text = String.format(
            "%.6f, %.6f",
            locationData.latitude,
            locationData.longitude
        )
        binding.tvAddress.text = locationData.getFormattedAddress()
        
        if (locationManager.isMocking()) {
            binding.tvStatus.text = "模拟位置运行中"
            binding.fabStartMock.visibility = android.view.View.GONE
            binding.fabStopMock.visibility = android.view.View.VISIBLE
        } else {
            binding.tvStatus.text = "准备就绪"
            binding.fabStartMock.visibility = android.view.View.VISIBLE
            binding.fabStopMock.visibility = android.view.View.GONE
        }
    }
    
    private fun updateMapWithLocation(locationData: LocationData) {
        googleMap?.let { map ->
            map.clear()
            
            val latLng = LatLng(locationData.latitude, locationData.longitude)
            
            // 添加标记
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(locationData.name)
                    .snippet(locationData.getFormattedAddress())
            )
            marker?.tag = locationData
            
            // 移动相机到位置
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLng, 16f)
            )
        }
    }
    
    private fun showAddLocationDialog(latLng: LatLng?) {
        AddLocationDialog.newInstance(latLng).apply {
            onLocationSaved = { locationData ->
                currentLocation = locationData
                updateLocationInfo(locationData)
                updateMapWithLocation(locationData)
                Toast.makeText(this@MainActivity, "位置已保存", Toast.LENGTH_SHORT).show()
            }
        }.show(supportFragmentManager, "add_location")
    }
    
    private fun showSearchDialog() {
        SearchDialog.newInstance().apply {
            onLocationSelected = { locationData ->
                currentLocation = locationData
                updateLocationInfo(locationData)
                updateMapWithLocation(locationData)
                Toast.makeText(this@MainActivity, "位置已选择", Toast.LENGTH_SHORT).show()
            }
        }.show(supportFragmentManager, "search_location")
    }
    
    private fun showLocationDetailsDialog(locationData: LocationData) {
        LocationDetailsDialog.newInstance(locationData).apply {
            onLocationUpdated = { updatedLocation ->
                currentLocation = updatedLocation
                updateLocationInfo(updatedLocation)
                updateMapWithLocation(updatedLocation)
            }
            onStartMocking = {
                startMockLocation(locationData)
            }
        }.show(supportFragmentManager, "location_details")
    }
    
    private fun startMockLocation(locationData: LocationData) {
        if (!isMockLocationEnabled()) {
            Toast.makeText(
                this,
                "请在开发者选项中启用模拟位置功能",
                Toast.LENGTH_LONG
            ).show()
            openDeveloperSettings()
            return
        }
        
        locationManager.startMockingLocation(locationData)
        updateLocationInfo(locationData)
        Toast.makeText(this, "已开始模拟位置", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopMockLocation() {
        locationManager.stopMockingLocation()
        updateLocationInfo(currentLocation ?: LocationData(name = "无位置", address = "", latitude = 0.0, longitude = 0.0))
        Toast.makeText(this, "已停止模拟位置", Toast.LENGTH_SHORT).show()
    }
    
    private fun openLocationSettings() {
        locationManager.openLocationSettings(this)
    }
    
    private fun openDeveloperSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
            Timber.e(e, "Failed to open developer settings")
        }
    }
    
    private fun isMockLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION,
                0
            ) == 1
        } else {
            try {
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ALLOW_MOCK_LOCATION
                ) != "0"
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_saved_locations -> {
                openSavedLocations()
                true
            }
            R.id.menu_import_locations -> {
                importLocations()
                true
            }
            R.id.menu_export_locations -> {
                exportLocations()
                true
            }
            R.id.menu_settings -> {
                openSettings()
                true
            }
            R.id.menu_help -> {
                showHelp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openSavedLocations() {
        val intent = Intent(this, SavedLocationsActivity::class.java)
        startActivity(intent)
    }
    
    private fun importLocations() {
        // 实现导入功能
        Toast.makeText(this, "导入功能开发中", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportLocations() {
        // 实现导出功能
        Toast.makeText(this, "导出功能开发中", Toast.LENGTH_SHORT).show()
    }
    
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun showHelp() {
        // 显示帮助信息
        Toast.makeText(this, "帮助文档开发中", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        // 更新状态
        currentLocation?.let {
            updateLocationInfo(it)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
    }
}