package com.Atom2Universe.app.science.parentes

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A reading experience with a finite illustrated sequence, separate from atlas navigation. */
class ParentesStoryActivity : ThemedActivity() {
    private val palette by lazy { SciencePalette(this) }
    private val preferences by lazy { getSharedPreferences("parentes_stories", MODE_PRIVATE) }
    private lateinit var repo: ParentesRepository
    private lateinit var catalogue: ParentesStories
    private lateinit var root: LinearLayout
    private var storyId = ""
    private var chapterIndex = 0
    private var autoPlay = false
    private var pendingAnimation = false
    private var advance: Job? = null
    private var illustration: ParentesIllustrationView? = null
    private var playButton: Button? = null
    private var chapterDialog: AlertDialog? = null
    private val current get() = if (::catalogue.isInitialized) catalogue.stories.firstOrNull { it.id == storyId } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storyId = savedInstanceState?.getString("story").orEmpty()
        chapterIndex = savedInstanceState?.getInt("chapter") ?: 0
        root = column().apply { setBackgroundColor(palette.background) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left+dp(16), bars.top+dp(6), bars.right+dp(16), bars.bottom+dp(8))
            insets
        }
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if(current != null) library() else finish() }
        })
        load()
    }

    private fun load() {
        root.removeAllViews()
        header(getString(R.string.pt_story_library)) { finish() }
        root.addView(ProgressBar(this))
        root.addView(label(getString(R.string.pt_loading)))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching {
                val repository = ParentesRepository.load(applicationContext)
                repository to ParentesStories.load(applicationContext, repository.tree)
            } }
            result.onSuccess { (repository, stories) ->
                repo=repository; catalogue=stories
                if(current == null) library() else showChapter()
            }.onFailure { error ->
                android.util.Log.e("ParentesStories", "Story load failed", error)
                root.removeAllViews(); header(getString(R.string.pt_story_library)) { finish() }
                root.addView(label(getString(R.string.pt_error)))
                root.addView(button(getString(R.string.pt_story_retry)) { load() })
            }
        }
    }

    private fun clear() {
        advance?.cancel(); advance=null
        illustration?.stop(); illustration=null; playButton=null
        root.removeAllViews()
    }

    private fun library() {
        autoPlay=false; storyId=""; clear()
        header(getString(R.string.pt_story_library)) { finish() }
        val content=column()
        content.addView(label(getString(R.string.pt_story_intro),17f))
        catalogue.stories.forEach { story ->
            val card=column().apply {
                setPadding(dp(14),dp(14),dp(14),dp(14)); background=palette.shape(palette.surface,22f)
            }
            card.addView(ParentesIllustrationView(this,story.chapters.last().art).apply {
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(-1,dp(158)))
            card.addView(label(repo.text(this,story.title),22f,true))
            card.addView(label(repo.text(this,story.subtitle),16f))
            card.addView(label(getString(R.string.pt_story_chapters,story.chapters.size),13f))
            val saved=preferences.getInt(story.id,0).coerceIn(story.chapters.indices)
            card.addView(button(getString(if(saved>0) R.string.pt_story_resume else R.string.pt_story_start, saved+1)) {
                storyId=story.id; chapterIndex=saved; showChapter()
            },LinearLayout.LayoutParams(-1,-2))
            content.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(16) })
        }
        content.addView(label(getString(R.string.pt_story_limits),13f))
        root.addView(ScrollView(this).apply { addView(content) },LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun showChapter() {
        val story=current ?: return library()
        chapterIndex=chapterIndex.coerceIn(story.chapters.indices)
        val chapter=story.chapters[chapterIndex]
        preferences.edit().putInt(story.id,chapterIndex).apply()
        clear()
        header(repo.text(this,story.title)) { library() }
        val progress=row()
        progress.addView(button(getString(R.string.pt_story_chapter,chapterIndex+1,story.chapters.size)) { chooseChapter(story) },
            LinearLayout.LayoutParams(-2,-2))
        progress.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=story.chapters.size; setProgress(chapterIndex+1,false)
            progressTintList=android.content.res.ColorStateList.valueOf(palette.accent)
            contentDescription=getString(R.string.pt_story_chapter,chapterIndex+1,story.chapters.size)
        },LinearLayout.LayoutParams(0,dp(8),1f).apply { marginStart=dp(12) })
        root.addView(progress)

        val artColumn=column()
        val art=ParentesIllustrationView(this,chapter.art).apply { contentDescription=repo.text(this@ParentesStoryActivity,chapter.caption) }
        illustration=art
        artColumn.addView(art,LinearLayout.LayoutParams(-1,dp(180)))
        val chapterHeading=column()
        chapterHeading.addView(label(repo.name(this,chapter.node),13f).apply { setTextColor(palette.ink(palette.accent,palette.background)) })
        chapterHeading.addView(label(repo.text(this,chapter.title),25f,true))
        val narrative=column()
        narrative.addView(label(repo.text(this,chapter.body),17f).apply {
            setLineSpacing(dp(3).toFloat(),1f); setTextIsSelectable(true)
        })

        val forkName=if(repo.tree.nodes.getValue(chapter.fork).scientific.isEmpty()) getString(R.string.pt_common_node)
            else repo.name(this,chapter.fork)
        artColumn.addView(label(getString(R.string.pt_story_ancestor,forkName),13f))
        val fork=ParentesStoryForkView(this).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        artColumn.addView(fork,LinearLayout.LayoutParams(-1,dp(56)))
        art.onProgress={ fork.progress=it }
        val branches=row().apply { gravity=Gravity.TOP }
        branches.addView(label(getString(R.string.pt_story_toward,repo.name(this,story.endpoint)),14f,true).apply {
            setTextColor(palette.ink(0xff71cba5.toInt(),palette.background))
        },LinearLayout.LayoutParams(0,-2,1f))
        branches.addView(label(getString(R.string.pt_story_cousin,repo.name(this,chapter.cousin)),14f,true).apply {
            setTextColor(palette.ink(0xffe6b574.toInt(),palette.background))
        },LinearLayout.LayoutParams(0,-2,1f))
        artColumn.addView(branches)
        narrative.addView(label(repo.text(this,chapter.caption),13f))
        narrative.addView(button(getString(R.string.pt_story_replay)) { art.play() })
        narrative.addView(label(getString(R.string.pt_story_fork_hint),13f))
        narrative.addView(button(getString(R.string.pt_story_explore)) {
            pause()
            setResult(RESULT_OK,Intent().putExtra("first",story.endpoint).putExtra("second",chapter.cousin))
            finish()
        },LinearLayout.LayoutParams(-1,-2))

        val wide=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE && resources.configuration.screenWidthDp>=600
        if(wide) {
            narrative.addView(chapterHeading,0)
            root.addView(row().apply {
                gravity=Gravity.TOP
                addView(ScrollView(this@ParentesStoryActivity).apply { addView(artColumn) },LinearLayout.LayoutParams(0,-1,.42f))
                addView(ScrollView(this@ParentesStoryActivity).apply { addView(narrative) },LinearLayout.LayoutParams(0,-1,.58f))
            },LinearLayout.LayoutParams(-1,0,1f))
        } else {
            root.addView(ScrollView(this).apply {
                addView(column().apply { addView(chapterHeading); addView(artColumn); addView(narrative) })
            },LinearLayout.LayoutParams(-1,0,1f))
        }
        val navigation=row()
        navigation.addView(icon(R.drawable.ic_chevron_left,R.string.pt_previous) {
            pause(); chapterIndex--; showChapter()
        }.apply { isEnabled=chapterIndex>0; alpha=if(isEnabled) 1f else .35f })
        playButton=button(getString(if(autoPlay) R.string.pt_story_pause else R.string.pt_story_play)) {
            if(autoPlay) pause() else { autoPlay=true; art.resume(); updatePlayButton(); scheduleAdvance() }
        }
        navigation.addView(playButton,LinearLayout.LayoutParams(0,-2,1f))
        navigation.addView(button(getString(if(chapterIndex==story.chapters.lastIndex) R.string.pt_story_finish else R.string.pt_next)) {
            pause()
            if(chapterIndex==story.chapters.lastIndex) library() else { chapterIndex++; showChapter() }
        },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(navigation)
        pendingAnimation=true
        art.post {
            if(illustration===art && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                pendingAnimation=false; art.play()
            }
        }
        if(autoPlay) scheduleAdvance()
    }

    private fun scheduleAdvance() {
        advance?.cancel()
        val story=current ?: return
        if(!autoPlay) return
        val chapter=story.chapters[chapterIndex]
        // A reading interval derived from the actual localized text, including the fork.
        val words=(repo.text(this,chapter.body)+" "+repo.text(this,chapter.caption)+" "+getString(R.string.pt_story_fork_hint))
            .split(Regex("\\s+")).size
        advance=lifecycleScope.launch {
            delay((words*380L).coerceAtLeast(20_000L))
            if(chapterIndex<story.chapters.lastIndex) { chapterIndex++; showChapter() } else pause()
        }
    }

    private fun pause() {
        autoPlay=false; advance?.cancel(); advance=null
        illustration?.pause(); updatePlayButton()
    }
    private fun updatePlayButton() { playButton?.setText(if(autoPlay) R.string.pt_story_pause else R.string.pt_story_play) }

    private fun chooseChapter(story:LineageStory) {
        pause()
        chapterDialog=AlertDialog.Builder(this).setTitle(R.string.pt_story_choose)
            .setSingleChoiceItems(story.chapters.mapIndexed { index,c ->
                getString(R.string.pt_trail_step,repo.text(this,c.title),index+1,story.chapters.size)
            }.toTypedArray(),chapterIndex) { dialog,index ->
                chapterIndex=index; dialog.dismiss(); showChapter()
            }.setNeutralButton(R.string.pt_story_restart) { _,_ -> chapterIndex=0; showChapter() }
            .setNegativeButton(R.string.pt_close,null).show()
    }

    private fun header(title:String,back:()->Unit) {
        root.addView(row().apply {
            addView(icon(R.drawable.ic_arrow_back_24,R.string.pt_back,back))
            addView(label(title,20f,true),LinearLayout.LayoutParams(0,-2,1f))
        })
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun label(value:String,size:Float=16f,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size; setTextColor(palette.text)
        setPadding(dp(4),dp(7),dp(4),dp(7))
        if(bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun button(value:String,action:()->Unit)=androidx.appcompat.widget.AppCompatButton(this).apply {
        text=value; textSize=14f; isAllCaps=false; minHeight=dp(48); minimumHeight=dp(48)
        setPadding(dp(12),dp(8),dp(12),dp(8)); setTextColor(palette.text)
        background=palette.shape(palette.raised,14f); setOnClickListener { action() }
        layoutParams=LinearLayout.LayoutParams(-2,-2).apply { setMargins(dp(3),dp(5),dp(3),dp(5)) }
    }
    private fun icon(drawable:Int,description:Int,action:()->Unit)=androidx.appcompat.widget.AppCompatImageButton(this).apply {
        setImageResource(drawable); imageTintList=android.content.res.ColorStateList.valueOf(palette.text)
        contentDescription=getString(description); background=null; setPadding(dp(12),dp(12),dp(12),dp(12))
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)); setOnClickListener { action() }
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    override fun onSaveInstanceState(outState:Bundle) {
        super.onSaveInstanceState(outState); outState.putString("story",storyId); outState.putInt("chapter",chapterIndex)
    }
    override fun onResume() {
        super.onResume()
        if(pendingAnimation) { pendingAnimation=false; illustration?.play() }
    }
    override fun onPause() { pause(); super.onPause() }
    override fun onDestroy() { advance?.cancel(); illustration?.stop(); chapterDialog?.dismiss(); super.onDestroy() }
}
