package app.aaps.ui.compose.overview

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.compose.TABLET_MIN_SW_DP
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.pump.PumpActivityDialog
import app.aaps.core.ui.compose.pump.PumpActivityFab
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.notificationsSheet.NotificationFab
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel

private val SPLIT_LAYOUT_MIN_WIDTH: Dp = 720.dp

@Composable
fun OverviewScreen(
    profileName: String,
    profilePsId: Long = 0,
    isProfileModified: Boolean,
    profileProgress: Float,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetRecordId: Long = 0,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeRecordId: Long = 0,
    tbrState: TbrState,
    smbEnabled: Boolean,
    isSimpleMode: Boolean,
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    // Disables the command chips' click (running mode / profile / temp target) on an unpaired client — same gate as nav/Manage.
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    paddingValues: PaddingValues,
    fabBottomOffset: Dp = 0.dp,
    bolusState: BolusProgressState? = null,
    pumpStatusText: String = "",
    queueStatusText: String? = null,
    isPumpCommunicating: Boolean = false,
    onStopBolus: () -> Unit = {},
    eversenseCalibrationSubmittedAt: Long = 0L,
    modifier: Modifier = Modifier
) {
    var showNotificationSheet by remember { mutableStateOf(false) }
    var showPumpActivityDialog by remember { mutableStateOf(false) }
    val showPumpFab = isPumpCommunicating || (bolusState != null && bolusState.isSMB)

    LaunchedEffect(showPumpFab) {
        if (!showPumpFab && showPumpActivityDialog) {
            delay(3_000)
            showPumpActivityDialog = false
        }
    }

    LaunchedEffect(autoShowNotificationSheet) {
        if (autoShowNotificationSheet) {
            showNotificationSheet = true
            onAutoShowConsumed()
        }
    }

    val runningModeSceneManaged = activeSceneState?.scopedRecords?.rmId
        ?.let { it == runningModeRecordId && it > 0 } == true
    val tempTargetSceneManaged = activeSceneState?.scopedRecords?.ttId
        ?.let { it == tempTargetRecordId && it > 0 } == true
    val profileSceneManaged = activeSceneState?.scopedRecords?.psId
        ?.let { it == profilePsId && it > 0 } == true

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= TABLET_MIN_SW_DP && isLandscape

    // Local function, not an inline lambda: its body is its own scope, so the plain
    // AnimatedVisibility call inside stays unambiguously BoxScope's overload rather than also
    // considering the outer Column's — nesting Box directly under Column (a lambda closure,
    // not a function boundary) makes that call ambiguous between the two.
    @Composable
    fun OverviewContent(contentModifier: Modifier) {
        // Top clearance for the app bar is already handled once, above, by the unconditional
        // Spacer before the calibration banner - this content now starts below that instead of
        // at y=0 the way it used to. Re-applying paddingValues' top component here too, on top
        // of that, was doubling the gap whenever the banner was visible. Bottom/side clearance
        // (nav bar etc.) is untouched and still needed as-is.
        val layoutDirection = LocalLayoutDirection.current
        val contentPaddingValues = PaddingValues(
            start = paddingValues.calculateStartPadding(layoutDirection),
            top = 0.dp,
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = paddingValues.calculateBottomPadding()
        )
        Box(modifier = contentModifier) {
            if (isTablet) {
                OverviewScreenTablet(
                    profileName = profileName,
                    isProfileModified = isProfileModified,
                    profileProgress = profileProgress,
                    profileSceneManaged = profileSceneManaged,
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    tbrState = tbrState,
                    smbEnabled = smbEnabled,
                    isSimpleMode = isSimpleMode,
                    graphViewModel = graphViewModel,
                    chipsViewModel = chipsViewModel,
                    manageViewModel = manageViewModel,
                    statusViewModel = statusViewModel,
                    statusLightsDef = statusLightsDef,
                    onNavigate = onNavigate,
                    onTbrChipClick = onTbrChipClick,
                    onIobChipClick = onIobChipClick,
                    paddingValues = contentPaddingValues,
                    activeSceneState = activeSceneState,
                    sceneExpired = sceneExpired,
                    onEndScene = onEndScene,
                    onDismissScene = onDismissScene,
                    endSceneEnabled = endSceneEnabled,
                    commandsAllowed = commandsAllowed,
                    formatDuration = formatDuration
                )
            } else BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (isLandscape && maxWidth >= SPLIT_LAYOUT_MIN_WIDTH) {
                    OverviewScreenSplit(
                        profileName = profileName,
                        isProfileModified = isProfileModified,
                        profileProgress = profileProgress,
                        profileSceneManaged = profileSceneManaged,
                        tempTargetText = tempTargetText,
                        tempTargetState = tempTargetState,
                        tempTargetProgress = tempTargetProgress,
                        tempTargetReason = tempTargetReason,
                        tempTargetSceneManaged = tempTargetSceneManaged,
                        runningMode = runningMode,
                        runningModeText = runningModeText,
                        runningModeRemaining = runningModeRemaining,
                        runningModeProgress = runningModeProgress,
                        runningModeSceneManaged = runningModeSceneManaged,
                        tbrState = tbrState,
                        smbEnabled = smbEnabled,
                        isSimpleMode = isSimpleMode,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        manageViewModel = manageViewModel,
                        statusViewModel = statusViewModel,
                        statusLightsDef = statusLightsDef,
                        onNavigate = onNavigate,
                        onTbrChipClick = onTbrChipClick,
                        onIobChipClick = onIobChipClick,
                        paddingValues = contentPaddingValues,
                        activeSceneState = activeSceneState,
                        sceneExpired = sceneExpired,
                        onEndScene = onEndScene,
                        onDismissScene = onDismissScene,
                        endSceneEnabled = endSceneEnabled,
                        commandsAllowed = commandsAllowed,
                        formatDuration = formatDuration
                    )
                } else {
                    OverviewScreenStacked(
                        profileName = profileName,
                        isProfileModified = isProfileModified,
                        profileProgress = profileProgress,
                        profileSceneManaged = profileSceneManaged,
                        tempTargetText = tempTargetText,
                        tempTargetState = tempTargetState,
                        tempTargetProgress = tempTargetProgress,
                        tempTargetReason = tempTargetReason,
                        tempTargetSceneManaged = tempTargetSceneManaged,
                        runningMode = runningMode,
                        runningModeText = runningModeText,
                        runningModeRemaining = runningModeRemaining,
                        runningModeProgress = runningModeProgress,
                        runningModeSceneManaged = runningModeSceneManaged,
                        tbrState = tbrState,
                        smbEnabled = smbEnabled,
                        isSimpleMode = isSimpleMode,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        manageViewModel = manageViewModel,
                        statusViewModel = statusViewModel,
                        statusLightsDef = statusLightsDef,
                        onNavigate = onNavigate,
                        onTbrChipClick = onTbrChipClick,
                        onIobChipClick = onIobChipClick,
                        paddingValues = contentPaddingValues,
                        activeSceneState = activeSceneState,
                        sceneExpired = sceneExpired,
                        onEndScene = onEndScene,
                        onDismissScene = onDismissScene,
                        endSceneEnabled = endSceneEnabled,
                        commandsAllowed = commandsAllowed,
                        formatDuration = formatDuration
                    )
                }
            }

            // Calculation progress (IOB / graph data). Overlaid on top of content so it never reflows
            // the layout — previously a flow child of the content Column which caused the screen to jump.
            AnimatedVisibility(
                visible = calcProgress < 100,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(contentPaddingValues)
                    .fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { calcProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            PumpActivityFab(
                visible = showPumpFab,
                bolusState = bolusState,
                onClick = { showPumpActivityDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPaddingValues)
                    .padding(end = 16.dp, bottom = 128.dp + fabBottomOffset)
            )

            NotificationFab(
                notificationCount = notifications.size,
                highestLevel = notifications.minByOrNull { it.level.ordinal }?.level,
                onClick = { showNotificationSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPaddingValues)
                    .padding(end = 16.dp, bottom = 72.dp + fabBottomOffset)
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Reserves the toolbar's clearance unconditionally, regardless of whether the banner
        // below is visible - the caller-supplied modifier on EversenseCalibrationBanner is
        // applied inside its AnimatedVisibility content lambda, which composes nothing at all
        // (not even a zero-size placeholder) while never-shown, so a Modifier.padding() on the
        // banner itself doesn't reserve any space in that (most common) case. A Spacer here is
        // unconditional either way, so the toolbar is cleared whether the banner is showing,
        // hidden, or mid-transition, and OverviewContent needs no top padding of its own.
        Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
        EversenseCalibrationBanner(submittedAtMs = eversenseCalibrationSubmittedAt)
        OverviewContent(contentModifier = Modifier.weight(1f).fillMaxWidth())
    }

    if (showPumpActivityDialog) {
        PumpActivityDialog(
            bolusState = bolusState,
            pumpStatus = pumpStatusText,
            queueStatus = queueStatusText,
            isModal = false,
            onStop = onStopBolus,
            onDismiss = { showPumpActivityDialog = false }
        )
    }

    if (showNotificationSheet && notifications.isNotEmpty()) {
        NotificationBottomSheet(
            notifications = notifications,
            onDismissSheet = { showNotificationSheet = false },
            onDismissNotification = onDismissNotification,
            onNotificationActionClick = onNotificationActionClick
        )
    }
}
