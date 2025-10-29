(function () {
  'use strict';

  const globalScope = typeof window !== 'undefined'
    ? window
    : typeof self !== 'undefined'
      ? self
      : (typeof globalThis !== 'undefined' ? globalThis : null);

  if (!globalScope) {
    return;
  }

  const existing = globalScope.SccAudioEngine || {};
  const globalConfig = globalScope && typeof globalScope.GAME_CONFIG === 'object'
    ? globalScope.GAME_CONFIG
    : null;
  const SCC_ENGINE_CONFIG = globalConfig?.audio?.engines?.scc || null;

  const SAMPLE_RATE = 44100;
  const TABLE_LENGTH = 32;
  const TWO_PI = Math.PI * 2;
  const DEFAULT_VIBRATO_RATE = 5.5;
  const DEFAULT_VIBRATO_DEPTH_CENTS = 20;
  const DEFAULT_VIBRATO_FADE_MS = 80;
  const DEFAULT_PORTAMENTO_MS = 40;
  const PITCH_BEND_RANGE = 2;

  const DEFAULT_MASTER_GAIN = Number.isFinite(SCC_ENGINE_CONFIG?.masterGain)
    ? clamp(SCC_ENGINE_CONFIG.masterGain, 0, 1)
    : 0.28;
  const DEFAULT_SOFT_CLIPPER_DRIVE = Number.isFinite(SCC_ENGINE_CONFIG?.softClipperDrive)
    ? Math.max(0.1, SCC_ENGINE_CONFIG.softClipperDrive)
    : 1.2;
  const DEFAULT_CHORUS_DELAY_MS = Number.isFinite(SCC_ENGINE_CONFIG?.chorusDelayMs)
    ? Math.max(1, SCC_ENGINE_CONFIG.chorusDelayMs)
    : 12;
  const DEFAULT_CHORUS_MIX = Number.isFinite(SCC_ENGINE_CONFIG?.chorusMix)
    ? clamp(SCC_ENGINE_CONFIG.chorusMix, 0, 1)
    : 0.04;

  // Limite supérieure appliquée aux volumes MIDI afin de conserver une marge
  // de sécurité lors du mixage et d'éviter la saturation perceptible.
  const CHANNEL_LEVEL_LIMIT = 1.2;

  const VOL4_TO_GAIN = [
    0.0, 0.035, 0.055, 0.075,
    0.1, 0.135, 0.17, 0.215,
    0.27, 0.335, 0.41, 0.495,
    0.595, 0.71, 0.84, 1.0,
  ];

  const WAVETABLES = {
    PULSE50: Array.from({ length: TABLE_LENGTH }, (_, i) => (i < TABLE_LENGTH / 2 ? 255 : 0)),
    PULSE25: Array.from({ length: TABLE_LENGTH }, (_, i) => (i < TABLE_LENGTH / 4 ? 255 : 0)),
    TRI_SOFT: (() => {
      const table = new Array(TABLE_LENGTH);
      const half = TABLE_LENGTH / 2;
      for (let i = 0; i < half; i += 1) {
        table[i] = Math.round((i / (half - 1)) * 255);
      }
      for (let i = 0; i < half; i += 1) {
        table[half + i] = Math.round(((half - i - 1) / (half - 1)) * 255);
      }
      return table;
    })(),
    SAW_SOFT: (() => {
      const table = new Array(TABLE_LENGTH);
      for (let i = 0; i < TABLE_LENGTH; i += 1) {
        table[i] = Math.round((i / (TABLE_LENGTH - 1)) * 255);
      }
      return table;
    })(),
    SINE_COARSE: (() => {
      const table = new Array(TABLE_LENGTH);
      for (let i = 0; i < TABLE_LENGTH; i += 1) {
        const value = Math.sin((TWO_PI * i) / TABLE_LENGTH);
        table[i] = Math.round(((value + 1) * 0.5) * 255);
      }
      return table;
    })(),
    NOISE_KIT: [
      210, 34, 178, 15, 240, 66, 199, 12,
      255, 0, 132, 88, 220, 44, 170, 28,
      200, 60, 140, 20, 230, 52, 160, 8,
      190, 72, 120, 36, 250, 96, 180, 48,
    ],
  };

  function convertUnsignedToFloat(table) {
    const result = new Float32Array(table.length);
    for (let i = 0; i < table.length; i += 1) {
      result[i] = ((table[i] || 0) - 128) / 128;
    }
    return result;
  }

  const FLOAT_WAVES = Object.fromEntries(
    Object.entries(WAVETABLES).map(([key, value]) => [key, convertUnsignedToFloat(value)])
  );

  function lerp(a, b, t) {
    return a + ((b - a) * t);
  }

  function centsToRatio(cents) {
    return Math.pow(2, cents / 1200);
  }

  function semitonesToRatio(semitones) {
    return Math.pow(2, semitones / 12);
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function computeChannelGain(volume, expression) {
    const resolvedVolume = clamp(volume, 0, CHANNEL_LEVEL_LIMIT);
    const resolvedExpression = clamp(expression, 0, CHANNEL_LEVEL_LIMIT);
    return resolvedVolume * resolvedExpression;
  }

  function createDefaultInstrument() {
    return {
      wavetable: 'PULSE50',
      envelope: {
        attack: 3,
        decay: 40,
        sustain: 12,
        release: 60,
      },
      vibrato: false,
      portamento: false,
      gain: 1,
    };
  }

  function normaliseEnvelope(definition, sampleRate) {
    if (!definition) {
      return {
        attackSamples: 1,
        decaySamples: 1,
        sustainLevel: 12,
        releaseSamples: 1,
      };
    }
    const attackMs = Number.isFinite(definition.attack) ? Math.max(0, definition.attack) : 0;
    const decayMs = Number.isFinite(definition.decay) ? Math.max(0, definition.decay) : 0;
    const releaseMs = Number.isFinite(definition.release) ? Math.max(0, definition.release) : 0;
    const sustainLevel = clamp(Math.round(Number.isFinite(definition.sustain) ? definition.sustain : 12), 0, 15);
    return {
      attackSamples: Math.max(1, Math.round((attackMs / 1000) * sampleRate)),
      decaySamples: Math.max(1, Math.round((decayMs / 1000) * sampleRate)),
      sustainLevel,
      releaseSamples: Math.max(1, Math.round((releaseMs / 1000) * sampleRate)),
    };
  }

  class SccEnvelope {
    constructor(definition, sampleRate) {
      this.setDefinition(definition, sampleRate);
    }

    setDefinition(definition, sampleRate) {
      this.definition = normaliseEnvelope(definition, sampleRate);
      this.level = 0;
      this.phase = 'idle';
      this.sampleRate = sampleRate;
      this.accumulator = 0;
      this.active = false;
    }

    trigger() {
      this.phase = 'attack';
      this.level = 0;
      this.accumulator = 0;
      this.active = true;
    }

    release() {
      if (this.phase === 'idle' || this.phase === 'finished') {
        return;
      }
      this.phase = 'release';
      this.accumulator = 0;
    }

    step() {
      if (!this.active) {
        return 0;
      }

      if (this.phase === 'attack') {
        const increment = 15 / this.definition.attackSamples;
        this.accumulator += increment;
        if (this.accumulator >= 1) {
          const delta = Math.floor(this.accumulator);
          this.level = clamp(this.level + delta, 0, 15);
          this.accumulator -= delta;
        }
        if (this.level >= 15) {
          this.level = 15;
          this.phase = 'decay';
          this.accumulator = 0;
        }
      } else if (this.phase === 'decay') {
        const target = this.definition.sustainLevel;
        if (this.level <= target) {
          this.level = target;
          this.phase = 'sustain';
          this.accumulator = 0;
        } else {
          const decrement = Math.max(0.00001, (15 - target) / this.definition.decaySamples);
          this.accumulator += decrement;
          if (this.accumulator >= 1) {
            const delta = Math.floor(this.accumulator);
            this.level = clamp(this.level - delta, target, 15);
            this.accumulator -= delta;
          }
          if (this.level <= target) {
            this.level = target;
            this.phase = 'sustain';
            this.accumulator = 0;
          }
        }
      } else if (this.phase === 'sustain') {
        this.level = this.definition.sustainLevel;
      } else if (this.phase === 'release') {
        const decrement = 15 / this.definition.releaseSamples;
        this.accumulator += decrement;
        if (this.accumulator >= 1) {
          const delta = Math.floor(this.accumulator);
          this.level = clamp(this.level - delta, 0, 15);
          this.accumulator -= delta;
        }
        if (this.level <= 0) {
          this.level = 0;
          this.phase = 'finished';
          this.active = false;
        }
      } else {
        this.active = false;
        this.level = 0;
      }

      return this.level;
    }
  }

  function computePanGains(panValue) {
    const clampedPan = clamp(panValue, -1, 1);
    const angle = (clampedPan + 1) * (Math.PI / 4);
    const left = Math.cos(angle);
    const right = Math.sin(angle);
    return { left, right };
  }

  class SccVoice {
    constructor(index, sampleRate, basePan = 0) {
      this.index = index;
      this.sampleRate = sampleRate;
      this.basePan = basePan;
      this.table = FLOAT_WAVES.PULSE50;
      this.envelope = new SccEnvelope(null, sampleRate);
      this.reset();
    }

    reset() {
      this.active = false;
      this.channel = 0;
      this.note = 60;
      this.velocity = 0.5;
      this.channelGain = 1;
      this.additionalGain = 1;
      this.phase = 0;
      this.phaseIncrement = 0;
      this.currentIncrement = 0;
      this.targetIncrement = 0;
      this.portamentoCoeff = 0;
      this.portamentoActive = false;
      this.vibratoEnabled = false;
      this.vibratoPhase = 0;
      this.vibratoIncrement = 0;
      this.vibratoFadeSamples = 1;
      this.vibratoSampleCounter = 0;
      this.vibratoDepth = DEFAULT_VIBRATO_DEPTH_CENTS;
      this.pitchRatio = 1;
      this.baseFrequency = 440;
      this.pitchBendRatio = 1;
      this.pan = this.basePan;
      const gains = computePanGains(this.pan);
      this.leftGain = gains.left;
      this.rightGain = gains.right;
      this.currentGain = 0;
      this.age = 0;
      this.startedAt = 0;
      this.isDrum = false;
      this.drumType = '';
      this.drumState = null;
      this.sustained = false;
      this.released = false;
      this.table = FLOAT_WAVES.PULSE50;
      this.envelope.setDefinition({ attack: 0, decay: 0, sustain: 15, release: 1 }, this.sampleRate);
      this.envelope.active = false;
    }

    configureInstrument(instrument, sampleRate) {
      const envelopeDefinition = instrument && instrument.envelope ? instrument.envelope : null;
      this.additionalGain = Number.isFinite(instrument?.gain) ? instrument.gain : 1;
      this.table = FLOAT_WAVES[instrument?.wavetable] || FLOAT_WAVES.PULSE50;
      this.vibratoEnabled = Boolean(instrument?.vibrato);
      const vibratoRate = Number.isFinite(instrument?.vibratoRate) ? instrument.vibratoRate : DEFAULT_VIBRATO_RATE;
      const vibratoDepth = Number.isFinite(instrument?.vibratoDepth) ? instrument.vibratoDepth : DEFAULT_VIBRATO_DEPTH_CENTS;
      const vibratoFade = Number.isFinite(instrument?.vibratoFadeMs) ? instrument.vibratoFadeMs : DEFAULT_VIBRATO_FADE_MS;
      this.vibratoDepth = vibratoDepth;
      this.vibratoPhase = 0;
      this.vibratoIncrement = (TWO_PI * vibratoRate) / sampleRate;
      this.vibratoFadeSamples = Math.max(1, Math.round((vibratoFade / 1000) * sampleRate));
      this.vibratoSampleCounter = 0;
      this.envelope.setDefinition(envelopeDefinition, sampleRate);
      this.isDrum = Boolean(instrument?.isDrum);
      this.drumType = instrument?.drumType || '';
      this.drumState = null;
    }

    start(params) {
      this.active = true;
      this.channel = params.channel;
      this.note = params.note;
      this.velocity = params.velocity;
      this.channelGain = params.channelGain;
      this.pitchRatio = params.pitchRatio;
      this.pitchBendRatio = params.pitchBendRatio || 1;
      this.baseFrequency = params.frequency;
      this.phase = 0;
      const baseIncrement = (TABLE_LENGTH * this.baseFrequency * this.pitchRatio * this.pitchBendRatio) / this.sampleRate;
      this.targetIncrement = baseIncrement;
      if (params.portamento && Number.isFinite(params.portamentoCoeff) && params.previousIncrement) {
        this.currentIncrement = params.previousIncrement;
        this.portamentoCoeff = clamp(params.portamentoCoeff, 0, 1);
        this.portamentoActive = true;
      } else {
        this.currentIncrement = baseIncrement;
        this.portamentoActive = false;
      }
      this.envelope.trigger();
      this.released = false;
      this.sustained = false;
      this.startedAt = params.samplePosition || 0;
      this.age = 0;
      if (params.pan !== undefined) {
        this.setPan(params.pan);
      }
      if (this.isDrum) {
        this.prepareDrumState(params.frequency, params.samplePosition || 0);
      }
    }

    prepareDrumState(frequency, startSample) {
      const state = {
        startSample,
        baseFrequency: frequency,
        sweepPosition: 0,
        lastNoise: 0,
        tonePhase: 0,
      };
      if (this.drumType === 'kick') {
        state.sweepDurationSamples = Math.max(1, Math.round((50 / 1000) * this.sampleRate));
        state.startFreq = 220;
        state.endFreq = 60;
      } else if (this.drumType === 'snare') {
        state.sweepDurationSamples = Math.max(1, Math.round((20 / 1000) * this.sampleRate));
        state.startFreq = 330;
        state.endFreq = 180;
        state.toneDuration = Math.max(1, Math.round((10 / 1000) * this.sampleRate));
      } else if (this.drumType === 'hihat') {
        state.sweepDurationSamples = Math.max(1, Math.round((5 / 1000) * this.sampleRate));
        state.startFreq = 8000;
        state.endFreq = 4000;
      } else if (this.drumType === 'crash') {
        state.sweepDurationSamples = Math.max(1, Math.round((40 / 1000) * this.sampleRate));
        state.startFreq = 6000;
        state.endFreq = 2800;
      } else if (this.drumType === 'tom') {
        state.sweepDurationSamples = Math.max(1, Math.round((35 / 1000) * this.sampleRate));
        state.startFreq = frequency * 1.3;
        state.endFreq = Math.max(120, frequency * 0.65);
      } else {
        state.sweepDurationSamples = Math.max(1, Math.round((30 / 1000) * this.sampleRate));
        state.startFreq = frequency * 1.2;
        state.endFreq = Math.max(60, frequency * 0.5);
      }
      this.drumState = state;
    }

    setChannelGain(volume) {
      this.channelGain = volume;
    }

    setPitchRatio(ratio) {
      this.pitchRatio = ratio;
      this.targetIncrement = (TABLE_LENGTH * this.baseFrequency * this.pitchRatio * this.pitchBendRatio) / this.sampleRate;
      if (!this.portamentoActive) {
        this.currentIncrement = this.targetIncrement;
      }
    }

    setPitchBendRatio(ratio) {
      this.pitchBendRatio = ratio;
      this.targetIncrement = (TABLE_LENGTH * this.baseFrequency * this.pitchRatio * this.pitchBendRatio) / this.sampleRate;
      if (!this.portamentoActive) {
        this.currentIncrement = this.targetIncrement;
      }
    }

    setPan(pan) {
      this.pan = clamp(pan, -1, 1);
      const gains = computePanGains(this.pan);
      this.leftGain = gains.left;
      this.rightGain = gains.right;
    }

    startRelease() {
      if (this.released) {
        return;
      }
      this.envelope.release();
      this.released = true;
    }

    forceStop() {
      this.envelope.release();
      this.envelope.level = 0;
      this.active = false;
      this.released = true;
    }

    getBaseIncrement() {
      return (TABLE_LENGTH * this.baseFrequency * this.pitchRatio * this.pitchBendRatio) / this.sampleRate;
    }

    computeDrumFrequency() {
      if (!this.drumState) {
        return this.currentIncrement * (this.sampleRate / TABLE_LENGTH);
      }
      const { sweepPosition, sweepDurationSamples, startFreq, endFreq } = this.drumState;
      const t = clamp(sweepPosition / sweepDurationSamples, 0, 1);
      const freq = lerp(startFreq, endFreq, t);
      this.drumState.sweepPosition += 1;
      return freq;
    }

    generateDrumSample() {
      if (!this.drumState) {
        return 0;
      }
      const freq = this.computeDrumFrequency();
      const increment = (TABLE_LENGTH * freq) / this.sampleRate;
      this.phase = (this.phase + increment) % TABLE_LENGTH;
      const index = Math.floor(this.phase);
      const nextIndex = (index + 1) % TABLE_LENGTH;
      const frac = this.phase - index;
      const noiseSample = lerp(this.table[index], this.table[nextIndex], frac);
      let value = noiseSample;
      if (this.drumType === 'snare') {
        const toneActive = this.drumState.toneDuration && this.drumState.toneDuration > 0;
        if (toneActive) {
          const toneFreq = 200;
          const toneInc = (TWO_PI * toneFreq) / this.sampleRate;
          this.drumState.tonePhase = (this.drumState.tonePhase + toneInc) % TWO_PI;
          const tone = Math.sin(this.drumState.tonePhase);
          value = (noiseSample * 0.65) + (tone * 0.35);
          this.drumState.toneDuration -= 1;
        }
      } else if (this.drumType === 'hihat' || this.drumType === 'crash') {
        const highPassed = noiseSample - (this.drumState.lastNoise || 0);
        this.drumState.lastNoise = noiseSample;
        value = this.drumType === 'crash'
          ? (highPassed * 0.7) + (noiseSample * 0.3)
          : highPassed;
      }
      return value;
    }

    advance() {
      if (!this.active) {
        return { left: 0, right: 0 };
      }
      this.age += 1;
      if (this.portamentoActive) {
        const delta = this.targetIncrement - this.currentIncrement;
        this.currentIncrement += delta * this.portamentoCoeff;
        if (Math.abs(delta) < 1e-6) {
          this.currentIncrement = this.targetIncrement;
          this.portamentoActive = false;
        }
      } else {
        this.currentIncrement = this.targetIncrement;
      }

      let sampleValue = 0;
      if (this.isDrum) {
        sampleValue = this.generateDrumSample();
      } else {
        let effectiveIncrement = this.currentIncrement;
        if (this.vibratoEnabled) {
          const depthRatio = centsToRatio(this.vibratoDepth);
          const minRatio = 2 - depthRatio;
          this.vibratoPhase = (this.vibratoPhase + this.vibratoIncrement) % TWO_PI;
          const fadeFactor = clamp(this.vibratoSampleCounter / this.vibratoFadeSamples, 0, 1);
          this.vibratoSampleCounter = Math.min(this.vibratoSampleCounter + 1, this.vibratoFadeSamples);
          const vibrato = Math.sin(this.vibratoPhase) * fadeFactor;
          const ratio = lerp(1 / minRatio, depthRatio, (vibrato + 1) * 0.5);
          effectiveIncrement *= ratio;
        }
        this.phase = (this.phase + effectiveIncrement) % TABLE_LENGTH;
        const index = Math.floor(this.phase);
        const nextIndex = (index + 1) % TABLE_LENGTH;
        const frac = this.phase - index;
        const currentSample = this.table[index];
        const nextSample = this.table[nextIndex];
        sampleValue = lerp(currentSample, nextSample, frac);
      }

      const envelopeLevel = this.envelope.step();
      if (!this.envelope.active && this.envelope.phase === 'finished') {
        this.active = false;
        return { left: 0, right: 0 };
      }
      const volume = VOL4_TO_GAIN[envelopeLevel] * this.velocity * this.channelGain * this.additionalGain;
      this.currentGain = volume;
      const output = sampleValue * volume;
      return {
        left: output * this.leftGain,
        right: output * this.rightGain,
      };
    }
  }

  function createChannelState() {
    return {
      program: 0,
      volume: 1,
      expression: 1,
      pan: 0,
      pitchBend: 0,
      sustain: false,
      activeVoices: new Set(),
      sustainedVoices: new Set(),
      lastVoiceIncrement: 0,
    };
  }

  function computePortamentoCoefficient(durationMs, sampleRate) {
    if (!durationMs || durationMs <= 0) {
      return 1;
    }
    const samples = (durationMs / 1000) * sampleRate;
    if (samples <= 1) {
      return 1;
    }
    return 1 - Math.exp(-1 / samples);
  }

  function buildLowPassCoefficients(sampleRate, frequency, Q) {
    const w0 = (TWO_PI * frequency) / sampleRate;
    const alpha = Math.sin(w0) / (2 * Q);
    const cosw0 = Math.cos(w0);
    const b0 = (1 - cosw0) / 2;
    const b1 = 1 - cosw0;
    const b2 = (1 - cosw0) / 2;
    const a0 = 1 + alpha;
    const a1 = -2 * cosw0;
    const a2 = 1 - alpha;
    return {
      b0: b0 / a0,
      b1: b1 / a0,
      b2: b2 / a0,
      a1: a1 / a0,
      a2: a2 / a0,
    };
  }

  function buildHighShelfCoefficients(sampleRate, frequency, gainDb) {
    const A = Math.pow(10, gainDb / 40);
    const w0 = (TWO_PI * frequency) / sampleRate;
    const cosw0 = Math.cos(w0);
    const sinw0 = Math.sin(w0);
    const alpha = sinw0 / 2 * Math.sqrt((A + 1 / A) * (1 / Math.sqrt(2) - 1) + 2);
    const beta = 2 * Math.sqrt(A) * alpha;

    const b0 = A * ((A + 1) + (A - 1) * cosw0 + beta);
    const b1 = -2 * A * ((A - 1) + (A + 1) * cosw0);
    const b2 = A * ((A + 1) + (A - 1) * cosw0 - beta);
    const a0 = (A + 1) - (A - 1) * cosw0 + beta;
    const a1 = 2 * ((A - 1) - (A + 1) * cosw0);
    const a2 = (A + 1) - (A - 1) * cosw0 - beta;

    return {
      b0: b0 / a0,
      b1: b1 / a0,
      b2: b2 / a0,
      a1: a1 / a0,
      a2: a2 / a0,
    };
  }

  function applyBiquadStereo(left, right, coeffs) {
    let x1L = 0; let x2L = 0; let y1L = 0; let y2L = 0;
    let x1R = 0; let x2R = 0; let y1R = 0; let y2R = 0;
    for (let i = 0; i < left.length; i += 1) {
      const x0L = left[i];
      const y0L = coeffs.b0 * x0L + coeffs.b1 * x1L + coeffs.b2 * x2L - coeffs.a1 * y1L - coeffs.a2 * y2L;
      x2L = x1L;
      x1L = x0L;
      y2L = y1L;
      y1L = y0L;
      left[i] = y0L;

      const x0R = right[i];
      const y0R = coeffs.b0 * x0R + coeffs.b1 * x1R + coeffs.b2 * x2R - coeffs.a1 * y1R - coeffs.a2 * y2R;
      x2R = x1R;
      x1R = x0R;
      y2R = y1R;
      y1R = y0R;
      right[i] = y0R;
    }
  }

  function applyChorus(left, right, sampleRate, settings = {}) {
    const delayMs = Number.isFinite(settings.delayMs)
      ? Math.max(1, settings.delayMs)
      : DEFAULT_CHORUS_DELAY_MS;
    const mix = Number.isFinite(settings.mix)
      ? clamp(settings.mix, 0, 1)
      : DEFAULT_CHORUS_MIX;
    if (mix <= 0) {
      return;
    }
    const delaySamples = Math.max(1, Math.round((delayMs / 1000) * sampleRate));
    for (let i = delaySamples; i < left.length; i += 1) {
      const delayedL = left[i - delaySamples];
      const delayedR = right[i - delaySamples];
      const dryL = left[i];
      const dryR = right[i];
      left[i] = (dryL * (1 - mix)) + (delayedR * mix);
      right[i] = (dryR * (1 - mix)) + (delayedL * mix);
    }
  }

  function applySoftClip(left, right, drive = DEFAULT_SOFT_CLIPPER_DRIVE) {
    const k = Math.max(0.1, drive);
    const norm = Math.tanh(k) || 1;
    for (let i = 0; i < left.length; i += 1) {
      left[i] = Math.tanh(k * left[i]) / norm;
      right[i] = Math.tanh(k * right[i]) / norm;
    }
  }

  function createDitheredInt16Buffer(left, right) {
    const length = left.length;
    const buffer = new Int16Array(length * 2);
    for (let i = 0; i < length; i += 1) {
      const l = clamp(left[i], -1, 1);
      const r = clamp(right[i], -1, 1);
      const dither = (Math.random() - Math.random()) * (1 / 32768);
      const ditherR = (Math.random() - Math.random()) * (1 / 32768);
      buffer[i * 2] = Math.round((l + dither) * 32767);
      buffer[i * 2 + 1] = Math.round((r + ditherR) * 32767);
    }
    return buffer;
  }

  function writeWavHeader(view, length, sampleRate) {
    const byteLength = length * 4 + 36;
    let offset = 0;
    function writeString(str) {
      for (let i = 0; i < str.length; i += 1) {
        view.setUint8(offset + i, str.charCodeAt(i));
      }
      offset += str.length;
    }
    writeString('RIFF');
    view.setUint32(offset, byteLength, true);
    offset += 4;
    writeString('WAVE');
    writeString('fmt ');
    view.setUint32(offset, 16, true);
    offset += 4;
    view.setUint16(offset, 1, true);
    offset += 2;
    view.setUint16(offset, 2, true);
    offset += 2;
    view.setUint32(offset, sampleRate, true);
    offset += 4;
    view.setUint32(offset, sampleRate * 4, true);
    offset += 4;
    view.setUint16(offset, 4, true);
    offset += 2;
    view.setUint16(offset, 16, true);
    offset += 2;
    writeString('data');
    view.setUint32(offset, length * 4, true);
  }

  class SccEngine {
    constructor(options = {}) {
      this.sampleRate = options.sampleRate || SAMPLE_RATE;
      this.voiceCount = 5;
      const basePans = Array.isArray(options.panSpread)
        ? options.panSpread
        : [-0.25, -0.1, 0.1, 0.25, 0];
      this.voices = new Array(this.voiceCount)
        .fill(null)
        .map((_, index) => new SccVoice(index, this.sampleRate, basePans[index] ?? 0));
      this.pitchBendRange = Number.isFinite(options.pitchBendRange) ? options.pitchBendRange : PITCH_BEND_RANGE;
      this.defaults = createDefaultInstrument();
      this.instrumentMap = { programs: {}, envelopes: {}, drums: {}, defaults: this.defaults };
      this.cachedEnvelopes = new Map();
      this.portamentoDurationMs = Number.isFinite(options.portamentoMs) ? options.portamentoMs : DEFAULT_PORTAMENTO_MS;
      const resolvedMasterGain = Number.isFinite(options.masterGain)
        ? options.masterGain
        : DEFAULT_MASTER_GAIN;
      this.masterGain = clamp(resolvedMasterGain, 0, 1);
      const optionDrive = Number.isFinite(options.softClipDrive)
        ? options.softClipDrive
        : (Number.isFinite(options.softClipperDrive) ? options.softClipperDrive : undefined);
      const resolvedDrive = Number.isFinite(optionDrive) ? optionDrive : DEFAULT_SOFT_CLIPPER_DRIVE;
      this.softClipDrive = Math.max(0.1, resolvedDrive);
      const resolvedChorusDelay = Number.isFinite(options.chorusDelayMs)
        ? options.chorusDelayMs
        : DEFAULT_CHORUS_DELAY_MS;
      const resolvedChorusMix = Number.isFinite(options.chorusMix)
        ? options.chorusMix
        : DEFAULT_CHORUS_MIX;
      this.chorusSettings = {
        delayMs: Math.max(1, resolvedChorusDelay),
        mix: clamp(resolvedChorusMix, 0, 1),
      };
      this.loaded = false;
    }

    async loadInstrumentMap(url) {
      if (!url) {
        throw new Error('Missing instrument map URL');
      }
      const response = await fetch(url, { cache: 'no-cache' });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const json = await response.json();
      this.setInstrumentMap(json);
      return json;
    }

    setInstrumentMap(map) {
      if (!map || typeof map !== 'object') {
        throw new Error('Invalid instrument map');
      }
      this.instrumentMap = {
        programs: map.programs || {},
        envelopes: map.envelopes || {},
        drums: map.drums || {},
        defaults: {
          ...this.defaults,
          ...(map.defaults || {}),
        },
      };
      this.cachedEnvelopes.clear();
      this.loaded = true;
    }

    getEnvelope(name) {
      const key = String(name || '');
      if (this.cachedEnvelopes.has(key)) {
        return this.cachedEnvelopes.get(key);
      }
      const def = this.instrumentMap.envelopes[key];
      if (!def) {
        return null;
      }
      const normalised = {
        attack: def.attack,
        decay: def.decay,
        sustain: def.sustain,
        release: def.release,
      };
      this.cachedEnvelopes.set(key, normalised);
      return normalised;
    }

    resolveProgram(programNumber) {
      const entry = this.instrumentMap.programs[String(programNumber)];
      if (!entry) {
        const defaultEnvelopeConfig = this.instrumentMap.defaults.envelope;
        const fallbackEnvelope = defaultEnvelopeConfig
          ? (typeof defaultEnvelopeConfig === 'string'
            ? this.getEnvelope(defaultEnvelopeConfig)
            : defaultEnvelopeConfig)
          : this.defaults.envelope;
        return {
          wavetable: this.instrumentMap.defaults.wavetable || 'PULSE50',
          envelope: fallbackEnvelope,
          vibrato: this.instrumentMap.defaults.vibrato ?? false,
          portamento: this.instrumentMap.defaults.portamento ?? false,
          gain: this.instrumentMap.defaults.gain ?? 1,
          vibratoRate: this.instrumentMap.defaults.vibratoRate,
          vibratoDepth: this.instrumentMap.defaults.vibratoDepth,
          vibratoFadeMs: this.instrumentMap.defaults.vibratoFadeMs,
        };
      }
      const envelopeName = entry.envelope;
      const resolvedEnvelope = envelopeName ? this.getEnvelope(envelopeName) : null;
      const defaultEnvelopeConfig = this.instrumentMap.defaults.envelope;
      const fallbackEnvelope = defaultEnvelopeConfig
        ? (typeof defaultEnvelopeConfig === 'string'
          ? this.getEnvelope(defaultEnvelopeConfig)
          : defaultEnvelopeConfig)
        : this.defaults.envelope;
      return {
        wavetable: entry.wavetable || this.instrumentMap.defaults.wavetable || 'PULSE50',
        envelope: resolvedEnvelope || fallbackEnvelope,
        vibrato: entry.vibrato ?? this.instrumentMap.defaults.vibrato,
        portamento: entry.portamento ?? false,
        gain: entry.gain ?? 1,
        vibratoRate: entry.vibratoRate,
        vibratoDepth: entry.vibratoDepth,
        vibratoFadeMs: entry.vibratoFadeMs,
      };
    }

    resolveDrum(noteNumber) {
      const entry = this.instrumentMap.drums[String(noteNumber)]
        || this.instrumentMap.drums.default
        || this.instrumentMap.drums[String(35)];
      if (!entry) {
        return {
          wavetable: 'NOISE_KIT',
          envelope: this.getEnvelope('DRUM_SHORT'),
          vibrato: false,
          isDrum: true,
          drumType: 'kick',
          gain: 1,
        };
      }
      const envelopeName = entry.envelope || 'DRUM_SHORT';
      const resolvedEnvelope = this.getEnvelope(envelopeName) || this.getEnvelope('DRUM_SHORT');
      return {
        wavetable: entry.wavetable || 'NOISE_KIT',
        envelope: resolvedEnvelope,
        vibrato: false,
        isDrum: true,
        drumType: entry.drumType || 'kick',
        gain: entry.gain ?? 1,
      };
    }

    buildEventTimeline(midi, options = {}) {
      if (!midi || !Array.isArray(midi.events)) {
        throw new Error('Invalid MIDI data');
      }
      if (!Number.isFinite(midi.division) || midi.division <= 0) {
        throw new Error('Invalid MIDI division');
      }
      const events = [];
      const tempoEvents = [];
      let secondsPerTick = (500000 / 1e6) / midi.division;
      let currentTime = 0;
      let lastTick = 0;
      const speed = Number.isFinite(options.speed) ? options.speed : 1;
      const timeScale = speed > 0 ? 1 / speed : 1;
      for (const event of midi.events) {
        const deltaTicks = event.ticks - lastTick;
        if (deltaTicks > 0) {
          currentTime += deltaTicks * secondsPerTick * timeScale;
          lastTick = event.ticks;
        }
        if (event.type === 'tempo') {
          secondsPerTick = (event.microsecondsPerBeat / 1e6) / midi.division;
          tempoEvents.push({ time: currentTime, microsecondsPerBeat: event.microsecondsPerBeat });
          continue;
        }
        events.push({ ...event, time: currentTime });
      }
      return { events, duration: currentTime, tempoEvents };
    }

    resetVoices() {
      for (const voice of this.voices) {
        voice.reset();
      }
    }

    allocateVoice(samplePosition, requestedVelocity) {
      for (const voice of this.voices) {
        if (!voice.active) {
          return voice;
        }
      }
      let candidate = this.voices[0];
      let bestScore = Number.POSITIVE_INFINITY;
      for (const voice of this.voices) {
        const gain = Number.isFinite(voice.currentGain) ? voice.currentGain : 0;
        const age = samplePosition - (voice.startedAt || 0);
        const score = (gain * 1.2) + (age * 0.0002);
        if (score < bestScore) {
          bestScore = score;
          candidate = voice;
        }
      }
      candidate.forceStop();
      return candidate;
    }

    updateChannelPitch(channel, channelState, bendValue) {
      channelState.pitchBend = bendValue;
      const ratio = semitonesToRatio((bendValue / 8192) * this.pitchBendRange);
      for (const voice of channelState.activeVoices) {
        voice.setPitchBendRatio(ratio);
      }
    }

    updateChannelVolume(channelState) {
      const volume = computeChannelGain(channelState.volume, channelState.expression);
      for (const voice of channelState.activeVoices) {
        voice.setChannelGain(volume);
      }
    }

    updateChannelPan(channelState, additionalOffset = 0) {
      for (const voice of channelState.activeVoices) {
        const basePan = voice.basePan;
        const newPan = clamp(basePan + channelState.pan + additionalOffset, -1, 1);
        voice.setPan(newPan);
      }
    }

    releaseVoice(channelState, voice) {
      if (!voice) {
        return;
      }
      if (channelState.sustain) {
        voice.sustained = true;
        channelState.sustainedVoices.add(voice);
        return;
      }
      voice.startRelease();
      channelState.activeVoices.delete(voice);
    }

    forceReleaseVoice(channelState, voice) {
      if (!voice) {
        return;
      }
      voice.startRelease();
      channelState.activeVoices.delete(voice);
      channelState.sustainedVoices.delete(voice);
      voice.sustained = false;
    }

    flushSustain(channelState) {
      for (const voice of channelState.sustainedVoices) {
        voice.startRelease();
        channelState.activeVoices.delete(voice);
      }
      channelState.sustainedVoices.clear();
    }

    findLegatoVoice(channelState, channel, excludeNote) {
      let candidate = null;
      let latestStart = -Infinity;
      const visited = new Set();
      const consider = (voice) => {
        if (!voice || visited.has(voice)) {
          return;
        }
        visited.add(voice);
        if (!voice.active || voice.isDrum || voice.released) {
          return;
        }
        if (voice.channel !== channel) {
          return;
        }
        if (excludeNote !== undefined && voice.note === excludeNote) {
          return;
        }
        if (voice.startedAt >= latestStart) {
          candidate = voice;
          latestStart = voice.startedAt;
        }
      };
      for (const voice of channelState.activeVoices) {
        consider(voice);
      }
      for (const voice of channelState.sustainedVoices) {
        consider(voice);
      }
      return candidate;
    }

    render(midi, options = {}) {
      if (!this.loaded) {
        throw new Error('SCC instrument map not loaded');
      }
      const transpose = Number.isFinite(options.transpose) ? options.transpose : 0;
      const fineDetune = Number.isFinite(options.fineDetune) ? options.fineDetune : 0;
      const speed = Number.isFinite(options.speed) ? options.speed : 1;
      const timeline = this.buildEventTimeline(midi, { speed });
      const tailSeconds = Math.max(0.5, (this.instrumentMap?.envelopes?.PAD?.release || 300) / 1000 + 0.05);
      const totalDuration = timeline.duration + tailSeconds;
      const totalSamples = Math.max(1, Math.ceil(totalDuration * this.sampleRate));
      const left = new Float32Array(totalSamples);
      const right = new Float32Array(totalSamples);

      const channelStates = new Array(16).fill(null).map(() => createChannelState());
      const voiceByNote = new Map();

      this.resetVoices();

      const portamentoCoeff = computePortamentoCoefficient(this.portamentoDurationMs, this.sampleRate);

      let eventIndex = 0;
      const events = timeline.events;
      const pitchBendRatios = new Array(16).fill(1);

      for (let sample = 0; sample < totalSamples; sample += 1) {
        while (eventIndex < events.length) {
          const event = events[eventIndex];
          const eventSample = Math.max(0, Math.floor(event.time * this.sampleRate));
          if (eventSample > sample) {
            break;
          }
          const channel = clamp(event.channel ?? 0, 0, 15);
          const channelState = channelStates[channel];
          if (event.type === 'program') {
            channelState.program = clamp(event.program ?? 0, 0, 127);
          } else if (event.type === 'control') {
            if (event.controller === 7) {
              channelState.volume = clamp(event.value / 127, 0, CHANNEL_LEVEL_LIMIT);
              this.updateChannelVolume(channelState);
            } else if (event.controller === 11) {
              channelState.expression = clamp(event.value / 127, 0, CHANNEL_LEVEL_LIMIT);
              this.updateChannelVolume(channelState);
            } else if (event.controller === 10) {
              const normalized = clamp((event.value / 127) * 2 - 1, -1, 1);
              channelState.pan = normalized * 0.6;
              this.updateChannelPan(channelState);
            } else if (event.controller === 64) {
              const sustainOn = event.value >= 64;
              channelState.sustain = sustainOn;
              if (!sustainOn) {
                this.flushSustain(channelState);
              }
            }
          } else if (event.type === 'pitchBend') {
            const ratio = semitonesToRatio((event.value / 8192) * this.pitchBendRange);
            pitchBendRatios[channel] = ratio;
            for (const voice of channelState.activeVoices) {
              voice.setPitchBendRatio(ratio);
            }
            for (const voice of channelState.sustainedVoices) {
              voice.setPitchBendRatio(ratio);
            }
          } else if (event.type === 'noteOn') {
            const key = `${channel}:${event.note}`;
            const existingVoice = voiceByNote.get(key);
            if (existingVoice) {
              this.releaseVoice(channelState, existingVoice);
              voiceByNote.delete(key);
            }
            const velocityBase = Math.max(0, Math.min(1, (event.velocity || 0) / 127));
            const velocity = Math.pow(velocityBase || 0.001, 0.8);
            const volume = computeChannelGain(channelState.volume, channelState.expression);
            const program = channelState.program || 0;
            const midiNote = clamp(event.note + transpose, 0, 127);
            const frequency = 440 * Math.pow(2, ((midiNote - 69) / 12)) * centsToRatio(fineDetune);
            const channelPan = channelState.pan;
            const portamentoEnabled = this.instrumentMap.programs[String(program)]?.portamento ?? false;
            const isDrumChannel = channel === 9;
            const instrument = isDrumChannel
              ? this.resolveDrum(event.note)
              : this.resolveProgram(program);
            let legatoVoice = null;
            let previousIncrement = null;
            if (portamentoEnabled && !isDrumChannel) {
              legatoVoice = this.findLegatoVoice(channelState, channel, event.note);
              if (legatoVoice) {
                previousIncrement = Number.isFinite(legatoVoice.currentIncrement) && legatoVoice.currentIncrement > 0
                  ? legatoVoice.currentIncrement
                  : Number.isFinite(legatoVoice.targetIncrement) && legatoVoice.targetIncrement > 0
                    ? legatoVoice.targetIncrement
                    : legatoVoice.getBaseIncrement();
              }
            }
            const voice = this.allocateVoice(sample, velocity);
            if (Number.isFinite(voice.channel) && channelStates[voice.channel]) {
              channelStates[voice.channel].activeVoices.delete(voice);
              channelStates[voice.channel].sustainedVoices.delete(voice);
            }
            for (const [existingKey, existingVoice] of voiceByNote.entries()) {
              if (existingVoice === voice) {
                voiceByNote.delete(existingKey);
                break;
              }
            }
            voice.configureInstrument({ ...instrument, isDrum: isDrumChannel || instrument.isDrum, drumType: instrument.drumType }, this.sampleRate);
            const pitchRatio = instrument.isDrum ? 1 : semitonesToRatio(0);
            const panOffset = channelPan + (instrument.panOffset || 0);
            voice.start({
              channel,
              note: event.note,
              velocity,
              channelGain: volume,
              pitchRatio,
              pitchBendRatio: pitchBendRatios[channel] || 1,
              frequency,
              portamento: Boolean(previousIncrement),
              previousIncrement,
              portamentoCoeff,
              pan: clamp(voice.basePan + panOffset, -1, 1),
              samplePosition: sample,
            });
            if (legatoVoice) {
              const legatoKey = `${channel}:${legatoVoice.note}`;
              if (voiceByNote.get(legatoKey) === legatoVoice) {
                voiceByNote.delete(legatoKey);
              }
              this.forceReleaseVoice(channelState, legatoVoice);
            }
            channelState.lastVoiceIncrement = voice.getBaseIncrement();
            channelState.activeVoices.add(voice);
            voiceByNote.set(key, voice);
          } else if (event.type === 'noteOff') {
            const key = `${channel}:${event.note}`;
            const voice = voiceByNote.get(key);
            if (voice) {
              this.releaseVoice(channelState, voice);
              voiceByNote.delete(key);
            }
          }
          eventIndex += 1;
        }

        let mixL = 0;
        let mixR = 0;
        for (const voice of this.voices) {
          const result = voice.advance();
          mixL += result.left;
          mixR += result.right;
        }
        left[sample] = mixL * this.masterGain;
        right[sample] = mixR * this.masterGain;
      }

      applyChorus(left, right, this.sampleRate, this.chorusSettings);
      applyBiquadStereo(left, right, buildLowPassCoefficients(this.sampleRate, 12000, 0.7));
      applyBiquadStereo(left, right, buildHighShelfCoefficients(this.sampleRate, 6000, 2));
      applySoftClip(left, right, this.softClipDrive);

      return {
        left,
        right,
        sampleRate: this.sampleRate,
        duration: totalSamples / this.sampleRate,
      };
    }

    createAudioBuffer(context, renderResult) {
      const audioContext = context;
      const buffer = audioContext.createBuffer(2, renderResult.left.length, renderResult.sampleRate);
      buffer.getChannelData(0).set(renderResult.left);
      buffer.getChannelData(1).set(renderResult.right);
      return buffer;
    }

    exportWav(renderResult) {
      const interleaved = createDitheredInt16Buffer(renderResult.left, renderResult.right);
      const buffer = new ArrayBuffer(44 + interleaved.length * 2);
      const view = new DataView(buffer);
      writeWavHeader(view, renderResult.left.length, renderResult.sampleRate);
      const dataView = new DataView(buffer, 44);
      for (let i = 0; i < interleaved.length; i += 1) {
        dataView.setInt16(i * 2, interleaved[i], true);
      }
      return buffer;
    }
  }

  existing.SccEngine = SccEngine;
  globalScope.SccAudioEngine = existing;
})();
