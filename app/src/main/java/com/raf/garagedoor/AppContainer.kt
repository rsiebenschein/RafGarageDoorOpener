package com.raf.garagedoor

import android.content.Context
import com.raf.garagedoor.automation.ActivityLogRepository
import com.raf.garagedoor.automation.AutomationCoordinator
import com.raf.garagedoor.automation.BackgroundDoorCommands
import com.raf.garagedoor.automation.AutomationNotifications
import com.raf.garagedoor.automation.AutomationPermissions
import com.raf.garagedoor.automation.AutomationRegistrar
import com.raf.garagedoor.automation.AutomationSettingsRepository
import com.raf.garagedoor.automation.CommandCountdown
import com.raf.garagedoor.automation.DataStoreActivityLogRepository
import com.raf.garagedoor.automation.DataStoreAutomationSettingsRepository
import com.raf.garagedoor.automation.DataStoreDiagnosticsLog
import com.raf.garagedoor.automation.DiagnosticsLog
import com.raf.garagedoor.automation.leftopen.DataStoreLeftOpenStateRepository
import com.raf.garagedoor.automation.leftopen.LeftOpenCheckScheduler
import com.raf.garagedoor.automation.leftopen.LeftOpenMonitor
import com.raf.garagedoor.automation.leftopen.LeftOpenStateRepository
import com.raf.garagedoor.automation.DataStorePresenceRepository
import com.raf.garagedoor.automation.DepartureOnDoorOpen
import com.raf.garagedoor.automation.DoorAutomationExecutor
import com.raf.garagedoor.automation.PresenceRepository
import com.raf.garagedoor.automation.PresenceTracker
import com.raf.garagedoor.bluetooth.CarConnectionRepository
import com.raf.garagedoor.bluetooth.DataStoreCarConnectionRepository
import com.raf.garagedoor.bluetooth.PairedBluetoothDevices
import com.raf.garagedoor.door.DoorCommandEvents
import com.raf.garagedoor.door.ObservedDoorController
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.door.TransientRetry
import com.raf.garagedoor.geofence.DataStoreGeofenceSettingsRepository
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.location.CurrentLocationProvider
import com.raf.garagedoor.location.FusedCurrentLocationProvider
import com.raf.garagedoor.location.PreciseLocationUpdates
import com.raf.garagedoor.myq.MyQGarageDoorController
import com.raf.garagedoor.myq.api.MyQDeviceApi
import com.raf.garagedoor.myq.auth.AppCheckTokenMinter
import com.raf.garagedoor.myq.auth.AuthorizationUrlFactory
import com.raf.garagedoor.myq.auth.MyQSession
import com.raf.garagedoor.myq.auth.MyQTokenService
import com.raf.garagedoor.myq.config.MyQApiConfig
import com.raf.garagedoor.myq.http.MyQHttpExecutor
import com.raf.garagedoor.myq.http.MyQRequestHeaders
import com.raf.garagedoor.storage.EncryptedTokenStore
import com.raf.garagedoor.storage.KeystoreCipher
import com.raf.garagedoor.widget.WidgetDoorStatusStore
import com.raf.garagedoor.widget.WidgetDoorToggle
import com.raf.garagedoor.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Manual dependency graph; small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()

    /** When this build was installed, so a cleared log records which attempt it belongs to. */
    val installedAt: Instant = Instant.ofEpochMilli(context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime)

    /** Process-wide scope for work that must outlive any screen, such as keeping registrations in sync. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val config = MyQApiConfig()
    private val headers = MyQRequestHeaders(config)
    /** Short timeouts so a dead network fails fast and the retry policy can try again on the new one. */
    private val http = MyQHttpExecutor(
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
            .callTimeout(Duration.ofSeconds(HTTP_CALL_TIMEOUT_SECONDS))
            .build(),
    )

    val myQSession = MyQSession(
        config = config,
        tokenService = MyQTokenService(config, http, headers, AppCheckTokenMinter(config, http), clock),
        tokenStore = EncryptedTokenStore(context, KeystoreCipher(TOKEN_KEY_ALIAS)),
        authorizationUrlFactory = AuthorizationUrlFactory(config),
        clock = clock,
    )

    /** Every accepted command, from any sender, so a visible door screen shows the movement at once. */
    val doorCommandEvents = DoorCommandEvents()

    val widgetDoorStatus = WidgetDoorStatusStore(context)

    val widgetUpdater = WidgetUpdater(context.applicationContext, widgetDoorStatus, clock)

    val garageDoorController: GarageDoorController = ObservedDoorController(
        MyQGarageDoorController(MyQDeviceApi(config, http, headers, myQSession)),
        doorCommandEvents,
        onDoorsListed = widgetUpdater::onDoorsListed,
    )

    val geofenceSettings: GeofenceSettingsRepository = DataStoreGeofenceSettingsRepository(context)

    val currentLocationProvider: CurrentLocationProvider = FusedCurrentLocationProvider(context)

    val automationSettings: AutomationSettingsRepository = DataStoreAutomationSettingsRepository(context)
    val presenceRepository: PresenceRepository = DataStorePresenceRepository(context)
    val activityLog: ActivityLogRepository = DataStoreActivityLogRepository(context)
    val carConnection: CarConnectionRepository = DataStoreCarConnectionRepository(context)
    val pairedBluetoothDevices = PairedBluetoothDevices(context)
    val automationPermissions = AutomationPermissions(context)
    val automationNotifications = AutomationNotifications(context)
    val commandCountdown = CommandCountdown()
    val diagnosticsLog: DiagnosticsLog = DataStoreDiagnosticsLog(context, clock)
    val leftOpenState: LeftOpenStateRepository = DataStoreLeftOpenStateRepository(context)

    /**
     * Shared by every background door command. Field tests saw the phone cling to weak home Wi-Fi
     * for about 40 s at the edge of range, so this outlasts that before giving up.
     */
    val doorRetry = TransientRetry(
        delays = TransientRetry.BACKGROUND_DELAYS,
        onRetry = { attempt, error -> diagnosticsLog.record("myQ attempt $attempt failed, retrying: ${error.message}") },
    )

    val backgroundDoorCommands = BackgroundDoorCommands(garageDoorController, doorRetry, activityLog, automationNotifications, clock)

    val leftOpenMonitor = LeftOpenMonitor(
        controller = garageDoorController,
        settings = automationSettings,
        stateRepository = leftOpenState,
        activityLog = activityLog,
        diagnostics = diagnosticsLog,
        retry = doorRetry,
        clock = clock,
    )

    val leftOpenCheckScheduler = LeftOpenCheckScheduler(context.applicationContext, automationSettings)

    private val doorAutomationExecutor = DoorAutomationExecutor(
        controller = garageDoorController,
        settings = automationSettings,
        presence = presenceRepository,
        carConnection = carConnection,
        activityLog = activityLog,
        alerts = automationNotifications,
        countdown = commandCountdown,
        retry = doorRetry,
        diagnostics = diagnosticsLog,
        clock = clock,
        onDoorOpened = leftOpenCheckScheduler::onDoorOpened,
    )

    val presenceTracker = PresenceTracker(
        locationUpdates = PreciseLocationUpdates(context),
        geofences = geofenceSettings,
        settings = automationSettings,
        presence = presenceRepository,
        executor = doorAutomationExecutor,
        diagnostics = diagnosticsLog,
        clock = clock,
    )

    val departureOnDoorOpen = DepartureOnDoorOpen(context.applicationContext, automationSettings, presenceRepository, diagnosticsLog)

    val widgetDoorToggle = WidgetDoorToggle(
        context = context.applicationContext,
        store = widgetDoorStatus,
        updater = widgetUpdater,
        departureOnDoorOpen = departureOnDoorOpen,
        leftOpenChecks = leftOpenCheckScheduler,
        clock = clock,
    )

    val automationCoordinator = AutomationCoordinator(
        context = context.applicationContext,
        settings = automationSettings,
        geofences = geofenceSettings,
        presence = presenceRepository,
        registrar = AutomationRegistrar(context.applicationContext),
        permissions = automationPermissions,
        diagnostics = diagnosticsLog,
    )

    private companion object {
        const val HTTP_CONNECT_TIMEOUT_SECONDS = 8L
        const val HTTP_CALL_TIMEOUT_SECONDS = 15L
        const val TOKEN_KEY_ALIAS = "myq_session_key"
    }
}
