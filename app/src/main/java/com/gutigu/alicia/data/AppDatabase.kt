package com.gutigu.alicia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        MedicationEntity::class,
        CheckInHistoryEntity::class,
        AppointmentEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun medicationDao(): MedicationDao
    abstract fun checkInHistoryDao(): CheckInHistoryDao
    abstract fun appointmentDao(): AppointmentDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Devuelve la instancia singleton de la base de datos.
         * Usado tanto por Hilt (DatabaseModule) como por Workers que no
         * pueden recibir inyección directa.
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alicia_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
