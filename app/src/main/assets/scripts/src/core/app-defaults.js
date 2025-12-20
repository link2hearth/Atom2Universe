const DEFAULT_SUDOKU_COMPLETION_REWARD = Object.freeze({
  enabled: true,
  levels: Object.freeze({
    facile: Object.freeze({
      bonusSeconds: 6 * 60 * 60,
      multiplier: 1,
      validSeconds: 6 * 60 * 60
    }),
    moyen: Object.freeze({
      bonusSeconds: 12 * 60 * 60,
      multiplier: 1,
      validSeconds: 12 * 60 * 60
    }),
    difficile: Object.freeze({
      bonusSeconds: 24 * 60 * 60,
      multiplier: 1,
      validSeconds: 24 * 60 * 60
    })
  })
});

const NOTE_SUPPORTED_FORMATS = Object.freeze(['txt', 'md']);
const NOTES_DEFAULT_STYLE = Object.freeze({
  font: 'monospace',
  fontSize: 16,
  textColor: '#f5f5f5',
  backgroundColor: '#0b1021'
});

const ARCADE_HUB_CARD_REORDER_DELAY_MS = 1500;
const ARCADE_HUB_CARD_REORDER_MOVE_THRESHOLD = 16;
const ARCADE_HUB_CARD_REORDER_TOUCH_MOVE_THRESHOLD = 36;
const ARCADE_HUB_CARD_REORDER_PEN_MOVE_THRESHOLD = 24;
