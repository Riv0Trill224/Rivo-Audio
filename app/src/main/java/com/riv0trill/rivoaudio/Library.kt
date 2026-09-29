package com.riv0trill.rivoaudio

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.Executors

fun stableID(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
fun fetchText(url: String): String {
    val c = URL(url).openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 15000; c.readTimeout = 15000; c.setRequestProperty("User-Agent", "RivoAudio/0.3.0 (https://github.com/Riv0Trill224/Rivo-Audio)")
    return try { check(c.responseCode in 200..299) { "Servicio no disponible (${c.responseCode})" }; c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
}

data class Track(val id: String, val path: String, var title: String, var artist: String, var album: String, val duration: Long, val video: Boolean, var rating: Int = 0, var plays: Int = 0, var verified: Boolean = false, var folder: String = "", var chartNote: String = "", var sourceName: String = "", var sourceUri: String = "") {
    fun json() = JSONObject().put("id",id).put("path",path).put("title",title).put("artist",artist).put("album",album).put("duration",duration).put("video",video).put("rating",rating).put("plays",plays).put("verified",verified).put("folder",folder).put("chartNote",chartNote).put("sourceName",sourceName).put("sourceUri",sourceUri)
    companion object { fun from(j: JSONObject) = Track(j.getString("id"),j.getString("path"),j.getString("title"),j.getString("artist"),j.getString("album"),j.optLong("duration"),j.optBoolean("video"),j.optInt("rating"),j.optInt("plays"),j.optBoolean("verified"),j.optString("folder"),j.optString("chartNote"),j.optString("sourceName"),j.optString("sourceUri")) }
}
class Library(private val context: Context) {
    val catalog=Catalog(context)
    @Volatile var scanning=false; private set
    val root = File(context.filesDir,"Music").apply { mkdirs() }
    val lyrics = File(context.filesDir,"Lyrics").apply { mkdirs() }
    val covers = File(context.filesDir,"Artwork").apply { mkdirs() }
    private val index = File(context.filesDir,"library.json")
    private val lyricIndex = File(lyrics,"index.json")
    val worker = Executors.newSingleThreadExecutor()
    @Volatile var tracks: List<Track> = emptyList(); private set
    init { runCatching { val a=JSONArray(index.readText()); tracks=(0 until a.length()).map { Track.from(a.getJSONObject(it)) }.toMutableList() } }
    @Synchronized fun save() { val f=File(index.path+".tmp"); f.writeText(JSONArray(tracks.map { it.json() }).toString()); check(f.renameTo(index)) }
    @Synchronized fun importFile(uri: Uri, name: String, refresh: Boolean = false): Track? {
        val ext=name.substringAfterLast('.',"").lowercase()
        if(ext !in listOf("mp3","m4a","aac","alac","wav","aif","aiff","caf","flac","mp4","m4v","mov","ogg","opus")) return null
        val id=stableID(uri.toString()); val existing=tracks.firstOrNull {it.id==id}; if(existing!=null&&!refresh){if(existing.sourceName.isBlank()){existing.sourceName=name;existing.sourceUri=uri.toString();save()};return existing}
        val file=File(root,"$id.$ext")
        val temp=File(file.path+".import");context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: error("No se puede leer $name");check(temp.length()>0);check(temp.renameTo(file))
        check(file.length()>0) { "Archivo vacío: $name" }
        val m=MediaMetadataRetriever()
        val track=try {
            m.setDataSource(file.path)
            val cover=m.embeddedPicture
            if(cover!=null&&!File(covers,"$id.jpg").exists()) File(covers,"$id.jpg").writeBytes(cover)
            val info=catalog.detail(id);if(info.optString("genre").isBlank())info.put("genre",m.extractMetadata(6) ?: "");if(info.optString("year").isBlank())info.put("year",m.extractMetadata(8) ?: "");catalog.saveDetail(id,info)
            val parts=name.substringBeforeLast('.').split(" - ")
            Track(id,file.path,m.extractMetadata(7) ?: if(parts.size>1)parts.drop(1).joinToString(" - ") else name.substringBeforeLast('.'),m.extractMetadata(2) ?: if(parts.size>1)parts[0] else "Artista desconocido",m.extractMetadata(1) ?: "Sin álbum",m.extractMetadata(9)?.toLongOrNull() ?: 0,ext in listOf("mp4","m4v","mov"),verified=m.extractMetadata(7)!=null&&m.extractMetadata(2)!=null,sourceName=name,sourceUri=uri.toString())
        } finally { m.release() }
        if(existing!=null){track.title=existing.title;track.artist=existing.artist;track.album=existing.album;track.rating=existing.rating;track.plays=existing.plays;track.verified=existing.verified;track.folder=existing.folder;track.chartNote=existing.chartNote;tracks=tracks.filter {it.id!=existing.id}}
        tracks=(tracks+track).sortedBy {it.title.lowercase()};save();return track
    }
    fun importFolder(uri: Uri) {
        val folder=DocumentFile.fromTreeUri(context,uri) ?: error("Carpeta no accesible")
        fun visit(dir: DocumentFile) {
            val children=dir.listFiles()
            children.filter { it.isDirectory }.forEach { visit(it) }
            children.filter { it.isFile }.forEach { f ->
                val track=importFile(f.uri,f.name ?: "Audio",true)
                track?.folder=uri.toString()
                if(track!=null&&!lyricFile(track).exists()&&!context.getSharedPreferences("rivo",0).getBoolean("deleted."+track.id,false)) children.firstOrNull { it.name==f.name?.substringBeforeLast('.')+".lrc" }?.let { lrc ->
                    context.contentResolver.openInputStream(lrc.uri)?.bufferedReader()?.use { saveLyrics(track,it.readText(),"Archivo local") }
                }
            }
        }
        visit(folder);save()
        val folders=folders();if((0 until folders.length()).none {folders.getJSONObject(it).optString("uri")==uri.toString()})folders.put(JSONObject().put("uri",uri.toString()).put("name",folder.name ?: "Carpeta"))
        File(context.filesDir,"folders.json").writeText(folders.toString())
    }
    fun fullScan():String {
        synchronized(this){check(!scanning){"Ya hay un escaneo en curso"};scanning=true}
        val errors=mutableListOf<String>()
        try {
            folders().objects().forEach {f->runCatching {importFolder(Uri.parse(f.getString("uri")))}.onFailure {errors.add(f.optString("name"))}}
            tracks.toList().filter {it.folder.isBlank()&&it.sourceUri.isNotBlank()}.forEach {t->runCatching {importFile(Uri.parse(t.sourceUri),t.sourceName,true)}.onFailure {errors.add(t.title)}}
            val known=tracks.map {File(it.path).canonicalPath}.toSet()
            root.walkTopDown().filter {it.isFile&&it.canonicalPath !in known}.toList().forEach {f->runCatching {importFile(Uri.fromFile(f),f.name)}}
            tracks.toList().forEach {t->if(File(t.path).exists()){val m=MediaMetadataRetriever();runCatching {m.setDataSource(t.path);val info=catalog.detail(t.id);if(info.optString("genre").isBlank())info.put("genre",m.extractMetadata(6) ?: "");if(info.optString("year").isBlank())info.put("year",m.extractMetadata(8) ?: "");catalog.saveDetail(t.id,info)};m.release()}}
            synchronized(this){tracks=tracks.filter {File(it.path).exists()};save()}
            return "Escaneo completo: ${tracks.size} pistas."+(if(errors.isEmpty())"" else "\nFuentes no accesibles: "+errors.distinct().joinToString())
        }finally{scanning=false}
    }
    fun folders()=runCatching {JSONArray(File(context.filesDir,"folders.json").readText())}.getOrDefault(JSONArray())
    @Synchronized fun removeFolder(uri:String) {tracks.filter {it.folder==uri}.forEach {File(it.path).delete()};tracks=tracks.filter {it.folder!=uri};save();val old=folders();val remaining=JSONArray();for(i in 0 until old.length())if(old.getJSONObject(i).optString("uri")!=uri)remaining.put(old.getJSONObject(i));File(context.filesDir,"folders.json").writeText(remaining.toString())}

    @Synchronized fun records(): JSONObject = runCatching { JSONObject(lyricIndex.readText()) }.getOrDefault(JSONObject())
    fun lyricFile(t: Track)=File(lyrics,t.id+".lrc")
    @Synchronized fun readLyrics(t: Track)=lyricFile(t).takeIf { it.exists() }?.readText() ?: ""
    @Synchronized fun saveLyrics(t: Track,text: String,source: String) {
        val f=lyricFile(t); val temp=File(f.path+".tmp"); temp.writeText(text); check(temp.renameTo(f))
        val r=records();r.put(t.id,JSONObject().put("file",f.name).put("source",source).put("updated",System.currentTimeMillis()))
        lyricIndex.writeText(r.toString()); context.getSharedPreferences("rivo",0).edit().remove("deleted."+t.id).apply()
    }
    @Synchronized fun deleteLyrics(t: Track) { lyricFile(t).delete();val r=records();r.remove(t.id);lyricIndex.writeText(r.toString());context.getSharedPreferences("rivo",0).edit().putBoolean("deleted."+t.id,true).apply() }
    fun lookup(t: Track): List<JSONObject> {
        val u="https://lrclib.net/api/search?track_name="+URLEncoder.encode(t.title,"UTF-8")+"&artist_name="+URLEncoder.encode(t.artist,"UTF-8")
        val a=JSONArray(fetchText(u));return (0 until a.length()).map { a.getJSONObject(it) }.filter { !it.isNull("syncedLyrics") && it.optString("syncedLyrics").isNotBlank() }
    }
    fun autoLyrics(t: Track) {
        val p=context.getSharedPreferences("rivo",0)
        if(!p.getBoolean("lyrics.auto",true)||p.getBoolean("deleted."+t.id,false)||lyricFile(t).exists()||normalized(t.title).isEmpty()||normalized(t.artist).isEmpty()||t.artist=="Artista desconocido"||System.currentTimeMillis()-p.getLong("attempt."+t.id,0)<86400000) return
        p.edit().putLong("attempt."+t.id,System.currentTimeMillis()).apply()
        worker.execute { runCatching { val a=lookup(t).filter { normalized(it.optString("trackName"))==normalized(t.title)&&normalized(it.optString("artistName"))==normalized(t.artist) }; synchronized(this) {if(a.size==1 && !lyricFile(t).exists() && !p.getBoolean("deleted."+t.id,false)) saveLyrics(t,a[0].getString("syncedLyrics"),"LRCLIB · automática")} } }
    }
}
