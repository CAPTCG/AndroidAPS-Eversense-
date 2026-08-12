package app.aaps.pump.omnipod.common.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.pair.O5CertificateStore
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.DisconnectHandler
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.STOP_CONNECTING_CHECK_INTERVAL_MS
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionEstablisher
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionNegotiationResynchronization
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodType
import app.aaps.pump.omnipod.common.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.util.BluetoothServiceUuids
import app.aaps.pump.omnipod.common.bledriver.pod.util.P256KeyGenerator
import java.util.UUID

/**
 * BLE GATT connection lifecycle and session establishment for Omnipod 5, parallel to
 * Dash's [Connection] rather than a modification of it - [Connection] is coupled
 * throughout to the much larger [app.aaps.pump.omnipod.common.bledriver.pod.state
 * .OmnipodDashPodStateManager] (full pod status surface: basal programs, alerts,
 * delivery status, etc.), which doesn't fit O5's connection-only [O5PodStateManager].
 *
 * Everything below the GATT-characteristic-selection level - [ServiceDiscoverer] (called
 * here with [PodType.OMNIPOD_5]), [CmdBleIO], [DataBleIO], [MessageIO],
 * [SessionEstablisher], [Session], [EnDecrypt] - is reused unmodified from the existing
 * Dash connection path, since none of it is actually Dash-specific once given the right
 * pod type and identity.
 */
