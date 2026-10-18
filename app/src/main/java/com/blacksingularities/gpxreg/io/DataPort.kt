package com.blacksingularities.gpxreg.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.blacksingularities.gpxreg.BuildConfig
import com.blacksingularities.gpxreg.data.TrackDb
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    GPX("gpx", "application/gpx+xml", "GPX"),
    GEOJSON("geojson", "application/geo+json", "GeoJSON"),
    KML("kml", "application/vnd.google-earth.kml+xml", "KML"),
    CSV("csv", "text/csv", "CSV"),
}

/** Jak podzielić trasę na pliki. */
enum class ExportSplit {
    /** Jeden plik – fragmenty jako osobne ścieżki. */
    WHOLE,

    /** Osobny plik dla każdego fragmentu (przesiadki). */
    PER_SEGMENT,

    /** Osobny plik dla każdego środka transportu (np. wszystkie odcinki rowerowe razem). */
    PER_MODE,
}

/** Eksport i import plików oraz kopii bazy. */
class DataPort(private val context: Context, private val db: TrackDb) {

    private val shareDir get() = File(context.cacheDir, "share").apply { mkdirs() }

    /** Dokument trasy; [segmentIds] – tylko wybrane fragmenty. */
    fun document(trackId: Long, segmentIds: Set<Long>? = null): GpxDocument {
        val track = db.track(trackId) ?: error(tr("Nie znaleziono trasy"))
        val pts = db.points(trackId).groupBy { it.segmentId }
        val units = Units(com.blacksingularities.gpxreg.data.UnitSystem.METRIC)
        val segs = track.segments.filter { segmentIds == null || it.id in segmentIds }.map { s ->
            val sp = pts[s.id].orEmpty().mapIndexed { i, p -> if (i == 0) p.copy(gap = false) else p }
            GpxSegment(
                index = s.index,
                mode = s.mode,
                name = if (track.segments.size > 1) "${track.name} – ${s.mode.label} (${s.index + 1}/${track.segments.size})" else track.name,
                stats = s.stats,
                points = sp,
            )
        }
        val desc = buildString {
            append(units.fmtDist(track.stats.distance)).append(" · ").append(Units.duration(track.stats.duration))
            if (track.segments.size > 1) append(" · ").append(track.segments.joinToString(" → ") { it.mode.label })
            if (track.notes.isNotBlank()) append("\n\n").append(track.notes)
        }
        return GpxDocument(
            name = track.name,
            description = desc,
            uuid = if (segmentIds == null) track.uuid else null,
            time = track.startTime,
            segments = segs,
            waypoints = if (segmentIds == null) db.waypoints(trackId) else emptyList(),
            creator = "OpenTraceTrack ${BuildConfig.VERSION_NAME} – $REPO_URL",
        )
    }

    /** Tworzy pliki w katalogu podręcznym (do udostępnienia lub zapisu). */
    fun produce(trackIds: List<Long>, format: ExportFormat, split: ExportSplit, segmentIds: Set<Long>? = null): List<File> {
        shareDir.listFiles()?.forEach { it.delete() }
        val out = ArrayList<File>()
        for (id in trackIds) {
            val doc = document(id, segmentIds)
            val base = baseName(doc)
            val docs: List<Pair<String, GpxDocument>> = when (split) {
                ExportSplit.WHOLE -> listOf(base to doc)
                ExportSplit.PER_SEGMENT -> doc.segments.map { s ->
                    "${base}_%02d_%s".format(s.index + 1, s.mode.gpxType) to doc.copy(
                        name = s.name,
                        uuid = null,
                        segments = listOf(s),
                        waypoints = doc.waypoints.filter { w -> s.points.isNotEmpty() && w.time in s.points.first().time..s.points.last().time },
                    )
                }
                ExportSplit.PER_MODE -> doc.segments.groupBy { it.mode }.map { (mode, list) ->
                    "${base}_${mode.gpxType}" to doc.copy(
                        name = "${doc.name} – ${mode.label}",
                        uuid = null,
                        segments = list,
                        waypoints = emptyList(),
                    )
                }
            }
            for ((name, d) in docs) {
                val f = unique(File(shareDir, "$name.${format.ext}"))
                f.bufferedWriter(Charsets.UTF_8).use { w ->
                    when (format) {
                        ExportFormat.GPX -> GpxWriter.write(d, w)
                        ExportFormat.GEOJSON -> GeoJsonWriter.write(d, w)
                        ExportFormat.KML -> KmlWriter.write(d, w)
                        ExportFormat.CSV -> CsvWriter.write(d, w)
                    }
                }
                out += f
            }
        }
        return out
    }

