package com.safetylog.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safetylog.app.data.dao.ProfileDao
import com.safetylog.app.data.model.InspectorProfile
import com.safetylog.app.data.auth.AuthRepository
import com.safetylog.app.data.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val companyName: String = "",
    val inspectorName: String = "",
    val certificationNumber: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val isSavedSuccess: Boolean = false,
    val isLoading: Boolean = true,
    val authUser: AuthUser? = null
)

class ProfileViewModel(
    private val profileDao: ProfileDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        observeAuth()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(authUser = user) }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val prof = profileDao.getProfileOnce()
            if (prof != null) {
                val cleanedEmail = if (prof.contactEmail.contains("apexfire") || prof.contactEmail.isBlank()) "jdesrosiers88@gmail.com" else prof.contactEmail
                val cleanedName = if (prof.inspectorName.contains("Safety Inspector") || prof.inspectorName.isBlank()) "J. Desrosiers" else prof.inspectorName
                _uiState.update {
                    it.copy(
                        companyName = prof.companyName,
                        inspectorName = cleanedName,
                        certificationNumber = prof.certificationNumber,
                        contactEmail = cleanedEmail,
                        contactPhone = prof.contactPhone,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        inspectorName = "J. Desrosiers",
                        contactEmail = "jdesrosiers88@gmail.com",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun onCompanyNameChanged(v: String) = _uiState.update { it.copy(companyName = v) }
    fun onInspectorNameChanged(v: String) = _uiState.update { it.copy(inspectorName = v) }
    fun onCertNumberChanged(v: String) = _uiState.update { it.copy(certificationNumber = v) }
    fun onEmailChanged(v: String) = _uiState.update { it.copy(contactEmail = v) }
    fun onPhoneChanged(v: String) = _uiState.update { it.copy(contactPhone = v) }

    fun syncFromOAuth() {
        val user = _uiState.value.authUser ?: return
        _uiState.update { current ->
            current.copy(
                inspectorName = if (user.displayName.isNotBlank()) user.displayName else current.inspectorName,
                contactEmail = if (user.email.isNotBlank()) user.email else current.contactEmail
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val s = _uiState.value
            val profile = InspectorProfile(
                id = 1,
                companyName = s.companyName.trim(),
                inspectorName = s.inspectorName.trim(),
                certificationNumber = s.certificationNumber.trim(),
                contactEmail = s.contactEmail.trim(),
                contactPhone = s.contactPhone.trim()
            )
            profileDao.upsertProfile(profile)
            _uiState.update { it.copy(isSavedSuccess = true) }
        }
    }

    class Factory(
        private val profileDao: ProfileDao,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(profileDao, authRepository) as T
        }
    }
}
