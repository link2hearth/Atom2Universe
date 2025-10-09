(function initMidiModule() {
  'use strict';

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
    const registerLane = typeof options.onLaneCreated === 'function'
      ? options.onLaneCreated
      : null;

    container.textContent = '';
    container.classList.toggle('is-interactive', interactive);

    const keyRefs = new Map();
    const laneRefs = new Map();
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
      const whiteLane = document.createElement('div');
      whiteLane.className = 'midi-key__lane';
      whiteLane.dataset.note = String(Math.round(note));
      whiteKey.appendChild(whiteLane);
      slot.appendChild(whiteKey);
      keyRefs.set(Math.round(note), whiteKey);
      laneRefs.set(Math.round(note), whiteLane);
      if (registerLane) {
        registerLane(whiteLane, { note: Math.round(note) });
      }

      if (note + 1 <= highest && isBlackKey(note + 1)) {
        const blackNote = note + 1;
        const blackKey = createKeyElement(blackNote, 'black', interactive, activeRange);
        const blackLane = document.createElement('div');
        blackLane.className = 'midi-key__lane';
        blackLane.dataset.note = String(Math.round(blackNote));
        blackKey.appendChild(blackLane);
        slot.appendChild(blackKey);
        keyRefs.set(Math.round(blackNote), blackKey);
        laneRefs.set(Math.round(blackNote), blackLane);
        if (registerLane) {
          registerLane(blackLane, { note: Math.round(blackNote) });
        }
        note += 1;
      }

      container.appendChild(slot);
      note += 1;
    }

    return { keyRefs, laneRefs };
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
    const windowSizeSelect = root.querySelector('#midiKeyboardOctaveSelect');
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
    const previewEntries = new Map();
    let fullKeyRefs = new Map();
    let fullLaneRefs = new Map();
    let miniKeyRefs = new Map();
    let miniLaneRefs = new Map();
    const pointerNotes = new Map();
    const keyboardNotes = new Map();

    const laneMetrics = new WeakMap();
    const laneObserver = typeof ResizeObserver !== 'undefined'
      ? new ResizeObserver((entries) => {
        entries.forEach((entry) => {
          const lane = entry?.target;
          if (!lane) {
            return;
          }
          const rectHeight = Number.isFinite(entry?.contentRect?.height)
            ? entry.contentRect.height
            : null;
          const measuredHeight = Math.max(
            0,
            rectHeight != null
              ? rectHeight
              : (lane.clientHeight || lane.offsetHeight || 0)
          );
          laneMetrics.set(lane, { height: measuredHeight });
        });
        refreshPreviewLanes();
      })
      : null;
    let resizeScheduled = false;

    function measureLaneHeight(lane) {
      if (!lane) {
        return 0;
      }
      const height = Math.max(0, lane.clientHeight || lane.offsetHeight || 0);
      laneMetrics.set(lane, { height });
      return height;
    }

    function getLaneHeight(lane) {
      if (!lane) {
        return 0;
      }
      const cached = laneMetrics.get(lane);
      if (cached && Number.isFinite(cached.height)) {
        return cached.height;
      }
      return measureLaneHeight(lane);
    }

    function registerLaneObserver(lane) {
      if (!lane) {
        return;
      }
      measureLaneHeight(lane);
      if (laneObserver) {
        try {
          laneObserver.observe(lane);
        } catch (error) {
          console.warn('Unable to observe MIDI keyboard lane', error);
        }
      }
    }

    function unregisterLaneMap(laneMap) {
      if (!laneMap || typeof laneMap.forEach !== 'function') {
        return;
      }
      laneMap.forEach((lane) => {
        if (!lane) {
          return;
        }
        if (laneObserver) {
          try {
            laneObserver.unobserve(lane);
          } catch (error) {
            console.warn('Unable to stop observing MIDI keyboard lane', error);
          }
        }
        laneMetrics.delete(lane);
      });
    }

    if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
      window.addEventListener('resize', () => {
        if (resizeScheduled) {
          return;
        }
        resizeScheduled = true;
        const schedule = typeof window.requestAnimationFrame === 'function'
          ? window.requestAnimationFrame
          : (handler) => window.setTimeout(handler, 16);
        schedule(() => {
          resizeScheduled = false;
          refreshPreviewLanes();
        });
      });
    }

    function clampOctaveCount(value) {
      const numeric = Number(value);
      if (!Number.isFinite(numeric)) {
        return 1;
      }
      const rounded = Math.round(numeric);
      if (rounded < 1) {
        return 1;
      }
      if (rounded > 5) {
        return 5;
      }
      return rounded;
    }

    function computeWindowSizeForLayout(octaves, layout) {
      const clampedOctaves = clampOctaveCount(octaves);
      const requested = Math.max(12, clampedOctaves * 12);
      if (!layout) {
        return requested;
      }
      const lowest = Number(layout.lowestNote);
      const highest = Number(layout.highestNote);
      if (!Number.isFinite(lowest) || !Number.isFinite(highest)) {
        return requested;
      }
      const layoutRange = Math.max(1, Math.round(highest - lowest + 1));
      if (layoutRange < 12) {
        return layoutRange;
      }
      return Math.min(requested, layoutRange);
    }

    function getOctaveCountFromWindowSize(size) {
      if (!Number.isFinite(size) || size <= 0) {
        return 1;
      }
      return clampOctaveCount(Math.round(size / 12));
    }

    function syncOctaveSelectFromWindowSize(size) {
      if (!windowSizeSelect) {
        return;
      }
      const value = String(getOctaveCountFromWindowSize(size));
      if (windowSizeSelect.value !== value) {
        windowSizeSelect.value = value;
      }
    }

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

    function getKeyLanes(noteNumber) {
      const lanes = [];
      const fullLane = fullLaneRefs.get(noteNumber);
      if (fullLane) {
        lanes.push(fullLane);
      }
      const miniLane = miniLaneRefs.get(noteNumber);
      if (miniLane) {
        lanes.push(miniLane);
      }
      return lanes;
    }

    function destroyPreviewBars(entry, options = {}) {
      if (!entry || !Array.isArray(entry.bars)) {
        return;
      }
      const immediate = options.immediate === true;
      entry.bars.forEach((state) => {
        if (!state || !state.element) {
          return;
        }
        if (typeof state.cleanup === 'function') {
          try {
            state.cleanup();
          } catch (error) {
            console.warn('Unable to clean MIDI preview listeners', error);
          }
        }
        const element = state.element;
        state.cleanup = null;
        state.element = null;
        state.lane = null;
        state.height = null;
        state.travel = null;
        state.animationDuration = null;
        if (immediate || typeof window === 'undefined') {
          element.remove();
          return;
        }
        element.classList.add('is-finishing');
        window.setTimeout(() => {
          element.remove();
        }, 220);
      });
      entry.bars = [];
    }

    function computePreviewBarHeight(laneHeight, sustainSeconds) {
      const duration = Math.max(0.08, Number.isFinite(sustainSeconds) ? sustainSeconds : 0.08);
      if (laneHeight <= 0) {
        return Math.max(12, Math.min(140, duration * 60));
      }
      const durationRatio = Math.max(0.18, Math.min(0.88, duration / 3.2));
      return Math.max(10, Math.min(laneHeight * 0.85, laneHeight * (0.24 + durationRatio)));
    }

    function createPreviewBar(entry, lane, previewDuration) {
      if (!lane || !entry) {
        return null;
      }

      const doc = lane.ownerDocument || document;
      const bar = doc.createElement('div');
      bar.className = 'midi-key__preview';
      bar.dataset.eventId = entry.id;

      const labelText = formatNoteName(entry.note);
      if (labelText) {
        bar.dataset.noteLabel = labelText;
        const label = doc.createElement('span');
        label.className = 'midi-key__preview-label';
        label.textContent = labelText;
        label.setAttribute('aria-hidden', 'true');
        bar.appendChild(label);
      } else {
        bar.removeAttribute('data-note-label');
      }

      const laneHeight = getLaneHeight(lane);
      const barHeight = computePreviewBarHeight(laneHeight, entry.sustainSeconds);
      const travelDistance = laneHeight > 0
        ? laneHeight
        : Math.max(barHeight * 2.2, 48);

      bar.style.height = `${barHeight}px`;
      bar.style.setProperty('--midi-preview-height', `${barHeight}px`);
      bar.style.setProperty('--midi-preview-travel', `${travelDistance}px`);
      bar.style.transform = 'translateY(0)';

      lane.appendChild(bar);

      const state = {
        element: bar,
        lane,
        travel: travelDistance,
        height: barHeight,
        cleanup: null,
      };

      if (previewDuration > 0) {
        const duration = Math.max(0.05, previewDuration);
        bar.style.setProperty('--midi-preview-duration', `${duration}s`);
        state.animationDuration = duration;
        const handleAnimationEnd = () => {
          bar.classList.remove('is-animating');
          bar.classList.add('is-landed');
          bar.style.transform = `translateY(${travelDistance}px)`;
        };
        bar.addEventListener('animationend', handleAnimationEnd, { once: true });
        state.cleanup = () => {
          bar.removeEventListener('animationend', handleAnimationEnd);
        };
        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
          window.requestAnimationFrame(() => {
            bar.classList.add('is-active');
            bar.classList.add('is-animating');
          });
        } else {
          bar.classList.add('is-active');
          bar.classList.add('is-animating');
        }
      } else {
        bar.classList.add('is-active');
        bar.classList.add('is-landed');
        bar.style.transform = `translateY(${travelDistance}px)`;
      }

      return state;
    }

    function buildPreviewBars(entry) {
      if (!entry) {
        return;
      }
      destroyPreviewBars(entry, { immediate: true });
      const lanes = getKeyLanes(entry.note);
      if (!lanes.length) {
        entry.bars = [];
        return;
      }

      const previewDuration = entry.highlightStarted ? 0 : entry.previewLead;

      entry.bars = lanes
        .map((lane) => createPreviewBar(entry, lane, previewDuration))
        .filter(Boolean);
    }

    function settlePreviewBar(state) {
      if (!state || !state.element) {
        return;
      }
      const element = state.element;
      if (typeof state.cleanup === 'function') {
        try {
          state.cleanup();
        } catch (error) {
          console.warn('Unable to settle MIDI preview animation', error);
        }
      }
      state.cleanup = null;
      state.animationDuration = null;
      element.classList.add('is-active');
      element.classList.add('is-landed');
      element.classList.remove('is-animating');
      element.style.removeProperty('animation-name');
      element.style.removeProperty('animation-duration');
      element.style.removeProperty('animation-delay');
      element.style.removeProperty('--midi-preview-duration');
      if (Number.isFinite(state.travel)) {
        element.style.transform = `translateY(${state.travel}px)`;
      } else {
        element.style.transform = 'translateY(0)';
      }
    }

    function refreshPreviewLanes() {
      previewEntries.forEach((entry) => {
        buildPreviewBars(entry);
      });
    }

    function startPreviewHighlight(entry) {
      if (!entry || entry.highlightStarted) {
        return;
      }
      entry.highlightStarted = true;
      if (!entry.bars || entry.bars.length === 0) {
        if (entry.source === 'playback') {
          buildPreviewBars(entry);
        }
      }
      entry.bars.forEach((state) => {
        settlePreviewBar(state);
      });
      recordNoteStart(entry.note, entry.source, { velocity: entry.velocity });
    }

    function finalizePreviewEntry(entry, options = {}) {
      if (!entry) {
        return;
      }
      if (entry.highlightTimer && typeof window !== 'undefined') {
        window.clearTimeout(entry.highlightTimer);
      }
      entry.highlightTimer = null;
      if (entry.highlightStarted) {
        recordNoteStop(entry.note, entry.source);
      }
      destroyPreviewBars(entry, options);
      previewEntries.delete(entry.id);
    }

    function createPreviewEntry(detail, noteNumber, source) {
      const normalizedNote = Math.round(noteNumber);
      const previewLead = Math.max(0, Number.isFinite(detail?.previewLeadTime) ? detail.previewLeadTime : 0);
      const sustainSeconds = Math.max(0.08, Number.isFinite(detail?.durationSeconds) ? detail.durationSeconds : 0.08);
      const eventId = typeof detail?.id === 'string' && detail.id ? detail.id : `preview-${normalizedNote}-${Date.now()}`;

      if (previewEntries.has(eventId)) {
        finalizePreviewEntry(previewEntries.get(eventId), { immediate: true });
      }

      const entry = {
        id: eventId,
        note: normalizedNote,
        source,
        velocity: Number.isFinite(detail?.velocity) ? detail.velocity : null,
        previewLead,
        sustainSeconds,
        bars: [],
        highlightTimer: null,
        highlightStarted: false,
      };

      previewEntries.set(eventId, entry);

      if (previewLead > 0) {
        buildPreviewBars(entry);
        if (typeof window !== 'undefined') {
          const timerId = window.setTimeout(() => {
            entry.highlightTimer = null;
            if (!previewEntries.has(entry.id)) {
              return;
            }
            startPreviewHighlight(entry);
          }, Math.max(0, Math.round(previewLead * 1000)));
          entry.highlightTimer = timerId;
        } else {
          startPreviewHighlight(entry);
        }
      } else {
        startPreviewHighlight(entry);
      }

      return entry;
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
          if (Number.isFinite(stats.manualBrightness)) {
            element.style.setProperty('--midi-key-manual-brightness', stats.manualBrightness);
          }
        } else {
          element.classList.remove('is-playing--manual');
          element.style.removeProperty('--midi-key-manual-brightness');
        }
        if (playbackCount > 0) {
          element.classList.add('is-playing--playback');
          if (Number.isFinite(stats.playbackBrightness)) {
            element.style.setProperty('--midi-key-playback-brightness', stats.playbackBrightness);
          }
        } else {
          element.classList.remove('is-playing--playback');
          element.style.removeProperty('--midi-key-playback-brightness');
        }
      });
      if (total <= 0) {
        highlightState.delete(noteNumber);
      }
    }

    function computeBrightnessFromVelocity(velocity, options = {}) {
      const base = Number.isFinite(velocity) ? Math.max(0, Math.min(1, velocity)) : null;
      const fallback = Number.isFinite(options.fallback) ? options.fallback : 0.75;
      const multiplier = Number.isFinite(options.multiplier) ? options.multiplier : 0.35;
      const offset = Number.isFinite(options.offset) ? options.offset : 1;
      const normalized = base != null ? base : fallback;
      const brightness = offset + (normalized * multiplier);
      return Math.max(0.6, Math.min(1.8, brightness));
    }

    function recordNoteStart(noteNumber, source, detail = {}) {
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
        stats.manualBrightness = computeBrightnessFromVelocity(detail.velocity, {
          fallback: 0.78,
          multiplier: 0.42,
          offset: 1,
        });
      } else {
        stats.playback += 1;
        stats.playbackBrightness = computeBrightnessFromVelocity(detail.velocity, {
          fallback: 0.7,
          multiplier: 0.38,
          offset: 1,
        });
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
        if (stats.manual <= 0) {
          delete stats.manualBrightness;
        }
      } else {
        stats.playback = Math.max(0, stats.playback - 1);
        if (stats.playback <= 0) {
          delete stats.playbackBrightness;
        }
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
      const entry = createPreviewEntry(detail, noteNumber, source);
      if (!entry) {
        recordNoteStart(Math.round(noteNumber), source, { velocity: detail?.velocity });
      }
    }

    function handleNoteOff(detail) {
      const noteNumber = Number.isFinite(detail?.note) ? detail.note : null;
      if (noteNumber == null) {
        return;
      }
      const source = detail?.source === 'manual' ? 'manual' : 'playback';
      const eventId = typeof detail?.id === 'string' && detail.id ? detail.id : null;
      if (eventId && previewEntries.has(eventId)) {
        finalizePreviewEntry(previewEntries.get(eventId));
      } else {
        recordNoteStop(Math.round(noteNumber), source);
      }
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

    const configuredWindowSize = getKeyboardWindowSize();
    let requestedOctaves = clampOctaveCount(Math.round(configuredWindowSize / 12));
    const layouts = getConfiguredKeyboardLayouts();
    if (!layouts.length) {
      return;
    }

    populateLayoutSelect(layoutSelect, layouts);

    const defaultLayout = getDefaultKeyboardLayout(layouts);
    if (!defaultLayout) {
      return;
    }

    const initialWindowSize = computeWindowSizeForLayout(requestedOctaves, defaultLayout);
    requestedOctaves = getOctaveCountFromWindowSize(initialWindowSize);

    const midpoint = Math.round((defaultLayout.lowestNote + defaultLayout.highestNote) / 2);
    const tentativeStart = midpoint - Math.floor(initialWindowSize / 2);
    const initialStart = clampSelectionStart(tentativeStart, defaultLayout, initialWindowSize);

    const state = {
      layout: defaultLayout,
      selectionStart: initialStart,
      windowSize: initialWindowSize
    };

    syncOctaveSelectFromWindowSize(state.windowSize);
    layoutSelect.value = state.layout.id;

    const api = getI18n();
    if (api && typeof api.updateTranslations === 'function') {
      api.updateTranslations(layoutSelect);
      if (windowSizeSelect) {
        api.updateTranslations(windowSizeSelect);
      }
    }

    let pointerActive = false;
    let pointerId = null;

    function updateRangeLabel() {
      const activeRange = getActiveRange(state.selectionStart, state.windowSize);
      const end = Math.min(state.layout.highestNote, activeRange.end);
      const startName = formatNoteName(activeRange.start);
      const endName = formatNoteName(end);
      rangeValue.textContent = `${startName} – ${endName}`;
    }

    function updateFullSelectionHighlight() {
      const activeRange = getActiveRange(state.selectionStart, state.windowSize);
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
      const activeRange = getActiveRange(state.selectionStart, state.windowSize);
      const end = Math.min(state.layout.highestNote, activeRange.end);
      const miniLayout = {
        id: 'window',
        lowestNote: activeRange.start,
        highestNote: end,
        keyCount: Math.max(1, end - activeRange.start + 1)
      };
      unregisterLaneMap(miniLaneRefs);
      const result = buildKeyboard(miniContainer, miniLayout, {
        interactive: true,
        activeRange,
        onLaneCreated: registerLaneObserver,
      });
      miniKeyRefs = result.keyRefs || new Map();
      miniLaneRefs = result.laneRefs || new Map();
      refreshActiveHighlights();
      refreshPreviewLanes();
    }

    function updateView() {
      updateFullSelectionHighlight();
      renderMiniKeyboard();
      updateRangeLabel();
    }

    function renderFullKeyboard() {
      unregisterLaneMap(fullLaneRefs);
      const result = buildKeyboard(fullContainer, state.layout, {
        interactive: true,
        onLaneCreated: registerLaneObserver,
      });
      fullKeyRefs = result.keyRefs || new Map();
      fullLaneRefs = result.laneRefs || new Map();
      updateFullSelectionHighlight();
      refreshActiveHighlights();
      refreshPreviewLanes();
    }

    function setSelection(note) {
      const nextStart = clampSelectionStart(note, state.layout, state.windowSize);
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
      state.windowSize = computeWindowSizeForLayout(requestedOctaves, state.layout);
      requestedOctaves = getOctaveCountFromWindowSize(state.windowSize);
      syncOctaveSelectFromWindowSize(state.windowSize);
      state.selectionStart = clampSelectionStart(state.selectionStart, state.layout, state.windowSize);
      renderFullKeyboard();
      updateView();
    });

    if (windowSizeSelect) {
      windowSizeSelect.addEventListener('change', () => {
        requestedOctaves = clampOctaveCount(windowSizeSelect.value);
        state.windowSize = computeWindowSizeForLayout(requestedOctaves, state.layout);
        requestedOctaves = getOctaveCountFromWindowSize(state.windowSize);
        syncOctaveSelectFromWindowSize(state.windowSize);
        state.selectionStart = clampSelectionStart(state.selectionStart, state.layout, state.windowSize);
        updateView();
      });
    }

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
  function attachNavigationHandlers() {
    const backButton = typeof document !== 'undefined'
      ? document.getElementById('midiBackToOptions')
      : null;
    if (backButton) {
      backButton.addEventListener('click', () => {
        const targetPage = typeof showPage === 'function'
          ? showPage
          : (typeof globalThis !== 'undefined' && typeof globalThis.showPage === 'function'
            ? globalThis.showPage
            : null);
        if (targetPage) {
          targetPage('options');
        } else if (typeof document !== 'undefined') {
          const optionsSection = document.getElementById('options');
          optionsSection?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        if (typeof backButton.blur === 'function') {
          backButton.blur();
        }
      });
    }
  }

  function initialize() {
    setupKeyboardUI();
    attachNavigationHandlers();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }
})();
