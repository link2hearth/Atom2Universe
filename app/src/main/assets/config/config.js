/**
 * Configuration centrale du jeu Atom → Univers.
 * Toutes les valeurs ajustables (équilibrage, affichage, grands nombres, etc.)
 * sont rassemblées ici pour faciliter les modifications futures.
 */
const SHOP_MAX_PURCHASE_DEFAULT = 1000;
const COLLECTION_MULTIPLIER_LABEL_KEY = 'scripts.config.elementBonuses.collectionMultiplier';

/**
 * Paramètres d'affichage du Démineur.
 * `targetCellSize` indique la taille idéale (en pixels) d'une case lorsque c'est possible.
 * `minCellSize` et `maxCellSize` encadrent la taille acceptable d'une case afin de conserver une
 * lisibilité correcte, tandis que `maxRows` et `maxCols` bornent le nombre de lignes/colonnes
 * générées automatiquement.
 */
const MINESWEEPER_BOARD_SETTINGS = Object.freeze({
  targetCellSize: 48,
  minCellSize: 36,
  maxCellSize: 72,
  maxRows: 40,
  maxCols: 60
});

/**
 * Configuration du module MIDI.
 * `MIDI_KEYBOARD_LAYOUTS` répertorie les claviers disponibles ainsi que leurs bornes MIDI.
 */
const MIDI_KEYBOARD_LAYOUTS = [
  {
    id: '88',
    keyCount: 88,
    lowestMidiNote: 21,
    highestMidiNote: 108
  },
  {
    id: '76',
    keyCount: 76,
    lowestMidiNote: 28,
    highestMidiNote: 103
  },
  {
    id: '61',
    keyCount: 61,
    lowestMidiNote: 36,
    highestMidiNote: 96
  }
];

/** Identifiant du clavier sélectionné par défaut sur la page MIDI. */
const MIDI_DEFAULT_KEYBOARD_LAYOUT_ID = '88';

/** Nombre de demi-tons affichés dans la fenêtre d’exercice (2 octaves). */
const MIDI_KEYBOARD_WINDOW_SIZE = 24;

/**
 * Délai (en secondes) entre l’apparition visuelle d’une note et sa lecture réelle.
 * Permet d’afficher les barres de prévisualisation qui descendent vers le clavier.
 */
const MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS = 2;

/**
 * Palette cyclique appliquée aux barres de notes de prévisualisation.
 * Chaque entrée définit un dégradé (start → end) et les teintes de halo associées.
 */
const MIDI_PREVIEW_COLOR_PALETTE = Object.freeze([
  Object.freeze({
    start: 'rgba(138, 97, 255, 0.95)',
    end: 'rgba(255, 80, 112, 0.96)',
    glow: 'rgba(128, 88, 240, 0.45)',
    landedGlow: 'rgba(255, 96, 148, 0.58)'
  }),
  Object.freeze({
    start: 'rgba(70, 190, 255, 0.95)',
    end: 'rgba(0, 235, 190, 0.96)',
    glow: 'rgba(40, 170, 230, 0.42)',
    landedGlow: 'rgba(0, 235, 190, 0.55)'
  }),
  Object.freeze({
    start: 'rgba(255, 168, 76, 0.95)',
    end: 'rgba(255, 94, 150, 0.96)',
    glow: 'rgba(255, 150, 90, 0.45)',
    landedGlow: 'rgba(255, 110, 160, 0.58)'
  }),
  Object.freeze({
    start: 'rgba(135, 220, 130, 0.95)',
    end: 'rgba(68, 150, 255, 0.96)',
    glow: 'rgba(80, 200, 160, 0.42)',
    landedGlow: 'rgba(72, 150, 255, 0.56)'
  }),
  Object.freeze({
    start: 'rgba(255, 120, 90, 0.95)',
    end: 'rgba(255, 210, 90, 0.96)',
    glow: 'rgba(255, 145, 85, 0.42)',
    landedGlow: 'rgba(255, 210, 110, 0.55)'
  }),
  Object.freeze({
    start: 'rgba(210, 120, 255, 0.95)',
    end: 'rgba(120, 80, 255, 0.96)',
    glow: 'rgba(190, 110, 255, 0.45)',
    landedGlow: 'rgba(140, 90, 255, 0.58)'
  })
]);

if (typeof globalThis !== 'undefined') {
  globalThis.MINESWEEPER_BOARD_SETTINGS = MINESWEEPER_BOARD_SETTINGS;
  globalThis.MIDI_KEYBOARD_LAYOUTS = MIDI_KEYBOARD_LAYOUTS;
  globalThis.MIDI_DEFAULT_KEYBOARD_LAYOUT_ID = MIDI_DEFAULT_KEYBOARD_LAYOUT_ID;
  globalThis.MIDI_KEYBOARD_WINDOW_SIZE = MIDI_KEYBOARD_WINDOW_SIZE;
  globalThis.MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS = MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS;
  globalThis.MIDI_PREVIEW_COLOR_PALETTE = MIDI_PREVIEW_COLOR_PALETTE;
}

function translateOrDefault(key, fallback, params) {
  if (typeof key !== 'string' || !key.trim()) {
    return fallback;
  }

  const normalizedKey = key.trim();
  const api = globalThis.i18n;
  const translator = api && typeof api.t === 'function'
    ? api.t
    : typeof globalThis.t === 'function'
      ? globalThis.t
      : null;

  if (translator) {
    try {
      const translated = translator(normalizedKey, params);
      if (typeof translated === 'string') {
        const trimmed = translated.trim();
        if (!trimmed) {
          return fallback;
        }
        const stripped = trimmed.replace(/^!+/, '').replace(/!+$/, '');
        if (trimmed !== normalizedKey && stripped !== normalizedKey) {
          return translated;
        }
      } else if (translated != null) {
        return translated;
      }
    } catch (error) {
      console.warn('Unable to translate key', normalizedKey, error);
      return fallback;
    }
  }

  if (typeof fallback === 'string') {
    return fallback;
  }

  if (typeof key === 'string') {
    const stripped = key.trim();
    if (stripped) {
      return stripped;
    }
  }

  return fallback;
}

function getBuildingLevel(context, id) {
  if (!context || typeof context !== 'object') {
    return 0;
  }
  const value = Number(context[id] ?? 0);
  return Number.isFinite(value) && value > 0 ? value : 0;
}

const SHOP_BUILDING_IDS = [
  'freeElectrons',
  'physicsLab',
  'nuclearReactor',
  'particleAccelerator',
  'supercomputer',
  'interstellarProbe',
  'spaceStation',
  'starForge',
  'artificialGalaxy',
  'multiverseSimulator',
  'realityWeaver',
  'cosmicArchitect',
  'parallelUniverse',
  'omniverseLibrary',
  'quantumOverseer'
];

