package com.riv0trill.rivoaudio

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.net.URL
import java.io.File

class ArtistPhotos(private val context:Context) {
    private val dir=File(context.filesDir,"Artists").apply{mkdirs()}
    fun image(artist:String)=File(dir,stableID(artist)+".jpg")
    fun credit(artist:String)=runCatching {JSONObject(File(dir,stableID(artist)+".json").readText())}.getOrNull()
    fun download(artist:String,force:Boolean=false) {
        val p=context.getSharedPreferences("rivo",0);val key="artist.attempt."+artist
        if(!force&&(image(artist).exists()||System.currentTimeMillis()-p.getLong(key,0)<86400000))return
        p.edit().putLong(key,System.currentTimeMillis()).apply()
        fun get(base:String,params:Map<String,String>)=JSONObject(fetchText(base+"?"+params.map {it.key+"="+URLEncoder.encode(it.value,"UTF-8")}.joinToString("&")))
        fun objects(a:JSONArray)=(0 until a.length()).map {a.getJSONObject(it)}
        val escaped=artist.replace("\\","\\\\").replace("\"","\\\"")
        val a=get("https://musicbrainz.org/ws/2/artist/",mapOf("query" to "artist:\"$escaped\"","fmt" to "json","limit" to "8")).getJSONArray("artists")
        val matches=objects(a).filter {normalized(it.optString("name"))==normalized(artist)};check(matches.size==1){"Sin identidad única para este artista"}
        Thread.sleep(1100)
        val detail=get("https://musicbrainz.org/ws/2/artist/"+matches[0].getString("id"),mapOf("inc" to "url-rels","fmt" to "json"))
        val entity=objects(detail.getJSONArray("relations")).firstOrNull {it.optString("type")=="wikidata"}?.getJSONObject("url")?.getString("resource")?.substringAfterLast('/') ?: error("Sin fotografía vinculada")
        check(entity.matches(Regex("Q[0-9]+")))
        val claims=get("https://www.wikidata.org/w/api.php",mapOf("action" to "wbgetentities","ids" to entity,"props" to "claims","format" to "json")).getJSONObject("entities").getJSONObject(entity).getJSONObject("claims").getJSONArray("P18")
        val claim=objects(claims).firstOrNull {it.optString("rank")=="preferred"} ?: objects(claims).first {it.optString("rank")!="deprecated"}
        val filename=claim.getJSONObject("mainsnak").getJSONObject("datavalue").getString("value")
        val info=get("https://commons.wikimedia.org/w/api.php",mapOf("action" to "query","titles" to "File:$filename","prop" to "imageinfo","iiprop" to "url|extmetadata","iiurlwidth" to "600","format" to "json","formatversion" to "2")).getJSONObject("query").getJSONArray("pages").getJSONObject(0).getJSONArray("imageinfo").getJSONObject(0)
        val meta=info.getJSONObject("extmetadata");val license=meta.getJSONObject("LicenseShortName").getString("value");check(license.isNotBlank())
        val url=URL(info.getString("thumburl"));check(url.protocol=="https"&&url.host=="upload.wikimedia.org")
        val conn=url.openConnection();conn.connectTimeout=15000;conn.readTimeout=15000
        conn.getInputStream().use {input->image(artist).outputStream().use {input.copyTo(it)}}
        val credit=JSONObject().put("source",info.getString("descriptionurl")).put("license",license).put("author",meta.getJSONObject("Artist").getString("value").replace(Regex("<[^>]+>"),""))
        File(dir,stableID(artist)+".json").writeText(credit.toString())
    }
}
