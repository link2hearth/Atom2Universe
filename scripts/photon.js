(() => {
  const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const PHOTON_CONFIG_SOURCE =
    GLOBAL_CONFIG && typeof GLOBAL_CONFIG === 'object' && GLOBAL_CONFIG.arcade
      && typeof GLOBAL_CONFIG.arcade === 'object'
      ? GLOBAL_CONFIG.arcade.photon
      : null;

  const DEFAULT_COLOR_DEFS = {
    blue: {
      bar: '#0b2d6f',
      barEdge: '#3c67b3',
      haloInner: 'rgba(70, 145, 255, 0.9)',
      haloMid: 'rgba(54, 126, 240, 0.52)',
      haloOuter: 'rgba(44, 108, 228, 0.2)'
    },
    red: {
      bar: '#a01269',
      barEdge: '#e878b6',
      haloInner: 'rgba(255, 118, 186, 0.92)',
      haloMid: 'rgba(238, 84, 158, 0.5)',
      haloOuter: 'rgba(222, 60, 140, 0.18)'
    }
  };

  const DEFAULT_GRID_PALETTE = {
    stroke: 'rgba(120, 185, 255, 0.38)',
    shadow: 'rgba(120, 185, 255, 0.25)',
    dot: 'rgba(173, 212, 255, 0.22)'
  };

  const DEFAULT_BUBBLE_PALETTE = {
    highlightInner: 'rgba(255, 228, 236, 0.75)',
    highlightMid: 'rgba(255, 160, 194, 0.28)',
    highlightOuter: 'rgba(255, 120, 170, 0.05)',
    ring: 'rgba(255, 192, 216, 0.28)'
  };

  const HALO_PATTERN_CONFIG = {
    default: {
      type: 'grid'
    },
    blue: {
      type: 'grid',
      gridPalette: {
        stroke: 'rgba(110, 185, 255, 0.42)',
        shadow: 'rgba(86, 162, 255, 0.32)',
        dot: 'rgba(142, 205, 255, 0.28)'
      }
    },
    red: {
      type: 'bubble',
      bubblePalette: {
        highlightInner: 'rgba(255, 188, 212, 0.84)',
        highlightMid: 'rgba(255, 132, 190, 0.42)',
        highlightOuter: 'rgba(255, 96, 170, 0.1)',
        ring: 'rgba(255, 162, 210, 0.32)'
      }
    }
  };

  const DEFAULT_BAR_SETTINGS = {
    desiredCount: 3,
    spawnGap: 90
  };

  const DEFAULT_GEOMETRY_SETTINGS = {
    barWidthRatio: 0.78,
    barWidthMin: 200,
    barWidthMax: 1100,
    barHeightRatio: 0.08,
    barHeightMin: 42,
    barHeightMax: 80,
    haloHeightRatio: 0.12,
    haloHeightMin: 60,
    haloHeightMax: 100
  };

  const DEFAULT_SPEED_SETTINGS = {
    baseRatio: 0.78,
    min: 180,
    max: 320,
    initialReduction: 0.3,
    ramp: {
      intervalSeconds: 10,
      increment: 0.01,
      maxBonus: 0.5
    }
  };

  const BAR_TEXTURE_SPECS = {
    blue: {
      src: 'assets/image/Bulle.png',
      naturalWidth: 1233,
      naturalHeight: 325
    },
    red: {
            src: 'assets/image/Grille.png',
      naturalWidth: 1920,
      naturalHeight: 227
    }
  };

  const WIDTH_SHRINK_RATIO = 0.25;

  const isPlainObject = value => value && typeof value === 'object' && !Array.isArray(value);

  const readNumber = (value, fallback, { min, max } = {}) => {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    let result = numeric;
    if (typeof min === 'number') {
      result = Math.max(min, result);
    }
    if (typeof max === 'number') {
      result = Math.min(max, result);
    }
    return result;
  };

  const toPositiveInteger = (value, fallback) => {
    const numeric = Number(value);
    return Number.isInteger(numeric) && numeric > 0 ? numeric : fallback;
  };

  const source = isPlainObject(PHOTON_CONFIG_SOURCE) ? PHOTON_CONFIG_SOURCE : null;

  const COLOR_DEFS = (() => {
    const overrides = source && isPlainObject(source.colors) ? source.colors : null;
    if (!overrides) {
      return DEFAULT_COLOR_DEFS;
    }
    const entries = Object.entries(DEFAULT_COLOR_DEFS).map(([key, value]) => {
      const override = overrides[key];
      if (isPlainObject(override)) {
        return [key, { ...value, ...override }];
      }
      return [key, value];
    });
    Object.entries(overrides).forEach(([key, value]) => {
      if (!DEFAULT_COLOR_DEFS[key] && isPlainObject(value)) {
        entries.push([key, { ...value }]);
      }
    });
    return entries.reduce((acc, [key, value]) => {
      acc[key] = value;
      return acc;
    }, {});
  })();

  const BARS_CONFIG = (() => {
    const barsSource = source && isPlainObject(source.bars) ? source.bars : null;
    const desiredCount = toPositiveInteger(
      barsSource?.desiredCount ?? source?.desiredBarCount,
      DEFAULT_BAR_SETTINGS.desiredCount
    );
    const spawnGap = readNumber(
      barsSource?.spawnGap ?? source?.spawnGap,
      DEFAULT_BAR_SETTINGS.spawnGap,
      { min: 10 }
    );
    return {
      desiredCount,
      spawnGap
    };
  })();

  const GEOMETRY_CONFIG = (() => {
    const geometrySource = source && isPlainObject(source.geometry) ? source.geometry : null;
    return {
      barWidthRatio: readNumber(
        geometrySource?.barWidthRatio,
        DEFAULT_GEOMETRY_SETTINGS.barWidthRatio,
        { min: 0.1, max: 1 }
      ),
      barWidthMin: readNumber(
        geometrySource?.barWidthMin,
        DEFAULT_GEOMETRY_SETTINGS.barWidthMin,
        { min: 1 }
      ),
      barWidthMax: readNumber(
        geometrySource?.barWidthMax,
        DEFAULT_GEOMETRY_SETTINGS.barWidthMax,
        { min: 1 }
      ),
      barHeightRatio: readNumber(
        geometrySource?.barHeightRatio,
        DEFAULT_GEOMETRY_SETTINGS.barHeightRatio,
        { min: 0.02, max: 0.3 }
      ),
      barHeightMin: readNumber(
        geometrySource?.barHeightMin,
        DEFAULT_GEOMETRY_SETTINGS.barHeightMin,
        { min: 1 }
      ),
      barHeightMax: readNumber(
        geometrySource?.barHeightMax,
        DEFAULT_GEOMETRY_SETTINGS.barHeightMax,
        { min: 1 }
      ),
      haloHeightRatio: readNumber(
        geometrySource?.haloHeightRatio,
        DEFAULT_GEOMETRY_SETTINGS.haloHeightRatio,
        { min: 0.05, max: 0.3 }
      ),
      haloHeightMin: readNumber(
        geometrySource?.haloHeightMin,
        DEFAULT_GEOMETRY_SETTINGS.haloHeightMin,
        { min: 1 }
      ),
      haloHeightMax: readNumber(
        geometrySource?.haloHeightMax,
        DEFAULT_GEOMETRY_SETTINGS.haloHeightMax,
        { min: 1 }
      )
    };
  })();

  const SPEED_CONFIG = (() => {
    const speedSource = source && isPlainObject(source.speed) ? source.speed : null;
    const rampSource = speedSource && isPlainObject(speedSource.ramp) ? speedSource.ramp : null;
    const minSpeed = readNumber(speedSource?.min, DEFAULT_SPEED_SETTINGS.min, { min: 0 });
    const maxSpeed = readNumber(speedSource?.max, DEFAULT_SPEED_SETTINGS.max, { min: minSpeed });
    return {
      baseRatio: readNumber(
        speedSource?.baseRatio,
        DEFAULT_SPEED_SETTINGS.baseRatio,
        { min: 0.01 }
      ),
      min: minSpeed,
      max: Math.max(minSpeed, maxSpeed),
      initialReduction: readNumber(
        speedSource?.initialReduction,
        DEFAULT_SPEED_SETTINGS.initialReduction,
        { min: 0, max: 0.95 }
      ),
      ramp: {
        intervalSeconds: readNumber(
          rampSource?.intervalSeconds,
          DEFAULT_SPEED_SETTINGS.ramp.intervalSeconds,
          { min: 0.1 }
        ),
        increment: readNumber(
          rampSource?.increment,
          DEFAULT_SPEED_SETTINGS.ramp.increment,
          { min: 0 }
        ),
        maxBonus: readNumber(
          rampSource?.maxBonus,
          DEFAULT_SPEED_SETTINGS.ramp.maxBonus,
          { min: 0 }
        )
      }
    };
  })();

  const COLOR_ORDER = (() => {
    if (source && Array.isArray(source.colorOrder)) {
      const filtered = source.colorOrder.filter(color => COLOR_DEFS[color]);
      if (filtered.length >= 2) {
        return filtered;
      }
    }
    const defaultOrder = ['blue', 'red'].filter(color => COLOR_DEFS[color]);
    const remaining = Object.keys(COLOR_DEFS).filter(color => !defaultOrder.includes(color));
    const combined = [...defaultOrder, ...remaining];
    return combined.length >= 2 ? combined : Object.keys(COLOR_DEFS);
  })();

  const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

  function drawRoundedRect(ctx, x, y, width, height, radius) {
    const r = Math.min(radius, width / 2, height / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + width - r, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + r);
    ctx.lineTo(x + width, y + height - r);
    ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
    ctx.lineTo(x + r, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  const seededRandom = (seed, offset = 0) => {
    const value = Math.sin((seed + offset) * 43758.5453123) * 43758.5453123;
    return value - Math.floor(value);
  };

  function drawGridPattern(ctx, seed, x, y, width, height, palette) {
    const normalizedSeed = Number.isFinite(seed) ? seed : 0.5;
    const baseSeed = normalizedSeed * 9973 + width * 0.015 + height * 0.021;
    const spacing = clamp(Math.min(width, height) / 5, 14, 68);
    const offsetX = (seededRandom(baseSeed, 1.137) - 0.5) * spacing;
    const offsetY = (seededRandom(baseSeed, 2.731) - 0.5) * spacing;

    ctx.save();
    ctx.lineWidth = Math.max(1, spacing * 0.08);
    const gridColors = { ...DEFAULT_GRID_PALETTE, ...(palette || {}) };
    ctx.strokeStyle = gridColors.stroke;
    ctx.shadowColor = gridColors.shadow;
    ctx.shadowBlur = spacing * 0.35;
    for (let posX = x + offsetX - spacing * 2; posX <= x + width + spacing * 2; posX += spacing) {
      ctx.beginPath();
      ctx.moveTo(posX, y);
      ctx.lineTo(posX, y + height);
      ctx.stroke();
    }
    for (let posY = y + offsetY - spacing * 2; posY <= y + height + spacing * 2; posY += spacing) {
      ctx.beginPath();
      ctx.moveTo(x, posY);
      ctx.lineTo(x + width, posY);
      ctx.stroke();
    }
    ctx.restore();

    ctx.save();
    const dotRadius = Math.min(spacing * 0.18, 6);
    ctx.fillStyle = gridColors.dot;
    const cols = Math.ceil(width / spacing) + 2;
    const rows = Math.ceil(height / spacing) + 2;
    for (let col = -1; col < cols; col += 1) {
      for (let row = -1; row < rows; row += 1) {
        if ((col + row) % 2 !== 0) {
          continue;
        }
        const centerX = x + col * spacing + spacing / 2 + offsetX;
        const centerY = y + row * spacing + spacing / 2 + offsetY;
        if (centerX < x - spacing || centerX > x + width + spacing) {
          continue;
        }
        if (centerY < y - spacing || centerY > y + height + spacing) {
          continue;
        }
        ctx.beginPath();
        ctx.arc(centerX, centerY, dotRadius, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.restore();
  }

  function drawBubblePattern(ctx, seed, x, y, width, height, palette) {
    const normalizedSeed = Number.isFinite(seed) ? seed : 0.35;
    const baseSeed = normalizedSeed * 7919 + width * 0.017 + height * 0.029;
    const area = Math.max(width * height, 1);
    const bubbleCount = Math.max(42, Math.floor(area / 9000));
    const maxRadius = clamp(height * 0.32, 12, height * 0.55);

    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    const bubbleColors = { ...DEFAULT_BUBBLE_PALETTE, ...(palette || {}) };
    for (let i = 0; i < bubbleCount; i += 1) {
      const noiseX = seededRandom(baseSeed, i * 1.37);
      const noiseY = seededRandom(baseSeed, i * 2.91);
      const noiseR = seededRandom(baseSeed, i * 3.73);
      const centerX = x + noiseX * width;
      const centerY = y + noiseY * height;
      const radius = clamp(maxRadius * (0.35 + noiseR * 0.65), height * 0.06, maxRadius);
      const highlightOffset = radius * 0.35;
      const gradient = ctx.createRadialGradient(
        centerX - highlightOffset,
        centerY - highlightOffset,
        radius * 0.12,
        centerX,
        centerY,
        radius
      );
      gradient.addColorStop(0, bubbleColors.highlightInner);
      gradient.addColorStop(0.45, bubbleColors.highlightMid);
      gradient.addColorStop(1, bubbleColors.highlightOuter);
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
      ctx.fill();

      ctx.globalAlpha = 0.8;
      ctx.lineWidth = Math.max(1, radius * 0.14);
      ctx.strokeStyle = bubbleColors.ring;
      ctx.beginPath();
      ctx.arc(centerX - radius * 0.18, centerY - radius * 0.2, radius * 0.46, 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 1;
    }
    ctx.restore();
  }

  function drawHaloPattern(ctx, color, seed, x, y, width, height) {
    if (!ctx || width <= 0 || height <= 0) {
      return;
    }
    ctx.save();
    ctx.beginPath();
    ctx.rect(x, y, width, height);
    ctx.clip();

    const patternConfig = HALO_PATTERN_CONFIG[color] || HALO_PATTERN_CONFIG.default;
    const patternType = patternConfig?.type || 'grid';
    if (patternType === 'bubble') {
      drawBubblePattern(ctx, seed, x, y, width, height, patternConfig?.bubblePalette);
    } else {
      drawGridPattern(ctx, seed, x, y, width, height, patternConfig?.gridPalette);
    }

    ctx.restore();
  }

  class PhotonGame {
    constructor({ canvas, onScoreChange, onColorChange, onGameOver } = {}) {
      this.canvas = canvas || null;
      this.context = this.canvas ? this.canvas.getContext('2d') : null;
      this.onScoreChange = typeof onScoreChange === 'function' ? onScoreChange : () => {};
      this.onColorChange = typeof onColorChange === 'function' ? onColorChange : () => {};
      this.onGameOver = typeof onGameOver === 'function' ? onGameOver : () => {};

      this.viewportWidth = 0;
      this.viewportHeight = 0;
      this.colorOrder = COLOR_ORDER.length ? COLOR_ORDER : ['blue', 'red'];
      this.currentColorIndex = 0;
      this.currentColor = this.colorOrder[this.currentColorIndex] ?? 'blue';
      this.score = 0;
      this.bars = [];
      this.desiredBarCount = BARS_CONFIG.desiredCount;
      this.spawnGap = BARS_CONFIG.spawnGap;
      this.state = 'idle';
      this.lastTimestamp = 0;
      this.animationFrame = null;
      this.elapsedTime = 0;

      this.haloPatternSeeds = Object.create(null);

      this._tick = this._tick.bind(this);

      this.barTextures = {};
      this.loadBarTextures();

      this.ensureHaloPatternSeed(this.currentColor);

      if (this.context && this.canvas) {
        this.resize();
        this.render();
      }
    }

    loadBarTextures() {
      if (typeof Image === 'undefined') {
        this.barTextures = {};
        return;
      }
      this.barTextures = {};
      Object.entries(BAR_TEXTURE_SPECS).forEach(([key, spec]) => {
        const image = new Image();
        const entry = {
          image,
          loaded: false,
          error: false,
          spec
        };
        image.addEventListener('load', () => {
          entry.loaded = true;
          if (this.context) {
            this.render();
          }
        });
        image.addEventListener('error', () => {
          entry.error = true;
        });
        image.src = spec.src;
        this.barTextures[key] = entry;
      });
    }

    ensureHaloPatternSeed(color) {
      if (!color) {
        return;
      }
      if (!this.haloPatternSeeds || typeof this.haloPatternSeeds !== 'object') {
        this.haloPatternSeeds = Object.create(null);
      }
      if (Object.prototype.hasOwnProperty.call(this.haloPatternSeeds, color)) {
        return;
      }
      let seed = 0;
      if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
        const buffer = new Uint32Array(1);
        crypto.getRandomValues(buffer);
        seed = (buffer[0] % 1000003) / 1000003;
      } else {
        seed = Math.random();
      }
      if (!Number.isFinite(seed) || seed <= 0) {
        seed = Math.random() || 0.5;
      }
      this.haloPatternSeeds[color] = seed;
    }

    getBarWidth() {
      const width = this.viewportWidth || 0;
      if (!width) return 0;
      return clamp(
        width * GEOMETRY_CONFIG.barWidthRatio,
        GEOMETRY_CONFIG.barWidthMin,
        GEOMETRY_CONFIG.barWidthMax
      );
    }

    getBarHeight() {
      const height = this.viewportHeight || 0;
      if (!height) return 0;
      return clamp(
        height * GEOMETRY_CONFIG.barHeightRatio,
        GEOMETRY_CONFIG.barHeightMin,
        GEOMETRY_CONFIG.barHeightMax
      );
    }

    getBarSpeed() {
      const height = this.viewportHeight || 0;
      if (!height) {
        return SPEED_CONFIG.min * this.getSpeedMultiplier();
      }
      const base = clamp(height * SPEED_CONFIG.baseRatio, SPEED_CONFIG.min, SPEED_CONFIG.max);
      return base * this.getSpeedMultiplier();
    }

    getHaloHeight() {
      const height = this.viewportHeight || 0;
      if (!height) return 140;
      return clamp(
        height * GEOMETRY_CONFIG.haloHeightRatio,
        GEOMETRY_CONFIG.haloHeightMin,
        GEOMETRY_CONFIG.haloHeightMax
      );
    }

    getSpeedMultiplier() {
      const baseMultiplier = Math.max(0, 1 - SPEED_CONFIG.initialReduction);
      const ramp = SPEED_CONFIG.ramp;
      if (!ramp || ramp.intervalSeconds <= 0 || ramp.increment <= 0) {
        return baseMultiplier;
      }
      const steps = Math.floor((this.elapsedTime || 0) / ramp.intervalSeconds);
      const cappedBonus = Math.min(ramp.maxBonus, steps * ramp.increment);
      return baseMultiplier * (1 + Math.max(0, cappedBonus));
    }

    resize() {
      if (!this.canvas || !this.context) {
        return;
      }
      const rect = this.canvas.getBoundingClientRect();
      const deviceRatio = typeof window !== 'undefined' && Number.isFinite(window.devicePixelRatio)
        ? window.devicePixelRatio
        : 1;
      const ratio = clamp(deviceRatio, 1, 3);
      const width = Math.max(1, rect.width);
      const height = Math.max(1, rect.height);
      const displayWidth = Math.floor(width * ratio);
      const displayHeight = Math.floor(height * ratio);
      if (this.canvas.width !== displayWidth || this.canvas.height !== displayHeight) {
        this.canvas.width = displayWidth;
        this.canvas.height = displayHeight;
      }
      this.context.setTransform(ratio, 0, 0, ratio, 0, 0);
      this.viewportWidth = width;
      this.viewportHeight = height;
      this.render();
    }

    ensureBarSupply() {
      if (!this.viewportHeight) {
        return;
      }
      while (this.bars.length < this.desiredBarCount) {
        this.spawnBar();
      }
    }

    spawnBar() {
      const barHeight = this.getBarHeight();
      const topMost = this.bars.reduce((min, bar) => Math.min(min, bar.y), Infinity);
      const startY = Number.isFinite(topMost)
        ? Math.min(-barHeight, topMost - (barHeight + this.spawnGap))
        : -barHeight;
      const palette = this.colorOrder.length ? this.colorOrder : ['blue', 'red'];
      const color = palette[Math.floor(Math.random() * palette.length)] ?? this.currentColor;
      this.bars.push({
        color,
        y: startY,
        height: barHeight,
        resolved: false
      });
    }

    start() {
      if (!this.context) {
        return;
      }
      this.stopLoop();
      this.state = 'running';
      this.score = 0;
      this.currentColorIndex = 0;
      this.currentColor = this.colorOrder[this.currentColorIndex] ?? this.currentColor;
      this.elapsedTime = 0;
      this.bars = [];
      this.ensureHaloPatternSeed(this.currentColor);
      this.onColorChange(this.currentColor);
      this.onScoreChange(this.score);
      this.ensureBarSupply();
      this.lastTimestamp = performance.now();
      this.render();
      this.animationFrame = requestAnimationFrame(this._tick);
    }

    stop() {
      this.stopLoop();
      this.state = 'idle';
      this.bars = [];
      this.currentColorIndex = 0;
      this.currentColor = this.colorOrder[this.currentColorIndex] ?? this.currentColor;
      this.elapsedTime = 0;
      this.ensureHaloPatternSeed(this.currentColor);
      this.onColorChange(this.currentColor);
      this.render();
    }

    pause() {
      if (this.state !== 'running') {
        return;
      }
      this.state = 'paused';
      this.stopLoop();
    }

    resume() {
      if (this.state !== 'paused') {
        return;
      }
      this.state = 'running';
      this.lastTimestamp = performance.now();
      this.animationFrame = requestAnimationFrame(this._tick);
    }

    onEnter() {
      this.resize();
      if (this.state === 'paused') {
        this.resume();
      } else {
        this.render();
      }
    }

    onLeave() {
      if (this.state === 'running') {
        this.pause();
      }
    }

    toggleColor() {
      if (this.state !== 'running') {
        return;
      }
      if (!this.colorOrder.length) {
        return;
      }
      this.currentColorIndex = (this.currentColorIndex + 1) % this.colorOrder.length;
      this.currentColor = this.colorOrder[this.currentColorIndex];
      this.ensureHaloPatternSeed(this.currentColor);
      this.onColorChange(this.currentColor);
      this.render();
    }

    stopLoop() {
      if (this.animationFrame != null) {
        cancelAnimationFrame(this.animationFrame);
        this.animationFrame = null;
      }
    }

    _tick(timestamp) {
      if (this.state !== 'running') {
        this.animationFrame = null;
        return;
      }
      const deltaSeconds = clamp((timestamp - this.lastTimestamp) / 1000, 0, 0.12);
      this.lastTimestamp = timestamp;
      this.update(deltaSeconds);
      this.render();
      if (this.state === 'running') {
        this.animationFrame = requestAnimationFrame(this._tick);
      }
    }

    update(deltaSeconds) {
      if (deltaSeconds <= 0 || !Number.isFinite(deltaSeconds)) {
        return;
      }
      this.elapsedTime += deltaSeconds;
      const speed = this.getBarSpeed();
      let activeIndex = -1;
      let activeBottom = -Infinity;
      for (let i = 0; i < this.bars.length; i += 1) {
        const bar = this.bars[i];
        bar.y += speed * deltaSeconds;
        const bottom = bar.y + bar.height;
        if (bottom > activeBottom) {
          activeBottom = bottom;
          activeIndex = i;
        }
      }
      const haloTop = (this.viewportHeight || 0) - this.getHaloHeight();
      if (activeIndex >= 0) {
        const activeBar = this.bars[activeIndex];
        if (activeBar && !activeBar.resolved && activeBar.y + activeBar.height >= haloTop) {
          if (activeBar.color === this.currentColor) {
            this.resolveActiveBar(activeIndex);
          } else {
            this.triggerGameOver();
            return;
          }
        }
      }
      // Clean up any bars that moved far beyond the screen
      this.bars = this.bars.filter(bar => bar.y <= (this.viewportHeight || 0) + this.spawnGap * 2);
      this.ensureBarSupply();
    }

    resolveActiveBar(index) {
      const removed = this.bars.splice(index, 1);
      if (removed.length) {
        this.score += 1;
        this.onScoreChange(this.score);
      }
      this.ensureBarSupply();
    }

    triggerGameOver() {
      this.state = 'gameover';
      this.stopLoop();
      this.render();
      this.onGameOver({ score: this.score, color: this.currentColor });
    }

    render() {
      if (!this.context) {
        return;
      }
      const ctx = this.context;
      const width = this.viewportWidth || this.canvas?.width || 0;
      const height = this.viewportHeight || this.canvas?.height || 0;
      if (!width || !height) {
        return;
      }
      ctx.save();
      ctx.clearRect(0, 0, width, height);

      const backgroundGradient = ctx.createLinearGradient(0, 0, 0, height);
      backgroundGradient.addColorStop(0, '#090d19');
      backgroundGradient.addColorStop(1, '#04060d');
      ctx.fillStyle = backgroundGradient;
      ctx.fillRect(0, 0, width, height);

      const baseBarWidth = this.getBarWidth();
      const baseBarX = (width - baseBarWidth) / 2;
      for (const bar of this.bars) {
        const colors = COLOR_DEFS[bar.color] || COLOR_DEFS.blue;
        const progress = clamp((bar.y + bar.height) / Math.max(1, height), 0, 1);
        const widthFactor = 1 - WIDTH_SHRINK_RATIO * progress;
        const targetWidth = Math.max(1, width * widthFactor);
        const drawX = (width - targetWidth) / 2;
        const drawHeight = bar.height;
        const textureEntry = this.barTextures[bar.color];
        if (textureEntry && textureEntry.loaded) {
          ctx.imageSmoothingEnabled = true;
          ctx.drawImage(textureEntry.image, drawX, bar.y, targetWidth, drawHeight);
        } else {
          const barGradient = ctx.createLinearGradient(drawX, bar.y, drawX + targetWidth, bar.y + drawHeight);
          barGradient.addColorStop(0, colors.barEdge);
          barGradient.addColorStop(0.2, colors.bar);
          barGradient.addColorStop(0.8, colors.bar);
          barGradient.addColorStop(1, colors.barEdge);
          drawRoundedRect(ctx, drawX, bar.y, targetWidth, drawHeight, Math.min(28, targetWidth / 4));
          ctx.fillStyle = barGradient;
          ctx.fill();
          ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
          ctx.lineWidth = 2;
          ctx.stroke();
        }
      }

      const haloHeight = this.getHaloHeight();
      const haloTop = height - haloHeight;
      const haloColors = COLOR_DEFS[this.currentColor] || COLOR_DEFS.blue;
      this.ensureHaloPatternSeed(this.currentColor);
      const haloGradient = ctx.createLinearGradient(0, haloTop, 0, height);
      haloGradient.addColorStop(0, haloColors.haloOuter);
      haloGradient.addColorStop(0.45, haloColors.haloMid);
      haloGradient.addColorStop(1, haloColors.haloInner);
      ctx.fillStyle = haloGradient;
      ctx.fillRect(0, haloTop, width, haloHeight);

      const haloSeed = this.haloPatternSeeds?.[this.currentColor];
      drawHaloPattern(ctx, this.currentColor, haloSeed, 0, haloTop, width, haloHeight);

      const haloGlow = ctx.createRadialGradient(
        width / 2,
        height - haloHeight * 0.35,
        haloHeight * 0.25,
        width / 2,
        height,
        haloHeight
      );
      haloGlow.addColorStop(0, haloColors.haloInner);
      haloGlow.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = haloGlow;
      ctx.fillRect(0, haloTop, width, haloHeight);

      const baseLineGradient = ctx.createLinearGradient(0, haloTop, 0, haloTop + 12);
      baseLineGradient.addColorStop(0, 'rgba(255, 255, 255, 0.25)');
      baseLineGradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
      ctx.fillStyle = baseLineGradient;
      ctx.fillRect(baseBarX * 0.2, haloTop - 6, width - baseBarX * 0.4, 12);

      ctx.restore();
    }
  }

  window.PhotonGame = PhotonGame;
})();
