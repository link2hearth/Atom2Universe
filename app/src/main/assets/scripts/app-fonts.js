const TEXT_FONT_DEFAULT = 'orbitron';
const TEXT_FONT_CHOICES = Object.freeze({
  orbitron: {
    id: 'orbitron',
    stack: "'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  alien: {
    id: 'alien',
    stack: "'Alien', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  chomsky: {
    id: 'chomsky',
    stack: "'Chomsky', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'coffee-spark': {
    id: 'coffee-spark',
    stack: "'Coffee Spark', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'creativo-regular': {
    id: 'creativo-regular',
    stack: "'Creativo Regular', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'darling-coffee': {
    id: 'darling-coffee',
    stack: "'Darling Coffee', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'designer-notes': {
    id: 'designer-notes',
    stack: "'Designer Notes', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'digittech16-regular': {
    id: 'digittech16-regular',
    stack: "'DigitTech16 Regular', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'little-days': {
    id: 'little-days',
    stack: "'Little Days', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'roboto-condensed': {
    id: 'roboto-condensed',
    stack: "'Roboto Condensed', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'slow-play': {
    id: 'slow-play',
    stack: "'Slow Play', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'super-chiby': {
    id: 'super-chiby',
    stack: "'Super Chiby', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'super-croissant': {
    id: 'super-croissant',
    stack: "'Super Croissant', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'super-mario-64': {
    id: 'super-mario-64',
    stack: "'Super Mario 64', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'super-meatball': {
    id: 'super-meatball',
    stack: "'Super Meatball', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  supermario256: {
    id: 'supermario256',
    stack: "'SuperMario256', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  alamain1: {
    id: 'alamain1',
    stack: "'Alamain 1', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'dealerplate-california': {
    id: 'dealerplate-california',
    stack: "'dealerplate california', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'to-japan': {
    id: 'to-japan',
    stack: "'To Japan', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  'science-gothic': {
    id: 'science-gothic',
    stack: "'Science Gothic', 'Orbitron', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  }
});

const DIGIT_FONT_STORAGE_KEY = 'atom2univers.options.digitFont';
const DIGIT_FONT_DEFAULT = 'orbitron';
const DIGIT_FONT_CHOICES = Object.freeze({
  orbitron: {
    id: 'orbitron',
    stack: "'Orbitron', sans-serif",
    compactStack: "'Orbitron', monospace"
  },
  alien: {
    id: 'alien',
    stack: "'Alien', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Alien', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  chomsky: {
    id: 'chomsky',
    stack: "'Chomsky', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Chomsky', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'coffee-spark': {
    id: 'coffee-spark',
    stack: "'Coffee Spark', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Coffee Spark', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'creativo-regular': {
    id: 'creativo-regular',
    stack: "'Creativo Regular', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Creativo Regular', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'darling-coffee': {
    id: 'darling-coffee',
    stack: "'Darling Coffee', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Darling Coffee', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'designer-notes': {
    id: 'designer-notes',
    stack: "'Designer Notes', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Designer Notes', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'digittech16-regular': {
    id: 'digittech16-regular',
    stack: "'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'little-days': {
    id: 'little-days',
    stack: "'Little Days', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Little Days', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'roboto-condensed': {
    id: 'roboto-condensed',
    stack: "'Roboto Condensed', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Roboto Condensed', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'slow-play': {
    id: 'slow-play',
    stack: "'Slow Play', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Slow Play', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'super-chiby': {
    id: 'super-chiby',
    stack: "'Super Chiby', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Super Chiby', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'super-croissant': {
    id: 'super-croissant',
    stack: "'Super Croissant', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Super Croissant', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'super-mario-64': {
    id: 'super-mario-64',
    stack: "'Super Mario 64', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Super Mario 64', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'super-meatball': {
    id: 'super-meatball',
    stack: "'Super Meatball', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Super Meatball', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  supermario256: {
    id: 'supermario256',
    stack: "'SuperMario256', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'SuperMario256', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  alamain1: {
    id: 'alamain1',
    stack: "'Alamain 1', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Alamain 1', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'dealerplate-california': {
    id: 'dealerplate-california',
    stack: "'dealerplate california', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'dealerplate california', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'to-japan': {
    id: 'to-japan',
    stack: "'To Japan', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'To Japan', 'DigitTech16 Regular', 'Orbitron', monospace"
  },
  'science-gothic': {
    id: 'science-gothic',
    stack: "'Science Gothic', 'DigitTech16 Regular', 'Orbitron', sans-serif",
    compactStack: "'Science Gothic', 'DigitTech16 Regular', 'Orbitron', monospace"
  }
});

const FONT_SCALE_CONFIG = (() => {
  const fallbackGroup = Object.freeze({ default: 1, overrides: Object.freeze({}) });
  const fallback = Object.freeze({
    text: fallbackGroup,
    digits: fallbackGroup
  });

  const rawConfig = GLOBAL_CONFIG && GLOBAL_CONFIG.ui && GLOBAL_CONFIG.ui.fonts;
  if (!rawConfig || typeof rawConfig !== 'object') {
    return fallback;
  }

  const normalizeGroup = group => {
    const resolved = {
      default: 1,
      overrides: {}
    };
    if (!group || typeof group !== 'object') {
      return resolved;
    }
    const defaultValue = Number(group.default);
    if (Number.isFinite(defaultValue) && defaultValue > 0) {
      resolved.default = defaultValue;
    }
    if (group.overrides && typeof group.overrides === 'object') {
      Object.entries(group.overrides).forEach(([key, value]) => {
        const id = typeof key === 'string' ? key.trim().toLowerCase() : '';
        const factor = Number(value);
        if (id && Number.isFinite(factor) && factor > 0) {
          resolved.overrides[id] = factor;
        }
      });
    }
    return resolved;
  };

  return Object.freeze({
    text: Object.freeze(normalizeGroup(rawConfig.text)),
    digits: Object.freeze(normalizeGroup(rawConfig.digits))
  });
})();

function resolveFontScaleMultiplier(group, fontId) {
  const normalizedId = typeof fontId === 'string' ? fontId.trim().toLowerCase() : '';
  const override = normalizedId ? group?.overrides?.[normalizedId] : undefined;
  const overrideValue = Number(override);
  if (Number.isFinite(overrideValue) && overrideValue > 0) {
    return overrideValue;
  }
  const fallbackValue = Number(group?.default);
  return Number.isFinite(fallbackValue) && fallbackValue > 0 ? fallbackValue : 1;
}
