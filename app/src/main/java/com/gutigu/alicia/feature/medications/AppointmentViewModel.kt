package com.gutigu.alicia.feature.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.data.AppointmentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppointmentViewModel @Inject constructor(
    private val repository: AppointmentRepository
) : ViewModel() {

    val appointments = repository.appointments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addAppointment(
        doctorName: String,
        specialty: String,
        location: String,
        note: String,
        dateTimeMillis: Long
    ) {
        viewModelScope.launch {
            repository.addAppointment(doctorName, specialty, location, note, dateTimeMillis)
        }
    }

    fun deleteAppointment(appointment: AppointmentEntity) {
        viewModelScope.launch { repository.deleteAppointment(appointment) }
    }
}
