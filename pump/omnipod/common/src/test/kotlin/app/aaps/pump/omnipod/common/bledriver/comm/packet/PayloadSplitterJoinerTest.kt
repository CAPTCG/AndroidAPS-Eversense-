package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.comm.message.CrcMismatchException
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Round-trips payloads through [PayloadSplitter] -> [BlePacket.toByteArray] -> [PayloadJoiner]
 * for both [BlePacketLayout.DASH] and [BlePacketLayout.OMNIPOD_5].
 *
 * The O5 sweep in particular targets the bug fixed alongside the O5 layout support: packet
 * "rest"/size fields are on-wire unsigned bytes (0..255), but Kotlin's Byte is signed, so any
 * payload whose last-packet remainder exceeds 127 previously sign-extended into a negative
 * length and broke [PayloadJoiner] parsing. Dash never produced a remainder over 127 (its
 * packets max out at 20 bytes), so this was unreachable there - O5's 244-byte packets reach it
 * routinely for realistic message sizes (e.g. certificate-carrying pairing messages).
 */
class PayloadSplitterJoinerTest : TestBase() {

    private fun payloadOf(size: Int, seed: Int): ByteArray = Random(seed).nextBytes(size)

    private fun roundTrip(payload: ByteArray, layout: BlePacketLayout): ByteArray {
        val packets = PayloadSplitter(payload, layout).splitInPackets()
        val encoded = packets.map { it.toByteArray(layout) }

        val joiner = PayloadJoiner(encoded.first(), layout)
        for (packetBytes in encoded.drop(1)) {
            joiner.accumulate(packetBytes)
        }
        return joiner.finalize()
    }

    @Test
    fun `Dash round-trip holds for every supported payload size`() {
        // maxFragments=15 means fullFragments (middleFragments+1) must stay under 15, i.e.
        // middleFragments <= 13 - which caps the largest payload the Dash layout can carry
        // at 283 bytes (18 + 13*19 + 18, the last term being the max rest before it would
        // need a 14th middle fragment instead). Sizes beyond that are out of protocol range.
        for (size in 1..283) {
            val payload = payloadOf(size, seed = size)
            val result = roundTrip(payload, BlePacketLayout.DASH)
            assertThat(result).isEqualTo(payload)
        }
    }

    @Test
    fun `O5 round-trip holds for every payload size from 1 to 2000 bytes`() {
        // Covers, among other things, every case where the last packet's "rest" byte -
        // and the optional continuation packet's size byte - legitimately exceeds 127,
        // which is where the signed-Byte bug lived.
        for (size in 1..2000) {
            val payload = payloadOf(size, seed = size)
            val result = roundTrip(payload, BlePacketLayout.OMNIPOD_5)
            assertThat(result).isEqualTo(payload)
        }
    }

    // -- Cross-validation against OmnipodKit's own O5 framing test suite -------------------
    //
    // The expectations below are lifted from OmniTests/O5/O5BleFramingTests.swift (the
    // reference Swift implementation's own tests, itsmojo/OmnipodKit `ble-heartbeat`). They
    // pin this port's framing to the reference's asserted behavior rather than only to
    // internal round-trip consistency, so a future divergence in either the layout constants
    // or the split boundaries shows up here instead of on real hardware.

    @Test
    fun `O5 layout constants match OmnipodKit's asserted values`() {
        val layout = BlePacketLayout.OMNIPOD_5
        assertThat(layout.maxPayloadSize).isEqualTo(244)
        assertThat(layout.maxFragments).isEqualTo(15)
        assertThat(layout.firstPacketCapacityWithoutMiddlePackets).isEqualTo(237)
        assertThat(layout.firstPacketCapacityWithMiddlePackets).isEqualTo(242)
        assertThat(layout.firstPacketCapacityWithOptionalPlusOnePacket).isEqualTo(242)
        assertThat(layout.middlePacketCapacity).isEqualTo(243)
        assertThat(layout.lastPacketCapacity).isEqualTo(238)
    }

