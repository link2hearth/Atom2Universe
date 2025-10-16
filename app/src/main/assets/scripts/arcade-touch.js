(() => {
  const SECTION_IDS = ['touchArcadeHub', 'arcade', 'metaux', 'balance', 'gameOfLife'];
  const EXIT_URL = 'index.html#arcadeHub';
  const state = {
    current: null,
    instances: {
      particules: null,
      balance: null,
      metaux: null,
      gameOfLife: null
    },
    touchManager: null
  };

  const select = id => document.getElementById(id);

  function resolveTouchModeConfig() {
    const rootConfig = globalThis.ATOM2UNIVERS_CONFIG;
    if (rootConfig && typeof rootConfig === 'object' && rootConfig.touchMode) {
      return rootConfig.touchMode;
    }
    return {
      defaultScrollMode: 'lock',
      unlockDelayMs: 250
    };
  }

  function resolveArcadeModeButtons() {
    const container = select('arcadeModeSwitch');
    if (!container) {
      return [];
    }
    return Array.from(container.querySelectorAll('button'));
  }

  function ensureParticulesGame() {
    if (state.instances.particules || typeof ParticulesGame !== 'function') {
      return state.instances.particules;
    }
    const canvas = select('arcadeGameCanvas');
    if (!canvas) {
      return null;
    }
    state.instances.particules = new ParticulesGame({
      canvas,
      particleLayer: select('arcadeParticleLayer'),
      overlay: select('arcadeOverlay'),
      overlayButton: select('arcadeOverlayButton'),
      overlaySecondaryButton: select('arcadeOverlayQuitButton'),
      overlayMessage: select('arcadeOverlayMessage'),
      levelLabel: select('arcadeLevelValue'),
      livesLabel: select('arcadeLivesValue'),
      scoreLabel: select('arcadeScoreValue'),
      comboLabel: select('arcadeComboMessage'),
      modeField: select('arcadeModeField'),
      modeButtons: resolveArcadeModeButtons(),
      modeHint: select('arcadeModeHint')
    });
    return state.instances.particules;
  }

  function ensureBalanceGameInstance() {
    if (state.instances.balance || typeof BalanceGame !== 'function') {
      return state.instances.balance;
    }
    const page = select('balance');
    if (!page) {
      return null;
    }
    state.instances.balance = new BalanceGame({
      pageElement: page,
      stageElement: select('balanceStage'),
      boardElement: select('balanceBoard'),
      surfaceElement: select('balanceBoardSurface'),
      inventoryElement: select('balancePieces'),
      statusButton: select('balanceStatusButton'),
      statusElement: select('balanceStatus'),
      difficultySelect: select('balanceDifficultySelect'),
      resetButton: select('balanceResetButton'),
      dragLayer: select('balanceDragLayer')
    });
    return state.instances.balance;
  }

  function ensureMetauxGameInstance() {
    if (state.instances.metaux || typeof MetauxMatch3Game !== 'function') {
      return state.instances.metaux;
    }
    const board = select('metauxBoard');
    if (!board) {
      return null;
    }
    const game = new MetauxMatch3Game({
      boardElement: board,
      timerLabelElement: select('metauxTimerLabel'),
      timerValueElement: select('metauxTimerValue'),
      timerFillElement: select('metauxTimerFill'),
      timerMaxElement: select('metauxTimerMaxValue'),
      freePlayExitButton: select('metauxFreePlayExitButton'),
      endScreenElement: select('metauxEndScreen'),
      endTimeElement: select('metauxEndTimeValue'),
      endMatchesElement: select('metauxEndMatchesValue')
    });
    if (typeof game.initialize === 'function') {
      game.initialize();
    }
    state.instances.metaux = game;
    return state.instances.metaux;
  }

  function ensureGameOfLifeInstance() {
    if (state.instances.gameOfLife && typeof state.instances.gameOfLife === 'object') {
      return state.instances.gameOfLife;
    }
    if (typeof window !== 'undefined' && window.gameOfLifeArcade) {
      state.instances.gameOfLife = window.gameOfLifeArcade;
      return state.instances.gameOfLife;
    }
    return null;
  }

  function toggleSectionVisibility(targetId) {
    SECTION_IDS.forEach(sectionId => {
      const section = select(sectionId);
      if (!section) {
        return;
      }
      const isTarget = sectionId === targetId;
      if (isTarget) {
        section.removeAttribute('hidden');
        section.setAttribute('aria-hidden', 'false');
        section.classList.add('active');
      } else {
        section.classList.remove('active');
        section.setAttribute('aria-hidden', 'true');
        if (!section.hasAttribute('hidden')) {
          section.setAttribute('hidden', '');
        }
      }
    });
  }

  function updateNavigation(targetId) {
    const buttons = document.querySelectorAll('.touch-arcade-nav .nav-button');
    buttons.forEach(button => {
      const matches = button.dataset.target === targetId;
      button.classList.toggle('is-active', matches);
      button.setAttribute('aria-pressed', matches ? 'true' : 'false');
    });
  }

  function getInstanceForSection(sectionId) {
    switch (sectionId) {
      case 'arcade':
        return ensureParticulesGame();
      case 'balance':
        return ensureBalanceGameInstance();
      case 'metaux':
        return ensureMetauxGameInstance();
      case 'gameOfLife':
        return ensureGameOfLifeInstance();
      default:
        return null;
    }
  }

  function handleSectionLeave(sectionId) {
    if (!sectionId) {
      return;
    }
    const instance = getInstanceForSection(sectionId);
    if (instance && typeof instance.onLeave === 'function') {
      instance.onLeave();
    }
  }

  function handleSectionEnter(sectionId) {
    switch (sectionId) {
      case 'arcade': {
        const instance = ensureParticulesGame();
        if (instance && typeof instance.onEnter === 'function') {
          instance.onEnter();
        }
        break;
      }
      case 'balance': {
        const instance = ensureBalanceGameInstance();
        if (instance && typeof instance.onEnter === 'function') {
          instance.onEnter();
        }
        break;
      }
      case 'metaux': {
        const instance = ensureMetauxGameInstance();
        if (instance && typeof instance.onEnter === 'function') {
          instance.onEnter();
        }
        break;
      }
      case 'gameOfLife': {
        const instance = ensureGameOfLifeInstance();
        if (instance && typeof instance.onEnter === 'function') {
          instance.onEnter();
        }
        break;
      }
      case 'touchArcadeHub':
        if (state.touchManager && typeof state.touchManager.forceUnlock === 'function') {
          state.touchManager.forceUnlock();
        }
        break;
      default:
        break;
    }
  }

  function showSection(targetId) {
    if (!SECTION_IDS.includes(targetId)) {
      return;
    }
    const previous = state.current;
    if (previous === targetId) {
      updateNavigation(targetId);
      const sectionElement = select(targetId);
      ensureTouchManager(sectionElement);
      handleSectionEnter(targetId);
      return;
    }
    handleSectionLeave(previous);
    toggleSectionVisibility(targetId);
    updateNavigation(targetId);
    const sectionElement = select(targetId);
    ensureTouchManager(sectionElement);
    state.current = targetId;
    handleSectionEnter(targetId);
  }

  function ensureTouchManager(activeSectionElement) {
    if (!state.touchManager && typeof TouchInteractionManager === 'function') {
      const touchConfig = resolveTouchModeConfig();
      const rootElement = select('touchArcadeContainer') || document;
      state.touchManager = new TouchInteractionManager({
        rootElement,
        scrollTarget: document.body,
        defaultMode: touchConfig.defaultScrollMode,
        unlockDelayMs: touchConfig.unlockDelayMs
      });
      state.touchManager.start();
    }
    if (state.touchManager) {
      state.touchManager.applyPageMode(activeSectionElement);
    }
  }

  function bindTargetTriggers() {
    const triggers = document.querySelectorAll('[data-target]');
    triggers.forEach(trigger => {
      const { target } = trigger.dataset;
      if (!target) {
        return;
      }
      trigger.addEventListener('click', event => {
        event.preventDefault();
        showSection(target);
      });
    });
  }

  function exitToArcadeHub() {
    showSection('touchArcadeHub');
    if (state.touchManager && typeof state.touchManager.forceUnlock === 'function') {
      state.touchManager.forceUnlock();
    }
    if (typeof window !== 'undefined') {
      window.location.href = EXIT_URL;
    }
  }

  function bindExitButtons() {
    const exitButtons = document.querySelectorAll('[data-action="exit-hub"]');
    exitButtons.forEach(button => {
      button.addEventListener('click', event => {
        event.preventDefault();
        exitToArcadeHub();
      });
    });
  }

  function registerGameOfLifeReadyListener() {
    if (typeof window === 'undefined' || typeof window.addEventListener !== 'function') {
      return;
    }
    window.addEventListener('atom2univers:game-of-life-ready', event => {
      const instance = event?.detail?.instance || window.gameOfLifeArcade;
      if (instance && typeof instance === 'object') {
        state.instances.gameOfLife = instance;
        if (state.current === 'gameOfLife' && typeof instance.onEnter === 'function') {
          instance.onEnter();
        }
      }
    });
  }

  function applyInitialLanguage() {
    const i18n = globalThis.i18n;
    if (!i18n || typeof i18n.setLanguage !== 'function') {
      return Promise.resolve();
    }
    const lang = document.documentElement.getAttribute('lang') || 'fr';
    return i18n
      .setLanguage(lang)
      .catch(() => {})
      .finally(() => {
        if (typeof i18n.updateTranslations === 'function') {
          i18n.updateTranslations(document);
        }
      });
  }

  function start() {
    bindTargetTriggers();
    bindExitButtons();
    registerGameOfLifeReadyListener();
    showSection('touchArcadeHub');
  }

  function initialize() {
    applyInitialLanguage().finally(start);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }
})();
