const ATOM_IMAGE_FALLBACK = 'Assets/Image/Atom.png';
const GLOBAL_CONFIG = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;

const CRIT_ATOM_IMAGES = (() => {
  const source = Array.isArray(APP_DATA.CRIT_ATOM_IMAGES) && APP_DATA.CRIT_ATOM_IMAGES.length
    ? APP_DATA.CRIT_ATOM_IMAGES
    : Array.isArray(APP_DATA.DEFAULT_CRIT_ATOM_IMAGES) && APP_DATA.DEFAULT_CRIT_ATOM_IMAGES.length
      ? APP_DATA.DEFAULT_CRIT_ATOM_IMAGES
      : [];
  if (!source.length) {
    return [ATOM_IMAGE_FALLBACK];
  }
  return source.map(value => String(value));
})();

const MAIN_ATOM_IMAGES = (() => {
  const source = Array.isArray(APP_DATA.MAIN_ATOM_IMAGES) && APP_DATA.MAIN_ATOM_IMAGES.length
    ? APP_DATA.MAIN_ATOM_IMAGES
    : Array.isArray(APP_DATA.DEFAULT_MAIN_ATOM_IMAGES) && APP_DATA.DEFAULT_MAIN_ATOM_IMAGES.length
      ? APP_DATA.DEFAULT_MAIN_ATOM_IMAGES
      : [];
  if (!source.length) {
    return [ATOM_IMAGE_FALLBACK];
  }
  return source.map(value => String(value));
})();

const ALTERNATE_MAIN_ATOM_IMAGES = (() => {
  const source = Array.isArray(APP_DATA.ALTERNATE_MAIN_ATOM_IMAGES)
    && APP_DATA.ALTERNATE_MAIN_ATOM_IMAGES.length
      ? APP_DATA.ALTERNATE_MAIN_ATOM_IMAGES
      : Array.isArray(APP_DATA.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES)
        && APP_DATA.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES.length
        ? APP_DATA.DEFAULT_ALTERNATE_MAIN_ATOM_IMAGES
        : [];
  if (!source.length) {
    return [];
  }
  return source.map(value => String(value));
})();

function buildAtomButtonImagePool(sourceImages) {
  const unique = new Set();
  if (Array.isArray(sourceImages)) {
    sourceImages.forEach(value => {
      if (typeof value === 'string') {
        const normalized = value.trim();
        if (normalized) {
          unique.add(normalized);
        }
      }
    });
  }
  if (!unique.size) {
    unique.add(ATOM_IMAGE_FALLBACK);
  } else if (!unique.has(ATOM_IMAGE_FALLBACK)) {
    unique.add(ATOM_IMAGE_FALLBACK);
  }
  return Array.from(unique);
}

const DEFAULT_ATOM_BUTTON_IMAGES = buildAtomButtonImagePool(MAIN_ATOM_IMAGES);
const ALTERNATE_ATOM_BUTTON_IMAGES = buildAtomButtonImagePool(ALTERNATE_MAIN_ATOM_IMAGES);

function getAtomButtonImagePool() {
  const pool = isAtomImageVariantEnabled()
    ? ALTERNATE_ATOM_BUTTON_IMAGES
    : DEFAULT_ATOM_BUTTON_IMAGES;
  return Array.isArray(pool) && pool.length ? pool : [ATOM_IMAGE_FALLBACK];
}

function applyAtomVariantVisualState() {
  if (typeof document === 'undefined' || !document.body) {
    return;
  }
  document.body.dataset.atomVariant = isAtomImageVariantEnabled() ? 'alternate' : 'default';
}

const CRIT_ATOM_LIFETIME_MS = 6000;
const CRIT_ATOM_FADE_MS = 600;
const CRIT_ATOM_ANIMATION_SETTINGS = (() => {
  const config = GLOBAL_CONFIG?.presentation?.critAtoms;
  const rawMaxActive = Number(config?.maxActive);
  const maxActive = Number.isFinite(rawMaxActive)
    ? Math.max(0, Math.floor(rawMaxActive))
    : 24;
  const rawMaxFps = Number(config?.maxFps);
  const maxFps = Number.isFinite(rawMaxFps) && rawMaxFps > 0
    ? Math.min(rawMaxFps, 120)
    : 30;
  return {
    maxActive,
    maxFps,
    minFrameMs: maxFps > 0 ? 1000 / maxFps : 0
  };
})();
const activeCritAtoms = [];

