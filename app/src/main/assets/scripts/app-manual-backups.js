const manualBackupStatusFallbacks = Object.freeze({
  idle: 'Choose an action to manage your saves.',
  saving: 'Preparing the backup…',
  loading: 'Loading the selected backup…',
  saveSuccess: 'Backup exported on your device.',
  loadSuccess: 'Backup imported successfully.',
  error: 'Something went wrong. Please try again.',
  unsupported: 'Feature available only on the Android app.'
});

const manualBackupUiState = {
  statusKey: 'idle',
  busy: false,
  supported: false
};

function getAndroidManualBackupBridge() {
  if (typeof window === 'undefined') {
    return null;
  }
  const bridge = window.AndroidBridge;
  if (!bridge) {
    return null;
  }
  const type = typeof bridge;
  if (type === 'object' || type === 'function') {
    return bridge;
  }
  return null;
}

function renderManualBackupStatus() {
  if (!elements || !elements.backupStatus) {
    return;
  }
  const key = manualBackupUiState.statusKey in manualBackupStatusFallbacks
    ? manualBackupUiState.statusKey
    : 'idle';
  const translationKey = `index.sections.options.backups.status.${key}`;
  const fallback = manualBackupStatusFallbacks[key] || manualBackupStatusFallbacks.idle;
  elements.backupStatus.textContent = translateOrDefault(translationKey, fallback);
}

function updateManualBackupStatus(key) {
  if (typeof key !== 'string' || !key) {
    return;
  }
  manualBackupUiState.statusKey = key;
  renderManualBackupStatus();
}

function updateManualBackupButtons() {
  const disabled = manualBackupUiState.busy || !manualBackupUiState.supported;
  if (elements && elements.backupSaveButton) {
    elements.backupSaveButton.disabled = disabled;
  }
  if (elements && elements.backupLoadButton) {
    elements.backupLoadButton.disabled = disabled;
  }
}

function refreshManualBackupSupportState() {
  const bridge = getAndroidManualBackupBridge();
  const supported = !!(
    bridge
    && typeof bridge.saveBackup === 'function'
    && typeof bridge.loadBackup === 'function'
    && typeof bridge.sendBackupData === 'function'
  );
  manualBackupUiState.supported = supported;
  if (!supported && manualBackupUiState.statusKey !== 'unsupported') {
    manualBackupUiState.statusKey = 'unsupported';
  }
  if (!supported) {
    manualBackupUiState.busy = false;
  }
  updateManualBackupButtons();
  renderManualBackupStatus();
  return supported;
}

function setManualBackupBusy(isBusy) {
  manualBackupUiState.busy = !!isBusy;
  updateManualBackupButtons();
}

function initManualBackupUi() {
  const supported = refreshManualBackupSupportState();
  updateManualBackupStatus(supported ? 'idle' : 'unsupported');
}

function requestManualBackupExport() {
  const supported = refreshManualBackupSupportState();
  if (!supported) {
    handleManualBackupSaveComplete(false, 'unsupported');
    return false;
  }
  const bridge = getAndroidManualBackupBridge();
  if (!bridge || typeof bridge.saveBackup !== 'function') {
    handleManualBackupSaveComplete(false, 'unsupported');
    return false;
  }
  setManualBackupBusy(true);
  updateManualBackupStatus('saving');
  showToast(t('scripts.app.backups.export.start'));
  try {
    bridge.saveBackup();
    return true;
  } catch (error) {
    console.error('Unable to request manual backup export', error);
    handleManualBackupSaveComplete(false, 'error');
  }
  return false;
}

function requestManualBackupImport() {
  const supported = refreshManualBackupSupportState();
  if (!supported) {
    handleManualBackupLoadComplete(false, 'unsupported');
    return false;
  }
  const bridge = getAndroidManualBackupBridge();
  if (!bridge || typeof bridge.loadBackup !== 'function') {
    handleManualBackupLoadComplete(false, 'unsupported');
    return false;
  }
  setManualBackupBusy(true);
  updateManualBackupStatus('loading');
  showToast(t('scripts.app.backups.import.start'));
  try {
    bridge.loadBackup();
    return true;
  } catch (error) {
    console.error('Unable to request manual backup import', error);
    handleManualBackupLoadComplete(false, 'error');
  }
  return false;
}