    fun zip(files: List<File>, name: String): File {
        val z = File(shareDir, "$name.zip")
        ZipOutputStream(z.outputStream().buffered()).use { zos ->
            for (f in files) {
                zos.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return z
    }

    fun shareIntent(files: List<File>, mime: String): Intent {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, context.packageName + ".files", it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = if (uris.size == 1) mime else "*/*"
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, tr("Udostępnij")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Zapis pliku pod adres wybrany w systemowym oknie „Zapisz jako”. */
    fun copyTo(file: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "wt")?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error(tr("Nie można zapisać pliku"))
    }

    /** Zapis wielu plików do folderu wybranego przez użytkownika. */
    fun copyToFolder(files: List<File>, tree: Uri, mime: String): Int {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var n = 0
        for (f in files) {
            val doc = DocumentsContract.createDocument(context.contentResolver, dir, mime, f.name) ?: continue
            context.contentResolver.openOutputStream(doc)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            n++
        }
        return n
    }

    // ------------------------------------------------------------ import

    data class ImportResult(val trackIds: List<Long>, val skipped: Int, val failed: List<String>)

    /**
     * Import plików GPX. Każdy plik to jedna trasa; jego ścieżki `<trk>` stają się fragmentami
     * z rozpoznanym środkiem transportu. Plik z eksportu OpenTraceTrack wraca z tym samym identyfikatorem.
     */
    fun importGpx(uris: List<Uri>): ImportResult {
        val ids = ArrayList<Long>()
        var skipped = 0
        val failed = ArrayList<String>()
        for (uri in uris) {
            val name = displayName(uri)
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: error(tr("Nie można otworzyć pliku"))
                val doc = stream.use { GpxReader.read(it.buffered(), name.substringBeforeLast('.')) }
                val segs = doc.tracks.map { t -> TrackDb.ImportedSegment(t.mode, t.segments.flatten()) }
                val notes = doc.description
                val id = if (doc.uuid != null) {
                    db.importTrack(doc.name, notes, segs, doc.waypoints, uuid = doc.uuid)
                } else {
                    db.importTrack(doc.name, notes, segs, doc.waypoints)
                }
                if (id != null) ids += id else skipped++
            } catch (e: Exception) {
                failed += name
            }
        }
        db.notifyChanged()
        return ImportResult(ids, skipped, failed)
    }

    fun exportBackup(target: Uri) {
        val tmp = File(shareDir, "backup.sqlite")
        db.exportTo(tmp)
        copyTo(tmp, target)
        tmp.delete()
    }

    fun importBackup(source: Uri): TrackDb.MergeResult {
        val tmp = File(context.cacheDir, "import.sqlite")
        context.contentResolver.openInputStream(source)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: error(tr("Nie można otworzyć pliku"))
        try {
            return db.mergeFrom(tmp)
        } finally {
            tmp.delete()
        }
    }

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "track.gpx"

    companion object {
        fun backupName(): String = "OpenTraceTrack-" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".sqlite"

        /** „2026-10-18_1405_Trasa_do_pracy” – bez znaków, których nie lubią systemy plików. */
        fun baseName(doc: GpxDocument): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(doc.time))
            val clean = doc.name
                .replace(Regex("[\\\\/:*?\"<>|,;\\p{Cntrl}]"), "")
                .replace(Regex("\\s+"), "_")
                .trim('_', '.')
                .take(60)
            return if (clean.isEmpty()) date else "${date}_$clean"
        }

        private fun unique(f: File): File {
            if (!f.exists()) return f
            var i = 2
            while (true) {
                val c = File(f.parentFile, f.nameWithoutExtension + "_" + i + "." + f.extension)
                if (!c.exists()) return c
                i++
            }
        }
    }
}
