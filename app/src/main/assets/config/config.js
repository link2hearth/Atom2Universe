/** Active ou désactive complètement l'accès au DevKit. */
const DEFAULT_DEVKIT_ENABLED = false;

/**
 * Préfixe commun utilisé pour stocker les surcharges de configuration dans le localStorage.
 * Permet de persister les modifications réalisées via l'interface (DevKit, Collection, etc.).
 */
const CONFIG_OVERRIDE_STORAGE_PREFIX = 'atom2univers.config.';

const CONFIG_OVERRIDE_KEYS = Object.freeze({
  devkit: `${CONFIG_OVERRIDE_STORAGE_PREFIX}devkitEnabled`,
  collection: `${CONFIG_OVERRIDE_STORAGE_PREFIX}collectionEnabled`,
  info: `${CONFIG_OVERRIDE_STORAGE_PREFIX}infoSectionsEnabled`
});

/**
 * Paramètres globaux pour la gestion des sauvegardes.
 * `storageKey` est utilisé pour stocker les métadonnées côté web.
 * `maxEntries` limite le nombre de sauvegardes conservées.
 * `minAutoIntervalMs` impose un délai minimal entre deux sauvegardes auto.
 * `maxNativeEntries` aligne la limite avec le stockage natif Android.
 */
const SAVE_BACKUP_SETTINGS = Object.freeze({
  storageKey: 'atom2univers.backups',
  maxEntries: 8,
  minAutoIntervalMs: 3 * 60 * 1000,
  maxNativeEntries: 8
});

function getConfigStorage() {
  if (typeof globalThis === 'undefined' || typeof globalThis.localStorage === 'undefined') {
    return null;
  }
  return globalThis.localStorage;
}

function readConfigBoolean(key) {
  const storage = getConfigStorage();
  if (!storage) {
    return undefined;
  }
  try {
    const raw = storage.getItem(key);
    if (raw === 'true') {
      return true;
    }
    if (raw === 'false') {
      return false;
    }
  } catch (error) {
    return undefined;
  }
  return undefined;
}

function writeConfigBoolean(key, value) {
  const storage = getConfigStorage();
  if (!storage) {
    return false;
  }
  try {
    storage.setItem(key, value ? 'true' : 'false');
    return true;
  } catch (error) {
    return false;
  }
}

function resolveConfigBoolean(key, fallback) {
  const stored = readConfigBoolean(key);
  if (typeof stored === 'boolean') {
    return stored;
  }
  return fallback;
}

function toggleConfigBoolean(key, fallback) {
  const next = !resolveConfigBoolean(key, fallback);
  writeConfigBoolean(key, next);
  return next;
}

const DEVKIT_ENABLED = resolveConfigBoolean(CONFIG_OVERRIDE_KEYS.devkit, DEFAULT_DEVKIT_ENABLED);

/**
 * Active ou désactive l'ensemble des fonctionnalités de collection.
 * Lorsque désactivé, le bouton de navigation et les récompenses gacha associées sont masqués.
 */
const DEFAULT_COLLECTION_SYSTEM_ENABLED = false;

const COLLECTION_SYSTEM_ENABLED = resolveConfigBoolean(
  CONFIG_OVERRIDE_KEYS.collection,
  DEFAULT_COLLECTION_SYSTEM_ENABLED
);

const DEFAULT_INFO_SECTIONS_ENABLED = false;

const INFO_SECTIONS_ENABLED = resolveConfigBoolean(
  CONFIG_OVERRIDE_KEYS.info,
  DEFAULT_INFO_SECTIONS_ENABLED
);

function toggleDevkitFeatureEnabled() {
  const next = toggleConfigBoolean(CONFIG_OVERRIDE_KEYS.devkit, DEFAULT_DEVKIT_ENABLED);
  if (typeof globalThis !== 'undefined') {
    globalThis.DEVKIT_ENABLED = next;
  }
  return next;
}

function toggleCollectionFeatureEnabled() {
  const next = toggleConfigBoolean(
    CONFIG_OVERRIDE_KEYS.collection,
    DEFAULT_COLLECTION_SYSTEM_ENABLED
  );
  if (typeof globalThis !== 'undefined') {
    globalThis.COLLECTION_SYSTEM_ENABLED = next;
  }
  return next;
}

