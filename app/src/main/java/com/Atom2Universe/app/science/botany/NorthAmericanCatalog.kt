package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Native North American taxa; regional season notes avoid a continent-wide calendar.
 * Drawing dimensions refer to the principal subject (shoot, inflorescence or isolated organ);
 * enlarged insets have their own scale, as in the rest of the atlas.
 */
internal object NorthAmericanCatalog {
    val species = listOf(
        PlantSpecies(
            id="sequoiadendron", name=R.string.bot_sequoiadendron_name, latin=R.string.bot_sequoiadendron_latin, aliases=R.string.bot_sequoiadendron_aliases,
            familyId="Cupressaceae", sectionIds=setOf("trees"), heightMinCm=3000, heightMaxCm=9500,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WOOD, Habitat.MOUNTAIN, Habitat.PARK), evergreen=true,
            look=PlantLook(Crown.CONICAL, LeafShape.SCALE, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.CONIFER, FruitShape.CONE, FlowerColor.GREEN, 6.5f, 7f, 6f,
                seed=106, bark=0xff9c563a.toInt(), leafColor=0xff588778.toInt(), leafDetail=LeafDetail(awlShoot=true), architecture=TreeArchitecture(radius=75f, top=29f, bottom=202f, trunkWidth=38f, crooked=1f, spray=ConiferSpray.AWL, roundedConifer=true), flowerDetail=FlowerDetail(terminalPollen=true), fruitDetail=FruitDetail(cone=ConeForm.SEQUOIA)),
            description=R.string.bot_sequoiadendron_description, distribution=R.string.bot_sequoiadendron_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_trees, PlantView.LEAF to R.string.bot_awl_shoot,
                PlantView.FLOWER to FlowerShape.CONIFER.label, PlantView.FRUIT to FruitShape.CONE.label),
            sourceIds=setOf("powo", "osu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_sequoiadendron

        ),
        PlantSpecies(
            id="sequoia", name=R.string.bot_sequoia_name, latin=R.string.bot_sequoia_latin, aliases=R.string.bot_sequoia_aliases,
            familyId="Cupressaceae", sectionIds=setOf("trees"), heightMinCm=4000, heightMaxCm=11500,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WOOD, Habitat.WET, Habitat.PARK), evergreen=true,
            look=PlantLook(Crown.CONICAL, LeafShape.NEEDLE, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.CONIFER, FruitShape.CONE, FlowerColor.GREEN, 2f, 7f, 2.5f,
                seed=107, bark=0xff985a46.toInt(), leafDetail=LeafDetail(needleWidth=.037f, stomatalBands=true, needleAngle=79f), architecture=TreeArchitecture(radius=49f, top=20f, bottom=225f, trunkWidth=28f, crooked=1f), flowerDetail=FlowerDetail(terminalPollen=true), fruitDetail=FruitDetail(cone=ConeForm.REDWOOD)),
            description=R.string.bot_sequoia_description, distribution=R.string.bot_sequoia_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_trees, PlantView.LEAF to LeafShape.NEEDLE.label,
                PlantView.FLOWER to FlowerShape.CONIFER.label, PlantView.FRUIT to FruitShape.CONE.label),
            sourceIds=setOf("powo", "osu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_sequoia,
            additionalLeaves=setOf(LeafShape.SCALE)
        ),
        PlantSpecies(
            id="pseudotsuga", name=R.string.bot_pseudotsuga_name, latin=R.string.bot_pseudotsuga_latin, aliases=R.string.bot_pseudotsuga_aliases,
            familyId="Pinaceae", sectionIds=setOf("trees"), heightMinCm=2000, heightMaxCm=9000,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WOOD, Habitat.MOUNTAIN, Habitat.PARK), evergreen=true,
            look=PlantLook(Crown.CONICAL, LeafShape.NEEDLE, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.CONIFER, FruitShape.CONE, FlowerColor.GREEN, 3f, 12f, 8f,
                seed=108, bark=0xff89705a.toInt(), leafDetail=LeafDetail(needleWidth=.03f, needleAngle=66f), architecture=TreeArchitecture(radius=70f, top=27f, bottom=235f, trunkWidth=24f), fruitDetail=FruitDetail(cone=ConeForm.DOUGLAS)),
            description=R.string.bot_pseudotsuga_description, distribution=R.string.bot_pseudotsuga_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_trees, PlantView.LEAF to LeafShape.NEEDLE.label,
                PlantView.FLOWER to FlowerShape.CONIFER.label, PlantView.FRUIT to FruitShape.CONE.label),
            sourceIds=setOf("powo", "usfs"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_douglas

        ),
        PlantSpecies(
            id="acer_saccharum", name=R.string.bot_acer_saccharum_name, latin=R.string.bot_acer_saccharum_latin, aliases=R.string.bot_acer_saccharum_aliases,
            familyId="Sapindaceae", sectionIds=setOf("trees"), heightMinCm=1500, heightMaxCm=3500,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WOOD, Habitat.PARK), evergreen=false,
            look=PlantLook(Crown.ROUND, LeafShape.MAPLE, LeafMargin.LOBED, LeafArrangement.OPPOSITE,
                FlowerShape.CORYMB, FruitShape.DOUBLE_SAMARA, FlowerColor.YELLOW, 15f, 12f, 3f,
                seed=109, samaraAngle=35f, leafDetail=LeafDetail(lobeDepth=.47f, tip=LeafTip.ACUMINATE, palmateSegments=5, venation=Venation.PALMATE), architecture=TreeArchitecture(radius=99f, top=40f, bottom=210f, trunkWidth=22f), flowerDetail=FlowerDetail(cluster=FlowerCluster.PENDULOUS_UMBEL, perianthLobes=5, stamens=8)),
            description=R.string.bot_acer_saccharum_description, distribution=R.string.bot_acer_saccharum_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_trees, PlantView.LEAF to LeafShape.MAPLE.label,
                PlantView.FLOWER to FlowerShape.CORYMB.label, PlantView.FRUIT to FruitShape.DOUBLE_SAMARA.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_spring

        ),
        PlantSpecies(
            id="quercus_rubra", name=R.string.bot_quercus_rubra_name, latin=R.string.bot_quercus_rubra_latin, aliases=R.string.bot_quercus_rubra_aliases,
            familyId="Fagaceae", sectionIds=setOf("trees"), heightMinCm=1500, heightMaxCm=3000,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WOOD, Habitat.PARK), evergreen=false,
            look=PlantLook(Crown.SPREAD, LeafShape.OAK, LeafMargin.LOBED, LeafArrangement.ALTERNATE,
                FlowerShape.CATKIN, FruitShape.ACORN, FlowerColor.GREEN, 20f, 10f, 2.5f,
                seed=110, petiole=.23f, fruitStalk=.06f, leafDetail=LeafDetail(width=.34f, lobePairs=4, lobeDepth=.44f, roundedLobes=false, bristleTips=true), architecture=TreeArchitecture(radius=103f, top=46f, bottom=216f, trunkWidth=23f), fruitDetail=FruitDetail(shallowCup=true)),
            description=R.string.bot_quercus_rubra_description, distribution=R.string.bot_quercus_rubra_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_trees, PlantView.LEAF to LeafShape.OAK.label,
                PlantView.FLOWER to FlowerShape.CATKIN.label, PlantView.FRUIT to FruitShape.ACORN.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_rubra

        ),
        PlantSpecies(
            id="vaccinium_corymbosum", name=R.string.bot_vaccinium_corymbosum_name, latin=R.string.bot_vaccinium_corymbosum_latin, aliases=R.string.bot_vaccinium_corymbosum_aliases,
            familyId="Ericaceae", sectionIds=setOf("shrubs"), heightMinCm=100, heightMaxCm=350,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WET, Habitat.WOOD, Habitat.HEATH), evergreen=false,
            look=PlantLook(Crown.SHRUB, LeafShape.OVAL, LeafMargin.SERRATE, LeafArrangement.ALTERNATE,
                FlowerShape.BELL, FruitShape.BERRY, FlowerColor.WHITE, 7f, 0.8f, 1f,
                seed=111, leafDetail=LeafDetail(width=.28f, veinPairs=6), flowerDetail=FlowerDetail(corolla=CorollaForm.URN), fruitColor=0xff405577.toInt(), fruitDetail=FruitDetail(count=7, bloom=true, persistentCalyx=true)),
            description=R.string.bot_vaccinium_corymbosum_description, distribution=R.string.bot_vaccinium_corymbosum_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_shrubs, PlantView.LEAF to LeafShape.OVAL.label,
                PlantView.FLOWER to FlowerShape.BELL.label, PlantView.FRUIT to FruitShape.BERRY.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_spring,
            additionalMargins=setOf(LeafMargin.ENTIRE), additionalColors=setOf(FlowerColor.PINK)
        ),
        PlantSpecies(
            id="cornus_sericea", name=R.string.bot_cornus_sericea_name, latin=R.string.bot_cornus_sericea_latin, aliases=R.string.bot_cornus_sericea_aliases,
            familyId="Cornaceae", sectionIds=setOf("shrubs"), heightMinCm=100, heightMaxCm=300,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WET, Habitat.HEDGE, Habitat.WOOD), evergreen=false,
            look=PlantLook(Crown.SHRUB, LeafShape.OVAL, LeafMargin.ENTIRE, LeafArrangement.OPPOSITE,
                FlowerShape.CORYMB, FruitShape.DRUPE, FlowerColor.WHITE, 10f, 10f, 0.8f,
                seed=112, petals=4, bark=0xffa83e3e.toInt(), leafDetail=LeafDetail(venation=Venation.ARCUATE, veinPairs=6, tip=LeafTip.ACUMINATE), architecture=TreeArchitecture(radius=92f, top=97f, bottom=245f, density=.62f), flowerDetail=FlowerDetail(stamens=4), fruitColor=0xffe4e7dc.toInt(), fruitDetail=FruitDetail(count=11)),
            description=R.string.bot_cornus_sericea_description, distribution=R.string.bot_cornus_sericea_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_shrubs, PlantView.LEAF to LeafShape.OVAL.label,
                PlantView.FLOWER to FlowerShape.CORYMB.label, PlantView.FRUIT to FruitShape.DRUPE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_spring

        ),
        PlantSpecies(
            id="arctostaphylos", name=R.string.bot_arctostaphylos_name, latin=R.string.bot_arctostaphylos_latin, aliases=R.string.bot_arctostaphylos_aliases,
            familyId="Ericaceae", sectionIds=setOf("shrubs"), heightMinCm=5, heightMaxCm=30,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.HEATH, Habitat.MOUNTAIN, Habitat.WOOD), evergreen=true,
            look=PlantLook(Crown.SHRUB, LeafShape.OVAL, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.BELL, FruitShape.DRUPE, FlowerColor.WHITE, 2.5f, 0.6f, 0.8f,
                seed=113, fineShrub=true, leafDetail=LeafDetail(width=.25f, tip=LeafTip.ROUNDED, glossy=true, leafletWidestAboveMiddle=true), flowerDetail=FlowerDetail(corolla=CorollaForm.URN), fruitColor=0xffba4540.toInt(), fruitDetail=FruitDetail(count=5), herbDetail=HerbArchitecture(woodyMat=true, basalRosette=false, flowerScale=.13f)),
            description=R.string.bot_arctostaphylos_description, distribution=R.string.bot_arctostaphylos_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_shrubs, PlantView.LEAF to LeafShape.OVAL.label,
                PlantView.FLOWER to FlowerShape.BELL.label, PlantView.FRUIT to FruitShape.DRUPE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_spring,
            additionalColors=setOf(FlowerColor.PINK)
        ),
        PlantSpecies(
            id="rhododendron_catawbiense", name=R.string.bot_rhododendron_catawbiense_name, latin=R.string.bot_rhododendron_catawbiense_latin, aliases=R.string.bot_rhododendron_catawbiense_aliases,
            familyId="Ericaceae", sectionIds=setOf("shrubs"), heightMinCm=150, heightMaxCm=400,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.WOOD, Habitat.MOUNTAIN), evergreen=true,
            look=PlantLook(Crown.SHRUB, LeafShape.OVAL, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.BELL, FruitShape.CAPSULE, FlowerColor.PURPLE, 15f, 18f, 2f,
                seed=114, leafDetail=LeafDetail(width=.23f, glossy=true, veinPairs=10), flowerDetail=FlowerDetail(corolla=CorollaForm.FUNNEL, cluster=FlowerCluster.TERMINAL_TRUSS, stamens=10), fruitDetail=FruitDetail(capsule=CapsuleForm.VALVATE)),
            description=R.string.bot_rhododendron_catawbiense_description, distribution=R.string.bot_rhododendron_catawbiense_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_shrubs, PlantView.LEAF to LeafShape.OVAL.label,
                PlantView.FLOWER to FlowerShape.BELL.label, PlantView.FRUIT to FruitShape.CAPSULE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_spring

        ),
        PlantSpecies(
            id="rosa_virginiana", name=R.string.bot_rosa_virginiana_name, latin=R.string.bot_rosa_virginiana_latin, aliases=R.string.bot_rosa_virginiana_aliases,
            familyId="Rosaceae", sectionIds=setOf("shrubs"), heightMinCm=50, heightMaxCm=200,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.HEDGE, Habitat.MEADOW, Habitat.WOOD), evergreen=false,
            look=PlantLook(Crown.SHRUB, LeafShape.PINNATE, LeafMargin.SERRATE, LeafArrangement.ALTERNATE,
                FlowerShape.CUP, FruitShape.HIP, FlowerColor.PINK, 12f, 6f, 1.5f,
                seed=115, leaflets=7, thorny=true, leafDetail=LeafDetail(leafletShape=LeafShape.OVAL, leafletWidth=.28f, glossy=true), flowerDetail=FlowerDetail(stamens=40), fruitColor=0xffc65035.toInt(), fruitDetail=FruitDetail(count=4, persistentCalyx=true)),
            description=R.string.bot_rosa_virginiana_description, distribution=R.string.bot_rosa_virginiana_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_shrubs, PlantView.LEAF to LeafShape.PINNATE.label,
                PlantView.FLOWER to FlowerShape.CUP.label, PlantView.FRUIT to FruitShape.HIP.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_summer

        ),
        PlantSpecies(
            id="echinacea", name=R.string.bot_echinacea_name, latin=R.string.bot_echinacea_latin, aliases=R.string.bot_echinacea_aliases,
            familyId="Asteraceae", sectionIds=setOf("herbs"), heightMinCm=60, heightMaxCm=150,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.MEADOW, Habitat.WOOD, Habitat.PARK), evergreen=false,
            look=PlantLook(Crown.HERB, LeafShape.OVAL, LeafMargin.SERRATE, LeafArrangement.ALTERNATE,
                FlowerShape.DAISY, FruitShape.ACHENE, FlowerColor.PURPLE, 15f, 10f, 0.5f,
                seed=116, leafDetail=LeafDetail(width=.25f, venation=Venation.THREE_BASAL, ciliate=true), flowerDetail=FlowerDetail(rays=18, discHeight=43f, rayDroop=36f, discColor=0xff9e5c2e.toInt()), fruitDetail=FruitDetail(acheneWidth=19f, acheneRibs=4, pappusScales=true), herbDetail=HerbArchitecture(flowerScale=.38f, branches=false)),
            description=R.string.bot_echinacea_description, distribution=R.string.bot_echinacea_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_herbs, PlantView.LEAF to LeafShape.OVAL.label,
                PlantView.FLOWER to FlowerShape.DAISY.label, PlantView.FRUIT to FruitShape.ACHENE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_summer

        ),
        PlantSpecies(
            id="rudbeckia", name=R.string.bot_rudbeckia_name, latin=R.string.bot_rudbeckia_latin, aliases=R.string.bot_rudbeckia_aliases,
            familyId="Asteraceae", sectionIds=setOf("herbs"), heightMinCm=30, heightMaxCm=100,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.MEADOW, Habitat.DISTURBED, Habitat.PARK), evergreen=false,
            look=PlantLook(Crown.HERB, LeafShape.LANCE, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.DAISY, FruitShape.ACHENE, FlowerColor.YELLOW, 12f, 8f, 0.2f,
                seed=117, leafDetail=LeafDetail(width=.20f, ciliate=true, venation=Venation.THREE_BASAL), flowerDetail=FlowerDetail(rays=13, discHeight=31f, rayDroop=7f, discColor=0xff42302e.toInt()), fruitDetail=FruitDetail(acheneWidth=12f, acheneRibs=4), herbDetail=HerbArchitecture(flowerScale=.34f)),
            description=R.string.bot_rudbeckia_description, distribution=R.string.bot_rudbeckia_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_herbs, PlantView.LEAF to LeafShape.LANCE.label,
                PlantView.FLOWER to FlowerShape.DAISY.label, PlantView.FRUIT to FruitShape.ACHENE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_summer,
            additionalMargins=setOf(LeafMargin.SERRATE)
        ),
        PlantSpecies(
            id="helianthus", name=R.string.bot_helianthus_name, latin=R.string.bot_helianthus_latin, aliases=R.string.bot_helianthus_aliases,
            familyId="Asteraceae", sectionIds=setOf("herbs"), heightMinCm=50, heightMaxCm=300,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.MEADOW, Habitat.DISTURBED, Habitat.PARK), evergreen=false,
            look=PlantLook(Crown.HERB, LeafShape.HEART, LeafMargin.SERRATE, LeafArrangement.ALTERNATE,
                FlowerShape.DAISY, FruitShape.ACHENE, FlowerColor.YELLOW, 25f, 15f, 1f,
                seed=118, leafDetail=LeafDetail(base=LeafBase.CORDATE, width=.40f, venation=Venation.THREE_BASAL, ciliate=true), flowerDetail=FlowerDetail(rays=26, discRadius=39f, discColor=0xff695134.toInt()), fruitColor=0xff493e32.toInt(), fruitDetail=FruitDetail(acheneWidth=23f, acheneRibs=5, pappusScales=true, acheneColor=0xff514339.toInt()), herbDetail=HerbArchitecture(basalRosette=false, basalLeaf=LeafShape.HEART, lowerLeavesOpposite=true, branches=true, flowerScale=.43f)),
            description=R.string.bot_helianthus_description, distribution=R.string.bot_helianthus_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_herbs, PlantView.LEAF to LeafShape.HEART.label,
                PlantView.FLOWER to FlowerShape.DAISY.label, PlantView.FRUIT to FruitShape.ACHENE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_summer,
            additionalArrangements=setOf(LeafArrangement.OPPOSITE)
        ),
        PlantSpecies(
            id="eschscholzia", name=R.string.bot_eschscholzia_name, latin=R.string.bot_eschscholzia_latin, aliases=R.string.bot_eschscholzia_aliases,
            familyId="Papaveraceae", sectionIds=setOf("herbs"), heightMinCm=15, heightMaxCm=60,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.MEADOW, Habitat.DRY_SCRUB, Habitat.DISTURBED), evergreen=false,
            look=PlantLook(Crown.HERB, LeafShape.DIVIDED, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.CUP, FruitShape.CAPSULE, FlowerColor.ORANGE, 15f, 6f, 7f,
                seed=119, petals=4, leafColor=0xff78a396.toInt(), leafDetail=LeafDetail(finelyDivided=true), flowerDetail=FlowerDetail(corolla=CorollaForm.GOLDEN_CUP, stamens=28), fruitDetail=FruitDetail(capsule=CapsuleForm.LINEAR), herbDetail=HerbArchitecture(flowerScale=.32f)),
            description=R.string.bot_eschscholzia_description, distribution=R.string.bot_eschscholzia_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_herbs, PlantView.LEAF to LeafShape.DIVIDED.label,
                PlantView.FLOWER to FlowerShape.CUP.label, PlantView.FRUIT to FruitShape.CAPSULE.label),
            sourceIds=setOf("powo", "ncsu"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_california_poppy,
            additionalColors=setOf(FlowerColor.YELLOW)
        ),
        PlantSpecies(
            id="lupinus_polyphyllus", name=R.string.bot_lupinus_polyphyllus_name, latin=R.string.bot_lupinus_polyphyllus_latin, aliases=R.string.bot_lupinus_polyphyllus_aliases,
            familyId="Fabaceae", sectionIds=setOf("herbs"), heightMinCm=60, heightMaxCm=150,
            flowering=emptySet(), fruiting=emptySet(), habitats=setOf(Habitat.MEADOW, Habitat.WET, Habitat.WOOD), evergreen=false,
            look=PlantLook(Crown.HERB, LeafShape.PALMATE, LeafMargin.ENTIRE, LeafArrangement.ALTERNATE,
                FlowerShape.PEA, FruitShape.POD, FlowerColor.BLUE, 20f, 35f, 4f,
                seed=120, leaflets=11, petiole=.43f, leafDetail=LeafDetail(leafletWidth=.14f, radialLeaflets=true), flowerDetail=FlowerDetail(cluster=FlowerCluster.RACEMOSE, denseRaceme=true), fruitDetail=FruitDetail(podSeeds=7, podWidth=12f), herbDetail=HerbArchitecture(branches=false, flowerScale=.43f)),
            description=R.string.bot_lupinus_polyphyllus_description, distribution=R.string.bot_lupinus_polyphyllus_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_section_herbs, PlantView.LEAF to LeafShape.PALMATE.label,
                PlantView.FLOWER to FlowerShape.PEA.label, PlantView.FRUIT to FruitShape.POD.label),
            sourceIds=setOf("powo"),
            collectionIds=setOf("north_america"), phenologyNote=R.string.bot_phenology_na_summer,
            additionalColors=setOf(FlowerColor.PURPLE)
        )
    )
}
