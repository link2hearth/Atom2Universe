(function () {
  'use strict';

  function formatTemplate(message, params = {}) {
    if (typeof message !== 'string' || !message) {
      return message;
    }
    const normalizedMessage = message.replace(/\{\{\s*([^{}\s]+)\s*\}\}/g, '{$1}');
    return normalizedMessage.replace(/\{\s*([^\s{}]+)\s*\}/g, (match, token) => {
      if (Object.prototype.hasOwnProperty.call(params, token)) {
        const replacement = params[token];
        return replacement == null ? '' : String(replacement);
      }
      return match;
    });
  }

  const ENGLISH_MESSAGES = new Map([
    ['index.sections.options.chiptune.errors.midiIncompleteVlq', 'Incomplete MIDI frame (VLQ).'],
    ['index.sections.options.chiptune.errors.midiHeaderInvalid', 'Invalid MIDI header.'],
    ['index.sections.options.chiptune.errors.midiHeaderIncomplete', 'Incomplete MIDI header.'],
    ['index.sections.options.chiptune.errors.midiNoTracks', 'The MIDI file contains no tracks.'],
    ['index.sections.options.chiptune.errors.midiMissingStatus', 'Missing MIDI status byte for an event.'],
    ['index.sections.options.chiptune.errors.midiNoUsableTrack', 'No usable MIDI track found.'],
    ['index.sections.options.chiptune.errors.midiNoPlayableNotes', 'The file contains no playable notes.'],
    ['index.sections.options.chiptune.errors.midiResolution', 'Invalid MIDI resolution.'],
    ['index.sections.options.chiptune.errors.soundFontEmpty', 'Empty SoundFont.'],
    ['index.sections.options.chiptune.errors.soundFontHeader', 'Invalid SoundFont header.'],
    ['index.sections.options.chiptune.errors.soundFontFormat', 'Unsupported SoundFont format.'],
    ['index.sections.options.chiptune.errors.soundFontMissingSelection', 'No SoundFont selected.'],
    ['index.sections.options.chiptune.errors.webAudioUnavailable', 'The Web Audio API is not available in this browser.'],
  ]);

  function translateMessage(key, fallback, params = {}) {
    const normalizedKey = typeof key === 'string' ? key.trim() : '';

    if (normalizedKey) {
      const globalScope = typeof globalThis !== 'undefined'
        ? globalThis
        : typeof window !== 'undefined'
          ? window
          : typeof self !== 'undefined'
            ? self
            : typeof global !== 'undefined'
              ? global
              : null;

      if (globalScope) {
        let translator = null;
        if (globalScope.i18n && typeof globalScope.i18n.t === 'function') {
          translator = globalScope.i18n.t.bind(globalScope.i18n);
        } else if (typeof globalScope.t === 'function') {
          translator = globalScope.t.bind(globalScope);
        }

        if (translator) {
          try {
            const translated = translator(normalizedKey, params);
            if (translated != null && translated !== normalizedKey) {
              return translated;
            }
          } catch (error) {
            // Fallback to built-in messages if translation fails
          }
        }
      }
    }

    const baseMessage = normalizedKey && ENGLISH_MESSAGES.has(normalizedKey)
      ? ENGLISH_MESSAGES.get(normalizedKey)
      : fallback;
    if (typeof baseMessage === 'string') {
      return formatTemplate(baseMessage, params);
    }
    if (baseMessage != null) {
      return baseMessage;
    }
    if (normalizedKey) {
      return normalizedKey;
    }
    return '';
  }

  const DEFAULT_NOTE_NAMES = Object.freeze(['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B']);

  const N163_TABLE_LENGTH = 32;
  const N163_WAVE_GENERATORS = {
    default(index, length) {
      const phase = (index / length) * 2 * Math.PI;
      const value = (Math.sin(phase) * 0.72) + (Math.sin(phase * 2) * 0.28);
      return value * 0.85;
    },
    bright(index, length) {
      const phase = index / length;
      const base = (phase < 0.5 ? 1 : -1) * 0.9;
      const bend = Math.sin(phase * 2 * Math.PI) * 0.35;
      return (base * 0.75) + bend;
    },
    hollow(index, length) {
      const phase = (index / length) * 2 * Math.PI;
      return (Math.sin(phase) * 0.55) - (Math.sin(phase * 3) * 0.35);
    },
    reed(index, length) {
      const phase = (index / length) * 2 * Math.PI;
      return (Math.sin(phase) * 0.6) + (Math.sin(phase * 5) * 0.18);
    },
    bell(index, length) {
      const phase = (index / length) * 2 * Math.PI;
      return (Math.sin(phase) * 0.5) + (Math.sin(phase * 7) * 0.32);
    },
    bass(index, length) {
      const phase = index / length;
      const saw = ((phase * 2) % 2) - 1;
      return (saw * 0.75) + (Math.sin(phase * 2 * Math.PI) * 0.25);
    },
    airy(index, length) {
      const phase = (index / length) * 2 * Math.PI;
      return (Math.sin(phase) * 0.4) + (Math.sin(phase * 2) * 0.22) + (Math.sin(phase * 4) * 0.12);
    },
    pulse(index, length) {
      const phase = index / length;
      const duty = 0.31;
      return phase < duty ? 0.85 : -0.85;
    },
    brass(index, length) {
      const phase = (index / length) * 2 * Math.PI;
      return (Math.sin(phase) * 0.7) + (Math.sin(phase * 2) * 0.4) + (Math.sin(phase * 3) * 0.2);
    },
  };

  const quantizeN163Sample = (value) => {
    const steps = 31;
    const normalized = Math.max(-1, Math.min(1, value));
    const scaled = Math.round(((normalized + 1) / 2) * steps);
    return (scaled / steps) * 2 - 1;
  };

  const buildN163Tables = () => {
    const tables = {};
    for (const [name, generator] of Object.entries(N163_WAVE_GENERATORS)) {
      const table = new Float32Array(N163_TABLE_LENGTH);
      for (let i = 0; i < N163_TABLE_LENGTH; i += 1) {
        table[i] = quantizeN163Sample(generator(i, N163_TABLE_LENGTH));
      }
      tables[name] = table;
    }
    return Object.freeze(tables);
  };

  const N163_WAVETABLES = buildN163Tables();

  const DEFAULT_SOUNDFONTS = Object.freeze([
    Object.freeze({
      id: 'GeneralUser-GS',
      name: 'GeneralUser-GS',
      file: 'Assets/Music/GeneralUser-GS.sf2',
      default: false,
    }),
    Object.freeze({
      id: 'Piano',
      name: 'Piano',
      file: 'Assets/Music/Piano.sf2',
      default: true,
    }),
        Object.freeze({
      id: 'Classical_Oboe',
      name: 'Classical_Oboe',
      file: 'Assets/Music/Classical_Oboe.sf2',
      default: false,
    }),
    Object.freeze({
      id: 'merlin',
      name: 'merlin',
      file: 'Assets/Music/merlin.sf2',
      default: false,
    }),
  ]);

  class SoundFont {
    constructor(arrayBuffer) {
      if (!arrayBuffer || !arrayBuffer.byteLength) {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.soundFontEmpty', 'Empty SoundFont.'));
      }
      this.buffer = arrayBuffer;
      this.view = new DataView(arrayBuffer);
      this.sampleData = null;
      this.sampleHeaders = [];
      this.phdr = [];
      this.pbag = [];
      this.pgen = [];
      this.inst = [];
      this.ibag = [];
      this.igen = [];
      this.regionsByPreset = new Map();
      this.bufferCache = new Map();
      this.name = '';
      this.parse();
    }

    readFourCC(offset) {
      if (offset < 0 || offset + 4 > this.view.byteLength) {
        return '';
      }
      let result = '';
      for (let i = 0; i < 4; i += 1) {
        result += String.fromCharCode(this.view.getUint8(offset + i));
      }
      return result;
    }

    readString(offset, length) {
      let result = '';
      for (let i = 0; i < length && offset + i < this.view.byteLength; i += 1) {
        const code = this.view.getUint8(offset + i);
        if (code === 0) {
          break;
        }
        result += String.fromCharCode(code);
      }
      return result.trim();
    }

    parse() {
      if (this.readFourCC(0) !== 'RIFF') {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.soundFontHeader', 'Invalid SoundFont header.'));
      }
      const riffSize = this.view.getUint32(4, true);
      if (this.readFourCC(8) !== 'sfbk') {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.soundFontFormat', 'Unsupported SoundFont format.'));
      }
      const limit = Math.min(this.buffer.byteLength, riffSize + 8);
      let offset = 12;
      while (offset + 8 <= limit) {
        const chunkId = this.readFourCC(offset);
        const chunkSize = this.view.getUint32(offset + 4, true);
        const chunkStart = offset + 8;
        if (chunkId === 'LIST') {
          const listType = this.readFourCC(chunkStart);
          const listStart = chunkStart + 4;
          const listSize = chunkSize - 4;
          if (listType === 'INFO') {
            this.parseInfo(listStart, listSize);
          } else if (listType === 'sdta') {
            this.parseSdta(listStart, listSize);
          } else if (listType === 'pdta') {
            this.parsePdta(listStart, listSize);
          }
        }
        const advance = chunkStart + chunkSize + (chunkSize % 2 ? 1 : 0);
        offset = Math.max(offset + 8, advance);
      }
      this.buildInstrumentZones();
      this.buildPresetRegions();
    }

    parseInfo(start, size) {
      const end = Math.min(this.buffer.byteLength, start + size);
      let offset = start;
      while (offset + 8 <= end) {
        const id = this.readFourCC(offset);
        const chunkSize = this.view.getUint32(offset + 4, true);
        const chunkStart = offset + 8;
        if (id === 'INAM') {
          this.name = this.readString(chunkStart, chunkSize);
        }
        const advance = chunkStart + chunkSize + (chunkSize % 2 ? 1 : 0);
        offset = Math.max(offset + 8, advance);
      }
    }

    parseSdta(start, size) {
      const end = Math.min(this.buffer.byteLength, start + size);
      let offset = start;
      while (offset + 8 <= end) {
        const id = this.readFourCC(offset);
        const chunkSize = this.view.getUint32(offset + 4, true);
        const chunkStart = offset + 8;
        if (id === 'smpl') {
          this.sampleData = new Int16Array(this.buffer, chunkStart, Math.floor(chunkSize / 2));
        }
        const advance = chunkStart + chunkSize + (chunkSize % 2 ? 1 : 0);
        offset = Math.max(offset + 8, advance);
      }
    }

    parsePdta(start, size) {
      const end = Math.min(this.buffer.byteLength, start + size);
      let offset = start;
      while (offset + 8 <= end) {
        const id = this.readFourCC(offset);
        const chunkSize = this.view.getUint32(offset + 4, true);
        const chunkStart = offset + 8;
        switch (id) {
          case 'phdr':
            this.parsePhdr(chunkStart, chunkSize);
            break;
          case 'pbag':
            this.parsePbag(chunkStart, chunkSize);
            break;
          case 'pgen':
            this.parsePgen(chunkStart, chunkSize);
            break;
          case 'inst':
            this.parseInst(chunkStart, chunkSize);
            break;
          case 'ibag':
            this.parseIbag(chunkStart, chunkSize);
            break;
          case 'igen':
            this.parseIgen(chunkStart, chunkSize);
            break;
          case 'shdr':
            this.parseShdr(chunkStart, chunkSize);
            break;
          default:
            break;
        }
        const advance = chunkStart + chunkSize + (chunkSize % 2 ? 1 : 0);
        offset = Math.max(offset + 8, advance);
      }
    }

    parsePhdr(start, size) {
      const count = Math.floor(size / 38);
      const entries = [];
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 38);
        entries.push({
          name: this.readString(base, 20),
          preset: this.view.getUint16(base + 20, true),
          bank: this.view.getUint16(base + 22, true),
          bagIndex: this.view.getUint16(base + 24, true),
        });
      }
      this.phdr = entries;
    }

    parsePbag(start, size) {
      const count = Math.floor(size / 4);
      const entries = new Array(count);
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 4);
        entries[i] = {
          generatorIndex: this.view.getUint16(base, true),
          modulatorIndex: this.view.getUint16(base + 2, true),
        };
      }
      this.pbag = entries;
    }

    parsePgen(start, size) {
      const count = Math.floor(size / 4);
      const entries = new Array(count);
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 4);
        entries[i] = {
          operator: this.view.getUint16(base, true),
          amount: this.view.getInt16(base + 2, true),
        };
      }
      this.pgen = entries;
    }

    parseInst(start, size) {
      const count = Math.floor(size / 22);
      const entries = new Array(count);
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 22);
        entries[i] = {
          name: this.readString(base, 20),
          bagIndex: this.view.getUint16(base + 20, true),
        };
      }
      this.inst = entries;
    }

    parseIbag(start, size) {
      const count = Math.floor(size / 4);
      const entries = new Array(count);
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 4);
        entries[i] = {
          generatorIndex: this.view.getUint16(base, true),
          modulatorIndex: this.view.getUint16(base + 2, true),
        };
      }
      this.ibag = entries;
    }

    parseIgen(start, size) {
      const count = Math.floor(size / 4);
      const entries = new Array(count);
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 4);
        entries[i] = {
          operator: this.view.getUint16(base, true),
          amount: this.view.getInt16(base + 2, true),
        };
      }
      this.igen = entries;
    }

    parseShdr(start, size) {
      const count = Math.floor(size / 46);
      const entries = new Array(count);
      for (let i = 0; i < count; i += 1) {
        const base = start + (i * 46);
        entries[i] = {
          name: this.readString(base, 20),
          start: this.view.getUint32(base + 20, true),
          end: this.view.getUint32(base + 24, true),
          startLoop: this.view.getUint32(base + 28, true),
          endLoop: this.view.getUint32(base + 32, true),
          sampleRate: this.view.getUint32(base + 36, true),
          originalPitch: this.view.getUint8(base + 40),
          pitchCorrection: this.view.getInt8(base + 41),
          sampleLink: this.view.getUint16(base + 42, true),
          sampleType: this.view.getUint16(base + 44, true),
        };
      }
      this.sampleHeaders = entries;
    }

    buildInstrumentZones() {
      this.instrumentZones = [];
      this.instrumentGlobals = [];
      const count = Math.max(0, this.inst.length - 1);
      for (let i = 0; i < count; i += 1) {
        const instrument = this.inst[i];
        const nextInstrument = this.inst[i + 1];
        const zoneStart = instrument ? instrument.bagIndex : 0;
        const zoneEnd = nextInstrument ? nextInstrument.bagIndex : this.ibag.length;
        let globalZone = this.createZoneDefaults();
        const zones = [];
        for (let zoneIndex = zoneStart; zoneIndex < zoneEnd; zoneIndex += 1) {
          const bag = this.ibag[zoneIndex];
          const nextBag = this.ibag[zoneIndex + 1];
          const genStart = bag ? bag.generatorIndex : 0;
          const genEnd = nextBag ? nextBag.generatorIndex : this.igen.length;
          const zoneData = this.collectGeneratorValues(this.igen, genStart, genEnd);
          if (typeof zoneData.sampleID !== 'number') {
            globalZone = this.mergeZoneData(globalZone, zoneData);
            continue;
          }
          const merged = this.mergeZoneData(globalZone, zoneData);
          merged.sampleHeader = this.sampleHeaders[merged.sampleID];
          zones.push(merged);
        }
        this.instrumentGlobals[i] = globalZone;
        this.instrumentZones[i] = zones;
      }
    }

    buildPresetRegions() {
      this.regionsByPreset.clear();
      const count = Math.max(0, this.phdr.length - 1);
      for (let i = 0; i < count; i += 1) {
        const preset = this.phdr[i];
        const nextPreset = this.phdr[i + 1];
        const zoneStart = preset ? preset.bagIndex : 0;
        const zoneEnd = nextPreset ? nextPreset.bagIndex : this.pbag.length;
        let presetGlobal = this.createZoneDefaults();
        const regions = [];
        for (let zoneIndex = zoneStart; zoneIndex < zoneEnd; zoneIndex += 1) {
          const bag = this.pbag[zoneIndex];
          const nextBag = this.pbag[zoneIndex + 1];
          const genStart = bag ? bag.generatorIndex : 0;
          const genEnd = nextBag ? nextBag.generatorIndex : this.pgen.length;
          const zoneData = this.collectGeneratorValues(this.pgen, genStart, genEnd);
          if (typeof zoneData.instrument !== 'number') {
            presetGlobal = this.mergeZoneData(presetGlobal, zoneData);
            continue;
          }
          const instrumentIndex = zoneData.instrument;
          const instrumentZones = this.instrumentZones[instrumentIndex] || [];
          const instrumentGlobal = this.instrumentGlobals[instrumentIndex] || this.createZoneDefaults();
          const presetApplied = this.mergeZoneData(presetGlobal, zoneData);
          for (const instZone of instrumentZones) {
            const combined = this.mergeZoneData(this.mergeZoneData(presetApplied, instrumentGlobal), instZone);
            const region = this.finalizeRegion(combined);
            if (region) {
              regions.push(region);
            }
          }
        }
        const key = `${preset.bank}:${preset.preset}`;
        this.regionsByPreset.set(key, regions);
      }
    }

    createZoneDefaults() {
      return {
        keyRange: { lo: 0, hi: 127 },
        velRange: { lo: 0, hi: 127 },
        startOffset: 0,
        endOffset: 0,
        startLoopOffset: 0,
        endLoopOffset: 0,
        coarseTune: 0,
        fineTune: 0,
        scaleTuning: 100,
        attenuation: 0,
        pan: null,
        sampleModes: 0,
        rootKey: null,
        exclusiveClass: 0,
        reverbSend: null,
        chorusSend: null,
        volumeEnvelope: {},
      };
    }

    collectGeneratorValues(generators, start, end) {
      const result = {};
      const limit = Math.min(generators.length, end);
      for (let index = Math.max(0, start); index < limit; index += 1) {
        const entry = generators[index];
        if (!entry) {
          continue;
        }
        const amount = entry.amount;
        switch (entry.operator) {
          case 0:
            result.startOffset = (result.startOffset || 0) + amount;
            break;
          case 1:
            result.endOffset = (result.endOffset || 0) + amount;
            break;
          case 2:
            result.startLoopOffset = (result.startLoopOffset || 0) + amount;
            break;
          case 3:
            result.endLoopOffset = (result.endLoopOffset || 0) + amount;
            break;
          case 4:
            result.startOffset = (result.startOffset || 0) + (amount * 32768);
            break;
          case 12:
            result.endOffset = (result.endOffset || 0) + (amount * 32768);
            break;
          case 45:
            result.startLoopOffset = (result.startLoopOffset || 0) + (amount * 32768);
            break;
          case 50:
            result.endLoopOffset = (result.endLoopOffset || 0) + (amount * 32768);
            break;
          case 15:
            result.chorusSend = amount / 1000;
            break;
          case 16:
            result.reverbSend = amount / 1000;
            break;
          case 17:
            result.pan = amount / 500;
            break;
          case 33:
            result.volumeEnvelope = { ...(result.volumeEnvelope || {}), delay: amount };
            break;
          case 34:
            result.volumeEnvelope = { ...(result.volumeEnvelope || {}), attack: amount };
            break;
          case 35:
            result.volumeEnvelope = { ...(result.volumeEnvelope || {}), hold: amount };
            break;
          case 36:
            result.volumeEnvelope = { ...(result.volumeEnvelope || {}), decay: amount };
            break;
          case 37:
            result.volumeEnvelope = { ...(result.volumeEnvelope || {}), sustain: amount };
            break;
          case 38:
            result.volumeEnvelope = { ...(result.volumeEnvelope || {}), release: amount };
            break;
          case 41:
            result.instrument = amount;
            break;
          case 43:
            result.keyRange = this.decodeRange(amount);
            break;
          case 44:
            result.velRange = this.decodeRange(amount);
            break;
          case 48:
            result.attenuation = (result.attenuation || 0) + amount;
            break;
          case 51:
            result.coarseTune = (result.coarseTune || 0) + amount;
            break;
          case 52:
            result.fineTune = (result.fineTune || 0) + amount;
            break;
          case 53:
            result.sampleID = amount;
            break;
          case 54:
            result.sampleModes = amount;
            break;
          case 56:
            result.scaleTuning = amount;
            break;
          case 57:
            result.exclusiveClass = amount;
            break;
          case 58:
            result.rootKey = amount;
            break;
          default:
            break;
        }
      }
      return result;
    }

    decodeRange(amount) {
      const value = amount & 0xffff;
      return {
        lo: value & 0xff,
        hi: (value >>> 8) & 0xff,
      };
    }

    mergeZoneData(base, source) {
      const target = {
        ...base,
        keyRange: { ...base.keyRange },
        velRange: { ...base.velRange },
        volumeEnvelope: { ...(base.volumeEnvelope || {}) },
      };
      if (source.keyRange) {
        target.keyRange.lo = Math.max(target.keyRange.lo, source.keyRange.lo);
        target.keyRange.hi = Math.min(target.keyRange.hi, source.keyRange.hi);
        if (target.keyRange.lo > target.keyRange.hi) {
          target.keyRange.lo = target.keyRange.hi;
        }
      }
      if (source.velRange) {
        target.velRange.lo = Math.max(target.velRange.lo, source.velRange.lo);
        target.velRange.hi = Math.min(target.velRange.hi, source.velRange.hi);
        if (target.velRange.lo > target.velRange.hi) {
          target.velRange.lo = target.velRange.hi;
        }
      }
      if (Number.isFinite(source.startOffset)) {
        target.startOffset = (target.startOffset || 0) + source.startOffset;
      }
      if (Number.isFinite(source.endOffset)) {
        target.endOffset = (target.endOffset || 0) + source.endOffset;
      }
      if (Number.isFinite(source.startLoopOffset)) {
        target.startLoopOffset = (target.startLoopOffset || 0) + source.startLoopOffset;
      }
      if (Number.isFinite(source.endLoopOffset)) {
        target.endLoopOffset = (target.endLoopOffset || 0) + source.endLoopOffset;
      }
      if (Number.isFinite(source.coarseTune)) {
        target.coarseTune = (target.coarseTune || 0) + source.coarseTune;
      }
      if (Number.isFinite(source.fineTune)) {
        target.fineTune = (target.fineTune || 0) + source.fineTune;
      }
      if (Number.isFinite(source.scaleTuning)) {
        target.scaleTuning = source.scaleTuning;
      }
      if (Number.isFinite(source.attenuation)) {
        target.attenuation = (target.attenuation || 0) + source.attenuation;
      }
      if (Number.isFinite(source.pan)) {
        target.pan = source.pan;
      }
      if (typeof source.sampleModes === 'number') {
        target.sampleModes = source.sampleModes;
      }
      if (Number.isFinite(source.rootKey)) {
        target.rootKey = source.rootKey;
      }
      if (Number.isFinite(source.exclusiveClass)) {
        target.exclusiveClass = source.exclusiveClass;
      }
      if (typeof source.sampleID === 'number') {
        target.sampleID = source.sampleID;
      }
      if (source.sampleHeader) {
        target.sampleHeader = source.sampleHeader;
      }
      if (source.reverbSend !== null && source.reverbSend !== undefined) {
        target.reverbSend = source.reverbSend;
      }
      if (source.chorusSend !== null && source.chorusSend !== undefined) {
        target.chorusSend = source.chorusSend;
      }
      if (source.volumeEnvelope) {
        target.volumeEnvelope = { ...target.volumeEnvelope, ...source.volumeEnvelope };
      }
      if (typeof source.instrument === 'number') {
        target.instrument = source.instrument;
      }
      return target;
    }

    finalizeRegion(zone) {
      if (!this.sampleData || !zone || typeof zone.sampleID !== 'number') {
        return null;
      }
      const sampleHeader = zone.sampleHeader || this.sampleHeaders[zone.sampleID];
      if (!sampleHeader) {
        return null;
      }
      const dataLength = this.sampleData.length;
      const start = this.clampIndex(sampleHeader.start + (zone.startOffset || 0), 0, dataLength - 1);
      const endRaw = sampleHeader.end + (zone.endOffset || 0);
      const end = this.clampIndex(endRaw, start + 1, dataLength);
      const loopStartRaw = sampleHeader.startLoop + (zone.startLoopOffset || 0);
      const loopEndRaw = sampleHeader.endLoop + (zone.endLoopOffset || 0);
      const loopStart = this.clampIndex(loopStartRaw, start, end);
      const loopEnd = this.clampIndex(loopEndRaw, loopStart + 1, end);
      const rootKey = Number.isFinite(zone.rootKey) ? zone.rootKey : sampleHeader.originalPitch;
      const envelope = this.convertEnvelope(zone.volumeEnvelope);
      return {
        keyRange: zone.keyRange,
        velRange: zone.velRange,
        sampleId: zone.sampleID,
        sampleRate: sampleHeader.sampleRate || 44100,
        sampleStart: start,
        sampleEnd: end,
        loopStart,
        loopEnd,
        hasLoop: Boolean((zone.sampleModes & 1) && loopEnd > loopStart + 2),
        rootKey,
        coarseTune: zone.coarseTune || 0,
        fineTune: zone.fineTune || 0,
        scaleTuning: Number.isFinite(zone.scaleTuning) ? zone.scaleTuning : 100,
        pitchCorrection: sampleHeader.pitchCorrection || 0,
        attenuation: zone.attenuation || 0,
        pan: Number.isFinite(zone.pan) ? zone.pan : 0,
        exclusiveClass: zone.exclusiveClass || 0,
        reverbSend: zone.reverbSend,
        chorusSend: zone.chorusSend,
        volumeEnvelope: envelope,
        sampleName: sampleHeader.name || '',
      };
    }

    clampIndex(value, min, max) {
      const clamped = Math.max(min, Math.min(max, value));
      return Number.isFinite(clamped) ? clamped : min;
    }

    convertEnvelope(data) {
      if (!data) {
        return null;
      }
      const envelope = {};
      if (Number.isFinite(data.attack)) {
        const seconds = this.timecentsToSeconds(data.attack);
        if (Number.isFinite(seconds)) {
          envelope.attack = Math.max(0.001, seconds);
        }
      }
      if (Number.isFinite(data.decay)) {
        const seconds = this.timecentsToSeconds(data.decay);
        if (Number.isFinite(seconds)) {
          envelope.decay = Math.max(0.01, seconds);
        }
      }
      if (Number.isFinite(data.release)) {
        const seconds = this.timecentsToSeconds(data.release);
        if (Number.isFinite(seconds)) {
          envelope.release = Math.max(0.02, seconds);
        }
      }
      if (Number.isFinite(data.sustain)) {
        const sustainLevel = Math.pow(10, -data.sustain / 200);
        envelope.sustain = Math.max(0, Math.min(1, sustainLevel));
      }
      return Object.keys(envelope).length ? envelope : null;
    }

    timecentsToSeconds(value) {
      if (!Number.isFinite(value)) {
        return null;
      }
      return Math.pow(2, value / 1200);
    }

    getRegions({ bank = 0, program = 0, key = 60, velocity = 100 } = {}) {
      const normalizedBank = Number.isFinite(bank) ? Math.max(0, Math.min(16383, bank)) : 0;
      const normalizedProgram = Number.isFinite(program) ? Math.max(0, Math.min(127, program)) : 0;
      const presetKey = `${normalizedBank}:${normalizedProgram}`;
      const regions = this.regionsByPreset.get(presetKey) || [];
      const keyValue = Math.max(0, Math.min(127, Number.isFinite(key) ? Math.round(key) : 60));
      const velocityValue = Math.max(0, Math.min(127, Number.isFinite(velocity) ? Math.round(velocity) : 100));
      const matches = [];
      for (const region of regions) {
        if (!region) {
          continue;
        }
        if (keyValue < region.keyRange.lo || keyValue > region.keyRange.hi) {
          continue;
        }
        if (velocityValue < region.velRange.lo || velocityValue > region.velRange.hi) {
          continue;
        }
        matches.push(region);
      }
      return matches;
    }

    getRegionBuffer(audioContext, region) {
      if (!audioContext || !region || !this.sampleData) {
        return null;
      }
      const cacheKey = `${region.sampleId}:${region.sampleStart}:${region.sampleEnd}:${audioContext.sampleRate}`;
      if (this.bufferCache.has(cacheKey)) {
        return this.bufferCache.get(cacheKey);
      }
      const length = Math.max(1, region.sampleEnd - region.sampleStart);
      const buffer = audioContext.createBuffer(1, length, audioContext.sampleRate);
      const channelData = buffer.getChannelData(0);
      const maxSamples = Math.min(length, this.sampleData.length - region.sampleStart);
      for (let i = 0; i < maxSamples; i += 1) {
        const sample = this.sampleData[region.sampleStart + i] || 0;
        channelData[i] = Math.max(-1, Math.min(1, sample / 32768));
      }
      if (maxSamples < length) {
        for (let i = maxSamples; i < length; i += 1) {
          channelData[i] = 0;
        }
      }
      this.bufferCache.set(cacheKey, buffer);
      return buffer;
    }
  }

  class StreamReader {
    constructor(buffer) {
      this.view = new DataView(buffer);
      this.offset = 0;
      this.length = buffer.byteLength;
    }

    readUint8() {
      const value = this.view.getUint8(this.offset);
      this.offset += 1;
      return value;
    }

    readUint16() {
      const value = this.view.getUint16(this.offset);
      this.offset += 2;
      return value;
    }

    readUint32() {
      const value = this.view.getUint32(this.offset);
      this.offset += 4;
      return value;
    }

    readString(length) {
      let result = '';
      for (let i = 0; i < length; i += 1) {
        result += String.fromCharCode(this.readUint8());
      }
      return result;
    }

    readVarLength() {
      let value = 0;
      let byte;
      do {
        if (this.offset >= this.length) {
          throw new Error(translateMessage('index.sections.options.chiptune.errors.midiIncompleteVlq', 'Incomplete MIDI frame (VLQ).'));
        }
        byte = this.readUint8();
        value = (value << 7) | (byte & 0x7f);
      } while (byte & 0x80);
      return value;
    }

    skip(bytes) {
      this.offset = Math.min(this.offset + bytes, this.length);
    }
  }

  class MidiParser {
    static parse(buffer) {
      const reader = new StreamReader(buffer);
      const headerId = reader.readString(4);
      if (headerId !== 'MThd') {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.midiHeaderInvalid', 'Invalid MIDI header.'));
      }
      const headerLength = reader.readUint32();
      if (headerLength < 6) {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.midiHeaderIncomplete', 'Incomplete MIDI header.'));
      }
      const formatType = reader.readUint16();
      const trackCount = reader.readUint16();
      const division = reader.readUint16();
      if (headerLength > 6) {
        reader.skip(headerLength - 6);
      }

      if (trackCount < 1) {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.midiNoTracks', 'The MIDI file contains no tracks.'));
      }

      const tracks = [];
      for (let i = 0; i < trackCount; i += 1) {
        if (reader.offset >= reader.length) {
          break;
        }
        const chunkId = reader.readString(4);
        const chunkLength = reader.readUint32();
        const trackEnd = reader.offset + chunkLength;
        if (chunkId !== 'MTrk') {
          reader.skip(chunkLength);
          continue;
        }

        const events = [];
        let absoluteTicks = 0;
        let runningStatus = null;
        let order = 0;

        while (reader.offset < trackEnd) {
          const delta = reader.readVarLength();
          absoluteTicks += delta;
          if (reader.offset >= trackEnd) {
            break;
          }

          let statusByte = reader.readUint8();
          if (statusByte === 0xff) {
            const type = reader.readUint8();
            const length = reader.readVarLength();
            const data = [];
            for (let j = 0; j < length; j += 1) {
              if (reader.offset >= trackEnd) {
                break;
              }
              data.push(reader.readUint8());
            }
            if (type === 0x2f) {
              break;
            }
            if (type === 0x51 && data.length === 3) {
              const microseconds = (data[0] << 16) | (data[1] << 8) | data[2];
              events.push({
                type: 'tempo',
                microsecondsPerBeat: microseconds,
                ticks: absoluteTicks,
                order,
              });
              order += 1;
            }
            continue;
          }

          if (statusByte === 0xf0 || statusByte === 0xf7) {
            const length = reader.readVarLength();
            reader.skip(length);
            continue;
          }

          let data1;
          if (statusByte < 0x80) {
            if (runningStatus === null) {
              throw new Error(translateMessage('index.sections.options.chiptune.errors.midiMissingStatus', 'Missing MIDI status byte for an event.'));
            }
            data1 = statusByte;
            statusByte = runningStatus;
          } else {
            runningStatus = statusByte;
            data1 = reader.readUint8();
          }

          const eventType = statusByte & 0xf0;
          const channel = statusByte & 0x0f;

        if (eventType === 0xc0 || eventType === 0xd0) {
          // Program change / channel pressure (un seul octet de données)
          events.push({
            type: 'program',
            program: data1,
            channel,
            ticks: absoluteTicks,
            order,
          });
          order += 1;
          continue;
        }

        const data2 = reader.readUint8();

        if (eventType === 0x90) {
          if (data2 === 0) {
            events.push({
              type: 'noteOff',
              note: data1,
              velocity: 0,
              channel,
              ticks: absoluteTicks,
              order,
            });
          } else {
            events.push({
              type: 'noteOn',
              note: data1,
              velocity: data2,
              channel,
              ticks: absoluteTicks,
              order,
            });
          }
          order += 1;
          continue;
        }

        if (eventType === 0x80) {
          events.push({
            type: 'noteOff',
            note: data1,
            velocity: data2,
            channel,
            ticks: absoluteTicks,
            order,
          });
          order += 1;
          continue;
        }

        if (eventType === 0xb0) {
          events.push({
            type: 'control',
            controller: data1,
            value: data2,
            channel,
            ticks: absoluteTicks,
            order,
          });
          order += 1;
          continue;
        }

        // Ignorer les autres évènements canal (pitch bend, aftertouch polyphonique...)
        }

        reader.offset = trackEnd;
        tracks.push({ events });
      }

      if (!tracks.length) {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.midiNoUsableTrack', 'No usable MIDI track found.'));
      }

      const events = tracks
        .flatMap((track) => track.events)
        .sort((a, b) => (a.ticks - b.ticks) || (a.order - b.order));

      return {
        formatType,
        division,
        events,
      };
    }
  }

  class MidiTimeline {
    static fromMidi(parsed) {
      if (!parsed.events.length) {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.midiNoPlayableNotes', 'The file contains no playable notes.'));
      }
      if (!parsed.division) {
        throw new Error(translateMessage('index.sections.options.chiptune.errors.midiResolution', 'Invalid MIDI resolution.'));
      }

      const secondsPerBeatDefault = 500000 / 1e6;
      let secondsPerTick = secondsPerBeatDefault / parsed.division;
      let currentTime = 0;
      let lastTick = 0;
      const activeNotes = new Map();
      const channelStates = new Map();
      const notes = [];

      const getChannelState = (channel) => {
        if (!channelStates.has(channel)) {
          channelStates.set(channel, {
            program: 0,
            volume: 1,
            expression: 1,
            pan: 0,
            reverb: 0.35,
            sustain: false,
            sustainedNotes: new Map(),
          });
        }
        return channelStates.get(channel);
      };

      const registerActiveStack = (channel, note) => {
        const key = `${channel}:${note}`;
        if (!activeNotes.has(key)) {
          activeNotes.set(key, []);
        }
        return activeNotes.get(key);
      };

      const finalizePending = (pending, endTime, minimum = 0.08) => {
        const duration = Math.max(minimum, endTime - pending.time);
        notes.push({
          note: pending.note,
          startTime: pending.time,
          duration,
          velocity: pending.velocity,
          channel: pending.channel,
          program: pending.program,
          pan: pending.pan,
          reverb: pending.reverb,
          channelVolume: pending.channelVolume,
          expression: pending.expression,
          rawVelocity: pending.rawVelocity,
        });
      };

      const releaseSustained = (state, releaseTime) => {
        if (!state || !state.sustainedNotes) {
          return;
        }
        for (const stack of state.sustainedNotes.values()) {
          while (stack.length) {
            const entry = stack.shift();
            const targetEnd = Math.max(releaseTime, entry.offTime + 0.05);
            finalizePending(entry.pending, targetEnd, 0.1);
          }
        }
        state.sustainedNotes.clear();
      };

      const flushRemainingNotes = (fallback) => {
        for (const stack of activeNotes.values()) {
          while (stack.length) {
            const pending = stack.shift();
            finalizePending(pending, fallback, 0.12);
          }
        }
        activeNotes.clear();
        for (const state of channelStates.values()) {
          releaseSustained(state, fallback);
          state.sustain = false;
        }
      };

      for (const event of parsed.events) {
        const deltaTicks = event.ticks - lastTick;
        if (deltaTicks > 0) {
          currentTime += deltaTicks * secondsPerTick;
          lastTick = event.ticks;
        }

        if (event.type === 'tempo') {
          secondsPerTick = (event.microsecondsPerBeat / 1e6) / parsed.division;
          continue;
        }

        if (event.type === 'program') {
          const state = getChannelState(event.channel);
          state.program = event.program;
          continue;
        }

        if (event.type !== 'noteOn' && event.type !== 'noteOff') {
          if (event.type === 'control') {
            const state = getChannelState(event.channel);
            if (event.controller === 7) {
              state.volume = Math.max(0, Math.min(2, event.value / 127));
            } else if (event.controller === 11) {
              state.expression = Math.max(0, Math.min(2, event.value / 127));
            } else if (event.controller === 10) {
              const normalized = event.value / 127;
              state.pan = Math.max(-1, Math.min(1, (normalized * 2) - 1));
            } else if (event.controller === 64) {
              const sustainOn = event.value >= 64;
              if (sustainOn) {
                state.sustain = true;
              } else if (state.sustain) {
                state.sustain = false;
                releaseSustained(state, currentTime);
              }
            } else if (event.controller === 91) {
              state.reverb = Math.max(0, Math.min(1, event.value / 127));
            }
          }
          continue;
        }

        const state = getChannelState(event.channel);
        const stack = registerActiveStack(event.channel, event.note);

        if (event.type === 'noteOn') {
          const velocityBase = Math.max(0, Math.min(1, event.velocity / 127));
          const volumeFactor = Math.max(0, Math.min(2, state.volume * state.expression));
          const velocity = Math.max(0.02, Math.min(1, velocityBase * volumeFactor));
          stack.push({
            time: currentTime,
            velocity,
            note: event.note,
            channel: event.channel,
            program: state.program || 0,
            pan: state.pan,
            reverb: state.reverb,
            channelVolume: state.volume,
            expression: state.expression,
            rawVelocity: velocityBase,
          });
        } else {
          if (stack && stack.length) {
            const pending = stack.shift();
            if (state.sustain) {
              if (!state.sustainedNotes.has(event.note)) {
                state.sustainedNotes.set(event.note, []);
              }
              state.sustainedNotes.get(event.note).push({
                pending,
                offTime: currentTime,
              });
            } else {
              finalizePending(pending, currentTime, 0.08);
            }
          }
        }
      }

      if (activeNotes.size) {
        flushRemainingNotes(currentTime + 0.4);
      }

      const duration = notes.reduce((max, note) => Math.max(max, note.startTime + note.duration + 0.2), 0);
      notes.sort((a, b) => a.startTime - b.startTime);

      return {
        notes,
        duration,
      };
    }

    static formatDuration(seconds) {
      if (!Number.isFinite(seconds)) {
        return '';
      }
      if (seconds < 60) {
        return `${seconds.toFixed(1)} s`;
      }
      const minutes = Math.floor(seconds / 60);
      const remaining = Math.round(seconds % 60).toString().padStart(2, '0');
      return `${minutes} min ${remaining} s`;
    }
  }

  class ChiptunePlayer {
    constructor(elements) {
      this.fileInput = elements.fileInput;
      this.dropZone = elements.dropZone;
      this.folderButton = elements.folderButton;
      this.folderInput = elements.folderInput;
      this.artistSelect = elements.artistSelect;
      this.trackSelect = elements.trackSelect;
      this.trackSlider = elements.trackSlider;
      this.trackSliderValue = elements.trackSliderValue;
      this.headerPlaybackButton = elements.headerPlaybackButton;
      this.playButton = elements.playButton;
      this.skipButton = elements.skipButton;
      this.stopButton = elements.stopButton;
      this.randomAllButton = elements.randomAllButton;
      this.randomArtistButton = elements.randomArtistButton;
      this.status = elements.status;
      this.volumeSlider = elements.volumeSlider;
      this.volumeValue = elements.volumeValue;
      this.transposeSlider = elements.transposeSlider;
      this.transposeValue = elements.transposeValue;
      this.fineTuneSlider = elements.fineTuneSlider;
      this.fineTuneValue = elements.fineTuneValue;
      this.octaveDownButton = elements.octaveDownButton;
      this.octaveUpButton = elements.octaveUpButton;
      this.resetPitchButton = elements.resetPitchButton;
      this.articulationSlider = elements.articulationSlider;
      this.articulationValue = elements.articulationValue;
      this.speedSlider = elements.speedSlider;
      this.speedValue = elements.speedValue;
      this.pianoOverrideToggle = elements.pianoOverrideToggle;
      this.pianoOverrideValue = elements.pianoOverrideValue;
      this.pianoOverrideEnabled = true;
      this.engineSelect = elements.engineSelect;
      this.soundFontSelect = elements.soundFontSelect;
      this.progressLabel = elements.progressLabel;
      this.progressSlider = elements.progressSlider;
      this.progressValue = elements.progressValue;
      this.programUsageContainer = elements.programUsageContainer;
      this.programUsageSummary = elements.programUsageSummary;
      this.programUsageTitle = elements.programUsageTitle;
      this.programUsageNote = elements.programUsageNote;
      this.visibleTracks = [];

      const hasWindow = typeof window !== 'undefined';
      if (hasWindow) {
        this.requestFrame = typeof window.requestAnimationFrame === 'function'
          ? window.requestAnimationFrame.bind(window)
          : (callback) => window.setTimeout(callback, 100);
        this.cancelFrame = typeof window.cancelAnimationFrame === 'function'
          ? window.cancelAnimationFrame.bind(window)
          : (handle) => window.clearTimeout(handle);
      } else {
        this.requestFrame = () => 0;
        this.cancelFrame = () => {};
      }

      this.audioContext = null;
      this.masterGain = null;
      this.masterLimiter = null;
      this.dryBus = null;
      this.reverbSend = null;
      this.reverbMix = null;
      this.reverbNode = null;
      this.timeline = null;
      this.timelineAnalysis = null;
      this.currentTitle = '';
      this.currentTitleDescriptor = null;
      this.playing = false;
      this.pendingTimers = new Set();
      this.liveVoices = new Set();
      this.finishTimeout = null;
      this.languageChangeUnsubscribe = null;
      this.libraryArtists = [];
      this.libraryAllTracks = [];
      this.baseLibraryArtists = [];
      this.userLibraryArtists = [];
      this.userLibrarySignatures = new Map();
      this.currentArtistId = '';
      this.readyStatusMessage = null;
      this.lastStatusMessage = null;
      this.playStartTime = null;
      this.playStartOffset = 0;
      this.progressRaf = null;
      this.progressDuration = 0;
      this.progressMonitorSpeed = 1;
      this.pendingSeekSeconds = 0;
      this.lastKnownPosition = 0;
      this.isScrubbing = false;

      this.masterVolume = 0.8;
      this.maxGain = 0.26;
      this.waveCache = new Map();
      this.noiseBuffer = null;
      this.reverbBuffer = null;
      this.reverbDefaultSend = 0.12;
      this.sccWaveform = typeof window !== 'undefined' ? window.SccWaveform || null : null;
      this.engineMode = 'hifi';
      this.soundFontList = DEFAULT_SOUNDFONTS.map(font => ({ ...font }));
      this.soundFontFallback = DEFAULT_SOUNDFONTS;
      this.soundFontCache = new Map();
      this.activeSoundFont = null;
      this.activeSoundFontId = null;
      this.selectedSoundFontId = null;
      this.selectedSoundFontLabel = '';
      this.currentSoundFontLabel = '';
      this.loadingSoundFont = null;
      this.soundFontLoadErrored = false;
      this.schedulerInterval = null;
      this.schedulerState = null;
      this.scheduleAheadTime = 0.25;
      this.scheduleIntervalSeconds = 0.03;
      this.limiterSettings = {
        threshold: -8,
        knee: 10,
        ratio: 6,
        attack: 0.003,
        release: 0.25,
      };
      this.transposeSemitones = 0;
      this.fineDetuneCents = 0;
      this.transposeMin = -24;
      this.transposeMax = 24;
      this.detuneMin = -100;
      this.detuneMax = 100;
      this.articulationSetting = 70;
      this.playbackSpeed = 1;
      this.activePlaybackSpeed = 1;
      this.speedMin = 0.5;
      this.speedMax = 2;
      this.libraryLoadErrored = false;
      this.randomPlaybackMode = null;
      this.randomPlaybackArtistId = null;
      this.randomPlaybackQueue = [];
      this.randomPlaybackCurrentTrack = null;
      this.randomPlaybackPending = false;
      this.lastRandomPlaybackTrack = null;

      this.populateSoundFonts(this.soundFontList, false);

      this.initializeProgressControls();
      this.initializeProgramUsageGrid();

      if (this.volumeSlider) {
        const sliderValue = Number.parseFloat(this.volumeSlider.value);
        if (Number.isFinite(sliderValue)) {
          this.masterVolume = Math.max(0, Math.min(1, sliderValue / 100));
        } else {
          this.volumeSlider.value = String(Math.round(this.masterVolume * 100));
        }
      }
      this.setMasterVolume(this.masterVolume, false);

      if (this.transposeSlider) {
        const sliderValue = Number.parseInt(this.transposeSlider.value, 10);
        if (Number.isFinite(sliderValue)) {
          this.transposeSemitones = this.clampTranspose(sliderValue);
        } else {
          this.transposeSlider.value = '0';
        }
      }
      if (this.fineTuneSlider) {
        const sliderValue = Number.parseInt(this.fineTuneSlider.value, 10);
        if (Number.isFinite(sliderValue)) {
          this.fineDetuneCents = this.clampFineDetune(sliderValue);
        } else {
          this.fineTuneSlider.value = '0';
        }
      }
      if (this.articulationSlider) {
        const sliderValue = Number.parseInt(this.articulationSlider.value, 10);
        if (Number.isFinite(sliderValue)) {
          this.articulationSetting = this.clampArticulation(sliderValue);
        } else {
          this.articulationSlider.value = String(this.articulationSetting);
        }
      }
      if (this.speedSlider) {
        const sliderValue = Number.parseInt(this.speedSlider.value, 10);
        if (Number.isFinite(sliderValue)) {
          this.playbackSpeed = this.clampPlaybackSpeed(sliderValue / 100);
        } else {
          this.speedSlider.value = '100';
        }
      }
      if (this.engineSelect) {
        const hasScc = Boolean(this.sccWaveform);
        const validModes = new Set(['original', 'scc', 'n163', 'ym2413', 'sid', 'hifi']);
        const requestedValue = typeof this.engineSelect.value === 'string' ? this.engineSelect.value : '';
        let initialValue = validModes.has(requestedValue)
          ? requestedValue
          : 'hifi';
        if (!validModes.has(initialValue)) {
          initialValue = hasScc ? 'scc' : 'n163';
        }
        if (!validModes.has(initialValue)) {
          initialValue = 'original';
        }
        const sccOption = this.engineSelect.querySelector('option[value="scc"]');
        if (sccOption) {
          sccOption.disabled = !hasScc;
        }
        if (this.engineSelect.value !== initialValue) {
          this.engineSelect.value = initialValue;
        }
        this.setEngineMode(initialValue, { syncSelect: false });
      } else {
        this.engineMode = 'hifi';
      }
      this.setTransposeSemitones(this.transposeSemitones, { syncSlider: true, refreshVoices: false });
      this.setFineDetuneCents(this.fineDetuneCents, { syncSlider: true, refreshVoices: false });
      this.setArticulation(this.articulationSetting, { syncSlider: true, refresh: false });
      this.setPlaybackSpeed(this.playbackSpeed, { syncSlider: true });

      this.refreshProgressControls();
      this.refreshStaticTexts();

      this.bindEvents();
      this.updateButtons();
      this.loadSoundFonts();
      this.loadLibrary();
      this.subscribeToLanguageChanges();
    }

    isMidiFile(file) {
      if (!file) {
        return false;
      }
      const name = typeof file.name === 'string' ? file.name.toLowerCase() : '';
      if (name.endsWith('.mid') || name.endsWith('.midi')) {
        return true;
      }
      const type = typeof file.type === 'string' ? file.type.toLowerCase() : '';
      return type === 'audio/midi'
        || type === 'audio/x-midi'
        || type === 'audio/smf'
        || type === 'audio/mid';
    }

    getFileRelativePath(file) {
      if (!file) {
        return '';
      }
      const relativePath = typeof file.webkitRelativePath === 'string' && file.webkitRelativePath
        ? file.webkitRelativePath
        : typeof file.relativePath === 'string' && file.relativePath
          ? file.relativePath
          : typeof file.name === 'string'
            ? file.name
            : '';
      return relativePath.replace(/\\/g, '/');
    }

    getFolderRootName(files) {
      if (!Array.isArray(files) || !files.length) {
        return '';
      }
      for (const file of files) {
        const path = this.getFileRelativePath(file);
        if (!path) {
          continue;
        }
        const segments = path.split('/');
        if (segments.length > 1 && segments[0]) {
          return segments[0];
        }
      }
      const first = files[0];
      if (first) {
        const name = typeof first.name === 'string' ? first.name : '';
        if (name) {
          return name.replace(/\.[^.]+$/, '') || name;
        }
      }
      return '';
    }

    slugifyId(value) {
      if (typeof value !== 'string') {
        return '';
      }
      const trimmed = value.trim();
      if (!trimmed) {
        return '';
      }
      const normalized = typeof trimmed.normalize === 'function'
        ? trimmed.normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
        : trimmed;
      return normalized
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
    }

    generateUserArtistId(baseName = '') {
      let base = this.slugifyId(baseName) || 'user-folder';
      if (!base.startsWith('user-')) {
        base = `user-${base}`;
      }
      if (!base) {
        base = 'user-folder';
      }
      const existingIds = new Set();
      if (Array.isArray(this.libraryArtists)) {
        this.libraryArtists.forEach((artist) => {
          if (artist && typeof artist.id === 'string') {
            existingIds.add(artist.id);
          }
        });
      }
      if (Array.isArray(this.userLibraryArtists)) {
        this.userLibraryArtists.forEach((artist) => {
          if (artist && typeof artist.id === 'string') {
            existingIds.add(artist.id);
          }
        });
      }
      let candidate = base;
      let suffix = 2;
      while (existingIds.has(candidate)) {
        candidate = `${base}-${suffix}`;
        suffix += 1;
      }
      return candidate;
    }

    buildFolderSignature(files) {
      if (!Array.isArray(files) || !files.length) {
        return '';
      }
      const paths = files
        .map(file => this.getFileRelativePath(file))
        .filter(path => typeof path === 'string' && path);
      if (!paths.length) {
        return '';
      }
      paths.sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
      return paths.map(path => path.toLowerCase()).join('|');
    }

    trimFolderPrefix(path, folder) {
      if (typeof path !== 'string') {
        return '';
      }
      const normalizedPath = path.startsWith('/') ? path.slice(1) : path;
      if (!folder) {
        return normalizedPath;
      }
      const normalizedFolder = folder.replace(/\\/g, '/');
      const prefix = `${normalizedFolder}/`;
      if (normalizedPath.startsWith(prefix)) {
        return normalizedPath.slice(prefix.length) || normalizedPath;
      }
      return normalizedPath;
    }

    findExistingUserArtist(signature, folderName) {
      if (!Array.isArray(this.userLibraryArtists) || !this.userLibraryArtists.length) {
        return null;
      }
      if (signature && this.userLibrarySignatures.has(signature)) {
        const existingId = this.userLibrarySignatures.get(signature);
        const match = this.userLibraryArtists.find(item => item && item.id === existingId);
        if (match) {
          return match;
        }
      }
      if (folderName) {
        const normalized = folderName.toLowerCase();
        const match = this.userLibraryArtists.find((item) => {
          if (!item || typeof item.sourceFolder !== 'string') {
            return false;
          }
          return item.sourceFolder.toLowerCase() === normalized;
        });
        if (match) {
          return match;
        }
      }
      return null;
    }

    registerUserArtist(artist, signature, folderName) {
      if (!artist || typeof artist !== 'object') {
        return { replaced: false };
      }
      const normalizedSignature = typeof signature === 'string' ? signature : '';
      const normalizedFolder = typeof folderName === 'string' ? folderName.trim().toLowerCase() : '';
      let targetIndex = -1;
      if (typeof artist.id === 'string' && artist.id) {
        targetIndex = this.userLibraryArtists.findIndex(item => item && item.id === artist.id);
      }
      if (targetIndex < 0 && normalizedSignature && this.userLibrarySignatures.has(normalizedSignature)) {
        const existingId = this.userLibrarySignatures.get(normalizedSignature);
        targetIndex = this.userLibraryArtists.findIndex(item => item && item.id === existingId);
      }
      if (targetIndex < 0 && normalizedFolder) {
        targetIndex = this.userLibraryArtists.findIndex((item) => {
          if (!item || typeof item.sourceFolder !== 'string') {
            return false;
          }
          return item.sourceFolder.toLowerCase() === normalizedFolder;
        });
      }

      let replaced = false;
      if (targetIndex >= 0) {
        const previous = this.userLibraryArtists[targetIndex];
        if (previous && typeof previous.signature === 'string') {
          this.userLibrarySignatures.delete(previous.signature);
        }
        this.userLibraryArtists.splice(targetIndex, 1, artist);
        replaced = true;
      } else {
        this.userLibraryArtists.push(artist);
      }

      if (normalizedSignature) {
        this.userLibrarySignatures.set(normalizedSignature, artist.id);
      }

      return { replaced };
    }

    importMidiFolder(files) {
      if (!Array.isArray(files) || !files.length) {
        return;
      }
      const midiFiles = files.filter(file => this.isMidiFile(file));
      if (!midiFiles.length) {
        this.setStatusMessage(
          'index.sections.options.chiptune.folderImport.statusEmpty',
          'The selected folder does not contain any MIDI files.',
          {},
          'error',
        );
        return;
      }
      midiFiles.sort((a, b) => {
        const pathA = this.getFileRelativePath(a);
        const pathB = this.getFileRelativePath(b);
        return pathA.localeCompare(pathB, undefined, { sensitivity: 'base' });
      });

      const signature = this.buildFolderSignature(midiFiles);
      const folderRoot = this.getFolderRootName(midiFiles);
      const defaultName = this.translate(
        'index.sections.options.chiptune.folderImport.defaultName',
        'Local MIDI folder',
      );
      const existing = this.findExistingUserArtist(signature, folderRoot);
      const suggestedName = existing && typeof existing.name === 'string' && existing.name
        ? existing.name
        : folderRoot || defaultName;
      let artistName = suggestedName || defaultName;
      const canPrompt = typeof window !== 'undefined'
        && window
        && typeof window.prompt === 'function';
      if (canPrompt) {
        const promptMessage = this.translate(
          'index.sections.options.chiptune.folderImport.prompt',
          'Choose a name for this folder (optional)',
        );
        try {
          const response = window.prompt(promptMessage, artistName);
          if (response != null) {
            const trimmed = response.trim();
            if (trimmed) {
              artistName = trimmed;
            }
          }
        } catch (error) {
          // Ignore prompt errors silently
        }
      }

      const artistId = existing && typeof existing.id === 'string' && existing.id
        ? existing.id
        : this.generateUserArtistId(artistName || folderRoot || defaultName);

      try {
        const tracks = midiFiles.map((file, index) => {
          const relativePath = this.getFileRelativePath(file);
          const displayPath = this.trimFolderPrefix(relativePath, folderRoot)
            || (typeof file.name === 'string' && file.name)
            || `Track ${index + 1}`;
          return {
            file: `user-track-${artistId}-${index + 1}`,
            name: displayPath,
            source: 'user',
            blob: file,
            relativePath,
          };
        });

        const artist = {
          id: artistId,
          name: artistName || defaultName,
          tracks,
          source: 'user',
          signature,
          sourceFolder: folderRoot || '',
        };

        const { replaced } = this.registerUserArtist(artist, signature, folderRoot || '');

        this.populateLibrary(
          [...this.baseLibraryArtists, ...this.userLibraryArtists],
          this.libraryLoadErrored,
          {
            maintainSelection: false,
            preferredArtistId: artist.id,
            preferredTrackFile: tracks.length ? tracks[0].file : '',
          },
        );

        const key = replaced
          ? 'index.sections.options.chiptune.folderImport.statusUpdated'
          : 'index.sections.options.chiptune.folderImport.statusAdded';
        const fallback = replaced
          ? 'Folder updated: {name} ({count} tracks)'
          : 'Folder ready: {name} ({count} tracks)';
        this.setStatusMessage(key, fallback, { name: artist.name, count: tracks.length }, 'success');
      } catch (error) {
        console.error('Unable to import MIDI folder', error);
        this.setStatusMessage(
          'index.sections.options.chiptune.folderImport.statusError',
          'Unable to import the selected folder.',
          {},
          'error',
        );
      }
    }

    bindEvents() {
      if (this.fileInput) {
        this.fileInput.addEventListener('change', () => {
          const [file] = this.fileInput.files;
          if (!file) {
            return;
          }
          this.loadFromFile(file);
        });
      }

      if (this.folderButton) {
        this.folderButton.addEventListener('click', () => {
          if (this.folderInput) {
            this.folderInput.click();
          }
        });
      }

      if (this.folderInput) {
        this.folderInput.addEventListener('change', () => {
          const files = this.folderInput.files ? Array.from(this.folderInput.files) : [];
          if (files.length) {
            this.importMidiFolder(files);
          }
          this.folderInput.value = '';
        });
      }

      if (this.dropZone) {
        const prevent = (event) => {
          event.preventDefault();
          event.stopPropagation();
        };
        const activate = () => {
          this.dropZone.classList.add('is-dragover');
        };
        const deactivate = () => {
          this.dropZone.classList.remove('is-dragover');
        };

        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
          this.dropZone.addEventListener(eventName, prevent);
        });
        ['dragenter', 'dragover'].forEach(eventName => {
          this.dropZone.addEventListener(eventName, activate);
        });
        ['dragleave', 'drop'].forEach(eventName => {
          this.dropZone.addEventListener(eventName, deactivate);
        });

        this.dropZone.addEventListener('drop', (event) => {
          const files = event.dataTransfer && event.dataTransfer.files
            ? Array.from(event.dataTransfer.files)
            : [];
          if (!files.length) {
            return;
          }
          const midiFile = files.find(file => this.isMidiFile(file));
          if (!midiFile) {
            this.setStatusMessage(
              'index.sections.options.chiptune.status.unsupportedFile',
              'Please drop a MIDI file (.mid or .midi).',
              {},
              'error',
            );
            return;
          }
          this.loadFromFile(midiFile);
        });

        this.dropZone.addEventListener('click', () => {
          if (this.fileInput) {
            this.fileInput.click();
          }
        });

        this.dropZone.addEventListener('keydown', (event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            if (this.fileInput) {
              this.fileInput.click();
            }
          }
        });
      }

      if (this.artistSelect) {
        this.artistSelect.addEventListener('change', () => {
          const artistId = this.artistSelect.value;
          this.resetRandomPlayback();
          this.applyArtistSelection(artistId);
        });
      }

      if (this.trackSelect) {
        this.trackSelect.addEventListener('change', () => {
          const file = this.trackSelect.value;
          if (!file) {
            return;
          }
          const track = this.findTrackByFile(file, this.currentArtistId);
          if (!track) {
            this.setStatusMessage('index.sections.options.chiptune.status.trackNotFound', 'Unable to locate the selected track.', {}, 'error');
            return;
          }
          this.refreshTrackSlider(track.file);
          this.resetRandomPlayback();
          this.loadFromLibrary(track);
        });
      }

      if (this.trackSlider) {
        this.trackSlider.addEventListener('input', () => {
          this.handleTrackSliderInput(false);
        });
        this.trackSlider.addEventListener('change', () => {
          this.handleTrackSliderInput(true);
        });
      }

      if (this.randomAllButton) {
        this.randomAllButton.addEventListener('click', () => {
          this.playRandomTrack().catch((error) => {
            console.error('Unable to start random playback', error);
          });
        });
      }

      if (this.randomArtistButton) {
        this.randomArtistButton.addEventListener('click', () => {
          const artistId = this.currentArtistId || (this.artistSelect ? this.artistSelect.value : '');
          this.playRandomTrack(artistId || null).catch((error) => {
            console.error('Unable to start artist random playback', error);
          });
        });
      }

      if (this.playButton) {
        this.playButton.addEventListener('click', () => {
          if (this.playing) {
            this.pause();
          } else {
            this.play();
          }
        });
      }

      if (this.headerPlaybackButton) {
        this.headerPlaybackButton.addEventListener('click', () => {
          if (this.playing) {
            this.pause();
          } else {
            this.play();
          }
        });
      }

      if (this.skipButton) {
        this.skipButton.addEventListener('click', () => {
          this.skipRandomTrack();
        });
      }

      if (this.stopButton) {
        this.stopButton.addEventListener('click', () => {
          this.stop(true);
        });
      }

      if (this.volumeSlider) {
        this.volumeSlider.addEventListener('input', () => {
          const sliderValue = Number.parseFloat(this.volumeSlider.value);
          if (!Number.isFinite(sliderValue)) {
            return;
          }
          this.setMasterVolume(Math.max(0, Math.min(1, sliderValue / 100)), false);
        });
      }

      if (this.transposeSlider) {
        this.transposeSlider.addEventListener('input', () => {
          const sliderValue = Number.parseInt(this.transposeSlider.value, 10);
          if (!Number.isFinite(sliderValue)) {
            return;
          }
          this.setTransposeSemitones(sliderValue);
        });
      }

      if (this.fineTuneSlider) {
        this.fineTuneSlider.addEventListener('input', () => {
          const sliderValue = Number.parseInt(this.fineTuneSlider.value, 10);
          if (!Number.isFinite(sliderValue)) {
            return;
          }
          this.setFineDetuneCents(sliderValue);
        });
      }

      if (this.articulationSlider) {
        this.articulationSlider.addEventListener('input', () => {
          const sliderValue = Number.parseInt(this.articulationSlider.value, 10);
          if (!Number.isFinite(sliderValue)) {
            return;
          }
          this.setArticulation(sliderValue);
        });
      }

      if (this.speedSlider) {
        this.speedSlider.addEventListener('input', () => {
          const sliderValue = Number.parseInt(this.speedSlider.value, 10);
          if (!Number.isFinite(sliderValue)) {
            return;
          }
          this.setPlaybackSpeed(sliderValue / 100);
        });
      }

      if (this.pianoOverrideToggle) {
        this.pianoOverrideToggle.addEventListener('change', () => {
          const enabled = Boolean(this.pianoOverrideToggle.checked);
          this.setPianoOverrideEnabled(enabled, { syncControl: false });
        });
      }

      if (this.progressSlider) {
        const getSliderSeconds = () => {
          const rawValue = Number.parseFloat(this.progressSlider.value);
          return Number.isFinite(rawValue) ? rawValue : 0;
        };

        const handleInput = () => {
          if (!this.timeline) {
            return;
          }
          const seconds = getSliderSeconds();
          this.pendingSeekSeconds = Math.max(0, seconds);
          this.updateProgressDisplay(seconds, this.progressDuration || this.getTimelineDuration(), this.activePlaybackSpeed || this.playbackSpeed, {
            allowSliderUpdate: false,
            fromUser: true,
          });
        };

        const commitSeek = () => {
          const seconds = getSliderSeconds();
          this.seekTo(seconds, { fromUser: true });
        };

        const startScrub = () => {
          this.isScrubbing = true;
        };

        const finishScrub = () => {
          this.isScrubbing = false;
        };

        this.progressSlider.addEventListener('input', handleInput);
        this.progressSlider.addEventListener('change', () => {
          finishScrub();
          commitSeek();
        });

        ['pointerdown', 'mousedown', 'touchstart'].forEach((eventName) => {
          this.progressSlider.addEventListener(eventName, startScrub, { passive: true });
        });

        ['pointerup', 'pointercancel', 'mouseup', 'touchend', 'touchcancel'].forEach((eventName) => {
          this.progressSlider.addEventListener(eventName, finishScrub, { passive: true });
        });

        this.progressSlider.addEventListener('blur', finishScrub);
        this.progressSlider.addEventListener('keyup', (event) => {
          if (event?.key === 'Enter' || event?.key === ' ') {
            finishScrub();
            commitSeek();
          }
        });
      }

      if (this.engineSelect) {
        this.engineSelect.addEventListener('change', () => {
          this.setEngineMode(this.engineSelect.value);
        });
      }

      if (this.soundFontSelect) {
        this.soundFontSelect.addEventListener('change', () => {
          const value = typeof this.soundFontSelect.value === 'string' ? this.soundFontSelect.value : '';
          const normalized = value && value.trim() ? value.trim() : null;
          const wasPlaying = this.playing;
          const shouldActivateHiFi = Boolean(normalized) && this.engineMode !== 'hifi';
          this.setSoundFontSelection(normalized, { autoLoad: !shouldActivateHiFi && this.engineMode === 'hifi', stopPlayback: wasPlaying });
          if (shouldActivateHiFi) {
            this.setEngineMode('hifi');
            this.setStatusMessage('index.sections.options.chiptune.status.autoHiFi', 'Hi-Fi mode enabled automatically to use the SoundFont.', {}, 'success');
          }
        });
      }

      if (this.octaveDownButton) {
        this.octaveDownButton.addEventListener('click', () => {
          this.setTransposeSemitones(this.transposeSemitones - 12);
        });
      }

      if (this.octaveUpButton) {
        this.octaveUpButton.addEventListener('click', () => {
          this.setTransposeSemitones(this.transposeSemitones + 12);
        });
      }

      if (this.resetPitchButton) {
        this.resetPitchButton.addEventListener('click', () => {
          this.setTransposeSemitones(0);
          this.setFineDetuneCents(0);
        });
      }

      if (this.pianoOverrideToggle) {
        this.setPianoOverrideEnabled(Boolean(this.pianoOverrideToggle.checked));
      } else {
        this.setPianoOverrideEnabled(true, { syncControl: false });
      }

    }

    setMasterVolume(value, syncSlider = true) {
      const clamped = Math.max(0, Math.min(1, value));
      this.masterVolume = clamped;
      if (this.volumeSlider && syncSlider) {
        const sliderValue = Math.round(clamped * 100);
        if (Number.parseInt(this.volumeSlider.value, 10) !== sliderValue) {
          this.volumeSlider.value = String(sliderValue);
        }
      }
      if (this.volumeValue) {
        this.volumeValue.textContent = `${Math.round(clamped * 100)}%`;
      }
      if (this.masterGain) {
        const time = this.audioContext ? this.audioContext.currentTime : 0;
        try {
          this.masterGain.gain.cancelScheduledValues(time);
        } catch (error) {
          // Ignore cancellation errors if the context is not ready yet
        }
        this.masterGain.gain.setValueAtTime(clamped, time);
      }
    }

    clampTranspose(value) {
      return Math.max(this.transposeMin, Math.min(this.transposeMax, Math.round(value)));
    }

    clampFineDetune(value) {
      return Math.max(this.detuneMin, Math.min(this.detuneMax, Math.round(value)));
    }

    clampArticulation(value) {
      return Math.max(0, Math.min(100, Math.round(value)));
    }

    setTransposeSemitones(value, options = {}) {
      const { syncSlider = true, refreshVoices = true } = options;
      const clamped = this.clampTranspose(Number.isFinite(value) ? value : 0);
      this.transposeSemitones = clamped;
      const label = this.formatSemitoneLabel(clamped);
      if (this.transposeSlider && syncSlider) {
        const currentValue = Number.parseInt(this.transposeSlider.value, 10);
        if (!Number.isFinite(currentValue) || currentValue !== clamped) {
          this.transposeSlider.value = String(clamped);
        }
      }
      if (this.transposeSlider) {
        this.transposeSlider.setAttribute('aria-valuetext', label);
      }
      if (this.transposeValue) {
        this.transposeValue.textContent = label;
      }
      if (refreshVoices) {
        this.refreshLiveVoicesPitch();
      }
    }

    setFineDetuneCents(value, options = {}) {
      const { syncSlider = true, refreshVoices = true } = options;
      const clamped = this.clampFineDetune(Number.isFinite(value) ? value : 0);
      this.fineDetuneCents = clamped;
      const label = this.formatCentsLabel(clamped);
      if (this.fineTuneSlider && syncSlider) {
        const currentValue = Number.parseInt(this.fineTuneSlider.value, 10);
        if (!Number.isFinite(currentValue) || currentValue !== clamped) {
          this.fineTuneSlider.value = String(clamped);
        }
      }
      if (this.fineTuneSlider) {
        this.fineTuneSlider.setAttribute('aria-valuetext', label);
      }
      if (this.fineTuneValue) {
        this.fineTuneValue.textContent = label;
      }
      if (refreshVoices) {
        this.refreshLiveVoicesPitch();
      }
    }

    setArticulation(value, options = {}) {
      const { syncSlider = true, refresh = false } = options;
      const clamped = this.clampArticulation(Number.isFinite(value) ? value : this.articulationSetting);
      this.articulationSetting = clamped;
      const label = this.formatArticulationLabel(clamped);
      if (this.articulationSlider && syncSlider) {
        const currentValue = Number.parseInt(this.articulationSlider.value, 10);
        if (!Number.isFinite(currentValue) || currentValue !== clamped) {
          this.articulationSlider.value = String(clamped);
        }
      }
      if (this.articulationSlider) {
        this.articulationSlider.setAttribute('aria-valuetext', label);
      }
      if (this.articulationValue) {
        this.articulationValue.textContent = label;
      }
      if (refresh) {
        this.refreshLiveVoicesArticulation?.();
      }
    }

    clampPlaybackSpeed(value) {
      const normalized = Number.isFinite(value) ? value : 1;
      return Math.max(this.speedMin, Math.min(this.speedMax, normalized));
    }

    formatPlaybackSpeedLabel(ratio) {
      const clamped = this.clampPlaybackSpeed(ratio);
      const percent = Math.round(clamped * 100);
      if (Math.abs(percent - 100) <= 1) {
        return this.translate('index.sections.options.chiptune.labels.speedNormal', 'Normal tempo (100%)');
      }
      if (percent < 100) {
        return this.translate('index.sections.options.chiptune.labels.speedSlow', 'Slower tempo ({value}%)', { value: percent });
      }
      return this.translate('index.sections.options.chiptune.labels.speedFast', 'Faster tempo ({value}%)', { value: percent });
    }

    formatSpeedFactor(ratio) {
      const clamped = this.clampPlaybackSpeed(ratio);
      return this.translate('index.sections.options.chiptune.labels.speedFactor', 'Tempo ×{value}', { value: clamped.toFixed(2) });
    }

    getEffectiveDuration(timeline = this.timeline, speed = null) {
      if (!timeline || !Number.isFinite(timeline.duration)) {
        return 0;
      }
      const ratioSource = Number.isFinite(speed)
        ? speed
        : (this.playing ? this.activePlaybackSpeed : this.playbackSpeed);
      const ratio = this.clampPlaybackSpeed(ratioSource || 1);
      if (ratio <= 0) {
        return timeline.duration;
      }
      return timeline.duration / ratio;
    }

    formatDurationWithSpeed(seconds, options = {}) {
      if (!Number.isFinite(seconds)) {
        return '';
      }
      const { speed = null, includeFactor = true } = options;
      const ratioSource = Number.isFinite(speed)
        ? speed
        : (this.playing ? this.activePlaybackSpeed : this.playbackSpeed);
      const ratio = this.clampPlaybackSpeed(ratioSource || 1);
      const base = MidiTimeline.formatDuration(seconds / (ratio || 1));
      if (!includeFactor || Math.abs(ratio - 1) < 0.005) {
        return base;
      }
      return `${base} · ${this.formatSpeedFactor(ratio)}`;
    }

    setPlaybackSpeed(value, options = {}) {
      const { syncSlider = true } = options;
      const clamped = this.clampPlaybackSpeed(Number.isFinite(value) ? value : this.playbackSpeed || 1);
      this.playbackSpeed = clamped;
      const percent = Math.round(clamped * 100);
      const label = this.formatPlaybackSpeedLabel(clamped);
      if (this.speedSlider && syncSlider) {
        const current = Number.parseInt(this.speedSlider.value, 10);
        if (!Number.isFinite(current) || current !== percent) {
          this.speedSlider.value = String(percent);
        }
      }
      if (this.speedSlider) {
        this.speedSlider.setAttribute('aria-valuetext', label);
      }
      if (this.speedValue) {
        this.speedValue.textContent = label;
      }
      this.updateReadyStatusMessage();
    }

    setPianoOverrideEnabled(value, options = {}) {
      const { syncControl = true } = options;
      const enabled = Boolean(value);
      this.pianoOverrideEnabled = enabled;

      if (this.pianoOverrideToggle) {
        if (syncControl) {
          this.pianoOverrideToggle.checked = enabled;
        }
        this.pianoOverrideToggle.setAttribute('aria-checked', enabled ? 'true' : 'false');
        if (this.pianoOverrideValue) {
          this.pianoOverrideToggle.setAttribute('aria-describedby', this.pianoOverrideValue.id);
        }
      }

      const key = enabled
        ? 'index.sections.options.chiptune.pianoOverride.enabled'
        : 'index.sections.options.chiptune.pianoOverride.disabled';
      const fallback = enabled ? 'Enabled' : 'Disabled';
      const label = this.translate(key, fallback);

      if (this.pianoOverrideValue) {
        this.pianoOverrideValue.textContent = label;
      }

      this.updateReadyStatusMessage();
    }

    setEngineMode(value, options = {}) {
      const { syncSelect = true } = options;
      const hasScc = Boolean(this.sccWaveform);
      let normalized = 'original';
      if (value === 'ym2413') {
        normalized = 'ym2413';
      } else if (value === 'n163') {
        normalized = 'n163';
      } else if (value === 'sid') {
        normalized = 'sid';
      } else if (value === 'hifi') {
        normalized = 'hifi';
      } else if (value === 'scc' && hasScc) {
        normalized = 'scc';
      }
      this.engineMode = normalized;
      if (this.engineSelect && syncSelect) {
        if (this.engineSelect.value !== normalized) {
          this.engineSelect.value = normalized;
        }
        const sccOption = this.engineSelect.querySelector('option[value="scc"]');
        if (sccOption) {
          sccOption.disabled = !hasScc;
        }
      }
      if (normalized === 'hifi' && this.selectedSoundFontId) {
        this.ensureSoundFontReady().catch((error) => {
          console.error('Unable to prepare SoundFont', error);
        });
      }
      if (this.playing) {
        this.updateReadyStatusMessage();
        this.setStatus('', 'success', { type: 'playing', extra: '' });
      } else {
        this.updateReadyStatusMessage();
      }
    }

    getEngineLabel() {
      if (this.engineMode === 'scc' && this.sccWaveform) {
        return this.translate('index.sections.options.chiptune.engineLabels.scc', 'SCC engine');
      }
      if (this.engineMode === 'n163') {
        return this.translate('index.sections.options.chiptune.engineLabels.n163', 'Namco 163 engine');
      }
      if (this.engineMode === 'ym2413') {
        return this.translate('index.sections.options.chiptune.engineLabels.ym2413', 'Yamaha YM2413 engine');
      }
      if (this.engineMode === 'sid') {
        return this.translate('index.sections.options.chiptune.engineLabels.sid', 'MOS SID engine');
      }
      if (this.engineMode === 'hifi') {
        const label = this.currentSoundFontLabel || this.selectedSoundFontLabel;
        if (label) {
          return this.translate('index.sections.options.chiptune.engineLabels.hifiWithName', 'Hi-Fi SoundFont engine ({name})', { name: label });
        }
        return this.translate('index.sections.options.chiptune.engineLabels.hifi', 'Hi-Fi SoundFont engine');
      }
      return this.translate('index.sections.options.chiptune.engineLabels.original', 'Original engine');
    }

    buildPlayingStatusMessage(extra = '') {
      const base = this.currentTitle
        ? this.translate('index.sections.options.chiptune.status.playingWithTitle', 'Now playing: {title}', { title: this.currentTitle })
        : this.translate('index.sections.options.chiptune.status.playing', 'Now playing');
      const segments = [base];
      const engineLabel = this.getEngineLabel();
      if (engineLabel) {
        segments.push(engineLabel);
      }
      if (extra) {
        segments.push(extra);
      }
      return segments.filter(Boolean).join(' — ');
    }

    generateReadyStatusMessage() {
      if (!this.timeline || !this.currentTitle) {
        return '';
      }
      const summary = this.formatTimelineSummary(this.timeline, this.timelineAnalysis);
      const durationLabel = this.formatDurationWithSpeed(this.timeline.duration);
      const hasSummary = Boolean(summary);
      const baseKey = hasSummary
        ? 'index.sections.options.chiptune.status.readyWithSummary'
        : 'index.sections.options.chiptune.status.readyWithDuration';
      const baseFallback = hasSummary
        ? 'Ready: {title} — {summary}'
        : 'Ready: {title} ({duration})';
      const params = hasSummary
        ? { title: this.currentTitle, summary }
        : { title: this.currentTitle, duration: durationLabel };
      const baseMessage = this.translate(baseKey, baseFallback, params);
      const engineLabel = this.getEngineLabel();
      return engineLabel ? `${baseMessage} — ${engineLabel}` : baseMessage;
    }

    updateReadyStatusMessage() {
      if (!this.timeline || !this.currentTitle) {
        this.readyStatusMessage = null;
        return;
      }
      const message = this.generateReadyStatusMessage();
      if (message) {
        this.readyStatusMessage = {
          message,
          descriptor: { type: 'ready' },
        };
        if (!this.playing) {
          this.setStatus(message, 'success', this.readyStatusMessage.descriptor);
        }
      } else {
        this.readyStatusMessage = null;
        if (!this.playing) {
          this.setStatus('', 'idle');
        }
      }
    }

    formatSemitoneLabel(value) {
      const normalized = Number.isFinite(value) ? Math.round(value) : 0;
      if (normalized === 0) {
        return this.translate('index.sections.options.chiptune.labels.transposeZero', '0 semitone');
      }
      const sign = normalized > 0 ? '+' : '−';
      const abs = Math.abs(normalized);
      if (abs % 12 === 0) {
        const octaves = abs / 12;
        const key = octaves > 1
          ? 'index.sections.options.chiptune.labels.transposeOctaves'
          : 'index.sections.options.chiptune.labels.transposeOctave';
        const fallback = octaves > 1
          ? `${sign}${octaves} octaves`
          : `${sign}${octaves} octave`;
        return this.translate(key, fallback, { sign, count: octaves });
      }
      const key = abs > 1
        ? 'index.sections.options.chiptune.labels.transposeSemitones'
        : 'index.sections.options.chiptune.labels.transposeSemitone';
      const fallback = abs > 1
        ? `${sign}${abs} semitones`
        : `${sign}${abs} semitone`;
      return this.translate(key, fallback, { sign, count: abs });
    }

    formatCentsLabel(value) {
      const normalized = Number.isFinite(value) ? Math.round(value) : 0;
      if (normalized === 0) {
        return this.translate('index.sections.options.chiptune.labels.detuneZero', '0 cent');
      }
      const sign = normalized > 0 ? '+' : '−';
      const abs = Math.abs(normalized);
      const key = abs > 1
        ? 'index.sections.options.chiptune.labels.detuneCents'
        : 'index.sections.options.chiptune.labels.detuneCent';
      const fallback = abs > 1
        ? `${sign}${abs} cents`
        : `${sign}${abs} cent`;
      return this.translate(key, fallback, { sign, count: abs });
    }

    formatArticulationLabel(value) {
      const normalized = this.clampArticulation(Number.isFinite(value) ? value : this.articulationSetting);
      let descriptor = this.translate('index.sections.options.chiptune.labels.articulationBalanced', 'Balanced');
      if (normalized <= 20) {
        descriptor = this.translate('index.sections.options.chiptune.labels.articulationSustained', 'Sustained (organ)');
      } else if (normalized <= 45) {
        descriptor = this.translate('index.sections.options.chiptune.labels.articulationSoft', 'Soft');
      } else if (normalized <= 75) {
        descriptor = this.translate('index.sections.options.chiptune.labels.articulationPiano', 'Piano');
      } else {
        descriptor = this.translate('index.sections.options.chiptune.labels.articulationPlucked', 'Plucked piano');
      }
      return this.translate('index.sections.options.chiptune.labels.articulationValue', '{label} ({value}%)', { label: descriptor, value: normalized });
    }

    translate(key, fallback, params = {}) {
      return translateMessage(key, fallback, params);
    }

    initializeProgressControls() {
      if (this.progressLabel) {
        this.progressLabel.textContent = this.translate(
          'index.sections.options.chiptune.progress.label',
          'Playback position'
        );
      }
      if (this.progressSlider) {
        this.progressSlider.min = '0';
        this.progressSlider.max = '0';
        this.progressSlider.step = '0.01';
        this.progressSlider.value = '0';
        this.progressSlider.disabled = true;
      }
      if (this.progressValue) {
        this.progressValue.textContent = this.translate(
          'index.sections.options.chiptune.progress.empty',
          'No track loaded'
        );
      }
    }

    getTimelineDuration(timeline = this.timeline) {
      if (!timeline || !Number.isFinite(timeline.duration)) {
        return 0;
      }
      return Math.max(0, timeline.duration);
    }

    refreshProgressControls(timeline = this.timeline) {
      const duration = this.getTimelineDuration(timeline);
      this.progressDuration = duration;
      const hasTimeline = Boolean(timeline) && duration > 0;
      const speed = this.playing ? (this.activePlaybackSpeed || this.playbackSpeed || 1) : (this.playbackSpeed || 1);
      let position = this.playing ? this.lastKnownPosition : this.pendingSeekSeconds;
      if (!Number.isFinite(position)) {
        position = 0;
      }
      position = hasTimeline ? Math.max(0, Math.min(duration, position)) : 0;
      if (this.progressSlider) {
        this.progressSlider.disabled = !hasTimeline;
        this.progressSlider.min = '0';
        this.progressSlider.max = hasTimeline ? String(duration) : '0';
        this.progressSlider.step = hasTimeline ? '0.01' : '1';
        if (!this.isScrubbing) {
          this.progressSlider.value = String(position);
        }
      }
      this.lastKnownPosition = position;
      this.updateProgressDisplay(position, duration, speed, { allowSliderUpdate: false });
      if (!hasTimeline) {
        this.pendingSeekSeconds = 0;
        this.lastKnownPosition = 0;
      }
    }

    subscribeToLanguageChanges() {
      if (typeof this.languageChangeUnsubscribe === 'function') {
        try {
          this.languageChangeUnsubscribe();
        } catch (error) {
          console.warn('Unable to remove previous language listener', error);
        }
      }
      this.languageChangeUnsubscribe = null;

      const api = typeof globalThis !== 'undefined' ? globalThis.i18n : null;
      const handler = () => {
        this.refreshStaticTexts();
        this.refreshCurrentTitle();
        this.refreshReadyStatusCache();
        this.refreshStatusText();
        this.refreshProgressControls();
        this.setPlaybackSpeed(this.playbackSpeed, { syncSlider: false });
        this.setTransposeSemitones(this.transposeSemitones, { syncSlider: false, refreshVoices: false });
        this.setFineDetuneCents(this.fineDetuneCents, { syncSlider: false, refreshVoices: false });
        this.setArticulation(this.articulationSetting, { syncSlider: false, refresh: false });
      };

      if (api && typeof api.onLanguageChanged === 'function') {
        const unsubscribe = api.onLanguageChanged(handler);
        if (typeof unsubscribe === 'function') {
          this.languageChangeUnsubscribe = unsubscribe;
        }
        return;
      }

      if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
        globalThis.addEventListener('i18n:languagechange', handler);
        this.languageChangeUnsubscribe = () => {
          globalThis.removeEventListener('i18n:languagechange', handler);
        };
      }
    }

    updateProgressDisplay(position, duration, speed, options = {}) {
      const { allowSliderUpdate = true, fromUser = false } = options;
      const total = Number.isFinite(duration) && duration > 0 ? duration : 0;
      const clamped = total > 0
        ? Math.max(0, Math.min(total, Number.isFinite(position) ? position : 0))
        : Math.max(0, Number.isFinite(position) ? position : 0);
      this.lastKnownPosition = clamped;
      const effectiveSpeed = Number.isFinite(speed) ? speed : 1;

      if (this.progressValue) {
        let label = '';
        if (total > 0) {
          label = `${this.formatClock(clamped)} / ${this.formatClock(total)}`;
        } else if (this.timeline) {
          label = this.formatClock(clamped);
        } else {
          label = this.translate('index.sections.options.chiptune.progress.empty', 'No track loaded');
        }
        if (this.playing && Math.abs(effectiveSpeed - 1) >= 0.01 && label) {
          label = `${label} · ${this.formatSpeedFactor(effectiveSpeed)}`;
        }
        this.progressValue.textContent = label;
      }

      if (allowSliderUpdate && this.progressSlider && !this.isScrubbing) {
        const sliderValue = total > 0 ? clamped : 0;
        this.progressSlider.value = String(sliderValue);
      }

      if (!fromUser && !this.playing) {
        this.pendingSeekSeconds = clamped;
      }
    }

    initializeProgramUsageGrid() {
      if (!this.programUsageContainer) {
        return;
      }
      this.programUsageContainer.textContent = '';
    }

    updateProgramUsage(timeline) {
      if (!this.programUsageContainer) {
        if (this.programUsageSummary) {
          this.programUsageSummary.textContent = '';
        }
        return;
      }
      const doc = this.programUsageContainer.ownerDocument || document;
      const usedPrograms = new Set();
      let percussionUsed = false;
      const notes = timeline && Array.isArray(timeline.notes) ? timeline.notes : [];
      for (const note of notes) {
        if (!note) {
          continue;
        }
        const channel = Number.isFinite(note.channel) ? note.channel : 0;
        if (channel === 9) {
          percussionUsed = true;
          continue;
        }
        if (Number.isFinite(note.program)) {
          const programNumber = Math.max(0, Math.min(127, Math.round(note.program)));
          usedPrograms.add(programNumber);
        }
      }

      const programs = Array.from(usedPrograms).sort((a, b) => a - b);
      this.programUsageContainer.textContent = '';
      if (programs.length > 0) {
        const fragment = doc.createDocumentFragment();
        for (const program of programs) {
          const row = doc.createElement('div');
          row.className = 'chiptune-usage__row is-used';
          row.setAttribute('role', 'row');
          row.setAttribute('data-program', String(program));
          row.hidden = false;
          row.setAttribute('aria-hidden', 'false');
          row.setAttribute('aria-label', this.translate(
            'index.sections.options.chiptune.usage.rowUsed',
            `Program ${program} in use`,
            { program }
          ));

          const programCell = doc.createElement('span');
          programCell.className = 'chiptune-usage__program';
          programCell.setAttribute('role', 'gridcell');
          programCell.textContent = program.toString().padStart(3, '0');

          const statusCell = doc.createElement('span');
          statusCell.className = 'chiptune-usage__status';
          statusCell.setAttribute('role', 'gridcell');
          statusCell.setAttribute('aria-hidden', 'true');
          statusCell.textContent = '✖';

          row.append(programCell, statusCell);
          fragment.append(row);
        }
        this.programUsageContainer.append(fragment);
      }

      if (this.programUsageSummary) {
        const count = programs.length;
        let summaryKey = 'index.sections.options.chiptune.usage.summaryEmpty';
        let summaryFallback = 'No program detected';
        if (count === 1) {
          summaryKey = 'index.sections.options.chiptune.usage.summarySingle';
          summaryFallback = '1 program in use';
        } else if (count > 1) {
          summaryKey = 'index.sections.options.chiptune.usage.summaryMultiple';
          summaryFallback = `${count} programs in use`;
        }
        let summary = this.translate(summaryKey, summaryFallback, { count });
        if (timeline) {
          const percussionKey = percussionUsed
            ? 'index.sections.options.chiptune.usage.percussionActive'
            : 'index.sections.options.chiptune.usage.percussionInactive';
          const percussionFallback = percussionUsed ? 'Percussion active' : 'Percussion inactive';
          const percussionLabel = this.translate(percussionKey, percussionFallback);
          summary = summary
            ? `${summary} · ${percussionLabel}`
            : percussionLabel;
        }
        this.programUsageSummary.textContent = summary;
      }
    }

    refreshStaticTexts() {
      if (this.playButton) {
        this.updatePlayButtonLabel();
      }
      this.updateHeaderPlaybackButton();
      if (this.progressLabel) {
        this.progressLabel.textContent = this.translate(
          'index.sections.options.chiptune.progress.label',
          'Playback position'
        );
      }
      if (this.programUsageTitle) {
        this.programUsageTitle.textContent = this.translate(
          'index.sections.options.chiptune.usage.title',
          'MIDI programs in use'
        );
      }
      if (this.programUsageNote) {
        this.programUsageNote.textContent = this.translate(
          'index.sections.options.chiptune.usage.note',
          'Numbers refer to General MIDI program numbers (0 to 127).'
        );
      }
      if (this.soundFontSelect && this.soundFontSelect.options.length > 0) {
        const placeholder = this.soundFontSelect.options[0];
        if (placeholder) {
          if (this.soundFontList && this.soundFontList.length) {
            placeholder.textContent = this.translate(
              'index.sections.options.chiptune.soundfonts.placeholder',
              'Choose a SoundFont'
            );
            placeholder.disabled = true;
            placeholder.hidden = true;
          } else {
            const key = this.soundFontLoadErrored
              ? 'index.sections.options.chiptune.soundfonts.noneAvailable'
              : 'index.sections.options.chiptune.soundfonts.noneDeclared';
            const fallback = this.soundFontLoadErrored ? 'No SoundFont available' : 'No SoundFont declared';
            placeholder.textContent = this.translate(key, fallback);
            placeholder.disabled = false;
            placeholder.hidden = false;
          }
        }
      }
      if (this.artistSelect && this.artistSelect.options.length > 0) {
        const placeholder = this.artistSelect.options[0];
        if (placeholder) {
          let key = 'index.sections.options.chiptune.library.artists.placeholder';
          let fallback = 'Select an artist';
          if (this.libraryLoadErrored) {
            key = 'index.sections.options.chiptune.library.artists.unavailable';
            fallback = 'Local library unavailable';
          } else if (!this.libraryArtists.length) {
            key = 'index.sections.options.chiptune.library.artists.empty';
            fallback = 'No artist available';
          }
          placeholder.textContent = this.translate(key, fallback);
        }
      }
      if (this.trackSelect && this.trackSelect.options.length > 0) {
        const placeholder = this.trackSelect.options[0];
        if (placeholder) {
          let key = 'index.sections.options.chiptune.library.tracks.placeholder';
          let fallback = 'Select a track';
          if (this.libraryLoadErrored) {
            key = 'index.sections.options.chiptune.library.tracks.unavailable';
            fallback = 'Local library unavailable';
          } else if (!this.currentArtistId) {
            key = 'index.sections.options.chiptune.library.tracks.selectArtist';
            fallback = 'Choose an artist first';
          } else if (!this.getTracksForArtist(this.currentArtistId).length) {
            key = 'index.sections.options.chiptune.library.tracks.empty';
            fallback = 'No tracks for this artist';
          }
          placeholder.textContent = this.translate(key, fallback);
        }
      }
      this.refreshTrackSlider();
      this.updateProgramUsage(this.timeline);
    }

    findTimelineIndexAt(seconds) {
      if (!this.timeline || !Array.isArray(this.timeline.notes)) {
        return 0;
      }
      const target = Math.max(0, Number.isFinite(seconds) ? seconds : 0);
      const notes = this.timeline.notes;
      let low = 0;
      let high = notes.length;
      while (low < high) {
        const mid = Math.floor((low + high) / 2);
        const note = notes[mid];
        const start = Number.isFinite(note?.startTime) ? note.startTime : 0;
        if (start < target) {
          low = mid + 1;
        } else {
          high = mid;
        }
      }
      return low;
    }

    seekTo(seconds, options = {}) {
      if (!this.timeline) {
        return;
      }
      const { fromUser = false } = options;
      const duration = this.progressDuration || this.getTimelineDuration();
      const normalized = Number.isFinite(seconds) ? seconds : 0;
      const clamped = duration > 0 ? Math.max(0, Math.min(duration, normalized)) : 0;
      this.pendingSeekSeconds = clamped;
      const speed = this.playing ? (this.activePlaybackSpeed || this.playbackSpeed || 1) : (this.playbackSpeed || 1);
      this.updateProgressDisplay(clamped, duration, speed, { fromUser: true });
      if (this.playing) {
        this.play({ offset: clamped });
      } else if (fromUser) {
        this.updateReadyStatusMessage();
      }
    }

    analyzeTimeline(timeline) {
      if (!timeline || !Array.isArray(timeline.notes) || !timeline.notes.length) {
        return null;
      }

      let noteCount = 0;
      const melodicChannels = new Set();
      const programSet = new Set();
      let hasPercussion = false;
      let percussionCount = 0;
      let minNote = Infinity;
      let maxNote = -Infinity;
      let velocitySum = 0;
      let velocitySamples = 0;
      const events = [];

      for (const note of timeline.notes) {
        if (!note) {
          continue;
        }
        noteCount += 1;
        const channel = Number.isFinite(note.channel) ? note.channel : 0;
        const isPercussion = channel === 9;
        if (isPercussion) {
          hasPercussion = true;
          percussionCount += 1;
        } else {
          melodicChannels.add(channel);
          if (Number.isFinite(note.note)) {
            if (note.note < minNote) {
              minNote = note.note;
            }
            if (note.note > maxNote) {
              maxNote = note.note;
            }
          }
        }

        if (Number.isFinite(note.program)) {
          programSet.add(note.program);
        }

        if (Number.isFinite(note.rawVelocity)) {
          velocitySum += Math.max(0, Math.min(1, note.rawVelocity));
          velocitySamples += 1;
        } else if (Number.isFinite(note.velocity)) {
          velocitySum += Math.max(0, Math.min(1, note.velocity));
          velocitySamples += 1;
        }

        const startTime = Number.isFinite(note.startTime) ? note.startTime : 0;
        const duration = Number.isFinite(note.duration) ? Math.max(0, note.duration) : 0;
        const endTime = startTime + duration;

        events.push({ time: startTime, type: 'start', channel: isPercussion ? 'perc' : 'mel' });
        events.push({ time: endTime, type: 'end', channel: isPercussion ? 'perc' : 'mel' });
      }

      events.sort((a, b) => {
        if (a.time === b.time) {
          if (a.type === b.type) {
            return 0;
          }
          return a.type === 'end' ? -1 : 1;
        }
        return a.time - b.time;
      });

      let active = 0;
      let activeMelodic = 0;
      let peak = 0;
      let peakMelodic = 0;
      for (const event of events) {
        if (event.type === 'start') {
          active += 1;
          if (event.channel === 'mel') {
            activeMelodic += 1;
          }
          if (active > peak) {
            peak = active;
          }
          if (activeMelodic > peakMelodic) {
            peakMelodic = activeMelodic;
          }
        } else {
          active = Math.max(0, active - 1);
          if (event.channel === 'mel') {
            activeMelodic = Math.max(0, activeMelodic - 1);
          }
        }
      }

      return {
        noteCount,
        melodicChannelCount: melodicChannels.size,
        hasPercussion,
        percussionCount,
        programCount: programSet.size,
        minNote: Number.isFinite(minNote) ? minNote : null,
        maxNote: Number.isFinite(maxNote) ? maxNote : null,
        averageVelocity: velocitySamples ? velocitySum / velocitySamples : null,
        peakPolyphony: peak,
        peakMelodicPolyphony: peakMelodic,
      };
    }

    formatTimelineSummary(timeline, analysis) {
      if (!timeline) {
        return '';
      }
      const segments = [];
      const durationLabel = this.formatDurationWithSpeed(timeline.duration);
      if (durationLabel) {
        segments.push(durationLabel);
      }

      if (analysis && Number.isFinite(analysis.noteCount)) {
        const count = analysis.noteCount;
        const noteKey = count > 1
          ? 'index.sections.options.chiptune.summary.notesMultiple'
          : 'index.sections.options.chiptune.summary.notesSingle';
        const noteFallback = count > 1 ? `${count} notes` : `${count} note`;
        segments.push(this.translate(noteKey, noteFallback, { count }));

        if (analysis.melodicChannelCount || analysis.hasPercussion) {
          let channelLabel = '';
          if (analysis.melodicChannelCount) {
            const count = analysis.melodicChannelCount;
            const melodicKey = count > 1
              ? 'index.sections.options.chiptune.summary.melodicChannelsMultiple'
              : 'index.sections.options.chiptune.summary.melodicChannelsSingle';
            const melodicFallback = count > 1
              ? `${count} melodic channels`
              : `${count} melodic channel`;
            channelLabel = this.translate(melodicKey, melodicFallback, { count });
          }
          if (analysis.hasPercussion) {
            const percussionLabel = this.translate('index.sections.options.chiptune.summary.percussion', 'Percussion');
            channelLabel = channelLabel
              ? this.translate('index.sections.options.chiptune.summary.channelsWithPercussion', '{channels} + {percussion}', { channels: channelLabel, percussion: percussionLabel })
              : percussionLabel;
          }
          if (channelLabel) {
            segments.push(channelLabel);
          }
        }

        if (Number.isFinite(analysis.peakPolyphony) && analysis.peakPolyphony > 0) {
          segments.push(this.translate('index.sections.options.chiptune.summary.polyphony', 'Maximum polyphony {value}', { value: analysis.peakPolyphony }));
        }

        if (Number.isFinite(analysis.minNote) && Number.isFinite(analysis.maxNote)) {
          const rangeLabel = this.formatNoteRange(analysis.minNote, analysis.maxNote);
          if (rangeLabel) {
            segments.push(this.translate('index.sections.options.chiptune.summary.range', 'Range {range}', { range: rangeLabel }));
          }
        }
      }

      return segments.join(' · ');
    }

    formatMidiNoteName(noteNumber) {
      if (!Number.isFinite(noteNumber)) {
        return '';
      }
      const noteNames = DEFAULT_NOTE_NAMES;
      const normalized = Math.round(noteNumber);
      const pitchClass = ((normalized % 12) + 12) % 12;
      const octave = Math.floor(normalized / 12) - 1;
      return `${noteNames[pitchClass]}${octave}`;
    }

    formatNoteRange(minNote, maxNote) {
      if (!Number.isFinite(minNote) || !Number.isFinite(maxNote)) {
        return '';
      }
      const minLabel = this.formatMidiNoteName(minNote);
      const maxLabel = this.formatMidiNoteName(maxNote);
      if (!minLabel || !maxLabel) {
        return '';
      }
      if (Math.round(minNote) === Math.round(maxNote)) {
        return minLabel;
      }
      return this.translate('index.sections.options.chiptune.summary.rangeDetail', '{min} → {max}', { min: minLabel, max: maxLabel });
    }

    formatClock(seconds) {
      if (!Number.isFinite(seconds)) {
        return '0:00';
      }
      const total = Math.max(0, seconds);
      const hours = Math.floor(total / 3600);
      const minutes = Math.floor((total % 3600) / 60);
      const secs = Math.floor(total % 60);
      const secondsText = secs.toString().padStart(2, '0');
      if (hours > 0) {
        const minutesText = minutes.toString().padStart(2, '0');
        return `${hours}:${minutesText}:${secondsText}`;
      }
      return `${minutes}:${secondsText}`;
    }

    startProgressMonitor(startTime, offsetSeconds = 0, speedOverride = null, timelineDuration = null) {
      if (!this.timeline || !this.status) {
        return;
      }
      this.stopProgressMonitor();
      this.playStartTime = Number.isFinite(startTime) ? startTime : null;
      if (!Number.isFinite(this.playStartTime)) {
        return;
      }

      const speed = this.clampPlaybackSpeed(Number.isFinite(speedOverride) ? speedOverride : (this.activePlaybackSpeed || this.playbackSpeed || 1));
      const offset = Math.max(0, Number.isFinite(offsetSeconds) ? offsetSeconds : 0);
      const totalDuration = Number.isFinite(timelineDuration) && timelineDuration > 0
        ? timelineDuration
        : this.getTimelineDuration(this.timeline);
      this.playStartOffset = offset;
      this.progressMonitorSpeed = speed;
      this.progressDuration = totalDuration;
      this.updateProgressDisplay(offset, totalDuration, speed);
      const requestFrame = this.requestFrame || ((callback) => window.setTimeout(callback, 100));

      const update = () => {
        if (!this.playing || !this.audioContext) {
          this.progressRaf = null;
          return;
        }
        const now = this.audioContext.currentTime;
        const elapsed = Math.max(0, now - this.playStartTime);
        const timelineElapsed = offset + (elapsed * speed);
        const clampedElapsed = totalDuration > 0 ? Math.min(totalDuration, timelineElapsed) : timelineElapsed;
        this.updateProgressDisplay(clampedElapsed, totalDuration, speed, { allowSliderUpdate: true });
        let progressLabel = totalDuration > 0
          ? `${this.formatClock(clampedElapsed)} / ${this.formatClock(totalDuration)}`
          : this.formatClock(clampedElapsed);
        if (Math.abs(speed - 1) >= 0.01 && progressLabel) {
          progressLabel = `${progressLabel} · ${this.formatSpeedFactor(speed)}`;
        }
        const message = this.buildPlayingStatusMessage(progressLabel);
        if (this.status.textContent !== message) {
          this.status.textContent = message;
        }
        this.status.classList.remove('is-error');
        this.status.classList.add('is-success');
        this.lastStatusMessage = {
          descriptor: {
            type: 'playing',
            extra: typeof progressLabel === 'string' ? progressLabel : '',
          },
          state: 'success',
          message,
        };
        this.progressRaf = requestFrame(update);
      };

      this.progressRaf = requestFrame(update);
    }

    stopProgressMonitor() {
      if (this.progressRaf !== null) {
        const cancelFrame = this.cancelFrame || ((handle) => window.clearTimeout(handle));
        try {
          cancelFrame(this.progressRaf);
        } catch (error) {
          // Ignore cancellation issues
        }
        this.progressRaf = null;
      }
      this.playStartTime = null;
      this.playStartOffset = 0;
      this.progressMonitorSpeed = this.playbackSpeed || 1;
    }

    scheduleReadyStatusRestore(delay = 2200) {
      if (!this.readyStatusMessage || typeof window === 'undefined' || typeof window.setTimeout !== 'function') {
        return;
      }
      const timeout = Math.max(0, Number.isFinite(delay) ? delay : 0);
      const timerId = window.setTimeout(() => {
        this.pendingTimers.delete(timerId);
        if (!this.playing && this.readyStatusMessage) {
          const { message = '', descriptor = null } = this.readyStatusMessage;
          this.setStatus(message, 'success', descriptor || { type: 'ready' });
        }
      }, timeout);
      this.pendingTimers.add(timerId);
    }

    getArticulationFactor() {
      return Math.max(0, Math.min(1, (Number.isFinite(this.articulationSetting) ? this.articulationSetting : 0) / 100));
    }

    refreshLiveVoicesPitch() {
      if (!this.audioContext || !this.liveVoices || this.liveVoices.size === 0) {
        return;
      }
      const time = this.audioContext.currentTime;
      for (const voice of this.liveVoices) {
        if (!voice || voice.type !== 'melodic' || typeof voice.baseMidiNote !== 'number') {
          continue;
        }
        const frequency = this.midiNoteToFrequency(voice.baseMidiNote);
        if (!Number.isFinite(frequency) || frequency <= 0) {
          continue;
        }
        for (const osc of voice.oscillators || []) {
          if (!osc || !osc.frequency) {
            continue;
          }
          try {
            osc.frequency.setValueAtTime(frequency, time);
          } catch (error) {
            // Ignore frequency update issues
          }
        }
      }
    }

    refreshCurrentTitle() {
      if (!this.currentTitleDescriptor) {
        return;
      }
      const { key, fallback, params } = this.currentTitleDescriptor;
      this.currentTitle = this.translate(key, fallback, params);
    }

    refreshReadyStatusCache() {
      if (!this.readyStatusMessage || !this.readyStatusMessage.descriptor) {
        return;
      }
      if (this.readyStatusMessage.descriptor.type !== 'ready') {
        return;
      }
      const message = this.generateReadyStatusMessage();
      this.readyStatusMessage.message = message;
    }

    cloneStatusDescriptor(descriptor) {
      if (!descriptor || typeof descriptor !== 'object') {
        return null;
      }
      const cloned = { ...descriptor };
      if (descriptor.params && typeof descriptor.params === 'object') {
        cloned.params = { ...descriptor.params };
      }
      return cloned;
    }

    resolveStatusDescriptor(descriptor) {
      if (!descriptor || typeof descriptor !== 'object') {
        return '';
      }
      const type = descriptor.type || 'translation';
      if (type === 'translation') {
        const { key, fallback, params } = descriptor;
        return this.translate(key, fallback, params);
      }
      if (type === 'playing') {
        const extra = typeof descriptor.extra === 'string' ? descriptor.extra : '';
        return this.buildPlayingStatusMessage(extra);
      }
      if (type === 'ready') {
        return this.generateReadyStatusMessage();
      }
      if (typeof descriptor.message === 'string') {
        return descriptor.message;
      }
      return '';
    }

    setStatusMessage(key, fallback, params = {}, state = 'idle') {
      if (!key && !fallback) {
        this.setStatus('', state);
        return;
      }
      const descriptor = {
        type: 'translation',
        key,
        fallback,
      };
      if (params && typeof params === 'object' && Object.keys(params).length > 0) {
        descriptor.params = { ...params };
      }
      this.setStatus('', state, descriptor);
    }

    refreshStatusText() {
      if (!this.status || !this.lastStatusMessage) {
        return;
      }
      const { descriptor = null, message = '', state = 'idle' } = this.lastStatusMessage;
      let nextMessage = message || '';
      if (descriptor) {
        nextMessage = this.resolveStatusDescriptor(descriptor);
        if (descriptor.type === 'ready' && this.readyStatusMessage) {
          this.readyStatusMessage.message = nextMessage;
        }
      }
      this.status.textContent = nextMessage;
      this.status.classList.remove('is-error', 'is-success');
      if (state === 'error') {
        this.status.classList.add('is-error');
      } else if (state === 'success') {
        this.status.classList.add('is-success');
      }
      this.lastStatusMessage.message = nextMessage;
    }

    setStatus(message, state = 'idle', descriptor = null) {
      if (!this.status) {
        return;
      }
      let resolvedMessage = '';
      if (descriptor) {
        resolvedMessage = this.resolveStatusDescriptor(descriptor) || '';
      } else if (message != null) {
        resolvedMessage = String(message);
      }
      this.status.textContent = resolvedMessage;
      this.status.classList.remove('is-error', 'is-success');
      if (state === 'error') {
        this.status.classList.add('is-error');
      } else if (state === 'success') {
        this.status.classList.add('is-success');
      }
      if (descriptor) {
        this.lastStatusMessage = {
          descriptor: this.cloneStatusDescriptor(descriptor),
          state,
          message: resolvedMessage,
        };
      } else {
        this.lastStatusMessage = { message: resolvedMessage, state };
      }
    }

    updateButtons() {
      const hasTimeline = this.timeline && Array.isArray(this.timeline.notes) && this.timeline.notes.length > 0;
      const disabled = !hasTimeline;
      if (this.playButton) {
        this.playButton.disabled = disabled;
        this.updatePlayButtonLabel();
      }
      if (this.headerPlaybackButton) {
        this.headerPlaybackButton.disabled = disabled;
      }
      this.updateHeaderPlaybackButton();
      if (this.stopButton) {
        this.stopButton.disabled = !this.playing;
      }
      if (this.skipButton) {
        this.skipButton.disabled = !this.canSkipRandomTrack();
      }
    }

    updatePlayButtonLabel() {
      if (!this.playButton) {
        return;
      }
      const key = this.playing
        ? 'index.sections.options.chiptune.controls.pause'
        : 'index.sections.options.chiptune.controls.play';
      const fallback = this.playing ? 'Pause' : 'Play';
      const label = this.translate(key, fallback);
      if (this.playButton.textContent !== label) {
        this.playButton.textContent = label;
      }
      this.playButton.setAttribute('aria-label', label);
      if (this.playButton.dataset) {
        this.playButton.dataset.i18n = key;
        this.playButton.dataset.state = this.playing ? 'pause' : 'play';
      } else {
        this.playButton.setAttribute('data-i18n', key);
        this.playButton.setAttribute('data-state', this.playing ? 'pause' : 'play');
      }
    }

    updateHeaderPlaybackButton() {
      if (!this.headerPlaybackButton) {
        return;
      }
      const state = this.playing ? 'pause' : 'play';
      const key = this.playing
        ? 'index.sections.options.chiptune.controls.pause'
        : 'index.sections.options.chiptune.controls.play';
      const fallback = this.playing ? 'Pause' : 'Play';
      const label = this.translate(key, fallback);
      this.headerPlaybackButton.setAttribute('aria-label', label);
      this.headerPlaybackButton.setAttribute('title', label);
      this.headerPlaybackButton.setAttribute('aria-pressed', this.playing ? 'true' : 'false');
      if (this.headerPlaybackButton.dataset) {
        this.headerPlaybackButton.dataset.state = state;
        this.headerPlaybackButton.dataset.i18n = `aria-label:${key};title:${key}`;
      } else {
        this.headerPlaybackButton.setAttribute('data-state', state);
        this.headerPlaybackButton.setAttribute('data-i18n', `aria-label:${key};title:${key}`);
      }
      this.headerPlaybackButton.classList.toggle('is-playing', this.playing);
    }

    async ensureContext() {
      if (!this.audioContext) {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) {
          throw new Error(translateMessage('index.sections.options.chiptune.errors.webAudioUnavailable', 'API Web Audio non disponible dans ce navigateur.'));
        }
        this.audioContext = new AudioContextClass();
        this.waveCache = new Map();
        this.noiseBuffer = null;
        this.reverbBuffer = null;

        this.masterGain = this.audioContext.createGain();
        this.masterGain.gain.value = this.masterVolume;

        this.dryBus = this.audioContext.createGain();
        this.dryBus.gain.value = 1;

        this.reverbSend = this.audioContext.createGain();
        this.reverbSend.gain.value = 1;

        this.reverbMix = this.audioContext.createGain();
        this.reverbMix.gain.value = 0.25;

        this.reverbNode = this.createReverbNode();

        if (this.reverbNode) {
          this.reverbSend.connect(this.reverbNode);
          this.reverbNode.connect(this.reverbMix);
          this.reverbMix.connect(this.masterGain);
        } else {
          this.reverbSend = null;
          this.reverbMix = null;
        }

        this.dryBus.connect(this.masterGain);

        if (typeof this.audioContext.createDynamicsCompressor === 'function') {
          this.masterLimiter = this.audioContext.createDynamicsCompressor();
          const {
            threshold,
            knee,
            ratio,
            attack,
            release,
          } = this.limiterSettings;
          this.masterLimiter.threshold.setValueAtTime(threshold, this.audioContext.currentTime);
          this.masterLimiter.knee.setValueAtTime(knee, this.audioContext.currentTime);
          this.masterLimiter.ratio.setValueAtTime(ratio, this.audioContext.currentTime);
          this.masterLimiter.attack.setValueAtTime(attack, this.audioContext.currentTime);
          this.masterLimiter.release.setValueAtTime(release, this.audioContext.currentTime);
          this.masterGain.connect(this.masterLimiter);
          this.masterLimiter.connect(this.audioContext.destination);
        } else {
          this.masterLimiter = null;
          this.masterGain.connect(this.audioContext.destination);
        }
        this.schedulerInterval = null;
        this.schedulerState = null;
        this.setMasterVolume(this.masterVolume, false);
      }
      if (this.audioContext.state === 'suspended') {
        await this.audioContext.resume();
      }
      return this.audioContext;
    }

    async loadFromFile(file) {
      this.resetRandomPlayback();
      this.stop();
      this.setStatusMessage('index.sections.options.chiptune.status.loadingFile', 'Loading “{name}”…', { name: file.name });
      try {
        const buffer = await file.arrayBuffer();
        await this.loadFromBuffer(buffer, file.name);
      } catch (error) {
        console.error(error);
        this.setStatusMessage('index.sections.options.chiptune.status.fileError', 'Unable to read the file: {error}', { error: error.message }, 'error');
        this.timeline = null;
        this.readyStatusMessage = null;
        this.currentTitle = '';
        this.currentTitleDescriptor = null;
        this.updateButtons();
      } finally {
        if (this.fileInput) {
          this.fileInput.value = '';
        }
        if (this.trackSelect) {
          this.trackSelect.value = '';
        }
        this.visibleTracks = [];
        this.refreshStaticTexts();
      }
    }

    async loadFromLibrary(track, options = {}) {
      const { preserveRandomSession = false } = options;
      this.stop(false, { skipRandomReset: preserveRandomSession });
      if (!preserveRandomSession) {
        this.resetRandomPlayback();
      }
      const label = track.name
        || (typeof track.relativePath === 'string' && track.relativePath)
        || track.file;
      this.setStatusMessage('index.sections.options.chiptune.status.loadingTrack', 'Loading “{name}”…', { name: label });
      if (this.fileInput) {
        this.fileInput.value = '';
      }
      try {
        let buffer = null;
        if (track && track.blob && typeof track.blob.arrayBuffer === 'function') {
          buffer = await track.blob.arrayBuffer();
        } else {
          buffer = await this.fetchArrayBuffer(track.file);
        }
        await this.loadFromBuffer(buffer, label);
      } catch (error) {
        console.error(error);
        this.setStatusMessage('index.sections.options.chiptune.status.trackError', 'Unable to load the track: {error}', { error: error.message }, 'error');
        this.timeline = null;
        this.readyStatusMessage = null;
        this.currentTitle = '';
        this.currentTitleDescriptor = null;
        this.updateButtons();
      }
    }

    async fetchArrayBuffer(url) {
      const response = await fetch(url, { cache: 'no-cache' });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      return response.arrayBuffer();
    }

    async loadFromBuffer(buffer, label) {
      this.timeline = null;
      this.timelineAnalysis = null;
      this.readyStatusMessage = null;
      try {
        const midi = MidiParser.parse(buffer);
        const timeline = MidiTimeline.fromMidi(midi);
        this.timeline = timeline;
        if (label) {
          this.currentTitle = label;
          this.currentTitleDescriptor = null;
        } else {
          this.currentTitleDescriptor = {
            key: 'index.sections.options.chiptune.status.unknownTitle',
            fallback: 'Untitled MIDI',
          };
          this.currentTitle = this.translate(
            this.currentTitleDescriptor.key,
            this.currentTitleDescriptor.fallback
          );
        }
        this.timelineAnalysis = this.analyzeTimeline(timeline);
        const readyMessage = this.generateReadyStatusMessage();
        if (readyMessage) {
          this.readyStatusMessage = {
            message: readyMessage,
            descriptor: { type: 'ready' },
          };
          this.setStatus(readyMessage, 'success', this.readyStatusMessage.descriptor);
        } else {
          this.readyStatusMessage = null;
        }
        this.pendingSeekSeconds = 0;
        this.lastKnownPosition = 0;
        this.refreshProgressControls(timeline);
        this.updateProgramUsage(timeline);
      } catch (error) {
        this.timeline = null;
        this.timelineAnalysis = null;
        this.readyStatusMessage = null;
        this.currentTitle = '';
        this.currentTitleDescriptor = null;
        this.pendingSeekSeconds = 0;
        this.lastKnownPosition = 0;
        this.refreshProgressControls(null);
        this.updateProgramUsage(null);
        throw error;
      } finally {
        this.updateReadyStatusMessage();
        this.updateButtons();
        if (!this.timeline) {
          this.refreshProgressControls(null);
          this.updateProgramUsage(null);
        }
      }
    }

    async loadSoundFonts() {
      try {
        const response = await fetch('resources/chiptune/soundfonts.json', { cache: 'no-cache' });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const payload = await response.json();
        const fonts = Array.isArray(payload?.soundfonts) ? payload.soundfonts : [];
        this.populateSoundFonts(fonts, false);
      } catch (error) {
        console.error('Unable to load soundfont manifest', error);
        this.populateSoundFonts(this.soundFontFallback || [], true);
      }
    }

    populateSoundFonts(list, errored) {
      this.soundFontLoadErrored = Boolean(errored);
      const sanitized = (Array.isArray(list) ? list : [])
        .map((item) => ({
          id: typeof item?.id === 'string' ? item.id : '',
          name: typeof item?.name === 'string' && item.name ? item.name : '',
          file: typeof item?.file === 'string' ? item.file : '',
          default: Boolean(item?.default),
        }))
        .filter((item) => item.id && item.file);

      if (sanitized.length) {
        this.soundFontList = sanitized;
      } else if (!this.soundFontList || !this.soundFontList.length) {
        this.soundFontList = (this.soundFontFallback || []).map((font) => ({ ...font }));
      }

      const select = this.soundFontSelect;
      if (select) {
        while (select.options.length > 0) {
          select.remove(0);
        }
        const placeholder = document.createElement('option');
        placeholder.value = '';
        if (this.soundFontList.length) {
          placeholder.textContent = this.translate('index.sections.options.chiptune.soundfonts.placeholder', 'Choose a SoundFont');
          placeholder.disabled = true;
          placeholder.hidden = true;
        } else {
          placeholder.textContent = this.translate(
            errored
              ? 'index.sections.options.chiptune.soundfonts.noneAvailable'
              : 'index.sections.options.chiptune.soundfonts.noneDeclared',
            errored ? 'No SoundFont available' : 'No SoundFont declared'
          );
        }
        select.append(placeholder);
        for (const item of this.soundFontList) {
          const option = document.createElement('option');
          option.value = item.id;
          option.textContent = item.name || item.id;
          select.append(option);
        }
        select.disabled = this.soundFontList.length === 0;
      }

      const previousId = this.selectedSoundFontId;
      const preferred = this.soundFontList.find((item) => item.id === previousId)
        || this.soundFontList.find((item) => item.default)
        || this.soundFontList[0]
        || null;

      const selectedId = preferred ? preferred.id : null;
      this.setSoundFontSelection(selectedId, { autoLoad: false, stopPlayback: false });
    }

    setSoundFontSelection(id, options = {}) {
      const { autoLoad = false, stopPlayback = false } = options;
      const normalized = typeof id === 'string' && id ? id : null;
      this.selectedSoundFontId = normalized;
      const entry = normalized ? this.soundFontList.find((item) => item.id === normalized) : null;
      this.selectedSoundFontLabel = entry?.name || '';

      if (this.soundFontSelect) {
        const targetValue = normalized || '';
        if (this.soundFontSelect.value !== targetValue) {
          this.soundFontSelect.value = targetValue;
        }
        this.soundFontSelect.disabled = this.soundFontList.length === 0;
      }

      if (normalized && this.soundFontCache.has(normalized)) {
        this.activeSoundFont = this.soundFontCache.get(normalized) || null;
        this.activeSoundFontId = this.activeSoundFont ? normalized : null;
        this.currentSoundFontLabel = this.activeSoundFont ? (this.selectedSoundFontLabel || normalized) : '';
      } else if (!normalized || this.activeSoundFontId !== normalized) {
        this.activeSoundFont = null;
        this.activeSoundFontId = null;
        this.currentSoundFontLabel = '';
      }

      if (stopPlayback && this.playing) {
        this.stop(true);
      }

      if (autoLoad && normalized && this.engineMode === 'hifi') {
        this.ensureSoundFontReady().catch((error) => {
          console.error('Unable to load SoundFont', error);
        });
      } else {
        this.updateReadyStatusMessage();
      }
    }

    getCurrentSoundFontInfo() {
      if (!this.selectedSoundFontId) {
        return null;
      }
      return this.soundFontList.find((item) => item.id === this.selectedSoundFontId) || null;
    }

    async ensureSoundFontReady() {
      if (this.engineMode !== 'hifi') {
        return null;
      }
      const info = this.getCurrentSoundFontInfo();
      if (!info) {
        throw new Error(this.translate('index.sections.options.chiptune.errors.soundFontMissingSelection', 'No SoundFont selected.'));
      }
      if (this.soundFontCache.has(info.id)) {
        const cached = this.soundFontCache.get(info.id);
        this.activeSoundFont = cached;
        this.activeSoundFontId = info.id;
        this.currentSoundFontLabel = info.name || info.id;
        this.updateReadyStatusMessage();
        return cached;
      }
      if (this.loadingSoundFont && this.loadingSoundFont.id === info.id) {
        return this.loadingSoundFont.promise;
      }

      const loadPromise = (async () => {
        this.setStatusMessage('index.sections.options.chiptune.status.loadingSoundFont', 'Loading SoundFont “{name}”…', { name: info.name || info.id });
        try {
          const response = await fetch(info.file, { cache: 'no-cache' });
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
          }
          const data = await response.arrayBuffer();
          const font = new SoundFont(data);
          this.soundFontCache.set(info.id, font);
          if (this.selectedSoundFontId === info.id) {
            this.activeSoundFont = font;
            this.activeSoundFontId = info.id;
            this.currentSoundFontLabel = info.name || info.id;
            this.updateReadyStatusMessage();
            this.scheduleReadyStatusRestore(1600);
          }
          this.setStatusMessage('index.sections.options.chiptune.status.soundFontReady', 'SoundFont ready: {name}', { name: info.name || info.id }, 'success');
          return font;
        } catch (error) {
          this.activeSoundFont = null;
          this.activeSoundFontId = null;
          this.currentSoundFontLabel = '';
          this.setStatusMessage('index.sections.options.chiptune.status.soundFontError', 'Unable to load the SoundFont: {error}', { error: error.message }, 'error');
          throw error;
        } finally {
          if (this.loadingSoundFont && this.loadingSoundFont.id === info.id) {
            this.loadingSoundFont = null;
          }
        }
      })();

      this.loadingSoundFont = { id: info.id, promise: loadPromise };
      return loadPromise;
    }

    async loadLibrary() {
      try {
        const response = await fetch('resources/chiptune/library.json', { cache: 'no-cache' });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const payload = await response.json();
        let artists = [];
        if (Array.isArray(payload?.artists)) {
          artists = payload.artists;
        } else if (Array.isArray(payload?.tracks)) {
          artists = [
            {
              id: 'library',
              name: 'Library',
              tracks: payload.tracks,
            },
          ];
        }
        this.baseLibraryArtists = Array.isArray(artists) ? artists : [];
        this.populateLibrary(
          [...this.baseLibraryArtists, ...this.userLibraryArtists],
          false,
        );
      } catch (error) {
        console.error('Unable to load chiptune library manifest', error);
        this.baseLibraryArtists = [];
        this.populateLibrary([...this.userLibraryArtists], true);
      }
    }

    populateLibrary(artists, errored, options = {}) {
      const {
        maintainSelection = true,
        preferredArtistId = '',
        preferredTrackFile = '',
      } = options;
      this.libraryLoadErrored = Boolean(errored);
      this.resetRandomPlayback();

      const previousArtistId = maintainSelection ? this.currentArtistId : '';
      const previousTrackValue = maintainSelection && this.trackSelect ? this.trackSelect.value : '';

      const sanitizedArtists = [];
      const sanitizedTracks = [];
      const usedArtistIds = new Set();

      const fallbackArtistName = this.translate('index.sections.options.chiptune.library.unknownArtist', 'Unknown artist');

      const normalizeId = (rawId, fallbackBase) => {
        const base = typeof rawId === 'string' && rawId.trim()
          ? rawId.trim()
          : typeof fallbackBase === 'string' && fallbackBase.trim()
            ? fallbackBase.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
            : '';
        const initial = base || `artist-${sanitizedArtists.length + 1}`;
        if (!usedArtistIds.has(initial)) {
          usedArtistIds.add(initial);
          return initial;
        }
        let suffix = 2;
        let candidate = `${initial}-${suffix}`;
        while (usedArtistIds.has(candidate)) {
          suffix += 1;
          candidate = `${initial}-${suffix}`;
        }
        usedArtistIds.add(candidate);
        return candidate;
      };

      if (Array.isArray(artists)) {
        artists.forEach((artist) => {
          if (!artist || (typeof artist !== 'object')) {
            return;
          }
          const name = typeof artist.name === 'string' && artist.name.trim()
            ? artist.name.trim()
            : fallbackArtistName;
          const artistId = normalizeId(artist.id, name);
          const trackList = Array.isArray(artist.tracks) ? artist.tracks : [];
          let generatedIndex = 0;
          const normalizedTracks = trackList
            .filter(item => item && (typeof item.file === 'string' || (item.blob && typeof item.blob === 'object')))
            .map(item => {
              generatedIndex += 1;
              const fallbackId = `user-track-${artistId}-${generatedIndex}`;
              const fileId = typeof item.file === 'string' && item.file
                ? item.file
                : fallbackId;
              const trackName = typeof item.name === 'string' && item.name.trim()
                ? item.name.trim()
                : typeof item.title === 'string' && item.title.trim()
                  ? item.title.trim()
                  : fileId.replace(/^.*\//, '');
              const normalized = {
                file: fileId,
                name: trackName,
                artistId,
                artistName: name,
              };
              if (typeof item.source === 'string') {
                normalized.source = item.source;
              }
              if (item && item.blob && typeof item.blob.arrayBuffer === 'function') {
                normalized.blob = item.blob;
              }
              if (typeof item.relativePath === 'string') {
                normalized.relativePath = item.relativePath;
              }
              if (typeof item.id === 'string') {
                normalized.id = item.id;
              }
              if (typeof item.signature === 'string') {
                normalized.signature = item.signature;
              }
              if (typeof item.displayName === 'string') {
                normalized.displayName = item.displayName;
              }
              return normalized;
            });
          const sanitizedArtist = {
            id: artistId,
            name,
            tracks: normalizedTracks,
          };
          if (typeof artist.source === 'string') {
            sanitizedArtist.source = artist.source;
          }
          if (typeof artist.signature === 'string') {
            sanitizedArtist.signature = artist.signature;
          }
          if (typeof artist.sourceFolder === 'string') {
            sanitizedArtist.sourceFolder = artist.sourceFolder;
          }
          sanitizedArtists.push(sanitizedArtist);
          normalizedTracks.forEach(track => sanitizedTracks.push(track));
        });
      }

      this.libraryArtists = sanitizedArtists;
      this.libraryAllTracks = sanitizedTracks;

      const hasPreviousArtist = previousArtistId
        && sanitizedArtists.some(artist => artist.id === previousArtistId);
      const hasPreviousTrack = hasPreviousArtist
        && sanitizedArtists.some(artist => artist.id === previousArtistId
          && artist.tracks.some(track => track.file === previousTrackValue));

      let nextArtistId = '';
      if (preferredArtistId
        && sanitizedArtists.some(artist => artist.id === preferredArtistId)) {
        nextArtistId = preferredArtistId;
      } else if (hasPreviousArtist && maintainSelection) {
        nextArtistId = previousArtistId;
      }
      this.currentArtistId = nextArtistId;

      if (this.artistSelect) {
        while (this.artistSelect.options.length > 1) {
          this.artistSelect.remove(1);
        }

        for (const artist of this.libraryArtists) {
          const option = document.createElement('option');
          option.value = artist.id;
          option.textContent = artist.name || artist.id;
          this.artistSelect.append(option);
        }

        this.artistSelect.disabled = this.libraryArtists.length === 0;
        this.artistSelect.value = nextArtistId || '';
      }

      let nextTrackFile = '';
      if (preferredTrackFile
        && sanitizedTracks.some(track => track.file === preferredTrackFile)) {
        nextTrackFile = preferredTrackFile;
      } else if (hasPreviousTrack && maintainSelection) {
        nextTrackFile = previousTrackValue;
      }

      this.populateTrackSelect(nextArtistId, {
        selectedTrackFile: nextTrackFile,
        maintainSelection: maintainSelection && hasPreviousTrack && !preferredTrackFile,
      });
    }

    getTracksForArtist(artistId) {
      if (!artistId) {
        return [];
      }
      const artist = this.libraryArtists.find(item => item && item.id === artistId);
      if (!artist || !Array.isArray(artist.tracks)) {
        return [];
      }
      return artist.tracks;
    }

    populateTrackSelect(artistId, options = {}) {
      if (!this.trackSelect) {
        this.updateRandomButtons();
        this.refreshStaticTexts();
        return;
      }

      const { selectedTrackFile = '', maintainSelection = false } = options;
      const placeholder = this.trackSelect.options[0] || null;
      const previousValue = this.trackSelect.value;

      while (this.trackSelect.options.length > 1) {
        this.trackSelect.remove(1);
      }

      const tracks = this.getTracksForArtist(artistId);
      for (const track of tracks) {
        const option = document.createElement('option');
        option.value = track.file;
        option.textContent = track.name || track.file.replace(/^.*\//, '');
        this.trackSelect.append(option);
      }

      let targetValue = '';
      if (selectedTrackFile && tracks.some(track => track.file === selectedTrackFile)) {
        targetValue = selectedTrackFile;
      } else if (maintainSelection && previousValue && tracks.some(track => track.file === previousValue)) {
        targetValue = previousValue;
      }

      this.trackSelect.value = targetValue;
      this.trackSelect.disabled = tracks.length === 0;
      this.visibleTracks = Array.isArray(tracks) ? tracks.slice() : [];
      this.updateTrackNavigation(this.visibleTracks, targetValue);

      if (placeholder) {
        placeholder.disabled = false;
        placeholder.hidden = false;
      }

      this.updateRandomButtons();
      this.refreshStaticTexts();
    }

    updateTrackNavigation(tracks, selectedValue = '') {
      if (this.trackSelect) {
        this.trackSelect.removeAttribute('size');
        this.trackSelect.classList.remove('chiptune-select--scrollable');
      }
      if (!Array.isArray(tracks)) {
        this.visibleTracks = [];
        this.refreshTrackSlider('');
        return;
      }
      const selected = selectedValue || (this.trackSelect ? this.trackSelect.value : '');
      this.refreshTrackSlider(selected);
    }

    setTrackSliderValue(position, total) {
      if (!this.trackSliderValue) {
        return;
      }
      if (!Number.isFinite(total) || total <= 0) {
        this.trackSliderValue.textContent = this.translate(
          'index.sections.options.chiptune.trackSlider.empty',
          'No track available'
        );
        return;
      }
      const normalizedPosition = Math.min(total, Math.max(1, position + 1));
      this.trackSliderValue.textContent = this.translate(
        'index.sections.options.chiptune.trackSlider.position',
        '{current} / {total}',
        { current: normalizedPosition, total }
      );
    }

    refreshTrackSlider(selectedValue = '') {
      if (!this.trackSlider) {
        return;
      }
      const tracks = Array.isArray(this.visibleTracks) ? this.visibleTracks : [];
      if (!tracks.length) {
        this.trackSlider.min = 0;
        this.trackSlider.max = 0;
        this.trackSlider.value = 0;
        this.trackSlider.step = 1;
        this.trackSlider.disabled = true;
        this.setTrackSliderValue(0, 0);
        return;
      }
      const total = tracks.length;
      const currentValue = selectedValue || (this.trackSelect ? this.trackSelect.value : '');
      const currentIndex = tracks.findIndex(track => track.file === currentValue);
      const sliderIndex = currentIndex >= 0 ? currentIndex : Math.max(0, Math.min(total - 1, (this.trackSelect ? this.trackSelect.selectedIndex - 1 : 0)));
      this.trackSlider.min = 0;
      this.trackSlider.max = total - 1;
      this.trackSlider.step = 1;
      this.trackSlider.value = sliderIndex;
      this.trackSlider.disabled = total <= 1;
      this.setTrackSliderValue(sliderIndex, total);
    }

    handleTrackSliderInput(commitSelection) {
      if (!this.trackSlider || !this.trackSelect) {
        return;
      }
      const tracks = Array.isArray(this.visibleTracks) ? this.visibleTracks : [];
      if (!tracks.length) {
        return;
      }
      const sliderIndex = Math.max(0, Math.min(tracks.length - 1, Number.parseInt(this.trackSlider.value, 10) || 0));
      const track = tracks[sliderIndex];
      if (!track) {
        return;
      }
      if (this.trackSelect.value !== track.file) {
        this.trackSelect.value = track.file;
      }
      this.setTrackSliderValue(sliderIndex, tracks.length);
      if (commitSelection) {
        const event = new Event('change', { bubbles: true });
        this.trackSelect.dispatchEvent(event);
      }
    }

    applyArtistSelection(artistId, options = {}) {
      const { selectedTrackFile = '', maintainSelection = false } = options;
      const artistExists = this.libraryArtists.some(artist => artist.id === artistId);
      const resolvedArtistId = artistExists ? artistId : '';
      this.currentArtistId = resolvedArtistId;
      if (this.artistSelect) {
        const targetValue = resolvedArtistId || '';
        if (this.artistSelect.value !== targetValue) {
          this.artistSelect.value = targetValue;
        }
      }
      this.populateTrackSelect(resolvedArtistId, {
        selectedTrackFile,
        maintainSelection,
      });
    }

    findTrackByFile(file, artistId = '') {
      if (!file) {
        return null;
      }
      const artistTracks = artistId ? this.getTracksForArtist(artistId) : [];
      const matchInArtist = artistTracks.find(track => track.file === file);
      if (matchInArtist) {
        return matchInArtist;
      }
      return this.libraryAllTracks.find(track => track.file === file) || null;
    }

    resetRandomPlayback() {
      this.randomPlaybackMode = null;
      this.randomPlaybackArtistId = null;
      this.randomPlaybackQueue = [];
      this.randomPlaybackCurrentTrack = null;
      this.randomPlaybackPending = false;
      this.lastRandomPlaybackTrack = null;
      this.updateButtons();
    }

    isRandomPlaybackSession(mode, artistId) {
      if (!this.randomPlaybackMode || this.randomPlaybackMode !== mode) {
        return false;
      }
      if (mode === 'artist') {
        const normalizedId = typeof artistId === 'string' ? artistId : '';
        return this.randomPlaybackArtistId === normalizedId;
      }
      return true;
    }

    shuffleArray(array) {
      const list = array;
      for (let i = list.length - 1; i > 0; i -= 1) {
        const j = Math.floor(Math.random() * (i + 1));
        const temp = list[i];
        list[i] = list[j];
        list[j] = temp;
      }
      return list;
    }

    getRandomPlaybackPool(mode, artistId) {
      const normalizedId = mode === 'artist' ? (typeof artistId === 'string' ? artistId : '') : '';
      const pool = mode === 'artist'
        ? this.getTracksForArtist(normalizedId)
        : this.libraryAllTracks;
      return Array.isArray(pool) ? pool.filter(track => track && track.file) : [];
    }

    getRandomPlaybackCandidates(excludeTrack = null) {
      if (!this.randomPlaybackMode) {
        return [];
      }
      const mode = this.randomPlaybackMode;
      const artistId = mode === 'artist' ? this.randomPlaybackArtistId : '';
      let available = this.getRandomPlaybackPool(mode, artistId);
      if (!available.length) {
        return [];
      }
      const lastFile = excludeTrack && excludeTrack.file ? excludeTrack.file : '';
      if (lastFile) {
        const filtered = available.filter(track => track.file !== lastFile);
        if (filtered.length) {
          available = filtered;
        }
      }
      return available;
    }

    refillRandomPlaybackQueue(excludeTrack = null) {
      const candidates = this.getRandomPlaybackCandidates(excludeTrack);
      if (!candidates.length) {
        return false;
      }
      this.randomPlaybackQueue = this.shuffleArray(Array.from(candidates));
      return this.randomPlaybackQueue.length > 0;
    }

    initializeRandomPlayback(mode, artistId) {
      const normalizedId = mode === 'artist' ? (typeof artistId === 'string' ? artistId : '') : '';
      const available = this.getRandomPlaybackPool(mode, normalizedId);
      if (!available.length) {
        this.setStatusMessage(
          'index.sections.options.chiptune.status.randomUnavailable',
          'No tracks available for random playback.',
          {},
          'error',
        );
        return false;
      }
      this.randomPlaybackMode = mode;
      this.randomPlaybackArtistId = mode === 'artist' ? normalizedId : '';
      this.randomPlaybackQueue = this.shuffleArray(Array.from(available));
      this.randomPlaybackCurrentTrack = null;
      this.randomPlaybackPending = false;
      this.lastRandomPlaybackTrack = null;
      this.updateButtons();
      return true;
    }

    dequeueNextRandomTrack() {
      if (!Array.isArray(this.randomPlaybackQueue) || !this.randomPlaybackQueue.length) {
        return null;
      }
      return this.randomPlaybackQueue.shift() || null;
    }

    shouldContinueRandomPlayback() {
      if (!this.randomPlaybackMode) {
        return false;
      }
      if (this.randomPlaybackPending) {
        return false;
      }
      if (Array.isArray(this.randomPlaybackQueue) && this.randomPlaybackQueue.length > 0) {
        return true;
      }
      return this.refillRandomPlaybackQueue(this.lastRandomPlaybackTrack);
    }

    completeRandomPlayback() {
      if (!this.randomPlaybackMode) {
        return;
      }
      this.setStatusMessage(
        'index.sections.options.chiptune.status.randomComplete',
        'Random playback complete. All tracks have been played.',
        {},
        'success',
      );
      this.scheduleReadyStatusRestore();
      this.resetRandomPlayback();
    }

    async playRandomTrack(artistId = null, options = {}) {
      const { continueSession = false } = options;
      const mode = artistId ? 'artist' : 'all';
      const normalizedArtistId = mode === 'artist'
        ? (typeof artistId === 'string' ? artistId : '')
        : '';

      if (!continueSession || !this.isRandomPlaybackSession(mode, normalizedArtistId)) {
        if (!this.initializeRandomPlayback(mode, normalizedArtistId)) {
          return;
        }
      } else if (!this.randomPlaybackQueue.length) {
        if (!this.refillRandomPlaybackQueue(this.lastRandomPlaybackTrack)) {
          this.completeRandomPlayback();
          return;
        }
      }

      let track = this.dequeueNextRandomTrack();
      if (!track) {
        if (!this.refillRandomPlaybackQueue(this.lastRandomPlaybackTrack)) {
          this.completeRandomPlayback();
          return;
        }
        track = this.dequeueNextRandomTrack();
      }
      if (!track) {
        this.completeRandomPlayback();
        return;
      }

      this.randomPlaybackPending = true;
      this.randomPlaybackCurrentTrack = track;
      this.lastRandomPlaybackTrack = track;
      this.updateButtons();
      try {
        this.applyArtistSelection(track.artistId, { selectedTrackFile: track.file });
        await this.loadFromLibrary(track, { preserveRandomSession: true });
        const hasTimeline = this.timeline && Array.isArray(this.timeline.notes) && this.timeline.notes.length;
        if (!hasTimeline) {
          if (Array.isArray(this.randomPlaybackQueue) && this.randomPlaybackQueue.length) {
            this.randomPlaybackPending = false;
            this.updateButtons();
            await this.playRandomTrack(
              this.randomPlaybackMode === 'artist' ? this.randomPlaybackArtistId : null,
              { continueSession: true },
            );
          } else {
            this.completeRandomPlayback();
          }
          return;
        }
        await this.play();
      } catch (error) {
        console.error('Unable to play random track', error);
      } finally {
        this.randomPlaybackPending = false;
        this.updateButtons();
      }
    }

    canSkipRandomTrack() {
      if (!this.randomPlaybackMode) {
        return false;
      }
      if (this.randomPlaybackPending) {
        return false;
      }
      if (Array.isArray(this.randomPlaybackQueue) && this.randomPlaybackQueue.length > 0) {
        return true;
      }
      const candidates = this.getRandomPlaybackCandidates(this.lastRandomPlaybackTrack);
      return candidates.length > 0;
    }

    async skipRandomTrack() {
      if (!this.canSkipRandomTrack()) {
        return;
      }
      const nextArtistId = this.randomPlaybackMode === 'artist'
        ? this.randomPlaybackArtistId
        : null;
      this.randomPlaybackPending = true;
      this.updateButtons();
      if (this.playing) {
        this.stop(false, { skipRandomReset: true });
      }
      try {
        await this.playRandomTrack(nextArtistId, { continueSession: true });
      } catch (error) {
        console.error('Unable to skip random track', error);
        this.randomPlaybackPending = false;
        this.updateButtons();
      }
    }

    updateRandomButtons() {
      const hasTracks = Array.isArray(this.libraryAllTracks) && this.libraryAllTracks.length > 0;
      if (this.randomAllButton) {
        this.randomAllButton.disabled = !hasTracks;
      }
      const currentArtistTracks = this.getTracksForArtist(this.currentArtistId);
      if (this.randomArtistButton) {
        this.randomArtistButton.disabled = !currentArtistTracks.length;
      }
    }

    midiNoteToFrequency(note) {
      const transpose = Number.isFinite(this.transposeSemitones) ? this.transposeSemitones : 0;
      const fine = Number.isFinite(this.fineDetuneCents) ? this.fineDetuneCents : 0;
      const baseNote = note + transpose;
      const base = 440 * Math.pow(2, (baseNote - 69) / 12);
      if (fine === 0) {
        return base;
      }
      return base * Math.pow(2, fine / 1200);
    }

    getPulseWave(dutyCycle) {
      if (!this.audioContext) {
        return null;
      }
      const clamped = Math.min(0.95, Math.max(0.05, dutyCycle));
      const key = `pulse:${clamped.toFixed(3)}`;
      if (this.waveCache.has(key)) {
        return this.waveCache.get(key);
      }
      const harmonics = 32;
      const real = new Float32Array(harmonics);
      const imag = new Float32Array(harmonics);
      for (let i = 1; i < harmonics; i += 1) {
        const numerator = Math.sin(Math.PI * i * clamped);
        const coefficient = (2 / (i * Math.PI)) * numerator;
        real[i] = coefficient;
        imag[i] = 0;
      }
      const wave = this.audioContext.createPeriodicWave(real, imag, { disableNormalization: true });
      this.waveCache.set(key, wave);
      return wave;
    }

    getSccWave() {
      if (!this.audioContext || !this.sccWaveform) {
        return null;
      }
      const sampleRate = Math.round(this.audioContext.sampleRate || 0);
      const key = `scc:${sampleRate}`;
      if (this.waveCache.has(key)) {
        return this.waveCache.get(key);
      }
      let wave = null;
      if (typeof this.sccWaveform.getPeriodicWave === 'function') {
        wave = this.sccWaveform.getPeriodicWave(this.audioContext);
      }
      if (!wave && typeof this.sccWaveform.createPeriodicWave === 'function') {
        wave = this.sccWaveform.createPeriodicWave(this.audioContext);
      }
      if (wave) {
        this.waveCache.set(key, wave);
      }
      return wave;
    }

    createPeriodicWaveFromTable(table) {
      if (!this.audioContext || !table || !table.length) {
        return null;
      }
      const harmonics = table.length;
      const real = new Float32Array(harmonics);
      const imag = new Float32Array(harmonics);
      for (let k = 0; k < harmonics; k += 1) {
        let sumReal = 0;
        let sumImag = 0;
        for (let n = 0; n < harmonics; n += 1) {
          const sample = table[n];
          const phase = (2 * Math.PI * k * n) / harmonics;
          sumReal += sample * Math.cos(phase);
          sumImag += sample * Math.sin(phase);
        }
        real[k] = sumReal / harmonics;
        imag[k] = sumImag / harmonics;
      }
      real[0] = 0;
      imag[0] = 0;
      return this.audioContext.createPeriodicWave(real, imag, { disableNormalization: true });
    }

    getN163Wave(identifier = 'default') {
      if (!this.audioContext) {
        return null;
      }
      const table = N163_WAVETABLES[identifier] || N163_WAVETABLES.default;
      if (!table) {
        return null;
      }
      const key = `n163:${identifier}`;
      if (this.waveCache.has(key)) {
        return this.waveCache.get(key);
      }
      const wave = this.createPeriodicWaveFromTable(table);
      if (wave) {
        this.waveCache.set(key, wave);
      }
      return wave;
    }

    shouldUseN163Waveform(instrument, layer) {
      if (this.engineMode !== 'n163') {
        return false;
      }
      const layerPreference = layer?.waveform;
      if (layerPreference === 'analog' || layerPreference === 'original') {
        return false;
      }
      const layerId = layer?.n163Wave;
      const instrumentId = instrument?.n163Wave;
      return Boolean(layerId || instrumentId);
    }

    shouldUseSccWaveform(instrument, layer) {
      if (this.engineMode !== 'scc') {
        return false;
      }
      if (!this.sccWaveform) {
        return false;
      }
      if (instrument?.disableSccWaveform || layer?.disableSccWaveform) {
        return false;
      }
      const layerPreference = layer?.waveform;
      if (layerPreference === 'scc') {
        return true;
      }
      if (layerPreference === 'analog' || layerPreference === 'original') {
        return false;
      }
      if (layerPreference && layerPreference !== 'auto') {
        return false;
      }
      const instrumentPreference = instrument?.waveform;
      if (instrumentPreference === 'analog' || instrumentPreference === 'original') {
        return false;
      }
      if (instrumentPreference === 'scc') {
        return true;
      }
      return true;
    }

    getNoiseBuffer() {
      if (!this.audioContext) {
        return null;
      }
      if (this.noiseBuffer && this.noiseBuffer.sampleRate === this.audioContext.sampleRate) {
        return this.noiseBuffer;
      }
      const durationSeconds = 1.5;
      const length = Math.max(1, Math.floor(this.audioContext.sampleRate * durationSeconds));
      const buffer = this.audioContext.createBuffer(1, length, this.audioContext.sampleRate);
      const data = buffer.getChannelData(0);
      for (let i = 0; i < length; i += 1) {
        data[i] = (Math.random() * 2) - 1;
      }
      this.noiseBuffer = buffer;
      return buffer;
    }

    createReverbNode() {
      if (!this.audioContext || typeof this.audioContext.createConvolver !== 'function') {
        return null;
      }
      const convolver = this.audioContext.createConvolver();
      convolver.normalize = true;
      if (!this.reverbBuffer || this.reverbBuffer.sampleRate !== this.audioContext.sampleRate) {
        this.reverbBuffer = this.buildReverbImpulse(0.9, 1.6);
      }
      convolver.buffer = this.reverbBuffer;
      return convolver;
    }

    buildReverbImpulse(durationSeconds = 1.4, decay = 2.1) {
      if (!this.audioContext) {
        return null;
      }
      const sampleRate = this.audioContext.sampleRate;
      const length = Math.max(1, Math.floor(durationSeconds * sampleRate));
      const impulse = this.audioContext.createBuffer(2, length, sampleRate);
      const left = impulse.getChannelData(0);
      const right = impulse.getChannelData(1);
      for (let i = 0; i < length; i += 1) {
        const progress = i / length;
        const envelope = Math.pow(1 - progress, decay);
        const noiseL = ((Math.random() * 2) - 1) * envelope;
        const noiseR = ((Math.random() * 2) - 1) * envelope;
        left[i] = noiseL;
        right[i] = (noiseL * 0.35) + (noiseR * 0.65);
      }
      return impulse;
    }

    getInstrumentSettings(note) {
      const program = Number.isFinite(note.program) ? note.program : 0;
      const family = Math.floor(program / 8);

      const baseEnvelope = {
        attack: Math.max(0.0035, Math.min(0.06, 0.006 + (note.rawVelocity ? (0.03 * (1 - note.rawVelocity)) : 0))),
        decay: 0.12,
        sustain: 0.4,
        release: Math.max(0.14, Math.min(0.5, note.duration * 0.55)),
      };

      const mergeEnvelope = (overrides = {}) => ({
        ...baseEnvelope,
        ...(overrides || {}),
      });

      if (this.engineMode === 'hifi') {
        const hiFiInstrument = this.getHiFiInstrumentSettings(note, { program, family, baseEnvelope });
        if (hiFiInstrument) {
          return hiFiInstrument;
        }
        return null;
      }

      if (this.engineMode === 'n163') {
        return this.getN163InstrumentSettings(note, { family, baseEnvelope });
      }

      if (this.engineMode === 'sid') {
        return this.getSidInstrumentSettings(note, { family, baseEnvelope });
      }

      if (this.engineMode === 'ym2413') {
        return this.getYm2413InstrumentSettings(note, { family, baseEnvelope });
      }

      const defaultWaveform = this.engineMode === 'scc' ? 'scc' : 'analog';

      const withLayerDefaults = (sourceLayers, fallbackLayers) => {
        const baseLayers = Array.isArray(sourceLayers) && sourceLayers.length
          ? sourceLayers
          : fallbackLayers;
        return baseLayers.map(layer => ({
          waveform: defaultWaveform,
          ...layer,
        }));
      };

      const createPulseDefinition = (options = {}) => {
        const {
          gain = 0.74,
          layers,
          filter,
          lfo,
          reverbSend = 0.14,
          envelope,
        } = options;
        return {
          gain,
          waveform: options.waveform ?? defaultWaveform,
          layers: withLayerDefaults(layers, [
            { type: 'pulse', dutyCycle: 0.35, detune: -7, gain: 0.55 },
            { type: 'pulse', dutyCycle: 0.65, detune: 7, gain: 0.55 },
          ]),
          filter: filter ?? { type: 'lowpass', frequency: 5800, Q: 0.65 },
          lfo: lfo ?? { rate: 4.6, vibratoDepth: 4.2, tremoloDepth: 0.03 },
          reverbSend,
          envelope: mergeEnvelope(envelope),
        };
      };

      const createSoftDefinition = (options = {}) => {
        const {
          gain = 0.68,
          layers,
          filter,
          lfo,
          reverbSend = 0.1,
          envelope,
        } = options;
        return {
          gain,
          waveform: options.waveform ?? defaultWaveform,
          layers: withLayerDefaults(layers, [
            { type: 'triangle', detune: -6, gain: 0.5 },
            { type: 'triangle', detune: 6, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.5, detune: 0, gain: 0.25 },
          ]),
          filter: filter ?? { type: 'lowpass', frequency: 5200, Q: 0.6 },
          lfo: lfo ?? { rate: 4.2, vibratoDepth: 3.2, tremoloDepth: 0.02 },
          reverbSend,
          envelope: mergeEnvelope(envelope),
        };
      };

      const createBassDefinition = (options = {}) => {
        const {
          gain = 0.8,
          layers,
          filter,
          lfo,
          reverbSend = 0.08,
          envelope,
        } = options;
        return {
          gain,
          waveform: options.waveform ?? defaultWaveform,
          layers: withLayerDefaults(layers, [
            { type: 'sine', detune: -12, gain: 0.6 },
            { type: 'sine', detune: 0, gain: 0.55 },
            { type: 'triangle', detune: 0, gain: 0.35 },
          ]),
          filter: filter ?? { type: 'lowpass', frequency: 3200, Q: 0.75 },
          lfo: lfo ?? { rate: 4.8, vibratoDepth: 2.4, tremoloDepth: 0 },
          reverbSend,
          envelope: mergeEnvelope({ sustain: 0.48, release: 0.22, ...(envelope || {}) }),
        };
      };

      const createPluckDefinition = (options = {}) => {
        const {
          gain = 0.72,
          layers,
          filter,
          lfo,
          reverbSend = 0.1,
          envelope,
        } = options;
        return {
          gain,
          waveform: options.waveform ?? defaultWaveform,
          layers: withLayerDefaults(layers, [
            { type: 'pulse', dutyCycle: 0.28, detune: -5, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.72, detune: 5, gain: 0.5 },
          ]),
          filter: filter ?? { type: 'highpass', frequency: 180, Q: 0.6 },
          lfo: lfo ?? { rate: 5.6, vibratoDepth: 2.2, tremoloDepth: 0.05 },
          reverbSend,
          envelope: mergeEnvelope({ decay: 0.06, sustain: 0.18, release: 0.16, ...(envelope || {}) }),
        };
      };

      const definitions = {
        default: createPulseDefinition({
          envelope: { decay: 0.08, sustain: 0.36, release: 0.18 },
          reverbSend: 0.12,
        }),
        0: createPulseDefinition({
          lfo: { rate: 5.2, vibratoDepth: 5.5, tremoloDepth: 0.04 },
          envelope: { decay: 0.1, sustain: 0.34, release: 0.2 },
          reverbSend: 0.13,
        }),
        1: createSoftDefinition({
          envelope: { decay: 0.11, sustain: 0.32, release: 0.2 },
          reverbSend: 0.11,
        }),
        2: createSoftDefinition({
          layers: [
            { type: 'triangle', detune: -7, gain: 0.46 },
            { type: 'triangle', detune: 7, gain: 0.46 },
            { type: 'pulse', dutyCycle: 0.45, detune: 0, gain: 0.28 },
          ],
          lfo: { rate: 4.8, vibratoDepth: 4.8, tremoloDepth: 0.03 },
          envelope: { sustain: 0.44, release: 0.22 },
          reverbSend: 0.12,
        }),
        3: createPulseDefinition({
          filter: { type: 'bandpass', frequency: 4600, Q: 0.85 },
          lfo: { rate: 5.4, vibratoDepth: 4.5, tremoloDepth: 0.04 },
          envelope: { decay: 0.09, sustain: 0.34, release: 0.2 },
          reverbSend: 0.12,
        }),
        4: createPluckDefinition({
          filter: { type: 'bandpass', frequency: 3600, Q: 0.9 },
          envelope: { decay: 0.05, sustain: 0.16, release: 0.15 },
          reverbSend: 0.09,
        }),
        5: createBassDefinition({
          lfo: { rate: 4.4, vibratoDepth: 1.8, tremoloDepth: 0.02 },
          envelope: { sustain: 0.5, release: 0.24 },
        }),
        6: createBassDefinition({
          layers: [
            { type: 'sine', detune: -9, gain: 0.58 },
            { type: 'sine', detune: 9, gain: 0.58 },
            { type: 'triangle', detune: 0, gain: 0.35 },
          ],
          filter: { type: 'lowpass', frequency: 2800, Q: 0.8 },
          envelope: { sustain: 0.52, release: 0.26 },
          reverbSend: 0.09,
        }),
        7: createPulseDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.32, detune: -6, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.68, detune: 6, gain: 0.5 },
            { type: 'triangle', detune: 0, gain: 0.22 },
          ],
          lfo: { rate: 5.8, vibratoDepth: 6, tremoloDepth: 0.05 },
          envelope: { decay: 0.1, sustain: 0.4, release: 0.22 },
          reverbSend: 0.13,
        }),
        8: createPluckDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.24, detune: -4, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.76, detune: 4, gain: 0.5 },
            { type: 'triangle', detune: 0, gain: 0.2 },
          ],
          lfo: { rate: 5.8, vibratoDepth: 2.6, tremoloDepth: 0.05 },
          envelope: { decay: 0.05, sustain: 0.16, release: 0.14 },
          reverbSend: 0.11,
        }),
        9: createSoftDefinition({
          lfo: { rate: 5.4, vibratoDepth: 4.5, tremoloDepth: 0.03 },
          envelope: { decay: 0.12, sustain: 0.36, release: 0.22 },
          reverbSend: 0.13,
        }),
        10: createPulseDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.38, detune: -7, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.62, detune: 7, gain: 0.5 },
            { type: 'triangle', detune: 0, gain: 0.24 },
          ],
          filter: { type: 'lowpass', frequency: 5400, Q: 0.7 },
          lfo: { rate: 5.6, vibratoDepth: 5.5, tremoloDepth: 0.04 },
          envelope: { decay: 0.1, sustain: 0.4, release: 0.22 },
          reverbSend: 0.12,
        }),
        11: createBassDefinition({
          layers: [
            { type: 'sine', detune: -12, gain: 0.6 },
            { type: 'sine', detune: 0, gain: 0.55 },
            { type: 'pulse', dutyCycle: 0.5, detune: 12, gain: 0.25 },
          ],
          filter: { type: 'lowpass', frequency: 2600, Q: 0.85 },
          envelope: { sustain: 0.5, release: 0.28 },
          reverbSend: 0.1,
        }),
        12: createSoftDefinition({
          layers: [
            { type: 'triangle', detune: -5, gain: 0.48 },
            { type: 'triangle', detune: 5, gain: 0.48 },
            { type: 'pulse', dutyCycle: 0.52, detune: 0, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 5000, Q: 0.65 },
          lfo: { rate: 4.2, vibratoDepth: 2.8, tremoloDepth: 0.02 },
          envelope: { sustain: 0.36, release: 0.2 },
          reverbSend: 0.11,
        }),
        13: createPulseDefinition({
          filter: { type: 'bandpass', frequency: 3400, Q: 1 },
          lfo: { rate: 6.2, vibratoDepth: 4.2, tremoloDepth: 0.05 },
          envelope: { decay: 0.08, sustain: 0.3, release: 0.18 },
          reverbSend: 0.1,
        }),
        14: createSoftDefinition({
          lfo: { rate: 4.8, vibratoDepth: 4, tremoloDepth: 0.03 },
          envelope: { decay: 0.1, sustain: 0.34, release: 0.2 },
          reverbSend: 0.12,
        }),
        15: createPulseDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.26, detune: -6, gain: 0.52 },
            { type: 'pulse', dutyCycle: 0.74, detune: 6, gain: 0.52 },
            { type: 'triangle', detune: 0, gain: 0.2 },
          ],
          lfo: { rate: 5.4, vibratoDepth: 5.8, tremoloDepth: 0.05 },
          envelope: { decay: 0.09, sustain: 0.38, release: 0.22 },
          reverbSend: 0.13,
        }),
      };

      const definition = definitions[family] || definitions.default;
      const articulation = this.getArticulationFactor();

      const envelope = {
        ...definition.envelope,
      };
      const attackBase = Number.isFinite(envelope.attack) ? envelope.attack : baseEnvelope.attack;
      const decayBase = Number.isFinite(envelope.decay) ? envelope.decay : baseEnvelope.decay;
      const sustainBase = Number.isFinite(envelope.sustain) ? envelope.sustain : baseEnvelope.sustain;
      const releaseBase = Number.isFinite(envelope.release) ? envelope.release : baseEnvelope.release;

      const attackScale = 1 - (articulation * 0.4);
      const decayScale = 1 - (articulation * 0.42);
      const sustainCeiling = 0.45 + ((1 - articulation) * 0.4);
      const sustainFloor = 0.18 + ((1 - articulation) * 0.08);
      const releaseFloor = 0.18 + ((1 - articulation) * 0.1);
      const releaseCeiling = 0.6 + ((1 - articulation) * 0.5);
      const durationFactor = 0.4 + ((1 - articulation) * 0.25);
      const durationRelease = Math.min(releaseCeiling, Math.max(releaseFloor, note.duration * durationFactor));

      envelope.attack = Math.max(0.0035, attackBase * attackScale);
      envelope.decay = Math.max(0.04, decayBase * decayScale);
      const sustainValue = Number.isFinite(sustainBase) ? sustainBase : sustainCeiling;
      envelope.sustain = Math.max(sustainFloor, Math.min(sustainCeiling, sustainValue));
      const releaseValue = Number.isFinite(releaseBase) ? releaseBase : durationRelease;
      envelope.release = Math.max(releaseFloor, Math.min(releaseCeiling, Math.max(releaseValue, durationRelease)));

      let filter = null;
      if (definition.filter) {
        filter = { ...definition.filter };
        if (filter.type === 'lowpass') {
          const brightnessBoost = 1 + (articulation * 0.4);
          const baseFrequency = Number.isFinite(filter.frequency) ? filter.frequency : 2600;
          filter.frequency = Math.min(6500, baseFrequency * brightnessBoost);
          if (Number.isFinite(filter.Q)) {
            filter.Q = Math.max(0.55, Math.min(1.3, filter.Q * (0.95 - (articulation * 0.12))));
          }
        }
      }

      let lfo = null;
      if (definition.lfo) {
        lfo = { ...definition.lfo };
        if (Number.isFinite(lfo.vibratoDepth)) {
          lfo.vibratoDepth = Math.max(0, lfo.vibratoDepth * (0.35 + ((1 - articulation) * 0.65)));
          if (lfo.vibratoDepth < 0.5) {
            lfo.vibratoDepth = 0;
          }
        }
        if (Number.isFinite(lfo.tremoloDepth)) {
          lfo.tremoloDepth = Math.max(0, lfo.tremoloDepth * (0.4 + ((1 - articulation) * 0.6)));
          if (lfo.tremoloDepth < 0.02) {
            lfo.tremoloDepth = 0;
          }
        }
      }

      const baseReverbSend = Number.isFinite(definition.reverbSend)
        ? definition.reverbSend
        : this.reverbDefaultSend;
      const reverbScale = 0.45 + ((1 - articulation) * 0.55);
      const reverbSend = Math.max(0, baseReverbSend * reverbScale);

      const baseGain = Number.isFinite(definition.gain)
        ? definition.gain
        : Number.isFinite(definition.volume)
          ? definition.volume
          : 1;
      const gainAdjustment = 0.92 + (articulation * 0.16);
      const gain = Math.min(1.05, baseGain * gainAdjustment);

      const instrument = {
        ...definition,
        gain,
        envelope,
        reverbSend,
      };

      if (filter) {
        instrument.filter = filter;
      }
      if (lfo) {
        instrument.lfo = lfo;
      }

      return instrument;
    }

    getHiFiInstrumentSettings(note, context = {}) {
      if (!this.activeSoundFont || !this.selectedSoundFontId) {
        return null;
      }
      let program = Number.isFinite(context.program) ? context.program : 0;
      const baseEnvelope = context.baseEnvelope || {};
      const info = this.getCurrentSoundFontInfo();
      if (!info) {
        return null;
      }
      const midiNote = Number.isFinite(note.note) ? Math.round(note.note) : 60;
      const key = Math.max(0, Math.min(127, midiNote));
      const velocityBase = Number.isFinite(note.rawVelocity)
        ? Math.max(0, Math.min(1, note.rawVelocity))
        : (Number.isFinite(note.velocity) ? Math.max(0, Math.min(1, note.velocity)) : 0.6);
      const velocity = Math.max(0, Math.min(127, Math.round(velocityBase * 127)));
      const bank = note.channel === 9 ? 128 : 0;

      if (this.pianoOverrideEnabled && bank !== 128) {
        const normalizedProgram = Math.max(0, Math.min(127, Math.round(program)));
        if (normalizedProgram <= 7) {
          program = 0;
        }
      }

      let regions;
      try {
        regions = this.activeSoundFont.getRegions({ bank, program, key, velocity });
      } catch (error) {
        console.error('Unable to resolve SoundFont regions', error);
        return null;
      }

      if (!Array.isArray(regions) || !regions.length) {
        return null;
      }

      const preparedRegions = regions.map((region) => ({
        ...region,
        gain: Number.isFinite(region.attenuation) ? Math.pow(10, -region.attenuation / 200) : 1,
      }));

      const averagePan = preparedRegions.reduce((acc, region) => acc + (Number.isFinite(region.pan) ? region.pan : 0), 0) / (preparedRegions.length || 1);

      let envelope = null;
      for (const region of preparedRegions) {
        if (region.volumeEnvelope) {
          envelope = {
            ...baseEnvelope,
            ...region.volumeEnvelope,
          };
          break;
        }
      }

      if (!envelope) {
        envelope = { ...baseEnvelope };
      }

      const reverbSend = preparedRegions.reduce((max, region) => {
        const value = Number.isFinite(region.reverbSend) ? region.reverbSend : 0;
        return Math.max(max, value);
      }, this.reverbDefaultSend);

      return {
        type: 'soundfont',
        gain: 1,
        envelope,
        reverbSend: Number.isFinite(reverbSend) ? reverbSend : this.reverbDefaultSend,
        pan: averagePan || 0,
        regions: preparedRegions,
      };
    }

    getN163InstrumentSettings(note, context) {
      const { family, baseEnvelope } = context || {};

      const envelopeSource = baseEnvelope || {
        attack: 0.008,
        decay: 0.08,
        sustain: 0.36,
        release: 0.22,
      };

      const mergeEnvelope = (overrides = {}) => ({
        ...envelopeSource,
        ...(overrides || {}),
      });

      const createWaveDefinition = (options = {}) => {
        const {
          gain = 0.68,
          layers,
          filter,
          lfo,
          reverbSend = 0.08,
          envelope,
          wave = 'default',
          chorus,
        } = options;
        const resolvedLayers = Array.isArray(layers) && layers.length
          ? layers
          : [
            { type: 'sine', n163Wave: wave, detune: -4, gain: 0.5 },
            { type: 'sine', n163Wave: wave, detune: 4, gain: 0.5 },
          ];
        const normalizedLayers = resolvedLayers.map(layer => ({
          ...layer,
          n163Wave: layer?.n163Wave || wave,
        }));
        const definition = {
          gain,
          waveform: 'n163',
          n163Wave: wave,
          layers: normalizedLayers,
          filter: filter ? { ...filter } : null,
          lfo: lfo ? { ...lfo } : { rate: 4.6, vibratoDepth: 3.2, tremoloDepth: 0.02, waveform: 'triangle' },
          reverbSend,
          envelope: mergeEnvelope(envelope),
        };
        if (chorus) {
          definition.chorus = { ...chorus };
        }
        return definition;
      };

      const definitions = {
        default: createWaveDefinition({
          wave: 'default',
          layers: [
            { type: 'sine', n163Wave: 'default', detune: -3, gain: 0.52 },
            { type: 'sine', n163Wave: 'default', detune: 3, gain: 0.52 },
          ],
          lfo: { rate: 4.8, vibratoDepth: 3.4, tremoloDepth: 0.02, waveform: 'triangle' },
          envelope: { decay: 0.09, sustain: 0.34, release: 0.18 },
          reverbSend: 0.08,
        }),
        0: createWaveDefinition({
          wave: 'bright',
          layers: [
            { type: 'sine', n163Wave: 'bright', detune: -4, gain: 0.5 },
            { type: 'sine', n163Wave: 'bright', detune: 4, gain: 0.5 },
            { type: 'sine', n163Wave: 'bell', detune: 9, gain: 0.16 },
          ],
          lfo: { rate: 5.2, vibratoDepth: 3.6, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.08, sustain: 0.3, release: 0.18 },
          reverbSend: 0.07,
        }),
        1: createWaveDefinition({
          wave: 'hollow',
          layers: [
            { type: 'sine', n163Wave: 'hollow', detune: -5, gain: 0.5 },
            { type: 'sine', n163Wave: 'hollow', detune: 5, gain: 0.5 },
          ],
          filter: { type: 'highpass', frequency: 240, Q: 0.65 },
          envelope: { decay: 0.06, sustain: 0.22, release: 0.16 },
          reverbSend: 0.06,
        }),
        2: createWaveDefinition({
          wave: 'reed',
          layers: [
            { type: 'sine', n163Wave: 'reed', detune: -3, gain: 0.5 },
            { type: 'sine', n163Wave: 'reed', detune: 3, gain: 0.5 },
            { type: 'sine', n163Wave: 'hollow', detune: 12, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 3200, Q: 0.8 },
          lfo: { rate: 3.8, vibratoDepth: 2.4, tremoloDepth: 0.02, waveform: 'triangle' },
          envelope: { sustain: 0.48, release: 0.3 },
          reverbSend: 0.07,
        }),
        3: createWaveDefinition({
          wave: 'pulse',
          layers: [
            { type: 'sine', n163Wave: 'pulse', detune: -6, gain: 0.52 },
            { type: 'sine', n163Wave: 'pulse', detune: 6, gain: 0.52 },
          ],
          filter: { type: 'highpass', frequency: 260, Q: 0.7 },
          lfo: { rate: 5.2, vibratoDepth: 3.6, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.07, sustain: 0.26, release: 0.18 },
          reverbSend: 0.06,
        }),
        4: createWaveDefinition({
          wave: 'bass',
          layers: [
            { type: 'sine', n163Wave: 'bass', detune: -7, gain: 0.58 },
            { type: 'sine', n163Wave: 'bass', detune: 7, gain: 0.58 },
          ],
          filter: { type: 'lowpass', frequency: 2600, Q: 0.7 },
          lfo: { rate: 4.2, vibratoDepth: 1.8, tremoloDepth: 0, waveform: 'triangle' },
          envelope: { sustain: 0.46, release: 0.22 },
          reverbSend: 0.05,
        }),
        5: createWaveDefinition({
          wave: 'airy',
          layers: [
            { type: 'sine', n163Wave: 'airy', detune: -4, gain: 0.48 },
            { type: 'sine', n163Wave: 'airy', detune: 4, gain: 0.48 },
            { type: 'sine', n163Wave: 'reed', detune: 9, gain: 0.18 },
          ],
          filter: { type: 'lowpass', frequency: 3800, Q: 0.9 },
          lfo: { rate: 4.4, vibratoDepth: 3.2, tremoloDepth: 0.03, waveform: 'sine' },
          envelope: { decay: 0.11, sustain: 0.42, release: 0.24 },
          reverbSend: 0.09,
        }),
        6: createWaveDefinition({
          wave: 'airy',
          layers: [
            { type: 'sine', n163Wave: 'airy', detune: -5, gain: 0.5 },
            { type: 'sine', n163Wave: 'airy', detune: 5, gain: 0.5 },
            { type: 'sine', n163Wave: 'bright', detune: 10, gain: 0.16 },
          ],
          filter: { type: 'lowpass', frequency: 3400, Q: 0.85 },
          lfo: { rate: 4.2, vibratoDepth: 3.4, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.1, sustain: 0.44, release: 0.3 },
          reverbSend: 0.12,
        }),
        7: createWaveDefinition({
          wave: 'brass',
          layers: [
            { type: 'sine', n163Wave: 'brass', detune: -6, gain: 0.5 },
            { type: 'sine', n163Wave: 'brass', detune: 6, gain: 0.5 },
            { type: 'sine', n163Wave: 'bright', detune: 12, gain: 0.2 },
          ],
          filter: { type: 'bandpass', frequency: 3600, Q: 0.95 },
          lfo: { rate: 5, vibratoDepth: 4.6, tremoloDepth: 0.04, waveform: 'triangle' },
          envelope: { decay: 0.11, sustain: 0.36, release: 0.22 },
          reverbSend: 0.09,
        }),
        8: createWaveDefinition({
          wave: 'reed',
          layers: [
            { type: 'sine', n163Wave: 'reed', detune: -4, gain: 0.5 },
            { type: 'sine', n163Wave: 'reed', detune: 4, gain: 0.5 },
            { type: 'sine', n163Wave: 'hollow', detune: 9, gain: 0.18 },
          ],
          filter: { type: 'bandpass', frequency: 3200, Q: 0.9 },
          lfo: { rate: 4.4, vibratoDepth: 3.8, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.12, sustain: 0.42, release: 0.24 },
          reverbSend: 0.08,
        }),
        9: createWaveDefinition({
          wave: 'bell',
          layers: [
            { type: 'sine', n163Wave: 'bell', detune: -2, gain: 0.5 },
            { type: 'sine', n163Wave: 'bell', detune: 2, gain: 0.5 },
            { type: 'sine', n163Wave: 'airy', detune: 12, gain: 0.2 },
          ],
          lfo: { rate: 4.6, vibratoDepth: 2.4, tremoloDepth: 0.04, waveform: 'sine' },
          envelope: { decay: 0.12, sustain: 0.4, release: 0.26 },
          reverbSend: 0.1,
        }),
        10: createWaveDefinition({
          wave: 'pulse',
          layers: [
            { type: 'sine', n163Wave: 'pulse', detune: -5, gain: 0.5 },
            { type: 'sine', n163Wave: 'pulse', detune: 5, gain: 0.5 },
            { type: 'sine', n163Wave: 'bright', detune: 12, gain: 0.22 },
          ],
          lfo: { rate: 5.4, vibratoDepth: 4.8, tremoloDepth: 0.04, waveform: 'triangle' },
          envelope: { decay: 0.1, sustain: 0.34, release: 0.18 },
          reverbSend: 0.08,
        }),
        11: createWaveDefinition({
          wave: 'airy',
          layers: [
            { type: 'sine', n163Wave: 'airy', detune: -3, gain: 0.5 },
            { type: 'sine', n163Wave: 'airy', detune: 3, gain: 0.5 },
            { type: 'sine', n163Wave: 'bell', detune: 7, gain: 0.18 },
          ],
          filter: { type: 'lowpass', frequency: 3000, Q: 0.8 },
          lfo: { rate: 3.6, vibratoDepth: 2.8, tremoloDepth: 0.03, waveform: 'sine' },
          envelope: { attack: 0.012, decay: 0.15, sustain: 0.46, release: 0.32 },
          reverbSend: 0.13,
        }),
        12: createWaveDefinition({
          wave: 'hollow',
          layers: [
            { type: 'sine', n163Wave: 'hollow', detune: -4, gain: 0.48 },
            { type: 'sine', n163Wave: 'bell', detune: 4, gain: 0.48 },
          ],
          filter: { type: 'bandpass', frequency: 2800, Q: 1.1 },
          lfo: { rate: 5.6, vibratoDepth: 4.2, tremoloDepth: 0.04, waveform: 'sine' },
          envelope: { decay: 0.11, sustain: 0.28, release: 0.24 },
          reverbSend: 0.1,
        }),
        13: createWaveDefinition({
          wave: 'reed',
          layers: [
            { type: 'sine', n163Wave: 'reed', detune: -6, gain: 0.5 },
            { type: 'sine', n163Wave: 'pulse', detune: 6, gain: 0.45 },
          ],
          filter: { type: 'bandpass', frequency: 3400, Q: 0.95 },
          lfo: { rate: 4.8, vibratoDepth: 3.4, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.09, sustain: 0.3, release: 0.2 },
          reverbSend: 0.07,
        }),
        14: createWaveDefinition({
          wave: 'bright',
          layers: [
            { type: 'sine', n163Wave: 'bright', detune: -4, gain: 0.5 },
            { type: 'sine', n163Wave: 'bright', detune: 4, gain: 0.5 },
          ],
          filter: { type: 'highpass', frequency: 280, Q: 0.65 },
          envelope: { decay: 0.06, sustain: 0.2, release: 0.14 },
          reverbSend: 0.06,
        }),
        15: createWaveDefinition({
          wave: 'bell',
          layers: [
            { type: 'sine', n163Wave: 'bell', detune: -3, gain: 0.5 },
            { type: 'sine', n163Wave: 'bell', detune: 3, gain: 0.5 },
            { type: 'sine', n163Wave: 'airy', detune: 9, gain: 0.16 },
          ],
          filter: { type: 'bandpass', frequency: 3600, Q: 1.05 },
          lfo: { rate: 5.8, vibratoDepth: 4.4, tremoloDepth: 0.05, waveform: 'triangle' },
          envelope: { decay: 0.12, sustain: 0.28, release: 0.24 },
          reverbSend: 0.11,
        }),
      };

      const definition = Object.prototype.hasOwnProperty.call(definitions, family)
        ? definitions[family]
        : definitions.default;

      const articulation = this.getArticulationFactor();

      const envelope = {
        ...definition.envelope,
      };

      const attackBase = Number.isFinite(envelope.attack) ? envelope.attack : envelopeSource.attack;
      const decayBase = Number.isFinite(envelope.decay) ? envelope.decay : envelopeSource.decay;
      const sustainBase = Number.isFinite(envelope.sustain) ? envelope.sustain : envelopeSource.sustain;
      const releaseBase = Number.isFinite(envelope.release) ? envelope.release : envelopeSource.release;

      const attackScale = 0.75 + ((1 - articulation) * 0.25);
      const decayScale = 0.6 + ((1 - articulation) * 0.35);
      const sustainCeiling = 0.42 + ((1 - articulation) * 0.36);
      const sustainFloor = 0.14 + ((1 - articulation) * 0.08);
      const releaseFloor = 0.12 + ((1 - articulation) * 0.08);
      const releaseCeiling = 0.5 + ((1 - articulation) * 0.45);
      const durationFactor = 0.35 + ((1 - articulation) * 0.3);
      const durationRelease = Math.min(releaseCeiling, Math.max(releaseFloor, note.duration * durationFactor));

      envelope.attack = Math.max(0.0025, attackBase * attackScale);
      envelope.decay = Math.max(0.03, decayBase * decayScale);
      const sustainValue = Number.isFinite(sustainBase) ? sustainBase : sustainCeiling;
      envelope.sustain = Math.max(sustainFloor, Math.min(sustainCeiling, sustainValue));
      const releaseValue = Number.isFinite(releaseBase) ? releaseBase : durationRelease;
      envelope.release = Math.max(releaseFloor, Math.min(releaseCeiling, Math.max(releaseValue, durationRelease)));

      let filter = null;
      if (definition.filter) {
        filter = { ...definition.filter };
        if (filter.type === 'lowpass') {
          const brightnessBoost = 1 + (articulation * 0.5);
          const baseFrequency = Number.isFinite(filter.frequency) ? filter.frequency : 3200;
          filter.frequency = Math.min(6200, baseFrequency * brightnessBoost);
          if (Number.isFinite(filter.Q)) {
            filter.Q = Math.max(0.55, Math.min(1.2, filter.Q * (0.85 - (articulation * 0.15))));
          }
        } else if (filter.type === 'bandpass' && Number.isFinite(filter.frequency)) {
          const spread = 1 + (articulation * 0.2);
          filter.frequency = Math.min(7200, filter.frequency * spread);
        }
      }

      let lfo = null;
      if (definition.lfo) {
        lfo = { ...definition.lfo };
        if (Number.isFinite(lfo.vibratoDepth)) {
          lfo.vibratoDepth = Math.max(0, lfo.vibratoDepth * (0.4 + ((1 - articulation) * 0.6)));
          if (lfo.vibratoDepth < 0.4) {
            lfo.vibratoDepth = 0;
          }
        }
        if (Number.isFinite(lfo.tremoloDepth)) {
          lfo.tremoloDepth = Math.max(0, lfo.tremoloDepth * (0.35 + ((1 - articulation) * 0.55)));
          if (lfo.tremoloDepth < 0.015) {
            lfo.tremoloDepth = 0;
          }
        }
      }

      const baseReverbSend = Number.isFinite(definition.reverbSend)
        ? definition.reverbSend
        : this.reverbDefaultSend;
      const reverbScale = 0.3 + ((1 - articulation) * 0.5);
      const reverbSend = Math.max(0, baseReverbSend * reverbScale);

      const baseGain = Number.isFinite(definition.gain)
        ? definition.gain
        : Number.isFinite(definition.volume)
          ? definition.volume
          : 1;
      const gainAdjustment = 0.9 + (articulation * 0.14);
      const gain = Math.min(1, baseGain * gainAdjustment);

      const instrument = {
        ...definition,
        gain,
        envelope,
        reverbSend,
        layers: (definition.layers || []).map(layer => ({
          ...layer,
          n163Wave: layer?.n163Wave || definition.n163Wave || 'default',
        })),
      };

      if (filter) {
        instrument.filter = filter;
      }
      if (lfo) {
        instrument.lfo = lfo;
      }

      return instrument;
    }

    getSidInstrumentSettings(note, context) {
      const { family, baseEnvelope } = context || {};

      const envelopeSource = baseEnvelope || {
        attack: 0.0055,
        decay: 0.09,
        sustain: 0.38,
        release: 0.24,
      };

      const mergeEnvelope = (overrides = {}) => ({
        ...envelopeSource,
        ...(overrides || {}),
      });

      const createSidDefinition = (options = {}) => {
        const {
          gain = 0.74,
          layers,
          filter,
          lfo,
          reverbSend = 0.08,
          envelope,
          chorus,
          pulseWidth,
        } = options;

        const resolvedLayers = Array.isArray(layers) && layers.length
          ? layers
          : [
            { type: 'sawtooth', detune: -5, gain: 0.5 },
            { type: 'sawtooth', detune: 5, gain: 0.5 },
          ];

        const mappedLayers = resolvedLayers.map((layer) => {
          const normalized = { ...layer };
          if (typeof normalized.waveform !== 'string') {
            normalized.waveform = 'analog';
          }
          if (!Number.isFinite(normalized.gain)) {
            normalized.gain = 1;
          }
          if (!Number.isFinite(normalized.detune)) {
            normalized.detune = 0;
          }
          if (!Number.isFinite(normalized.dutyCycle) && Number.isFinite(pulseWidth) && normalized.type === 'pulse') {
            normalized.dutyCycle = pulseWidth;
          }
          return normalized;
        });

        const definition = {
          gain,
          waveform: 'sid',
          dutyCycle: Number.isFinite(pulseWidth) ? pulseWidth : undefined,
          layers: mappedLayers,
          filter: filter ? { ...filter } : { type: 'lowpass', frequency: 4600, Q: 0.75 },
          lfo: lfo ? { ...lfo } : { rate: 4.9, vibratoDepth: 3.8, tremoloDepth: 0.03, waveform: 'triangle' },
          reverbSend,
          envelope: mergeEnvelope(envelope),
        };

        if (!Number.isFinite(definition.dutyCycle)) {
          delete definition.dutyCycle;
        }

        if (chorus) {
          definition.chorus = { ...chorus };
        }

        return definition;
      };

      const definitions = {
        default: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -5, gain: 0.5 },
            { type: 'sawtooth', detune: 5, gain: 0.5 },
            { type: 'triangle', detune: 12, gain: 0.18 },
          ],
          filter: { type: 'lowpass', frequency: 4800, Q: 0.85 },
          lfo: { rate: 5.1, vibratoDepth: 4.2, tremoloDepth: 0.03, waveform: 'triangle' },
          reverbSend: 0.09,
          envelope: { decay: 0.09, sustain: 0.38, release: 0.24 },
        }),
        0: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.34, detune: -6, gain: 0.48 },
            { type: 'pulse', dutyCycle: 0.66, detune: 6, gain: 0.48 },
            { type: 'triangle', detune: 12, gain: 0.2 },
          ],
          filter: { type: 'lowpass', frequency: 4200, Q: 0.7 },
          lfo: { rate: 5, vibratoDepth: 3.6, tremoloDepth: 0.02, waveform: 'triangle' },
          envelope: { decay: 0.08, sustain: 0.3, release: 0.18 },
          reverbSend: 0.08,
        }),
        1: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.25, detune: -7, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.75, detune: 7, gain: 0.5 },
          ],
          filter: { type: 'highpass', frequency: 240, Q: 0.65 },
          envelope: { decay: 0.06, sustain: 0.18, release: 0.14 },
          reverbSend: 0.06,
        }),
        2: createSidDefinition({
          layers: [
            { type: 'triangle', detune: -4, gain: 0.5 },
            { type: 'triangle', detune: 4, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.5, detune: 0, gain: 0.25 },
          ],
          filter: { type: 'lowpass', frequency: 3600, Q: 0.8 },
          lfo: { rate: 4.6, vibratoDepth: 3.8, tremoloDepth: 0.04, waveform: 'sine' },
          envelope: { attack: 0.01, decay: 0.12, sustain: 0.5, release: 0.32 },
          reverbSend: 0.1,
        }),
        3: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -6, gain: 0.45 },
            { type: 'sawtooth', detune: 6, gain: 0.45 },
            { type: 'pulse', dutyCycle: 0.5, detune: 12, gain: 0.2 },
          ],
          filter: { type: 'highpass', frequency: 220, Q: 0.7 },
          envelope: { decay: 0.07, sustain: 0.24, release: 0.18 },
          reverbSend: 0.07,
        }),
        4: createSidDefinition({
          gain: 0.82,
          layers: [
            { type: 'pulse', dutyCycle: 0.52, detune: -12, gain: 0.6 },
            { type: 'triangle', detune: 0, gain: 0.45 },
          ],
          filter: { type: 'lowpass', frequency: 2400, Q: 0.85 },
          lfo: { rate: 4.2, vibratoDepth: 2.2, tremoloDepth: 0, waveform: 'triangle' },
          envelope: { decay: 0.1, sustain: 0.48, release: 0.26 },
          reverbSend: 0.05,
        }),
        5: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -5, gain: 0.48 },
            { type: 'sawtooth', detune: 5, gain: 0.48 },
            { type: 'triangle', detune: 12, gain: 0.2 },
          ],
          filter: { type: 'lowpass', frequency: 3800, Q: 0.9 },
          lfo: { rate: 4.4, vibratoDepth: 3.4, tremoloDepth: 0.03, waveform: 'triangle' },
          chorus: { mix: 0.35, rate: 0.55, depth: 0.012, delay: 0.024, feedback: 0.1, spread: 0.5 },
          envelope: { decay: 0.12, sustain: 0.42, release: 0.32 },
          reverbSend: 0.12,
        }),
        6: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -7, gain: 0.46 },
            { type: 'sawtooth', detune: 7, gain: 0.46 },
            { type: 'triangle', detune: 19, gain: 0.18 },
          ],
          filter: { type: 'lowpass', frequency: 3600, Q: 0.85 },
          lfo: { rate: 4.8, vibratoDepth: 4.2, tremoloDepth: 0.04, waveform: 'triangle' },
          chorus: { mix: 0.4, rate: 0.6, depth: 0.014, delay: 0.026, feedback: 0.12, spread: 0.55 },
          envelope: { decay: 0.16, sustain: 0.5, release: 0.42 },
          reverbSend: 0.14,
        }),
        7: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.42, detune: -6, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.58, detune: 6, gain: 0.5 },
            { type: 'sawtooth', detune: 12, gain: 0.22 },
          ],
          filter: { type: 'bandpass', frequency: 3400, Q: 0.95 },
          lfo: { rate: 5.2, vibratoDepth: 4.8, tremoloDepth: 0.04, waveform: 'triangle' },
          envelope: { decay: 0.1, sustain: 0.34, release: 0.22 },
          reverbSend: 0.09,
        }),
        8: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.44, detune: -4, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.56, detune: 4, gain: 0.5 },
            { type: 'triangle', detune: 9, gain: 0.2 },
          ],
          filter: { type: 'bandpass', frequency: 3200, Q: 0.9 },
          lfo: { rate: 4.6, vibratoDepth: 3.6, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.12, sustain: 0.4, release: 0.26 },
          reverbSend: 0.08,
        }),
        9: createSidDefinition({
          layers: [
            { type: 'triangle', detune: -3, gain: 0.5 },
            { type: 'triangle', detune: 3, gain: 0.5 },
            { type: 'sine', detune: 12, gain: 0.18 },
          ],
          filter: { type: 'bandpass', frequency: 2800, Q: 0.9 },
          lfo: { rate: 3.8, vibratoDepth: 2.2, tremoloDepth: 0.02, waveform: 'sine' },
          envelope: { attack: 0.012, decay: 0.14, sustain: 0.48, release: 0.34 },
          reverbSend: 0.12,
        }),
        10: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.3, detune: -5, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.7, detune: 5, gain: 0.5 },
            { type: 'sawtooth', detune: 12, gain: 0.24 },
          ],
          lfo: { rate: 5.6, vibratoDepth: 5.2, tremoloDepth: 0.05, waveform: 'triangle' },
          envelope: { decay: 0.1, sustain: 0.32, release: 0.2 },
          reverbSend: 0.08,
        }),
        11: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -4, gain: 0.45 },
            { type: 'sawtooth', detune: 4, gain: 0.45 },
            { type: 'triangle', detune: 14, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 3200, Q: 0.8 },
          lfo: { rate: 4, vibratoDepth: 3.4, tremoloDepth: 0.04, waveform: 'sine' },
          chorus: { mix: 0.42, rate: 0.5, depth: 0.015, delay: 0.028, feedback: 0.14, spread: 0.6 },
          envelope: { attack: 0.012, decay: 0.18, sustain: 0.52, release: 0.46 },
          reverbSend: 0.15,
        }),
        12: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -8, gain: 0.4 },
            { type: 'sawtooth', detune: 8, gain: 0.4 },
            { type: 'sawtooth', detune: 20, gain: 0.2 },
          ],
          filter: { type: 'highpass', frequency: 420, Q: 0.75 },
          lfo: { rate: 5.8, vibratoDepth: 4.6, tremoloDepth: 0.04, waveform: 'triangle' },
          envelope: { decay: 0.11, sustain: 0.26, release: 0.24 },
          reverbSend: 0.07,
        }),
        13: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.36, detune: -6, gain: 0.48 },
            { type: 'pulse', dutyCycle: 0.64, detune: 6, gain: 0.48 },
            { type: 'triangle', detune: 12, gain: 0.18 },
          ],
          filter: { type: 'bandpass', frequency: 3000, Q: 1 },
          lfo: { rate: 4.6, vibratoDepth: 3.2, tremoloDepth: 0.03, waveform: 'triangle' },
          envelope: { decay: 0.1, sustain: 0.28, release: 0.22 },
          reverbSend: 0.08,
        }),
        14: createSidDefinition({
          layers: [
            { type: 'pulse', dutyCycle: 0.22, detune: -4, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.78, detune: 4, gain: 0.5 },
            { type: 'sawtooth', detune: 9, gain: 0.2 },
          ],
          filter: { type: 'highpass', frequency: 320, Q: 0.8 },
          envelope: { decay: 0.09, sustain: 0.22, release: 0.18 },
          reverbSend: 0.06,
        }),
        15: createSidDefinition({
          layers: [
            { type: 'sawtooth', detune: -12, gain: 0.42 },
            { type: 'sawtooth', detune: 12, gain: 0.42 },
            { type: 'triangle', detune: 24, gain: 0.2 },
          ],
          filter: { type: 'bandpass', frequency: 3600, Q: 1.05 },
          lfo: { rate: 6.2, vibratoDepth: 5.6, tremoloDepth: 0.05, waveform: 'triangle' },
          envelope: { decay: 0.12, sustain: 0.3, release: 0.26 },
          reverbSend: 0.1,
        }),
      };

      const definition = Object.prototype.hasOwnProperty.call(definitions, family)
        ? definitions[family]
        : definitions.default;

      const articulation = this.getArticulationFactor();

      const envelope = {
        ...definition.envelope,
      };

      const attackBase = Number.isFinite(envelope.attack) ? envelope.attack : envelopeSource.attack;
      const decayBase = Number.isFinite(envelope.decay) ? envelope.decay : envelopeSource.decay;
      const sustainBase = Number.isFinite(envelope.sustain) ? envelope.sustain : envelopeSource.sustain;
      const releaseBase = Number.isFinite(envelope.release) ? envelope.release : envelopeSource.release;

      const attackScale = 0.68 + ((1 - articulation) * 0.32);
      const decayScale = 0.55 + ((1 - articulation) * 0.35);
      const sustainCeiling = 0.5 + ((1 - articulation) * 0.28);
      const sustainFloor = 0.14 + ((1 - articulation) * 0.12);
      const releaseFloor = 0.1 + ((1 - articulation) * 0.12);
      const releaseCeiling = 0.6 + ((1 - articulation) * 0.4);
      const durationFactor = 0.38 + ((1 - articulation) * 0.32);
      const durationRelease = Math.min(releaseCeiling, Math.max(releaseFloor, note.duration * durationFactor));

      envelope.attack = Math.max(0.0028, attackBase * attackScale);
      envelope.decay = Math.max(0.028, decayBase * decayScale);
      const sustainValue = Number.isFinite(sustainBase) ? sustainBase : sustainCeiling;
      envelope.sustain = Math.max(sustainFloor, Math.min(sustainCeiling, sustainValue));
      const releaseValue = Number.isFinite(releaseBase) ? releaseBase : durationRelease;
      envelope.release = Math.max(releaseFloor, Math.min(releaseCeiling, Math.max(releaseValue, durationRelease)));

      let filter = null;
      if (definition.filter) {
        filter = { ...definition.filter };
        if (filter.type === 'lowpass') {
          const brightnessBoost = 0.78 + (articulation * 0.65);
          const baseFrequency = Number.isFinite(filter.frequency) ? filter.frequency : 3600;
          filter.frequency = Math.min(7200, baseFrequency * brightnessBoost);
          if (Number.isFinite(filter.Q)) {
            const resonanceScale = 0.9 + (articulation * 0.35);
            filter.Q = Math.max(0.55, Math.min(1.6, filter.Q * resonanceScale));
          }
        } else if (filter.type === 'bandpass' && Number.isFinite(filter.frequency)) {
          const spread = 0.9 + (articulation * 0.5);
          filter.frequency = Math.min(7800, filter.frequency * spread);
          if (Number.isFinite(filter.Q)) {
            filter.Q = Math.max(0.6, Math.min(1.6, filter.Q * (0.85 + (articulation * 0.25))));
          }
        } else if (filter.type === 'highpass' && Number.isFinite(filter.frequency)) {
          const openness = 0.85 + (articulation * 0.4);
          filter.frequency = Math.min(4200, filter.frequency * openness);
        }
      }

      let lfo = null;
      if (definition.lfo) {
        lfo = { ...definition.lfo };
        if (Number.isFinite(lfo.vibratoDepth)) {
          lfo.vibratoDepth = Math.max(0, lfo.vibratoDepth * (0.5 + ((1 - articulation) * 0.55)));
          if (lfo.vibratoDepth < 0.35) {
            lfo.vibratoDepth = 0;
          }
        }
        if (Number.isFinite(lfo.tremoloDepth)) {
          lfo.tremoloDepth = Math.max(0, lfo.tremoloDepth * (0.4 + ((1 - articulation) * 0.5)));
          if (lfo.tremoloDepth < 0.015) {
            lfo.tremoloDepth = 0;
          }
        }
      }

      const baseReverbSend = Number.isFinite(definition.reverbSend)
        ? definition.reverbSend
        : this.reverbDefaultSend;
      const reverbScale = 0.28 + ((1 - articulation) * 0.48);
      const reverbSend = Math.max(0, baseReverbSend * reverbScale);

      const baseGain = Number.isFinite(definition.gain)
        ? definition.gain
        : Number.isFinite(definition.volume)
          ? definition.volume
          : 1;
      const gainAdjustment = 0.86 + (articulation * 0.18);
      const gain = Math.min(1.05, baseGain * gainAdjustment);

      const instrument = {
        ...definition,
        gain,
        envelope,
        reverbSend,
        layers: (definition.layers || []).map(layer => ({
          ...layer,
        })),
      };

      if (filter) {
        instrument.filter = filter;
      }
      if (lfo) {
        instrument.lfo = lfo;
      }

      if (!Number.isFinite(instrument.dutyCycle)) {
        delete instrument.dutyCycle;
      }

      return instrument;
    }

    getYm2413InstrumentSettings(note, context) {
      const { family, baseEnvelope } = context || {};

      const envelopeSource = baseEnvelope || {
        attack: 0.01,
        decay: 0.12,
        sustain: 0.4,
        release: 0.24,
      };

      const mergeEnvelope = (overrides = {}) => ({
        ...envelopeSource,
        ...(overrides || {}),
      });

      const createFmDefinition = (options = {}) => {
        const {
          gain = 0.76,
          layers,
          filter,
          lfo,
          reverbSend = 0.14,
          envelope,
          chorus,
        } = options;
        const definition = {
          gain,
          waveform: 'fm',
          layers: Array.isArray(layers) && layers.length ? layers : [
            { type: 'sine', detune: -4, gain: 0.5 },
            { type: 'sine', detune: 4, gain: 0.5 },
            { type: 'sine', detune: 12, gain: 0.22 },
          ],
          filter: filter ?? { type: 'lowpass', frequency: 5200, Q: 0.85 },
          lfo: lfo ?? { rate: 5.3, vibratoDepth: 5.1, tremoloDepth: 0.04 },
          reverbSend,
          envelope: mergeEnvelope(envelope),
        };
        if (chorus) {
          definition.chorus = { ...chorus };
        }
        return definition;
      };

      const definitions = {
        default: createFmDefinition({
          layers: [
            { type: 'sine', detune: -4, gain: 0.5 },
            { type: 'sine', detune: 4, gain: 0.5 },
            { type: 'sine', detune: 12, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 5200, Q: 0.85 },
          lfo: { rate: 5.3, vibratoDepth: 5.1, tremoloDepth: 0.04 },
          reverbSend: 0.12,
        }),
        0: createFmDefinition({
          layers: [
            { type: 'sine', detune: -6, gain: 0.48 },
            { type: 'sine', detune: 6, gain: 0.48 },
            { type: 'sine', detune: 12, gain: 0.24 },
            { type: 'sine', detune: 19, gain: 0.12 },
          ],
          filter: { type: 'lowpass', frequency: 4800, Q: 0.9 },
          envelope: { decay: 0.14, sustain: 0.26, release: 0.24 },
          reverbSend: 0.11,
        }),
        1: createFmDefinition({
          layers: [
            { type: 'sine', detune: -8, gain: 0.42 },
            { type: 'sine', detune: 8, gain: 0.42 },
            { type: 'sine', detune: 19, gain: 0.18 },
          ],
          filter: { type: 'highpass', frequency: 240, Q: 0.7 },
          envelope: { decay: 0.08, sustain: 0.18, release: 0.18 },
          reverbSend: 0.08,
        }),
        2: createFmDefinition({
          layers: [
            { type: 'sine', detune: -2, gain: 0.5 },
            { type: 'sine', detune: 2, gain: 0.5 },
            { type: 'sine', detune: 7, gain: 0.28 },
          ],
          filter: { type: 'lowpass', frequency: 3800, Q: 1.05 },
          envelope: { decay: 0.18, sustain: 0.5, release: 0.42 },
          lfo: { rate: 4.6, vibratoDepth: 3.8, tremoloDepth: 0.05 },
          chorus: { mix: 0.3, rate: 0.6, depth: 0.014, delay: 0.024, feedback: 0.12, spread: 0.5 },
          reverbSend: 0.15,
        }),
        3: createFmDefinition({
          layers: [
            { type: 'sine', detune: -5, gain: 0.46 },
            { type: 'sine', detune: 5, gain: 0.46 },
            { type: 'sine', detune: 17, gain: 0.2 },
          ],
          filter: { type: 'highpass', frequency: 320, Q: 0.8 },
          envelope: { decay: 0.11, sustain: 0.22, release: 0.2 },
          reverbSend: 0.09,
        }),
        4: createFmDefinition({
          layers: [
            { type: 'sine', detune: -12, gain: 0.6 },
            { type: 'sine', detune: -5, gain: 0.42 },
            { type: 'sine', detune: 0, gain: 0.32 },
          ],
          filter: { type: 'lowpass', frequency: 2600, Q: 0.9 },
          envelope: { sustain: 0.5, release: 0.22 },
          lfo: { rate: 4.2, vibratoDepth: 2.8, tremoloDepth: 0 },
          reverbSend: 0.07,
        }),
        5: createFmDefinition({
          layers: [
            { type: 'sine', detune: -4, gain: 0.48 },
            { type: 'sine', detune: 4, gain: 0.48 },
            { type: 'sine', detune: 9, gain: 0.28 },
          ],
          filter: { type: 'lowpass', frequency: 4200, Q: 0.95 },
          envelope: { decay: 0.2, sustain: 0.58, release: 0.48 },
          chorus: { mix: 0.34, rate: 0.55, depth: 0.016, delay: 0.028, feedback: 0.15, spread: 0.55 },
          reverbSend: 0.16,
        }),
        6: createFmDefinition({
          layers: [
            { type: 'sine', detune: -7, gain: 0.46 },
            { type: 'sine', detune: 7, gain: 0.46 },
            { type: 'sine', detune: 14, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 4400, Q: 0.9 },
          envelope: { decay: 0.2, sustain: 0.52, release: 0.46 },
          chorus: { mix: 0.36, rate: 0.65, depth: 0.015, delay: 0.026, feedback: 0.14, spread: 0.6 },
          lfo: { rate: 4.8, vibratoDepth: 4.6, tremoloDepth: 0.04 },
          reverbSend: 0.17,
        }),
        7: createFmDefinition({
          layers: [
            { type: 'sine', detune: -6, gain: 0.5 },
            { type: 'sine', detune: 6, gain: 0.5 },
            { type: 'sine', detune: 18, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 5200, Q: 0.8 },
          envelope: { decay: 0.18, sustain: 0.4, release: 0.32 },
          lfo: { rate: 5.6, vibratoDepth: 5.4, tremoloDepth: 0.05 },
          reverbSend: 0.13,
        }),
        8: createFmDefinition({
          layers: [
            { type: 'sine', detune: -3, gain: 0.44 },
            { type: 'sine', detune: 3, gain: 0.44 },
            { type: 'sine', detune: 11, gain: 0.26 },
          ],
          filter: { type: 'lowpass', frequency: 3600, Q: 1.05 },
          envelope: { decay: 0.16, sustain: 0.48, release: 0.38 },
          reverbSend: 0.12,
        }),
        9: createFmDefinition({
          layers: [
            { type: 'sine', detune: -2, gain: 0.48 },
            { type: 'sine', detune: 2, gain: 0.48 },
            { type: 'sine', detune: 15, gain: 0.18 },
          ],
          filter: { type: 'lowpass', frequency: 6000, Q: 0.75 },
          envelope: { decay: 0.18, sustain: 0.46, release: 0.4 },
          lfo: { rate: 4.2, vibratoDepth: 3.4, tremoloDepth: 0.03 },
          reverbSend: 0.14,
        }),
        10: createFmDefinition({
          layers: [
            { type: 'sine', detune: 0, gain: 0.48 },
            { type: 'sine', detune: 7, gain: 0.42 },
            { type: 'sine', detune: 19, gain: 0.22 },
          ],
          filter: { type: 'lowpass', frequency: 5400, Q: 0.88 },
          envelope: { decay: 0.14, sustain: 0.32, release: 0.3 },
          lfo: { rate: 6, vibratoDepth: 6, tremoloDepth: 0.05 },
          reverbSend: 0.11,
        }),
        11: createFmDefinition({
          layers: [
            { type: 'sine', detune: -5, gain: 0.44 },
            { type: 'sine', detune: 5, gain: 0.44 },
            { type: 'sine', detune: 12, gain: 0.28 },
          ],
          filter: { type: 'lowpass', frequency: 4000, Q: 0.95 },
          envelope: { decay: 0.24, sustain: 0.62, release: 0.58 },
          chorus: { mix: 0.4, rate: 0.5, depth: 0.018, delay: 0.03, feedback: 0.16, spread: 0.6 },
          reverbSend: 0.18,
        }),
        12: createFmDefinition({
          layers: [
            { type: 'sine', detune: -9, gain: 0.38 },
            { type: 'sine', detune: 9, gain: 0.38 },
            { type: 'sine', detune: 21, gain: 0.24 },
          ],
          filter: { type: 'bandpass', frequency: 3200, Q: 1.1 },
          envelope: { decay: 0.12, sustain: 0.28, release: 0.26 },
          lfo: { rate: 5.8, vibratoDepth: 5.6, tremoloDepth: 0.05 },
          reverbSend: 0.12,
        }),
        13: createFmDefinition({
          layers: [
            { type: 'sine', detune: -6, gain: 0.42 },
            { type: 'sine', detune: 6, gain: 0.42 },
            { type: 'sine', detune: 14, gain: 0.24 },
          ],
          filter: { type: 'lowpass', frequency: 3400, Q: 0.9 },
          envelope: { decay: 0.18, sustain: 0.36, release: 0.32 },
          reverbSend: 0.11,
        }),
        14: createFmDefinition({
          layers: [
            { type: 'sine', detune: -8, gain: 0.4 },
            { type: 'sine', detune: 8, gain: 0.4 },
            { type: 'sine', detune: 20, gain: 0.22 },
          ],
          filter: { type: 'highpass', frequency: 420, Q: 0.75 },
          envelope: { decay: 0.1, sustain: 0.2, release: 0.2 },
          reverbSend: 0.09,
        }),
        15: createFmDefinition({
          layers: [
            { type: 'sine', detune: 0, gain: 0.4 },
            { type: 'sine', detune: 12, gain: 0.3 },
            { type: 'sine', detune: 24, gain: 0.2 },
          ],
          filter: { type: 'bandpass', frequency: 2800, Q: 1 },
          envelope: { decay: 0.14, sustain: 0.24, release: 0.28 },
          lfo: { rate: 6.4, vibratoDepth: 6.2, tremoloDepth: 0.05 },
          reverbSend: 0.12,
        }),
      };

      const definition = Object.prototype.hasOwnProperty.call(definitions, family)
        ? definitions[family]
        : definitions.default;
      const articulation = this.getArticulationFactor();

      const envelope = {
        ...definition.envelope,
      };

      const attackBase = Number.isFinite(envelope.attack) ? envelope.attack : envelopeSource.attack;
      const decayBase = Number.isFinite(envelope.decay) ? envelope.decay : envelopeSource.decay;
      const sustainBase = Number.isFinite(envelope.sustain) ? envelope.sustain : envelopeSource.sustain;
      const releaseBase = Number.isFinite(envelope.release) ? envelope.release : envelopeSource.release;

      const noteDuration = Number.isFinite(note?.duration) ? Math.max(0.02, note.duration) : 0.24;

      const attackScale = Math.max(0.4, 1 - (articulation * 0.55));
      const decayScale = Math.max(0.4, 1 - (articulation * 0.45));
      const sustainCeiling = 0.6 + ((1 - articulation) * 0.25);
      const sustainFloor = 0.2 + ((1 - articulation) * 0.12);
      const releaseFloor = 0.2 + ((1 - articulation) * 0.1);
      const releaseCeiling = 0.85 + ((1 - articulation) * 0.4);
      const durationFactor = 0.38 + ((1 - articulation) * 0.3);
      const durationRelease = Math.min(releaseCeiling, Math.max(releaseFloor, noteDuration * durationFactor));

      envelope.attack = Math.max(0.003, attackBase * attackScale);
      envelope.decay = Math.max(0.035, decayBase * decayScale);
      const sustainValue = Number.isFinite(sustainBase) ? sustainBase : sustainCeiling;
      envelope.sustain = Math.max(sustainFloor, Math.min(sustainCeiling, sustainValue));
      const releaseValue = Number.isFinite(releaseBase) ? releaseBase : durationRelease;
      envelope.release = Math.max(releaseFloor, Math.min(releaseCeiling, Math.max(releaseValue, durationRelease)));

      let filter = null;
      if (definition.filter) {
        filter = { ...definition.filter };
        if (filter.type === 'lowpass') {
          const brightnessBoost = 0.9 + (articulation * 0.9);
          const baseFrequency = Number.isFinite(filter.frequency) ? filter.frequency : 3600;
          filter.frequency = Math.min(7200, baseFrequency * brightnessBoost);
          if (Number.isFinite(filter.Q)) {
            filter.Q = Math.max(0.55, Math.min(1.4, filter.Q * (0.85 + (articulation * 0.2))));
          }
        } else if (filter.type === 'highpass') {
          const baseFrequency = Number.isFinite(filter.frequency) ? filter.frequency : 220;
          const openness = 0.8 + (articulation * 0.6);
          filter.frequency = Math.min(3200, baseFrequency * openness);
        }
      }

      let lfo = null;
      if (definition.lfo) {
        lfo = { ...definition.lfo };
        if (Number.isFinite(lfo.vibratoDepth)) {
          lfo.vibratoDepth = Math.max(0, lfo.vibratoDepth * (0.5 + ((1 - articulation) * 0.5)));
          if (lfo.vibratoDepth < 0.4) {
            lfo.vibratoDepth = 0;
          }
        }
        if (Number.isFinite(lfo.tremoloDepth)) {
          lfo.tremoloDepth = Math.max(0, lfo.tremoloDepth * (0.55 + ((1 - articulation) * 0.45)));
          if (lfo.tremoloDepth < 0.015) {
            lfo.tremoloDepth = 0;
          }
        }
      }

      const baseReverbSend = Number.isFinite(definition.reverbSend)
        ? definition.reverbSend
        : this.reverbDefaultSend;
      const reverbScale = 0.4 + ((1 - articulation) * 0.6);
      const reverbSend = Math.max(0, baseReverbSend * reverbScale);

      const baseGain = Number.isFinite(definition.gain)
        ? definition.gain
        : Number.isFinite(definition.volume)
          ? definition.volume
          : 1;
      const gain = Math.min(1.1, baseGain * (0.88 + (articulation * 0.22)));

      const instrument = {
        ...definition,
        gain,
        envelope,
        reverbSend,
      };

      if (filter) {
        instrument.filter = filter;
      }
      if (lfo) {
        instrument.lfo = lfo;
      }

      return instrument;
    }

    applyInstrumentEffects(inputNode, instrument, voice, startAt, stopAt, releaseDuration) {
      if (!this.audioContext || !inputNode || !instrument) {
        return inputNode;
      }

      let currentNode = inputNode;

      if (instrument.chorus) {
        const settings = instrument.chorus || {};
        const mix = Math.max(0, Math.min(1, settings.mix ?? 0.35));
        const delayBase = Math.max(0.003, Math.min(0.05, settings.delay ?? 0.02));
        const depth = Math.max(0.0002, Math.min(0.03, settings.depth ?? 0.008));
        const rate = Math.max(0.05, Math.min(6, settings.rate ?? 0.8));
        const feedback = Math.max(0, Math.min(0.8, settings.feedback ?? 0.1));
        const spread = Math.max(0, Math.min(1, settings.spread ?? 0.5));
        const waveform = settings.waveform || 'sine';

        const delay = this.audioContext.createDelay(0.1);
        delay.delayTime.setValueAtTime(delayBase, startAt);

        const feedbackGain = this.audioContext.createGain();
        feedbackGain.gain.setValueAtTime(feedback, startAt);
        delay.connect(feedbackGain);
        feedbackGain.connect(delay);

        const wetGain = this.audioContext.createGain();
        wetGain.gain.setValueAtTime(mix, startAt);

        const dryGain = this.audioContext.createGain();
        const dryLevel = Math.max(0, Math.min(1, 1 - (mix * 0.5)));
        dryGain.gain.setValueAtTime(dryLevel, startAt);

        inputNode.connect(dryGain);
        inputNode.connect(delay);
        delay.connect(wetGain);

        let wetOutput = wetGain;
        let dryOutput = dryGain;

        if (typeof this.audioContext.createStereoPanner === 'function' && spread > 0.001) {
          const wetPan = this.audioContext.createStereoPanner();
          wetPan.pan.setValueAtTime(spread, startAt);
          wetGain.connect(wetPan);
          wetOutput = wetPan;
          voice.nodes.push(wetPan);

          const dryPan = this.audioContext.createStereoPanner();
          dryPan.pan.setValueAtTime(-spread * 0.6, startAt);
          dryGain.connect(dryPan);
          dryOutput = dryPan;
          voice.nodes.push(dryPan);
        }

        const mixOutput = this.audioContext.createGain();
        wetOutput.connect(mixOutput);
        dryOutput.connect(mixOutput);

        const lfo = this.audioContext.createOscillator();
        lfo.type = waveform;
        lfo.frequency.setValueAtTime(rate, startAt);

        const lfoGain = this.audioContext.createGain();
        lfoGain.gain.setValueAtTime(depth, startAt);
        lfo.connect(lfoGain);
        lfoGain.connect(delay.delayTime);

        const lfoStop = stopAt + releaseDuration + 0.1;
        try {
          lfo.start(startAt);
          lfo.stop(lfoStop);
        } catch (error) {
          // Ignore oscillator scheduling issues
        }

        if (!voice.effectOscillators) {
          voice.effectOscillators = [];
        }
        voice.effectOscillators.push(lfo);

        voice.nodes.push(delay, feedbackGain, wetGain, dryGain, mixOutput, lfoGain);
        currentNode = mixOutput;
      }

      return currentNode;
    }

    getLfoSettings(instrument) {
      if (instrument && instrument.lfo) {
        const { lfo } = instrument;
        return {
          rate: Math.max(0.1, lfo.rate || 5),
          vibratoDepth: Math.max(0, lfo.vibratoDepth || 0),
          tremoloDepth: Math.max(0, Math.min(0.95, lfo.tremoloDepth || 0)),
          waveform: lfo.waveform || 'sine',
        };
      }

      if (instrument && instrument.vibrato) {
        const { vibrato } = instrument;
        return {
          rate: Math.max(0.1, vibrato.rate || 5),
          vibratoDepth: Math.max(0, vibrato.depth || 0),
          tremoloDepth: 0,
          waveform: vibrato.waveform || 'sine',
        };
      }

      return null;
    }

    getPercussionSettings(note) {
      const key = note.note;

      if (!Number.isFinite(key) || key < 35 || key > 81) {
        return null;
      }

      if (key === 35 || key === 36) {
        return {
          volume: 1.1,
          duration: 0.32,
          envelope: { attack: 0.001, decay: 0.08, sustain: 0.0001, release: 0.12 },
          tone: { type: 'sine', frequency: 110, frequencyEnd: 55, level: 1.2 },
          noise: { level: 0.45, filter: { type: 'lowpass', frequency: 1400, Q: 0.8 } },
        };
      }

      if (key === 38 || key === 40) {
        return {
          volume: 1,
          duration: 0.26,
          envelope: { attack: 0.001, decay: 0.06, sustain: 0.0001, release: 0.16 },
          tone: { type: 'triangle', frequency: 220, frequencyEnd: 140, level: 0.45, filter: { type: 'lowpass', frequency: 1200, Q: 0.7 } },
          noise: { level: 1.1, filter: { type: 'bandpass', frequency: 3200, Q: 0.9 } },
        };
      }

      if (key === 42 || key === 44 || key === 46) {
        return {
          volume: 0.8,
          duration: 0.18,
          envelope: { attack: 0.001, decay: 0.04, sustain: 0.0001, release: 0.08 },
          noise: { level: 1.2, filter: { type: 'highpass', frequency: 6500, Q: 0.7 } },
        };
      }

      if (key === 49 || key === 57) {
        return {
          volume: 0.9,
          duration: 0.4,
          envelope: { attack: 0.001, decay: 0.12, sustain: 0.0001, release: 0.22 },
          tone: { type: 'sine', frequency: 330, frequencyEnd: 200, level: 0.6 },
          noise: { level: 0.6, filter: { type: 'bandpass', frequency: 1900, Q: 1.1 } },
        };
      }

      return {
        volume: 0.75,
        duration: 0.22,
        envelope: { attack: 0.001, decay: 0.06, sustain: 0.0001, release: 0.12 },
        noise: { level: 1, filter: { type: 'bandpass', frequency: 2600, Q: 0.8 } },
      };
    }

    getSustainProfile(note, frequency, durationSeconds) {
      const profile = {
        gainScale: 1,
        sustainScale: 1,
        releaseScale: 1,
        reverbScale: 1,
      };

      const duration = Number.isFinite(durationSeconds) ? Math.max(0, durationSeconds) : 0;
      if (!Number.isFinite(frequency) || frequency <= 0 || duration <= 0.55) {
        return profile;
      }

      const baseVelocity = Number.isFinite(note?.rawVelocity)
        ? Math.max(0, Math.min(1, note.rawVelocity))
        : (Number.isFinite(note?.velocity) ? Math.max(0, Math.min(1, note.velocity)) : 0.6);

      const isLow = frequency < 260;
      const isVeryLow = frequency < 150;
      const isSub = frequency < 95;

      if (isLow) {
        const effectiveDuration = Math.max(0, duration - 0.55);
        if (effectiveDuration <= 0) {
          return profile;
        }
        const normalizedDuration = Math.min(1, effectiveDuration / 4.5);
        const velocityInfluence = 0.7 + (baseVelocity * 0.45);
        const gainDepth = isSub ? 0.72 : (isVeryLow ? 0.56 : 0.42);
        const sustainDepth = isSub ? 0.78 : (isVeryLow ? 0.6 : 0.48);
        const releaseDepth = isSub ? 0.68 : (isVeryLow ? 0.55 : 0.45);
        const reverbDepth = isSub ? 0.65 : (isVeryLow ? 0.5 : 0.4);

        const attenuation = normalizedDuration * gainDepth * velocityInfluence;
        const sustainAttenuation = normalizedDuration * sustainDepth;
        const releaseAttenuation = normalizedDuration * releaseDepth;
        const reverbAttenuation = normalizedDuration * reverbDepth;

        profile.gainScale = Math.max(0.28, 1 - attenuation);
        profile.sustainScale = Math.max(0.22, 1 - sustainAttenuation);
        profile.releaseScale = Math.max(0.38, 1 - releaseAttenuation);
        profile.reverbScale = Math.max(0.35, 1 - reverbAttenuation);
        return profile;
      }

      if (duration > 2.8) {
        const normalizedDuration = Math.min(1, (duration - 2.8) / 4.2);
        const gentleDepth = 0.22 + (baseVelocity * 0.18);
        profile.gainScale = Math.max(0.45, 1 - (normalizedDuration * gentleDepth));
        profile.sustainScale = Math.max(0.4, 1 - (normalizedDuration * 0.35));
        profile.releaseScale = Math.max(0.5, 1 - (normalizedDuration * 0.45));
        profile.reverbScale = Math.max(0.5, 1 - (normalizedDuration * 0.4));
      }

      return profile;
    }

    scheduleNote(note, baseTime, speedParam = this.activePlaybackSpeed || 1) {
      if (!this.audioContext || !this.masterGain) {
        return;
      }

      const speed = this.clampPlaybackSpeed(speedParam || 1);
      const now = this.audioContext.currentTime;
      const startOffset = Number.isFinite(note.startTime) ? note.startTime : 0;
      const startAt = Math.max(baseTime + (startOffset / speed), now + 0.001);
      const velocity = Math.max(0.08, Math.min(1, note.velocity || 0.2));

      const instrument = this.getInstrumentSettings(note);
      if (note.channel === 9) {
        const usesSoundFont = instrument && instrument.type === 'soundfont';
        if (!usesSoundFont) {
          if (this.engineMode === 'hifi' && !instrument) {
            return;
          }
          this.schedulePercussion(note, baseTime, speed);
          return;
        }
      }
      if (!instrument) {
        return;
      }
      const frequency = this.midiNoteToFrequency(note.note);
      const lfoSettings = this.getLfoSettings(instrument);

      const hasVibrato = lfoSettings && lfoSettings.vibratoDepth > 0;
      const hasTremolo = lfoSettings && lfoSettings.tremoloDepth > 0;
      const needsLfo = lfoSettings && (hasVibrato || hasTremolo);

      const instrumentGain = Number.isFinite(instrument?.gain)
        ? instrument.gain
        : Number.isFinite(instrument?.volume)
          ? instrument.volume
          : 1;
      let peakGain = Math.min(1, velocity * this.maxGain * instrumentGain);
      const envelope = instrument.envelope || {};
      const attack = Math.max(0.002, (envelope.attack || 0.01) / speed);
      const decay = Math.max(0.01, (envelope.decay || 0.06) / speed);
      let sustainLevel = Math.min(1, Math.max(0, envelope.sustain ?? 0.6));
      const timelineDuration = Math.max(0.02, Number.isFinite(note.duration) ? note.duration : 0.12);
      const duration = Math.max(0.02, timelineDuration / speed);
      const releaseBase = Number.isFinite(envelope.release)
        ? envelope.release
        : Math.min(0.9, timelineDuration * 0.8);
      let releaseDuration = Math.max(0.08, releaseBase / speed);
      const sustainProfile = this.getSustainProfile(note, frequency, duration);
      const gainScale = Number.isFinite(sustainProfile?.gainScale) ? sustainProfile.gainScale : 1;
      const sustainScale = Number.isFinite(sustainProfile?.sustainScale) ? sustainProfile.sustainScale : 1;
      const releaseScale = Number.isFinite(sustainProfile?.releaseScale) ? sustainProfile.releaseScale : 1;
      peakGain = Math.min(1, peakGain * gainScale);
      sustainLevel = Math.min(1, Math.max(0.05, sustainLevel * sustainScale));
      releaseDuration = Math.max(0.04, releaseDuration * releaseScale);
      const stopAt = startAt + duration;
      const attackEnd = Math.min(stopAt, startAt + attack);
      const decayEnd = Math.min(stopAt, attackEnd + decay);

      if (instrument && instrument.type === 'soundfont' && Array.isArray(instrument.regions) && instrument.regions.length) {
        this.scheduleSoundFontVoice(note, instrument, {
          startAt,
          stopAt,
          velocity,
          peakGain,
          sustainLevel,
          attackEnd,
          decayEnd,
          releaseDuration,
          sustainProfile,
        });
        return;
      }

      const layers = Array.isArray(instrument.layers) && instrument.layers.length
        ? instrument.layers
        : [{ type: instrument.type || 'sine', dutyCycle: instrument.dutyCycle, detune: 0, gain: 1 }];
      const totalLayerGain = layers.reduce((sum, layer) => sum + Math.max(0, layer.gain ?? 1), 0) || 1;

      const voiceInput = this.audioContext.createGain();
      voiceInput.gain.setValueAtTime(1, startAt);

      let lfoOscillator = null;
      let vibratoGain = null;
      if (needsLfo) {
        lfoOscillator = this.audioContext.createOscillator();
        lfoOscillator.type = lfoSettings.waveform || 'sine';
        lfoOscillator.frequency.setValueAtTime(lfoSettings.rate, startAt);
      }

      if (lfoOscillator && hasVibrato) {
        vibratoGain = this.audioContext.createGain();
        const depthInCents = lfoSettings.vibratoDepth;
        const depthFactor = Math.max(0, Math.pow(2, depthInCents / 1200) - 1);
        const depthHz = frequency * depthFactor;
        vibratoGain.gain.setValueAtTime(depthHz, startAt);
      }

      const voice = {
        type: 'melodic',
        startTime: startAt,
        stopTime: stopAt + releaseDuration,
        gainNode: null,
        oscillators: [],
        effectOscillators: [],
        nodes: [voiceInput],
        baseMidiNote: note.note,
      };

      this.liveVoices.add(voice);

      let activeOscillators = 0;

      const connectOscillator = (layer) => {
        const osc = this.audioContext.createOscillator();
        const useSccWave = this.shouldUseSccWaveform(instrument, layer);
        const useN163Wave = this.shouldUseN163Waveform(instrument, layer);
        let configured = false;
        if (useSccWave) {
          const sccWave = this.getSccWave();
          if (sccWave) {
            osc.setPeriodicWave(sccWave);
            configured = true;
          }
        }
        if (!configured && useN163Wave) {
          const waveId = layer?.n163Wave || instrument?.n163Wave || 'default';
          const n163Wave = this.getN163Wave(waveId);
          if (n163Wave) {
            osc.setPeriodicWave(n163Wave);
            configured = true;
          }
        }
        if (!configured) {
          const analogType = layer?.type ?? instrument?.type ?? 'sine';
          if (analogType === 'pulse') {
            const dutyCycle = Number.isFinite(layer?.dutyCycle)
              ? layer.dutyCycle
              : Number.isFinite(instrument?.dutyCycle)
                ? instrument.dutyCycle
                : 0.5;
            const pulseWave = this.getPulseWave(dutyCycle);
            if (pulseWave) {
              osc.setPeriodicWave(pulseWave);
              configured = true;
            } else {
              osc.type = 'square';
              configured = true;
            }
          } else if (analogType === 'square' || analogType === 'sawtooth' || analogType === 'triangle' || analogType === 'sine') {
            osc.type = analogType;
            configured = true;
          } else {
            osc.type = 'sine';
            configured = true;
          }
        }
        osc.frequency.setValueAtTime(frequency, startAt);
        if (Number.isFinite(layer.detune)) {
          osc.detune.setValueAtTime(layer.detune * 100, startAt);
        }

        const layerGain = this.audioContext.createGain();
        const normalizedGain = Math.max(0, layer.gain ?? 1) / totalLayerGain;
        layerGain.gain.setValueAtTime(normalizedGain, startAt);

        osc.connect(layerGain).connect(voiceInput);

        if (vibratoGain) {
          vibratoGain.connect(osc.frequency);
        }

        activeOscillators += 1;
        voice.oscillators.push(osc);
        voice.nodes.push(layerGain);

        osc.onended = () => {
          activeOscillators -= 1;
          try {
            osc.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
          try {
            layerGain.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
          if (activeOscillators <= 0) {
            voice.cleanup?.();
          }
        };

        osc.start(startAt);
        osc.stop(stopAt + releaseDuration + 0.02);
      };

      for (const layer of layers) {
        connectOscillator(layer);
      }

      if (vibratoGain && lfoOscillator) {
        lfoOscillator.connect(vibratoGain);
        voice.lfoGain = vibratoGain;
        voice.nodes.push(vibratoGain);
      }

      let lastNode = voiceInput;

      if (instrument.filter) {
        const filter = this.audioContext.createBiquadFilter();
        filter.type = instrument.filter.type || 'lowpass';
        if (Number.isFinite(instrument.filter.Q)) {
          filter.Q.setValueAtTime(instrument.filter.Q, startAt);
        }
        if (Number.isFinite(instrument.filter.frequency)) {
          const velocityInfluence = 1 + (velocity * 0.4);
          filter.frequency.setValueAtTime(instrument.filter.frequency * velocityInfluence, startAt);
        }
        lastNode.connect(filter);
        lastNode = filter;
        voice.filterNode = filter;
        voice.nodes.push(filter);
      }

      lastNode = this.applyInstrumentEffects(lastNode, instrument, voice, startAt, stopAt, releaseDuration) || lastNode;

      let tremoloNode = null;
      if (lfoOscillator && hasTremolo) {
        tremoloNode = this.audioContext.createGain();
        tremoloNode.gain.setValueAtTime(1, startAt);
        lastNode.connect(tremoloNode);
        lastNode = tremoloNode;

        const tremoloGain = this.audioContext.createGain();
        const tremoloDepth = Math.max(0, Math.min(0.95, lfoSettings.tremoloDepth));
        const tremoloScale = tremoloDepth / 2;
        tremoloGain.gain.setValueAtTime(tremoloScale, startAt);
        lfoOscillator.connect(tremoloGain).connect(tremoloNode.gain);

        const tremoloOffset = this.audioContext.createConstantSource();
        tremoloOffset.offset.setValueAtTime(1 - tremoloScale, startAt);
        tremoloOffset.connect(tremoloNode.gain);
        tremoloOffset.start(startAt);
        tremoloOffset.stop(stopAt + releaseDuration + 0.05);

        voice.tremoloNode = tremoloNode;
        voice.tremoloGain = tremoloGain;
        voice.tremoloOffset = tremoloOffset;
        voice.nodes.push(tremoloNode, tremoloGain, tremoloOffset);
      }

      const envelopeGain = this.audioContext.createGain();
      voice.gainNode = envelopeGain;
      voice.nodes.push(envelopeGain);
      lastNode.connect(envelopeGain);

      envelopeGain.gain.cancelScheduledValues(startAt);
      envelopeGain.gain.setValueAtTime(0.0001, startAt);
      envelopeGain.gain.linearRampToValueAtTime(peakGain, attackEnd);
      envelopeGain.gain.linearRampToValueAtTime(Math.max(0.0001, peakGain * sustainLevel), decayEnd);
      envelopeGain.gain.setValueAtTime(Math.max(0.0001, peakGain * sustainLevel), stopAt);
      envelopeGain.gain.exponentialRampToValueAtTime(0.0001, stopAt + releaseDuration);

      let outputNode = envelopeGain;
      let panNode = null;
      const basePan = Number.isFinite(note.pan) ? note.pan : 0;
      const instrumentPan = Number.isFinite(instrument?.pan) ? instrument.pan : 0;
      const panValue = Math.max(-1, Math.min(1, basePan + instrumentPan));
      if (typeof this.audioContext.createStereoPanner === 'function') {
        panNode = this.audioContext.createStereoPanner();
        panNode.pan.setValueAtTime(panValue, startAt);
        outputNode.connect(panNode);
        outputNode = panNode;
        voice.panNode = panNode;
        voice.nodes.push(panNode);
      } else if (typeof this.audioContext.createPanner === 'function') {
        panNode = this.audioContext.createPanner();
        panNode.panningModel = 'equalpower';
        const x = panValue;
        panNode.setPosition(x, 0, 1 - Math.abs(x));
        outputNode.connect(panNode);
        outputNode = panNode;
        voice.panNode = panNode;
        voice.nodes.push(panNode);
      }

      const baseReverbSend = instrument.reverbSend ?? this.reverbDefaultSend;
      const reverbScale = Number.isFinite(sustainProfile?.reverbScale) ? sustainProfile.reverbScale : 1;
      const reverbAmount = Math.max(0, Math.min(1, baseReverbSend * 0.7 * reverbScale));
      const dryAmount = Math.max(0, 1 - (reverbAmount * 0.5));

      const dryGain = this.audioContext.createGain();
      dryGain.gain.setValueAtTime(dryAmount, startAt);
      voice.nodes.push(dryGain);

      outputNode.connect(dryGain);
      dryGain.connect(this.dryBus || this.masterGain);

      if (this.reverbSend) {
        const reverbGain = this.audioContext.createGain();
        reverbGain.gain.setValueAtTime(reverbAmount, startAt);
        voice.nodes.push(reverbGain);
        outputNode.connect(reverbGain);
        reverbGain.connect(this.reverbSend);
        voice.reverbGain = reverbGain;
      } else {
        dryGain.gain.setValueAtTime(Math.min(1, dryAmount + (reverbAmount * 0.35)), startAt);
      }

      voice.cleanup = () => {
        if (voice.cleaned) {
          return;
        }
        voice.cleaned = true;
        this.liveVoices.delete(voice);
        for (const node of voice.nodes) {
          if (!node) {
            continue;
          }
          try {
            node.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        if (voice.tremoloOffset) {
          try {
            voice.tremoloOffset.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        if (voice.lfoGain) {
          try {
            voice.lfoGain.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        if (voice.lfo) {
          try {
            voice.lfo.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        if (Array.isArray(voice.effectOscillators)) {
          for (const eff of voice.effectOscillators) {
            if (!eff) {
              continue;
            }
            try {
              eff.disconnect();
            } catch (error) {
              // Ignore disconnect issues
            }
          }
        }
      };

      if (lfoOscillator && (hasVibrato || hasTremolo)) {
        voice.lfo = lfoOscillator;
        this.audioContext.resume?.();
        lfoOscillator.start(startAt);
        lfoOscillator.stop(stopAt + releaseDuration + 0.05);
      }
    }

    scheduleSoundFontVoice(note, instrument, context = {}) {
      if (!this.audioContext || !this.masterGain) {
        return;
      }
      const regions = Array.isArray(instrument?.regions) ? instrument.regions : [];
      if (!regions.length || !this.activeSoundFont) {
        return;
      }

      const {
        startAt,
        stopAt,
        velocity = 0.5,
        peakGain = 0.5,
        sustainLevel = 0.6,
        attackEnd = startAt,
        decayEnd = stopAt,
        releaseDuration = 0.2,
        sustainProfile = null,
      } = context;

      const voiceInput = this.audioContext.createGain();
      voiceInput.gain.setValueAtTime(1, startAt);

      const voice = {
        type: 'melodic',
        startTime: startAt,
        stopTime: stopAt + releaseDuration,
        gainNode: null,
        oscillators: [],
        effectOscillators: [],
        nodes: [voiceInput],
        baseMidiNote: note.note,
      };

      this.liveVoices.add(voice);

      let activeSources = 0;

      for (const region of regions) {
        const buffer = this.activeSoundFont.getRegionBuffer(this.audioContext, region);
        if (!buffer) {
          continue;
        }

        const source = this.audioContext.createBufferSource();
        source.buffer = buffer;

        const transpose = Number.isFinite(this.transposeSemitones) ? this.transposeSemitones : 0;
        const targetNote = (Number.isFinite(note.note) ? note.note : 0) + transpose;
        const scale = Number.isFinite(region.scaleTuning) ? region.scaleTuning : 100;
        const rootKey = Number.isFinite(region.rootKey) ? region.rootKey : 60;
        const centsFromKey = (targetNote - rootKey) * scale;
        const coarseCents = (region.coarseTune || 0) * 100;
        const fineCents = region.fineTune || 0;
        const correction = region.pitchCorrection || 0;
        const fineDetune = Number.isFinite(this.fineDetuneCents) ? this.fineDetuneCents : 0;
        const totalCents = centsFromKey + coarseCents + fineCents + correction + fineDetune;
        const baseRate = region.sampleRate ? (region.sampleRate / this.audioContext.sampleRate) : 1;
        const playbackRate = Math.pow(2, totalCents / 1200) * baseRate;
        source.playbackRate.setValueAtTime(Math.max(0.001, playbackRate), startAt);

        if (region.hasLoop) {
          const loopStartFrames = Math.max(0, region.loopStart - region.sampleStart);
          const loopEndFrames = Math.max(loopStartFrames + 1, region.loopEnd - region.sampleStart);
          source.loop = true;
          source.loopStart = Math.max(0, Math.min(loopStartFrames, buffer.length - 1)) / this.audioContext.sampleRate;
          source.loopEnd = Math.max(source.loopStart + 0.002, Math.min(loopEndFrames, buffer.length) / this.audioContext.sampleRate);
        }

        const layerGain = this.audioContext.createGain();
        const regionGain = Number.isFinite(region.gain) ? region.gain : 1;
        layerGain.gain.setValueAtTime(Math.max(0, Math.min(1.5, regionGain)), startAt);
        source.connect(layerGain).connect(voiceInput);

        activeSources += 1;
        voice.oscillators.push(source);
        voice.nodes.push(layerGain);

        source.onended = () => {
          activeSources -= 1;
          try {
            source.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
          try {
            layerGain.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
          if (activeSources <= 0) {
            voice.cleanup?.();
          }
        };

        source.start(startAt);
        source.stop(stopAt + releaseDuration + 0.05);
      }

      if (activeSources <= 0) {
        try {
          voiceInput.disconnect();
        } catch (error) {
          // Ignore disconnect issues
        }
        this.liveVoices.delete(voice);
        return;
      }

      let lastNode = voiceInput;

      lastNode = this.applyInstrumentEffects(lastNode, instrument, voice, startAt, stopAt, releaseDuration) || lastNode;

      const envelopeGain = this.audioContext.createGain();
      voice.gainNode = envelopeGain;
      voice.nodes.push(envelopeGain);
      lastNode.connect(envelopeGain);

      envelopeGain.gain.cancelScheduledValues(startAt);
      envelopeGain.gain.setValueAtTime(0.0001, startAt);
      envelopeGain.gain.linearRampToValueAtTime(peakGain, attackEnd);
      envelopeGain.gain.linearRampToValueAtTime(Math.max(0.0001, peakGain * sustainLevel), decayEnd);
      envelopeGain.gain.setValueAtTime(Math.max(0.0001, peakGain * sustainLevel), stopAt);
      envelopeGain.gain.exponentialRampToValueAtTime(0.0001, stopAt + releaseDuration);

      let outputNode = envelopeGain;
      let panNode = null;
      const basePan = Number.isFinite(note.pan) ? note.pan : 0;
      const instrumentPan = Number.isFinite(instrument?.pan) ? instrument.pan : 0;
      const panValue = Math.max(-1, Math.min(1, basePan + instrumentPan));
      if (typeof this.audioContext.createStereoPanner === 'function') {
        panNode = this.audioContext.createStereoPanner();
        panNode.pan.setValueAtTime(panValue, startAt);
        outputNode.connect(panNode);
        outputNode = panNode;
        voice.panNode = panNode;
        voice.nodes.push(panNode);
      } else if (typeof this.audioContext.createPanner === 'function') {
        panNode = this.audioContext.createPanner();
        panNode.panningModel = 'equalpower';
        const x = panValue;
        panNode.setPosition(x, 0, 1 - Math.abs(x));
        outputNode.connect(panNode);
        outputNode = panNode;
        voice.panNode = panNode;
        voice.nodes.push(panNode);
      }

      const baseReverbSend = Number.isFinite(instrument.reverbSend)
        ? instrument.reverbSend
        : this.reverbDefaultSend;
      const reverbScale = Number.isFinite(sustainProfile?.reverbScale) ? sustainProfile.reverbScale : 1;
      const reverbAmount = Math.max(0, Math.min(1, baseReverbSend * 0.7 * reverbScale));
      const dryAmount = Math.max(0, 1 - (reverbAmount * 0.5));

      const dryGain = this.audioContext.createGain();
      dryGain.gain.setValueAtTime(dryAmount, startAt);
      voice.nodes.push(dryGain);
      outputNode.connect(dryGain);
      dryGain.connect(this.dryBus || this.masterGain);

      if (this.reverbSend) {
        const reverbGain = this.audioContext.createGain();
        reverbGain.gain.setValueAtTime(reverbAmount, startAt);
        voice.nodes.push(reverbGain);
        outputNode.connect(reverbGain);
        reverbGain.connect(this.reverbSend);
        voice.reverbGain = reverbGain;
      } else {
        dryGain.gain.setValueAtTime(Math.min(1, dryAmount + (reverbAmount * 0.35)), startAt);
      }

      voice.cleanup = () => {
        if (voice.cleaned) {
          return;
        }
        voice.cleaned = true;
        this.liveVoices.delete(voice);
        for (const node of voice.nodes) {
          if (!node) {
            continue;
          }
          try {
            node.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
      };
    }

    schedulePercussion(note, baseTime, speedParam = this.activePlaybackSpeed || 1) {
      if (!this.audioContext || !this.masterGain) {
        return;
      }

      const speed = this.clampPlaybackSpeed(speedParam || 1);
      const now = this.audioContext.currentTime;
      const startOffset = Number.isFinite(note.startTime) ? note.startTime : 0;
      const startAt = Math.max(baseTime + (startOffset / speed), now + 0.001);
      const velocity = Math.max(0.1, Math.min(1, note.velocity));
      const settings = this.getPercussionSettings(note);
      if (!settings) {
        return;
      }
      const envelope = settings.envelope || {};
      const attack = Math.max(0.001, (envelope.attack || 0.005) / speed);
      const decay = Math.max(0.01, (envelope.decay || 0.05) / speed);
      const sustainLevel = Math.max(0, Math.min(1, envelope.sustain ?? 0.0001));
      const baseDuration = Math.max(0.05, settings.duration || note.duration || 0.18);
      const duration = Math.max(0.04, baseDuration / speed);
      const release = Math.max(0.02, (envelope.release || 0.08) / speed);
      const stopAt = startAt + duration;
      const totalStop = stopAt + release;

      const gainNode = this.audioContext.createGain();
      const peakGain = Math.min(1, velocity * this.maxGain * (settings.volume || 0.75));
      const attackEnd = startAt + attack;
      const decayEnd = attackEnd + decay;
      gainNode.gain.cancelScheduledValues(startAt);
      gainNode.gain.setValueAtTime(0.0001, startAt);
      gainNode.gain.linearRampToValueAtTime(peakGain, attackEnd);
      gainNode.gain.exponentialRampToValueAtTime(Math.max(0.0001, peakGain * sustainLevel), decayEnd);
      gainNode.gain.setValueAtTime(Math.max(0.0001, peakGain * sustainLevel), stopAt);
      gainNode.gain.exponentialRampToValueAtTime(0.0001, totalStop);

      const voice = {
        type: 'percussion',
        startTime: startAt,
        stopTime: totalStop,
        gainNode,
        sources: [],
        nodes: [],
      };

      this.liveVoices.add(voice);

      const cleanup = () => {
        if (voice.cleaned) {
          return;
        }
        voice.cleaned = true;
        this.liveVoices.delete(voice);
        for (const node of voice.nodes) {
          try {
            node.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        try {
          gainNode.disconnect();
        } catch (error) {
          // Ignore disconnect issues
        }
      };
      voice.cleanup = cleanup;

      let activeSources = 0;
      const registerNode = (node) => {
        voice.nodes.push(node);
        return node;
      };
      const scheduleSource = (source, chainNodes = []) => {
        registerNode(source);
        voice.sources.push(source);
        let previous = source;
        for (const node of chainNodes) {
          registerNode(node);
          previous.connect(node);
          previous = node;
        }
        previous.connect(gainNode);
        activeSources += 1;
        source.start(startAt);
        source.stop(totalStop);
        source.onended = () => {
          try {
            source.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
          activeSources -= 1;
          if (activeSources <= 0) {
            cleanup();
          }
        };
      };

      if (settings.noise) {
        const noiseBuffer = this.getNoiseBuffer();
        if (noiseBuffer) {
          const noise = this.audioContext.createBufferSource();
          noise.buffer = noiseBuffer;
          noise.loop = true;
          const chain = [];
          if (settings.noise.filter) {
            const filter = this.audioContext.createBiquadFilter();
            if (settings.noise.filter.type) {
              filter.type = settings.noise.filter.type;
            } else {
              filter.type = 'bandpass';
            }
            if (Number.isFinite(settings.noise.filter.frequency)) {
              filter.frequency.setValueAtTime(settings.noise.filter.frequency, startAt);
            }
            if (Number.isFinite(settings.noise.filter.Q)) {
              filter.Q.setValueAtTime(settings.noise.filter.Q, startAt);
            }
            chain.push(filter);
          }
          const noiseGain = this.audioContext.createGain();
          noiseGain.gain.setValueAtTime(Math.max(0.0001, settings.noise.level ?? 1), startAt);
          chain.push(noiseGain);
          scheduleSource(noise, chain);
        }
      }

      if (settings.tone) {
        const osc = this.audioContext.createOscillator();
        osc.type = settings.tone.type || 'sine';
        if (Number.isFinite(settings.tone.frequency)) {
          osc.frequency.setValueAtTime(settings.tone.frequency, startAt);
        }
        if (Number.isFinite(settings.tone.frequencyEnd)) {
          const endFrequency = Math.max(10, settings.tone.frequencyEnd);
          osc.frequency.exponentialRampToValueAtTime(endFrequency, stopAt);
        }
        if (Number.isFinite(settings.tone.detune)) {
          osc.detune.setValueAtTime(settings.tone.detune, startAt);
        }
        const chain = [];
        if (settings.tone.filter) {
          const filter = this.audioContext.createBiquadFilter();
          if (settings.tone.filter.type) {
            filter.type = settings.tone.filter.type;
          } else {
            filter.type = 'lowpass';
          }
          if (Number.isFinite(settings.tone.filter.frequency)) {
            filter.frequency.setValueAtTime(settings.tone.filter.frequency, startAt);
          }
          if (Number.isFinite(settings.tone.filter.Q)) {
            filter.Q.setValueAtTime(settings.tone.filter.Q, startAt);
          }
          chain.push(filter);
        }
        const toneGain = this.audioContext.createGain();
        toneGain.gain.setValueAtTime(Math.max(0.0001, settings.tone.level ?? 1), startAt);
        chain.push(toneGain);
        scheduleSource(osc, chain);
      }

      if (!activeSources) {
        cleanup();
      }

      let outputNode = gainNode;
      let panNode = null;
      const panValue = Math.max(-1, Math.min(1, Number.isFinite(note.pan) ? note.pan : 0));
      if (typeof this.audioContext.createStereoPanner === 'function') {
        panNode = this.audioContext.createStereoPanner();
        panNode.pan.setValueAtTime(panValue, startAt);
        outputNode.connect(panNode);
        outputNode = panNode;
        voice.nodes.push(panNode);
      } else if (typeof this.audioContext.createPanner === 'function') {
        panNode = this.audioContext.createPanner();
        panNode.panningModel = 'equalpower';
        const x = panValue;
        panNode.setPosition(x, 0, 1 - Math.abs(x));
        outputNode.connect(panNode);
        outputNode = panNode;
        voice.nodes.push(panNode);
      }

      const basePercussionReverb = note.reverb ?? this.reverbDefaultSend;
      const reverbAmount = Math.max(0, Math.min(1, basePercussionReverb * 0.7));
      const dryAmount = Math.max(0, 1 - (reverbAmount * 0.45));

      const dryGain = this.audioContext.createGain();
      dryGain.gain.setValueAtTime(dryAmount, startAt);
      outputNode.connect(dryGain);
      dryGain.connect(this.dryBus || this.masterGain);
      voice.nodes.push(dryGain);

      if (this.reverbSend) {
        const reverbGain = this.audioContext.createGain();
        reverbGain.gain.setValueAtTime(reverbAmount, startAt);
        outputNode.connect(reverbGain);
        reverbGain.connect(this.reverbSend);
        voice.nodes.push(reverbGain);
      } else {
        dryGain.gain.setValueAtTime(Math.min(1, dryAmount + (reverbAmount * 0.3)), startAt);
      }
    }

    async play(options = {}) {
      const { offset = null, allowHiFiFallback = true } = options;
      if (!this.timeline || !this.timeline.notes.length) {
        this.setStatusMessage('index.sections.options.chiptune.status.noMidiData', 'No MIDI data to play.', {}, 'error');
        return;
      }

      try {
        const context = await this.ensureContext();
        this.stop(false, { preservePosition: true });

        if (this.engineMode === 'hifi') {
          try {
            await this.ensureSoundFontReady();
          } catch (error) {
            console.error(error);
            if (allowHiFiFallback) {
              this.setStatusMessage(
                'index.sections.options.chiptune.status.soundFontAutoFallback',
                'SoundFont unavailable: {error}. Reverting to original engine.',
                { error: error.message },
                'error',
              );
              this.setEngineMode('original');
              return this.play({ offset, allowHiFiFallback: false });
            }
            this.setStatusMessage('index.sections.options.chiptune.status.soundFontUnavailable', 'SoundFont unavailable: {error}', { error: error.message }, 'error');
            this.playing = false;
            this.updateButtons();
            return;
          }
          if (!this.activeSoundFont) {
            if (allowHiFiFallback) {
              this.setStatusMessage(
                'index.sections.options.chiptune.status.soundFontAutoFallback',
                'SoundFont unavailable: {error}. Reverting to original engine.',
                { error: this.translate('index.sections.options.chiptune.errors.soundFontMissingSelection', 'No SoundFont selected.') },
                'error',
              );
              this.setEngineMode('original');
              return this.play({ offset, allowHiFiFallback: false });
            }
            this.setStatusMessage('index.sections.options.chiptune.status.soundFontMissing', 'No SoundFont ready for Hi-Fi mode.', {}, 'error');
            this.playing = false;
            this.updateButtons();
            return;
          }
        }

        this.playing = true;
        this.updateButtons();

        this.activePlaybackSpeed = this.playbackSpeed;
        const playbackSpeed = this.activePlaybackSpeed || 1;
        const timelineDuration = this.getTimelineDuration(this.timeline);
        const requestedOffset = Number.isFinite(offset) ? offset : this.pendingSeekSeconds;
        const startOffset = timelineDuration > 0
          ? Math.max(0, Math.min(timelineDuration, Number.isFinite(requestedOffset) ? requestedOffset : 0))
          : 0;
        const normalizedSpeed = playbackSpeed > 0 ? playbackSpeed : 1;
        const remainingTimelineDuration = timelineDuration > 0
          ? Math.max(0, timelineDuration - startOffset)
          : 0;
        const effectiveDuration = remainingTimelineDuration > 0
          ? remainingTimelineDuration / normalizedSpeed
          : (timelineDuration > 0
            ? 0
            : this.getEffectiveDuration(this.timeline, playbackSpeed));
        const finishDelaySeconds = Math.max(0, (effectiveDuration || 0) + 0.6);
        const startTime = context.currentTime + 0.05;
        const schedulerStartTime = startTime - (startOffset / playbackSpeed);
        this.playStartTime = startTime;
        this.playStartOffset = startOffset;
        this.progressMonitorSpeed = playbackSpeed;
        this.progressDuration = timelineDuration;
        this.lastKnownPosition = startOffset;
        this.pendingSeekSeconds = startOffset;
        this.refreshProgressControls(this.timeline);
        this.startScheduler(schedulerStartTime, startOffset);
        this.startProgressMonitor(startTime, startOffset, playbackSpeed, timelineDuration);

        this.finishTimeout = window.setTimeout(() => {
          this.finishTimeout = null;
          const hasRandomSession = Boolean(this.randomPlaybackMode);
          const shouldQueueNext = this.shouldContinueRandomPlayback();
          const sessionFinished = hasRandomSession
            && !shouldQueueNext
            && (!Array.isArray(this.randomPlaybackQueue) || !this.randomPlaybackQueue.length);

          if (shouldQueueNext) {
            this.stop(false, { skipRandomReset: true });
          } else {
            this.stop(false);
          }

          if (shouldQueueNext) {
            const nextArtistId = this.randomPlaybackMode === 'artist'
              ? this.randomPlaybackArtistId
              : null;
            this.playRandomTrack(nextArtistId, { continueSession: true }).catch((error) => {
              console.error('Unable to continue random playback', error);
              this.completeRandomPlayback();
            });
          } else if (sessionFinished) {
            this.completeRandomPlayback();
          } else {
            this.setStatusMessage('index.sections.options.chiptune.status.playbackComplete', 'Playback finished: {title}', { title: this.currentTitle }, 'success');
            this.scheduleReadyStatusRestore();
          }
        }, Math.ceil(finishDelaySeconds * 1000));

        this.setStatus('', 'success', { type: 'playing', extra: '' });
      } catch (error) {
        console.error(error);
        this.setStatusMessage('index.sections.options.chiptune.status.playbackError', 'Playback failed: {error}', { error: error.message }, 'error');
        this.stopProgressMonitor();
        this.playing = false;
        this.updateButtons();
      }
    }

    pause() {
      if (!this.playing) {
        return;
      }

      this.capturePlaybackPosition();

      const currentTitle = this.currentTitle;
      this.stop(false, { preservePosition: true, skipRandomReset: true });

      const hasTitle = typeof currentTitle === 'string' && currentTitle.trim().length > 0;
      const key = hasTitle
        ? 'index.sections.options.chiptune.status.playbackPausedWithTitle'
        : 'index.sections.options.chiptune.status.playbackPaused';
      const fallback = hasTitle ? 'Playback paused: {title}' : 'Playback paused.';
      const params = hasTitle ? { title: currentTitle } : {};

      this.setStatusMessage(key, fallback, params, 'info');
      this.scheduleReadyStatusRestore();
      this.updateButtons();
    }

    capturePlaybackPosition() {
      if (!this.playing || !this.audioContext || !Number.isFinite(this.playStartTime)) {
        return null;
      }

      const duration = this.progressDuration || this.getTimelineDuration();
      const baseSpeed = Number.isFinite(this.progressMonitorSpeed)
        ? this.progressMonitorSpeed
        : (this.activePlaybackSpeed || this.playbackSpeed || 1);
      const startOffset = Number.isFinite(this.playStartOffset) ? this.playStartOffset : 0;
      const now = this.audioContext.currentTime;
      const elapsed = Math.max(0, now - this.playStartTime);
      const position = startOffset + (elapsed * baseSpeed);
      const clamped = duration > 0
        ? Math.max(0, Math.min(duration, position))
        : Math.max(0, position);

      this.lastKnownPosition = clamped;
      this.pendingSeekSeconds = clamped;

      return clamped;
    }

    stop(manual = false, options = {}) {
      const { preservePosition = false, skipRandomReset = false } = options;
      if (this.finishTimeout) {
        window.clearTimeout(this.finishTimeout);
        this.finishTimeout = null;
      }

      this.stopProgressMonitor();

      this.stopScheduler();

      for (const timerId of this.pendingTimers) {
        window.clearTimeout(timerId);
      }
      this.pendingTimers.clear();

      if (this.audioContext) {
        const now = this.audioContext.currentTime;
        for (const voice of this.liveVoices) {
          try {
            if (voice.type === 'percussion') {
              const stopAt = now < voice.startTime ? voice.startTime : now + 0.12;
              voice.gainNode.gain.cancelScheduledValues(now);
              voice.gainNode.gain.setValueAtTime(voice.gainNode.gain.value, now);
              voice.gainNode.gain.exponentialRampToValueAtTime(0.0001, stopAt);
              for (const source of voice.sources) {
                try {
                  source.stop(stopAt);
                } catch (error) {
                  // Ignore stop issues
                }
              }
            } else if (voice.type === 'melodic') {
              const oscillators = Array.isArray(voice.oscillators) && voice.oscillators.length
                ? voice.oscillators
                : (voice.osc ? [voice.osc] : []);
              const fadeStart = now < voice.startTime ? voice.startTime : now;
              const fadeEnd = fadeStart + 0.12;
              if (voice.gainNode && voice.gainNode.gain) {
                try {
                  voice.gainNode.gain.cancelScheduledValues(fadeStart);
                } catch (error) {
                  // Ignore cancellation errors
                }
                const currentValue = voice.gainNode.gain.value;
                voice.gainNode.gain.setValueAtTime(currentValue, fadeStart);
                voice.gainNode.gain.exponentialRampToValueAtTime(0.0001, fadeEnd);
              }
              for (const osc of oscillators) {
                try {
                  if (fadeStart <= voice.startTime) {
                    osc.stop(voice.startTime);
                  } else {
                    osc.stop(fadeEnd + 0.02);
                  }
                } catch (error) {
                  // Ignore stop issues
                }
              }
              if (voice.lfo) {
                try {
                  const stopAt = fadeStart <= voice.startTime ? voice.startTime : fadeEnd;
                  voice.lfo.stop(stopAt);
                } catch (error) {
                  // Ignore stop issues
                }
              }
              if (voice.tremoloOffset) {
                try {
                  const stopAt = fadeStart <= voice.startTime ? voice.startTime : fadeEnd;
                  voice.tremoloOffset.stop(stopAt);
                } catch (error) {
                  // Ignore stop issues
                }
              }
              if (Array.isArray(voice.effectOscillators)) {
                const stopAt = fadeStart <= voice.startTime ? voice.startTime : fadeEnd;
                for (const eff of voice.effectOscillators) {
                  if (!eff || typeof eff.stop !== 'function') {
                    continue;
                  }
                  try {
                    eff.stop(stopAt + 0.02);
                  } catch (error) {
                    // Ignore stop issues
                  }
                }
              }
            }
          } catch (error) {
            // Ignorer les erreurs liées à l'état de l'oscillateur
          }
        }
        this.liveVoices.clear();
      }

      const wasPlaying = this.playing;
      this.playing = false;
      this.activePlaybackSpeed = this.playbackSpeed;
      this.updateButtons();

      const duration = this.progressDuration || this.getTimelineDuration();
      if (preservePosition) {
        const hasDuration = Number.isFinite(duration) && duration > 0;
        const referenceDuration = hasDuration ? duration : (this.timeline ? this.getTimelineDuration(this.timeline) : 0);
        const maxDuration = Number.isFinite(referenceDuration) && referenceDuration > 0 ? referenceDuration : 0;
        const lastPosition = Number.isFinite(this.lastKnownPosition) ? this.lastKnownPosition : 0;
        const clampedPosition = maxDuration > 0
          ? Math.max(0, Math.min(maxDuration, lastPosition))
          : Math.max(0, lastPosition);
        this.pendingSeekSeconds = clampedPosition;
        this.lastKnownPosition = clampedPosition;
      } else {
        if (manual) {
          this.pendingSeekSeconds = 0;
          this.lastKnownPosition = 0;
        } else if (duration > 0) {
          this.pendingSeekSeconds = 0;
          this.lastKnownPosition = Math.max(0, Math.min(duration, this.lastKnownPosition || 0));
        } else {
          this.pendingSeekSeconds = 0;
          this.lastKnownPosition = 0;
        }
      }
      this.refreshProgressControls(this.timeline);

      this.randomPlaybackCurrentTrack = null;
      if (manual && !skipRandomReset) {
        this.resetRandomPlayback();
      }

      if (manual && wasPlaying) {
        this.setStatusMessage('index.sections.options.chiptune.status.playbackStopped', 'Playback stopped: {title}', { title: this.currentTitle });
        this.scheduleReadyStatusRestore();
      }
    }

    startScheduler(startTime, offsetSeconds = 0) {
      if (!this.audioContext || !this.timeline) {
        return;
      }
      const startOffset = Math.max(0, Number.isFinite(offsetSeconds) ? offsetSeconds : 0);
      this.schedulerState = {
        startTime,
        index: this.findTimelineIndexAt(startOffset),
        speed: this.activePlaybackSpeed || 1,
        offset: startOffset,
      };
      this.processScheduler();
      if (this.schedulerInterval) {
        window.clearInterval(this.schedulerInterval);
      }
      this.schedulerInterval = window.setInterval(() => {
        this.processScheduler();
      }, Math.max(4, Math.floor(this.scheduleIntervalSeconds * 1000)));
    }

    stopScheduler() {
      if (this.schedulerInterval) {
        window.clearInterval(this.schedulerInterval);
        this.schedulerInterval = null;
      }
      this.schedulerState = null;
    }

    processScheduler() {
      if (!this.playing || !this.schedulerState || !this.audioContext || !this.timeline) {
        return;
      }

      const { startTime, speed: schedulerSpeed } = this.schedulerState;
      const speed = this.clampPlaybackSpeed(schedulerSpeed || 1);
      const notes = this.timeline.notes;
      if (!Array.isArray(notes) || !notes.length) {
        this.stopScheduler();
        return;
      }

      let index = this.schedulerState.index;
      const currentTime = this.audioContext.currentTime;
      const windowEnd = currentTime + this.scheduleAheadTime;

      while (index < notes.length) {
        const note = notes[index];
        const offset = Number.isFinite(note.startTime) ? note.startTime : 0;
        const noteStart = startTime + (offset / speed);
        if (noteStart > windowEnd) {
          break;
        }
        this.scheduleNote(note, startTime, speed);
        index += 1;
      }

      this.schedulerState.index = index;

      if (index >= notes.length) {
        this.stopScheduler();
      }
    }
  }

  const elements = {
    fileInput: document.getElementById('chiptuneFileInput'),
    dropZone: document.getElementById('chiptuneDropZone'),
    folderButton: document.getElementById('chiptuneImportFolderButton'),
    folderInput: document.getElementById('chiptuneFolderInput'),
    artistSelect: document.getElementById('chiptuneArtistSelect'),
    trackSelect: document.getElementById('chiptuneTrackSelect'),
    trackSlider: document.getElementById('chiptuneTrackSlider'),
    trackSliderValue: document.getElementById('chiptuneTrackSliderValue'),
    headerPlaybackButton: document.getElementById('headerPlaybackToggle'),
    playButton: document.getElementById('chiptunePlayButton'),
    skipButton: document.getElementById('chiptuneSkipButton'),
    stopButton: document.getElementById('chiptuneStopButton'),
    randomAllButton: document.getElementById('chiptuneRandomAllButton'),
    randomArtistButton: document.getElementById('chiptuneRandomArtistButton'),
    status: document.getElementById('chiptuneStatus'),
    volumeSlider: document.getElementById('chiptuneVolumeSlider'),
    volumeValue: document.getElementById('chiptuneVolumeValue'),
    transposeSlider: document.getElementById('chiptuneTransposeSlider'),
    transposeValue: document.getElementById('chiptuneTransposeValue'),
    fineTuneSlider: document.getElementById('chiptuneFineTuneSlider'),
    fineTuneValue: document.getElementById('chiptuneFineTuneValue'),
    octaveDownButton: document.getElementById('chiptuneOctaveDownButton'),
    octaveUpButton: document.getElementById('chiptuneOctaveUpButton'),
    resetPitchButton: document.getElementById('chiptuneResetPitchButton'),
    articulationSlider: document.getElementById('chiptuneArticulationSlider'),
    articulationValue: document.getElementById('chiptuneArticulationValue'),
    speedSlider: document.getElementById('chiptuneSpeedSlider'),
    speedValue: document.getElementById('chiptuneSpeedValue'),
    pianoOverrideToggle: document.getElementById('chiptunePianoOverrideToggle'),
    pianoOverrideValue: document.getElementById('chiptunePianoOverrideValue'),
    engineSelect: document.getElementById('chiptuneEngineSelect'),
    soundFontSelect: document.getElementById('chiptuneSoundFontSelect'),
    progressLabel: document.getElementById('chiptuneProgressLabel'),
    progressSlider: document.getElementById('chiptuneProgressSlider'),
    progressValue: document.getElementById('chiptuneProgressValue'),
    programUsageContainer: document.getElementById('chiptuneProgramUsage'),
    programUsageSummary: document.getElementById('chiptuneUsageSummary'),
    programUsageTitle: document.getElementById('chiptuneUsageTitle'),
    programUsageNote: document.getElementById('chiptuneUsageNote'),
  };

  if (elements.fileInput && elements.playButton && elements.stopButton && elements.status) {
    new ChiptunePlayer(elements);
  }
})();
