package com.riv0trill.rivoaudio

import android.content.Context
import android.graphics.*
import android.widget.Button

/** Vector controls avoid platform-dependent Unicode glyphs. */
class RivoButton(context:Context):Button(context){
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c:Canvas){
        val key=text.toString()
        if(key !in setOf("|◀","▶|","▶","❚❚","▦","☷","❝","☰","⤨","↻¹","⌄","‹","⋯")){super.onDraw(c);return}
        val size=resources.displayMetrics.density*(if(key=="❚❚"||key=="▶")30 else 24)
        c.save();c.translate((width-size)/2,(height-size)/2);c.scale(size/24,size/24)
        pen.color=currentTextColor;pen.strokeWidth=1.8f;pen.strokeCap=Paint.Cap.ROUND;pen.strokeJoin=Paint.Join.ROUND;pen.style=Paint.Style.STROKE
        fun line(vararg p:Float){val path=Path();path.moveTo(p[0],p[1]);for(i in 2 until p.size step 2)path.lineTo(p[i],p[i+1]);c.drawPath(path,pen)}
        fun triangle(x:Float,reverse:Boolean=false){pen.style=Paint.Style.FILL;val path=Path();path.moveTo(x,4f);path.lineTo(if(reverse)x-13 else x+13,12f);path.lineTo(x,20f);path.close();c.drawPath(path,pen)}
        when(key){
            "▶"->triangle(7f)
            "❚❚"->{pen.style=Paint.Style.FILL;c.drawRoundRect(4f,3f,10f,21f,1.5f,1.5f,pen);c.drawRoundRect(14f,3f,20f,21f,1.5f,1.5f,pen)}
            "|◀"->{line(4f,4f,4f,20f);triangle(20f,true)}
            "▶|"->{line(20f,4f,20f,20f);triangle(4f)}
            "⌄"->line(5f,9f,12f,16f,19f,9f)
            "‹"->line(15f,5f,8f,12f,15f,19f)
            "⋯"->{pen.style=Paint.Style.FILL;for(x in listOf(5f,12f,19f))c.drawCircle(x,12f,1.6f,pen)}
            "▦"->{pen.style=Paint.Style.FILL;for(x in listOf(3f,14f))for(y in listOf(3f,14f))c.drawRoundRect(x,y,x+8,y+8,1.5f,1.5f,pen)}
            "☷"->{for((x,y) in listOf(5f to 8f,12f to 16f,19f to 10f)){line(x,2f,x,y-3);line(x,y+3,x,22f);c.drawRoundRect(x-2,y-3,x+2,y+3,1f,1f,pen)}}
            "☰"->{for(y in listOf(5f,12f,19f)){c.drawCircle(3f,y,.7f,pen);line(9f,y,22f,y)}}
            "❝"->{line(4f,15f,21f,15f);line(4f,21f,17f,21f);line(16f,7f,22f,7f);pen.style=Paint.Style.FILL;for(x in listOf(3f,9f)){c.drawRoundRect(x,6f,x+4,11f,1f,1f,pen);pen.style=Paint.Style.STROKE;line(x,7f,x+1,3f,x+3,2f);pen.style=Paint.Style.FILL}}
            "↻¹"->{line(4f,9f,4f,5f,19f,5f,16f,2f);line(19f,5f,16f,8f);line(20f,15f,20f,19f,5f,19f,8f,22f);line(5f,19f,8f,16f);pen.textSize=9f;pen.style=Paint.Style.FILL;c.drawText("1",10f,15f,pen)}
            "⤨"->{line(2f,5f,6f,5f,18f,19f,22f,19f,19f,16f);line(22f,19f,19f,22f);line(2f,19f,6f,19f,10f,14f);line(14f,10f,18f,5f,22f,5f,19f,2f);line(22f,5f,19f,8f)}
        }
        c.restore()
    }
}
