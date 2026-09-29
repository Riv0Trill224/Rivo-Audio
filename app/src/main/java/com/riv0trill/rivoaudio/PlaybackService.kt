package com.riv0trill.rivoaudio

import android.content.Intent
import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
    lateinit var lastFM: LastFMClient
    private var startedAt=0L
    private var lastTrack:Track?=null
    private var logicalTrack:Track?=null
    private var session: MediaSession? = null
    val eq = RivoEqualizer()
    private val handler=Handler(Looper.getMainLooper())
    private var listened=0L; private var lastTick=0L; private var recorded=false
    private val sample=object:Runnable { override fun run() {
        if(player.isPlaying) {
            val now=android.os.SystemClock.elapsedRealtime(); if(lastTick>0) listened+=now-lastTick;lastTick=now
            val t=logicalTrack
            if(t!=null&&!recorded&&t.duration>30000&&listened>=minOf(240000,t.duration/2)) { recorded=true;t.plays++;val timestamp=startedAt;library.worker.execute { library.save();lastFM.enqueue(t,timestamp) } }
        } else lastTick=0
        handler.postDelayed(this,10000)
    } }
    override fun onCreate() {
        super.onCreate();instance=this;library=Library(this);lastFM=LastFMClient(this)
        val renderers=object:DefaultRenderersFactory(this) {
            override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioOutputPlaybackParams:Boolean):AudioSink = DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(eq)).build()
        }
        player=ExoPlayer.Builder(this,renderers).build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object:Player.Listener {
            override fun onMediaItemTransition(item:MediaItem?,reason:Int) {
                val track=library.tracks.firstOrNull {it.id==item?.mediaId} ?: return
                val previous=lastTrack
                val same=previous!=null&&previous.video!=track.video&&normalized(previous.title)==normalized(track.title)&&normalized(previous.artist)==normalized(track.artist)
                lastTrack=track
                logicalTrack=if(track.video)library.tracks.firstOrNull {!it.video&&normalized(it.title)==normalized(track.title)&&normalized(it.artist)==normalized(track.artist)} ?: track else track
                if(!same){listened=0;lastTick=0;recorded=false;startedAt=System.currentTimeMillis()/1000;logicalTrack?.let {t->library.autoLyrics(t);library.worker.execute {lastFM.nowPlaying(t)}}}
            }
            override fun onIsPlayingChanged(isPlaying:Boolean) { handler.removeCallbacks(sample);lastTick=0;if(isPlaying) handler.post(sample) }
        })
        applySettings();session=MediaSession.Builder(this,player).build()
    }
    fun applySettings() {
        val p=getSharedPreferences("rivo",0)
        player.setPlaybackSpeed(p.getFloat("audio.rate",1f));eq.apply(p)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo)=session
    override fun onTaskRemoved(rootIntent: Intent?) { if(!player.playWhenReady) stopSelf() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);session?.release();player.release();library.worker.shutdown();instance=null;super.onDestroy() }
}
