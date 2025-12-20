diff --git a/app/src/main/assets/scripts/src/core/feature-flags.js b/app/src/main/assets/scripts/src/core/feature-flags.js
new file mode 100644
index 0000000000000000000000000000000000000000..3f80fafd5b19e9ea6adc473d56d4547fc3744e8f
--- /dev/null
+++ b/app/src/main/assets/scripts/src/core/feature-flags.js
@@ -0,0 +1,149 @@
+// Rôle : centraliser les flags globaux et la config exportée vers app.js.
+const APP_DATA = typeof globalThis !== 'undefined' && globalThis.APP_DATA ? globalThis.APP_DATA : {};
+const GLOBAL_CONFIG =
+  typeof globalThis !== 'undefined' && globalThis.GAME_CONFIG ? globalThis.GAME_CONFIG : {};
+
+function resolveGlobalBooleanFlag(flagName, fallback = true) {
+  if (typeof globalThis !== 'undefined' && typeof globalThis[flagName] === 'boolean') {
+    return globalThis[flagName];
+  }
+  return fallback;
+}
+
+function isDevkitFeatureEnabled() {
+  return resolveGlobalBooleanFlag('DEVKIT_ENABLED', true);
+}
+
+function isCollectionFeatureEnabled() {
+  return resolveGlobalBooleanFlag('COLLECTION_SYSTEM_ENABLED', true);
+}
+
+function isCollectionVideosFeatureEnabled() {
+  return resolveGlobalBooleanFlag('COLLECTION_VIDEOS_ENABLED', isCollectionFeatureEnabled());
+}
+
+function isInfoSectionsFeatureEnabled() {
+  return resolveGlobalBooleanFlag('INFO_SECTIONS_ENABLED', true);
+}
+
+function isMusicModuleEnabled() {
+  return resolveGlobalBooleanFlag('MUSIC_MODULE_ENABLED', true);
+}
+
+function isEscapeAdvancedDifficultiesEnabled() {
+  return resolveGlobalBooleanFlag('ESCAPE_ADVANCED_DIFFICULTIES_ENABLED', false);
+}
+
+function isAtomImageVariantEnabled() {
+  return resolveGlobalBooleanFlag('ATOM_IMAGE_VARIANT_ENABLED', false);
+}
+
+function toggleDevkitFeatureAvailability() {
+  if (typeof globalThis !== 'undefined' && typeof globalThis.toggleDevkitFeatureEnabled === 'function') {
+    return globalThis.toggleDevkitFeatureEnabled();
+  }
+  const next = !isDevkitFeatureEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.DEVKIT_ENABLED = next;
+  }
+  return next;
+}
+
+function toggleCollectionFeatureAvailability() {
+  if (
+    typeof globalThis !== 'undefined'
+    && typeof globalThis.toggleCollectionFeatureEnabled === 'function'
+  ) {
+    return globalThis.toggleCollectionFeatureEnabled();
+  }
+  const next = !isCollectionFeatureEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.COLLECTION_SYSTEM_ENABLED = next;
+  }
+  return next;
+}
+
+function toggleCollectionVideosFeatureAvailability() {
+  if (
+    typeof globalThis !== 'undefined'
+    && typeof globalThis.toggleCollectionVideosFeatureEnabled === 'function'
+  ) {
+    return globalThis.toggleCollectionVideosFeatureEnabled();
+  }
+  const next = !isCollectionVideosFeatureEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.COLLECTION_VIDEOS_ENABLED = next;
+  }
+  return next;
+}
+
+function toggleInfoSectionsFeatureAvailability() {
+  if (
+    typeof globalThis !== 'undefined'
+    && typeof globalThis.toggleInfoSectionsFeatureEnabled === 'function'
+  ) {
+    return globalThis.toggleInfoSectionsFeatureEnabled();
+  }
+  const next = !isInfoSectionsFeatureEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.INFO_SECTIONS_ENABLED = next;
+  }
+  return next;
+}
+
+function toggleMusicModuleAvailability() {
+  if (
+    typeof globalThis !== 'undefined'
+    && typeof globalThis.toggleMusicModuleEnabled === 'function'
+  ) {
+    return globalThis.toggleMusicModuleEnabled();
+  }
+  const next = !isMusicModuleEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.MUSIC_MODULE_ENABLED = next;
+  }
+  return next;
+}
+
+function toggleAtomImageVariantAvailability() {
+  if (
+    typeof globalThis !== 'undefined'
+    && typeof globalThis.toggleAtomImageVariantEnabled === 'function'
+  ) {
+    return globalThis.toggleAtomImageVariantEnabled();
+  }
+  const next = !isAtomImageVariantEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.ATOM_IMAGE_VARIANT_ENABLED = next;
+  }
+  return next;
+}
+
+function toggleEscapeAdvancedDifficultiesAvailability() {
+  if (
+    typeof globalThis !== 'undefined'
+    && typeof globalThis.toggleEscapeAdvancedDifficultiesEnabled === 'function'
+  ) {
+    return globalThis.toggleEscapeAdvancedDifficultiesEnabled();
+  }
+  const next = !isEscapeAdvancedDifficultiesEnabled();
+  if (typeof globalThis !== 'undefined') {
+    globalThis.ESCAPE_ADVANCED_DIFFICULTIES_ENABLED = next;
+  }
+  if (typeof window !== 'undefined') {
+    try {
+      const detail = { enabled: next };
+      const eventName = 'escape:advanced-difficulties-changed';
+      if (typeof window.CustomEvent === 'function') {
+        window.dispatchEvent(new CustomEvent(eventName, { detail }));
+      } else if (typeof document !== 'undefined' && typeof document.createEvent === 'function') {
+        const event = document.createEvent('CustomEvent');
+        event.initCustomEvent(eventName, false, false, detail);
+        window.dispatchEvent(event);
+      }
+    } catch (error) {
+      // Ignore dispatch errors in the fallback path.
+    }
+  }
+  return next;
+}
