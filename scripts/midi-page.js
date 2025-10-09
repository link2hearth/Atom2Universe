(function initMidiPage() {
  'use strict';

  const LANGUAGE_STORAGE_KEY = 'atom2univers.language';
  const NOTE_NAMES = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
  const BLACK_NOTE_INDEXES = new Set([1, 3, 6, 8, 10]);
  const FALLBACK_KEYBOARD_LAYOUTS = [
    { id: '88', keyCount: 88, lowestMidiNote: 21, highestMidiNote: 108 },
    { id: '76', keyCount: 76, lowestMidiNote: 28, highestMidiNote: 103 },
    { id: '61', keyCount: 61, lowestMidiNote: 36, highestMidiNote: 96 }
  ];

  function getI18n() {
    return typeof globalThis !== 'undefined' ? globalThis.i18n : null;
  }

  function normalizeLanguageCode(raw) {
    if (typeof raw !== 'string') {
      return '';
    }
    return raw.trim().toLowerCase();
  }

  function getAvailableLanguages() {
    const api = getI18n();
    if (api && typeof api.getAvailableLanguages === 'function') {
      try {
        const languages = api.getAvailableLanguages();
        if (Array.isArray(languages) && languages.length) {
          const normalized = languages
            .map(code => (typeof code === 'string' ? code.trim() : ''))
            .filter(Boolean);
          if (normalized.length) {
            return normalized;
          }
        }
      } catch (error) {
        console.warn('Unable to read available languages from i18n', error);
      }
    }
    return ['fr', 'en'];
  }

  function matchAvailableLanguage(raw) {
    const normalized = normalizeLanguageCode(raw);
    if (!normalized) {
      return null;
    }
    const available = getAvailableLanguages();
    const direct = available.find(code => code.toLowerCase() === normalized);
    if (direct) {
      return direct;
    }
    const [base] = normalized.split('-');
    if (base) {
      const baseMatch = available.find(code => code.toLowerCase() === base);
      if (baseMatch) {
        return baseMatch;
      }
    }
    return null;
  }

  function readStoredLanguagePreference() {
    try {
      const stored = globalThis.localStorage?.getItem(LANGUAGE_STORAGE_KEY);
      if (stored) {
        const matched = matchAvailableLanguage(stored);
        if (matched) {
          return matched;
        }
      }
    } catch (error) {
      console.warn('Unable to read stored language preference', error);
    }
    return null;
  }

  function writeStoredLanguagePreference(lang) {
    try {
      const normalized = matchAvailableLanguage(lang) || normalizeLanguageCode(lang);
      if (normalized) {
        globalThis.localStorage?.setItem(LANGUAGE_STORAGE_KEY, normalized);
      }
    } catch (error) {
      console.warn('Unable to persist language preference', error);
    }
  }

  function detectNavigatorLanguage() {
    const nav = typeof navigator !== 'undefined' ? navigator : null;
    if (!nav) {
      return null;
    }
    const candidates = [];
    if (Array.isArray(nav.languages)) {
      candidates.push(...nav.languages);
    }
    if (typeof nav.language === 'string') {
      candidates.push(nav.language);
    }
    for (let index = 0; index < candidates.length; index += 1) {
      const match = matchAvailableLanguage(candidates[index]);
      if (match) {
        return match;
      }
    }
    return null;
  }

  function getDefaultLanguage() {
    const documentLang = typeof document !== 'undefined' ? document.documentElement?.lang : '';
    const matchedDocumentLang = documentLang ? matchAvailableLanguage(documentLang) : null;
    if (matchedDocumentLang) {
      return matchedDocumentLang;
    }
    const available = getAvailableLanguages();
    if (available.length) {
      return available[0];
    }
    return 'fr';
  }

  function getInitialLanguage() {
    const stored = readStoredLanguagePreference();
    if (stored) {
      return stored;
    }
    const navigatorLanguage = detectNavigatorLanguage();
    if (navigatorLanguage) {
      return navigatorLanguage;
    }
    return getDefaultLanguage();
  }

  function applyLanguage(language) {
    const api = getI18n();
    const fallback = getDefaultLanguage();
    const target = matchAvailableLanguage(language) || fallback;

    if (!api || typeof api.setLanguage !== 'function') {
      if (typeof document !== 'undefined' && document.documentElement) {
        document.documentElement.lang = target;
      }
      writeStoredLanguagePreference(target);
      return Promise.resolve();
    }

    return api
      .setLanguage(target)
      .then(() => {
        if (typeof api.updateTranslations === 'function') {
          api.updateTranslations(document);
        }
        const current = typeof api.getCurrentLanguage === 'function'
          ? api.getCurrentLanguage()
          : target;
        const normalized = matchAvailableLanguage(current) || target;
        if (typeof document !== 'undefined' && document.documentElement) {
          document.documentElement.lang = normalized;
        }
        writeStoredLanguagePreference(normalized);
      })
      .catch((error) => {
        console.error('Unable to initialize language for MIDI page', error);
        if (typeof document !== 'undefined' && document.documentElement) {
          document.documentElement.lang = target;
        }
      });
  }

  function getKeyboardWindowSize() {
    const configured = Number(globalThis.MIDI_KEYBOARD_WINDOW_SIZE);
    if (Number.isFinite(configured) && configured >= 12) {
      return Math.round(configured);
    }
    return 24;
  }

  function normalizeKeyboardLayout(raw) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }

    const id = typeof raw.id === 'string' && raw.id.trim() ? raw.id.trim() : '';
    const keyCountValue = Number(raw.keyCount);
    const hasKeyCount = Number.isFinite(keyCountValue) && keyCountValue > 0;

    let lowest = Number(
      raw.lowestMidiNote ?? raw.lowestNote ?? raw.startNote ?? raw.lowest
    );
    let highest = Number(
      raw.highestMidiNote ?? raw.highestNote ?? raw.endNote ?? raw.highest
    );

    const hasLowest = Number.isFinite(lowest);
    const hasHighest = Number.isFinite(highest);

    if (hasLowest && hasKeyCount && !hasHighest) {
      highest = lowest + Math.round(keyCountValue) - 1;
    } else if (hasHighest && hasKeyCount && !hasLowest) {
      lowest = highest - Math.round(keyCountValue) + 1;
    }

    if (!Number.isFinite(lowest) || !Number.isFinite(highest)) {
      return null;
    }

    const normalizedLowest = Math.round(Math.min(lowest, highest));
    const normalizedHighest = Math.round(Math.max(lowest, highest));
    const normalizedKeyCount = normalizedHighest - normalizedLowest + 1;

    if (normalizedKeyCount <= 0) {
      return null;
    }

    return {
      id: id || String(normalizedKeyCount),
      lowestNote: normalizedLowest,
      highestNote: normalizedHighest,
      keyCount: normalizedKeyCount
    };
  }

  function getConfiguredKeyboardLayouts() {
    const configured = Array.isArray(globalThis.MIDI_KEYBOARD_LAYOUTS)
      ? globalThis.MIDI_KEYBOARD_LAYOUTS
      : null;

    const normalized = (configured || FALLBACK_KEYBOARD_LAYOUTS)
      .map(normalizeKeyboardLayout)
      .filter(Boolean);

    if (normalized.length) {
      return normalized;
    }

    return FALLBACK_KEYBOARD_LAYOUTS
      .map(normalizeKeyboardLayout)
      .filter(Boolean);
  }

  function findLayoutById(id, layouts) {
    if (!id || !Array.isArray(layouts)) {
      return null;
    }

    const normalizedId = String(id).trim().toLowerCase();
    return (
      layouts.find(layout => layout.id.toLowerCase() === normalizedId) || null
    );
  }

  function getDefaultKeyboardLayout(layouts) {
    if (!Array.isArray(layouts) || !layouts.length) {
      return null;
    }

    const configuredId =
      typeof globalThis.MIDI_DEFAULT_KEYBOARD_LAYOUT_ID === 'string'
        ? globalThis.MIDI_DEFAULT_KEYBOARD_LAYOUT_ID.trim()
        : '';

    return findLayoutById(configuredId, layouts) || layouts[0];
  }

  function isBlackKey(note) {
    if (!Number.isFinite(note)) {
      return false;
    }
    const index = ((Math.round(note) % 12) + 12) % 12;
    return BLACK_NOTE_INDEXES.has(index);
  }

  function formatNoteName(note) {
    if (!Number.isFinite(note)) {
      return '';
    }
    const midi = Math.round(note);
    const index = ((midi % 12) + 12) % 12;
    const octave = Math.floor(midi / 12) - 1;
    return `${NOTE_NAMES[index]}${octave}`;
  }

  function clampSelectionStart(note, layout, windowSize) {
    if (!layout) {
      return 0;
    }

    const lowest = Number(layout.lowestNote);
    const highest = Number(layout.highestNote);

    if (!Number.isFinite(lowest) || !Number.isFinite(highest)) {
      return 0;
    }

    if (highest <= lowest) {
      return lowest;
    }

    const size = Number.isFinite(windowSize) && windowSize > 0
      ? Math.round(windowSize)
      : 24;
    const maxStart = Math.max(lowest, highest - size + 1);

    let candidate = Number.isFinite(note) ? Math.round(note) : lowest;
    if (candidate < lowest) {
      candidate = lowest;
    } else if (candidate > maxStart) {
      candidate = maxStart;
    }

    if (isBlackKey(candidate)) {
      candidate -= 1;
    }

    if (candidate < lowest) {
      candidate = lowest;
    }

    while (candidate > lowest && isBlackKey(candidate)) {
      candidate -= 1;
    }

    if (candidate + size - 1 > highest) {
      candidate = highest - size + 1;
      while (candidate > lowest && isBlackKey(candidate)) {
        candidate -= 1;
      }
      if (candidate < lowest) {
        candidate = lowest;
      }
    }

    if (isBlackKey(candidate)) {
      let downward = candidate - 1;
      while (downward >= lowest && isBlackKey(downward)) {
        downward -= 1;
      }
      if (downward >= lowest && !isBlackKey(downward)) {
        candidate = downward;
      } else {
        let upward = candidate + 1;
        const limit = Math.min(maxStart, highest);
        while (upward <= limit && isBlackKey(upward)) {
          upward += 1;
        }
        if (upward <= limit && !isBlackKey(upward)) {
          candidate = upward;
        } else {
          candidate = lowest;
        }
      }
    }

    return candidate;
  }

  function getActiveRange(start, windowSize) {
    const normalizedStart = Number.isFinite(start) ? Math.round(start) : 0;
    const size = Number.isFinite(windowSize) && windowSize > 0
      ? Math.round(windowSize)
      : 24;
    return {
      start: normalizedStart,
      end: normalizedStart + size - 1
    };
  }

  function createKeyElement(note, tone, interactive, activeRange) {
    const element = document.createElement(interactive ? 'button' : 'div');
    element.className = `midi-key midi-key--${tone}`;
    element.dataset.note = String(Math.round(note));
    element.dataset.noteLabel = formatNoteName(note);

    if (interactive) {
      element.type = 'button';
      const label = formatNoteName(note);
      element.setAttribute('aria-label', label);
      element.title = label;
    } else {
      element.setAttribute('aria-hidden', 'true');
      element.tabIndex = -1;
    }

    if (activeRange && note >= activeRange.start && note <= activeRange.end) {
      element.classList.add('is-active');
    }

    return element;
  }

  function buildKeyboard(container, layout, options = {}) {
    if (!container || !layout) {
      return { keyRefs: new Map() };
    }

    const interactive = Boolean(options.interactive);
    const activeRange = options.activeRange || null;

    container.textContent = '';
    container.classList.toggle('is-interactive', interactive);

    const keyRefs = new Map();
    let note = layout.lowestNote;
    const highest = layout.highestNote;

    while (note <= highest) {
      if (isBlackKey(note)) {
        note += 1;
        continue;
      }

      const slot = document.createElement('div');
      slot.className = 'midi-keyboard__white-slot';

      const whiteKey = createKeyElement(note, 'white', interactive, activeRange);
      slot.appendChild(whiteKey);
      keyRefs.set(Math.round(note), whiteKey);

      if (note + 1 <= highest && isBlackKey(note + 1)) {
        const blackNote = note + 1;
        const blackKey = createKeyElement(blackNote, 'black', interactive, activeRange);
        slot.appendChild(blackKey);
        keyRefs.set(Math.round(blackNote), blackKey);
        note += 1;
      }

      container.appendChild(slot);
      note += 1;
    }

    return { keyRefs };
  }

  function formatLayoutFallbackLabel(layout) {
    if (!layout) {
      return '';
    }
    const start = formatNoteName(layout.lowestNote);
    const end = formatNoteName(layout.highestNote);
    return `${layout.keyCount} touches (${start} – ${end})`;
  }

  function populateLayoutSelect(select, layouts) {
    if (!select) {
      return;
    }

    select.innerHTML = '';
    layouts.forEach((layout) => {
      const option = document.createElement('option');
      option.value = layout.id;
      option.dataset.i18n = `midi.keyboard.layoutOptions.${layout.id}`;
      option.textContent = formatLayoutFallbackLabel(layout);
      select.appendChild(option);
    });
  }

  function extractNoteFromEvent(event) {
    if (!event) {
      return null;
    }
    const key = event.target?.closest?.('.midi-key');
    if (!key || !key.dataset || typeof key.dataset.note !== 'string') {
      return null;
    }
    const note = Number.parseInt(key.dataset.note, 10);
    return Number.isFinite(note) ? note : null;
  }

  function resolveNoteFromPointer(event) {
    const direct = extractNoteFromEvent(event);
    if (direct != null) {
      return direct;
    }

    if (
      typeof document === 'undefined'
      || typeof event?.clientX !== 'number'
      || typeof event?.clientY !== 'number'
    ) {
      return null;
    }

    const element = document.elementFromPoint(event.clientX, event.clientY);
    if (!element) {
      return null;
    }

    const key = element.closest('.midi-key');
    if (!key || typeof key.dataset.note !== 'string') {
      return null;
    }

    const note = Number.parseInt(key.dataset.note, 10);
    return Number.isFinite(note) ? note : null;
  }


  function setupKeyboardUI() {
    const root = typeof document !== 'undefined'
      ? document.getElementById('midiKeyboardArea')
      : null;
    if (!root) {
      return;
    }

    const layoutSelect = root.querySelector('#midiKeyboardLayoutSelect');
    const fullContainer = root.querySelector('#midiKeyboardFull');
    const miniContainer = root.querySelector('#midiKeyboardMini');
    const rangeValue = root.querySelector('#midiKeyboardRangeValue');

    if (!layoutSelect || !fullContainer || !miniContainer || !rangeValue) {
      return;
    }

    const globalScope = typeof globalThis !== 'undefined'
      ? globalThis
      : (typeof window !== 'undefined' ? window : null);
    let midiPlayer = globalScope && globalScope.atom2universMidiPlayer
      ? globalScope.atom2universMidiPlayer
      : null;
    let detachNoteObserver = null;

    const highlightState = new Map();
    let fullKeyRefs = new Map();
    let miniKeyRefs = new Map();
    const pointerNotes = new Map();
    const keyboardNotes = new Map();

    function getKeyElements(noteNumber) {
      const elements = [];
      const fullKey = fullKeyRefs.get(noteNumber);
      if (fullKey) {
        elements.push(fullKey);
      }
      const miniKey = miniKeyRefs.get(noteNumber);
      if (miniKey) {
        elements.push(miniKey);
      }
      return elements;
    }

    function updateKeyHighlight(noteNumber) {
      const stats = highlightState.get(noteNumber) || { manual: 0, playback: 0 };
      const manualCount = Math.max(0, stats.manual || 0);
      const playbackCount = Math.max(0, stats.playback || 0);
      const total = manualCount + playbackCount;
      const elements = getKeyElements(noteNumber);
      elements.forEach((element) => {
        if (!element) {
          return;
        }
        if (total > 0) {
          element.classList.add('is-playing');
        } else {
          element.classList.remove('is-playing');
        }
        if (manualCount > 0) {
          element.classList.add('is-playing--manual');
        } else {
          element.classList.remove('is-playing--manual');
        }
        if (playbackCount > 0) {
          element.classList.add('is-playing--playback');
        } else {
          element.classList.remove('is-playing--playback');
        }
      });
      if (total <= 0) {
        highlightState.delete(noteNumber);
      }
    }

    function recordNoteStart(noteNumber, source) {
      if (!Number.isFinite(noteNumber)) {
        return;
      }
      const normalized = Math.round(noteNumber);
      const stats = highlightState.get(normalized) || { manual: 0, playback: 0 };
      if (!highlightState.has(normalized)) {
        highlightState.set(normalized, stats);
      }
      if (source === 'manual') {
        stats.manual += 1;
      } else {
        stats.playback += 1;
      }
      updateKeyHighlight(normalized);
    }

    function recordNoteStop(noteNumber, source) {
      if (!Number.isFinite(noteNumber)) {
        return;
      }
      const normalized = Math.round(noteNumber);
      const stats = highlightState.get(normalized);
      if (!stats) {
        return;
      }
      if (source === 'manual') {
        stats.manual = Math.max(0, stats.manual - 1);
      } else {
        stats.playback = Math.max(0, stats.playback - 1);
      }
      updateKeyHighlight(normalized);
    }

    function refreshActiveHighlights() {
      highlightState.forEach((_, note) => {
        updateKeyHighlight(note);
      });
    }

    function handleNoteOn(detail) {
      const noteNumber = Number.isFinite(detail?.note) ? detail.note : null;
      if (noteNumber == null) {
        return;
      }
      const source = detail?.source === 'manual' ? 'manual' : 'playback';
      recordNoteStart(noteNumber, source);
    }

    function handleNoteOff(detail) {
      const noteNumber = Number.isFinite(detail?.note) ? detail.note : null;
      if (noteNumber == null) {
        return;
      }
      const source = detail?.source === 'manual' ? 'manual' : 'playback';
      recordNoteStop(noteNumber, source);
    }

    function attachPlayer(player) {
      if (player === midiPlayer) {
        return;
      }
      if (detachNoteObserver) {
        detachNoteObserver();
        detachNoteObserver = null;
      }
      midiPlayer = player;
      if (player && typeof player.registerNoteObserver === 'function') {
        detachNoteObserver = player.registerNoteObserver({
          onNoteOn: handleNoteOn,
          onNoteOff: handleNoteOff,
        });
      }
    }

    if (midiPlayer) {
      attachPlayer(midiPlayer);
    }
    if (globalScope && typeof globalScope.addEventListener === 'function') {
      globalScope.addEventListener('atom2univers:midiPlayerReady', (event) => {
        const player = event?.detail?.player
          || (globalScope && globalScope.atom2universMidiPlayer)
          || null;
        if (player) {
          attachPlayer(player);
        }
      });
    }

    const windowSize = getKeyboardWindowSize();
    const layouts = getConfiguredKeyboardLayouts();
    if (!layouts.length) {
      return;
    }

    populateLayoutSelect(layoutSelect, layouts);

    const defaultLayout = getDefaultKeyboardLayout(layouts);
    if (!defaultLayout) {
      return;
    }

    const midpoint = Math.round((defaultLayout.lowestNote + defaultLayout.highestNote) / 2);
    const tentativeStart = midpoint - Math.floor(windowSize / 2);
    const initialStart = clampSelectionStart(tentativeStart, defaultLayout, windowSize);

    const state = {
      layout: defaultLayout,
      selectionStart: initialStart
    };

    layoutSelect.value = state.layout.id;

    const api = getI18n();
    if (api && typeof api.updateTranslations === 'function') {
      api.updateTranslations(layoutSelect);
    }

    let pointerActive = false;
    let pointerId = null;

    function updateRangeLabel() {
      const activeRange = getActiveRange(state.selectionStart, windowSize);
      const end = Math.min(state.layout.highestNote, activeRange.end);
      const startName = formatNoteName(activeRange.start);
      const endName = formatNoteName(end);
      rangeValue.textContent = `${startName} – ${endName}`;
    }

    function updateFullSelectionHighlight() {
      const activeRange = getActiveRange(state.selectionStart, windowSize);
      fullKeyRefs.forEach((element, note) => {
        if (!element) {
          return;
        }
        if (note >= activeRange.start && note <= activeRange.end) {
          element.classList.add('is-active');
        } else {
          element.classList.remove('is-active');
        }
      });
    }

    function renderMiniKeyboard() {
      const activeRange = getActiveRange(state.selectionStart, windowSize);
      const end = Math.min(state.layout.highestNote, activeRange.end);
      const miniLayout = {
        id: 'window',
        lowestNote: activeRange.start,
        highestNote: end,
        keyCount: Math.max(1, end - activeRange.start + 1)
      };
      const result = buildKeyboard(miniContainer, miniLayout, {
        interactive: true,
        activeRange
      });
      miniKeyRefs = result.keyRefs || new Map();
      refreshActiveHighlights();
    }

    function updateView() {
      updateFullSelectionHighlight();
      renderMiniKeyboard();
      updateRangeLabel();
    }

    function renderFullKeyboard() {
      const result = buildKeyboard(fullContainer, state.layout, { interactive: true });
      fullKeyRefs = result.keyRefs || new Map();
      updateFullSelectionHighlight();
      refreshActiveHighlights();
    }

    function setSelection(note) {
      const nextStart = clampSelectionStart(note, state.layout, windowSize);
      if (nextStart === state.selectionStart) {
        return;
      }
      state.selectionStart = nextStart;
      updateView();
    }

    renderFullKeyboard();
    updateView();

    layoutSelect.addEventListener('change', () => {
      const nextLayout = findLayoutById(layoutSelect.value, layouts) || state.layout;
      state.layout = nextLayout;
      state.selectionStart = clampSelectionStart(state.selectionStart, state.layout, windowSize);
      renderFullKeyboard();
      updateView();
    });

    fullContainer.addEventListener('click', (event) => {
      const note = extractNoteFromEvent(event);
      if (note == null) {
        return;
      }
      setSelection(note);
    });

    fullContainer.addEventListener('pointerdown', (event) => {
      if (typeof event.button === 'number' && event.button !== 0) {
        return;
      }
      const note = resolveNoteFromPointer(event);
      if (note == null) {
        return;
      }
      event.preventDefault();
      pointerActive = true;
      pointerId = event.pointerId;
      if (typeof fullContainer.setPointerCapture === 'function') {
        try {
          fullContainer.setPointerCapture(pointerId);
        } catch (error) {
          console.warn('Unable to capture pointer for keyboard interaction', error);
        }
      }
      setSelection(note);
    });

    fullContainer.addEventListener('pointermove', (event) => {
      if (!pointerActive) {
        return;
      }
      const note = resolveNoteFromPointer(event);
      if (note == null) {
        return;
      }
      setSelection(note);
    });

    function stopPointerInteraction() {
      if (pointerActive && pointerId != null && typeof fullContainer.releasePointerCapture === 'function') {
        try {
          fullContainer.releasePointerCapture(pointerId);
        } catch (error) {
          console.warn('Unable to release pointer capture', error);
        }
      }
      pointerActive = false;
      pointerId = null;
    }

    fullContainer.addEventListener('pointerup', stopPointerInteraction);
    fullContainer.addEventListener('pointerleave', stopPointerInteraction);
    fullContainer.addEventListener('pointercancel', stopPointerInteraction);

    function startPointerNote(pointer, midiNote) {
      if (!midiPlayer || typeof midiPlayer.playManualNote !== 'function') {
        return;
      }
      const playPromise = Promise.resolve(
        midiPlayer.playManualNote(midiNote, { velocity: 0.78 })
      ).catch((error) => {
        console.warn('Unable to start manual note', error);
        return null;
      });
      pointerNotes.set(pointer, { note: midiNote, handlePromise: playPromise });
    }

    async function stopPointerNote(pointer) {
      const state = pointerNotes.get(pointer);
      if (!state) {
        return;
      }
      pointerNotes.delete(pointer);
      try {
        const handle = await state.handlePromise;
        if (handle && midiPlayer && typeof midiPlayer.stopManualNote === 'function') {
          midiPlayer.stopManualNote(handle);
        }
      } catch (error) {
        console.warn('Unable to stop manual note', error);
      }
    }

    function releaseMiniPointer(pointer) {
      if (typeof miniContainer.releasePointerCapture === 'function') {
        try {
          miniContainer.releasePointerCapture(pointer);
        } catch (error) {
          console.warn('Unable to release mini keyboard pointer', error);
        }
      }
    }

    miniContainer.addEventListener('pointerdown', (event) => {
      if (typeof event.button === 'number' && event.button !== 0) {
        return;
      }
      if (!midiPlayer || typeof midiPlayer.playManualNote !== 'function') {
        return;
      }
      const note = extractNoteFromEvent(event);
      if (note == null) {
        return;
      }
      event.preventDefault();
      const midiNote = Math.max(0, Math.min(127, Math.round(note)));
      startPointerNote(event.pointerId, midiNote);
      if (typeof miniContainer.setPointerCapture === 'function') {
        try {
          miniContainer.setPointerCapture(event.pointerId);
        } catch (error) {
          console.warn('Unable to capture mini keyboard pointer', error);
        }
      }
    });

    miniContainer.addEventListener('pointermove', (event) => {
      const state = pointerNotes.get(event.pointerId);
      if (!state) {
        return;
      }
      const note = resolveNoteFromPointer(event);
      if (note == null) {
        return;
      }
      const midiNote = Math.max(0, Math.min(127, Math.round(note)));
      if (midiNote === state.note) {
        return;
      }
      stopPointerNote(event.pointerId).then(() => {
        startPointerNote(event.pointerId, midiNote);
      });
    });

    miniContainer.addEventListener('pointerup', (event) => {
      releaseMiniPointer(event.pointerId);
      stopPointerNote(event.pointerId);
    });

    miniContainer.addEventListener('pointercancel', (event) => {
      releaseMiniPointer(event.pointerId);
      stopPointerNote(event.pointerId);
    });

    miniContainer.addEventListener('lostpointercapture', (event) => {
      stopPointerNote(event.pointerId);
    });

    function startKeyboardNote(target, midiNote) {
      if (!target || keyboardNotes.has(target)) {
        return;
      }
      if (!midiPlayer || typeof midiPlayer.playManualNote !== 'function') {
        return;
      }
      const playPromise = Promise.resolve(
        midiPlayer.playManualNote(midiNote, { velocity: 0.72 })
      ).catch((error) => {
        console.warn('Unable to start manual note', error);
        return null;
      });
      keyboardNotes.set(target, { note: midiNote, handlePromise: playPromise });
    }

    async function stopKeyboardNote(target) {
      const state = keyboardNotes.get(target);
      if (!state) {
        return;
      }
      keyboardNotes.delete(target);
      try {
        const handle = await state.handlePromise;
        if (handle && midiPlayer && typeof midiPlayer.stopManualNote === 'function') {
          midiPlayer.stopManualNote(handle);
        }
      } catch (error) {
        console.warn('Unable to stop manual note', error);
      }
    }

    miniContainer.addEventListener('keydown', (event) => {
      if (event.repeat) {
        return;
      }
      if (event.code !== 'Space' && event.code !== 'Enter') {
        return;
      }
      if (!midiPlayer || typeof midiPlayer.playManualNote !== 'function') {
        return;
      }
      const note = extractNoteFromEvent(event);
      if (note == null) {
        return;
      }
      event.preventDefault();
      const midiNote = Math.max(0, Math.min(127, Math.round(note)));
      startKeyboardNote(event.target, midiNote);
    });

    miniContainer.addEventListener('keyup', (event) => {
      if (event.code !== 'Space' && event.code !== 'Enter') {
        return;
      }
      event.preventDefault();
      stopKeyboardNote(event.target);
    });

    miniContainer.addEventListener('blur', (event) => {
      stopKeyboardNote(event.target);
    }, true);
  }
  function initialize() {
    const initialLanguage = getInitialLanguage();
    applyLanguage(initialLanguage)
      .catch(() => {})
      .finally(() => {
        setupKeyboardUI();
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }
})();
