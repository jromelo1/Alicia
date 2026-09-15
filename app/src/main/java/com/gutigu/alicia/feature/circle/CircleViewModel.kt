package com.gutigu.alicia.feature.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gutigu.alicia.CircleMember
import com.gutigu.alicia.MemberRole
import com.gutigu.alicia.data.CircleSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

data class CircleScreenState(
    val members: List<CircleMember> = emptyList(),
    val showSheet: Boolean = false,
    val editingMember: CircleMember? = null,   // null = modo "agregar nuevo"
    val circleCode: String? = null
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class CircleViewModel @Inject constructor(
    private val repository: CircleRepository,
    private val circleSessionRepository: CircleSessionRepository
) : ViewModel() {

    private val _sheetState = MutableStateFlow(false to null as CircleMember?)

    val state: StateFlow<CircleScreenState> = combine(
        repository.observeMembers(),
        _sheetState,
        circleSessionRepository.circleIdFlow
    ) { members, sheet, circleCode ->
        CircleScreenState(members = members, showSheet = sheet.first, editingMember = sheet.second, circleCode = circleCode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CircleScreenState()
    )

    // ── Acciones de la pantalla ─────────────────

    fun openAddSheet() { _sheetState.value = true to null }
    fun openEditSheet(member: CircleMember) { _sheetState.value = true to member }
    fun closeSheet() { _sheetState.value = false to null }

    fun saveMember(member: CircleMember) {
        viewModelScope.launch {
            if (member.userId.isBlank()) {
                repository.addMember(member)
            } else {
                repository.updateMember(member)
            }
        }
        closeSheet()
    }

    fun removeMember(userId: String) {
        viewModelScope.launch { repository.removeMember(userId) }
    }

    fun togglePanicNotify(member: CircleMember) {
        viewModelScope.launch {
            repository.updateMember(member.copy(notifyOnPanic = !member.notifyOnPanic))
        }
    }

    // ── Helpers públicos ────────────────────────

    /** Número de guardianes actuales (límite recomendado: 3). */
    val guardianCount get() = state.value.members.count { it.role == MemberRole.GUARDIAN }
}
