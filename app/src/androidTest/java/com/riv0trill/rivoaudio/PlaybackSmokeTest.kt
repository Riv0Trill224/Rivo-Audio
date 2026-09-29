package com.riv0trill.rivoaudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    @org.junit.Rule @JvmField val watcher=object:org.junit.rules.TestWatcher() {
        override fun failed(error:Throwable,description:org.junit.runner.Description) {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"failure.png"))
        }
    }
    @Test fun localPlaybackLyricsAndSettings() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        if(android.os.Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("rivo",0).edit().putBoolean("lyrics.auto",false).commit()
        val wav=File(context.cacheDir,"Example - Neon Nights.wav")
        val count=44100*20;val data=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36+count*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16).put("data".toByteArray()).putInt(count*2)
        repeat(count){data.putShort((kotlin.math.sin(it*440*2*Math.PI/44100)*1000).toInt().toShort())};wav.writeBytes(data.array())
        val library=Library(context);val song=library.importFile(Uri.fromFile(wav),wav.name)!!
        library.saveLyrics(song,"[00:00.00]Primera línea\n[00:02.00]Segunda línea","Prueba")
        val reloaded=Library(context);assertTrue(reloaded.readLyrics(reloaded.tracks.first()).contains("Primera"))
        song.title="Neon Nights";library.save();assertTrue(library.readLyrics(song).contains("Segunda"))
        val device=UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            val row=device.findObject(UiSelector().text("Neon Nights"));assertTrue(row.waitForExists(20000));row.click()
            assertTrue(device.findObject(UiSelector().text("Letras sincronizadas")).waitForExists(10000))
            Thread.sleep(1500)
            instrumentation.runOnMainSync {assertTrue(PlaybackService.instance!!.player.isPlaying);assertTrue(PlaybackService.instance!!.player.currentPosition>0)}
            device.takeScreenshot(File(context.getExternalFilesDir(null),"player.png"))
            device.executeShellCommand("cp ${context.getExternalFilesDir(null)}/player.png /sdcard/Download/rivo-player.png")
            device.findObject(UiSelector().text("Letras sincronizadas")).click()
            assertTrue(device.findObject(UiSelector().text("Editar / reemplazar / buscar letra")).waitForExists(5000))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"lyrics.png"))
            device.executeShellCommand("cp ${context.getExternalFilesDir(null)}/lyrics.png /sdcard/Download/rivo-lyrics.png")
            device.findObject(UiSelector().text("⋯")).click()
            assertTrue(device.findObject(UiSelector().text("Opciones visuales")).waitForExists(5000))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"settings.png"))
            device.executeShellCommand("cp ${context.getExternalFilesDir(null)}/settings.png /sdcard/Download/rivo-settings.png")
            instrumentation.runOnMainSync { val p=context.getSharedPreferences("rivo",0);p.edit().putFloat("gain.2",3f).putInt("eq.bands",31).commit();PlaybackService.instance!!.applySettings() }
            Thread.sleep(500)
            instrumentation.runOnMainSync {assertTrue(PlaybackService.instance!!.player.isPlaying);PlaybackService.instance!!.player.pause()}
        }
        library.deleteLyrics(song);assertFalse(Library(context).lyricFile(song).exists())
        library.worker.shutdown();reloaded.worker.shutdown()
    }
}
