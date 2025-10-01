(() => {
  const PIXELS_PER_METER = 60;
  const BASE_GRAVITY = 900;
  const DIVE_MULTIPLIER = 1.65;
  const BASE_PUSH_ACCEL = 14;
  const START_GROUND_SPEED = 90;
  const MIN_LANDING_SPEED = 18;
  const GROUND_DRAG = 0.972;
  const HOLDING_DRAG = 0.948;
  const AIR_DRAG = 0.993;
  const JUMP_BASE = 180;
  const JUMP_SPEED_RATIO = 0.4;
  const MAX_JUMP_IMPULSE = 380;
  const TERRAIN_SAMPLE_SPACING = 36;
  const CAMERA_LERP_MIN = 0.08;
  const CAMERA_LERP_MAX = 0.25;
  const MAX_FRAME_DELTA = 1 / 30;

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
      this.currentX = 0;
      this.currentY = this.baseLevel;
      this.segmentCount = 0;
    }

    configure({ minY, maxY, baseLevel, sampleSpacing } = {}) {
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
      this.currentY = clamp(this.currentY ?? this.baseLevel, this.minY, this.maxY);
    }

    reset(startX, endX) {
      this.points = [];
      this.currentX = startX;
      this.currentY = this.baseLevel;
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
      const length = randomInRange(260, 520);
      const amplitude = randomInRange(36, 112);
      const steps = Math.max(8, Math.round(length / this.sampleSpacing));
      const direction = Math.random() < 0.5 ? -1 : 1;
      const baseShift = randomInRange(-80, 80);
      const nextBase = clamp(this.baseLevel + baseShift, this.minY + 30, this.maxY - 30);
      const startY = this.currentY;

      for (let index = 1; index <= steps; index += 1) {
        const t = index / steps;
        const eased = easeInOutSine(t);
        const wave = Math.sin(t * Math.PI);
        const base = lerp(startY, nextBase, eased);
        const y = clamp(base + wave * amplitude * direction, this.minY, this.maxY);
        this.currentX += length / steps;
        this.currentY = y;
        this.points.push({ x: this.currentX, y });
      }

      this.baseLevel = nextBase;
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

    getPoints() {
      return this.points;
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

      this.pixelRatio = 1;
      this.viewWidth = 0;
      this.viewHeight = 0;
      this.cameraX = 0;

      this.terrain = new TerrainGenerator();
      this.player = {
        x: 0,
        y: 0,
        speed: START_GROUND_SPEED,
        vx: 0,
        vy: 0,
        onGround: true
      };

      this.distanceTravelled = 0;
      this.isPressing = false;
      this.pendingRelease = false;
      this.keyboardPressed = false;
      this.activePointers = new Set();
      this.statusState = null;

      this.skyDots = [];

      this.running = false;
      this.lastTimestamp = null;
      this.frameHandle = null;

      this.messages = {
        ready: translate('index.sections.wave.status.ready', 'Relâchez pour bondir sur la prochaine montée.'),
        hold: translate('index.sections.wave.status.hold', 'Maintenez pour piquer et gagner de la vitesse.'),
        air: translate('index.sections.wave.status.air', 'En vol ! Orientez-vous pour reprendre de la vitesse.')
      };

      this.handleResize = this.handleResize.bind(this);
      this.handlePointerDown = this.handlePointerDown.bind(this);
      this.handlePointerUp = this.handlePointerUp.bind(this);
      this.handlePointerCancel = this.handlePointerCancel.bind(this);
      this.handleKeyDown = this.handleKeyDown.bind(this);
      this.handleKeyUp = this.handleKeyUp.bind(this);
      this.tick = this.tick.bind(this);

      this.attachEvents();
      this.handleResize();
      this.resetState();
      this.render(0);
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
      const minY = height * 0.38;
      const maxY = height * 0.82;
      const baseLevel = height * 0.68;
      this.terrain.configure({ minY, maxY, baseLevel, sampleSpacing: TERRAIN_SAMPLE_SPACING });
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
      const span = this.viewWidth * 3;
      const startX = -this.viewWidth;
      this.terrain.reset(startX, startX + span);
      this.player.x = 0;
      this.player.y = this.terrain.getHeight(this.player.x);
      this.player.speed = START_GROUND_SPEED;
      this.player.vx = 0;
      this.player.vy = 0;
      this.player.onGround = true;
      this.cameraX = this.player.x - this.viewWidth * 0.35;
      this.distanceTravelled = 0;
      this.pendingRelease = false;
      this.isPressing = false;
      this.keyboardPressed = false;
      this.activePointers.clear();
      this.lastTimestamp = null;
      this.statusState = null;
      this.updateHud(0);
    }

    resetState() {
      this.resetTerrain();
    }

    handlePointerDown(event) {
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
      this.isPressing = pressed;
      if (!pressed) {
        this.pendingRelease = this.player.onGround;
      } else {
        this.pendingRelease = false;
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

    onEnter() {
      this.start();
    }

    onLeave() {
      this.stop();
      this.setPressingState(false);
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
      const targetMaxX = this.cameraX + this.viewWidth * 2.6;
      const pruneBefore = this.cameraX - this.viewWidth * 1.2;
      this.terrain.ensure(targetMaxX);
      this.terrain.prune(pruneBefore);

      if (this.player.onGround) {
        const slopeAngle = this.terrain.getSlopeAngle(this.player.x);
        const tangentX = Math.cos(slopeAngle);
        const tangentY = Math.sin(slopeAngle);
        const gravityAccel = BASE_GRAVITY * (this.isPressing ? DIVE_MULTIPLIER : 1);
        const slopeAccel = -gravityAccel * Math.sin(slopeAngle);
        const pushAccel = this.isPressing ? BASE_PUSH_ACCEL : 0;
        const acceleration = slopeAccel + pushAccel;
        this.player.speed += acceleration * delta;
        const dragFactor = this.isPressing ? HOLDING_DRAG : GROUND_DRAG;
        const decay = Math.pow(dragFactor, delta * 60);
        this.player.speed *= decay;
        if (this.player.speed < 0.001) {
          this.player.speed = 0;
        }
        this.player.vx = this.player.speed * tangentX;
        this.player.vy = this.player.speed * tangentY;
        this.player.x += this.player.vx * delta;
        this.player.y = this.terrain.getHeight(this.player.x);
        if (!this.isPressing && this.pendingRelease && slopeAngle < -degToRad(6) && this.player.speed > 0) {
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
        this.player.vy += BASE_GRAVITY * delta;
        const drag = Math.pow(AIR_DRAG, delta * 60);
        this.player.vx *= drag;
        this.player.vy *= drag;
        this.player.x += this.player.vx * delta;
        this.player.y += this.player.vy * delta;
        const groundY = this.terrain.getHeight(this.player.x);
        if (this.player.y >= groundY) {
          this.player.y = groundY;
          const slopeAngle = this.terrain.getSlopeAngle(this.player.x);
          const tangentX = Math.cos(slopeAngle);
          const tangentY = Math.sin(slopeAngle);
          const projectedSpeed = this.player.vx * tangentX + this.player.vy * tangentY;
          const dampened = Math.max(Math.abs(projectedSpeed) * 0.85, 0);
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
      this.updateCamera(delta);
      this.updateHud(delta);
    }

    updateCamera(delta) {
      const desired = this.player.x - this.viewWidth * 0.35;
      if (!Number.isFinite(this.cameraX)) {
        this.cameraX = desired;
        return;
      }
      const lerpFactor = clamp(delta * 4.6, CAMERA_LERP_MIN, CAMERA_LERP_MAX);
      this.cameraX += (desired - this.cameraX) * lerpFactor;
    }

    updateHud() {
      if (!this.distanceElement || !this.speedElement || !this.altitudeElement) {
        return;
      }
      const distanceMeters = Math.max(0, this.distanceTravelled) / PIXELS_PER_METER;
      const speed = Math.hypot(this.player.vx, this.player.vy);
      const speedKmh = Math.max(0, speed / PIXELS_PER_METER * 3.6);
      const groundY = this.terrain.getHeight(this.player.x);
      const altitude = clamp((groundY - this.player.y) / PIXELS_PER_METER, 0, 9999);
      this.distanceElement.textContent = formatNumber(distanceMeters, 0);
      this.speedElement.textContent = formatNumber(speedKmh, speedKmh >= 50 ? 0 : 1);
      this.altitudeElement.textContent = formatNumber(altitude, altitude >= 10 ? 0 : 1);
      if (this.statusElement) {
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
      const startX = this.cameraX - this.viewWidth * 0.25;
      const endX = this.cameraX + this.viewWidth * 1.1;
      ctx.save();
      ctx.beginPath();
      ctx.moveTo(startX - this.cameraX, this.viewHeight);
      for (let index = 0; index < points.length; index += 1) {
        const point = points[index];
        if (point.x < startX) {
          continue;
        }
        if (point.x > endX) {
          ctx.lineTo(endX - this.cameraX, this.viewHeight);
          break;
        }
        const screenX = point.x - this.cameraX;
        ctx.lineTo(screenX, point.y);
      }
      ctx.lineTo(endX - this.cameraX, this.viewHeight);
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
      const radius = clamp(this.viewHeight * 0.04, 12, 28);
      const x = this.player.x - this.cameraX;
      const y = this.player.y;
      ctx.save();
      ctx.translate(x, y);

      const shadowGradient = ctx.createRadialGradient(0, radius * 0.6, radius * 0.35, 0, radius, radius * 1.1);
      shadowGradient.addColorStop(0, 'rgba(0, 0, 0, 0.4)');
      shadowGradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = shadowGradient;
      ctx.beginPath();
      ctx.ellipse(0, radius * 0.8, radius * 1.2, radius * 0.45, 0, 0, Math.PI * 2);
      ctx.fill();

      ctx.translate(0, -radius * 0.4);
      const bodyGradient = ctx.createRadialGradient(0, -radius * 0.2, radius * 0.2, 0, 0, radius);
      bodyGradient.addColorStop(0, '#f8fbff');
      bodyGradient.addColorStop(0.55, '#9ad7ff');
      bodyGradient.addColorStop(1, '#2a6ed8');
      ctx.fillStyle = bodyGradient;
      ctx.beginPath();
      ctx.arc(0, 0, radius, 0, Math.PI * 2);
      ctx.fill();

      const trailLength = clamp(radius * 3, 30, 60);
      const trailWidth = radius * 0.45;
      const trailGradient = ctx.createLinearGradient(-trailLength, 0, 0, 0);
      trailGradient.addColorStop(0, 'rgba(92, 208, 255, 0)');
      trailGradient.addColorStop(0.65, 'rgba(92, 208, 255, 0.2)');
      trailGradient.addColorStop(1, 'rgba(92, 208, 255, 0.6)');
      ctx.fillStyle = trailGradient;
      ctx.beginPath();
      ctx.roundRect(-trailLength, -trailWidth * 0.5, trailLength, trailWidth, trailWidth * 0.5);
      ctx.fill();

      ctx.restore();
    }
  }

  window.WaveGame = WaveGame;
})();
