package com.Atom2Universe.app.games.physics.catalog

/**
 * Catalogue moteur indépendant de l'interface.
 *
 * Les limites très larges protègent les flottants contre NaN/infini sans interdire
 * le jeu absurde : une pièce peut mesurer jusqu'à 1 000 km et développer des
 * puissances bien au-delà d'une installation industrielle réelle.
 */
object MachinePartCatalog {
    private const val MAX_LENGTH = 1_000_000f
    private const val MAX_MASS = 1e18f
    private const val MAX_FORCE = 1e18f
    private const val MAX_TORQUE = 1e21f
    private const val MAX_PRESSURE = 1e12f
    private const val MAX_POWER = 1e18f

    private fun length(key: String, default: Float, min: Float = 1e-6f) =
        PartParameterDefinition(key, ParameterUnit.METER, default, min, MAX_LENGTH)
    private fun area(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.SQUARE_METER, default, 1e-12f, 1e12f)
    private fun volume(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.CUBIC_METER, default, 1e-12f, 1e18f)
    private fun mass(key: String = "mass", default: Float = 1f) =
        PartParameterDefinition(key, ParameterUnit.KILOGRAM, default, 1e-9f, MAX_MASS)
    private fun force(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.NEWTON, default, 0f, MAX_FORCE)
    private fun torque(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.NEWTON_METER, default, 0f, MAX_TORQUE)
    private fun pressure(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.PASCAL, default, 0f, MAX_PRESSURE)
    private fun power(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.WATT, default, 0f, MAX_POWER)
    private fun ratio(key: String, default: Float, min: Float = 0f, max: Float = 1_000_000f) =
        PartParameterDefinition(key, ParameterUnit.RATIO, default, min, max)
    private fun count(key: String, default: Int, min: Int = 1, max: Int = 1_000_000) =
        PartParameterDefinition(key, ParameterUnit.COUNT, default.toFloat(), min.toFloat(), max.toFloat(), integer = true)
    private fun time(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.SECOND, default, 0f, 1e9f)
    private fun percent(key: String, default: Float) =
        PartParameterDefinition(key, ParameterUnit.PERCENT, default, 0f, 1f)

    private fun port(
        id: String, kind: PortKind,
        direction: PortDirection = PortDirection.BIDIRECTIONAL,
        layer: PortLayerRule = PortLayerRule.OVERLAPPING_LAYERS
    ) = MachinePortDefinition(id, kind, direction, layer)

    private fun definition(
        id: String,
        name: String,
        description: String,
        category: PartCategory,
        support: SimulationSupport,
        parameters: List<PartParameterDefinition> = emptyList(),
        ports: List<MachinePortDefinition> = emptyList(),
        tags: Set<String> = emptySet(),
        depth: Int = 1,
        collidable: Boolean = true
    ) = MachinePartDefinition(id, name, description, category, support, parameters, ports, tags, depth, collidable)

