(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const TABLE_RATIO = 2;
  const TABLE_PADDING_RATIO = 0.06;
  const TABLE_PADDING_MIN = 28;
  const TABLE_PADDING_MAX = 70;
  const BALL_RADIUS_RATIO = 0.024;
  const BALL_RADIUS_MIN = 10;
  const BALL_RADIUS_MAX = 18;
  const MAX_DRAG_DISTANCE = 180;
  const MAX_LAUNCH_SPEED = 980; // pixels per second
  const FRICTION_PER_FRAME = 0.985;
  const RESTITUTION = 0.92;
  const STOP_SPEED = 14; // pixels per second
  const TRAIL_OPACITY = 0.2;
  const POINTER_LINE_COLOR = 'rgba(240, 255, 245, 0.6)';

  function onReady(callback) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', callback, { once: true });
    } else {
      callback();
    }
  }

  function translate(key, fallback, params) {
    if (typeof key !== 'string' || !key.trim()) {
      return typeof fallback === 'string' ? fallback : '';
    }
    const translator =
      (typeof window !== 'undefined' && window.i18n && typeof window.i18n.t === 'function'
        ? window.i18n.t
        : null)
      || (typeof window !== 'undefined' && typeof window.t === 'function' ? window.t : null);
    if (translator) {
      try {
        const result = translator(key, params);
        if (typeof result === 'string' && result.trim()) {
          return result;
        }
      } catch (error) {
        console.warn('Billard translation error for', key, error);
      }
    }
    if (typeof fallback === 'string') {
      return fallback;
    }
    return key;
  }

  onReady(() => {
    const section = document.getElementById('billard');
    const layout = document.getElementById('billardLayout');
    const tableContainer = document.getElementById('billardTableContainer');
    const canvas = document.getElementById('billardCanvas');
    const powerFill = document.getElementById('billardPowerFill');
    const powerValue = document.getElementById('billardPowerValue');
    const resetButton = document.getElementById('billardResetButton');
    const arrangeButton = document.getElementById('billardArrangeToggle');
    const modeHint = document.getElementById('billardModeHint');

    if (!section || !tableContainer || !canvas || !powerFill || !resetButton || !arrangeButton) {
      return;
    }

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    const state = {
      orientation: 'landscape',
      width: 0,
      height: 0,
      bounds: {
        left: 0,
        right: 0,
        top: 0,
        bottom: 0,
        padding: 0
      },
      ballRadius: 12,
      balls: [],
      lastTimestamp: null,
      arrangeMode: false,
      powerLevel: 0,
      dragging: {
        active: false,
        id: null,
        mode: null,
        ball: null,
        startX: 0,
        startY: 0,
        currentX: 0,
        currentY: 0
      },
      initialized: false
    };

    const balls = [
      createBall({
        id: 'white',
        fill: '#f8fbff',
        highlight: '#ffffff'
      }),
      createBall({
        id: 'yellow',
        fill: '#ffe680',
        highlight: '#fff6c4'
      }),
      createBall({
        id: 'red',
        fill: '#ff6868',
        highlight: '#ffc0c0'
      })
    ];

    state.balls = balls;

    if (typeof ResizeObserver === 'function') {
      const resizeObserver = new ResizeObserver(() => {
        updateCanvasSize();
      });
      resizeObserver.observe(tableContainer);
    }

    window.addEventListener('resize', () => {
      updateCanvasSize();
    });

    if (typeof window !== 'undefined') {
      window.addEventListener('orientationchange', () => {
        updateCanvasSize();
      });
    }

    canvas.addEventListener('pointerdown', handlePointerDown);
    canvas.addEventListener('pointermove', handlePointerMove);
    canvas.addEventListener('pointerup', handlePointerUp);
    canvas.addEventListener('pointercancel', handlePointerCancel);
    canvas.addEventListener('pointerleave', handlePointerCancel);

    resetButton.addEventListener('click', () => {
      resetBalls(true);
      updatePowerBar(0);
    });

    arrangeButton.addEventListener('click', () => {
      setArrangeMode(!state.arrangeMode);
    });

    updateModeHint();
    updatePowerBar(0);
    updateCanvasSize(true);
    requestAnimationFrame(step);

    function createBall({ id, fill, highlight }) {
      return {
        id,
        fill,
        highlight,
        radius: state.ballRadius,
        x: 0,
        y: 0,
        vx: 0,
        vy: 0
      };
    }

    function updateCanvasSize(forceReset = false) {
      const rect = tableContainer.getBoundingClientRect();
      if (!rect.width || !rect.height) {
        return;
      }

      const orientation = rect.width >= rect.height ? 'landscape' : 'portrait';
      state.orientation = orientation;
      section.setAttribute('data-orientation', orientation);
      if (layout) {
        layout.setAttribute('data-orientation', orientation);
      }

      let targetWidth = rect.width;
      let targetHeight = rect.height;

      if (orientation === 'landscape') {
        const heightFromWidth = targetWidth / TABLE_RATIO;
        if (heightFromWidth > targetHeight) {
          targetWidth = targetHeight * TABLE_RATIO;
        } else {
          targetHeight = heightFromWidth;
        }
      } else {
        const widthFromHeight = targetHeight / TABLE_RATIO;
        if (widthFromHeight > targetWidth) {
          targetHeight = targetWidth * TABLE_RATIO;
        } else {
          targetWidth = widthFromHeight;
        }
      }

      const width = Math.max(260, Math.floor(targetWidth));
      const height = Math.max(260, Math.floor(targetHeight));

      const previousWidth = state.width || width;
      const previousHeight = state.height || height;

      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
        state.width = width;
        state.height = height;

        const padding = clamp(
          Math.round(width * TABLE_PADDING_RATIO),
          TABLE_PADDING_MIN,
          TABLE_PADDING_MAX
        );
        const radius = clamp(
          Math.round(width * BALL_RADIUS_RATIO),
          BALL_RADIUS_MIN,
          BALL_RADIUS_MAX
        );

        state.ballRadius = radius;
        state.bounds = {
          left: padding + radius,
          right: width - padding - radius,
          top: padding + radius,
          bottom: height - padding - radius,
          padding
        };

        const scaleX = width / previousWidth;
        const scaleY = height / previousHeight;

        state.balls.forEach(ball => {
          ball.radius = radius;
          if (state.initialized && !forceReset) {
            ball.x *= scaleX;
            ball.y *= scaleY;
            ball.vx *= scaleX;
            ball.vy *= scaleY;
            clampBallPosition(ball);
          }
        });

        if (!state.initialized || forceReset) {
          resetBalls(true);
          state.initialized = true;
        }
      }
    }

    function resetBalls(randomizeOrder) {
      const { left, right, top, bottom } = state.bounds;
      const centerX = (left + right) / 2;
      const topY = top + (bottom - top) * 0.25;
      const bottomY = bottom - (bottom - top) * 0.2;
      const offsetX = (right - left) * 0.18;

      const order = randomizeOrder && Math.random() < 0.5 ? ['white', 'yellow'] : ['yellow', 'white'];
      const positions = {
        red: { x: centerX, y: topY },
        [order[0]]: { x: centerX - offsetX, y: bottomY },
        [order[1]]: { x: centerX + offsetX, y: bottomY }
      };

      state.balls.forEach(ball => {
        const position = positions[ball.id];
        if (position) {
          ball.x = position.x;
          ball.y = position.y;
        } else {
          ball.x = centerX;
          ball.y = (top + bottom) / 2;
        }
        ball.vx = 0;
        ball.vy = 0;
        clampBallPosition(ball);
      });
    }

    function clamp(value, min, max) {
      if (!Number.isFinite(value)) {
        return min;
      }
      if (value < min) {
        return min;
      }
      if (value > max) {
        return max;
      }
      return value;
    }

    function clampBallPosition(ball) {
      const { left, right, top, bottom } = state.bounds;
      ball.x = clamp(ball.x, left, right);
      ball.y = clamp(ball.y, top, bottom);
    }

    function setArrangeMode(enabled) {
      const isEnabled = Boolean(enabled);
      if (state.arrangeMode === isEnabled) {
        return;
      }
      state.arrangeMode = isEnabled;
      arrangeButton.setAttribute('aria-pressed', isEnabled ? 'true' : 'false');
      arrangeButton.classList.toggle('billard-button--active', isEnabled);
      if (isEnabled) {
        state.balls.forEach(ball => {
          ball.vx = 0;
          ball.vy = 0;
        });
      }
      updateModeHint();
    }

    function updateModeHint() {
      if (!modeHint) {
        return;
      }
      const key = state.arrangeMode
        ? 'index.sections.billard.arrangeHint'
        : 'index.sections.billard.playHint';
      const fallback = state.arrangeMode
        ? 'Le mode arranger permet de déplacer librement les billes sur la table.'
        : 'Touchez une bille, tirez à l’opposé de la direction voulue, puis relâchez pour jouer.';
      modeHint.textContent = translate(key, fallback);
    }

    function updatePowerBar(level) {
      const safeLevel = clamp(Number.isFinite(level) ? level : 0, 0, 1);
      state.powerLevel = safeLevel;
      const percentage = Math.round(safeLevel * 100);
      powerFill.style.height = `${percentage}%`;
      powerFill.dataset.power = String(percentage);
      if (powerValue) {
        const text = translate('index.sections.billard.power.valueDynamic', '{{value}} %', {
          value: percentage
        });
        powerValue.textContent = typeof text === 'string' ? text : `${percentage} %`;
      }
    }

    function getCanvasCoordinates(event) {
      const rect = canvas.getBoundingClientRect();
      const x = ((event.clientX - rect.left) / rect.width) * canvas.width;
      const y = ((event.clientY - rect.top) / rect.height) * canvas.height;
      return { x, y };
    }

    function findBallAt(x, y) {
      for (let index = state.balls.length - 1; index >= 0; index -= 1) {
        const ball = state.balls[index];
        const distance = Math.hypot(ball.x - x, ball.y - y);
        if (distance <= ball.radius * 1.1) {
          return ball;
        }
      }
      return null;
    }

    function areBallsMoving() {
      return state.balls.some(ball => Math.hypot(ball.vx, ball.vy) > STOP_SPEED);
    }

    function handlePointerDown(event) {
      if (state.dragging.active) {
        return;
      }
      const { x, y } = getCanvasCoordinates(event);
      const targetBall = findBallAt(x, y);
      if (!targetBall) {
        return;
      }
      if (!state.arrangeMode && areBallsMoving()) {
        return;
      }
      event.preventDefault();
      state.dragging.active = true;
      state.dragging.id = event.pointerId;
      state.dragging.ball = targetBall;
      state.dragging.startX = x;
      state.dragging.startY = y;
      state.dragging.currentX = x;
      state.dragging.currentY = y;
      state.dragging.mode = state.arrangeMode ? 'arrange' : 'aim';
      if (state.dragging.mode === 'arrange') {
        targetBall.vx = 0;
        targetBall.vy = 0;
      }
      try {
        canvas.setPointerCapture(event.pointerId);
      } catch (error) {
        // Pointer capture may fail on some browsers; ignore.
      }
      updatePowerBar(0);
    }

    function handlePointerMove(event) {
      if (!state.dragging.active || state.dragging.id !== event.pointerId) {
        return;
      }
      const { x, y } = getCanvasCoordinates(event);
      state.dragging.currentX = x;
      state.dragging.currentY = y;

      if (state.dragging.mode === 'arrange') {
        const { ball } = state.dragging;
        if (ball) {
          ball.x = x;
          ball.y = y;
          clampBallPosition(ball);
          ball.vx = 0;
          ball.vy = 0;
        }
      } else if (state.dragging.mode === 'aim') {
        const dx = x - state.dragging.startX;
        const dy = y - state.dragging.startY;
        const distance = Math.hypot(dx, dy);
        const normalized = clamp(distance / MAX_DRAG_DISTANCE, 0, 1);
        updatePowerBar(normalized);
      }
    }

    function handlePointerUp(event) {
      if (!state.dragging.active || state.dragging.id !== event.pointerId) {
        return;
      }
      const { mode, ball, startX, startY, currentX, currentY } = state.dragging;
      if (mode === 'aim' && ball) {
        const dx = currentX - startX;
        const dy = currentY - startY;
        const distance = Math.hypot(dx, dy);
        const normalized = clamp(distance / MAX_DRAG_DISTANCE, 0, 1);
        if (distance > 2 && normalized > 0) {
          const speed = normalized * MAX_LAUNCH_SPEED;
          ball.vx = (-dx / distance) * speed;
          ball.vy = (-dy / distance) * speed;
        }
      } else if (mode === 'arrange' && ball) {
        clampBallPosition(ball);
        ball.vx = 0;
        ball.vy = 0;
      }
      updatePowerBar(0);
      clearPointerState();
      try {
        canvas.releasePointerCapture(event.pointerId);
      } catch (error) {
        // Ignore release failures
      }
    }

    function handlePointerCancel(event) {
      if (!state.dragging.active || state.dragging.id !== event.pointerId) {
        return;
      }
      if (state.dragging.ball) {
        clampBallPosition(state.dragging.ball);
      }
      updatePowerBar(0);
      clearPointerState();
    }

    function clearPointerState() {
      state.dragging.active = false;
      state.dragging.id = null;
      state.dragging.mode = null;
      state.dragging.ball = null;
    }

    function step(timestamp) {
      if (state.lastTimestamp == null) {
        state.lastTimestamp = timestamp;
      }
      const deltaMs = Math.min(64, timestamp - state.lastTimestamp);
      state.lastTimestamp = timestamp;
      const deltaSeconds = deltaMs / 1000;

      updatePhysics(deltaSeconds);
      render();

      requestAnimationFrame(step);
    }

    function updatePhysics(deltaSeconds) {
      if (!Number.isFinite(deltaSeconds) || deltaSeconds <= 0) {
        return;
      }

      const friction = Math.pow(FRICTION_PER_FRAME, deltaSeconds * 60);
      const { left, right, top, bottom } = state.bounds;

      state.balls.forEach(ball => {
        ball.x += ball.vx * deltaSeconds;
        ball.y += ball.vy * deltaSeconds;

        if (ball.x < left) {
          ball.x = left;
          ball.vx = Math.abs(ball.vx) * RESTITUTION;
        } else if (ball.x > right) {
          ball.x = right;
          ball.vx = -Math.abs(ball.vx) * RESTITUTION;
        }

        if (ball.y < top) {
          ball.y = top;
          ball.vy = Math.abs(ball.vy) * RESTITUTION;
        } else if (ball.y > bottom) {
          ball.y = bottom;
          ball.vy = -Math.abs(ball.vy) * RESTITUTION;
        }

        ball.vx *= friction;
        ball.vy *= friction;

        if (Math.hypot(ball.vx, ball.vy) < STOP_SPEED) {
          ball.vx = 0;
          ball.vy = 0;
        }
      });

      for (let i = 0; i < state.balls.length; i += 1) {
        for (let j = i + 1; j < state.balls.length; j += 1) {
          resolveBallCollision(state.balls[i], state.balls[j]);
        }
      }
    }

    function resolveBallCollision(a, b) {
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const distance = Math.hypot(dx, dy);
      const minDistance = a.radius + b.radius;

      if (!distance || distance >= minDistance) {
        return;
      }

      const overlap = minDistance - distance;
      const nx = dx / distance;
      const ny = dy / distance;
      const separation = overlap / 2;

      a.x -= nx * separation;
      a.y -= ny * separation;
      b.x += nx * separation;
      b.y += ny * separation;

      const relativeVx = b.vx - a.vx;
      const relativeVy = b.vy - a.vy;
      const velocityAlongNormal = relativeVx * nx + relativeVy * ny;

      if (velocityAlongNormal > 0) {
        return;
      }

      const impulse = -(1 + RESTITUTION) * velocityAlongNormal;
      const impulseX = impulse * nx;
      const impulseY = impulse * ny;

      a.vx -= impulseX / 2;
      a.vy -= impulseY / 2;
      b.vx += impulseX / 2;
      b.vy += impulseY / 2;
    }

    function render() {
      context.clearRect(0, 0, canvas.width, canvas.height);
      drawSpots();
      drawBalls();
      drawPointer();
    }

    function drawSpots() {
      context.save();
      context.globalAlpha = 0.25;
      context.fillStyle = '#ffffff';
      const { left, right, top, bottom } = state.bounds;
      const centerX = (left + right) / 2;
      const centerY = (top + bottom) / 2;
      const markerRadius = Math.max(2, Math.floor(state.ballRadius * 0.4));

      drawSpot(centerX, top + (bottom - top) * 0.25, markerRadius);
      drawSpot(centerX - (right - left) * 0.18, bottom - (bottom - top) * 0.2, markerRadius);
      drawSpot(centerX + (right - left) * 0.18, bottom - (bottom - top) * 0.2, markerRadius);
      drawSpot(centerX, centerY, Math.max(1, markerRadius - 1));
      context.restore();
    }

    function drawSpot(x, y, radius) {
      context.beginPath();
      context.arc(x, y, radius, 0, Math.PI * 2);
      context.fill();
    }

    function drawBalls() {
      state.balls.forEach(ball => {
        const gradient = context.createRadialGradient(
          ball.x - ball.radius * 0.4,
          ball.y - ball.radius * 0.4,
          ball.radius * 0.2,
          ball.x,
          ball.y,
          ball.radius
        );
        gradient.addColorStop(0, '#ffffff');
        gradient.addColorStop(0.3, ball.highlight);
        gradient.addColorStop(1, ball.fill);

        context.beginPath();
        context.fillStyle = gradient;
        context.arc(ball.x, ball.y, ball.radius, 0, Math.PI * 2);
        context.fill();

        context.lineWidth = 1.5;
        context.strokeStyle = 'rgba(0, 0, 0, 0.4)';
        context.stroke();

        if (state.arrangeMode) {
          context.save();
          context.strokeStyle = 'rgba(255, 255, 255, 0.45)';
          context.lineWidth = 1;
          context.setLineDash([6, 6]);
          context.beginPath();
          context.arc(ball.x, ball.y, ball.radius + 4, 0, Math.PI * 2);
          context.stroke();
          context.restore();
        }
      });
    }

    function drawPointer() {
      if (!state.dragging.active || state.dragging.mode !== 'aim' || !state.dragging.ball) {
        return;
      }
      const { ball, currentX, currentY } = state.dragging;
      context.save();
      context.lineWidth = 2;
      context.strokeStyle = POINTER_LINE_COLOR;
      context.beginPath();
      context.moveTo(ball.x, ball.y);
      context.lineTo(currentX, currentY);
      context.stroke();

      const cueLength = state.powerLevel * 80;
      const dx = ball.x - currentX;
      const dy = ball.y - currentY;
      const distance = Math.hypot(dx, dy) || 1;
      const ux = dx / distance;
      const uy = dy / distance;
      const cueX = ball.x + ux * cueLength;
      const cueY = ball.y + uy * cueLength;

      context.globalAlpha = TRAIL_OPACITY + state.powerLevel * 0.3;
      context.beginPath();
      context.moveTo(ball.x, ball.y);
      context.lineTo(cueX, cueY);
      context.stroke();
      context.restore();
    }
  });
})();