function toggleInfoSectionsFeatureEnabled() {
  const next = toggleConfigBoolean(
    CONFIG_OVERRIDE_KEYS.info,
    DEFAULT_INFO_SECTIONS_ENABLED
  );
  if (typeof globalThis !== 'undefined') {
    globalThis.INFO_SECTIONS_ENABLED = next;
  }
  return next;
}

/**
 * Configuration centrale du jeu Atom → Univers.
 * Toutes les valeurs ajustables (équilibrage, affichage, grands nombres, etc.)
 * sont rassemblées ici pour faciliter les modifications futures.
 */
const SHOP_MAX_PURCHASE_DEFAULT = 100;

/**
 * Durée (en millisecondes) de l’animation de fondu appliquée au démarrage.
 */
const STARTUP_FADE_DURATION_MS = 1000;

/**
 * Ratio maximal appliqué lors du rendu des canvas.
 * Permet de conserver une image nette sans dépasser un coût GPU excessif
 * sur les appareils à très haute densité de pixels.
 */
const MAX_CANVAS_DEVICE_PIXEL_RATIO = 2;
const COLLECTION_MULTIPLIER_LABEL_KEY = 'scripts.config.elementBonuses.collectionMultiplier';

/**
 * Réglages des moteurs audio chiptune.
 * Permet d'ajuster le gain global et la coloration d'effets sans modifier le code du moteur.
 */
const AUDIO_ENGINE_SETTINGS = Object.freeze({
  scc: Object.freeze({
    masterGain: 0.18,
    softClipperDrive: 1.05,
    chorusDelayMs: 12,
    chorusMix: 0.025
  })
});

/**
 * Profils de performance disponibles pour la boucle principale du jeu.
 * - `apcFlushIntervalMs` contrôle la fréquence minimale (en millisecondes)
 *   à laquelle les gains manuels (APC) sont appliqués au total d'atomes.
 * - `apsFlushIntervalMs` contrôle la fréquence minimale (en millisecondes)
 *   à laquelle les gains automatiques (APS) sont appliqués.
 * - `frameIntervalMs` impose un délai minimal entre deux itérations de la
 *   boucle principale (0 = rafraîchissement natif via requestAnimationFrame).
 */
const PERFORMANCE_MODE_SETTINGS = Object.freeze({
  fluid: Object.freeze({
    apcFlushIntervalMs: 16,
    apsFlushIntervalMs: 16,
    frameIntervalMs: 0,
    atomAnimation: Object.freeze({
      amplitudeScale: 1,
      motionScale: 1
    })
  }),
  eco: Object.freeze({
    apcFlushIntervalMs: 250,
    apsFlushIntervalMs: 500,
    frameIntervalMs: 33,
    atomAnimation: Object.freeze({
      amplitudeScale: 1,
      motionScale: 1
    })
  })
});

/**
 * Métadonnées associées aux modes de performance.
 * `isDefault` identifie le mode appliqué lors du premier lancement.
 */
const PERFORMANCE_MODE_DEFINITIONS = Object.freeze([
  Object.freeze({
    id: 'fluid',
    labelKey: 'index.sections.options.performance.options.fluid'
  }),
  Object.freeze({
    id: 'eco',
    labelKey: 'index.sections.options.performance.options.eco',
    isDefault: true
  })
]);

/**
 * Paramètres d'affichage du Démineur.
 * `targetCellSize` indique la taille idéale (en pixels) d'une case lorsque c'est possible.
 * `minCellSize` et `maxCellSize` encadrent la taille acceptable d'une case afin de conserver une
 * lisibilité correcte, tandis que `maxRows` et `maxCols` bornent le nombre de lignes/colonnes
 * générées automatiquement.
 */
