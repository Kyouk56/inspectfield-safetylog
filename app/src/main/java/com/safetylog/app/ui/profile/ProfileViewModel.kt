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
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val authUser: AuthUser? = null,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val profileDao: ProfileDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    @Volatile
    private var hasUserEdited = false

    init {
        loadProfile()
        observeAuth()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { current ->
                    if (current.isLoading || hasUserEdited) {
                        current.copy(authUser = user)
                    } else {
                        current.copy(
                            authUser = user,
                            inspectorName = if (current.inspectorName.isBlank() && user?.displayName?.isNotBlank() == true) {
                                user.displayName
                            } else {
                                current.inspectorName
                            },
                            contactEmail = if (current.contactEmail.isBlank() && user?.email?.isNotBlank() == true) {
                                user.email
                            } else {
                                current.contactEmail
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val prof = profileDao.getProfileOnce()
                _uiState.update { current ->
                    if (hasUserEdited) {
                        current.copy(isLoading = false)
                    } else if (prof != null) {
                        val auth = current.authUser ?: authRepository.currentUser.value
                        val resolvedName = prof.inspectorName.ifBlank { auth?.displayName.orEmpty() }
                        val resolvedEmail = prof.contactEmail.ifBlank { auth?.email.orEmpty() }
                        current.copy(
                            companyName = prof.companyName,
                            inspectorName = resolvedName,
                            certificationNumber = prof.certificationNumber,
                            contactEmail = resolvedEmail,
                            contactPhone = prof.contactPhone,
                            isLoading = false
                        )
                    } else {
                        val auth = current.authUser ?: authRepository.currentUser.value
                        current.copy(
                            inspectorName = auth?.displayName.orEmpty(),
                            contactEmail = auth?.email.orEmpty(),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error loading profile: ${e.message}") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun onCompanyNameChanged(v: String) {
        hasUserEdited = true
        _uiState.update { it.copy(companyName = v) }
    }

    fun onInspectorNameChanged(v: String) {
        hasUserEdited = true
        _uiState.update { it.copy(inspectorName = v) }
    }

    fun onCertNumberChanged(v: String) {
        hasUserEdited = true
        _uiState.update { it.copy(certificationNumber = v) }
    }

    fun onEmailChanged(v: String) {
        hasUserEdited = true
        _uiState.update { it.copy(contactEmail = v) }
    }

    fun onPhoneChanged(v: String) {
        hasUserEdited = true
        _uiState.update { it.copy(contactPhone = v) }
    }

    fun clearSaveSuccess() = _uiState.update { it.copy(isSavedSuccess = false) }
    fun clearErrorMessage() = _uiState.update { it.copy(errorMessage = null) }

    fun syncFromOAuth() {
        val user = _uiState.value.authUser ?: return
        hasUserEdited = true
        _uiState.update { current ->
            current.copy(
                inspectorName = if (user.displayName.isNotBlank()) user.displayName else current.inspectorName,
                contactEmail = if (user.email.isNotBlank()) user.email else current.contactEmail
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSaving = true, errorMessage = null) }
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
                _uiState.update { it.copy(isSaving = false, isSavedSuccess = true, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Failed to save profile: ${e.message}") }
            }
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