function encodeUtf8ToBase64(value) {
  if (typeof value !== 'string' || !value) {
    return null;
  }
  try {
    if (typeof globalThis.TextEncoder === 'function' && typeof globalThis.btoa === 'function') {
      const encoder = new TextEncoder();
      const bytes = encoder.encode(value);
      let binary = '';
      for (let index = 0; index < bytes.length; index += 1) {
        binary += String.fromCharCode(bytes[index]);
      }
      return globalThis.btoa(binary);
    }
  } catch (error) {
    // Fallback below.
  }
  if (
    typeof globalThis.btoa === 'function'
    && typeof globalThis.encodeURIComponent === 'function'
    && typeof globalThis.unescape === 'function'
  ) {
    try {
      return globalThis.btoa(globalThis.unescape(globalThis.encodeURIComponent(value)));
    } catch (error) {
      // Continue to Buffer fallback.
    }
  }
  if (typeof Buffer !== 'undefined') {
    try {
      return Buffer.from(value, 'utf-8').toString('base64');
    } catch (error) {
      return null;
    }
  }
  return null;
}

function decodeBase64ToUtf8(base64) {
  if (typeof base64 !== 'string' || !base64) {
    return null;
  }
  try {
    if (typeof globalThis.atob === 'function') {
      const binary = globalThis.atob(base64);
      if (typeof globalThis.TextDecoder === 'function') {
        const decoder = new TextDecoder();
        const bytes = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) {
          bytes[index] = binary.charCodeAt(index);
        }
        return decoder.decode(bytes);
      }
      if (
        typeof globalThis.decodeURIComponent === 'function'
        && typeof globalThis.escape === 'function'
      ) {
        return globalThis.decodeURIComponent(globalThis.escape(binary));
      }
      return binary;
    }
  } catch (error) {
    // Fallback below.
  }
  if (typeof Buffer !== 'undefined') {
    try {
      return Buffer.from(base64, 'base64').toString('utf-8');
    } catch (error) {
      return null;
    }
  }
  return null;
}

function buildManualBackupEnvelope(serialized) {
  if (typeof serialized !== 'string' || !serialized) {
    return null;
  }
  try {
    const payload = {
      version: 1,
      savedAt: Date.now(),
      source: 'manual',
      data: serialized
    };
    return JSON.stringify(payload);
  } catch (error) {
    return null;
  }
}

function extractSerializedBackupPayload(raw) {
  if (typeof raw !== 'string' || !raw) {
    return null;
  }
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const parsed = JSON.parse(trimmed);
    if (parsed && typeof parsed === 'object') {
      const wrappedData = typeof parsed.data === 'string' ? parsed.data.trim() : null;
      if (wrappedData) {
        return wrappedData;
      }
      const hasCoreFields = parsed
        && typeof parsed === 'object'
        && parsed.atoms != null
        && parsed.lifetime != null
        && parsed.perClick != null
        && parsed.perSecond != null;
      if (hasCoreFields) {
        return trimmed;
      }
    }
  } catch (error) {
    return trimmed;
  }
  return trimmed;
}

function handleManualBackupSaveComplete(success, reason) {
  const normalizedReason = typeof reason === 'string' && reason ? reason : 'error';
  setManualBackupBusy(false);
  if (success) {
    updateManualBackupStatus('saveSuccess');
    showToast(t('scripts.app.backups.export.success'));
    return;
  }
  switch (normalizedReason) {
    case 'cancelled':
      updateManualBackupStatus('idle');
      showToast(t('scripts.app.backups.export.cancelled'));
      break;
    case 'unsupported':
      updateManualBackupStatus('unsupported');
      showToast(t('scripts.app.backups.export.unsupported'));
      break;
    case 'encoding':
    case 'serialization':
      updateManualBackupStatus('error');
      showToast(t('scripts.app.backups.export.encodeError'));
      break;
    default:
      updateManualBackupStatus('error');
      showToast(t('scripts.app.backups.export.failed'));
      break;
  }
}