const MINESWEEPER_BOARD_SETTINGS = Object.freeze({
  targetCellSize: 42,
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
 * Durées utilisées pour les notes jouées manuellement depuis le clavier.
 * Une durée plus longue améliore la tenue des sons sur les écrans tactiles,
 * tout en laissant la possibilité d’écourter la note à la relâche.
 */
const MIDI_MANUAL_NOTE_DEFAULT_DURATION_SECONDS = 6;
const MIDI_MANUAL_NOTE_MIN_DURATION_SECONDS = 2.5;

/**
 * Délai (en secondes) entre l’apparition visuelle d’une note et sa lecture réelle.
 * Permet d’afficher les barres de prévisualisation qui descendent vers le clavier.
 */
const MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS = 2;

/**
 * Paramètres de planification des notes MIDI.
 * `MIDI_PLAYBACK_SCHEDULE_AHEAD_SECONDS` définit la fenêtre (en secondes) dans laquelle
 * les notes sont préparées. `MIDI_PLAYBACK_SCHEDULER_INTERVAL_SECONDS` contrôle la
 * fréquence de recalcul. Des valeurs plus faibles allègent la charge CPU tout en
 * conservant une marge suffisante pour éviter les coupures audio.
 */
const MIDI_PLAYBACK_SCHEDULE_AHEAD_SECONDS = 0.12;
const MIDI_PLAYBACK_SCHEDULER_INTERVAL_SECONDS = 0.06;

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
  globalThis.MAX_CANVAS_DEVICE_PIXEL_RATIO = MAX_CANVAS_DEVICE_PIXEL_RATIO;
  globalThis.MINESWEEPER_BOARD_SETTINGS = MINESWEEPER_BOARD_SETTINGS;
  globalThis.MIDI_KEYBOARD_LAYOUTS = MIDI_KEYBOARD_LAYOUTS;
  globalThis.MIDI_DEFAULT_KEYBOARD_LAYOUT_ID = MIDI_DEFAULT_KEYBOARD_LAYOUT_ID;
  globalThis.MIDI_KEYBOARD_WINDOW_SIZE = MIDI_KEYBOARD_WINDOW_SIZE;
  globalThis.MIDI_MANUAL_NOTE_DEFAULT_DURATION_SECONDS = MIDI_MANUAL_NOTE_DEFAULT_DURATION_SECONDS;
  globalThis.MIDI_MANUAL_NOTE_MIN_DURATION_SECONDS = MIDI_MANUAL_NOTE_MIN_DURATION_SECONDS;
  globalThis.MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS = MIDI_PLAYBACK_PREVIEW_LEAD_SECONDS;
  globalThis.MIDI_PLAYBACK_SCHEDULE_AHEAD_SECONDS = MIDI_PLAYBACK_SCHEDULE_AHEAD_SECONDS;
  globalThis.MIDI_PLAYBACK_SCHEDULER_INTERVAL_SECONDS = MIDI_PLAYBACK_SCHEDULER_INTERVAL_SECONDS;
  globalThis.MIDI_PREVIEW_COLOR_PALETTE = MIDI_PREVIEW_COLOR_PALETTE;
  globalThis.PERFORMANCE_MODE_SETTINGS = PERFORMANCE_MODE_SETTINGS;
  globalThis.PERFORMANCE_MODE_DEFINITIONS = PERFORMANCE_MODE_DEFINITIONS;
  globalThis.STARTUP_FADE_DURATION_MS = STARTUP_FADE_DURATION_MS;
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

const SHOP_BUILDING_IDS = ['godFinger', 'starCore', 'gachaTicketBooth', 'mach3TicketBooth'];

const SHOP_PROGRESSIVE_GROWTH_RATE = 1.11;

function calculateProgressiveBonus(level = 0, baseIncrement = 1, growthRate = SHOP_PROGRESSIVE_GROWTH_RATE) {
  if (!Number.isFinite(level) || level <= 0) {
    return 0;
  }

  const increment = Number.isFinite(baseIncrement) ? baseIncrement : 0;
  if (increment <= 0) {
    return 0;
  }

  const rate = Number.isFinite(growthRate) && growthRate > 0 ? growthRate : 1;

  if (Math.abs(rate - 1) < 1e-9) {
    return increment * level;
  }

  const total = increment * (Math.pow(rate, level) - 1) / (rate - 1);
  const rounded = Math.round(total * 1000) / 1000;
  return Number.isFinite(rounded) && rounded > 0 ? rounded : 0;
}

function loadConfigJson(path, fallback) {
  if (typeof path !== 'string' || !path.trim()) {
    return fallback;
  }
  if (typeof XMLHttpRequest === 'undefined') {
    return fallback;
  }
  try {
    const request = new XMLHttpRequest();
    request.open('GET', path, false);
    request.overrideMimeType('application/json');
    request.send(null);
    const status = Number(request.status) || 0;
    const responseText = request.responseText;
    const hasBody = typeof responseText === 'string' && responseText.trim();
    const successfulRequest = (status >= 200 && status < 300) || (status === 0 && hasBody);
    if (successfulRequest && hasBody) {
      try {
        return JSON.parse(responseText);
      } catch (parseError) {
        console.warn('Unable to parse configuration JSON', path, parseError);
      }
    }
  } catch (error) {
    console.warn('Unable to load configuration JSON', path, error);
  }
  return fallback;
}

const ARCADE_HOLDEM_CONFIG = loadConfigJson('./config/arcade/holdem.json', {});
const ARCADE_BALANCE_CONFIG = loadConfigJson('./config/arcade/balance.json', {});
const ARCADE_MATH_CONFIG = loadConfigJson('./config/arcade/math.json', {});
const ARCADE_DICE_CONFIG = loadConfigJson('./config/arcade/dice.json', {});
const ARCADE_PARTICULES_CONFIG = loadConfigJson('./config/arcade/particules.json', {});
const ARCADE_ECHECS_CONFIG = loadConfigJson('./config/arcade/echecs.json', {});
const ARCADE_SUDOKU_CONFIG = loadConfigJson('./config/arcade/sudoku.json', {});
const ARCADE_SOLITAIRE_CONFIG = loadConfigJson('./config/arcade/solitaire.json', {});
const ARCADE_QUANTUM2048_CONFIG = loadConfigJson('./config/arcade/quantum2048.json', {});
const ARCADE_DEMINEUR_CONFIG = loadConfigJson('./config/arcade/demineur.json', {});

function createShopBuildingDefinitions() {
  const withDefaults = def => ({ maxLevel: SHOP_MAX_PURCHASE_DEFAULT, ...def });
  return [
    {
      id: 'godFinger',
      name: 'Doigt créateur',
      description: 'Le pouvoir divin canalisé dans un seul clic.',
      effectSummary:
        'Production manuelle : commence à +1 APC et progresse d’environ +11 % par niveau.',
      category: 'manual',
      baseCost: 25,
      costScale: 1.16,
      effect: (level = 0) => {
        const clickAdd = calculateProgressiveBonus(level, 1);
        return { clickAdd };
      }
    },
    {
      id: 'starCore',
      name: 'Cœur d’étoile',
      description: 'Compactez une étoile pour générer des flux constants d’atomes.',
      effectSummary:
        'Production passive : commence à +1 APS et progresse d’environ +11 % par niveau.',
      category: 'auto',
      baseCost: 30,
      costScale: 1.16,
      effect: (level = 0) => {
        const autoAdd = calculateProgressiveBonus(level, 1);
        return { autoAdd };
      }
    },
    {
      id: 'gachaTicketBooth',
      name: 'Guichet gacha',
      description: 'Échangez vos atomes contre des tickets de tirage garantis.',
      effectSummary:
        'Convertit les atomes en tickets gacha : +1 ticket par achat. Limite portée à 200 niveaux (+200 par Big Bang). Le prix augmente de 25 000 atomes à chaque niveau et ce coût de base est multiplié par 4 000 après chaque Big Bang.',
      category: 'special',
      baseCost: 250000,
      costIncrement: 250000,
      bigBangBaseCostMultiplier: 4000,
      // Multiplie le coût de base (et l'incrément) du magasin après chaque Big Bang.
      gachaTicketsPerPurchase: 1,
      maxLevel: 200,
      bigBangLevelBonusMultiplier: 2,
      // Multiplie le bonus de niveaux supplémentaires accordé après chaque Big Bang.
      effect: () => ({})
    },
    {
      id: 'mach3TicketBooth',
      name: 'Terminal Mach3',
      description: 'Échangez vos tickets gacha contre des crédits Mach3 garantis.',
      effectSummary:
        'Convertit 100 tickets gacha en 1 crédit Mach3 par achat. Limite portée à 2 niveaux (+2 par Big Bang).',
      category: 'special',
      baseCost: 0,
      gachaTicketCostPerPurchase: 100,
      mach3TicketsPerPurchase: 1,
      maxLevel: 2,
      bigBangLevelBonusMultiplier: 0.02,
      // 0,02 = +2 niveaux par Big Bang avec un palier de 100.
      effect: () => ({})
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

const RAW_GACHA_BONUS_IMAGE_CONFIG = loadConfigJson(
  './config/gacha/bonus-images.json',
  { images: [] }
);

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
   * Paramètres dédiés aux moteurs audio.
   * - engines.scc.masterGain : atténue le signal après mixage pour éviter la saturation.
   * - engines.scc.softClipperDrive : intensité du lissage appliqué pour limiter les crêtes.
   * - engines.scc.chorusDelayMs : délai du chorus (en millisecondes).
   * - engines.scc.chorusMix : proportion du signal traité réinjectée.
   */
  audio: {
    engines: {
      scc: {
        masterGain: AUDIO_ENGINE_SETTINGS.scc.masterGain,
        softClipperDrive: AUDIO_ENGINE_SETTINGS.scc.softClipperDrive,
        chorusDelayMs: AUDIO_ENGINE_SETTINGS.scc.chorusDelayMs,
        chorusMix: AUDIO_ENGINE_SETTINGS.scc.chorusMix
      }
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
            id: 'particles',
            label: 'Arcade Particules :',
            description:
              'Collisionnez les particules élémentaires afin de gagner des tickets de tirage pour le Gacha, et des crédits pour le mini jeu Métaux.'
          },
          {
            id: 'match3',
            label: 'Arcade Métaux :',
            description:
              'Alignez les alliages pour forger des bonus qui dopent votre production.'
          },
          {
            id: 'photon',
            label: 'Arcade Photon :',
            description:
              'Guidez un photon sur son onde cosmique et récoltez l’énergie sans perdre le rythme.'
          },
          {
            id: 'objectx',
            label: 'Arcade ObjectX :',
            description:
              "Fusionnez des blocs quantiques sur la grille choisie jusqu’à atteindre la tuile objectif."
          },
          {
            id: 'bigger',
            label: 'Arcade Plus gros :',
            description:
              'Gérez un bac plein écran et fusionnez les billes identiques pour fabriquer la taille 1024.'
          },
          {
            id: 'balance',
            label: 'Arcade Balance :',
            description:
              'Disposez des blocs de masses différentes pour maintenir la planche parfaitement stable.'
          },
          {
            id: 'math',
            label: 'Arcade Math :',
            description:
              'Complétez les nombres ou symboles manquants et maintenez la série de calculs.'
          },
          {
            id: 'sudoku',
            label: 'Arcade Sudoku :',
            description:
              'Résolvez des grilles générées dynamiquement avec des aides optionnelles.'
          },
          {
            id: 'demineur',
            label: 'Arcade Démineur :',
            description:
              'Dévoilez toutes les cases sans déclencher les mines quantiques.'
          },
          {
            id: 'solitaire',
            label: 'Arcade Solitaire :',
            description:
              'Empilez les cartes pour compléter les quatre fondations le plus efficacement possible.'
          },
          {
            id: 'echecs',
            label: 'Arcade Échecs :',
            description:
              'Affrontez une IA adaptable sur un échiquier futuriste.'
          },
          {
            id: 'gameOfLife',
            label: 'Arcade Jeu de la vie :',
            description:
              'Orchestrez vos motifs favoris dans l’automate cellulaire de Conway.'
          },
          {
            id: 'escape',
            label: 'Arcade Escape :',
            description:
              'Progressez en infiltration dans un labyrinthe procédural sans entrer dans le champ de vision des gardiens.'
          },
          {
            id: 'blackjack',
            label: 'Arcade Blackjack :',
            description:
              'Affrontez le croupier avec un sabot qui se régénère automatiquement.'
          },
          {
            id: 'pachinko',
            label: 'Arcade Pachinko :',
            description:
              'Lâchez des orbes prismatiques sur un plateau étoilé et visez les cases à fort multiplicateur.'
          },
          {
            id: 'holdem',
            label: 'Arcade Hold’em :',
            description:
              'Affrontez une table de poker quantique avec des adversaires adaptatifs et gérez vos relances.'
          },
          {
            id: 'gacha',
            label: 'Gacha :',
            description:
              'Grâce à vos tickets gagnez des Atomes du tableau périodique des éléments, leurs collections vous octroieront des bonus de APS et APC notamment.'
          },
          {
            id: 'tableau',
            label: 'Tableau :',
            description:
              'Consultez le tableau périodique pour suivre vos collections et leurs bonus cumulés.'
          },
          {
            id: 'fusion',
            label: 'Fusion :',
            description:
              'Combinez vos éléments spéciaux pour débloquer des améliorations permanentes.'
          },
          {
            id: 'musique',
            label: 'Musique :',
            description:
              'Personnalisez l’ambiance sonore avec vos playlists chiptune et vos SoundFonts.'
          }
        ]
      }
    }
  },

  /**
   * Liste des fonctionnalités configurables du jeu.
   * Permet aux modules d’interface de parcourir facilement les identifiants déclarés.
   */
  featureList: Object.freeze([
    'arcade.hub',
    'arcade.particules',
    'arcade.metaux',
    'arcade.photon',
    'arcade.objectx',
    'arcade.bigger',
    'arcade.balance',
    'arcade.math',
    'arcade.sudoku',
    'arcade.demineur',
    'arcade.solitaire',
    'arcade.roulette',
    'arcade.pachinko',
    'arcade.blackjack',
    'arcade.holdem',
    'arcade.echecs',
    'arcade.gameOfLife',
    'arcade.escape',
    'system.gacha',
    'system.tableau',
    'system.fusion',
    'system.musique'
  ]),

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
      default: 'large',
      options: [
        { id: 'small', factor: 0.75 },
        { id: 'normal', factor: 1 },
        { id: 'large', factor: 1.25 },
        { id: 'x2', factor: 1.5 }
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
    offlineCapSeconds: 60 * 60 * 240,
    offlineTickets: {
      secondsPerTicket: 15 * 60,
      capSeconds: 60 * 60 * 60
    },
    defaultTheme: 'dark',
    crit: {
      baseChance: 0.05,
      baseMultiplier: 2,
      maxMultiplier: 1000
    },
    featureUnlocks: {
      arcade: {
        hub: { type: 'lifetimeAtoms', amount: { type: 'number', value: 1000 } },
        particules: { type: 'feature', requires: 'arcade.hub' },
        metaux: { type: 'feature', requires: 'arcade.hub' },
        photon: { type: 'feature', requires: 'arcade.hub' },
        objectx: { type: 'feature', requires: 'arcade.hub' },
        bigger: { type: 'feature', requires: 'arcade.hub' },
        balance: { type: 'feature', requires: 'arcade.hub' },
        math: { type: 'feature', requires: 'arcade.hub' },
        sudoku: { type: 'feature', requires: 'arcade.hub' },
        demineur: { type: 'feature', requires: 'arcade.hub' },
        solitaire: { type: 'feature', requires: 'arcade.hub' },
        roulette: { type: 'feature', requires: 'arcade.hub' },
        pachinko: { type: 'feature', requires: 'arcade.hub' },
        blackjack: { type: 'feature', requires: 'arcade.hub' },
        holdem: { type: 'feature', requires: 'arcade.hub' },
        echecs: { type: 'feature', requires: 'arcade.hub' },
        gameOfLife: { type: 'feature', requires: 'arcade.hub' },
        escape: { type: 'feature', requires: 'arcade.hub' }
      },
      systems: {
        gacha: { type: 'page', pageId: 'gacha' },
        tableau: { type: 'page', pageId: 'tableau' },
        fusion: { type: 'page', pageId: 'fusion' },
        musique: { type: 'always' }
      }
    }
  },

  /**
   * Paramètres liés aux interactions et retours visuels.
   * - windowMs : fenêtre temporelle (ms) utilisée pour lisser l'intensité du bouton.
   * - maxClicksPerSecond : nombre de clics par seconde considéré comme 100% de puissance.
   * - starCount : nombre d'étoiles utilisées dans l'arrière-plan animé.
   * - ecoStarCount : nombre d'étoiles affichées lorsque le mode éco privilégie un rendu statique.
   */
  presentation: {
    clicks: {
      windowMs: 1000,
      maxClicksPerSecond: 18
    },
    starfield: {
      starCount: 60,
      ecoStarCount: 24
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
      decayIntervalSeconds: 120,
      decayAmountSeconds: 1,
      tickIntervalMs: 100
    }
  },

  /**
   * Configuration des mini-jeux d'arcade.
   * Chaque sous-objet contient l'équilibrage spécifique au jeu concerné
   * (vitesses, probabilités, textes, etc.) afin de centraliser les réglages.
   */
  arcade: {
    // Les configurations des mini-jeux Hold’em, Roulette, Pachinko, Balance, Math, Dice, Particules,
    // Échecs et Sudoku sont définies dans des fichiers JSON dédiés (`config/arcade/holdem.json`,
    // `config/arcade/roulette.json`, `config/arcade/pachinko.json`, `config/arcade/balance.json`,
    // `config/arcade/math.json`, `config/arcade/dice.json`, `config/arcade/particules.json`,
    // `config/arcade/echecs.json` et `config/arcade/sudoku.json`).
    /**
     * Paramètres du mini-jeu Hold’em.
     * - blind : montant unique utilisé comme blinde et relance de référence.
     * - dealerSpeedMs : cadence (en millisecondes) utilisée pour rythmer les animations du croupier.
     * - startingStack : réserve d’atomes attribuée à chaque participant lors d’un nouveau tableau.
     * - aiStack : pile de départ attribuée à chaque adversaire IA lors d’une nouvelle installation ou réintégration.
     * - opponentCount : fourchette du nombre d’adversaires IA présents à la table.
     * - aiProfiles : profils disponibles pour les adversaires (aggressivité, prudence, bluff).
     * - defaultAiProfile : identifiant du profil sélectionné par défaut.
     * - raiseGuidance : réglages de la suggestion de relance côté joueur (ratio sur le pot, stack et multiplicateur).
     * - maxRaisesPerRound : nombre maximum de relances autorisées par phase (valeur négative = illimité).
     * - growth : configuration de la montée des enjeux (facteur +10 % et plafond maximum).
     */
    holdem: ARCADE_HOLDEM_CONFIG,
    /**
     * Paramètres du mini-jeu Dice (Yams).
     * - diceCount : nombre de dés utilisés.
     * - faces : nombre de faces par dé.
     * - rollsPerTurn : lancers disponibles par manche.
     * - fullHouseScore / smallStraightScore / largeStraightScore / yahtzeeScore : valeurs fixes des combinaisons spéciales.
     * - threeKindBonus / fourKindBonus / twoPairBonus : bonus ajoutés aux figures concernées.
     * - bonusThreshold / bonusValue : palier et valeur du bonus de la section supérieure.
     */
    dice: ARCADE_DICE_CONFIG,
    // Paramètres du mini-jeu Échecs.
    echecs: ARCADE_ECHECS_CONFIG,
    // Paramètres du mini-jeu Balance (plateforme d'équilibre).
    balance: ARCADE_BALANCE_CONFIG,
    // Paramètres du mini-jeu Math (progression des opérations).
    math: ARCADE_MATH_CONFIG,
    // Paramètres du mini-jeu Solitaire (patience classique).
    solitaire: ARCADE_SOLITAIRE_CONFIG,
    particules: ARCADE_PARTICULES_CONFIG,
    // Paramètres du mini-jeu Sudoku.
    sudoku: ARCADE_SUDOKU_CONFIG,
    quantum2048: ARCADE_QUANTUM2048_CONFIG,
    demineur: ARCADE_DEMINEUR_CONFIG,
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
   * Paramètres du Big Bang.
   * - levelBonusStep : nombre de niveaux supplémentaires ajoutés au plafond des magasins après activation.
   *   Le Big Bang se débloque lorsque Doigt créateur et Cœur d’étoile atteignent leur niveau maximum.
   */
  bigBang: {
    levelBonusStep: 100
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
      successChance: 0.4,
      rewards: {
        apcFlat: 1000,
        apsFlat: 1000,
        /**
         * Chaque réussite augmente ces gains de 10 % par rapport à la précédente.
         */
        growthMultiplier: 1.2,
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
      successChance: 0.2,
      rewards: {
        apcFlat: 10000,
        apsFlat: 10000,
        /**
         * Chaque réussite augmente ces gains de 10 % par rapport à la précédente.
         */
        growthMultiplier: 1.2,
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
    bonusImages: {
      folder: 'Assets/Image/Gacha',
      images: Array.isArray(RAW_GACHA_BONUS_IMAGE_CONFIG?.images)
        ? RAW_GACHA_BONUS_IMAGE_CONFIG.images
        : []
    },
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
    /**
     * Paliers de déblocage progressif des collections disponibles dans le gacha.
     * - drawThreshold : nombre de tirages cumulés avant d'accéder au palier.
     * - allowedRarityCount : nombre de collections accessibles (null = toutes).
     */
    collectionUnlocks: [
      {
        drawThreshold: 0,
        allowedRarityCount: 2
      },
      {
        drawThreshold: 50,
        allowedRarityCount: 3
      },
      {
        drawThreshold: 150,
        allowedRarityCount: null
      }
    ],
    weeklyRarityWeights: {
      monday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 6,
        mythique: 0,
        irreel: 0
      },
      tuesday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 0,
        mythique: 6,
        irreel: 0
      },
      wednesday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 0,
        mythique: 0,
        irreel: 6
      },
      thursday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 6,
        mythique: 0,
        irreel: 0
      },
      friday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 0,
        mythique: 6,
        irreel: 0
      },
      saturday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 0,
        mythique: 0,
        irreel: 6
      },
      sunday: {
        commun: 80,
        essentiel: 7,
        stellaire: 7,
        singulier: 2,
        mythique: 2,
        irreel: 2
      }
    }
  },

  /**
   * Apparition de l'étoile à tickets sur la page principale.
   * - averageSpawnIntervalSeconds : intervalle moyen entre deux apparitions.
   * - minimumSpawnIntervalSeconds : durée minimale (après réduction) avant réapparition.
   * - clickReductionSeconds : réduction appliquée au délai moyen à chaque clic manuel.
   * - lifetimeSeconds : durée de vie maximale avant disparition manuelle (sans auto-collecte).
   * - speedPixelsPerSecond : vitesse de base utilisée pour le calcul de l'impulsion initiale.
   * - speedVariance : variation aléatoire relative appliquée à la vitesse horizontale (0.35 = ±35 %).
   * - spawnOffsetPixels : marge (en px) dans laquelle l'étoile peut dépasser les bords.
   * - size : taille (en pixels) du sprite.
   * - rewardTickets : nombre de tickets octroyés par clic.
   * - gravity : intensité de la gravité (en px/s²) appliquée pendant le rebond.
   * - bounceRestitution : restitution verticale lors du rebond au sol (0-1).
   * - wallRestitution : restitution lors du rebond sur les murs/ plafond (0-1).
   * - floorFriction : coefficient de friction appliqué horizontalement lors d'un contact au sol (0-1).
   * - launchVerticalSpeed : vitesse verticale initiale (en px/s) au moment de l'apparition.
   * - minHorizontalSpeed : vitesse horizontale minimale assurant un déplacement suffisant.
   * - horizontalSpeedMin / horizontalSpeedMax : bornes de la vitesse horizontale générée.
   */
  ticketStar: {
    averageSpawnIntervalSeconds: 600,
    minimumSpawnIntervalSeconds: 5,
    clickReductionSeconds: 0.2,
    lifetimeSeconds: 15,
    speedPixelsPerSecond: 90,
    speedVariance: 0.35,
    spawnOffsetPixels: 48,
    size: 92,
    rewardTickets: 1,
    gravity: 900,
    bounceRestitution: 0.86,
    wallRestitution: 0.82,
    floorFriction: 0.9,
    launchVerticalSpeed: 420,
    minHorizontalSpeed: 180,
    horizontalSpeedMin: 260,
    horizontalSpeedMax: 420
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
          uniqueClickAdd: 50,
          duplicateClickAdd: 1000,
          label: 'scripts.config.elementBonuses.essentiel.perCopy'
        },
        setBonus: [
          {
            clickAdd: 50000,
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
          uniqueClickAdd: 20000,
          duplicateClickAdd: 25000,
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
          uniqueClickAdd: 50000,
          uniqueAutoAdd: 50000,
          duplicateClickAdd: 100000,
          duplicateAutoAdd: 100000,
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
          frenzyBonus: 'scripts.config.elementBonuses.mythique.frenzyBonus',
          multiplier: COLLECTION_MULTIPLIER_LABEL_KEY
        },
        multiplier: {
          every: 10,
          increment: 1,
          targets: ['perClick', 'perSecond'],
          label: COLLECTION_MULTIPLIER_LABEL_KEY
        },
        ticketBonus: {
          uniqueReductionSeconds: 10,
          minIntervalSeconds: 30
        },
        offlineBonus: {
          baseMultiplier: 0.01,
          perDuplicate: 0.01,
          cap: 0.5
        },
        duplicateOverflow: {},
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
          every: 10,
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
  globalThis.DEVKIT_ENABLED = DEVKIT_ENABLED;
  globalThis.COLLECTION_SYSTEM_ENABLED = COLLECTION_SYSTEM_ENABLED;
  globalThis.INFO_SECTIONS_ENABLED = INFO_SECTIONS_ENABLED;
  globalThis.toggleDevkitFeatureEnabled = toggleDevkitFeatureEnabled;
  globalThis.toggleCollectionFeatureEnabled = toggleCollectionFeatureEnabled;
  globalThis.toggleInfoSectionsFeatureEnabled = toggleInfoSectionsFeatureEnabled;
}


















