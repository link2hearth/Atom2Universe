(function antiCheatModule(global) {
  // Valeurs de configuration issues du fichier config/config.js.
  const SETTINGS = global.ANTI_CHEAT_SETTINGS || {
    windowMs: 1000,
    minClicks: 20,
    maxPosDelta: 10
  };

  // Historique glissant des clics { t: timestamp, x, y }.
  const recentClicks = [];
  let blocked = false;
  const overlayId = 'antiCheatOverlay';

  function pruneOldClicks(now) {
    const cutoff = now - SETTINGS.windowMs;
    while (recentClicks.length && recentClicks[0].t < cutoff) {
      recentClicks.shift();
    }
  }

  function clicksAreClustered() {
    if (!recentClicks.length) {
      return false;
    }
    const reference = recentClicks[0];
    const limitSquared = SETTINGS.maxPosDelta * SETTINGS.maxPosDelta;
    return recentClicks.every((click) => {
      const dx = click.x - reference.x;
      const dy = click.y - reference.y;
      return dx * dx + dy * dy <= limitSquared;
    });
  }

  function isSuspiciousPattern(now) {
    pruneOldClicks(now);
    if (recentClicks.length <= SETTINGS.minClicks) {
      return false;
    }
    return clicksAreClustered();
  }

  function notifyAndroid() {
    try {
      if (global.AndroidAntiCheat && typeof global.AndroidAntiCheat.onCheatDetected === 'function') {
        global.AndroidAntiCheat.onCheatDetected();
      }
    } catch (error) {
      // Ignorer les erreurs de passerelle native.
    }
  }

  function buildOverlay() {
    let overlay = document.getElementById(overlayId);
    if (overlay) {
      return overlay;
    }

    overlay = document.createElement('div');
    overlay.id = overlayId;
    overlay.className = 'anti-cheat-overlay';
    overlay.setAttribute('role', 'alertdialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-hidden', 'true');
    overlay.hidden = true;

    const content = document.createElement('div');
    content.className = 'anti-cheat-overlay__content';

    const title = document.createElement('h2');
    title.className = 'anti-cheat-overlay__title';
    title.setAttribute('data-i18n', 'scripts.antiCheat.overlay.title');
    title.textContent = 'Comportement suspect détecté';

    const message = document.createElement('p');
    message.className = 'anti-cheat-overlay__message';
    message.setAttribute('data-i18n', 'scripts.antiCheat.overlay.message');
    message.textContent = 'Le jeu est bloqué en raison d\'un schéma de clics anormal.';

    const action = document.createElement('button');
    action.type = 'button';
    action.className = 'anti-cheat-overlay__action';
    action.setAttribute('data-i18n', 'scripts.antiCheat.overlay.action');
    action.textContent = 'Recharger le jeu';
    action.addEventListener('click', () => {
      window.location.reload();
    });

    content.appendChild(title);
    content.appendChild(message);
    content.appendChild(action);
    overlay.appendChild(content);
    document.body.appendChild(overlay);
    if (global.i18n && typeof global.i18n.updateTranslations === 'function') {
      global.i18n.updateTranslations(overlay);
    }
    return overlay;
  }

  function showOverlay() {
    const overlay = buildOverlay();
    overlay.hidden = false;
    overlay.setAttribute('aria-hidden', 'false');
  }

  function blockGame(event) {
    blocked = true;
    global.ANTI_CHEAT_BLOCKED = true;
    showOverlay();
    notifyAndroid();

    if (event) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }

  function handleClick(event) {
    if (blocked) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }

    const now = Date.now();
    recentClicks.push({
      t: now,
      x: event.clientX,
      y: event.clientY
    });

    if (isSuspiciousPattern(now)) {
      blockGame(event);
    }
  }

  document.addEventListener('click', handleClick, true);

  global.atom2universAntiCheat = {
    isBlocked: () => blocked,
    settings: { ...SETTINGS }
  };
})(typeof window !== 'undefined' ? window : this);
