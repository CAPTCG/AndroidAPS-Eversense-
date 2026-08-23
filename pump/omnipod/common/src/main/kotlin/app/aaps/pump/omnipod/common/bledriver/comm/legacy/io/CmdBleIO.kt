package app.aaps.pump.omnipod.common.bledriver.comm.legacy.io

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO as CmdBleIOInterface
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import java.util.concurrent.BlockingQueue

class CmdBleIO(
    private val aapsLogger: AAPSLogger,
    characteristic: BluetoothGattCharacteristic,
    private val incomingPackets: BlockingQueue<ByteArray>,
    gatt: BluetoothGatt,
    bleCommCallbacks: BleCommCallbacks
) : BleIO(
    aapsLogger,
    characteristic,
    incomingPackets,
    gatt,
    bleCommCallbacks,
    CharacteristicType.CMD
), CmdBleIOInterface {

    override fun peekCommand(): ByteArray? {
        return incomingPackets.peek()
    }

    override fun hello() = hello(OmnipodDashBleManagerImpl.CONTROLLER_ID)

    /**
     * Announces [controllerId] in the CMD 'hello' handshake, instead of Dash's hardcoded
     * [OmnipodDashBleManagerImpl.CONTROLLER_ID].
     *
     * O5 identifies with the certificate-derived controller id from the imported credentials,
     * and the id announced here has to be that same one. OmnipodKit threads a single `myId`
     * through `sendHello(myId:)`, the pairing `Ids`, and `O5CertificateStore(controllerId:)` -
     * it has no separate constant for this handshake - and a successful real-pod capture shows
     * it announcing that id (`myId 0x2A098C`).
     *
     * The no-arg [hello] keeps the Dash id, which is correct for Dash.
     */
    fun hello(controllerId: Int) = sendAndConfirmPacket(BleCommandHello(controllerId).data)

    // OmnipodKit's PeripheralManager.waitForCommand(): the pod can send an intermediate
    // PAIR_STATUS command on this same characteristic while it's still preparing the
    // actually-expected response (observed during O5 pairing) - looping past it here,
    // within the same overall deadline, matches that behavior instead of treating it as
    // a mismatch and giving up early.
    override fun expectCommandType(expected: BleCommand, timeoutMs: Long): BleConfirmResult {
        val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val remainingMs = deadlineMs - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) {
                return BleConfirmError("Error reading packet")
            }
            val received = receivePacket(remainingMs) ?: return BleConfirmError("Error reading packet")
            when {
                received.isEmpty()                                       ->
                    return BleConfirmIncorrectData(received)

                received[0] == expected.data[0]                          ->
                    return BleConfirmSuccess

                received[0] == BleCommandType.PAIR_STATUS.value          -> {
                    aapsLogger.debug(
                        LTag.PUMPBTCOMM,
                        "expectCommandType: skipping intermediate PAIR_STATUS while waiting for $expected"
                    )
                    // loop, still bounded by the same deadline
                }

                else                                                     ->
                    return BleConfirmIncorrectData(received)
            }
        }
    }
}
