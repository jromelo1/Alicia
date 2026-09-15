package com.gutigu.alicia.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)   // usa el singleton compartido con Workers

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideCheckInHistoryDao(db: AppDatabase): CheckInHistoryDao = db.checkInHistoryDao()

    @Provides
    fun provideAppointmentDao(db: AppDatabase): AppointmentDao = db.appointmentDao()
}
