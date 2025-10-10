import { initializeApp } from './app.js';

function bootstrap() {
  if (typeof document === 'undefined') {
    return;
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      initializeApp();
    }, { once: true });
    return;
  }
  initializeApp();
}

bootstrap();
