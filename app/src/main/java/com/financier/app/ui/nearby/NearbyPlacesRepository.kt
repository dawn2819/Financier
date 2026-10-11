package com.financier.app.ui.nearby

import android.location.Location
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

object NearbyPlacesRepository {
    private const val RADIUS_METERS = 5_000
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    fun findNearby(latitude: Double, longitude: Double): List<NearbyPlace> {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        val query = """
            [out:json][timeout:25];
            nwr(around:$RADIUS_METERS,$lat,$lon)["amenity"~"^(atm|bank)$"];
            out center tags;
        """.trimIndent()
        val request = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", "Financier/1.0.0 (Android nearby bank search)")
            .post(FormBody.Builder().add("data", query).build())
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("OpenStreetMap request failed (${it.code})")
            val body = it.body?.string() ?: throw IOException("Empty OpenStreetMap response")
            val elements = JsonParser.parseString(body).asJsonObject
                .getAsJsonArray("elements") ?: return emptyList()

            return elements.mapNotNull { element ->
                val item = element.asJsonObject
                val tags = item.getAsJsonObject("tags") ?: return@mapNotNull null
                val amenity = tags.get("amenity")?.asString ?: return@mapNotNull null
                val coordinates = item.getAsJsonObject("center")
                val itemLat = item.get("lat")?.asDouble ?: coordinates?.get("lat")?.asDouble
                    ?: return@mapNotNull null
                val itemLon = item.get("lon")?.asDouble ?: coordinates?.get("lon")?.asDouble
                    ?: return@mapNotNull null
                val name = tags.stringOrNull("name")
                    ?: tags.stringOrNull("brand")
                    ?: if (amenity == "atm") "ATM" else "Bank"
                val addressParts = listOfNotNull(
                    tags.stringOrNull("addr:street")?.let { street ->
                        val number = tags.stringOrNull("addr:housenumber")
                        if (number == null) street else "$street $number"
                    },
                    tags.stringOrNull("addr:suburb"),
                    tags.stringOrNull("addr:city")
                ).distinct()
                val distance = FloatArray(1)
                Location.distanceBetween(latitude, longitude, itemLat, itemLon, distance)

                NearbyPlace(
                    name = name,
                    address = addressParts.joinToString(", "),
                    kind = if (amenity == "atm") NearbyPlace.Kind.ATM else NearbyPlace.Kind.BANK,
                    latitude = itemLat,
                    longitude = itemLon,
                    distanceMeters = distance[0]
                )
            }
                .distinctBy { "${it.name.lowercase(Locale.ROOT)}:${it.latitude}:${it.longitude}" }
                .sortedBy { it.distanceMeters }
                .take(30)
        }
    }

    private fun com.google.gson.JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
}
