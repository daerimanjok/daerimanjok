package app.daerimanjok.feature.daeri.record

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A point on the recorded route. Position is null until navigation has reported one. */
@Entity(tableName = "route_points", indices = [Index("tripId")])
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val atMs: Long,
    val lat: Double?,
    val lon: Double?,
    val speedKmh: Float,
    val limitKmh: Int?,
    /** Road name where known (demo drive); real navigation doesn't report it. */
    val road: String?,
)

@Entity(tableName = "trip_events", indices = [Index("tripId")])
data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val type: String,
    val atMs: Long,
    val speedKmh: Float,
    val limitKmh: Int?,
    val endMs: Long?,
    val maxKmh: Float?,
) {
    fun toEvent() = TripEvent(EventType.valueOf(type), atMs, speedKmh, limitKmh, endMs, maxKmh)

    companion object {
        fun of(tripId: Long, e: TripEvent) = EventRow(
            tripId = tripId, type = e.type.name, atMs = e.atMs, speedKmh = e.speedKmh,
            limitKmh = e.limitKmh, endMs = e.endMs, maxKmh = e.maxKmh,
        )
    }
}

@Dao
interface TripDao {
    @Insert suspend fun insertPoints(points: List<RoutePoint>)
    @Insert suspend fun insertEvents(events: List<EventRow>)

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY atMs")
    fun points(tripId: Long): Flow<List<RoutePoint>>

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY atMs")
    suspend fun pointsNow(tripId: Long): List<RoutePoint>

    @Query("SELECT * FROM trip_events WHERE tripId = :tripId ORDER BY atMs")
    fun events(tripId: Long): Flow<List<EventRow>>

    @Query("DELETE FROM route_points WHERE tripId = :tripId")
    suspend fun deletePoints(tripId: Long)

    @Query("DELETE FROM trip_events WHERE tripId = :tripId")
    suspend fun deleteEvents(tripId: Long)

    /** Leftovers of trips that are no longer the current one (e.g. app data from a crash). */
    @Query("DELETE FROM route_points WHERE tripId != :keep")
    suspend fun deleteOtherPoints(keep: Long)

    @Query("DELETE FROM trip_events WHERE tripId != :keep")
    suspend fun deleteOtherEvents(keep: Long)

    @Query("DELETE FROM route_points")
    suspend fun deleteAllPoints()

    @Query("DELETE FROM trip_events")
    suspend fun deleteAllEvents()
}

/** On-device only: the route and events of the current trip. Wiped with the report. */
@Database(entities = [RoutePoint::class, EventRow::class], version = 1, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {
    abstract fun trips(): TripDao

    companion object {
        fun create(context: Context): TripDatabase =
            Room.databaseBuilder(context.applicationContext, TripDatabase::class.java, "trips.db").build()
    }
}
