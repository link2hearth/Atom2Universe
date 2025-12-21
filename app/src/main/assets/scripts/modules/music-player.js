const musicPlayer = (() => {
  const MUSIC_DIR = 'Assets/Music/';
  const SUPPORTED_EXTENSIONS = MUSIC_SUPPORTED_EXTENSIONS;
  const FALLBACK_TRACKS = MUSIC_FALLBACK_TRACKS;

  const SUPPORTED_EXTENSION_PATTERN = (() => {
    const uniqueExtensions = Array.from(new Set(
      SUPPORTED_EXTENSIONS
        .map(ext => (typeof ext === 'string' ? ext.trim().toLowerCase() : ''))
        .map(ext => ext.replace(/[^a-z0-9]/g, ''))
        .filter(Boolean)
    ));
    if (!uniqueExtensions.length) {
      return /href="([^"?#]+\.(?:mp3|ogg|wav|webm|m4a))"/gi;
    }
    const pattern = uniqueExtensions.join('|');
    return new RegExp(`href="([^"?#]+\\.(?:${pattern}))"`, 'gi');
  })();

  if (typeof window === 'undefined' || typeof Audio === 'undefined') {
    const resolved = Promise.resolve([]);
    let stubVolume = DEFAULT_MUSIC_VOLUME;
    return {
      init: options => {
        if (options && typeof options.volume === 'number') {
          stubVolume = clampMusicVolume(options.volume, stubVolume);
        }
        return resolved;
      },
      ready: () => resolved,
      getTracks: () => [],
      getCurrentTrack: () => null,
      getCurrentTrackId: () => null,
      getPlaybackState: () => 'unsupported',
      playTrackById: id => {
        const normalized = typeof id === 'string' ? id.trim().toLowerCase() : '';
        return !normalized || normalized === 'none';
      },
      stop: () => true,
      setVolume: value => {
        stubVolume = clampMusicVolume(value, stubVolume);
        return stubVolume;
      },
      getVolume: () => stubVolume,
      onChange: () => () => {},
      isAwaitingUserGesture: () => false
    };
  }

  let tracks = [];
  let audioElement = null;
  let currentIndex = -1;
  let readyPromise = null;
  let preferredTrackId = null;
  let awaitingUserGesture = true;
  let unlockListenersAttached = false;
  let volume = DEFAULT_MUSIC_VOLUME;
  const changeListeners = new Set();

  const formatDisplayName = fileName => {
    if (!fileName) {
      return '';
    }
    const segments = String(fileName).split('/').filter(Boolean);
    const lastSegment = segments.length ? segments[segments.length - 1] : String(fileName);
    const baseName = lastSegment
      .replace(/\.[^/.]+$/, '')
      .replace(/[_-]+/g, ' ')
      .trim();
    if (!baseName) {
      return lastSegment || fileName;
    }
    return baseName.replace(/\b\w/g, char => char.toUpperCase());
  };

  const sanitizeFileName = input => {
    if (!input || typeof input !== 'string') {
      return '';
    }
    let value = input.trim();
    if (!value) {
      return '';
    }
    try {
      value = decodeURIComponent(value);
    } catch (error) {
      // Ignore decoding issues and keep the original value.
    }
    value = value.replace(/^[./]+/, '');
    value = value.replace(/^Assets\/?Music\//i, '');
    value = value.replace(/^assets\/?music\//i, '');
    value = value.split(/[?#]/)[0];
    value = value.replace(/\\/g, '/');
    const parts = value
      .split('/')
      .map(part => part.trim())
      .filter(part => part && part !== '..');
    return parts.join('/');
  };

  const isSupportedFile = fileName => {
    const cleanName = sanitizeFileName(fileName);
    const segments = cleanName.split('.');
    if (segments.length <= 1) {
      return false;
    }
    const extension = segments.pop().toLowerCase();
    return SUPPORTED_EXTENSIONS.includes(extension);
  };

  const createTrack = (fileName, { placeholder = false } = {}) => {
    const cleanName = sanitizeFileName(fileName);
    if (!cleanName || !isSupportedFile(cleanName)) {
      return null;
    }
    const encodedPath = cleanName
      .split('/')
      .map(segment => encodeURIComponent(segment))
      .join('/');
    return {
      id: cleanName,
      filename: cleanName,
      src: `${MUSIC_DIR}${encodedPath}`,
      displayName: formatDisplayName(cleanName),
      placeholder
    };
  };

  const normalizeCandidate = entry => {
    if (!entry) {
      return '';
    }
    if (typeof entry === 'string') {
      return sanitizeFileName(entry);
    }
    if (typeof entry === 'object') {
      const candidate = entry.path
        ?? entry.src
        ?? entry.url
        ?? entry.file
        ?? entry.filename
        ?? entry.name;
      return typeof candidate === 'string' ? sanitizeFileName(candidate) : '';
    }
    return '';
  };

  const findIndexForId = id => {
    if (!id) {
      return -1;
    }
    const trimmed = typeof id === 'string' ? id.trim().toLowerCase() : '';
    const sanitized = sanitizeFileName(id).toLowerCase();
    const candidates = new Set([trimmed, sanitized]);
    const addBaseVariant = value => {
      if (value && value.includes('.')) {
        candidates.add(value.replace(/\.[^/.]+$/, ''));
      }
    };
    addBaseVariant(trimmed);
    addBaseVariant(sanitized);
    return tracks.findIndex(track => {
      const name = track.id?.toLowerCase?.() ?? '';
      const file = track.filename?.toLowerCase?.() ?? '';
      const src = track.src?.toLowerCase?.() ?? '';
      const base = track.filename?.split('/')?.pop()?.toLowerCase?.() ?? '';
      const display = track.displayName?.toLowerCase?.() ?? '';
      return (
        candidates.has(name)
        || candidates.has(file)
        || candidates.has(src)
        || candidates.has(base)
        || candidates.has(display)
      );
    });
  };

  const getPlaybackState = () => {
    if (!audioElement) {
      return 'idle';
    }
    if (audioElement.error) {
      return 'error';
    }
    if (audioElement.paused) {
      return audioElement.currentTime > 0 ? 'paused' : 'idle';
    }
    return 'playing';
  };

  const emitChange = type => {
    const payload = {
      type,
      tracks: tracks.map(track => ({ ...track })),
      currentTrack: tracks[currentIndex] ? { ...tracks[currentIndex] } : null,
      state: getPlaybackState(),
      awaitingUserGesture
    };
    changeListeners.forEach(listener => {
      try {
        listener(payload);
      } catch (error) {
        console.error('Music listener error', error);
      }
    });
  };

  const applyVolumeToAudio = () => {
    if (audioElement) {
      audioElement.volume = volume;
    }
  };

  const setVolumeValue = (value, { silent = false } = {}) => {
    const normalized = clampMusicVolume(value, volume);
    if (normalized === volume) {
      return volume;
    }
    volume = normalized;
    applyVolumeToAudio();
    if (!silent) {
      emitChange('volume');
    }
    return volume;
  };

  const getVolumeValue = () => volume;

  const getAudioElement = () => {
    if (!audioElement) {
      audioElement = new Audio();
      audioElement.loop = true;
      audioElement.preload = 'auto';
      audioElement.setAttribute('preload', 'auto');
      audioElement.volume = volume;
      audioElement.addEventListener('playing', () => {
        awaitingUserGesture = false;
        emitChange('state');
      });
      audioElement.addEventListener('pause', () => {
        emitChange('state');
      });
      audioElement.addEventListener('error', () => {
        emitChange('error');
      });
    }
    return audioElement;
  };

  const tryPlay = () => {
    const audio = getAudioElement();
    if (!audio.src) {
      return;
    }
    audio.volume = volume;
    const playPromise = audio.play();
    if (playPromise && typeof playPromise.catch === 'function') {
      playPromise.catch(() => {});
    }
  };

  const stop = ({ keepPreference = false, silent = false } = {}) => {
    let hadSource = false;
    if (audioElement) {
      try {
        audioElement.pause();
      } catch (error) {
        // Ignore pause issues.
      }
      try {
        if (audioElement.currentTime) {
          audioElement.currentTime = 0;
        }
      } catch (error) {
        // Ignore reset issues.
      }
      if (audioElement.src) {
        hadSource = true;
      }
      audioElement.removeAttribute('src');
      audioElement.src = '';
    }
    const wasPlaying = hadSource || currentIndex !== -1;
    currentIndex = -1;
    if (!keepPreference) {
      preferredTrackId = null;
    }
    if (!silent) {
      emitChange('stop');
    } else {
      emitChange('track');
    }
    return wasPlaying;
  };

  const playIndex = index => {
    if (!tracks.length) {
      currentIndex = -1;
      emitChange('track');
      return false;
    }
    const wrappedIndex = ((index % tracks.length) + tracks.length) % tracks.length;
    const track = tracks[wrappedIndex];
    const audio = getAudioElement();
    if (audio.src !== track.src) {
      audio.src = track.src;
    }
    audio.currentTime = 0;
    audio.volume = volume;
    currentIndex = wrappedIndex;
    preferredTrackId = track.id;
    emitChange('track');
    tryPlay();
    return true;
  };

  const setupUnlockListeners = () => {
    if (unlockListenersAttached || typeof document === 'undefined') {
      return;
    }
    unlockListenersAttached = true;
    awaitingUserGesture = true;
    const unlock = () => {
      awaitingUserGesture = false;
      tryPlay();
    };
    document.addEventListener('pointerdown', unlock, { once: true, capture: false });
    document.addEventListener('keydown', unlock, { once: true, capture: false });
  };

  const loadJsonList = async fileName => {
    try {
      const response = await fetch(`${MUSIC_DIR}${fileName}`, { cache: 'no-store' });
      if (!response.ok) {
        return [];
      }
      const data = await response.json();
      if (Array.isArray(data)) {
        return data;
      }
      if (data && Array.isArray(data.files)) {
        return data.files;
      }
      if (data && Array.isArray(data.tracks)) {
        return data.tracks;
      }
      return [];
    } catch (error) {
      return [];
    }
  };

  const loadDirectoryListing = async () => {
    try {
      const response = await fetch(MUSIC_DIR, { cache: 'no-store' });
      if (!response.ok) {
        return [];
      }
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        const data = await response.json();
        if (Array.isArray(data)) {
          return data;
        }
        if (data && Array.isArray(data.files)) {
          return data.files;
        }
        if (data && Array.isArray(data.tracks)) {
          return data.tracks;
        }
        return [];
      }
      const text = await response.text();
      const matches = Array.from(text.matchAll(SUPPORTED_EXTENSION_PATTERN));
      return matches.map(match => match[1]).filter(Boolean);
    } catch (error) {
      return [];
    }
  };

  const sortTrackList = list => {
    return list
      .slice()
      .sort((a, b) => {
        const nameA = (a?.displayName || a?.filename || '').toString();
        const nameB = (b?.displayName || b?.filename || '').toString();
        return compareTextLocalized(nameA, nameB, { sensitivity: 'base' });
      });
  };

  const verifyTrackAvailability = async list => {
    const results = await Promise.all(
      list.map(async track => {
        if (!track || !track.placeholder) {
          return track;
        }
        if (typeof window !== 'undefined' && window.location?.protocol === 'file:') {
          return { ...track, placeholder: false };
        }
        try {
          const response = await fetch(track.src, { method: 'HEAD', cache: 'no-store' });
          if (response.ok) {
            return { ...track, placeholder: false };
          }
          if (response.status === 405) {
            const rangeResponse = await fetch(track.src, {
              method: 'GET',
              headers: { Range: 'bytes=0-0' },
              cache: 'no-store'
            });
            if (rangeResponse.ok) {
              return { ...track, placeholder: false };
            }
          }
        } catch (error) {
          try {
            const fallbackResponse = await fetch(track.src, {
              method: 'GET',
              headers: { Range: 'bytes=0-0' },
              cache: 'no-store'
            });
            if (fallbackResponse.ok) {
              return { ...track, placeholder: false };
            }
          } catch (innerError) {
            // Ignore network failures and keep placeholder flag.
          }
        }
        return track;
      })
    );
    return results;
  };

  const discoverTracks = async () => {
    const discovered = new Set();
    const pushCandidate = candidate => {
      const normalized = normalizeCandidate(candidate);
      if (!normalized || !isSupportedFile(normalized)) {
        return;
      }
      discovered.add(normalized);
    };

    for (const manifest of ['tracks.json', 'manifest.json', 'playlist.json', 'music.json', 'list.json']) {
      const entries = await loadJsonList(manifest);
      entries.forEach(pushCandidate);
      if (discovered.size > 0) {
        break;
      }
    }

    if (discovered.size === 0) {
      const listing = await loadDirectoryListing();
      listing.forEach(pushCandidate);
    }

    if (discovered.size > 0) {
      return Array.from(discovered)
        .map(name => createTrack(name))
        .filter(Boolean);
    }

    return FALLBACK_TRACKS.map(name => createTrack(name, { placeholder: true })).filter(Boolean);
  };

  const init = (options = {}) => {
    if (readyPromise) {
      if (typeof options.volume === 'number') {
        setVolumeValue(options.volume);
      }
      if (typeof options.preferredTrackId === 'string') {
        const trimmed = options.preferredTrackId.trim();
        if (!trimmed || ['none', 'off', 'stop'].includes(trimmed.toLowerCase())) {
          preferredTrackId = null;
          stop({ keepPreference: false });
        } else {
          preferredTrackId = sanitizeFileName(trimmed) || null;
          if (preferredTrackId && options.autoplay !== false) {
            const preferredIndex = findIndexForId(preferredTrackId);
            if (preferredIndex >= 0) {
              playIndex(preferredIndex);
            }
          } else if (options.autoplay === false) {
            stop({ keepPreference: Boolean(preferredTrackId) });
          }
        }
      } else if (options.autoplay === false) {
        stop({ keepPreference: Boolean(preferredTrackId) });
      }
      return readyPromise;
    }

    if (typeof options.volume === 'number') {
      setVolumeValue(options.volume, { silent: true });
    }

    if (typeof options.preferredTrackId === 'string') {
      const rawPreference = options.preferredTrackId.trim();
      if (!rawPreference || ['none', 'off', 'stop'].includes(rawPreference.toLowerCase())) {
        preferredTrackId = null;
      } else {
        preferredTrackId = sanitizeFileName(rawPreference);
        if (preferredTrackId && !preferredTrackId.trim()) {
          preferredTrackId = null;
        }
      }
    } else {
      preferredTrackId = null;
    }

    const autoplay = options?.autoplay !== false;

    setupUnlockListeners();

    readyPromise = discoverTracks()
      .then(async foundTracks => {
        const verified = await verifyTrackAvailability(foundTracks);
        tracks = sortTrackList(verified);
        emitChange('tracks');
        if (!tracks.length) {
          currentIndex = -1;
          emitChange('track');
          if (!autoplay) {
            stop({ keepPreference: Boolean(preferredTrackId) });
          }
          return tracks;
        }
        if (!autoplay) {
          stop({ keepPreference: Boolean(preferredTrackId) });
          return tracks;
        }
        const preferredIndex = preferredTrackId ? findIndexForId(preferredTrackId) : -1;
        const indexToPlay = preferredIndex >= 0
          ? preferredIndex
          : Math.floor(Math.random() * tracks.length);
        playIndex(indexToPlay);
        return tracks;
      })
      .catch(error => {
        console.error('Erreur de découverte des pistes musicales', error);
        const fallbackList = FALLBACK_TRACKS.map(name => createTrack(name, { placeholder: true })).filter(Boolean);
        return verifyTrackAvailability(fallbackList).then(verifiedFallback => {
          tracks = sortTrackList(verifiedFallback);
          emitChange('tracks');
          if (!tracks.length) {
            currentIndex = -1;
            emitChange('track');
            return tracks;
          }
          if (!autoplay) {
            stop({ keepPreference: Boolean(preferredTrackId) });
            return tracks;
          }
          const preferredIndex = preferredTrackId ? findIndexForId(preferredTrackId) : -1;
          playIndex(preferredIndex >= 0 ? preferredIndex : Math.floor(Math.random() * tracks.length));
          return tracks;
        });
      });

    return readyPromise;
  };

  const ready = () => {
    if (readyPromise) {
      return readyPromise;
    }
    return init();
  };

  const getTracks = () => tracks.map(track => ({ ...track }));

  const getCurrentTrack = () => {
    if (currentIndex < 0 || currentIndex >= tracks.length) {
      return null;
    }
    return { ...tracks[currentIndex] };
  };

  const getCurrentTrackId = () => {
    const current = getCurrentTrack();
    return current ? current.id : null;
  };

  const playTrackById = id => {
    const raw = typeof id === 'string' ? id.trim() : '';
    const normalized = raw.toLowerCase();
    if (!raw || normalized === 'none' || normalized === 'off' || normalized === 'stop') {
      stop({ keepPreference: false });
      return true;
    }
    const sanitized = sanitizeFileName(raw);
    if (!sanitized) {
      stop({ keepPreference: false });
      return true;
    }
    preferredTrackId = sanitized;
    if (!tracks.length) {
      emitChange('track');
      return false;
    }
    const index = findIndexForId(sanitized);
    if (index === -1) {
      emitChange('track');
      return false;
    }
    return playIndex(index);
  };

  const onChange = listener => {
    if (typeof listener !== 'function') {
      return () => {};
    }
    changeListeners.add(listener);
    return () => {
      changeListeners.delete(listener);
    };
  };

  return {
    init,
    ready,
    getTracks,
    getCurrentTrack,
    getCurrentTrackId,
    getPlaybackState,
    playTrackById,
    stop,
    setVolume: (value, options) => setVolumeValue(value, options || {}),
    getVolume: () => getVolumeValue(),
    onChange,
    isAwaitingUserGesture: () => awaitingUserGesture
  };
})();

function updateMusicSelectOptions() {
  const select = elements.musicTrackSelect;
  if (!select) {
    return;
  }
  const tracks = musicPlayer.getTracks();
  const current = musicPlayer.getCurrentTrack();
  const previousValue = select.value;
  select.innerHTML = '';
  if (!tracks.length) {
    const option = document.createElement('option');
    option.value = '';
    option.textContent = t('scripts.app.music.noneAvailable');
    select.appendChild(option);
    select.disabled = true;
    return;
  }
  const noneOption = document.createElement('option');
  noneOption.value = '';
  noneOption.textContent = t('scripts.app.music.noneOption');
  select.appendChild(noneOption);
  tracks.forEach(track => {
    const option = document.createElement('option');
    option.value = track.id;
    option.textContent = track.placeholder
      ? t('scripts.app.music.missingDisplay', { name: track.displayName })
      : track.displayName;
    option.dataset.placeholder = track.placeholder ? 'true' : 'false';
    select.appendChild(option);
  });
  let valueToSelect = current?.id || '';
  if (!valueToSelect && previousValue) {
    if (previousValue === '' || previousValue === 'none') {
      valueToSelect = '';
    } else if (tracks.some(track => track.id === previousValue)) {
      valueToSelect = previousValue;
    }
  }
  if (!valueToSelect
    && gameState.musicTrackId
    && gameState.musicEnabled !== false
    && tracks.some(track => track.id === gameState.musicTrackId)) {
    valueToSelect = gameState.musicTrackId;
  }
  select.value = valueToSelect;
  select.disabled = false;
}

function updateMusicStatus() {
  const status = elements.musicTrackStatus;
  if (!status) {
    return;
  }
  const tracks = musicPlayer.getTracks();
  if (!tracks.length) {
    status.textContent = t('scripts.app.music.addFilesHint');
    return;
  }
  const current = musicPlayer.getCurrentTrack();
  if (!current) {
    if (gameState.musicEnabled === false) {
      status.textContent = t('scripts.app.music.disabled');
    } else {
      status.textContent = t('scripts.app.music.selectTrack');
    }
    return;
  }
  const playbackState = musicPlayer.getPlaybackState();
  let message = t('scripts.app.music.looping', { track: current.displayName });
  if (current.placeholder) {
    message += t('scripts.app.music.missingSuffix');
  }
  if (playbackState === 'unsupported') {
    message += t('scripts.app.music.unsupportedSuffix');
  } else if (playbackState === 'error') {
    message += t('scripts.app.music.errorSuffix');
  } else if (musicPlayer.isAwaitingUserGesture()) {
    message += t('scripts.app.music.awaitingInteractionSuffix');
  } else if (playbackState === 'paused' || playbackState === 'idle') {
    message += t('scripts.app.music.pausedSuffix');
  }
  status.textContent = message;
}

function decodeTrackName(value) {
  try {
    return decodeURIComponent(value);
  } catch (error) {
    return value;
  }
}

function extractTrackDisplay(track) {
  if (!track) {
    return { display: '' };
  }
  const fileCandidate = typeof track.filename === 'string'
    ? track.filename
    : typeof track.id === 'string'
      ? track.id
      : '';
  const baseName = fileCandidate.split('/').pop() || '';
  const withoutExtension = baseName.replace(/\.[^.]+$/, '');
  const decodedBase = decodeTrackName(withoutExtension).trim();
  const separators = [' - ', ' – ', ' — '];
  let artist = '';
  let title = '';

  separators.some(separator => {
    if (decodedBase.includes(separator)) {
      const [candidateArtist, ...rest] = decodedBase.split(separator);
      artist = candidateArtist.trim();
      title = rest.join(separator).trim();
      return true;
    }
    return false;
  });

  if (!artist && decodedBase.includes('-')) {
    const dashParts = decodedBase.split('-');
    if (dashParts.length >= 2) {
      artist = dashParts.shift().trim();
      title = dashParts.join('-').trim();
    }
  }

  if (!artist && decodedBase.includes('_')) {
    const underscoreParts = decodedBase.split('_');
    if (underscoreParts.length >= 2) {
      artist = underscoreParts.shift().trim();
      title = underscoreParts.join(' ').trim();
    }
  }

  const fallbackDisplay = track.displayName || decodedBase || fileCandidate;
  const display = artist && title
    ? `${artist} — ${title}`
    : artist || fallbackDisplay;
  const tooltip = title && artist
    ? `${artist} — ${title}`
    : fallbackDisplay;

  return { artist, title, display, tooltip };
}

const midiPlaybackInfo = {
  state: 'idle',
  artist: '',
  title: '',
  track: ''
};

function updateMidiPlaybackInfo(detail = {}) {
  midiPlaybackInfo.state = detail.state || (detail.playing ? 'playing' : 'idle');
  midiPlaybackInfo.artist = detail.artist || '';
  midiPlaybackInfo.title = detail.title || '';
  midiPlaybackInfo.track = detail.track || '';
}

function getMidiNowPlayingDisplay() {
  const isPlaying = midiPlaybackInfo.state === 'playing';
  if (!isPlaying) {
    return null;
  }
  const artist = (midiPlaybackInfo.artist || '').trim();
  const title = (midiPlaybackInfo.title || '').trim();
  const display = artist && title ? `${artist} — ${title}` : artist || title;
  const tooltip = display || midiPlaybackInfo.track || '';
  return display ? { display, tooltip } : null;
}

function toggleNowPlayingBar(visible) {
  if (!elements.nowPlayingBar) {
    return;
  }
  if (visible) {
    elements.nowPlayingBar.hidden = false;
    elements.nowPlayingBar.setAttribute('aria-hidden', 'false');
    elements.nowPlayingBar.classList.add('now-playing--visible');
    return;
  }
  elements.nowPlayingBar.classList.remove('now-playing--visible');
  elements.nowPlayingBar.hidden = true;
  elements.nowPlayingBar.setAttribute('aria-hidden', 'true');
  if (elements.nowPlayingTrack) {
    elements.nowPlayingTrack.textContent = '';
    elements.nowPlayingTrack.removeAttribute('title');
  }
  elements.nowPlayingBar.removeAttribute('title');
}

function updateNowPlayingBanner(event) {
  if (!elements.nowPlayingBar || !elements.nowPlayingTrack || !isMusicModuleEnabled()) {
    return;
  }
  const midiDisplay = getMidiNowPlayingDisplay();
  if (midiDisplay) {
    setTextContentIfChanged(elements.nowPlayingTrack, midiDisplay.display);
    elements.nowPlayingBar.title = midiDisplay.tooltip;
    elements.nowPlayingTrack.setAttribute('title', midiDisplay.tooltip);
    toggleNowPlayingBar(true);
    return;
  }
  const playbackState = event?.state || musicPlayer.getPlaybackState();
  const track = event?.currentTrack || musicPlayer.getCurrentTrack();
  const shouldShow = playbackState === 'playing' && track;
  if (!shouldShow) {
    toggleNowPlayingBar(false);
    return;
  }
  const info = extractTrackDisplay(track);
  const displayText = info.display || track.displayName || track.filename || '';
  setTextContentIfChanged(elements.nowPlayingTrack, displayText);
  elements.nowPlayingBar.title = info.tooltip || displayText;
  elements.nowPlayingTrack.setAttribute('title', info.tooltip || displayText);
  toggleNowPlayingBar(true);
}

function updateMusicVolumeControl() {
  const slider = elements.musicVolumeSlider;
  if (!slider) {
    return;
  }
  const volume = typeof musicPlayer.getVolume === 'function'
    ? musicPlayer.getVolume()
    : DEFAULT_MUSIC_VOLUME;
  const clamped = Math.round(Math.min(100, Math.max(0, volume * 100)));
  slider.value = String(clamped);
  slider.setAttribute('aria-valuenow', String(clamped));
  slider.setAttribute('aria-valuetext', `${clamped}%`);
  slider.title = t('scripts.app.music.volumeLabel', { value: clamped });
  const playbackState = musicPlayer.getPlaybackState();
  slider.disabled = playbackState === 'unsupported';
}

function refreshMusicControls() {
  if (!isMusicModuleEnabled()) {
    if (elements.musicTrackSelect) {
      elements.musicTrackSelect.value = '';
      elements.musicTrackSelect.disabled = true;
    }
    if (elements.musicTrackStatus) {
      elements.musicTrackStatus.textContent = t('scripts.app.music.disabled');
    }
    if (elements.musicVolumeSlider) {
      elements.musicVolumeSlider.value = '0';
      elements.musicVolumeSlider.setAttribute('aria-valuenow', '0');
      elements.musicVolumeSlider.setAttribute('aria-valuetext', '0%');
      elements.musicVolumeSlider.disabled = true;
    }
    return;
  }
  updateMusicSelectOptions();
  updateMusicStatus();
  updateMusicVolumeControl();
  updateNowPlayingBanner();
}

musicPlayer.onChange(event => {
  if (event?.currentTrack && event.currentTrack.id) {
    gameState.musicTrackId = event.currentTrack.id;
    gameState.musicEnabled = true;
  } else if (event?.type === 'stop') {
    gameState.musicTrackId = null;
    gameState.musicEnabled = false;
  } else if (Array.isArray(event?.tracks) && event.tracks.length === 0) {
    gameState.musicTrackId = null;
    gameState.musicEnabled = DEFAULT_MUSIC_ENABLED;
  }

  if (event?.type === 'volume') {
    gameState.musicVolume = musicPlayer.getVolume();
  }

  if (event?.type === 'tracks'
    || event?.type === 'track'
    || event?.type === 'state'
    || event?.type === 'error'
    || event?.type === 'volume'
    || event?.type === 'stop') {
    refreshMusicControls();
  }
  updateNowPlayingBanner(event);
});

if (typeof globalThis !== 'undefined' && typeof globalThis.addEventListener === 'function') {
  globalThis.addEventListener('atom2univers:midiPlayback', event => {
    updateMidiPlaybackInfo(event?.detail || {});
    updateNowPlayingBanner();
  });
}

function updateMusicModuleVisibility() {
  const enabled = isMusicModuleEnabled();

  if (!enabled) {
    toggleNowPlayingBar(false);
  }

  if (enabled && appStartCompleted && !musicModuleInitRequested) {
    musicModuleInitRequested = true;
    musicPlayer.init({
      preferredTrackId: gameState.musicTrackId,
      autoplay: gameState.musicEnabled !== false,
      volume: gameState.musicVolume
    });
    musicPlayer.ready().then(() => {
      refreshMusicControls();
    });
  }

  setNavButtonLockState(elements.navMidiButton, enabled);

  if (elements.headerPlaybackButton) {
    elements.headerPlaybackButton.hidden = !enabled;
    elements.headerPlaybackButton.setAttribute('aria-hidden', enabled ? 'false' : 'true');
    elements.headerPlaybackButton.setAttribute('aria-disabled', enabled ? 'false' : 'true');
    elements.headerPlaybackButton.disabled = !enabled;
    if (!enabled) {
      elements.headerPlaybackButton.setAttribute('disabled', '');
    } else {
      elements.headerPlaybackButton.removeAttribute('disabled');
    }
    if (!enabled) {
      elements.headerPlaybackButton.setAttribute('tabindex', '-1');
      if (typeof elements.headerPlaybackButton.blur === 'function') {
        elements.headerPlaybackButton.blur();
      }
    } else {
      elements.headerPlaybackButton.removeAttribute('tabindex');
    }
  }

  if (elements.headerNextButton) {
    elements.headerNextButton.hidden = !enabled;
    elements.headerNextButton.setAttribute('aria-hidden', enabled ? 'false' : 'true');
    elements.headerNextButton.setAttribute('aria-disabled', enabled ? 'false' : 'true');
    elements.headerNextButton.disabled = !enabled;
    if (!enabled) {
      elements.headerNextButton.setAttribute('disabled', '');
      elements.headerNextButton.classList.add('is-disabled');
      elements.headerNextButton.setAttribute('tabindex', '-1');
    } else {
      elements.headerNextButton.removeAttribute('disabled');
      elements.headerNextButton.classList.remove('is-disabled');
      elements.headerNextButton.removeAttribute('tabindex');
    }
  }

  if (elements.midiPage) {
    elements.midiPage.setAttribute('data-music-disabled', enabled ? 'false' : 'true');
    elements.midiPage.setAttribute('aria-hidden', enabled ? 'false' : 'true');
    if (!enabled && document.body?.dataset?.activePage === 'midi') {
      showPage('options');
    }
  }

  if (elements.midiModuleCard) {
    elements.midiModuleCard.hidden = !enabled;
    elements.midiModuleCard.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  }

  if (elements.midiKeyboardArea) {
    elements.midiKeyboardArea.hidden = !enabled;
    elements.midiKeyboardArea.setAttribute('aria-hidden', enabled ? 'false' : 'true');
  }

  if (!enabled) {
    if (musicPlayer && typeof musicPlayer.stop === 'function') {
      const currentTrackId = typeof musicPlayer.getCurrentTrackId === 'function'
        ? musicPlayer.getCurrentTrackId()
        : null;
      if (currentTrackId) {
        musicPlayer.stop();
      }
    }
    const midiPlayer = typeof globalThis !== 'undefined'
      ? globalThis.atom2universMidiPlayer
      : null;
    if (midiPlayer && typeof midiPlayer.stop === 'function') {
      midiPlayer.stop(true);
    }
    musicModuleInitRequested = false;
    gameState.musicTrackId = null;
    gameState.musicEnabled = false;
  }

  refreshMusicControls();
}
