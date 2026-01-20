package com.location.simulator.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.location.simulator.data.model.LocationData

@Dao
interface LocationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationData): Long
    
    @Update
    suspend fun update(location: LocationData)
    
    @Delete
    suspend fun delete(location: LocationData)
    
    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM locations ORDER BY updated_at DESC")
    fun getAllLocations(): LiveData<List<LocationData>>
    
    @Query("SELECT * FROM locations WHERE is_favorite = 1 ORDER BY updated_at DESC")
    fun getFavoriteLocations(): LiveData<List<LocationData>>
    
    @Query("SELECT * FROM locations WHERE category = :category ORDER BY updated_at DESC")
    fun getLocationsByCategory(category: String): LiveData<List<LocationData>>
    
    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: Long): LocationData?
    
    @Query("SELECT * FROM locations WHERE name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%'")
    fun searchLocations(query: String): LiveData<List<LocationData>>
    
    @Query("UPDATE locations SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)
    
    @Query("SELECT * FROM locations ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastLocation(): LocationData?
}