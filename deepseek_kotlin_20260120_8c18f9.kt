package com.location.simulator.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.location.simulator.R
import com.location.simulator.data.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit

class LocationManager private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: LocationManager? = null
        
        fun getInstance(context: Context): LocationManager {
            return INSTANCE ?: synchronized(this) {
                val instance = LocationManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val appContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(appContext)
    
    private var locationCallback: LocationCallback? = null
    private var currentLocation: Location? = null
    private var onLocationUpdate: ((Location) -> Unit)? = null
    
    private val mockLocations = mutableListOf<LocationData>()
    private var isMocking = false
    
    /**
     * 获取当前真实位置
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onSuccess: (Location) -> Unit, onError: (Exception) -> Unit) {
        if (!hasLocationPermission()) {
            onError(Exception("Location permission not granted"))
            return
        }
        
        if (!isLocationEnabled()) {
            onError(Exception("Location service not enabled"))
            return
        }
        
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    onSuccess(it)
                } ?: run {
                    // 如果获取不到最近位置，则请求位置更新
                    requestSingleLocationUpdate(onSuccess, onError)
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }
    
    @SuppressLint("MissingPermission")
    private fun requestSingleLocationUpdate(
        onSuccess: (Location) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(10)
        ).build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.firstOrNull()?.let {
                    currentLocation = it
                    onSuccess(it)
                }
                locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            }
        }
        
        locationCallback?.let {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                it,
                Looper.getMainLooper()
            )
        }
    }
    
    /**
     * 开始持续位置更新
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(callback: (Location) -> Unit) {
        if (!hasLocationPermission()) {
            Timber.e("Location permission not granted")
            return
        }
        
        if (!isLocationEnabled()) {
            Timber.e("Location service not enabled")
            return
        }
        
        onLocationUpdate = callback
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(5)
        )
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(2))
            .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(10))
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    currentLocation = location
                    onLocationUpdate?.invoke(location)
                }
            }
        }
        
        locationCallback?.let {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                it,
                Looper.getMainLooper()
            )
        }
    }
    
    /**
     * 停止位置更新
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        onLocationUpdate = null
    }
    
    /**
     * 检查位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查位置服务是否启用
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = 
            appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * 打开位置设置
     */
    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        context.startActivity(intent)
    }
    
    /**
     * 开始模拟位置
     */
    fun startMockingLocation(locationData: LocationData) {
        mockLocations.add(locationData)
        isMocking = true
        startMockService(locationData)
    }
    
    /**
     * 停止模拟位置
     */
    fun stopMockingLocation() {
        isMocking = false
        stopMockService()
    }
    
    private fun startMockService(locationData: LocationData) {
        val intent = Intent(appContext, MockLocationService::class.java).apply {
            putExtra("latitude", locationData.latitude)
            putExtra("longitude", locationData.longitude)
            putExtra("name", locationData.name)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }
    
    private fun stopMockService() {
        val intent = Intent(appContext, MockLocationService::class.java).apply {
            action = "STOP"
        }
        appContext.stopService(intent)
    }
    
    fun isMocking(): Boolean = isMocking
    fun getCurrentMockLocation(): LocationData? = mockLocations.lastOrNull()
}

class MockLocationService : Service() {
    
    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }
    
    private val binder = LocalBinder()
    private lateinit var locationManager: android.location.LocationManager
    private var isMocking = false
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_mock_service"
        private const val CHANNEL_NAME = "Location Mock Service"
    }
    
    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        createNotificationChannel()
        Timber.i("MockLocationService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopMocking()
                stopSelf()
            }
            else -> {
                val latitude = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
                val longitude = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0
                val name = intent?.getStringExtra("name") ?: "Mock Location"
                
                startForegroundService()
                startMocking(latitude, longitude, name)
            }
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service for mocking location"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundService() {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("位置模拟运行中")
            .setContentText("正在提供模拟位置")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    @SuppressLint("MissingPermission")
    private fun startMocking(latitude: Double, longitude: Double, name: String) {
        if (!isMockLocationEnabled()) {
            Timber.e("Mock location is not enabled in developer options")
            return
        }
        
        try {
            // 移除旧的测试提供者
            removeTestProviders()
            
            // 添加GPS测试提供者
            locationManager.addTestProvider(
                android.location.LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, 0, 5
            )
            
            // 设置测试提供者状态
            locationManager.setTestProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER,
                true
            )
            
            // 创建模拟位置
            val mockLocation = android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                this.time = System.currentTimeMillis()
                this.accuracy = 10f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
            }
            
            // 注入位置
            locationManager.setTestProviderLocation(
                android.location.LocationManager.GPS_PROVIDER,
                mockLocation
            )
            
            isMocking = true
            Timber.i("Started mocking location: $latitude, $longitude ($name)")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start mocking location")
        }
    }
    
    private fun stopMocking() {
        try {
            removeTestProviders()
            isMocking = false
            Timber.i("Stopped mocking location")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop mocking location")
        }
    }
    
    private fun removeTestProviders() {
        try {
            val providers = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER
            )
            
            providers.forEach { provider ->
                if (locationManager.getProvider(provider) != null) {
                    locationManager.removeTestProvider(provider)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove test providers")
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
    
    override fun onDestroy() {
        stopMocking()
        super.onDestroy()
        Timber.i("MockLocationService destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
}