package com.riv0trill.rivoaudio

import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

class RivoEqualizer : BaseAudioProcessor() {
    companion object {
        val frequencies=doubleArrayOf(20.0,25.0,31.0,40.0,50.0,62.0,80.0,100.0,125.0,160.0,200.0,250.0,315.0,400.0,500.0,630.0,800.0,1000.0,1250.0,1600.0,2000.0,2500.0,3150.0,4000.0,5000.0,6300.0,8000.0,10000.0,12500.0,16000.0,20000.0)
        fun indices(count:Int)=when(count){31->(0..30).toList();15->listOf(0,2,4,6,8,10,12,14,16,18,20,22,24,26,29);else->listOf(2,5,8,11,14,17,20,23,26,29)}
        val presets=linkedMapOf("Plano" to doubleArrayOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),"Graves" to doubleArrayOf(6.0,5.0,4.0,2.0,0.0,0.0,0.0,0.0,0.0,0.0),"Hip-Hop" to doubleArrayOf(5.0,4.0,2.0,0.0,-1.0,0.0,1.0,2.0,2.0,1.0),"Voces" to doubleArrayOf(-2.0,-1.0,0.0,1.0,2.0,3.0,3.0,2.0,0.0,-1.0),"Brillante" to doubleArrayOf(0.0,0.0,0.0,0.0,0.0,1.0,2.0,3.0,4.0,3.0))
        fun preset(p:SharedPreferences,name:String) { val values=presets[name] ?: return;val refs=indices(10);val edit=p.edit();frequencies.forEachIndexed {i,f->val upper=refs.indexOfFirst {frequencies[it]>=f}.let {if(it<0)9 else it};val lower=max(0,upper-1);val gain=when{f<=frequencies[refs[0]]->values[0];f>=frequencies[refs[9]]->values[9];else->values[lower]+(values[upper]-values[lower])*ln(f/frequencies[refs[lower]])/ln(frequencies[refs[upper]]/frequencies[refs[lower]])};edit.putFloat("gain.$i",gain.toFloat())};edit.putString("eq.preset",name).apply() }
    }
    data class Config(val enabled:Boolean,val count:Int,val gains:DoubleArray,val preamp:Double)
    @Volatile private var config=Config(true,10,DoubleArray(31),1.0)
    private var applied:Config?=null
    private var coefficients=emptyArray<DoubleArray>()
    private var states=emptyArray<DoubleArray>()
    fun apply(p:SharedPreferences) {config=Config(p.getBoolean("eq.enabled",true),p.getInt("eq.bands",10),DoubleArray(31){p.getFloat("gain.$it",0f).toDouble()},10.0.pow(p.getFloat("audio.preamp",0f)/20.0))}
    override fun onConfigure(format:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat { if(format.encoding!=C.ENCODING_PCM_16BIT)throw AudioProcessor.UnhandledAudioFormatException(format);return format }
    override fun onFlush() {applied=null}
    override fun queueInput(input:ByteBuffer) {
        val cfg=config;val channels=inputAudioFormat.channelCount
        if(applied!==cfg) {
            coefficients=indices(cfg.count).filter {cfg.enabled&&abs(cfg.gains[it])>0.001&&frequencies[it]<inputAudioFormat.sampleRate*0.49}.map {i->
                val a=10.0.pow(cfg.gains[i]/40.0);val w=2*PI*frequencies[i]/inputAudioFormat.sampleRate;val bw=when(cfg.count){31->1.0/3;15->2.0/3;else->1.0};val alpha=sin(w)*sinh(ln(2.0)/2*bw*w/sin(w));val a0=1+alpha/a
                doubleArrayOf((1+alpha*a)/a0,-2*cos(w)/a0,(1-alpha*a)/a0,-2*cos(w)/a0,(1-alpha/a)/a0)
            }.toTypedArray();states=Array(coefficients.size){DoubleArray(channels*2)};applied=cfg
        }
        val output=replaceOutputBuffer(input.remaining());var sample=0
        while(input.remaining()>=2) {var x=input.short.toDouble()*cfg.preamp;val ch=sample%channels
            coefficients.forEachIndexed {i,c->val state=states[i];val at=ch*2;val y=c[0]*x+state[at];state[at]=c[1]*x-c[3]*y+state[at+1];state[at+1]=c[2]*x-c[4]*y;x=y}
            output.putShort(x.coerceIn(-32768.0,32767.0).toInt().toShort());sample++
        };output.flip()
    }
}
