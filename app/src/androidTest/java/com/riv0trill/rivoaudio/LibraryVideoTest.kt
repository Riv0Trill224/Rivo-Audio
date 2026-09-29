package com.riv0trill.rivoaudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import android.net.Uri
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryVideoTest {
    @org.junit.Rule @JvmField val watcher=object:org.junit.rules.TestWatcher() {
        override fun failed(error:Throwable,description:org.junit.runner.Description) {
            val d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            d.takeScreenshot(File("/sdcard/Download/rivo-video-failure.png"))
            d.dumpWindowHierarchy(File("/sdcard/Download/rivo-video-failure.xml"))
            d.wakeUp();d.setOrientationNatural();d.unfreezeRotation()
        }
    }
    @Test fun playlistsScanCreditsAndVideoQueue() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("rivo",0).edit().putBoolean("lyrics.auto",false).commit()
        val library=PlaybackService.instance?.library ?: Library(context)
        fun fixture(asset:String,name:String):Track{val f=File(context.cacheDir,name);instrumentation.context.assets.open(asset).use {input->f.outputStream().use {input.copyTo(it)}};return library.importFile(Uri.fromFile(f),name)!!}
        val audio=fixture("waveform.mp3","Clip.mp3");val video=fixture("Clip.mp4","Clip.mp4");val next=fixture("waveform.mp3","Next.mp3")
        assertTrue(exactPair(audio,video));assertFalse(exactPair(audio,video.copy(sourceName="Clip Official Video.mp4")));assertFalse(exactPair(audio,video.copy(sourceName="clip.mp4")))
        library.catalog.savePlaylist(JSONObject().put("id","video-test").put("name","Videos de prueba").put("video",true).put("tracks",JSONArray(listOf(video.id))))
        library.catalog.saveDetail(audio.id,JSONObject().put("genre","Jazz").put("credits","Productor: Example"));audio.rating=5;library.save();library.fullScan()
        assertEquals(5,library.tracks.first {it.id==audio.id}.rating);assertEquals("Productor: Example",Catalog(context).detail(audio.id).getString("credits"));assertEquals(video.id,Catalog(context).playlists().first {it.getString("id")=="video-test"}.getJSONArray("tracks").getString(0))
        val parsed=MusicBrainzCatalog.parseCredits(JSONObject("""{"artist-credit":[{"name":"Singer"}],"first-release-date":"2020-01-01","relations":[{"type":"producer","artist":{"name":"Producer"}},{"work":{"relations":[{"type":"lyricist","artist":{"name":"Writer"}}]}}]}"""),"test")
        assertTrue(parsed.getString("credits").contains("Letrista: Writer"));assertEquals("2020",parsed.getString("year"))
        library.saveLyrics(audio,"[00:00.00]Letra en video\n[00:02.00]Segunda línea","Prueba")
        val device=UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.findObject(UiSelector().text("Tracks")).waitForExists(15000))
            fun item(t:Track)=MediaItem.Builder().setMediaId(t.id).setUri(Uri.fromFile(File(t.path))).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build()).build()
            instrumentation.runOnMainSync {val p=PlaybackService.instance!!.player;p.setMediaItems(listOf(item(audio),item(next)));p.repeatMode=Player.REPEAT_MODE_OFF;p.prepare();p.play()}
            Thread.sleep(600)
            val mini=device.findObject(UiSelector().textContains("Abrir reproductor"));if(mini.exists())mini.click()else device.findObject(UiSelector().text("Selecciona una canción")).click()
            assertTrue(device.findObject(UiSelector().text("Cambiar a Video")).waitForExists(5000));device.findObject(UiSelector().text("Cambiar a Video")).click()
            instrumentation.runOnMainSync {val p=PlaybackService.instance!!.player;assertEquals(video.id,p.currentMediaItem!!.mediaId);p.pause()}
            assertTrue(device.findObject(UiSelector().text("Pantalla completa")).waitForExists(5000));device.findObject(UiSelector().text("Pantalla completa")).click()
            assertTrue("Fullscreen opens before rotation",device.findObject(UiSelector().text("Cerrar pantalla completa")).waitForExists(5000))
            device.setOrientationLeft();Thread.sleep(500)
            assertTrue(device.findObject(UiSelector().text("Cerrar pantalla completa")).waitForExists(5000))
            device.findObject(UiSelector().text("Ocultar letra")).click();assertTrue(device.findObject(UiSelector().text("Mostrar letra")).waitForExists(3000));device.findObject(UiSelector().text("Mostrar letra")).click()
            var position=0L;instrumentation.runOnMainSync {val p=PlaybackService.instance!!.player;assertEquals(2,p.mediaItemCount);assertEquals(video.id,p.currentMediaItem!!.mediaId);p.play();position=p.currentPosition}
            device.sleep();Thread.sleep(1600)
            instrumentation.runOnMainSync {val p=PlaybackService.instance!!.player;assertTrue("Background video audio",p.isPlaying);assertTrue(p.currentPosition>position+500)}
            device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");device.setOrientationNatural();Thread.sleep(400)
            device.takeScreenshot(File(context.getExternalFilesDir(null),"video.png"));device.executeShellCommand("cp ${context.getExternalFilesDir(null)}/video.png /sdcard/Download/rivo-video.png")
            instrumentation.runOnMainSync {val p=PlaybackService.instance!!.player;p.seekTo(p.duration-100)}
            Thread.sleep(1700)
            instrumentation.runOnMainSync {val p=PlaybackService.instance!!.player;assertEquals(next.id,p.currentMediaItem!!.mediaId);assertEquals(Player.REPEAT_MODE_OFF,p.repeatMode);p.pause()}
        }
        device.unfreezeRotation()
    }
}
