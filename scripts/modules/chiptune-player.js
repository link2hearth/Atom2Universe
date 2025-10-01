(function () {
  'use strict';

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
          throw new Error('Trame MIDI incomplète (VLQ).');
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
        throw new Error('En-tête MIDI invalide.');
      }
      const headerLength = reader.readUint32();
      if (headerLength < 6) {
        throw new Error('En-tête MIDI incomplet.');
      }
      const formatType = reader.readUint16();
      const trackCount = reader.readUint16();
      const division = reader.readUint16();
      if (headerLength > 6) {
        reader.skip(headerLength - 6);
      }

      if (trackCount < 1) {
        throw new Error('Le fichier MIDI ne contient aucune piste.');
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
              throw new Error('Status MIDI manquant pour un évènement.');
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
        throw new Error('Aucune piste MIDI exploitable.');
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
        throw new Error('Le fichier ne contient pas de notes jouables.');
      }
      if (!parsed.division) {
        throw new Error('Résolution MIDI invalide.');
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
      this.librarySelect = elements.librarySelect;
      this.playButton = elements.playButton;
      this.stopButton = elements.stopButton;
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

      this.audioContext = null;
      this.masterGain = null;
      this.masterLimiter = null;
      this.dryBus = null;
      this.reverbSend = null;
      this.reverbMix = null;
      this.reverbNode = null;
      this.timeline = null;
      this.currentTitle = '';
      this.playing = false;
      this.pendingTimers = new Set();
      this.liveVoices = new Set();
      this.finishTimeout = null;
      this.libraryTracks = [];

      this.masterVolume = 0.8;
      this.maxGain = 0.26;
      this.waveCache = new Map();
      this.noiseBuffer = null;
      this.reverbBuffer = null;
      this.reverbDefaultSend = 0.3;
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
      this.setTransposeSemitones(this.transposeSemitones, { syncSlider: true, refreshVoices: false });
      this.setFineDetuneCents(this.fineDetuneCents, { syncSlider: true, refreshVoices: false });

      this.bindEvents();
      this.updateButtons();
      this.loadLibrary();
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

      if (this.librarySelect) {
        this.librarySelect.addEventListener('change', () => {
          const file = this.librarySelect.value;
          if (!file) {
            return;
          }
          const track = this.libraryTracks.find(item => item.file === file);
          if (!track) {
            this.setStatus('Impossible de trouver le morceau sélectionné.', 'error');
            return;
          }
          this.loadFromLibrary(track);
        });
      }

      if (this.playButton) {
        this.playButton.addEventListener('click', () => {
          this.play();
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

    formatSemitoneLabel(value) {
      const normalized = Number.isFinite(value) ? Math.round(value) : 0;
      if (normalized === 0) {
        return '0 demi-ton';
      }
      const sign = normalized > 0 ? '+' : '−';
      const abs = Math.abs(normalized);
      if (abs % 12 === 0) {
        const octaves = abs / 12;
        const unit = octaves > 1 ? 'octaves' : 'octave';
        return `${sign}${octaves} ${unit}`;
      }
      return `${sign}${abs} demi-ton${abs > 1 ? 's' : ''}`;
    }

    formatCentsLabel(value) {
      const normalized = Number.isFinite(value) ? Math.round(value) : 0;
      if (normalized === 0) {
        return '0 cent';
      }
      const sign = normalized > 0 ? '+' : '−';
      const abs = Math.abs(normalized);
      return `${sign}${abs} cent${abs > 1 ? 's' : ''}`;
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

    setStatus(message, state = 'idle') {
      if (!this.status) {
        return;
      }
      this.status.textContent = message;
      this.status.classList.remove('is-error', 'is-success');
      if (state === 'error') {
        this.status.classList.add('is-error');
      } else if (state === 'success') {
        this.status.classList.add('is-success');
      }
    }

    updateButtons() {
      if (this.playButton) {
        this.playButton.disabled = !this.timeline || this.playing;
      }
      if (this.stopButton) {
        this.stopButton.disabled = !this.playing;
      }
    }

    async ensureContext() {
      if (!this.audioContext) {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) {
          throw new Error('API Web Audio non disponible dans ce navigateur.');
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
        this.reverbMix.gain.value = 0.45;

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
      this.stop();
      this.setStatus(`Chargement de « ${file.name} »...`);
      try {
        const buffer = await file.arrayBuffer();
        await this.loadFromBuffer(buffer, file.name);
      } catch (error) {
        console.error(error);
        this.setStatus(`Impossible de lire le fichier : ${error.message}`, 'error');
        this.timeline = null;
        this.updateButtons();
      } finally {
        if (this.fileInput) {
          this.fileInput.value = '';
        }
        if (this.librarySelect) {
          this.librarySelect.value = '';
        }
      }
    }

    async loadFromLibrary(track) {
      this.stop();
      const label = track.name || track.file;
      this.setStatus(`Chargement de « ${label} »...`);
      if (this.fileInput) {
        this.fileInput.value = '';
      }
      try {
        const buffer = await this.fetchArrayBuffer(track.file);
        await this.loadFromBuffer(buffer, label);
      } catch (error) {
        console.error(error);
        this.setStatus(`Impossible de charger le morceau : ${error.message}`, 'error');
        this.timeline = null;
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
      try {
        const midi = MidiParser.parse(buffer);
        const timeline = MidiTimeline.fromMidi(midi);
        this.timeline = timeline;
        this.currentTitle = label || 'MIDI inconnu';
        this.setStatus(`Prêt : ${this.currentTitle} (${MidiTimeline.formatDuration(timeline.duration)})`, 'success');
      } catch (error) {
        throw error;
      }
      this.updateButtons();
    }

    async loadLibrary() {
      if (!this.librarySelect) {
        return;
      }
      try {
        const response = await fetch('resources/chiptune/library.json', { cache: 'no-cache' });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const payload = await response.json();
        const tracks = Array.isArray(payload?.tracks) ? payload.tracks : [];
        this.populateLibrary(tracks, false);
      } catch (error) {
        console.error('Unable to load chiptune library manifest', error);
        this.populateLibrary([], true);
      }
    }

    populateLibrary(tracks, errored) {
      this.libraryTracks = tracks
        .filter(item => item && typeof item.file === 'string')
        .map(item => ({
          file: item.file,
          name: typeof item.name === 'string' && item.name ? item.name : null,
        }));

      if (!this.librarySelect) {
        return;
      }

      while (this.librarySelect.options.length > 1) {
        this.librarySelect.remove(1);
      }

      this.librarySelect.value = '';

      for (const track of this.libraryTracks) {
        const option = document.createElement('option');
        option.value = track.file;
        option.textContent = track.name || track.file.replace(/^.*\//, '');
        this.librarySelect.append(option);
      }

      this.librarySelect.disabled = this.libraryTracks.length === 0;

      if (this.libraryTracks.length === 0) {
        this.librarySelect.options[0].textContent = errored
          ? 'Bibliothèque locale indisponible'
          : 'Aucun morceau local pour le moment';
      } else {
        this.librarySelect.options[0].textContent = 'Sélectionnez un morceau';
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
        this.reverbBuffer = this.buildReverbImpulse(2.6, 3.2);
      }
      convolver.buffer = this.reverbBuffer;
      return convolver;
    }

    buildReverbImpulse(durationSeconds = 2.6, decay = 3.2) {
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
        attack: Math.max(0.005, Math.min(0.06, 0.012 + (note.rawVelocity ? (0.04 * (1 - note.rawVelocity)) : 0))),
        decay: 0.1,
        sustain: 0.65,
        release: Math.max(0.22, Math.min(0.8, note.duration * 0.85)),
      };

      const definitions = {
        default: {
          gain: 0.9,
          layers: [
            { type: 'sawtooth', detune: -6, gain: 0.45 },
            { type: 'sawtooth', detune: 6, gain: 0.45 },
            { type: 'triangle', detune: 0, gain: 0.2 },
          ],
          filter: { type: 'lowpass', frequency: 4200, Q: 0.7 },
          lfo: { rate: 5.2, vibratoDepth: 18, tremoloDepth: 0.12 },
          reverbSend: 0.35,
          envelope: { ...baseEnvelope },
        },
        0: {
          gain: 0.85,
          layers: [
            { type: 'triangle', detune: -4, gain: 0.55 },
            { type: 'triangle', detune: 4, gain: 0.55 },
            { type: 'sawtooth', detune: 0, gain: 0.35 },
          ],
          filter: { type: 'lowpass', frequency: 3600, Q: 0.9 },
          lfo: { rate: 5.5, vibratoDepth: 14, tremoloDepth: 0.1 },
          reverbSend: 0.32,
          envelope: {
            ...baseEnvelope,
            attack: Math.max(0.003, baseEnvelope.attack * 0.7),
            decay: 0.18,
            sustain: 0.5,
          },
        },
        1: {
          gain: 0.88,
          layers: [
            { type: 'pulse', dutyCycle: 0.33, detune: -2, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.42, detune: 2, gain: 0.5 },
            { type: 'triangle', detune: 0, gain: 0.25 },
          ],
          filter: { type: 'lowpass', frequency: 3900, Q: 0.6 },
          lfo: { rate: 4.4, vibratoDepth: 18, tremoloDepth: 0.18 },
          reverbSend: 0.28,
          envelope: { ...baseEnvelope, decay: 0.14, sustain: 0.72, release: Math.max(0.4, note.duration) },
        },
        2: {
          gain: 0.82,
          layers: [
            { type: 'pulse', dutyCycle: 0.22, detune: -7, gain: 0.6 },
            { type: 'pulse', dutyCycle: 0.18, detune: 7, gain: 0.5 },
            { type: 'triangle', detune: 0, gain: 0.2 },
          ],
          filter: { type: 'lowpass', frequency: 3400, Q: 0.9 },
          lfo: { rate: 6.2, vibratoDepth: 20, tremoloDepth: 0.12 },
          reverbSend: 0.26,
          envelope: { ...baseEnvelope, decay: 0.08, sustain: 0.48, release: Math.max(0.25, note.duration * 0.5) },
        },
        3: {
          gain: 1,
          layers: [
            { type: 'triangle', detune: -3, gain: 0.55 },
            { type: 'triangle', detune: 3, gain: 0.55 },
            { type: 'sine', detune: -12, gain: 0.2 },
          ],
          filter: { type: 'lowpass', frequency: 2800, Q: 1 },
          lfo: { rate: 3.8, vibratoDepth: 10, tremoloDepth: 0.06 },
          reverbSend: 0.22,
          envelope: { ...baseEnvelope, decay: 0.12, sustain: 0.62, release: Math.max(0.32, note.duration * 0.7) },
        },
        4: {
          gain: 0.78,
          layers: [
            { type: 'sawtooth', detune: -12, gain: 0.45 },
            { type: 'sawtooth', detune: 12, gain: 0.45 },
            { type: 'triangle', detune: 0, gain: 0.35 },
            { type: 'sine', detune: 7, gain: 0.15 },
          ],
          filter: { type: 'lowpass', frequency: 3600, Q: 0.8 },
          lfo: { rate: 5.1, vibratoDepth: 24, tremoloDepth: 0.18 },
          reverbSend: 0.42,
          envelope: { ...baseEnvelope, attack: Math.max(0.015, baseEnvelope.attack * 1.4), decay: 0.14, sustain: 0.72 },
        },
        5: {
          gain: 0.8,
          layers: [
            { type: 'sawtooth', detune: -15, gain: 0.4 },
            { type: 'sawtooth', detune: 15, gain: 0.4 },
            { type: 'pulse', dutyCycle: 0.48, detune: 0, gain: 0.3 },
            { type: 'triangle', detune: 7, gain: 0.2 },
          ],
          filter: { type: 'lowpass', frequency: 3200, Q: 0.7 },
          lfo: { rate: 5.4, vibratoDepth: 28, tremoloDepth: 0.22 },
          reverbSend: 0.48,
          envelope: { ...baseEnvelope, attack: Math.max(0.02, baseEnvelope.attack * 1.6), decay: 0.18, sustain: 0.78 },
        },
        6: {
          gain: 0.92,
          layers: [
            { type: 'sawtooth', detune: -9, gain: 0.5 },
            { type: 'sawtooth', detune: 9, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.28, detune: 0, gain: 0.25 },
          ],
          filter: { type: 'lowpass', frequency: 3800, Q: 0.9 },
          lfo: { rate: 5.6, vibratoDepth: 20, tremoloDepth: 0.2 },
          reverbSend: 0.34,
          envelope: { ...baseEnvelope, attack: Math.max(0.012, baseEnvelope.attack), decay: 0.12, sustain: 0.6 },
        },
        7: {
          gain: 0.94,
          layers: [
            { type: 'pulse', dutyCycle: 0.18, detune: -6, gain: 0.55 },
            { type: 'pulse', dutyCycle: 0.22, detune: 6, gain: 0.55 },
            { type: 'sawtooth', detune: 0, gain: 0.25 },
          ],
          filter: { type: 'bandpass', frequency: 4200, Q: 1.1 },
          lfo: { rate: 6.8, vibratoDepth: 30, tremoloDepth: 0.16 },
          reverbSend: 0.3,
          envelope: { ...baseEnvelope, decay: 0.1, sustain: 0.55, release: Math.max(0.26, note.duration * 0.6) },
        },
        8: {
          gain: 0.72,
          layers: [
            { type: 'sawtooth', detune: -18, gain: 0.4 },
            { type: 'sawtooth', detune: 18, gain: 0.4 },
            { type: 'triangle', detune: 0, gain: 0.35 },
            { type: 'sine', detune: 10, gain: 0.18 },
          ],
          filter: { type: 'lowpass', frequency: 3000, Q: 0.7 },
          lfo: { rate: 4.4, vibratoDepth: 22, tremoloDepth: 0.24 },
          reverbSend: 0.52,
          envelope: { ...baseEnvelope, attack: Math.max(0.028, baseEnvelope.attack * 2.4), decay: 0.22, sustain: 0.82, release: Math.max(0.6, note.duration) },
        },
      };

      const definition = definitions[family] || definitions.default;
      return {
        ...definition,
        envelope: {
          ...definition.envelope,
          release: Math.max(definition.envelope.release || 0.25, Math.min(1.6, note.duration * 0.9)),
        },
        reverbSend: Number.isFinite(definition.reverbSend)
          ? definition.reverbSend
          : this.reverbDefaultSend,
      };
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
          tone: { type: 'square', frequency: 330, frequencyEnd: 200, level: 0.6 },
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

    scheduleNote(note, baseTime) {
      if (!this.audioContext || !this.masterGain) {
        return;
      }

      if (note.channel === 9) {
        this.schedulePercussion(note, baseTime);
        return;
      }

      const now = this.audioContext.currentTime;
      const startAt = Math.max(baseTime + note.startTime, now + 0.001);
      const velocity = Math.max(0.08, Math.min(1, note.velocity || 0.2));

      const instrument = this.getInstrumentSettings(note);
      const frequency = this.midiNoteToFrequency(note.note);
      const lfoSettings = this.getLfoSettings(instrument);

      const hasVibrato = lfoSettings && lfoSettings.vibratoDepth > 0;
      const hasTremolo = lfoSettings && lfoSettings.tremoloDepth > 0;
      const needsLfo = lfoSettings && (hasVibrato || hasTremolo);

      const layers = Array.isArray(instrument.layers) && instrument.layers.length
        ? instrument.layers
        : [{ type: instrument.type || 'sawtooth', dutyCycle: instrument.dutyCycle, detune: 0, gain: 1 }];
      const totalLayerGain = layers.reduce((sum, layer) => sum + Math.max(0, layer.gain ?? 1), 0) || 1;

      const voiceInput = this.audioContext.createGain();
      voiceInput.gain.setValueAtTime(1, startAt);

      const peakGain = Math.min(1, velocity * this.maxGain * (instrument.gain || instrument.volume || 1));
      const envelope = instrument.envelope || {};
      const attack = Math.max(0.002, envelope.attack || 0.01);
      const decay = Math.max(0.01, envelope.decay || 0.06);
      const sustainLevel = Math.min(1, Math.max(0, envelope.sustain ?? 0.6));
      const releaseDuration = Math.max(0.12, envelope.release || Math.min(0.9, note.duration * 0.8));
      const stopAt = startAt + note.duration;
      const attackEnd = Math.min(stopAt, startAt + attack);
      const decayEnd = Math.min(stopAt, attackEnd + decay);

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
        nodes: [voiceInput],
        baseMidiNote: note.note,
      };

      this.liveVoices.add(voice);

      let activeOscillators = 0;

      const connectOscillator = (layer) => {
        const osc = this.audioContext.createOscillator();
        if (layer.type === 'pulse') {
          const wave = this.getPulseWave(layer.dutyCycle || instrument.dutyCycle || 0.5);
          if (wave) {
            osc.setPeriodicWave(wave);
          } else {
            osc.type = 'square';
          }
        } else {
          osc.type = layer.type || instrument.type || 'sawtooth';
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
          const velocityInfluence = 1 + (velocity * 0.8);
          filter.frequency.setValueAtTime(instrument.filter.frequency * velocityInfluence, startAt);
        }
        lastNode.connect(filter);
        lastNode = filter;
        voice.filterNode = filter;
        voice.nodes.push(filter);
      }

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
      const panValue = Math.max(-1, Math.min(1, Number.isFinite(note.pan) ? note.pan : 0));
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

      const reverbAmount = Math.max(0, Math.min(1, instrument.reverbSend ?? this.reverbDefaultSend));
      const dryAmount = Math.max(0, 1 - (reverbAmount * 0.65));

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
        dryGain.gain.setValueAtTime(Math.min(1, dryAmount + (reverbAmount * 0.5)), startAt);
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
      };

      if (lfoOscillator && (hasVibrato || hasTremolo)) {
        voice.lfo = lfoOscillator;
        this.audioContext.resume?.();
        lfoOscillator.start(startAt);
        lfoOscillator.stop(stopAt + releaseDuration + 0.05);
      }
    }

    schedulePercussion(note, baseTime) {
      if (!this.audioContext || !this.masterGain) {
        return;
      }

      const now = this.audioContext.currentTime;
      const startAt = Math.max(baseTime + note.startTime, now + 0.001);
      const velocity = Math.max(0.1, Math.min(1, note.velocity));
      const settings = this.getPercussionSettings(note);
      const envelope = settings.envelope || {};
      const attack = Math.max(0.001, envelope.attack || 0.005);
      const decay = Math.max(0.01, envelope.decay || 0.05);
      const sustainLevel = Math.max(0, Math.min(1, envelope.sustain ?? 0.0001));
      const release = Math.max(0.02, envelope.release || 0.08);
      const baseDuration = Math.max(0.05, settings.duration || note.duration || 0.18);
      const stopAt = startAt + baseDuration;
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

      const reverbAmount = Math.max(0, Math.min(1, (note.reverb ?? this.reverbDefaultSend) * 0.9));
      const dryAmount = Math.max(0, 1 - (reverbAmount * 0.6));

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
        dryGain.gain.setValueAtTime(Math.min(1, dryAmount + (reverbAmount * 0.4)), startAt);
      }
    }

    async play() {
      if (!this.timeline || !this.timeline.notes.length) {
        this.setStatus('Aucune donnée MIDI à lire.', 'error');
        return;
      }

      try {
        const context = await this.ensureContext();
        this.stop();
        this.playing = true;
        this.updateButtons();

        const startTime = context.currentTime + 0.05;
        this.startScheduler(startTime);

        this.finishTimeout = window.setTimeout(() => {
          this.finishTimeout = null;
          this.stop(false);
          this.setStatus(`Lecture terminée : ${this.currentTitle}`, 'success');
        }, Math.ceil((this.timeline.duration + 0.6) * 1000));

        this.setStatus(`Lecture en cours : ${this.currentTitle}`, 'success');
      } catch (error) {
        console.error(error);
        this.setStatus(`Lecture impossible : ${error.message}`, 'error');
        this.playing = false;
        this.updateButtons();
      }
    }

    stop(manual = false) {
      if (this.finishTimeout) {
        window.clearTimeout(this.finishTimeout);
        this.finishTimeout = null;
      }

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
            }
          } catch (error) {
            // Ignorer les erreurs liées à l'état de l'oscillateur
          }
        }
        this.liveVoices.clear();
      }

      const wasPlaying = this.playing;
      this.playing = false;
      this.updateButtons();

      if (manual && wasPlaying) {
        this.setStatus(`Lecture stoppée : ${this.currentTitle}`);
      }
    }

    startScheduler(startTime) {
      if (!this.audioContext || !this.timeline) {
        return;
      }
      this.schedulerState = {
        startTime,
        index: 0,
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

      const { startTime } = this.schedulerState;
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
        const noteStart = startTime + note.startTime;
        if (noteStart > windowEnd) {
          break;
        }
        this.scheduleNote(note, startTime);
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
    librarySelect: document.getElementById('chiptuneLibrarySelect'),
    playButton: document.getElementById('chiptunePlayButton'),
    stopButton: document.getElementById('chiptuneStopButton'),
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
  };

  if (elements.fileInput && elements.playButton && elements.stopButton && elements.status) {
    new ChiptunePlayer(elements);
  }
})();
