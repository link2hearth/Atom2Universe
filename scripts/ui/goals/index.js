const TROPHY_I18N_BASES = Object.freeze({
  scale: Object.freeze([
    'scripts.appData.atomScale.trophies',
    'scripts.appData.trophies.presets',
    'config.trophies.presets'
  ]),
  standard: Object.freeze(['scripts.appData.trophies', 'config.trophies'])
});

function createTrophyTranslationBases(id) {
  if (typeof id !== 'string' || !id) {
    return [];
  }
  const bases = [];
  if (id.startsWith('scale')) {
    TROPHY_I18N_BASES.scale.forEach(base => bases.push(`${base}.${id}`));
  }
  TROPHY_I18N_BASES.standard.forEach(base => bases.push(`${base}.${id}`));
  return bases;
}

export function initializeGoalsUI(options) {
  if (!options || typeof options !== 'object') {
    throw new TypeError('initializeGoalsUI requires an options object.');
  }

  const {
    elements,
    trophyDefs,
    milestoneList = [],
    gameState,
    translateOrDefault,
    t,
    formatNumberLocalized,
    formatTrophyBonusValue,
    getUnlockedTrophySet,
    areAchievementsFeatureUnlocked
  } = options;

  if (!elements || typeof elements !== 'object') {
    throw new TypeError('initializeGoalsUI requires an elements object.');
  }
  if (!Array.isArray(trophyDefs)) {
    throw new TypeError('initializeGoalsUI requires trophyDefs to be an array.');
  }
  if (!gameState || typeof gameState !== 'object') {
    throw new TypeError('initializeGoalsUI requires a gameState reference.');
  }
  if (typeof translateOrDefault !== 'function') {
    throw new TypeError('initializeGoalsUI requires translateOrDefault to be a function.');
  }
  if (typeof formatNumberLocalized !== 'function') {
    throw new TypeError('initializeGoalsUI requires formatNumberLocalized to be a function.');
  }
  if (typeof formatTrophyBonusValue !== 'function') {
    throw new TypeError('initializeGoalsUI requires formatTrophyBonusValue to be a function.');
  }
  if (typeof getUnlockedTrophySet !== 'function') {
    throw new TypeError('initializeGoalsUI requires getUnlockedTrophySet to be a function.');
  }
  if (typeof areAchievementsFeatureUnlocked !== 'function') {
    throw new TypeError('initializeGoalsUI requires areAchievementsFeatureUnlocked to be a function.');
  }

  const trophyCards = new Map();

  function translateTrophyField(def, field, fallback, params) {
    const bases = createTrophyTranslationBases(def?.id);
    for (const base of bases) {
      const key = `${base}.${field}`;
      const translated = translateOrDefault(key, '', params);
      if (translated) {
        return translated;
      }
    }
    return fallback || '';
  }

  function resolveTrophyRewardParams(def) {
    if (!def || typeof def !== 'object' || !def.reward) {
      return null;
    }
    const params = {};
    const reward = def.reward;
    const bonusCandidates = [
      reward.trophyMultiplierAdd,
      reward.trophyMultiplierBonus,
      reward.trophyMultiplier,
      reward.trophyBonus
    ];
    const bonusValue = bonusCandidates.find(value => Number.isFinite(Number(value)));
    if (bonusValue != null && Number.isFinite(Number(bonusValue))) {
      const numeric = Number(bonusValue);
      params.bonus = formatTrophyBonusValue(numeric);
      params.total = formatTrophyBonusValue(1 + numeric);
    }
    let multiplierValue = null;
    if (typeof reward.multiplier === 'number') {
      multiplierValue = reward.multiplier;
    } else if (reward.multiplier && typeof reward.multiplier === 'object') {
      if (typeof reward.multiplier.toNumber === 'function') {
        multiplierValue = reward.multiplier.toNumber();
      } else {
        multiplierValue = reward.multiplier.global
          ?? reward.multiplier.all
          ?? reward.multiplier.total
          ?? reward.multiplier.perClick
          ?? reward.multiplier.click
          ?? reward.multiplier.perSecond
          ?? reward.multiplier.auto;
      }
    }
    if (multiplierValue != null && Number.isFinite(Number(multiplierValue))) {
      params.multiplier = formatNumberLocalized(Number(multiplierValue), {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      });
    }
    return Object.keys(params).length ? params : null;
  }

  function getTrophyDisplayTexts(def) {
    if (!def || typeof def !== 'object') {
      return { name: '', description: '', reward: '' };
    }
    const fallbackName = typeof def.name === 'string' ? def.name : '';
    const fallbackDescription = typeof def.description === 'string' ? def.description : '';
    const fallbackReward = typeof def.rewardText === 'string'
      ? def.rewardText
      : (def.reward && typeof def.reward.description === 'string' ? def.reward.description : '');

    let name = translateTrophyField(def, 'name', fallbackName);
    if (!name) {
      name = fallbackName;
    }

    let descriptionParams = null;
    const descriptionTarget = def.targetText || '';
    const descriptionFlavor = translateTrophyField(def, 'flavor', def.flavor || '');
    if (descriptionTarget || descriptionFlavor) {
      descriptionParams = {
        target: descriptionTarget,
        flavor: descriptionFlavor
      };
    }
    let description = '';
    if (descriptionParams) {
      description = translateOrDefault('scripts.appData.atomScale.trophies.description', '', descriptionParams)
        || translateOrDefault('config.trophies.description', '', descriptionParams);
    }
    if (!description) {
      description = translateTrophyField(def, 'description', '', descriptionParams);
    }
    if (!description) {
      description = fallbackDescription;
    }

    const rewardParams = resolveTrophyRewardParams(def);
    let rewardText = translateTrophyField(def, 'reward', '', rewardParams);
    if (!rewardText && rewardParams) {
      rewardText = translateOrDefault('scripts.appData.atomScale.trophies.reward', '', rewardParams)
        || translateOrDefault('config.trophies.reward.description', '', rewardParams);
    }
    if (!rewardText) {
      rewardText = fallbackReward;
    }

    return {
      name,
      description,
      reward: rewardText
    };
  }

  function buildGoalCard(def) {
    const card = document.createElement('article');
    card.className = 'goal-card';
    card.dataset.trophyId = def.id;
    card.setAttribute('role', 'listitem');
    card.classList.add('goal-card--locked');
    card.hidden = true;
    card.setAttribute('aria-hidden', 'true');

    const header = document.createElement('header');
    header.className = 'goal-card__header';

    const title = document.createElement('h3');
    const texts = getTrophyDisplayTexts(def);
    title.textContent = texts.name;
    title.className = 'goal-card__title';

    header.append(title);

    const description = document.createElement('p');
    description.className = 'goal-card__description';
    description.textContent = texts.description || '';

    card.append(header, description);

    const reward = document.createElement('p');
    reward.className = 'goal-card__reward';
    reward.textContent = texts.reward || '';
    reward.hidden = !texts.reward;
    card.appendChild(reward);

    return { root: card, title, description, reward };
  }

  function renderGoals() {
    if (!elements.goalsList) return;
    trophyCards.clear();
    elements.goalsList.innerHTML = '';
    if (!trophyDefs.length) {
      if (elements.goalsEmpty) {
        elements.goalsEmpty.hidden = false;
        elements.goalsEmpty.setAttribute('aria-hidden', 'false');
      }
      return;
    }
    const fragment = document.createDocumentFragment();
    trophyDefs.forEach(def => {
      const card = buildGoalCard(def);
      trophyCards.set(def.id, card);
      fragment.appendChild(card.root);
    });
    elements.goalsList.appendChild(fragment);
    refreshGoalCardTexts();
    updateGoalsUI();
  }

  function refreshGoalCardTexts() {
    if (!trophyCards.size) {
      return;
    }
    trophyDefs.forEach(def => {
      const card = trophyCards.get(def.id);
      if (!card) {
        return;
      }
      const texts = getTrophyDisplayTexts(def);
      if (card.title) {
        card.title.textContent = texts.name;
      }
      if (card.description) {
        card.description.textContent = texts.description || '';
      }
      if (card.reward) {
        if (texts.reward) {
          card.reward.textContent = texts.reward;
          card.reward.hidden = false;
        } else {
          card.reward.textContent = '';
          card.reward.hidden = true;
        }
      }
    });
  }

  function updateMilestone() {
    if (!elements.nextMilestone) return;
    for (const milestone of milestoneList) {
      if (gameState.lifetime.compare(milestone.amount) < 0) {
        elements.nextMilestone.textContent = milestone.text;
        return;
      }
    }
    const fallback = typeof t === 'function'
      ? t('scripts.app.shop.milestoneHint')
      : translateOrDefault('scripts.app.shop.milestoneHint', '');
    elements.nextMilestone.textContent = fallback;
  }

  function updateGoalsUI() {
    if (!elements.goalsList || !trophyCards.size) return;
    const unlockedSet = getUnlockedTrophySet();
    const featureUnlocked = areAchievementsFeatureUnlocked();
    let visibleCount = 0;
    trophyDefs.forEach(def => {
      const card = trophyCards.get(def.id);
      if (!card) return;
      const isUnlocked = unlockedSet.has(def.id);
      const shouldShow = featureUnlocked && isUnlocked;
      card.root.classList.toggle('goal-card--completed', isUnlocked);
      card.root.classList.toggle('goal-card--locked', !isUnlocked);
      card.root.hidden = !shouldShow;
      card.root.setAttribute('aria-hidden', String(!shouldShow));
      if (shouldShow) {
        visibleCount += 1;
      }
    });
    if (elements.goalsEmpty) {
      const hideEmpty = !featureUnlocked || visibleCount > 0;
      elements.goalsEmpty.hidden = hideEmpty;
      elements.goalsEmpty.setAttribute('aria-hidden', hideEmpty ? 'true' : 'false');
    }
  }

  return {
    render: renderGoals,
    refreshTexts: refreshGoalCardTexts,
    update: updateGoalsUI,
    updateMilestone,
    getDisplayTexts: getTrophyDisplayTexts
  };
}