function handleManualBackupLoadComplete(success, reason) {
  const normalizedReason = typeof reason === 'string' && reason ? reason : 'error';
  setManualBackupBusy(false);
  if (success) {
    updateManualBackupStatus('loadSuccess');
    showToast(t('scripts.app.backups.import.success'));
    return;
  }
  switch (normalizedReason) {
    case 'cancelled':
      updateManualBackupStatus('idle');
      showToast(t('scripts.app.backups.import.cancelled'));
      break;
    case 'unsupported':
      updateManualBackupStatus('unsupported');
      showToast(t('scripts.app.backups.export.unsupported'));
      break;
    case 'decode':
      updateManualBackupStatus('error');
      showToast(t('scripts.app.backups.import.decodeError'));
      break;
    case 'apply':
      updateManualBackupStatus('error');
      showToast(t('scripts.app.backups.import.applyError'));
      break;
    default:
      updateManualBackupStatus('error');
      showToast(t('scripts.app.backups.import.failed'));
      break;
  }
}

function applyManualBackupFromSerialized(serialized) {
  if (typeof serialized !== 'string' || !serialized) {
    handleManualBackupLoadComplete(false, 'decode');
    return;
  }
  try {
    applySerializedGameState(serialized);
    if (typeof localStorage !== 'undefined' && localStorage) {
      try {
        localStorage.setItem(PRIMARY_SAVE_STORAGE_KEY, serialized);
      } catch (storageError) {
        console.warn('Unable to persist imported backup locally', storageError);
      }
    }
    writeNativeSaveData(serialized);
    storeReloadSaveSnapshot(serialized);
    lastSerializedSave = serialized;
    saveBackupManager.recordSuccessfulSave({ currentSerialized: serialized });
    handleManualBackupLoadComplete(true);
  } catch (error) {
    console.error('Unable to apply imported backup payload', error);
    handleManualBackupLoadComplete(false, 'apply');
  }
}

if (typeof window !== 'undefined') {
  window.getBackupData = function () {
    try {
      const payload = serializeState();
      const serialized = JSON.stringify(payload);
      const envelope = buildManualBackupEnvelope(serialized) || serialized;
      const base64Data = encodeUtf8ToBase64(envelope);
      if (!base64Data) {
        handleManualBackupSaveComplete(false, 'encoding');
        return false;
      }
      const bridge = getAndroidManualBackupBridge();
      if (!bridge || typeof bridge.sendBackupData !== 'function') {
        handleManualBackupSaveComplete(false, 'unsupported');
        return false;
      }
      bridge.sendBackupData(base64Data);
      return true;
    } catch (error) {
      console.error('Unable to serialize manual backup data', error);
      handleManualBackupSaveComplete(false, 'serialization');
    }
    return false;
  };

  window.onBackupSaved = function (success, reason) {
    handleManualBackupSaveComplete(success === true, reason);
  };

  window.onBackupLoaded = function (base64Data) {
    if (typeof base64Data !== 'string' || !base64Data) {
      handleManualBackupLoadComplete(false, 'decode');
      return;
    }
    const payload = decodeBase64ToUtf8(base64Data);
    if (typeof payload !== 'string' || !payload) {
      handleManualBackupLoadComplete(false, 'decode');
      return;
    }
    const serialized = extractSerializedBackupPayload(payload);
    if (typeof serialized !== 'string' || !serialized) {
      handleManualBackupLoadComplete(false, 'decode');
      return;
    }
    applyManualBackupFromSerialized(serialized);
  };

  window.onBackupLoadFailed = function (reason) {
    handleManualBackupLoadComplete(false, reason);
  };
}