class O5Connection(
    private val podDevice: BluetoothDevice,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: O5PodStateManager,
    private val p256KeyGenerator: P256KeyGenerator
) : BleConnection, DisconnectHandler {

    private val incomingPackets = IncomingPackets()
    private val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets, this)
    private var gattConnection: BluetoothGatt? = null

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    private var _connectionWaitCond: ConnectionWaitCondition? = null

    @Volatile
    override var session: Session? = null

    @Volatile
    override var msgIO: MessageIO? = null

    @Synchronized
    override fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Connecting (O5) connectionWaitCond=$connectionWaitCond")
        _connectionWaitCond = connectionWaitCond
        podState.connectionAttempts++
        podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.CONNECTING
        val autoConnect = false
        var gatt = gattConnection
        if (gatt == null) {
            gatt = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                Thread.sleep(SLEEP_WHEN_FAILING_TO_CONNECT_GATT) // Do not retry too often
                throw FailedToConnectException("connectGatt() returned null")
            }
            gattConnection = gatt
        } else if (!gatt.connect()) {
            throw FailedToConnectException("connect() returned false")
        }
        val before = SystemClock.elapsedRealtime()
        if (waitForConnection(connectionWaitCond) !is Connected) {
            podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.DISCONNECTED
            _connectionWaitCond = null
            throw FailedToConnectException(podDevice.address)
        }
        val waitedMs = SystemClock.elapsedRealtime() - before
        val timeoutMs = connectionWaitCond.timeoutMs
        if (timeoutMs != null) {
            var newTimeout = timeoutMs - waitedMs
            if (newTimeout < MIN_DISCOVERY_TIMEOUT_MS) {
                newTimeout = MIN_DISCOVERY_TIMEOUT_MS
            }
            connectionWaitCond.timeoutMs = newTimeout
        }
        podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.CONNECTED

        // Deliberately NOT requesting a larger ATT MTU here - stay at the 23-byte default.
        //
        // This used to call requestMtu(244 + 3), on the reasoning that CoreBluetooth negotiates
        // the MTU automatically and Android has to ask. A capture of a *successful* iOS pairing
        // (OmnipodKit via Trio, 2026-08-12) shows that is not what happens: the pod peripheral
        // stays at "mtu = 23" for the entire exchange, including the 959-byte SPS2 message.
        //
        // The MTU is not just a size limit, it selects the ATT operation. At MTU 23 a 244-byte
        // characteristic write becomes a long write (Prepare Write / Execute Write in 20-byte
        // chunks), which is what the pod sees from iOS and from a real PDM. At MTU 247 the same
        // bytes go out as a single Write Request. Requesting the larger MTU therefore changed
        // the wire protocol on every write, while looking like a harmless optimisation.
        //
        // That is the one difference found between our failing SPS2 and OmnipodKit's working
        // one - everything above the transport matches byte for byte (transcript layout and
        // contents, KDF input, nonce timing, certificate sizes, message sizes and sequence
        // numbers). Android performs the long write itself once the payload exceeds MTU-3, so
        // no fragmentation change is needed here; BlePacketLayout.OMNIPOD_5 keeps its 244-byte
        // payload to stay aligned with OmnipodKit's BlePodProfile.
        //
        // BleCommCallbacks.waitForMtuChange/negotiatedMtu are intentionally left in place: the
        // onMtuChanged callback still fires if the peer initiates, and keeping the mechanism
        // means restoring the request is a one-line change if this turns out to be wrong.

        val discoverer = ServiceDiscoverer(aapsLogger, gatt, bleCommCallbacks, this)
        val discovered = discoverer.discoverServices(connectionWaitCond, PodType.OMNIPOD_5)

        // O5 has a separate GATT service used purely for pod keep-alive, entirely absent
        // from Dash's profile (see BlePodProfile.swift: heartbeatServiceUUID is nil for
        // Dash, real UUIDs for O5). OmnipodKit's makePeripheralConfiguration() discovers
        // and subscribes to it as part of this same initial connection setup, alongside
        // CMD/DATA. Best-effort/non-fatal: a pod firmware that expects a fully-set-up
        // central and never sees this subscription could plausibly explain a disconnect a
        // few seconds into pairing with no other explanation - but this is unverified
        // against real hardware, so a missing service or failed subscription must not
        // become a new way for pairing to fail outright.
        enableHeartbeatNotifications(gatt)

        val cmdBleIO = CmdBleIO(
            aapsLogger,
            discovered.getValue(CharacteristicType.CMD),
            incomingPackets.cmdQueue,
            gatt,
            bleCommCallbacks
        )
        val dataBleIO = DataBleIO(
            aapsLogger,
            discovered.getValue(CharacteristicType.DATA),
            incomingPackets.dataQueue,
            gatt,
            bleCommCallbacks,
            CharacteristicType.DATA_O5
        )
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO, PodType.OMNIPOD_5)
        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
        _connectionWaitCond = null
    }

    @Synchronized
    override fun disconnect(closeGatt: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting (O5) closeGatt=$closeGatt")
        if (!closeGatt && gattConnection != null) {
            gattConnection?.disconnect()
            podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.DISCONNECTED
        } else {
            gattConnection?.close()
            bleCommCallbacks.resetConnection()
            gattConnection = null
            session = null
            msgIO = null
            podState.bluetoothConnectionState = O5PodStateManager.BluetoothConnectionState.DISCONNECTED
        }
    }

    /**
     * See the call site in [connect] for why this exists. Determines notify vs indicate
     * from the characteristic's own declared properties rather than assuming either, since
     * this characteristic's actual GATT properties haven't been confirmed against real
     * hardware yet.
     */
    private fun enableHeartbeatNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(UUID.fromString(BluetoothServiceUuids.O5_HEARTBEAT_SERVICE_UUID))
        if (service == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 heartbeat service not found - continuing without it")
            return
        }
        val characteristic = service.getCharacteristic(UUID.fromString(BluetoothServiceUuids.O5_HEARTBEAT_CHARACTERISTIC_UUID))
        if (characteristic == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 heartbeat characteristic not found - continuing without it")
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Could not enable local notifications for O5 heartbeat characteristic")
            return
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor == null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "O5 heartbeat characteristic has no CCCD - continuing without it")
            return
        }
        val enableValue =
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
        descriptor.value = enableValue
        if (!gatt.writeDescriptor(descriptor)) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Could not write O5 heartbeat CCCD")
            return
        }
        val confirmation = bleCommCallbacks.confirmWrite(
            enableValue,
            descriptor.uuid.toString(),
            BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS
        )
        if (confirmation !is WriteConfirmationSuccess) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Could not confirm O5 heartbeat CCCD write: $confirmation")
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "O5 heartbeat notifications enabled")
        }
    }

    private fun waitForConnection(connectionWaitCond: ConnectionWaitCondition): ConnectionState {
        aapsLogger.debug(LTag.PUMPBTCOMM, "waitForConnection (O5) connectionWaitCond=$connectionWaitCond")
        try {
            connectionWaitCond.timeoutMs?.let {
                bleCommCallbacks.waitForConnection(it)
            }
            val startWaiting = System.currentTimeMillis()
            connectionWaitCond.stopConnection?.let {
                while (!bleCommCallbacks.waitForConnection(STOP_CONNECTING_CHECK_INTERVAL_MS)) {
                    if (it.count == 0L) {
                        throw ConnectException("stopConnecting called")
                    }
                    val secondsElapsed = (System.currentTimeMillis() - startWaiting) / 1000
                    if (secondsElapsed > MAX_WAIT_FOR_CONNECTION_SECONDS) {
                        throw ConnectException("connection timeout")
                    }
                }
            }
        } catch (e: InterruptedException) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection (O5)")
        }
        return connectionState()
    }

    override fun connectionState(): ConnectionState {
        val connectionState = bluetoothManager?.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state (O5): $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return NotConnected
        }
        return Connected
    }

    /**
     * [ids] here must be built via [Ids.forController] using the pod's certificate-derived
     * controller id and its own O5 pod id - O5 has no [Ids] constructor of its own since it
     * has no Dash-style pod state manager to derive one from (see [Ids]'s class doc).
     */
    override fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
        val mIO = msgIO ?: throw ConnectException("Connection lost")

        val eapAkaExchanger = SessionEstablisher(aapsLogger, config, mIO, ltk, eapSqn, ids, msgSeq)
        return when (val keys = eapAkaExchanger.negotiateSessionKeys()) {
            is SessionNegotiationResynchronization -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "EAP AKA resynchronization (O5): ${keys.synchronizedEapSqn}")
                }
                keys.synchronizedEapSqn
            }

            is SessionKeys                         -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "CK (O5): ${keys.ck.toHex()}")
                    aapsLogger.info(LTag.PUMPCOMM, "msgSequenceNumber (O5): ${keys.msgSequenceNumber}")
                    aapsLogger.info(LTag.PUMPCOMM, "Nonce (O5): ${keys.nonce}")
                }
                val enDecrypt = EnDecrypt(aapsLogger, keys.nonce, keys.ck)
                // Real O5 pod firmware rejects certain dose-affecting commands unless
                // Type-4-signed (see Session.sign()'s doc comment) - reuse the same
                // certificate/keypair pairing already validated for this controller id,
                // rather than a separate signing-only code path.
                val controllerId = requireNotNull(podState.controllerId) {
                    "Missing controllerId, cannot establish a signed O5 session"
                }
                val certStore = O5CertificateStore(aapsLogger, p256KeyGenerator, controllerId)
                session = Session(aapsLogger, mIO, ids, sessionKeys = keys, enDecrypt = enDecrypt, commandSigner = certStore)
                null
            }
        }
    }

    // This will be called from a different thread !!!
    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Lost connection (O5) with status: $status")
        _connectionWaitCond?.stopConnection?.let {
            if (it.count > 0) {
                it.countDown()
            }
        }
        disconnect(true)
    }

    companion object {
        const val MIN_DISCOVERY_TIMEOUT_MS = 10000L
        const val MAX_WAIT_FOR_CONNECTION_SECONDS = Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS + 10
        const val SLEEP_WHEN_FAILING_TO_CONNECT_GATT = 10000L

        /** Standard Client Characteristic Configuration Descriptor UUID (BLE spec). */
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
