package app.aaps.pump.omnipod.common.bledriver.comm.pair

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * [O5KeyExchange] - real P-256/SHA-256 crypto is exercised via real generators (not hand-
 * verified against an external test vector, unlike [O5CertificateStoreTest]'s DER round-trip
 * or [PulseLogEntryTest]'s openomni golden value - no equivalent published vector was found
 * for this KDF). Tests instead check structural properties: sizes, byte-layout composition,
 * and round-trip behavior (e.g. increment-then-check-against-original), which don't depend on
 * knowing the "correct" hash output in advance.
 */
class O5KeyExchangeTest {

    private val aapsLogger = AAPSLoggerTest()
    private val controllerIdData = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    private fun newExchange(
        keyGenerator: P256KeyGenerator = P256KeyGenerator(),
        randomByteGenerator: RandomByteGenerator = RandomByteGenerator()
    ) = O5KeyExchange(aapsLogger, keyGenerator, randomByteGenerator, controllerIdData)

    /** ECDH requires a genuine point on the curve - a real generated public key, standing in
     *  for the pod's, unlike an arbitrary byte array which fails curve validation. */
    private fun realPublicKey(): ByteArray {
        val kg = P256KeyGenerator()
        return kg.publicFromPrivate(kg.generatePrivateKey())
    }

    @Test
    fun `constructor generates a correctly-sized nonce and public key`() {
        val ke = newExchange()

        assertThat(ke.pdmNonce).hasLength(O5KeyExchange.NONCE_SIZE)
        assertThat(ke.pdmPublic).hasLength(O5KeyExchange.PUBLIC_KEY_SIZE)
    }

    @Test
    fun `constructor throws when the random nonce generator returns the wrong size`() {
        val randomByteGenerator = spy(RandomByteGenerator())
        doReturn(ByteArray(4)).whenever(randomByteGenerator).nextBytes(org.mockito.kotlin.any())

        assertThrows(PairingException::class.java) {
            newExchange(randomByteGenerator = randomByteGenerator)
        }
    }

    @Test
    fun `constructor throws when the key generator returns a wrong-size public key`() {
        val keyGenerator = spy(P256KeyGenerator())
        doReturn(ByteArray(10)).whenever(keyGenerator).publicFromPrivate(org.mockito.kotlin.any())

        assertThrows(PairingException::class.java) {
            newExchange(keyGenerator = keyGenerator)
        }
    }

    @Test
    fun `o5UpdatePodPublicData throws for a wrong-size payload`() {
        val ke = newExchange()

        assertThrows(MessageIOException::class.java) {
            ke.o5UpdatePodPublicData(ByteArray(10))
        }
    }

