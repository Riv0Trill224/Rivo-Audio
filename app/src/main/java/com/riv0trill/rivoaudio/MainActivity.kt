package com.riv0trill.rivoaudio

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.concurrent.Executor

class MainActivity : Activity() {
    private val thumbs=object:android.util.LruCache<String,Bitmap>(16*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount}
    private var ftp:FTPServer?=null
    private var controller: MediaController?=null
    private var future: com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
    private val library get()=PlaybackService.instance!!.library
    private val prefs by lazy { getSharedPreferences("rivo",0) }
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private var videoSurface:PlayerView?=null
    private var page="Canciones"
    private var selectedArtist:String?=null
    private var editTrack:Track?=null
    private var lyricEditor:EditText?=null
    private var lyricEditorTrackId:String?=null
    private var lyricView:TextView?=null
    private var lyricScroll:ScrollView?=null
    private var lyricLines=listOf<Pair<Long,String>>()
    private var lyricIndex=-1
    private var lyricVersion=0L
    private var progress:SeekBar?=null
    private var timeLabel:TextView?=null
    private var playButton:Button?=null
    private var endTimeLabel:TextView?=null
    private var mini:Button?=null
    private val accent=Color.rgb(212,173,255)
    private val ink=Color.rgb(9,12,20)
    private val surface=Color.rgb(26,27,41)
    private val updater=object:Runnable { override fun run() { updateProgress();handler.postDelayed(this,if(controller?.isPlaying==true)500 else 1500) } }
    private fun current()=controller?.currentMediaItem?.mediaId?.let { id -> library.tracks.firstOrNull { it.id==id } }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(text:String,size:Float=16f)=TextView(this).apply { this.text=text;textSize=size;setTextColor(Color.WHITE);setPadding(dp(8),dp(6),dp(8),dp(6)) }
    private fun button(text:String,action:()->Unit)=Button(this).apply { this.text=text;isAllCaps=false;setTextColor(accent);background=GradientDrawable().apply {setColor(surface);cornerRadius=dp(18).toFloat()};setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener { action() } }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;clipToPadding=false }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    private fun addButton(text:String,action:()->Unit) { body.addView(button(text,action),LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,dp(4),0,dp(4))}) }
    private fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
    private fun async(work:()->Unit,done:()->Unit={ render() }) { library.worker.execute { try { work();runOnUiThread { if(!isFinishing) done() } } catch(e:Exception) { runOnUiThread { toast(e.message ?: "Error") } } } }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.statusBarColor=ink;window.navigationBarColor=ink
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),99)
        root=column();root.setBackgroundColor(ink);root.setPadding(dp(12),dp(8),dp(12),dp(8));setContentView(root)
        root.setOnApplyWindowInsetsListener { v,insets -> v.setPadding(dp(12),insets.systemWindowInsetTop+dp(8),dp(12),insets.systemWindowInsetBottom+dp(8));insets }
        root.addView(label("RIVØ AUDIO",26f));root.addView(label("Cargando biblioteca…"))
        future=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        future!!.addListener({ runCatching { controller=future!!.get();controller!!.addListener(object:Player.Listener {
            override fun onMediaItemTransition(item:MediaItem?,reason:Int) { if(page=="Reproductor"||page=="Letras") render() else updateProgress() }
            override fun onPlayerError(error:PlaybackException) { toast("No se pudo reproducir: ${error.message}") }
        });render() }.onFailure { toast(it.message ?: "No se pudo iniciar el audio") } },Executor { runOnUiThread(it) })
    }
    override fun onStart() { super.onStart();handler.post(updater) }
    override fun onStop() { handler.removeCallbacks(updater);super.onStop() }
    override fun onDestroy() { ftp?.stop();future?.let { MediaController.releaseFuture(it) };super.onDestroy() }
    override fun onBackPressed() { if(page!="Canciones") { page="Canciones";selectedArtist=null;render() } else super.onBackPressed() }
    private fun render() {
        if(controller==null||PlaybackService.instance==null)return
        videoSurface?.player=null;videoSurface=null
        applyBackdrop()
        root.removeAllViews();progress=null;timeLabel=null;endTimeLabel=null;playButton=null;lyricView=null;lyricScroll=null;lyricIndex=-1
        val heading=row()
        heading.setPadding(dp(8),dp(8),dp(8),dp(16))
        val back=button(if(page=="Reproductor")"⌄" else "‹") {page="Canciones";selectedArtist=null;render()}
        heading.addView(back,LinearLayout.LayoutParams(dp(42),dp(42)))
        val title=column();title.gravity=Gravity.CENTER
        title.addView(label(if(page=="Reproductor")"R I V Ø   A U D I O" else page,if(page=="Reproductor")13f else 22f).apply {gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)})
        if(page=="Reproductor")title.addView(label("T U  B I B L I O T E C A",9f).apply {setTextColor(Color.GRAY);gravity=Gravity.CENTER;setPadding(0,0,0,0)})
        heading.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(button("⋯") {page="Ajustes";render()},LinearLayout.LayoutParams(dp(42),dp(42)));root.addView(heading)
        body=column();body.setPadding(dp(12),0,dp(12),0);val scroll=ScrollView(this);scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(page) {
            "Canciones" -> libraryPage()
            "Artistas" -> artistsPage()
            "Reproductor" -> playerPage()
            "Letras" -> lyricsPage()
            "EQ" -> eqPage()
            "Ajustes" -> settingsPage()
            "Transferir" -> transferPage()
            "Escuchas" -> historyPage()
            "Last.fm" -> lastFMPage()
            "Administrar letras" -> managerPage()
        }
        mini=button(current()?.let { "${it.title} · Abrir reproductor" } ?: "Selecciona una canción") { page="Reproductor";render() };if(page!="Reproductor")root.addView(mini)
        val tabs=row();listOf("Canciones","Artistas","EQ","Escuchas","Transferir").forEach { name -> tabs.addView(button(name) { page=name;selectedArtist=null;render() }.apply { textSize=10f;minWidth=0;setPadding(0,0,0,0) },LinearLayout.LayoutParams(0,dp(48),1f)) };if(page!="Reproductor"&&page!="Letras")root.addView(tabs)
        updateProgress()
    }
    private fun applyBackdrop() {
        val layers=mutableListOf<android.graphics.drawable.Drawable>()
        layers.add(android.graphics.drawable.ColorDrawable(ink))
        if(prefs.getBoolean("visual.artwork",true)&&(page=="Reproductor"||page=="Letras")) {
            current()?.let { t->
                val file=File(library.covers,t.id+".jpg")
                if(file.exists()) {
                    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    BitmapFactory.decodeFile(file.path,bounds)
                    bounds.inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/48);bounds.inJustDecodeBounds=false
                    BitmapFactory.decodeFile(file.path,bounds)?.let { source->
                        val small=Bitmap.createScaledBitmap(source,12,12,true)
                        val softened=Bitmap.createScaledBitmap(small,256,256,true)
                        layers.add(android.graphics.drawable.BitmapDrawable(resources,softened).apply {alpha=97;isFilterBitmap=true;gravity=Gravity.FILL})
                    }
                }
            }
        }
        layers.add(GradientDrawable(GradientDrawable.Orientation.TR_BL,intArrayOf(0x604A1760,0xCC090C14.toInt(),ink)))
        root.background=android.graphics.drawable.LayerDrawable(layers.toTypedArray())
    }
    private fun libraryPage() {
        body.addView(label("Tu colección, a tu ritmo.",22f));body.addView(label("${library.tracks.size} pistas"))
        val search=EditText(this).apply { hint="Canción, artista o álbum";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY) };body.addView(search)
        addButton("＋ Añadir música") { pickOptions() }
        addButton("Mezclar") { library.tracks.filter { !it.video }.shuffled().firstOrNull()?.let { controller?.shuffleModeEnabled=true;play(it) } }
        val list=ListView(this);list.dividerHeight=dp(6);list.setBackgroundColor(ink);body.addView(list,LinearLayout.LayoutParams(-1,maxOf(dp(220),resources.displayMetrics.heightPixels-dp(360))))
        var visible=library.tracks.toList()
        val adapter=object:BaseAdapter() {
            override fun getCount()=visible.size
            override fun getItem(position:Int)=visible[position]
            override fun getItemId(position:Int)=position.toLong()
            override fun getView(position:Int,convert:View?,parent:ViewGroup?):View {
                val track=visible[position];val line=convert as? LinearLayout ?: row().apply {
                    setPadding(dp(8),dp(8),dp(8),dp(8));setBackgroundColor(surface)
                    addView(ImageView(this@MainActivity).apply {scaleType=ImageView.ScaleType.CENTER_CROP},LinearLayout.LayoutParams(dp(48),dp(48)))
                    addView(column().apply {addView(label("",16f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END});addView(label("",13f).apply {maxLines=1;setTextColor(Color.LTGRAY)})},LinearLayout.LayoutParams(0,-2,1f))
                }
                val texts=line.getChildAt(1) as LinearLayout;(texts.getChildAt(0) as TextView).text=track.title;(texts.getChildAt(1) as TextView).text=track.artist
                val image=line.getChildAt(0) as ImageView;val f=File(library.covers,track.id+".jpg");val key=track.id;var bitmap=thumbs.get(key)
                if(bitmap==null&&f.exists()){val opts=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(f.path,opts);opts.inSampleSize=maxOf(1,maxOf(opts.outWidth,opts.outHeight)/96);opts.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(f.path,opts);if(bitmap!=null)thumbs.put(key,bitmap)}
                if(bitmap!=null)image.setImageBitmap(bitmap) else image.setImageResource(android.R.drawable.ic_media_play)
                return line
            }
        }
        list.adapter=adapter;list.setOnItemClickListener {_,_,i,_->play(visible[i])};list.setOnItemLongClickListener {_,_,i,_->editMetadata(visible[i]);true}
        search.addTextChangedListener(object:android.text.TextWatcher {override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun afterTextChanged(e:android.text.Editable?){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){val q=s.toString();visible=library.tracks.filter {"${it.title} ${it.artist} ${it.album}".contains(q,true)};adapter.notifyDataSetChanged()}})
    }

    private fun artistsPage() {
        val artist=selectedArtist
        if(artist==null) library.tracks.groupBy { it.artist }.toSortedMap().forEach { (a,ts) -> addButton("$a · ${ts.size} canciones") { selectedArtist=a;render() } }
        else { body.addView(label(artist,26f))
            val photos=ArtistPhotos(this);val file=photos.image(artist)
            if(file.exists()){val image=ImageView(this);image.setImageBitmap(BitmapFactory.decodeFile(file.path));image.scaleType=ImageView.ScaleType.CENTER_CROP;body.addView(image,LinearLayout.LayoutParams(-1,dp(220)))}
            photos.credit(artist)?.let { credit->body.addView(label(credit.optString("author")+" · "+credit.optString("license"),12f));addButton("Ver fuente de fotografía") {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(credit.getString("source"))))} }
            addButton("Actualizar foto automáticamente") {async({photos.download(artist,true)})}
            if(!file.exists())async({photos.download(artist)},{if(photos.image(artist).exists()&&page=="Artistas"&&selectedArtist==artist)render()})
            library.tracks.filter { it.artist==artist }.forEach { t-> addButton(t.title) { play(t) } } }
    }
    private fun mediaItem(t:Track)=MediaItem.Builder().setMediaId(t.id).setUri(Uri.fromFile(File(t.path))).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).setArtworkUri(File(library.covers,t.id+".jpg").takeIf { it.exists() }?.let { Uri.fromFile(it) }).build()).build()
    private fun play(t:Track) { val tracks=library.tracks.filter { it.video==t.video };controller?.setMediaItems(tracks.map { mediaItem(it) },tracks.indexOf(t).coerceAtLeast(0),0);controller?.repeatMode=Player.REPEAT_MODE_ALL;controller?.prepare();controller?.play();page="Reproductor";render() }
    private fun playerPage() {
        val t=current() ?: run { body.addView(label("Selecciona una canción"));return }
        if(t.video) {videoSurface=PlayerView(this).apply {player=controller;useController=true};body.addView(videoSurface,LinearLayout.LayoutParams(-1,dp(220)))}
        else {
            val size=minOf(resources.displayMetrics.widthPixels-dp(80),(resources.displayMetrics.heightPixels*0.31).toInt(),dp(380))
            val frame=FrameLayout(this)
            frame.background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(133,34,158),ink,Color.rgb(76,82,160))).apply {cornerRadius=dp(32).toFloat()}
            frame.clipToOutline=true
            val art=ImageView(this);art.scaleType=ImageView.ScaleType.CENTER_CROP
            val file=File(library.covers,t.id+".jpg")
            if(file.exists()) {
                val opts=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,opts)
                opts.inSampleSize=maxOf(1,maxOf(opts.outWidth,opts.outHeight)/800);opts.inJustDecodeBounds=false
                art.setImageBitmap(BitmapFactory.decodeFile(file.path,opts));frame.addView(art,FrameLayout.LayoutParams(-1,-1))
            } else frame.addView(object:View(this) {
                val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0x80FFFFFF.toInt();strokeWidth=dp(2).toFloat();strokeCap=Paint.Cap.ROUND}
                override fun onDraw(c:Canvas) {super.onDraw(c);floatArrayOf(.16f,.38f,.62f,.32f,.45f,.23f).forEachIndexed {i,v->val x=width/2f+(i-2.5f)*dp(9);c.drawLine(x,height/2f-v*dp(90)/2,x,height/2f+v*dp(90)/2,pen)}}
            },FrameLayout.LayoutParams(-1,-1))
            val badge=label(File(t.path).extension.uppercase(),11f).apply {setTypeface(null,Typeface.BOLD);background=GradientDrawable().apply {setColor(0x60606070);cornerRadius=dp(18).toFloat()}}
            frame.addView(badge,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply {setMargins(0,0,dp(14),dp(14))})
            body.addView(frame,LinearLayout.LayoutParams(size,size).apply {gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(12)})
        }
        val info=row();info.gravity=Gravity.TOP
        val metadata=column()
        metadata.addView(label(t.title,22f).apply {setTypeface(null,Typeface.BOLD);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
        metadata.addView(label(t.artist,16f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(Color.LTGRAY);setPadding(dp(8),0,dp(8),dp(4))})
        metadata.addView(label(t.album,12f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setTextColor(Color.GRAY);setPadding(dp(8),0,dp(8),dp(8))})
        info.addView(metadata,LinearLayout.LayoutParams(0,-2,1f))
        info.addView(button(if(t.rating==5)"★" else "☆") {t.rating=if(t.rating==5)0 else 5;library.save();render()}.apply {textSize=26f;setBackgroundColor(Color.TRANSPARENT);contentDescription="Marcar con cinco estrellas"},LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(info)
        val pair=library.tracks.filter { it.video!=t.video&&normalized(it.title)==normalized(t.title)&&normalized(it.artist)==normalized(t.artist) }
        if(pair.isNotEmpty()) addButton(if(t.video) "Cambiar a Audio" else "Cambiar a Video") {
            fun switch(other:Track) { val pos=controller!!.currentPosition;val playing=controller!!.playWhenReady;controller!!.setMediaItem(mediaItem(other),pos);controller!!.prepare();controller!!.playWhenReady=playing;render() }
            if(pair.size==1)switch(pair[0]) else AlertDialog.Builder(this).setTitle("Selecciona la versión").setItems(pair.map { it.title+" · "+File(it.path).extension }.toTypedArray()) { _,i->switch(pair[i]) }.show()
        }
        val options=row()
        options.addView(button("☷  EQ") {page="EQ";render()},LinearLayout.LayoutParams(dp(80),dp(40)))
        options.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        options.addView(button("↻¹") {controller!!.repeatMode=if(controller!!.repeatMode==Player.REPEAT_MODE_ONE)Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE;render()}.apply {contentDescription="Repetir canción";setTextColor(if(controller!!.repeatMode==Player.REPEAT_MODE_ONE)accent else Color.GRAY);setBackgroundColor(Color.TRANSPARENT)},LinearLayout.LayoutParams(dp(48),dp(44)))
        options.addView(button("⤨") {controller!!.shuffleModeEnabled=!controller!!.shuffleModeEnabled;render()}.apply {contentDescription="Aleatorio";setTextColor(if(controller!!.shuffleModeEnabled)accent else Color.GRAY);setBackgroundColor(Color.TRANSPARENT)},LinearLayout.LayoutParams(dp(48),dp(44)));body.addView(options)
        progress=SeekBar(this).apply {max=1000;thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);progressTintList=android.content.res.ColorStateList.valueOf(accent);contentDescription="Posición de reproducción";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){if(user)controller?.seekTo(p.toLong())}
            override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
        })};body.addView(progress,LinearLayout.LayoutParams(-1,dp(36)))
        val times=row();timeLabel=label("",12f).apply {setTextColor(Color.GRAY)};endTimeLabel=label("",12f).apply {setTextColor(Color.GRAY);gravity=Gravity.END}
        times.addView(timeLabel,LinearLayout.LayoutParams(0,-2,1f));times.addView(endTimeLabel,LinearLayout.LayoutParams(0,-2,1f));body.addView(times)
        val controls=row();controls.gravity=Gravity.CENTER;controls.setPadding(0,dp(10),0,dp(14))
        fun transport(text:String,description:String,large:Boolean=false,action:()->Unit):Button {
            val size=dp(if(large)84 else 60)
            return button(text,action).apply {contentDescription=description;textSize=if(large)30f else 23f;setTextColor(if(large)ink else Color.WHITE);background=GradientDrawable().apply {shape=GradientDrawable.OVAL;setColor(if(large)accent else 0x70000000)};layoutParams=LinearLayout.LayoutParams(size,size).apply {setMargins(dp(10),0,dp(10),0)}}
        }
        controls.addView(transport("|◀","Anterior") {if(controller!!.currentPosition>3000)controller!!.seekTo(0) else controller!!.seekToPreviousMediaItem()})
        playButton=transport("▶","Reproducir",true) {controller?.let {if(it.isPlaying)it.pause() else it.play()}};controls.addView(playButton)
        controls.addView(transport("▶|","Siguiente") {controller?.seekToNextMediaItem()});body.addView(controls)
        val audio=PlaybackService.instance?.player?.audioFormat
        body.addView(label("${File(t.path).extension.uppercase()}  ·  ${audio?.sampleRate ?: 0} Hz  ·  ${audio?.channelCount ?: 0} canales",11f).apply {gravity=Gravity.CENTER;setTextColor(Color.GRAY);typeface=Typeface.MONOSPACE})
        val footer=row();footer.background=GradientDrawable().apply {setColor(0x18FFFFFF);cornerRadius=dp(28).toFloat()}
        listOf(Triple("▦","Biblioteca",{page="Canciones";render()}),Triple("☷","Ecualizador",{page="EQ";render()}),Triple("❝","Letras sincronizadas",{page="Letras";render()}),Triple("☰","Cola",{showQueue()})).forEach {(symbol,description,action)->
            footer.addView(button(symbol,action).apply {contentDescription=description;textSize=24f;setTextColor(Color.LTGRAY);setBackgroundColor(Color.TRANSPARENT)},LinearLayout.LayoutParams(0,dp(48),1f))
        };body.addView(footer,LinearLayout.LayoutParams(-1,dp(48)).apply {topMargin=dp(8);bottomMargin=dp(8)})
        addButton("Editar información y carátula") {editMetadata(t)}
    }
    private fun showQueue() {AlertDialog.Builder(this).setTitle("Cola").setItems((0 until controller!!.mediaItemCount).map {controller!!.getMediaItemAt(it).mediaMetadata.title.toString()}.toTypedArray()) {_,i->controller!!.seekToDefaultPosition(i);controller!!.play()}.show()}
    private fun format(ms:Long):String { val sec=maxOf(0,ms)/1000;return "%d:%02d".format(sec/60,sec%60) }
    private fun updateProgress() {
        val p=controller ?: return
        progress?.max=p.duration.coerceIn(1,Int.MAX_VALUE.toLong()).toInt();progress?.progress=p.currentPosition.toInt()
        timeLabel?.text=format(p.currentPosition);endTimeLabel?.text=format(p.duration);playButton?.text=if(p.isPlaying)"❚❚" else "▶";playButton?.contentDescription=if(p.isPlaying)"Pausar" else "Reproducir"
        if(page=="Letras") {val t=lyricTrack();if(t!=null&&library.lyricFile(t).lastModified()!=lyricVersion){render();return}}
        if(page=="Letras"&&lyricLines.isNotEmpty()) {
            val index=lyricLines.indexOfLast { it.first<=p.currentPosition }
            if(index!=lyricIndex) { lyricIndex=index;val text=android.text.SpannableString(lyricLines.joinToString("\n\n") { it.second });var offset=0
                lyricLines.forEachIndexed { i,line ->text.setSpan(android.text.style.ForegroundColorSpan(if(i==index)Color.WHITE else Color.GRAY),offset,offset+line.second.length,0);offset+=line.second.length+2 }
                lyricView?.text=text
                lyricView?.post { lyricView?.layout?.let { layout -> val at=lyricLines.take(maxOf(0,index)).sumOf { it.second.length+2 };val y=layout.getLineTop(layout.getLineForOffset(at.coerceAtMost(text.length)));if(prefs.getBoolean("visual.motion",true))lyricScroll?.smoothScrollTo(0,maxOf(0,y-dp(90))) else lyricScroll?.scrollTo(0,maxOf(0,y-dp(90))) } }
            }
        }
    }
    private fun lyricTrack():Track? { val t=current() ?: return null;return if(t.video)library.tracks.firstOrNull {!it.video&&normalized(it.title)==normalized(t.title)&&normalized(it.artist)==normalized(t.artist)} ?: t else t }
    private fun lyricsPage() {
        val t=lyricTrack() ?: return
        lyricVersion=library.lyricFile(t).lastModified()
        addButton("Editar / reemplazar / buscar letra") { editLyrics(t) }
        val text=library.readLyrics(t)
        val pattern=Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
        lyricLines=text.lines().flatMap { line-> val matches=pattern.findAll(line).toList();val words=matches.lastOrNull()?.let { line.substring(it.range.last+1).trim() } ?: "";matches.map { m-> val frac=m.groupValues[3].padEnd(3,'0').take(3).toLongOrNull() ?: 0;(m.groupValues[1].toLong()*60000+m.groupValues[2].toLong()*1000+frac) to words } }.sortedBy { it.first }
        lyricView=label(if(text.isBlank())"Sin letra descargada" else text,prefs.getFloat("visual.lyricSize",30f))
        lyricScroll=ScrollView(this);lyricScroll!!.addView(lyricView);body.addView(lyricScroll,LinearLayout.LayoutParams(-1,dp(440)))
    }
    private fun managerPage() { body.addView(label("Letras guardadas localmente · LRC"));library.tracks.filter { !it.video }.forEach { t->addButton("${t.title}\n${library.records().optJSONObject(t.id)?.optString("source") ?: "Sin letra"}") { editLyrics(t) } } }
    private fun editLyrics(t:Track) {
        editTrack=t
        val editor=EditText(this).apply { setText(library.readLyrics(t));minLines=8;gravity=Gravity.TOP;setTextColor(Color.WHITE) }
        lyricEditor=editor;lyricEditorTrackId=t.id
        val panel=column();panel.setPadding(dp(16),dp(8),dp(16),dp(8));panel.addView(editor)
        panel.addView(button("Reemplazar desde Archivos") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*";addCategory(Intent.CATEGORY_OPENABLE) },102) })
        panel.addView(button("Exportar LRC") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type="text/plain";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,t.title+".lrc") },103) })
        panel.addView(button("Buscar en LRCLIB") { async({ val matches=library.lookup(t);runOnUiThread { if(matches.isEmpty())toast("Sin coincidencias") else AlertDialog.Builder(this).setTitle("Elige la letra correcta").setItems(matches.map { it.optString("trackName")+" · "+it.optString("artistName") }.toTypedArray()) { _,i ->val text=matches[i].getString("syncedLyrics");library.saveLyrics(t,text,"LRCLIB · selección manual");editor.setText(text) }.show() } },{}) })
        AlertDialog.Builder(this).setTitle(t.title).setView(panel).setPositiveButton("Guardar") { _,_->library.saveLyrics(t,editor.text.toString(),"Edición manual");render() }.setNeutralButton("Eliminar") { _,_->AlertDialog.Builder(this).setMessage("¿Eliminar la letra? Se conserva la canción.").setPositiveButton("Eliminar") { _,_->library.deleteLyrics(t);render() }.setNegativeButton("Cancelar",null).show() }.setNegativeButton("Cerrar",null).create().apply {setOnDismissListener {if(lyricEditor===editor){lyricEditor=null;lyricEditorTrackId=null}};show()}
    }
    private fun toggle(text:String,key:String,default:Boolean) { body.addView(Switch(this).apply { this.text=text;setTextColor(Color.WHITE);isChecked=prefs.getBoolean(key,default);setOnCheckedChangeListener { _,v->prefs.edit().putBoolean(key,v).apply();PlaybackService.instance?.applySettings() } }) }
    private fun slider(title:String,key:String,min:Float,max:Float,default:Float) {
        val text=label("$title: ${prefs.getFloat(key,default)}");body.addView(text)
        body.addView(SeekBar(this).apply { this.max=100;progress=((prefs.getFloat(key,default)-min)/(max-min)*100).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){ if(user){val v=min+(max-min)*p/100;prefs.edit().putFloat(key,v).apply();text.text="$title: %.2f".format(v);PlaybackService.instance?.applySettings()} };override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){} }) })
    }
    private fun settingsPage() {
        body.addView(label("Opciones visuales",22f));toggle("Fondo de carátula","visual.artwork",true);toggle("Animar letras","visual.motion",true);slider("Tamaño de letra","visual.lyricSize",20f,40f,30f)
        body.addView(label("Letras",22f));toggle("Buscar letras automáticamente","lyrics.auto",true);addButton("Administrar letras descargadas") { page="Administrar letras";render() }
        body.addView(label("Motor de audio",22f));toggle("Ecualizador activo","eq.enabled",true);slider("Velocidad","audio.rate",0.5f,2f,1f);slider("Preamplificación dB","audio.preamp",-12f,0f,0f);addButton("Ecualizador y presets") {page="EQ";render()}
        val am=getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        body.addView(label("Salidas disponibles: "+am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).joinToString { it.productName.toString() }))
        val audio=PlaybackService.instance?.player?.audioFormat
        body.addView(label("${DeviceProfile.detect()} · ${audio?.sampleRate ?: 0} Hz · ${audio?.channelCount ?: 0} canales\nAndroid administra la ruta al DAC, Bluetooth o altavoz. Esta versión no garantiza salida bit perfect.",13f))
    }
    private fun eqPage() {
        toggle("Ecualizador activo","eq.enabled",true)
        val count=prefs.getInt("eq.bands",10)
        body.addView(label(prefs.getString("eq.preset","Plano") ?: "Plano",24f))
        val bands=row();listOf(10,15,31).forEach { n->bands.addView(button("$n bandas") {prefs.edit().putInt("eq.bands",n).apply();PlaybackService.instance?.applySettings();render()},LinearLayout.LayoutParams(0,-2,1f)) };body.addView(bands)
        val presetRow=row();RivoEqualizer.presets.keys.forEach { name->presetRow.addView(button(name) {RivoEqualizer.preset(prefs,name);PlaybackService.instance?.applySettings();render()}) };val presetScroll=HorizontalScrollView(this);presetScroll.addView(presetRow);body.addView(presetScroll)
        RivoEqualizer.indices(count).forEach { i->
            val text=label("${RivoEqualizer.frequencies[i].toInt()} Hz · ${prefs.getFloat("gain.$i",0f)} dB");body.addView(text)
            body.addView(SeekBar(this).apply {max=48;progress=((prefs.getFloat("gain.$i",0f)+12)*2).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean) {if(user){val gain=p/2f-12;prefs.edit().putFloat("gain.$i",gain).putString("eq.preset","Personalizado").apply();PlaybackService.instance?.applySettings();text.text="${RivoEqualizer.frequencies[i].toInt()} Hz · $gain dB"}}
                override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
            }) })
        }
        body.addView(label("EQ de 10, 15 o 31 bandas. Reduce la preamplificación si realzas varias bandas.",13f))
    }
    private fun historyPage() { addButton("Conectar y configurar Last.fm") {page="Last.fm";render()};body.addView(label("Escuchas registradas",22f));library.tracks.filter { it.plays>0 }.sortedByDescending { it.plays }.forEach { t->addButton("${t.title} · ${t.plays} escuchas") { play(t) } } }
    private fun lastFMPage() {
        val fm=PlaybackService.instance!!.lastFM
        body.addView(label(if(fm.user.isBlank())"Conecta Last.fm" else "Conectado: ${fm.user}",22f))
        toggle("Enviar escuchas","lastfm.enabled",false)
        val key=EditText(this).apply {hint="API key";setTextColor(Color.WHITE)};val secret=EditText(this).apply {hint="Shared secret";inputType=129;setTextColor(Color.WHITE)};body.addView(key);body.addView(secret)
        addButton("Guardar credenciales") {val k=key.text.toString().trim();val v=secret.text.toString().trim();async({fm.configure(k,v)},{toast("Credenciales guardadas")})}
        addButton("Autorizar en Last.fm") {async({val url=fm.authorizeURL();runOnUiThread {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}},{})}
        addButton("Ya autoricé · conectar cuenta") {async({fm.finish()})}
        addButton("Reintentar escuchas pendientes") {async({fm.flush()})}
        addButton("Desconectar") {fm.disconnect();render()}
        body.addView(label(fm.status));body.addView(label("${fm.queue().length()} escuchas pendientes. Las credenciales se guardan cifradas en este dispositivo.",13f))
    }
    private fun transferPage() {
        val folders=library.folders();for(i in 0 until folders.length()) {val folder=folders.getJSONObject(i);body.addView(label(folder.getString("name"),18f));addButton("Volver a escanear") {async({library.importFolder(Uri.parse(folder.getString("uri")))})};addButton("Quitar carpeta de la biblioteca") {AlertDialog.Builder(this).setMessage("Se borran las copias de Rivo. La carpeta original se conserva.").setPositiveButton("Quitar") {_,_->async({library.removeFolder(folder.getString("uri"))})}.setNegativeButton("Cancelar",null).show()} }
        if(ftp==null)ftp=FTPServer(library)
        addButton(if(ftp!!.running) "Desactivar FTP" else "Activar FTP") {runCatching {if(ftp!!.running)ftp!!.stop() else ftp!!.start();render()}.onFailure {toast(it.message ?: "No se pudo iniciar FTP")} }
        if(ftp!!.running)body.addView(label("${ftp!!.address}:2121\nUsuario: rivo\nClave temporal: ${ftp!!.password}\nMantén Rivo Audio abierta. FTP es para tu red Wi-Fi local.",14f))
 addButton("Añadir carpeta desde Archivos") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),101) };addButton("Añadir canciones o videos") { pickFiles() };addButton("Administrar letras descargadas") {page="Administrar letras";render()};addButton("Ajustes visuales y de audio") {page="Ajustes";render()};body.addView(label("Los archivos se copian a la biblioteca de Rivo Audio. Las letras se guardan en Lyrics; puedes exportarlas desde su editor.")) }
    private fun pickOptions() { AlertDialog.Builder(this).setItems(arrayOf("Seleccionar carpeta","Seleccionar archivos")) { _,i->if(i==0)startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),101) else pickFiles() }.show() }
    private fun pickFiles() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("audio/*","video/*"));putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE) },100) }
    private fun editMetadata(t:Track) { editTrack=t;val panel=column();val title=EditText(this).apply {setText(t.title)};val artist=EditText(this).apply {setText(t.artist)};val album=EditText(this).apply {setText(t.album)};panel.addView(title);panel.addView(artist);panel.addView(album);val chart=EditText(this).apply {hint="Dato de lista (manual)";setText(t.chartNote)};panel.addView(chart);val rating=RatingBar(this).apply {numStars=5;stepSize=1f;rating=t.rating.toFloat()};panel.addView(rating);panel.addView(button("Cambiar carátula") {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},104)});AlertDialog.Builder(this).setTitle("Editar biblioteca").setView(panel).setPositiveButton("Guardar") { _,_->t.title=title.text.toString();t.artist=artist.text.toString();t.album=album.text.toString();t.chartNote=chart.text.toString();t.rating=rating.rating.toInt();t.verified=t.title.isNotBlank()&&t.artist.isNotBlank()&&t.artist!="Artista desconocido";library.save();render() }.setNegativeButton("Cancelar",null).show() }
    @Deprecated("Activity result compatibility") override fun onActivityResult(code:Int,result:Int,data:Intent?) {
        super.onActivityResult(code,result,data);if(result!=RESULT_OK||data==null)return
        val uri=data.data
        when(code) {
            100 -> { val uris=if(data.clipData!=null)(0 until data.clipData!!.itemCount).map {data.clipData!!.getItemAt(it).uri} else listOfNotNull(uri);toast("Importando…");async({uris.forEach {u->var name="Audio";contentResolver.query(u,null,null,null,null)?.use {if(it.moveToFirst())name=it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))};library.importFile(u,name)}}) }
            101 ->if(uri!=null){runCatching {contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};toast("Importando carpeta…");async({library.importFolder(uri)})}
            102 ->if(uri!=null) editTrack?.let {t->async({contentResolver.openInputStream(uri)?.bufferedReader()?.use {library.saveLyrics(t,it.readText(),"Archivo importado")}}, {if(lyricEditorTrackId==t.id)lyricEditor?.setText(library.readLyrics(t));render()})}
            103 ->if(uri!=null) editTrack?.let {t->async({contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {it.write(library.readLyrics(t))}}, {toast("LRC exportado")})}
            104 ->if(uri!=null) editTrack?.let {t->async({contentResolver.openInputStream(uri)?.use {input->File(library.covers,t.id+".jpg").outputStream().use {input.copyTo(it)}};thumbs.remove(t.id)})}
        }
    }
}
