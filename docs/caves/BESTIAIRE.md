# Relooking du bestiaire

Les douze créatures de survie et le soldat Assaut utilisent désormais les modèles
de `render/EnemyModels.kt`. Les animaux conservent leurs modèles et leurs robes.
Les matériaux mats, aux couleurs désaturées, accompagnent la direction pastel.

| Ennemi | Silhouette et détails |
| --- | --- |
| Zombie | Vêtements usés bleu grisé, peau sauge, cheveux et visage asymétrique |
| Squelette | Côtes séparées, colonne, bassin, mâchoire et cavités du crâne |
| Ogre | Ventre clair, ceinture, bracelets et défenses |
| Nain | Casque à crête, barbe en deux mèches, boucle et marteau |
| Gobelin | Grandes oreilles effilées, nez, sacoche et petite lame |
| Troll | Longs membres, plaques de mousse, nez et petites défenses |
| Golem | Épaulières, genouillères, fissures et rune lumineuse |
| Spectre | Capuche creuse, mains pâles et pans de robe flottants |
| Araignée | Abdomen marqué, huit pattes à deux segments, yeux et crochets |
| Diablotin | Peau corail, petites ailes nervurées, cornes et queue |
| Momie | Bandages superposés, coutures et bande pendante |
| Slime | Dôme gélatineux continu, base aplatie, petits yeux et reflets doux |
| Soldat | Treillis sauge, poches, sac, casque à visière, oreillette et fusil détaillé |

## Animation

Repos : respiration légère, regard mobile, tissus, queue ou ailes selon l’espèce.
Marche : phase calculée depuis la distance parcourue pour les créatures de survie,
avec amplitude amortie à l’arrêt. Rythmes lourds pour les géants et rapides pour
les petites créatures. Les pattes de l’araignée alternent avec un léger lever.
Le gel suspend les horloges de ces animations.

Les frappes réelles déclenchent une impulsion visuelle et sa récupération, avec
une légère avancée du corps ; les coups reçus provoquent un recul visuel.
Le soldat conserve le recul du fusil lié aux tirs. Visages, casques, bandages et
accessoires utilisent les pivots de leurs membres pour rester solidaires.

## Intégration et vérification

La galerie Assaut expose les nouveaux modèles directement. Le présentoir action
alterne deux secondes de marche et une frappe avec récupération ; celui du soldat
montre ses tirs. Les poses de référence restent figées. Les légendes sont EN/FR.

Les hauteurs de référence historiques sont conservées, ainsi que les identifiants,
PV, dégâts, butin et délais de frappe. La teinte de niveau est atténuée pour laisser
les matériaux lisibles ; les labels gardent le niveau. Les nouvelles armes visibles
du nain et du gobelin sont des détails de modèle, pas de nouveaux objets de butin.

Géométrie construite une seule fois : plafond de 48 boîtes par modèle ; le slime utilise un dôme triangulé avec étirement et élargissement compensés. Rendu par
lots de 32 corps. Compilation autorisée : `compileDebugKotlin` uniquement.
À contrôler sur appareil : chaque pose dans Assaut, silhouettes des boss, lisibilité
des yeux, articulation des accessoires, frappes en survie, gel et fluidité en groupe.
Le rendu visuel et les performances sur appareil restent à valider par le joueur.
