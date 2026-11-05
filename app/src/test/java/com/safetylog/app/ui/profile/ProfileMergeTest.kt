package com.safetylog.app.ui.profile

import com.safetylog.app.data.auth.AuthProvider
import com.safetylog.app.data.auth.AuthUser
import com.safetylog.app.data.model.InspectorProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the "saved credentials reopen blank" defect.
 *
 * Root cause: ProfileViewModel used one global `hasUserEdited` flag to decide
 * whether to discard the persisted row. Because that flag was set on the first
 * keystroke and never reset after a successful save, the asynchronous DB read
 * hit the discard branch and the form reopened with company / certification /
 * phone wiped, even though the row was still intact on disk.
 *
 * The fix merges field-by-field instead of discarding wholesale.
 */
class ProfileMergeTest {

    private val persisted = InspectorProfile(
        id = 1,
        companyName = "Apex Fire & Life Safety",
        inspectorName = "Marcus Vance",
        certificationNumber = "NICET-IV #894210",
        contactEmail = "marcus@apex.example",
        contactPhone = "(555) 392-1084"
    )

    private val session = AuthUser(
        userId = "auth_test",
        email = "session@apex.example",
        displayName = "Session Officer",
        provider = AuthProvider.GOOGLE
    )

    @Test
    fun `persisted row loads fully when nothing is being edited`() {
        val merged = mergePersistedProfile(
            current = ProfileUiState(isLoading = true),
            persisted = persisted,
            session = null,
            editedFields = emptySet()
        )

        assertEquals("Apex Fire & Life Safety", merged.companyName)
        assertEquals("Marcus Vance", merged.inspectorName)
        assertEquals("NICET-IV #894210", merged.certificationNumber)
        assertEquals("marcus@apex.example", merged.contactEmail)
        assertEquals("(555) 392-1084", merged.contactPhone)
        assertFalse(merged.isLoading)
    }

    @Test
    fun `editing one field does not discard the other persisted fields`() {
        // This is the exact regression: the employee starts typing their company
        // name before the DB read returns. Every other stored value must survive.
        val merged = mergePersistedProfile(
            current = ProfileUiState(isLoading = true, companyName = "Apex Fire & Life Safety Serv"),
            persisted = persisted,
            session = null,
            editedFields = setOf(ProfileField.COMPANY)
        )

        assertEquals("Apex Fire & Life Safety Serv", merged.companyName)
        assertEquals("Marcus Vance", merged.inspectorName)
        assertEquals("NICET-IV #894210", merged.certificationNumber)
        assertEquals("marcus@apex.example", merged.contactEmail)
        assertEquals("(555) 392-1084", merged.contactPhone)
        assertFalse(merged.isLoading)
    }

    @Test
    fun `in-progress edits to every field are all preserved`() {
        val merged = mergePersistedProfile(
            current = ProfileUiState(
                isLoading = true,
                companyName = "New Co",
                inspectorName = "New Name",
                certificationNumber = "NEW-CERT",
                contactEmail = "new@co.example",
                contactPhone = "555-0000"
            ),
            persisted = persisted,
            session = null,
            editedFields = setOf(
                ProfileField.COMPANY,
                ProfileField.NAME,
                ProfileField.CERT,
                ProfileField.EMAIL,
                ProfileField.PHONE
            )
        )

        assertEquals("New Co", merged.companyName)
        assertEquals("New Name", merged.inspectorName)
        assertEquals("NEW-CERT", merged.certificationNumber)
        assertEquals("new@co.example", merged.contactEmail)
        assertEquals("555-0000", merged.contactPhone)
    }

    @Test
    fun `blank persisted fields fall back to session identity`() {
        val partial = persisted.copy(inspectorName = "", contactEmail = "")

        val merged = mergePersistedProfile(
            current = ProfileUiState(isLoading = true),
            persisted = partial,
            session = session,
            editedFields = emptySet()
        )

        assertEquals("Session Officer", merged.inspectorName)
        assertEquals("session@apex.example", merged.contactEmail)
        assertEquals("Apex Fire & Life Safety", merged.companyName)
    }

    @Test
    fun `untouched blank fields are seeded from session when no row exists`() {
        val merged = mergePersistedProfile(
            current = ProfileUiState(isLoading = true),
            persisted = null,
            session = session,
            editedFields = emptySet()
        )

        assertEquals("Session Officer", merged.inspectorName)
        assertEquals("session@apex.example", merged.contactEmail)
        assertTrue(merged.companyName.isBlank())
        assertFalse(merged.isLoading)
    }

    @Test
    fun `edit already in progress is not overwritten by session seeding`() {
        val merged = mergePersistedProfile(
            current = ProfileUiState(isLoading = true, inspectorName = "Typed By Hand"),
            persisted = null,
            session = session,
            editedFields = setOf(ProfileField.NAME)
        )

        assertEquals("Typed By Hand", merged.inspectorName)
        // Email was never edited, so the session may still fill it.
        assertEquals("session@apex.example", merged.contactEmail)
    }

    // --- Save-time guards (defect: save during the pre-load blank window) ---

    @Test
    fun `pre-load ui state counts as all blank`() {
        // The form renders empty strings while isLoading is true. That state must
        // never be treated as a legitimate value to persist over a real row.
        assertTrue(ProfileUiState(isLoading = true).allFieldsBlank())
    }

    @Test
    fun `any single entered field makes the form non-blank`() {
        assertFalse(ProfileUiState(certificationNumber = "NICET-IV #894210").allFieldsBlank())
        assertFalse(ProfileUiState(contactPhone = "555-0000").allFieldsBlank())
    }

    @Test
    fun `whitespace only fields still count as blank`() {
        assertTrue(
            ProfileUiState(
                companyName = "   ",
                inspectorName = "\t",
                certificationNumber = "",
                contactEmail = " ",
                contactPhone = ""
            ).allFieldsBlank()
        )
    }

    @Test
    fun `populated persisted row is protected from a blank save`() {
        assertTrue(persisted.hasStoredContent())
    }

    @Test
    fun `empty persisted row is not protected because there is nothing to lose`() {
        val empty = InspectorProfile(
            id = 1,
            companyName = "",
            inspectorName = "",
            certificationNumber = "",
            contactEmail = "",
            contactPhone = ""
        )

        assertFalse(empty.hasStoredContent())
    }
}
