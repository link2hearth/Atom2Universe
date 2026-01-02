(function initEmbeddedI18n(global) {
  if (!global || typeof global !== 'object') {
    return;
  }
  if (!global.APP_EMBEDDED_I18N || typeof global.APP_EMBEDDED_I18N !== 'object') {
    global.APP_EMBEDDED_I18N = {};
  }
})(typeof globalThis !== 'undefined' ? globalThis : window);
