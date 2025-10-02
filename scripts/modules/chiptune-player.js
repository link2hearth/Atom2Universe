(function () {
  'use strict';

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
      this.articulationSlider = elements.articulationSlider;
      this.articulationValue = elements.articulationValue;
      this.speedSlider = elements.speedSlider;
      this.speedValue = elements.speedValue;
      this.engineSelect = elements.engineSelect;

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
      this.playing = false;
      this.pendingTimers = new Set();
      this.liveVoices = new Set();
      this.finishTimeout = null;
      this.libraryTracks = [];
      this.readyStatusMessage = '';
      this.lastStatusMessage = null;
      this.playStartTime = null;
      this.progressRaf = null;

      this.masterVolume = 0.8;
      this.maxGain = 0.26;
      this.waveCache = new Map();
      this.noiseBuffer = null;
      this.reverbBuffer = null;
      this.reverbDefaultSend = 0.12;
      this.sccWaveform = typeof window !== 'undefined' ? window.SccWaveform || null : null;
      this.engineMode = this.sccWaveform ? 'scc' : 'n163';
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
        const validModes = new Set(['original', 'scc', 'n163', 'ym2413', 'sid']);
        const requestedValue = typeof this.engineSelect.value === 'string' ? this.engineSelect.value : '';
        let initialValue = validModes.has(requestedValue)
          ? requestedValue
          : (hasScc ? 'scc' : 'n163');
        if (initialValue === 'scc' && !hasScc) {
          initialValue = 'n163';
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
        this.engineMode = this.sccWaveform ? 'scc' : 'n163';
      }
      this.setTransposeSemitones(this.transposeSemitones, { syncSlider: true, refreshVoices: false });
      this.setFineDetuneCents(this.fineDetuneCents, { syncSlider: true, refreshVoices: false });
      this.setArticulation(this.articulationSetting, { syncSlider: true, refresh: false });
      this.setPlaybackSpeed(this.playbackSpeed, { syncSlider: true });

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

      if (this.engineSelect) {
        this.engineSelect.addEventListener('change', () => {
          this.setEngineMode(this.engineSelect.value);
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
        return 'Tempo normal (100%)';
      }
      if (percent < 100) {
        return `Tempo ralenti (${percent}%)`;
      }
      return `Tempo accéléré (${percent}%)`;
    }

    formatSpeedFactor(ratio) {
      const clamped = this.clampPlaybackSpeed(ratio);
      return `tempo ×${clamped.toFixed(2)}`;
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
      if (this.playing) {
        this.updateReadyStatusMessage();
        this.setStatus(this.buildPlayingStatusMessage(), 'success');
      } else {
        this.updateReadyStatusMessage();
      }
    }

    getEngineLabel() {
      if (this.engineMode === 'scc' && this.sccWaveform) {
        return 'moteur SCC';
      }
      if (this.engineMode === 'n163') {
        return 'moteur Namco 163';
      }
      if (this.engineMode === 'ym2413') {
        return 'moteur Yamaha YM2413';
      }
      if (this.engineMode === 'sid') {
        return 'moteur MOS SID';
      }
      return 'moteur original';
    }

    buildPlayingStatusMessage(extra = '') {
      let message = this.currentTitle
        ? `Lecture en cours : ${this.currentTitle}`
        : 'Lecture en cours';
      const engineLabel = this.getEngineLabel();
      if (engineLabel) {
        message += ` — ${engineLabel}`;
      }
      if (extra) {
        message += ` — ${extra}`;
      }
      return message;
    }

    updateReadyStatusMessage() {
      if (!this.timeline || !this.currentTitle) {
        return;
      }
      const summary = this.formatTimelineSummary(this.timeline, this.timelineAnalysis);
      const baseMessage = summary
        ? `Prêt : ${this.currentTitle} — ${summary}`
        : `Prêt : ${this.currentTitle} (${this.formatDurationWithSpeed(this.timeline.duration)})`;
      const engineLabel = this.getEngineLabel();
      const message = engineLabel ? `${baseMessage} — ${engineLabel}` : baseMessage;
      this.readyStatusMessage = message;
      if (!this.playing) {
        this.setStatus(message, 'success');
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

    formatArticulationLabel(value) {
      const normalized = this.clampArticulation(Number.isFinite(value) ? value : this.articulationSetting);
      let descriptor = 'Équilibré';
      if (normalized <= 20) {
        descriptor = 'Soutenu (orgue)';
      } else if (normalized <= 45) {
        descriptor = 'Doux';
      } else if (normalized <= 75) {
        descriptor = 'Piano';
      } else {
        descriptor = 'Piano pincé';
      }
      return `${descriptor} (${normalized}%)`;
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
        const noteLabel = `${analysis.noteCount} note${analysis.noteCount > 1 ? 's' : ''}`;
        segments.push(noteLabel);

        if (analysis.melodicChannelCount || analysis.hasPercussion) {
          let channelLabel = '';
          if (analysis.melodicChannelCount) {
            const count = analysis.melodicChannelCount;
            const channelWord = count > 1 ? 'canaux' : 'canal';
            channelLabel = `${count} ${channelWord} mélodique${count > 1 ? 's' : ''}`;
          }
          if (analysis.hasPercussion) {
            channelLabel = channelLabel
              ? `${channelLabel} + percussions`
              : 'Percussions';
          }
          if (channelLabel) {
            segments.push(channelLabel);
          }
        }

        if (Number.isFinite(analysis.peakPolyphony) && analysis.peakPolyphony > 0) {
          segments.push(`Polyphonie max ${analysis.peakPolyphony}`);
        }

        if (Number.isFinite(analysis.minNote) && Number.isFinite(analysis.maxNote)) {
          const rangeLabel = this.formatNoteRange(analysis.minNote, analysis.maxNote);
          if (rangeLabel) {
            segments.push(`Plage ${rangeLabel}`);
          }
        }
      }

      return segments.join(' · ');
    }

    formatMidiNoteName(noteNumber) {
      if (!Number.isFinite(noteNumber)) {
        return '';
      }
      const noteNames = ['Do', 'Do♯', 'Ré', 'Ré♯', 'Mi', 'Fa', 'Fa♯', 'Sol', 'Sol♯', 'La', 'La♯', 'Si'];
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
      return `${minLabel} → ${maxLabel}`;
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

    startProgressMonitor(startTime) {
      if (!this.timeline || !this.status) {
        return;
      }
      this.stopProgressMonitor();
      this.playStartTime = Number.isFinite(startTime) ? startTime : null;
      if (!Number.isFinite(this.playStartTime)) {
        return;
      }

      const speed = this.activePlaybackSpeed || this.playbackSpeed || 1;
      const totalDuration = this.getEffectiveDuration(this.timeline, speed);
      const requestFrame = this.requestFrame || ((callback) => window.setTimeout(callback, 100));

      const update = () => {
        if (!this.playing || !this.audioContext) {
          this.progressRaf = null;
          return;
        }
        const now = this.audioContext.currentTime;
        const elapsed = Math.max(0, now - this.playStartTime);
        const clampedElapsed = totalDuration > 0 ? Math.min(totalDuration, elapsed) : elapsed;
        let progressLabel = totalDuration > 0
          ? `${this.formatClock(clampedElapsed)} / ${this.formatClock(totalDuration)}`
          : this.formatClock(clampedElapsed);
        if (Math.abs(speed - 1) >= 0.01) {
          progressLabel = progressLabel
            ? `${progressLabel} · ${this.formatSpeedFactor(speed)}`
            : this.formatSpeedFactor(speed);
        }
        const message = this.buildPlayingStatusMessage(progressLabel);
        if (this.status.textContent !== message) {
          this.status.textContent = message;
        }
        this.status.classList.remove('is-error');
        this.status.classList.add('is-success');
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
    }

    scheduleReadyStatusRestore(delay = 2200) {
      if (!this.readyStatusMessage || typeof window === 'undefined' || typeof window.setTimeout !== 'function') {
        return;
      }
      const timeout = Math.max(0, Number.isFinite(delay) ? delay : 0);
      const timerId = window.setTimeout(() => {
        this.pendingTimers.delete(timerId);
        if (!this.playing && this.readyStatusMessage) {
          this.setStatus(this.readyStatusMessage, 'success');
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
      this.lastStatusMessage = { message, state };
    }

    updateButtons() {
      const hasTimeline = this.timeline && Array.isArray(this.timeline.notes) && this.timeline.notes.length > 0;
      if (this.playButton) {
        const disabled = !hasTimeline || this.playing;
        this.playButton.disabled = disabled;
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
      this.timeline = null;
      this.timelineAnalysis = null;
      this.readyStatusMessage = '';
      try {
        const midi = MidiParser.parse(buffer);
        const timeline = MidiTimeline.fromMidi(midi);
        this.timeline = timeline;
        this.currentTitle = label || 'MIDI inconnu';
        this.timelineAnalysis = this.analyzeTimeline(timeline);
        const summary = this.formatTimelineSummary(timeline, this.timelineAnalysis);
        const message = summary
          ? `Prêt : ${this.currentTitle} — ${summary}`
          : `Prêt : ${this.currentTitle} (${this.formatDurationWithSpeed(timeline.duration)})`;
        this.readyStatusMessage = message;
        this.setStatus(message, 'success');
      } catch (error) {
        this.timeline = null;
        this.timelineAnalysis = null;
        this.readyStatusMessage = '';
        throw error;
      } finally {
        this.updateReadyStatusMessage();
        this.updateButtons();
      }
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

      if (this.engineMode === 'n163') {
        return this.getN163InstrumentSettings(note, { family, baseEnvelope });
      }

      if (this.engineMode === 'sid') {
        return this.getSidInstrumentSettings(note, { family, baseEnvelope });
      }

      if (this.engineMode === 'ym2413') {
        return this.getYm2413InstrumentSettings(note, { family, baseEnvelope });
      }

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
          waveform: options.waveform ?? 'analog',
          layers: Array.isArray(layers) && layers.length ? layers : [
            { type: 'pulse', dutyCycle: 0.35, detune: -7, gain: 0.55 },
            { type: 'pulse', dutyCycle: 0.65, detune: 7, gain: 0.55 },
          ],
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
          layers: Array.isArray(layers) && layers.length ? layers : [
            { type: 'triangle', detune: -6, gain: 0.5 },
            { type: 'triangle', detune: 6, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.5, detune: 0, gain: 0.25 },
          ],
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
          layers: Array.isArray(layers) && layers.length ? layers : [
            { type: 'sine', detune: -12, gain: 0.6 },
            { type: 'sine', detune: 0, gain: 0.55 },
            { type: 'triangle', detune: 0, gain: 0.35 },
          ],
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
          waveform: options.waveform ?? 'analog',
          layers: Array.isArray(layers) && layers.length ? layers : [
            { type: 'pulse', dutyCycle: 0.28, detune: -5, gain: 0.5 },
            { type: 'pulse', dutyCycle: 0.72, detune: 5, gain: 0.5 },
          ],
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
      if (note.channel === 9) {
        this.schedulePercussion(note, baseTime, speed);
        return;
      }

      const now = this.audioContext.currentTime;
      const startOffset = Number.isFinite(note.startTime) ? note.startTime : 0;
      const startAt = Math.max(baseTime + (startOffset / speed), now + 0.001);
      const velocity = Math.max(0.08, Math.min(1, note.velocity || 0.2));

      const instrument = this.getInstrumentSettings(note);
      const frequency = this.midiNoteToFrequency(note.note);
      const lfoSettings = this.getLfoSettings(instrument);

      const hasVibrato = lfoSettings && lfoSettings.vibratoDepth > 0;
      const hasTremolo = lfoSettings && lfoSettings.tremoloDepth > 0;
      const needsLfo = lfoSettings && (hasVibrato || hasTremolo);

      const layers = Array.isArray(instrument.layers) && instrument.layers.length
        ? instrument.layers
        : [{ type: instrument.type || 'sine', dutyCycle: instrument.dutyCycle, detune: 0, gain: 1 }];
      const totalLayerGain = layers.reduce((sum, layer) => sum + Math.max(0, layer.gain ?? 1), 0) || 1;

      const voiceInput = this.audioContext.createGain();
      voiceInput.gain.setValueAtTime(1, startAt);

      let peakGain = Math.min(1, velocity * this.maxGain * (instrument.gain || instrument.volume || 1));
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

        this.activePlaybackSpeed = this.playbackSpeed;
        const playbackSpeed = this.activePlaybackSpeed || 1;
        const effectiveDuration = this.getEffectiveDuration(this.timeline, playbackSpeed);
        const startTime = context.currentTime + 0.05;
        this.playStartTime = startTime;
        this.startScheduler(startTime);
        this.startProgressMonitor(startTime);

        this.finishTimeout = window.setTimeout(() => {
          this.finishTimeout = null;
          this.stop(false);
          this.setStatus(`Lecture terminée : ${this.currentTitle}`, 'success');
          this.scheduleReadyStatusRestore();
        }, Math.ceil(((effectiveDuration || 0) + 0.6) * 1000));

        this.setStatus(this.buildPlayingStatusMessage(), 'success');
      } catch (error) {
        console.error(error);
        this.setStatus(`Lecture impossible : ${error.message}`, 'error');
        this.stopProgressMonitor();
        this.playing = false;
        this.updateButtons();
      }
    }

    stop(manual = false) {
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

      if (manual && wasPlaying) {
        this.setStatus(`Lecture stoppée : ${this.currentTitle}`);
        this.scheduleReadyStatusRestore();
      }
    }

    startScheduler(startTime) {
      if (!this.audioContext || !this.timeline) {
        return;
      }
      this.schedulerState = {
        startTime,
        index: 0,
        speed: this.activePlaybackSpeed || 1,
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
    articulationSlider: document.getElementById('chiptuneArticulationSlider'),
    articulationValue: document.getElementById('chiptuneArticulationValue'),
    speedSlider: document.getElementById('chiptuneSpeedSlider'),
    speedValue: document.getElementById('chiptuneSpeedValue'),
    engineSelect: document.getElementById('chiptuneEngineSelect'),
  };

  if (elements.fileInput && elements.playButton && elements.stopButton && elements.status) {
    new ChiptunePlayer(elements);
  }
})();
