package com.motrix.android.core.database.converter

import androidx.room.TypeConverter
import com.motrix.android.core.engine.model.TaskState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromTaskState(state: TaskState): String {
        return state.name
    }

    @TypeConverter
    fun toTaskState(value: String): TaskState {
        return try {
            TaskState.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TaskState.ERROR
        }
    }
}
