(() => {
  const PIXELS_PER_METER = 60;
  const BASE_GRAVITY = 900;
  const DIVE_MULTIPLIER = 3.2;
  const BASE_PUSH_ACCEL = 42;
  const START_GROUND_SPEED = 90;
  const INITIAL_LAUNCH_SPEED = 360;
  const INITIAL_LAUNCH_HEIGHT_OFFSET = 12;
  const START_SCREEN_X_RATIO = 0.05;
  const START_HEIGHT_RATIO = 0.12;
  const START_MIN_CLEARANCE = 48;
  const MIN_LANDING_SPEED = 22;
  const GROUND_DRAG = 0.9978;
  const HOLDING_DRAG = 0.9991;
  const AIR_DRAG = 0.999;
  const DIVE_PULL_STRENGTH = 0.85;
  const JUMP_BASE = 180;
  const JUMP_SPEED_RATIO = 0.4;
  const MAX_JUMP_IMPULSE = 380;
  const AUTO_JUMP_SPEED_THRESHOLD = 120;
  const TERRAIN_SAMPLE_SPACING = 36;
  const MIN_WAVE_WAVELENGTH = 640;
  const MAX_WAVE_WAVELENGTH = 1240;
  const MIN_AMPLITUDE_RATIO = 0.12;
  const MAX_AMPLITUDE_RATIO = 0.26;
  const AMPLITUDE_LENGTH_INFLUENCE = 0.85;
  const MAX_WAVE_EXTENSION_FACTOR = 1.75;
  const WAVE_AMPLITUDE_SPEED_MIN_KMH = 100;
  const WAVE_AMPLITUDE_SPEED_MAX_KMH = 300;
  const WAVE_AMPLITUDE_MAX_MULTIPLIER = 3;
  const CAMERA_LERP_MIN = 0.08;
  const CAMERA_LERP_MAX = 0.25;
  const CAMERA_SCALE_LERP_MIN = 0.05;
  const CAMERA_SCALE_LERP_MAX = 0.18;
  const CAMERA_VERTICAL_LERP_MIN = 0.08;
  const CAMERA_VERTICAL_LERP_MAX = 0.22;
  const BASE_MIN_CAMERA_SCALE = 0.55;
  const ABSOLUTE_MIN_CAMERA_SCALE = 0.38;
  const MAX_DYNAMIC_SPAN_MULTIPLIER = 1.8;
  const SPEED_ZOOM_MIN_KMH = 110;
  const SPEED_ZOOM_MAX_KMH = 420;
  const ALTITUDE_ZOOM_MIN_METERS = 4;
  const ALTITUDE_ZOOM_MAX_METERS = 30;
  const MIN_VISIBLE_SPAN_RATIO = 0.9;
  const MAX_VISIBLE_SPAN_RATIO = 2.8;
  const CAMERA_TOP_MARGIN_RATIO = 0.18;
  const CAMERA_BOTTOM_MARGIN_RATIO = 0.28;
  const GROUND_INFLUENCE_RATIO = 0.35;
  const MAX_WAVE_TOP_SCREEN_RATIO = 0.45; // highest visible crest stays within the bottom 45% of the screen
  const MAX_FRAME_DELTA = 1 / 30;
  const AIR_PRESS_FORWARD_IMPULSE = 120;
  const AIR_PRESS_DOWN_IMPULSE = 260;
  const GROUND_PRESS_FORWARD_IMPULSE = 140;
  const DOWNHILL_IMPULSE_MULTIPLIER = 1.35;
  const UPHILL_DECEL_FACTOR_MIN = 0.35;
  const UPHILL_DECEL_SPEED = 420;
  const UPHILL_DRAG_BONUS = 0.9995;
  const UPHILL_PRESS_GRACE_DURATION = 2;

  const BALL_COLOR_CHANGE_INTERVAL_MS = 60000;
  const BALL_COLOR_TRANSITION_MS = 5000;
  const BALL_TRAIL_MAX_POINTS = 36;
  const BALL_TRAIL_MAX_AGE_MS = 320;
  const BALL_TRAIL_SAMPLE_INTERVAL_MS = 30;

  const ENERGY_BALL_SPRITE = (() => {
    if (typeof Image === 'undefined') {
      return {
        image: null,
        loaded: false,
        frameWidth: 32,
        frameHeight: 32,
        trailHeight: 128,
        normalTrailOffsetY: 32,
        speedTrailOffsetY: 160,
        colorCount: 6
      };
    }
    const image = new Image();
    image.src = 'Assets/Sprites/energy_ball2.png';
    const sprite = {
      image,
      loaded: false,
      frameWidth: 32,
      frameHeight: 32,
      trailHeight: 128,
      normalTrailOffsetY: 32,
      speedTrailOffsetY: 160,
      colorCount: 6
    };
    const markLoaded = () => {
      sprite.loaded = true;
    };
    if (typeof image.decode === 'function') {
      image.decode().then(markLoaded).catch(() => {
        sprite.loaded = image.complete && image.naturalWidth > 0;
      });
    }
    image.addEventListener('load', markLoaded, { once: true });
    image.addEventListener('error', () => {
      sprite.loaded = false;
    }, { once: true });
    if (image.complete && image.naturalWidth > 0) {
      sprite.loaded = true;
    }
    return sprite;
  })();

  const ENERGY_BALL_FALLBACK_COLORS = [
    { core: '#f3fbff', rim: '#6fd0ff', trail: 'rgba(104, 214, 255, 0.75)', glow: 'rgba(140, 220, 255, 0.7)' },
    { core: '#f5fff3', rim: '#5fe074', trail: 'rgba(120, 246, 160, 0.75)', glow: 'rgba(136, 246, 184, 0.7)' },
    { core: '#fff7ef', rim: '#ffb34d', trail: 'rgba(255, 196, 120, 0.75)', glow: 'rgba(255, 204, 136, 0.7)' },
    { core: '#fff0fb', rim: '#f172ff', trail: 'rgba(252, 140, 255, 0.75)', glow: 'rgba(252, 168, 255, 0.7)' },
    { core: '#f0f6ff', rim: '#9b89ff', trail: 'rgba(168, 156, 255, 0.75)', glow: 'rgba(176, 164, 255, 0.7)' },
    { core: '#f8ffef', rim: '#9ded3f', trail: 'rgba(180, 244, 96, 0.75)', glow: 'rgba(196, 248, 132, 0.7)' }
  ];

  const getFallbackEnergyBallColor = index => {
    if (!Array.isArray(ENERGY_BALL_FALLBACK_COLORS) || !ENERGY_BALL_FALLBACK_COLORS.length) {
      return { core: '#f8fbff', rim: '#9ad7ff', trail: 'rgba(92, 208, 255, 0.7)', glow: 'rgba(150, 220, 255, 0.65)' };
    }
    const normalized = Number.isFinite(index) ? Math.floor(index) : 0;
    const palette = ENERGY_BALL_FALLBACK_COLORS[((normalized % ENERGY_BALL_FALLBACK_COLORS.length) + ENERGY_BALL_FALLBACK_COLORS.length) % ENERGY_BALL_FALLBACK_COLORS.length];
    return palette || ENERGY_BALL_FALLBACK_COLORS[0];
  };

  const degToRad = degrees => (degrees * Math.PI) / 180;

  function translate(key, fallback, params) {
    if (typeof translateOrDefault === 'function') {
      return translateOrDefault(key, fallback, params);
    }
    if (typeof window !== 'undefined' && typeof window.translateOrDefault === 'function') {
      return window.translateOrDefault(key, fallback, params);
    }
    return fallback;
  }

  function formatNumber(value, fractionDigits = 0) {
    const numeric = Number(value) || 0;
    const options = {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits
    };
    try {
      return numeric.toLocaleString(undefined, options);
    } catch (error) {
      return numeric.toFixed(fractionDigits);
    }
  }

  function clamp(value, min, max) {
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  function lerp(a, b, t) {
    return a + (b - a) * t;
  }

  function easeInOutSine(t) {
    return (1 - Math.cos(Math.PI * clamp(t, 0, 1))) / 2;
  }

  function randomInRange(min, max) {
    return min + Math.random() * (max - min);
  }

  class TerrainGenerator {
    constructor({ sampleSpacing = TERRAIN_SAMPLE_SPACING } = {}) {
      this.sampleSpacing = sampleSpacing;
      this.points = [];
      this.minY = 120;
      this.maxY = 480;
      this.baseLevel = 320;
      this.defaultBaseLevel = this.baseLevel;
      this.verticalSpan = this.maxY - this.minY;
      this.currentX = 0;
      this.currentY = this.baseLevel;
      this.segmentCount = 0;
      this.minAmplitude = 48;
      this.maxAmplitude = 120;
      this.minWavelength = MIN_WAVE_WAVELENGTH;
      this.maxWavelength = MAX_WAVE_WAVELENGTH;
      this.currentAmplitude = 72;
      this.phase = 0;
      this.phaseSpeed = (Math.PI * 2) / 420;
      this.baseMinAmplitude = this.minAmplitude;
      this.baseMaxAmplitude = this.maxAmplitude;
      this.amplitudeScale = 1;
      this.baseMinY = this.minY;
      this.baseMaxY = this.maxY;
      this.viewHeight = 600;
    }

    configure({ minY, maxY, baseLevel, sampleSpacing, viewHeight } = {}) {
      if (typeof sampleSpacing === 'number') {
        this.sampleSpacing = Math.max(12, sampleSpacing);
      }
      if (typeof minY === 'number') {
        this.minY = minY;
      }
      if (typeof maxY === 'number') {
        this.maxY = Math.max(this.minY + 40, maxY);
      }
      if (typeof baseLevel === 'number') {
        this.baseLevel = clamp(baseLevel, this.minY + 20, this.maxY - 20);
      } else {
        this.baseLevel = clamp(this.baseLevel, this.minY + 20, this.maxY - 20);
      }
      this.defaultBaseLevel = this.baseLevel;
      if (typeof viewHeight === 'number' && Number.isFinite(viewHeight) && viewHeight > 0) {
        this.viewHeight = viewHeight;
      }
      this.baseMinY = this.minY;
      this.baseMaxY = this.maxY;
      this.verticalSpan = Math.max(60, this.maxY - this.minY);
      const minAmplitude = Math.max(24, this.verticalSpan * MIN_AMPLITUDE_RATIO);
      const maxAmplitude = Math.max(minAmplitude + 12, this.verticalSpan * MAX_AMPLITUDE_RATIO);
      this.minAmplitude = minAmplitude;
      this.maxAmplitude = maxAmplitude;
      this.baseMinAmplitude = minAmplitude;
      this.baseMaxAmplitude = maxAmplitude;
      this.setAmplitudeScale(this.amplitudeScale || 1);
      const minWave = Math.max(this.sampleSpacing * 6, this.verticalSpan * 1.1, MIN_WAVE_WAVELENGTH);
      const maxWave = Math.max(minWave + this.sampleSpacing * 3, this.verticalSpan * 2.4, MAX_WAVE_WAVELENGTH);
      this.minWavelength = minWave;
      this.maxWavelength = maxWave;
      this.currentY = clamp(this.currentY ?? this.baseLevel, this.minY, this.maxY);
    }

    reset(startX, endX) {
      this.points = [];
      this.currentX = startX;
      const baseOffset = randomInRange(-this.verticalSpan * 0.08, this.verticalSpan * 0.08);
      const baseMargin = Math.max(36, this.maxAmplitude * 0.6);
      this.baseLevel = clamp(this.defaultBaseLevel + baseOffset, this.minY + baseMargin, this.maxY - baseMargin);
      this.currentAmplitude = randomInRange(this.minAmplitude, this.maxAmplitude);
      const initialWavelength = randomInRange(this.minWavelength, this.maxWavelength);
      this.phaseSpeed = (Math.PI * 2) / Math.max(60, initialWavelength);
      this.phase = randomInRange(0, Math.PI * 2);
      this.currentY = clamp(
        this.baseLevel + Math.sin(this.phase) * this.currentAmplitude,
        this.minY,
        this.maxY
      );
      this.segmentCount = 0;
      this.points.push({ x: this.currentX, y: this.currentY });
      this.ensure(endX);
    }

    ensure(maxX) {
      if (!this.points.length) {
        this.points.push({ x: this.currentX, y: this.currentY });
      }
      while (this.points[this.points.length - 1].x < maxX) {
        this.appendSegment();
      }
    }

    prune(minX) {
      while (this.points.length > 4 && this.points[1].x < minX) {
        this.points.shift();
      }
    }

    appendSegment() {
      const verticalSpan = this.verticalSpan || this.maxY - this.minY;
      const baseShift = randomInRange(-verticalSpan * 0.04, verticalSpan * 0.04);
      const baseMargin = Math.max(30, this.maxAmplitude * 0.6);
      const nextBase = clamp(this.baseLevel + baseShift, this.minY + baseMargin, this.maxY - baseMargin);
      const nextAmplitude = randomInRange(this.minAmplitude, this.maxAmplitude);
      const amplitudeRange = Math.max(1, this.maxAmplitude - this.minAmplitude);
      const amplitudeRatio = clamp((nextAmplitude - this.minAmplitude) / amplitudeRange, 0, 1);
      const baseLength = randomInRange(this.minWavelength, this.maxWavelength);
      const lengthBonus = amplitudeRatio * (this.maxWavelength - this.minWavelength) * AMPLITUDE_LENGTH_INFLUENCE;
      const maxExtendedLength = this.maxWavelength * MAX_WAVE_EXTENSION_FACTOR;
      const length = clamp(baseLength + lengthBonus, this.minWavelength, maxExtendedLength);
      const steps = Math.max(16, Math.round(length / this.sampleSpacing));
      const stepLength = length / steps;
      const nextPhaseSpeed = (Math.PI * 2) / Math.max(60, length);
      const startBase = this.baseLevel;
      const startAmplitude = this.currentAmplitude;
      const startPhaseSpeed = this.phaseSpeed;

      for (let index = 1; index <= steps; index += 1) {
        const t = index / steps;
        const eased = easeInOutSine(t);
        const base = lerp(startBase, nextBase, eased);
        const amplitude = lerp(startAmplitude, nextAmplitude, eased);
        const phaseSpeed = lerp(startPhaseSpeed, nextPhaseSpeed, eased);
        this.phase += phaseSpeed * stepLength;
        const y = clamp(base + Math.sin(this.phase) * amplitude, this.minY, this.maxY);
        this.currentX += stepLength;
        this.currentY = y;
        this.points.push({ x: this.currentX, y });
      }

      this.baseLevel = nextBase;
      this.currentAmplitude = nextAmplitude;
      this.phaseSpeed = nextPhaseSpeed;
      const fullTurn = Math.PI * 2;
      this.phase = ((this.phase % fullTurn) + fullTurn) % fullTurn;
      this.segmentCount += 1;
    }

    getHeight(x) {
      if (!this.points.length) {
        return this.baseLevel;
      }
      if (x <= this.points[0].x) {
        return this.points[0].y;
      }
      const lastIndex = this.points.length - 1;
      if (x >= this.points[lastIndex].x) {
        return this.points[lastIndex].y;
      }
      for (let index = 1; index < this.points.length; index += 1) {
        const point = this.points[index];
        if (x <= point.x) {
          const previous = this.points[index - 1];
          const span = point.x - previous.x || 1;
          const t = clamp((x - previous.x) / span, 0, 1);
          return lerp(previous.y, point.y, t);
        }
      }
      return this.baseLevel;
    }

    getSlopeAngle(x) {
      if (this.points.length < 2) {
        return 0;
      }
      if (x <= this.points[0].x) {
        const next = this.points[1];
        return Math.atan2(next.y - this.points[0].y, next.x - this.points[0].x);
      }
      for (let index = 1; index < this.points.length; index += 1) {
        const point = this.points[index];
        if (x <= point.x) {
          const previous = this.points[index - 1];
          return Math.atan2(point.y - previous.y, point.x - previous.x);
        }
      }
      const lastIndex = this.points.length - 1;
      const last = this.points[lastIndex];
      const prev = this.points[lastIndex - 1];
      return Math.atan2(last.y - prev.y, last.x - prev.x);
    }

    setAmplitudeScale(scale = 1) {
      const normalizedScale = clamp(
        Number.isFinite(scale) ? scale : 1,
        1,
        WAVE_AMPLITUDE_MAX_MULTIPLIER
      );
      this.amplitudeScale = normalizedScale;
      const rawMin = this.baseMinAmplitude * normalizedScale;
      const rawMax = this.baseMaxAmplitude * normalizedScale;
      const minAmplitude = Math.max(24, rawMin);
      const maxAmplitude = Math.max(minAmplitude + 12, rawMax);
      this.minAmplitude = minAmplitude;
      this.maxAmplitude = maxAmplitude;
      this.currentAmplitude = clamp(this.currentAmplitude, this.minAmplitude, this.maxAmplitude);
      this.updateAdaptiveBounds(normalizedScale);
    }

    getPoints() {
      return this.points;
    }

    updateAdaptiveBounds(scale = 1) {
      const viewHeight = Number.isFinite(this.viewHeight) && this.viewHeight > 0 ? this.viewHeight : this.maxY;
      const amplitudeBudget = Math.max(this.baseMaxAmplitude * scale, this.baseMinAmplitude * scale);
      const crestPadding = Math.max(48, amplitudeBudget * 0.35);
      const troughPadding = Math.max(64, amplitudeBudget * 0.45);
      const desiredMin = Math.max(0, this.defaultBaseLevel - amplitudeBudget - crestPadding);
      const desiredMax = Math.min(viewHeight * 1.75, this.defaultBaseLevel + amplitudeBudget + troughPadding);
      const baseMinY = typeof this.baseMinY === 'number' ? this.baseMinY : desiredMin;
      const baseMaxY = typeof this.baseMaxY === 'number' ? this.baseMaxY : desiredMax;
      this.minY = Math.min(baseMinY, desiredMin);
      this.maxY = Math.max(baseMaxY, desiredMax);
      if (!(this.maxY > this.minY + 60)) {
        this.maxY = this.minY + Math.max(120, amplitudeBudget * 2.2);
      }
      const safeMargin = Math.max(36, amplitudeBudget * 0.7);
      const safeMin = this.minY + safeMargin;
      const safeMax = this.maxY - safeMargin;
      if (safeMin >= safeMax) {
        const midpoint = (this.minY + this.maxY) / 2;
        this.baseLevel = midpoint;
        this.defaultBaseLevel = midpoint;
      } else {
        const candidateBase = Number.isFinite(this.baseLevel) ? this.baseLevel : this.defaultBaseLevel;
        const clampedBase = clamp(candidateBase, safeMin, safeMax);
        this.baseLevel = clampedBase;
        this.defaultBaseLevel = clampedBase;
      }
      this.verticalSpan = Math.max(60, this.maxY - this.minY);
      this.currentY = clamp(this.currentY ?? this.baseLevel, this.minY, this.maxY);
    }
  }

  class WaveGame {
    constructor(options = {}) {
      this.canvas = options.canvas || null;
      this.stage = options.stage || this.canvas?.parentElement || null;
      this.ctx = this.canvas ? this.canvas.getContext('2d', { alpha: false }) : null;
      this.distanceElement = options.distanceElement || null;
      this.speedElement = options.speedElement || null;
      this.altitudeElement = options.altitudeElement || null;
      this.statusElement = options.statusElement || null;
      this.resetButton = options.resetButton || null;

      this.pixelRatio = 1;
      this.viewWidth = 0;
      this.viewHeight = 0;
      this.cameraX = 0;
      this.cameraY = 0;
      this.cameraScale = 1;
      this.minCameraScale = BASE_MIN_CAMERA_SCALE;

      this.terrain = new TerrainGenerator();
      this.player = {
        x: 0,
        y: 0,
        speed: START_GROUND_SPEED,
        vx: 0,
        vy: 0,
        onGround: true,
        trail: []
      };

      this.distanceTravelled = 0;
      this.isPressing = false;
      this.currentPressDuration = 0;
      this.pendingRelease = false;
      this.keyboardPressed = false;
      this.activePointers = new Set();
      this.statusState = null;
      this.currentSpeedKmh = 0;

      this.elapsedTimeMs = 0;
      this.lastTrailSampleMs = -Infinity;
      this.lastTrailRotation = 0;
      this.ballColorState = {
        currentIndex: 0,
        previousIndex: null,
        lastChangeMs: 0,
        nextChangeMs: BALL_COLOR_CHANGE_INTERVAL_MS,
        transitionEndMs: 0,
        colorCount: Math.max(1, ENERGY_BALL_SPRITE.colorCount || 1)
      };

      this.skyDots = [];

      this.running = false;
      this.lastTimestamp = null;
      this.frameHandle = null;

      this.stats = {
        bestDistance: 0,
        bestSpeed: 0,
        maxAltitude: 0,
        totalDistance: 0
      };
      this.lastRecordedRunDistance = 0;
      this.distanceAutosaveAccumulator = 0;
      this.autosaveTimer = null;

      this.messages = this.statusElement
        ? {
            ready: translate(
              'index.sections.wave.status.ready',
              'Relâchez pour bondir sur la prochaine montée.'
            ),
            hold: translate(
              'index.sections.wave.status.hold',
              'Maintenez pour piquer et gagner de la vitesse.'
            ),
            air: translate(
              'index.sections.wave.status.air',
              'En vol ! Orientez-vous pour reprendre de la vitesse.'
            )
          }
        : null;

      this.handleResize = this.handleResize.bind(this);
      this.handlePointerDown = this.handlePointerDown.bind(this);
      this.handlePointerUp = this.handlePointerUp.bind(this);
      this.handlePointerCancel = this.handlePointerCancel.bind(this);
      this.handleKeyDown = this.handleKeyDown.bind(this);
      this.handleKeyUp = this.handleKeyUp.bind(this);
      this.handleResetClick = this.handleResetClick.bind(this);
      this.tick = this.tick.bind(this);

      this.loadAutosavedStats();
      this.attachEvents();
      this.handleResize();
      this.resetState();
      this.render(0);
    }

    triggerManualAtomClick() {
      let handler = null;
      if (typeof globalThis !== 'undefined' && typeof globalThis.handleManualAtomClick === 'function') {
        handler = globalThis.handleManualAtomClick;
      } else if (typeof handleManualAtomClick === 'function') {
        handler = handleManualAtomClick;
      }
      if (typeof handler === 'function') {
        try {
          handler({ contextId: 'wave' });
        } catch (error) {
          console.warn('Unable to trigger manual click from Wave game', error);
        }
      }
    }

    getAutosaveApi() {
      if (typeof window === 'undefined') {
        return null;
      }
      const api = window.ArcadeAutosave;
      if (!api || typeof api.set !== 'function' || typeof api.get !== 'function') {
        return null;
      }
      return api;
    }

    sanitizeAutosavedStats(raw) {
      if (!raw || typeof raw !== 'object') {
        return null;
      }
      const toNumber = value => {
        const numeric = Number(value);
        return Number.isFinite(numeric) && numeric >= 0 ? numeric : 0;
      };
      const bestDistance = toNumber(raw.bestDistance ?? raw.bestDistanceMeters ?? raw.maxDistance ?? raw.distance);
      const bestSpeed = toNumber(raw.bestSpeed ?? raw.maxSpeed ?? raw.speed);
      const maxAltitude = toNumber(raw.maxAltitude ?? raw.bestAltitude ?? raw.altitude);
      const totalDistance = toNumber(
        raw.totalDistance
          ?? raw.distanceTotal
          ?? raw.accumulatedDistance
          ?? raw.distanceAccumulated
          ?? raw.bestDistance
          ?? raw.bestDistanceMeters
          ?? raw.maxDistance
          ?? raw.distance
      );
      return {
        bestDistance,
        bestSpeed,
        maxAltitude,
        totalDistance: Math.max(totalDistance, bestDistance)
      };
    }

    loadAutosavedStats() {
      const api = this.getAutosaveApi();
      if (!api) {
        return;
      }
      let saved = null;
      try {
        saved = api.get('wave');
      } catch (error) {
        return;
      }
      if (!saved) {
        return;
      }
      const normalized = this.sanitizeAutosavedStats(saved);
      if (!normalized) {
        try {
          api.set('wave', null);
        } catch (error) {
          // Ignore autosave cleanup errors
        }
        return;
      }
      this.stats = {
        bestDistance: normalized.bestDistance,
        bestSpeed: normalized.bestSpeed,
        maxAltitude: normalized.maxAltitude,
        totalDistance: normalized.totalDistance
      };
      this.distanceAutosaveAccumulator = 0;
      this.lastRecordedRunDistance = 0;
    }

    serializeStats() {
      const normalize = value => (Number.isFinite(value) && value >= 0 ? value : 0);
      return {
        bestDistance: normalize(this.stats?.bestDistance),
        bestSpeed: normalize(this.stats?.bestSpeed),
        maxAltitude: normalize(this.stats?.maxAltitude),
        totalDistance: normalize(this.stats?.totalDistance)
      };
    }

    persistAutosave() {
      const api = this.getAutosaveApi();
      if (!api) {
        return;
      }
      try {
        api.set('wave', this.serializeStats());
      } catch (error) {
        // Ignore autosave persistence errors
      }
    }

    scheduleAutosave() {
      if (typeof window === 'undefined') {
        return;
      }
      if (this.autosaveTimer != null) {
        window.clearTimeout(this.autosaveTimer);
      }
      this.autosaveTimer = window.setTimeout(() => {
        this.autosaveTimer = null;
        this.persistAutosave();
      }, 200);
    }

    flushAutosave() {
      if (typeof window !== 'undefined' && this.autosaveTimer != null) {
        window.clearTimeout(this.autosaveTimer);
        this.autosaveTimer = null;
      }
      this.persistAutosave();
    }

    attachEvents() {
      if (typeof window !== 'undefined') {
        window.addEventListener('resize', this.handleResize);
      }
      const pointerTarget = this.stage || this.canvas;
      if (pointerTarget) {
        pointerTarget.addEventListener('pointerdown', this.handlePointerDown);
        pointerTarget.addEventListener('pointerup', this.handlePointerUp);
        pointerTarget.addEventListener('pointercancel', this.handlePointerCancel);
        pointerTarget.addEventListener('pointerleave', this.handlePointerCancel);
      }
      if (this.resetButton) {
        this.resetButton.addEventListener('click', this.handleResetClick);
      }
      if (typeof document !== 'undefined') {
        document.addEventListener('keydown', this.handleKeyDown);
        document.addEventListener('keyup', this.handleKeyUp);
      }
    }

    detachEvents() {
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.handleResize);
      }
      const pointerTarget = this.stage || this.canvas;
      if (pointerTarget) {
        pointerTarget.removeEventListener('pointerdown', this.handlePointerDown);
        pointerTarget.removeEventListener('pointerup', this.handlePointerUp);
        pointerTarget.removeEventListener('pointercancel', this.handlePointerCancel);
        pointerTarget.removeEventListener('pointerleave', this.handlePointerCancel);
      }
      if (this.resetButton) {
        this.resetButton.removeEventListener('click', this.handleResetClick);
      }
      if (typeof document !== 'undefined') {
        document.removeEventListener('keydown', this.handleKeyDown);
        document.removeEventListener('keyup', this.handleKeyUp);
      }
    }

    handleResize() {
      if (!this.canvas) {
        return;
      }
      const stage = this.stage || this.canvas;
      const ratio = clamp(typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1, 1, 3);
      const fallbackWidth = this.viewWidth && this.viewWidth > 0 ? this.viewWidth : this.canvas.clientWidth || 1;
      let width = stage?.clientWidth || fallbackWidth || 1;
      const viewportWidth =
        typeof window !== 'undefined' && Number.isFinite(window.innerWidth) && window.innerWidth > 0
          ? window.innerWidth
          : 960;
      if (!Number.isFinite(width) || width <= 1) {
        const containerWidth = stage?.parentElement?.clientWidth || viewportWidth;
        width = clamp(containerWidth, 280, 960);
      }

      const minimumHeight = Math.max(280, width * 0.6);
      const viewportHeight =
        typeof window !== 'undefined' && Number.isFinite(window.innerHeight) && window.innerHeight > 0
          ? window.innerHeight
          : minimumHeight * 1.5;
      const maximumHeight = Math.max(minimumHeight, viewportHeight * 0.9);

      let height = stage?.clientHeight || this.viewHeight || minimumHeight;
      if (!Number.isFinite(height) || height <= 0) {
        height = minimumHeight;
      }
      height = clamp(height, minimumHeight, maximumHeight);

      if (stage && stage !== this.canvas) {
        stage.style.height = `${height}px`;
      }
      this.canvas.style.width = '100%';
      this.canvas.style.height = '100%';

      this.pixelRatio = ratio;
      this.viewWidth = width;
      this.viewHeight = height;
      this.canvas.width = Math.max(1, Math.floor(width * ratio));
      this.canvas.height = Math.max(1, Math.floor(height * ratio));
      if (this.ctx) {
        this.ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      }
      this.cameraScale = 1;
      this.cameraY = 0;
      this.minCameraScale = BASE_MIN_CAMERA_SCALE;
      const minY = height * 0.7;
      const maxY = height * 0.95;
      const baseLevel = height * 0.9;
      this.terrain.configure({
        minY,
        maxY,
        baseLevel,
        sampleSpacing: TERRAIN_SAMPLE_SPACING,
        viewHeight: height
      });
      this.refreshSky();
      this.resetTerrain();
    }

    refreshSky() {
      const density = 42;
      this.skyDots = Array.from({ length: density }, () => ({
        x: Math.random(),
        y: Math.random() * 0.55,
        radius: 0.8 + Math.random() * 1.4,
        alpha: 0.2 + Math.random() * 0.4
      }));
    }

    resetTerrain() {
      this.cameraScale = 1;
      this.cameraY = 0;
      const worldWidth = this.viewWidth / Math.max(this.cameraScale, this.minCameraScale);
      const span = worldWidth * 3;
      const startX = -worldWidth;
      this.terrain.setAmplitudeScale(1);
      this.terrain.reset(startX, startX + span);
      const startScreenX = worldWidth * START_SCREEN_X_RATIO;
      this.player.x = startX + startScreenX;
      const groundY = this.terrain.getHeight(this.player.x);
      const safeGroundY = Math.max(0, groundY - START_MIN_CLEARANCE);
      const desiredHeight = Math.max(0, this.viewHeight * START_HEIGHT_RATIO);
      this.player.y = Math.min(desiredHeight, safeGroundY);
      this.player.speed = START_GROUND_SPEED;
      this.player.vx = 0;
      this.player.vy = 0;
      this.player.onGround = true;
      this.cameraX = this.player.x - worldWidth * START_SCREEN_X_RATIO;
      this.distanceTravelled = 0;
      this.pendingRelease = false;
      this.isPressing = false;
      this.currentPressDuration = 0;
      this.keyboardPressed = false;
      this.activePointers.clear();
      this.lastTimestamp = null;
      this.statusState = null;
      if (Number.isFinite(this.distanceAutosaveAccumulator) && this.distanceAutosaveAccumulator > 0) {
        this.scheduleAutosave();
      }
      this.distanceAutosaveAccumulator = 0;
      this.lastRecordedRunDistance = 0;
      this.applyInitialLaunch();
      this.resetBallVisualState();
      this.captureTrailPoint(true);
      this.updateCurrentSpeed();
      this.updateTerrainAmplitude(this.currentSpeedKmh);
      this.updateHud(0);
    }

    resetState() {
      this.resetTerrain();
    }

    applyInitialLaunch() {
      const launchAngle = degToRad(20);
      this.player.onGround = false;
      this.player.speed = INITIAL_LAUNCH_SPEED;
      this.player.vx = Math.cos(launchAngle) * INITIAL_LAUNCH_SPEED;
      this.player.vy = Math.sin(launchAngle) * INITIAL_LAUNCH_SPEED;
      this.player.y = Math.max(0, this.player.y - INITIAL_LAUNCH_HEIGHT_OFFSET);
      this.pendingRelease = false;
    }

    getBallRadius() {
      return clamp(this.viewHeight * 0.04, 12, 28);
    }

    resetBallVisualState() {
      this.elapsedTimeMs = 0;
      this.lastTrailSampleMs = -Infinity;
      this.lastTrailRotation = 0;
      if (this.player && typeof this.player === 'object') {
        this.player.trail = [];
      }
      const colorCount = Math.max(1, ENERGY_BALL_SPRITE.colorCount || 1);
      this.ballColorState = {
        currentIndex: 0,
        previousIndex: null,
        lastChangeMs: 0,
        nextChangeMs: BALL_COLOR_CHANGE_INTERVAL_MS,
        transitionEndMs: 0,
        colorCount
      };
    }

    updateBallColorState() {
      if (!this.ballColorState || typeof this.ballColorState !== 'object') {
        this.resetBallVisualState();
      }
      const state = this.ballColorState;
      const now = Number.isFinite(this.elapsedTimeMs) ? this.elapsedTimeMs : 0;
      const colorCount = Math.max(1, ENERGY_BALL_SPRITE.colorCount || state.colorCount || 1);
      state.colorCount = colorCount;
      if (!Number.isFinite(state.nextChangeMs)) {
        const base = Number.isFinite(state.lastChangeMs) ? state.lastChangeMs : now;
        state.nextChangeMs = base + BALL_COLOR_CHANGE_INTERVAL_MS;
      }
      while (now >= state.nextChangeMs) {
        const nextIndex = (Number.isFinite(state.currentIndex) ? state.currentIndex : 0) + 1;
        const normalizedNext = ((nextIndex % colorCount) + colorCount) % colorCount;
        state.previousIndex = Number.isFinite(state.currentIndex)
          ? ((Math.floor(state.currentIndex) % colorCount) + colorCount) % colorCount
          : 0;
        state.currentIndex = normalizedNext;
        state.lastChangeMs = state.nextChangeMs;
        state.transitionEndMs = state.lastChangeMs + BALL_COLOR_TRANSITION_MS;
        state.nextChangeMs += BALL_COLOR_CHANGE_INTERVAL_MS;
      }
      if (state.previousIndex != null && now >= state.transitionEndMs) {
        state.previousIndex = null;
        state.transitionEndMs = 0;
      }
    }

    captureTrailPoint(force = false) {
      if (!this.player || typeof this.player !== 'object') {
        return;
      }
      if (!Array.isArray(this.player.trail)) {
        this.player.trail = [];
      }
      const now = Number.isFinite(this.elapsedTimeMs) ? this.elapsedTimeMs : 0;
      const elapsedSinceSample = now - this.lastTrailSampleMs;
      const shouldSample = force || elapsedSinceSample >= BALL_TRAIL_SAMPLE_INTERVAL_MS;
      if (shouldSample) {
        const radius = this.getBallRadius();
        const centerOffset = radius * 0.4;
        const vx = Number.isFinite(this.player.vx) ? this.player.vx : 0;
        const vy = Number.isFinite(this.player.vy) ? this.player.vy : 0;
        let rotation = Number.isFinite(this.lastTrailRotation) ? this.lastTrailRotation : 0;
        const speedSq = vx * vx + vy * vy;
        if (speedSq > 0.001) {
          rotation = Math.atan2(vx, -vy);
          this.lastTrailRotation = rotation;
        }
        this.player.trail.push({
          x: this.player.x,
          y: this.player.y - centerOffset,
          vx,
          vy,
          rotation,
          radius,
          time: now
        });
        this.lastTrailSampleMs = now;
      }
      const trail = this.player.trail;
      const maxAge = BALL_TRAIL_MAX_AGE_MS;
      while (trail.length > BALL_TRAIL_MAX_POINTS) {
        trail.shift();
      }
      while (trail.length && now - trail[0].time > maxAge) {
        trail.shift();
      }
    }

    updateBallTrail(force = false) {
      this.captureTrailPoint(force);
    }

    getActiveBallColors(now = Number.isFinite(this.elapsedTimeMs) ? this.elapsedTimeMs : 0) {
      const state = this.ballColorState;
      const colors = [];
      const colorCount = Math.max(1, ENERGY_BALL_SPRITE.colorCount || (state?.colorCount) || 1);
      const normalizeIndex = value => {
        const numeric = Number.isFinite(value) ? Math.floor(value) : 0;
        return ((numeric % colorCount) + colorCount) % colorCount;
      };
      const mergeColor = (index, alpha) => {
        if (!Number.isFinite(alpha) || alpha <= 0) {
          return;
        }
        const normalized = normalizeIndex(index);
        const clampedAlpha = clamp(alpha, 0, 1);
        const existing = activeMap.get(normalized);
        if (existing == null || clampedAlpha > existing) {
          activeMap.set(normalized, clampedAlpha);
        }
      };
      const activeMap = new Map();
      const currentIndex = state && typeof state === 'object' ? normalizeIndex(state.currentIndex) : 0;
      mergeColor(currentIndex, 1);
      if (state && typeof state === 'object' && state.previousIndex != null) {
        const duration = Math.max(1, BALL_COLOR_TRANSITION_MS);
        const elapsed = now - (Number.isFinite(state.lastChangeMs) ? state.lastChangeMs : now);
        const progress = clamp(elapsed / duration, 0, 1);
        const alpha = 1 - progress;
        if (alpha > 0) {
          mergeColor(state.previousIndex, alpha);
        }
      }
      return Array.from(activeMap.entries()).map(([index, alpha]) => ({ index, alpha }));
    }

    isEventFromControls(event) {
      const target = event?.target;
      if (!target) {
        return false;
      }
      if (this.resetButton && (target === this.resetButton || this.resetButton.contains(target))) {
        return true;
      }
      if (typeof target.closest === 'function') {
        const controls = target.closest('.wave-controls');
        if (controls && this.stage?.contains?.(controls)) {
          return true;
        }
      }
      return false;
    }

    isEventFromInteractiveOverlay(event) {
      const isInteractive = node => {
        if (!node || typeof node.closest !== 'function') {
          return false;
        }
        return Boolean(node.closest('.ticket-star, .frenzy-token'));
      };

      if (isInteractive(event?.target)) {
        return true;
      }

      if (typeof event?.composedPath === 'function') {
        const path = event.composedPath();
        if (Array.isArray(path)) {
          for (const node of path) {
            if (isInteractive(node)) {
              return true;
            }
          }
        }
      }

      return false;
    }

    handlePointerDown(event) {
      if (this.isEventFromControls(event) || this.isEventFromInteractiveOverlay(event)) {
        return;
      }
      if (event.pointerType === 'mouse' && event.button !== 0) {
        return;
      }
      this.activePointers.add(event.pointerId);
      this.setPressingState(true);
      if (typeof this.canvas?.setPointerCapture === 'function') {
        this.canvas.setPointerCapture(event.pointerId);
      }
      event.preventDefault();
    }

    handlePointerUp(event) {
      if (this.isEventFromControls(event) || this.isEventFromInteractiveOverlay(event)) {
        return;
      }
      if (this.activePointers.has(event.pointerId)) {
        this.activePointers.delete(event.pointerId);
      }
      if (typeof this.canvas?.releasePointerCapture === 'function') {
        try {
          this.canvas.releasePointerCapture(event.pointerId);
        } catch (error) {
          // Ignored when capture is not set.
        }
      }
      const pressed = this.activePointers.size > 0 || this.keyboardPressed;
      this.setPressingState(pressed);
      event.preventDefault();
    }

    handlePointerCancel(event) {
      if (this.isEventFromControls(event) || this.isEventFromInteractiveOverlay(event)) {
        return;
      }
      if (this.activePointers.has(event.pointerId)) {
        this.activePointers.delete(event.pointerId);
      }
      const pressed = this.activePointers.size > 0 || this.keyboardPressed;
      this.setPressingState(pressed);
      event.preventDefault();
    }

    handleKeyDown(event) {
      if (event.repeat) {
        return;
      }
      if (event.code === 'Space' || event.code === 'ArrowDown') {
        this.keyboardPressed = true;
        this.setPressingState(true);
        event.preventDefault();
      }
    }

    handleKeyUp(event) {
      if (event.code === 'Space' || event.code === 'ArrowDown') {
        this.keyboardPressed = false;
        const pressed = this.activePointers.size > 0;
        this.setPressingState(pressed);
        event.preventDefault();
      }
    }

    setPressingState(pressed) {
      if (pressed === this.isPressing) {
        return;
      }
      if (pressed) {
        this.triggerManualAtomClick();
      }
      this.isPressing = pressed;
      if (!pressed) {
        this.currentPressDuration = 0;
        this.pendingRelease = this.player.onGround;
        return;
      }
      this.currentPressDuration = 0;
      this.pendingRelease = false;
      this.applyPressImpulse();
    }

    applyPressImpulse() {
      if (!this.player) {
        return;
      }
      if (this.player.onGround) {
        const slopeAngle = this.terrain.getSlopeAngle(this.player.x);
        const tangentX = Math.cos(slopeAngle);
        const tangentY = Math.sin(slopeAngle);
        let impulse = GROUND_PRESS_FORWARD_IMPULSE;
        if (slopeAngle < 0) {
          const slopePull = clamp(Math.abs(Math.sin(slopeAngle)), 0.2, 1);
          impulse *= 1 + (DOWNHILL_IMPULSE_MULTIPLIER - 1) * slopePull;
        }
        this.player.vx += tangentX * impulse;
        this.player.vy += tangentY * impulse;
        this.player.speed = Math.hypot(this.player.vx, this.player.vy);
      } else {
        this.player.vx += AIR_PRESS_FORWARD_IMPULSE;
        this.player.vy += AIR_PRESS_DOWN_IMPULSE;
        this.player.speed = Math.hypot(this.player.vx, this.player.vy);
      }
    }

    handleResetClick(event) {
      event.preventDefault();
      this.restart();
      if (typeof event.target?.blur === 'function') {
        event.target.blur();
      }
    }

    start() {
      if (this.running) {
        return;
      }
      this.running = true;
      this.lastTimestamp = null;
      this.frameHandle = requestAnimationFrame(this.tick);
    }

    stop() {
      if (!this.running) {
        return;
      }
      this.running = false;
      if (this.frameHandle) {
        cancelAnimationFrame(this.frameHandle);
        this.frameHandle = null;
      }
      this.lastTimestamp = null;
    }

    restart() {
      this.resetState();
      if (!this.running) {
        this.render(0);
      }
    }

    onEnter() {
      this.start();
    }

    onLeave() {
      this.stop();
      this.setPressingState(false);
      this.flushAutosave();
    }

    tick(timestamp) {
      if (!this.running) {
        return;
      }
      if (this.lastTimestamp == null) {
        this.lastTimestamp = timestamp;
      }
      const delta = clamp((timestamp - this.lastTimestamp) / 1000, 0, MAX_FRAME_DELTA);
      this.lastTimestamp = timestamp;
      this.update(delta);
      this.render(delta);
      this.frameHandle = requestAnimationFrame(this.tick);
    }

    update(delta) {
      const numericDelta = Number.isFinite(delta) ? delta : 0;
      const deltaMs = Math.max(0, numericDelta) * 1000;
      this.elapsedTimeMs += deltaMs;
      this.updateBallColorState();
      this.updateTerrainAmplitude(this.currentSpeedKmh);
      const viewWorldWidth = this.viewWidth / this.cameraScale;
      const targetMaxX = this.cameraX + viewWorldWidth * 2.6;
      const pruneBefore = this.cameraX - viewWorldWidth * 1.2;
      this.terrain.ensure(targetMaxX);
      this.terrain.prune(pruneBefore);

      if (this.isPressing) {
        this.currentPressDuration += delta;
      } else if (this.currentPressDuration !== 0) {
        this.currentPressDuration = 0;
      }

      if (this.player.onGround) {
        const slopeAngle = this.terrain.getSlopeAngle(this.player.x);
        const tangentX = Math.cos(slopeAngle);
        const tangentY = Math.sin(slopeAngle);
        const uphillPressGraceActive =
          this.isPressing && slopeAngle > 0 && this.currentPressDuration < UPHILL_PRESS_GRACE_DURATION;
        const pressingPenaltyActive = this.isPressing && !uphillPressGraceActive;
        const gravityAccel = BASE_GRAVITY * (pressingPenaltyActive ? DIVE_MULTIPLIER : 1);
        let slopeAccel = -gravityAccel * Math.sin(slopeAngle);
        if (slopeAngle > 0 && this.player.speed > 0) {
          const speedRatio = clamp(this.player.speed / UPHILL_DECEL_SPEED, 0, 1);
          const retentionFactor = lerp(1, UPHILL_DECEL_FACTOR_MIN, speedRatio);
          slopeAccel *= retentionFactor;
        }
        const pushAccel = this.isPressing ? BASE_PUSH_ACCEL : BASE_PUSH_ACCEL * 0.12;
        const acceleration = slopeAccel + pushAccel;
        this.player.speed += acceleration * delta;
        let dragFactor = pressingPenaltyActive ? HOLDING_DRAG : GROUND_DRAG;
        if (slopeAngle > 0 && this.player.speed > 0) {
          const speedRatio = clamp(this.player.speed / UPHILL_DECEL_SPEED, 0, 1);
          const boostedDrag = lerp(dragFactor, UPHILL_DRAG_BONUS, speedRatio);
          dragFactor = Math.max(dragFactor, boostedDrag);
        }
        const decay = Math.pow(dragFactor, delta * 60);
        this.player.speed *= decay;
        if (this.player.speed < 0.001) {
          this.player.speed = 0;
        }
        this.player.vx = this.player.speed * tangentX;
        this.player.vy = this.player.speed * tangentY;
        this.player.x += this.player.vx * delta;
        this.player.y = this.terrain.getHeight(this.player.x);
        const crestingSlope = slopeAngle < -degToRad(6);
        const autoJumpReady = crestingSlope && this.player.speed >= AUTO_JUMP_SPEED_THRESHOLD;
        const manualJumpReady = !this.isPressing && this.pendingRelease && crestingSlope && this.player.speed > 0;
        if (autoJumpReady || manualJumpReady) {
          const alongSlopeSpeed = Math.max(this.player.speed, Math.hypot(this.player.vx, this.player.vy));
          const jumpImpulse = clamp(
            JUMP_BASE + alongSlopeSpeed * JUMP_SPEED_RATIO,
            JUMP_BASE,
            MAX_JUMP_IMPULSE
          );
          this.player.onGround = false;
          this.player.vy -= jumpImpulse;
          this.player.y -= 1.5;
          this.player.speed = Math.hypot(this.player.vx, this.player.vy);
          this.pendingRelease = false;
        }
      } else {
        const gravityAccel = BASE_GRAVITY * (this.isPressing ? DIVE_MULTIPLIER : 1);
        this.player.vy += gravityAccel * delta;
        const drag = Math.pow(AIR_DRAG, delta * 60);
        this.player.vx *= drag;
        this.player.vy *= drag;
        if (this.isPressing) {
          const slopeAngle = this.terrain.getSlopeAngle(this.player.x);
          const tangentX = Math.cos(slopeAngle);
          const tangentY = Math.sin(slopeAngle);
          const divePull = Math.max(0, -tangentY);
          if (divePull > 0) {
            const diveAccel = gravityAccel * divePull * delta * DIVE_PULL_STRENGTH;
            this.player.vx += tangentX * diveAccel;
            this.player.vy += tangentY * diveAccel;
          }
        }
        this.player.x += this.player.vx * delta;
        this.player.y += this.player.vy * delta;
        const groundY = this.terrain.getHeight(this.player.x);
        if (this.player.y >= groundY) {
          this.player.y = groundY;
          const slopeAngle = this.terrain.getSlopeAngle(this.player.x);
          const tangentX = Math.cos(slopeAngle);
          const tangentY = Math.sin(slopeAngle);
          const projectedSpeed = this.player.vx * tangentX + this.player.vy * tangentY;
          const dampened = Math.max(projectedSpeed * 0.97, 0);
          this.player.speed = dampened < MIN_LANDING_SPEED ? 0 : dampened;
          this.player.vx = this.player.speed * tangentX;
          this.player.vy = this.player.speed * tangentY;
          this.player.onGround = true;
          this.pendingRelease = false;
        }
      }

      if (!this.player.onGround) {
        this.pendingRelease = false;
        this.player.speed = Math.hypot(this.player.vx, this.player.vy);
      }

      this.distanceTravelled = Math.max(this.distanceTravelled, this.player.x);
      const speedKmh = this.updateCurrentSpeed();
      this.updateTerrainAmplitude(speedKmh);
      this.updateCamera(delta);
      this.updateHud(delta);
      this.updateBallTrail(false);
    }

    updateCurrentSpeed() {
      if (!this.player) {
        this.currentSpeedKmh = 0;
        return this.currentSpeedKmh;
      }
      const vx = Number.isFinite(this.player.vx) ? this.player.vx : 0;
      const vy = Number.isFinite(this.player.vy) ? this.player.vy : 0;
      const speed = Math.hypot(vx, vy);
      this.player.speed = speed;
      const speedKmh = Math.max(0, (speed / PIXELS_PER_METER) * 3.6);
      this.currentSpeedKmh = speedKmh;
      return speedKmh;
    }

    updateTerrainAmplitude(speedKmh = 0) {
      if (!this.terrain || typeof this.terrain.setAmplitudeScale !== 'function') {
        return;
      }
      const normalizedSpeed = clamp(
        (Math.max(0, speedKmh) - WAVE_AMPLITUDE_SPEED_MIN_KMH) /
          Math.max(WAVE_AMPLITUDE_SPEED_MAX_KMH - WAVE_AMPLITUDE_SPEED_MIN_KMH, 1),
        0,
        1
      );
      const scale = normalizedSpeed <= 0 ? 1 : lerp(1, WAVE_AMPLITUDE_MAX_MULTIPLIER, normalizedSpeed);
      this.terrain.setAmplitudeScale(scale);
    }

    updateCamera(delta) {
      const groundY = this.terrain.getHeight(this.player.x);
      const topMargin = this.viewHeight * CAMERA_TOP_MARGIN_RATIO;
      const bottomMargin = this.viewHeight * CAMERA_BOTTOM_MARGIN_RATIO;
      const baseTop = Math.max(0, this.player.y - topMargin);
      const baseBottom = Math.max(
        this.player.y + bottomMargin,
        groundY + bottomMargin * GROUND_INFLUENCE_RATIO
      );
      const altitudeMeters = Math.max(0, (groundY - this.player.y) / PIXELS_PER_METER);
      const speedInfluence = clamp(
        (this.currentSpeedKmh - SPEED_ZOOM_MIN_KMH) /
          Math.max(SPEED_ZOOM_MAX_KMH - SPEED_ZOOM_MIN_KMH, 1),
        0,
        1
      );
      const altitudeInfluence = clamp(
        (altitudeMeters - ALTITUDE_ZOOM_MIN_METERS) /
          Math.max(ALTITUDE_ZOOM_MAX_METERS - ALTITUDE_ZOOM_MIN_METERS, 1),
        0,
        1
      );
      const zoomInfluence = Math.max(speedInfluence, altitudeInfluence);
      const smoothedInfluence = easeInOutSine(zoomInfluence);
      const dynamicSpanMultiplier = lerp(1, MAX_DYNAMIC_SPAN_MULTIPLIER, smoothedInfluence);
      const span = Math.max(baseBottom - baseTop, 1);
      const spanIncrease = span * Math.max(dynamicSpanMultiplier - 1, 0);
      const altitudeBias = clamp(0.5 + (altitudeInfluence - speedInfluence) * 0.35, 0.35, 0.75);
      const extraTop = spanIncrease * altitudeBias;
      const extraBottom = spanIncrease - extraTop;
      const desiredTop = Math.max(0, baseTop - extraTop);
      const desiredBottom = baseBottom + extraBottom;
      const dynamicMinScale = clamp(
        BASE_MIN_CAMERA_SCALE / dynamicSpanMultiplier,
        ABSOLUTE_MIN_CAMERA_SCALE,
        BASE_MIN_CAMERA_SCALE
      );
      this.minCameraScale = dynamicMinScale;

      const maxSpanRatio = MAX_VISIBLE_SPAN_RATIO * dynamicSpanMultiplier;
      const rawSpan = Math.max(desiredBottom - desiredTop, this.viewHeight * MIN_VISIBLE_SPAN_RATIO);
      const clampedSpan = clamp(rawSpan, this.viewHeight * MIN_VISIBLE_SPAN_RATIO, this.viewHeight * maxSpanRatio);
      const crestScreenLimit = this.viewHeight * (1 - MAX_WAVE_TOP_SCREEN_RATIO);
      let targetScale = clamp(this.viewHeight / clampedSpan, dynamicMinScale, 1);

      for (let attempt = 0; attempt < 3; attempt += 1) {
        const previewViewHeight = this.viewHeight / targetScale;
        const previewLowerBound = Math.max(0, desiredBottom - previewViewHeight);
        const previewUpperBound = Math.max(previewLowerBound, desiredTop);
        const baseCameraY = clamp(desiredTop, previewLowerBound, previewUpperBound);
        const crestCameraLimit = groundY - crestScreenLimit / targetScale;
        const previewCameraY = Number.isFinite(crestCameraLimit)
          ? Math.max(0, Math.min(baseCameraY, crestCameraLimit))
          : Math.max(0, baseCameraY);
        const crestScreenY = (groundY - previewCameraY) * targetScale;
        if (crestScreenY < crestScreenLimit && groundY > previewCameraY) {
          const requiredScale = clamp(
            crestScreenLimit / (groundY - previewCameraY),
            dynamicMinScale,
            1
          );
          if (requiredScale > targetScale + 1e-4) {
            targetScale = requiredScale;
            continue;
          }
        }
        break;
      }

      const scaleLerp = clamp(delta * 4.5, CAMERA_SCALE_LERP_MIN, CAMERA_SCALE_LERP_MAX);
      if (!Number.isFinite(this.cameraScale)) {
        this.cameraScale = targetScale;
      } else {
        const nextScale = this.cameraScale + (targetScale - this.cameraScale) * scaleLerp;
        this.cameraScale = targetScale > this.cameraScale ? Math.max(targetScale, nextScale) : nextScale;
      }
      this.cameraScale = clamp(this.cameraScale, dynamicMinScale, 1);

      const viewWorldHeight = this.viewHeight / this.cameraScale;
      const lowerBound = Math.max(0, desiredBottom - viewWorldHeight);
      const upperBound = Math.max(lowerBound, desiredTop);
      const baseCameraY = clamp(desiredTop, lowerBound, upperBound);
      const crestCameraLimit = groundY - crestScreenLimit / this.cameraScale;
      let desiredCameraY = Number.isFinite(crestCameraLimit)
        ? Math.max(0, Math.min(baseCameraY, crestCameraLimit))
        : Math.max(0, baseCameraY);
      const verticalLerp = clamp(delta * 4.5, CAMERA_VERTICAL_LERP_MIN, CAMERA_VERTICAL_LERP_MAX);
      if (!Number.isFinite(this.cameraY)) {
        this.cameraY = desiredCameraY;
      } else {
        this.cameraY += (desiredCameraY - this.cameraY) * verticalLerp;
      }
      if (Number.isFinite(crestCameraLimit)) {
        this.cameraY = Math.min(this.cameraY, Math.max(0, crestCameraLimit));
      }
      this.cameraY = Math.max(0, this.cameraY);

      const viewWorldWidth = this.viewWidth / this.cameraScale;
      const desiredX = this.player.x - viewWorldWidth * START_SCREEN_X_RATIO;
      if (!Number.isFinite(this.cameraX)) {
        this.cameraX = desiredX;
        return;
      }
      const lerpFactor = clamp(delta * 4.6, CAMERA_LERP_MIN, CAMERA_LERP_MAX);
      this.cameraX += (desiredX - this.cameraX) * lerpFactor;
    }

    updateHud() {
      if (!this.distanceElement || !this.speedElement || !this.altitudeElement) {
        return;
      }
      const distanceMeters = Math.max(0, this.distanceTravelled) / PIXELS_PER_METER;
      const speedKmh = this.currentSpeedKmh;
      const groundY = this.terrain.getHeight(this.player.x);
      const altitude = clamp((groundY - this.player.y) / PIXELS_PER_METER, 0, 9999);
      this.distanceElement.textContent = formatNumber(distanceMeters, 0);
      this.speedElement.textContent = formatNumber(speedKmh, speedKmh >= 50 ? 0 : 1);
      this.altitudeElement.textContent = formatNumber(altitude, altitude >= 10 ? 0 : 1);
      if (this.stats && typeof this.stats === 'object') {
        let statsChanged = false;
        const previousDistance = Number.isFinite(this.lastRecordedRunDistance)
          ? this.lastRecordedRunDistance
          : 0;
        if (!Number.isFinite(this.stats.totalDistance) || this.stats.totalDistance < 0) {
          this.stats.totalDistance = 0;
        }
        if (distanceMeters >= previousDistance) {
          const delta = distanceMeters - previousDistance;
          if (delta > 0) {
            this.stats.totalDistance += delta;
            const accumulator = Number.isFinite(this.distanceAutosaveAccumulator)
              ? this.distanceAutosaveAccumulator
              : 0;
            const combined = accumulator + delta;
            if (combined >= 1) {
              statsChanged = true;
              this.distanceAutosaveAccumulator = combined - Math.floor(combined);
            } else {
              this.distanceAutosaveAccumulator = combined;
            }
          }
        } else {
          if (Number.isFinite(this.distanceAutosaveAccumulator) && this.distanceAutosaveAccumulator > 0) {
            statsChanged = true;
          }
          this.distanceAutosaveAccumulator = 0;
        }
        this.lastRecordedRunDistance = distanceMeters;

        if (distanceMeters > (this.stats.bestDistance || 0)) {
          this.stats.bestDistance = distanceMeters;
          statsChanged = true;
        }
        if (speedKmh > (this.stats.bestSpeed || 0)) {
          this.stats.bestSpeed = speedKmh;
          statsChanged = true;
        }
        if (altitude > (this.stats.maxAltitude || 0)) {
          this.stats.maxAltitude = altitude;
          statsChanged = true;
        }
        if (statsChanged) {
          this.scheduleAutosave();
        }
      }
      if (this.statusElement && this.messages) {
        const nextState = this.player.onGround ? (this.isPressing ? 'hold' : 'ready') : 'air';
        if (nextState !== this.statusState) {
          this.statusState = nextState;
          const message = this.messages[nextState] || '';
          if (typeof this.statusElement.textContent === 'string') {
            this.statusElement.textContent = message;
          }
        }
      }
    }

    render() {
      if (!this.ctx) {
        return;
      }
      const ctx = this.ctx;
      ctx.save();
      ctx.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
      ctx.clearRect(0, 0, this.viewWidth, this.viewHeight);
      this.drawSky(ctx);
      this.drawTerrain(ctx);
      this.drawPlayer(ctx);
      ctx.restore();
    }

    drawSky(ctx) {
      const gradient = ctx.createLinearGradient(0, 0, 0, this.viewHeight);
      gradient.addColorStop(0, 'rgba(10, 16, 45, 1)');
      gradient.addColorStop(0.55, 'rgba(9, 20, 52, 1)');
      gradient.addColorStop(1, 'rgba(4, 9, 24, 1)');
      ctx.fillStyle = gradient;
      ctx.fillRect(0, 0, this.viewWidth, this.viewHeight);

      if (this.skyDots?.length) {
        ctx.save();
        ctx.fillStyle = '#dbe7ff';
        this.skyDots.forEach(dot => {
          const x = dot.x * this.viewWidth;
          const y = dot.y * this.viewHeight * 0.8;
          ctx.globalAlpha = dot.alpha;
          ctx.beginPath();
          ctx.arc(x, y, dot.radius, 0, Math.PI * 2);
          ctx.fill();
        });
        ctx.restore();
      }
    }

    drawTerrain(ctx) {
      const points = this.terrain.getPoints();
      if (!points.length) {
        return;
      }
      const scale = this.cameraScale;
      const viewWorldWidth = this.viewWidth / scale;
      const startX = this.cameraX - viewWorldWidth * 0.25;
      const endX = this.cameraX + viewWorldWidth * 1.1;
      ctx.save();
      ctx.beginPath();
      ctx.moveTo((startX - this.cameraX) * scale, this.viewHeight);
      for (let index = 0; index < points.length; index += 1) {
        const point = points[index];
        if (point.x < startX) {
          continue;
        }
        if (point.x > endX) {
          ctx.lineTo((endX - this.cameraX) * scale, this.viewHeight);
          break;
        }
        const screenX = (point.x - this.cameraX) * scale;
        const screenY = (point.y - this.cameraY) * scale;
        ctx.lineTo(screenX, screenY);
      }
      ctx.lineTo((endX - this.cameraX) * scale, this.viewHeight);
      ctx.closePath();

      const fillGradient = ctx.createLinearGradient(0, this.viewHeight * 0.4, 0, this.viewHeight);
      fillGradient.addColorStop(0, 'rgba(24, 40, 84, 0.92)');
      fillGradient.addColorStop(0.55, 'rgba(20, 54, 112, 0.95)');
      fillGradient.addColorStop(1, 'rgba(8, 16, 40, 1)');
      ctx.fillStyle = fillGradient;
      ctx.fill();

      ctx.lineWidth = Math.max(1.5, this.viewHeight * 0.004);
      ctx.strokeStyle = 'rgba(132, 196, 255, 0.45)';
      ctx.stroke();

      ctx.restore();
    }

    drawPlayer(ctx) {
      const scale = this.cameraScale;
      const radius = this.getBallRadius();
      const scaledRadius = Math.max(radius * scale, 6);
      const baseScreenX = (this.player.x - this.cameraX) * scale;
      const baseScreenY = (this.player.y - this.cameraY) * scale;
      const centerOffsetWorld = radius * 0.4;
      const centerScreenY = (this.player.y - centerOffsetWorld - this.cameraY) * scale;
      const spriteReady = ENERGY_BALL_SPRITE.loaded && ENERGY_BALL_SPRITE.image;
      const colors = this.getActiveBallColors();
      const effectiveColors = colors.length ? colors : [{ index: 0, alpha: 1 }];
      const nowMs = Number.isFinite(this.elapsedTimeMs) ? this.elapsedTimeMs : 0;
      const timeSeconds = nowMs / 1000;
      const pulse = 0.55 + 0.35 * Math.sin(timeSeconds * 7.1);
      const trailPoints = Array.isArray(this.player?.trail) ? this.player.trail : [];
      const variantCount = Math.max(1, ENERGY_BALL_SPRITE.colorCount || effectiveColors.length || 1);
      const normalizeSpriteIndex = value => {
        const numeric = Number.isFinite(value) ? Math.floor(value) : 0;
        return ((numeric % variantCount) + variantCount) % variantCount;
      };

      ctx.save();
      ctx.translate(baseScreenX, baseScreenY);
      const shadowGradient = ctx.createRadialGradient(
        0,
        scaledRadius * 0.6,
        scaledRadius * 0.35,
        0,
        scaledRadius,
        scaledRadius * 1.1
      );
      shadowGradient.addColorStop(0, 'rgba(0, 0, 0, 0.4)');
      shadowGradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = shadowGradient;
      ctx.beginPath();
      ctx.ellipse(0, scaledRadius * 0.8, scaledRadius * 1.2, scaledRadius * 0.45, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      if (trailPoints.length) {
        if (spriteReady) {
          const sprite = ENERGY_BALL_SPRITE;
          const spriteScale = (scaledRadius * 2) / sprite.frameWidth;
          const destWidth = sprite.frameWidth * spriteScale;
          const destHeight = sprite.trailHeight * spriteScale;
          ctx.save();
          ctx.globalCompositeOperation = 'lighter';
          trailPoints.forEach(point => {
            if (!point || typeof point.time !== 'number') {
              return;
            }
            const age = nowMs - point.time;
            if (age < 0 || age > BALL_TRAIL_MAX_AGE_MS) {
              return;
            }
            const lifeRatio = clamp(1 - age / BALL_TRAIL_MAX_AGE_MS, 0, 1);
            if (lifeRatio <= 0) {
              return;
            }
            const screenX = (point.x - this.cameraX) * scale;
            const screenY = (point.y - this.cameraY) * scale;
            const vx = Number.isFinite(point.vx) ? point.vx : 0;
            const vy = Number.isFinite(point.vy) ? point.vy : 0;
            const storedRotation = Number.isFinite(point.rotation) ? point.rotation : null;
            const rotation =
              storedRotation != null
                ? storedRotation
                : vx === 0 && vy === 0
                ? Number.isFinite(this.lastTrailRotation)
                  ? this.lastTrailRotation
                  : 0
                : Math.atan2(vx, -vy);
            effectiveColors.forEach(color => {
              const alpha = (0.25 + lifeRatio * 0.45) * color.alpha;
              if (alpha <= 0) {
                return;
              }
              const spriteIndex = normalizeSpriteIndex(color.index);
              ctx.save();
              ctx.translate(screenX, screenY);
              if (rotation !== 0) {
                ctx.rotate(rotation);
              }
              ctx.scale(1, -1);
              ctx.globalAlpha = clamp(alpha, 0, 1);
              ctx.drawImage(
                sprite.image,
                spriteIndex * sprite.frameWidth,
                sprite.normalTrailOffsetY,
                sprite.frameWidth,
                sprite.trailHeight,
                -destWidth / 2,
                -destHeight,
                destWidth,
                destHeight
              );
              ctx.restore();
            });
          });
          ctx.restore();
        } else {
          ctx.save();
          ctx.globalCompositeOperation = 'lighter';
          trailPoints.forEach(point => {
            if (!point || typeof point.time !== 'number') {
              return;
            }
            const age = nowMs - point.time;
            if (age < 0 || age > BALL_TRAIL_MAX_AGE_MS) {
              return;
            }
            const lifeRatio = clamp(1 - age / BALL_TRAIL_MAX_AGE_MS, 0, 1);
            if (lifeRatio <= 0) {
              return;
            }
            const screenX = (point.x - this.cameraX) * scale;
            const screenY = (point.y - this.cameraY) * scale;
            effectiveColors.forEach(color => {
              const palette = getFallbackEnergyBallColor(color.index);
              const alpha = (0.12 + lifeRatio * 0.38) * color.alpha;
              if (alpha <= 0) {
                return;
              }
              ctx.save();
              ctx.globalAlpha = clamp(alpha, 0, 1);
              ctx.fillStyle = palette.trail;
              const radiusMultiplier = 0.7 + lifeRatio * 0.6;
              ctx.beginPath();
              ctx.arc(screenX, screenY, scaledRadius * radiusMultiplier, 0, Math.PI * 2);
              ctx.fill();
              ctx.restore();
            });
          });
          ctx.restore();
        }
      }

      ctx.save();
      ctx.translate(baseScreenX, centerScreenY);
      if (spriteReady) {
        const sprite = ENERGY_BALL_SPRITE;
        const spriteScale = (scaledRadius * 2) / sprite.frameWidth;
        const destWidth = sprite.frameWidth * spriteScale;
        const destHeight = sprite.frameHeight * spriteScale;
        ctx.save();
        ctx.globalCompositeOperation = 'lighter';
        effectiveColors.forEach(color => {
          const alpha = clamp(color.alpha, 0, 1);
          if (alpha <= 0) {
            return;
          }
          const glowAlpha = (0.22 + pulse * 0.28) * alpha;
          if (glowAlpha <= 0) {
            return;
          }
          ctx.globalAlpha = clamp(glowAlpha, 0, 1);
          const glowRadius = scaledRadius * (1.2 + pulse * 0.35);
          ctx.fillStyle = 'rgba(255, 255, 255, 0.75)';
          ctx.beginPath();
          ctx.arc(0, 0, glowRadius, 0, Math.PI * 2);
          ctx.fill();
        });
        ctx.restore();
        effectiveColors.forEach(color => {
          const alpha = clamp(color.alpha, 0, 1);
          if (alpha <= 0) {
            return;
          }
          const spriteIndex = normalizeSpriteIndex(color.index);
          ctx.save();
          ctx.globalAlpha = alpha;
          ctx.scale(1, -1);
          ctx.drawImage(
            sprite.image,
            spriteIndex * sprite.frameWidth,
            0,
            sprite.frameWidth,
            sprite.frameHeight,
            -destWidth / 2,
            destHeight / 2,
            destWidth,
            -destHeight
          );
          ctx.restore();
        });
      } else {
        effectiveColors.forEach(color => {
          const alpha = clamp(color.alpha, 0, 1);
          if (alpha <= 0) {
            return;
          }
          const palette = getFallbackEnergyBallColor(color.index);
          ctx.save();
          ctx.globalCompositeOperation = 'lighter';
          ctx.globalAlpha = alpha;
          const gradient = ctx.createRadialGradient(
            -scaledRadius * 0.25,
            -scaledRadius * 0.25,
            scaledRadius * 0.2,
            0,
            0,
            scaledRadius
          );
          gradient.addColorStop(0, palette.core);
          gradient.addColorStop(1, palette.rim);
          ctx.fillStyle = gradient;
          ctx.beginPath();
          ctx.arc(0, 0, scaledRadius, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        });
      }
      ctx.restore();
    }
  }

  window.WaveGame = WaveGame;
})();
