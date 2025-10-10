const FUSION_LOG_SUCCESS_CLASS = 'fusion-log--success';
const FUSION_LOG_FAILURE_CLASS = 'fusion-log--failure';

function resolveGlobalFunction(name, override) {
  if (typeof override === 'function') {
    return override;
  }
  if (typeof globalThis !== 'undefined') {
    const candidate = globalThis[name];
    if (typeof candidate === 'function') {
      return candidate;
    }
  }
  return null;
}

function createFallbackLogger(elements) {
  if (!elements || typeof elements !== 'object') {
    return () => {};
  }
  const logElement = elements.fusionLog || null;
  if (!logElement) {
    return () => {};
  }
  return (message, status = null) => {
    const text = typeof message === 'string' ? message : '';
    logElement.textContent = text;
    if (typeof logElement.classList?.remove === 'function') {
      logElement.classList.remove(FUSION_LOG_SUCCESS_CLASS, FUSION_LOG_FAILURE_CLASS);
      if (status === 'success') {
        logElement.classList.add(FUSION_LOG_SUCCESS_CLASS);
      } else if (status === 'failure') {
        logElement.classList.add(FUSION_LOG_FAILURE_CLASS);
      }
    }
  };
}

export function initializeFusionUI(options = {}) {
  const {
    elements = null,
    renderFusionList: renderOverride,
    updateFusionUI: updateOverride,
    setFusionLog: setLogOverride,
    refreshFusionLocalization: refreshLocalizationOverride
  } = options;

  if (elements && typeof globalThis !== 'undefined') {
    globalThis.atom2universElements = elements;
    if (!globalThis.elements || globalThis.elements === elements) {
      globalThis.elements = elements;
    }
  }

  const renderFusionList = resolveGlobalFunction('renderFusionList', renderOverride);
  const updateFusionUI = resolveGlobalFunction('updateFusionUI', updateOverride);
  const setFusionLog = resolveGlobalFunction('setFusionLog', setLogOverride)
    || createFallbackLogger(elements);
  const refreshFusionLocalization = resolveGlobalFunction(
    'refreshFusionLocalization',
    refreshLocalizationOverride
  );

  const safeRender = typeof renderFusionList === 'function'
    ? () => { renderFusionList(); }
    : () => {};
  const safeUpdate = typeof updateFusionUI === 'function'
    ? () => { updateFusionUI(); }
    : () => {};
  const safeSetLog = typeof setFusionLog === 'function'
    ? (message, status) => { setFusionLog(message, status); }
    : () => {};
  const safeRefreshLocalization = typeof refreshFusionLocalization === 'function'
    ? () => { refreshFusionLocalization(); }
    : () => {};

  function refresh() {
    safeRender();
    safeUpdate();
  }

  return {
    render: safeRender,
    update: safeUpdate,
    setLog: safeSetLog,
    refreshLocalization: safeRefreshLocalization,
    refresh
  };
}

export default initializeFusionUI;
