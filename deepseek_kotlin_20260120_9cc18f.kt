package com.location.simulator.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.google.gson.Gson
import com.location.simulator.data.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.Locale

class GeocodingManager(private val context: Context) {
    
    companion object {
        private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org"
        private const val GOOGLE_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json"
        private const val USER_AGENT = "LocationSimulator/1.0"
    }
    
    private val geocoder = Geocoder(context, Locale.getDefault())
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()
    
    private val gson = Gson()
    
    /**
     * 使用Android Geocoder进行地址解析
     */
    suspend fun geocodeAddress(query: String): List<LocationData> {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocationName(query, 10)
                addresses.mapNotNull { address ->
                    if (address.latitude != 0.0 && address.longitude != 0.0) {
                        createLocationDataFromAddress(address, query)
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Geocoding failed")
                emptyList()
            }
        }
    }
    
    /**
     * 使用Android Geocoder进行反向地理编码
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): LocationData? {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.let { address ->
                    createLocationDataFromAddress(address, null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Reverse geocoding failed")
                null
            }
        }
    }
    
    /**
     * 使用OpenStreetMap Nominatim进行地址搜索
     */
    suspend fun searchWithNominatim(query: String): List<LocationData> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$NOMINATIM_BASE_URL/search?format=json&q=${encode(query)}&limit=10"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    val nominatimResults = gson.fromJson(
                        json,
                        Array<NominatimResult>::class.java
                    )
                    
                    nominatimResults.mapNotNull { result ->
                        LocationData(
                            name = result.displayName.split(",").firstOrNull() ?: "Location",
                            address = result.displayName,
                            latitude = result.lat.toDouble(),
                            longitude = result.lon.toDouble(),
                            buildingName = result.address?.houseNumber?.let { 
                                result.address.houseNumber 
                            },
                            streetNumber = result.address?.houseNumber,
                            route = result.address?.road,
                            locality = result.address?.city ?: result.address?.town ?: result.address?.village,
                            administrativeArea = result.address?.state,
                            country = result.address?.country,
                            postalCode = result.address?.postcode
                        )
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "Nominatim search failed")
                emptyList()
            }
        }
    }
    
    /**
     * 通过街道/楼栋详细地址搜索
     */
    suspend fun searchDetailedAddress(
        building: String? = null,
        street: String? = null,
        city: String? = null,
        state: String? = null,
        country: String? = null
    ): List<LocationData> {
        return withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()
            
            building?.takeIf { it.isNotBlank() }?.let { queryParts.add(it) }
            street?.takeIf { it.isNotBlank() }?.let { queryParts.add(it) }
            city?.takeIf { it.isNotBlank() }?.let { queryParts.add(it) }
            state?.takeIf { it.isNotBlank() }?.let { queryParts.add(it) }
            country?.takeIf { it.isNotBlank() }?.let { queryParts.add(it) }
            
            if (queryParts.isEmpty()) {
                return@withContext emptyList()
            }
            
            val query = queryParts.joinToString(", ")
            geocodeAddress(query)
        }
    }
    
    private fun createLocationDataFromAddress(address: Address, originalQuery: String?): LocationData {
        return LocationData(
            name = originalQuery ?: address.featureName ?: "Location",
            address = address.getAddressLine(0) ?: "",
            latitude = address.latitude,
            longitude = address.longitude,
            buildingName = address.featureName,
            streetNumber = address.subThoroughfare,
            route = address.thoroughfare,
            locality = address.locality,
            administrativeArea = address.adminArea,
            country = address.countryName,
            postalCode = address.postalCode,
            description = address.getAddressLine(0)
        )
    }
    
    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }
    
    private data class NominatimResult(
        val place_id: Long,
        val licence: String,
        val osm_type: String,
        val osm_id: Long,
        val boundingbox: List<String>,
        val lat: String,
        val lon: String,
        val display_name: String,
        val class: String,
        val type: String,
        val importance: Double,
        val address: AddressDetails?
    )
    
    private data class AddressDetails(
        val house_number: String?,
        val road: String?,
        val city: String?,
        val town: String?,
        val village: String?,
        val state: String?,
        val country: String?,
        val postcode: String?,
        val country_code: String?
    )
}