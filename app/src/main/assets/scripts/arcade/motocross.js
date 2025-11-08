(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const DEFAULT_SEED = 'at2u';
  const DEFAULT_LENGTH = 1800;
  const DEFAULT_DIFFICULTY = 'normal';
  const MIN_LENGTH = 800;
  const MAX_LENGTH = 3200;
  const CHECKPOINT_INTERVAL = 400;
  const SLOPE_STEP = 0.12;
  const Y_TOL = 80;
  const ELEVATION_LIMIT = 260;

  const PHYSICS_STEP = 1 / 120;
  const MAX_FRAME_STEP = 1 / 30;
  const MASS = 120;
  const GRAVITY = 1800;
  const CHASSIS_WIDTH = 108;
  const CHASSIS_HEIGHT = 32;
  const WHEEL_BASE = 92;
  const WHEEL_RADIUS = 18;
  const WHEEL_VERTICAL_OFFSET = 20;
  const BIKE_INERTIA = (MASS * (CHASSIS_WIDTH * CHASSIS_WIDTH + CHASSIS_HEIGHT * CHASSIS_HEIGHT)) / 12;
  const ENGINE_FORCE = 6800;
  const BRAKE_FORCE = 5200;
  const TILT_TORQUE = 22000;
  const SPRING_STIFFNESS = 3600;
  const SPRING_DAMPING = 140;
  const FRICTION_DAMPING = 34;
  const FRICTION_COEFFICIENT = 0.85;
  const LINEAR_DAMPING = 0.12;
  const ANGULAR_DAMPING = 0.18;
  const NORMAL_CORRECTION_FACTOR = 0.55;
  const CAMERA_LOOK_AHEAD = 0.22;
  const CAMERA_OFFSET_Y = 140;
  const CAMERA_SMOOTH = 6.5;
  const FALL_EXTRA_MARGIN = 320;

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
    ? new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 })
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
    const numeric = Number.isFinite(value) ? value : 0;
    if (numberFormatter) {
      return numberFormatter.format(numeric);
    }
    return numeric.toFixed(1);
  }

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function lerp(current, target, factor) {
    return current + (target - current) * factor;
  }

  function hashSeed(value) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      let normalized = value | 0;
      if (normalized === 0) {
        normalized = 1;
      }
      return normalized >>> 0;
    }
    const text = typeof value === 'string' ? value : DEFAULT_SEED;
    let hash = 2166136261 >>> 0;
    for (let i = 0; i < text.length; i += 1) {
      hash ^= text.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
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
    if (tags.includes('hard')) {
      return 'hard';
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
      case 'hard':
        return [25, 90];
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

  function createBlock(id, tags, geo, speedRangeOverride) {
    const poly = createPolyline(geo);
    const start = poly[0];
    const end = poly[poly.length - 1];
    const slopeIn = computeSlope(poly[0], poly[1]);
    const slopeOut = computeSlope(poly[poly.length - 2], end);
    const length = end[0] - start[0];
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
      geo: Object.freeze(poly)
    });
  }

  function polyFlat(length) {
    return [[0, 0], [length, 0]];
  }

  function polySlope(length, dy) {
    return [[0, 0], [length * 0.4, dy * 0.5], [length, dy]];
  }

  function polyRollers(length, count, amplitude) {
    const steps = Math.max(2, count * 4);
    const points = [];
    for (let i = 0; i <= steps; i += 1) {
      const t = i / steps;
      const x = length * t;
      const y = Math.sin(t * Math.PI * count * 2) * amplitude;
      points.push([x, y]);
    }
    return points;
  }

  function polyWhoops(length, count, amplitude) {
    const steps = Math.max(2, count * 3);
    const points = [];
    for (let i = 0; i <= steps; i += 1) {
      const t = i / steps;
      const x = length * t;
      const y = Math.sin(t * Math.PI * count * 2) * amplitude * Math.pow(Math.sin(t * Math.PI), 0.6);
      points.push([x, y]);
    }
    return points;
  }

  function polyTableTop(length, height, tableLen) {
    const rampLen = Math.max(40, (length - tableLen) / 2);
    return [
      [0, 0],
      [rampLen * 0.45, -height * 0.5],
      [rampLen, -height],
      [rampLen + tableLen, -height],
      [length - rampLen * 0.4, -height * 0.35],
      [length, 0]
    ];
  }

  function polyGap(length, takeoffAngle, gapLen, landingSlope) {
    const takeoffLen = Math.max(50, length * 0.3);
    const landingLen = Math.max(70, length - takeoffLen - gapLen);
    const takeoffHeight = Math.tan(takeoffAngle) * takeoffLen;
    return [
      [0, 0],
      [takeoffLen * 0.5, -takeoffHeight * 0.5],
      [takeoffLen, -takeoffHeight],
      [takeoffLen + gapLen * 0.6, -takeoffHeight * 0.25],
      [takeoffLen + gapLen, -takeoffHeight * 0.1],
      [length - landingLen * 0.4, landingSlope * landingLen * 0.4],
      [length, landingSlope * landingLen]
    ];
  }

  function polyStep(length, height, plateau) {
    const rampLen = Math.max(50, (length - plateau) / 2);
    return [
      [0, 0],
      [rampLen * 0.4, -height * 0.5],
      [rampLen, -height],
      [rampLen + plateau, -height],
      [length, -height * 0.35]
    ];
  }

  function polyDrop(length, height, plateau) {
    const rampLen = Math.max(50, (length - plateau) / 2);
    return [
      [0, 0],
      [rampLen * 0.6, height * 0.5],
      [rampLen, height],
      [rampLen + plateau, height],
      [length, height * 0.3]
    ];
  }

  const START_BLOCK = createBlock('flat/start/01', ['flat', 'easy', 'starter'], polyFlat(200));

  const BLOCK_LIBRARY = Object.freeze([
    START_BLOCK,
    createBlock('flat/easy/02', ['flat', 'easy'], polyFlat(240)),
    createBlock('flat/normal/01', ['flat', 'normal'], polyFlat(280)),
    createBlock('flat/hard/01', ['flat', 'hard'], polyFlat(220)),
    createBlock('gentle_up/easy/01', ['gentle_up', 'easy'], polySlope(220, -40)),
    createBlock('gentle_up/normal/01', ['gentle_up', 'normal'], polySlope(260, -55)),
    createBlock('gentle_up/hard/01', ['gentle_up', 'hard'], polySlope(240, -70)),
    createBlock('gentle_down/easy/01', ['gentle_down', 'easy'], polySlope(220, 36)),
    createBlock('gentle_down/normal/01', ['gentle_down', 'normal'], polySlope(280, 48)),
    createBlock('gentle_down/hard/01', ['gentle_down', 'hard'], polySlope(260, 64)),
    createBlock('roller/easy/01', ['roller', 'easy'], polyRollers(240, 2, 12)),
    createBlock('roller/normal/01', ['roller', 'normal'], polyRollers(260, 2, 16)),
    createBlock('roller/normal/02', ['roller', 'normal'], polyRollers(300, 3, 18)),
    createBlock('roller/hard/01', ['roller', 'hard'], polyRollers(300, 3, 22)),
    createBlock('whoops/easy/01', ['whoops', 'easy'], polyWhoops(220, 3, 16)),
    createBlock('whoops/normal/01', ['whoops', 'normal'], polyWhoops(240, 4, 20)),
    createBlock('whoops/hard/01', ['whoops', 'hard'], polyWhoops(280, 5, 26)),
    createBlock('tabletop_jump/easy/01', ['tabletop_jump', 'easy'], polyTableTop(300, 48, 120)),
    createBlock('tabletop_jump/normal/01', ['tabletop_jump', 'normal'], polyTableTop(320, 60, 110)),
    createBlock('tabletop_jump/hard/01', ['tabletop_jump', 'hard'], polyTableTop(340, 72, 100)),
    createBlock('gap_jump/normal/01', ['gap_jump', 'normal'], polyGap(340, Math.PI / 9, 70, 0.32)),
    createBlock('gap_jump/normal/02', ['gap_jump', 'normal'], polyGap(320, Math.PI / 10, 60, 0.28)),
    createBlock('gap_jump/hard/01', ['gap_jump', 'hard'], polyGap(360, Math.PI / 8, 80, 0.35)),
    createBlock('step_up/normal/01', ['step_up', 'normal'], polyStep(260, 60, 60)),
    createBlock('step_up/normal/02', ['step_up', 'normal'], polyStep(300, 70, 80)),
    createBlock('step_up/hard/01', ['step_up', 'hard'], polyStep(280, 80, 70)),
    createBlock('step_down/easy/01', ['step_down', 'easy'], polyDrop(260, 60, 60)),
    createBlock('step_down/normal/01', ['step_down', 'normal'], polyDrop(280, 80, 70)),
    createBlock('step_down/hard/01', ['step_down', 'hard'], polyDrop(300, 96, 70)),
    createBlock('landing_pad/easy/01', ['landing_pad', 'easy'], polySlope(280, 24)),
    createBlock('landing_pad/easy/02', ['landing_pad', 'easy'], polySlope(260, 18)),
    createBlock('landing_pad/normal/01', ['landing_pad', 'normal'], polySlope(320, 18)),
    createBlock('landing_pad/hard/01', ['landing_pad', 'hard'], polySlope(340, 12)),
    createBlock('gentle_up/normal/02', ['gentle_up', 'normal'], polySlope(300, -60)),
    createBlock('gentle_down/normal/02', ['gentle_down', 'normal'], polySlope(300, 52))
  ]);

  const TRACK_BLOCKS = BLOCK_LIBRARY.filter(block => block !== START_BLOCK);
  const LANDING_BLOCKS = TRACK_BLOCKS.filter(block => block.tags.includes('landing_pad'));

  function matchesDifficulty(block, difficulty) {
    if (!block || !Array.isArray(block.tags)) {
      return false;
    }
    if (difficulty === 'hard') {
      return block.tags.includes('hard');
    }
    if (difficulty === 'easy') {
      return block.tags.includes('easy');
    }
    return block.tags.includes('normal') || block.tags.includes('easy');
  }

  function pickNextBlock(prevBlock, difficulty, rng, currentY) {
    const compatible = TRACK_BLOCKS.filter(block => {
      if (block === prevBlock) {
        return false;
      }
      if (Math.abs(prevBlock.slope_out - block.slope_in) > SLOPE_STEP) {
        return false;
      }
      if (Math.abs(block.y0) > Y_TOL) {
        return false;
      }
      return true;
    });

    let pool = compatible.filter(block => matchesDifficulty(block, difficulty));
    if (!pool.length) {
      pool = compatible.length ? compatible : TRACK_BLOCKS;
    }

    if (!pool.length) {
      return null;
    }

    if (currentY > ELEVATION_LIMIT * 0.6) {
      const rising = pool.filter(block => block.y1 <= 10);
      if (rising.length) {
        pool = rising;
      }
    } else if (currentY < -ELEVATION_LIMIT * 0.6) {
      const lowering = pool.filter(block => block.y1 >= -10);
      if (lowering.length) {
        pool = lowering;
      }
    }

    const index = Math.floor(rng() * pool.length) % pool.length;
    return pool[index];
  }

  function pickLandingBlock(prevBlock, difficulty, rng) {
    const compatible = LANDING_BLOCKS.filter(block => Math.abs(prevBlock.slope_out - block.slope_in) <= SLOPE_STEP);
    let pool = compatible.filter(block => matchesDifficulty(block, difficulty));
    if (!pool.length) {
      pool = compatible.length ? compatible : LANDING_BLOCKS;
    }
    if (!pool.length) {
      return null;
    }
    const index = Math.floor(rng() * pool.length) % pool.length;
    return pool[index];
  }

  function appendBlockPoints(target, block, offsetX, offsetY, skipFirst) {
    const { geo } = block;
    for (let i = 0; i < geo.length; i += 1) {
      if (skipFirst && i === 0) {
        continue;
      }
      const point = geo[i];
      target.push([offsetX + point[0], offsetY + point[1]]);
    }
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

  function createCheckpoints(track, interval) {
    const checkpoints = [];
    const totalLength = track.points[track.points.length - 1][0];
    const startX = Math.max(40, track.points[0][0] + 40);
    let currentX = startX;
    let hint = 0;
    checkpoints.push(createCheckpoint(track, track.points[0][0] + 10, 0));
    while (currentX < totalLength) {
      const checkpoint = createCheckpoint(track, currentX, hint);
      checkpoints.push(checkpoint);
      hint = checkpoint.segmentIndex;
      currentX += interval;
    }
    checkpoints.push(createCheckpoint(track, totalLength, hint));
    return checkpoints;
  }

  function buildTrack(seed, targetLength, difficulty) {
    const rng = mulberry32(seed || 1);
    const points = [];
    const usedIds = [];
    let currentX = 0;
    let currentY = 0;
    let prevBlock = START_BLOCK;
    appendBlockPoints(points, START_BLOCK, currentX, currentY, false);
    currentX += START_BLOCK.length;
    currentY += START_BLOCK.y1;
    usedIds.push(START_BLOCK.id);

    let safety = 0;
    const normalizedDifficulty = typeof difficulty === 'string' ? difficulty : DEFAULT_DIFFICULTY;
    while (currentX < targetLength && safety < 80) {
      safety += 1;
      const nextBlock = pickNextBlock(prevBlock, normalizedDifficulty, rng, currentY);
      if (!nextBlock) {
        break;
      }
      appendBlockPoints(points, nextBlock, currentX, currentY, true);
      currentX += nextBlock.length;
      currentY += nextBlock.y1;
      usedIds.push(nextBlock.id);
      prevBlock = nextBlock;
      if (currentY > ELEVATION_LIMIT) {
        currentY = ELEVATION_LIMIT;
      } else if (currentY < -ELEVATION_LIMIT) {
        currentY = -ELEVATION_LIMIT;
      }
    }

    const landing = pickLandingBlock(prevBlock, normalizedDifficulty, rng);
    if (landing) {
      appendBlockPoints(points, landing, currentX, currentY, true);
      currentX += landing.length;
      currentY += landing.y1;
      usedIds.push(landing.id);
    }

    if (points.length < 2) {
      return null;
    }

    const segments = createSegments(points);
    let maxY = -Infinity;
    for (let i = 0; i < points.length; i += 1) {
      if (points[i][1] > maxY) {
        maxY = points[i][1];
      }
    }
    const track = {
      points,
      segments,
      length: currentX,
      usedIds,
      maxY: maxY + FALL_EXTRA_MARGIN
    };
    const checkpointInterval = intervalClamp(CHECKPOINT_INTERVAL);
    track.checkpoints = createCheckpoints(track, checkpointInterval);
    return track;
  }

  function intervalClamp(value) {
    const numeric = Number.isFinite(value) ? value : CHECKPOINT_INTERVAL;
    return clamp(numeric, 200, 600);
  }

  const state = {
    initialized: false,
    active: false,
    elements: null,
    ctx: null,
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
    camera: { x: 0, y: 0 },
    input: { accelerate: 0, brake: 0, tiltForward: 0, tiltBackward: 0 },
    statusKey: 'index.sections.motocross.ui.status.ready',
    statusParams: null,
    statusState: 'idle',
    lastTimestamp: null,
    accumulator: 0,
    checkpoints: [],
    currentCheckpoint: 0,
    respawnData: null,
    fallThreshold: 600,
    pendingRespawn: false,
    speed: 0
  };

  function getElements() {
    if (!document) {
      return null;
    }
    return {
      canvas: document.getElementById('motocrossCanvas'),
      seedInput: document.getElementById('motocrossSeed'),
      lengthInput: document.getElementById('motocrossLength'),
      difficultySelect: document.getElementById('motocrossDifficulty'),
      generateButton: document.getElementById('motocrossGenerate'),
      speedValue: document.getElementById('motocrossSpeedValue'),
      blockList: document.getElementById('motocrossBlockList'),
      status: document.getElementById('motocrossStatus')
    };
  }

  function applyStatus() {
    const { elements, statusKey, statusParams, statusState } = state;
    if (!elements || !elements.status) {
      return;
    }
    const fallback = statusKey === 'index.sections.motocross.ui.status.ready'
      ? 'Appuyez sur « Générer » pour créer une piste.'
      : '';
    const text = translate(statusKey, fallback, statusParams);
    elements.status.textContent = text;
    elements.status.dataset.state = statusState || 'idle';
  }

  function setStatus(key, fallback, params, stateName = 'idle') {
    state.statusKey = key;
    state.statusParams = params || null;
    state.statusState = stateName;
    applyStatus();
  }

  function updateBlockList(usedIds) {
    const { elements } = state;
    if (!elements || !elements.blockList) {
      return;
    }
    const container = elements.blockList;
    container.textContent = '';
    if (!Array.isArray(usedIds) || !usedIds.length) {
      container.textContent = '—';
      return;
    }
    const fragment = document.createDocumentFragment();
    usedIds.forEach(id => {
      const chip = document.createElement('span');
      chip.className = 'motocross__chip';
      chip.textContent = id;
      fragment.appendChild(chip);
    });
    container.appendChild(fragment);
  }

  function updateSpeedDisplay() {
    const { elements, speed } = state;
    if (!elements || !elements.speedValue) {
      return;
    }
    elements.speedValue.textContent = formatSpeed(speed);
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

  function clampLengthValue(value) {
    const numeric = Number.isFinite(value) ? value : DEFAULT_LENGTH;
    return clamp(Math.round(numeric), MIN_LENGTH, MAX_LENGTH);
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
    bike.velocity.x = boost ? 80 : 0;
    bike.velocity.y = 0;
    bike.angularVelocity = 0;
    wheels.back.segmentIndex = data.segmentIndex;
    wheels.front.segmentIndex = data.segmentIndex;
    updateWheelData();
    state.camera.x = bike.position.x;
    state.camera.y = bike.position.y - CAMERA_OFFSET_Y;
    state.pendingRespawn = false;
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
    const toSurface = {
      x: wheel.pos.x - sample.point.x,
      y: wheel.pos.y - sample.point.y
    };
    const distance = toSurface.x * normal.x + toSurface.y * normal.y;
    const penetration = WHEEL_RADIUS - distance;
    wheel.onGround = penetration > 0;
    if (!wheel.onGround) {
      return {
        normalForce: 0,
        tangentForce: driveInput * ENGINE_FORCE,
        correction: { x: 0, y: 0 },
        normal,
        tangent
      };
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
    const tangentVelocity = pointVelocity.x * tangent.x + pointVelocity.y * tangent.y;
    const driveForce = driveInput * ENGINE_FORCE;
    const desiredTangentForce = -tangentVelocity * FRICTION_DAMPING + driveForce;
    const maxFriction = normalForce * FRICTION_COEFFICIENT;
    const tangentForce = clamp(desiredTangentForce, -maxFriction, maxFriction);
    const correctionScale = penetration * NORMAL_CORRECTION_FACTOR;
    const correction = {
      x: normal.x * correctionScale,
      y: normal.y * correctionScale
    };
    return {
      normalForce,
      tangentForce,
      correction,
      normal,
      tangent
    };
  }

  function stepPhysics(dt) {
    const track = state.track;
    if (!track) {
      return;
    }
    const bike = state.bike;
    updateWheelData();
    const { back, front } = state.wheels;
    const driveInput = state.input.accelerate - state.input.brake;
    const tiltInput = state.input.tiltForward - state.input.tiltBackward;

    const backContact = computeWheelContact(back, track, driveInput);
    const frontContact = computeWheelContact(front, track, driveInput * 0.6);

    let totalForceX = 0;
    let totalForceY = MASS * GRAVITY;
    let totalTorque = tiltInput * TILT_TORQUE;
    let correctionX = 0;
    let correctionY = 0;
    let correctionCount = 0;

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
    });

    const accelerationX = totalForceX / MASS;
    const accelerationY = totalForceY / MASS;
    bike.velocity.x += accelerationX * dt;
    bike.velocity.y += accelerationY * dt;
    bike.angularVelocity += (totalTorque / BIKE_INERTIA) * dt;

    bike.velocity.x *= 1 - LINEAR_DAMPING * dt;
    bike.velocity.y *= 1 - LINEAR_DAMPING * dt;
    bike.angularVelocity *= 1 - ANGULAR_DAMPING * dt;

    bike.position.x += bike.velocity.x * dt;
    bike.position.y += bike.velocity.y * dt;
    bike.angle += bike.angularVelocity * dt;

    if (correctionCount > 0) {
      bike.position.x += correctionX / correctionCount;
      bike.position.y += correctionY / correctionCount;
    }

    updateWheelData();
    const speedMagnitude = Math.hypot(bike.velocity.x, bike.velocity.y);
    state.speed = speedMagnitude;
    updateCheckpointsProgress();

    if (bike.position.y - WHEEL_RADIUS > state.fallThreshold) {
      state.pendingRespawn = true;
    }
  }

  function updateCamera(dt) {
    const { camera, bike } = state;
    const lookAhead = clamp(bike.velocity.x * CAMERA_LOOK_AHEAD, -160, 240);
    const targetX = bike.position.x + lookAhead;
    const targetY = bike.position.y - CAMERA_OFFSET_Y;
    const smooth = 1 - Math.exp(-CAMERA_SMOOTH * dt);
    camera.x = lerp(camera.x, targetX, smooth);
    camera.y = lerp(camera.y, targetY, smooth);
  }

  function renderTrack(ctx, track) {
    if (!track || !track.points.length) {
      return;
    }
    ctx.lineWidth = 4;
    ctx.strokeStyle = '#60a5fa';
    ctx.beginPath();
    const [firstX, firstY] = track.points[0];
    ctx.moveTo(firstX, firstY);
    for (let i = 1; i < track.points.length; i += 1) {
      const [x, y] = track.points[i];
      ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.lineWidth = 1;
    ctx.strokeStyle = 'rgba(94, 234, 212, 0.35)';
    track.checkpoints.forEach(checkpoint => {
      ctx.beginPath();
      ctx.moveTo(checkpoint.x, checkpoint.y - 8);
      ctx.lineTo(checkpoint.x, checkpoint.y + 32);
      ctx.stroke();
    });
  }

  function renderBike(ctx) {
    const { bike, wheels } = state;
    const back = wheels.back;
    const front = wheels.front;
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

    ctx.save();
    ctx.translate(-camera.x + width * 0.5, -camera.y + height * 0.65);
    if (track) {
      renderTrack(ctx, track);
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
      applyRespawn(true);
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
    updateSpeedDisplay();
  }

  function parseLengthInput() {
    const value = state.elements?.lengthInput?.value;
    const numeric = Number.parseFloat(value);
    return clampLengthValue(Number.isFinite(numeric) ? numeric : DEFAULT_LENGTH);
  }

  function parseDifficulty() {
    const value = state.elements?.difficultySelect?.value;
    if (value === 'easy' || value === 'hard') {
      return value;
    }
    return DEFAULT_DIFFICULTY;
  }

  function parseSeed() {
    const value = state.elements?.seedInput?.value;
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
    return DEFAULT_SEED;
  }

  function generateTrack(options = {}) {
    const silent = !!(options && options.silent);
    const seedText = parseSeed();
    const seedValue = hashSeed(seedText);
    const lengthValue = parseLengthInput();
    const difficulty = parseDifficulty();
    const track = buildTrack(seedValue, lengthValue, difficulty);
    if (!track) {
      updateBlockList([]);
      setStatus(
        'index.sections.motocross.ui.status.failed',
        'Impossible de générer la piste avec ces paramètres.',
        null,
        'error'
      );
      return;
    }
    state.track = track;
    state.checkpoints = track.checkpoints || [];
    state.currentCheckpoint = 0;
    state.respawnData = computeRespawnData(0);
    state.fallThreshold = track.maxY;
    updateRespawnCheckpoint();
    applyRespawn(false);
    if (!silent) {
      console.info('[Motocross] Track blocks:', track.usedIds);
    }
    updateBlockList(track.usedIds);
    if (!silent) {
      const params = { count: track.usedIds.length };
      setStatus(
        'index.sections.motocross.ui.status.generated',
        'Piste générée : {count} blocs.',
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

  function handleResize() {
    resizeCanvas();
    renderScene();
  }

  function handleKeyDown(event) {
    if (!event) {
      return;
    }
    const code = event.code;
    switch (code) {
      case 'ArrowRight':
        state.input.accelerate = 1;
        event.preventDefault();
        break;
      case 'ArrowLeft':
        state.input.brake = 1;
        event.preventDefault();
        break;
      case 'KeyA':
        state.input.tiltBackward = 1;
        break;
      case 'KeyD':
        state.input.tiltForward = 1;
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
        state.input.accelerate = 0;
        event.preventDefault();
        break;
      case 'ArrowLeft':
        state.input.brake = 0;
        event.preventDefault();
        break;
      case 'KeyA':
        state.input.tiltBackward = 0;
        break;
      case 'KeyD':
        state.input.tiltForward = 0;
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
    window.addEventListener('resize', handleResize);
    window.addEventListener('keydown', handleKeyDown, { passive: false });
    window.addEventListener('keyup', handleKeyUp);
    window.addEventListener('i18n:languagechange', applyStatus);
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
    attachListeners();
    resizeCanvas();
    applyStatus();
    generateTrack({ silent: true });
    window.requestAnimationFrame(tick);
  }

  const api = {
    onEnter() {
      initialize();
      state.active = true;
      state.lastTimestamp = null;
      applyStatus();
    },
    onLeave() {
      state.active = false;
      state.input.accelerate = 0;
      state.input.brake = 0;
      state.input.tiltForward = 0;
      state.input.tiltBackward = 0;
    }
  };

  window.motocrossArcade = api;
})();
