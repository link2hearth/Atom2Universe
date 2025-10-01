(function () {
  'use strict';

  const DEFAULT_MASTER_VOLUME = 60;
  const DEFAULT_VOICE_SETTINGS = [
    { frequency: 220, volume: 70 },
    { frequency: 330, volume: 65 },
    { frequency: 440, volume: 60 },
    { frequency: 550, volume: 55 },
    { frequency: 660, volume: 50 },
  ];

  const SHARED_WAVEFORM = typeof window !== 'undefined' ? window.SccWaveform : null;

  const RAW_WAVETABLE = SHARED_WAVEFORM?.rawTable
    ? Array.from(SHARED_WAVEFORM.rawTable)
    : [
      18, 21, 24, 26, 28, 29, 30, 31,
      31, 30, 29, 27, 25, 22, 19, 15,
      10, 4, -1, -6, -10, -13, -16, -18,
      -19, -20, -20, -19, -17, -14, -10, -4,
    ];

  function normaliseWaveTable(rawTable) {
    const length = Array.isArray(rawTable) ? rawTable.length : 0;
    if (!length) {
      return new Array(32).fill(0);
    }
    const mean = rawTable.reduce((acc, value) => acc + value, 0) / length;
    const centered = rawTable.map((value) => value - mean);
    const maxMagnitude = centered.reduce((acc, value) => Math.max(acc, Math.abs(value)), 0) || 1;
    return centered.map((value) => value / maxMagnitude);
  }

  const NORMALISED_WAVETABLE = SHARED_WAVEFORM?.table
    ? Array.from(SHARED_WAVEFORM.table)
    : normaliseWaveTable(RAW_WAVETABLE);

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

  function getPeriodicWaveForContext(context) {
    if (!context) {
      return null;
    }
    if (SHARED_WAVEFORM) {
      if (typeof SHARED_WAVEFORM.getPeriodicWave === 'function') {
        const sharedWave = SHARED_WAVEFORM.getPeriodicWave(context);
        if (sharedWave) {
          return sharedWave;
        }
      }
      if (typeof SHARED_WAVEFORM.createPeriodicWave === 'function') {
        const createdWave = SHARED_WAVEFORM.createPeriodicWave(context);
        if (createdWave) {
          return createdWave;
        }
      }
    }
    return createPeriodicWaveFromSamples(context, NORMALISED_WAVETABLE);
  }

  class SccSynth {
    constructor(elements) {
      this.elements = elements;
      this.audioContext = null;
      this.masterGain = null;
      this.periodicWave = null;
      this.voices = [];
      this.voiceSettings = DEFAULT_VOICE_SETTINGS.map((voice) => ({ ...voice }));
      this.masterVolume = DEFAULT_MASTER_VOLUME;
      this.running = false;
      this.supported = typeof window !== 'undefined' && !!(window.AudioContext || window.webkitAudioContext);

      this.bindUI();
      this.updateAllDisplays();

      if (!this.supported) {
        this.disableControls();
        this.setStatus("Synthèse Web Audio indisponible dans ce navigateur.", { isError: true });
      }
    }

    bindUI() {
      const { startButton, stopButton, masterSlider } = this.elements;

      if (startButton) {
        startButton.addEventListener('click', () => {
          this.start().catch((error) => {
            console.error('Impossible de démarrer le synthé SCC', error);
            this.setStatus('Erreur lors du démarrage du synthé.', { isError: true });
          });
        });
      }

      if (stopButton) {
        stopButton.addEventListener('click', () => {
          this.stop();
        });
      }

      if (masterSlider) {
        masterSlider.addEventListener('input', (event) => {
          const value = Number(event.target.value);
          this.masterVolume = this.clampPercent(value);
          this.updateMasterVolumeDisplay();
          this.applyMasterVolume();
        });
      }

      this.elements.voiceControls.forEach((voiceElement, index) => {
        const voiceIndex = index;
        if (voiceElement.frequencySlider) {
          voiceElement.frequencySlider.addEventListener('input', (event) => {
            const value = Number(event.target.value);
            this.setVoiceFrequency(voiceIndex, value);
          });
        }
        if (voiceElement.volumeSlider) {
          voiceElement.volumeSlider.addEventListener('input', (event) => {
            const value = Number(event.target.value);
            this.setVoiceVolume(voiceIndex, value);
          });
        }
      });
    }

    disableControls() {
      const { startButton, stopButton, masterSlider } = this.elements;
      if (startButton) {
        startButton.disabled = true;
      }
      if (stopButton) {
        stopButton.disabled = true;
      }
      if (masterSlider) {
        masterSlider.disabled = true;
      }
      this.elements.voiceControls.forEach((control) => {
        if (control.frequencySlider) {
          control.frequencySlider.disabled = true;
        }
        if (control.volumeSlider) {
          control.volumeSlider.disabled = true;
        }
      });
    }

    clampPercent(value) {
      if (!Number.isFinite(value)) {
        return 0;
      }
      return Math.min(Math.max(value, 0), 100);
    }

    async ensureContext() {
      if (!this.supported) {
        throw new Error('Web Audio API non prise en charge.');
      }
      if (this.audioContext) {
        if (this.audioContext.state === 'suspended') {
          await this.audioContext.resume();
        }
        return;
      }

      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      this.audioContext = new AudioContextClass();
      this.masterGain = this.audioContext.createGain();
      this.masterGain.gain.value = this.percentToGain(this.masterVolume);
      this.masterGain.connect(this.audioContext.destination);
      this.periodicWave = getPeriodicWaveForContext(this.audioContext);
    }

    percentToGain(value) {
      const percent = this.clampPercent(value);
      const normalised = percent / 100;
      return Math.pow(normalised, 1.2);
    }

    setVoiceFrequency(index, frequency) {
      const clampedFrequency = Number.isFinite(frequency) ? Math.min(Math.max(frequency, 40), 4000) : 0;
      this.voiceSettings[index].frequency = clampedFrequency;
      this.updateFrequencyDisplay(index);
      if (this.running && this.voices[index]) {
        const now = this.audioContext.currentTime;
        this.voices[index].oscillator.frequency.setTargetAtTime(clampedFrequency, now, 0.015);
      }
    }

    setVoiceVolume(index, volume) {
      const clampedVolume = this.clampPercent(volume);
      this.voiceSettings[index].volume = clampedVolume;
      this.updateVolumeDisplay(index);
      if (this.running && this.voices[index]) {
        const now = this.audioContext.currentTime;
        const gainValue = this.percentToGain(clampedVolume);
        this.voices[index].gain.gain.setTargetAtTime(gainValue, now, 0.02);
      }
    }

    applyMasterVolume() {
      if (!this.masterGain) {
        return;
      }
      const now = this.audioContext.currentTime;
      const gainValue = this.percentToGain(this.masterVolume);
      this.masterGain.gain.setTargetAtTime(gainValue, now, 0.02);
    }

    updateFrequencyDisplay(index) {
      const voiceControl = this.elements.voiceControls[index];
      const display = voiceControl ? voiceControl.frequencyValue : null;
      if (display) {
        display.textContent = `${Math.round(this.voiceSettings[index].frequency)} Hz`;
      }
    }

    updateVolumeDisplay(index) {
      const voiceControl = this.elements.voiceControls[index];
      const display = voiceControl ? voiceControl.volumeValue : null;
      if (display) {
        display.textContent = `${Math.round(this.voiceSettings[index].volume)}%`;
      }
    }

    updateMasterVolumeDisplay() {
      if (this.elements.masterValue) {
        this.elements.masterValue.textContent = `${Math.round(this.masterVolume)}%`;
      }
    }

    updateAllDisplays() {
      this.voiceSettings.forEach((_, index) => {
        if (this.elements.voiceControls[index].frequencySlider) {
          this.elements.voiceControls[index].frequencySlider.value = String(this.voiceSettings[index].frequency);
        }
        if (this.elements.voiceControls[index].volumeSlider) {
          this.elements.voiceControls[index].volumeSlider.value = String(this.voiceSettings[index].volume);
        }
        this.updateFrequencyDisplay(index);
        this.updateVolumeDisplay(index);
      });
      if (this.elements.masterSlider) {
        this.elements.masterSlider.value = String(this.masterVolume);
      }
      this.updateMasterVolumeDisplay();
      this.updateButtons();
    }

    updateButtons() {
      const { startButton, stopButton } = this.elements;
      if (startButton) {
        startButton.disabled = !this.supported || this.running;
      }
      if (stopButton) {
        stopButton.disabled = !this.running;
      }
    }

    async start() {
      if (this.running) {
        return;
      }
      await this.ensureContext();
      this.startAllVoices();
      this.running = true;
      this.updateButtons();
      this.setStatus('Synthé SCC actif. Ajustez les curseurs pour modeler le son.', { isSuccess: true });
    }

    startAllVoices() {
      if (!this.audioContext || !this.masterGain || !this.periodicWave) {
        return;
      }
      this.stopAllVoices();
      this.voiceSettings.forEach((settings, index) => {
        const oscillator = this.audioContext.createOscillator();
        oscillator.setPeriodicWave(this.periodicWave);
        oscillator.frequency.value = settings.frequency;
        const gainNode = this.audioContext.createGain();
        gainNode.gain.value = this.percentToGain(settings.volume);
        oscillator.connect(gainNode);
        gainNode.connect(this.masterGain);
        oscillator.start();
        this.voices[index] = { oscillator, gain: gainNode };
      });
    }

    stopAllVoices() {
      if (!this.voices.length) {
        this.voices = [];
        return;
      }
      this.voices.forEach((voice) => {
        if (!voice) {
          return;
        }
        try {
          voice.oscillator.stop();
        } catch (error) {
          // Oscillateur déjà arrêté : ignorer.
        }
        try {
          voice.oscillator.disconnect();
        } catch (error) {
          // Ignorer les erreurs de déconnexion.
        }
        try {
          voice.gain.disconnect();
        } catch (error) {
          // Ignorer les erreurs de déconnexion.
        }
      });
      this.voices = [];
    }

    stop() {
      if (!this.running) {
        return;
      }
      this.stopAllVoices();
      this.running = false;
      this.updateButtons();
      if (this.audioContext && this.audioContext.state !== 'closed') {
        this.audioContext.suspend().catch(() => {
          // Aucun besoin de remonter l'erreur.
        });
      }
      this.setStatus('Aucune voix active.', { isSuccess: false });
    }

    setStatus(message, { isError = false, isSuccess = false } = {}) {
      const { status } = this.elements;
      if (!status) {
        return;
      }
      status.textContent = message;
      status.classList.toggle('is-error', Boolean(isError));
      status.classList.toggle('is-success', Boolean(isSuccess));
    }
  }

  function initialiseSccSynth() {
    const startButton = document.getElementById('sccStartButton');
    const stopButton = document.getElementById('sccStopButton');
    const status = document.getElementById('sccStatus');
    const masterSlider = document.getElementById('sccMasterVolume');
    const masterValue = document.getElementById('sccMasterVolumeValue');

    if (!startButton || !stopButton || !status || !masterSlider || !masterValue) {
      return;
    }

    const voiceControls = [];
    for (let i = 1; i <= DEFAULT_VOICE_SETTINGS.length; i += 1) {
      const frequencySlider = document.getElementById(`sccVoice${i}Frequency`);
      const frequencyValue = document.getElementById(`sccVoice${i}FrequencyValue`);
      const volumeSlider = document.getElementById(`sccVoice${i}Volume`);
      const volumeValue = document.getElementById(`sccVoice${i}VolumeValue`);
      voiceControls.push({ frequencySlider, frequencyValue, volumeSlider, volumeValue });
    }

    new SccSynth({
      startButton,
      stopButton,
      status,
      masterSlider,
      masterValue,
      voiceControls,
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialiseSccSynth, { once: true });
  } else {
    initialiseSccSynth();
  }
})();
