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

      this.audioContext = null;
      this.masterGain = null;
      this.timeline = null;
      this.currentTitle = '';
      this.playing = false;
      this.pendingTimers = new Set();
      this.liveVoices = new Set();
      this.finishTimeout = null;
      this.libraryTracks = [];

      this.maxGain = 0.32;
      this.waveCache = new Map();

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
        this.masterGain = this.audioContext.createGain();
        this.masterGain.gain.value = 1;
        this.masterGain.connect(this.audioContext.destination);
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
            type: 'pulse',
            dutyCycle: 0.25,
            envelope: { ...baseEnvelope, decay: 0.04, sustain: 0.5 },
            vibrato: { depth: 3, rate: 5.5 },
            volume: 0.9,
          };
        case 1: // Orgues
          return {
            type: 'pulse',
            dutyCycle: 0.375,
            envelope: { ...baseEnvelope, decay: 0.08, sustain: 0.7, release: Math.max(0.2, note.duration * 0.9) },
            vibrato: { depth: 4, rate: 4.5 },
            volume: 0.85,
          };
        case 2: // Guitares
          return {
            type: 'pulse',
            dutyCycle: 0.18,
            envelope: { ...baseEnvelope, decay: 0.05, sustain: 0.45 },
            vibrato: { depth: 6, rate: 6.5 },
            volume: 0.82,
          };
        case 3: // Basses
          return {
            type: 'triangle',
            envelope: { ...baseEnvelope, decay: 0.08, sustain: 0.55 },
            vibrato: { depth: 2, rate: 4 },
            volume: 1,
          };
        case 4: // Cordes
          return {
            type: 'sawtooth',
            envelope: { ...baseEnvelope, attack: 0.02, decay: 0.12, sustain: 0.65 },
            vibrato: { depth: 5, rate: 5 },
            volume: 0.75,
          };
        case 5: // Ensembles
          return {
            type: 'pulse',
            dutyCycle: 0.44,
            envelope: { ...baseEnvelope, attack: 0.018, decay: 0.12, sustain: 0.7 },
            vibrato: { depth: 8, rate: 5.2 },
            volume: 0.78,
          };
        case 6: // Cuivres
          return {
            type: 'pulse',
            dutyCycle: 0.22,
            envelope: { ...baseEnvelope, attack: 0.015, decay: 0.08, sustain: 0.5 },
            vibrato: { depth: 7, rate: 5.8 },
            volume: 0.9,
          };
        case 7: // Leads
          return {
            type: 'pulse',
            dutyCycle: 0.12,
            envelope: { ...baseEnvelope, decay: 0.05, sustain: 0.4 },
            vibrato: { depth: 10, rate: 6.8 },
            volume: 0.92,
          };
        case 8: // Pad
          return {
            type: 'sawtooth',
            envelope: { ...baseEnvelope, attack: 0.03, decay: 0.16, sustain: 0.75, release: Math.max(0.3, note.duration) },
            vibrato: { depth: 6, rate: 4.2 },
            volume: 0.7,
          };
        default:
          return {
            type: 'pulse',
            dutyCycle: 0.5,
            envelope: baseEnvelope,
            vibrato: { depth: 3, rate: 5 },
            volume: 0.85,
          };
      }
    }

    scheduleNote(note, baseTime) {
      if (!this.audioContext || !this.masterGain) {
        return;
      }
      const startAt = baseTime + note.startTime;
      const velocity = Math.max(0.1, Math.min(1, note.velocity));

      const instrument = this.getInstrumentSettings(note);
      const osc = this.audioContext.createOscillator();
      const gainNode = this.audioContext.createGain();
      const frequency = this.midiNoteToFrequency(note.note);

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
        osc,
        gainNode,
        startTime: startAt,
        stopTime: stopAt + releaseDuration,
      };

      let lfo = null;
      let lfoGain = null;
      if (instrument.vibrato && instrument.vibrato.depth > 0 && instrument.vibrato.rate > 0) {
        lfo = this.audioContext.createOscillator();
        lfoGain = this.audioContext.createGain();
        lfo.frequency.setValueAtTime(instrument.vibrato.rate, startAt);
        lfoGain.gain.setValueAtTime(instrument.vibrato.depth, startAt);
        lfo.connect(lfoGain).connect(osc.frequency);
        lfo.start(startAt);
        lfo.stop(stopAt + releaseDuration);
        voice.lfo = lfo;
        voice.lfoGain = lfoGain;
      }

      osc.connect(gainNode).connect(this.masterGain);

      this.liveVoices.add(voice);
      osc.onended = () => {
        this.liveVoices.delete(voice);
        gainNode.disconnect();
        if (voice.lfoGain) {
          voice.lfoGain.disconnect();
        }
        if (voice.lfo) {
          voice.lfo.disconnect();
        }
      };

      osc.start(startAt);
      osc.stop(stopAt + releaseDuration);
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
        for (const note of this.timeline.notes) {
          const delay = Math.max(0, (startTime + note.startTime - context.currentTime) * 1000);
          const timerId = window.setTimeout(() => {
            this.pendingTimers.delete(timerId);
            if (!this.playing) {
              return;
            }
            this.scheduleNote(note, startTime);
          }, delay);
          this.pendingTimers.add(timerId);
        }

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

      for (const timerId of this.pendingTimers) {
        window.clearTimeout(timerId);
      }
      this.pendingTimers.clear();

      if (this.audioContext) {
        const now = this.audioContext.currentTime;
        for (const voice of this.liveVoices) {
          try {
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
  }

  const elements = {
    fileInput: document.getElementById('chiptuneFileInput'),
    librarySelect: document.getElementById('chiptuneLibrarySelect'),
    playButton: document.getElementById('chiptunePlayButton'),
    stopButton: document.getElementById('chiptuneStopButton'),
    status: document.getElementById('chiptuneStatus'),
  };

  if (elements.fileInput && elements.playButton && elements.stopButton && elements.status) {
    new ChiptunePlayer(elements);
  }
})();
