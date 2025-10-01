(function () {
  'use strict';

  const globalScope = typeof window !== 'undefined'
    ? window
    : typeof self !== 'undefined'
      ? self
      : null;

  if (!globalScope) {
    return;
  }

  const existing = globalScope.SccWaveform || {};

  const DEFAULT_RAW_TABLE = [
    18, 21, 24, 26, 28, 29, 30, 31,
    31, 30, 29, 27, 25, 22, 19, 15,
    10, 4, -1, -6, -10, -13, -16, -18,
    -19, -20, -20, -19, -17, -14, -10, -4,
  ];

  const rawTable = Array.isArray(existing.rawTable) && existing.rawTable.length === 32
    ? existing.rawTable.slice()
    : DEFAULT_RAW_TABLE.slice();

  function normaliseWaveTable(raw) {
    const length = Array.isArray(raw) ? raw.length : 0;
    if (!length) {
      return new Array(32).fill(0);
    }
    const mean = raw.reduce((acc, value) => acc + value, 0) / length;
    const centered = raw.map((value) => value - mean);
    const maxMagnitude = centered.reduce((acc, value) => Math.max(acc, Math.abs(value)), 0) || 1;
    return centered.map((value) => value / maxMagnitude);
  }

  const table = Array.isArray(existing.table) && existing.table.length === rawTable.length
    ? existing.table.slice()
    : normaliseWaveTable(rawTable);

  const contextCache = typeof WeakMap === 'function' ? new WeakMap() : new Map();

  function createPeriodicWaveFromSamples(context, samples) {
    if (!context || typeof context.createPeriodicWave !== 'function') {
      return null;
    }
    const sampleCount = samples.length;
    const real = new Float32Array(sampleCount);
    const imag = new Float32Array(sampleCount);

    for (let harmonic = 0; harmonic < sampleCount; harmonic += 1) {
      let sumReal = 0;
      let sumImag = 0;
      for (let n = 0; n < sampleCount; n += 1) {
        const value = samples[n];
        const phase = (2 * Math.PI * harmonic * n) / sampleCount;
        sumReal += value * Math.cos(phase);
        sumImag += value * Math.sin(phase);
      }
      real[harmonic] = sumReal / sampleCount;
      imag[harmonic] = -sumImag / sampleCount;
    }

    return context.createPeriodicWave(real, imag, { disableNormalization: true });
  }

  function createPeriodicWave(context) {
    return createPeriodicWaveFromSamples(context, table);
  }

  function getPeriodicWave(context) {
    if (!context) {
      return null;
    }
    if (contextCache.has(context)) {
      return contextCache.get(context);
    }
    const wave = createPeriodicWave(context);
    if (wave) {
      contextCache.set(context, wave);
    }
    return wave;
  }

  const api = {
    rawTable: Object.freeze(rawTable.slice()),
    table: Object.freeze(table.slice()),
    normalise: normaliseWaveTable,
    createPeriodicWave,
    getPeriodicWave,
  };

  globalScope.SccWaveform = {
    ...existing,
    ...api,
  };
})();
