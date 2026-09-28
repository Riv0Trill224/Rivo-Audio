package com.riv0trill.rivoaudio

import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    companion object { var instance: PlaybackService? = null }
    lateinit var player: ExoPlayer
    lateinit var library: Library
    private var session: MediaSession? = null
    var eq: Equalizer? = null
    private val handler=Handler(Looper.getMainLooper())
    private var listened=0L; private var lastTick=0L; private var recorded=false
    private val sample=object:Runnable { override fun run() {
        if(player.isPlaying) {
            val now=android.os.SystemClock.elapsedRealtime(); if(lastTick>0) listened+=now-lastTick;lastTick=now
            val t=library.tracks.firstOrNull { it.id==player.currentMediaItem?.mediaId }
            if(t!=null&&!recorded&&t.duration>30000&&listened>=minOf(240000,t.duration/2)) { recorded=true;t.plays++;library.worker.execute { library.save() } }
        } else lastTick=0
        handler.postDelayed(this,10000)
    } }
    override fun onCreate() {
        super.onCreate();instance=this;library=Library(this)
        player=ExoPlayer.Builder(this).build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object:Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { runCatching { eq?.release();eq=Equalizer(0,audioSessionId);applySettings() } }
            override fun onMediaItemTransition(item:MediaItem?,reason:Int) { listened=0;lastTick=0;recorded=false;library.tracks.firstOrNull { it.id==item?.mediaId }?.let { library.autoLyrics(it) } }
            override fun onIsPlayingChanged(isPlaying:Boolean) { handler.removeCallbacks(sample);lastTick=0;if(isPlaying) handler.post(sample) }
        })
        applySettings();session=MediaSession.Builder(this,player).build()
    }
    fun applySettings() {
        val p=getSharedPreferences("rivo",0)
        player.setPlaybackSpeed(p.getFloat("audio.rate",1f));player.volume=Math.pow(10.0,p.getFloat("audio.preamp",0f)/20.0).toFloat()
        eq?.let { e ->
            e.enabled=p.getBoolean("eq.enabled",true)
            for(b in 0 until e.numberOfBands.toInt()) e.setBandLevel(b.toShort(),p.getInt("eq.$b",0).coerceIn(e.bandLevelRange[0].toInt(),e.bandLevelRange[1].toInt()).toShort())
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo)=session
    override fun onTaskRemoved(rootIntent: Intent?) { if(!player.playWhenReady) stopSelf() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);eq?.release();session?.release();player.release();library.worker.shutdown();instance=null;super.onDestroy() }
}
