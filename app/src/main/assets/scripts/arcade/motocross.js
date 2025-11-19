(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const CONFIG_PATH = 'config/arcade/motocross.json';
  const DEFAULT_CONFIG = Object.freeze({
    steepSlopeGrip: Object.freeze({
      angleThresholdDeg: 30,
      maxAngleDeg: 80,
      normalForceBoost: 2800,
      correctionBoost: 8,
      torqueAssist: 18000,
      angularDampingMultiplier: 0.6
    }),
    rewards: Object.freeze({
      gacha: Object.freeze({
        intervalMeters: 500,
        ticketAmount: 1
      })
    })
  });

  const DEFAULT_DIFFICULTY = 'easy';
  const CHECKPOINT_INTERVAL = 960;
  const SLOPE_STEP = 0.12;
  const ELEVATION_LIMIT = 520;
  const INITIAL_BLOCK_COUNT = 14;
  const EXTEND_BLOCK_COUNT = 6;
  const TRACK_AHEAD_BUFFER = 3600;

  const TRACK_LENGTH_MULTIPLIER = 2.6;
  const TRACK_HEIGHT_MULTIPLIER = 3;
  const PROFILE_AMPLITUDE_MULTIPLIER = 1.45;
  const TRACK_CURVE_SUBDIVISIONS = 8;
  const TRACK_MIN_CURVE_STEP = 4;

  const BACKGROUND_OPTIONS = [
    'Assets/sprites/city_background_night.png',
    'Assets/sprites/city_background_sunset.png'
  ];
  const BACKGROUND_SCROLL_RATIO = 0.22;
  const BIKE_SPRITE_SOURCE = 'Assets/sprites/Moto.png';
  const BIKE_SPRITE_ANCHORS = Object.freeze({
    backWheel: { x: 0.18, y: 0.82 },
    frontWheel: { x: 0.82, y: 0.82 }
  });
  const PHYSICS_STEP = 1 / 120;
  const MAX_FRAME_STEP = 1 / 30;
  const MASS = 60;
  const GRAVITY = 1200;
  const BIKE_SCALE = 0.6;
  const CHASSIS_WIDTH = 108 * BIKE_SCALE;
  const CHASSIS_HEIGHT = 32 * BIKE_SCALE;
  const WHEEL_BASE = 92 * BIKE_SCALE;
  const WHEEL_RADIUS = 18 * BIKE_SCALE;
  const WHEEL_VERTICAL_OFFSET = 20 * BIKE_SCALE;
  const BIKE_INERTIA = (MASS * (CHASSIS_WIDTH * CHASSIS_WIDTH + CHASSIS_HEIGHT * CHASSIS_HEIGHT)) / 12;
  const ENGINE_FORCE = 24800;
  const BRAKE_FORCE = ENGINE_FORCE * 0.9;
  const GROUND_BRAKE_MULTIPLIER = 1.35;
  const BOOST_BASE_DELAY = 1;
  const BOOST_STEP_DURATION = 0.1;
  const BOOST_STEP_RATE = 0.1;
  const BOOST_MAX_MULTIPLIER = 3;
  const BOOST_MAX_STEPS = Math.ceil(Math.log(BOOST_MAX_MULTIPLIER) / Math.log(1 + BOOST_STEP_RATE));
  const TILT_TORQUE = 31200;
  const SPRING_STIFFNESS = 4200;
  const SPRING_DAMPING = 180;
  const FRICTION_DAMPING = 36;
  const FRICTION_COEFFICIENT = 1.25;
  const LINEAR_DAMPING = 0.05;
  const ANGULAR_DAMPING = 0.12;
  const NORMAL_CORRECTION_FACTOR = 0.7;
  const AIR_ROTATION_MAX_SPEED = Math.PI * 3;
  const AIR_ROTATION_ACCEL = AIR_ROTATION_MAX_SPEED * 6;
  const CAMERA_LOOK_AHEAD = 0.24;
  const CAMERA_OFFSET_Y = 180;
  const CAMERA_SMOOTH = 6.5;
  const CAMERA_ZOOM_SMOOTH = 5.2;
  const FALL_EXTRA_MARGIN = 480;
  const CAMERA_VERTICAL_ANCHOR = 0.55;
  const CAMERA_ZOOM_MIN = 0.48;
  const CAMERA_ZOOM_MAX = 1;
  const CAMERA_TRACK_WINDOW_BEHIND = 420;
  const CAMERA_TRACK_WINDOW_AHEAD = 960;
  const CAMERA_TOP_MARGIN = 140;
  const CAMERA_BOTTOM_MARGIN = 220;
  const GROUND_PROXIMITY_THRESHOLD = 10;
  const UPSIDE_DOWN_WHEEL_CLEARANCE = 64;
  const UPSIDE_DOWN_CENTER_CLEARANCE = CHASSIS_HEIGHT * 3;

  const SPEED_DISPLAY_DIVISOR = 5;
  const MOTOCROSS_PROGRESS_KEY = 'motocross';
  const MOTOCROSS_AUTOSAVE_VERSION = 1;

  function getGlobalGameState() {
    if (typeof globalThis !== 'undefined') {
      if (globalThis.atom2universGameState && typeof globalThis.atom2universGameState === 'object') {
        return globalThis.atom2universGameState;
      }
      if (globalThis.gameState && typeof globalThis.gameState === 'object') {
        return globalThis.gameState;
      }
    }
    if (typeof window !== 'undefined') {
      if (window.atom2universGameState && typeof window.atom2universGameState === 'object') {
        return window.atom2universGameState;
      }
      if (window.gameState && typeof window.gameState === 'object') {
        return window.gameState;
      }
    }
    return null;
  }

  function getSaveGameFunction() {
    if (typeof saveGame === 'function') {
      return saveGame;
    }
    if (typeof globalThis !== 'undefined' && typeof globalThis.saveGame === 'function') {
      return globalThis.saveGame;
    }
    if (typeof window !== 'undefined' && typeof window.saveGame === 'function') {
      return window.saveGame;
    }
    return null;
  }

  function getArcadeAutosaveApi() {
    if (typeof window === 'undefined') {
      return null;
    }
    const api = window.ArcadeAutosave;
    if (!api || typeof api.get !== 'function' || typeof api.set !== 'function') {
      return null;
    }
    return api;
  }

  function findMotocrossEntryKey(entries) {
    if (!entries || typeof entries !== 'object') {
      return null;
    }
    if (entries[MOTOCROSS_PROGRESS_KEY]) {
      return MOTOCROSS_PROGRESS_KEY;
    }
    const fallbackKey = Object.keys(entries).find(key => (
      typeof key === 'string' && key.toLowerCase() === MOTOCROSS_PROGRESS_KEY
    ));
    return fallbackKey || null;
  }

  function ensureMotocrossProgressEntry(globalState) {
    if (!globalState || typeof globalState !== 'object') {
      return null;
    }
    if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
      globalState.arcadeProgress = { version: 1, entries: {} };
    }
    const progress = globalState.arcadeProgress;
    if (!progress.entries || typeof progress.entries !== 'object') {
      progress.entries = {};
    }
    const entries = progress.entries;
    const key = findMotocrossEntryKey(entries);
    let entry = key ? entries[key] : null;
    if (!entry || typeof entry !== 'object') {
      entry = { state: {}, updatedAt: Date.now() };
    }
    if (!entry.state || typeof entry.state !== 'object') {
      entry.state = {};
    }
    entries[MOTOCROSS_PROGRESS_KEY] = entry;
    return entry;
  }

  function readMotocrossAutosaveSnapshot() {
    const api = getArcadeAutosaveApi();
    if (!api) {
      return null;
    }
    try {
      const entry = api.get(MOTOCROSS_PROGRESS_KEY);
      if (entry && typeof entry === 'object') {
        return entry;
      }
    } catch (error) {
      // Ignore autosave read errors.
    }
    return null;
  }

  function normalizeMotocrossRecord(record) {
    if (!record || typeof record !== 'object') {
      return { bestDistance: 0, bestSpeed: 0 };
    }
    return {
      bestDistance: record.bestDistance ?? record.maxDistance ?? record.distance ?? 0,
      bestSpeed: record.bestSpeed ?? record.speed ?? record.topSpeed ?? 0
    };
  }

  function mergeMotocrossSnapshots(primary, secondary) {
    if (!primary && !secondary) {
      return null;
    }
    if (!primary) {
      return { ...secondary };
    }
    if (!secondary) {
      return { ...primary };
    }
    return {
      bestDistance: Math.max(Number(primary.bestDistance) || 0, Number(secondary.bestDistance) || 0),
      bestSpeed: Math.max(Number(primary.bestSpeed) || 0, Number(secondary.bestSpeed) || 0)
    };
  }

  function readMotocrossRecordSnapshot() {
    const autosaveSnapshot = normalizeMotocrossRecord(readMotocrossAutosaveSnapshot());
    const globalState = getGlobalGameState();
    let progressSnapshot = null;
    if (globalState && globalState.arcadeProgress && typeof globalState.arcadeProgress === 'object') {
      const entries = globalState.arcadeProgress.entries && typeof globalState.arcadeProgress.entries === 'object'
        ? globalState.arcadeProgress.entries
        : null;
      if (entries) {
        const key = findMotocrossEntryKey(entries);
        if (key) {
          const entry = entries[key];
          if (entry && typeof entry === 'object') {
            const state = entry.state && typeof entry.state === 'object' ? entry.state : entry;
            progressSnapshot = normalizeMotocrossRecord(state);
          }
        }
      }
    }
    return mergeMotocrossSnapshots(autosaveSnapshot, progressSnapshot);
  }

  const translate = (() => {
    if (typeof window !== 'undefined' && typeof window.t === 'function') {
      const translator = window.t.bind(window);
      return (key, fallback, params) => {
        if (typeof key !== 'string' || !key.trim()) {
          return fallback || '';
        }
        const normalizedKey = key.trim();
        const translated = translator(normalizedKey, params);
        if (typeof translated === 'string' && translated.trim()) {
          return translated;
        }
        return fallback || normalizedKey;
      };
    }
    return (key, fallback, params) => {
      if (!fallback) {
        if (!key) {
          return '';
        }
        return params ? formatTemplate(key, params) : key;
      }
      return params ? formatTemplate(fallback, params) : fallback;
    };
  })();

  const numberFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, {
      maximumFractionDigits: 0,
      minimumFractionDigits: 0,
      useGrouping: false
    })
    : null;

  const distanceFormatter = typeof Intl !== 'undefined' && Intl.NumberFormat
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 })
    : null;

  function formatTemplate(template, params) {
    if (!template || typeof template !== 'string') {
      return '';
    }
    if (!params || typeof params !== 'object') {
      return template;
    }
    return template.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
      const value = params[token];
      return value == null ? match : String(value);
    });
  }

  function formatSpeed(value) {
    const numeric = Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
    const divisor = SPEED_DISPLAY_DIVISOR > 0 ? SPEED_DISPLAY_DIVISOR : 1;
    const scaled = Math.max(0, Math.round(numeric / divisor));
    if (numberFormatter) {
      return numberFormatter.format(scaled);
    }
    return scaled.toString();
  }

  function formatDistance(value) {
    const numeric = Number.isFinite(value) ? Math.max(0, value) : 0;
    if (distanceFormatter) {
      return distanceFormatter.format(numeric);
    }
    return numeric.toFixed(0);
  }

  function getEmptyRecordValue() {
    return translate('scripts.info.progress.motocross.empty', '—');
  }

  function formatTicketCount(value) {
    const numeric = Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
    if (distanceFormatter) {
      return distanceFormatter.format(numeric);
    }
    return numeric.toString();
  }

  function formatBlockName(entry) {
    if (!entry || typeof entry.id !== 'string') {
      return '#';
    }
    const tokens = entry.id
      .split('/')
      .filter(Boolean)
      .map(part => part.replace(/[-_]+/g, ' '))
      .map(part => {
        if (!part) {
          return part;
        }
        return part.charAt(0).toUpperCase() + part.slice(1);
      });
    return tokens.join(' › ') || entry.id;
  }

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function lerp(current, target, factor) {
    return current + (target - current) * factor;
  }

  function clampNumber(value, min, max, fallback) {
    const numeric = Number(value);
    if (Number.isFinite(numeric)) {
      return clamp(numeric, min, max);
    }
    if (Number.isFinite(fallback)) {
      return clamp(fallback, min, max);
    }
    return clamp(min, min, max);
  }

  function degreesToRadians(degrees) {
    return (degrees * Math.PI) / 180;
  }

  function catmullRomScalar(p0, p1, p2, p3, t) {
    const t2 = t * t;
    const t3 = t2 * t;
    return 0.5 * (
      (2 * p1)
      + (-p0 + p2) * t
      + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
      + (-p0 + 3 * p1 - 3 * p2 + p3) * t3
    );
  }

  function smoothPolyline(points, subdivisions) {
    if (!Array.isArray(points) || points.length < 2) {
      return [[0, 0], [200 * TRACK_LENGTH_MULTIPLIER, 0]];
    }
    const result = [];
    const segmentCount = points.length - 1;
    const steps = Math.max(1, Math.floor(subdivisions));
    for (let i = 0; i < segmentCount; i += 1) {
      const p0 = points[Math.max(0, i - 1)];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = points[Math.min(points.length - 1, i + 2)];
      if (result.length === 0) {
        result.push([p1[0], p1[1]]);
      }
      const samples = steps + 1;
      for (let s = 1; s <= samples; s += 1) {
        if (s === samples) {
          result.push([p2[0], p2[1]]);
          continue;
        }
        const t = s / samples;
        let x = p1[0] + (p2[0] - p1[0]) * t;
        const y = catmullRomScalar(p0[1], p1[1], p2[1], p3[1], t);
        if (result.length) {
          const prevX = result[result.length - 1][0];
          if (x <= prevX) {
            const segmentSpan = Math.abs(p2[0] - p1[0]);
            const minDelta = Math.max(segmentSpan / (samples + 1), TRACK_MIN_CURVE_STEP * 0.25);
            x = prevX + minDelta;
            if (x >= p2[0]) {
              x = p2[0] - Math.max(minDelta * 0.25, 0.1);
            }
            if (x <= prevX) {
              continue;
            }
          }
        }
        result.push([x, y]);
      }
    }
    return result;
  }

  function createEntropySeed() {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      const buffer = new Uint32Array(1);
      crypto.getRandomValues(buffer);
      const value = buffer[0] >>> 0;
      return value === 0 ? 1 : value;
    }
    const fallback = Math.floor(Math.random() * 4294967295) >>> 0;
    return fallback === 0 ? 1 : fallback;
  }

  function mulberry32(seed) {
    let t = seed >>> 0;
    return () => {
      t += 0x6d2b79f5;
      let r = Math.imul(t ^ (t >>> 15), 1 | t);
      r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
  }

  function getDifficultyTag(tags) {
    if (!Array.isArray(tags)) {
      return 'normal';
    }
    if (tags.includes('easy')) {
      return 'easy';
    }
    return 'normal';
  }

  function getSpeedRangeForTags(tags) {
    const difficulty = getDifficultyTag(tags);
    switch (difficulty) {
      case 'easy':
        return [5, 55];
      default:
        return [12, 70];
    }
  }

  function createPolyline(points) {
    if (!Array.isArray(points) || points.length < 2) {
      return [[0, 0], [200, 0]];
    }
    return points.map(pair => {
      if (!Array.isArray(pair) || pair.length < 2) {
        return [0, 0];
      }
      return [Number(pair[0]) || 0, Number(pair[1]) || 0];
    });
  }

  function computeSlope(a, b) {
    const dx = b[0] - a[0];
    if (Math.abs(dx) < 1e-6) {
      return 0;
    }
    return (b[1] - a[1]) / dx;
  }

  function prepareBlockGeometry(geo) {
    const base = createPolyline(geo);
    if (!Array.isArray(base) || base.length < 2) {
      return [[0, 0], [200 * TRACK_LENGTH_MULTIPLIER, 0]];
    }
    const scaled = base.map(point => {
      const x = (Number(point[0]) || 0) * TRACK_LENGTH_MULTIPLIER;
      const y = (Number(point[1]) || 0) * TRACK_HEIGHT_MULTIPLIER;
      return [x, y];
    });
    return smoothPolyline(scaled, TRACK_CURVE_SUBDIVISIONS);
  }

  function freezeGeometry(polyline) {
    return Object.freeze(polyline.map(point => Object.freeze([point[0], point[1]])));
  }

  function createBlock(id, tags, geo, speedRangeOverride) {
    const poly = prepareBlockGeometry(geo);
    const start = poly[0];
    const end = poly[poly.length - 1];
    const slopeIn = computeSlope(poly[0], poly[1]);
    const slopeOut = computeSlope(poly[poly.length - 2], end);
    const length = end[0] - start[0];
    let minY = Infinity;
    let maxY = -Infinity;
    for (let i = 0; i < poly.length; i += 1) {
      const [, y] = poly[i];
      if (y < minY) {
        minY = y;
      }
      if (y > maxY) {
        maxY = y;
      }
    }
    const [speedMin, speedMax] = Array.isArray(speedRangeOverride)
      ? speedRangeOverride
      : getSpeedRangeForTags(tags);
    return Object.freeze({
      id,
      length,
      y0: 0,
      y1: end[1] - start[1],
      slope_in: slopeIn,
      slope_out: slopeOut,
      speed_min: speedMin,
      speed_max: speedMax,
      tags: Object.freeze([...tags]),
      geo: freezeGeometry(poly),
      minY,
      maxY
    });
  }

  function normalizeSteepSlopeGrip(rawConfig) {
    const fallback = DEFAULT_CONFIG.steepSlopeGrip;
    const threshold = clampNumber(rawConfig?.angleThresholdDeg, 0, 80, fallback.angleThresholdDeg);
    const maxAngleMin = threshold + 1;
    const maxAngle = clampNumber(rawConfig?.maxAngleDeg, maxAngleMin, 89, fallback.maxAngleDeg);
    const normalForceBoost = clampNumber(rawConfig?.normalForceBoost, 0, 50000, fallback.normalForceBoost);
    const correctionBoost = clampNumber(rawConfig?.correctionBoost, 0, 200, fallback.correctionBoost);
    const torqueAssist = clampNumber(rawConfig?.torqueAssist, 0, 100000, fallback.torqueAssist);
    const angularDampingMultiplier = clampNumber(
      rawConfig?.angularDampingMultiplier,
      0,
      10,
      fallback.angularDampingMultiplier
    );
    return {
      angleThresholdDeg: threshold,
      maxAngleDeg: Math.max(maxAngle, maxAngleMin),
      normalForceBoost,
      correctionBoost,
      torqueAssist,
      angularDampingMultiplier
    };
  }

  function normalizeGachaReward(rawConfig) {
    const fallback = DEFAULT_CONFIG.rewards.gacha;
    const intervalMeters = clampNumber(rawConfig?.intervalMeters, 50, 100000, fallback.intervalMeters);
    const ticketAmount = clampNumber(rawConfig?.ticketAmount, 0, 99, fallback.ticketAmount);
    return {
      intervalMeters,
      ticketAmount: Math.max(0, Math.floor(ticketAmount))
    };
  }

  function normalizeConfig(rawConfig) {
    const source = (!rawConfig || typeof rawConfig !== 'object') ? {} : rawConfig;
    return {
      steepSlopeGrip: normalizeSteepSlopeGrip(source.steepSlopeGrip),
      rewards: {
        gacha: normalizeGachaReward(source?.rewards?.gacha || source?.rewards)
      }
    };
  }

  function polyFlat(length) {
    return [[0, 0], [length, 0]];
  }

  function polySlope(length, dy) {
    const lead = Math.min(70, length * 0.28);
    const tail = Math.min(70, length * 0.28);
    const midStart = lead + Math.max(20, (length - lead - tail) * 0.35);
    const midEnd = Math.max(midStart + 10, length - tail);
    const tailMid = (midEnd + length) / 2;
    return [
      [0, 0],
      [lead * 0.5, dy * 0.04],
      [lead, dy * 0.12],
      [midStart, dy * 0.62],
      [midEnd, dy * 0.88],
      [tailMid, dy * 0.96],
      [length, dy]
    ];
  }

  function polyRollers(length, count, amplitude) {
    const lead = Math.min(60, length * 0.12);
    const usable = Math.max(length - lead * 2, 40);
    const steps = Math.max(4, count * 6);
    const points = [
      [0, 0],
      [lead, 0]
    ];
    for (let i = 0; i <= steps; i += 1) {
      const t = i / steps;
      const x = lead + usable * t;
      const y = Math.sin(t * Math.PI * count * 2) * amplitude;
      points.push([x, y]);
    }
    points.push([length - lead, 0]);
    points.push([length, 0]);
    return points;
  }

  function polyLooping(entryLength, radius, exitLength, sampleCount) {
    const safeEntry = Math.max(Number(entryLength) || 0, 1);
    const safeRadius = Math.max(Number(radius) || 0, 10);
    const safeExit = Math.max(Number(exitLength) || 0, 1);
    const samples = Math.max(12, Math.floor(Number(sampleCount) || 0));
    const loopWidth = safeRadius * 2.05;
    const baseOffsetFactor = 0.48;
    const bottomY = -safeRadius * baseOffsetFactor;
    const centerY = bottomY - safeRadius;
    const totalLength = safeEntry + loopWidth + safeExit;
    const points = [
      [0, 0],
      [safeEntry * 0.28, bottomY * 0.18],
      [safeEntry * 0.56, bottomY * 0.52],
      [safeEntry * 0.82, bottomY * 0.88],
      [safeEntry, bottomY]
    ];
    for (let i = 1; i <= samples; i += 1) {
      const t = (i / samples) * Math.PI * 2;
      const x = safeEntry + (loopWidth * (i / samples));
      const y = centerY + safeRadius * Math.cos(t);
      points.push([x, y]);
    }
    const exitStartX = safeEntry + loopWidth + safeExit * 0.2;
    const exitMidX = safeEntry + loopWidth + safeExit * 0.6;
    points.push([exitStartX, bottomY * 0.58]);
    points.push([exitMidX, bottomY * 0.24]);
    points.push([totalLength, 0]);
    return points;
  }

  function polyWhoops(length, count, amplitude) {
    const lead = Math.min(55, length * 0.12);
    const usable = Math.max(length - lead * 2, 30);
    const steps = Math.max(4, count * 6);
    const points = [
      [0, 0],
      [lead, 0]
    ];
    for (let i = 0; i <= steps; i += 1) {
      const t = i / steps;
      const x = lead + usable * t;
      const envelope = Math.pow(Math.sin(t * Math.PI), 0.7);
      const y = Math.sin(t * Math.PI * count * 2) * amplitude * envelope;
      points.push([x, y]);
    }
    points.push([length - lead, 0]);
    points.push([length, 0]);
    return points;
  }

  function polyTableTop(length, height, tableLen) {
    const rampLen = Math.max(60, (length - tableLen) / 2);
    const lead = Math.min(65, rampLen * 0.5);
    const exit = length - rampLen;
    return [
      [0, 0],
      [lead, -height * 0.08],
      [rampLen * 0.7, -height * 0.55],
      [rampLen, -height],
      [rampLen + tableLen, -height],
      [exit + (rampLen - lead) * 0.35, -height * 0.35],
      [length - lead, -height * 0.08],
      [length, 0]
    ];
  }

  function polyGap(length, takeoffAngle, gapLen, landingSlope) {
    const takeoffLen = Math.max(60, length * 0.32);
    const landingLen = Math.max(80, length - takeoffLen - gapLen);
    const takeoffHeight = Math.tan(takeoffAngle) * takeoffLen;
    const landingHeight = landingSlope * landingLen;
    const lead = Math.min(60, takeoffLen * 0.45);
    const landingLead = Math.min(70, landingLen * 0.35);
    return [
      [0, 0],
      [lead, -takeoffHeight * 0.12],
      [takeoffLen * 0.75, -takeoffHeight * 0.65],
      [takeoffLen, -takeoffHeight],
      [takeoffLen + gapLen * 0.55, -takeoffHeight * 0.35],
      [takeoffLen + gapLen, -takeoffHeight * 0.08],
      [length - landingLen + landingLead * 0.25, landingHeight * 0.55],
      [length - landingLead, landingHeight * 0.95],
      [length, landingHeight]
    ];
  }

  function polyStep(length, height, plateau) {
    const rampLen = Math.max(60, (length - plateau) / 2);
    const lead = Math.min(60, rampLen * 0.55);
    const plateauEnd = rampLen + plateau;
    return [
      [0, 0],
      [lead, -height * 0.1],
      [rampLen, -height],
      [plateauEnd, -height],
      [length - lead, -height * 0.35],
      [length, -height * 0.1]
    ];
  }

  function polyDrop(length, height, plateau) {
    const rampLen = Math.max(60, (length - plateau) / 2);
    const lead = Math.min(60, rampLen * 0.55);
    const plateauEnd = rampLen + plateau;
    return [
      [0, 0],
      [lead, height * 0.1],
      [rampLen, height],
      [plateauEnd, height],
      [length - lead, height * 0.35],
      [length, height * 0.1]
    ];
  }

  function polyProfile(length, template, amplitude, verticalOffset = 0) {
    if (!Array.isArray(template) || template.length < 2) {
      return polyFlat(length);
    }
    const safeLength = Math.max(Number(length) || 0, 1);
    const safeAmplitude = Number.isFinite(amplitude) ? amplitude : 0;
    const offset = Number.isFinite(verticalOffset) ? verticalOffset : 0;
    const points = [[0, offset]];
    for (let i = 1; i < template.length - 1; i += 1) {
      const entry = template[i];
      if (!Array.isArray(entry) || entry.length < 2) {
        continue;
      }
      const t = clamp(Number(entry[0]) || 0, 0, 1);
      const x = t * safeLength;
      const y = offset + safeAmplitude * (Number(entry[1]) || 0);
      points.push([x, y]);
    }
    points.push([safeLength, offset]);
    points.sort((a, b) => a[0] - b[0]);
    for (let i = 1; i < points.length; i += 1) {
      if (points[i][0] <= points[i - 1][0]) {
        const previous = points[i - 1][0];
        const minimumStep = Math.max(1, safeLength * 0.01);
        points[i][0] = Math.min(safeLength, previous + minimumStep);
      }
    }
    points[0][0] = 0;
    points[points.length - 1][0] = safeLength;
    return points;
  }

  const PROFILE_TEMPLATES = Object.freeze({
    doubleHill: Object.freeze([
      [0, 0],
      [0.12, -0.45],
      [0.24, 0.05],
      [0.44, -0.88],
      [0.64, -0.18],
      [0.82, -0.62],
      [1, 0]
    ]),
    softWave: Object.freeze([
      [0, 0],
      [0.18, -0.35],
      [0.36, -0.08],
      [0.58, 0.32],
      [0.78, 0.08],
      [0.92, -0.18],
      [1, 0]
    ]),
    ridgeDrop: Object.freeze([
      [0, 0],
      [0.1, -0.22],
      [0.24, -0.96],
      [0.36, -1.15],
      [0.54, -0.42],
      [0.72, 0.32],
      [0.88, 0.14],
      [1, 0]
    ]),
    rolling: Object.freeze([
      [0, 0],
      [0.12, -0.35],
      [0.26, 0.32],
      [0.4, -0.46],
      [0.56, 0.46],
      [0.72, -0.3],
      [0.88, 0.2],
      [1, 0]
    ]),
    mellow: Object.freeze([
      [0, 0],
      [0.16, -0.28],
      [0.34, 0.18],
      [0.52, 0.3],
      [0.72, 0.08],
      [0.9, -0.12],
      [1, 0]
    ]),
    shallowValley: Object.freeze([
      [0, 0],
      [0.2, 0.22],
      [0.4, 0.48],
      [0.5, 0.6],
      [0.62, 0.42],
      [0.82, 0.16],
      [1, 0]
    ]),
    deepValley: Object.freeze([
      [0, 0],
      [0.18, 0.32],
      [0.38, 0.84],
      [0.5, 1],
      [0.62, 0.84],
      [0.82, 0.3],
      [1, 0]
    ]),
    bowl: Object.freeze([
      [0, 0],
      [0.14, 0.12],
      [0.32, 0.42],
      [0.5, 0.72],
      [0.68, 0.42],
      [0.86, 0.12],
      [1, 0]
    ]),
    landingSlope: Object.freeze([
      [0, 0],
      [0.26, 0.08],
      [0.52, -0.18],
      [0.78, -0.32],
      [1, 0]
    ]),
    closingRise: Object.freeze([
      [0, 0],
      [0.24, -0.32],
      [0.48, -0.58],
      [0.74, -0.24],
      [1, 0]
    ])
  });

  const UNIT_TO_METERS = 0.1 / 3;
  const MIN_DISPLAY_SPEED_KMH = 0.15;
  const TEST_STEP_INTERVAL = 2400;
  const TEST_CAMERA_ZOOM = 0.72;

  const START_BLOCK = createBlock('flat/start/01', ['flat', 'easy', 'starter'], polyFlat(200));
  function createProfileBlock(id, tags, length, templateKey, amplitude, verticalOffset) {
    const template = PROFILE_TEMPLATES[templateKey];
    const scaledAmplitude = Number.isFinite(amplitude) ? amplitude * PROFILE_AMPLITUDE_MULTIPLIER : amplitude;
    const scaledOffset = Number.isFinite(verticalOffset) ? verticalOffset * PROFILE_AMPLITUDE_MULTIPLIER : verticalOffset;
    const geometry = template
      ? polyProfile(length, template, scaledAmplitude, scaledOffset)
      : polyFlat(length);
    return createBlock(id, tags, geometry);
  }

  const BLOCK_LIBRARY = Object.freeze([
    START_BLOCK,
    createBlock('flat/easy/02', ['flat', 'easy'], polyFlat(240)),
    createBlock('flat/normal/01', ['flat', 'normal'], polyFlat(300)),
    createProfileBlock('flow/double/easy/01', ['flow', 'easy'], 280, 'doubleHill', 28),
    createProfileBlock('flow/double/normal/01', ['flow', 'normal'], 320, 'doubleHill', 42),
    createProfileBlock('flow/wave/easy/01', ['wave', 'easy'], 260, 'softWave', 22),
    createProfileBlock('flow/wave/normal/01', ['wave', 'normal'], 300, 'softWave', 34),
    createProfileBlock('crest/launch/easy/01', ['crest', 'easy'], 280, 'ridgeDrop', 36),
    createProfileBlock('crest/launch/normal/01', ['crest', 'normal'], 320, 'ridgeDrop', 48),
    createProfileBlock('rhythm/rolling/easy/01', ['rhythm', 'easy'], 260, 'rolling', 28),
    createProfileBlock('rhythm/rolling/normal/01', ['rhythm', 'normal'], 300, 'rolling', 40),
    createProfileBlock('rhythm/mellow/easy/01', ['rhythm', 'easy'], 240, 'mellow', 20),
    createProfileBlock('rhythm/mellow/normal/01', ['rhythm', 'normal'], 280, 'mellow', 30),
    createProfileBlock('valley/shallow/easy/01', ['valley', 'easy'], 260, 'shallowValley', 32),
    createProfileBlock('valley/shallow/normal/01', ['valley', 'normal'], 300, 'shallowValley', 44),
    createProfileBlock('valley/deep/easy/01', ['valley', 'easy'], 280, 'deepValley', 38),
    createProfileBlock('valley/deep/normal/01', ['valley', 'normal'], 320, 'deepValley', 54),
    createProfileBlock('valley/bowl/easy/01', ['valley', 'easy'], 280, 'bowl', 34),
    createProfileBlock('valley/bowl/normal/01', ['valley', 'normal'], 320, 'bowl', 48),
    createProfileBlock('landing/flow/easy/01', ['landing_pad', 'easy'], 280, 'landingSlope', 26),
    createProfileBlock('landing/flow/normal/01', ['landing_pad', 'normal'], 320, 'landingSlope', 32),
    createProfileBlock('closing/rise/easy/01', ['flow', 'easy'], 260, 'closingRise', 26),
    createProfileBlock('closing/rise/normal/01', ['flow', 'normal'], 300, 'closingRise', 36)
  ]);

  const TRACK_BLOCKS = BLOCK_LIBRARY.filter(block => block !== START_BLOCK);
  const LANDING_BLOCKS = TRACK_BLOCKS.filter(block => block.tags.includes('landing_pad'));

  function matchesDifficulty(block, difficulty) {
    if (!block || !Array.isArray(block.tags)) {
      return false;
    }
    if (difficulty === 'easy') {
      return block.tags.includes('easy');
    }
    return block.tags.includes('normal') || block.tags.includes('easy');
  }

  function normalizeDifficulty(value) {
    if (value === 'easy') {
      return 'easy';
    }
    if (value === 'normal' || value === 'hard') {
      return 'normal';
    }
    return 'normal';
  }

  function buildDifficultyOptions(baseDifficulty, blockCount) {
    const safeCount = Math.max(0, Number.isFinite(blockCount) ? blockCount : 0);
    const options = [];
    const pushOption = (level, weight) => {
      if (weight > 0) {
        options.push({ level, weight });
      }
    };

    if (baseDifficulty === 'normal') {
      pushOption('normal', 4 + Math.floor(safeCount / 5));
      pushOption('easy', 3);
      return options;
    }

    pushOption('easy', 4);
    if (safeCount >= 2) {
      pushOption('normal', 3 + Math.floor((safeCount - 1) / 3));
    }
    return options;
  }

  function pickEffectiveDifficulty(generator) {
    if (!generator || typeof generator.rng !== 'function') {
      return 'normal';
    }
    const base = normalizeDifficulty(generator.baseDifficulty || generator.difficulty || DEFAULT_DIFFICULTY);
    const options = buildDifficultyOptions(base, generator.blocksGenerated);
    if (!options.length) {
      return base;
    }
    const totalWeight = options.reduce((sum, option) => sum + option.weight, 0);
    if (totalWeight <= 0) {
      return base;
    }
    const roll = generator.rng() * totalWeight;
    let cumulative = 0;
    for (let i = 0; i < options.length; i += 1) {
      cumulative += options[i].weight;
      if (roll <= cumulative) {
        return options[i].level;
      }
    }
    return options[options.length - 1].level;
  }

  function blockWithinElevation(block, baseY) {
    if (!block) {
      return false;
    }
    const min = baseY + block.minY;
    const max = baseY + block.maxY;
    return min >= -ELEVATION_LIMIT && max <= ELEVATION_LIMIT;
  }

  function pickNextBlock(prevBlock, difficulty, rng, currentY, history) {
    if (!prevBlock) {
      return null;
    }
    const tolerances = [SLOPE_STEP, SLOPE_STEP * 1.5, SLOPE_STEP * 2.5, Infinity];
    let pool = [];
    for (let i = 0; i < tolerances.length; i += 1) {
      const tolerance = tolerances[i];
      pool = TRACK_BLOCKS.filter(block => {
        if (!blockWithinElevation(block, currentY)) {
          return false;
        }
        if (tolerance !== Infinity && Math.abs(prevBlock.slope_out - block.slope_in) > tolerance) {
          return false;
        }
        return true;
      });
      if (pool.length) {
        break;
      }
    }

    if (!pool.length) {
      return null;
    }

    let filtered = pool.filter(block => matchesDifficulty(block, difficulty));
    if (!filtered.length) {
      filtered = pool;
    }

    const recent = Array.isArray(history) ? history : [];
    const preferred = filtered.filter(block => !recent.includes(block.id));
    const candidates = preferred.length ? preferred : filtered;

    if (currentY > ELEVATION_LIMIT * 0.5) {
      const descending = candidates.filter(block => block.y1 <= 0);
      if (descending.length) {
        return descending[Math.floor(rng() * descending.length) % descending.length];
      }
    } else if (currentY < -ELEVATION_LIMIT * 0.5) {
      const ascending = candidates.filter(block => block.y1 >= 0);
      if (ascending.length) {
        return ascending[Math.floor(rng() * ascending.length) % ascending.length];
      }
    }

    const index = Math.floor(rng() * candidates.length) % candidates.length;
    return candidates[index];
  }

  function pickLandingBlock(prevBlock, difficulty, rng, currentY) {
    if (!prevBlock) {
      return null;
    }
    const tolerances = [SLOPE_STEP, SLOPE_STEP * 2, Infinity];
    let pool = [];
    for (let i = 0; i < tolerances.length; i += 1) {
      const tolerance = tolerances[i];
      pool = LANDING_BLOCKS.filter(block => {
        if (!blockWithinElevation(block, currentY)) {
          return false;
        }
        if (tolerance !== Infinity && Math.abs(prevBlock.slope_out - block.slope_in) > tolerance) {
          return false;
        }
        return true;
      });
      if (pool.length) {
        break;
      }
    }

    if (!pool.length) {
      return null;
    }

    let filtered = pool.filter(block => matchesDifficulty(block, difficulty));
    if (!filtered.length) {
      filtered = pool;
    }
    const index = Math.floor(rng() * filtered.length) % filtered.length;
    return filtered[index];
  }

  function appendBlockPoints(target, block, offsetX, offsetY, skipFirst) {
    const { geo } = block;
    let lastPoint = null;
    for (let i = 0; i < geo.length; i += 1) {
      if (skipFirst && i === 0) {
        continue;
      }
      const point = geo[i];
      lastPoint = [offsetX + point[0], offsetY + point[1]];
      target.push(lastPoint);
    }
    return lastPoint;
  }

  function createSegments(points) {
    const segments = [];
    for (let i = 0; i < points.length - 1; i += 1) {
      const [x1, y1] = points[i];
      const [x2, y2] = points[i + 1];
      const dx = x2 - x1;
      const dy = y2 - y1;
      const length = Math.hypot(dx, dy);
      if (length <= 0.0001) {
        continue;
      }
      const tangent = { x: dx / length, y: dy / length };
      const normal = { x: tangent.y, y: -tangent.x };
      segments.push({
        p0: { x: x1, y: y1 },
        p1: { x: x2, y: y2 },
        tangent,
        normal,
        length,
        minX: Math.min(x1, x2),
        maxX: Math.max(x1, x2)
      });
    }
    return segments;
  }

  function sampleTrack(track, x, hintIndex) {
    const { segments } = track;
    if (!segments.length) {
      return {
        point: { x, y: 0 },
        normal: { x: 0, y: -1 },
        tangent: { x: 1, y: 0 },
        index: 0
      };
    }
    const clampedX = clamp(x, segments[0].minX, segments[segments.length - 1].maxX);
    let index = typeof hintIndex === 'number' ? clamp(Math.floor(hintIndex), 0, segments.length - 1) : 0;
    let segment = segments[index];

    const withinSegment = seg => clampedX >= seg.minX - 0.01 && clampedX <= seg.maxX + 0.01;
    if (!withinSegment(segment)) {
      if (clampedX < segment.minX) {
        while (index > 0 && clampedX < segments[index].minX) {
          index -= 1;
        }
      } else if (clampedX > segment.maxX) {
        while (index < segments.length - 1 && clampedX > segments[index].maxX) {
          index += 1;
        }
      }
      segment = segments[index];
      if (!withinSegment(segment)) {
        for (let i = 0; i < segments.length; i += 1) {
          if (withinSegment(segments[i])) {
            segment = segments[i];
            index = i;
            break;
          }
        }
      }
    }

    const dx = segment.p1.x - segment.p0.x;
    let t = 0;
    if (Math.abs(dx) > 1e-6) {
      t = (clampedX - segment.p0.x) / dx;
    }
    t = clamp(t, 0, 1);
    const y = segment.p0.y + (segment.p1.y - segment.p0.y) * t;
    return {
      point: { x: clampedX, y },
      normal: segment.normal,
      tangent: segment.tangent,
      index
    };
  }

  function createCheckpoint(track, x, hint) {
    const sample = sampleTrack(track, x, hint);
    const angle = Math.atan2(sample.tangent.y, sample.tangent.x);
    return {
      x: sample.point.x,
      y: sample.point.y,
      angle,
      segmentIndex: sample.index
    };
  }

  function rebuildSegments(track, startIndex) {
    if (!track || !Array.isArray(track.points)) {
      return;
    }
    const points = track.points;
    const segments = Array.isArray(track.segments) ? track.segments : [];
    const begin = clamp(Number.isFinite(startIndex) ? Math.floor(startIndex) : 0, 0, Math.max(0, points.length - 1));
    if (segments.length > begin) {
      segments.length = begin;
    }
    for (let i = begin; i < points.length - 1; i += 1) {
      const [x1, y1] = points[i];
      const [x2, y2] = points[i + 1];
      const dx = x2 - x1;
      const dy = y2 - y1;
      const length = Math.hypot(dx, dy);
      if (length <= 0.0001) {
        continue;
      }
      const tangent = { x: dx / length, y: dy / length };
      const normal = { x: tangent.y, y: -tangent.x };
      segments.push({
        p0: { x: x1, y: y1 },
        p1: { x: x2, y: y2 },
        tangent,
        normal,
        length,
        minX: Math.min(x1, x2),
        maxX: Math.max(x1, x2)
      });
    }
    track.segments = segments;
  }

  function updateTrackElevation(track, startIndex) {
    if (!track || !Array.isArray(track.points)) {
      return;
    }
    const begin = clamp(Number.isFinite(startIndex) ? Math.floor(startIndex) : 0, 0, track.points.length);
    if (!Number.isFinite(track.highestY)) {
      track.highestY = -Infinity;
    }
    for (let i = begin; i < track.points.length; i += 1) {
      const [, y] = track.points[i];
      if (y > track.highestY) {
        track.highestY = y;
      }
    }
    if (!Number.isFinite(track.highestY)) {
      track.highestY = 0;
    }
    track.maxY = track.highestY + FALL_EXTRA_MARGIN;
  }

  function ensureTrackCheckpoints(track) {
    if (!track || !Array.isArray(track.checkpoints)) {
      return;
    }
    const interval = track.checkpointInterval || intervalClamp(CHECKPOINT_INTERVAL);
    const maxLength = track.length || 0;
    let hint = track.lastCheckpointHint || 0;
    while (track.nextCheckpointX <= maxLength) {
      const checkpoint = createCheckpoint(track, track.nextCheckpointX, hint);
      track.checkpoints.push(checkpoint);
      hint = checkpoint.segmentIndex;
      track.lastCheckpointHint = hint;
      track.nextCheckpointX += interval;
    }
  }

  function createTrackGenerator(seed, difficulty) {
    const normalized = normalizeDifficulty(typeof difficulty === 'string' ? difficulty : DEFAULT_DIFFICULTY);
    return {
      rng: mulberry32(seed || 1),
      difficulty: normalized,
      baseDifficulty: normalized,
      prevBlock: START_BLOCK,
      currentY: 0,
      history: [START_BLOCK.id],
      blocksGenerated: 0,
      lastDifficulty: normalized
    };
  }

  function extendTrack(track, blockCount) {
    if (!track || !track.generator) {
      return;
    }
    const generator = track.generator;
    let added = 0;
    while (added < blockCount) {
      const effectiveDifficulty = pickEffectiveDifficulty(generator);
      let nextBlock = pickNextBlock(generator.prevBlock, effectiveDifficulty, generator.rng, generator.currentY, generator.history);
      if (!nextBlock) {
        nextBlock = pickLandingBlock(generator.prevBlock, effectiveDifficulty, generator.rng, generator.currentY) || START_BLOCK;
      }
      if (!nextBlock) {
        break;
      }
      const previousPointCount = track.points.length;
      const blockStartX = track.length;
      const lastPoint = appendBlockPoints(track.points, nextBlock, track.length, generator.currentY, true);
      if (!lastPoint) {
        break;
      }
      generator.currentY = lastPoint[1];
      track.length = lastPoint[0];
      track.usedIds.push(nextBlock.id);
      generator.history.push(nextBlock.id);
      if (generator.history.length > 5) {
        generator.history.shift();
      }
      generator.prevBlock = nextBlock;
      generator.blocksGenerated += 1;
      generator.lastDifficulty = effectiveDifficulty;
      const segmentStartIndex = Math.max(previousPointCount - 1, 0);
      rebuildSegments(track, segmentStartIndex);
      updateTrackElevation(track, previousPointCount);
      if (Array.isArray(track.blocks)) {
        const blockEntry = {
          id: nextBlock.id,
          tags: Array.isArray(nextBlock.tags) ? [...nextBlock.tags] : [],
          startX: blockStartX,
          endX: track.length,
          segmentHint: Math.max(0, Math.min(segmentStartIndex, track.segments.length - 1))
        };
        track.blocks.push(blockEntry);
      }
      added += 1;
    }
    if (Array.isArray(track.checkpoints)) {
      ensureTrackCheckpoints(track);
    }
  }

  function buildTrack(seed, difficulty) {
    const generator = createTrackGenerator(seed, difficulty);
    const points = [];
    const usedIds = [];
    const blockEntries = [];
    let currentX = 0;
    let currentY = 0;
    const startEnd = appendBlockPoints(points, START_BLOCK, currentX, currentY, false);
    if (startEnd) {
      currentX = startEnd[0];
      currentY = startEnd[1];
    }
    usedIds.push(START_BLOCK.id);
    blockEntries.push({
      id: START_BLOCK.id,
      tags: Array.isArray(START_BLOCK.tags) ? [...START_BLOCK.tags] : [],
      startX: 0,
      endX: currentX,
      segmentHint: 0
    });
    const segments = createSegments(points);
    let highestY = -Infinity;
    for (let i = 0; i < points.length; i += 1) {
      if (points[i][1] > highestY) {
        highestY = points[i][1];
      }
    }
    const baseHighest = Number.isFinite(highestY) ? highestY : 0;
    const track = {
      points,
      segments,
      length: currentX,
      usedIds,
      highestY: baseHighest,
      maxY: baseHighest + FALL_EXTRA_MARGIN,
      generator,
      seed: seed || 1,
      difficulty: generator.difficulty,
      checkpoints: [],
      blocks: blockEntries,
      checkpointInterval: intervalClamp(CHECKPOINT_INTERVAL),
      nextCheckpointX: Math.max(40, points.length ? points[0][0] + 40 : 40),
      lastCheckpointHint: 0
    };
    generator.currentY = currentY;
    if (track.points.length) {
      const firstCheckpoint = createCheckpoint(track, track.points[0][0] + 10, 0);
      track.checkpoints.push(firstCheckpoint);
      track.lastCheckpointHint = firstCheckpoint.segmentIndex;
      track.nextCheckpointX = Math.max(track.nextCheckpointX, firstCheckpoint.x + track.checkpointInterval);
    }
    extendTrack(track, INITIAL_BLOCK_COUNT);
    if (!track.points || track.points.length < 2) {
      return null;
    }
    return track;
  }

  function ensureTrackAhead() {
    const { track, bike } = state;
    if (!track || !track.generator || !bike) {
      return;
    }
    const remaining = track.length - bike.position.x;
    if (remaining < TRACK_AHEAD_BUFFER) {
      extendTrack(track, EXTEND_BLOCK_COUNT);
      state.fallThreshold = track.maxY;
    }
  }

  function intervalClamp(value) {
    const numeric = Number.isFinite(value) ? value : CHECKPOINT_INTERVAL;
    return clamp(numeric, 600, 1400);
  }

  function createBackgroundState() {
    return {
      image: null,
      url: null,
      loaded: false,
      loading: false,
      width: 0,
      height: 0
    };
  }

  function createBikeSpriteState() {
    return {
      image: null,
      loaded: false,
      loading: false,
      width: 0,
      height: 0
    };
  }

  const state = {
    initialized: false,
    active: false,
    elements: null,
    ctx: null,
    config: normalizeConfig(DEFAULT_CONFIG),
    devicePixelRatio: typeof window !== 'undefined' && window.devicePixelRatio ? window.devicePixelRatio : 1,
    track: null,
    bike: {
      position: { x: 0, y: 0 },
      velocity: { x: 0, y: 0 },
      angle: 0,
      angularVelocity: 0
    },
    wheels: {
      back: { pos: { x: 0, y: 0 }, r: { x: 0, y: 0 }, segmentIndex: 0, onGround: false },
      front: { pos: { x: 0, y: 0 }, r: { x: 0, y: 0 }, segmentIndex: 0, onGround: false }
    },
    camera: { x: 0, y: 0, zoom: 1, targetZoom: 1 },
    input: {
      accelerate: 0,
      brake: 0,
      sources: {
        keyboard: { accelerate: 0, brake: 0 },
        touch: { accelerate: 0, brake: 0 }
      }
    },
    pointer: {
      left: new Set(),
      right: new Set()
    },
    boost: {
      holdTime: 0,
      steps: 0,
      factor: 1
    },
    statusKey: 'index.sections.motocross.ui.status.ready',
    statusFallback: 'Appuyez sur « Générer » pour lancer une piste aléatoire.',
    statusParams: null,
    statusState: 'idle',
    lastTimestamp: null,
    accumulator: 0,
    currentCheckpoint: 0,
    respawnData: null,
    fallThreshold: 600,
    pendingRespawn: false,
    speed: 0,
    speedStartX: 0,
    speedElapsed: 0,
    gameOver: false,
    trackStartX: 0,
    runStartX: 0,
    maxDistance: 0,
    runPeakSpeed: 0,
    gachaMilestonesClaimed: 0,
    bestRecords: { bestDistance: 0, bestSpeed: 0 },
    recordsDirty: false,
    runRecords: { distance: false, speed: false },
    lastRunSummary: null,
    lastGeneratedCount: 0,
    background: createBackgroundState(),
    bikeSprite: createBikeSpriteState(),
    test: { active: false, timer: null, index: 0, resumeActive: false }
  };

  function sanitizeMotocrossRecordValue(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      return 0;
    }
    return Math.max(0, Math.round(numeric));
  }

  function hydrateMotocrossRecords() {
    const snapshot = readMotocrossRecordSnapshot();
    if (!snapshot) {
      state.bestRecords.bestDistance = 0;
      state.bestRecords.bestSpeed = 0;
      state.recordsDirty = false;
      updateBestRecordsDisplay();
      return;
    }
    state.bestRecords.bestDistance = sanitizeMotocrossRecordValue(snapshot.bestDistance);
    state.bestRecords.bestSpeed = sanitizeMotocrossRecordValue(snapshot.bestSpeed);
    state.recordsDirty = false;
    updateBestRecordsDisplay();
  }

  function updateBestDistanceRecord(distance) {
    const sanitized = sanitizeMotocrossRecordValue(distance);
    if (sanitized > state.bestRecords.bestDistance) {
      state.bestRecords.bestDistance = sanitized;
      state.recordsDirty = true;
      if (state.runRecords) {
        state.runRecords.distance = true;
      }
      updateBestRecordsDisplay();
    }
  }

  function updateBestSpeedRecord(speed) {
    const sanitized = sanitizeMotocrossRecordValue(speed);
    if (sanitized > state.bestRecords.bestSpeed) {
      state.bestRecords.bestSpeed = sanitized;
      state.recordsDirty = true;
      if (state.runRecords) {
        state.runRecords.speed = true;
      }
      updateBestRecordsDisplay();
    }
  }

  function persistMotocrossRecords(force = false) {
    const hasRecord = state.bestRecords.bestDistance > 0 || state.bestRecords.bestSpeed > 0;
    if (!force && (!state.recordsDirty || !hasRecord)) {
      return;
    }
    if (!hasRecord) {
      return;
    }
    const autosavePayload = {
      version: MOTOCROSS_AUTOSAVE_VERSION,
      bestDistance: state.bestRecords.bestDistance,
      bestSpeed: state.bestRecords.bestSpeed
    };
    const autosave = getArcadeAutosaveApi();
    if (autosave) {
      try {
        autosave.set(MOTOCROSS_PROGRESS_KEY, autosavePayload);
      } catch (error) {
        // Ignore autosave errors to avoid interrupting the run.
      }
    }
    const globalState = getGlobalGameState();
    if (globalState) {
      const entry = ensureMotocrossProgressEntry(globalState);
      if (entry) {
        const nextState = entry.state && typeof entry.state === 'object' ? { ...entry.state } : {};
        nextState.bestDistance = autosavePayload.bestDistance;
        nextState.bestSpeed = autosavePayload.bestSpeed;
        const payload = { state: nextState, updatedAt: Date.now() };
        if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
          globalState.arcadeProgress.entries = {};
        }
        globalState.arcadeProgress.entries[MOTOCROSS_PROGRESS_KEY] = payload;
      }
    }
    state.recordsDirty = false;
    const saveFn = getSaveGameFunction();
    if (typeof saveFn === 'function') {
      try {
        saveFn();
      } catch (error) {
        // Ignore save errors for this optional sync
      }
    }
  }

  hydrateMotocrossRecords();

  function applyConfig(config) {
    const normalized = normalizeConfig(config);
    state.config = normalized;
  }

  function loadConfig() {
    if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
      return;
    }
    fetch(CONFIG_PATH)
      .then(response => (response.ok ? response.json() : null))
      .then(data => {
        if (data && typeof data === 'object') {
          applyConfig(data);
        }
      })
      .catch(error => {
        console.warn('Motocross: unable to load config', error);
      });
  }

  function updateCombinedInput() {
    const input = state.input;
    if (!input) {
      return;
    }
    const sources = input.sources || {};
    const keyboard = sources.keyboard || {};
    const touch = sources.touch || {};
    const accelerate = Math.max(
      keyboard.accelerate || 0,
      touch.accelerate || 0
    );
    const brake = Math.max(
      keyboard.brake || 0,
      touch.brake || 0
    );
    input.accelerate = accelerate ? 1 : 0;
    input.brake = brake ? 1 : 0;
  }

  function syncTouchInput() {
    if (!state.input || !state.input.sources) {
      updateCombinedInput();
      return;
    }
    const touch = state.input.sources.touch;
    if (touch) {
      const rightSet = state.pointer?.right;
      const leftSet = state.pointer?.left;
      touch.accelerate = rightSet instanceof Set && rightSet.size > 0 ? 1 : 0;
      touch.brake = leftSet instanceof Set && leftSet.size > 0 ? 1 : 0;
    }
    updateCombinedInput();
  }

  function updateBoostState(dt, accelerateHeld) {
    const boost = state.boost;
    if (!boost) {
      return 1;
    }
    if (accelerateHeld) {
      boost.holdTime += dt;
      const maxHoldTime = BOOST_BASE_DELAY + BOOST_STEP_DURATION * BOOST_MAX_STEPS;
      if (boost.holdTime > maxHoldTime) {
        boost.holdTime = maxHoldTime;
      }
      const extraTime = boost.holdTime - BOOST_BASE_DELAY;
      if (extraTime >= BOOST_STEP_DURATION) {
        const newSteps = Math.min(
          Math.floor(extraTime / BOOST_STEP_DURATION),
          BOOST_MAX_STEPS
        );
        boost.steps = newSteps;
        const rawFactor = Math.pow(1 + BOOST_STEP_RATE, newSteps);
        boost.factor = clamp(rawFactor, 1, BOOST_MAX_MULTIPLIER);
      } else {
        boost.steps = 0;
        boost.factor = 1;
      }
    } else {
      boost.holdTime = 0;
      boost.steps = 0;
      boost.factor = 1;
    }
    return boost.factor;
  }

  function setKeyboardInput(action, value) {
    if (!state.input || !state.input.sources || !state.input.sources.keyboard) {
      return;
    }
    const normalized = value ? 1 : 0;
    const keyboard = state.input.sources.keyboard;
    if (action === 'accelerate') {
      if (keyboard.accelerate === normalized) {
        return;
      }
      keyboard.accelerate = normalized;
    } else if (action === 'brake') {
      if (keyboard.brake === normalized) {
        return;
      }
      keyboard.brake = normalized;
    } else {
      return;
    }
    updateCombinedInput();
  }

  function updatePointerSide(pointerId, clientX) {
    if (!state.pointer || typeof clientX !== 'number') {
      return;
    }
    const width = typeof window !== 'undefined' && window.innerWidth ? window.innerWidth : 0;
    if (width <= 0) {
      return;
    }
    const center = width / 2;
    const rightSet = state.pointer.right;
    const leftSet = state.pointer.left;
    if (!(rightSet instanceof Set) || !(leftSet instanceof Set)) {
      return;
    }
    if (clientX >= center) {
      leftSet.delete(pointerId);
      rightSet.add(pointerId);
    } else {
      rightSet.delete(pointerId);
      leftSet.add(pointerId);
    }
    syncTouchInput();
  }

  function releasePointer(pointerId) {
    if (!state.pointer) {
      return;
    }
    const rightSet = state.pointer.right;
    const leftSet = state.pointer.left;
    let changed = false;
    if (rightSet instanceof Set && rightSet.delete(pointerId)) {
      changed = true;
    }
    if (leftSet instanceof Set && leftSet.delete(pointerId)) {
      changed = true;
    }
    if (changed) {
      syncTouchInput();
    }
  }

  function resetInputState() {
    if (state.input && state.input.sources) {
      const { keyboard, touch } = state.input.sources;
      if (keyboard) {
        keyboard.accelerate = 0;
        keyboard.brake = 0;
      }
      if (touch) {
        touch.accelerate = 0;
        touch.brake = 0;
      }
    }
    if (state.pointer) {
      if (state.pointer.left instanceof Set) {
        state.pointer.left.clear();
      }
      if (state.pointer.right instanceof Set) {
        state.pointer.right.clear();
      }
    }
    if (state.boost) {
      state.boost.holdTime = 0;
      state.boost.steps = 0;
      state.boost.factor = 1;
    }
    updateCombinedInput();
  }

  function getElements() {
    if (!document) {
      return null;
    }
    return {
      canvas: document.getElementById('motocrossCanvas'),
      generateButton: document.getElementById('motocrossGenerate'),
      speedValue: document.getElementById('motocrossSpeedValue'),
      distanceValue: document.getElementById('motocrossDistanceValue'),
      status: document.getElementById('motocrossStatus'),
      restartButton: document.getElementById('motocrossRestart'),
      testButton: document.getElementById('motocrossTest'),
      recordsDistanceValue: document.getElementById('motocrossRecordsDistanceValue'),
      recordsSpeedValue: document.getElementById('motocrossRecordsSpeedValue'),
      gameOverOverlay: document.getElementById('motocrossGameOverOverlay'),
      gameOverDistanceValue: document.getElementById('motocrossGameOverDistanceValue'),
      gameOverSpeedValue: document.getElementById('motocrossGameOverSpeedValue'),
      gameOverBestDistanceValue: document.getElementById('motocrossGameOverBestDistanceValue'),
      gameOverBestSpeedValue: document.getElementById('motocrossGameOverBestSpeedValue'),
      gameOverDistanceBadge: document.getElementById('motocrossGameOverDistanceBadge'),
      gameOverSpeedBadge: document.getElementById('motocrossGameOverSpeedBadge'),
      gameOverRetryButton: document.getElementById('motocrossGameOverRetry'),
      gameOverDismissButton: document.getElementById('motocrossGameOverDismiss')
    };
  }

  function applyStatus() {
    const { elements, statusKey, statusFallback, statusParams, statusState, gameOver } = state;
    if (!elements || !elements.status) {
      return;
    }
    const fallback = typeof statusFallback === 'string'
      ? formatTemplate(statusFallback, statusParams || {})
      : '';
    const text = translate(statusKey, fallback, statusParams);
    elements.status.textContent = text;
    elements.status.dataset.state = statusState || 'idle';
    elements.status.setAttribute('data-i18n', statusKey || '');
    if (elements.restartButton) {
      const visible = !!gameOver;
      elements.restartButton.hidden = !visible;
      elements.restartButton.setAttribute('aria-hidden', visible ? 'false' : 'true');
    }
  }

  function setStatus(key, fallback, params, stateName = 'idle') {
    state.statusKey = key;
    state.statusFallback = typeof fallback === 'string' ? fallback : '';
    state.statusParams = params || null;
    state.statusState = stateName;
    if (stateName === 'gameover') {
      state.gameOver = true;
      state.active = false;
      state.pendingRespawn = false;
      resetInputState();
      persistMotocrossRecords();
      showGameOverOverlay(state.lastRunSummary);
    } else {
      state.gameOver = false;
      hideGameOverOverlay();
    }
    applyStatus();
  }

  function updateSpeedDisplay() {
    const { elements, speed } = state;
    if (!elements || !elements.speedValue) {
      return;
    }
    elements.speedValue.textContent = formatSpeed(speed);
  }

  function setDisplayedSpeed(value) {
    const normalized = Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
    if (state.speed === normalized) {
      return;
    }
    state.speed = normalized;
    updateSpeedDisplay();
    updateBestSpeedRecord(normalized);
  }

  function resetAverageSpeed(referenceX) {
    const baseX = Number.isFinite(referenceX)
      ? referenceX
      : state.bike?.position?.x ?? 0;
    state.speedStartX = baseX;
    state.speedElapsed = 0;
    setDisplayedSpeed(0);
  }

  function updateAverageSpeed(dt) {
    if (!Number.isFinite(dt) || dt <= 0 || !state?.bike) {
      return;
    }
    if (!Number.isFinite(state.speedStartX)) {
      state.speedStartX = state.bike.position.x;
    }
    state.speedElapsed += dt;
    const elapsed = state.speedElapsed;
    if (!Number.isFinite(elapsed) || elapsed <= 0) {
      setDisplayedSpeed(0);
      return;
    }
    const baseX = state.speedStartX;
    const distanceMeters = Math.max(0, (state.bike.position.x - baseX) * UNIT_TO_METERS);
    const averageSpeedMs = distanceMeters / elapsed;
    const averageSpeedKmh = averageSpeedMs * 3.6;
    const displaySpeed = averageSpeedKmh < MIN_DISPLAY_SPEED_KMH ? 0 : averageSpeedKmh;
    setDisplayedSpeed(displaySpeed);
  }

  function updateRunPeakSpeed() {
    if (!state?.bike || !state.bike.velocity) {
      return;
    }
    const vx = Number(state.bike.velocity.x) * UNIT_TO_METERS;
    const vy = Number(state.bike.velocity.y) * UNIT_TO_METERS;
    if (!Number.isFinite(vx) || !Number.isFinite(vy)) {
      return;
    }
    const speedMs = Math.sqrt(vx * vx + vy * vy);
    const speedKmh = speedMs * 3.6;
    if (!Number.isFinite(speedKmh)) {
      return;
    }
    if (speedKmh > state.runPeakSpeed) {
      state.runPeakSpeed = speedKmh;
      updateBestSpeedRecord(speedKmh);
    }
  }

  function captureRunSummary() {
    return {
      distance: sanitizeMotocrossRecordValue(state.maxDistance),
      speed: sanitizeMotocrossRecordValue(state.runPeakSpeed),
      bestDistance: state.bestRecords.bestDistance,
      bestSpeed: state.bestRecords.bestSpeed,
      newBestDistance: Boolean(state.runRecords?.distance),
      newBestSpeed: Boolean(state.runRecords?.speed)
    };
  }

  function updateDistanceDisplay() {
    const { elements, maxDistance } = state;
    if (!elements || !elements.distanceValue) {
      return;
    }
    elements.distanceValue.textContent = formatDistance(maxDistance);
  }

  function updateBestRecordsDisplay() {
    const { elements, bestRecords } = state;
    if (!elements) {
      return;
    }
    const emptyValue = getEmptyRecordValue();
    const distanceText = bestRecords.bestDistance > 0
      ? formatDistance(bestRecords.bestDistance)
      : emptyValue;
    const speedText = bestRecords.bestSpeed > 0
      ? formatDistance(bestRecords.bestSpeed)
      : emptyValue;
    if (elements.recordsDistanceValue) {
      elements.recordsDistanceValue.textContent = distanceText;
    }
    if (elements.recordsSpeedValue) {
      elements.recordsSpeedValue.textContent = speedText;
    }
    if (elements.gameOverBestDistanceValue) {
      elements.gameOverBestDistanceValue.textContent = distanceText;
    }
    if (elements.gameOverBestSpeedValue) {
      elements.gameOverBestSpeedValue.textContent = speedText;
    }
  }

  function updateGameOverOverlay(summary) {
    const { elements } = state;
    if (!elements) {
      return;
    }
    const emptyValue = getEmptyRecordValue();
    const distanceValue = summary && summary.distance > 0
      ? formatDistance(summary.distance)
      : emptyValue;
    const speedValue = summary && summary.speed > 0
      ? formatDistance(summary.speed)
      : emptyValue;
    if (elements.gameOverDistanceValue) {
      elements.gameOverDistanceValue.textContent = distanceValue;
    }
    if (elements.gameOverSpeedValue) {
      elements.gameOverSpeedValue.textContent = speedValue;
    }
    if (elements.gameOverDistanceBadge) {
      elements.gameOverDistanceBadge.hidden = !(summary && summary.newBestDistance);
    }
    if (elements.gameOverSpeedBadge) {
      elements.gameOverSpeedBadge.hidden = !(summary && summary.newBestSpeed);
    }
    updateBestRecordsDisplay();
  }

  function showGameOverOverlay(summary) {
    const overlay = state.elements?.gameOverOverlay;
    if (!overlay) {
      return;
    }
    updateGameOverOverlay(summary || state.lastRunSummary);
    overlay.hidden = false;
    overlay.setAttribute('aria-hidden', 'false');
  }

  function hideGameOverOverlay() {
    const overlay = state.elements?.gameOverOverlay;
    if (!overlay) {
      return;
    }
    overlay.hidden = true;
    overlay.setAttribute('aria-hidden', 'true');
  }

  function awardDistanceGachaTickets(distanceMeters) {
    const rewardConfig = state?.config?.rewards?.gacha;
    if (!rewardConfig) {
      return;
    }
    const interval = Number(rewardConfig.intervalMeters);
    const ticketAmount = Number(rewardConfig.ticketAmount);
    if (!Number.isFinite(interval) || interval <= 0 || !Number.isFinite(ticketAmount) || ticketAmount <= 0) {
      return;
    }
    const distance = Math.max(0, Number(distanceMeters) || 0);
    const reachedMilestones = Math.floor(distance / interval);
    const claimedMilestones = Math.max(0, Math.floor(Number(state.gachaMilestonesClaimed) || 0));
    if (reachedMilestones <= claimedMilestones) {
      return;
    }
    const newMilestones = reachedMilestones - claimedMilestones;
    const ticketsToGrant = newMilestones * ticketAmount;
    if (ticketsToGrant <= 0) {
      state.gachaMilestonesClaimed = reachedMilestones;
      return;
    }
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha !== 'function') {
      return;
    }
    let granted = 0;
    try {
      granted = awardGacha(ticketsToGrant, { unlockTicketStar: true });
    } catch (error) {
      console.warn('Motocross: unable to grant gacha tickets', error);
      granted = 0;
    }
    state.gachaMilestonesClaimed = reachedMilestones;
    if (!Number.isFinite(granted) || granted <= 0) {
      return;
    }
    if (typeof showToast === 'function') {
      const suffix = granted > 1 ? 's' : '';
      const message = translate(
        'scripts.arcade.motocross.rewards.gachaMilestone',
        'Motocross bonus! +{count} gacha ticket{suffix}.',
        { count: formatTicketCount(granted), suffix }
      );
      showToast(message);
    }
  }

  function updateTestButtonAvailability() {
    const { elements } = state;
    if (!elements || !elements.testButton) {
      return;
    }
    elements.testButton.hidden = true;
    elements.testButton.setAttribute('aria-hidden', 'true');
    elements.testButton.disabled = true;
    elements.testButton.setAttribute('aria-disabled', 'true');
  }

  function clearTestTimer() {
    if (state.test && state.test.timer) {
      window.clearTimeout(state.test.timer);
      state.test.timer = null;
    }
  }

  function focusTestBlock(entry) {
    if (!entry) {
      return;
    }
    const { track } = state;
    if (!track) {
      return;
    }
    const startX = Number.isFinite(entry.startX) ? entry.startX : 0;
    const endX = Number.isFinite(entry.endX) ? entry.endX : startX;
    const centerX = startX + (endX - startX) / 2;
    const sample = sampleTrack(track, centerX, entry.segmentHint);
    if (!sample || !sample.point) {
      return;
    }
    const point = sample.point;
    const tangent = sample.tangent || { x: 1, y: 0 };
    const angle = Math.atan2(tangent.y, tangent.x);
    state.bike.position.x = point.x;
    state.bike.position.y = point.y - WHEEL_RADIUS;
    state.bike.angle = angle;
    state.bike.velocity.x = 0;
    state.bike.velocity.y = 0;
    state.bike.angularVelocity = 0;
    state.wheels.back.segmentIndex = sample.index;
    state.wheels.front.segmentIndex = sample.index;
    updateWheelData();
    resetAverageSpeed(point.x);
    state.camera.x = point.x;
    state.camera.y = point.y - CAMERA_OFFSET_Y;
    state.camera.targetZoom = clamp(TEST_CAMERA_ZOOM, CAMERA_ZOOM_MIN, CAMERA_ZOOM_MAX);
    state.camera.zoom = state.camera.targetZoom;
    renderScene();
  }

  function stopTrackTest(statusOptions) {
    if (!state.test) {
      state.test = { active: false, timer: null, index: 0, resumeActive: false };
    }
    const wasActive = !!state.test.active;
    const resumeActive = state.test.resumeActive;
    clearTestTimer();
    state.test.active = false;
    state.test.index = 0;
    state.test.resumeActive = false;
    if (typeof resumeActive === 'boolean') {
      state.active = resumeActive;
    }
    state.pendingRespawn = false;
    updateTestButtonAvailability();
    updateSpeedDisplay();
    updateDistanceDisplay();
    if (wasActive) {
      if (statusOptions && typeof statusOptions === 'object') {
        setStatus(
          statusOptions.key,
          statusOptions.fallback,
          statusOptions.params,
          statusOptions.stateName || 'idle'
        );
      } else {
        applyStatus();
      }
    }
  }

  function runTrackTestStep(index) {
    if (!state.test) {
      state.test = { active: false, timer: null, index: 0, resumeActive: false };
    }
    if (!state.test.active) {
      return;
    }
    const blocks = state.track && Array.isArray(state.track.blocks) ? state.track.blocks : [];
    if (!blocks.length) {
      stopTrackTest({
        key: 'index.sections.motocross.ui.status.testingUnavailable',
        fallback: 'Générez une piste avant de lancer le test.',
        params: null,
        stateName: 'error'
      });
      return;
    }
    if (index >= blocks.length) {
      stopTrackTest({
        key: 'index.sections.motocross.ui.status.testingDone',
        fallback: 'Analyse terminée. Relancez la piste pour rouler !',
        params: null,
        stateName: 'success'
      });
      return;
    }
    const entry = blocks[index];
    focusTestBlock(entry);
    const params = {
      index: index + 1,
      total: blocks.length,
      name: formatBlockName(entry)
    };
    setStatus(
      'index.sections.motocross.ui.status.testingSegment',
      'Segment {index}/{total} : {name}',
      params,
      'testing'
    );
    state.test.index = index;
    clearTestTimer();
    state.test.timer = window.setTimeout(() => {
      runTrackTestStep(index + 1);
    }, TEST_STEP_INTERVAL);
  }

  function startTrackTest() {
    if (!state.test) {
      state.test = { active: false, timer: null, index: 0, resumeActive: false };
    }
    const hasTrack = state.track && Array.isArray(state.track.blocks) && state.track.blocks.length > 0;
    if (!hasTrack) {
      setStatus(
        'index.sections.motocross.ui.status.testingUnavailable',
        'Générez une piste avant de lancer le test.',
        null,
        'error'
      );
      return;
    }
    state.test.active = true;
    state.test.index = 0;
    state.test.resumeActive = state.active;
    state.active = false;
    state.pendingRespawn = false;
    clearTestTimer();
    setStatus(
      'index.sections.motocross.ui.status.testingStart',
      'Analyse de la piste en cours…',
      null,
      'testing'
    );
    updateTestButtonAvailability();
    runTrackTestStep(0);
  }

  function resizeCanvas() {
    const { elements } = state;
    if (!elements || !elements.canvas) {
      return;
    }
    const canvas = elements.canvas;
    const rect = canvas.getBoundingClientRect();
    const dpr = typeof window !== 'undefined' && window.devicePixelRatio ? window.devicePixelRatio : 1;
    state.devicePixelRatio = dpr;
    const width = Math.max(1, Math.floor(rect.width * dpr));
    const height = Math.max(1, Math.floor(rect.height * dpr));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
  }

  function updateWheelData() {
    const { bike, wheels } = state;
    const cos = Math.cos(bike.angle);
    const sin = Math.sin(bike.angle);
    const offsets = [
      { key: 'back', localX: -WHEEL_BASE / 2 },
      { key: 'front', localX: WHEEL_BASE / 2 }
    ];
    offsets.forEach(entry => {
      const local = { x: entry.localX, y: WHEEL_VERTICAL_OFFSET };
      const worldX = bike.position.x + local.x * cos - local.y * sin;
      const worldY = bike.position.y + local.x * sin + local.y * cos;
      const wheel = wheels[entry.key];
      wheel.pos.x = worldX;
      wheel.pos.y = worldY;
      wheel.r.x = worldX - bike.position.x;
      wheel.r.y = worldY - bike.position.y;
    });
  }

  function computeRespawnData(checkpointIndex) {
    const track = state.track;
    if (!track || !track.checkpoints || !track.checkpoints.length) {
      return null;
    }
    const index = clamp(checkpointIndex, 0, track.checkpoints.length - 1);
    const checkpoint = track.checkpoints[index];
    const backSample = sampleTrack(track, checkpoint.x, checkpoint.segmentIndex);
    const frontSample = sampleTrack(track, checkpoint.x + WHEEL_BASE, backSample.index);
    const backWheel = { x: backSample.point.x, y: backSample.point.y - WHEEL_RADIUS };
    const frontWheel = { x: frontSample.point.x, y: frontSample.point.y - WHEEL_RADIUS };
    const avgX = (backWheel.x + frontWheel.x) / 2;
    const avgY = (backWheel.y + frontWheel.y) / 2;
    const angle = Math.atan2(frontWheel.y - backWheel.y, frontWheel.x - backWheel.x);
    const sin = Math.sin(angle);
    const cos = Math.cos(angle);
    const position = {
      x: avgX + sin * WHEEL_VERTICAL_OFFSET,
      y: avgY - cos * WHEEL_VERTICAL_OFFSET
    };
    return {
      index,
      x: position.x,
      y: position.y,
      angle,
      segmentIndex: backSample.index
    };
  }

  function applyRespawn(boost = true) {
    const data = state.respawnData || computeRespawnData(0);
    if (!data) {
      return;
    }
    const { bike, wheels } = state;
    bike.position.x = data.x;
    bike.position.y = data.y;
    bike.angle = data.angle;
    bike.velocity.x = boost ? 110 : 0;
    bike.velocity.y = 0;
    bike.angularVelocity = 0;
    wheels.back.segmentIndex = data.segmentIndex;
    wheels.front.segmentIndex = data.segmentIndex;
    updateWheelData();
    state.camera.x = bike.position.x;
    state.camera.y = bike.position.y - CAMERA_OFFSET_Y;
    state.camera.zoom = 1;
    state.camera.targetZoom = 1;
    state.pendingRespawn = false;
    resetAverageSpeed(data.x);
  }

  function updateRespawnCheckpoint() {
    const data = computeRespawnData(state.currentCheckpoint);
    if (data) {
      state.respawnData = data;
    }
  }

  function updateCheckpointsProgress() {
    const { track, bike } = state;
    if (!track || !track.checkpoints || track.checkpoints.length < 2) {
      return;
    }
    const nextIndex = Math.min(track.checkpoints.length - 1, state.currentCheckpoint + 1);
    const nextCheckpoint = track.checkpoints[nextIndex];
    if (bike.position.x >= nextCheckpoint.x - 10) {
      state.currentCheckpoint = nextIndex;
      updateRespawnCheckpoint();
    }
  }

  function computeWheelContact(wheel, track, driveInput) {
    const sample = sampleTrack(track, wheel.pos.x, wheel.segmentIndex);
    wheel.segmentIndex = sample.index;
    const normal = sample.normal;
    const tangent = sample.tangent;
    const slopeAngle = Math.atan2(tangent.y, tangent.x);
    const toSurface = {
      x: wheel.pos.x - sample.point.x,
      y: wheel.pos.y - sample.point.y
    };
    const distance = toSurface.x * normal.x + toSurface.y * normal.y;
    const penetration = WHEEL_RADIUS - distance;
    wheel.onGround = penetration > 0;
    wheel.clearance = distance - WHEEL_RADIUS;
    let driveForce = 0;
    if (driveInput > 0) {
      const boost = state.boost?.factor || 1;
      driveForce = driveInput * ENGINE_FORCE * boost;
    } else if (driveInput < 0) {
      driveForce = driveInput * BRAKE_FORCE;
    }
    if (!wheel.onGround) {
      return {
        normalForce: 0,
        tangentForce: driveForce,
        correction: { x: 0, y: 0 },
        normal,
        tangent,
        clearance: wheel.clearance
      };
    }
    if (driveInput < 0) {
      driveForce *= GROUND_BRAKE_MULTIPLIER;
    }
    if (driveInput > 0) {
      const slopeFactor = clamp(1 + Math.max(0, Math.abs(tangent.y) - 0.2) * 2.4, 1, 3.5);
      driveForce *= slopeFactor;
    }
    const pointVelocity = {
      x: state.bike.velocity.x - state.bike.angularVelocity * wheel.r.y,
      y: state.bike.velocity.y + state.bike.angularVelocity * wheel.r.x
    };
    const normalVelocity = pointVelocity.x * normal.x + pointVelocity.y * normal.y;
    let normalForce = penetration * SPRING_STIFFNESS - normalVelocity * SPRING_DAMPING;
    if (normalForce < 0) {
      normalForce = 0;
    }
    const steepGripSettings = state.config?.steepSlopeGrip;
    let steepGripFactor = 0;
    if (steepGripSettings && tangent.y > 0 && normalForce > 0) {
      const thresholdRad = degreesToRadians(steepGripSettings.angleThresholdDeg);
      const maxAngleRad = Math.max(thresholdRad + 0.0001, degreesToRadians(steepGripSettings.maxAngleDeg));
      if (slopeAngle > thresholdRad) {
        const normalized = clamp((slopeAngle - thresholdRad) / (maxAngleRad - thresholdRad), 0, 1);
        steepGripFactor = normalized;
        normalForce += steepGripSettings.normalForceBoost * normalized;
      }
    }
    const tangentVelocity = pointVelocity.x * tangent.x + pointVelocity.y * tangent.y;
    const desiredTangentForce = -tangentVelocity * FRICTION_DAMPING + driveForce;
    const maxFriction = normalForce * FRICTION_COEFFICIENT;
    const tangentForce = clamp(desiredTangentForce, -maxFriction, maxFriction);
    let correctionScale = penetration * NORMAL_CORRECTION_FACTOR;
    if (steepGripFactor > 0 && steepGripSettings) {
      correctionScale += steepGripSettings.correctionBoost * steepGripFactor;
    }
    const correction = {
      x: normal.x * correctionScale,
      y: normal.y * correctionScale
    };
    return {
      normalForce,
      tangentForce,
      correction,
      normal,
      tangent,
      clearance: wheel.clearance,
      slopeAngle,
      steepGripFactor
    };
  }

  function stepPhysics(dt) {
    const accelerateHeld = state.input.accelerate > 0 ? 1 : 0;
    const brakeHeld = state.input.brake > 0 ? 1 : 0;
    updateBoostState(dt, accelerateHeld);
    const track = state.track;
    if (!track) {
      return;
    }
    const bike = state.bike;
    updateWheelData();
    const { back, front } = state.wheels;
    const controlDelta = accelerateHeld - brakeHeld;

    if (accelerateHeld && brakeHeld) {
      bike.velocity.x = 0;
      bike.velocity.y = 0;
      bike.angularVelocity = 0;
      updateWheelData();
      updateAverageSpeed(dt);
      setDisplayedSpeed(0);
      return;
    }

    const backProbe = computeWheelContact(back, track, 0);
    const frontProbe = computeWheelContact(front, track, 0);
    const airborne = !back.onGround && !front.onGround;
    const backClearance = Number.isFinite(backProbe?.clearance)
      ? backProbe.clearance
      : Number.POSITIVE_INFINITY;
    const frontClearance = Number.isFinite(frontProbe?.clearance)
      ? frontProbe.clearance
      : Number.POSITIVE_INFINITY;
    const wheelsCloseToGround = backClearance < GROUND_PROXIMITY_THRESHOLD
      && frontClearance < GROUND_PROXIMITY_THRESHOLD;
    const minWheelClearance = Math.min(backClearance, frontClearance);
    const nearGroundByWheels = Number.isFinite(minWheelClearance)
      && minWheelClearance < UPSIDE_DOWN_WHEEL_CLEARANCE;
    let centerClearance = Number.POSITIVE_INFINITY;
    if (track && back && typeof back.segmentIndex === 'number') {
      const centerSample = sampleTrack(track, bike.position.x, back.segmentIndex);
      if (centerSample && centerSample.normal) {
        const toCenterX = bike.position.x - centerSample.point.x;
        const toCenterY = bike.position.y - centerSample.point.y;
        const projected = toCenterX * centerSample.normal.x + toCenterY * centerSample.normal.y;
        if (Number.isFinite(projected)) {
          centerClearance = Math.max(0, projected);
        }
      }
    }
    const nearGroundByCenter = Number.isFinite(centerClearance)
      && centerClearance < UPSIDE_DOWN_CENTER_CLEARANCE;
    const nearGroundWhileUpsideDown = nearGroundByWheels || nearGroundByCenter;
    const allowRotationControl = !wheelsCloseToGround;
    const driveControl = airborne ? 0 : controlDelta;
    const tiltControl = allowRotationControl ? controlDelta : 0;

    const backContact = airborne ? backProbe : computeWheelContact(back, track, driveControl);
    const frontContact = airborne
      ? frontProbe
      : computeWheelContact(front, track, driveControl * 0.85);

    let totalForceX = 0;
    let totalForceY = MASS * GRAVITY;
    const tiltMultiplier = airborne ? 0 : 0.35;
    let totalTorque = tiltControl * TILT_TORQUE * tiltMultiplier;
    let correctionX = 0;
    let correctionY = 0;
    let correctionCount = 0;
    const steepGripSettings = state.config?.steepSlopeGrip;
    let slopeAngularDamping = 0;
    let frontSteepGrip = 0;

    [
      { wheel: back, contact: backContact },
      { wheel: front, contact: frontContact }
    ].forEach(entry => {
      const { wheel, contact } = entry;
      if (!contact) {
        return;
      }
      totalForceX += contact.normalForce * contact.normal.x + contact.tangentForce * contact.tangent.x;
      totalForceY += contact.normalForce * contact.normal.y + contact.tangentForce * contact.tangent.y;
      const torqueNormal = wheel.r.x * contact.normal.y - wheel.r.y * contact.normal.x;
      const torqueTangent = wheel.r.x * contact.tangent.y - wheel.r.y * contact.tangent.x;
      totalTorque += torqueNormal * contact.normalForce + torqueTangent * contact.tangentForce;
      if (wheel.onGround) {
        correctionX += contact.correction.x;
        correctionY += contact.correction.y;
        correctionCount += 1;
      }
      if (steepGripSettings && contact.steepGripFactor > 0) {
        const damping = steepGripSettings.angularDampingMultiplier * contact.steepGripFactor;
        slopeAngularDamping = Math.max(slopeAngularDamping, damping);
        if (wheel === front) {
          frontSteepGrip = Math.max(frontSteepGrip, contact.steepGripFactor);
        }
      }
    });

    if (steepGripSettings && frontSteepGrip > 0 && totalTorque > 0) {
      totalTorque -= steepGripSettings.torqueAssist * frontSteepGrip;
    }

    if (airborne) {
      const rotationInput = allowRotationControl ? clamp(controlDelta, -1, 1) : 0;
      const targetAngularVelocity = rotationInput * AIR_ROTATION_MAX_SPEED;
      const maxDelta = AIR_ROTATION_ACCEL * dt;
      const deltaAngular = clamp(targetAngularVelocity - bike.angularVelocity, -maxDelta, maxDelta);
      bike.angularVelocity += deltaAngular;
    }

    const accelerationX = totalForceX / MASS;
    const accelerationY = totalForceY / MASS;
    bike.velocity.x += accelerationX * dt;
    bike.velocity.y += accelerationY * dt;
    bike.angularVelocity += (totalTorque / BIKE_INERTIA) * dt;

    bike.velocity.x *= 1 - LINEAR_DAMPING * dt;
    bike.velocity.y *= 1 - LINEAR_DAMPING * dt;
    const angularDampingBase = ANGULAR_DAMPING + slopeAngularDamping;
    const angularDampingFactor = clamp(angularDampingBase * dt, 0, 0.95);
    bike.angularVelocity *= 1 - angularDampingFactor;

    if (airborne) {
      bike.angularVelocity = clamp(bike.angularVelocity, -AIR_ROTATION_MAX_SPEED, AIR_ROTATION_MAX_SPEED);
    }

    bike.position.x += bike.velocity.x * dt;
    bike.position.y += bike.velocity.y * dt;
    bike.angle += bike.angularVelocity * dt;

    if (correctionCount > 0) {
      bike.position.x += correctionX / correctionCount;
      bike.position.y += correctionY / correctionCount;
    }

    updateWheelData();
    updateAverageSpeed(dt);
    updateRunPeakSpeed();

    const referenceX = Number.isFinite(state.runStartX) ? state.runStartX : 0;
    const progress = bike.position.x - referenceX;
    if (Number.isFinite(progress)) {
      const progressMeters = progress * UNIT_TO_METERS;
      state.maxDistance = Math.max(state.maxDistance, progressMeters);
      updateBestDistanceRecord(state.maxDistance);
      awardDistanceGachaTickets(state.maxDistance);
    }

    updateCheckpointsProgress();
    ensureTrackAhead();

    if (bike.position.y - WHEEL_RADIUS > state.fallThreshold) {
      state.pendingRespawn = true;
    }

    if (!state.gameOver) {
      const normalizedAngle = Math.atan2(Math.sin(bike.angle), Math.cos(bike.angle));
      const isUpsideDown = Math.abs(normalizedAngle) > Math.PI * 0.75;
      const falling = bike.velocity.y > 0;
      if (isUpsideDown && (!airborne || (falling && nearGroundWhileUpsideDown))) {
        state.lastRunSummary = captureRunSummary();
        const formattedDistance = formatDistance(state.maxDistance);
        setStatus(
          'index.sections.motocross.ui.status.gameOver',
          'Game over! Distance travelled: {distance} m.',
          { distance: formattedDistance },
          'gameover'
        );
      }
    }
  }

  function measureTrackWindow(track, centerX) {
    if (!track || !Number.isFinite(centerX)) {
      return null;
    }
    const minX = centerX - CAMERA_TRACK_WINDOW_BEHIND;
    const maxX = centerX + CAMERA_TRACK_WINDOW_AHEAD;
    const segments = Array.isArray(track.segments) ? track.segments : null;
    let minY = Number.POSITIVE_INFINITY;
    let maxY = Number.NEGATIVE_INFINITY;
    let found = false;
    if (segments && segments.length) {
      for (let i = 0; i < segments.length; i += 1) {
        const segment = segments[i];
        if (!segment) {
          continue;
        }
        if (segment.maxX < minX) {
          continue;
        }
        if (segment.minX > maxX && found) {
          break;
        }
        const p0 = segment.p0;
        const p1 = segment.p1;
        if (p0 && Number.isFinite(p0.y)) {
          minY = Math.min(minY, p0.y);
          maxY = Math.max(maxY, p0.y);
          found = true;
        }
        if (p1 && Number.isFinite(p1.y)) {
          minY = Math.min(minY, p1.y);
          maxY = Math.max(maxY, p1.y);
          found = true;
        }
      }
    }
    if (!found && Array.isArray(track.points)) {
      for (let i = 0; i < track.points.length; i += 1) {
        const point = track.points[i];
        if (!Array.isArray(point) || point.length < 2) {
          continue;
        }
        const [x, y] = point;
        if (x < minX) {
          continue;
        }
        if (x > maxX && found) {
          break;
        }
        if (Number.isFinite(y)) {
          minY = Math.min(minY, y);
          maxY = Math.max(maxY, y);
          found = true;
        }
      }
    }
    if (!found || !Number.isFinite(minY) || !Number.isFinite(maxY)) {
      return null;
    }
    return { minY, maxY };
  }

  function computeCameraZoom(targetY) {
    const { track, ctx, devicePixelRatio, camera } = state;
    if (!track || !ctx || typeof targetY !== 'number') {
      return camera?.zoom || 1;
    }
    const canvas = ctx.canvas;
    if (!canvas) {
      return camera?.zoom || 1;
    }
    const height = canvas.height / (devicePixelRatio || 1);
    if (!Number.isFinite(height) || height <= 0) {
      return camera?.zoom || 1;
    }
    const windowBounds = measureTrackWindow(track, state.bike.position.x);
    if (!windowBounds) {
      return camera?.zoom || 1;
    }
    let allowedZoom = CAMERA_ZOOM_MAX;
    const topAvailable = height * CAMERA_VERTICAL_ANCHOR;
    const bottomAvailable = height * (1 - CAMERA_VERTICAL_ANCHOR);
    if (topAvailable > 0) {
      const topDelta = targetY - (windowBounds.minY - CAMERA_TOP_MARGIN);
      if (topDelta > 0) {
        allowedZoom = Math.min(allowedZoom, topAvailable / topDelta);
      }
    }
    if (bottomAvailable > 0) {
      const bottomDelta = (windowBounds.maxY + CAMERA_BOTTOM_MARGIN) - targetY;
      if (bottomDelta > 0) {
        allowedZoom = Math.min(allowedZoom, bottomAvailable / bottomDelta);
      }
    }
    if (!Number.isFinite(allowedZoom) || allowedZoom <= 0) {
      allowedZoom = camera?.zoom || 1;
    }
    return clamp(allowedZoom, CAMERA_ZOOM_MIN, CAMERA_ZOOM_MAX);
  }

  function updateCamera(dt) {
    const { camera, bike } = state;
    const lookAhead = clamp(bike.velocity.x * CAMERA_LOOK_AHEAD, -160, 240);
    const targetX = bike.position.x + lookAhead;
    const targetY = bike.position.y - CAMERA_OFFSET_Y;
    const smooth = 1 - Math.exp(-CAMERA_SMOOTH * dt);
    camera.x = lerp(camera.x, targetX, smooth);
    camera.y = lerp(camera.y, targetY, smooth);
    const zoomTarget = computeCameraZoom(targetY);
    camera.targetZoom = zoomTarget;
    const zoomSmooth = 1 - Math.exp(-CAMERA_ZOOM_SMOOTH * dt);
    camera.zoom = lerp(camera.zoom, zoomTarget, zoomSmooth);
  }

  function renderTrack(ctx, track, zoom = 1) {
    if (!track || !track.points.length) {
      return;
    }
    const strokeWidth = 4 / (zoom || 1);
    ctx.lineWidth = Math.max(1, strokeWidth);
    ctx.strokeStyle = '#60a5fa';
    ctx.beginPath();
    const [firstX, firstY] = track.points[0];
    ctx.moveTo(firstX, firstY);
    for (let i = 1; i < track.points.length; i += 1) {
      const [x, y] = track.points[i];
      ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.lineWidth = Math.max(0.5, strokeWidth * 0.25);
    ctx.strokeStyle = 'rgba(94, 234, 212, 0.35)';
    track.checkpoints.forEach(checkpoint => {
      ctx.beginPath();
      ctx.moveTo(checkpoint.x, checkpoint.y - 8);
      ctx.lineTo(checkpoint.x, checkpoint.y + 32);
      ctx.stroke();
    });
  }

  function renderBike(ctx) {
    const { bike, wheels, bikeSprite } = state;
    if (!wheels || !bike) {
      return;
    }
    const back = wheels.back;
    const front = wheels.front;
    if (!back || !front || !back.pos || !front.pos) {
      return;
    }

    const sprite = bikeSprite;
    const spriteImage = sprite?.image;
    const isImageInstance = typeof Image === 'function' ? spriteImage instanceof Image : !!spriteImage;
    const spriteReady = sprite
      && sprite.loaded
      && isImageInstance
      && sprite.width > 0
      && sprite.height > 0;

    if (!spriteReady) {
      ctx.fillStyle = '#0f172a';
      ctx.beginPath();
      ctx.arc(back.pos.x, back.pos.y, WHEEL_RADIUS, 0, Math.PI * 2);
      ctx.fill();
      ctx.beginPath();
      ctx.arc(front.pos.x, front.pos.y, WHEEL_RADIUS, 0, Math.PI * 2);
      ctx.fill();

      ctx.save();
      ctx.translate(bike.position.x, bike.position.y);
      ctx.rotate(bike.angle);
      ctx.fillStyle = '#38bdf8';
      ctx.fillRect(-CHASSIS_WIDTH / 2, -CHASSIS_HEIGHT / 2, CHASSIS_WIDTH, CHASSIS_HEIGHT);
      ctx.fillStyle = '#bae6fd';
      ctx.fillRect(-CHASSIS_WIDTH / 4, -CHASSIS_HEIGHT * 0.75, CHASSIS_WIDTH / 2, CHASSIS_HEIGHT / 2);
      ctx.restore();
      ensureBikeSprite();
      return;
    }

    const image = sprite.image;
    const width = sprite.width;
    const height = sprite.height;

    const anchors = BIKE_SPRITE_ANCHORS || {};
    const safeRatio = (value, fallback) => {
      const numeric = Number.isFinite(value) ? value : fallback;
      return clamp(numeric, 0, 1);
    };

    const backRatioX = safeRatio(anchors.backWheel?.x, 0.2);
    const backRatioY = safeRatio(anchors.backWheel?.y, 0.82);
    const frontRatioX = safeRatio(anchors.frontWheel?.x, 0.8);
    const frontRatioY = safeRatio(anchors.frontWheel?.y, backRatioY);

    const backPixel = {
      x: backRatioX * width,
      y: backRatioY * height
    };
    const frontPixel = {
      x: frontRatioX * width,
      y: frontRatioY * height
    };

    const pixelDX = frontPixel.x - backPixel.x;
    const pixelDY = frontPixel.y - backPixel.y;
    const pixelDistance = Math.hypot(pixelDX, pixelDY);

    let actualDX = front.pos.x - back.pos.x;
    let actualDY = front.pos.y - back.pos.y;
    let actualDistance = Math.hypot(actualDX, actualDY);

    if (!(pixelDistance > 0)) {
      ctx.fillStyle = '#0f172a';
      ctx.beginPath();
      ctx.arc(back.pos.x, back.pos.y, WHEEL_RADIUS, 0, Math.PI * 2);
      ctx.fill();
      ctx.beginPath();
      ctx.arc(front.pos.x, front.pos.y, WHEEL_RADIUS, 0, Math.PI * 2);
      ctx.fill();
      return;
    }

    if (!(actualDistance > 0.001)) {
      actualDistance = WHEEL_BASE;
      actualDX = Math.cos(bike.angle) * actualDistance;
      actualDY = Math.sin(bike.angle) * actualDistance;
    }

    const axisX = {
      x: actualDX / actualDistance,
      y: actualDY / actualDistance
    };
    const axisY = {
      x: -axisX.y,
      y: axisX.x
    };

    const scale = actualDistance / pixelDistance;

    ctx.save();
    ctx.translate(back.pos.x, back.pos.y);
    ctx.transform(
      axisX.x * scale,
      axisX.y * scale,
      axisY.x * scale,
      axisY.y * scale,
      0,
      0
    );
    ctx.drawImage(image, -backPixel.x, -backPixel.y, width, height);
    ctx.restore();
  }

  function renderScene() {
    const { ctx, track, camera, devicePixelRatio } = state;
    if (!ctx) {
      return;
    }
    const canvas = ctx.canvas;
    const width = canvas.width / devicePixelRatio;
    const height = canvas.height / devicePixelRatio;

    ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
    ctx.clearRect(0, 0, width, height);

    renderBackground(ctx, width, height);

    const zoom = camera && Number.isFinite(camera.zoom) ? camera.zoom : 1;
    ctx.save();
    ctx.translate(width * 0.5, height * CAMERA_VERTICAL_ANCHOR);
    ctx.scale(zoom, zoom);
    ctx.translate(-camera.x, -camera.y);
    if (track) {
      renderTrack(ctx, track, zoom);
    }
    renderBike(ctx);
    ctx.restore();
  }

  function tick(timestamp) {
    if (!state.initialized) {
      return;
    }
    window.requestAnimationFrame(tick);
    if (!state.ctx) {
      return;
    }
    if (state.lastTimestamp == null) {
      state.lastTimestamp = timestamp;
    }
    let delta = (timestamp - state.lastTimestamp) / 1000;
    state.lastTimestamp = timestamp;
    if (!Number.isFinite(delta) || delta <= 0) {
      delta = PHYSICS_STEP;
    }
    delta = Math.min(delta, MAX_FRAME_STEP);

    if (state.pendingRespawn) {
      if (!state.gameOver) {
        applyRespawn(true);
      } else {
        state.pendingRespawn = false;
      }
    }

    if (state.active) {
      state.accumulator += delta;
      const maxAccumulation = PHYSICS_STEP * 5;
      if (state.accumulator > maxAccumulation) {
        state.accumulator = maxAccumulation;
      }
      while (state.accumulator >= PHYSICS_STEP) {
        stepPhysics(PHYSICS_STEP);
        state.accumulator -= PHYSICS_STEP;
      }
      updateCamera(delta);
    }

    renderScene();
    updateDistanceDisplay();
  }

  function pickBackgroundSource(forceNew) {
    const pool = Array.isArray(BACKGROUND_OPTIONS) ? BACKGROUND_OPTIONS : [];
    if (pool.length === 0) {
      return null;
    }
    let background = state.background;
    if (!background || forceNew) {
      background = createBackgroundState();
      state.background = background;
    }
    const shouldForce = forceNew || !background.loaded;
    if (!shouldForce && background.loaded && background.image) {
      return background.url;
    }
    let choice = pool[Math.floor(Math.random() * pool.length)];
    if (shouldForce && pool.length > 1 && choice === background.url) {
      const alternatives = pool.filter(item => item !== background.url);
      if (alternatives.length) {
        choice = alternatives[Math.floor(Math.random() * alternatives.length)];
      }
    }
    background.url = choice;
    background.loaded = false;
    background.loading = true;
    background.width = 0;
    background.height = 0;
    const image = new Image();
    if ('decoding' in image) {
      image.decoding = 'async';
    }
    background.image = image;
    image.addEventListener('load', () => {
      if (state.background !== background || background.image !== image) {
        return;
      }
      background.loaded = true;
      background.loading = false;
      background.width = image.naturalWidth || image.width || 0;
      background.height = image.naturalHeight || image.height || 0;
      renderScene();
    });
    image.addEventListener('error', () => {
      if (state.background !== background || background.image !== image) {
        return;
      }
      background.loaded = false;
      background.loading = false;
    });
    image.src = choice;
    return choice;
  }

  function ensureBikeSprite(forceReload = false) {
    if (!BIKE_SPRITE_SOURCE) {
      return;
    }
    let sprite = state.bikeSprite;
    if (!sprite) {
      sprite = createBikeSpriteState();
      state.bikeSprite = sprite;
    }
    if (!forceReload && sprite.loaded && sprite.image) {
      return;
    }
    if (!forceReload && sprite.loading && sprite.image) {
      return;
    }
    sprite.loaded = false;
    sprite.loading = true;
    sprite.width = 0;
    sprite.height = 0;
    const image = new Image();
    if ('decoding' in image) {
      image.decoding = 'async';
    }
    sprite.image = image;
    image.addEventListener('load', () => {
      if (state.bikeSprite !== sprite || sprite.image !== image) {
        return;
      }
      sprite.loaded = true;
      sprite.loading = false;
      sprite.width = image.naturalWidth || image.width || 0;
      sprite.height = image.naturalHeight || image.height || 0;
      renderScene();
    });
    image.addEventListener('error', () => {
      if (state.bikeSprite !== sprite || sprite.image !== image) {
        return;
      }
      sprite.loaded = false;
      sprite.loading = false;
    });
    image.src = BIKE_SPRITE_SOURCE;
  }

  function renderBackground(ctx, width, height) {
    const background = state.background;
    const cameraX = state.camera?.x || 0;
    let drawn = false;
    if (background && background.loaded && background.image && background.width > 0 && background.height > 0) {
      const scale = height / background.height;
      const scaledWidth = background.width * scale;
      if (scaledWidth > 0 && Number.isFinite(scale)) {
        const offset = ((cameraX * BACKGROUND_SCROLL_RATIO) % scaledWidth + scaledWidth) % scaledWidth;
        const startX = -offset;
        const scaledHeight = height;
        for (let drawX = startX; drawX < width; drawX += scaledWidth) {
          ctx.drawImage(background.image, drawX, height - scaledHeight, scaledWidth, scaledHeight);
        }
        drawn = true;
      }
    }
    if (!drawn) {
      const gradient = ctx.createLinearGradient(0, 0, 0, height);
      gradient.addColorStop(0, '#0b1224');
      gradient.addColorStop(1, '#050811');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, width, height);
    }
    const overlay = ctx.createLinearGradient(0, 0, 0, height);
    overlay.addColorStop(0, 'rgba(8, 12, 24, 0.35)');
    overlay.addColorStop(1, 'rgba(5, 8, 18, 0.85)');
    ctx.fillStyle = overlay;
    ctx.fillRect(0, 0, width, height);
  }

  function generateTrack(options = {}) {
    const silent = !!(options && options.silent);
    stopTrackTest();
    persistMotocrossRecords();
    pickBackgroundSource(!silent);
    ensureBikeSprite(false);
    const seedValue = createEntropySeed();
    const track = buildTrack(seedValue, DEFAULT_DIFFICULTY);
    if (!track) {
      setStatus(
        'index.sections.motocross.ui.status.failed',
        'Impossible de générer une nouvelle piste pour le moment.',
        null,
        'error'
      );
      return;
    }
    state.track = track;
    state.currentCheckpoint = 0;
    state.respawnData = computeRespawnData(0);
    state.fallThreshold = track.maxY;
    state.trackStartX = Array.isArray(track.points) && track.points.length ? track.points[0][0] : 0;
    state.runStartX = state.respawnData ? state.respawnData.x : state.trackStartX;
    state.maxDistance = 0;
    state.runPeakSpeed = 0;
    state.gachaMilestonesClaimed = 0;
    state.lastRunSummary = null;
    if (state.runRecords) {
      state.runRecords.distance = false;
      state.runRecords.speed = false;
    }
    state.gameOver = false;
    state.pendingRespawn = false;
    state.active = true;
    hideGameOverOverlay();
    resetInputState();
    state.lastGeneratedCount = Array.isArray(track.usedIds) ? track.usedIds.length : 0;
    updateRespawnCheckpoint();
    applyRespawn(false);
    updateDistanceDisplay();
    updateTestButtonAvailability();
    if (!silent) {
      const params = { count: track.usedIds.length };
      setStatus(
        'index.sections.motocross.ui.status.generated',
        'Piste prête, bon ride !',
        params,
        'success'
      );
    } else {
      applyStatus();
    }
  }

  function handleGenerate() {
    generateTrack();
  }

  function handleTest() {
    if (state.test && state.test.active) {
      stopTrackTest({
        key: 'index.sections.motocross.ui.status.testingStopped',
        fallback: 'Test interrompu. Relancez le test ou roulez !',
        params: null,
        stateName: 'idle'
      });
      return;
    }
    startTrackTest();
  }

  function handleRestart() {
    stopTrackTest();
    persistMotocrossRecords();
    hideGameOverOverlay();
    if (!state.track) {
      generateTrack();
      return;
    }
    state.gameOver = false;
    state.active = true;
    state.pendingRespawn = false;
    state.maxDistance = 0;
    state.runPeakSpeed = 0;
    state.gachaMilestonesClaimed = 0;
    state.lastRunSummary = null;
    if (state.runRecords) {
      state.runRecords.distance = false;
      state.runRecords.speed = false;
    }
    state.currentCheckpoint = 0;
    const restartRespawn = computeRespawnData(0);
    if (restartRespawn) {
      state.respawnData = restartRespawn;
      state.runStartX = restartRespawn.x;
    } else {
      state.runStartX = state.trackStartX;
    }
    updateRespawnCheckpoint();
    applyRespawn(false);
    resetInputState();
    updateDistanceDisplay();
    updateTestButtonAvailability();
    const hasCount = Number.isFinite(state.lastGeneratedCount) && state.lastGeneratedCount > 0;
    const params = hasCount ? { count: state.lastGeneratedCount } : null;
    setStatus(
      'index.sections.motocross.ui.status.generated',
      'Track ready for a new run!',
      params,
      'success'
    );
  }

  function handleGameOverRetry(event) {
    if (event && typeof event.preventDefault === 'function') {
      event.preventDefault();
    }
    handleRestart();
  }

  function handleGameOverDismiss(event) {
    if (event && typeof event.preventDefault === 'function') {
      event.preventDefault();
    }
    hideGameOverOverlay();
  }

  function handleResize() {
    resizeCanvas();
    renderScene();
  }

  function handlePointerDown(event) {
    if (!event || event.pointerType !== 'touch') {
      return;
    }
    updatePointerSide(event.pointerId, event.clientX);
    if (typeof event.preventDefault === 'function') {
      event.preventDefault();
    }
  }

  function handlePointerMove(event) {
    if (!event || event.pointerType !== 'touch') {
      return;
    }
    updatePointerSide(event.pointerId, event.clientX);
  }

  function handlePointerUp(event) {
    if (!event || event.pointerType !== 'touch') {
      return;
    }
    releasePointer(event.pointerId);
  }

  function handleKeyDown(event) {
    if (!event) {
      return;
    }
    const code = event.code;
    switch (code) {
      case 'ArrowRight':
        setKeyboardInput('accelerate', 1);
        event.preventDefault();
        break;
      case 'ArrowLeft':
        setKeyboardInput('brake', 1);
        event.preventDefault();
        break;
      case 'KeyR':
        state.pendingRespawn = true;
        break;
      default:
        break;
    }
  }

  function handleKeyUp(event) {
    if (!event) {
      return;
    }
    const code = event.code;
    switch (code) {
      case 'ArrowRight':
        setKeyboardInput('accelerate', 0);
        event.preventDefault();
        break;
      case 'ArrowLeft':
        setKeyboardInput('brake', 0);
        event.preventDefault();
        break;
      default:
        break;
    }
  }

  function attachListeners() {
    const { elements } = state;
    if (!elements) {
      return;
    }
    elements.generateButton?.addEventListener('click', handleGenerate);
    elements.restartButton?.addEventListener('click', handleRestart);
    elements.testButton?.addEventListener('click', handleTest);
    elements.gameOverRetryButton?.addEventListener('click', handleGameOverRetry);
    elements.gameOverDismissButton?.addEventListener('click', handleGameOverDismiss);
    window.addEventListener('resize', handleResize);
    window.addEventListener('pointerdown', handlePointerDown, { passive: false });
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
    window.addEventListener('pointerleave', handlePointerUp);
    window.addEventListener('pointerout', handlePointerUp);
    window.addEventListener('keydown', handleKeyDown, { passive: false });
    window.addEventListener('keyup', handleKeyUp);
    window.addEventListener('i18n:languagechange', () => {
      applyStatus();
      updateBestRecordsDisplay();
      updateGameOverOverlay(state.lastRunSummary);
    });
  }

  function initialize() {
    if (state.initialized) {
      return;
    }
    state.elements = getElements();
    if (!state.elements || !state.elements.canvas) {
      return;
    }
    state.ctx = state.elements.canvas.getContext('2d');
    state.initialized = true;
    resetInputState();
    attachListeners();
    resizeCanvas();
    updateSpeedDisplay();
    updateDistanceDisplay();
    updateBestRecordsDisplay();
    updateGameOverOverlay(state.lastRunSummary);
    updateTestButtonAvailability();
    applyStatus();
    ensureBikeSprite(false);
    generateTrack({ silent: true });
    window.requestAnimationFrame(tick);
  }

  const api = {
    onEnter() {
      initialize();
      resetInputState();
      state.active = true;
      state.lastTimestamp = null;
      applyStatus();
    },
    onLeave() {
      stopTrackTest();
      state.active = false;
      resetInputState();
      hideGameOverOverlay();
      persistMotocrossRecords();
    },
    syncRecordsFromSave() {
      hydrateMotocrossRecords();
    }
  };

  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', () => {
      persistMotocrossRecords(true);
    });
  }

  loadConfig();
  window.motocrossArcade = api;
})();