    @Test
    fun `o5UpdatePodPublicData splits the payload and derives 16-byte conf and ltk`() {
        val ke = newExchange()
        val podPublic = realPublicKey()
        val podNonce = ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 100).toByte() }

        ke.o5UpdatePodPublicData(podPublic + podNonce)

        assertThat(ke.podPublic).isEqualTo(podPublic)
        assertThat(ke.podNonce).isEqualTo(podNonce)
        assertThat(ke.conf).hasLength(O5KeyExchange.CMAC_SIZE)
        assertThat(ke.ltk).hasLength(O5KeyExchange.CMAC_SIZE)
        // conf and ltk are two different 16-byte slices of the same 32-byte SHA-256 digest.
        assertThat(ke.conf).isNotEqualTo(ke.ltk)
    }

    @Test
    fun `getSPSNonce builds the direction-prefixed 13-byte nonce from both parties' first 6 bytes`() {
        val ke = newExchange()
        ke.o5UpdatePodPublicData(realPublicKey() + ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 1).toByte() })

        val writeNonce = ke.getSPSNonce(O5KeyExchange.Direction.WRITE)
        val readNonce = ke.getSPSNonce(O5KeyExchange.Direction.READ)

        assertThat(writeNonce).hasLength(13)
        assertThat(writeNonce[0]).isEqualTo(0x01.toByte())
        assertThat(writeNonce.copyOfRange(1, 7)).isEqualTo(ke.pdmNonce.copyOfRange(0, 6))
        assertThat(writeNonce.copyOfRange(7, 13)).isEqualTo(ke.podNonce.copyOfRange(0, 6))

        assertThat(readNonce).hasLength(13)
        assertThat(readNonce[0]).isEqualTo(0x02.toByte())
        assertThat(readNonce.copyOfRange(1, 7)).isEqualTo(ke.podNonce.copyOfRange(0, 6))
        assertThat(readNonce.copyOfRange(7, 13)).isEqualTo(ke.pdmNonce.copyOfRange(0, 6))
    }

    @Test
    fun `incrementNonce increments the first 8 bytes as a little-endian counter and leaves the rest untouched`() {
        val ke = newExchange()
        val originalPdmNonce = ke.pdmNonce.copyOf()

        ke.incrementNonce(O5KeyExchange.Direction.WRITE)

        // Bytes 8-15 (outside the 8-byte counter) must be untouched.
        assertThat(ke.pdmNonce.copyOfRange(8, 16)).isEqualTo(originalPdmNonce.copyOfRange(8, 16))
        assertThat(ke.pdmNonce).isNotEqualTo(originalPdmNonce)

        // Incrementing again should differ from the first increment too (not a no-op).
        val afterFirstIncrement = ke.pdmNonce.copyOf()
        ke.incrementNonce(O5KeyExchange.Direction.WRITE)
        assertThat(ke.pdmNonce).isNotEqualTo(afterFirstIncrement)
    }

    @Test
    fun `incrementNonce wraps a little-endian counter of all-0xFF bytes to zero`() {
        val randomByteGenerator = spy(RandomByteGenerator())
        val allFF = ByteArray(O5KeyExchange.NONCE_SIZE) { 0xFF.toByte() }
        doReturn(allFF).whenever(randomByteGenerator).nextBytes(org.mockito.kotlin.any())
        val ke = newExchange(randomByteGenerator = randomByteGenerator)

        ke.incrementNonce(O5KeyExchange.Direction.WRITE)

        assertThat(ke.pdmNonce.copyOfRange(0, 8)).isEqualTo(ByteArray(8))
        // Untouched tail stays 0xFF.
        assertThat(ke.pdmNonce.copyOfRange(8, 16)).isEqualTo(ByteArray(8) { 0xFF.toByte() })
    }

    @Test
    fun `buildChannelBindingTranscript is 171 bytes composed of the documented fields in order`() {
        val ke = newExchange()
        ke.o5UpdatePodPublicData(realPublicKey() + ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 1).toByte() })

        val transcript = ke.buildChannelBindingTranscript()

        assertThat(transcript).hasLength(171)
        assertThat(transcript[0]).isEqualTo(0x01.toByte())
        assertThat(transcript.copyOfRange(1, 7)).isEqualTo(O5KeyExchange.FIRMWARE_ID)
        assertThat(transcript.copyOfRange(7, 11)).isEqualTo(ByteArray(4))
        assertThat(transcript.copyOfRange(11, 75)).isEqualTo(ke.pdmPublic)
        assertThat(transcript.copyOfRange(75, 139)).isEqualTo(ke.podPublic)
        assertThat(transcript.copyOfRange(139, 155)).isEqualTo(ke.pdmNonce)
        assertThat(transcript.copyOfRange(155, 171)).isEqualTo(ke.podNonce)
    }

    @Test
    fun `buildPodChannelBindingTranscript uses a podNonce decremented by one relative to the current value`() {
        val ke = newExchange()
        ke.o5UpdatePodPublicData(realPublicKey() + ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 1).toByte() })
        val originalPodNonce = ke.podNonce.copyOf()

        // Advance podNonce by one, then the pod-transcript's adjusted nonce should match what
        // podNonce was BEFORE that increment - i.e. decrement-by-one is a true inverse here.
        ke.incrementNonce(O5KeyExchange.Direction.READ)
        val transcript = ke.buildPodChannelBindingTranscript()

        assertThat(transcript).hasLength(171)
        assertThat(transcript[0]).isEqualTo(0x02.toByte())
        val podNonceAdjustedInTranscript = transcript.copyOfRange(139, 155)
        assertThat(podNonceAdjustedInTranscript).isEqualTo(originalPodNonce)
    }

    // -- Golden vector from a real, successful O5 pairing ---------------------------------
    //
    // Captured 2026-08-12 from OmnipodKit (Trio on iOS) completing SPS2 against a real pod -
    // the exchange this Kotlin port is dropped at. These are the actual bytes OmnipodKit put
    // on the wire, so unlike the structural tests above, this pins the transcript and SPS
    // nonces to a known-accepted reference rather than to our own reading of the Swift.
    //
    // Only pdmPublic/podPublic/pdmNonce/podNonce feed the transcript and the SPS nonces, and
    // all four are recoverable from that capture. The ephemeral private key is not, so conf/
    // ltk cannot be reproduced here - but they don't affect either value under test.

    private object TrioCapture {

        /** Controller ephemeral public key (from the logged transcript). */
        const val PDM_PUBLIC =
            "84dbaf14bb1d7155fbe077a84bafa8dbcbda86be6a3c2c6c57d4913a5bc77512" +
                "24a6362163c46b61d21d5085cf9e5df3721abe734de867483928b973abf8406b"

        /** Pod ephemeral public key + nonce, exactly as received in the pod's SPS1 reply. */
        const val POD_SPS1_PAYLOAD =
            "bc207ee6cc8ac3f9ab1a59f209b8f595c424f56e0077e469e2161a267eee2443" +
                "5c33aaf5398ebdac04c93e15ca08d7006c2a2f50e806cb33400be879e103eb6f" +
                "c1d022a08a9feabafcb941a483963255"

        /** Controller nonce as first generated, before any increment. */
        const val PDM_NONCE = "5a60f0c9af60befebed4c8272a47b8d9"

        /** AES-CCM nonces logged by OmnipodKit at each SPS2.1/SPS2 step. */
        const val NONCE_SPS2_1_WRITE = "015a60f0c9af60c1d022a08a9f"
        const val NONCE_SPS2_1_READ = "02c1d022a08a9f5b60f0c9af60"
        const val NONCE_SPS2_WRITE = "015b60f0c9af60c2d022a08a9f"
        const val NONCE_SPS2_READ = "02c2d022a08a9f5c60f0c9af60"

        /** The 171-byte transcript OmnipodKit signed, and the pod accepted. */
        const val TRANSCRIPT =
            "019b0ab96a76f40000000084dbaf14bb1d7155fbe077a84bafa8dbcbda86be6a" +
                "3c2c6c57d4913a5bc7751224a6362163c46b61d21d5085cf9e5df3721abe734d" +
                "e867483928b973abf8406bbc207ee6cc8ac3f9ab1a59f209b8f595c424f56e00" +
                "77e469e2161a267eee24435c33aaf5398ebdac04c93e15ca08d7006c2a2f50e8" +
                "06cb33400be879e103eb6f5b60f0c9af60befebed4c8272a47b8d9c2d022a08a" +
                "9feabafcb941a483963255"
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    /**
     * Builds an exchange whose controller-side material is the captured session's, so the
     * transcript and SPS nonces are reproducible. The private key stays real (a stub would
     * fail ECDH's curve validation against the pod's genuine public key); it only affects
     * the shared secret, which neither value under test depends on.
     */
    private fun trioCaptureExchange(): O5KeyExchange {
        val keyGenerator = spy(P256KeyGenerator())
        doReturn(hex(TrioCapture.PDM_PUBLIC)).whenever(keyGenerator).publicFromPrivate(any())
        val randomByteGenerator = spy(RandomByteGenerator())
        doReturn(hex(TrioCapture.PDM_NONCE)).whenever(randomByteGenerator).nextBytes(any())
        return O5KeyExchange(aapsLogger, keyGenerator, randomByteGenerator, controllerIdData)
            .apply { o5UpdatePodPublicData(hex(TrioCapture.POD_SPS1_PAYLOAD)) }
    }

    @Test
    fun `channel-binding transcript matches a real pairing the pod accepted`() {
        val ke = trioCaptureExchange()

        // Walk the same sequence O5LTKExchanger does: send SPS2.1 (WRITE +1), read the pod's
        // SPS2.1 (READ +1), then build the transcript. Both nonces are one step on by then.
        ke.incrementNonce(O5KeyExchange.Direction.WRITE)
        ke.incrementNonce(O5KeyExchange.Direction.READ)

        assertThat(ke.buildChannelBindingTranscript().hex()).isEqualTo(TrioCapture.TRANSCRIPT)
    }

    @Test
    fun `SPS nonces match the real pairing at every step`() {
        val ke = trioCaptureExchange()

        // SPS2.1: encrypt ours, then decrypt the pod's - each followed by its own increment.
        assertThat(ke.getSPSNonce(O5KeyExchange.Direction.WRITE).hex()).isEqualTo(TrioCapture.NONCE_SPS2_1_WRITE)
        ke.incrementNonce(O5KeyExchange.Direction.WRITE)
        assertThat(ke.getSPSNonce(O5KeyExchange.Direction.READ).hex()).isEqualTo(TrioCapture.NONCE_SPS2_1_READ)
        ke.incrementNonce(O5KeyExchange.Direction.READ)

        // SPS2: same again, one step further on.
        assertThat(ke.getSPSNonce(O5KeyExchange.Direction.WRITE).hex()).isEqualTo(TrioCapture.NONCE_SPS2_WRITE)
        ke.incrementNonce(O5KeyExchange.Direction.WRITE)
        assertThat(ke.getSPSNonce(O5KeyExchange.Direction.READ).hex()).isEqualTo(TrioCapture.NONCE_SPS2_READ)
    }
}
