package app.aaps.pump.omnipod.common.bledriver.pod.state

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [O5PodStateManager.lastUserBolusStartTime] / [O5PodStateManager.lastUserBolusRequestedUnits] -
 * the filter that keeps a basal drift correction from being treated as a bolus the user gave.
 *
 * A correction is written to the same `lastBolus*` fields as a real bolus, so without the
 * [O5PodStateManager.lastBolusIsBasalCorrection] tag it would satisfy the zero-TBR exemption in
 * `O5PumpPlugin.needsBasalCorrection` and refresh the 5-minute window that permits the next
 * correction - and it would surface as the user's last bolus in the overview and in the Nightscout
 * device status.
 */
class O5LastUserBolusTest {

    @Test fun `a real bolus is exposed as the user's last bolus`() {
        val state = InMemoryO5PodStateManager()
        val now = System.currentTimeMillis()

        state.lastBolusStartTime = now
        state.lastBolusRequestedUnits = 2.5
        state.lastBolusIsBasalCorrection = false

        assertThat(state.lastUserBolusStartTime).isEqualTo(now)
        assertThat(state.lastUserBolusRequestedUnits).isEqualTo(2.5)
    }

    @Test fun `a basal correction is hidden, but the raw fields still hold it`() {
        val state = InMemoryO5PodStateManager()
        val now = System.currentTimeMillis()

        state.lastBolusStartTime = now
        state.lastBolusRequestedUnits = 0.05
        state.lastBolusIsBasalCorrection = true

        // The raw fields stay populated - the delivery/completion path still needs them.
        assertThat(state.lastBolusStartTime).isEqualTo(now)
        assertThat(state.lastBolusRequestedUnits).isEqualTo(0.05)

        assertThat(state.lastUserBolusStartTime).isNull()
        assertThat(state.lastUserBolusRequestedUnits).isNull()
    }

    @Test fun `a correction following a real bolus closes the window`() {
        val state = InMemoryO5PodStateManager()

        state.lastBolusStartTime = System.currentTimeMillis()
        state.lastBolusRequestedUnits = 2.5
        state.lastBolusIsBasalCorrection = false
        assertThat(state.lastUserBolusStartTime).isNotNull()

        // The correction overwrites the same fields and must not keep the window open.
        state.lastBolusStartTime = System.currentTimeMillis()
        state.lastBolusRequestedUnits = 0.05
        state.lastBolusIsBasalCorrection = true
        assertThat(state.lastUserBolusStartTime).isNull()
    }

    @Test fun `reset clears the correction tag`() {
        val state = InMemoryO5PodStateManager()
        state.lastBolusIsBasalCorrection = true

        state.reset()

        assertThat(state.lastBolusIsBasalCorrection).isFalse()
        assertThat(state.lastUserBolusStartTime).isNull()
    }
}
