package com.blacksingularities.gpxreg.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.blacksingularities.gpxreg.GpxRegApp
import com.blacksingularities.gpxreg.MainActivity
import com.blacksingularities.gpxreg.R
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.record.RecState
import com.blacksingularities.gpxreg.record.RecStatus
import com.blacksingularities.gpxreg.record.Recorder

/** Akcje skrótów, widżetu i kafelka – obsługuje je [MainActivity]. */
object QuickActions {
    const val START = "com.blacksingularities.gpxreg.action.START_RECORDING"
    const val OPEN_RECORDING = "com.blacksingularities.gpxreg.action.OPEN_RECORDING"

    fun intent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getActivity(context, action.hashCode(), intent(context, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Skróty na ikonie aplikacji (przytrzymanie ikony w launcherze). */
    fun publishShortcuts(context: Context) {
        runCatching {
            val sm = context.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
            val start = android.content.pm.ShortcutInfo.Builder(context, "start")
                .setShortLabel(tr(R.string.shortcut_start))
                .setLongLabel(tr(R.string.shortcut_start_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_record))
                .setIntent(intent(context, START))
                .build()
            val tracks = android.content.pm.ShortcutInfo.Builder(context, "tracks")
                .setShortLabel(tr(R.string.trasy))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_tracks))
                .setIntent(intent(context, MainActivity.ACTION_TRACKS))
                .build()
            sm.dynamicShortcuts = listOf(start, tracks)
        }
    }

    /** Odświeża widżety i kafelek po zmianie stanu nagrywania. */
    fun refresh(context: Context, s: RecState, units: Units) {
        RecordWidget.update(context, s, units)
        runCatching { TileService.requestListeningState(context, ComponentName(context, RecordTile::class.java)) }
    }
}

/** Widżet na ekranie głównym: stan nagrywania i jednym dotknięciem start (albo powrót do nagrania). */
class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as? GpxRegApp ?: return
        update(context, Recorder.state.value, Units(app.settings.value.units))
    }

    companion object {
        fun update(context: Context, s: RecState, units: Units) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, RecordWidget::class.java)) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            val (title, text) = when (s.status) {
                RecStatus.IDLE -> tr(R.string.widget_idle) to tr(R.string.widget_idle_hint)
                RecStatus.RECORDING -> tr(R.string.nagrywanie_trasy_x, s.mode.label) to "${units.fmtDist(s.total.distance)} · ${Units.duration(s.elapsed)}"
                RecStatus.PAUSED, RecStatus.AUTO_PAUSED -> tr(R.string.nagrywanie_wstrzymane) to "${units.fmtDist(s.total.distance)} · ${Units.duration(s.elapsed)}"
            }
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_text, text)
            views.setInt(R.id.widget_icon, "setBackgroundResource", if (s.active) R.drawable.widget_button else R.drawable.widget_button_idle)
            views.setContentDescription(R.id.widget_root, "$title. $text")
            views.setOnClickPendingIntent(R.id.widget_root, QuickActions.pending(context, if (s.active) QuickActions.OPEN_RECORDING else QuickActions.START))
            runCatching { manager.updateAppWidget(ids, views) }
        }
    }
}

/** Kafelek w szybkich ustawieniach: start nagrywania albo powrót do trwającego nagrania. */
class RecordTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val s = Recorder.state.value
        tile.state = if (s.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (s.active) tr(R.string.nagrywanie) else tr(R.string.shortcut_start)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (s.active) s.mode.label else null
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val action = if (Recorder.state.value.active) QuickActions.OPEN_RECORDING else QuickActions.START
        val open = {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(QuickActions.pending(this, action))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(QuickActions.intent(this, action))
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
