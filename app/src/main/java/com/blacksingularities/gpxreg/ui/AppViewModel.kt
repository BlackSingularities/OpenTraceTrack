package com.blacksingularities.gpxreg.ui

import com.blacksingularities.gpxreg.R
import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.gpxreg.GpxRegApp
import com.blacksingularities.gpxreg.data.AppSettings
import com.blacksingularities.gpxreg.data.Track
import com.blacksingularities.gpxreg.data.TrackPoint
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Waypoint
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.i18n.trp
import com.blacksingularities.gpxreg.CrashLog
import com.blacksingularities.gpxreg.record.RouteGuide
import com.blacksingularities.gpxreg.widget.QuickActions
import android.content.Intent
import com.blacksingularities.gpxreg.io.DataPort
import com.blacksingularities.gpxreg.io.ExportFormat
import com.blacksingularities.gpxreg.io.ExportSplit
import com.blacksingularities.gpxreg.record.RecEvent
import com.blacksingularities.gpxreg.record.Recorder
import com.blacksingularities.gpxreg.update.UPDATE_API
import com.blacksingularities.gpxreg.update.UpdateManager
import com.blacksingularities.gpxreg.update.UpdateState
import com.blacksingularities.gpxreg.update.friendlyError
import com.blacksingularities.gpxreg.update.parseReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab { RECORD, TRACKS, STATS }