function createShopBuildingDefinitions() {
  const withDefaults = def => ({ maxLevel: SHOP_MAX_PURCHASE_DEFAULT, ...def });
  return [
    {
      id: 'freeElectrons',
      name: 'Électrons libres',
      description: 'Canalisez des électrons pour amplifier chaque clic quantique.',
      effectSummary:
        'Production manuelle : +1 APC par niveau.',
      category: 'manual',
      baseCost: 15,
      costScale: 1.15,
      effect: (level = 0) => {
        const clickAdd = level > 0 ? level : 0;
        return { clickAdd };
      }
    },
    {
      id: 'physicsLab',
      name: 'Laboratoire de Physique',
      description: 'Des équipes de chercheurs boostent votre production atomique.',
      effectSummary:
        'Production passive : +1 APS par niveau.',
      category: 'auto',
      baseCost: 100,
      costScale: 1.15,
      effect: (level = 0) => {
        const autoAdd = level > 0 ? level : 0;
        return { autoAdd };
      }
    },
    {
      id: 'nuclearReactor',
      name: 'Réacteur nucléaire',
      description: 'Des réacteurs contrôlés libèrent une énergie colossale.',
      effectSummary:
        'Production passive : +10 APS par niveau.',
      category: 'auto',
      baseCost: 1000,
      costScale: 1.15,
      effect: (level = 0) => {
        const baseAmount = 10 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'particleAccelerator',
      name: 'Accélérateur de particules',
      description: 'Boostez vos particules pour intensifier la production passive.',
      effectSummary:
        'Production passive : +50 APS par niveau.',
      category: 'hybrid',
      baseCost: 12_000,
      costScale: 1.15,
      effect: (level = 0) => {
        const baseAmount = 50 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'supercomputer',
      name: 'Supercalculateurs',
      description: 'Des centres de calcul quantique optimisent vos gains.',
      effectSummary:
        'Production passive : +500 APS par niveau.',
      category: 'auto',
      baseCost: 200_000,
      costScale: 1.15,
      effect: (level = 0) => {
        const baseAmount = 500 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'interstellarProbe',
      name: 'Sonde interstellaire',
      description: 'Explorez la galaxie pour récolter toujours plus.',
      effectSummary:
        'Production passive : +5 000 APS par niveau. Bonus conditionnel : avec 50 Électrons libres ou plus, chaque bâtiment possédé ajoute +100 APC.',
      category: 'hybrid',
      baseCost: 5e6,
      costScale: 1.2,
      effect: (level = 0, context = null) => {
        const baseAmount = 5000 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        let clickAdd = 0;
        if (level > 0) {
          const freeElectronLevel = getBuildingLevel(context, 'freeElectrons');
          if (freeElectronLevel >= 50) {
            const ownedBuildingCount = SHOP_BUILDING_IDS.reduce(
              (sum, buildingId) => sum + getBuildingLevel(context, buildingId),
              0
            );
            if (ownedBuildingCount > 0) {
              const bonusPerUnit = 100;
              clickAdd = ownedBuildingCount * bonusPerUnit;
            }
          }
        }
        const result = { autoAdd };
        if (clickAdd > 0) {
          result.clickAdd = clickAdd;
        }
        return result;
      }
    },
    {
      id: 'spaceStation',
      name: 'Station spatiale',
      description: 'Des bases orbitales coordonnent votre expansion.',
      effectSummary:
        'Production passive : +50 000 APS par niveau.',
      category: 'hybrid',
      baseCost: 1e8,
      costScale: 1.2,
      effect: (level = 0) => {
        const baseAmount = 50_000 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'starForge',
      name: 'Forgeron d’étoiles',
      description: 'Façonnez des étoiles et dopez votre production passive.',
      effectSummary:
        'Production passive : +500 000 APS par niveau.',
      category: 'hybrid',
      baseCost: 5e10,
      costScale: 1.2,
      effect: (level = 0) => {
        const baseAmount = 500_000 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'artificialGalaxy',
      name: 'Galaxie artificielle',
      description: 'Ingénierie galactique pour une expansion sans fin.',
      effectSummary:
        'Production passive : +5 000 000 APS par niveau.',
      category: 'auto',
      baseCost: 1e13,
      costScale: 1.2,
      effect: (level = 0) => {
        const baseAmount = 5e6 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'multiverseSimulator',
      name: 'Simulateur de Multivers',
      description: 'Simulez l’infini pour optimiser chaque seconde.',
      effectSummary:
        'Production passive : +500 000 000 APS par niveau.',
      category: 'auto',
      baseCost: 1e16,
      costScale: 1.2,
      effect: (level = 0) => {
        const baseAmount = 5e8 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'realityWeaver',
      name: 'Tisseur de Réalité',
      description: 'Tissez les lois physiques à votre avantage.',
      effectSummary:
        'Production passive : +10 000 000 000 APS par niveau.',
      category: 'hybrid',
      baseCost: 1e20,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e10 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'cosmicArchitect',
      name: 'Architecte Cosmique',
      description: 'Réécrivez les plans du cosmos pour libérer une énergie infinie.',
      effectSummary:
        'Production passive : +1 000 000 000 000 APS par niveau.',
      category: 'hybrid',
      baseCost: 1e25,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e12 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'parallelUniverse',
      name: 'Univers parallèle',
      description: 'Expérimentez des réalités alternatives à haut rendement.',
      effectSummary:
        'Production passive : +100 000 000 000 000 APS par niveau.',
      category: 'auto',
      baseCost: 1e30,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e14 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'omniverseLibrary',
      name: 'Bibliothèque de l’Omnivers',
      description: 'Compilez le savoir infini pour booster toute production.',
      effectSummary:
        'Production passive : +10 000 000 000 000 000 APS par niveau.',
      category: 'hybrid',
      baseCost: 1e36,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e16 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    },
    {
      id: 'quantumOverseer',
      name: 'Grand Ordonnateur Quantique',
      description: 'Ordonnez le multivers et atteignez la singularité.',
      effectSummary:
        'Production passive : +1 000 000 000 000 000 000 APS par niveau.',
      category: 'hybrid',
      baseCost: 1e42,
      costScale: 1.25,
      effect: (level = 0) => {
        const baseAmount = 1e18 * level;
        const autoAdd = level > 0 ? baseAmount : 0;
        return { autoAdd };
      }
    }
  ].map(withDefaults);
}

function createAtomScalePreset({ id, targetText, amount, name, flavor }) {
  const baseKey = typeof id === 'string' && id.trim()
    ? `scripts.appData.atomScale.trophies.${id}`
    : '';

  return {
    id,
    targetText,
    amount,
    name,
    flavor,
    i18nBaseKey: baseKey
  };
}

const ATOM_SCALE_TROPHY_PRESETS = [
  createAtomScalePreset({
    id: 'scaleHumanCell',
    name: 'Échelle : cellule humaine',
    targetText: '10^14',
    flavor: 'l’équivalent d’une cellule humaine « moyenne »',
    amount: { type: 'layer0', mantissa: 1, exponent: 14 }
  }),
  createAtomScalePreset({
    id: 'scaleSandGrain',
    name: 'Échelle : grain de sable',
    targetText: '10^19',
    flavor: 'la masse d’un grain de sable (~1 mm)',
    amount: { type: 'layer0', mantissa: 1, exponent: 19 }
  }),
  createAtomScalePreset({
    id: 'scaleAnt',
    name: 'Échelle : fourmi',
    targetText: '10^20',
    flavor: 'comparable à une fourmi (~5 mg)',
    amount: { type: 'layer0', mantissa: 1, exponent: 20 }
  }),
  createAtomScalePreset({
    id: 'scaleWaterDrop',
    name: 'Échelle : goutte d’eau',
    targetText: '5 × 10^21',
    flavor: 'la quantité d’atomes contenue dans une goutte d’eau de 0,05 mL',
    amount: { type: 'layer0', mantissa: 5, exponent: 21 }
  }),
  createAtomScalePreset({
    id: 'scalePaperclip',
    name: 'Échelle : trombone',
    targetText: '10^22',
    flavor: 'l’équivalent d’un trombone en fer (~1 g)',
    amount: { type: 'layer0', mantissa: 1, exponent: 22 }
  }),
  createAtomScalePreset({
    id: 'scaleCoin',
    name: 'Échelle : pièce',
    targetText: '10^23',
    flavor: 'la masse atomique d’une pièce de monnaie (~7,5 g)',
    amount: { type: 'layer0', mantissa: 1, exponent: 23 }
  }),
  createAtomScalePreset({
    id: 'scaleApple',
    name: 'Échelle : pomme',
    targetText: '10^25',
    flavor: 'la masse atomique d’une pomme (~100 g)',
    amount: { type: 'layer0', mantissa: 1, exponent: 25 }
  }),
  createAtomScalePreset({
    id: 'scaleSmartphone',
    name: 'Échelle : smartphone',
    targetText: '3 × 10^25',
    flavor: 'autant qu’un smartphone moderne (~180 g)',
    amount: { type: 'layer0', mantissa: 3, exponent: 25 }
  }),
  createAtomScalePreset({
    id: 'scaleWaterLitre',
    name: 'Échelle : litre d’eau',
    targetText: '10^26',
    flavor: 'l’équivalent d’un litre d’eau',
    amount: { type: 'layer0', mantissa: 1, exponent: 26 }
  }),
  createAtomScalePreset({
    id: 'scaleHuman',
    name: 'Échelle : être humain',
    targetText: '7 × 10^27',
    flavor: 'comparable à un humain de 70 kg',
    amount: { type: 'layer0', mantissa: 7, exponent: 27 }
  }),
  createAtomScalePreset({
    id: 'scalePiano',
    name: 'Échelle : piano',
    targetText: '10^29',
    flavor: 'équivaut à un piano (~450 kg)',
    amount: { type: 'layer0', mantissa: 1, exponent: 29 }
  }),
  createAtomScalePreset({
    id: 'scaleCar',
    name: 'Échelle : voiture compacte',
    targetText: '10^30',
    flavor: 'autant qu’une voiture compacte (~1,3 t)',
    amount: { type: 'layer0', mantissa: 1, exponent: 30 }
  }),
  createAtomScalePreset({
    id: 'scaleElephant',
    name: 'Échelle : éléphant',
    targetText: '3 × 10^31',
    flavor: 'équivaut à un éléphant (~6 t)',
    amount: { type: 'layer0', mantissa: 3, exponent: 31 }
  }),
  createAtomScalePreset({
    id: 'scaleBoeing747',
    name: 'Échelle : Boeing 747',
    targetText: '10^33',
    flavor: 'autant qu’un Boeing 747',
    amount: { type: 'layer0', mantissa: 1, exponent: 33 }
  }),
  createAtomScalePreset({
    id: 'scalePyramid',
    name: 'Échelle : pyramide de Khéops',
    targetText: '2 × 10^35',
    flavor: 'la masse d’atomes de la grande pyramide de Khéops',
    amount: { type: 'layer0', mantissa: 2, exponent: 35 }
  }),
  createAtomScalePreset({
    id: 'scaleAtmosphere',
    name: 'Échelle : atmosphère terrestre',
    targetText: '2 × 10^44',
    flavor: 'équivaut à l’atmosphère terrestre complète',
    amount: { type: 'layer0', mantissa: 2, exponent: 44 }
  }),
  createAtomScalePreset({
    id: 'scaleOceans',
    name: 'Échelle : océans terrestres',
    targetText: '10^47',
    flavor: 'autant que tous les océans de la Terre',
    amount: { type: 'layer0', mantissa: 1, exponent: 47 }
  }),
  createAtomScalePreset({
    id: 'scaleEarth',
    name: 'Échelle : Terre',
    targetText: '10^50',
    flavor: 'égale la masse atomique de la planète Terre',
    amount: { type: 'layer0', mantissa: 1, exponent: 50 }
  }),
  createAtomScalePreset({
    id: 'scaleSun',
    name: 'Échelle : Soleil',
    targetText: '10^57',
    flavor: 'équivaut au Soleil',
    amount: { type: 'layer0', mantissa: 1, exponent: 57 }
  }),
  createAtomScalePreset({
    id: 'scaleMilkyWay',
    name: 'Échelle : Voie lactée',
    targetText: '10^69',
    flavor: 'comparable à la Voie lactée entière',
    amount: { type: 'layer0', mantissa: 1, exponent: 69 }
  }),
  createAtomScalePreset({
    id: 'scaleLocalGroup',
    name: 'Échelle : Groupe local',
    targetText: '10^71',
    flavor: 'autant que le Groupe local de galaxies',
    amount: { type: 'layer0', mantissa: 1, exponent: 71 }
  }),
  createAtomScalePreset({
    id: 'scaleVirgoCluster',
    name: 'Échelle : superamas de la Vierge',
    targetText: '10^74',
    flavor: 'équivaut au superamas de la Vierge',
    amount: { type: 'layer0', mantissa: 1, exponent: 74 }
  }),
  createAtomScalePreset({
    id: 'scaleObservableUniverse',
    name: 'Échelle : univers observable',
    targetText: '10^80',
    flavor: 'atteignez le total estimé d’atomes de l’univers observable',
    amount: { type: 'layer0', mantissa: 1, exponent: 80 }
  })
];

function resolveAtomScalePreset(entry) {
  if (!entry || typeof entry !== 'object') {
    return null;
  }

  const { id, targetText, amount, name, flavor, i18nBaseKey } = entry;
  const fallbackName = typeof name === 'string' ? name : '';
  const fallbackFlavor = typeof flavor === 'string' ? flavor : '';
  const resolvedName = i18nBaseKey
    ? translateOrDefault(`${i18nBaseKey}.name`, fallbackName)
    : fallbackName;
  const resolvedFlavor = i18nBaseKey
    ? translateOrDefault(`${i18nBaseKey}.flavor`, fallbackFlavor)
    : fallbackFlavor;

  return {
    id,
    targetText,
    amount,
    name: resolvedName,
    flavor: resolvedFlavor
  };
}

const RESOLVED_ATOM_SCALE_TROPHY_PRESETS = ATOM_SCALE_TROPHY_PRESETS
  .map(resolveAtomScalePreset)
  .filter(Boolean);

globalThis.ATOM_SCALE_TROPHY_DATA = RESOLVED_ATOM_SCALE_TROPHY_PRESETS;

function getCurrentLocale() {
  if (globalThis.i18n && typeof globalThis.i18n.getCurrentLocale === 'function') {
    return globalThis.i18n.getCurrentLocale();
  }
  return 'fr-FR';
}

function formatLocalizedNumber(value, options) {
  if (globalThis.i18n && typeof globalThis.i18n.formatNumber === 'function') {
    return globalThis.i18n.formatNumber(value, options);
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '';
  }
  return numeric.toLocaleString(getCurrentLocale(), options);
}

function formatAtomScaleBonus(value) {
  if (!Number.isFinite(value)) {
    return '0';
  }
  const roundedInteger = Math.round(value);
  if (Math.abs(value - roundedInteger) <= 1e-9) {
    return formatLocalizedNumber(roundedInteger);
  }
  return formatLocalizedNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function createAtomScaleTrophies() {
  const bonusPerTrophy = 2;
  return RESOLVED_ATOM_SCALE_TROPHY_PRESETS.map((entry, index) => {
    const displayBonus = formatAtomScaleBonus(bonusPerTrophy);
    const displayTotal = formatAtomScaleBonus(1 + bonusPerTrophy);
    const descriptionFallback = `Atteignez ${entry.targetText} atomes cumulés, ${entry.flavor}.`;
    const rewardFallback = `Ajoute +${displayBonus} au Boost global sur la production manuelle et automatique.`;

    return {
      id: entry.id,
      name: entry.name,
      targetText: entry.targetText,
      flavor: entry.flavor,
      description: translateOrDefault(
        'scripts.appData.atomScale.trophies.description',
        descriptionFallback,
        { target: entry.targetText, flavor: entry.flavor }
      ),
      condition: {
        type: 'lifetimeAtoms',
        amount: entry.amount
      },
      reward: {
        trophyMultiplierAdd: bonusPerTrophy,
        description: translateOrDefault(
          'scripts.appData.atomScale.trophies.reward',
          rewardFallback,
          { bonus: displayBonus, total: displayTotal }
        )
      },
      order: index
    };
  });
}

const GAME_CONFIG = {
  /**
   * Paramètres du système de langues.
   * - languages : liste des codes langues disponibles et leur locale associée.
   * - defaultLanguage : langue chargée par défaut lors du démarrage du jeu.
   * - path : répertoire contenant les fichiers JSON de traduction.
   * - fetchOptions : options passées à fetch() (cache désactivé pour éviter les 304 persistants).
   */
  i18n: {
    languages: [
      { code: 'fr', locale: 'fr-FR' },
      { code: 'en', locale: 'en-US' }
    ],
    defaultLanguage: 'fr',
    path: './scripts/i18n',
    fetchOptions: {
      cache: 'no-store'
    }
  },

  /**
   * Textes affichés dans l'interface utilisateur.
   * Centralisés ici pour faciliter leur édition sans toucher au HTML.
   */
  uiText: {
    options: {
      welcomeCard: {
        title: 'Bienvenue',
        introParagraphs: [
          "Bienvenue dans Atom to Univers, commencez en cliquant pour gagner des Atomes (APC Atoms Par Clic), achetez des améliorations dans le magasin et automatisez la récolte (APS Atoms Par Seconde). Débloquez des minis jeux au fur et à mesure de votre progression. L’objectif est d’accumuler le plus possible d’Atomes pour fabriquer un Univers."
        ],
        unlockedDetails: [
          {
            label: 'Mini Jeu Particules :',
            description:
              'Collisionnez les particules élémentaires afin de gagner des tickets de tirage pour le Gacha, et des crédits pour le mini jeu Mach3.'
          },
          {
            label: 'Gacha :',
            description:
              'Grâce à vos tickets gagnez des Atomes du tableau périodique des éléments, leurs collections vous octroieront des bonus de APS et APC notamment.'
          },
          {
            label: 'Mini jeu Mach3 :',
            description:
              "C’est un jeu de Match 3, alignez le Cuivre, le Bronze, l’Argent, l’Or et le Carbone (Diamant) le plus vite possible avant que le timer atteigne 0. Plus votre score sera grand, plus grand sera votre bonus de production d’APS."
          }
        ]
      }
    }
  },

  /**
   * Thèmes visuels proposés dans l’interface.
   * - default : identifiant appliqué par défaut lors d’une nouvelle partie.
   * - available : liste des thèmes avec leurs classes CSS et la clé i18n de leur libellé.
   */
  themes: {
    default: 'dark',
    available: Object.freeze([
      Object.freeze({
        id: 'dark',
        name: 'Thème sombre',
        labelKey: 'scripts.config.themes.dark',
        classes: Object.freeze(['theme-dark'])
      }),
      Object.freeze({
        id: 'light',
        name: 'Thème clair',
        labelKey: 'scripts.config.themes.light',
        classes: Object.freeze(['theme-light'])
      }),
      Object.freeze({
        id: 'neon',
        name: 'Thème néon',
        labelKey: 'scripts.config.themes.neon',
        classes: Object.freeze(['theme-neon'])
      }),
      Object.freeze({
        id: 'aurora',
        name: 'Thème Aurore',
        labelKey: 'scripts.config.themes.aurora',
        classes: Object.freeze(['theme-aurora', 'theme-neon'])
      })
    ])
  },

  /**
   * Options d’accessibilité de l’interface utilisateur.
   * - scale.default : identifiant de l’option utilisée par défaut lors d’une nouvelle partie.
   * - scale.options : facteurs d’agrandissement disponibles pour l’interface.
   */
  ui: {
    scale: {
      default: 'normal',
      options: [
        { id: 'small', factor: 0.75 },
        { id: 'normal', factor: 1 },
        { id: 'large', factor: 1.5 }
      ]
    }
  },

  /**
   * Paramètres du système de grands nombres et des layers.
   * - layer1Threshold : passage automatique au layer 1 lorsque l'exposant dépasse ce seuil.
   * - layer1Downshift : retour au layer 0 quand la valeur redescend sous ce niveau.
   * - logDifferenceLimit : limite de différence logarithmique utilisée pour comparer deux valeurs.
   * - epsilon : tolérance minimale avant de considérer une valeur comme nulle.
   */
  numbers: {
    layer1Threshold: 1e6,
    layer1Downshift: 5,
    logDifferenceLimit: 15,
    epsilon: 1e-12
  },

  /**
   * Valeurs de base de la progression.
   * - basePerClick : quantité d'atomes gagnés par clic avant bonus (Layer 0 par défaut).
   * - basePerSecond : production passive initiale (0 si aucune production automatique).
   * - offlineCapSeconds : durée maximale (en secondes) prise en compte pour les gains hors-ligne.
   * - offlineTickets : configuration des gains de tickets hors-ligne (intervalle et plafond).
   * - defaultTheme : thème visuel utilisé lors d'une nouvelle partie ou après réinitialisation.
   * - crit : paramètres initiaux des coups critiques (chance, multiplicateur et plafond).
   */
  progression: {
    basePerClick: { type: 'number', value: 1 },
    basePerSecond: { type: 'number', value: 0 },
    offlineCapSeconds: 60 * 60 * 12,
    offlineTickets: {
      secondsPerTicket: 60 * 60,
      capSeconds: 60 * 60 * 50
    },
    defaultTheme: 'dark',
    crit: {
      baseChance: 0.05,
      baseMultiplier: 2,
      maxMultiplier: 100
    }
  },

  /**
   * Paramètres liés aux interactions et retours visuels.
   * - windowMs : fenêtre temporelle (ms) utilisée pour lisser l'intensité du bouton.
   * - maxClicksPerSecond : nombre de clics par seconde considéré comme 100% de puissance.
   * - starCount : nombre d'étoiles utilisées dans l'arrière-plan animé.
   */
  presentation: {
    clicks: {
      windowMs: 1000,
      maxClicksPerSecond: 10
    },
    starfield: {
      starCount: 60
    }
  },

  /**
   * Paramètres du système de frénésie.
   * - displayDurationMs : durée d'affichage des icônes (en millisecondes).
   * - effectDurationMs : durée du bonus une fois collecté.
   * - multiplier : multiplicateur appliqué à la production visée.
   * - spawnChancePerSecond : probabilités d'apparition par seconde (APC / APS).
   */
  frenzies: {
    displayDurationMs: 8000,
    effectDurationMs: 30000,
    multiplier: 2,
    baseMaxStacks: 1,
    spawnChancePerSecond: {
      perClick: 0.01,
      perSecond: 0.01
    }
  },

  /**
   * Réglages du mini-jeu Métaux.
   * - rows / cols : dimensions de la grille.
   * - clearDelayMs : délai avant la disparition visuelle d'un alignement.
   * - refillDelayMs : délai avant la réapparition de nouvelles cases.
   * - popEffect : animation lors de la disparition, paramétrable.
   * - maxShuffleAttempts : nombre maximal de tentatives de redistribution.
   * - tileTypes : définition des métaux disponibles (identifiant, libellé, couleur).
   * - timer : configuration du chrono (valeur initiale, gains, pénalités, cadence de mise à jour).
   */
  metaux: {
    rows: 10,
    cols: 16,
    clearDelayMs: 220,
    refillDelayMs: 120,
    popEffect: {
      durationMs: 220,
      scale: 1.22,
      glowOpacity: 0.8
    },
    maxShuffleAttempts: 120,
    tileTypes: [
      {
        id: 'bronze',
        label: 'Cu',
        color: '#F8A436',
        image: 'Assets/Image/Bronze.png'
      },
      {
        id: 'argent',
        label: 'Ag',
        color: '#1C8213',
        image: 'Assets/Image/Argent.png'
      },
      {
        id: 'or',
        label: 'Au',
        color: '#E6C838',
        image: 'Assets/Image/Or.png'
      },
      {
        id: 'platine',
        label: 'Pt',
        color: '#45A9E2',
        image: 'Assets/Image/Cuivre.png'
      },
      {
        id: 'diamant',
        label: 'C',
        color: '#E9F6FD',
        image: 'Assets/Image/Diamant.png'
      }
    ],
    timer: {
      initialSeconds: 6,
      maxSeconds: 6,
      matchRewardSeconds: 2,
      penaltyWindowSeconds: 20,
      penaltyAmountSeconds: 1,
      minMaxSeconds: 1,
      tickIntervalMs: 100
    }
  },

  /**
   * Configuration des mini-jeux d'arcade.
   * Chaque sous-objet contient l'équilibrage spécifique au jeu concerné
   * (vitesses, probabilités, textes, etc.) afin de centraliser les réglages.
   */
  arcade: {
    blackjack: {
      decks: 8,
      dealerHitSoft17: false,
      betOptions: [10, 20, 50, 100]
    },
    // Paramètres du mini-jeu Échecs.
    echecs: {
      ai: {
        /**
         * Profondeur de recherche maximale (en demi-coups) utilisée par l'IA noire.
         * Une valeur plus élevée augmente la force de jeu mais rallonge le temps de calcul.
         */
        searchDepth: 3,
        /**
         * Budget de temps autorisé pour un coup de l'IA (en millisecondes).
         * Si 0, aucun plafond n'est appliqué et seule la profondeur limite le calcul.
         */
        timeLimitMs: 1200,
        /**
         * Délai (ms) ajouté avant le lancement du calcul afin de laisser l'interface se mettre à jour.
         */
        moveDelayMs: 150,
        /**
         * Nombre maximum d'entrées conservées dans la table de transposition.
         */
        transpositionSize: 4000,
        /**
         * Paramètres de créativité : permettent à l'IA de varier ses choix lorsque plusieurs
         * coups de valeur proche sont disponibles afin d'éviter un style trop répétitif.
         */
        creativity: {
          enabled: true,
          thresholdCentipawns: 80,
          variabilityCentipawns: 30,
          candidateCount: 3,
          excitementBonus: 0.35
        },
        /**
         * Ajustements dynamiques de réflexion : l'IA peut prolonger sa recherche lorsqu'une
         * position comporte beaucoup d'échanges ou des échecs possibles.
         */
        searchExtensions: {
          captureDepthBonus: 1,
          checkDepthBonus: 1,
          tacticalMoveThreshold: 2,
          maxDepth: 5,
          timeBonusMs: 450,
          branchingThreshold: 26
        }
      },
      /**
       * Paramétrage des récompenses et de la difficulté.
       * - difficulty.defaultMode : difficulté sélectionnée par défaut.
       * - difficulty.modes : liste des modes proposés avec leur étiquette i18n,
       *   les réglages IA associés et la récompense accordée en cas de victoire.
       * - match.moveLimit : nombre maximal de tours complets avant déclaration d'une nulle technique.
       */
      difficulty: {
        defaultMode: 'standard',
        modes: [
          {
            id: 'training',
            labelKey: 'scripts.arcade.chess.difficulty.training',
            descriptionKey: 'scripts.arcade.chess.difficulty.trainingDescription',
            ai: { depth: 2, timeLimitMs: 600 },
            reward: {
              offlineSeconds: 600,
              offlineMultiplier: 1
            }
          },
          {
            id: 'standard',
            labelKey: 'scripts.arcade.chess.difficulty.standard',
            descriptionKey: 'scripts.arcade.chess.difficulty.standardDescription',
            ai: { depth: 3, timeLimitMs: 1200 },
            reward: {
              offlineSeconds: 1200,
              offlineMultiplier: 1.5
            }
          },
          {
            id: 'expert',
            labelKey: 'scripts.arcade.chess.difficulty.expert',
            descriptionKey: 'scripts.arcade.chess.difficulty.expertDescription',
            ai: { depth: 4, timeLimitMs: 1800 },
            reward: {
              offlineSeconds: 1800,
              offlineMultiplier: 2
            }
          }
        ]
      },
      match: {
        moveLimit: 80
      }
    },
    // Paramètres du mini-jeu Balance (plateforme d'équilibre).
    balance: {
      board: {
        widthPx: 640,
        surfaceHeightPx: 12,
        pivotHeightPx: 30,
        tolerance: 0.026,
        maxTiltDegrees: 18,
        maxOffsetForTilt: 0.32,
        testDurationMs: 900,
        settleDurationMs: 600,
        physics: {
          gravity: 9.81, // Accélération gravitationnelle (m/s²) utilisée pour la simulation.
          lengthMeters: 2.4, // Longueur approximative de la planche (m) servant de base aux leviers.
          boardMassKg: 2, // Masse estimée de la planche pour le calcul de l'inertie (kg).
          cubeMassPerWeight: 1.6, // Masse équivalente (kg) apportée par une unité de poids de bloc.
          pivotFriction: 14, // Coefficient de frottement du pivot (N·m·s) pour amortir la rotation.
          stiffness: 960, // Couple de rappel appliqué par radian (N·m/rad) pour stabiliser l'équilibre.
          simulationStepMs: 16, // Intervalle entre deux itérations de la simulation (ms).
          simulationDurationMs: 1800, // Durée totale simulée lors d'un test (ms).
          maxAngularVelocity: 5, // Vitesse angulaire maximale autorisée (rad/s) pour éviter les à-coups.
          settleThresholdVelocity: 0.0025, // Seuil de vitesse sous lequel la planche est considérée comme stable (rad/s).
          settleThresholdTorque: 0.05 // Seuil de couple résiduel pour interrompre la simulation (N·m).
        }
      },
      /**
       * Réglages des modes de difficulté.
       * - defaultMode : mode sélectionné par défaut à l'ouverture du mini-jeu.
       * - modes : liste ordonnée des modes disponibles avec leur clé de traduction et
       *   un multiplicateur appliqué à la tolérance de base. Une valeur plus faible
       *   demande un équilibrage plus précis.
       */
      difficulty: {
        defaultMode: 'easy',
        modes: [
          {
            id: 'easy',
            labelKey: 'scripts.arcade.balance.difficulty.easy',
            descriptionKey: 'scripts.arcade.balance.difficulty.easyDescription',
            toleranceMultiplier: 1
          },
          {
            id: 'hard',
            labelKey: 'scripts.arcade.balance.difficulty.hard',
            descriptionKey: 'scripts.arcade.balance.difficulty.hardDescription',
            toleranceMultiplier: 0.45
          }
        ]
      },
      cubeRules: {
        countPerSet: 5, // Nombre de blocs générés par série.
        widthRatio: 0.18, // Largeur uniforme des blocs par rapport à la planche.
        inventoryWidthPx: { min: 64, max: 112 }, // Largeur minimale et maximale affichée dans l'inventaire.
        weightRange: { min: 1, max: 20 }, // Fourchette des poids aléatoires attribués aux blocs.
        stackOffsetMultiplier: 0.72, // Hauteur relative entre deux blocs empilés.
        stackGroupingThreshold: 0.08, // Distance horizontale maximale (en proportion de la planche) pour regrouper les blocs.
        randomizeWeights: true // Active la génération aléatoire des poids pour chaque série.
      },
      cubeSets: [
        {
          id: 'equilibrium',
          labelKey: 'scripts.arcade.balance.sets.equilibrium',
          cubeCount: 5
        },
        {
          id: 'fractal',
          labelKey: 'scripts.arcade.balance.sets.fractal',
          cubeCount: 5
        },
        {
          id: 'binary',
          labelKey: 'scripts.arcade.balance.sets.binary',
          cubeCount: 5
        }
      ],
      defaultSetId: 'equilibrium',
      rewards: {
        /**
         * Paramètres de la récompense accordée lorsque le joueur équilibre parfaitement la planche.
         * - maxAttempts : nombre maximal de tests autorisés pour décrocher le bonus (2 = une erreur permise).
         * - ticketAmount : quantité de tickets Mach3 remis lors de la réussite (0 pour désactiver la récompense).
         */
        perfectBalance: {
          maxAttempts: 2,
          ticketAmount: 1
        }
      }
    },
    // Paramètres du mini-jeu Math (progression des opérations).
    math: {
      termCountRange: { min: 4, max: 5 },
      baseRange: { min: 1, max: 9 },
      advancedRange: { min: 2, max: 12 },
      expertRange: { min: 2, max: 15 },
      optionsCount: 6,
      maxMistakes: 3, // Nombre d'erreurs autorisées avant le game over.
      roundTimerSeconds: 30, // Temps alloué pour répondre (en secondes).
      thresholds: {
        multiply: 3,
        divide: 7,
        expert: 12
      },
      resultLimits: {
        base: 40,
        advanced: 80,
        expert: 120
      },
      symbolMode: {
        /**
         * Niveau à partir duquel toutes les opérations (+, -, ×, ÷) sont proposées.
         * Les niveaux inférieurs n'affichent que les opérations de base (addition/soustraction).
         */
        fullSymbolUnlockLevel: 5,
        /**
         * Ensemble des symboles proposés avant le palier d'extension.
         */
        earlySymbols: ['+', '-'],
        /**
         * Ensemble complet des symboles proposés une fois le palier atteint.
         */
        fullSymbols: ['+', '-', '*', '/']
      }
    },
    particules: {
      ticketReward: 1,
      /**
       * Modes de jeu disponibles pour Particules.
       * - free : partie d'entraînement sans coût ni récompense.
       * - paid : mode avec récompenses qui prélève un pourcentage du solde actuel d'atomes.
       */
      modes: {
        defaultMode: 'free',
        free: { id: 'free' },
        paid: {
          id: 'paid',
          /** Pourcentage du solde d'atomes utilisé comme coût d'entrée (0,9 = 90%). */
          costRatio: 0.9
        }
      },
      /**
       * Contrôle des effets visuels optionnels pour limiter l'impact sur les performances.
       * Tous les effets sont désactivés par défaut et peuvent être réactivés individuellement au besoin.
       */
      visualEffects: {
        enableScreenPulse: false,
        enableGlow: false,
        enableShockwave: false,
        enableBallGhost: false
      },
      grid: {
        columns: 14,
        rows: 6,
        vertical: {
          columns: 10,
          rows: 10
        }
      },
      maxLives: 3,
      geometry: {
        paddingX: 0.08,
        paddingY: 0.04,
        usableHeight: 0.46,
        innerWidthRatio: 0.92,
        innerHeightRatio: 0.68,
        vertical: {
          paddingX: 0,
          paddingY: 0.02,
          usableHeight: 0.5,
          innerWidthRatio: 1,
          innerHeightRatio: 0.82
        }
      },
      paddle: {
        baseWidthRatio: 0.18,
        minWidthRatio: 0.12,
        heightRatio: 0.025,
        extend: {
          maxWidthRatio: 0.32,
          multiplier: 1.6
        },
        stretchDurationMs: 620,
        bounceDurationMs: 260,
        bounce: {
          intensityBase: 0.28,
          maxHeightRatio: 0.7
        }
      },
      ball: {
        radiusRatio: 0.015,
        baseSpeedRatio: 1.9,
        speedGrowthRatio: 0.15,
        minSpeedPerMs: 0.25,
        speedFactor: 0.0006,
        followOffsetRatio: 1.6,
        /**
         * Intervalle (en degrés) utilisé lors du lancement automatique de la bille.
         * Par défaut, la bille est envoyée quasi à la verticale pour maximiser la montée initiale.
         */
        launchAngleDegrees: {
          min: -100,
          max: -80
        },
        trail: {
          minDistance: 2,
          minDistanceFactor: 0.35,
          captureIntervalMs: 18,
          maxPoints: 12,
          pruneMs: 260
        },
        ghost: {
          intervalMs: 68,
          blurFactor: 1.4,
          removeDelayMs: 360
        }
      },
      graviton: {
        lifetimeMs: 10000,
        spawnChance: {
          base: 0.01,
          perLevel: 0.0025,
          min: 0.005,
          max: 0.1,
          overrides: [
            { level: 8, chance: 0.02 },
            { level: 10, chance: 0.05 },
            { minLevel: 11, chance: 0.1 }
          ]
        },
        detectionMessage: 'Graviton détecté !',
        detectionMessageDurationMs: 2600,
        dissipateMessage: 'Le graviton s’est dissipé…',
        dissipateMessageDurationMs: 2800,
        captureMessage: 'Graviton capturé !',
        captureMessageDurationMs: 3600
      },
      lingerBonus: {
        thresholdMs: 20000,
        intervalMs: 1000,
        chance: 0.1,
        message: 'Multiballe bonus !',
        messageDurationMs: 2400
      },
      combos: {
        requiredColors: ['red', 'green', 'blue'],
        powerUpRewards: ['laser', 'extend', 'multiball'],
        chainThreshold: 3,
        bonusMessagePrefix: 'Bonus: ',
        bonusMessageDurationMs: 2400,
        quarkMessagePrefix: 'Combo quark ! ',
        quarkMessageDurationMs: 3200,
        chainWindowMs: 520,
        shockwave: {
          baseScale: 1.25,
          scalePerChain: 0.08,
          maxScaleChains: 6,
          baseOpacity: 0.78,
          opacityPerChain: 0.03,
          maxOpacity: 0.92,
          removeDelayMs: 640,
          stagePulse: {
            baseIntensity: 1.02,
            intensityPerChain: 0.005,
            maxChains: 5,
            durationMs: 420
          }
        }
      },
      powerUps: {
        ids: {
          extend: 'extend',
          multiball: 'multiball',
          laser: 'laser',
          speed: 'speed',
          floor: 'floor'
        },
        fallSpeedRatio: 0.00042,
        laserSpeedRatio: -0.0026,
        laserIntervalMs: 420,
        labels: {
          extend: 'Barre allongée',
          multiball: 'Multiballe',
          laser: 'Tir laser',
          speed: 'Accélération',
          floor: 'Bouclier inférieur'
        },
        visuals: {
          default: {
            symbol: 'P',
            gradient: ['#ffffff', '#a6d8ff'],
            textColor: '#041022',
            glow: 'rgba(140, 210, 255, 0.45)',
            border: 'rgba(255, 255, 255, 0.5)',
            widthMultiplier: 1.45
          },
          extend: {
            symbol: 'L',
            gradient: ['#66f4ff', '#2c9cff'],
            textColor: '#041222',
            glow: 'rgba(110, 220, 255, 0.55)',
            border: 'rgba(255, 255, 255, 0.65)',
            widthMultiplier: 1.5
          },
          multiball: {
            symbol: 'M',
            gradient: ['#ffe066', '#ff7b6b'],
            textColor: '#241104',
            glow: 'rgba(255, 160, 110, 0.55)',
            border: 'rgba(255, 255, 255, 0.6)',
            widthMultiplier: 1.6
          },
          laser: {
            symbol: 'T',
            gradient: ['#ff96c7', '#ff4d9a'],
            textColor: '#36001a',
            glow: 'rgba(255, 120, 190, 0.55)',
            border: 'rgba(255, 255, 255, 0.55)',
            widthMultiplier: 1.45
          },
          speed: {
            symbol: 'S',
            gradient: ['#9d7bff', '#4f3bff'],
            textColor: '#1a083a',
            glow: 'rgba(160, 140, 255, 0.52)',
            border: 'rgba(255, 255, 255, 0.55)',
            widthMultiplier: 1.45
          },
          floor: {
            symbol: 'F',
            gradient: ['#6ef7a6', '#1ec37a'],
            textColor: '#052615',
            glow: 'rgba(90, 240, 180, 0.55)',
            border: 'rgba(255, 255, 255, 0.55)',
            widthMultiplier: 1.55
          }
        },
        pulseIntensity: {
          multiball: 1.06,
          extend: 1.04,
          floor: 1.05
        },
        effects: {
          extend: { durationMs: 12000 },
          laser: { durationMs: 9000 },
          speed: { durationMs: 8000, speedMultiplier: 1.35 },
          floor: { durationMs: 10000 }
        },
        floorShield: {
          heightRatio: 0.06,
          minHeightPx: 12
        }
      },
      bricks: {
        types: {
          simple: 'simple',
          resistant: 'resistant',
          bonus: 'bonus',
          graviton: 'graviton'
        },
        scoreValues: {
          simple: 120,
          resistant: 200,
          bonus: 160,
          graviton: 420
        },
        bonusDistribution: {
          targetedPatternRatio: 0.32,
          otherPatternRatio: 0.22,
          perLevelIncrement: 0.01,
          minRatio: 0.18,
          maxRatio: 0.42
        },
        patterns: [
          { id: 'organic', weight: 0.25 },
          { id: 'singleGap', weight: 0.1 },
          { id: 'multiGap', weight: 0.1 },
          { id: 'singleBrick', weight: 0.01 },
          { id: 'singleLine', weight: 0.1 },
          { id: 'bottomUniform', weight: 0.1 },
          { id: 'uniformLines', weight: 0.1 },
          { id: 'checkerboard', weight: 0.12 },
          { id: 'diagonals', weight: 0.12 }
        ],
        organic: {
          baseFillStart: 0.55,
          baseFillGrowth: 0.02,
          minFill: 0.55,
          maxFill: 0.82,
          depthBiasMax: 0.18,
          variability: 0.12,
          minProbability: 0.35,
          maxProbability: 0.92
        },
        singleGap: {
          removeRowChance: 0.5
        },
        multiGap: {
          removeRowChance: 0.75,
          removeColumnChance: 0.65,
          minEmptyRows: 2,
          maxEmptyRows: 3,
          minEmptyCols: 2,
          maxEmptyCols: 4
        },
        uniform: {
          fullRowChance: 0.25
        },
        checkerboard: {
          extraFillChance: 0.45
        },
        diagonals: {
          extraFillChance: 0.4
        },
        brickTypeWeights: {
          base: {
            simple: 0.6,
            resistant: 0.26,
            bonus: 0.14
          },
          levelFactor: {
            step: 0.015,
            simple: -0.25,
            resistant: 0.55,
            bonus: 0.2,
            max: 0.2
          }
        },
        skin: null
      },
      particles: {
        simple: [
          {
            id: 'quarkUp',
            family: 'quark',
            quarkColor: 'red',
            colors: ['#ff6c7a', '#ff2d55'],
            symbol: 'u',
            symbolColor: '#fff5f8',
            sprite: { sheet: 'quarks', column: 0 }
          },
          {
            id: 'quarkDown',
            family: 'quark',
            quarkColor: 'green',
            colors: ['#7ef37d', '#2bc84a'],
            symbol: 'd',
            symbolColor: '#03210f',
            sprite: { sheet: 'quarks', column: 1 }
          },
          {
            id: 'quarkStrange',
            family: 'quark',
            quarkColor: 'blue',
            colors: ['#7ac3ff', '#2f82ff'],
            symbol: 's',
            symbolColor: '#021639',
            sprite: { sheet: 'quarks', column: 2 }
          },
          {
            id: 'quarkCharm',
            family: 'quark',
            quarkColor: 'red',
            colors: ['#ffb36c', '#ff7b2d'],
            symbol: 'c',
            symbolColor: '#241002',
            sprite: { sheet: 'quarks', column: 3 }
          },
          {
            id: 'quarkTop',
            family: 'quark',
            quarkColor: 'green',
            colors: ['#a78bff', '#6c4dff'],
            symbol: 't',
            symbolColor: '#160835',
            sprite: { sheet: 'quarks', column: 4 }
          },
          {
            id: 'quarkBottom',
            family: 'quark',
            quarkColor: 'blue',
            colors: ['#6ce7ff', '#2ab3ff'],
            symbol: 'b',
            symbolColor: '#03202c',
            sprite: { sheet: 'quarks', column: 5 }
          }
        ],
        resistant: [
          {
            id: 'higgs',
            family: 'boson',
            colors: ['#ffe680', '#f7c948'],
            symbol: 'H⁰',
            symbolColor: '#4d3100',
            minHits: 3,
            maxHits: 3,
            sprite: { sheet: 'particles', column: 0 }
          },
          {
            id: 'bosonW',
            family: 'boson',
            colors: ['#8ec5ff', '#4b92ff'],
            symbol: 'W',
            symbolColor: '#04193a',
            minHits: 2,
            maxHits: 3,
            sprite: { sheet: 'particles', column: 1 }
          },
          {
            id: 'bosonZ',
            family: 'boson',
            colors: ['#ffd291', '#ffb74b'],
            symbol: 'Z⁰',
            symbolColor: '#3a1b00',
            minHits: 2,
            maxHits: 3,
            sprite: { sheet: 'particles', column: 2 }
          },
          {
            id: 'gluon',
            family: 'boson',
            colors: ['#14151c', '#2c2e36'],
            symbol: 'g',
            symbolColor: '#9fa5ff',
            minHits: 3,
            maxHits: 3,
            sprite: { sheet: 'particles', column: 3 }
          },
          {
            id: 'photon',
            family: 'boson',
            colors: ['#ffd447', '#ffb347'],
            symbol: 'γ',
            symbolColor: '#3e2500',
            minHits: 2,
            maxHits: 2,
            sprite: { sheet: 'particles', column: 4 }
          }
        ],
        bonus: [
          {
            id: 'positron',
            family: 'lepton',
            colors: ['#f6f6ff', '#dcdcf9'],
            symbol: 'e⁺',
            symbolColor: '#312a5c',
            sprite: { sheet: 'particles', column: 5 }
          },
          {
            id: 'tau',
            family: 'lepton',
            colors: ['#b89bff', '#d9c9ff'],
            symbol: 'τ',
            symbolColor: '#1f0b45',
            sprite: { sheet: 'particles', column: 5 }
          },
          {
            id: 'sterileNeutrino',
            family: 'lepton',
            colors: ['#c3c7d4', '#eff1f6'],
            symbol: 'νₛ',
            symbolColor: '#1f2535',
            sprite: { sheet: 'particles', column: 5 }
          }
        ],
        graviton: {
          id: 'graviton',
          family: 'graviton',
          colors: ['#ff6ec7', '#7afcff', '#ffe45e', '#9d4edd'],
          symbol: 'G*',
          symbolColor: '#ffffff'
        }
      },
      ui: {
        start: {
          message:
            'Guidez le palet et renvoyez la particule pour percuter les briques quantiques.',
          buttonLabel: 'Commencer'
        },
        pause: {
          message: 'Partie en pause. Touchez la raquette pour continuer.',
          buttonLabel: 'Reprendre'
        },
        lifeLost: {
          message: 'Particule perdue ! Touchez la raquette pour continuer.',
          buttonLabel: 'Reprendre'
        },
        levelCleared: {
          template: 'Niveau {level} terminé !{reward}',
          buttonLabel: 'Continuer',
          rewardTemplate: ' {reward} obtenu !',
          noReward: ' Aucun ticket cette fois.',
          speedBonusTemplate: ' Bonus vitesse : +{bonus} !'
        },
        gameOver: {
          withTickets: 'Partie terminée ! Tickets gagnés : {tickets}{bonus}.',
          withoutTickets: 'Partie terminée ! Aucun ticket gagné cette fois-ci.',
          buttonLabel: 'Rejouer'
        },
        hud: {
          ticketSingular: 'ticket',
          ticketPlural: 'tickets',
          bonusTicketSingular: 'ticket Mach3',
          bonusTicketPlural: 'tickets Mach3'
        }
      }
    },
    sudoku: {
      /**
       * Nombre d'indices laissés dans les grilles générées selon la difficulté.
       * Les valeurs min / max permettent d'introduire de la variété sans éditer le code.
       */
      levelClues: {
        facile: { min: 30, max: 34 },
        moyen: { min: 24, max: 28 },
        difficile: { min: 18, max: 22 }
      },
      /**
       * Bonus accordé lorsqu'un Sudoku est résolu rapidement.
       * - timeLimitMinutes : temps maximal (en minutes) pour déclencher le bonus.
       * - offlineBonusHours : durée couverte à 100 % pendant la prochaine collecte hors ligne.
       * - offlineMultiplier : multiplicateur appliqué durant cette fenêtre (1 = 100 % de l’APS).
       */
      rewards: {
        speedCompletion: {
          timeLimitMinutes: 10,
          offlineBonusHours: 6,
          offlineMultiplier: 1
        }
      }
    },
    quantum2048: {
      gridSizes: [3, 4, 5, 6],
      targetValues: [16, 32, 64, 128, 256, 512, 1024, 2048],
      defaultGridSize: 4,
      recommendedTargetBySize: {
        3: 64,
        4: 256,
        5: 1024,
        6: 2048
      },
      targetPoolsBySize: {
        3: [16, 32, 64],
        4: [64, 128, 256, 512],
        5: [128, 256, 512, 1024],
        6: [128, 256, 512, 1024, 2048]
      },
      randomizeGames: true,
      spawnValues: [2, 4],
      spawnWeights: [0.9, 0.1]
    },
  },

  /**
   * Ordre d'affichage des étapes de calcul des productions dans l'onglet Infos.
   * Chaque entrée correspond à un identifiant d'étape connu du jeu. La liste
   * peut être réorganisée ou complétée pour s'adapter à de futurs bonus.
   */
  infoPanels: {
    productionOrder: [
      'baseFlat',
      'shopFlat',
      'elementFlat',
      'fusionFlat',
      'collectionMultiplier',
      'frenzy',
      'trophyMultiplier',
      'total'
    ]
  },

  /**
   * Paramètres généraux de la boutique.
   * - defaultMaxPurchase : limite de niveaux achetables pour chaque bâtiment.
   */
  shop: {
    defaultMaxPurchase: SHOP_MAX_PURCHASE_DEFAULT
  },

  /**
   * Définitions complètes des améliorations de la boutique.
   * Chaque entrée décrit :
   * - baseCost : coût initial de l'amélioration.
   * - costScale : multiplicateur appliqué à chaque niveau.
   * - effect : fonction retournant les bonus conférés pour un niveau donné.
   */
  upgrades: createShopBuildingDefinitions(),

  /**
   * Recettes de fusion moléculaire disponibles dans l'onglet dédié.
   * Chaque recette consomme des éléments du stock courant et applique des
   * bonus immédiats en cas de réussite.
   */
  fusions: [
    {
      id: 'hydrogen',
      name: 'Hydrogène → Hélium (He)',
      translationKey: 'scripts.gameData.fusions.recipes.hydrogen',
      description: 'Fusionnez 4 protons d’H au cœur d’une étoile pour produire 1 He.',
      inputs: [
        { elementId: 'element-001-hydrogene', count: 4 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 10000,
        elements: [
          { elementId: 'element-002-helium', count: 1 }
        ]
      }
    },
    {
      id: 'carbon',
      name: 'Hélium → Carbone (C)',
      translationKey: 'scripts.gameData.fusions.recipes.carbon',
      description: 'Déclenchez la triple nucléosynthèse alpha : fusionnez 3 noyaux d’He pour tenter de forger 1 C.',
      inputs: [
        { elementId: 'element-002-helium', count: 3 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 5000,
        elements: [
          { elementId: 'element-006-carbone', count: 1 }
        ]
      }
    },
    {
      id: 'oxygen',
      name: 'Oxygène (O₂)',
      translationKey: 'scripts.gameData.fusions.recipes.oxygen',
      description: 'Assemblez 2 atomes d’Oxygène pour produire du dioxygène.',
      inputs: [
        { elementId: 'element-008-oxygene', count: 2 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 10000
      }
    },
    {
      id: 'fluorine',
      name: 'Fluor (F₂)',
      translationKey: 'scripts.gameData.fusions.recipes.fluorine',
      description: 'Fusionnez 2 atomes de Fluor pour générer une molécule de difluor.',
      inputs: [
        { elementId: 'element-009-fluor', count: 2 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'chlorine',
      name: 'Chlore (Cl₂)',
      translationKey: 'scripts.gameData.fusions.recipes.chlorine',
      description: 'Associez 2 atomes de Chlore pour obtenir du dichlore.',
      inputs: [
        { elementId: 'element-017-chlore', count: 2 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'bromine',
      name: 'Brome (Br₂)',
      translationKey: 'scripts.gameData.fusions.recipes.bromine',
      description: 'Combinez 2 atomes de Brome pour produire du dibrome.',
      inputs: [
        { elementId: 'element-035-brome', count: 2 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'iodine',
      name: 'Iode (I₂)',
      translationKey: 'scripts.gameData.fusions.recipes.iodine',
      description: 'Assemblez 2 atomes d’Iode pour créer du diiode.',
      inputs: [
        { elementId: 'element-053-iode', count: 2 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'sulfur',
      name: 'Soufre (S₈)',
      translationKey: 'scripts.gameData.fusions.recipes.sulfur',
      description: 'Regroupez 8 atomes de Soufre pour former une couronne cyclique S₈.',
      inputs: [
        { elementId: 'element-016-soufre', count: 8 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'phosphorus',
      name: 'Phosphore (P₄)',
      translationKey: 'scripts.gameData.fusions.recipes.phosphorus',
      description: 'Fusionnez 4 atomes de Phosphore pour synthétiser du tétraphosphore.',
      inputs: [
        { elementId: 'element-015-phosphore', count: 4 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'water',
      name: 'Molécule d’eau (H₂O)',
      translationKey: 'scripts.gameData.fusions.recipes.water',
      description: 'Combinez 2 Hydrogènes et 1 Oxygène pour tenter de former de l’eau.',
      inputs: [
        { elementId: 'element-001-hydrogene', count: 2 },
        { elementId: 'element-008-oxygene', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'sodiumChloride',
      name: 'Chlorure de sodium (NaCl)',
      translationKey: 'scripts.gameData.fusions.recipes.sodiumChloride',
      description: 'Combinez Sodium et Chlore pour cristalliser du sel.',
      inputs: [
        { elementId: 'element-011-sodium', count: 1 },
        { elementId: 'element-017-chlore', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'siliconDioxide',
      name: 'Dioxyde de silicium (SiO₂)',
      translationKey: 'scripts.gameData.fusions.recipes.siliconDioxide',
      description: 'Associez Silicium et Oxygène pour obtenir du quartz.',
      inputs: [
        { elementId: 'element-014-silicium', count: 1 },
        { elementId: 'element-008-oxygene', count: 2 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 100
      }
    },
    {
      id: 'bronzeAlloy',
      name: 'Bronze (alliage Cu-Sn)',
      translationKey: 'scripts.gameData.fusions.recipes.bronzeAlloy',
      description: 'Alliez ~88 % de Cuivre avec ~12 % d’Étain pour obtenir un bronze protohistorique.',
      inputs: [
        { elementId: 'element-029-cuivre', count: 9 },
        { elementId: 'element-050-etain', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 150
      }
    },
    {
      id: 'stainlessSteel18_8',
      name: 'Acier inoxydable 18/8',
      translationKey: 'scripts.gameData.fusions.recipes.stainlessSteel18_8',
      description: 'Combinez ~70 % de Fer, ~18 % de Chrome et ~8 % de Nickel pour former l’inox 18/8.',
      inputs: [
        { elementId: 'element-026-fer', count: 7 },
        { elementId: 'element-024-chrome', count: 2 },
        { elementId: 'element-028-nickel', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 200
      }
    },
    {
      id: 'duraluminAlloy',
      name: 'Duralumin (alliage Al-Cu-Mn)',
      translationKey: 'scripts.gameData.fusions.recipes.duraluminAlloy',
      description: 'Assemblez ~95 % d’Aluminium avec ~4 % de Cuivre et des traces de Manganèse/Magnésium (~1 %).',
      inputs: [
        { elementId: 'element-013-aluminium', count: 8 },
        { elementId: 'element-029-cuivre', count: 1 },
        { elementId: 'element-025-manganese', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 250
      }
    },
    {
      id: 'laitonAlloy',
      name: 'Laiton (alliage Cu-Zn)',
      translationKey: 'scripts.gameData.fusions.recipes.laitonAlloy',
      description: 'Mélangez ~65 % de Cuivre et ~35 % de Zinc pour façonner un laiton de plomberie.',
      inputs: [
        { elementId: 'element-029-cuivre', count: 7 },
        { elementId: 'element-030-zinc', count: 3 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 150
      }
    },
    {
      id: 'leadAcidAlloy',
      name: 'Alliage plomb-acide',
      translationKey: 'scripts.gameData.fusions.recipes.leadAcidAlloy',
      description: 'Solidifiez ~93 % de Plomb avec ~7 % d’Antimoine pour vos batteries au plomb.',
      inputs: [
        { elementId: 'element-082-plomb', count: 9 },
        { elementId: 'element-051-antimoine', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 200
      }
    },
    {
      id: 'ti6Al4VAlloy',
      name: 'Alliage Ti-6Al-4V',
      translationKey: 'scripts.gameData.fusions.recipes.ti6Al4VAlloy',
      description: 'Fusionnez ~90 % de Titane avec ~6 % d’Aluminium et ~4 % de Vanadium pour l’aéronautique.',
      inputs: [
        { elementId: 'element-022-titane', count: 8 },
        { elementId: 'element-013-aluminium', count: 1 },
        { elementId: 'element-023-vanadium', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 300
      }
    },
    {
      id: 'roseGoldAlloy',
      name: 'Or rose (alliage Au-Cu-Ag)',
      translationKey: 'scripts.gameData.fusions.recipes.roseGoldAlloy',
      description: 'Alliez ~75 % d’Or, ~22 % de Cuivre et ~3 % d’Argent pour une teinte rosée 18 carats.',
      inputs: [
        { elementId: 'element-079-or', count: 7 },
        { elementId: 'element-029-cuivre', count: 2 },
        { elementId: 'element-047-argent', count: 1 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 300
      }
    },
    {
      id: 'platinumIridiumAlloy',
      name: 'Alliage platine-iridium',
      translationKey: 'scripts.gameData.fusions.recipes.platinumIridiumAlloy',
      description: 'Combinez ~70 % de Platine avec ~30 % d’Iridium pour une référence de métrologie.',
      inputs: [
        { elementId: 'element-078-platine', count: 7 },
        { elementId: 'element-077-iridium', count: 3 }
      ],
      successChance: 0.5,
      rewards: {
        apcFlat: 350
      }
    }
  ],


  /**
   * Liste des trophées et succès spéciaux.
   * Chaque entrée définit :
   * - id : identifiant unique.
   * - name / description : textes affichés dans la page Objectifs.
   * - condition : type de condition et valeur cible.
   * - reward : effets associés (multiplicateurs, améliorations de frénésie, etc.).
   */
  trophies: [
    ...createAtomScaleTrophies(),
    {
      id: 'millionAtoms',
      name: 'Ruée vers le million',
      description: 'Accumulez un total d’un million d’atomes synthétisés.',
      condition: {
        type: 'lifetimeAtoms',
        amount: { type: 'number', value: 1_000_000 }
      },
      reward: {
        trophyMultiplierAdd: 0.5,
        description: 'Ajoute +0,5 au Boost global sur la production manuelle et automatique.'
      },
      order: -1
    },
    {
      id: 'frenzyCollector',
      name: 'Convergence frénétique',
      description: 'Déclenchez 100 frénésies (APC et APS cumulés).',
      condition: {
        type: 'frenzyTotal',
        amount: 100
      },
      reward: {
        frenzyMaxStacks: 2,
        description: 'Débloque la frénésie multiple : deux frénésies peuvent se cumuler.'
      },
      order: 1010
    },
    {
      id: 'frenzyMaster',
      name: 'Tempête tri-phasée',
      description: 'Déclenchez 1 000 frénésies cumulées.',
      condition: {
        type: 'frenzyTotal',
        amount: 1_000
      },
      reward: {
        frenzyMaxStacks: 3,
        multiplier: {
          global: 1.05
        },
        description: 'Active la triple frénésie et ajoute un bonus global ×1,05.'
      },
      order: 1020
    },
    {
      id: 'ticketHarvester',
      name: 'Collecteur d’étoiles',
      description: 'Complétez les collections Nucléosynthèse primordiale et Spallation cosmique.',
      condition: {
        type: 'collectionRarities',
        rarities: ['commun', 'essentiel']
      },
      reward: {
        ticketStarAutoCollect: {
          delaySeconds: 3
        },
        description: 'Sur l’écran principal, les étoiles à tickets se récoltent seules après 3 secondes.'
      },
      order: 1030
    },
    {
      id: 'alloyMastery',
      name: 'Maîtres des alliages',
      description: 'Réussissez toutes les fusions d’alliages métalliques disponibles.',
      condition: {
        type: 'fusionSuccesses',
        fusions: [
          'bronzeAlloy',
          'stainlessSteel18_8',
          'duraluminAlloy',
          'laitonAlloy',
          'leadAcidAlloy',
          'ti6Al4VAlloy',
          'roseGoldAlloy',
          'platinumIridiumAlloy'
        ]
      },
      reward: {
        trophyMultiplierAdd: 10,
        description: 'Ajoute +10 au multiplicateur de trophées.'
      },
      order: 1040
    }
  ],

  gacha: {
    ticketCost: 1, // Nombre de tickets requis par tirage
    rarities: [
      {
        id: 'commun',
        label: 'Nucléosynthèse primordiale',
        description: "Premiers éléments créés dans l'univers, juste après le Big Bang.",
        weight: 35,
        color: '#1abc9c'
      },
      {
        id: 'essentiel',
        label: 'Spallation cosmique',
        description: 'Éléments légers formés par spallation et désintégrations sous l’action des rayons cosmiques.',
        weight: 25,
        color: '#3498db'
      },
      {
        id: 'stellaire',
        label: 'Étoiles simples',
        description: 'Éléments forgés lentement par fusion au cœur des étoiles de type solaire.',
        weight: 20,
        color: '#9b59b6'
      },
      {
        id: 'singulier',
        label: 'Supernovae',
        description: 'Éléments lourds créés lors des supernovas et des fusions d’étoiles à neutrons.',
        weight: 8,
        color: '#cd6155'
      },
      {
        id: 'mythique',
        label: 'Étoiles massives',
        description: 'Éléments produits par fusion dans les cœurs très chauds des étoiles géantes avant leur explosion.',
        weight: 7,
        color: '#FFBF66'
      },
      {
        id: 'irreel',
        label: 'Artificiels',
        description: 'Éléments instables créés artificiellement par l’homme dans des réacteurs et accélérateurs.',
        weight: 5,
        color: '#7f8c8d'
      }
    ],
    weeklyRarityWeights: {
      monday: {
        commun: 70,
        essentiel: 10,
        stellaire: 10,
        singulier: 10,
        mythique: 0,
        irreel: 0
      },
      tuesday: {
        commun: 70,
        essentiel: 10,
        stellaire: 10,
        singulier: 0,
        mythique: 10,
        irreel: 0
      },
      wednesday: {
        commun: 70,
        essentiel: 10,
        stellaire: 10,
        singulier: 0,
        mythique: 0,
        irreel: 10
      },
      thursday: {
        commun: 70,
        essentiel: 10,
        stellaire: 10,
        singulier: 10,
        mythique: 0,
        irreel: 0
      },
      friday: {
        commun: 70,
        essentiel: 10,
        stellaire: 10,
        singulier: 0,
        mythique: 10,
        irreel: 0
      },
      saturday: {
        commun: 70,
        essentiel: 10,
        stellaire: 10,
        singulier: 0,
        mythique: 0,
        irreel: 10
      },
      sunday: {
        commun: 71,
        essentiel: 10,
        stellaire: 10,
        singulier: 3,
        mythique: 3,
        irreel: 3
      }
    }
  },

  /**
   * Apparition de l'étoile à tickets sur la page principale.
   * - averageSpawnIntervalSeconds : intervalle moyen entre deux apparitions.
   * - speedPixelsPerSecond : vitesse de déplacement de l'icône.
   * - speedVariance : variation aléatoire relative appliquée à la vitesse (0.35 = ±35 %).
   * - spawnOffsetPixels : distance (en px) à l'extérieur de l'écran lors de l'apparition.
   * - size : taille (en pixels) du sprite.
   * - rewardTickets : nombre de tickets octroyés par clic.
   */
  ticketStar: {
    averageSpawnIntervalSeconds: 600,
    speedPixelsPerSecond: 90,
    speedVariance: 0.35,
    spawnOffsetPixels: 48,
    size: 72,
    rewardTickets: 1
  },

  /**
   * Bonus appliqués en fonction de la rareté des éléments de la collection.
   * Chaque groupe permet de configurer :
   * - perCopy : bonus plats ajoutés pour chaque copie possédée. Options :
   *   - clickAdd / autoAdd : valeurs ajoutées à l'APC / APS.
   *   - minCopies / minUnique : quantités minimales avant d'activer le bonus.
   * - setBonus : bonus uniques débloqués une fois le groupe complété. Options :
   *   - requireAllUnique (par défaut true) : exige d'avoir tous les éléments de la rareté.
   *   - minCopies / minUnique : seuils supplémentaires pour déclencher le bonus.
   * - multiplier : multiplicateur progressif basé sur le nombre de copies.
   * - Les raretés spéciales (ex. : « mythique ») peuvent définir des bonus
   *   supplémentaires via les clés suivantes :
   *   - ticketBonus : réduction de l'intervalle de l'étoile à tickets.
   *   - offlineBonus : multiplicateur de gains hors-ligne par duplicata.
   *   - duplicateOverflow : gain plat supplémentaire après le plafond.
   *   - frenzyBonus : multiplicateur des chances de frénésie.
   *   Les blocs `labels` associés peuvent personnaliser l'intitulé affiché
   *   pour ces bonus via les clés `setBonus`/`frenzyBonus`, `ticketBonus`,
   *   `offlineBonus` et `duplicateOverflow`.
   */
  elementBonuses: {
    groups: {
      commun: {
        perCopy: {
          clickAdd: 25
        },
        setBonus: {
          clickAdd: 500,
          requireAllUnique: true
        },
        multiplier: {
          every: 100,
          increment: 1,
          targets: ['perClick', 'perSecond'],
          label: COLLECTION_MULTIPLIER_LABEL_KEY
        }
      },
      essentiel: {
        perCopy: {
          uniqueClickAdd: 100,
          duplicateClickAdd: 200,
          label: 'scripts.config.elementBonuses.essentiel.perCopy'
        },
        setBonus: [
          {
            clickAdd: 1000,
            requireAllUnique: true,
            label: 'scripts.config.elementBonuses.essentiel.setBonus'
          }
        ],
        multiplier: {
          every: 50,
          increment: 1,
          targets: ['perClick', 'perSecond'],
          label: COLLECTION_MULTIPLIER_LABEL_KEY
        }
      },
      stellaire: {
        perCopy: {
          uniqueClickAdd: 200,
          duplicateClickAdd: 250,
          label: 'scripts.config.elementBonuses.stellaire.perCopy'
        },
        multiplier: {
          every: 25,
          increment: 1,
          targets: ['perClick', 'perSecond'],
          label: COLLECTION_MULTIPLIER_LABEL_KEY
        },
        setBonus: {
          requireAllUnique: true,
          label: 'scripts.config.elementBonuses.stellaire.setBonus',
          rarityFlatMultipliers: {
            commun: { perClick: 2 }
          }
        },
        labels: {
          perCopy: 'scripts.config.elementBonuses.stellaire.labels.perCopy',
          setBonus: 'scripts.config.elementBonuses.stellaire.setBonus',
          multiplier: COLLECTION_MULTIPLIER_LABEL_KEY
        }
      },
      singulier: {
        perCopy: {
          uniqueClickAdd: 50,
          uniqueAutoAdd: 50,
          duplicateClickAdd: 50,
          duplicateAutoAdd: 50,
          label: 'scripts.config.elementBonuses.singulier.perCopy'
        },
        multiplier: {
          every: 10,
          increment: 1,
          targets: ['perClick', 'perSecond'],
          label: COLLECTION_MULTIPLIER_LABEL_KEY
        },
        labels: {
          perCopy: 'scripts.config.elementBonuses.singulier.perCopy',
          multiplier: COLLECTION_MULTIPLIER_LABEL_KEY
        }
      },
      mythique: {
        labels: {
          setBonus: 'scripts.config.elementBonuses.mythique.setBonus',
          ticketBonus: 'scripts.config.elementBonuses.mythique.ticketBonus',
          offlineBonus: 'scripts.config.elementBonuses.mythique.offlineBonus',
          duplicateOverflow: 'scripts.config.elementBonuses.mythique.duplicateOverflow',
          frenzyBonus: 'scripts.config.elementBonuses.mythique.frenzyBonus'
        },
        ticketBonus: {
          uniqueReductionSeconds: 15,
          minIntervalSeconds: 30
        },
        offlineBonus: {
          baseMultiplier: 0.01,
          perDuplicate: 0.01,
          cap: 0.5
        },
        duplicateOverflow: {
          flatBonus: 50
        },
        frenzyBonus: {
          multiplier: 1.5
        }
      },
      irreel: {
        crit: {
          perUnique: {
            chanceAdd: 0.01
          },
          perDuplicate: {
            multiplierAdd: 0.01
          }
        },
        multiplier: {
          every: 5,
          increment: 1,
          targets: ['perClick', 'perSecond'],
          label: COLLECTION_MULTIPLIER_LABEL_KEY
        }
      }
    }
  },

  elementFamilies: {
    'alkali-metal': {
      label: 'Métaux alcalins',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les métaux alcalins sont collectés.
      ]
    },
    'alkaline-earth-metal': {
      label: 'Métaux alcalino-terreux',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les métaux alcalino-terreux sont collectés.
      ]
    },
    'transition-metal': {
      label: 'Métaux de transition',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les métaux de transition sont collectés.
      ]
    },
    'post-transition-metal': {
      label: 'Métaux pauvres',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les métaux pauvres sont collectés.
      ]
    },
    metalloid: {
      label: 'Métalloïdes',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les métalloïdes sont collectés.
      ]
    },
    nonmetal: {
      label: 'Non-métaux',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les non-métaux sont collectés.
      ]
    },
    halogen: {
      label: 'Halogènes',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les halogènes sont collectés.
      ]
    },
    'noble-gas': {
      label: 'Gaz nobles',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les gaz nobles sont collectés.
      ]
    },
    lanthanide: {
      label: 'Lanthanides',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les lanthanides sont collectés.
      ]
    },
    actinide: {
      label: 'Actinides',
      bonuses: [
        // Ajoutez ici les bonus accordés lorsque tous les actinides sont collectés.
      ]
    }
  },

  elements: [
    {
      numero: 1,
      name: 'Hydrogène',
      famille: 'nonmetal',
      rarete: 'commun',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 2,
      name: 'Hélium',
      famille: 'noble-gas',
      rarete: 'commun',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 3,
      name: 'Lithium',
      famille: 'alkali-metal',
      rarete: 'essentiel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 4,
      name: 'Béryllium',
      famille: 'alkaline-earth-metal',
      rarete: 'essentiel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 5,
      name: 'Bore',
      famille: 'metalloid',
      rarete: 'essentiel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 6,
      name: 'Carbone',
      famille: 'nonmetal',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 7,
      name: 'Azote',
      famille: 'nonmetal',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 8,
      name: 'Oxygène',
      famille: 'nonmetal',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 9,
      name: 'Fluor',
      famille: 'halogen',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 10,
      name: 'Néon',
      famille: 'noble-gas',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 11,
      name: 'Sodium',
      famille: 'alkali-metal',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 12,
      name: 'Magnésium',
      famille: 'alkaline-earth-metal',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 13,
      name: 'Aluminium',
      famille: 'post-transition-metal',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 14,
      name: 'Silicium',
      famille: 'metalloid',
      rarete: 'stellaire',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 15,
      name: 'Phosphore',
      famille: 'nonmetal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 16,
      name: 'Soufre',
      famille: 'nonmetal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 17,
      name: 'Chlore',
      famille: 'halogen',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 18,
      name: 'Argon',
      famille: 'noble-gas',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 19,
      name: 'Potassium',
      famille: 'alkali-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 20,
      name: 'Calcium',
      famille: 'alkaline-earth-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 21,
      name: 'Scandium',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 22,
      name: 'Titane',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 23,
      name: 'Vanadium',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 24,
      name: 'Chrome',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 25,
      name: 'Manganèse',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 26,
      name: 'Fer',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 27,
      name: 'Cobalt',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 28,
      name: 'Nickel',
      famille: 'transition-metal',
      rarete: 'mythique',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 29,
      name: 'Cuivre',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 30,
      name: 'Zinc',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 31,
      name: 'Gallium',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 32,
      name: 'Germanium',
      famille: 'metalloid',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 33,
      name: 'Arsenic',
      famille: 'metalloid',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 34,
      name: 'Sélénium',
      famille: 'nonmetal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 35,
      name: 'Brome',
      famille: 'halogen',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 36,
      name: 'Krypton',
      famille: 'noble-gas',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 37,
      name: 'Rubidium',
      famille: 'alkali-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 38,
      name: 'Strontium',
      famille: 'alkaline-earth-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 39,
      name: 'Yttrium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 40,
      name: 'Zirconium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 41,
      name: 'Niobium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 42,
      name: 'Molybdène',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 43,
      name: 'Technétium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 44,
      name: 'Ruthénium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 45,
      name: 'Rhodium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 46,
      name: 'Palladium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 47,
      name: 'Argent',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 48,
      name: 'Cadmium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 49,
      name: 'Indium',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 50,
      name: 'Étain',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 51,
      name: 'Antimoine',
      famille: 'metalloid',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 52,
      name: 'Tellure',
      famille: 'metalloid',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 53,
      name: 'Iode',
      famille: 'halogen',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 54,
      name: 'Xénon',
      famille: 'noble-gas',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 55,
      name: 'Césium',
      famille: 'alkali-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 56,
      name: 'Baryum',
      famille: 'alkaline-earth-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 57,
      name: 'Lanthane',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 58,
      name: 'Cérium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 59,
      name: 'Praséodyme',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 60,
      name: 'Néodyme',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 61,
      name: 'Prométhium',
      famille: 'lanthanide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 62,
      name: 'Samarium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 63,
      name: 'Europium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 64,
      name: 'Gadolinium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 65,
      name: 'Terbium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 66,
      name: 'Dysprosium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 67,
      name: 'Holmium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 68,
      name: 'Erbium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 69,
      name: 'Thulium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 70,
      name: 'Ytterbium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 71,
      name: 'Lutécium',
      famille: 'lanthanide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 72,
      name: 'Hafnium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 73,
      name: 'Tantale',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 74,
      name: 'Tungstène',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 75,
      name: 'Rhénium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 76,
      name: 'Osmium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 77,
      name: 'Iridium',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 78,
      name: 'Platine',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 79,
      name: 'Or',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 80,
      name: 'Mercure',
      famille: 'transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 81,
      name: 'Thallium',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 82,
      name: 'Plomb',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 83,
      name: 'Bismuth',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 84,
      name: 'Polonium',
      famille: 'post-transition-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 85,
      name: 'Astate',
      famille: 'halogen',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 86,
      name: 'Radon',
      famille: 'noble-gas',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 87,
      name: 'Francium',
      famille: 'alkali-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 88,
      name: 'Radium',
      famille: 'alkaline-earth-metal',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 89,
      name: 'Actinium',
      famille: 'actinide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 90,
      name: 'Thorium',
      famille: 'actinide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 91,
      name: 'Protactinium',
      famille: 'actinide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 92,
      name: 'Uranium',
      famille: 'actinide',
      rarete: 'singulier',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 93,
      name: 'Neptunium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 94,
      name: 'Plutonium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 95,
      name: 'Américium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 96,
      name: 'Curium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 97,
      name: 'Berkélium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 98,
      name: 'Californium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 99,
      name: 'Einsteinium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 100,
      name: 'Fermium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 101,
      name: 'Mendélévium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 102,
      name: 'Nobélium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 103,
      name: 'Lawrencium',
      famille: 'actinide',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 104,
      name: 'Rutherfordium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 105,
      name: 'Dubnium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 106,
      name: 'Seaborgium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 107,
      name: 'Bohrium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 108,
      name: 'Hassium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 109,
      name: 'Meitnérium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 110,
      name: 'Darmstadtium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 111,
      name: 'Roentgenium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 112,
      name: 'Copernicium',
      famille: 'transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 113,
      name: 'Nihonium',
      famille: 'post-transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 114,
      name: 'Flérovium',
      famille: 'post-transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 115,
      name: 'Moscovium',
      famille: 'post-transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 116,
      name: 'Livermorium',
      famille: 'post-transition-metal',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 117,
      name: 'Tennesse',
      famille: 'halogen',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    },
    {
      numero: 118,
      name: 'Oganesson',
      famille: 'noble-gas',
      rarete: 'irreel',
      bonus: 'ajoute +1 au APS flat'
    }
  ]
};

GAME_CONFIG.progression.defaultTheme = GAME_CONFIG.themes.default;

if (typeof globalThis !== 'undefined') {
  globalThis.GAME_CONFIG = GAME_CONFIG;
}
