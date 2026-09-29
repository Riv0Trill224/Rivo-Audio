package com.riv0trill.rivoaudio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.View
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlin.math.sqrt

/** 56 short RMS windows, matching the iOS reader; never decodes the full song. */
object WaveformReader {
    private const val COUNT=56
    fun peaks(file:File,cacheDir:File):FloatArray {
        val cache=File(cacheDir,"waveform-"+stableID("v2:${file.path}:${file.length()}:${file.lastModified()}")+".txt")
        runCatching {cache.readText().split(',').map {it.toFloat()}.toFloatArray()}.getOrNull()?.let { if(it.size==COUNT&&it.all {v->v.isFinite()&&v in 0f..1f})return it }
        val raw=runCatching {wav(file) ?: decoded(file)}.getOrElse {return floatArrayOf()}
        if(Thread.currentThread().isInterrupted||raw.size!=COUNT)return floatArrayOf()
        val scale=maxOf(raw.maxOrNull() ?: 0f,.001f)
        val normalized=FloatArray(COUNT){(raw[it]/scale).coerceIn(0f,1f)}
        runCatching {cache.writeText(normalized.joinToString(","))}
        return normalized
    }
    private fun checkCancelled(){if(Thread.currentThread().isInterrupted)throw InterruptedException()}
    private fun wav(file:File):FloatArray?=RandomAccessFile(file,"r").use {r->
        if(r.length()<44)return null
        fun tag()=ByteArray(4).also {r.readFully(it)}.toString(Charsets.US_ASCII)
        fun u32()=Integer.reverseBytes(r.readInt()).toLong() and 0xffffffffL
        fun u16()=java.lang.Short.reverseBytes(r.readShort()).toInt() and 0xffff
        if(tag()!="RIFF")return null
        u32();if(tag()!="WAVE")return null
        var encoding=0;var channels=0;var bits=0;var stride=0;var start=0L;var bytes=0L
        while(r.filePointer+8<=r.length()){
            val name=tag();val size=u32();val offset=r.filePointer
            if(name=="fmt "&&size>=16){encoding=u16();channels=u16();u32();u32();stride=u16();bits=u16()}
            if(name=="data"){start=offset;bytes=minOf(size,r.length()-offset);break}
            if(offset+size>r.length())return null
            r.seek(offset+size+(size and 1))
        }
        if(encoding!=1||bits!=16||channels<1||stride<channels*2||bytes<stride)return null
        val frames=bytes/stride
        FloatArray(COUNT){i->
            checkCancelled();val frame=frames*i/COUNT;val n=minOf(1024L,frames-frame).toInt();r.seek(start+frame*stride)
            var sum=0.0
            repeat(n){val value=java.lang.Short.reverseBytes(r.readShort()).toDouble()/32768;sum+=value*value;r.skipBytes(stride-2)}
            sqrt(sum/maxOf(1,n)).toFloat()
        }
    }
    private fun decoded(file:File):FloatArray {
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        try {
            extractor.setDataSource(file.path)
            val track=(0 until extractor.trackCount).firstOrNull {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true} ?: return floatArrayOf()
            extractor.selectTrack(track);val input=extractor.getTrackFormat(track)
            val duration=input.getLong(MediaFormat.KEY_DURATION);if(duration<=0)return floatArrayOf()
            val codec=MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME)!!);decoder=codec
            codec.configure(input,null,null,0);codec.start()
            var output=input;val info=MediaCodec.BufferInfo();val deadline=System.nanoTime()+15_000_000_000L
            return FloatArray(COUNT){i->
                checkCancelled();check(System.nanoTime()<deadline)
                val target=duration*i/COUNT;extractor.seekTo(maxOf(0L,target-250_000L),MediaExtractor.SEEK_TO_PREVIOUS_SYNC);codec.flush()
                var ended=false;var rms:Float?=null;var attempts=0
                while(rms==null&&attempts++<300){
                    checkCancelled();check(System.nanoTime()<deadline)
                    if(!ended){val slot=codec.dequeueInputBuffer(1000)
                        if(slot>=0){val buffer=codec.getInputBuffer(slot)!!;buffer.clear();val size=extractor.readSampleData(buffer,0)
                            if(size<0){codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);ended=true}
                            else {codec.queueInputBuffer(slot,0,size,extractor.sampleTime,0);extractor.advance()}
                        }
                    }
                    val slot=codec.dequeueOutputBuffer(info,1000)
                    if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)output=codec.outputFormat
                    if(slot>=0){
                        try {
                            if(info.size>0){val rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);val channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                val encoding=if(output.containsKey(MediaFormat.KEY_PCM_ENCODING))output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                                val sampleBytes=if(encoding==AudioFormat.ENCODING_PCM_FLOAT)4 else 2
                                check(encoding==AudioFormat.ENCODING_PCM_FLOAT||encoding==AudioFormat.ENCODING_PCM_16BIT)
                                val frames=info.size/(sampleBytes*channels);val skip=maxOf(0L,(target-info.presentationTimeUs)*rate/1_000_000).coerceAtMost(frames.toLong()).toInt()
                                if(skip<frames){val buffer=codec.getOutputBuffer(slot)!!.order(ByteOrder.LITTLE_ENDIAN);val count=minOf(1024,frames-skip);var sum=0.0
                                    repeat(count){n->val at=info.offset+(skip+n)*sampleBytes*channels;val v=if(sampleBytes==4)buffer.getFloat(at).toDouble() else buffer.getShort(at).toDouble()/32768;sum+=v*v};rms=sqrt(sum/count).toFloat()
                                }
                            }
                            if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0&&rms==null)rms=0f
                        }finally{codec.releaseOutputBuffer(slot,false)}
                    }
                }
                rms ?: error("No PCM window")
            }
        }finally{runCatching {decoder?.stop()};decoder?.release();extractor.release()}
    }
}

class PlaybackWaveform(context:Context):View(context){
    var peaks= floatArrayOf();set(value){field=value;contentDescription=if(value.isEmpty())"Forma de onda cargando" else "Forma de onda, ${value.size} muestras";invalidate()}
    var position=0f;set(value){field=value.coerceIn(0f,1f);invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);if(peaks.isEmpty())return
        val gap=3*resources.displayMetrics.density;val bar=maxOf(1f,(width-gap*(peaks.size-1))/peaks.size)
        peaks.forEachIndexed {i,v->val h=maxOf(4*resources.displayMetrics.density,v*height);val x=i*(bar+gap)
            paint.color=if(i.toFloat()/peaks.size<=position)Color.rgb(212,173,255) else 0x2EFFFFFF
            canvas.drawRoundRect(x,(height-h)/2,x+bar,(height+h)/2,bar/2,bar/2,paint)
        }
    }
}
