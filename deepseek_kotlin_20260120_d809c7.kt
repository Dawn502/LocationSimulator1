package com.location.simulator

import android.app.Application
import android.content.Context
import android.util.Log
import com.location.simulator.data.AppDatabase
import com.location.simulator.data.repository.LocationRepository
import com.location.simulator.utils.LocationManager
import timber.log.Timber

class LocationSimulatorApp : Application() {
    
    companion object {
        lateinit var instance: LocationSimulatorApp
            private set
        
        lateinit var locationManager: LocationManager
            private set
        
        lateinit var locationRepository: LocationRepository
            private set
        
        lateinit var database: AppDatabase
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 初始化数据库
        database = AppDatabase.getDatabase(this)
        
        // 初始化位置管理器
        locationManager = LocationManager.getInstance(this)
        
        // 初始化仓库
        locationRepository = LocationRepository(database.locationDao())
        
        Timber.i("LocationSimulatorApp initialized")
    }
    
    fun getAppContext(): Context = applicationContext
}