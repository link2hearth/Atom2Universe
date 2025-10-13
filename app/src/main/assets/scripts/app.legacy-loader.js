(function legacyAppLoader(global) {
  if (!global || global.__LEGACY_APP_LOADER__) {
    return;
  }
  global.__LEGACY_APP_LOADER__ = true;

  var documentRef = global.document;
  if (!documentRef) {
    return;
  }

  var hasURLConstructor = typeof global.URL === 'function';

  function ensureDirectoryHref(href) {
    if (typeof href !== 'string' || !href) {
      return null;
    }
    var withoutHash = href.split('#')[0];
    var withoutQuery = withoutHash.split('?')[0];
    if (/\/\/.test(withoutQuery)) {
      // Ensure we always keep the trailing slash for directory hrefs.
      if (withoutQuery.charAt(withoutQuery.length - 1) === '/') {
        return withoutQuery;
      }
      var lastSlashIndex = withoutQuery.lastIndexOf('/');
      if (lastSlashIndex === -1) {
        return withoutQuery + '/';
      }
      return withoutQuery.slice(0, lastSlashIndex + 1);
    }
    return withoutQuery;
  }

  var anchorForResolution = documentRef.createElement('a');

  function resolveWithFallback(specifier, baseHref) {
    if (typeof specifier !== 'string') {
      throw new TypeError('Legacy loader: specifier must be a string.');
    }
    var trimmed = specifier.trim();
    if (!trimmed) {
      throw new TypeError('Legacy loader: specifier cannot be empty.');
    }
    if (hasURLConstructor) {
      var resolved = new URL(trimmed, baseHref);
      return resolved.href;
    }

    if (/^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(trimmed)) {
      return trimmed;
    }

    anchorForResolution.href = baseHref;
    if (trimmed.slice(0, 2) === '//') {
      return anchorForResolution.protocol ? anchorForResolution.protocol + trimmed : trimmed;
    }

    var absoluteBase = anchorForResolution.href;
    var directoryHref = ensureDirectoryHref(absoluteBase);
    if (!directoryHref) {
      throw new Error('Legacy loader: unable to resolve base URL using fallback.');
    }
    anchorForResolution.href = directoryHref + trimmed;
    return anchorForResolution.href;
  }

  function resolveBaseHref(baseCandidate, relative) {
    if (!baseCandidate) {
      return null;
    }
    try {
      return hasURLConstructor
        ? new URL(relative, baseCandidate).href
        : resolveWithFallback(relative, baseCandidate);
    } catch (error) {
      return null;
    }
  }

  var currentScript = documentRef.currentScript;
  var baseUrl = null;

  if (currentScript && currentScript.src) {
    baseUrl = resolveBaseHref(currentScript.src, './');
  }
  if (!baseUrl && documentRef.baseURI) {
    baseUrl = resolveBaseHref(documentRef.baseURI, './scripts/');
  }
  if (!baseUrl) {
    var locationHref = global.location && global.location.href ? global.location.href : '';
    baseUrl = resolveBaseHref(locationHref, './scripts/');
  }

  if (!baseUrl) {
    console.error('Legacy loader: unable to resolve asset base URL.');
    return;
  }

  var loadedScripts = Object.create(null);

  function resolveSpecifier(specifier) {
    try {
      return resolveWithFallback(specifier, baseUrl);
    } catch (error) {
      throw new Error('Legacy loader: unable to resolve specifier "' + specifier + '".');
    }
  }

  function injectScript(url) {
    if (loadedScripts[url]) {
      return loadedScripts[url];
    }

    loadedScripts[url] = new Promise(function(resolve, reject) {
      var script = documentRef.createElement('script');
      script.src = url;
      script.defer = false;
      script.async = false;
      script.addEventListener('load', function() {
        resolve(global);
      });
      script.addEventListener('error', function(event) {
        var reason = new Error('Legacy loader: failed to load "' + url + '".');
        reason.event = event;
        reject(reason);
      });
      (documentRef.head || documentRef.documentElement || documentRef.body || documentRef).appendChild(script);
    });

    return loadedScripts[url];
  }

  global.__dynamicImport__ = function legacyDynamicImport(specifier) {
    var url;
    try {
      url = resolveSpecifier(specifier);
    } catch (error) {
      return Promise.reject(error);
    }
    return injectScript(url).then(function() {
      return {};
    });
  };

  if (documentRef.documentElement) {
    documentRef.documentElement.classList.add('is-legacy-runtime');
  }

  injectScript(resolveSpecifier('app.js')).catch(function(error) {
    console.error('Legacy loader: unable to bootstrap the application.', error);
  });
})(typeof window !== 'undefined' ? window : this);