    @Test
    fun `O5 split produces the same packet counts OmnipodKit asserts`() {
        // Sizes and expected counts taken directly from O5BleFramingTests.swift, including
        // its boundary cases (242/243 straddle firstPacketCapacityWithOptionalPlusOnePacket)
        // and the AID/bolus message sizes it pins by name.
        val expected = mapOf(
            0 to 1,      // testSplitJoin_roundTrip_emptyPayload
            100 to 1,    // testSplit_packetCount_monotonic
            300 to 2,
            242 to 2,    // testSplitJoin_roundTrip_boundary242
            243 to 2,    // testSplitJoin_roundTrip_boundary243
            500 to 3,    // testSplitJoin_roundTrip_medium500
            641 to 3,    // testSplitJoin_roundTrip_multiPacketSizes
            642 to 3,
            893 to 4,
            953 to 4,
            18 to 1,     // utcSend
            15 to 1,     // tdiSend
            11 to 1,     // diaSend
            17 to 1,     // egvSend
            204 to 1,    // targetBgProfileSend
            176 to 1,    // algorithmInsulinHistorySend
            20 to 1      // bolusExtra (one-unit / prime / cannula)
        )

        for ((size, packetCount) in expected) {
            val packets = PayloadSplitter(payloadOf(size, seed = size), BlePacketLayout.OMNIPOD_5).splitInPackets()
            assertThat(packets).hasSize(packetCount)
        }
    }

    @Test
    fun `O5 953-byte payload reports three full fragments, as OmnipodKit asserts`() {
        // testSplit_firstPacket_fullFragments_953: first.fullFragments == 3, 4 packets total.
        val packets = PayloadSplitter(payloadOf(953, seed = 2), BlePacketLayout.OMNIPOD_5).splitInPackets()

        assertThat(packets).hasSize(4)
        assertThat(packets.filterIsInstance<FirstBlePacket>().single().fullFragments).isEqualTo(3)
    }

    @Test
    fun `O5 last-packet remainder over 127 round-trips correctly (regression for the signed-Byte bug)`() {
        // firstPacketCapacityWithMiddlePackets=242, middlePacketCapacity=243: 2 middle
        // fragments then a remainder of exactly 200 bytes in the last packet - 200 doesn't
        // fit in a signed Kotlin Byte (max 127), which is exactly what the fix addresses.
        val size = 242 + 2 * 243 + 200
        val payload = payloadOf(size, seed = 4242)

        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val lastPacket = packets.filterIsInstance<LastBlePacket>().single()
        assertThat(lastPacket.size.toUnsignedInt()).isEqualTo(200)

        val result = roundTrip(payload, BlePacketLayout.OMNIPOD_5)
        assertThat(result).isEqualTo(payload)
    }

    @Test
    fun `LastOptionalPlusOneBlePacket round-trips a size byte over 127`() {
        // The splitter can never actually hand this packet type a size over 127 for O5's
        // current capacity ratio (243 middle / 238 last caps its overflow at 4 bytes), but
        // BLEPacket.swift's on-wire format supports the full 0..255 range for this field
        // and nothing in BlePacket.kt itself assumes otherwise - so pin toByteArray()/parse()
        // directly, independent of what the splitter happens to produce today.
        val layout = BlePacketLayout.OMNIPOD_5
        val payload = payloadOf(200, seed = 55)
        val packet = LastOptionalPlusOneBlePacket(index = 5, payload = payload, size = 200.toByte())

        val encoded = packet.toByteArray(layout)
        val parsed = LastOptionalPlusOneBlePacket.parse(encoded, layout)

        assertThat(parsed.size.toUnsignedInt()).isEqualTo(200)
        assertThat(parsed.payload).isEqualTo(payload)
    }

    // -- Packet padding: O5 pads tail packets only, Dash pads everything ------------------
    // Sizes below are the observed BLE writes from a Trio pairing that succeeded against a
    // real pod on 2026-08-17. See PacketPadding's doc comment.

    @Test
    fun `O5 single-packet messages are written at their exact length, not padded to 244`() {
        // 44 bytes is the real size of the SP1+SP2 pairing message. A single-packet message
        // is a FirstBlePacket, which OmnipodKit never pads - Trio wrote 51 bytes here.
        val payload = payloadOf(44, seed = 7)

        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val encoded = packets.single().toByteArray(BlePacketLayout.OMNIPOD_5)

        // 7-byte header (index, fragments, crc32, size) + 44-byte payload.
        assertThat(encoded.size).isEqualTo(51)
    }

