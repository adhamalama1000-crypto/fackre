package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.warehouse.inventory.data.local.entity.WarehouseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WarehouseDao {

    @Query("SELECT * FROM warehouses ORDER BY isDefault DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses ORDER BY isDefault DESC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<WarehouseEntity>

    @Query(
        """
        SELECT * FROM warehouses
        WHERE :query = ''
           OR name LIKE '%' || :query || '%'
           OR code LIKE '%' || :query || '%'
           OR location LIKE '%' || :query || '%'
        ORDER BY isDefault DESC, name COLLATE NOCASE ASC
        """
    )
    fun search(query: String): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WarehouseEntity?

    @Query("SELECT * FROM warehouses WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): WarehouseEntity?

    @Query("SELECT * FROM warehouses WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): WarehouseEntity?

    @Query("SELECT COUNT(*) FROM warehouses")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM warehouses")
    fun observeCount(): Flow<Int>

    /** Clears the default flag everywhere; call inside a transaction before setting a new one. */
    @Query("UPDATE warehouses SET isDefault = 0, updatedAt = :now, synced = 0 WHERE isDefault = 1")
    suspend fun clearDefault(now: Long)

    @Query("UPDATE warehouses SET isDefault = 1, updatedAt = :now, synced = 0 WHERE id = :id")
    suspend fun markDefault(id: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(warehouse: WarehouseEntity): Long

    @Update
    suspend fun update(warehouse: WarehouseEntity)

    @Delete
    suspend fun delete(warehouse: WarehouseEntity)
}
