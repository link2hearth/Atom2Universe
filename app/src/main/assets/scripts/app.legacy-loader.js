(function legacyAppLoader(global) {
  if (!global || global.__LEGACY_APP_LOADER__) {
    return;
  }
  global.__LEGACY_APP_LOADER__ = true;

  var documentRef = global.document;
  if (!documentRef) {
    return;
  }

  var currentScript = documentRef.currentScript;
  var baseUrl;
  try {
    if (currentScript && currentScript.src) {
      baseUrl = new URL('./', currentScript.src);
    } else if (documentRef.baseURI) {
      baseUrl = new URL('./scripts/', documentRef.baseURI);
    } else {
      baseUrl = new URL('./scripts/', global.location && global.location.href ? global.location.href : '');
    }
  } catch (error) {
    console.error('Legacy loader: unable to resolve asset base URL.', error);
    return;
  }

  var loadedScripts = Object.create(null);

  function resolveSpecifier(specifier) {
    if (typeof specifier !== 'string') {
      throw new TypeError('Legacy loader: specifier must be a string.');
    }
    var trimmed = specifier.trim();
    if (!trimmed) {
      throw new TypeError('Legacy loader: specifier cannot be empty.');
    }
    try {
      var resolved = new URL(trimmed, baseUrl);
      return resolved.href;
    } catch (error) {
      throw new Error('Legacy loader: unable to resolve specifier "' + trimmed + '".');
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

  injectScript(new URL('app.js', baseUrl).href).catch(function(error) {
    console.error('Legacy loader: unable to bootstrap the application.', error);
  });
})(typeof window !== 'undefined' ? window : this);