/** Krótki komunikat na dole ekranu, opcjonalnie z akcją („Cofnij”, „Otwórz”). */
data class Snack(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** Pliki gotowe do zapisania lub udostępnienia. */
data class ExportJob(val files: List<File>, val format: ExportFormat)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GpxRegApp
    val db = app.db
    val port = app.port
    val settings: StateFlow<AppSettings> = app.settings.state

    fun updateSettings(t: (AppSettings) -> AppSettings) {
        val before = app.settings.value.language
        app.settings.update(t)
        if (app.settings.value.language != before) {
            I18n.apply(app.settings.value.language)
            QuickActions.publishShortcuts(app)
        }
    }

    // ------------------------------------------------------------ nawigacja

    var tab by mutableStateOf(Tab.RECORD)
    var openTrack by mutableStateOf<Long?>(null)
    var settingsOpen by mutableStateOf(false)
    var updateSheet by mutableStateOf(false)
    var aboutOpen by mutableStateOf(false)

    /** Skrót, widżet albo kafelek poprosił o start nagrywania – wykonuje go ekran nagrywania. */
    var startRequested by mutableStateOf(false)

    // ------------------------------------------------------------ komunikaty

    private val _snacks = MutableSharedFlow<Snack>(extraBufferCapacity = 4)
    val snacks: SharedFlow<Snack> = _snacks.asSharedFlow()
    fun snack(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        _snacks.tryEmit(Snack(text, action, onAction))
    }

    // ------------------------------------------------------------ trasy

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    var tracksLoaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            db.changes.collect {
                _tracks.value = withContext(Dispatchers.IO) { db.tracks() }
                tracksLoaded = true
            }
        }
        viewModelScope.launch {
            Recorder.events.collect { e ->
                when (e) {
                    is RecEvent.Saved -> snack(tr(R.string.trasa_zapisana), tr(R.string.otworz)) {
                        tab = Tab.TRACKS
                        openTrack = e.trackId
                    }
                    RecEvent.Discarded -> snack(tr(R.string.nagranie_odrzucone))
                    is RecEvent.ModeChanged -> if (e.auto) {
                        snack(tr(R.string.zmieniono_na_x, e.to.label), tr(R.string.cofnij)) { Recorder.switchMode(e.from) }
                    } else {
                        snack(tr(R.string.nowy_fragment_x, e.to.label))
                    }
                    is RecEvent.WaypointAdded -> snack(tr(R.string.dodano_x, e.name))
                    RecEvent.Overspeed -> Unit
                }
            }
        }
        Recorder.checkInterrupted()
    }

    /** Szczegóły trasy (z punktami) – ładowane na żądanie. */
    data class TrackDetail(val track: Track, val points: List<TrackPoint>, val waypoints: List<Waypoint>)

    suspend fun loadDetail(id: Long): TrackDetail? = withContext(Dispatchers.IO) {
        val t = db.track(id) ?: return@withContext null
        TrackDetail(t, db.points(id), db.waypoints(id))
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            snack(e.message ?: tr(R.string.nieznany_blad))
        }
    }

    fun rename(id: Long, name: String) = io { db.rename(id, name.trim().ifEmpty { tr(R.string.trasa) }) }
    fun setNotes(id: Long, notes: String) = io { db.setNotes(id, notes) }
    fun toggleFavorite(t: Track) = io { db.setFavorite(t.id, !t.favorite) }

    fun delete(ids: Collection<Long>) = io {
        db.deleteTracks(ids)
        snack(if (ids.size == 1) tr(R.string.trasa_usunieta) else trp(R.plurals.tracks_deleted, ids.size))
    }

    fun setSegmentMode(segId: Long, mode: TransportMode) = io { db.setSegmentMode(segId, mode) }
    fun splitSegment(segId: Long, at: Long, mode: TransportMode) = io {
        db.splitSegment(segId, at, mode)
        snack(tr(R.string.fragment_podzielony))
    }
    fun mergeWithNext(segId: Long) = io { if (db.mergeWithNext(segId)) snack(tr(R.string.fragmenty_polaczone)) }
    fun deleteSegment(segId: Long) = io { if (db.deleteSegment(segId)) snack(tr(R.string.fragment_usuniety)) }
    fun renameWaypoint(id: Long, name: String, note: String) = io { db.updateWaypoint(id, name, note) }
    fun deleteWaypoint(id: Long) = io { db.deleteWaypoint(id) }

    // ------------------------------------------------------------ eksport / import

    var exporting by mutableStateOf(false)
        private set

    /** Przygotowuje pliki; [then] dostaje gotowe zadanie (zapis albo udostępnienie). */
    fun prepareExport(
        trackIds: List<Long>,
        format: ExportFormat,
        split: ExportSplit,
        segmentIds: Set<Long>? = null,
        then: (ExportJob) -> Unit,
    ) {
        exporting = true
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { port.produce(trackIds, format, split, segmentIds) }
                if (files.isEmpty()) snack(tr(R.string.brak_danych_do_eksportu)) else then(ExportJob(files, format))
            } catch (e: Exception) {
                snack(tr(R.string.eksport_nieudany_x, e.message ?: ""))
            } finally {
                exporting = false
            }
        }
    }

    fun saveExport(job: ExportJob, uri: Uri) = io {
        port.copyTo(job.files.single(), uri)
        snack(tr(R.string.zapisano_x, job.files.single().name))
    }

    fun saveExportToFolder(job: ExportJob, tree: Uri) = io {
        val n = port.copyToFolder(job.files, tree, job.format.mime)
        snack(tr(R.string.zapisano_plikow_x, n))
    }

    fun zipExport(job: ExportJob): File = port.zip(job.files, "OpenTraceTrack-export")

    fun importGpx(uris: List<Uri>) = io {
        val r = port.importGpx(uris)
        val msg = buildString {
            append(tr(R.string.zaimportowano_trasy_x, r.trackIds.size))
            if (r.skipped > 0) append(" · ").append(tr(R.string.pominieto_juz_sa_x, r.skipped))
            if (r.failed.isNotEmpty()) append(" · ").append(tr(R.string.bledne_pliki_x, r.failed.joinToString()))
        }
        if (r.trackIds.size == 1) {
            snack(msg, tr(R.string.otworz)) {
                tab = Tab.TRACKS
                openTrack = r.trackIds.first()
            }
        } else {
            snack(msg)
        }
    }

    fun exportBackup(uri: Uri) = io {
        port.exportBackup(uri)
        snack(tr(R.string.kopia_bazy_zapisana))
    }

    fun importBackup(uri: Uri) = io {
        val r = port.importBackup(uri)
        snack(tr(R.string.dodano_trasy_x_pominieto_juz_sa, r.imported, r.skipped))
    }

    // ------------------------------------------------------------ trasa wzorcowa

    fun followRoute(trackId: Long) = io {
        RouteGuide.follow(trackId)
        val name = RouteGuide.state.value?.name ?: return@io
        snack(tr(R.string.guide_set, name), tr(R.string.nagrywanie)) {
            openTrack = null
            tab = Tab.RECORD
        }
    }

    fun stopFollowing() = RouteGuide.clear()

    // ------------------------------------------------------------ automatyczna kopia

    fun setAutoBackupFolder(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val old = settings.value.autoBackupUri
        if (old.isNotBlank() && old != tree.toString()) {
            runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
        }
        runCatching { app.contentResolver.takePersistableUriPermission(tree, flags) }
        updateSettings { it.copy(autoBackupUri = tree.toString(), lastAutoBackup = 0) }
        backupNow()
    }

    fun disableAutoBackup() {
        val old = settings.value.autoBackupUri
        if (old.isNotBlank()) {
            runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        updateSettings { it.copy(autoBackupUri = "") }
    }

    fun backupNow() = io {
        val uri = settings.value.autoBackupUri.takeIf { it.isNotBlank() } ?: return@io
        try {
            port.autoBackup(Uri.parse(uri))
            app.settings.update { it.copy(lastAutoBackup = System.currentTimeMillis()) }
            snack(tr(R.string.kopia_bazy_zapisana))
        } catch (e: Exception) {
            snack(tr(R.string.backup_failed))
        }
    }

    // ------------------------------------------------------------ dziennik błędów

    var crashLogAvailable by mutableStateOf(CrashLog.exists())
        private set

    fun crashLogIntent(): Intent = port.shareIntent(listOf(CrashLog.file), "text/plain")

    fun clearCrashLog() {
        CrashLog.clear()
        crashLogAvailable = false
    }

    // ------------------------------------------------------------ aktualizacje

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var whatsNew by mutableStateOf<Pair<String, String>?>(null)
    private var updateJob: Job? = null
    private var lastCheck = 0L

    fun checkUpdate(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            update = UpdateState.Checking
            update = try {
                val info = withContext(Dispatchers.IO) { parseReleases(UpdateManager.httpGet(UPDATE_API), UpdateManager.currentVersion) }
                lastCheck = System.currentTimeMillis()
                when {
                    info == null -> UpdateState.UpToDate
                    silent && info.version == settings.value.skippedVersion -> UpdateState.Idle
                    else -> UpdateState.Available(info)
                }
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed(friendlyError(e))
            }
        }
    }

    fun checkUpdateIfDue() {
        if (!settings.value.autoUpdate) return
        if (System.currentTimeMillis() - lastCheck < 6 * 3600_000L) return
        val u = update
        if (u is UpdateState.Downloading || u is UpdateState.Ready || u is UpdateState.Installing) return
        checkUpdate(silent = true)
    }

    fun skipVersion() {
        val v = (update as? UpdateState.Available)?.info?.version ?: return
        updateSettings { it.copy(skippedVersion = v) }
        update = UpdateState.Idle
        updateSheet = false
    }

    fun later() {
        updateSheet = false
        bannerHidden = true
    }

    var bannerHidden by mutableStateOf(false)

    fun downloadAndInstall() {
        val info = when (val u = update) {
            is UpdateState.Available -> u.info
            is UpdateState.Failed -> u.info
            is UpdateState.Ready -> {
                install(u)
                return
            }
            else -> null
        } ?: run {
            checkUpdate()
            return
        }
        if (!UpdateManager.canInstall(app)) {
            UpdateManager.openInstallPermissionSettings(app)
            snack(tr(R.string.zezwol_opentracetrack_na_instalowanie_aplika))
            return
        }
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            update = UpdateState.Downloading(info, 0f, 0)
            try {
                val file = UpdateManager.download(app, info) { p, bytes ->
                    viewModelScope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(info, p, bytes) }
                }
                val ready = UpdateState.Ready(info, file)
                update = ready
                install(ready)
            } catch (e: kotlinx.coroutines.CancellationException) {
                update = UpdateState.Available(info)
            } catch (e: Exception) {
                update = UpdateState.Failed(friendlyError(e), info)
            }
        }
    }

    fun cancelDownload() {
        updateJob?.cancel()
    }

    private fun install(ready: UpdateState.Ready) {
        if (Recorder.state.value.active) {
            snack(tr(R.string.trwa_nagrywanie_zainstaluj_aktualizacje_po_j))
            return
        }
        viewModelScope.launch {
            update = UpdateState.Installing(ready.info)
            try {
                UpdateManager.install(app, ready.file, ready.info)
            } catch (e: Exception) {
                update = UpdateState.Failed(friendlyError(e), ready.info)
                return@launch
            }
            // Instalator przejmuje ekran; gdy użytkownik anuluje, wracamy do gotowego pliku.
            kotlinx.coroutines.delay(15_000)
            if (update is UpdateState.Installing) update = ready
        }
    }

    val updatePending: Boolean
        get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing

    init {
        checkUpdateIfDue()
        whatsNew = UpdateManager.takeWhatsNew(app)
        UpdateManager.clearUpdatedNotification(app)
    }
}
