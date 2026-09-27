package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Deterministic Save Slot Entity (3 slots max, capped well below 200 KB each).
 */
@Serializable
@Entity(tableName = "save_slots")
data class SaveSlotEntity(
    @PrimaryKey val slotIndex: Int, // 1, 2, or 3
    val schemaVersion: Int,
    val scenarioId: String,
    val seed: Int,
    val tickCount: Long,
    val positionMeters: Double,
    val speedMps: Double,
    val throttleNotch: Int,
    val reverser: Int,
    val autoBrakePercent: Float,
    val indBrakePercent: Float,
    val dynamicBrakeNotch: Int,
    val selectedLocoIndex: Int,
    val currentStationIndex: Int,
    val scenarioScore: Int,
    val inputEventsJson: String,
    val savedAtEpochMs: Long
)

/**
 * Surveyor Mode Track Spline & Scenery Placement Entity.
 */
@Serializable
@Entity(tableName = "surveyor_items")
data class SurveyorItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val isTrackSplineNode: Boolean,
    val assetId: String,
    val assetName: String,
    val gridX: Float,
    val gridZ: Float,
    val rotationDeg: Int, // Snapped to 5-degree increments
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Generated AI Rail Livery / Photography / Veo Video History Entity.
 */
@Serializable
@Entity(tableName = "generated_rail_media")
data class GeneratedRailMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaType: String, // "IMAGE_CREATE", "IMAGE_EDIT", "VEO_VIDEO"
    val prompt: String,
    val modelUsed: String,
    val aspectRatio: String,
    val base64OrUri: String,
    val statusNote: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface SimDao {
    @Query("SELECT * FROM save_slots ORDER BY slotIndex ASC")
    fun getAllSaveSlots(): Flow<List<SaveSlotEntity>>

    @Query("SELECT * FROM save_slots WHERE slotIndex = :slotIndex LIMIT 1")
    suspend fun getSaveSlot(slotIndex: Int): SaveSlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSaveSlot(slot: SaveSlotEntity)

    @Query("DELETE FROM save_slots WHERE slotIndex = :slotIndex")
    suspend fun deleteSaveSlot(slotIndex: Int)

    @Query("SELECT * FROM surveyor_items ORDER BY id ASC")
    fun getAllSurveyorItems(): Flow<List<SurveyorItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurveyorItem(item: SurveyorItemEntity): Long

    @Query("DELETE FROM surveyor_items WHERE id = :id")
    suspend fun deleteSurveyorItem(id: Int)

    @Query("DELETE FROM surveyor_items")
    suspend fun clearAllSurveyorItems()

    @Query("SELECT * FROM generated_rail_media ORDER BY createdAtMs DESC")
    fun getAllGeneratedMedia(): Flow<List<GeneratedRailMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedMedia(media: GeneratedRailMediaEntity)

    @Query("DELETE FROM generated_rail_media WHERE id = :id")
    suspend fun deleteGeneratedMedia(id: Int)
}

@Database(
    entities = [
        SaveSlotEntity::class,
        SurveyorItemEntity::class,
        GeneratedRailMediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SimDatabase : RoomDatabase() {
    abstract fun simDao(): SimDao

    companion object {
        @Volatile
        private var INSTANCE: SimDatabase? = null

        fun getInstance(context: Context): SimDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SimDatabase::class.java,
                    "ironrail_sim_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class SimRepository(private val dao: SimDao) {
    val saveSlots: Flow<List<SaveSlotEntity>> = dao.getAllSaveSlots()
    val surveyorItems: Flow<List<SurveyorItemEntity>> = dao.getAllSurveyorItems()
    val generatedMedia: Flow<List<GeneratedRailMediaEntity>> = dao.getAllGeneratedMedia()

    suspend fun getSaveSlot(slotIndex: Int): SaveSlotEntity? = dao.getSaveSlot(slotIndex)
    suspend fun saveSlot(slot: SaveSlotEntity) = dao.upsertSaveSlot(slot)
    suspend fun deleteSlot(slotIndex: Int) = dao.deleteSaveSlot(slotIndex)

    suspend fun addSurveyorItem(item: SurveyorItemEntity): Long = dao.insertSurveyorItem(item)
    suspend fun removeSurveyorItem(id: Int) = dao.deleteSurveyorItem(id)
    suspend fun clearSurveyor() = dao.clearAllSurveyorItems()

    suspend fun saveMedia(media: GeneratedRailMediaEntity) = dao.insertGeneratedMedia(media)
    suspend fun deleteMedia(id: Int) = dao.deleteGeneratedMedia(id)
}
