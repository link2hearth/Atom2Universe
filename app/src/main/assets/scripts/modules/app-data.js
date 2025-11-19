(function initAppData(global) {
  const existingAppData = global.APP_DATA && typeof global.APP_DATA === 'object'
    ? global.APP_DATA
    : null;

  const translate = (() => {
    if (typeof global.t === 'function') {
      return global.t.bind(global);
    }
    if (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function') {
      return globalThis.t.bind(globalThis);
    }
    return (key, params) => {
      if (typeof key !== 'string' || !key) {
        return '';
      }
      if (!params || typeof params !== 'object') {
        return key;
      }
      return key.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
        const value = params[token];
        return value == null ? match : String(value);
      });
    };
  })();

  const FALLBACK_NUMBER_LOCALE = 'fr-FR';
  let resolvedNumberLocale = (() => {
    const navigatorLanguage = typeof global.navigator === 'object' && global.navigator
      ? global.navigator.language ?? global.navigator.userLanguage
      : null;
    if (navigatorLanguage && typeof navigatorLanguage === 'string' && navigatorLanguage.trim()) {
      return navigatorLanguage.trim();
    }
    const documentLanguage = global.document
      && global.document.documentElement
      && typeof global.document.documentElement.lang === 'string'
        ? global.document.documentElement.lang.trim()
        : '';
    if (documentLanguage) {
      return documentLanguage;
    }
    return FALLBACK_NUMBER_LOCALE;
  })();

  function formatLocaleNumber(value, options) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '0';
    }
    if (globalThis.i18n && typeof globalThis.i18n.formatNumber === 'function') {
      const formatted = globalThis.i18n.formatNumber(numeric, options);
      if (formatted) {
        return formatted;
      }
    }
    const locale = globalThis.i18n && typeof globalThis.i18n.getCurrentLocale === 'function'
      ? globalThis.i18n.getCurrentLocale()
      : resolvedNumberLocale;
    try {
      return numeric.toLocaleString(locale, options);
    } catch (error) {
      return numeric.toLocaleString(FALLBACK_NUMBER_LOCALE, options);
    }
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('i18n:languagechange', event => {
      if (event?.detail?.locale) {
        resolvedNumberLocale = event.detail.locale;
      } else if (globalThis.i18n && typeof globalThis.i18n.getCurrentLocale === 'function') {
        resolvedNumberLocale = globalThis.i18n.getCurrentLocale();
      }
    });
  }

  const DEFAULT_ATOM_SCALE_TROPHY_SPECS = [
    {
      id: 'scaleHumanCell',
      targetText: '10^14',
      nameKey: 'scripts.appData.atomScale.trophies.scaleHumanCell.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleHumanCell.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 14 }
    },
    {
      id: 'scaleSandGrain',
      targetText: '10^19',
      nameKey: 'scripts.appData.atomScale.trophies.scaleSandGrain.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleSandGrain.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 19 }
    },
    {
      id: 'scaleAnt',
      targetText: '10^20',
      nameKey: 'scripts.appData.atomScale.trophies.scaleAnt.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleAnt.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 20 }
    },
    {
      id: 'scaleWaterDrop',
      targetText: '5 × 10^21',
      nameKey: 'scripts.appData.atomScale.trophies.scaleWaterDrop.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleWaterDrop.flavor',
      amount: { type: 'layer0', mantissa: 5, exponent: 21 }
    },
    {
      id: 'scalePaperclip',
      targetText: '10^22',
      nameKey: 'scripts.appData.atomScale.trophies.scalePaperclip.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scalePaperclip.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 22 }
    },
    {
      id: 'scaleCoin',
      targetText: '10^23',
      nameKey: 'scripts.appData.atomScale.trophies.scaleCoin.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleCoin.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 23 }
    },
    {
      id: 'scaleApple',
      targetText: '10^25',
      nameKey: 'scripts.appData.atomScale.trophies.scaleApple.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleApple.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 25 }
    },
    {
      id: 'scaleSmartphone',
      targetText: '3 × 10^25',
      nameKey: 'scripts.appData.atomScale.trophies.scaleSmartphone.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleSmartphone.flavor',
      amount: { type: 'layer0', mantissa: 3, exponent: 25 }
    },
    {
      id: 'scaleWaterLitre',
      targetText: '10^26',
      nameKey: 'scripts.appData.atomScale.trophies.scaleWaterLitre.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleWaterLitre.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 26 }
    },
    {
      id: 'scaleHuman',
      targetText: '7 × 10^27',
      nameKey: 'scripts.appData.atomScale.trophies.scaleHuman.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleHuman.flavor',
      amount: { type: 'layer0', mantissa: 7, exponent: 27 }
    },
    {
      id: 'scalePiano',
      targetText: '10^29',
      nameKey: 'scripts.appData.atomScale.trophies.scalePiano.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scalePiano.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 29 }
    },
    {
      id: 'scaleCar',
      targetText: '10^30',
      nameKey: 'scripts.appData.atomScale.trophies.scaleCar.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleCar.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 30 }
    },
    {
      id: 'scaleElephant',
      targetText: '3 × 10^31',
      nameKey: 'scripts.appData.atomScale.trophies.scaleElephant.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleElephant.flavor',
      amount: { type: 'layer0', mantissa: 3, exponent: 31 }
    },
    {
      id: 'scaleBoeing747',
      targetText: '10^33',
      nameKey: 'scripts.appData.atomScale.trophies.scaleBoeing747.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleBoeing747.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 33 }
    },
    {
      id: 'scalePyramid',
      targetText: '2 × 10^35',
      nameKey: 'scripts.appData.atomScale.trophies.scalePyramid.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scalePyramid.flavor',
      amount: { type: 'layer0', mantissa: 2, exponent: 35 }
    },
    {
      id: 'scaleAtmosphere',
      targetText: '2 × 10^44',
      nameKey: 'scripts.appData.atomScale.trophies.scaleAtmosphere.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleAtmosphere.flavor',
      amount: { type: 'layer0', mantissa: 2, exponent: 44 }
    },
    {
      id: 'scaleOceans',
      targetText: '10^47',
      nameKey: 'scripts.appData.atomScale.trophies.scaleOceans.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleOceans.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 47 }
    },
    {
      id: 'scaleEarth',
      targetText: '10^50',
      nameKey: 'scripts.appData.atomScale.trophies.scaleEarth.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleEarth.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 50 }
    },
    {
      id: 'scaleSun',
      targetText: '10^57',
      nameKey: 'scripts.appData.atomScale.trophies.scaleSun.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleSun.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 57 }
    },
    {
      id: 'scaleMilkyWay',
      targetText: '10^69',
      nameKey: 'scripts.appData.atomScale.trophies.scaleMilkyWay.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleMilkyWay.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 69 }
    },
    {
      id: 'scaleLocalGroup',
      targetText: '10^71',
      nameKey: 'scripts.appData.atomScale.trophies.scaleLocalGroup.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleLocalGroup.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 71 }
    },
    {
      id: 'scaleVirgoCluster',
      targetText: '10^74',
      nameKey: 'scripts.appData.atomScale.trophies.scaleVirgoCluster.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleVirgoCluster.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 74 }
    },
    {
      id: 'scaleObservableUniverse',
      targetText: '10^80',
      nameKey: 'scripts.appData.atomScale.trophies.scaleObservableUniverse.name',
      flavorKey: 'scripts.appData.atomScale.trophies.scaleObservableUniverse.flavor',
      amount: { type: 'layer0', mantissa: 1, exponent: 80 }
    }
  ];

  const DEFAULT_ATOM_SCALE_TROPHY_DATA = DEFAULT_ATOM_SCALE_TROPHY_SPECS.map(({ nameKey, flavorKey, ...entry }) => ({
    ...entry,
    name: translate(nameKey),
    flavor: translate(flavorKey)
  }));

  const ATOM_SCALE_TROPHY_DATA = Array.isArray(global.ATOM_SCALE_TROPHY_DATA)
    ? global.ATOM_SCALE_TROPHY_DATA
    : DEFAULT_ATOM_SCALE_TROPHY_DATA;

  function formatAtomScaleBonusValue(value) {
    if (!Number.isFinite(value)) {
      return '0';
    }
    const roundedInteger = Math.round(value);
    if (Math.abs(value - roundedInteger) <= 1e-9) {
      return formatLocaleNumber(roundedInteger);
    }
    return formatLocaleNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function createFallbackAtomScaleTrophies() {
    const bonusPerTrophy = 2;
    return ATOM_SCALE_TROPHY_DATA.map((entry, index) => {
      const displayBonus = formatAtomScaleBonusValue(bonusPerTrophy);
      const displayTotal = formatAtomScaleBonusValue(1 + bonusPerTrophy);
      const description = translate('scripts.appData.atomScale.trophies.description', {
        target: entry.targetText,
        flavor: entry.flavor
      });
      return {
        id: entry.id,
        name: entry.name,
        description,
        condition: {
          type: 'lifetimeAtoms',
          amount: entry.amount
        },
        reward: {
          trophyMultiplierAdd: bonusPerTrophy,
          description: translate('scripts.appData.atomScale.trophies.reward', {
            bonus: displayBonus,
            total: displayTotal
          })
        },
        order: index
      };
    });
  }

  const FALLBACK_MILESTONES = [
    {
      amount: 100,
      text: translate('scripts.appData.milestones.autoSynthesis', {
        amount: formatLocaleNumber(100)
      })
    },
    {
      amount: 1_000,
      text: translate('scripts.appData.milestones.quantumGloves', {
        amount: formatLocaleNumber(1_000)
      })
    },
    {
      amount: 1_000_000,
      text: translate('scripts.appData.milestones.overclock', {
        amount: formatLocaleNumber(1_000_000)
      })
    },
    {
      amount: { type: 'layer0', mantissa: 1, exponent: 8 },
      text: translate('scripts.appData.milestones.nextEra', { amount: '10^8' })
    }
  ];

  const FALLBACK_TROPHIES = [
    ...createFallbackAtomScaleTrophies(),
    {
      id: 'millionAtoms',
      name: translate('scripts.appData.trophies.millionAtoms.name'),
      description: translate('scripts.appData.trophies.millionAtoms.description'),
      condition: {
        type: 'lifetimeAtoms',
        amount: { type: 'number', value: 1_000_000 }
      },
      reward: {
        trophyMultiplierAdd: 0.5,
        description: translate('scripts.appData.trophies.millionAtoms.reward', {
          bonus: formatAtomScaleBonusValue(0.5),
          total: formatAtomScaleBonusValue(1.5)
        })
      },
      order: 1000
    },
    {
      id: 'frenzyCollector',
      name: translate('scripts.appData.trophies.frenzyCollector.name'),
      description: translate('scripts.appData.trophies.frenzyCollector.description'),
      condition: {
        type: 'frenzyTotal',
        amount: 100
      },
      reward: {
        frenzyMaxStacks: 2,
        description: translate('scripts.appData.trophies.frenzyCollector.reward')
      },
      order: 1010
    },
    {
      id: 'frenzyMaster',
      name: translate('scripts.appData.trophies.frenzyMaster.name'),
      description: translate('scripts.appData.trophies.frenzyMaster.description'),
      condition: {
        type: 'frenzyTotal',
        amount: 1_000
      },
      reward: {
        frenzyMaxStacks: 3,
        multiplier: { global: 1.05 },
        description: translate('scripts.appData.trophies.frenzyMaster.reward', {
          multiplier: formatLocaleNumber(1.05, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        })
      },
      order: 1020
    }
  ];

  const DEFAULT_MUSIC_SUPPORTED_EXTENSIONS = Object.freeze(['mp3', 'ogg', 'wav', 'webm', 'm4a']);

  const DEFAULT_MUSIC_FALLBACK_TRACKS = Object.freeze([]);

  function sanitizeMusicExtensions(raw) {
    const candidates = Array.isArray(raw)
      ? raw
      : raw != null
        ? [raw]
        : [];
    const seen = new Set();
    const sanitized = [];
    candidates.forEach(entry => {
      let value = null;
      if (typeof entry === 'string') {
        value = entry;
      } else if (entry && typeof entry === 'object') {
        value = entry.extension ?? entry.ext ?? entry.value ?? null;
      }
      if (!value) {
        return;
      }
      const normalized = String(value)
        .trim()
        .toLowerCase()
        .replace(/^[.]+/, '');
      if (!normalized || seen.has(normalized)) {
        return;
      }
      seen.add(normalized);
      sanitized.push(normalized);
    });
    if (!sanitized.length) {
      return [...DEFAULT_MUSIC_SUPPORTED_EXTENSIONS];
    }
    return sanitized;
  }

  function sanitizeFallbackTracks(raw) {
    const candidates = Array.isArray(raw)
      ? raw
      : raw != null
        ? [raw]
        : [];
    const sanitized = [];
    candidates.forEach(entry => {
      let value = null;
      if (typeof entry === 'string') {
        value = entry;
      } else if (entry && typeof entry === 'object') {
        value = entry.path
          ?? entry.src
          ?? entry.url
          ?? entry.file
          ?? entry.filename
          ?? entry.name
          ?? null;
      }
      if (!value) {
        return;
      }
      const normalized = String(value).trim();
      if (!normalized) {
        return;
      }
      sanitized.push(normalized);
    });
    if (!sanitized.length) {
      return [...DEFAULT_MUSIC_FALLBACK_TRACKS];
    }
    return sanitized;
  }

  function parseRgbColor(value) {
    if (Array.isArray(value)) {
      const color = [];
      for (let i = 0; i < 3; i += 1) {
        const numeric = Number(value[i]);
        if (!Number.isFinite(numeric)) {
          return null;
        }
        color.push(Math.max(0, Math.min(255, Math.round(numeric))));
      }
      return color;
    }
    if (typeof value === 'string') {
      let hex = value.trim();
      if (!hex) {
        return null;
      }
      if (hex.startsWith('#')) {
        hex = hex.slice(1);
      }
      if (hex.length === 3) {
        hex = hex
          .split('')
          .map(char => `${char}${char}`)
          .join('');
      }
      if (hex.length !== 6 || /[^0-9a-f]/i.test(hex)) {
        return null;
      }
      const color = [
        parseInt(hex.slice(0, 2), 16),
        parseInt(hex.slice(2, 4), 16),
        parseInt(hex.slice(4, 6), 16)
      ];
      return color;
    }
    return null;
  }

  const DEFAULT_GLOW_STOPS = Object.freeze([
    { stop: 0, color: [248, 226, 158] },
    { stop: 0.35, color: [255, 202, 112] },
    { stop: 0.65, color: [255, 130, 54] },
    { stop: 1, color: [255, 46, 18] }
  ]);

  function sanitizeGlowStops(raw) {
    const base = Array.isArray(raw) ? raw : [];
    const sanitized = [];
    base.forEach(entry => {
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const stopValue = Number(entry.stop ?? entry.position ?? entry.offset ?? entry.value);
      if (!Number.isFinite(stopValue)) {
        return;
      }
      const color = parseRgbColor(entry.color ?? entry.rgb ?? entry.hex ?? entry.value);
      if (!color) {
        return;
      }
      sanitized.push({
        stop: Math.max(0, Math.min(1, stopValue)),
        color
      });
    });
    if (sanitized.length < 2) {
      return DEFAULT_GLOW_STOPS.map(entry => ({ stop: entry.stop, color: [...entry.color] }));
    }
    sanitized.sort((a, b) => a.stop - b.stop);
    return sanitized;
  }

  const DEFAULT_MAIN_ATOM_IMAGES = Object.freeze([
    'Assets/Image/Atom.png',
    'Assets/Image/Atom0.png',
    'Assets/Image/Atom1.png',
    'Assets/Image/Atom2.png',
    'Assets/Image/Atom3.png',
    'Assets/Image/Atom4.png',
    'Assets/Image/Atom5.png',
    'Assets/Image/Atom6.png',
    'Assets/Image/Atom7.png',
    'Assets/Image/Atom8.png',
    'Assets/Image/Atom9.png',
    'Assets/Image/Atom10.png',
    'Assets/Image/Atom11.png'
  ]);

  const DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES = Object.freeze([
    'Assets/Image/Atom2/Atom2 (1).png',
    'Assets/Image/Atom2/Atom2 (2).png',
    'Assets/Image/Atom2/Atom2 (3).png',
    'Assets/Image/Atom2/Atom2 (4).png',
    'Assets/Image/Atom2/Atom2 (5).png',
    'Assets/Image/Atom2/Atom2 (6).png',
    'Assets/Image/Atom2/Atom2 (7).png',
    'Assets/Image/Atom2/Atom2 (8).png',
    'Assets/Image/Atom2/Atom2 (9).png',
    'Assets/Image/Atom2/Atom2 (10).png',
    'Assets/Image/Atom2/Atom2 (11).png',
    'Assets/Image/Atom2/Atom2 (12).png',
    'Assets/Image/Atom2/Atom2 (13).png',
    'Assets/Image/Atom2/Atom2 (14).png',
    'Assets/Image/Atom2/Atom2 (15).png',
    'Assets/Image/Atom2/Atom2 (16).png',
    'Assets/Image/Atom2/Atom2 (17).png',
    'Assets/Image/Atom2/Atom2 (18).png',
    'Assets/Image/Atom2/Atom2 (19).png',
    'Assets/Image/Atom2/Atom2 (20).png',
    'Assets/Image/Atom2/Atom2 (21).png',
    'Assets/Image/Atom2/Atom2 (22).png',
    'Assets/Image/Atom2/Atom2 (23).png',
    'Assets/Image/Atom2/Atom2 (24).png',
    'Assets/Image/Atom2/Atom2 (25).png',
    'Assets/Image/Atom2/Atom2 (26).png',
    'Assets/Image/Atom2/Atom2 (27).png',
    'Assets/Image/Atom2/Atom2 (28).png',
    'Assets/Image/Atom2/Atom2 (29).png',
    'Assets/Image/Atom2/Atom2 (30).png',
    'Assets/Image/Atom2/Atom2 (31).png',
    'Assets/Image/Atom2/Atom2 (32).png',
    'Assets/Image/Atom2/Atom2 (33).png',
    'Assets/Image/Atom2/Atom2 (34).png',
    'Assets/Image/Atom2/Atom2 (35).png',
    'Assets/Image/Atom2/Atom2 (36).png',
    'Assets/Image/Atom2/Atom2 (37).png',
    'Assets/Image/Atom2/Atom2 (38).png',
    'Assets/Image/Atom2/Atom2 (39).png',
    'Assets/Image/Atom2/Atom2 (40).png',
    'Assets/Image/Atom2/Atom2 (41).png'
  ]);

  const DEFAULT_CRIT_ATOM_IMAGES = Object.freeze([
    'Assets/Image/Atom low/Atom.png',
    'Assets/Image/Atom low/Atom0.png',
    'Assets/Image/Atom low/Atom1.png',
    'Assets/Image/Atom low/Atom2.png',
    'Assets/Image/Atom low/Atom3.png',
    'Assets/Image/Atom low/Atom4.png',
    'Assets/Image/Atom low/Atom5.png',
    'Assets/Image/Atom low/Atom6.png',
    'Assets/Image/Atom low/Atom7.png',
    'Assets/Image/Atom low/Atom8.png',
    'Assets/Image/Atom low/Atom9.png',
    'Assets/Image/Atom low/Atom10.png',
    'Assets/Image/Atom low/Atom11.png'
  ]);

  function sanitizeAtomImages(raw, fallbackImages) {
    const candidates = Array.isArray(raw) ? raw : [];
    const sanitized = [];
    candidates.forEach(entry => {
      const value = typeof entry === 'string'
        ? entry
        : (entry && typeof entry === 'object'
          ? entry.src ?? entry.url ?? entry.path ?? entry.href ?? null
          : null);
      if (!value) {
        return;
      }
      const normalized = String(value).trim();
      if (!normalized || sanitized.includes(normalized)) {
        return;
      }
      sanitized.push(normalized);
    });
    if (!sanitized.length) {
      return Array.isArray(fallbackImages) ? [...fallbackImages] : [];
    }
    return sanitized;
  }

  const MUSIC_SUPPORTED_EXTENSIONS = sanitizeMusicExtensions(
    existingAppData?.MUSIC_SUPPORTED_EXTENSIONS ?? global.MUSIC_SUPPORTED_EXTENSIONS
  );

  const MUSIC_FALLBACK_TRACKS = sanitizeFallbackTracks(
    existingAppData?.MUSIC_FALLBACK_TRACKS ?? global.MUSIC_FALLBACK_TRACKS
  );

  const GLOW_STOPS = sanitizeGlowStops(existingAppData?.GLOW_STOPS ?? global.GLOW_STOPS);

  const MAIN_ATOM_IMAGES = sanitizeAtomImages(
    existingAppData?.MAIN_ATOM_IMAGES ?? global.MAIN_ATOM_IMAGES,
    DEFAULT_MAIN_ATOM_IMAGES
  );

  const ALTERNATE_MAIN_ATOM_IMAGES = sanitizeAtomImages(
    existingAppData?.ALTERNATE_MAIN_ATOM_IMAGES ?? global.ALTERNATE_MAIN_ATOM_IMAGES,
    DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES
  );

  const CRIT_ATOM_IMAGES = sanitizeAtomImages(
    existingAppData?.CRIT_ATOM_IMAGES ?? global.CRIT_ATOM_IMAGES,
    DEFAULT_CRIT_ATOM_IMAGES
  );

  const appData = existingAppData ? { ...existingAppData } : {};
  appData.DEFAULT_ATOM_SCALE_TROPHY_DATA = DEFAULT_ATOM_SCALE_TROPHY_DATA;
  appData.ATOM_SCALE_TROPHY_DATA = ATOM_SCALE_TROPHY_DATA;
  appData.FALLBACK_MILESTONES = FALLBACK_MILESTONES;
  appData.FALLBACK_TROPHIES = FALLBACK_TROPHIES;
  appData.DEFAULT_MUSIC_SUPPORTED_EXTENSIONS = [...DEFAULT_MUSIC_SUPPORTED_EXTENSIONS];
  appData.MUSIC_SUPPORTED_EXTENSIONS = [...MUSIC_SUPPORTED_EXTENSIONS];
  appData.DEFAULT_MUSIC_FALLBACK_TRACKS = [...DEFAULT_MUSIC_FALLBACK_TRACKS];
  appData.MUSIC_FALLBACK_TRACKS = [...MUSIC_FALLBACK_TRACKS];
  appData.DEFAULT_GLOW_STOPS = DEFAULT_GLOW_STOPS.map(entry => ({ stop: entry.stop, color: [...entry.color] }));
  appData.GLOW_STOPS = GLOW_STOPS.map(entry => ({ stop: entry.stop, color: [...entry.color] }));
  appData.DEFAULT_MAIN_ATOM_IMAGES = [...DEFAULT_MAIN_ATOM_IMAGES];
  appData.MAIN_ATOM_IMAGES = [...MAIN_ATOM_IMAGES];
  appData.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES = [...DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES];
  appData.ALTERNATE_MAIN_ATOM_IMAGES = [...ALTERNATE_MAIN_ATOM_IMAGES];
  appData.DEFAULT_CRIT_ATOM_IMAGES = [...DEFAULT_CRIT_ATOM_IMAGES];
  appData.CRIT_ATOM_IMAGES = [...CRIT_ATOM_IMAGES];
  global.APP_DATA = appData;
})(typeof globalThis !== 'undefined' ? globalThis : window);
