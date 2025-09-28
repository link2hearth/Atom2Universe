(() => {
  const COLOR_DEFS = {
    blue: {
      bar: '#56a6ff',
      barEdge: '#9ed2ff',
      haloInner: 'rgba(86, 166, 255, 0.88)',
      haloMid: 'rgba(86, 166, 255, 0.4)',
      haloOuter: 'rgba(86, 166, 255, 0.08)'
    },
    red: {
      bar: '#ff6b8d',
      barEdge: '#ffc2d1',
      haloInner: 'rgba(255, 107, 141, 0.9)',
      haloMid: 'rgba(255, 107, 141, 0.42)',
      haloOuter: 'rgba(255, 107, 141, 0.08)'
    }
  };

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

  class PhotonGame {
    constructor({ canvas, onScoreChange, onColorChange, onGameOver } = {}) {
      this.canvas = canvas || null;
      this.context = this.canvas ? this.canvas.getContext('2d') : null;
      this.onScoreChange = typeof onScoreChange === 'function' ? onScoreChange : () => {};
      this.onColorChange = typeof onColorChange === 'function' ? onColorChange : () => {};
      this.onGameOver = typeof onGameOver === 'function' ? onGameOver : () => {};

      this.viewportWidth = 0;
      this.viewportHeight = 0;
      this.currentColor = 'blue';
      this.score = 0;
      this.bars = [];
      this.desiredBarCount = 3;
      this.spawnGap = 90;
      this.state = 'idle';
      this.lastTimestamp = 0;
      this.animationFrame = null;

      this._tick = this._tick.bind(this);

      if (this.context && this.canvas) {
        this.resize();
        this.render();
      }
    }

    getBarWidth() {
      const width = this.viewportWidth || 0;
      if (!width) return 0;
      return clamp(width * 0.78, 200, 1100);
    }

    getBarHeight() {
      const height = this.viewportHeight || 0;
      if (!height) return 0;
      return clamp(height * 0.08, 42, 80);
    }

    getBarSpeed() {
      const height = this.viewportHeight || 0;
      if (!height) return 180;
      return clamp(height * 0.78, 180, 320);
    }

    getHaloHeight() {
      const height = this.viewportHeight || 0;
      if (!height) return 140;
      return clamp(height * 0.12, 60, 100);
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
      const color = Math.random() < 0.5 ? 'blue' : 'red';
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
      this.currentColor = 'blue';
      this.bars = [];
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
      this.currentColor = 'blue';
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
      this.currentColor = this.currentColor === 'blue' ? 'red' : 'blue';
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

      const barWidth = this.getBarWidth();
      const barX = (width - barWidth) / 2;
      for (const bar of this.bars) {
        const colors = COLOR_DEFS[bar.color] || COLOR_DEFS.blue;
        const barGradient = ctx.createLinearGradient(barX, bar.y, barX + barWidth, bar.y + bar.height);
        barGradient.addColorStop(0, colors.barEdge);
        barGradient.addColorStop(0.2, colors.bar);
        barGradient.addColorStop(0.8, colors.bar);
        barGradient.addColorStop(1, colors.barEdge);
        drawRoundedRect(ctx, barX, bar.y, barWidth, bar.height, Math.min(28, barWidth / 4));
        ctx.fillStyle = barGradient;
        ctx.fill();
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      const haloHeight = this.getHaloHeight();
      const haloTop = height - haloHeight;
      const haloColors = COLOR_DEFS[this.currentColor] || COLOR_DEFS.blue;
      const haloGradient = ctx.createLinearGradient(0, haloTop, 0, height);
      haloGradient.addColorStop(0, haloColors.haloOuter);
      haloGradient.addColorStop(0.45, haloColors.haloMid);
      haloGradient.addColorStop(1, haloColors.haloInner);
      ctx.fillStyle = haloGradient;
      ctx.fillRect(0, haloTop, width, haloHeight);

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
      ctx.fillRect(barX * 0.2, haloTop - 6, width - barX * 0.4, 12);

      ctx.restore();
    }
  }

  window.PhotonGame = PhotonGame;
})();
