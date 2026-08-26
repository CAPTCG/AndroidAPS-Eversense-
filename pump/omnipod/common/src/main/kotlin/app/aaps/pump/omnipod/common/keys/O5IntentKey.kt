package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.pump.omnipod.common.R

/**
 * Omnipod 5 preference entries that open a screen rather than edit a stored value.
 *
 * Kept separate from the Boolean/Int preference key enums because these carry no persisted
 * value - the screen they launch is supplied at the use site via `withCompose`, see
 * `O5PumpPlugin.getPreferenceScreenContent`.
 */
enum class O5IntentKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    /** Opens the credential import screen, where an O5 registration credential is pasted in. */
    CertificateStore(
        key = "omnipod_5_certificate_store",
        titleResId = R.string.omnipod_5_certificate_store,
        summaryResId = R.string.omnipod_5_certificate_store_summary
    )
}
