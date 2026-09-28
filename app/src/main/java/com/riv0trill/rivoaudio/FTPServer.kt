package com.riv0trill.rivoaudio

import android.net.Uri
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.Executors

class FTPServer(private val library:Library) {
    private var listener:ServerSocket?=null
    private val pool=Executors.newCachedThreadPool()
    private val sockets=java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
    private val passiveSockets=java.util.Collections.synchronizedSet(mutableSetOf<ServerSocket>())
    val password=UUID.randomUUID().toString().take(12)
    val running get()=listener!=null
    val address get()=NetworkInterface.getNetworkInterfaces().toList().flatMap {it.inetAddresses.toList()}.firstOrNull {it is Inet4Address&&!it.isLoopbackAddress}?.hostAddress ?: "Sin Wi-Fi"
    private val root=File(library.root.parentFile,"Transfers").apply {mkdirs()}
    fun start() {if(running)return;val server=ServerSocket(2121);listener=server;pool.execute {while(!server.isClosed){try{val socket=server.accept();sockets.add(socket);pool.execute {serve(socket)}}catch(_:Exception){break}}} }
    fun stop() {listener?.close();listener=null;synchronized(sockets){sockets.toList().forEach {runCatching {it.close()}};sockets.clear()};synchronized(passiveSockets){passiveSockets.toList().forEach {runCatching {it.close()}};passiveSockets.clear()}}
    private fun serve(control:Socket) {
        var passive:ServerSocket?=null
        var cwd=root
        try {control.soTimeout=300000;val reader=control.getInputStream().bufferedReader();val writer=control.getOutputStream().bufferedWriter()
            fun reply(s:String){writer.write(s+"\r\n");writer.flush()}
            fun target(name:String):File {val f=if(name.startsWith('/'))File(root,name.removePrefix("/"))else File(cwd,name);val c=f.canonicalFile;check(c.path==root.canonicalPath||c.path.startsWith(root.canonicalPath+File.separator)){"Ruta inválida"};return c}
            var user=false;var authenticated=false
            reply("220 Rivo Audio FTP")
            while(true) {val line=reader.readLine() ?: break;val cmd=line.substringBefore(' ').uppercase();val arg=line.substringAfter(' ',"")
                if(cmd=="USER"){user=arg=="rivo";reply("331 Password required");continue}
                if(cmd=="PASS"){authenticated=user&&arg==password;reply(if(authenticated)"230 Logged in" else "530 Login incorrect");continue}
                if(cmd=="QUIT"){reply("221 Goodbye");break}
                if(!authenticated){reply("530 Login required");continue}
                try {when(cmd){
                    "SYST"->reply("215 UNIX Type: L8")
                    "FEAT"->{reply("211-Features");reply(" EPSV");reply(" UTF8");reply("211 End")}
                    "OPTS","TYPE","NOOP"->reply("200 OK")
                    "PWD"->reply("257 \"/"+cwd.relativeTo(root).path.replace("\\","/").removePrefix(".")+"\"")
                    "CWD"->{val f=target(arg);check(f.isDirectory);cwd=f;reply("250 Directory changed")}
                    "CDUP"->{cwd=if(cwd==root)root else cwd.parentFile!!;reply("250 Directory changed")}
                    "MKD"->{check(target(arg).mkdirs());reply("257 Directory created")}
                    "SIZE"->{val f=target(arg);check(f.isFile);reply("213 ${f.length()}")}
                    "PASV","EPSV"->{passive?.let {it.close();passiveSockets.remove(it)};val p=ServerSocket(0);p.soTimeout=30000;passive=p;passiveSockets.add(p);if(cmd=="EPSV")reply("229 Entering Extended Passive Mode (|||${p.localPort}|)") else {val ip=control.localAddress.hostAddress!!.replace('.',',');reply("227 Entering Passive Mode ($ip,${p.localPort/256},${p.localPort%256})")}}
                    "LIST","NLST","STOR","RETR"->{val p=passive ?: error("Use PASV first");reply("150 Opening data connection");p.accept().use {data->check(data.inetAddress==control.inetAddress){"Peer mismatch"};data.soTimeout=60000
                        when(cmd){
                            "LIST","NLST"->data.getOutputStream().bufferedWriter().use {out->cwd.listFiles()?.forEach {f->out.write(if(cmd=="NLST")f.name+"\r\n" else "${if(f.isDirectory)"d" else "-"}rw-r--r-- 1 rivo rivo ${f.length()} Jan 01 00:00 ${f.name}\r\n")}}
                            "RETR"->target(arg).inputStream().use {input->input.copyTo(data.getOutputStream())}
                            "STOR"->{val f=target(arg);f.parentFile?.mkdirs();val temp=File(f.parentFile,f.name+".upload");data.getInputStream().use {input->temp.outputStream().use {input.copyTo(it)}};check(temp.renameTo(f));library.worker.execute {runCatching {library.importFile(Uri.fromFile(f),f.name)}}}
                        }
                    };p.close();passiveSockets.remove(p);passive=null;reply("226 Transfer complete")}
                    else->reply("502 Command not supported")
                }}catch(_:Exception){reply("550 Operation failed")}
            }
        }catch(_:Exception){}finally{passive?.let {runCatching {it.close()};passiveSockets.remove(it)};control.close();sockets.remove(control)}
    }
}