function removeActiveCritAtom(entry) {
  const index = activeCritAtoms.indexOf(entry);
  if (index >= 0) {
    activeCritAtoms.splice(index, 1);
  }
}

function ensureCritAtomLayer() {
  if (elements.critAtomLayer && elements.critAtomLayer.isConnected) {
    return elements.critAtomLayer;
  }
  const layer = document.createElement('div');
  layer.className = 'crit-atom-layer';
  layer.setAttribute('aria-hidden', 'true');
  document.body.appendChild(layer);
  elements.critAtomLayer = layer;
  return layer;
}

function pickRandom(array) {
  if (!Array.isArray(array) || array.length === 0) {
    return undefined;
  }
  return array[Math.floor(Math.random() * array.length)];
}

function randomizeAtomButtonImage() {
  const image = getAtomImageElement();
  if (!image) {
    return;
  }
  const pool = getAtomButtonImagePool();
  const current = image.dataset.atomImage || image.getAttribute('src') || ATOM_IMAGE_FALLBACK;
  let next = pickRandom(pool) || ATOM_IMAGE_FALLBACK;
  if (pool.length > 1) {
    let attempts = 0;
    while (next === current && attempts < 4) {
      next = pickRandom(pool) || ATOM_IMAGE_FALLBACK;
      attempts += 1;
    }
  }
  if (image.getAttribute('src') !== next) {
    image.setAttribute('src', next);
  }
  image.dataset.atomImage = next;
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function spawnCriticalAtom(multiplier = 1) {
  if (critAtomVisualsDisabled) {
    return;
  }
  if (CRIT_ATOM_ANIMATION_SETTINGS.maxActive === 0) {
    return;
  }
  const layer = ensureCritAtomLayer();
  if (!layer) return;

  const safeMultiplier = Math.max(1, Number(multiplier) || 1);
  const fallbackImage = ATOM_IMAGE_FALLBACK;
  const imageSource = pickRandom(CRIT_ATOM_IMAGES) || fallbackImage;

  const atom = document.createElement('img');
  atom.src = imageSource;
  atom.alt = '';
  atom.className = 'crit-atom';
  atom.setAttribute('aria-hidden', 'true');

  const buttonSize = elements.atomButtonCore
    ? elements.atomButtonCore.offsetWidth
    : elements.atomButton
    ? elements.atomButton.offsetWidth
    : Math.min(window.innerWidth, window.innerHeight) * 0.2;
  const normalizedSize = clamp(buttonSize * (0.62 + Math.min(safeMultiplier, 6) * 0.04), 64, 160);
  atom.style.setProperty('--crit-atom-size', `${normalizedSize.toFixed(2)}px`);

  const baseScale = 0.78 + Math.random() * 0.18;
  const baseRotation = Math.random() * 360;
  atom.style.setProperty('--crit-atom-scale', baseScale.toFixed(3));
  atom.style.setProperty('--crit-atom-x', '0px');
  atom.style.setProperty('--crit-atom-y', '0px');
  atom.style.setProperty('--crit-atom-rotation', `${baseRotation.toFixed(2)}deg`);

  const maxActive = CRIT_ATOM_ANIMATION_SETTINGS.maxActive;
  if (Number.isFinite(maxActive) && maxActive > 0) {
    while (activeCritAtoms.length >= maxActive) {
      const oldest = activeCritAtoms.shift();
      if (oldest && typeof oldest.cleanup === 'function') {
        oldest.cleanup();
      }
    }
  }

  const entry = { cleanup: null };
  activeCritAtoms.push(entry);

  layer.appendChild(atom);

  const layerRect = layer.getBoundingClientRect();
  const viewportWidth = layerRect.width || window.innerWidth;
  const viewportHeight = layerRect.height || window.innerHeight;
  const atomRect = atom.getBoundingClientRect();
  const atomWidth = atomRect.width || normalizedSize;
  const atomHeight = atomRect.height || normalizedSize;

  const buttonRect = elements.atomButtonCore
    ? elements.atomButtonCore.getBoundingClientRect()
    : elements.atomButton
    ? elements.atomButton.getBoundingClientRect()
    : null;
  const startX = (buttonRect ? buttonRect.left + buttonRect.width / 2 : viewportWidth / 2)
    - layerRect.left - atomWidth / 2;
  const startY = (buttonRect ? buttonRect.top + buttonRect.height / 2 : viewportHeight / 2)
    - layerRect.top - atomHeight / 2;

  let x = clamp(startX, 0, Math.max(0, viewportWidth - atomWidth));
  let y = clamp(startY, 0, Math.max(0, viewportHeight - atomHeight));
  let rotation = baseRotation;

  atom.style.setProperty('--crit-atom-x', `${x.toFixed(2)}px`);
  atom.style.setProperty('--crit-atom-y', `${y.toFixed(2)}px`);
  atom.style.setProperty('--crit-atom-rotation', `${rotation.toFixed(2)}deg`);

  requestAnimationFrame(() => {
    atom.classList.add('is-active');
  });

  const maxX = Math.max(0, viewportWidth - atomWidth);
  const maxY = Math.max(0, viewportHeight - atomHeight);
  let vx = (Math.random() * 2 - 1) * (240 + Math.random() * 220);
  if (Math.abs(vx) < 160) {
    vx = 160 * (Math.random() < 0.5 ? -1 : 1);
  }
  let vy = -(360 + Math.random() * 240);
  const gravity = 900;
  const bounceLoss = 0.72 + Math.random() * 0.08;
  const wallLoss = 0.78 + Math.random() * 0.1;
  const floorFriction = 0.88;
  const rotationVelocity = (Math.random() * 160 + 80) * (Math.random() < 0.5 ? -1 : 1);

  const startTime = performance.now();
  let lastTime = startTime;
  let frameId = null;
  let active = true;

  function updateAtomPosition(now) {
    if (!active) {
      return;
    }
    if (!atom.isConnected) {
      cleanup();
      return;
    }
    const elapsed = now - startTime;
    const frameDeltaMs = now - lastTime;
    if (frameDeltaMs < CRIT_ATOM_ANIMATION_SETTINGS.minFrameMs) {
      frameId = requestAnimationFrame(updateAtomPosition);
      return;
    }
    const delta = Math.min(frameDeltaMs / 1000, 0.04);
    lastTime = now;

    vy += gravity * delta;
    x += vx * delta;
    y += vy * delta;
    rotation += rotationVelocity * delta;

    if (x <= 0) {
      x = 0;
      vx = Math.abs(vx) * wallLoss;
    } else if (x >= maxX) {
      x = maxX;
      vx = -Math.abs(vx) * wallLoss;
    }

    if (y <= 0) {
      y = 0;
      vy = Math.abs(vy) * wallLoss;
    } else if (y >= maxY) {
      y = maxY;
      if (Math.abs(vy) > 80) {
        vy = -Math.abs(vy) * bounceLoss;
      } else {
        vy = 0;
      }
      vx *= floorFriction;
    }

    atom.style.setProperty('--crit-atom-x', `${x.toFixed(2)}px`);
    atom.style.setProperty('--crit-atom-y', `${y.toFixed(2)}px`);
    atom.style.setProperty('--crit-atom-rotation', `${rotation.toFixed(2)}deg`);

    if (elapsed < CRIT_ATOM_LIFETIME_MS) {
      frameId = requestAnimationFrame(updateAtomPosition);
    }
  }

  frameId = requestAnimationFrame(now => {
    lastTime = now;
    updateAtomPosition(now);
  });

  const fadeTimeout = window.setTimeout(() => {
    atom.classList.add('is-fading');
    atom.style.setProperty('--crit-atom-scale', `${(baseScale * 0.6).toFixed(3)}`);
  }, Math.max(0, CRIT_ATOM_LIFETIME_MS - CRIT_ATOM_FADE_MS));

  const removalTimeout = window.setTimeout(() => {
    cleanup();
  }, CRIT_ATOM_LIFETIME_MS);

  function cleanup() {
    if (!active) {
      return;
    }
    active = false;
    window.clearTimeout(fadeTimeout);
    window.clearTimeout(removalTimeout);
    if (frameId) {
      cancelAnimationFrame(frameId);
    }
    removeActiveCritAtom(entry);
    if (atom.isConnected) {
      atom.remove();
    }
  }

  entry.cleanup = cleanup;

  atom.addEventListener('transitionend', event => {
    if (event.propertyName !== 'opacity') {
      return;
    }
    const elapsed = performance.now() - startTime;
    if (elapsed + 16 >= CRIT_ATOM_LIFETIME_MS) {
      cleanup();
    }
  });
}

const critBannerState = {
  fadeTimeoutId: null,
  hideTimeoutId: null
};

function clearCritBannerTimers() {
  if (critBannerState.fadeTimeoutId != null) {
    clearTimeout(critBannerState.fadeTimeoutId);
    critBannerState.fadeTimeoutId = null;
  }
  if (critBannerState.hideTimeoutId != null) {
    clearTimeout(critBannerState.hideTimeoutId);
    critBannerState.hideTimeoutId = null;
  }
}

function hideCritBanner(immediate = false) {
  const display = elements.statusCrit;
  const valueElement = elements.statusCritValue;
  if (!display || !valueElement) return;
  clearCritBannerTimers();
  if (immediate) {
    display.hidden = true;
    display.classList.remove('is-active', 'is-fading');
    valueElement.classList.remove('status-crit-value--smash');
    display.removeAttribute('aria-label');
    display.removeAttribute('title');
    return;
  }
  display.classList.add('is-fading');
  critBannerState.hideTimeoutId = setTimeout(() => {
    display.hidden = true;
    display.classList.remove('is-active', 'is-fading');
    valueElement.classList.remove('status-crit-value--smash');
    display.removeAttribute('aria-label');
    display.removeAttribute('title');
    critBannerState.hideTimeoutId = null;
  }, 360);
}

function formatCritLayeredNumber(value) {
  const layered = value instanceof LayeredNumber ? value : toLayeredValue(value, 0);
  if (!layered || layered.sign === 0) {
    return '0';
  }

  if (layered.layer === 0 && Math.abs(layered.exponent) < 6) {
    const numeric = layered.sign * layered.mantissa * Math.pow(10, layered.exponent);
    const formatted = LayeredNumber.formatLocalizedNumber(numeric, {
      maximumFractionDigits: 2,
      minimumFractionDigits: 0
    });
    if (formatted) {
      return formatted;
    }
    return `${numeric}`;
  }

  return layered.toString();
}

function showCritBanner(input) {
  const display = elements.statusCrit;
  const valueElement = elements.statusCritValue;
  if (!display || !valueElement) return;

  const options = input instanceof LayeredNumber || typeof input === 'number'
    ? { bonusAmount: input }
    : (input ?? {});

  const layeredBonus = options.bonusAmount != null
    ? toLayeredNumber(options.bonusAmount, 0)
    : LayeredNumber.zero();
  const layeredBase = options.baseAmount != null
    ? toLayeredNumber(options.baseAmount, 0)
    : null;

  let layeredTotal;
  if (options.totalAmount != null) {
    layeredTotal = toLayeredNumber(options.totalAmount, 0);
  } else if (layeredBase) {
    layeredTotal = layeredBase.add(layeredBonus);
  } else {
    layeredTotal = layeredBonus.clone();
  }

  if (!layeredTotal || layeredTotal.sign <= 0) {
    hideCritBanner(true);
    return;
  }

  const totalText = `+${formatCritLayeredNumber(layeredTotal)}`;

  const multiplierValue = Number(options.multiplier ?? options.multiplierValue);
  const hasMultiplier = Number.isFinite(multiplierValue) && multiplierValue > 1;
  const multiplierText = hasMultiplier
    ? `${formatNumberLocalized(multiplierValue, { maximumFractionDigits: 2 })}×`
    : '';

  valueElement.textContent = hasMultiplier
    ? `${multiplierText} = ${totalText}`
    : totalText;

  display.hidden = false;
  display.classList.remove('is-fading');
  display.classList.add('is-active');

  valueElement.classList.remove('status-crit-value--smash');
  void valueElement.offsetWidth; // force reflow to restart the impact animation
  valueElement.classList.add('status-crit-value--smash');

  const ariaText = hasMultiplier
    ? `Coup critique ${multiplierText} = ${totalText} atomes`
    : `Coup critique : ${totalText} atomes`;
  display.setAttribute('aria-label', ariaText);

  const details = [];
  if (layeredBase && layeredBase.sign > 0) {
    details.push(`base ${formatCritLayeredNumber(layeredBase)}`);
  }
  if (layeredBonus && layeredBonus.sign > 0 && (!layeredBase || layeredBonus.compare(layeredTotal) !== 0)) {
    details.push(`bonus +${formatCritLayeredNumber(layeredBonus)}`);
  }
  const detailText = details.length ? ` — ${details.join(' · ')}` : '';
  display.title = hasMultiplier
    ? `Bonus critique ${multiplierText} = ${totalText}${detailText}`
    : `Bonus critique ${totalText}${detailText}`;

  clearCritBannerTimers();
  critBannerState.fadeTimeoutId = setTimeout(() => {
    display.classList.add('is-fading');
    critBannerState.fadeTimeoutId = null;
    critBannerState.hideTimeoutId = setTimeout(() => {
      display.hidden = true;
      display.classList.remove('is-active', 'is-fading');
      valueElement.classList.remove('status-crit-value--smash');
      display.removeAttribute('aria-label');
      display.removeAttribute('title');
      critBannerState.hideTimeoutId = null;
    }, 360);
  }, 3000);
}

function showCriticalIndicator(multiplier) {
  spawnCriticalAtom(multiplier);
}

function applyCriticalHit(baseAmount) {
  const amount = baseAmount instanceof LayeredNumber
    ? baseAmount.clone()
    : new LayeredNumber(baseAmount ?? 0);
  const critState = cloneCritState(gameState.crit);
  const chance = Number(critState.chance) || 0;
  const effectiveMultiplier = Math.max(1, Math.min(critState.multiplier || 1, critState.maxMultiplier || critState.multiplier || 1));
  if (chance <= 0 || effectiveMultiplier <= 1) {
    return { amount, isCritical: false, multiplier: 1 };
  }
  if (Math.random() >= chance) {
    return { amount, isCritical: false, multiplier: 1 };
  }
  const critAmount = amount.multiplyNumber(effectiveMultiplier);
  return { amount: critAmount, isCritical: true, multiplier: effectiveMultiplier };
}

function handleManualAtomClick(options = {}) {
  const contextId = options?.contextId;
  const context = resolveManualClickContext(contextId);
  const baseAmount = gameState.perClick instanceof LayeredNumber
    ? gameState.perClick
    : toLayeredNumber(gameState.perClick ?? 0, 0);
  const critResult = applyCriticalHit(baseAmount);
  queueManualApcGain(critResult.amount);
  registerManualClick();
  registerApcFrenzyClick(performance.now(), context);
  soundEffects.pop.play();
  if (critResult.isCritical) {
    gameState.lastCritical = {
      at: Date.now(),
      multiplier: critResult.multiplier
    };
    const critBonus = critResult.amount.subtract(baseAmount);
    showCritBanner({
      bonusAmount: critBonus,
      totalAmount: critResult.amount,
      baseAmount,
      multiplier: critResult.multiplier
    });
  }
  animateAtomPress({
    critical: critResult.isCritical,
    multiplier: critResult.multiplier,
    context
  });
}

if (typeof globalThis !== 'undefined') {
  globalThis.handleManualAtomClick = handleManualAtomClick;
  globalThis.isManualClickContextActive = isManualClickContextActive;
}

function shouldTriggerGlobalClick(event) {
  if (!isGamePageActive()) return false;
  if (event.target.closest('.app-header')) return false;
  if (event.target.closest('.status-bar')) return false;
  return true;
}
