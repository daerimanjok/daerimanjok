package app.daerimanjok.feature.daeri

import android.content.Context
import app.daerimanjok.core.data.SecretBox
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.daeriStore by preferencesDataStore(name = "daeri")

/**
 * On-device only. Nothing here is ever sent off the car.
 * The owner setup (with the PIN hash) is encrypted with a Keystore key; the trip isn't secret
 * from the car itself but is only shown behind the PIN.
 */
class DaeriStore(context: Context, private val box: SecretBox = SecretBox()) {

    private val store = context.applicationContext.daeriStore
    private val setupKey = stringPreferencesKey("setup")
    private val tripKey = stringPreferencesKey("trip")

    suspend fun loadSetup(): OwnerSetup = runCatching {
        store.data.first()[setupKey]?.let { stored ->
            // Older installs saved the setup unencrypted; it's re-saved encrypted on the next change.
            val json = box.decrypt(stored) ?: stored.takeIf { it.startsWith("{") }
            json?.let { decodeSetup(JSONObject(it)) }
        }
    }.getOrNull() ?: OwnerSetup()

    suspend fun saveSetup(setup: OwnerSetup) {
        store.edit { it[setupKey] = box.encrypt(encodeSetup(setup).toString()) }
    }

    suspend fun loadTrip(): DaeriTrip =
        runCatching { store.data.first()[tripKey]?.let { decodeTrip(JSONObject(it)) } }.getOrNull() ?: DaeriTrip()

    suspend fun saveTrip(trip: DaeriTrip) {
        store.edit { it[tripKey] = encodeTrip(trip).toString() }
    }

    private fun encodeSetup(s: OwnerSetup) = JSONObject().apply {
        putOpt("pinHash", s.pinHash)
        putOpt("pinSalt", s.pinSalt)
        s.destination?.let { put("destination", encodePlace(it)) }
        put("parkingSpot", s.parkingSpot)
        put("note", s.note)
        put("contact", s.contact)
    }

    private fun decodeSetup(o: JSONObject) = OwnerSetup(
        pinHash = o.optStringOrNull("pinHash"),
        pinSalt = o.optStringOrNull("pinSalt"),
        destination = o.optJSONObject("destination")?.let(::decodePlace),
        parkingSpot = o.optString("parkingSpot", ""),
        note = o.optString("note", ""),
        contact = o.optString("contact", ""),
    )

    private fun encodePlace(p: Place) = JSONObject().apply {
        put("kind", p.kind.name)
        put("name", p.name)
        put("address", p.address)
        put("lat", p.latitude)
        put("lon", p.longitude)
        put("poiId", p.poiId)
        put("poiSubId", p.poiSubId)
    }

    private fun decodePlace(o: JSONObject) = Place(
        kind = runCatching { PlaceKind.valueOf(o.getString("kind")) }.getOrDefault(PlaceKind.OTHER),
        name = o.optString("name", ""),
        address = o.optString("address", ""),
        latitude = o.getDouble("lat"),
        longitude = o.getDouble("lon"),
        poiId = o.optString("poiId", "0"),
        poiSubId = o.optString("poiSubId", "0"),
    )

    private fun encodeTrip(t: DaeriTrip) = JSONObject().apply {
        put("phase", t.phase.name)
        putOpt("startAddress", t.startAddress)
        t.destination?.let { put("destination", encodePlace(it)) }
        put("parkingSpot", t.parkingSpot)
        putOpt("arrivedAtMs", t.arrivedAtMs)
        put("demo", t.demo)
        t.stats?.let { s ->
            put("stats", JSONObject().apply {
                put("startedAtMs", s.startedAtMs)
                putOpt("endedAtMs", s.endedAtMs)
                putOpt("startOdometerKm", s.startOdometerKm?.toDouble())
                putOpt("lastOdometerKm", s.lastOdometerKm?.toDouble())
                put("estimatedDistanceKm", s.estimatedDistanceKm)
                put("topSpeedKmh", s.topSpeedKmh.toDouble())
                put("movingMs", s.movingMs)
                put("hardBrakes", s.hardBrakes)
                put("hardAccels", s.hardAccels)
                put("overspeeds", s.overspeeds)
                put("driverDoorOpens", s.driverDoorOpens)
                putOpt("arrivedAt", s.arrivedAt)
                putOpt("lastParkedAtMs", s.lastParkedAtMs)
            })
        }
    }

    private fun decodeTrip(o: JSONObject): DaeriTrip {
        val stats = o.optJSONObject("stats")?.let { s ->
            TripStats(
                startedAtMs = s.getLong("startedAtMs"),
                endedAtMs = s.optLongOrNull("endedAtMs"),
                startOdometerKm = s.optDoubleOrNull("startOdometerKm")?.toFloat(),
                lastOdometerKm = s.optDoubleOrNull("lastOdometerKm")?.toFloat(),
                estimatedDistanceKm = s.optDouble("estimatedDistanceKm", 0.0),
                topSpeedKmh = s.optDouble("topSpeedKmh", 0.0).toFloat(),
                movingMs = s.optLong("movingMs", 0),
                hardBrakes = s.optInt("hardBrakes", 0),
                hardAccels = s.optInt("hardAccels", 0),
                overspeeds = s.optInt("overspeeds", 0),
                driverDoorOpens = s.optInt("driverDoorOpens", 0),
                arrivedAt = s.optStringOrNull("arrivedAt"),
                lastParkedAtMs = s.optLongOrNull("lastParkedAtMs"),
            )
        }
        return DaeriTrip(
            phase = runCatching { DaeriPhase.valueOf(o.getString("phase")) }.getOrDefault(DaeriPhase.IDLE),
            stats = stats,
            startAddress = o.optStringOrNull("startAddress"),
            destination = o.optJSONObject("destination")?.let(::decodePlace),
            parkingSpot = o.optString("parkingSpot", ""),
            arrivedAtMs = o.optLongOrNull("arrivedAtMs"),
            demo = o.optBoolean("demo", false),
        )
    }

    private fun JSONObject.optStringOrNull(k: String) = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.optLongOrNull(k: String) = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optDoubleOrNull(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
}
