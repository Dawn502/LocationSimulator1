package com.location.simulator.data.model

import android.location.Location
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.gms.maps.model.LatLng
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
@Entity(tableName = "locations")
data class LocationData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "address")
    val address: String,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    
    @ColumnInfo(name = "altitude")
    val altitude: Double = 0.0,
    
    @ColumnInfo(name = "accuracy")
    val accuracy: Float = 10f,
    
    @ColumnInfo(name = "building_name")
    val buildingName: String? = null,
    
    @ColumnInfo(name = "street_number")
    val streetNumber: String? = null,
    
    @ColumnInfo(name = "route")
    val route: String? = null,
    
    @ColumnInfo(name = "locality")
    val locality: String? = null,
    
    @ColumnInfo(name = "administrative_area")
    val administrativeArea: String? = null,
    
    @ColumnInfo(name = "country")
    val country: String? = null,
    
    @ColumnInfo(name = "postal_code")
    val postalCode: String? = null,
    
    @ColumnInfo(name = "phone")
    val phone: String? = null,
    
    @ColumnInfo(name = "website")
    val website: String? = null,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "category")
    val category: String = "Custom"
) : Parcelable {
    
    companion object {
        fun fromLocation(location: Location, name: String = "Current Location"): LocationData {
            return LocationData(
                name = name,
                address = "",
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy
            )
        }
        
        fun fromLatLng(latLng: LatLng, name: String = "Location"): LocationData {
            return LocationData(
                name = name,
                address = "",
                latitude = latLng.latitude,
                longitude = latLng.longitude
            )
        }
    }
    
    fun toLocation(): Location {
        return Location("custom").apply {
            latitude = this@LocationData.latitude
            longitude = this@LocationData.longitude
            altitude = this@LocationData.altitude
            accuracy = this@LocationData.accuracy
            time = System.currentTimeMillis()
        }
    }
    
    fun toLatLng(): LatLng {
        return LatLng(latitude, longitude)
    }
    
    fun getFormattedAddress(): String {
        val parts = mutableListOf<String>()
        
        if (!buildingName.isNullOrEmpty()) {
            parts.add(buildingName!!)
        }
        
        if (!streetNumber.isNullOrEmpty() && !route.isNullOrEmpty()) {
            parts.add("$streetNumber $route")
        } else if (!route.isNullOrEmpty()) {
            parts.add(route!!)
        }
        
        if (!locality.isNullOrEmpty()) {
            parts.add(locality!!)
        }
        
        if (!administrativeArea.isNullOrEmpty()) {
            parts.add(administrativeArea!!)
        }
        
        if (!country.isNullOrEmpty()) {
            parts.add(country!!)
        }
        
        if (!postalCode.isNullOrEmpty()) {
            parts.add(postalCode!!)
        }
        
        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            address
        }
    }
    
    fun getFormattedDate(): String {
        val date = Date(createdAt)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return format.format(date)
    }
    
    override fun toString(): String {
        return "$name - ${getFormattedAddress()}"
    }
}