package com.safetylog.app.ui.profile

import android.util.Log
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
import java.util.Collections

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

/**
 * Individual profile fields that can be under active edit.
 *
 * A single global "dirty" boolean was previously used to decide whether the
 * persisted profile row should be discarded. Because it stayed `true` for the
 * whole ViewModel lifetime (including after a successful save, and across
 * profile -> auth -> profile back-stack reuse), the DB read that finishes after
 * any keystroke threw away the stored company / certification / phone values and
 * the form reopened blank. Tracking fields individually makes the merge lossless.
 */
internal enum class ProfileField { COMPANY, NAME, CERT, EMAIL, PHONE }

/**
 * Pure merge of persisted profile data with in-progress edits and session identity.
 *
 * Invariants:
 *  - A persisted value is never discarded just because some other field is dirty.
 *  - Only a field the inspector is actively editing may override persisted data.
 *  - Session identity only fills fields that are still blank and untouched.
 */
internal fun mergePersistedProfile(
    current: ProfileUiState,
    persisted: InspectorProfile?,
    session: AuthUser?,
    editedFields: Set<ProfileField>
): ProfileUiState {
    val resolvedName = persisted?.inspectorName.orEmpty().ifBlank { session?.displayName.orEmpty() }
    val resolvedEmail = persisted?.contactEmail.orEmpty().ifBlank { session?.email.orEmpty() }

    return if (persisted != null) {
        current.copy(
            companyName = if (ProfileField.COMPANY in editedFields) current.companyName else persisted.companyName,
            inspectorName = if (ProfileField.NAME in editedFields) current.inspectorName else resolvedName,
            certificationNumber = if (ProfileField.CERT in editedFields) current.certificationNumber else persisted.certificationNumber,
            contactEmail = if (ProfileField.EMAIL in editedFields) current.contactEmail else resolvedEmail,
            contactPhone = if (ProfileField.PHONE in editedFields) current.contactPhone else persisted.contactPhone,
            isLoading = false
        )
    } else {
        current.copy(
            inspectorName = if (ProfileField.NAME in editedFields) current.inspectorName else resolvedName,
            contactEmail = if (ProfileField.EMAIL in editedFields) current.contactEmail else resolvedEmail,
            isLoading = false
        )
    }
}

