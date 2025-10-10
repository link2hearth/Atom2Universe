const noop = () => {};

function getGlobalFunction(name, fallback = noop) {
  if (typeof globalThis !== 'undefined' && typeof globalThis[name] === 'function') {
    return globalThis[name].bind(globalThis);
  }
  return fallback;
}

function resolveMetauxGame() {
  if (typeof globalThis === 'undefined') {
    return null;
  }
  if (typeof globalThis.getMetauxGame === 'function') {
    try {
      return globalThis.getMetauxGame();
    } catch (error) {
      return null;
    }
  }
  if (globalThis.metauxGame && typeof globalThis.metauxGame === 'object') {
    return globalThis.metauxGame;
  }
  return null;
}

function initMetauxGame() {
  if (typeof globalThis !== 'undefined' && typeof globalThis.initMetauxGame === 'function') {
    try {
      globalThis.initMetauxGame();
    } catch (error) {
      // Ignore initialization issues and keep graceful fallbacks.
    }
  }
  return resolveMetauxGame();
}

export function initializeArcadeUI(options) {
  const {
    elements,
    gameState,
    translate,
    showToast,
    showPage,
    saveGame,
    formatIntegerLocalized,
    setNavButtonLockState,
    isPageUnlocked,
    unlockPage,
    isArcadeUnlocked,
    ensureWaveGame,
    ensureBalanceGame,
    ensureQuantum2048Game,
    ensureApsCritState,
    getApsCritMultiplier,
    getApsCritRemainingSeconds,
    recalcProduction,
    updateUI,
    pulseApsCritPanel
  } = options;

  const translateMessage = typeof translate === 'function'
    ? translate
    : (key, params, fallback) => {
        if (typeof globalThis !== 'undefined' && typeof globalThis.t === 'function') {
          try {
            return globalThis.t(key, params, fallback);
          } catch (error) {
            return fallback ?? key;
          }
        }
        return fallback ?? key;
      };

  const formatTicketLabel = getGlobalFunction(
    'formatTicketLabel',
    count => {
      const numeric = Math.max(0, Math.floor(Number(count) || 0));
      const formatted = formatIntegerLocalized(numeric);
      const unitKey = numeric === 1
        ? 'scripts.gacha.tickets.single'
        : 'scripts.gacha.tickets.plural';
      const fallback = numeric === 1 ? 'ticket' : 'tickets';
      const label = translateMessage(unitKey, { count: numeric }, fallback) || fallback;
      return `${formatted} ${label}`;
    }
  );

  const formatBonusTicketLabel = getGlobalFunction(
    'formatBonusTicketLabel',
    count => {
      const numeric = Math.max(0, Math.floor(Number(count) || 0));
      const formatted = formatIntegerLocalized(numeric);
      const unitKey = numeric === 1
        ? 'scripts.gacha.tickets.bonusSingle'
        : 'scripts.gacha.tickets.bonusPlural';
      const fallback = numeric === 1 ? 'bonus' : 'bonus';
      const label = translateMessage(unitKey, { count: numeric }, fallback) || fallback;
      return `${formatted} ${label}`;
    }
  );

  const handleGachaSunClick = getGlobalFunction('handleGachaSunClick', null);
  const toggleGachaRollMode = getGlobalFunction('toggleGachaRollMode', null);

  function triggerBrandPortalPulse() {
    const pulseTarget = elements.navArcadeButton;
    if (!pulseTarget) {
      return;
    }
    pulseTarget.classList.add('nav-button--pulse');
    clearTimeout(triggerBrandPortalPulse.timeoutId);
    triggerBrandPortalPulse.timeoutId = setTimeout(() => {
      if (elements.navArcadeButton) {
        elements.navArcadeButton.classList.remove('nav-button--pulse');
      }
    }, 1600);
  }

  function getMetauxCreditCount() {
    return Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
  }

  function formatMetauxCreditLabel(count) {
    const formatter = formatBonusTicketLabel || formatIntegerLocalized;
    if (formatter === formatIntegerLocalized) {
      const numeric = Math.max(0, Math.floor(Number(count) || 0));
      const unit = numeric === 1 ? 'crédit' : 'crédits';
      return `${formatIntegerLocalized(numeric)} ${unit}`;
    }
    return formatter(count);
  }

  function isMetauxSessionRunning() {
    const metauxGame = resolveMetauxGame();
    if (!metauxGame) {
      return false;
    }
    if (typeof metauxGame.isSessionRunning === 'function') {
      return metauxGame.isSessionRunning();
    }
    return metauxGame.gameOver === false;
  }

  function updateMetauxCreditsUI() {
    const available = getMetauxCreditCount();
    const active = isMetauxSessionRunning();
    if (elements.metauxNewGameCredits) {
      elements.metauxNewGameCredits.textContent = `Mach3 : ${formatMetauxCreditLabel(available)}`;
    }
    if (elements.metauxNewGameButton) {
      const canStart = available > 0 && !active;
      elements.metauxNewGameButton.disabled = !canStart;
      elements.metauxNewGameButton.setAttribute('aria-disabled', canStart ? 'false' : 'true');
      const tooltip = canStart
        ? `Consomme 1 crédit Mach3 (restant : ${formatMetauxCreditLabel(available)}).`
        : available > 0
          ? 'Partie en cours… Terminez-la avant de relancer.'
          : 'Aucun crédit Mach3 disponible. Jouez à Atom2Univers pour en gagner.';
      elements.metauxNewGameButton.title = tooltip;
      const ariaLabel = `${available > 0 ? 'Nouvelle partie' : 'Crédit indisponible'} — ${tooltip}`;
      elements.metauxNewGameButton.setAttribute('aria-label', ariaLabel);
    }
    if (elements.metauxFreePlayButton) {
      const disabled = active;
      elements.metauxFreePlayButton.disabled = disabled;
      elements.metauxFreePlayButton.setAttribute('aria-disabled', disabled ? 'true' : 'false');
      const tooltip = disabled
        ? 'Partie en cours… Terminez-la avant de relancer.'
        : 'Partie libre : aucun crédit requis.';
      elements.metauxFreePlayButton.title = tooltip;
      const label = (elements.metauxFreePlayButton.textContent || '').trim() || 'Free Play';
      elements.metauxFreePlayButton.setAttribute('aria-label', `${label} — ${tooltip}`);
    }
    if (elements.metauxCreditStatus) {
      let statusText = '';
      const metauxGame = resolveMetauxGame();
      const freeMode = metauxGame
        && typeof metauxGame.isFreePlayMode === 'function'
        && metauxGame.isFreePlayMode();
      if (active) {
        statusText = freeMode
          ? 'Partie libre en cours — expérimentez sans pression.'
          : 'Forge en cours… Utilisez vos déplacements pour créer des alliages !';
      } else if (available > 0) {
        statusText = `Crédits disponibles : ${formatMetauxCreditLabel(available)}.`;
      } else {
        statusText = 'Aucun crédit Mach3 disponible. Lancez une partie libre ou jouez à Atom2Univers pour en gagner.';
      }
      elements.metauxCreditStatus.textContent = statusText;
      elements.metauxCreditStatus.hidden = false;
    }
  }

  function updateArcadeTicketDisplay() {
    const available = Math.max(0, Math.floor(Number(gameState.gachaTickets) || 0));
    const ticketLabel = formatTicketLabel(available);
    if (elements.arcadeTicketValues?.length) {
      elements.arcadeTicketValues.forEach(valueElement => {
        valueElement.textContent = ticketLabel;
      });
    }
    if (elements.arcadeTicketButtons?.length) {
      const gachaUnlocked = isPageUnlocked('gacha');
      elements.arcadeTicketButtons.forEach(button => {
        button.disabled = !gachaUnlocked;
        button.setAttribute('aria-disabled', gachaUnlocked ? 'false' : 'true');
        if (gachaUnlocked) {
          button.setAttribute('aria-label', `Ouvrir le portail Gacha (${ticketLabel})`);
          button.title = `Tickets disponibles : ${ticketLabel}`;
        } else {
          button.setAttribute('aria-label', 'Portail Gacha verrouillé');
          button.title = 'Obtenez un ticket de tirage pour débloquer le portail Gacha';
        }
      });
    }
    const bonusCount = Math.max(0, Math.floor(Number(gameState.bonusParticulesTickets) || 0));
    const bonusValue = formatIntegerLocalized(bonusCount);
    const bonusLabel = formatMetauxCreditLabel(bonusCount);
    if (elements.arcadeBonusTicketValues?.length) {
      elements.arcadeBonusTicketValues.forEach(valueElement => {
        valueElement.textContent = bonusValue;
      });
    }
    if (elements.arcadeBonusTicketAnnouncements?.length) {
      elements.arcadeBonusTicketAnnouncements.forEach(announcement => {
        announcement.textContent = `Mach3 : ${bonusLabel}`;
      });
    }
    if (elements.arcadeBonusTicketButtons?.length) {
      const hasCredits = bonusCount > 0;
      elements.arcadeBonusTicketButtons.forEach(button => {
        button.disabled = !hasCredits;
        button.setAttribute('aria-disabled', hasCredits ? 'false' : 'true');
        if (hasCredits) {
          button.title = `Lancer Métaux — ${bonusLabel}`;
          button.setAttribute('aria-label', `Ouvrir Métaux (Mach3 : ${bonusLabel})`);
        } else {
          button.title = 'Attrapez un graviton pour gagner un Mach3.';
          button.setAttribute('aria-label', 'Mach3 indisponible — attrapez un graviton.');
        }
      });
    }
    updateMetauxCreditsUI();
  }

  function updateBrandPortalState(options = {}) {
    const unlocked = isArcadeUnlocked();
    if (elements.brandPortal) {
      elements.brandPortal.disabled = false;
      elements.brandPortal.setAttribute('aria-disabled', 'false');
      elements.brandPortal.classList.remove('brand--locked');
      elements.brandPortal.classList.toggle('brand--portal-ready', unlocked);
    }
    if (elements.navArcadeButton) {
      setNavButtonLockState(elements.navArcadeButton, unlocked);
      if (!unlocked) {
        elements.navArcadeButton.classList.remove('nav-button--pulse');
      } else {
        updateArcadeTicketDisplay();
        if (options.animate) {
          triggerBrandPortalPulse();
        }
      }
    } else if (unlocked) {
      updateArcadeTicketDisplay();
    }
  }

  function handleMetauxSessionEnd(summary) {
    updateMetauxCreditsUI();
    if (!summary || typeof summary !== 'object') {
      return;
    }
    const elapsedMs = Number(summary.elapsedMs ?? summary.time ?? 0);
    const matches = Number(summary.matches ?? summary.matchCount ?? 0);
    const secondsEarned = Number.isFinite(elapsedMs) && elapsedMs > 0
      ? Math.max(0, Math.round(elapsedMs / 1000))
      : 0;
    const matchesEarned = Number.isFinite(matches) && matches > 0
      ? Math.max(0, Math.floor(matches))
      : 0;
    if (secondsEarned <= 0 && matchesEarned <= 0) {
      return;
    }
    const apsCrit = ensureApsCritState();
    const hadEffects = apsCrit.effects.length > 0;
    const previousMultiplier = getApsCritMultiplier(apsCrit);
    const currentRemaining = getApsCritRemainingSeconds(apsCrit);
    let chronoAdded = 0;
    let effectAdded = false;
    if (!hadEffects) {
      if (secondsEarned > 0 && matchesEarned > 0) {
        apsCrit.effects.push({
          multiplierAdd: matchesEarned,
          remainingSeconds: secondsEarned
        });
        chronoAdded = secondsEarned;
        effectAdded = true;
      }
    } else if (matchesEarned > 0 && currentRemaining > 0) {
      apsCrit.effects.push({
        multiplierAdd: matchesEarned,
        remainingSeconds: currentRemaining
      });
      effectAdded = true;
    }
    if (!effectAdded) {
      return;
    }
    apsCrit.effects = apsCrit.effects.filter(effect => {
      const remaining = Number(effect?.remainingSeconds) || 0;
      const value = Number(effect?.multiplierAdd) || 0;
      return remaining > 0 && value > 0;
    });
    if (!apsCrit.effects.length) {
      return;
    }
    const newMultiplier = getApsCritMultiplier(apsCrit);
    if (newMultiplier !== previousMultiplier) {
      recalcProduction();
    }
    updateUI();
    pulseApsCritPanel();
    const messageParts = [];
    if (chronoAdded > 0) {
      messageParts.push(translateMessage('scripts.app.metaux.chronoBonus', {
        value: formatIntegerLocalized(chronoAdded)
      }, `+${chronoAdded}s`));
    }
    if (matchesEarned > 0) {
      messageParts.push(translateMessage('scripts.app.metaux.multiBonus', {
        value: formatIntegerLocalized(matchesEarned)
      }, `+${matchesEarned}`));
    }
    if (messageParts.length) {
      showToast(translateMessage('scripts.app.metaux.toast', {
        details: messageParts.join(' · ')
      }, messageParts.join(' · ')));
    }
    saveGame();
  }

  if (elements.arcadeHubCards?.length) {
    elements.arcadeHubCards.forEach(card => {
      card.addEventListener('click', () => {
        const target = card.dataset.pageTarget;
        if (!target || !isPageUnlocked(target)) {
          return;
        }
        if (target === 'wave') {
          ensureWaveGame();
        }
        if (target === 'balance') {
          ensureBalanceGame();
        }
        if (target === 'quantum2048') {
          ensureQuantum2048Game();
        }
        showPage(target);
      });
    });
  }

  if (elements.arcadeTicketButtons?.length) {
    elements.arcadeTicketButtons.forEach(button => {
      button.addEventListener('click', () => {
        if (!isPageUnlocked('gacha')) {
          return;
        }
        showPage('gacha');
      });
    });
  }

  if (elements.arcadeBonusTicketButtons?.length) {
    elements.arcadeBonusTicketButtons.forEach(button => {
      button.addEventListener('click', () => {
        if (button.disabled) {
          return;
        }
        showPage('metaux');
      });
    });
  }

  if (elements.brandPortal) {
    elements.brandPortal.addEventListener('click', () => {
      showPage('game');
    });
  }

  if (elements.arcadeReturnButton) {
    elements.arcadeReturnButton.addEventListener('click', () => {
      showPage('game');
      if (isArcadeUnlocked()) {
        triggerBrandPortalPulse();
      }
    });
  }

  if (elements.metauxOpenButton) {
    elements.metauxOpenButton.addEventListener('click', () => {
      showPage('metaux');
    });
  }

  if (elements.metauxReturnButton) {
    elements.metauxReturnButton.addEventListener('click', () => {
      showPage('game');
    });
  }

  if (elements.metauxNewGameButton) {
    elements.metauxNewGameButton.addEventListener('click', () => {
      const metauxGame = initMetauxGame();
      const credits = getMetauxCreditCount();
      if (!metauxGame) {
        showToast(translateMessage('scripts.app.metaux.unavailable', null, 'Métaux indisponible.'));
        return;
      }
      if (isMetauxSessionRunning()) {
        showToast(translateMessage('scripts.app.metaux.gameInProgress', null, 'Partie Métaux déjà en cours.'));
        updateMetauxCreditsUI();
        return;
      }
      if (credits <= 0) {
        showToast(translateMessage('scripts.app.metaux.noCredits', null, 'Aucun crédit Mach3 disponible.'));
        updateMetauxCreditsUI();
        return;
      }
      gameState.bonusParticulesTickets = credits - 1;
      if (typeof metauxGame.restart === 'function') {
        metauxGame.restart();
      }
      updateMetauxCreditsUI();
      saveGame();
    });
  }

  if (elements.metauxFreePlayButton) {
    elements.metauxFreePlayButton.addEventListener('click', () => {
      const metauxGame = initMetauxGame();
      if (!metauxGame) {
        showToast(translateMessage('scripts.app.metaux.unavailable', null, 'Métaux indisponible.'));
        return;
      }
      if (isMetauxSessionRunning()) {
        showToast(translateMessage('scripts.app.metaux.gameInProgress', null, 'Partie Métaux déjà en cours.'));
        updateMetauxCreditsUI();
        return;
      }
      if (typeof metauxGame.startFreePlay === 'function') {
        metauxGame.startFreePlay();
      } else if (typeof metauxGame.restart === 'function') {
        metauxGame.restart({ freePlay: true });
      }
      updateMetauxCreditsUI();
    });
  }

  if (elements.metauxFreePlayExitButton) {
    elements.metauxFreePlayExitButton.addEventListener('click', () => {
      const metauxGame = initMetauxGame();
      if (!metauxGame) {
        showToast(translateMessage('scripts.app.metaux.unavailable', null, 'Métaux indisponible.'));
        return;
      }
      if (typeof metauxGame.isFreePlayMode === 'function' && !metauxGame.isFreePlayMode()) {
        return;
      }
      if (metauxGame.processing) {
        showToast('Patientez, la réaction en chaîne est en cours.');
        return;
      }
      if (typeof metauxGame.endFreePlaySession === 'function') {
        const ended = metauxGame.endFreePlaySession({ showEndScreen: true });
        if (ended && typeof globalThis !== 'undefined' && typeof globalThis.updateMetauxCreditsUI === 'function') {
          globalThis.updateMetauxCreditsUI();
        }
      }
    });
  }

  if (elements.gachaSunButton && handleGachaSunClick) {
    elements.gachaSunButton.addEventListener('click', event => {
      event.preventDefault();
      Promise.resolve(handleGachaSunClick()).catch(error => {
        console.error('Erreur lors du tirage cosmique', error);
      });
    });
  }

  if (elements.gachaTicketModeButton && toggleGachaRollMode) {
    elements.gachaTicketModeButton.addEventListener('click', event => {
      event.preventDefault();
      toggleGachaRollMode();
    });
  }

  if (typeof globalThis !== 'undefined') {
    globalThis.updateArcadeTicketDisplay = updateArcadeTicketDisplay;
    globalThis.updateMetauxCreditsUI = updateMetauxCreditsUI;
    globalThis.handleMetauxSessionEnd = handleMetauxSessionEnd;
  }

  updateArcadeTicketDisplay();

  return {
    updateArcadeTicketDisplay,
    updateBrandPortalState,
    updateMetauxCreditsUI,
    handleMetauxSessionEnd
  };
}