    val definitions: List<MachinePartDefinition> = buildList {
        // ── Structure et assemblage ──────────────────────────────────────────
        add(definition("structure.beam", "Poutre", "Poutre rectangulaire rigide paramétrique.", PartCategory.STRUCTURE, SimulationSupport.NATIVE,
            listOf(length("length", 2f), length("height", 0.15f), length("depth", 0.15f), mass()), listOf(port("mount_a", PortKind.STRUCTURAL), port("mount_b", PortKind.STRUCTURAL)), setOf("wood", "metal", "frame")))
        add(definition("structure.plate", "Plaque", "Plaque pleine pour châssis, blindage ou table.", PartCategory.STRUCTURE, SimulationSupport.NATIVE,
            listOf(length("width", 1f), length("height", 1f), length("thickness", 0.02f), mass()), listOf(port("surface", PortKind.STRUCTURAL))))
        add(definition("structure.block", "Bloc massif", "Bloc structurel plein de toute dimension.", PartCategory.STRUCTURE, SimulationSupport.NATIVE,
            listOf(length("width", 0.5f), length("height", 0.5f), length("depth", 0.5f), mass()), listOf(port("mount", PortKind.STRUCTURAL))))
        add(definition("structure.frame", "Châssis", "Cadre composé pour porter une machine complète.", PartCategory.STRUCTURE, SimulationSupport.COMPOSABLE,
            listOf(length("width", 3f), length("height", 2f), length("member_size", 0.12f), mass()), listOf(port("mount", PortKind.STRUCTURAL)), setOf("frame"), depth = 2))
        add(definition("structure.ground_anchor", "Ancrage au monde", "Fixation immobile au décor physique.", PartCategory.STRUCTURE, SimulationSupport.NATIVE,
            listOf(force("max_force", 1e9f), torque("max_torque", 1e9f)), listOf(port("mount", PortKind.STRUCTURAL)), setOf("static")))
        add(definition("structure.spacer", "Entretoise de couche", "Écarte deux pièces et transmet un axe entre couches.", PartCategory.FASTENER, SimulationSupport.COMPOSABLE,
            listOf(length("length", 0.1f), length("radius", 0.02f), mass()), listOf(
                port("side_a", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING),
                port("side_b", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING)), setOf("layer_bridge"), collidable = false))
        add(definition("structure.bolt", "Boulon", "Fixation démontable avec résistance maximale.", PartCategory.FASTENER, SimulationSupport.COMPOSABLE,
            listOf(length("diameter", 0.012f), force("shear_limit", 30_000f), force("tension_limit", 40_000f)), listOf(port("mount_a", PortKind.STRUCTURAL), port("mount_b", PortKind.STRUCTURAL))))
        add(definition("structure.weld", "Soudure", "Liaison rigide pouvant céder sous charge.", PartCategory.FASTENER, SimulationSupport.COMPOSABLE,
            listOf(length("length", 0.1f), force("shear_limit", 100_000f), torque("moment_limit", 20_000f)), listOf(port("part_a", PortKind.STRUCTURAL), port("part_b", PortKind.STRUCTURAL))))
        add(definition("structure.breakable_joint", "Liaison fusible", "Assemblage calibré pour rompre à une charge précise.", PartCategory.SAFETY, SimulationSupport.COMPOSABLE,
            listOf(force("break_force", 10_000f), torque("break_torque", 1_000f)), listOf(port("part_a", PortKind.STRUCTURAL), port("part_b", PortKind.STRUCTURAL))))
        add(definition("structure.rivet", "Rivet", "Fixation permanente légère.", PartCategory.FASTENER, SimulationSupport.COMPOSABLE,
            listOf(length("diameter", 0.008f), force("shear_limit", 8_000f)), listOf(port("part_a", PortKind.STRUCTURAL), port("part_b", PortKind.STRUCTURAL))))
        add(definition("structure.ballast", "Ballast", "Masse compacte réglable pour équilibrage.", PartCategory.STRUCTURE, SimulationSupport.NATIVE,
            listOf(mass(default = 100f), length("radius", 0.25f)), listOf(port("mount", PortKind.STRUCTURAL)), setOf("counterweight")))

        // ── Rotation, axes et stockage inertiel ─────────────────────────────
        add(definition("rotary.solid_shaft", "Arbre plein", "Arbre de transmission traversant une ou plusieurs couches.", PartCategory.ROTARY, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.025f), length("length", 1f), mass(), torque("torque_limit", 2_000f)), listOf(
                port("end_a", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ANY_LAYER), port("end_b", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ANY_LAYER)), setOf("layer_bridge"), collidable = false))
        add(definition("rotary.hollow_shaft", "Arbre creux", "Arbre léger laissant passer un autre axe ou une conduite.", PartCategory.ROTARY, SimulationSupport.COMPOSABLE,
            listOf(length("outer_radius", 0.04f), length("inner_radius", 0.03f), length("length", 1f), mass(), torque("torque_limit", 1_500f)), listOf(
                port("end_a", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ANY_LAYER), port("end_b", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ANY_LAYER)), setOf("layer_bridge"), collidable = false))
        add(definition("rotary.axle", "Axe fixe", "Axe porteur autour duquel tourne une roue.", PartCategory.ROTARY, SimulationSupport.NATIVE,
            listOf(length("radius", 0.02f), torque("friction_torque", 0.2f)), listOf(port("bearing", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING)), collidable = false))
        add(definition("rotary.bearing", "Roulement", "Palier à faible friction et charge limitée.", PartCategory.ROTARY, SimulationSupport.NATIVE,
            listOf(length("bore_radius", 0.025f), force("radial_load", 20_000f), torque("friction_torque", 0.05f)), listOf(port("housing", PortKind.STRUCTURAL), port("shaft", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING)), collidable = false))
        add(definition("rotary.bushing", "Palier lisse", "Palier robuste avec frottement sec supérieur.", PartCategory.ROTARY, SimulationSupport.NATIVE,
            listOf(length("bore_radius", 0.025f), force("radial_load", 50_000f), torque("friction_torque", 1f)), listOf(port("housing", PortKind.STRUCTURAL), port("shaft", PortKind.ROTARY_SHAFT)), collidable = false))
        add(definition("rotary.wheel", "Roue", "Roue pleine ou de roulement.", PartCategory.ROTARY, SimulationSupport.NATIVE,
            listOf(length("radius", 0.4f), length("width", 0.1f), mass(default = 15f)), listOf(port("hub", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING)), setOf("wheel")))
        add(definition("rotary.flywheel", "Volant d'inertie", "Anneau lourd stockant l'énergie cinétique.", PartCategory.ENERGY, SimulationSupport.NATIVE,
            listOf(length("outer_radius", 0.6f), length("inner_radius", 0.35f), length("width", 0.15f), mass(default = 120f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING)), setOf("energy_storage")))
        add(definition("rotary.drum", "Tambour", "Tambour d'enroulement pour câble, corde ou convoyeur.", PartCategory.ROTARY, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.25f), length("width", 0.4f), mass(default = 25f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("line", PortKind.FLEXIBLE_MECHANICAL))))
        add(definition("rotary.crank", "Manivelle", "Convertit rotation et déplacement alternatif.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.2f), mass(default = 3f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("pin", PortKind.STRUCTURAL))))
        add(definition("rotary.crankshaft", "Vilebrequin", "Arbre à plusieurs manetons pour moteurs et pompes.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            listOf(length("throw", 0.08f), count("throws", 4), mass(default = 30f)), listOf(port("input", PortKind.ROTARY_SHAFT), port("output", PortKind.ROTARY_SHAFT), port("rods", PortKind.STRUCTURAL))))
        add(definition("rotary.cam", "Came", "Profil rotatif commandant un poussoir.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(length("base_radius", 0.08f), length("lift", 0.03f), mass(default = 1f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("follower", PortKind.LINEAR_MECHANICAL))))
        add(definition("rotary.eccentric", "Excentrique", "Disque excentré pour vibration ou pompe.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.1f), length("offset", 0.02f), mass(default = 2f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("body", PortKind.STRUCTURAL))))
        add(definition("rotary.winch", "Treuil", "Tambour motorisable avec frein et câble.", PartCategory.LINEAR, SimulationSupport.COMPOSABLE,
            listOf(length("drum_radius", 0.2f), length("cable_length", 20f), force("line_pull", 20_000f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("cable", PortKind.FLEXIBLE_MECHANICAL))))

        // ── Engrenages et transmissions ─────────────────────────────────────
        val gearParams = listOf(length("pitch_radius", 0.25f), count("teeth", 40, 3), length("face_width", 0.05f), length("bore_radius", 0.02f))
        add(definition("transmission.spur_gear", "Roue dentée droite", "Engrenage extérieur droit à module calculé.", PartCategory.TRANSMISSION, SimulationSupport.NATIVE,
            gearParams, listOf(port("shaft", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ADJACENT_OR_OVERLAPPING), port("teeth", PortKind.GEAR_TEETH, layer = PortLayerRule.OVERLAPPING_LAYERS)), setOf("gear", "external")))
        add(definition("transmission.internal_ring_gear", "Couronne intérieure", "Denture intérieure pour trains planétaires.", PartCategory.TRANSMISSION, SimulationSupport.NATIVE,
            gearParams, listOf(port("mount", PortKind.STRUCTURAL), port("teeth", PortKind.GEAR_TEETH)), setOf("gear", "internal")))
        add(definition("transmission.helical_gear", "Roue hélicoïdale", "Engrenage silencieux avec poussée axiale abstraite.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            gearParams + ratio("helix_angle_deg", 20f, 0f, 60f), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("teeth", PortKind.GEAR_TEETH)), setOf("gear")))
        add(definition("transmission.rack", "Crémaillère", "Denture linéaire convertissant rotation et translation.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(length("length", 2f), length("module", 0.01f), count("teeth", 200, 3), force("force_limit", 100_000f)), listOf(port("teeth", PortKind.GEAR_TEETH), port("slider", PortKind.LINEAR_MECHANICAL)), setOf("gear", "linear")))
        add(definition("transmission.worm", "Vis sans fin", "Grande réduction, entraînement souvent irréversible.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(length("pitch_radius", 0.04f), count("starts", 1), torque("torque_limit", 500f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("teeth", PortKind.GEAR_TEETH)), setOf("gear", "worm")))
        add(definition("transmission.worm_wheel", "Roue de vis sans fin", "Roue menée d'un réducteur à vis.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            gearParams, listOf(port("shaft", PortKind.ROTARY_SHAFT), port("teeth", PortKind.GEAR_TEETH)), setOf("gear", "worm")))
        add(definition("transmission.planet_carrier", "Porte-satellites", "Support mobile d'un train épicycloïdal.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.3f), count("planet_count", 3, 2, 100)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("planet_pins", PortKind.ROTARY_SHAFT)), setOf("planetary")))
        add(definition("transmission.pulley", "Poulie", "Poulie de courroie avec rayon libre.", PartCategory.TRANSMISSION, SimulationSupport.NATIVE,
            listOf(length("radius", 0.2f), length("width", 0.04f), mass(default = 4f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("belt", PortKind.FLEXIBLE_MECHANICAL)), setOf("belt")))
        add(definition("transmission.belt", "Courroie", "Transmission souple ouverte ou croisée.", PartCategory.TRANSMISSION, SimulationSupport.NATIVE,
            listOf(length("length", 2f), force("tension_limit", 2_000f), percent("efficiency", 0.96f)), listOf(port("pulley_a", PortKind.FLEXIBLE_MECHANICAL, layer = PortLayerRule.ANY_LAYER), port("pulley_b", PortKind.FLEXIBLE_MECHANICAL, layer = PortLayerRule.ANY_LAYER)), setOf("belt"), collidable = false))
        add(definition("transmission.sprocket", "Pignon de chaîne", "Roue pour chaîne sans glissement nominal.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            gearParams, listOf(port("shaft", PortKind.ROTARY_SHAFT), port("chain", PortKind.FLEXIBLE_MECHANICAL)), setOf("chain")))
        add(definition("transmission.chain", "Chaîne", "Transmission flexible résistante aux grands couples.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            listOf(length("pitch", 0.0127f), count("links", 100), force("tension_limit", 20_000f)), listOf(port("sprocket_a", PortKind.FLEXIBLE_MECHANICAL), port("sprocket_b", PortKind.FLEXIBLE_MECHANICAL)), setOf("chain"), collidable = false))
        add(definition("transmission.gearbox", "Boîte de vitesses", "Rapport compact configurable avec pertes.", PartCategory.TRANSMISSION, SimulationSupport.COMPOSABLE,
            listOf(ratio("ratio", 4f, 0.000001f), torque("torque_limit", 10_000f), percent("efficiency", 0.94f)), listOf(port("input", PortKind.ROTARY_SHAFT), port("output", PortKind.ROTARY_SHAFT)), setOf("gearbox"), depth = 2))
        add(definition("transmission.differential", "Différentiel", "Répartit un couple entre deux sorties.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(torque("torque_limit", 5_000f), ratio("bias_ratio", 1f, 1f, 100f)), listOf(port("input", PortKind.ROTARY_SHAFT), port("left", PortKind.ROTARY_SHAFT), port("right", PortKind.ROTARY_SHAFT)), depth = 2))
        add(definition("transmission.clutch", "Embrayage", "Accouple deux arbres avec limite de couple et patinage.", PartCategory.TRANSMISSION, SimulationSupport.NATIVE,
            listOf(torque("max_torque", 1_000f), percent("engagement", 1f)), listOf(port("input", PortKind.ROTARY_SHAFT), port("output", PortKind.ROTARY_SHAFT)), setOf("clutch"), collidable = false))
        add(definition("transmission.torque_limiter", "Limiteur de couple", "Patine au-delà du couple réglé.", PartCategory.SAFETY, SimulationSupport.NATIVE,
            listOf(torque("limit", 500f)), listOf(port("input", PortKind.ROTARY_SHAFT), port("output", PortKind.ROTARY_SHAFT)), collidable = false))
        add(definition("transmission.brake", "Frein d'arbre", "Dissipe l'énergie d'un arbre avec couple réglable.", PartCategory.TRANSMISSION, SimulationSupport.NATIVE,
            listOf(torque("max_torque", 800f), percent("command", 1f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER)), collidable = false))
        add(definition("transmission.ratchet", "Rochet", "Autorise la rotation dans un seul sens.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(torque("torque_limit", 2_000f), count("teeth", 24, 3)), listOf(port("input", PortKind.ROTARY_SHAFT), port("output", PortKind.ROTARY_SHAFT))))
        add(definition("transmission.freewheel", "Roue libre", "Découple automatiquement lorsque la sortie dépasse l'entrée.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(torque("torque_limit", 1_000f)), listOf(port("input", PortKind.ROTARY_SHAFT), port("output", PortKind.ROTARY_SHAFT))))
        add(definition("transmission.universal_joint", "Joint de Cardan", "Relie deux arbres désalignés entre couches.", PartCategory.TRANSMISSION, SimulationSupport.CATALOG_ONLY,
            listOf(torque("torque_limit", 2_000f), ratio("max_angle_deg", 35f, 0f, 90f)), listOf(
                port("input", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ANY_LAYER), port("output", PortKind.ROTARY_SHAFT, layer = PortLayerRule.ANY_LAYER)), setOf("layer_bridge"), collidable = false))

        // ── Translation, élasticité et câbles ────────────────────────────────
        add(definition("linear.guide", "Rail de guidage", "Glissière droite avec course et butées.", PartCategory.LINEAR, SimulationSupport.NATIVE,
            listOf(length("stroke", 1f), force("load_limit", 20_000f)), listOf(port("frame", PortKind.STRUCTURAL), port("carriage", PortKind.LINEAR_MECHANICAL))))
        add(definition("linear.carriage", "Chariot", "Corps mobile d'un rail linéaire.", PartCategory.LINEAR, SimulationSupport.NATIVE,
            listOf(length("length", 0.25f), mass(default = 5f)), listOf(port("guide", PortKind.LINEAR_MECHANICAL), port("mount", PortKind.STRUCTURAL))))
        add(definition("linear.lead_screw", "Vis de translation", "Convertit une rotation en translation précise.", PartCategory.LINEAR, SimulationSupport.CATALOG_ONLY,
            listOf(length("lead_per_turn", 0.005f), force("force_limit", 50_000f), percent("efficiency", 0.4f)), listOf(port("shaft", PortKind.ROTARY_SHAFT), port("nut", PortKind.LINEAR_MECHANICAL))))
        add(definition("linear.spring", "Ressort", "Ressort linéaire avec raideur et précharge.", PartCategory.ENERGY, SimulationSupport.COMPOSABLE,
            listOf(force("preload", 0f), ratio("stiffness_n_per_m", 1_000f, 0f, 1e15f), length("rest_length", 0.5f)), listOf(port("end_a", PortKind.STRUCTURAL), port("end_b", PortKind.STRUCTURAL)), setOf("energy_storage"), collidable = false))
        add(definition("linear.damper", "Amortisseur", "Dissipation visqueuse entre deux points.", PartCategory.LINEAR, SimulationSupport.COMPOSABLE,
            listOf(ratio("damping_ns_per_m", 200f, 0f, 1e15f), force("max_force", 20_000f)), listOf(port("end_a", PortKind.STRUCTURAL), port("end_b", PortKind.STRUCTURAL)), collidable = false))
        add(definition("linear.gas_spring", "Vérin à gaz", "Ressort progressif par compression d'une chambre.", PartCategory.PNEUMATIC, SimulationSupport.COMPOSABLE,
            listOf(length("stroke", 0.3f), area("piston_area", 0.001f), pressure("pressure", 1e6f)), listOf(port("end_a", PortKind.STRUCTURAL), port("end_b", PortKind.STRUCTURAL))))
        add(definition("linear.rope", "Corde", "Liaison flexible qui tire sans pousser.", PartCategory.LINEAR, SimulationSupport.NATIVE,
            listOf(length("length", 5f), mass(default = 2f), force("break_force", 10_000f)), listOf(port("end_a", PortKind.FLEXIBLE_MECHANICAL, layer = PortLayerRule.ANY_LAYER), port("end_b", PortKind.FLEXIBLE_MECHANICAL, layer = PortLayerRule.ANY_LAYER)), collidable = false))
        add(definition("linear.steel_cable", "Câble acier", "Câble lourd pour treuil et pont roulant.", PartCategory.LINEAR, SimulationSupport.COMPOSABLE,
            listOf(length("length", 20f), length("diameter", 0.012f), force("break_force", 80_000f)), listOf(port("end_a", PortKind.FLEXIBLE_MECHANICAL, layer = PortLayerRule.ANY_LAYER), port("end_b", PortKind.FLEXIBLE_MECHANICAL, layer = PortLayerRule.ANY_LAYER)), collidable = false))
        add(definition("linear.lever", "Levier", "Bras rigide à pivot libre.", PartCategory.LINEAR, SimulationSupport.NATIVE,
            listOf(length("length", 1f), mass(default = 4f)), listOf(port("pivot", PortKind.ROTARY_SHAFT), port("input", PortKind.STRUCTURAL), port("output", PortKind.STRUCTURAL))))
        add(definition("linear.toggle_linkage", "Genouillère", "Mécanisme amplificateur de force près du verrouillage.", PartCategory.LINEAR, SimulationSupport.COMPOSABLE,
            listOf(length("link_length", 0.4f), force("load_limit", 100_000f)), listOf(port("input", PortKind.LINEAR_MECHANICAL), port("output", PortKind.LINEAR_MECHANICAL))))
        add(definition("linear.scissor_lift", "Ciseaux", "Élévateur extensible à bras croisés.", PartCategory.LINEAR, SimulationSupport.COMPOSABLE,
            listOf(length("link_length", 1f), count("stages", 2), force("load_limit", 20_000f)), listOf(port("base", PortKind.STRUCTURAL), port("platform", PortKind.LINEAR_MECHANICAL))))
        add(definition("linear.conveyor", "Convoyeur", "Bande mobile entraînée entre rouleaux.", PartCategory.LINEAR, SimulationSupport.CATALOG_ONLY,
            listOf(length("length", 5f), length("width", 0.8f), ratio("speed_mps", 1f, 0f, 1e5f)), listOf(port("drive", PortKind.ROTARY_SHAFT), port("frame", PortKind.STRUCTURAL))))

        addAll(pneumaticDefinitions())
        addAll(hydraulicDefinitions())
        addAll(projectileDefinitions())
        addAll(energyDefinitions())
        addAll(controlDefinitions())
    }

    private fun pneumaticDefinitions() = listOf(
        definition("pneumatic.chamber", "Chambre pneumatique", "Volume de gaz compressible adiabatique.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(volume("volume", 0.01f), pressure("gauge_pressure", 500_000f)), listOf(port("gas", PortKind.PNEUMATIC_GAS))),
        definition("pneumatic.receiver", "Réservoir d'air", "Grande réserve d'air comprimé avec pression nominale.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(volume("volume", 0.1f), pressure("pressure", 800_000f), pressure("pressure_limit", 1_200_000f)), listOf(port("inlet", PortKind.PNEUMATIC_GAS), port("outlet", PortKind.PNEUMATIC_GAS)), depth = 2),
        definition("pneumatic.pipe", "Tube d'air", "Conduite rigide avec pertes d'orifice.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(length("length", 1f), area("area", 0.0001f), pressure("pressure_limit", 2e6f)), listOf(port("a", PortKind.PNEUMATIC_GAS, layer = PortLayerRule.ANY_LAYER), port("b", PortKind.PNEUMATIC_GAS, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("pneumatic.hose", "Flexible pneumatique", "Tuyau souple pouvant traverser les couches.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(length("length", 2f), area("area", 0.00008f), pressure("pressure_limit", 1.5e6f)), listOf(port("a", PortKind.PNEUMATIC_GAS, layer = PortLayerRule.ANY_LAYER), port("b", PortKind.PNEUMATIC_GAS, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("pneumatic.manifold", "Collecteur pneumatique", "Distribue un flux d'air vers plusieurs branches.", PartCategory.PNEUMATIC, SimulationSupport.COMPOSABLE,
            listOf(count("outlets", 4, 2, 64), area("port_area", 0.0001f)), listOf(port("supply", PortKind.PNEUMATIC_GAS), port("branches", PortKind.PNEUMATIC_GAS))),
        definition("pneumatic.shutoff_valve", "Vanne d'arrêt", "Ouvre ou ferme progressivement une conduite.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(area("area", 0.0001f), percent("opening", 1f)), listOf(port("a", PortKind.PNEUMATIC_GAS), port("b", PortKind.PNEUMATIC_GAS), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("pneumatic.check_valve", "Clapet antiretour", "Autorise le gaz dans un seul sens.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(area("area", 0.0001f), pressure("cracking_pressure", 5_000f)), listOf(port("inlet", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("outlet", PortKind.PNEUMATIC_GAS, PortDirection.OUTPUT)), collidable = false),
        definition("pneumatic.regulator", "Détendeur", "Maintient une pression aval réglée.", PartCategory.PNEUMATIC, SimulationSupport.CATALOG_ONLY,
            listOf(pressure("set_pressure", 400_000f), area("area", 0.0001f)), listOf(port("inlet", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("outlet", PortKind.PNEUMATIC_GAS, PortDirection.OUTPUT))),
        definition("pneumatic.relief_valve", "Soupape pneumatique", "Évacue l'air au-dessus d'une pression sûre.", PartCategory.SAFETY, SimulationSupport.CATALOG_ONLY,
            listOf(pressure("set_pressure", 1e6f), area("area", 0.0001f)), listOf(port("inlet", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("vent", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("pneumatic.compressor", "Compresseur", "Transforme une puissance d'arbre en énergie d'air comprimé.", PartCategory.PNEUMATIC, SimulationSupport.COMPOSABLE,
            listOf(power("rated_power", 2_000f), pressure("max_pressure", 1e6f), percent("efficiency", 0.7f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("outlet", PortKind.PNEUMATIC_GAS, PortDirection.OUTPUT))),
        definition("pneumatic.vacuum_pump", "Pompe à vide", "Retire du gaz d'une chambre.", PartCategory.PNEUMATIC, SimulationSupport.CATALOG_ONLY,
            listOf(power("rated_power", 1_000f), pressure("minimum_absolute_pressure", 5_000f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("suction", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("exhaust", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("pneumatic.single_cylinder", "Vérin simple effet", "Piston pneumatique poussé dans un sens.", PartCategory.PNEUMATIC, SimulationSupport.NATIVE,
            listOf(length("stroke", 0.5f), area("piston_area", 0.002f), volume("dead_volume", 0.0002f), force("force_limit", 20_000f)), listOf(port("gas", PortKind.PNEUMATIC_GAS), port("rod", PortKind.LINEAR_MECHANICAL), port("body", PortKind.STRUCTURAL))),
        definition("pneumatic.double_cylinder", "Vérin double effet", "Deux chambres commandent les deux sens du piston.", PartCategory.PNEUMATIC, SimulationSupport.COMPOSABLE,
            listOf(length("stroke", 0.5f), area("piston_area", 0.002f), area("rod_area", 0.0003f)), listOf(port("extend", PortKind.PNEUMATIC_GAS), port("retract", PortKind.PNEUMATIC_GAS), port("rod", PortKind.LINEAR_MECHANICAL), port("body", PortKind.STRUCTURAL))),
        definition("pneumatic.air_motor", "Moteur pneumatique", "Convertit débit et pression en rotation.", PartCategory.PNEUMATIC, SimulationSupport.CATALOG_ONLY,
            listOf(volume("displacement_per_turn", 0.0001f), torque("max_torque", 100f), percent("efficiency", 0.65f)), listOf(port("inlet", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("exhaust", PortKind.EXHAUST, PortDirection.OUTPUT), port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT))),
        definition("pneumatic.nozzle", "Buse d'air", "Transforme pression d'air en jet et poussée.", PartCategory.PNEUMATIC, SimulationSupport.CATALOG_ONLY,
            listOf(area("throat_area", 0.00001f), percent("opening", 1f)), listOf(port("gas", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("jet", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("pneumatic.launcher", "Lanceur pneumatique", "Canon à détente de gaz avec recul.", PartCategory.PROJECTILE, SimulationSupport.NATIVE,
            listOf(length("barrel_length", 1f), area("bore_area", 0.005f), volume("chamber_volume", 0.002f), pressure("pressure", 600_000f), force("seal_friction", 20f)), listOf(port("gas", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("projectile", PortKind.PROJECTILE_PATH, PortDirection.OUTPUT), port("mount", PortKind.STRUCTURAL)), setOf("potato_gun")),
        definition("pneumatic.quick_exhaust", "Échappement rapide", "Vide directement un vérin sans repasser par la commande.", PartCategory.PNEUMATIC, SimulationSupport.CATALOG_ONLY,
            listOf(area("area", 0.0002f)), listOf(port("supply", PortKind.PNEUMATIC_GAS), port("cylinder", PortKind.PNEUMATIC_GAS), port("vent", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("pneumatic.venturi", "Venturi", "Crée une aspiration à partir d'un jet d'air.", PartCategory.PNEUMATIC, SimulationSupport.CATALOG_ONLY,
            listOf(area("throat_area", 0.00002f), pressure("supply_pressure", 600_000f)), listOf(port("supply", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("vacuum", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("exhaust", PortKind.EXHAUST, PortDirection.OUTPUT)))
    )

    private fun hydraulicDefinitions() = listOf(
        definition("hydraulic.reservoir", "Réservoir hydraulique", "Réserve de liquide non pressurisée.", PartCategory.HYDRAULIC, SimulationSupport.COMPOSABLE,
            listOf(volume("volume", 0.1f), ratio("density", 850f, 1f, 50_000f)), listOf(port("suction", PortKind.HYDRAULIC_LIQUID), port("return", PortKind.HYDRAULIC_LIQUID)), depth = 2),
        definition("hydraulic.accumulator", "Accumulateur hydraulique", "Stocke un liquide et du travail sous pression.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(volume("volume", 0.02f), pressure("pressure", 20e6f), pressure("pressure_limit", 40e6f)), listOf(port("fluid", PortKind.HYDRAULIC_LIQUID)), setOf("energy_storage"), depth = 2),
        definition("hydraulic.rigid_pipe", "Tube hydraulique", "Conduite rigide haute pression.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(length("length", 1f), area("area", 0.0001f), pressure("pressure_limit", 50e6f)), listOf(port("a", PortKind.HYDRAULIC_LIQUID, layer = PortLayerRule.ANY_LAYER), port("b", PortKind.HYDRAULIC_LIQUID, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("hydraulic.hose", "Flexible hydraulique", "Conduite souple entre pièces mobiles et couches.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(length("length", 2f), area("area", 0.00008f), pressure("pressure_limit", 35e6f)), listOf(port("a", PortKind.HYDRAULIC_LIQUID, layer = PortLayerRule.ANY_LAYER), port("b", PortKind.HYDRAULIC_LIQUID, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("hydraulic.manifold", "Bloc distributeur", "Collecteur multivoies haute pression.", PartCategory.HYDRAULIC, SimulationSupport.COMPOSABLE,
            listOf(count("ports", 6, 2, 64), pressure("pressure_limit", 50e6f)), listOf(port("supply", PortKind.HYDRAULIC_LIQUID), port("branches", PortKind.HYDRAULIC_LIQUID))),
        definition("hydraulic.directional_valve", "Distributeur", "Commande le sens d'un vérin ou moteur.", PartCategory.HYDRAULIC, SimulationSupport.CATALOG_ONLY,
            listOf(count("positions", 3, 2, 8), area("area", 0.0001f)), listOf(port("pressure", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("tank", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT), port("a", PortKind.HYDRAULIC_LIQUID), port("b", PortKind.HYDRAULIC_LIQUID), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER))),
        definition("hydraulic.flow_valve", "Régulateur de débit", "Étrangle le débit avec pertes mesurables.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(area("max_area", 0.0001f), percent("opening", 0.5f)), listOf(port("a", PortKind.HYDRAULIC_LIQUID), port("b", PortKind.HYDRAULIC_LIQUID), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("hydraulic.check_valve", "Clapet hydraulique", "Empêche le retour du liquide.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(area("area", 0.0001f), pressure("cracking_pressure", 100_000f)), listOf(port("inlet", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("outlet", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT)), collidable = false),
        definition("hydraulic.relief_valve", "Soupape hydraulique", "Limite la pression et renvoie au réservoir.", PartCategory.SAFETY, SimulationSupport.CATALOG_ONLY,
            listOf(pressure("set_pressure", 25e6f), area("area", 0.0001f)), listOf(port("pressure", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("return", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT))),
        definition("hydraulic.filter", "Filtre", "Protège les composants avec une perte de charge.", PartCategory.HYDRAULIC, SimulationSupport.CATALOG_ONLY,
            listOf(area("area", 0.001f), pressure("max_delta_pressure", 500_000f)), listOf(port("inlet", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("outlet", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT))),
        definition("hydraulic.pump", "Pompe hydraulique", "Pompe volumétrique entraînée par arbre.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(volume("displacement_per_turn", 0.00001f), torque("max_torque", 500f), pressure("target_pressure", 20e6f), percent("efficiency", 0.85f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("suction", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("pressure", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT))),
        definition("hydraulic.hand_pump", "Pompe manuelle", "Petite pompe alternative actionnée par levier.", PartCategory.HYDRAULIC, SimulationSupport.COMPOSABLE,
            listOf(volume("volume_per_stroke", 0.00001f), pressure("max_pressure", 70e6f)), listOf(port("lever", PortKind.LINEAR_MECHANICAL, PortDirection.INPUT), port("suction", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("pressure", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT))),
        definition("hydraulic.cylinder", "Vérin hydraulique", "Actionneur linéaire double effet de forte puissance.", PartCategory.HYDRAULIC, SimulationSupport.COMPOSABLE,
            listOf(length("stroke", 1f), area("piston_area", 0.01f), area("rod_area", 0.002f), force("force_limit", 500_000f)), listOf(port("extend", PortKind.HYDRAULIC_LIQUID), port("retract", PortKind.HYDRAULIC_LIQUID), port("rod", PortKind.LINEAR_MECHANICAL), port("body", PortKind.STRUCTURAL))),
        definition("hydraulic.ram", "Presse à vérin", "Vérin simple effet massif pour presse.", PartCategory.HYDRAULIC, SimulationSupport.COMPOSABLE,
            listOf(length("stroke", 0.5f), area("piston_area", 0.05f), force("force_limit", 5e6f)), listOf(port("pressure", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("ram", PortKind.LINEAR_MECHANICAL), port("frame", PortKind.STRUCTURAL)), depth = 2),
        definition("hydraulic.motor", "Moteur hydraulique", "Convertit pression et débit en couple.", PartCategory.HYDRAULIC, SimulationSupport.CATALOG_ONLY,
            listOf(volume("displacement_per_turn", 0.00005f), torque("max_torque", 2_000f), percent("efficiency", 0.85f)), listOf(port("pressure", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("return", PortKind.HYDRAULIC_LIQUID, PortDirection.OUTPUT), port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT))),
        definition("hydraulic.nozzle", "Buse hydraulique", "Convertit la pression liquide en vitesse et poussée.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(area("area", 1e-6f), percent("discharge_coefficient", 0.92f)), listOf(port("fluid", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("jet", PortKind.FLUID_JET, PortDirection.OUTPUT))),
        definition("hydraulic.waterjet_head", "Tête de découpe à eau", "Buse très haute pression avec modèle de pénétration.", PartCategory.HYDRAULIC, SimulationSupport.NATIVE,
            listOf(area("orifice_area", 1.25e-7f), pressure("working_pressure", 600e6f), percent("efficiency", 0.35f)), listOf(port("water", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("abrasive", PortKind.PROJECTILE_PATH, PortDirection.INPUT), port("jet", PortKind.FLUID_JET, PortDirection.OUTPUT)), setOf("waterjet", "cutting")),
        definition("hydraulic.abrasive_feeder", "Doseur d'abrasif", "Injecte des grains dans une tête de découpe.", PartCategory.HYDRAULIC, SimulationSupport.CATALOG_ONLY,
            listOf(mass("capacity", 10f), ratio("mass_flow_kgps", 0.005f, 0f, 1e6f)), listOf(port("abrasive", PortKind.PROJECTILE_PATH, PortDirection.OUTPUT), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER))),
        definition("hydraulic.press_frame", "Bâti de presse", "Structure dimensionnée pour fermer l'effort d'un vérin.", PartCategory.STRUCTURE, SimulationSupport.COMPOSABLE,
            listOf(length("width", 2f), length("height", 3f), force("load_limit", 5e6f), mass(default = 2_000f)), listOf(port("base", PortKind.STRUCTURAL), port("ram_mount", PortKind.STRUCTURAL)), depth = 3)
    )

    private fun projectileDefinitions() = listOf(
        definition("projectile.sphere", "Projectile sphérique", "Boule de toute masse et diamètre.", PartCategory.PROJECTILE, SimulationSupport.NATIVE,
            listOf(length("radius", 0.05f), mass(default = 0.5f)), listOf(port("path", PortKind.PROJECTILE_PATH)), setOf("ammo")),
        definition("projectile.slug", "Projectile cylindrique", "Masse cylindrique guidée dans un tube.", PartCategory.PROJECTILE, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.04f), length("length", 0.1f), mass(default = 0.5f)), listOf(port("path", PortKind.PROJECTILE_PATH)), setOf("ammo")),
        definition("projectile.potato", "Pomme de terre", "Projectile léger déformable pour lance-patate.", PartCategory.PROJECTILE, SimulationSupport.COMPOSABLE,
            listOf(length("radius", 0.04f), mass(default = 0.25f), force("seal_force", 40f)), listOf(port("path", PortKind.PROJECTILE_PATH)), setOf("ammo", "potato_gun")),
        definition("projectile.sabot", "Sabot", "Adaptateur léger entre projectile et alésage.", PartCategory.PROJECTILE, SimulationSupport.CATALOG_ONLY,
            listOf(length("bore_radius", 0.05f), length("projectile_radius", 0.02f), mass(default = 0.05f)), listOf(port("barrel", PortKind.PROJECTILE_PATH), port("payload", PortKind.STRUCTURAL))),
        definition("projectile.barrel", "Tube de lancement", "Guide linéaire d'un projectile sous pression.", PartCategory.PROJECTILE, SimulationSupport.COMPOSABLE,
            listOf(length("length", 1f), length("bore_radius", 0.04f), pressure("pressure_limit", 2e6f)), listOf(port("breech", PortKind.PNEUMATIC_GAS), port("path", PortKind.PROJECTILE_PATH), port("mount", PortKind.STRUCTURAL))),
        definition("projectile.breech", "Culasse", "Fermeture arrière résistante avec déclenchement.", PartCategory.PROJECTILE, SimulationSupport.CATALOG_ONLY,
            listOf(pressure("pressure_limit", 10e6f), force("lock_force", 100_000f)), listOf(port("chamber", PortKind.PNEUMATIC_GAS), port("barrel", PortKind.PROJECTILE_PATH), port("trigger", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER))),
        definition("projectile.muzzle_brake", "Frein de bouche", "Dévie un jet pour réduire le recul.", PartCategory.PROJECTILE, SimulationSupport.CATALOG_ONLY,
            listOf(percent("recoil_reduction", 0.35f)), listOf(port("path", PortKind.PROJECTILE_PATH), port("exhaust", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("projectile.catch_box", "Piège à projectile", "Absorbe progressivement l'énergie d'un tir d'essai.", PartCategory.SAFETY, SimulationSupport.COMPOSABLE,
            listOf(length("depth", 1f), PartParameterDefinition("energy_capacity", ParameterUnit.JOULE, 100_000f, 0f, 1e18f)), listOf(port("path", PortKind.PROJECTILE_PATH, PortDirection.INPUT), port("mount", PortKind.STRUCTURAL)), depth = 2)
    )

    private fun energyDefinitions() = listOf(
        definition("energy.rotary_motor", "Moteur rotatif idéal", "Source de vitesse limitée en couple.", PartCategory.ENERGY, SimulationSupport.NATIVE,
            listOf(PartParameterDefinition("target_speed", ParameterUnit.RADIAN_PER_SECOND, 10f, -1e6f, 1e6f), torque("max_torque", 100f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER))),
        definition("energy.linear_motor", "Moteur linéaire idéal", "Source de vitesse linéaire limitée en force.", PartCategory.ENERGY, SimulationSupport.NATIVE,
            listOf(ratio("target_speed_mps", 1f, -1e6f, 1e6f), force("max_force", 1_000f)), listOf(port("output", PortKind.LINEAR_MECHANICAL, PortDirection.OUTPUT), port("control", PortKind.CONTROL_SIGNAL, layer = PortLayerRule.ANY_LAYER))),
        definition("energy.electric_motor", "Moteur électrique", "Convertit puissance électrique en couple avec rendement.", PartCategory.ENERGY, SimulationSupport.COMPOSABLE,
            listOf(power("rated_power", 2_000f), torque("stall_torque", 20f), PartParameterDefinition("no_load_speed", ParameterUnit.RADIAN_PER_SECOND, 300f, 0f, 1e6f), percent("efficiency", 0.9f)), listOf(port("power", PortKind.ELECTRICAL_POWER, PortDirection.INPUT, PortLayerRule.ANY_LAYER), port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT))),
        definition("energy.generator", "Génératrice", "Freine un arbre et produit de l'électricité.", PartCategory.ENERGY, SimulationSupport.CATALOG_ONLY,
            listOf(power("rated_power", 5_000f), percent("efficiency", 0.9f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("power", PortKind.ELECTRICAL_POWER, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER))),
        definition("energy.battery", "Batterie", "Stockage électrique avec énergie et puissance limites.", PartCategory.ENERGY, SimulationSupport.CATALOG_ONLY,
            listOf(PartParameterDefinition("capacity", ParameterUnit.JOULE, 3.6e6f, 0f, 1e18f), power("max_power", 10_000f), PartParameterDefinition("voltage", ParameterUnit.VOLT, 48f, 0f, 1e9f)), listOf(port("power", PortKind.ELECTRICAL_POWER, layer = PortLayerRule.ANY_LAYER)), setOf("energy_storage"), collidable = false),
        definition("energy.capacitor", "Condensateur", "Stockage électrique de forte puissance et courte durée.", PartCategory.ENERGY, SimulationSupport.CATALOG_ONLY,
            listOf(PartParameterDefinition("capacity_f", ParameterUnit.NONE, 10f, 0f, 1e12f), PartParameterDefinition("voltage", ParameterUnit.VOLT, 100f, 0f, 1e9f)), listOf(port("power", PortKind.ELECTRICAL_POWER, layer = PortLayerRule.ANY_LAYER)), setOf("energy_storage"), collidable = false),
        definition("energy.combustion_engine", "Moteur à combustion", "Source de couple dépendant du régime et du carburant.", PartCategory.ENERGY, SimulationSupport.CATALOG_ONLY,
            listOf(power("rated_power", 50_000f), torque("peak_torque", 150f), volume("displacement", 0.002f)), listOf(port("fuel", PortKind.HYDRAULIC_LIQUID, PortDirection.INPUT), port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT), port("exhaust", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("energy.steam_engine", "Machine à vapeur", "Transforme une différence de pression en rotation alternative.", PartCategory.ENERGY, SimulationSupport.CATALOG_ONLY,
            listOf(area("piston_area", 0.01f), length("stroke", 0.2f), pressure("max_pressure", 2e6f)), listOf(port("steam", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("exhaust", PortKind.EXHAUST, PortDirection.OUTPUT), port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT))),
        definition("energy.turbine", "Turbine", "Convertit un jet de fluide en puissance d'arbre.", PartCategory.ENERGY, SimulationSupport.CATALOG_ONLY,
            listOf(length("radius", 0.3f), power("rated_power", 20_000f), percent("efficiency", 0.75f)), listOf(port("fluid", PortKind.FLUID_JET, PortDirection.INPUT), port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT))),
        definition("energy.human_crank", "Manivelle humaine", "Source limitée de couple et puissance pour essais.", PartCategory.ENERGY, SimulationSupport.COMPOSABLE,
            listOf(power("sustainable_power", 100f), torque("max_torque", 50f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.OUTPUT)))
    )

    private fun controlDefinitions() = listOf(
        definition("control.trigger", "Déclencheur", "Signal manuel instantané.", PartCategory.CONTROL, SimulationSupport.COMPOSABLE,
            listOf(time("pulse_duration", 0.05f)), listOf(port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("control.switch", "Interrupteur", "Commande binaire persistante.", PartCategory.CONTROL, SimulationSupport.COMPOSABLE,
            emptyList(), listOf(port("input", PortKind.CONTROL_SIGNAL, PortDirection.INPUT, PortLayerRule.ANY_LAYER), port("output", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("control.timer", "Minuterie", "Retarde ou cadence un signal.", PartCategory.CONTROL, SimulationSupport.CATALOG_ONLY,
            listOf(time("delay", 1f), time("period", 1f)), listOf(port("input", PortKind.CONTROL_SIGNAL, PortDirection.INPUT, PortLayerRule.ANY_LAYER), port("output", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("control.logic_gate", "Porte logique", "Combine plusieurs signaux de contrôle.", PartCategory.CONTROL, SimulationSupport.CATALOG_ONLY,
            listOf(count("inputs", 2, 1, 64)), listOf(port("inputs", PortKind.CONTROL_SIGNAL, PortDirection.INPUT, PortLayerRule.ANY_LAYER), port("output", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("control.pid", "Régulateur PID", "Asservit vitesse, position, pression ou débit.", PartCategory.CONTROL, SimulationSupport.CATALOG_ONLY,
            listOf(ratio("kp", 1f), ratio("ki", 0f), ratio("kd", 0f)), listOf(port("setpoint", PortKind.CONTROL_SIGNAL, PortDirection.INPUT, PortLayerRule.ANY_LAYER), port("measurement", PortKind.CONTROL_SIGNAL, PortDirection.INPUT, PortLayerRule.ANY_LAYER), port("output", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("sensor.pressure", "Capteur de pression", "Mesure un circuit pneumatique ou hydraulique via adaptateur.", PartCategory.SENSOR, SimulationSupport.COMPOSABLE,
            listOf(pressure("range", 100e6f)), listOf(port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("sensor.tachometer", "Tachymètre", "Mesure la vitesse d'un arbre.", PartCategory.SENSOR, SimulationSupport.COMPOSABLE,
            emptyList(), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("sensor.encoder", "Codeur angulaire", "Mesure angle et vitesse d'un arbre.", PartCategory.SENSOR, SimulationSupport.COMPOSABLE,
            listOf(count("steps_per_turn", 1_024)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("sensor.force", "Capteur de force", "Mesure l'effort traversant une liaison.", PartCategory.SENSOR, SimulationSupport.COMPOSABLE,
            listOf(force("range", 100_000f)), listOf(port("mount_a", PortKind.STRUCTURAL), port("mount_b", PortKind.STRUCTURAL), port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("sensor.position", "Capteur de position", "Mesure la course d'une glissière.", PartCategory.SENSOR, SimulationSupport.COMPOSABLE,
            listOf(length("range", 1f)), listOf(port("linear", PortKind.LINEAR_MECHANICAL, PortDirection.INPUT), port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("sensor.limit_switch", "Fin de course", "Émet un signal à une position limite.", PartCategory.SENSOR, SimulationSupport.COMPOSABLE,
            emptyList(), listOf(port("trigger", PortKind.LINEAR_MECHANICAL, PortDirection.INPUT), port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("safety.rupture_disk", "Disque de rupture", "S'ouvre définitivement au-dessus de la pression réglée.", PartCategory.SAFETY, SimulationSupport.CATALOG_ONLY,
            listOf(pressure("burst_pressure", 2e6f), area("area", 0.0001f)), listOf(port("inlet", PortKind.PNEUMATIC_GAS, PortDirection.INPUT), port("vent", PortKind.EXHAUST, PortDirection.OUTPUT))),
        definition("safety.fuse", "Fusible électrique", "Coupe un circuit au-dessus du courant admis.", PartCategory.SAFETY, SimulationSupport.CATALOG_ONLY,
            listOf(PartParameterDefinition("current_limit", ParameterUnit.AMPERE, 20f, 0f, 1e9f)), listOf(port("a", PortKind.ELECTRICAL_POWER, layer = PortLayerRule.ANY_LAYER), port("b", PortKind.ELECTRICAL_POWER, layer = PortLayerRule.ANY_LAYER)), collidable = false),
        definition("safety.emergency_stop", "Arrêt d'urgence", "Signal prioritaire coupant moteurs et vannes.", PartCategory.SAFETY, SimulationSupport.CATALOG_ONLY,
            emptyList(), listOf(port("signal", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)), collidable = false),
        definition("control.governor", "Régulateur centrifuge", "Limite mécaniquement la vitesse d'un arbre.", PartCategory.CONTROL, SimulationSupport.CATALOG_ONLY,
            listOf(PartParameterDefinition("target_speed", ParameterUnit.RADIAN_PER_SECOND, 20f, 0f, 1e6f), torque("control_torque", 50f)), listOf(port("shaft", PortKind.ROTARY_SHAFT, PortDirection.INPUT), port("control", PortKind.CONTROL_SIGNAL, PortDirection.OUTPUT, PortLayerRule.ANY_LAYER)))
    )

    private val byId = definitions.associateBy { it.id }

    fun find(id: String): MachinePartDefinition? = byId[id]
    fun require(id: String): MachinePartDefinition = byId[id] ?: error("pièce inconnue : $id")
    fun category(category: PartCategory): List<MachinePartDefinition> = definitions.filter { it.category == category }
    fun tagged(tag: String): List<MachinePartDefinition> = definitions.filter { tag in it.tags }
}

