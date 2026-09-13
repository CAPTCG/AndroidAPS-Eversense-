package app.aaps.pump.omnipod.common.bledriver.comm.pair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Covers [O5RegistrationData.installFromText] - the shared entry point used by the paste
 * import screen and by the build-time credential seeding. Manipulates the process-wide
 * registry, so it is cleaned up in @BeforeEach/@AfterEach (same discipline as
 * [app.aaps.pump.omnipod.common.ui.O5CredentialImportViewModelTest]).
 */
class O5RegistrationDataTest {

    private fun b64(vararg bytes: Int): String =
        Base64.getEncoder().encodeToString(bytes.map { it.toByte() }.toByteArray())

    private fun packed(controllerId: Long): String =
        "$controllerId|${b64(1, 2, 3, 4)}|${b64(5, 6, 7, 8)}||"

    private fun json(controllerId: Long): String =
        """{"controllerId":"$controllerId","privateKey":"01020304","publicKey":"05060708",""" +
            """"intermediateCA":"${b64(9)}","tlsCertificate":"${b64(10)}"}"""

    @BeforeEach
    @AfterEach
    fun clearRegistry() {
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
    }

    @Test
    fun `installFromText installs a packed credential with the given source and returns its controllerId`() {
        val controllerId = 12345678L

        val result = O5RegistrationData.installFromText(packed(controllerId), O5RegistrationData.O5RegistrationSource.BUILT_IN)

        assertThat(result).isEqualTo(controllerId)
        assertThat(O5RegistrationData.contains(controllerId)).isTrue()
        assertThat(O5RegistrationData.source(controllerId)).isEqualTo(O5RegistrationData.O5RegistrationSource.BUILT_IN)
    }

    @Test
    fun `installFromText installs an o5keypair JSON credential`() {
        val controllerId = 87654321L

        val result = O5RegistrationData.installFromText(json(controllerId), O5RegistrationData.O5RegistrationSource.BUILT_IN)

        assertThat(result).isEqualTo(controllerId)
        assertThat(O5RegistrationData.contains(controllerId)).isTrue()
    }

    @Test
    fun `installFromText returns null and installs nothing for blank text`() {
        assertThat(O5RegistrationData.installFromText("   ", O5RegistrationData.O5RegistrationSource.BUILT_IN)).isNull()
        assertThat(O5RegistrationData.allValues).isEmpty()
    }

    @Test
    fun `installFromText returns null for a malformed packed string`() {
        assertThat(O5RegistrationData.installFromText("not a credential", O5RegistrationData.O5RegistrationSource.BUILT_IN)).isNull()
        assertThat(O5RegistrationData.allValues).isEmpty()
    }

    @Test
    fun `installFromText returns null for JSON missing a required field`() {
        val incomplete = """{"controllerId":"1","privateKey":"01"}"""

        assertThat(O5RegistrationData.installFromText(incomplete, O5RegistrationData.O5RegistrationSource.BUILT_IN)).isNull()
        assertThat(O5RegistrationData.allValues).isEmpty()
    }

    @Test
    fun `installOneFromPool installs exactly one of several blank-line-separated credentials`() {
        val ids = listOf(111L, 222L, 333L)
        val pool = ids.joinToString("\n\n") { packed(it) }

        val result = O5RegistrationData.installOneFromPool(pool, O5RegistrationData.O5RegistrationSource.BUILT_IN)

        assertThat(result).isIn(ids)
        // Exactly one credential is installed, not the whole pool.
        assertThat(O5RegistrationData.allValues).hasSize(1)
        assertThat(O5RegistrationData.contains(result!!)).isTrue()
    }

    @Test
    fun `installOneFromPool skips a malformed entry and installs a valid one`() {
        val pool = "this is not a credential\n\n${packed(4242L)}"

        val result = O5RegistrationData.installOneFromPool(pool, O5RegistrationData.O5RegistrationSource.BUILT_IN)

        assertThat(result).isEqualTo(4242L)
        assertThat(O5RegistrationData.allValues).hasSize(1)
    }

    @Test
    fun `installOneFromPool with a single credential installs that one`() {
        val result = O5RegistrationData.installOneFromPool(packed(9090L), O5RegistrationData.O5RegistrationSource.BUILT_IN)

        assertThat(result).isEqualTo(9090L)
    }

    @Test
    fun `installOneFromPool returns null and installs nothing when no entry parses`() {
        val pool = "garbage\n\nmore garbage\n\n"

        assertThat(O5RegistrationData.installOneFromPool(pool, O5RegistrationData.O5RegistrationSource.BUILT_IN)).isNull()
        assertThat(O5RegistrationData.allValues).isEmpty()
    }
}
