function getCustomPagesRegistry() {
  const config = typeof globalThis !== 'undefined' ? globalThis.GAME_CONFIG : null;
  const customPages = config && typeof config.customPages === 'object' ? config.customPages : null;
  if (customPages && Array.isArray(customPages.entries)) {
    return customPages.entries;
  }
  if (Array.isArray(customPages)) {
    return customPages;
  }
  return [];
}

function isStandaloneOpenMode(openMode) {
  return typeof openMode === 'string' && openMode.trim().toLowerCase() === 'standalone';
}

function setDataAttribute(element, name, value) {
  if (!value || !element) {
    return;
  }
  element.setAttribute(name, value);
}

function renderCustomPages() {
  const list = document.getElementById('customPagesList');
  const emptyState = document.getElementById('customPagesEmptyState');
  if (!list) {
    return;
  }

  const entries = getCustomPagesRegistry();
  list.innerHTML = '';

  if (!Array.isArray(entries) || entries.length === 0) {
    if (emptyState) {
      emptyState.hidden = false;
    }
    return;
  }

  if (emptyState) {
    emptyState.hidden = true;
  }

  entries.forEach(entry => {
    if (!entry || typeof entry !== 'object') {
      return;
    }
    const id = typeof entry.id === 'string' ? entry.id.trim() : '';
    const path = typeof entry.path === 'string' ? entry.path.trim() : '';
    const titleKey = typeof entry.titleKey === 'string' ? entry.titleKey.trim() : '';
    if (!id || !path || !titleKey) {
      return;
    }

    const item = document.createElement('li');
    item.className = 'custom-pages-item';

    const link = document.createElement('a');
    link.className = 'custom-pages-link option-link-button option-link-button--secondary';
    link.href = path;
    link.textContent = id;
    link.dataset.i18n = titleKey;
    if (isStandaloneOpenMode(entry.openMode)) {
      link.target = '_blank';
      link.rel = 'noopener';
    }
    setDataAttribute(link, 'data-custom-page-id', id);
    if (Array.isArray(entry.css) && entry.css.length > 0) {
      setDataAttribute(link, 'data-custom-css', entry.css.join(','));
    }
    if (Array.isArray(entry.js) && entry.js.length > 0) {
      setDataAttribute(link, 'data-custom-js', entry.js.join(','));
    }

    item.appendChild(link);

    if (typeof entry.descriptionKey === 'string' && entry.descriptionKey.trim()) {
      const description = document.createElement('p');
      description.className = 'custom-pages-description';
      description.textContent = id;
      description.dataset.i18n = entry.descriptionKey.trim();
      item.appendChild(description);
    }

    list.appendChild(item);
  });

  if (globalThis.i18n && typeof globalThis.i18n.updateTranslations === 'function') {
    globalThis.i18n.updateTranslations(list);
  }
}

function setupCustomPages() {
  renderCustomPages();
  if (typeof window !== 'undefined') {
    window.addEventListener('i18n:languagechange', () => {
      renderCustomPages();
    });
  }
}

if (typeof window !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', setupCustomPages);
  } else {
    setupCustomPages();
  }
}
