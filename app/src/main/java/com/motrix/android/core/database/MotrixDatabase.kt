package com.motrix.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.motrix.android.core.database.converter.Converters
import com.motrix.android.core.database.dao.FileDao
import com.motrix.android.core.database.dao.SettingsDao
import com.motrix.android.core.database.dao.TaskDao
import com.motrix.android.core.database.dao.TrackerDao
import com.motrix.android.core.database.entity.FileEntity
import com.motrix.android.core.database.entity.SettingsEntity
import com.motrix.android.core.database.entity.TaskEntity
import com.motrix.android.core.database.entity.TrackerEntity

@Database(
    entities = [
        TaskEntity::class,
        FileEntity::class,
        SettingsEntity::class,
        TrackerEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MotrixDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun fileDao(): FileDao
    abstract fun settingsDao(): SettingsDao
    abstract fun trackerDao(): TrackerDao

    companion object {
        @Volatile
        private var INSTANCE: MotrixDatabase? = null

        fun getInstance(): MotrixDatabase {
            return INSTANCE ?: throw IllegalStateException(
                "MotrixDatabase has not been initialized. Call initialize(context) first."
            )
        }

        fun initialize(instance: MotrixDatabase) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = instance
                }
            }
        }

        fun destroyInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
