class LayeredNumber {
  constructor(input = 0) {
    this.sign = 0;
    this.layer = 0;
    this.mantissa = 0;
    this.exponent = 0;
    this.value = 0;

    let source = input;

    if (source instanceof LayeredNumber) {
      this.sign = source.sign;
      this.layer = source.layer;
      this.mantissa = source.mantissa;
      this.exponent = source.exponent;
      this.value = source.value;
      return;
    }

    if (typeof source === 'string') {
      const trimmed = source.trim();
      if (!trimmed) {
        return;
      }
      const numeric = Number(trimmed);
      if (Number.isFinite(numeric)) {
        source = numeric;
      } else {
        try {
          const parsed = JSON.parse(trimmed);
          if (typeof parsed === 'number') {
            source = parsed;
          } else if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
            source = parsed;
          } else {
            return;
          }
        } catch (error) {
          return;
        }
      }
    }

    if (typeof source === 'number') {
      this.fromNumber(source);
      return;
    }

    if (source && typeof source === 'object') {
      this.sign = source.sign ?? 0;
      this.layer = source.layer ?? 0;
      this.mantissa = source.mantissa ?? 0;
      this.exponent = source.exponent ?? 0;
      this.value = source.value ?? 0;
      this.normalize();
    }
  }

  setZeroState() {
    this.sign = 0;
    this.layer = 0;
    this.mantissa = 0;
    this.exponent = 0;
    this.value = 0;
    return this;
  }

  static zero() {
    return new LayeredNumber(0);
  }

  static one() {
    return new LayeredNumber(1);
  }

  static fromJSON(obj) {
    if (!obj) return LayeredNumber.zero();
    return new LayeredNumber(obj);
  }

  fromNumber(num) {
    if (!isFinite(num) || num === 0) {
      return this.setZeroState();
    }
    this.sign = num >= 0 ? 1 : -1;
    const abs = Math.abs(num);
    if (abs < 1e-12) {
      return this.setZeroState();
    }
    this.layer = 0;
    this.exponent = 0;
    this.mantissa = abs;
    this.normalize();
    return this;
  }

  static fromLayer0(mantissa, exponent = 0, sign = 1) {
    const inst = new LayeredNumber();
    if (mantissa === 0) {
      return inst.setZeroState();
    }
    inst.sign = sign >= 0 ? 1 : -1;
    inst.layer = 0;
    inst.mantissa = Math.abs(mantissa);
    inst.exponent = exponent;
    return inst.normalize();
  }

  static fromLayer1(value, sign = 1) {
    const inst = new LayeredNumber();
    if (value <= -Infinity) {
      return LayeredNumber.zero();
    }
    inst.sign = sign >= 0 ? 1 : -1;
    inst.layer = 1;
    inst.value = value;
    return inst.normalize();
  }

  clone() {
    return new LayeredNumber(this);
  }

  normalize() {
    if (this.sign === 0 || this.mantissa === 0 || !isFinite(this.mantissa)) {
      if (this.layer === 0) {
        if (this.mantissa === 0 || !isFinite(this.mantissa)) {
          this.sign = 0;
          this.mantissa = 0;
          this.exponent = 0;
        }
      }
    }

    if (this.sign === 0) {
      return this.setZeroState();
    }

    if (this.layer === 0) {
      if (this.mantissa === 0) {
        this.sign = 0;
        this.exponent = 0;
        return this;
      }
      let mant = this.mantissa;
      let exp = this.exponent;
      const sign = this.sign >= 0 ? 1 : -1;
      if (!isFinite(mant)) {
        this.layer = 1;
        this.value = Math.log10(Math.abs(mant)) + exp;
        this.mantissa = 0;
        this.exponent = 0;
        this.sign = sign;
        return this;
      }
      while (mant >= 10) {
        mant /= 10;
        exp += 1;
      }
      while (mant < 1 && mant > 0) {
        mant *= 10;
        exp -= 1;
      }
      this.mantissa = mant;
      this.exponent = exp;
      this.sign = sign;
      if (exp >= LayeredNumber.LAYER1_THRESHOLD) {
        const log10 = Math.log10(this.mantissa) + this.exponent;
        this.layer = 1;
        this.value = log10;
        this.mantissa = 0;
        this.exponent = 0;
      }
    } else if (this.layer === 1) {
      if (!isFinite(this.value)) {
        this.value = Number.POSITIVE_INFINITY;
      }
      if (this.value < LayeredNumber.LAYER1_DOWN) {
        const log10 = this.value;
        const exp = Math.floor(log10);
        const mant = Math.pow(10, log10 - exp);
        this.layer = 0;
        this.mantissa = mant;
        this.exponent = exp;
        this.value = 0;
        this.normalize();
      }
    }
    return this;
  }

  toLayer(targetLayer) {
    if (targetLayer === this.layer) {
      return this.clone();
    }
    if (targetLayer < 0) targetLayer = 0;
    let result = this.clone();
    while (result.layer < targetLayer) {
      if (result.layer === 0) {
        const log10 = Math.log10(result.mantissa) + result.exponent;
        result.layer = 1;
        result.value = log10;
        result.mantissa = 0;
        result.exponent = 0;
      } else {
        // Higher layers not implemented, approximate by staying in current layer
        break;
      }
    }
    while (result.layer > targetLayer) {
      if (result.layer === 1) {
        const log10 = result.value;
        const exp = Math.floor(log10);
        const mant = Math.pow(10, log10 - exp);
        result.layer = 0;
        result.mantissa = mant;
        result.exponent = exp;
        result.value = 0;
        result.normalize();
      } else {
        break;
      }
    }
    return result;
  }

  isZero() {
    return this.sign === 0;
  }

  compare(other) {
    const b = LayeredNumber.cast(other);
    if (this.sign === 0 && b.sign === 0) return 0;
    if (this.sign >= 0 && b.sign < 0) return 1;
    if (this.sign < 0 && b.sign >= 0) return -1;
    if (this.sign === 0) return -b.sign;
    if (b.sign === 0) return this.sign;

    const sign = this.sign;
    if (this.layer !== b.layer) {
      return (this.layer > b.layer ? 1 : -1) * sign;
    }
    if (this.layer === 0) {
      if (this.exponent !== b.exponent) {
        return (this.exponent > b.exponent ? 1 : -1) * sign;
      }
      if (this.mantissa !== b.mantissa) {
        return (this.mantissa > b.mantissa ? 1 : -1) * sign;
      }
      return 0;
    }
    if (this.value !== b.value) {
      return (this.value > b.value ? 1 : -1) * sign;
    }
    return 0;
  }

  add(other) {
    const b = LayeredNumber.cast(other);
    if (this.sign === 0) return b.clone();
    if (b.sign === 0) return this.clone();

    if (this.sign !== b.sign) {
      if (this.sign < 0) {
        return b.subtract(this.negate());
      }
      return this.subtract(b.negate());
    }

    if (this.layer === b.layer) {
      if (this.layer === 0) {
        if (this.exponent === b.exponent) {
          return LayeredNumber.fromLayer0(this.mantissa + b.mantissa, this.exponent, this.sign).normalize();
        }
        if (this.exponent > b.exponent) {
          const diff = this.exponent - b.exponent;
          if (diff > LayeredNumber.LOG_DIFF_LIMIT) return this.clone();
          const mantissa = this.mantissa + b.mantissa / Math.pow(10, diff);
          return LayeredNumber.fromLayer0(mantissa, this.exponent, this.sign).normalize();
        }
        const diff = b.exponent - this.exponent;
        if (diff > LayeredNumber.LOG_DIFF_LIMIT) return b.clone();
        const mantissa = b.mantissa + this.mantissa / Math.pow(10, diff);
        return LayeredNumber.fromLayer0(mantissa, b.exponent, this.sign).normalize();
      }

      const max = Math.max(this.value, b.value);
      const min = Math.min(this.value, b.value);
      if (max - min > LayeredNumber.LOG_DIFF_LIMIT) {
        return LayeredNumber.fromLayer1(max, this.sign);
      }
      const resultValue = max + Math.log10(1 + Math.pow(10, min - max));
      return LayeredNumber.fromLayer1(resultValue, this.sign).normalize();
    }

    if (this.layer > b.layer) {
      const lifted = b.toLayer(this.layer);
      if (lifted.layer !== this.layer) {
        return this.clone();
      }
      return this.add(lifted);
    }
    const lifted = this.toLayer(b.layer);
    if (lifted.layer !== b.layer) {
      return b.clone();
    }
    return lifted.add(b);
  }

  subtract(other) {
    const b = LayeredNumber.cast(other);
    if (b.sign === 0) return this.clone();
    if (this.sign === 0) return b.negate();

    if (this.sign !== b.sign) {
      return this.add(b.negate());
    }

    const cmp = this.compare(b);
    if (cmp === 0) return LayeredNumber.zero();
    if (cmp < 0) {
      return b.subtract(this).negate();
    }

    if (this.layer === b.layer) {
      if (this.layer === 0) {
        if (this.exponent === b.exponent) {
          const mantissa = this.mantissa - b.mantissa;
          if (mantissa <= LayeredNumber.EPSILON) return LayeredNumber.zero();
          return LayeredNumber.fromLayer0(mantissa, this.exponent, this.sign).normalize();
        }
        if (this.exponent > b.exponent) {
          const diff = this.exponent - b.exponent;
          if (diff > LayeredNumber.LOG_DIFF_LIMIT) return this.clone();
          const mantissa = this.mantissa - b.mantissa / Math.pow(10, diff);
          if (mantissa <= LayeredNumber.EPSILON) return LayeredNumber.zero();
          return LayeredNumber.fromLayer0(mantissa, this.exponent, this.sign).normalize();
        }
      } else {
        const max = this.value;
        const min = b.value;
        if (max - min > LayeredNumber.LOG_DIFF_LIMIT) {
          return this.clone();
        }
        const diffPow = Math.pow(10, min - max);
        const inner = 1 - diffPow;
        if (inner <= LayeredNumber.EPSILON) {
          return LayeredNumber.zero();
        }
        const resultValue = max + Math.log10(inner);
        return LayeredNumber.fromLayer1(resultValue, this.sign).normalize();
      }
    }

    if (this.layer > b.layer) {
      const lifted = b.toLayer(this.layer);
      if (lifted.layer !== this.layer) {
        return this.clone();
      }
      return this.subtract(lifted);
    }
    const lifted = this.toLayer(b.layer);
    if (lifted.layer !== b.layer) {
      return b.negate().subtract(this.negate());
    }
    return lifted.subtract(b);
  }

  multiply(other) {
    const b = LayeredNumber.cast(other);
    if (this.sign === 0 || b.sign === 0) return LayeredNumber.zero();

    const sign = this.sign * b.sign;
    if (this.layer === 0 && b.layer === 0) {
      const mantissa = this.mantissa * b.mantissa;
      const exponent = this.exponent + b.exponent;
      return LayeredNumber.fromLayer0(mantissa, exponent, sign).normalize();
    }
    const highLayer = Math.max(this.layer, b.layer);
    const aLift = this.toLayer(highLayer);
    const bLift = b.toLayer(highLayer);
    if (highLayer === 1) {
      const value = aLift.value + bLift.value;
      return LayeredNumber.fromLayer1(value, sign).normalize();
    }
    // fallback
    const resultValue = Math.log10(this.toNumber()) + Math.log10(b.toNumber());
    return LayeredNumber.fromLayer1(resultValue, sign).normalize();
  }

  multiplyNumber(num) {
    if (typeof num !== 'number') return this.multiply(num);
    if (num === 0) return LayeredNumber.zero();
    if (this.sign === 0) return LayeredNumber.zero();
    if (this.layer === 0) {
      const mantissa = this.mantissa * Math.abs(num);
      const sign = num >= 0 ? this.sign : -this.sign;
      return LayeredNumber.fromLayer0(mantissa, this.exponent, sign).normalize();
    }
    const add = Math.log10(Math.abs(num));
    const sign = num >= 0 ? this.sign : -this.sign;
    return LayeredNumber.fromLayer1(this.value + add, sign).normalize();
  }

  divide(other) {
    const b = LayeredNumber.cast(other);
    if (b.isZero()) return LayeredNumber.zero();
    if (this.isZero()) return LayeredNumber.zero();
    const sign = this.sign * b.sign;
    if (this.layer === 0 && b.layer === 0) {
      const mantissa = this.mantissa / b.mantissa;
      const exponent = this.exponent - b.exponent;
      return LayeredNumber.fromLayer0(mantissa, exponent, sign).normalize();
    }
    const highLayer = Math.max(this.layer, b.layer);
    const aLift = this.toLayer(highLayer);
    const bLift = b.toLayer(highLayer);
    if (highLayer === 1) {
      const value = aLift.value - bLift.value;
      return LayeredNumber.fromLayer1(value, sign).normalize();
    }
    const resultValue = Math.log10(this.toNumber()) - Math.log10(b.toNumber());
    return LayeredNumber.fromLayer1(resultValue, sign).normalize();
  }

  pow(power) {
    if (typeof power !== 'number') return this.pow(power.toNumber());
    if (power === 0) return LayeredNumber.one();
    if (this.isZero()) return LayeredNumber.zero();

    const exponentIsInteger = Number.isFinite(power) && Number.isInteger(power);
    const isEvenInteger = exponentIsInteger && Math.abs(power % 2) === 0;
    const resultSign = this.sign >= 0 ? 1 : (isEvenInteger ? 1 : -1);

    if (this.layer === 0) {
      const mantissa = Math.pow(this.mantissa, power);
      const exponent = this.exponent * power;
      return LayeredNumber.fromLayer0(mantissa, exponent, resultSign).normalize();
    }
    return LayeredNumber.fromLayer1(this.value * power, resultSign).normalize();
  }

  negate() {
    const result = this.clone();
    result.sign *= -1;
    return result;
  }

  toNumber() {
    if (this.sign === 0) return 0;
    if (this.layer === 0) {
      return this.sign * this.mantissa * Math.pow(10, this.exponent);
    }
    const approx = this.value;
    if (approx > 308) {
      return this.sign * Number.POSITIVE_INFINITY;
    }
    return this.sign * Math.pow(10, approx);
  }

  toString() {
    if (this.sign === 0) return '0';
    if (this.layer === 0) {
      const value = this.mantissa * Math.pow(10, this.exponent);
      if (Math.abs(this.exponent) < 6) {
        const numeric = this.sign * value;
        const absolute = Math.abs(numeric);
        const options = absolute >= 1
          ? { maximumFractionDigits: 0, minimumFractionDigits: 0 }
          : { maximumFractionDigits: 2, minimumFractionDigits: 0 };
        const formatted = LayeredNumber.formatLocalizedNumber(numeric, options);
        if (formatted && formatted.length > 0) {
          return formatted;
        }
        if (absolute >= 1) {
          const truncated = Math.trunc(numeric);
          return `${truncated}`;
        }
        return `${numeric}`;
      }
      const mantissaValue = this.mantissa;
      const approxInteger = Math.abs(mantissaValue - Math.round(mantissaValue)) < 1e-9;
      const prefix = this.sign < 0 ? '-' : '';
      let mantissaText;
      if (approxInteger) {
        mantissaText = `${Math.round(mantissaValue)}`;
      } else {
        const normalized = Number.parseFloat(mantissaValue.toFixed(2));
        mantissaText = Number.isFinite(normalized)
          ? normalized.toString()
          : mantissaValue.toString();
      }
      return `${prefix}${mantissaText}e${this.exponent}`;
    }
    const exponentText = LayeredNumber.formatExponent(this.value);
    const prefix = this.sign < 0 ? '-' : '';
    return `${prefix}10^${exponentText}`;
  }

  format() {
    return this.toString();
  }

  toJSON() {
    return {
      sign: this.sign,
      layer: this.layer,
      mantissa: this.mantissa,
      exponent: this.exponent,
      value: this.value
    };
  }

  static cast(value) {
    if (value instanceof LayeredNumber) return value;
    if (typeof value === 'number') return new LayeredNumber(value);
    return new LayeredNumber(value);
  }

  static formatLocalizedNumber(value, options) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return '';
    }

    const api = typeof globalThis === 'object' && globalThis !== null ? globalThis.i18n : null;
    if (api && typeof api.formatNumber === 'function') {
      try {
        const formatted = api.formatNumber(numeric, options);
        if (typeof formatted === 'string' && formatted.length > 0) {
          return formatted;
        }
      } catch (error) {
        // Ignore formatting errors and fall back to locale-based formatting.
      }
    }

    let locale = 'fr-FR';
    if (api && typeof api.getCurrentLocale === 'function') {
      try {
        const resolved = api.getCurrentLocale();
        if (typeof resolved === 'string' && resolved) {
          locale = resolved;
        }
      } catch (error) {
        // Ignore locale resolution errors and keep default locale.
      }
    }

    try {
      return numeric.toLocaleString(locale, options);
    } catch (error) {
      try {
        return numeric.toLocaleString('fr-FR', options);
      } catch (fallbackError) {
        return `${numeric}`;
      }
    }
  }

  static formatExponent(value) {
    if (!isFinite(value)) return '∞';
    const abs = Math.abs(value);
    if (abs < 1e4) {
      if (abs >= 100) return value.toFixed(0);
      if (abs >= 10) return value.toFixed(1);
      return value.toFixed(2);
    }
    return value.toExponential(2).replace('+', '').replace('e0', '');
  }
}

