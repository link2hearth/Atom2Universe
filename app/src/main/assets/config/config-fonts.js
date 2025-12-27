/**
 * Ajustements de taille propres aux polices.
 * - text.default / digits.default : multiplicateur de base appliqué si aucune entrée dédiée n'existe.
 * - text.overrides / digits.overrides : associe un identifiant de police (ex: "aquiline-two")
 *   à un multiplicateur (ex: 1.2 pour agrandir de +20 %, 2 pour doubler).
 */
const UI_FONT_SCALE_CONFIG = Object.freeze({
  text: Object.freeze({
    default: 1,
    overrides: Object.freeze({
      orbitron: 1,
      alien: 1,
      chomsky: 1.8,
      'coffee-spark': 1.4,
      'creativo-regular': 1.2,
      'darling-coffee': 1.2,
      'designer-notes': 1.5,
      'digittech16-regular': 1,
      'little-days': 1.8,
      'roboto-condensed': 1.5,
      'slow-play': 1.5,
      'super-chiby': 1.5,
      'super-croissant': 1.5,
      'super-mario-64': 1.5,
      'super-meatball': 1.4,
      supermario256: 1.4,
      alamain1: 1,
      'dealerplate-california': 1.4,
      'to-japan': 1.1,
      'science-gothic': 1.2
    })
  }),
  digits: Object.freeze({
    default: 1.1,
    overrides: Object.freeze({
      orbitron: 1,
      alien: 1.2,
      chomsky: 1,
      'coffee-spark': 1,
      'creativo-regular': 1,
      'darling-coffee': 1,
      'designer-notes': 1,
      'digittech16-regular': 1,
      'little-days': 1,
      'roboto-condensed': 1,
      'slow-play': 1,
      'super-chiby': 1,
      'super-croissant': 1,
      'super-mario-64': 1,
      'super-meatball': 1,
      supermario256: 1,
      alamain1: 1,
      'dealerplate-california': 1,
      'to-japan': 1,
      'science-gothic': 1
    })
  })
});
