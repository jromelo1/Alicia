package com.gutigu.alicia.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY dateTimeMillis ASC")
    fun getAllAppointments(): Flow<List<AppointmentEntity>>

    @Insert
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Delete
    suspend fun deleteAppointment(appointment: AppointmentEntity)

    /** Citas aún no ocurridas — para reprogramar sus recordatorios tras un reinicio. */
    @Query("SELECT * FROM appointments WHERE dateTimeMillis > :now ORDER BY dateTimeMillis ASC")
    suspend fun getUpcoming(now: Long): List<AppointmentEntity>
}
