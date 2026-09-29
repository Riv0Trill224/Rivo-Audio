package com.riv0trill.rivoaudio

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.URLEncoder

fun JSONArray.objects()=(0 until length()).mapNotNull {optJSONObject(it)}
fun JSONArray.strings()=(0 until length()).map {optString(it)}
fun exactPair(a:Track,b:Track)=a.video!=b.video&&a.sourceName.isNotBlank()&&a.sourceName.substringBeforeLast('.')==b.sourceName.substringBeforeLast('.')
class Catalog(context:Context){
    private val file=File(context.filesDir,"extras.json")
    private var root=runCatching {JSONObject(file.readText())}.getOrDefault(JSONObject())
    @Synchronized fun detail(id:String):JSONObject=JSONObject(root.optJSONObject("details")?.optJSONObject(id)?.toString() ?: "{}")
    @Synchronized fun saveDetail(id:String,detail:JSONObject){val all=root.optJSONObject("details") ?: JSONObject();all.put(id,detail);root.put("details",all);save()}
    @Synchronized fun playlists():List<JSONObject> =(root.optJSONArray("playlists") ?: JSONArray()).objects().map {JSONObject(it.toString())}
    @Synchronized fun savePlaylist(list:JSONObject){val lists=playlists().filter {it.optString("id")!=list.optString("id")}+list;root.put("playlists",JSONArray(lists));save()}
    @Synchronized fun deletePlaylist(id:String){root.put("playlists",JSONArray(playlists().filter {it.optString("id")!=id}));save()}
    @Synchronized fun artist(name:String)=root.optJSONObject("artists")?.optString(name,"") ?: ""
    @Synchronized fun saveArtist(name:String,text:String){val all=root.optJSONObject("artists") ?: JSONObject();all.put(name,text);root.put("artists",all);save()}
    private fun save(){val temp=File(file.path+".tmp");temp.writeText(root.toString());check(temp.renameTo(file))}
}
object MusicBrainzCatalog{
    private var nextRequest=0L
    @Synchronized private fun request(endpoint:String,params:Map<String,String> = emptyMap()):JSONObject {
        val delay=nextRequest-android.os.SystemClock.elapsedRealtime();if(delay>0)Thread.sleep(delay)
        nextRequest=android.os.SystemClock.elapsedRealtime()+1100
        val query=(params+mapOf("fmt" to "json")).map {URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}.joinToString("&")
        return JSONObject(fetchText("https://musicbrainz.org/ws/2/$endpoint?$query"))
    }
    private fun clean(s:String)=s.replace('"',' ').replace('\\',' ')
    fun search(t:Track)=request("recording",mapOf("query" to "recording:\"${clean(t.title)}\" AND artist:\"${clean(t.artist)}\"","limit" to "10")).optJSONArray("recordings")?.objects() ?: emptyList()
    fun credits(id:String)=parseCredits(request("recording/$id",mapOf("inc" to "artist-credits+artist-rels+work-rels+work-level-rels+releases+genres")),id)
    fun parseCredits(item:JSONObject,id:String):JSONObject{
        val lines=mutableListOf<String>();item.optJSONArray("artist-credit")?.objects()?.forEach {lines.add("Intérprete: "+it.optString("name"))}
        val roles=mapOf("producer" to "Productor","engineer" to "Ingeniero de audio","mix" to "Mezcla","mastering" to "Masterización","lyricist" to "Letrista","composer" to "Compositor","writer" to "Autor","vocal" to "Voz","instrument" to "Instrumentista")
        fun relations(o:JSONObject){o.optJSONArray("relations")?.objects()?.forEach {rel->
            rel.optJSONObject("artist")?.let {artist->val type=rel.optString("type");val attrs=rel.optJSONArray("attributes")?.strings()?.joinToString() ?: "";lines.add("${roles[type] ?: type}${if(attrs.isBlank())"" else " ($attrs)"}: ${artist.optString("name")}")}
            rel.optJSONObject("work")?.let {relations(it)}
        }}
        relations(item)
        val date=item.optString("first-release-date").ifBlank {item.optJSONArray("releases")?.objects()?.map {it.optString("date")}?.filter {it.isNotBlank()}?.sorted()?.firstOrNull() ?: ""}
        return JSONObject().put("credits",lines.distinct().joinToString("\n")).put("year",date.take(4)).put("genre",item.optJSONArray("genres")?.objects()?.joinToString {it.optString("name")} ?: "").put("source","https://musicbrainz.org/recording/$id")
    }
    fun artist(name:String):String{
        val matches=request("artist",mapOf("query" to "artist:\"${clean(name)}\"","limit" to "10")).optJSONArray("artists")?.objects()?.filter {normalized(it.optString("name"))==normalized(name)} ?: emptyList()
        check(matches.size==1){"Identidad ambigua o sin resultados. No se asignó información."}
        val id=matches[0].getString("id");val value=request("artist/$id",mapOf("inc" to "genres+url-rels"));val life=value.optJSONObject("life-span") ?: JSONObject()
        return "Nombre: ${value.optString("name")}\nTipo: ${value.optString("type","No disponible")}\nPaís: ${value.optString("country",value.optJSONObject("area")?.optString("name") ?: "No disponible")}\nInicio: ${life.optString("begin","No disponible")}\nFin: ${life.optString("end","No disponible")}\n${value.optString("disambiguation")}\nFuente: https://musicbrainz.org/artist/$id"
    }
}
