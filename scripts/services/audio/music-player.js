export const DEFAULT_MUSIC_VOLUME = 0.5;

export function clampMusicVolume(value, fallback = DEFAULT_MUSIC_VOLUME) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  if (numeric <= 0) {
    return 0;
  }
  if (numeric >= 1) {
    return 1;
  }
  return numeric;
}

function normalizeDirectory(input) {
  if (typeof input !== 'string' || !input.trim()) {
    return 'Assets/Music/';
  }
  const trimmed = input.trim();
  return trimmed.endsWith('/') ? trimmed : `${trimmed}/`;
}

function normalizeExtensions(list) {
  if (!Array.isArray(list) || !list.length) {
    return ['mp3', 'ogg', 'wav', 'webm', 'm4a'];
  }
  const unique = new Set();
  list.forEach(entry => {
    if (typeof entry !== 'string') {
      return;
    }
    const normalized = entry.trim().toLowerCase();
    if (normalized) {
      unique.add(normalized);
    }
  });
  return unique.size ? Array.from(unique) : ['mp3', 'ogg', 'wav', 'webm', 'm4a'];
}

function normalizeFallbackTracks(list) {
  if (!Array.isArray(list) || !list.length) {
    return [];
  }
  return list
    .map(entry => (typeof entry === 'string' ? entry.trim() : ''))
    .filter(Boolean);
}

function defaultCompareText(a, b) {
  return String(a).localeCompare(String(b));
}

export function createMusicPlayer(options = {}) {
  const {
    musicDir = 'Assets/Music/',
    supportedExtensions = [],
    fallbackTracks = [],
    defaultVolume = DEFAULT_MUSIC_VOLUME,
    clampVolume = clampMusicVolume,
    compareText = defaultCompareText
  } = options;

  const MUSIC_DIR = normalizeDirectory(musicDir);
  const SUPPORTED_EXTENSIONS = normalizeExtensions(supportedExtensions);
  const FALLBACK_TRACKS = normalizeFallbackTracks(fallbackTracks);
  const compare = typeof compareText === 'function' ? compareText : defaultCompareText;

  if (typeof window === 'undefined' || typeof Audio === 'undefined') {
    const resolved = Promise.resolve([]);
    let stubVolume = clampVolume(defaultVolume, defaultVolume);
    return {
      init(initOptions) {
        if (initOptions && typeof initOptions.volume === 'number') {
          stubVolume = clampVolume(initOptions.volume, stubVolume);
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
      setVolume(value) {
        stubVolume = clampVolume(value, stubVolume);
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
  let volume = clampVolume(defaultVolume, defaultVolume);
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
        || entry.filename
        || entry.id
        || entry.src
        || entry.source
        || entry.file
        || entry.url;
      return sanitizeFileName(candidate);
    }
    return '';
  };

  const discoverTracksFromList = async listName => {
    const candidates = await loadJsonList(listName);
    return candidates
      .map(candidate => createTrack(normalizeCandidate(candidate)))
      .filter(Boolean);
  };

  const discoverTracks = async () => {
    const explicitList = await discoverTracksFromList('tracks.json');
    if (explicitList.length) {
      return explicitList;
    }
    const fallbackList = await discoverTracksFromList('playlist.json');
    if (fallbackList.length) {
      return fallbackList;
    }
    const directoryList = await loadDirectoryListing();
    const normalized = directoryList
      .map(candidate => createTrack(normalizeCandidate(candidate)))
      .filter(Boolean);
    if (normalized.length) {
      return normalized;
    }
    const sanitizedFallback = FALLBACK_TRACKS
      .map(candidate => createTrack(normalizeCandidate(candidate), { placeholder: true }))
      .filter(Boolean);
    return sanitizedFallback;
  };

  const findIndexForId = id => {
    if (!id) {
      return -1;
    }
    const target = id.trim().toLowerCase();
    return tracks.findIndex(track => track.id.toLowerCase() === target);
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
    const normalized = clampVolume(value, volume);
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
      const matches = Array.from(
        text.matchAll(/href="([^"?#]+\.(?:mp3|ogg|wav|webm|m4a))"/gi)
      );
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
        return compare(nameA, nameB, { sensitivity: 'base' });
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

  const init = (initOptions = {}) => {
    if (readyPromise) {
      return readyPromise;
    }

    if (initOptions && typeof initOptions.volume === 'number') {
      setVolumeValue(initOptions.volume, { silent: true });
    }

    if (typeof initOptions.preferredTrackId === 'string') {
      const rawPreference = initOptions.preferredTrackId.trim();
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

    const autoplay = initOptions?.autoplay !== false;

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
    setVolume: (value, initOptions) => setVolumeValue(value, initOptions || {}),
    getVolume: () => getVolumeValue(),
    onChange,
    isAwaitingUserGesture: () => awaitingUserGesture
  };
}
