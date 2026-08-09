package app.aaps.core.interfaces.automation

import kotlinx.coroutines.flow.StateFlow

interface Automation {

    /**
     * Live snapshot of the automation event list. Emits on user edits, ordering changes, and
     * NS-synced reloads. Replaces the prior `EventAutomationDataChanged` RxBus broadcast.
     */
    val events: StateFlow<List<AutomationEvent>>

    /**
     * Single source of truth for "automation executes here". True only on a master device; clients
     * (AAPSCLIENT) edit + sync definitions but never run them. UI surfaces that offer to run/execute
     * a user action must hide it when this is false. Execution itself is also hard-gated internally.
     */
    val executionEnabled: Boolean

    fun findEventById(id: String): AutomationEvent?

    /**
     * Run an automation event's actions.
     *
     * @param userInitiated true when a person pressed something to run this - the home screen
     *   Automation sheet, quick launch, a wear tile - as opposed to the periodic loop deciding a
     *   rule should fire. A user-initiated run **skips the event's trigger check**: the trigger
     *   answers "when should this fire by itself", and pressing the button is the user answering
     *   it. Without this, a rule flagged as a user action but carrying any real condition is
     *   offered as a button, confirmed, and then silently does nothing.
     *
     *   Per-action preconditions are still enforced either way - those are the actions' own
     *   safety guards, not a schedule, and a manual run is no reason to skip them.
     */
    suspend fun processEvent(someEvent: AutomationEvent, userInitiated: Boolean = false)

    /**
     * Generate reminder via [app.aaps.plugins.automation.TimerUtil]
     *
     */
    fun scheduleAutomationEventBolusReminder()

    /**
     * Remove scheduled reminder from automations
     *
     */
    fun removeAutomationEventBolusReminder()

    /**
     * Generate reminder via [app.aaps.plugins.automation.TimerUtil]
     *
     * @param seconds seconds to the future
     */
    fun scheduleTimeToEatReminder(seconds: Int)

    /**
     * Remove Automation event
     */
    fun removeAutomationEventEatReminder()

    /**
     * Create new Automation event to alarm when is time to eat
     */
    fun scheduleAutomationEventEatReminder()
}