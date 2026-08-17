package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.warehouse.inventory.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query(
        """
        SELECT * FROM suppliers
        WHERE :query = ''
           OR name LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun search(query: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SupplierEntity?>

    @Query("SELECT COUNT(*) FROM suppliers")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(supplier: SupplierEntity): Long

    @Update
    suspend fun update(supplier: SupplierEntity)

    @Delete
    suspend fun delete(supplier: SupplierEntity)
}
