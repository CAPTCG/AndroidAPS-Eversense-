package app.aaps.pump.omnipod.common.bledriver.comm

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.O5BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandReceiveSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.security.SecureO5RegistrationStorage
import app.aaps.pump.omnipod.common.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.common.bledriver.pod.util.P256KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [O5BleManagerImpl] - covers the guard/validation paths reachable without a live BLE
 * connection (missing pairing state, no connection established yet, the "already paired"
 * short-circuit). Deep BLE session establishment ([O5BleManagerImpl.establishSession] and
 * everything downstream of a real [app.aaps.pump.omnipod.common.bledriver.comm.interfaces
 * .session.BleConnection]) is out of scope for this pass - it would need mocking the whole
 * connection/session stack, not just this class's direct dependencies.
 */
class O5BleManagerImplTest {

    private val aapsLogger = AAPSLoggerTest()
    private val podState = mock<O5PodStateManager>()
    private val config = mock<Config>()
    private val context = mock<Context>()
    private val bleConnectionFactory = mock<O5BleConnectionFactory>()
    private val bleDeviceManager = mock<BleDeviceManager>()
    private val secureO5RegistrationStorage = mock<SecureO5RegistrationStorage>()
    private val p256KeyGenerator = P256KeyGenerator()

    private fun newManager() = O5BleManagerImpl(
        aapsLogger, podState, config, context, bleConnectionFactory,
        bleDeviceManager, secureO5RegistrationStorage, p256KeyGenerator
    )

    private val podAddress = "AA:BB:CC:DD:EE:FF"

    /** A paired pod whose BLE link and session are already up, so connect() takes its
     *  already-connected path. Returns the session so a test can stub command traffic. */
    private fun alreadyConnectedPod(): Session {
        val session = mock<Session>()
        val conn = mock<BleConnection>()
        whenever(podState.bluetoothAddress).thenReturn(podAddress)
        whenever(bleDeviceManager.isBluetoothAvailable()).thenReturn(true)
        whenever(bleDeviceManager.ensureBondedIfRequired(podAddress)).thenReturn(true)
        whenever(conn.connectionState()).thenReturn(Connected)
        whenever(conn.session).thenReturn(session)
        whenever(bleConnectionFactory.createConnection(podAddress)).thenReturn(conn)
        return session
    }

    @Test
    fun `connect chained with andThen into another call does not trip over its own busy flag`() {
        // andThen subscribes the next source synchronously inside onComplete. If connect()
        // released busy only in finally, the second call would see busy=true and fail.
        alreadyConnectedPod()
        val manager = newManager()

        val observer = manager.connect(timeoutMs = 1000).ignoreElements()
            .andThen(manager.connect(timeoutMs = 1000).ignoreElements())
            .test()

        observer.assertNoErrors()
        observer.assertComplete()
    }

    @Test
    fun `connect then two commands chained with andThen all run, as fetchStatus does`() {
        // Same shape as O5PumpPlugin.fetchStatus(): ensureConnected().andThen(status read)
        // .andThen(follow-up read). On the first paired pod (2026-09-10) every such chain
        // failed with BusyException, so status reads and bolus cancel never reached the pod.
        val session = alreadyConnectedPod()
        val cmd = mock<Command>()
        whenever(session.sendCommand(cmd)).thenReturn(CommandSendSuccess)
        whenever(session.readAndAckResponse()).thenReturn(CommandReceiveSuccess(mock<Response>()))
        val manager = newManager()

        val observer = manager.connect(timeoutMs = 1000).ignoreElements()
            .andThen(manager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements())
            .andThen(manager.sendCommand(cmd, DefaultStatusResponse::class).ignoreElements())
            .test()

        observer.assertNoErrors()
        observer.assertComplete()
        verify(session, times(2)).sendCommand(cmd)
    }

    @BeforeEach
    fun clearRegistrationData() {
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
    }

    @AfterEach
    fun tearDown() {
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
    }

    @Test
    fun `getStatus returns NotConnected before any connection has been established`() {
        val manager = newManager()

        assertThat(manager.getStatus()).isEqualTo(NotConnected)
    }

    @Test
    fun `constructor loads previously-imported credentials via SecureO5RegistrationStorage`() {
        newManager()

        verify(secureO5RegistrationStorage).loadAndInstallAll()
    }

    @Test
    fun `sendCommand fails with FailedToConnectException when there is no established connection`() {
        val manager = newManager()

        val observer = manager.sendCommand(mock<Command>(), DefaultStatusResponse::class).test()

        observer.assertError(FailedToConnectException::class.java)
    }

    @Test
    fun `connect fails with FailedToConnectException when the pod has no known bluetoothAddress`() {
        whenever(podState.bluetoothAddress).thenReturn(null)
        val manager = newManager()

        val observer = manager.connect(timeoutMs = 1000).test()

        observer.assertError(FailedToConnectException::class.java)
    }

    @Test
    fun `pairNewPod requires a known address when an LTK is already present`() {
        whenever(podState.ltk).thenReturn(byteArrayOf(1, 2, 3))
        whenever(podState.bluetoothAddress).thenReturn(null)
        val manager = newManager()

        val observer = manager.pairNewPod().test()

        observer.assertError(FailedToConnectException::class.java)
    }

    @Test
    fun `pairNewPod fails with PairingException when no O5 registration data is installed`() {
        whenever(podState.ltk).thenReturn(null)
        val manager = newManager()

        val observer = manager.pairNewPod().test()

        observer.assertError(PairingException::class.java)
    }

    @Test
    fun `pairNewPod forgets a saved address from an earlier unpaired pod before a new activation`() {
        // The address of a pod that failed to pair stays in state until the pod is discarded.
        // A new activation must scan for the pod that is actually here, not dial the old one -
        // that is how a real attempt on 2026-09-10 timed out against a pod from August.
        whenever(podState.ltk).thenReturn(null)
        whenever(podState.bluetoothAddress).thenReturn("44:D4:65:62:73:54")
        val manager = newManager()

        val observer = manager.pairNewPod().test()

        observer.assertError(PairingException::class.java)
        verify(podState).bluetoothAddress = null
    }

    @Test
    fun `pairNewPod keeps the saved address of a pod that is already paired`() {
        // A paired pod (it has an LTK) is reconnected by its saved address - that address is
        // the only way to reach it, so it must not be forgotten.
        whenever(podState.ltk).thenReturn(byteArrayOf(1, 2, 3))
        whenever(podState.bluetoothAddress).thenReturn("AA:BB:CC:DD:EE:FF")
        whenever(bleConnectionFactory.createConnection("AA:BB:CC:DD:EE:FF")).thenThrow(IllegalStateException("no pod in test"))
        val manager = newManager()

        val observer = manager.pairNewPod().test()

        observer.assertError(IllegalStateException::class.java)
        verify(podState, never()).bluetoothAddress = null
    }

    @Test
    fun `removeBond does nothing and does not call the device manager when bluetoothAddress is unknown`() {
        whenever(podState.bluetoothAddress).thenReturn(null)
        val manager = newManager()

        manager.removeBond()

        verify(bleDeviceManager, never()).removeBond(org.mockito.kotlin.any())
    }

    @Test
    fun `removeBond delegates to the device manager with the pod's bluetoothAddress`() {
        whenever(podState.bluetoothAddress).thenReturn("AA:BB:CC:DD:EE:FF")
        val manager = newManager()

        manager.removeBond()

        verify(bleDeviceManager).removeBond("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `disconnect does not throw when there is no active connection`() {
        val manager = newManager()

        manager.disconnect(closeGatt = false)
    }
}
