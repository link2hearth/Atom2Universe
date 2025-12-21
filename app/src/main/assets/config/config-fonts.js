/**
 * Ajustements de taille propres aux polices.
 * - text.default / digits.default : multiplicateur de base appliqué si aucune entrée dédiée n'existe.
 * - text.overrides / digits.overrides : associe un identifiant de police (ex: "aquiline-two")
 *   à un multiplicateur (ex: 1.2 pour agrandir de +20 %, 2 pour doubler).
 */
const UI_FONT_SCALE_CONFIG = Object.freeze({
  text: Object.freeze({
    default: 1,
    overrides: Object.freeze({})
  }),
  digits: Object.freeze({
    default: 1,
    overrides: Object.freeze({})
  })
});
