(() => {
  const GLOBAL = typeof globalThis !== 'undefined' ? globalThis : window;
  if (!GLOBAL) {
    return;
  }
  const touchModeConfig = Object.freeze({
    defaultScrollMode: 'lock',
    unlockDelayMs: 250
  });
  if (!GLOBAL.ATOM2UNIVERS_CONFIG || typeof GLOBAL.ATOM2UNIVERS_CONFIG !== 'object') {
    GLOBAL.ATOM2UNIVERS_CONFIG = {};
  }
  GLOBAL.ATOM2UNIVERS_CONFIG.touchMode = touchModeConfig;
})();
