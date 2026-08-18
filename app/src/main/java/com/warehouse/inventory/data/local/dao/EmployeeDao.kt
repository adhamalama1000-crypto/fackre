package com.warehouse.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.warehouse.inventory.data.local.entity.EmployeeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {

    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<EmployeeEntity>>

    @Query(
        """
        SELECT * FROM employees
        WHERE :query = ''
           OR name LIKE '%' || :query || '%'
           OR code LIKE '%' || :query || '%'
           OR department LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun search(query: String): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): EmployeeEntity?

    @Query("SELECT COUNT(*) FROM employees")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(employee: EmployeeEntity): Long

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Delete
    suspend fun delete(employee: EmployeeEntity)
}
