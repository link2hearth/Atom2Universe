package com.Atom2Universe.app.midi.sf2

import android.util.Log
import java.io.File

/**
 * Outil de diagnostic pour identifier pourquoi certains presets SF2 ne se chargent pas.
 * Affiche des informations détaillées sur les presets, zones, et régions rejetées.
 */
object Sf2DiagnosticTool {
    private const val TAG = "Sf2Diagnostic"

    /**
     * Analyse un fichier SF2 et affiche un rapport détaillé sur tous les presets
     */
    fun analyzeSf2File(filePath: String) {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "ANALYSE DU FICHIER SF2: $filePath")
        Log.d(TAG, "=========================================")

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "❌ Fichier introuvable: $filePath")
                return
            }

            Log.d(TAG, "📁 Taille du fichier: ${file.length() / 1024 / 1024} MB")

            // Parse avec diagnostics détaillés
            val sf2File = parseWithDiagnostics(file)

            Log.d(TAG, "\n📊 STATISTIQUES GLOBALES:")
            Log.d(TAG, "  - Nombre total de presets: ${sf2File.presetCount}")
            Log.d(TAG, "  - Nombre total de samples: ${sf2File.sampleCount}")
            Log.d(TAG, "  - Mémoire utilisée: ${sf2File.memoryUsageBytes / 1024 / 1024} MB")

            // Analyse chaque preset
            val programs = sf2File.getPrograms()
            Log.d(TAG, "\n🎹 DÉTAILS DES PRESETS:")

            for (program in programs) {
                analyzePreset(sf2File, program)
            }

            Log.d(TAG, "\n=========================================")
            Log.d(TAG, "FIN DE L'ANALYSE")
            Log.d(TAG, "=========================================")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'analyse: ${e.message}", e)
        }
    }

    /**
     * Analyse un preset spécifique en détail
     */
    private fun analyzePreset(sf2File: Sf2File, program: ProgramInfo) {
        val preset = sf2File.getPreset(program.bank, program.program) ?: return

        Log.d(TAG, "\n  📌 Preset: ${program.bank}:${program.program} '${program.name}'")
        Log.d(TAG, "     Nombre de régions: ${preset.regions.size}")

        if (preset.regions.isEmpty()) {
            Log.w(TAG, "     ⚠️ PRESET VIDE - Aucune région valide!")
        } else {
            // Analyse la distribution des régions
            val keyRanges = preset.regions.map { "${it.keyRange.first}-${it.keyRange.last}" }.distinct()
            val velRanges = preset.regions.map { "${it.velRange.first}-${it.velRange.last}" }.distinct()

            Log.d(TAG, "     Key ranges: ${keyRanges.size} différents")
            Log.d(TAG, "     Velocity ranges: ${velRanges.size} différents")

            // Vérifie les régions avec des problèmes potentiels
            val suspiciousRegions = preset.regions.filter { region ->
                val keyCenter = (region.keyRange.first + region.keyRange.last) / 2
                val offset = kotlin.math.abs(keyCenter - region.rootKey)
                offset > 24  // Plus de 2 octaves
            }

            if (suspiciousRegions.isNotEmpty()) {
                Log.w(TAG, "     ⚠️ ${suspiciousRegions.size} régions avec un rootKey offset > 2 octaves")
            }
        }
    }

    /**
     * Analyse un preset spécifique par bank:program
     */
    fun analyzeSpecificPreset(filePath: String, bank: Int, program: Int) {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "ANALYSE DÉTAILLÉE DU PRESET $bank:$program")
        Log.d(TAG, "=========================================")

        try {
            val sf2File = parseWithDiagnostics(File(filePath))
            val preset = sf2File.getPreset(bank, program)

            if (preset == null) {
                Log.e(TAG, "❌ Preset $bank:$program introuvable!")
                return
            }

            Log.d(TAG, "\n📌 Preset: '${preset.name}'")
            Log.d(TAG, "   Bank: $bank, Program: $program")
            Log.d(TAG, "   Nombre de régions: ${preset.regions.size}")

            if (preset.regions.isEmpty()) {
                Log.e(TAG, "\n❌ PROBLÈME: Ce preset n'a AUCUNE région valide!")
                Log.e(TAG, "   Causes possibles:")
                Log.e(TAG, "   1. Toutes les régions ont été rejetées (rootKey extrême)")
                Log.e(TAG, "   2. Les instruments référencés n'existent pas")
                Log.e(TAG, "   3. Les samples sont invalides")
                Log.e(TAG, "\n   Consultez les logs du parser ci-dessus pour plus de détails.")
            } else {
                Log.d(TAG, "\n✓ Preset valide avec ${preset.regions.size} régions")

                // Affiche les 10 premières régions
                Log.d(TAG, "\n📋 Premières régions (max 10):")
                preset.regions.take(10).forEachIndexed { index, region ->
                    Log.d(TAG, "   ${index + 1}. Key: ${region.keyRange}, Vel: ${region.velRange}, " +
                            "RootKey: ${region.rootKey}, Sample: '${region.sampleName}'")
                }

                if (preset.regions.size > 10) {
                    Log.d(TAG, "   ... et ${preset.regions.size - 10} autres régions")
                }
            }

            Log.d(TAG, "\n=========================================")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur: ${e.message}", e)
        }
    }

    /**
     * Test si un preset peut jouer une note spécifique
     */
    fun testPresetNote(filePath: String, bank: Int, program: Int, note: Int, velocity: Int) {
        Log.d(TAG, "🎵 TEST: Preset $bank:$program, Note=$note, Velocity=$velocity")

        try {
            val sf2File = parseWithDiagnostics(File(filePath))
            val regions = sf2File.getRegions(bank, program, note, velocity)

            Log.d(TAG, "   Régions correspondantes: ${regions.size}")

            if (regions.isEmpty()) {
                Log.w(TAG, "   ⚠️ Aucune région ne correspond à cette note!")
            } else {
                regions.forEachIndexed { index, region ->
                    val gain = region.calculateGain()
                    val gainDb = if (gain > 0) 20 * kotlin.math.log10(gain.toDouble()) else -96.0

                    Log.d(TAG, "   ✓ Région ${index + 1}:")
                    Log.d(TAG, "     • Key ${region.keyRange}, Vel ${region.velRange}")
                    Log.d(TAG, "     • Sample: '${region.sampleName}'")
                    Log.d(TAG, "     • Atténuation: ${region.attenuation} cB (${region.attenuation / 10.0} dB)")
                    Log.d(TAG, "     • Gain calculé: ${String.format("%.4f", gain)} (${String.format("%.2f", gainDb)} dB)")
                    Log.d(TAG, "     • Pan: ${region.pan}")

                    region.volumeEnvelope?.let { env ->
                        Log.d(TAG, "     • Envelope: A=${env.attack}s D=${env.decay}s S=${env.sustain} R=${env.release}s")
                    }

                    if (region.filterFc != null) {
                        Log.d(TAG, "     • Filtre: Fc=${region.filterFc} cents, Q=${region.filterQ ?: 0}")
                    }

                    // Warnings
                    if (region.attenuation > 200) {
                        Log.w(TAG, "     ⚠️ Atténuation très élevée (> 200 cB)!")
                    }
                    if (gain < 0.01f) {
                        Log.w(TAG, "     ⚠️ Gain très faible (< 1%) - région presque inaudible!")
                    }
                    region.volumeEnvelope?.let { env ->
                        if (env.sustain < 0.2f) {
                            Log.w(TAG, "     ⚠️ Sustain très faible (< 20%)")
                        }
                    }
                }

                // Calculer le gain effectif final avec tous les multiplicateurs
                val sampleRegion = regions.firstOrNull()
                if (sampleRegion != null) {
                    val baseGain = sampleRegion.calculateGain()
                    val globalGain = 0.25f  // valeur par défaut du SF2Engine
                    val effectiveGain = baseGain * globalGain
                    val effectiveGainDb = if (effectiveGain > 0) 20 * kotlin.math.log10(effectiveGain.toDouble()) else -96.0

                    Log.d(TAG, "\n   📊 GAIN EFFECTIF (avec globalGain=0.25):")
                    Log.d(TAG, "      ${String.format("%.4f", effectiveGain)} (${String.format("%.2f", effectiveGainDb)} dB)")

                    if (effectiveGain < 0.01f) {
                        Log.e(TAG, "\n   ❌ PROBLÈME DÉTECTÉ: Le gain effectif est < 1%!")
                        Log.e(TAG, "      La note sera pratiquement inaudible.")
                        Log.e(TAG, "      Solutions possibles:")
                        Log.e(TAG, "      1. Augmenter le globalGain (actuellement 0.25)")
                        Log.e(TAG, "      2. L'atténuation dans le SF2 est trop élevée")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur: ${e.message}", e)
        }
    }
}

/**
 * Parse un fichier SF2 avec logs de diagnostic
 */
private fun parseWithDiagnostics(file: File): Sf2File {
    Log.d("Sf2Diagnostic", "\n🔍 Parsing du fichier: ${file.name}")
    val parser = Sf2Parser()
    return parser.parse(file)
}
