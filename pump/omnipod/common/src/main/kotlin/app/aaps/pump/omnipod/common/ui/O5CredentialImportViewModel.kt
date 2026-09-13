package app.aaps.pump.omnipod.common.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.pump.omnipod.common.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.common.bledriver.pod.security.SecureO5RegistrationStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** One row of the "currently installed credentials" list shown in the import screen. */
data class InstalledCredentialRow(
    val controllerId: Long,
    val source: O5RegistrationData.O5RegistrationSource
)

/** Result of the last import attempt, so the screen can show a success/error message. */
sealed class ImportResult {
    object None : ImportResult()
    data class Success(val controllerId: Long) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

/**
 * Drives a settings screen for importing an Omnipod 5 credential and viewing/removing
 * already-installed credentials. Accepts either format, auto-detected from the pasted
 * text:
 * - a `.o5keypair`-shaped JSON object (as produced by OmnipodKit's own `toJSON()` on
 *   iOS - `controllerId`/`privateKey`/`publicKey`/`intermediateCA`/`tlsCertificate`,
 *   keys hex-encoded and certs base64-encoded) - see [O5RegistrationData.fromJsonMap]
 * - the packed `"controllerId|priv|pub|ica|tls"` string format - see
 *   [O5RegistrationData.installPacked]
 *
 * Deliberately has no dosing-related functionality whatsoever - this only manages which
 * credentials [O5RegistrationData] knows about, nothing about pairing, connection, or
 * pod control.
 */
@HiltViewModel
class O5CredentialImportViewModel @Inject constructor(
    private val secureO5RegistrationStorage: SecureO5RegistrationStorage
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _importResult = MutableStateFlow<ImportResult>(ImportResult.None)
    val importResult: StateFlow<ImportResult> = _importResult

    private val _installedCredentials = MutableStateFlow<List<InstalledCredentialRow>>(emptyList())
    val installedCredentials: StateFlow<List<InstalledCredentialRow>> = _installedCredentials

    init {
        refreshInstalledCredentials()
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
        // Clear any stale result once the user starts editing again.
        if (_importResult.value != ImportResult.None) {
            _importResult.value = ImportResult.None
        }
    }

    /**
     * Attempts to parse and install [inputText]'s current value, auto-detecting whether
     * it's a `.o5keypair`-shaped JSON object or a packed credential string (see class doc).
     * On success, also persists it (encrypted) so it survives app restarts, and clears the
     * input field. On failure, leaves the input as-is so the user can correct it.
     */
    fun importCurrentInput() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) {
            _importResult.value = ImportResult.Failure("Paste a credential string first")
            return
        }

        val controllerId = O5RegistrationData.installFromText(text, O5RegistrationData.O5RegistrationSource.IMPORTED)
        if (controllerId == null) {
            _importResult.value = ImportResult.Failure(
                "Could not parse that credential - check it was copied completely"
            )
            return
        }

        val installed = O5RegistrationData.get(controllerId)
        if (installed == null) {
            // Shouldn't happen given install() just ran for this controllerId, but guard
            // anyway rather than reporting success for something that didn't actually register.
            _importResult.value = ImportResult.Failure("Import failed unexpectedly")
            return
        }

        secureO5RegistrationStorage.persistEntry(installed, O5RegistrationData.O5RegistrationSource.IMPORTED)
        _importResult.value = ImportResult.Success(controllerId)
        _inputText.value = ""
        refreshInstalledCredentials()
    }

    /** Removes a credential from both the in-memory registry and persisted storage. */
    fun removeCredential(controllerId: Long) {
        O5RegistrationData.remove(controllerId)
        secureO5RegistrationStorage.removeEntry(controllerId)
        refreshInstalledCredentials()
    }

    private fun refreshInstalledCredentials() {
        _installedCredentials.value = O5RegistrationData.allValues.mapNotNull { data ->
            O5RegistrationData.source(data.controllerId)?.let { source ->
                InstalledCredentialRow(data.controllerId, source)
            }
        }
    }
}
