package com.riv0trill.rivoaudio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LastFMClient(private val context:Context) {
    private val file=File(context.noBackupFilesDir,"lastfm.bin")
    private val queueFile=File(context.filesDir,"lastfm-queue.json")
    private var credentials=JSONObject()
    var status="Conecta Last.fm para enviar tus escuchas.";private set
    val user get()=credentials.optString("user")
    init {runCatching {val cipher=Cipher.getInstance("AES/GCM/NoPadding");val b=file.readBytes();cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,b.copyOfRange(0,12)));credentials=JSONObject(String(cipher.doFinal(b.copyOfRange(12,b.size))))}}
    private fun key():SecretKey {val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)};return (ks.getKey("rivo.lastfm",null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").run {init(KeyGenParameterSpec.Builder("rivo.lastfm",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generateKey()} }
    private fun persist() {val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());file.writeBytes(c.iv+c.doFinal(credentials.toString().toByteArray()))}
    @Synchronized fun configure(api:String,secret:String) {require(api.matches(Regex("[a-fA-F0-9]{32}"))&&secret.matches(Regex("[a-fA-F0-9]{32}"))) {"API key y secret deben tener 32 caracteres hexadecimales"};credentials=JSONObject().put("key",api).put("secret",secret);persist()}
    @Synchronized fun authorizeURL():String {val token=call("auth.getToken",emptyMap()).getString("token");credentials.put("token",token);persist();return "https://www.last.fm/api/auth/?api_key=${credentials.getString("key")}&token=$token"}
    @Synchronized fun finish() {val session=call("auth.getSession",mapOf("token" to credentials.getString("token"))).getJSONObject("session");credentials.put("session",session.getString("key")).put("user",session.getString("name"));persist();status="Conectado: $user"}
    @Synchronized fun nowPlaying(t:Track) {if(user.isBlank()||!enabled()||!t.verified)return;runCatching {call("track.updateNowPlaying",fields(t))}.onFailure {status=it.message ?: "Sin conexión"}}
    private fun enabled()=context.getSharedPreferences("rivo",0).getBoolean("lastfm.enabled",false)
    private fun fields(t:Track)=mapOf("artist" to t.artist,"track" to t.title,"album" to t.album,"duration" to (t.duration/1000).toString())
    @Synchronized fun enqueue(t:Track,started:Long) {if(user.isBlank()||!enabled()||!t.verified)return;val a=queue();a.put(JSONObject(fields(t)).put("timestamp",started.toString()).put("account",user));queueFile.writeText(a.toString());flush()}
    fun queue():JSONArray=runCatching {JSONArray(queueFile.readText())}.getOrDefault(JSONArray())
    @Synchronized fun flush() {
        if(user.isBlank()||!enabled())return
        val a=queue();val remaining=JSONArray()
        var failed=false
        for(i in 0 until a.length()) {val item=a.getJSONObject(i)
            if(failed||item.optString("account")!=user||item.has("rejection")){remaining.put(item);continue}
            try {val fields=item.keys().asSequence().filter {it!="account"}.associateWith {item.getString(it)};val result=call("track.scrobble",fields)
                if(result.getJSONObject("scrobbles").getJSONObject("@attr").optInt("accepted")==1) status="Escucha enviada" else {item.put("rejection","Last.fm no aceptó esta escucha");remaining.put(item)}
            } catch(e:Exception) {failed=true;remaining.put(item);status=e.message ?: "Pendiente de conexión"}
        };queueFile.writeText(remaining.toString())
    }
    @Synchronized fun disconnect() {credentials.remove("session");credentials.remove("user");persist();status="Desconectado"}
    private fun call(method:String,fields:Map<String,String>):JSONObject {
        val p=fields.toMutableMap();p["method"]=method;p["api_key"]=credentials.getString("key");if(method.startsWith("track."))p["sk"]=credentials.getString("session")
        val raw=p.toSortedMap().map {it.key+it.value}.joinToString("")+credentials.getString("secret")
        p["api_sig"]=MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)};p["format"]="json"
        val body=p.map {URLEncoder.encode(it.key,"UTF-8")+"="+URLEncoder.encode(it.value,"UTF-8")}.joinToString("&")
        val c=URL("https://ws.audioscrobbler.com/2.0/").openConnection() as java.net.HttpURLConnection
        c.connectTimeout=15000;c.readTimeout=15000;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
        return try {c.outputStream.use {it.write(body.toByteArray())};val j=JSONObject(c.inputStream.bufferedReader().use {it.readText()});check(!j.has("error")){j.optString("message")};j} finally {c.disconnect()}
    }
}
