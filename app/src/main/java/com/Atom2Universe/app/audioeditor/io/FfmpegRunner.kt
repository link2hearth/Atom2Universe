package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lance FFmpeg avec un **tableau d'arguments** (jamais une chaîne passée à un shell : un titre qui
 * contient une apostrophe ou un `$` ne peut rien casser). Bloque jusqu'à la fin, sur le fil appelant.
 *
 * C'est le seul endroit du module qui touche à FFmpegKit, ce qui garde le reste testable en JVM.
 */
object FfmpegRunner {

    /**
     * @param durationMs durée de l'audio traité, pour convertir la progression de FFmpeg (temps écoulé) en fraction ; 0 si inconnue
     * @throws java.util.concurrent.CancellationException si [ctx] est annulé (FFmpeg est alors interrompu)
     * @throws IOException si FFmpeg échoue
     */
    fun run(args: List<String>, durationMs: Long, ctx: RunContext) {
        val done = CountDownLatch(1)
        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            { done.countDown() },
            null,
            { stats ->
                if (durationMs > 0) ctx.report((stats.time / durationMs).toFloat().coerceIn(0f, 1f))
            },
        )
        try {
            while (!done.await(150, TimeUnit.MILLISECONDS)) ctx.checkCancelled()
        } catch (e: Throwable) {
            FFmpegKit.cancel(session.sessionId)
            throw e
        }
        val rc = session.returnCode
        when {
            ReturnCode.isSuccess(rc) -> ctx.report(1f)
            ReturnCode.isCancel(rc) -> throw java.util.concurrent.CancellationException("FFmpeg annulé")
            else -> throw IOException("FFmpeg a échoué : " + (session.failStackTrace ?: session.output?.takeLast(400) ?: "code ${rc?.value}"))
        }
    }
}

object FfmpegTranscoder : Transcoder {
    override fun run(args: List<String>, durationMs: Long, ctx: RunContext) = FfmpegRunner.run(args, durationMs, ctx)
}