class ProfileViewModel(
    private val profileDao: ProfileDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private companion object {
        const val TAG = "ProfileVM"

        /** PII-safe masking so diagnostic logs never leak credentials or personal data. */
        fun mask(label: String, value: String): String =
            if (value.isBlank()) "$label=<blank>" else "$label=set(len=${value.length})"

        fun describe(s: ProfileUiState): String =
            "loading=${s.isLoading} saving=${s.isSaving} authRole=${s.authUser?.role} " +
                "${mask("company", s.companyName)} ${mask("name", s.inspectorName)} " +
                "${mask("cert", s.certificationNumber)} ${mask("email", s.contactEmail)} ${mask("phone", s.contactPhone)}"
    }

    private val editedFields: MutableSet<ProfileField> =
        Collections.synchronizedSet(mutableSetOf<ProfileField>())

    private val hasUserEdited: Boolean
        get() = synchronized(editedFields) { editedFields.isNotEmpty() }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "init: wiring state; profile load and auth observation start concurrently")
        loadProfile()
        observeAuth()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                Log.d(
                    TAG,
                    "observeAuth: emit user=${if (user == null) "null" else "present"} " +
                        "displayName=${mask("displayName", user?.displayName.orEmpty())} " +
                        "email=${mask("email", user?.email.orEmpty())} role=${user?.role} " +
                        "isLoading=${_uiState.value.isLoading} editedFields=$editedFields"
                )
                _uiState.update { current ->
                    if (current.isLoading) {
                        // Persisted data has not arrived yet; never populate text
                        // fields from the session here or the DB merge would be
                        // overwritten moments later.
                        Log.d(TAG, "observeAuth: deferred text fill (profile still loading)")
                        current.copy(authUser = user)
                    } else {
                        val canFillName = ProfileField.NAME !in editedFields &&
                            current.inspectorName.isBlank() &&
                            user?.displayName?.isNotBlank() == true
                        val canFillEmail = ProfileField.EMAIL !in editedFields &&
                            current.contactEmail.isBlank() &&
                            user?.email?.isNotBlank() == true
                        if (canFillName || canFillEmail) {
                            Log.d(TAG, "observeAuth: filling untouched blank fields nameFilled=$canFillName emailFilled=$canFillEmail")
                        }
                        current.copy(
                            authUser = user,
                            inspectorName = if (canFillName) user!!.displayName else current.inspectorName,
                            contactEmail = if (canFillEmail) user!!.email else current.contactEmail
                        )
                    }
                }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "loadProfile: begin editedFields=$editedFields")
                val prof = profileDao.getProfileOnce()
                Log.d(
                    TAG,
                    "loadProfile: db result profile=${if (prof == null) "null" else "present"} " +
                        "${mask("company", prof?.companyName.orEmpty())} " +
                        "${mask("name", prof?.inspectorName.orEmpty())} " +
                        "${mask("cert", prof?.certificationNumber.orEmpty())} " +
                        "${mask("email", prof?.contactEmail.orEmpty())} " +
                        "${mask("phone", prof?.contactPhone.orEmpty())}"
                )
                _uiState.update { current ->
                    val auth = current.authUser ?: authRepository.currentUser.value
                    if (prof != null && hasUserEdited) {
                        Log.d(
                            TAG,
                            "loadProfile: merging persisted row with in-progress edits for $editedFields " +
                                "(persisted values are never discarded)"
                        )
                    }
                    mergePersistedProfile(current, prof, auth, synchronized(editedFields) { editedFields.toSet() })
                }
                Log.d(TAG, "loadProfile: end ${describe(_uiState.value)}")
            } catch (e: Exception) {
                Log.e(TAG, "loadProfile: failed to read persisted profile", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error loading profile: ${e.message}") }
            }
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut: requested (session=${if (_uiState.value.authUser == null) "none" else "active"})")
        viewModelScope.launch {
            authRepository.signOut()
            Log.d(TAG, "signOut: completed, currentUser=${if (authRepository.currentUser.value == null) "null" else "present"}")
        }
    }

    fun onCompanyNameChanged(v: String) {
        editedFields.add(ProfileField.COMPANY)
        Log.d(TAG, "edit: company ${mask("value", v)} -> editedFields=$editedFields")
        _uiState.update { it.copy(companyName = v) }
    }

    fun onInspectorNameChanged(v: String) {
        editedFields.add(ProfileField.NAME)
        Log.d(TAG, "edit: inspectorName ${mask("value", v)} -> editedFields=$editedFields")
        _uiState.update { it.copy(inspectorName = v) }
    }

    fun onCertNumberChanged(v: String) {
        editedFields.add(ProfileField.CERT)
        Log.d(TAG, "edit: certificationNumber ${mask("value", v)} -> editedFields=$editedFields")
        _uiState.update { it.copy(certificationNumber = v) }
    }

    fun onEmailChanged(v: String) {
        editedFields.add(ProfileField.EMAIL)
        Log.d(TAG, "edit: contactEmail ${mask("value", v)} -> editedFields=$editedFields")
        _uiState.update { it.copy(contactEmail = v) }
    }

    fun onPhoneChanged(v: String) {
        editedFields.add(ProfileField.PHONE)
        Log.d(TAG, "edit: contactPhone ${mask("value", v)} -> editedFields=$editedFields")
        _uiState.update { it.copy(contactPhone = v) }
    }

    fun clearSaveSuccess() = _uiState.update { it.copy(isSavedSuccess = false) }
    fun clearErrorMessage() = _uiState.update { it.copy(errorMessage = null) }

    fun syncFromOAuth() {
        val user = _uiState.value.authUser
        if (user == null) {
            Log.w(TAG, "syncFromOAuth: ignored - no authenticated session present")
            return
        }
        Log.d(TAG, "syncFromOAuth: overwriting profile fields from session (role=${user.role})")
        editedFields.add(ProfileField.NAME)
        editedFields.add(ProfileField.EMAIL)
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
                Log.d(TAG, "saveProfile: begin ${describe(s)}")
                val profile = InspectorProfile(
                    id = 1,
                    companyName = s.companyName.trim(),
                    inspectorName = s.inspectorName.trim(),
                    certificationNumber = s.certificationNumber.trim(),
                    contactEmail = s.contactEmail.trim(),
                    contactPhone = s.contactPhone.trim()
                )
                profileDao.upsertProfile(profile)
                Log.d(TAG, "saveProfile: persisted inspector_profiles id=${profile.id}")
                if (authRepository.currentUser.value == null && s.inspectorName.isNotBlank()) {
                    Log.w(
                        TAG,
                        "saveProfile: no active session -> minting NEW auth session from profile form " +
                            "email=${mask("email", s.contactEmail)} (identity is unverified free-text, not a validated OAuth claim)"
                    )
                    authRepository.signInWithEmail(
                        displayName = s.inspectorName.trim(),
                        email = s.contactEmail.trim().ifBlank { "inspector@safetylog.app" }
                    )
                    Log.d(TAG, "saveProfile: minted session role=${authRepository.currentUser.value?.role}")
                }
                // The saved values are now the persisted truth, so every field is
                // clean again. Leaving edits flagged dirty is what previously made
                // a later reload discard the row.
                synchronized(editedFields) { editedFields.clear() }
                _uiState.update { it.copy(isSaving = false, isSavedSuccess = true, errorMessage = null) }
                Log.d(TAG, "saveProfile: success, dirty fields cleared ${describe(_uiState.value)}")
            } catch (e: Exception) {
                Log.e(TAG, "saveProfile: failed to persist credentials", e)
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
