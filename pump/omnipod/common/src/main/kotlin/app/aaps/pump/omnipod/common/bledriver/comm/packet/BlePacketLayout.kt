package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType

/**
 * How far each outgoing BLE packet is zero-filled.
 *
 * OmnipodKit's BLEPacket.swift has no such switch - it pads per packet type, unconditionally:
 * `LastBlePacket.toData` and `LastOptionalPlusOneBlePacket.toData` each append an explicit
 * `Data(count: maxPayloadSize - payload.count - headerSize)` tail, while `FirstBlePacket.toData`
 * appends nothing (its `Data(capacity:)` is an allocation hint, not a length) and
 * `MiddleBlePacket.toData` needs none - a middle packet's payload is always exactly
 * `maxPayloadSize - 1`, so it is full by construction.
 *
 * This enum exists only because the Dash path here diverges from that and must not be disturbed.
 */
enum class PacketPadding {

    /**
     * Every packet zero-filled to `maxPayloadSize`, including the first.
     *
     * Dash's long-standing behavior. It differs from the Swift reference on first packets, but
     * Dash's `maxPayloadSize` is 20 so the overshoot is a few bytes, and it is proven against
     * real Dash hardware over years of use. Left alone deliberately - correctness here is
     * established by the hardware, not by matching the reference.
     */
    ALL_PACKETS,

    /**
     * Only the tail packet of a split message zero-filled, matching BLEPacket.swift exactly.
     *
     * "Tail packet" means [LastBlePacket] / [LastOptionalPlusOneBlePacket] specifically - the
     * types that exist only once a message is too big for one packet. A message that fits in a
     * single packet is a [FirstBlePacket] and is therefore NOT padded, even though it is the
     * last packet of its message positionally.
     *
     * Required for O5, and this asymmetry is exactly what a working Trio pairing captured
     * 2026-08-17 shows: single-packet messages go out at their exact length (SP1+SP2 51,
     * SPS0 35, SPS1 110), while every packet of a split message is a full 244 - `[244, 244, 244]`
     * for SPS2.1 and `[244] * 5` for SPS2. First and middle packets of a split message are
     * already full by construction, so padding the tail is what makes all of those writes 244.
     *
     * AAPS previously padded nothing and died at SPS2, whose tail packet went out as 10 bytes.
     */
    TAIL_PACKET_ONLY
}

/**
 * BLE packet framing parameters. Dash and Omnipod 5 share the same header byte layout but
 * use different maximum payload sizes per packet (Dash: 20 bytes, O5: 244 bytes), which in
 * turn changes every derived fragment capacity used when splitting/joining message payloads.
 *
 * Ported from OmnipodKit's BlePodProfile.swift (loopandlearn/OmnipodKit) `BlePacketLayout`
 * struct and its `omnipodDash`/`omnipod5` presets.
 */
data class BlePacketLayout(
    val maxPayloadSize: Int,
    val maxFragments: Int,
    val firstPacketHeaderSizeWithoutMiddlePackets: Int,
    val firstPacketHeaderSizeWithMiddlePackets: Int,
    val lastPacketHeaderSize: Int,
    /** How much of each BLE write [BlePacket.toByteArray] zero-fills. See [PacketPadding]. */
    val packetPadding: PacketPadding
) {
    val firstPacketCapacityWithoutMiddlePackets: Int
        get() = maxPayloadSize - firstPacketHeaderSizeWithoutMiddlePackets

    val firstPacketCapacityWithMiddlePackets: Int
        get() = maxPayloadSize - firstPacketHeaderSizeWithMiddlePackets

    val firstPacketCapacityWithOptionalPlusOnePacket: Int
        get() = firstPacketCapacityWithMiddlePackets

    val middlePacketCapacity: Int
        get() = maxPayloadSize - 1

    val lastPacketCapacity: Int
        get() = maxPayloadSize - lastPacketHeaderSize

    companion object {

        val DASH = BlePacketLayout(
            maxPayloadSize = 20,
            maxFragments = 15,
            firstPacketHeaderSizeWithoutMiddlePackets = 7,
            firstPacketHeaderSizeWithMiddlePackets = 2,
            lastPacketHeaderSize = 6,
            packetPadding = PacketPadding.ALL_PACKETS
        )

        val OMNIPOD_5 = BlePacketLayout(
            maxPayloadSize = 244,
            maxFragments = 15,
            firstPacketHeaderSizeWithoutMiddlePackets = 7,
            firstPacketHeaderSizeWithMiddlePackets = 2,
            lastPacketHeaderSize = 6,
            packetPadding = PacketPadding.TAIL_PACKET_ONLY
        )
    }
}

val PodType.blePacketLayout: BlePacketLayout
    get() = if (isO5) BlePacketLayout.OMNIPOD_5 else BlePacketLayout.DASH
