package com.Atom2Universe.app.science.biology

import com.Atom2Universe.app.R

/** Editorial physiology lessons: anatomical targets never imply a simulated process. */
data class AnatomyJourneyStep(
    val title: Int, val body: Int, val sources: List<AnatomyJourneySource>,
    val families: Set<String> = emptySet(),
    val regions: Set<AnatomyRegion> = emptySet(),
    val layers: Set<AnatomyLayer> = emptySet(),
) {
    fun structures(catalog: AnatomyCatalog) = catalog.structures.filter {
        it.hasMesh && (it.family in families ||
            (it.layer in layers && (regions.isEmpty() || it.region in regions)))
    }
}

data class AnatomyJourney(val id: String, val title: Int, val intro: Int, val steps: List<AnatomyJourneyStep>)

enum class AnatomyJourneySource(val label: Int, val url: String) {
    AIRWAYS(R.string.bio_j_source_airways, "https://www.nhlbi.nih.gov/health/lungs/respiratory-system"),
    BREATHING(R.string.bio_j_source_breathing, "https://www.nhlbi.nih.gov/health/lungs/breathing-benefits"),
    CONTROL(R.string.bio_j_source_control, "https://www.nhlbi.nih.gov/health/lungs/body-controls-breathing"),
    CIRCULATION(R.string.bio_j_source_circulation, "https://www.nhlbi.nih.gov/health/heart/blood-flow"),
    RHYTHM(R.string.bio_j_source_rhythm, "https://www.nhlbi.nih.gov/health/heart/heart-beats"),
    DIGESTION(R.string.bio_j_source_digestion, "https://www.niddk.nih.gov/health-information/digestive-diseases/digestive-system-how-it-works"),
    ASTHMA(R.string.bio_j_source_asthma, "https://www.nhlbi.nih.gov/health/asthma"),
    BONES(R.string.bio_j_source_bones, "https://openstax.org/books/anatomy-and-physiology-2e/pages/6-1-the-functions-of-the-skeletal-system"),
    REMODELING(R.string.bio_j_source_remodeling, "https://openstax.org/books/anatomy-and-physiology-2e/pages/6-6-exercise-nutrition-hormones-and-bone-tissue"),
}

object AnatomyJourneys {
    private fun step(title: Int, body: Int, source: AnatomyJourneySource, vararg families: String) =
        AnatomyJourneyStep(title, body, listOf(source), families.toSet())

    val all = listOf(
        AnatomyJourney("breathing", R.string.bio_j_breathing, R.string.bio_j_breathing_intro, listOf(
            step(R.string.bio_j_air_title, R.string.bio_j_air_body, AnatomyJourneySource.AIRWAYS,
                "organ_trachea", "organ_bronchus", "organ_lung"),
            step(R.string.bio_j_diaphragm_title, R.string.bio_j_diaphragm_body, AnatomyJourneySource.BREATHING,
                "muscle_diaphragm", "organ_lung"),
            step(R.string.bio_j_exchange_title, R.string.bio_j_exchange_body, AnatomyJourneySource.BREATHING, "organ_lung"),
            step(R.string.bio_j_circuit_title, R.string.bio_j_circuit_body, AnatomyJourneySource.CIRCULATION,
                "organ_heart", "organ_lung"),
            step(R.string.bio_j_control_title, R.string.bio_j_control_body, AnatomyJourneySource.CONTROL,
                "muscle_diaphragm", "organ_lung", "organ_heart"),
        )),
        AnatomyJourney("heartbeat", R.string.bio_j_heartbeat, R.string.bio_j_heartbeat_intro, listOf(
            step(R.string.bio_j_pump_title, R.string.bio_j_pump_body, AnatomyJourneySource.CIRCULATION, "organ_heart"),
            step(R.string.bio_j_signal_title, R.string.bio_j_signal_body, AnatomyJourneySource.RHYTHM, "organ_heart"),
            step(R.string.bio_j_valves_title, R.string.bio_j_valves_body, AnatomyJourneySource.RHYTHM, "organ_heart"),
            step(R.string.bio_j_pulse_title, R.string.bio_j_pulse_body, AnatomyJourneySource.RHYTHM,
                "organ_heart", "organ_lung"),
        )),
        AnatomyJourney("digestion", R.string.bio_j_digestion, R.string.bio_j_digestion_intro, listOf(
            step(R.string.bio_j_mouth_title, R.string.bio_j_mouth_body, AnatomyJourneySource.DIGESTION,
                "organ_tongue", "organ_salivary", "organ_esophagus"),
            step(R.string.bio_j_stomach_title, R.string.bio_j_stomach_body, AnatomyJourneySource.DIGESTION,
                "organ_esophagus", "organ_stomach"),
            step(R.string.bio_j_juices_title, R.string.bio_j_juices_body, AnatomyJourneySource.DIGESTION,
                "organ_liver", "organ_gallbladder", "organ_pancreas", "organ_duodenum"),
            step(R.string.bio_j_absorb_title, R.string.bio_j_absorb_body, AnatomyJourneySource.DIGESTION,
                "organ_duodenum", "organ_jejunum", "organ_ileum"),
            step(R.string.bio_j_colon_title, R.string.bio_j_colon_body, AnatomyJourneySource.DIGESTION,
                "organ_cecum", "organ_colon", "organ_rectum"),
        )),
        AnatomyJourney("asthma", R.string.bio_j_asthma, R.string.bio_j_asthma_intro, listOf(
            step(R.string.bio_j_asthma_air_title, R.string.bio_j_asthma_air_body, AnatomyJourneySource.ASTHMA,
                "organ_bronchus", "organ_lung"),
            step(R.string.bio_j_asthma_trigger_title, R.string.bio_j_asthma_trigger_body, AnatomyJourneySource.ASTHMA,
                "organ_trachea", "organ_bronchus", "organ_lung"),
            step(R.string.bio_j_asthma_exchange_title, R.string.bio_j_asthma_exchange_body, AnatomyJourneySource.BREATHING,
                "organ_lung", "organ_heart"),
        )),
        AnatomyJourney("skeleton", R.string.bio_j_skeleton, R.string.bio_j_skeleton_intro, listOf(
            AnatomyJourneyStep(R.string.bio_j_protect_title, R.string.bio_j_protect_body,
                listOf(AnatomyJourneySource.BONES), regions = setOf(AnatomyRegion.SKULL, AnatomyRegion.SPINE),
                layers = setOf(AnatomyLayer.SKELETON)),
            AnatomyJourneyStep(R.string.bio_j_ribs_title, R.string.bio_j_ribs_body,
                listOf(AnatomyJourneySource.BONES), families = setOf("organ_heart", "organ_lung"),
                regions = setOf(AnatomyRegion.THORAX), layers = setOf(AnatomyLayer.SKELETON)),
            step(R.string.bio_j_levers_title, R.string.bio_j_levers_body, AnatomyJourneySource.BONES,
                "humerus", "radius", "ulna", "muscle_biceps"),
            step(R.string.bio_j_marrow_title, R.string.bio_j_marrow_body, AnatomyJourneySource.BONES, "femur"),
            step(R.string.bio_j_remodel_title, R.string.bio_j_remodel_body, AnatomyJourneySource.REMODELING,
                "femur", "muscle_quadriceps"),
        )),
    )
    fun find(id: String?) = all.firstOrNull { it.id == id }
}
