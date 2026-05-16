package com.financier.app.data.local.dao

import androidx.room.*
import com.financier.app.data.local.entity.AppSettingsEntity

@Dao
interface SettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettingsEntity)

    @Update
    suspend fun updateSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM app_settings WHERE user_id = :userId LIMIT 1")
    suspend fun getSettingsByUser(userId: Long): AppSettingsEntity?
}