    @Test
    fun `O5 split messages pad the tail packet, so every write is a full 244 bytes`() {
        // The SPS2 message - the largest of the pairing sequence, and the one AAPS died on
        // while sending a 10-byte tail packet where Trio sent 244.
        val payload = payloadOf(959, seed = 8)

        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val encoded = packets.map { it.toByteArray(BlePacketLayout.OMNIPOD_5) }

        // Trio's writes for a payload this size were [244, 244, 244, 244, 244].
        assertThat(encoded.map { it.size })
            .isEqualTo(List(encoded.size) { BlePacketLayout.OMNIPOD_5.maxPayloadSize })
        // Padding is trailing zeros only - the message still round-trips byte-for-byte.
        assertThat(roundTrip(payload, BlePacketLayout.OMNIPOD_5)).isEqualTo(payload)
    }

    @Test
    fun `O5 tail padding is zero-filled and does not disturb the payload`() {
        val payload = payloadOf(500, seed = 11)

        val encoded = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5)
            .splitInPackets().map { it.toByteArray(BlePacketLayout.OMNIPOD_5) }

        // The tail packet declares its real length in its size byte; everything past that
        // header + length must be zeros, never stale or truncated payload.
        val tail = encoded.last()
        val declared = tail[1].toUnsignedInt()
        assertThat(tail.drop(6 + declared).toByteArray()).isEqualTo(ByteArray(tail.size - 6 - declared))
        assertThat(roundTrip(payload, BlePacketLayout.OMNIPOD_5)).isEqualTo(payload)
    }

    @Test
    fun `Dash packets keep their existing full-length padding`() {
        val payload = payloadOf(5, seed = 9)

        val encoded = PayloadSplitter(payload, BlePacketLayout.DASH)
            .splitInPackets().single().toByteArray(BlePacketLayout.DASH)

        assertThat(encoded.size).isEqualTo(BlePacketLayout.DASH.maxPayloadSize)
    }

    @Test
    fun `Dash split messages still pad every packet, unchanged by the O5 padding fix`() {
        // Dash's padding predates the O5 work and is proven against real hardware over years.
        // The O5 fix must not have altered it on any packet type, split messages included.
        val payload = payloadOf(120, seed = 10)

        val encoded = PayloadSplitter(payload, BlePacketLayout.DASH)
            .splitInPackets().map { it.toByteArray(BlePacketLayout.DASH) }

        assertThat(encoded.size).isGreaterThan(1)
        assertThat(encoded.map { it.size })
            .isEqualTo(List(encoded.size) { BlePacketLayout.DASH.maxPayloadSize })
        assertThat(roundTrip(payload, BlePacketLayout.DASH)).isEqualTo(payload)
    }

    @Test
    fun `Byte toUnsignedInt reinterprets the full 0-255 range correctly`() {
        assertThat(0.toByte().toUnsignedInt()).isEqualTo(0)
        assertThat(127.toByte().toUnsignedInt()).isEqualTo(127)
        assertThat(128.toByte().toUnsignedInt()).isEqualTo(128)
        assertThat(200.toByte().toUnsignedInt()).isEqualTo(200)
        assertThat(255.toByte().toUnsignedInt()).isEqualTo(255)
    }

    @Test
    fun `corrupted payload is still detected via CRC mismatch after joining (O5)`() {
        val payload = payloadOf(500, seed = 99)
        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val encoded = packets.map { it.toByteArray(BlePacketLayout.OMNIPOD_5) }.toMutableList()

        // Flip the first byte of the last packet's actual payload (right after its 6-byte
        // header) rather than anything further out: parse() only reads up to the packet's
        // own declared "rest" length, so a byte past that is not part of the message and
        // corrupting it would prove nothing.
        val corruptIndex = 6
        encoded[encoded.lastIndex][corruptIndex] = (encoded.last()[corruptIndex] + 1).toByte()

        val joiner = PayloadJoiner(encoded.first(), BlePacketLayout.OMNIPOD_5)
        for (packetBytes in encoded.drop(1)) {
            joiner.accumulate(packetBytes)
        }

        assertThrows(CrcMismatchException::class.java) {
            joiner.finalize()
        }
    }
}