const DEFAULT_LAYER1_THRESHOLD = 1e6;
const DEFAULT_LAYER1_DOWNSHIFT = 5;
const DEFAULT_LOG_DIFF_LIMIT = 15;
const DEFAULT_EPSILON = 1e-12;

LayeredNumber.LAYER1_THRESHOLD = Number.isFinite(LayeredNumber.LAYER1_THRESHOLD)
  ? LayeredNumber.LAYER1_THRESHOLD
  : DEFAULT_LAYER1_THRESHOLD;
LayeredNumber.LAYER1_DOWN = Number.isFinite(LayeredNumber.LAYER1_DOWN)
  ? LayeredNumber.LAYER1_DOWN
  : DEFAULT_LAYER1_DOWNSHIFT;
LayeredNumber.LOG_DIFF_LIMIT = Number.isFinite(LayeredNumber.LOG_DIFF_LIMIT)
  ? LayeredNumber.LOG_DIFF_LIMIT
  : DEFAULT_LOG_DIFF_LIMIT;
LayeredNumber.EPSILON = Number.isFinite(LayeredNumber.EPSILON)
  ? LayeredNumber.EPSILON
  : DEFAULT_EPSILON;

if (typeof globalThis !== 'undefined') {
  globalThis.LayeredNumber = LayeredNumber;
}

