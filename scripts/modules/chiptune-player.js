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

          // Ignorer les autres évènements canal (CC, pitch bend...)
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
      const channelPrograms = new Map();
      const notes = [];

      const flushRemainingNotes = (fallback) => {
        for (const stack of activeNotes.values()) {
          while (stack.length) {
            const pending = stack.shift();
            const duration = Math.max(0.12, fallback - pending.time);
            notes.push({
              note: pending.note,
              startTime: pending.time,
              duration,
              velocity: pending.velocity,
              channel: pending.channel,
              program: pending.program,
            });
          }
        }
        activeNotes.clear();
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
          channelPrograms.set(event.channel, event.program);
          continue;
        }

        if (event.type !== 'noteOn' && event.type !== 'noteOff') {
          continue;
        }

        const key = `${event.channel}:${event.note}`;
        if (!activeNotes.has(key)) {
          activeNotes.set(key, []);
        }

        if (event.type === 'noteOn') {
          const program = channelPrograms.has(event.channel)
            ? channelPrograms.get(event.channel)
            : 0;
          activeNotes.get(key).push({
            time: currentTime,
            velocity: event.velocity / 127,
            note: event.note,
            channel: event.channel,
            program,
          });
        } else {
          const stack = activeNotes.get(key);
          if (stack && stack.length) {
            const pending = stack.shift();
            const duration = Math.max(0.08, currentTime - pending.time);
            notes.push({
              note: event.note,
              startTime: pending.time,
              duration,
              velocity: pending.velocity,
              channel: pending.channel,
              program: pending.program,
            });
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

      this.audioContext = null;
      this.masterGain = null;
      this.masterLimiter = null;
      this.timeline = null;
      this.currentTitle = '';
      this.playing = false;
      this.pendingTimers = new Set();
      this.liveVoices = new Set();
      this.finishTimeout = null;
      this.libraryTracks = [];

      this.masterVolume = 0.8;
      this.maxGain = 0.32;
      this.waveCache = new Map();
      this.noiseBuffer = null;
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

      if (this.volumeSlider) {
        const sliderValue = Number.parseFloat(this.volumeSlider.value);
        if (Number.isFinite(sliderValue)) {
          this.masterVolume = Math.max(0, Math.min(1, sliderValue / 100));
        } else {
          this.volumeSlider.value = String(Math.round(this.masterVolume * 100));
        }
      }
      this.setMasterVolume(this.masterVolume, false);

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
        this.masterGain = this.audioContext.createGain();
        this.masterGain.gain.value = this.masterVolume;
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
      return 440 * Math.pow(2, (note - 69) / 12);
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

    getInstrumentSettings(note) {
      const program = Number.isFinite(note.program) ? note.program : 0;

      const baseEnvelope = {
        attack: 0.012,
        decay: 0.06,
        sustain: 0.6,
        release: Math.max(0.16, Math.min(0.5, note.duration * 0.7)),
      };

      const family = Math.floor(program / 8);
      switch (family) {
        case 0: // Pianos / Chromatic Perc.
          return {
            type: 'triangle',
            envelope: {
              ...baseEnvelope,
              attack: 0.01,
              decay: 0.12,
              sustain: 0.45,
              release: Math.max(0.22, note.duration * 0.6),
            },
            lfo: { rate: 5, vibratoDepth: 14, tremoloDepth: 0.12 },
            volume: 0.85,
            filter: { type: 'lowpass', frequency: 2200, Q: 0.7 },
          };
        case 1: // Orgues
          return {
            type: 'pulse',
            dutyCycle: 0.375,
            envelope: { ...baseEnvelope, decay: 0.08, sustain: 0.7, release: Math.max(0.2, note.duration * 0.9) },
            lfo: { rate: 4.5, vibratoDepth: 18, tremoloDepth: 0.18 },
            volume: 0.85,
          };
        case 2: // Guitares
          return {
            type: 'pulse',
            dutyCycle: 0.18,
            envelope: { ...baseEnvelope, decay: 0.05, sustain: 0.45 },
            lfo: { rate: 6.5, vibratoDepth: 24, tremoloDepth: 0.1 },
            volume: 0.82,
          };
        case 3: // Basses
          return {
            type: 'triangle',
            envelope: { ...baseEnvelope, decay: 0.08, sustain: 0.55 },
            lfo: { rate: 4, vibratoDepth: 12, tremoloDepth: 0.05 },
            volume: 1,
          };
        case 4: // Cordes
          return {
            type: 'sawtooth',
            envelope: { ...baseEnvelope, attack: 0.02, decay: 0.12, sustain: 0.65 },
            lfo: { rate: 5, vibratoDepth: 20, tremoloDepth: 0.16 },
            volume: 0.75,
          };
        case 5: // Ensembles
          return {
            type: 'pulse',
            dutyCycle: 0.44,
            envelope: { ...baseEnvelope, attack: 0.018, decay: 0.12, sustain: 0.7 },
            lfo: { rate: 5.2, vibratoDepth: 26, tremoloDepth: 0.22 },
            volume: 0.78,
          };
        case 6: // Cuivres
          return {
            type: 'pulse',
            dutyCycle: 0.22,
            envelope: { ...baseEnvelope, attack: 0.015, decay: 0.08, sustain: 0.5 },
            lfo: { rate: 5.8, vibratoDepth: 22, tremoloDepth: 0.18 },
            volume: 0.9,
          };
        case 7: // Leads
          return {
            type: 'pulse',
            dutyCycle: 0.12,
            envelope: { ...baseEnvelope, decay: 0.05, sustain: 0.4 },
            lfo: { rate: 6.8, vibratoDepth: 32, tremoloDepth: 0.12 },
            volume: 0.92,
          };
        case 8: // Pad
          return {
            type: 'sawtooth',
            envelope: { ...baseEnvelope, attack: 0.03, decay: 0.16, sustain: 0.75, release: Math.max(0.3, note.duration) },
            lfo: { rate: 4.2, vibratoDepth: 18, tremoloDepth: 0.2 },
            volume: 0.7,
          };
        default:
          return {
            type: 'pulse',
            dutyCycle: 0.5,
            envelope: baseEnvelope,
            lfo: { rate: 5, vibratoDepth: 18, tremoloDepth: 0.1 },
            volume: 0.85,
          };
      }
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
      const velocity = Math.max(0.1, Math.min(1, note.velocity));

      const instrument = this.getInstrumentSettings(note);
      const osc = this.audioContext.createOscillator();
      const gainNode = this.audioContext.createGain();
      const frequency = this.midiNoteToFrequency(note.note);
      const lfoSettings = this.getLfoSettings(instrument);

      if (instrument.type === 'pulse') {
        const wave = this.getPulseWave(instrument.dutyCycle || 0.5);
        if (wave) {
          osc.setPeriodicWave(wave);
        } else {
          osc.type = 'square';
        }
      } else {
        osc.type = instrument.type;
      }

      const peakGain = Math.min(1, velocity * this.maxGain * (instrument.volume || 1));
      const envelope = instrument.envelope || {};
      const attack = Math.max(0.001, envelope.attack || 0.01);
      const decay = Math.max(0.005, envelope.decay || 0.05);
      const sustainLevel = Math.min(1, Math.max(0, envelope.sustain ?? 0.5));
      const releaseDuration = Math.max(0.06, envelope.release || Math.min(0.5, note.duration * 0.6));
      const stopAt = startAt + note.duration;

      const attackEnd = Math.min(stopAt, startAt + attack);
      const decayEnd = Math.min(stopAt, attackEnd + decay);

      gainNode.gain.cancelScheduledValues(startAt);
      gainNode.gain.setValueAtTime(0.0001, startAt);
      gainNode.gain.linearRampToValueAtTime(peakGain, attackEnd);
      gainNode.gain.linearRampToValueAtTime(Math.max(0.0001, peakGain * sustainLevel), decayEnd);
      gainNode.gain.setValueAtTime(Math.max(0.0001, peakGain * sustainLevel), stopAt);
      gainNode.gain.exponentialRampToValueAtTime(0.0001, stopAt + releaseDuration);

      osc.frequency.setValueAtTime(frequency, startAt);
      const voice = {
        type: 'melodic',
        osc,
        gainNode,
        startTime: startAt,
        stopTime: stopAt + releaseDuration,
      };

      const hasVibrato = lfoSettings && lfoSettings.vibratoDepth > 0;
      const hasTremolo = lfoSettings && lfoSettings.tremoloDepth > 0;
      const needsLfo = lfoSettings && (hasVibrato || hasTremolo);

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
        lfoOscillator.connect(vibratoGain).connect(osc.frequency);
        voice.lfoGain = vibratoGain;
      }

      let lastNode = osc;
      if (instrument.filter) {
        const filter = this.audioContext.createBiquadFilter();
        if (instrument.filter.type) {
          filter.type = instrument.filter.type;
        }
        if (Number.isFinite(instrument.filter.frequency)) {
          filter.frequency.setValueAtTime(instrument.filter.frequency, startAt);
        }
        if (Number.isFinite(instrument.filter.Q)) {
          filter.Q.setValueAtTime(instrument.filter.Q, startAt);
        }
        lastNode.connect(filter);
        lastNode = filter;
        voice.filterNode = filter;
      }

      if (lfoOscillator && hasTremolo) {
        const tremoloNode = this.audioContext.createGain();
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
        tremoloOffset.stop(stopAt + releaseDuration);

        voice.tremoloNode = tremoloNode;
        voice.tremoloGain = tremoloGain;
        voice.tremoloOffset = tremoloOffset;
      }

      lastNode.connect(gainNode);
      gainNode.connect(this.masterGain);

      this.liveVoices.add(voice);
      osc.onended = () => {
        this.liveVoices.delete(voice);
        gainNode.disconnect();
        if (voice.filterNode) {
          try {
            voice.filterNode.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        if (voice.tremoloNode) {
          try {
            voice.tremoloNode.disconnect();
          } catch (error) {
            // Ignore disconnect issues
          }
        }
        if (voice.tremoloGain) {
          try {
            voice.tremoloGain.disconnect();
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

      osc.start(startAt);
      osc.stop(stopAt + releaseDuration);
      if (lfoOscillator && (hasVibrato || hasTremolo)) {
        lfoOscillator.start(startAt);
        lfoOscillator.stop(stopAt + releaseDuration);
        voice.lfo = lfoOscillator;
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
      gainNode.connect(this.masterGain);

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
            } else if (voice.osc) {
              if (now < voice.startTime) {
                voice.osc.stop(voice.startTime);
              } else {
                voice.gainNode.gain.cancelScheduledValues(now);
                voice.gainNode.gain.setValueAtTime(voice.gainNode.gain.value, now);
                voice.gainNode.gain.exponentialRampToValueAtTime(0.0001, now + 0.08);
                voice.osc.stop(now + 0.12);
              }
              if (voice.lfo) {
                const stopAt = now < voice.startTime ? voice.startTime : now + 0.12;
                voice.lfo.stop(stopAt);
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
  };

  if (elements.fileInput && elements.playButton && elements.stopButton && elements.status) {
    new ChiptunePlayer(elements);
  }
})();
