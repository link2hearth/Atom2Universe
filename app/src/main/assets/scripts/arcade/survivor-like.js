(() => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  const root = document.querySelector('[data-survivor-like-root]');
  if (!root) {
    return;
  }

  const GAME_ID = 'survivorLike';
  const SAVE_CORE_KEY = typeof SAVE_CORE_KEYS !== 'undefined' && SAVE_CORE_KEYS?.arcadeGamePrefix
    ? `${SAVE_CORE_KEYS.arcadeGamePrefix}${GAME_ID}`
    : 'atom2univers.arcade.survivorLike';
  const HERO_IDS = Object.freeze(['Mage', 'Robot', 'Ghost', 'ChatNoir', 'Skeleton', 'Vortex']);
  const CONFIG_PATH = 'config/arcade/survivor-like.json';

  // Configuration des sprites d'ennemis (sprite sheets 256x256, 4x4 grille, chaque sprite 64x64)
  // Ligne 0: marche vers BAS, Ligne 1: GAUCHE, Ligne 2: DROITE, Ligne 3: HAUT
  // Animation: 4 frames à 250ms chaque = 1 seconde par cycle
  const ENEMY_SPRITE_CONFIG = {
    basePath: 'Assets/sprites/Survive/ennemis/',
    spriteSize: 64,      // Taille d'un sprite individuel
    sheetSize: 256,      // Taille du sprite sheet complet
    gridSize: 4,         // 4x4 grille
    frameDuration: 250,  // ms par frame
    // Sprites disponibles: 1.png à 916.png ET 1s.png à 916s.png (~1832 sprites)
    maxSpriteNumber: 916,
    hasVariants: true    // true = fichiers X.png ET Xs.png existent
  };

  // Directions pour les animations (row index dans le sprite sheet)
  const SPRITE_DIRECTION = {
    DOWN: 0,
    LEFT: 1,
    RIGHT: 2,
    UP: 3
  };

  const DEFAULT_CONFIG = Object.freeze({
    player: {
      baseSpeed: 180,
      baseHealth: 100,
      collisionRadius: 16,
      iframeDuration: 500,
      weaponSlots: 5,
      startWeapons: ['projectile']
    },
    weapons: {
      projectile: {
        damage: 10,
        fireRate: 2,
        projectileSpeed: 300,
        projectileCount: 1,
        projectileSize: 4,
        projectileLifetime: 2000,
        penetration: 0
      },
      laser: {
        damage: 8,
        fireRate: 1,
        range: 250,
        width: 6,
        duration: 400,
        color: '#ff3366'
      },
      aura: {
        damage: 3,
        radius: 120,
        slowFactor: 0.7,
        tickRate: 10,
        color: '#4af0ff'
      },
      bouncing: {
        damage: 25,
        fireRate: 0.8,
        speed: 220,
        maxBounces: 1,
        size: 7,
        homingStrength: 1.2
      },
      bomb: {
        damage: 50,
        fireRate: 0.5,
        speed: 120,
        explosionRadius: 100,
        size: 8,
        color: '#ff8800'
      }
    },
    weaponUnlocks: {
      projectile: 1,
      laser: 3,
      aura: 6,
      bouncing: 10,
      bomb: 15
    },
    enemies: {
      zombie: {
        health: 30,
        speed: 80,
        damage: 10,
        xp: 5,
        radius: 14,
        color: '#4a9d4a',
        spawnWeight: 1
      },
      fast: {
        health: 15,
        speed: 140,
        damage: 5,
        xp: 3,
        radius: 12,
        color: '#d4593a',
        spawnWeight: 0.7
      },
      miniBoss: {
        health: 750,
        speed: 60,
        damage: 25,
        xp: 500,
        radius: 32,
        color: '#ff4444',
        spawnWeight: 0
      }
    },
    spawning: {
      initialInterval: 1500,
      minInterval: 300,
      intervalDecayPerWave: 100,
      enemiesPerWave: 10,
      waveInterval: 30000,
      baseMaxEnemies: 30,
      maxEnemiesPerWave: 10,
      absoluteMaxEnemies: 150,
      spawnDistance: 800,
      healthScaling: {
        enabled: true,
        perWave: 0.1,
        maxMultiplier: 3
      },
      groupSpawn: {
        enabled: true,
        chance: 0.15,
        minWaveToActivate: 2,
        minSize: 3,
        maxSize: 8,
        sizeIncreasePerWave: 2,
        spreadRadius: 150
      },
      bossSpawn: {
        enabled: true,
        spawnIntervalSeconds: 90,
        preventNormalSpawnDuration: 5000
      }
    },
    progression: {
      xpPerLevel: 100,
      xpScaling: 'exponential'
    },
    rewards: {
      gacha: {
        survivalIntervalSeconds: 60,
        ticketAmount: 1
      }
    }
  });

  const elements = {
    page: document.getElementById('survivorLike'),
    canvas: document.getElementById('survivorLikeCanvas'),
    healthFill: document.getElementById('survivorLikeHealthFill'),
    healthText: document.getElementById('survivorLikeHealthText'),
    xpFill: document.getElementById('survivorLikeXpFill'),
    levelText: document.getElementById('survivorLikeLevel'),
    timeValue: document.getElementById('survivorLikeTimeValue'),
    waveValue: document.getElementById('survivorLikeWaveValue'),
    killsValue: document.getElementById('survivorLikeKillsValue'),
    joystickZone: document.getElementById('survivorLikeJoystickZone'),
    joystick: document.getElementById('survivorLikeJoystick'),
    joystickInner: document.getElementById('survivorLikeJoystickInner'),
    startOverlay: document.getElementById('survivorLikeStartOverlay'),
    levelUpOverlay: document.getElementById('survivorLikeLevelUpOverlay'),
    gameOverOverlay: document.getElementById('survivorLikeGameOverOverlay'),
    pauseOverlay: document.getElementById('survivorLikePauseOverlay'),
    startButton: document.getElementById('survivorLikeStartButton'),
    continueButton: document.getElementById('survivorLikeContinueButton'),
    bestTimeValue: document.getElementById('survivorLikeBestTimeValue'),
    bestLevelValue: document.getElementById('survivorLikeBestLevelValue'),
    upgradeChoices: document.getElementById('survivorLikeUpgradeChoices'),
    gameOverTime: document.getElementById('survivorLikeGameOverTime'),
    gameOverLevel: document.getElementById('survivorLikeGameOverLevel'),
    gameOverKills: document.getElementById('survivorLikeGameOverKills'),
    gameOverTickets: document.getElementById('survivorLikeGameOverTickets'),
    restartButton: document.getElementById('survivorLikeRestartButton'),
    quitButton: document.getElementById('survivorLikeQuitButton'),
    pauseButton: document.getElementById('survivorLikePauseButton'),
    resumeButton: document.getElementById('survivorLikeResumeButton'),
    pauseQuitButton: document.getElementById('survivorLikePauseQuitButton'),
    exitButton: document.getElementById('survivorLikeExitButton'),
    exitButtonLevelUp: document.getElementById('survivorLikeExitButtonLevelUp'),
    exitButtonGameOver: document.getElementById('survivorLikeExitButtonGameOver'),
    exitButtonPause: document.getElementById('survivorLikeExitButtonPause'),
    pauseTime: document.getElementById('survivorLikePauseTime'),
    pauseWave: document.getElementById('survivorLikePauseWave'),
    pauseLevel: document.getElementById('survivorLikePauseLevel'),
    pauseKills: document.getElementById('survivorLikePauseKills'),
    pausePlayerStats: document.getElementById('survivorLikePausePlayerStats'),
    pauseWeapons: document.getElementById('survivorLikePauseWeapons'),
    heroCards: document.querySelectorAll('.survivor-like__hero-card')
  };

  const heroRecordElements = HERO_IDS.reduce((acc, hero) => {
    acc[hero] = {
      time: document.getElementById(`survivorLikeHero${hero}Time`),
      kills: document.getElementById(`survivorLikeHero${hero}Kills`)
    };
    return acc;
  }, {});

  const GameState = {
    MENU: 'MENU',
    PLAYING: 'PLAYING',
    PAUSED: 'PAUSED',
    LEVELUP: 'LEVELUP',
    GAMEOVER: 'GAMEOVER'
  };

  const state = {
    config: DEFAULT_CONFIG,
    gameState: GameState.MENU,
    running: false,
    paused: false,
    lastTime: 0,
    elapsed: 0,
    bestTime: 0,
    bestLevel: 1,
    bestTimeByHero: {},
    bestKillsByHero: {},
    selectedHero: null, // Selected hero from start screen (null = random)
    pendingLevelUpChoices: null,
    player: {
      x: 0,
      y: 0,
      speed: 120,
      health: 100,
      maxHealth: 100,
      xp: 0,
      level: 1,
      xpForNextLevel: 50,
      weapons: [],
      weaponSlots: 5,
      iframes: 0,
      skinIndex: 0,
      // Nouvelle structure d'améliorations
      upgrades: {
        global: {
          speed: 0,           // 0-10 (après 10, plus disponible)
          maxHealth: 0,       // illimité
          lifeSteal: 0,       // 0-10 (BONUS SPÉCIAL)
          regeneration: 0,    // 0-15 (BONUS SPÉCIAL)
          armor: 0,           // 0-10 (BONUS SPÉCIAL)
          xpBonus: 0,         // 0-20
          critChance: 0,      // 0-10 (BONUS SPÉCIAL)
          revives: 0          // 0-3 (BONUS SPÉCIAL)
        },
        weapons: {
          projectile: {
            projectileCount: 0,     // 0-10
            damagePercent: 0,       // 0-20
            fireRatePercent: 0,     // 0-15
            speedPercent: 0,        // 0-10
            penetration: 0,         // 0-5
            lifetimePercent: 0,     // 0-8
            sizePercent: 0          // 0-5
          },
          laser: {
            rangePercent: 0,        // 0-15
            damagePercent: 0,       // 0-20
            widthPercent: 0,        // 0-10
            durationPercent: 0,     // 0-12
            fireRatePercent: 0,     // 0-10
            multiLaser: 0           // 0-3
          },
          aura: {
            radiusPercent: 0,       // 0-20
            damagePercent: 0,       // 0-20
            slowPercent: 0,         // 0-10
            tickRatePercent: 0,     // 0-12
            critChance: 0           // 0-5
          },
          bouncing: {
            maxBounces: 0,          // 0-15
            damagePercent: 0,       // 0-20
            speedPercent: 0,        // 0-10
            fireRatePercent: 0,     // 0-12
            sizePercent: 0,         // 0-8
            multiShot: 0            // 0-5
          },
          bomb: {
            explosionRadiusPercent: 0,  // 0-15
            damagePercent: 0,           // 0-20
            fireRatePercent: 0,         // 0-10
            speedPercent: 0,            // 0-8
            multiShot: 0,               // 0-4
            burnDamage: 0               // 0-5
          },
          blackhole: {
            pullRadiusPercent: 0,       // 0-15
            damagePercent: 0,           // 0-20
            durationPercent: 0,         // 0-12
            tickRatePercent: 0,         // 0-10
            fireRatePercent: 0,         // 0-10
            multiShot: 0                // 0-3
          }
        }
      },
      // Compteur de types de bonus spéciaux actifs
      specialBonusesActive: []  // Max 2 types différents
    },
    camera: {
      x: 0,
      y: 0
    },
    input: {
      joystickActive: false,
      joystickStartX: 0,
      joystickStartY: 0,
      joystickDeltaX: 0,
      joystickDeltaY: 0,
      touchId: null
    },
    projectiles: [],
    lasers: [],
    bouncingProjectiles: [],
    bombs: [],
    explosions: [],
    blackholes: [],
    enemies: [],
    wave: 1,
    kills: 0,
    nextSpawnTime: 0,
    nextWaveTime: 30000,
    spawnInterval: 2000,
    lastGachaTime: 0,
    nextBossTime: 0,
    bossSpawnBlocked: false,
    bossSpawnBlockedUntil: 0,
    // Sprites d'ennemis : un sprite différent par type pour chaque vague
    waveEnemySprites: {
      zombie: null,     // { filename: '42s.png', image: Image, loaded: false }
      fast: null,       // { filename: '789.png', image: Image, loaded: false }
      miniBoss: null    // { filename: '1s.png', image: Image, loaded: false }
    }
  };

  let ctx = null;
  let canvasWidth = 0;
  let canvasHeight = 0;
  let spriteSheet = null;
  let spriteSheetLoaded = false;

  // Background system
  const BACKGROUND_CONFIG = {
    images: ['Background1.png', 'Background2.png', 'Background3.png'],
    basePath: 'Assets/sprites/Survive/Background/',
    tileSize: 512,
    sheetSize: 1024
  };
  let backgroundImages = []; // Array of loaded Image objects
  let backgroundsLoaded = false;
  let currentBackgroundTile = null; // { imageIndex, tileX, tileY }

  // Sprite sheet coordinates
  const SPRITE_COLS = [
    { x: 0, w: 79 },
    { x: 79, w: 85 },
    { x: 164, w: 76 },
    { x: 240, w: 85 }
  ];

  const SPRITE_ROWS = {
    zombie: { y: 0, h: 67 },
    fast: { y: 67, h: 77 },
    player: { y: 144, h: 73 },
    boss: { y: 217, h: 79 }
  };

  function translate(key, fallback) {
    return window.t ? window.t(key) : fallback;
  }

  // Deep merge helper function
  function deepMerge(target, source) {
    const result = { ...target };
    for (const key in source) {
      if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
        result[key] = deepMerge(target[key] || {}, source[key]);
      } else {
        result[key] = source[key];
      }
    }
    return result;
  }

  async function loadConfig() {
    // First try to load from JSON file
    try {
      const response = await fetch(CONFIG_PATH);
      if (response.ok) {
        const jsonConfig = await response.json();
        // Deep merge: DEFAULT_CONFIG <- jsonConfig
        state.config = deepMerge(DEFAULT_CONFIG, jsonConfig);
      }
    } catch (e) {
      console.warn('Could not load survivor-like.json, using defaults:', e);
    }

    // Also check for global config override (for backwards compatibility)
    const globalConfig = window.GAME_CONFIG?.arcade?.survivorLike;
    if (globalConfig && typeof globalConfig === 'object') {
      state.config = deepMerge(state.config, globalConfig);
    }

    // Merge upgrade config values into UPGRADE_CATALOG
    mergeUpgradeConfig();
  }

  function mergeUpgradeConfig() {
    const configUpgrades = state.config.upgrades;
    if (!configUpgrades) return;

    // Merge global upgrades
    if (configUpgrades.global) {
      for (const [upgradeId, configValues] of Object.entries(configUpgrades.global)) {
        if (UPGRADE_CATALOG.global[upgradeId]) {
          // Merge numeric values from config, keep labels/descriptions/icons from JS
          if (configValues.maxLevel !== undefined) {
            UPGRADE_CATALOG.global[upgradeId].maxLevel = configValues.maxLevel === -1 ? Infinity : configValues.maxLevel;
          }
          if (configValues.effect !== undefined) {
            UPGRADE_CATALOG.global[upgradeId].effect = configValues.effect;
          }
        }
      }
    }

    // Merge weapon upgrades
    if (configUpgrades.weapons) {
      for (const [weaponType, weaponUpgrades] of Object.entries(configUpgrades.weapons)) {
        if (UPGRADE_CATALOG.weapons[weaponType]) {
          for (const [upgradeId, configValues] of Object.entries(weaponUpgrades)) {
            if (UPGRADE_CATALOG.weapons[weaponType][upgradeId]) {
              // Merge numeric values from config
              if (configValues.maxLevel !== undefined) {
                UPGRADE_CATALOG.weapons[weaponType][upgradeId].maxLevel = configValues.maxLevel === -1 ? Infinity : configValues.maxLevel;
              }
              if (configValues.effect !== undefined) {
                UPGRADE_CATALOG.weapons[weaponType][upgradeId].effect = configValues.effect;
              }
            }
          }
        }
      }
    }
  }

  function loadSpriteSheet() {
    spriteSheet = new Image();
    spriteSheet.onload = () => {
      spriteSheetLoaded = true;
    };
    spriteSheet.onerror = () => {
      console.error('Failed to load sprite sheet');
      spriteSheetLoaded = false;
    };
    spriteSheet.src = 'Assets/sprites/ennemis-survive.png';
  }

  // ============================================
  // BACKGROUND SYSTEM
  // ============================================

  function loadBackgroundImages() {
    backgroundImages = [];
    let loadedCount = 0;

    BACKGROUND_CONFIG.images.forEach((filename, index) => {
      const img = new Image();
      img.onload = () => {
        loadedCount++;
        if (loadedCount === BACKGROUND_CONFIG.images.length) {
          backgroundsLoaded = true;
        }
      };
      img.onerror = () => {
        console.error(`Failed to load background: ${filename}`);
      };
      img.src = BACKGROUND_CONFIG.basePath + filename;
      backgroundImages[index] = img;
    });
  }

  function selectRandomBackgroundTile() {
    // Choose random image (0, 1, or 2)
    const imageIndex = Math.floor(Math.random() * BACKGROUND_CONFIG.images.length);
    // Choose random tile position (0 or 1 for X and Y)
    const tileX = Math.floor(Math.random() * 2); // 0 = left, 1 = right
    const tileY = Math.floor(Math.random() * 2); // 0 = top, 1 = bottom

    currentBackgroundTile = { imageIndex, tileX, tileY };
  }

  function renderBackground() {
    if (!backgroundsLoaded || !currentBackgroundTile) return;

    const img = backgroundImages[currentBackgroundTile.imageIndex];
    if (!img || !img.complete) return;

    const tileSize = BACKGROUND_CONFIG.tileSize;
    // Source coordinates in the sprite sheet
    const srcX = currentBackgroundTile.tileX * tileSize;
    const srcY = currentBackgroundTile.tileY * tileSize;

    // Calculate how many tiles we need to cover the viewport (plus margin for scrolling)
    const margin = tileSize;
    const startX = Math.floor((state.camera.x - canvasWidth / 2 - margin) / tileSize) * tileSize;
    const startY = Math.floor((state.camera.y - canvasHeight / 2 - margin) / tileSize) * tileSize;
    const endX = state.camera.x + canvasWidth / 2 + margin;
    const endY = state.camera.y + canvasHeight / 2 + margin;

    // Draw tiled background
    for (let x = startX; x < endX; x += tileSize) {
      for (let y = startY; y < endY; y += tileSize) {
        // Convert world coordinates to screen coordinates
        const screenX = x - state.camera.x + canvasWidth / 2;
        const screenY = y - state.camera.y + canvasHeight / 2;

        ctx.drawImage(
          img,
          srcX, srcY, tileSize, tileSize, // Source
          screenX, screenY, tileSize, tileSize // Destination
        );
      }
    }
  }

  function initCanvas() {
    if (!elements.canvas) return;
    ctx = elements.canvas.getContext('2d');
    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);
  }

  function resizeCanvas() {
    if (!elements.canvas || !root) return;
    // Utiliser la taille complète de la fenêtre pour éviter les bandes noires
    canvasWidth = elements.canvas.width = window.innerWidth;
    canvasHeight = elements.canvas.height = window.innerHeight;
  }

  // ============================================
  // ENEMY SPRITE SYSTEM
  // ============================================

  /**
   * Choisit un sprite aléatoire parmi les ~1832 disponibles
   * @returns {string} - Le nom du fichier (ex: '42s.png' ou '789.png')
   */
  function chooseRandomEnemySprite() {
    // Choisir un numéro entre 1 et 916
    const randomNum = Math.floor(Math.random() * ENEMY_SPRITE_CONFIG.maxSpriteNumber) + 1;

    // Choisir aléatoirement entre version normale (X.png) et variante (Xs.png)
    const useVariant = ENEMY_SPRITE_CONFIG.hasVariants && Math.random() < 0.5;

    if (useVariant) {
      return `${randomNum}s.png`;
    } else {
      return `${randomNum}.png`;
    }
  }

  /**
   * Charge un sprite sheet d'ennemi
   * @param {string} filename - Le nom du fichier (ex: '42s.png')
   * @returns {Object} - { filename, image, loaded }
   */
  function loadEnemySpriteSheet(filename) {
    const spriteData = {
      filename: filename,
      image: new Image(),
      loaded: false
    };

    spriteData.image.onload = () => {
      spriteData.loaded = true;
    };

    spriteData.image.onerror = () => {
      console.error(`Failed to load enemy sprite sheet: ${filename}`);
    };

    spriteData.image.src = ENEMY_SPRITE_CONFIG.basePath + filename;

    return spriteData;
  }

  /**
   * Initialise les sprites pour la vague : 1 sprite par type d'ennemi
   * Choisit et charge 3 sprites aléatoires parmi les ~1832 disponibles
   */
  function initializeWaveSprites() {
    const enemyTypes = ['zombie', 'fast', 'miniBoss'];

    for (const enemyType of enemyTypes) {
      const filename = chooseRandomEnemySprite();
      state.waveEnemySprites[enemyType] = loadEnemySpriteSheet(filename);
    }
  }

  /**
   * Calcule la direction de l'ennemi basée sur son mouvement
   * @param {number} dx - Différence X (destination - position)
   * @param {number} dy - Différence Y (destination - position)
   * @returns {number} - SPRITE_DIRECTION.DOWN/LEFT/RIGHT/UP
   */
  function getEnemyDirection(dx, dy) {
    // Si le mouvement est principalement horizontal
    if (Math.abs(dx) > Math.abs(dy)) {
      return dx < 0 ? SPRITE_DIRECTION.LEFT : SPRITE_DIRECTION.RIGHT;
    } else {
      // Mouvement principalement vertical
      return dy < 0 ? SPRITE_DIRECTION.UP : SPRITE_DIRECTION.DOWN;
    }
  }

  /**
   * Calcule le frame d'animation actuel
   * @param {number} currentTime - Temps actuel en ms
   * @returns {number} - Index du frame (0-3)
   */
  function getAnimationFrame(currentTime) {
    const cycleTime = currentTime % (ENEMY_SPRITE_CONFIG.frameDuration * 4);
    return Math.floor(cycleTime / ENEMY_SPRITE_CONFIG.frameDuration);
  }

  /**
   * Dessine un ennemi avec le sprite sheet animé de son type
   * @param {Object} enemy - L'objet ennemi
   * @param {number} currentTime - Temps actuel en ms
   */
  function drawEnemySprite(enemy, currentTime) {
    // Use enemy's individual sprite (assigned at spawn)
    const spriteData = enemy.sprite;

    // Si le sprite n'est pas chargé, retourner false pour utiliser le fallback
    if (!spriteData || !spriteData.loaded) {
      return false;
    }

    // Calculer la direction de l'ennemi vers le joueur
    const dx = state.player.x - enemy.x;
    const dy = state.player.y - enemy.y;
    const direction = getEnemyDirection(dx, dy);

    // Calculer le frame d'animation
    const frame = getAnimationFrame(currentTime);

    // Position dans le sprite sheet
    const srcX = frame * ENEMY_SPRITE_CONFIG.spriteSize;
    const srcY = direction * ENEMY_SPRITE_CONFIG.spriteSize;

    // Taille de dessin (peut être ajustée selon le type d'ennemi)
    let drawSize = ENEMY_SPRITE_CONFIG.spriteSize;
    if (enemy.type === 'miniBoss') {
      drawSize *= 2; // Boss 2× plus gros
    }

    // Dessiner le sprite
    ctx.drawImage(
      spriteData.image,
      srcX, srcY,
      ENEMY_SPRITE_CONFIG.spriteSize,
      ENEMY_SPRITE_CONFIG.spriteSize,
      enemy.x - drawSize / 2,
      enemy.y - drawSize / 2,
      drawSize,
      drawSize
    );

    return true;
  }

  function initPlayer() {
    state.player.x = 0;
    state.player.y = 0;
    state.player.speed = 120; // Nouvelle vitesse de base
    state.player.health = state.config.player.baseHealth;
    state.player.maxHealth = state.config.player.baseHealth;
    state.player.xp = 0;
    state.player.level = 1;
    state.player.xpForNextLevel = calculateXPForLevel(1);
    state.player.weaponSlots = 2; // Maximum 2 armes

    // Initialize upgrades structure
    state.player.upgrades = {
      global: {
        speed: 0,
        maxHealth: 0,
        lifeSteal: 0,
        regeneration: 0,
        armor: 0,
        xpBonus: 0,
        critChance: 0,
        revives: 0
      },
      weapons: {
        projectile: {
          projectileCount: 0,
          damagePercent: 0,
          fireRatePercent: 0,
          speedPercent: 0,
          penetration: 0,
          lifetimePercent: 0,
          sizePercent: 0
        },
        laser: {
          rangePercent: 0,
          damagePercent: 0,
          widthPercent: 0,
          durationPercent: 0,
          fireRatePercent: 0,
          multiLaser: 0
        },
        aura: {
          radiusPercent: 0,
          damagePercent: 0,
          slowPercent: 0,
          tickRatePercent: 0,
          critChance: 0
        },
        bouncing: {
          maxBounces: 0,
          damagePercent: 0,
          speedPercent: 0,
          fireRatePercent: 0,
          sizePercent: 0,
          multiShot: 0
        },
        bomb: {
          explosionRadiusPercent: 0,
          damagePercent: 0,
          fireRatePercent: 0,
          speedPercent: 0,
          multiShot: 0,
          burnDamage: 0
        },
        blackhole: {
          pullRadiusPercent: 0,
          damagePercent: 0,
          durationPercent: 0,
          tickRatePercent: 0,
          fireRatePercent: 0,
          multiShot: 0
        }
      }
    };

    state.player.specialBonusesActive = [];
    state.player.usedRevives = 0;

    // Initialisation compteurs de vol de vie
    state.player.lifeStealThisSecond = 0;
    state.player.lastLifeStealReset = 0;

    // Map weapon to character sprite
    const weaponToCharacter = {
      'projectile': 'Mage',
      'bouncing': 'ChatNoir',
      'aura': 'Ghost',
      'laser': 'Robot',
      'bomb': 'Skeleton',
      'blackhole': 'Vortex'
    };

    // Map character to weapon (reverse mapping)
    const characterToWeapon = {
      'Mage': 'projectile',
      'ChatNoir': 'bouncing',
      'Ghost': 'aura',
      'Robot': 'laser',
      'Skeleton': 'bomb',
      'Vortex': 'blackhole'
    };

    // Use selected hero or choose random
    let startingWeaponType;
    if (state.selectedHero && characterToWeapon[state.selectedHero]) {
      startingWeaponType = characterToWeapon[state.selectedHero];
      state.player.characterType = state.selectedHero;
    } else {
      // Random hero
      const availableWeapons = ['projectile', 'laser', 'aura', 'bouncing', 'bomb', 'blackhole'];
      startingWeaponType = availableWeapons[Math.floor(Math.random() * availableWeapons.length)];
      state.player.characterType = weaponToCharacter[startingWeaponType];
    }

    state.player.characterSprite = null; // Will be loaded

    // Initialize with starting weapon
    state.player.weapons = [];
    const weaponConfig = state.config.weapons[startingWeaponType];
    if (weaponConfig) {
      state.player.weapons.push({
        type: startingWeaponType,
        level: 1,
        lastShotTime: 0,
        config: { ...weaponConfig }
      });
    }

    state.player.iframes = 0;
    state.player.offeredWeapon = null; // Track which weapon was offered

    // Load character sprite
    loadCharacterSprite();
  }

  function loadCharacterSprite() {
    if (!state.player.characterType) return;

    const sprite = new Image();
    sprite.onload = () => {
      state.player.characterSprite = sprite;
    };
    sprite.onerror = () => {
      console.error(`Failed to load character sprite: ${state.player.characterType}.png`);
      state.player.characterSprite = null;
    };
    sprite.src = `Assets/sprites/Survive/${state.player.characterType}.png`;
  }

  function resetGame() {
    state.elapsed = 0;
    state.wave = 1;
    state.kills = 0;
    state.projectiles = [];
    state.lasers = [];
    state.bouncingProjectiles = [];
    state.bombs = [];
    state.explosions = [];
    state.blackholes = [];
    state.enemies = [];
    state.nextSpawnTime = 0;
    state.nextWaveTime = state.config.spawning.waveInterval;
    state.spawnInterval = state.config.spawning.initialInterval;
    state.lastGachaTime = 0;
    const bossConfig = state.config.spawning.bossSpawn;
    state.nextBossTime = bossConfig?.enabled ? (bossConfig.spawnIntervalSeconds * 1000) : 0;
    state.bossSpawnBlocked = false;
    state.bossSpawnBlockedUntil = 0;
    // Select random background tile for this game
    selectRandomBackgroundTile();
    initPlayer();
    updateHUD();
    // Initialiser les sprites d'ennemis pour la vague 1
    initializeWaveSprites();
  }

  function showOverlay(overlay) {
    // Permettre le scroll tactile quand un overlay est visible
    if (root) {
      root.style.touchAction = 'auto';
    }
    // Désactiver la zone du joystick pour permettre le scroll
    if (elements.joystickZone) {
      elements.joystickZone.style.pointerEvents = 'none';
    }

    if (overlay === 'start') {
      updateContinueButton();
      elements.startOverlay?.classList.remove('survivor-like__overlay--hidden');
      elements.levelUpOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.gameOverOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.pauseOverlay?.classList.add('survivor-like__overlay--hidden');
      // Masquer le bouton pause dans le menu
      elements.pauseButton?.classList.remove('survivor-like__pause-button--visible');
    } else if (overlay === 'levelup') {
      elements.startOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.levelUpOverlay?.classList.remove('survivor-like__overlay--hidden');
      elements.gameOverOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.pauseOverlay?.classList.add('survivor-like__overlay--hidden');
      // Le bouton pause reste visible pendant levelup
    } else if (overlay === 'gameover') {
      elements.startOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.levelUpOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.gameOverOverlay?.classList.remove('survivor-like__overlay--hidden');
      elements.pauseOverlay?.classList.add('survivor-like__overlay--hidden');
      // Masquer le bouton pause en game over
      elements.pauseButton?.classList.remove('survivor-like__pause-button--visible');
    } else if (overlay === 'pause') {
      elements.startOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.levelUpOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.gameOverOverlay?.classList.add('survivor-like__overlay--hidden');
      elements.pauseOverlay?.classList.remove('survivor-like__overlay--hidden');
      // Le bouton pause reste visible
    }
  }

  function hideAllOverlays() {
    // Bloquer le scroll tactile quand on revient au jeu
    if (root) {
      root.style.touchAction = 'none';
    }
    // Réactiver la zone du joystick quand on revient au jeu
    if (elements.joystickZone) {
      elements.joystickZone.style.pointerEvents = 'auto';
    }

    elements.startOverlay?.classList.add('survivor-like__overlay--hidden');
    elements.levelUpOverlay?.classList.add('survivor-like__overlay--hidden');
    elements.gameOverOverlay?.classList.add('survivor-like__overlay--hidden');
    elements.pauseOverlay?.classList.add('survivor-like__overlay--hidden');
  }

  function startGame() {
    resetGame();
    clearGameState();
    state.gameState = GameState.PLAYING;
    state.running = true;
    state.lastTime = performance.now();
    hideAllOverlays();
    hideStatusBar();
    // Afficher le bouton pause
    elements.pauseButton?.classList.add('survivor-like__pause-button--visible');
    requestAnimationFrame(gameLoop);
  }

  function continueGame() {
    if (!loadGameState()) {
      startGame();
      return;
    }
    if (state.gameState === GameState.LEVELUP) {
      state.running = false;
      state.paused = true;
      showStatusBar();
      showOverlay('levelup');
      elements.pauseButton?.classList.add('survivor-like__pause-button--visible');
      return;
    }

    state.gameState = GameState.PLAYING;
    state.running = true;
    state.paused = false;
    state.lastTime = performance.now();
    hideAllOverlays();
    hideStatusBar();
    // Afficher le bouton pause
    elements.pauseButton?.classList.add('survivor-like__pause-button--visible');
    requestAnimationFrame(gameLoop);
  }

  function gameOver() {
    // Vérifier résurrections disponibles
    const globalUpgrades = state.player.upgrades.global;
    if (globalUpgrades.revives > 0) {
      // Utiliser une résurrection
      if (!state.player.usedRevives) {
        state.player.usedRevives = 0;
      }

      if (state.player.usedRevives < globalUpgrades.revives) {
        state.player.usedRevives++;
        // Ressusciter avec 50% de la vie max
        state.player.health = Math.floor(state.player.maxHealth * 0.5);
        state.player.iframes = state.config.player.iframeDuration * 3; // Iframes prolongés
        // Continuer le jeu
        return;
      }
    }

    // Pas de résurrection disponible, vraiment game over
    state.gameState = GameState.GAMEOVER;
    state.running = false;

    clearGameState();

    // Calculer et distribuer les tickets gacha basés sur le temps de survie (1 par minute)
    const minutesSurvived = Math.floor(state.elapsed / 60000);
    let ticketsEarned = minutesSurvived;
    if (ticketsEarned > 0) {
      const awardGacha = typeof gainGachaTickets === 'function'
        ? gainGachaTickets
        : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
          ? window.gainGachaTickets
          : null;
      if (typeof awardGacha === 'function') {
        awardGacha(ticketsEarned, { unlockTicketStar: true });
      }
    }

    if (elements.gameOverTime) {
      elements.gameOverTime.textContent = formatTime(state.elapsed);
    }
    if (elements.gameOverLevel) {
      elements.gameOverLevel.textContent = state.player.level;
    }
    if (elements.gameOverKills) {
      elements.gameOverKills.textContent = state.kills;
    }
    // Afficher les tickets gagnés
    if (elements.gameOverTickets) {
      elements.gameOverTickets.textContent = ticketsEarned;
    }

    if (state.elapsed > state.bestTime) {
      state.bestTime = state.elapsed;
    }
    if (state.player.level > state.bestLevel) {
      state.bestLevel = state.player.level;
    }
    saveRecords({ heroId: state.player.characterType, heroTime: state.elapsed, heroKills: state.kills });

    showStatusBar();
    showOverlay('gameover');
  }

  function formatTime(milliseconds) {
    const totalSeconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  function formatKills(value) {
    if (!Number.isFinite(Number(value)) || value <= 0) {
      return '—';
    }
    return Math.floor(Number(value)).toLocaleString();
  }

  function updateHeroRecordDisplay() {
    HERO_IDS.forEach((hero) => {
      const heroTime = state.bestTimeByHero[hero] || 0;
      const heroKills = state.bestKillsByHero[hero] || 0;
      const heroElements = heroRecordElements[hero];
      if (heroElements?.time) {
        heroElements.time.textContent = heroTime > 0 ? formatTime(heroTime) : '—';
      }
      if (heroElements?.kills) {
        heroElements.kills.textContent = heroKills > 0 ? formatKills(heroKills) : '—';
      }
    });
  }

  function updateHUD() {
    if (elements.healthFill) {
      const healthPercent = (state.player.health / state.player.maxHealth) * 100;
      // Barre verticale : on utilise height au lieu de width
      elements.healthFill.style.height = `${healthPercent}%`;
    }
    if (elements.healthText) {
      elements.healthText.textContent = `${Math.ceil(state.player.health)} / ${state.player.maxHealth}`;
    }
    if (elements.xpFill) {
      const xpPercent = (state.player.xp / state.player.xpForNextLevel) * 100;
      // Barre verticale : on utilise height au lieu de width
      elements.xpFill.style.height = `${xpPercent}%`;
    }
    if (elements.levelText) {
      elements.levelText.textContent = translate('index.sections.survivorLike.hud.level', 'Niv.') + ' ' + state.player.level;
    }
    if (elements.timeValue) {
      elements.timeValue.textContent = formatTime(state.elapsed);
    }
    if (elements.waveValue) {
      elements.waveValue.textContent = state.wave;
    }
    if (elements.killsValue) {
      elements.killsValue.textContent = state.kills;
    }
  }

  function setupJoystick() {
    if (!elements.joystickZone) return;

    elements.joystickZone.addEventListener('touchstart', handleJoystickStart, { passive: false });
    elements.joystickZone.addEventListener('touchmove', handleJoystickMove, { passive: false });
    elements.joystickZone.addEventListener('touchend', handleJoystickEnd, { passive: false });
    elements.joystickZone.addEventListener('touchcancel', handleJoystickEnd, { passive: false });
  }

  function handleJoystickStart(e) {
    if (state.gameState !== GameState.PLAYING) return;
    e.preventDefault();

    const touch = e.changedTouches[0];
    if (!touch) return;

    state.input.touchId = touch.identifier;
    state.input.joystickActive = true;
    state.input.joystickStartX = touch.clientX;
    state.input.joystickStartY = touch.clientY;

    if (elements.joystick) {
      elements.joystick.style.left = `${touch.clientX - 60}px`;
      elements.joystick.style.top = `${touch.clientY - 60}px`;
      elements.joystick.classList.add('survivor-like__joystick--active');
    }
  }

  function handleJoystickMove(e) {
    if (!state.input.joystickActive) return;
    e.preventDefault();

    let touch = null;
    for (let i = 0; i < e.changedTouches.length; i++) {
      if (e.changedTouches[i].identifier === state.input.touchId) {
        touch = e.changedTouches[i];
        break;
      }
    }
    if (!touch) return;

    const deltaX = touch.clientX - state.input.joystickStartX;
    const deltaY = touch.clientY - state.input.joystickStartY;
    const distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    const maxDistance = 50;

    if (distance > maxDistance) {
      const angle = Math.atan2(deltaY, deltaX);
      state.input.joystickDeltaX = Math.cos(angle) * maxDistance;
      state.input.joystickDeltaY = Math.sin(angle) * maxDistance;
    } else {
      state.input.joystickDeltaX = deltaX;
      state.input.joystickDeltaY = deltaY;
    }

    if (elements.joystickInner) {
      elements.joystickInner.style.transform = `translate(${state.input.joystickDeltaX}px, ${state.input.joystickDeltaY}px)`;
    }
  }

  function handleJoystickEnd(e) {
    if (!state.input.joystickActive) return;

    let touchEnded = false;
    for (let i = 0; i < e.changedTouches.length; i++) {
      if (e.changedTouches[i].identifier === state.input.touchId) {
        touchEnded = true;
        break;
      }
    }
    if (!touchEnded) return;

    state.input.joystickActive = false;
    state.input.joystickDeltaX = 0;
    state.input.joystickDeltaY = 0;
    state.input.touchId = null;

    if (elements.joystick) {
      elements.joystick.classList.remove('survivor-like__joystick--active');
    }
    if (elements.joystickInner) {
      elements.joystickInner.style.transform = 'translate(0, 0)';
    }
  }

  function updatePlayer(deltaSeconds) {
    const magnitude = Math.sqrt(
      state.input.joystickDeltaX * state.input.joystickDeltaX +
      state.input.joystickDeltaY * state.input.joystickDeltaY
    );

    if (magnitude > 0) {
      const normalizedX = state.input.joystickDeltaX / magnitude;
      const normalizedY = state.input.joystickDeltaY / magnitude;
      state.player.x += normalizedX * state.player.speed * deltaSeconds;
      state.player.y += normalizedY * state.player.speed * deltaSeconds;
    }

    if (state.player.iframes > 0) {
      state.player.iframes -= deltaSeconds * 1000;
    }
  }

  function updateCamera() {
    state.camera.x = state.player.x;
    state.camera.y = state.player.y;
  }

  // === WEAPON SYSTEM ===
  const WeaponSystem = {
    projectile: {
      shoot(weapon, currentTime) {
        const fireInterval = 1000 / weapon.config.fireRate;
        if (currentTime - weapon.lastShotTime < fireInterval) return;

        const closestEnemy = findClosestEnemy();
        if (!closestEnemy) return;

        weapon.lastShotTime = currentTime;

        const angleToEnemy = Math.atan2(
          closestEnemy.y - state.player.y,
          closestEnemy.x - state.player.x
        );

        const projectileCount = weapon.config.projectileCount;
        const spreadAngle = 0.2;

        for (let i = 0; i < projectileCount; i++) {
          let angle = angleToEnemy;
          if (projectileCount > 1) {
            const offset = (i - (projectileCount - 1) / 2) * spreadAngle;
            angle += offset;
          }

          const projPenetration = weapon.config.penetration || 0;

          state.projectiles.push({
            x: state.player.x,
            y: state.player.y,
            vx: Math.cos(angle) * weapon.config.projectileSpeed,
            vy: Math.sin(angle) * weapon.config.projectileSpeed,
            damage: weapon.config.damage,
            size: weapon.config.projectileSize,
            penetration: projPenetration,
            hitCount: 0,  // Compteur de hits persistant
            createdAt: currentTime
          });
        }
      }
    },

    laser: {
      shoot(weapon, currentTime) {
        const fireInterval = 1000 / weapon.config.fireRate;
        if (currentTime - weapon.lastShotTime < fireInterval) return;

        const closestEnemy = findClosestEnemy();
        if (!closestEnemy) return;

        weapon.lastShotTime = currentTime;

        const angle = Math.atan2(
          closestEnemy.y - state.player.y,
          closestEnemy.x - state.player.x
        );

        // Multi-laser support
        const laserCount = (weapon.config.multiLaser || 0) + 1;
        const spreadAngle = 0.15; // Angle entre les lasers

        for (let i = 0; i < laserCount; i++) {
          let laserAngle = angle;
          if (laserCount > 1) {
            const offset = (i - (laserCount - 1) / 2) * spreadAngle;
            laserAngle += offset;
          }

          state.lasers.push({
            startX: state.player.x,
            startY: state.player.y,
            endX: state.player.x + Math.cos(laserAngle) * weapon.config.range,
            endY: state.player.y + Math.sin(laserAngle) * weapon.config.range,
            damage: weapon.config.damage,
            width: weapon.config.width,
            duration: weapon.config.duration,
            color: weapon.config.color,
            createdAt: currentTime
          });
        }
      },

      update(lasers, currentTime) {
        for (let i = lasers.length - 1; i >= 0; i--) {
          const laser = lasers[i];
          if (currentTime - laser.createdAt > laser.duration) {
            lasers.splice(i, 1);
          }
        }
      },

      render(ctx, lasers, currentTime) {
        for (const laser of lasers) {
          const age = currentTime - laser.createdAt;
          if (age > laser.duration) continue;

          const alpha = 1 - (age / laser.duration);
          ctx.save();
          ctx.strokeStyle = laser.color;
          ctx.lineWidth = laser.width;
          ctx.globalAlpha = alpha;

          // Glow effect
          ctx.shadowBlur = 10;
          ctx.shadowColor = laser.color;

          ctx.beginPath();
          ctx.moveTo(laser.startX - state.camera.x + canvasWidth / 2, laser.startY - state.camera.y + canvasHeight / 2);
          ctx.lineTo(laser.endX - state.camera.x + canvasWidth / 2, laser.endY - state.camera.y + canvasHeight / 2);
          ctx.stroke();
          ctx.restore();
        }
      }
    },

    aura: {
      update(weapon, deltaSeconds, currentTime) {
        const tickInterval = 1000 / weapon.config.tickRate;
        if (!weapon.lastTickTime) weapon.lastTickTime = 0;

        // Apply slow and damage to all enemies in radius
        for (let j = state.enemies.length - 1; j >= 0; j--) {
          const enemy = state.enemies[j];
          const dx = enemy.x - state.player.x;
          const dy = enemy.y - state.player.y;
          const dist = Math.sqrt(dx * dx + dy * dy);

          if (dist < weapon.config.radius) {
            // Apply slow effect (enemies move slower in aura)
            enemy.slowedByAura = weapon.config.slowFactor;

            // Periodic damage
            if (currentTime - weapon.lastTickTime >= tickInterval) {
              if (!enemy.lastAuraDamage || currentTime - enemy.lastAuraDamage >= tickInterval) {
                let damage = weapon.config.damage;

                // Aura-specific crit chance
                if (weapon.config.critChance > 0) {
                  const auraCritChancePercent = weapon.config.critChance * 100;
                  if (Math.random() * 100 < auraCritChancePercent) {
                    damage *= 2; // Critical hit
                  }
                }

                const finalDamage = applyDamageWithBonuses(damage);
                enemy.health -= finalDamage;
                enemy.lastAuraDamage = currentTime;

                // Check if enemy is killed
                if (enemy.health <= 0) {
                  state.kills++;
                  // Bonus XP
                  let xpGained = enemy.xp;
                  if (state.player.upgrades.global.xpBonus > 0) {
                    xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
                  }
                  state.player.xp += xpGained;
                  state.enemies.splice(j, 1);

                  if (state.player.xp >= state.player.xpForNextLevel) {
                    levelUp();
                  }
                }
              }
            }
          } else {
            // Remove slow if enemy leaves aura
            enemy.slowedByAura = null;
          }
        }

        if (currentTime - weapon.lastTickTime >= tickInterval) {
          weapon.lastTickTime = currentTime;
        }
      },

      render(ctx, weapon, currentTime) {
        const pulse = 1 + Math.sin(currentTime / 200) * 0.1;
        const radius = weapon.config.radius * pulse;

        ctx.save();
        ctx.strokeStyle = weapon.config.color;
        ctx.lineWidth = 2;
        ctx.globalAlpha = 0.3;

        // Shadow effect
        ctx.shadowBlur = 15;
        ctx.shadowColor = weapon.config.color;

        ctx.beginPath();
        ctx.arc(state.player.x, state.player.y, radius, 0, Math.PI * 2);
        ctx.stroke();

        // Inner glow
        ctx.globalAlpha = 0.1;
        ctx.fillStyle = weapon.config.color;
        ctx.fill();

        ctx.restore();
      }
    },

    bouncing: {
      shoot(weapon, currentTime) {
        const fireInterval = 1000 / weapon.config.fireRate;
        if (currentTime - weapon.lastShotTime < fireInterval) return;

        const closestEnemy = findClosestEnemy();
        if (!closestEnemy) return;

        weapon.lastShotTime = currentTime;

        const angle = Math.atan2(
          closestEnemy.y - state.player.y,
          closestEnemy.x - state.player.x
        );

        // Multi-shot support
        const projectileCount = (weapon.config.multiShot || 0) + 1;
        const spreadAngle = 0.2;

        for (let i = 0; i < projectileCount; i++) {
          let projAngle = angle;
          if (projectileCount > 1) {
            const offset = (i - (projectileCount - 1) / 2) * spreadAngle;
            projAngle += offset;
          }

          state.bouncingProjectiles.push({
            x: state.player.x,
            y: state.player.y,
            vx: Math.cos(projAngle) * weapon.config.speed,
            vy: Math.sin(projAngle) * weapon.config.speed,
            damage: weapon.config.damage,
            size: weapon.config.size,
            bounces: 0,
            maxBounces: weapon.config.maxBounces,
            homingStrength: weapon.config.homingStrength,
            createdAt: currentTime
          });
        }
      },

      update(projectiles, deltaSeconds, currentTime) {
        const viewBounds = {
          left: state.camera.x - canvasWidth / 2,
          right: state.camera.x + canvasWidth / 2,
          top: state.camera.y - canvasHeight / 2,
          bottom: state.camera.y + canvasHeight / 2
        };

        for (let i = projectiles.length - 1; i >= 0; i--) {
          const proj = projectiles[i];

          // Apply slight homing toward closest enemy
          const closest = findClosestEnemy();
          if (closest && proj.homingStrength > 0) {
            const dx = closest.x - proj.x;
            const dy = closest.y - proj.y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
              const homingForce = proj.homingStrength;
              proj.vx += (dx / dist) * homingForce * deltaSeconds * 100;
              proj.vy += (dy / dist) * homingForce * deltaSeconds * 100;
            }
          }

          // Move
          proj.x += proj.vx * deltaSeconds;
          proj.y += proj.vy * deltaSeconds;

          // Bounce on viewport edges (ne compte pas comme un rebond, juste pour garder le projectile visible)
          if (proj.x < viewBounds.left || proj.x > viewBounds.right) {
            proj.vx *= -1;
            proj.x = proj.x < viewBounds.left ? viewBounds.left : viewBounds.right;
            // Les rebonds sur les bords ne comptent pas
          }
          if (proj.y < viewBounds.top || proj.y > viewBounds.bottom) {
            proj.vy *= -1;
            proj.y = proj.y < viewBounds.top ? viewBounds.top : viewBounds.bottom;
            // Les rebonds sur les bords ne comptent pas
          }

          // Supprimer après un certain temps si le projectile n'a touché personne (10 secondes max)
          if (currentTime - proj.createdAt > 10000) {
            projectiles.splice(i, 1);
          }
        }
      },

      render(ctx, projectiles) {
        for (const proj of projectiles) {
          ctx.fillStyle = '#ff88ff';
          ctx.beginPath();
          ctx.arc(proj.x, proj.y, proj.size, 0, Math.PI * 2);
          ctx.fill();

          // Trail effect
          ctx.strokeStyle = 'rgba(255, 136, 255, 0.3)';
          ctx.lineWidth = proj.size * 2;
          ctx.beginPath();
          ctx.moveTo(proj.x, proj.y);
          ctx.lineTo(proj.x - proj.vx * 0.05, proj.y - proj.vy * 0.05);
          ctx.stroke();
        }
      }
    },

    bomb: {
      shoot(weapon, currentTime) {
        const fireInterval = 1000 / weapon.config.fireRate;
        if (currentTime - weapon.lastShotTime < fireInterval) return;

        const closestEnemy = findClosestEnemy();
        if (!closestEnemy) return;

        weapon.lastShotTime = currentTime;

        const angle = Math.atan2(
          closestEnemy.y - state.player.y,
          closestEnemy.x - state.player.x
        );

        // Multi-shot support
        const bombCount = (weapon.config.multiShot || 0) + 1;
        const spreadAngle = 0.25;

        for (let i = 0; i < bombCount; i++) {
          let bombAngle = angle;
          if (bombCount > 1) {
            const offset = (i - (bombCount - 1) / 2) * spreadAngle;
            bombAngle += offset;
          }

          state.bombs.push({
            x: state.player.x,
            y: state.player.y,
            vx: Math.cos(bombAngle) * weapon.config.speed,
            vy: Math.sin(bombAngle) * weapon.config.speed,
            damage: weapon.config.damage,
            size: weapon.config.size,
            explosionRadius: weapon.config.explosionRadius,
            burnDamage: weapon.config.burnDamage || 0,
            color: weapon.config.color,
            createdAt: currentTime
          });
        }
      },

      update(bombs, deltaSeconds, currentTime) {
        for (let i = bombs.length - 1; i >= 0; i--) {
          const bomb = bombs[i];

          // Move
          bomb.x += bomb.vx * deltaSeconds;
          bomb.y += bomb.vy * deltaSeconds;

          // Check for collision with enemies or timeout
          let shouldExplode = false;

          for (const enemy of state.enemies) {
            const dx = bomb.x - enemy.x;
            const dy = bomb.y - enemy.y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < enemy.radius + bomb.size) {
              shouldExplode = true;
              break;
            }
          }

          if (currentTime - bomb.createdAt > 3000) {
            shouldExplode = true;
          }

          if (shouldExplode) {
            // Create explosion
            state.explosions.push({
              x: bomb.x,
              y: bomb.y,
              radius: bomb.explosionRadius,
              damage: bomb.damage,
              color: bomb.color,
              createdAt: currentTime,
              duration: 300,
              damageApplied: false
            });

            // Apply damage to all enemies in radius
            for (let j = state.enemies.length - 1; j >= 0; j--) {
              const enemy = state.enemies[j];
              const dx = bomb.x - enemy.x;
              const dy = bomb.y - enemy.y;
              const dist = Math.sqrt(dx * dx + dy * dy);

              if (dist < bomb.explosionRadius) {
                const finalDamage = applyDamageWithBonuses(bomb.damage);
                enemy.health -= finalDamage;

                // Apply burn effect if burnDamage > 0
                if (bomb.burnDamage > 0) {
                  if (!enemy.burnEffects) enemy.burnEffects = [];
                  enemy.burnEffects.push({
                    damagePerSecond: bomb.burnDamage,
                    startTime: currentTime,
                    duration: 2000 // 2 secondes
                  });
                }

                if (enemy.health <= 0) {
                  state.kills++;
                  // Bonus XP
                  let xpGained = enemy.xp;
                  if (state.player.upgrades.global.xpBonus > 0) {
                    xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
                  }
                  state.player.xp += xpGained;
                  state.enemies.splice(j, 1);

                  if (state.player.xp >= state.player.xpForNextLevel) {
                    levelUp();
                  }
                }
              }
            }

            bombs.splice(i, 1);
          }
        }

        // Update explosions (animation only)
        for (let i = state.explosions.length - 1; i >= 0; i--) {
          const explosion = state.explosions[i];
          if (currentTime - explosion.createdAt > explosion.duration) {
            state.explosions.splice(i, 1);
          }
        }
      },

      render(ctx, bombs, explosions, currentTime) {
        // Draw bombs
        for (const bomb of bombs) {
          const age = currentTime - bomb.createdAt;
          const scale = 1 + Math.sin(age / 100) * 0.2;

          ctx.fillStyle = bomb.color;
          ctx.beginPath();
          ctx.arc(bomb.x, bomb.y, bomb.size * scale, 0, Math.PI * 2);
          ctx.fill();

          // Fuse effect
          ctx.strokeStyle = '#fff';
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.arc(bomb.x, bomb.y, bomb.size * scale * 1.2, 0, Math.PI * 2);
          ctx.stroke();
        }

        // Draw explosions
        for (const explosion of explosions) {
          const age = currentTime - explosion.createdAt;
          const progress = age / explosion.duration;
          const radius = explosion.radius * (0.5 + progress * 0.5);
          const alpha = 1 - progress;

          ctx.save();
          ctx.globalAlpha = alpha;

          // Outer ring
          ctx.strokeStyle = explosion.color;
          ctx.lineWidth = 8;
          ctx.beginPath();
          ctx.arc(explosion.x, explosion.y, radius, 0, Math.PI * 2);
          ctx.stroke();

          // Inner fill
          ctx.fillStyle = explosion.color;
          ctx.globalAlpha = alpha * 0.3;
          ctx.fill();

          ctx.restore();
        }
      }
    },

    blackhole: {
      shoot(weapon, currentTime) {
        const fireInterval = 1000 / weapon.config.fireRate;
        if (currentTime - weapon.lastShotTime < fireInterval) return;

        const closestEnemy = findClosestEnemy();
        if (!closestEnemy) return;

        weapon.lastShotTime = currentTime;

        const angle = Math.atan2(
          closestEnemy.y - state.player.y,
          closestEnemy.x - state.player.x
        );

        // Multi-shot support
        const holeCount = (weapon.config.multiShot || 0) + 1;
        const spreadAngle = 0.3;

        for (let i = 0; i < holeCount; i++) {
          let holeAngle = angle;
          if (holeCount > 1) {
            const offset = (i - (holeCount - 1) / 2) * spreadAngle;
            holeAngle += offset;
          }

          state.blackholes.push({
            x: state.player.x,
            y: state.player.y,
            vx: Math.cos(holeAngle) * weapon.config.speed,
            vy: Math.sin(holeAngle) * weapon.config.speed,
            damage: weapon.config.damage,
            size: weapon.config.size,
            pullRadius: weapon.config.pullRadius,
            duration: weapon.config.duration,
            tickRate: weapon.config.tickRate,
            color: weapon.config.color,
            createdAt: currentTime,
            lastTickTime: currentTime,
            activated: false,
            activatedAt: 0
          });
        }
      },

      update(blackholes, deltaSeconds, currentTime) {
        for (let i = blackholes.length - 1; i >= 0; i--) {
          const hole = blackholes[i];

          // Phase 1: Flying towards enemies
          if (!hole.activated) {
            hole.x += hole.vx * deltaSeconds;
            hole.y += hole.vy * deltaSeconds;

            // Check for collision with enemy to activate
            for (const enemy of state.enemies) {
              const dx = hole.x - enemy.x;
              const dy = hole.y - enemy.y;
              const dist = Math.sqrt(dx * dx + dy * dy);
              if (dist < enemy.radius + hole.size) {
                hole.activated = true;
                hole.activatedAt = currentTime;
                hole.vx = 0;
                hole.vy = 0;
                break;
              }
            }

            // Auto-activate after 3 seconds of travel
            if (currentTime - hole.createdAt > 3000) {
              hole.activated = true;
              hole.activatedAt = currentTime;
              hole.vx = 0;
              hole.vy = 0;
            }
          }

          // Phase 2: Active vortex - pull and damage
          if (hole.activated) {
            const vortexAge = currentTime - hole.activatedAt;

            // Check if vortex expired
            if (vortexAge > hole.duration) {
              blackholes.splice(i, 1);
              continue;
            }

            // Apply damage ticks
            const tickInterval = 1000 / hole.tickRate;
            if (currentTime - hole.lastTickTime >= tickInterval) {
              hole.lastTickTime = currentTime;

              // Damage and pull enemies in radius
              for (let j = state.enemies.length - 1; j >= 0; j--) {
                const enemy = state.enemies[j];
                const dx = hole.x - enemy.x;
                const dy = hole.y - enemy.y;
                const dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < hole.pullRadius && dist > 1) {
                  // Apply damage
                  const finalDamage = applyDamageWithBonuses(hole.damage);
                  enemy.health -= finalDamage;

                  // Pull enemy towards center
                  const pullStrength = 80 * deltaSeconds;
                  const pullX = (dx / dist) * pullStrength;
                  const pullY = (dy / dist) * pullStrength;
                  enemy.x += pullX;
                  enemy.y += pullY;

                  if (enemy.health <= 0) {
                    state.kills++;
                    let xpGained = enemy.xp;
                    if (state.player.upgrades.global.xpBonus > 0) {
                      xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
                    }
                    state.player.xp += xpGained;
                    state.enemies.splice(j, 1);

                    if (state.player.xp >= state.player.xpForNextLevel) {
                      levelUp();
                    }
                  }
                }
              }
            }

            // Continuous pull effect (between ticks)
            for (const enemy of state.enemies) {
              const dx = hole.x - enemy.x;
              const dy = hole.y - enemy.y;
              const dist = Math.sqrt(dx * dx + dy * dy);

              if (dist < hole.pullRadius && dist > 1) {
                // Marquer l'ennemi comme étant dans un blackhole (désactive la séparation)
                enemy.inBlackhole = true;

                const pullStrength = 150 * deltaSeconds;
                const pullX = (dx / dist) * pullStrength;
                const pullY = (dy / dist) * pullStrength;
                enemy.x += pullX;
                enemy.y += pullY;
              }
            }
          }
        }
      },

      render(ctx, blackholes, currentTime) {
        for (const hole of blackholes) {
          if (!hole.activated) {
            // Draw flying projectile
            const age = currentTime - hole.createdAt;
            const scale = 1 + Math.sin(age / 80) * 0.15;

            // Dark core
            ctx.fillStyle = '#1a0033';
            ctx.beginPath();
            ctx.arc(hole.x, hole.y, hole.size * scale, 0, Math.PI * 2);
            ctx.fill();

            // Purple glow
            ctx.strokeStyle = hole.color;
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.arc(hole.x, hole.y, hole.size * scale * 1.3, 0, Math.PI * 2);
            ctx.stroke();
          } else {
            // Draw active vortex
            const vortexAge = currentTime - hole.activatedAt;
            const lifeProgress = vortexAge / hole.duration;
            const pulseScale = 1 + Math.sin(vortexAge / 50) * 0.1;

            // Outer pull radius indicator (fading rings)
            for (let ring = 3; ring >= 1; ring--) {
              const ringRadius = hole.pullRadius * (ring / 3) * pulseScale;
              const ringAlpha = (1 - lifeProgress) * 0.2 * (ring / 3);
              ctx.strokeStyle = hole.color;
              ctx.globalAlpha = ringAlpha;
              ctx.lineWidth = 2;
              ctx.beginPath();
              ctx.arc(hole.x, hole.y, ringRadius, 0, Math.PI * 2);
              ctx.stroke();
            }
            ctx.globalAlpha = 1;

            // Swirling effect
            const swirl = vortexAge / 100;
            for (let arm = 0; arm < 4; arm++) {
              const armAngle = swirl + (arm * Math.PI / 2);
              ctx.strokeStyle = hole.color;
              ctx.globalAlpha = (1 - lifeProgress) * 0.6;
              ctx.lineWidth = 3;
              ctx.beginPath();
              ctx.arc(hole.x, hole.y, hole.size * 2.5, armAngle, armAngle + 0.8);
              ctx.stroke();
            }
            ctx.globalAlpha = 1;

            // Dark center (event horizon)
            const gradient = ctx.createRadialGradient(
              hole.x, hole.y, 0,
              hole.x, hole.y, hole.size * 2
            );
            gradient.addColorStop(0, '#000000');
            gradient.addColorStop(0.5, '#1a0033');
            gradient.addColorStop(1, 'transparent');

            ctx.fillStyle = gradient;
            ctx.beginPath();
            ctx.arc(hole.x, hole.y, hole.size * 2 * pulseScale, 0, Math.PI * 2);
            ctx.fill();

            // Bright accretion disk
            ctx.strokeStyle = '#cc66ff';
            ctx.lineWidth = 2;
            ctx.globalAlpha = (1 - lifeProgress) * 0.8;
            ctx.beginPath();
            ctx.arc(hole.x, hole.y, hole.size * 1.5 * pulseScale, 0, Math.PI * 2);
            ctx.stroke();
            ctx.globalAlpha = 1;
          }
        }
      }
    }
  };

  function updateWeapons(timestamp, deltaSeconds) {
    // Shoot weapons
    for (const weapon of state.player.weapons) {
      const system = WeaponSystem[weapon.type];
      if (system && system.shoot) {
        system.shoot(weapon, timestamp);
      }
    }

    // Update weapon systems
    if (state.player.weapons.some(w => w.type === 'laser')) {
      WeaponSystem.laser.update(state.lasers, timestamp);
    }

    // Update aura weapon (passive, always active)
    const auraWeapon = state.player.weapons.find(w => w.type === 'aura');
    if (auraWeapon) {
      WeaponSystem.aura.update(auraWeapon, deltaSeconds, timestamp);
    }

    // Update bouncing projectiles
    if (state.player.weapons.some(w => w.type === 'bouncing')) {
      WeaponSystem.bouncing.update(state.bouncingProjectiles, deltaSeconds, timestamp);
    }

    // Update bombs and explosions
    if (state.player.weapons.some(w => w.type === 'bomb')) {
      WeaponSystem.bomb.update(state.bombs, deltaSeconds, timestamp);
    }

    // Update blackholes
    if (state.player.weapons.some(w => w.type === 'blackhole')) {
      // Réinitialiser le flag inBlackhole pour tous les ennemis
      for (const enemy of state.enemies) {
        enemy.inBlackhole = false;
      }
      WeaponSystem.blackhole.update(state.blackholes, deltaSeconds, timestamp);
    }
  }

  function findClosestEnemy() {
    let closest = null;
    let closestDist = Infinity;

    for (const enemy of state.enemies) {
      const dx = enemy.x - state.player.x;
      const dy = enemy.y - state.player.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      if (dist < closestDist) {
        closestDist = dist;
        closest = enemy;
      }
    }

    return closest;
  }

  function updateProjectiles(deltaSeconds, currentTime) {
    for (let i = state.projectiles.length - 1; i >= 0; i--) {
      const proj = state.projectiles[i];
      proj.x += proj.vx * deltaSeconds;
      proj.y += proj.vy * deltaSeconds;

      const lifetime = 2000;
      if (currentTime - proj.createdAt > lifetime) {
        state.projectiles.splice(i, 1);
      }
    }
  }

  function chooseEnemyType() {
    const enemyTypes = Object.keys(state.config.enemies);
    const weights = enemyTypes.map(type => state.config.enemies[type].spawnWeight);
    const totalWeight = weights.reduce((a, b) => a + b, 0);
    let random = Math.random() * totalWeight;

    let chosenType = enemyTypes[0];
    for (let i = 0; i < enemyTypes.length; i++) {
      random -= weights[i];
      if (random <= 0) {
        chosenType = enemyTypes[i];
        break;
      }
    }
    return chosenType;
  }

  function getHealthScaling() {
    const scalingConfig = state.config.spawning.healthScaling;
    if (!scalingConfig?.enabled) return 1;

    // SCALING EXPONENTIEL : vie augmente exponentiellement avec les vagues
    // Formule : 1 + 0.15 × (wave ^ 1.4)
    // Vague 1: 1× HP
    // Vague 5: ~2.5× HP
    // Vague 10: ~5.5× HP
    // Vague 20: ~17× HP
    // Vague 30: ~42× HP
    // Vague 50: ~150× HP
    const waveBonus = 1 + 0.15 * Math.pow(state.wave, 1.4);

    // On garde un max mais beaucoup plus élevé
    const maxMultiplier = 500; // Au lieu de 3
    return Math.min(waveBonus, maxMultiplier);
  }

  function getCurrentMaxEnemies() {
    const spawningConfig = state.config.spawning;
    const baseMax = spawningConfig.baseMaxEnemies || 15;
    const perWave = spawningConfig.maxEnemiesPerWave || 5;
    const absoluteMax = spawningConfig.absoluteMaxEnemies || 100;
    const exponentialGrowth = spawningConfig.exponentialGrowth || 1; // 1 = linear, >1 = exponential

    // Exponential growth formula: baseMax * (exponentialGrowth ^ (wave - 1))
    let calculatedMax;
    if (exponentialGrowth > 1) {
      calculatedMax = Math.floor(baseMax * Math.pow(exponentialGrowth, state.wave - 1));
    } else {
      // Fallback to linear if exponentialGrowth is 1 or not set
      calculatedMax = baseMax + (state.wave - 1) * perWave;
    }

    return Math.min(calculatedMax, absoluteMax);
  }

  function spawnEnemy(forceBoss = false) {
    // Boss spawn handling - boss can spawn even at max enemy count
    if (forceBoss) {
      spawnBoss();
      return;
    }

    // Normal enemies respect the limit
    const currentMaxEnemies = getCurrentMaxEnemies();
    if (state.enemies.length >= currentMaxEnemies) return;

    // Skip normal spawns during boss spawn block period
    if (state.bossSpawnBlocked && state.elapsed < state.bossSpawnBlockedUntil) {
      return;
    }

    const healthMultiplier = getHealthScaling();
    const groupConfig = state.config.spawning.groupSpawn;

    // SPAWN 5× PLUS D'ENNEMIS : au lieu de spawner 1 ennemi, on en spawn 5
    const enemiesPerSpawn = 5;

    // Check if group spawns should be active based on wave
    const minWaveForGroups = groupConfig?.minWaveToActivate || 1;
    const groupsActive = groupConfig?.enabled && state.wave >= minWaveForGroups;
    const shouldSpawnGroup = groupsActive && Math.random() < (groupConfig.chance || 0);

    if (shouldSpawnGroup) {
      // Calculate group size based on wave
      const baseMinSize = groupConfig.minSize || 3;
      const baseMaxSize = groupConfig.maxSize || 8;
      const sizeIncrease = groupConfig.sizeIncreasePerWave || 2;

      const minSize = Math.min(baseMinSize + (state.wave - minWaveForGroups) * sizeIncrease, baseMaxSize);
      const maxSize = Math.min(baseMaxSize + (state.wave - minWaveForGroups) * sizeIncrease, baseMaxSize * 2);

      const groupSize = Math.floor(Math.random() * (maxSize - minSize + 1) + minSize);
      const spreadRadius = groupConfig.spreadRadius || 150;

      const spawnDistance = state.config.spawning.spawnDistance;
      const centerAngle = Math.random() * Math.PI * 2;
      const centerX = state.player.x + Math.cos(centerAngle) * spawnDistance;
      const centerY = state.player.y + Math.sin(centerAngle) * spawnDistance;

      for (let i = 0; i < groupSize && state.enemies.length < currentMaxEnemies; i++) {
        const chosenType = chooseEnemyType();
        const enemyConfig = state.config.enemies[chosenType];

        const offsetAngle = Math.random() * Math.PI * 2;
        const offsetDist = Math.random() * spreadRadius;
        const x = centerX + Math.cos(offsetAngle) * offsetDist;
        const y = centerY + Math.sin(offsetAngle) * offsetDist;

        const scaledHealth = Math.floor(enemyConfig.health * healthMultiplier);

        state.enemies.push({
          type: chosenType,
          x,
          y,
          health: scaledHealth,
          maxHealth: scaledHealth,
          speed: enemyConfig.speed,
          damage: enemyConfig.damage,
          xp: enemyConfig.xp,
          radius: enemyConfig.radius,
          color: enemyConfig.color,
          spriteIndex: Math.floor(Math.random() * 4),
          sprite: state.waveEnemySprites[chosenType] // Assign sprite permanently to this enemy
        });
      }
    } else {
      // Spawn multiple enemies instead of just 1
      for (let i = 0; i < enemiesPerSpawn && state.enemies.length < currentMaxEnemies; i++) {
        const chosenType = chooseEnemyType();
        const enemyConfig = state.config.enemies[chosenType];
        const spawnDistance = state.config.spawning.spawnDistance;
        const angle = Math.random() * Math.PI * 2;
        const x = state.player.x + Math.cos(angle) * spawnDistance;
        const y = state.player.y + Math.sin(angle) * spawnDistance;

        const scaledHealth = Math.floor(enemyConfig.health * healthMultiplier);

        state.enemies.push({
          type: chosenType,
          x,
          y,
          health: scaledHealth,
          maxHealth: scaledHealth,
          speed: enemyConfig.speed,
          damage: enemyConfig.damage,
          xp: enemyConfig.xp,
          radius: enemyConfig.radius,
          color: enemyConfig.color,
          spriteIndex: Math.floor(Math.random() * 4),
          sprite: state.waveEnemySprites[chosenType] // Assign sprite permanently to this enemy
        });
      }
    }
  }

  function spawnBoss() {
    const bossConfig = state.config.enemies.miniBoss;
    if (!bossConfig) return;

    const spawnDistance = state.config.spawning.spawnDistance;
    const angle = Math.random() * Math.PI * 2;
    const x = state.player.x + Math.cos(angle) * spawnDistance;
    const y = state.player.y + Math.sin(angle) * spawnDistance;

    const healthMultiplier = getHealthScaling();
    const scaledHealth = Math.floor(bossConfig.health * healthMultiplier);

    state.enemies.push({
      type: 'miniBoss',
      x,
      y,
      health: scaledHealth,
      maxHealth: scaledHealth,
      speed: bossConfig.speed,
      damage: bossConfig.damage,
      xp: bossConfig.xp,
      radius: bossConfig.radius,
      color: bossConfig.color,
      spriteIndex: Math.floor(Math.random() * 4),
      sprite: state.waveEnemySprites['miniBoss'] // Assign sprite permanently to this boss
    });

    // Block normal spawns for a duration
    const blockDuration = state.config.spawning.bossSpawn?.preventNormalSpawnDuration || 5000;
    state.bossSpawnBlocked = true;
    state.bossSpawnBlockedUntil = state.elapsed + blockDuration;
  }

  function updateEnemies(deltaSeconds) {
    const separationConfig = state.config.spawning.enemySeparation;
    const separationEnabled = separationConfig?.enabled !== false;
    const minDistance = separationConfig?.minDistance || 8;
    const separationForce = separationConfig?.separationForce || 200;

    // Paramètres de lissage pour une séparation fluide
    const maxForcePerPair = 50; // Force max entre deux ennemis
    const maxDisplacementPerFrame = 15; // Déplacement max par frame (pixels)
    const smoothingFactor = 0.4; // Facteur de lissage (0-1, plus bas = plus smooth)

    // First pass: move enemies towards player
    for (const enemy of state.enemies) {
      const dx = state.player.x - enemy.x;
      const dy = state.player.y - enemy.y;
      const dist = Math.sqrt(dx * dx + dy * dy);

      if (dist > 0) {
        // Apply slow effect if enemy is in aura
        const speedMultiplier = enemy.slowedByAura || 1;

        // Limiter la vitesse de l'ennemi à 90% de la vitesse du joueur
        const maxEnemySpeed = state.player.speed * 0.9;
        const effectiveSpeed = Math.min(enemy.speed * speedMultiplier, maxEnemySpeed);

        enemy.x += (dx / dist) * effectiveSpeed * deltaSeconds;
        enemy.y += (dy / dist) * effectiveSpeed * deltaSeconds;
      }
    }

    // Second pass: apply separation to prevent overlapping
    if (separationEnabled && state.enemies.length > 1) {
      const enemyCount = state.enemies.length;

      // Store separation forces for all enemies before applying
      const separationForces = new Array(enemyCount);
      for (let i = 0; i < enemyCount; i++) {
        separationForces[i] = { x: 0, y: 0 };
      }

      // Calculate separation forces for all pairs
      for (let i = 0; i < enemyCount; i++) {
        const enemy1 = state.enemies[i];

        // Skip enemies being pulled by a blackhole
        if (enemy1.inBlackhole) continue;

        const checkRadius = (enemy1.radius + 40) * 2;

        for (let j = i + 1; j < enemyCount; j++) {
          const enemy2 = state.enemies[j];

          // Skip enemies being pulled by a blackhole
          if (enemy2.inBlackhole) continue;

          // Quick distance check before expensive sqrt
          const dx = enemy2.x - enemy1.x;
          const dy = enemy2.y - enemy1.y;
          const distSq = dx * dx + dy * dy;
          const maxDistSq = checkRadius * checkRadius;

          if (distSq < maxDistSq && distSq > 0.01) {
            const dist = Math.sqrt(distSq);
            const combinedRadius = enemy1.radius + enemy2.radius;
            const minSeparation = combinedRadius + minDistance;

            if (dist < minSeparation) {
              // Calculate separation vector (normalized)
              const nx = dx / dist;
              const ny = dy / dist;

              // Calculate overlap ratio (0 to 1, where 1 = completely overlapping)
              const overlapRatio = (minSeparation - dist) / minSeparation;

              // Calculate force with smooth falloff and cap
              // Force increases quadratically as overlap increases for smoother behavior
              const rawForce = overlapRatio * overlapRatio * separationForce * deltaSeconds;
              const force = Math.min(rawForce, maxForcePerPair * deltaSeconds);

              // Accumulate forces (push apart equally)
              separationForces[i].x -= nx * force;
              separationForces[i].y -= ny * force;
              separationForces[j].x += nx * force;
              separationForces[j].y += ny * force;
            }
          }
        }
      }

      // Apply accumulated separation forces with smoothing and clamping
      for (let i = 0; i < enemyCount; i++) {
        // Skip enemies being pulled by a blackhole
        if (state.enemies[i].inBlackhole) continue;

        let fx = separationForces[i].x * smoothingFactor;
        let fy = separationForces[i].y * smoothingFactor;

        // Clamp total displacement to prevent jumps
        const totalDisplacement = Math.sqrt(fx * fx + fy * fy);
        if (totalDisplacement > maxDisplacementPerFrame) {
          const scale = maxDisplacementPerFrame / totalDisplacement;
          fx *= scale;
          fy *= scale;
        }

        state.enemies[i].x += fx;
        state.enemies[i].y += fy;
      }
    }

    // Reset slow effect for next frame
    for (const enemy of state.enemies) {
      enemy.slowedByAura = 1;
    }
  }

  // Apply burn effects (damage over time from bombs)
  function applyBurnEffects(deltaSeconds, currentTime) {
    for (let i = state.enemies.length - 1; i >= 0; i--) {
      const enemy = state.enemies[i];

      if (!enemy.burnEffects || enemy.burnEffects.length === 0) continue;

      // Apply all active burn effects
      for (let j = enemy.burnEffects.length - 1; j >= 0; j--) {
        const burn = enemy.burnEffects[j];
        const elapsed = currentTime - burn.startTime;

        // Remove expired burns
        if (elapsed >= burn.duration) {
          enemy.burnEffects.splice(j, 1);
          continue;
        }

        // Apply burn damage
        const burnDamage = burn.damagePerSecond * deltaSeconds;
        enemy.health -= burnDamage;
      }

      // Check if enemy died from burn
      if (enemy.health <= 0) {
        state.kills++;
        let xpGained = enemy.xp;
        if (state.player.upgrades.global.xpBonus > 0) {
          xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
        }
        state.player.xp += xpGained;
        state.enemies.splice(i, 1);

        if (state.player.xp >= state.player.xpForNextLevel) {
          levelUp();
        }
      }
    }
  }

  function lineCircleCollision(laser, circle) {
    // Point-to-line distance algorithm
    const dx = laser.endX - laser.startX;
    const dy = laser.endY - laser.startY;
    const length = Math.sqrt(dx * dx + dy * dy);
    if (length === 0) return false;

    const dot = ((circle.x - laser.startX) * dx + (circle.y - laser.startY) * dy) / (length * length);
    const clampedDot = Math.max(0, Math.min(1, dot));
    const closestX = laser.startX + clampedDot * dx;
    const closestY = laser.startY + clampedDot * dy;

    const distX = circle.x - closestX;
    const distY = circle.y - closestY;
    const dist = Math.sqrt(distX * distX + distY * distY);

    return dist < (circle.radius + laser.width / 2);
  }

  // Calcule les dégâts finaux avec bonus critiques et vol de vie
  function applyDamageWithBonuses(baseDamage) {
    const globalUpgrades = state.player.upgrades.global;
    let finalDamage = baseDamage;

    // Chance de coup critique
    if (globalUpgrades.critChance > 0) {
      const critChancePercent = globalUpgrades.critChance * 5; // 5% par palier
      if (Math.random() * 100 < critChancePercent) {
        finalDamage *= 2; // Coup critique = x2 dégâts
      }
    }

    // Vol de vie (limité à 10% vie max par seconde)
    if (globalUpgrades.lifeSteal > 0) {
      const lifeStealPercent = globalUpgrades.lifeSteal * 2; // 2% par palier
      let healAmount = finalDamage * (lifeStealPercent / 100);

      // Limite de vol de vie par frame
      if (!state.player.lifeStealThisSecond) {
        state.player.lifeStealThisSecond = 0;
        state.player.lastLifeStealReset = performance.now();
      }

      // Reset du compteur chaque seconde
      const now = performance.now();
      if (now - state.player.lastLifeStealReset >= 1000) {
        state.player.lifeStealThisSecond = 0;
        state.player.lastLifeStealReset = now;
      }

      // Limite : max 10% de la vie totale par seconde
      const maxHealPerSecond = state.player.maxHealth * 0.1;
      const remainingHeal = maxHealPerSecond - state.player.lifeStealThisSecond;
      healAmount = Math.min(healAmount, Math.max(0, remainingHeal));

      if (healAmount > 0) {
        state.player.health = Math.min(state.player.maxHealth, state.player.health + healAmount);
        state.player.lifeStealThisSecond += healAmount;
      }
    }

    return finalDamage;
  }

  // Calcule les dégâts subis par le joueur avec réduction d'armure
  function applyDamageReduction(incomingDamage) {
    const globalUpgrades = state.player.upgrades.global;

    if (globalUpgrades.armor > 0) {
      const armorPercent = globalUpgrades.armor * 5; // 5% de réduction par palier
      const reduction = armorPercent / 100;
      return Math.floor(incomingDamage * (1 - reduction));
    }

    return incomingDamage;
  }

  function checkCollisions() {
    // Projectiles avec système de pénétration
    for (let i = state.projectiles.length - 1; i >= 0; i--) {
      const proj = state.projectiles[i];
      const maxHits = (proj.penetration || 0) + 1;

      for (let j = state.enemies.length - 1; j >= 0; j--) {
        const enemy = state.enemies[j];
        const dx = proj.x - enemy.x;
        const dy = proj.y - enemy.y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < enemy.radius + proj.size) {
          const finalDamage = applyDamageWithBonuses(proj.damage);
          enemy.health -= finalDamage;
          proj.hitCount++;

          if (enemy.health <= 0) {
            state.kills++;
            // Bonus XP
            let xpGained = enemy.xp;
            if (state.player.upgrades.global.xpBonus > 0) {
              xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
            }
            state.player.xp += xpGained;
            state.enemies.splice(j, 1);

            if (state.player.xp >= state.player.xpForNextLevel) {
              levelUp();
            }
          }

          if (proj.hitCount >= maxHits) {
            state.projectiles.splice(i, 1);
            break;
          }
        }
      }
    }

    // Laser collisions (continuous damage)
    for (const laser of state.lasers) {
      for (let j = state.enemies.length - 1; j >= 0; j--) {
        const enemy = state.enemies[j];
        if (lineCircleCollision(laser, enemy)) {
          const baseDamage = laser.damage * (1 / 60); // Damage per frame at 60fps
          const finalDamage = applyDamageWithBonuses(baseDamage);
          enemy.health -= finalDamage;

          if (enemy.health <= 0) {
            state.kills++;
            // Bonus XP
            let xpGained = enemy.xp;
            if (state.player.upgrades.global.xpBonus > 0) {
              xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
            }
            state.player.xp += xpGained;
            state.enemies.splice(j, 1);

            if (state.player.xp >= state.player.xpForNextLevel) {
              levelUp();
            }
          }
        }
      }
    }

    // Bouncing projectiles collisions
    // Les grosses balles peuvent toucher TOUS les ennemis dans leur rayon
    for (let i = state.bouncingProjectiles.length - 1; i >= 0; i--) {
      const proj = state.bouncingProjectiles[i];

      // Collecter tous les ennemis touchés par cette balle
      const hitEnemies = [];
      const hitIndices = new Set();

      for (let j = 0; j < state.enemies.length; j++) {
        const enemy = state.enemies[j];
        const dx = proj.x - enemy.x;
        const dy = proj.y - enemy.y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < enemy.radius + proj.size) {
          hitEnemies.push({ enemy, index: j, dist });
          hitIndices.add(j);
        }
      }

      // Si on a touché au moins un ennemi
      if (hitEnemies.length > 0) {
        // Appliquer les dégâts à TOUS les ennemis touchés
        const finalDamage = applyDamageWithBonuses(proj.damage);

        // Trier par distance pour traiter d'abord les plus proches
        hitEnemies.sort((a, b) => a.dist - b.dist);

        // Stocker le centre de masse des ennemis touchés pour le rebond
        let centerX = 0, centerY = 0;

        for (const hit of hitEnemies) {
          hit.enemy.health -= finalDamage;
          centerX += hit.enemy.x;
          centerY += hit.enemy.y;
        }

        centerX /= hitEnemies.length;
        centerY /= hitEnemies.length;

        // Trouver le prochain ennemi le plus proche qui N'A PAS été touché
        let closestDist = Infinity;
        let closestEnemy = null;

        for (let k = 0; k < state.enemies.length; k++) {
          if (hitIndices.has(k)) continue; // Skip les ennemis déjà touchés
          const otherEnemy = state.enemies[k];
          const dx = otherEnemy.x - proj.x;
          const dy = otherEnemy.y - proj.y;
          const distance = Math.sqrt(dx * dx + dy * dy);

          if (distance < closestDist) {
            closestDist = distance;
            closestEnemy = otherEnemy;
          }
        }

        // Rebondir vers le prochain ennemi ou s'éloigner du groupe touché
        const speed = Math.sqrt(proj.vx * proj.vx + proj.vy * proj.vy);
        if (closestEnemy) {
          const angle = Math.atan2(closestEnemy.y - proj.y, closestEnemy.x - proj.x);
          proj.vx = Math.cos(angle) * speed;
          proj.vy = Math.sin(angle) * speed;
        } else {
          // Pas d'autre ennemi, rebondir à l'opposé du centre de masse
          const angle = Math.atan2(centerY - proj.y, centerX - proj.x);
          proj.vx = -Math.cos(angle) * speed;
          proj.vy = -Math.sin(angle) * speed;
        }

        proj.bounces++;

        // Supprimer après avoir dépassé maxBounces
        if (proj.bounces > proj.maxBounces) {
          state.bouncingProjectiles.splice(i, 1);
        }

        // Traiter les morts et XP (en ordre inverse pour les indices)
        const sortedByIndexDesc = [...hitEnemies].sort((a, b) => b.index - a.index);
        for (const hit of sortedByIndexDesc) {
          if (hit.enemy.health <= 0) {
            state.kills++;
            let xpGained = hit.enemy.xp;
            if (state.player.upgrades.global.xpBonus > 0) {
              xpGained = Math.floor(xpGained * (1 + state.player.upgrades.global.xpBonus * 0.1));
            }
            state.player.xp += xpGained;
            state.enemies.splice(hit.index, 1);

            if (state.player.xp >= state.player.xpForNextLevel) {
              levelUp();
            }
          }
        }
      }
    }

    // Player collision avec les ennemis
    if (state.player.iframes <= 0) {
      for (let i = state.enemies.length - 1; i >= 0; i--) {
        const enemy = state.enemies[i];
        const dx = state.player.x - enemy.x;
        const dy = state.player.y - enemy.y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < state.config.player.collisionRadius + enemy.radius) {
          const reducedDamage = applyDamageReduction(enemy.damage);
          state.player.health -= reducedDamage;
          state.player.iframes = state.config.player.iframeDuration;

          if (state.player.health <= 0) {
            gameOver();
          }
          break;
        }
      }
    }
  }

  function calculateXPForLevel(level) {
    // Nouvelle courbe de progression exponentielle douce
    if (level <= 1) return 50;
    if (level <= 5) {
      // Niveaux 2-5: progression rapide initiale
      return Math.floor(50 + (level - 1) * 30);  // 50, 80, 110, 140, 170
    }
    if (level <= 15) {
      // Niveaux 6-15: croissance modérée
      return Math.floor(170 + Math.pow(level - 5, 1.3) * 25);
    }
    // Niveau 16+: croissance exponentielle
    return Math.floor(645 + Math.pow(level - 15, 1.5) * 40);
  }

  function levelUp() {
    state.player.level++;
    state.player.xp = 0;
    state.player.xpForNextLevel = calculateXPForLevel(state.player.level);

    showLevelUpMenu();
    pauseGame();
  }

  // ============================================
  // CATALOGUE D'AMÉLIORATIONS
  // ============================================

  let UPGRADE_CATALOG = {
    // Améliorations globales
    global: {
      speed: {
        maxLevel: 10,
        effect: 25,
        label: 'Vitesse',
        description: '+25 vitesse de déplacement',
        icon: '💨'
      },
      maxHealth: {
        maxLevel: Infinity,
        effect: 20,
        label: 'Vie accrue',
        description: '+20 vie maximale et heal complet',
        icon: '❤️',
        special: true
      },
      lifeSteal: {
        maxLevel: 10,
        effect: 2,
        label: 'Vol de vie',
        description: '+2% de vol de vie',
        icon: '🩸',
        special: true
      },
      regeneration: {
        maxLevel: 15,
        effect: 1,
        label: 'Régénération',
        description: '+1 HP/seconde',
        icon: '💚',
        special: true
      },
      armor: {
        maxLevel: 10,
        effect: 5,
        label: 'Armure',
        description: '+5% réduction de dégâts',
        icon: '🛡️',
        special: true
      },
      critChance: {
        maxLevel: 10,
        effect: 5,
        label: 'Chance critique',
        description: '+5% chance de coup critique (×2 dégâts)',
        icon: '⚡',
        special: true
      },
      revives: {
        maxLevel: 3,
        effect: 1,
        label: 'Résurrection',
        description: '+1 résurrection automatique',
        icon: '✨',
        special: true
      },
      xpBonus: {
        maxLevel: 20,
        effect: 10,
        label: 'Boost XP',
        description: '+10% XP gagnée',
        icon: '📈'
      }
    },
    // Améliorations par arme
    weapons: {
      projectile: {
        projectileCount: {
          maxLevel: 10,
          effect: 1,
          label: 'Projectiles +1',
          description: '+1 projectile supplémentaire',
          icon: '🎯'
        },
        damagePercent: {
          maxLevel: 20,
          effect: 10,
          label: 'Dégâts +10%',
          description: '+10% dégâts',
          icon: '💥'
        },
        fireRatePercent: {
          maxLevel: 15,
          effect: 10,
          label: 'Cadence +10%',
          description: '+10% vitesse de tir',
          icon: '⚡'
        },
        speedPercent: {
          maxLevel: 10,
          effect: 15,
          label: 'Vitesse projectile +15%',
          description: '+15% vitesse des projectiles',
          icon: '🚀'
        },
        penetration: {
          maxLevel: 5,
          effect: 1,
          label: 'Pénétration +1',
          description: '+1 ennemi traversé',
          icon: '➡️'
        },
        lifetimePercent: {
          maxLevel: 8,
          effect: 20,
          label: 'Portée +20%',
          description: '+20% durée de vie (portée)',
          icon: '📏'
        },
        sizePercent: {
          maxLevel: 5,
          effect: 15,
          label: 'Taille +15%',
          description: '+15% taille (hitbox)',
          icon: '⭕'
        }
      },
      laser: {
        rangePercent: {
          maxLevel: 15,
          effect: 20,
          label: 'Portée laser +20%',
          description: '+20% distance du laser',
          icon: '📏'
        },
        damagePercent: {
          maxLevel: 20,
          effect: 15,
          label: 'Dégâts laser +15%',
          description: '+15% dégâts',
          icon: '💥'
        },
        widthPercent: {
          maxLevel: 10,
          effect: 25,
          label: 'Largeur laser +25%',
          description: '+25% largeur du rayon',
          icon: '↔️'
        },
        durationPercent: {
          maxLevel: 12,
          effect: 30,
          label: 'Durée laser +30%',
          description: '+30% durée d\'affichage',
          icon: '⏱️'
        },
        fireRatePercent: {
          maxLevel: 10,
          effect: 15,
          label: 'Cadence laser +15%',
          description: '+15% vitesse de tir',
          icon: '⚡'
        },
        multiLaser: {
          maxLevel: 3,
          effect: 1,
          label: 'Multi-laser +1',
          description: '+1 laser parallèle',
          icon: '🔱'
        }
      },
      aura: {
        radiusPercent: {
          maxLevel: 20,
          effect: 15,
          label: 'Rayon aura +15%',
          description: '+15% radius de l\'aura',
          icon: '🌊'
        },
        damagePercent: {
          maxLevel: 20,
          effect: 20,
          label: 'Dégâts aura +20%',
          description: '+20% DPS',
          icon: '💥'
        },
        slowPercent: {
          maxLevel: 10,
          effect: 5,
          label: 'Ralentissement +5%',
          description: 'Ralentit davantage les ennemis',
          icon: '🐌'
        },
        tickRatePercent: {
          maxLevel: 12,
          effect: 25,
          label: 'Fréquence aura +25%',
          description: '+25% fréquence des dégâts',
          icon: '⚡'
        },
        critChance: {
          maxLevel: 5,
          effect: 10,
          label: 'Critique aura +10%',
          description: '+10% chance de double dégât',
          icon: '✨'
        }
      },
      bouncing: {
        maxBounces: {
          maxLevel: 15,
          effect: 2,
          label: 'Rebonds +2',
          description: '+2 rebonds max',
          icon: '↩️'
        },
        damagePercent: {
          maxLevel: 20,
          effect: 15,
          label: 'Dégâts rebonds +15%',
          description: '+15% dégâts',
          icon: '💥'
        },
        speedPercent: {
          maxLevel: 10,
          effect: 15,
          label: 'Vitesse rebonds +15%',
          description: '+15% vitesse projectile',
          icon: '🚀'
        },
        fireRatePercent: {
          maxLevel: 12,
          effect: 10,
          label: 'Cadence rebonds +10%',
          description: '+10% vitesse de tir',
          icon: '⚡'
        },
        sizePercent: {
          maxLevel: 8,
          effect: 20,
          label: 'Taille rebonds +20%',
          description: '+20% taille projectile',
          icon: '⭕'
        },
        multiShot: {
          maxLevel: 5,
          effect: 1,
          label: 'Multi-shot +1',
          description: '+1 balle par tir',
          icon: '🎯'
        }
      },
      bomb: {
        explosionRadiusPercent: {
          maxLevel: 15,
          effect: 15,
          label: 'Rayon explosion +15%',
          description: '+15% radius explosion',
          icon: '💣'
        },
        damagePercent: {
          maxLevel: 20,
          effect: 20,
          label: 'Dégâts bombe +20%',
          description: '+20% dégâts',
          icon: '💥'
        },
        fireRatePercent: {
          maxLevel: 10,
          effect: 15,
          label: 'Cadence bombe +15%',
          description: '+15% vitesse de tir',
          icon: '⚡'
        },
        speedPercent: {
          maxLevel: 8,
          effect: 10,
          label: 'Vitesse bombe +10%',
          description: '+10% vitesse projectile',
          icon: '🚀'
        },
        multiShot: {
          maxLevel: 4,
          effect: 1,
          label: 'Multi-bombes +1',
          description: '+1 bombe par tir',
          icon: '💣'
        },
        burnDamage: {
          maxLevel: 5,
          effect: 10,
          label: 'Brûlure +10%',
          description: '+10% dégâts DoT sur 2s',
          icon: '🔥'
        }
      },
      blackhole: {
        pullRadiusPercent: {
          maxLevel: 15,
          effect: 15,
          label: 'Rayon aspiration +15%',
          description: '+15% rayon du vortex',
          icon: '🌀'
        },
        damagePercent: {
          maxLevel: 20,
          effect: 20,
          label: 'Dégâts vortex +20%',
          description: '+20% dégâts par tick',
          icon: '💥'
        },
        durationPercent: {
          maxLevel: 12,
          effect: 15,
          label: 'Durée vortex +15%',
          description: '+15% durée du vortex',
          icon: '⏱️'
        },
        tickRatePercent: {
          maxLevel: 10,
          effect: 20,
          label: 'Fréquence ticks +20%',
          description: '+20% ticks par seconde',
          icon: '⚡'
        },
        fireRatePercent: {
          maxLevel: 10,
          effect: 15,
          label: 'Cadence trou noir +15%',
          description: '+15% vitesse de lancer',
          icon: '🚀'
        },
        multiShot: {
          maxLevel: 3,
          effect: 1,
          label: 'Multi-vortex +1',
          description: '+1 trou noir par lancer',
          icon: '🌀'
        }
      }
    }
  };

  function generateUpgradeChoices() {
    const available = [];
    let weaponOffer = null;

    // 1. PROPOSITION D'ARME (niveau 5+, si 1 seule arme)
    // L'arme est TOUJOURS proposée en premier choix jusqu'à ce que le joueur en achète une
    if (state.player.weapons.length < 2 && state.player.level >= 5) {
      // Choisir une arme à proposer (rotation si refusée)
      if (!state.player.offeredWeapon) {
        const allWeapons = ['projectile', 'laser', 'aura', 'bouncing', 'bomb', 'blackhole'];
        const currentWeaponTypes = state.player.weapons.map(w => w.type);
        const availableWeapons = allWeapons.filter(w => !currentWeaponTypes.includes(w));
        state.player.offeredWeapon = availableWeapons[Math.floor(Math.random() * availableWeapons.length)];
      }

      const weaponNames = {
        projectile: 'Projectiles',
        laser: 'Laser transperçant',
        aura: 'Zone de repousse',
        bouncing: 'Balles rebondissantes',
        bomb: 'Bombes explosives',
        blackhole: 'Trou noir'
      };
      const weaponDescs = {
        projectile: 'Tirs automatiques vers les ennemis',
        laser: 'Rayon qui traverse tous les ennemis',
        aura: 'Ralentit et blesse les ennemis proches',
        bouncing: 'Projectiles qui rebondissent',
        bomb: 'Large zone de dégâts',
        blackhole: 'Aspire et endommage les ennemis'
      };

      // Stocker l'offre d'arme séparément pour la mettre en premier
      weaponOffer = {
        type: 'weaponUnlock',
        weaponType: state.player.offeredWeapon,
        title: `🆕 ${weaponNames[state.player.offeredWeapon]}`,
        description: weaponDescs[state.player.offeredWeapon],
        isNew: true
      };
    }

    // 2. AMÉLIORATIONS GLOBALES (sauf vitesse si >= 10)
    for (const [upgradeId, upgradeDef] of Object.entries(UPGRADE_CATALOG.global)) {
      const currentLevel = state.player.upgrades.global[upgradeId];

      // Vérifier si l'amélioration a atteint le max
      if (currentLevel >= upgradeDef.maxLevel) continue;

      // Vérifier si c'est un bonus spécial
      if (upgradeDef.special) {
        // Si le joueur a déjà 2 bonus spéciaux différents et celui-ci n'en fait pas partie, skip
        if (state.player.specialBonusesActive.length >= 2 &&
            !state.player.specialBonusesActive.includes(upgradeId)) {
          continue;
        }
      }

      const nextLevel = currentLevel + 1;
      available.push({
        type: 'globalUpgrade',
        upgradeId: upgradeId,
        title: `${upgradeDef.icon} ${upgradeDef.label}`,
        description: `${upgradeDef.description} • Palier ${nextLevel}/${upgradeDef.maxLevel === Infinity ? '∞' : upgradeDef.maxLevel}`,
        special: upgradeDef.special || false
      });
    }

    // 3. AMÉLIORATIONS D'ARMES
    for (const weapon of state.player.weapons) {
      const weaponType = weapon.type;
      const weaponUpgrades = UPGRADE_CATALOG.weapons[weaponType];

      if (!weaponUpgrades) continue;

      for (const [upgradeId, upgradeDef] of Object.entries(weaponUpgrades)) {
        const currentLevel = state.player.upgrades.weapons[weaponType][upgradeId];

        // Vérifier si l'amélioration a atteint le max
        if (currentLevel >= upgradeDef.maxLevel) continue;

        const nextLevel = currentLevel + 1;
        const weaponName = {
          projectile: 'Projectiles',
          laser: 'Laser',
          aura: 'Aura',
          bouncing: 'Rebonds',
          bomb: 'Bombe',
          blackhole: 'Trou noir'
        }[weaponType];

        available.push({
          type: 'weaponUpgrade',
          weaponType: weaponType,
          upgradeId: upgradeId,
          title: `${upgradeDef.icon} ${upgradeDef.label}`,
          description: `${weaponName} • ${upgradeDef.description} • Palier ${nextLevel}/${upgradeDef.maxLevel}`
        });
      }
    }

    // 4. TICKET GACHA (toujours disponible comme option)
    available.push({
      type: 'gachaTicket',
      title: '🎫 Ticket Gacha',
      description: 'Obtenir 1 ticket de tirage',
      special: true
    });

    // 5. Mélanger et prendre les choix
    const shuffled = available.sort(() => Math.random() - 0.5);

    // Si une arme est proposée, elle est TOUJOURS en premier + 4 autres choix
    // Sinon, 5 choix normaux
    if (weaponOffer) {
      return [weaponOffer, ...shuffled.slice(0, 4)];
    }
    return shuffled.slice(0, 5);
  }

  function showLevelUpMenu() {
    state.gameState = GameState.LEVELUP;

    const choices = generateUpgradeChoices();
    state.pendingLevelUpChoices = choices;

    renderLevelUpChoices(choices);

    showStatusBar();
    showOverlay('levelup');
    saveGameState();
  }

  function showPauseMenu() {
    if (state.gameState !== GameState.PLAYING) return;

    // Arrêter le jeu et passer en mode pause
    state.running = false;
    state.gameState = GameState.PAUSED;

    // Mettre à jour les stats de la partie actuelle
    if (elements.pauseTime) {
      elements.pauseTime.textContent = formatTime(state.elapsed);
    }
    if (elements.pauseWave) {
      elements.pauseWave.textContent = state.wave;
    }
    if (elements.pauseLevel) {
      elements.pauseLevel.textContent = state.player.level;
    }
    if (elements.pauseKills) {
      elements.pauseKills.textContent = state.kills;
    }

    // Générer les stats du joueur
    generatePlayerStatsDisplay();

    // Générer l'affichage des armes
    generateWeaponsDisplay();

    showStatusBar();
    showOverlay('pause');
    saveGameState();
  }


  function generatePlayerStatsDisplay() {
    if (!elements.pausePlayerStats) return;

    const globalUpgrades = state.player.upgrades.global;
    const stats = [];

    // Vitesse
    const currentSpeed = state.player.speed;
    stats.push({ label: 'Vitesse', value: currentSpeed });

    // Vie max
    stats.push({ label: 'Vie max', value: state.player.maxHealth });

    // Bonus spéciaux actifs
    if (globalUpgrades.lifeSteal > 0) {
      const percent = globalUpgrades.lifeSteal * 2;
      stats.push({ label: 'Vol de vie', value: `${percent}%` });
    }

    if (globalUpgrades.regeneration > 0) {
      stats.push({ label: 'Régénération', value: `${globalUpgrades.regeneration} HP/s` });
    }

    if (globalUpgrades.armor > 0) {
      const percent = globalUpgrades.armor * 5;
      stats.push({ label: 'Armure', value: `${percent}%` });
    }

    if (globalUpgrades.critChance > 0) {
      const percent = globalUpgrades.critChance * 5;
      stats.push({ label: 'Chance critique', value: `${percent}%` });
    }

    if (globalUpgrades.revives > 0) {
      const remaining = globalUpgrades.revives - state.player.usedRevives;
      stats.push({ label: 'Résurrections', value: `${remaining}/${globalUpgrades.revives}` });
    }

    if (globalUpgrades.xpBonus > 0) {
      const percent = globalUpgrades.xpBonus * 10;
      stats.push({ label: 'Bonus XP', value: `+${percent}%` });
    }

    // Générer le HTML
    elements.pausePlayerStats.innerHTML = stats.map(stat => `
      <div class="survivor-like__pause-stat-item">
        <span class="survivor-like__pause-stat-label">${stat.label}</span>
        <span class="survivor-like__pause-stat-value">${stat.value}</span>
      </div>
    `).join('');
  }

  function generateWeaponsDisplay() {
    if (!elements.pauseWeapons) return;

    const weaponNames = {
      projectile: { name: 'Projectiles', icon: '🎯' },
      laser: { name: 'Laser', icon: '⚡' },
      aura: { name: 'Aura', icon: '💫' },
      bouncing: { name: 'Rebonds', icon: '🔵' },
      bomb: { name: 'Bombe', icon: '💣' },
      blackhole: { name: 'Trou noir', icon: '🌀' }
    };

    elements.pauseWeapons.innerHTML = state.player.weapons.map(weapon => {
      const info = weaponNames[weapon.type] || { name: weapon.type, icon: '❓' };
      const config = weapon.config;
      const upgrades = state.player.upgrades.weapons[weapon.type];

      let statsHTML = '';

      // Afficher les stats pertinentes selon le type d'arme
      switch (weapon.type) {
        case 'projectile':
          statsHTML = `
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Dégâts</span>
              <span>${Math.floor(config.damage)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Tir/s</span>
              <span>${config.fireRate.toFixed(1)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Projectiles</span>
              <span>${config.projectileCount}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Pénétration</span>
              <span>${config.penetration}</span>
            </div>
          `;
          break;

        case 'laser':
          statsHTML = `
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Dégâts</span>
              <span>${Math.floor(config.damage)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Portée</span>
              <span>${Math.floor(config.range)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Largeur</span>
              <span>${Math.floor(config.width)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Lasers</span>
              <span>${config.multiLaser || 1}</span>
            </div>
          `;
          break;

        case 'aura':
          statsHTML = `
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Dégâts</span>
              <span>${Math.floor(config.damage)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Rayon</span>
              <span>${Math.floor(config.radius)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Ralentissement</span>
              <span>${Math.floor((1 - config.slowFactor) * 100)}%</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Tick/s</span>
              <span>${config.tickRate}</span>
            </div>
          `;
          break;

        case 'bouncing':
          statsHTML = `
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Dégâts</span>
              <span>${Math.floor(config.damage)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Rebonds</span>
              <span>${config.maxBounces}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Projectiles</span>
              <span>${(config.multiShot || 0) + 1}</span>
            </div>
          `;
          break;

        case 'bomb':
          statsHTML = `
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Dégâts</span>
              <span>${Math.floor(config.damage)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Rayon explosion</span>
              <span>${Math.floor(config.explosionRadius)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Brûlure</span>
              <span>${config.burnDamage ? Math.floor(config.burnDamage) : 0}/s</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Bombes</span>
              <span>${config.multiShot || 1}</span>
            </div>
          `;
          break;

        case 'blackhole':
          statsHTML = `
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Dégâts/tick</span>
              <span>${Math.floor(config.damage)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Rayon aspiration</span>
              <span>${Math.floor(config.pullRadius)}</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Durée</span>
              <span>${(config.duration / 1000).toFixed(1)}s</span>
            </div>
            <div class="survivor-like__pause-weapon-stat">
              <span class="survivor-like__pause-weapon-stat-label">Ticks/s</span>
              <span>${config.tickRate.toFixed(1)}</span>
            </div>
          `;
          break;
      }

      return `
        <div class="survivor-like__pause-weapon-card">
          <div class="survivor-like__pause-weapon-header">
            <span class="survivor-like__pause-weapon-icon">${info.icon}</span>
            <span class="survivor-like__pause-weapon-name">${info.name}</span>
          </div>
          <div class="survivor-like__pause-weapon-stats">
            ${statsHTML}
          </div>
        </div>
      `;
    }).join('');
  }

  function applyUpgrade(upgrade) {
    switch (upgrade.type) {
      case 'globalUpgrade':
        applyGlobalUpgrade(upgrade.upgradeId, upgrade.special);
        break;
      case 'weaponUpgrade':
        applyWeaponUpgrade(upgrade.weaponType, upgrade.upgradeId);
        break;
      case 'weaponUnlock':
        unlockWeapon(upgrade.weaponType);
        break;
      case 'gachaTicket':
        buyGachaTicket();
        break;
    }

    state.pendingLevelUpChoices = null;

    // Si le joueur a refusé l'arme proposée, en proposer une autre
    if (upgrade.type !== 'weaponUnlock' && state.player.offeredWeapon !== null && state.player.weapons.length < 2) {
      const allWeapons = ['projectile', 'laser', 'aura', 'bouncing', 'bomb', 'blackhole'];
      const currentWeapons = state.player.weapons.map(w => w.type);
      const available = allWeapons.filter(w => !currentWeapons.includes(w) && w !== state.player.offeredWeapon);

      if (available.length > 0) {
        state.player.offeredWeapon = available[Math.floor(Math.random() * available.length)];
      }
    }

    updateHUD();
    state.gameState = GameState.PLAYING;
    state.paused = false;
    state.running = true;
    hideAllOverlays();
    hideStatusBar();
    state.lastTime = performance.now();
    requestAnimationFrame(gameLoop);
    saveGameState();
  }

  function applyGlobalUpgrade(upgradeId, isSpecial) {
    const upgradeDef = UPGRADE_CATALOG.global[upgradeId];
    if (!upgradeDef) return;

    // Incrémenter le niveau de l'amélioration
    state.player.upgrades.global[upgradeId]++;

    // Appliquer l'effet
    switch (upgradeId) {
      case 'speed':
        state.player.speed += upgradeDef.effect;
        break;
      case 'maxHealth':
        state.player.maxHealth += upgradeDef.effect;
        state.player.health = state.player.maxHealth; // Heal complet
        break;
      case 'lifeSteal':
      case 'regeneration':
      case 'armor':
      case 'critChance':
      case 'revives':
      case 'xpBonus':
        // Ces effets sont appliqués de manière passive dans le gameLoop
        break;
    }

    // Si c'est un bonus spécial et qu'il n'est pas déjà dans la liste, l'ajouter
    if (isSpecial && !state.player.specialBonusesActive.includes(upgradeId)) {
      state.player.specialBonusesActive.push(upgradeId);
    }
  }

  function applyWeaponUpgrade(weaponType, upgradeId) {
    const upgradeDef = UPGRADE_CATALOG.weapons[weaponType][upgradeId];
    if (!upgradeDef) return;

    // Incrémenter le niveau de l'amélioration
    state.player.upgrades.weapons[weaponType][upgradeId]++;

    // Mettre à jour la config de l'arme en temps réel
    const weapon = state.player.weapons.find(w => w.type === weaponType);
    if (!weapon) return;

    recalculateWeaponStats(weapon);
  }

  function recalculateWeaponStats(weapon) {
    const weaponType = weapon.type;
    const baseConfig = state.config.weapons[weaponType];
    const upgrades = state.player.upgrades.weapons[weaponType];

    // Réinitialiser à la config de base
    weapon.config = { ...baseConfig };

    // Appliquer chaque amélioration
    switch (weaponType) {
      case 'projectile':
        weapon.config.projectileCount += upgrades.projectileCount;
        weapon.config.damage = Math.floor(baseConfig.damage * (1 + upgrades.damagePercent * 0.1));
        weapon.config.fireRate = baseConfig.fireRate * (1 + upgrades.fireRatePercent * 0.1);
        weapon.config.projectileSpeed = baseConfig.projectileSpeed * (1 + upgrades.speedPercent * 0.15);
        weapon.config.penetration = upgrades.penetration;
        weapon.config.projectileLifetime = baseConfig.projectileLifetime * (1 + upgrades.lifetimePercent * 0.2);
        weapon.config.projectileSize = baseConfig.projectileSize * (1 + upgrades.sizePercent * 0.15);
        break;

      case 'laser':
        weapon.config.range = baseConfig.range * (1 + upgrades.rangePercent * 0.2);
        weapon.config.damage = Math.floor(baseConfig.damage * (1 + upgrades.damagePercent * 0.15));
        weapon.config.width = baseConfig.width * (1 + upgrades.widthPercent * 0.25);
        weapon.config.duration = baseConfig.duration * (1 + upgrades.durationPercent * 0.3);
        weapon.config.fireRate = baseConfig.fireRate * (1 + upgrades.fireRatePercent * 0.15);
        weapon.config.multiLaser = upgrades.multiLaser;
        break;

      case 'aura':
        weapon.config.radius = baseConfig.radius * (1 + upgrades.radiusPercent * 0.15);
        weapon.config.damage = Math.floor(baseConfig.damage * (1 + upgrades.damagePercent * 0.2));
        weapon.config.slowFactor = baseConfig.slowFactor * (1 - upgrades.slowPercent * 0.05);
        weapon.config.tickRate = baseConfig.tickRate * (1 + upgrades.tickRatePercent * 0.25);
        weapon.config.critChance = upgrades.critChance * 0.1;
        break;

      case 'bouncing':
        weapon.config.maxBounces = baseConfig.maxBounces + upgrades.maxBounces * 2;
        weapon.config.damage = Math.floor(baseConfig.damage * (1 + upgrades.damagePercent * 0.15));
        weapon.config.speed = baseConfig.speed * (1 + upgrades.speedPercent * 0.15);
        weapon.config.fireRate = baseConfig.fireRate * (1 + upgrades.fireRatePercent * 0.1);
        weapon.config.size = baseConfig.size * (1 + upgrades.sizePercent * 0.2);
        weapon.config.multiShot = upgrades.multiShot;
        break;

      case 'bomb':
        weapon.config.explosionRadius = baseConfig.explosionRadius * (1 + upgrades.explosionRadiusPercent * 0.15);
        weapon.config.damage = Math.floor(baseConfig.damage * (1 + upgrades.damagePercent * 0.2));
        weapon.config.fireRate = baseConfig.fireRate * (1 + upgrades.fireRatePercent * 0.15);
        weapon.config.speed = baseConfig.speed * (1 + upgrades.speedPercent * 0.1);
        weapon.config.multiShot = upgrades.multiShot;
        weapon.config.burnDamage = upgrades.burnDamage * 0.1;
        break;

      case 'blackhole':
        weapon.config.pullRadius = baseConfig.pullRadius * (1 + upgrades.pullRadiusPercent * 0.15);
        weapon.config.damage = Math.floor(baseConfig.damage * (1 + upgrades.damagePercent * 0.2));
        weapon.config.duration = baseConfig.duration * (1 + upgrades.durationPercent * 0.15);
        weapon.config.tickRate = baseConfig.tickRate * (1 + upgrades.tickRatePercent * 0.2);
        weapon.config.fireRate = baseConfig.fireRate * (1 + upgrades.fireRatePercent * 0.15);
        weapon.config.multiShot = upgrades.multiShot;
        break;
    }
  }

  function buyGachaTicket() {
    const awardGacha = typeof gainGachaTickets === 'function'
      ? gainGachaTickets
      : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
        ? window.gainGachaTickets
        : null;
    if (typeof awardGacha === 'function') {
      awardGacha(1, { unlockTicketStar: true });
    }
  }

  function unlockWeapon(weaponType) {
    const weaponConfig = state.config.weapons[weaponType];
    if (!weaponConfig) return;

    const newWeapon = {
      type: weaponType,
      level: 1,
      lastShotTime: 0,
      config: { ...weaponConfig }
    };

    state.player.weapons.push(newWeapon);

    // Recalculer les stats de l'arme avec les améliorations existantes
    recalculateWeaponStats(newWeapon);

    // Clear offered weapon so no more weapons are offered (max 2 weapons)
    state.player.offeredWeapon = null;
  }

  function updateSpawning(deltaMs) {
    // Boss spawn timer
    const bossConfig = state.config.spawning.bossSpawn;
    if (bossConfig?.enabled) {
      state.nextBossTime -= deltaMs;
      if (state.nextBossTime <= 0) {
        spawnEnemy(true); // Force boss spawn
        state.nextBossTime = bossConfig.spawnIntervalSeconds * 1000;
      }
    }

    // Unblock normal spawns after block period
    if (state.bossSpawnBlocked && state.elapsed >= state.bossSpawnBlockedUntil) {
      state.bossSpawnBlocked = false;
    }

    // Normal enemy spawning
    state.nextSpawnTime -= deltaMs;
    if (state.nextSpawnTime <= 0) {
      spawnEnemy();
      state.nextSpawnTime = state.spawnInterval;
    }

    // Wave progression
    state.nextWaveTime -= deltaMs;
    if (state.nextWaveTime <= 0) {
      state.wave++;
      state.nextWaveTime = state.config.spawning.waveInterval;

      // Update spawn interval with exponential decay if configured
      const exponentialDecay = state.config.spawning.intervalDecayExponential;
      if (exponentialDecay && exponentialDecay < 1) {
        // Exponential: multiply by decay factor (e.g., 0.95 means 5% faster each wave)
        state.spawnInterval = Math.max(
          state.config.spawning.minInterval,
          state.spawnInterval * exponentialDecay
        );
      } else {
        // Linear: subtract fixed amount
        state.spawnInterval = Math.max(
          state.config.spawning.minInterval,
          state.spawnInterval - state.config.spawning.intervalDecayPerWave
        );
      }

      // Charger de nouveaux sprites pour la nouvelle vague
      initializeWaveSprites();
    }
  }

  function checkGachaReward() {
    const interval = state.config.rewards.gacha.survivalIntervalSeconds * 1000;
    if (state.elapsed - state.lastGachaTime >= interval) {
      state.lastGachaTime = state.elapsed;
      const amount = state.config.rewards.gacha.ticketAmount;
      const awardGacha = typeof gainGachaTickets === 'function'
        ? gainGachaTickets
        : typeof window !== 'undefined' && typeof window.gainGachaTickets === 'function'
          ? window.gainGachaTickets
          : null;
      if (typeof awardGacha === 'function') {
        awardGacha(amount, { unlockTicketStar: true });
      }
    }
  }

  function drawSprite(x, y, colIndex, rowType, scale = 1) {
    if (!spriteSheetLoaded || !spriteSheet) return false;

    const col = SPRITE_COLS[colIndex];
    const row = SPRITE_ROWS[rowType];
    if (!col || !row) return false;

    const drawWidth = col.w * scale;
    const drawHeight = row.h * scale;

    ctx.drawImage(
      spriteSheet,
      col.x, row.y, col.w, row.h,
      x - drawWidth / 2, y - drawHeight / 2, drawWidth, drawHeight
    );

    return true;
  }

  function render() {
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvasWidth, canvasHeight);

    // Draw tiled background (or fallback to solid color)
    if (backgroundsLoaded && currentBackgroundTile) {
      renderBackground();
    } else {
      ctx.fillStyle = '#0d0d0d';
      ctx.fillRect(0, 0, canvasWidth, canvasHeight);
    }

    ctx.save();

    ctx.translate(canvasWidth / 2 - state.camera.x, canvasHeight / 2 - state.camera.y);

    // Draw aura (behind everything else)
    const auraWeapon = state.player.weapons.find(w => w.type === 'aura');
    if (auraWeapon) {
      WeaponSystem.aura.render(ctx, auraWeapon, performance.now());
    }

    // Optimize rendering: only draw closest enemies and those on screen
    const renderMaxEnemies = state.config.spawning.renderMaxEnemies || 200;
    const currentTime = performance.now();

    // Calculate viewport bounds (with margin for smoother appearance)
    const viewportMargin = 100;
    const viewportLeft = state.camera.x - canvasWidth / 2 - viewportMargin;
    const viewportRight = state.camera.x + canvasWidth / 2 + viewportMargin;
    const viewportTop = state.camera.y - canvasHeight / 2 - viewportMargin;
    const viewportBottom = state.camera.y + canvasHeight / 2 + viewportMargin;

    // Filter and sort enemies by distance to player (for rendering priority)
    const enemiesWithDistance = state.enemies.map(enemy => {
      const dx = enemy.x - state.player.x;
      const dy = enemy.y - state.player.y;
      const distSq = dx * dx + dy * dy; // Use squared distance to avoid sqrt
      return { enemy, distSq };
    });

    // Sort by distance (closest first) and limit to renderMaxEnemies
    enemiesWithDistance.sort((a, b) => a.distSq - b.distSq);
    const enemiesToRender = enemiesWithDistance.slice(0, renderMaxEnemies);

    // Render sorted enemies
    for (const { enemy } of enemiesToRender) {
      const isBoss = enemy.type === 'miniBoss';

      // Additional viewport culling check (skip if completely off-screen)
      if (enemy.x < viewportLeft || enemy.x > viewportRight ||
          enemy.y < viewportTop || enemy.y > viewportBottom) {
        continue; // Skip rendering this enemy
      }

      // Draw enemy with animated sprite sheet or fallback to circle
      let spriteDrawn = drawEnemySprite(enemy, currentTime);

      if (!spriteDrawn) {
        // Fallback: draw circle
        ctx.fillStyle = enemy.color;
        ctx.beginPath();
        ctx.arc(enemy.x, enemy.y, enemy.radius, 0, Math.PI * 2);
        ctx.fill();
      }

      // Draw boss health bar
      if (isBoss) {
        // Health bar above boss
        const barWidth = enemy.radius * 2.5;
        const barHeight = 6;
        const barX = enemy.x - barWidth / 2;
        const barY = enemy.y - enemy.radius - 15;
        const healthPercent = enemy.health / enemy.maxHealth;

        // Background
        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
        ctx.fillRect(barX, barY, barWidth, barHeight);

        // Health fill
        ctx.fillStyle = healthPercent > 0.5 ? '#ff4444' : healthPercent > 0.25 ? '#ff8844' : '#ffaa44';
        ctx.fillRect(barX, barY, barWidth * healthPercent, barHeight);

        // Border
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 1;
        ctx.strokeRect(barX, barY, barWidth, barHeight);
      }
    }

    ctx.restore();

    // Draw lasers (in screen space, not world space)
    if (state.lasers.length > 0) {
      WeaponSystem.laser.render(ctx, state.lasers, performance.now());
    }

    ctx.save();
    ctx.translate(canvasWidth / 2 - state.camera.x, canvasHeight / 2 - state.camera.y);

    for (const proj of state.projectiles) {
      ctx.fillStyle = '#fff';
      ctx.beginPath();
      ctx.arc(proj.x, proj.y, proj.size, 0, Math.PI * 2);
      ctx.fill();
    }

    // Draw bouncing projectiles
    if (state.bouncingProjectiles.length > 0) {
      WeaponSystem.bouncing.render(ctx, state.bouncingProjectiles);
    }

    // Draw bombs and explosions
    if (state.bombs.length > 0 || state.explosions.length > 0) {
      WeaponSystem.bomb.render(ctx, state.bombs, state.explosions, performance.now());
    }

    // Draw blackholes
    if (state.blackholes.length > 0) {
      WeaponSystem.blackhole.render(ctx, state.blackholes, performance.now());
    }

    // Draw player with character sprite or fallback to circle
    ctx.save(); // Isolate player rendering

    const playerAlpha = state.player.iframes > 0 ? 0.5 : 1;
    ctx.globalAlpha = playerAlpha;
    ctx.globalCompositeOperation = 'source-over'; // Ensure proper transparency blending

    let playerSpriteDrawn = false;
    if (state.player.characterSprite) {
      // Draw character sprite (Mage, Robot, Ghost, ChatNoir, Skeleton, or Vortex)
      const spriteSize = 64; // Taille du sprite
      ctx.drawImage(
        state.player.characterSprite,
        state.player.x - spriteSize / 2,
        state.player.y - spriteSize / 2,
        spriteSize,
        spriteSize
      );
      playerSpriteDrawn = true;
    }

    if (!playerSpriteDrawn) {
      // Fallback: draw circle
      ctx.fillStyle = '#4a9df0';
      ctx.beginPath();
      ctx.arc(state.player.x, state.player.y, state.config.player.collisionRadius, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore(); // Restore context state

    ctx.restore();
  }

  function applyPassiveEffects(deltaSeconds) {
    const globalUpgrades = state.player.upgrades.global;

    // Régénération (limitée à 10% vie max par seconde)
    if (globalUpgrades.regeneration > 0) {
      const regenPerSecond = globalUpgrades.regeneration;
      // Limite : max 10% de la vie totale par seconde
      const maxRegenPerSecond = state.player.maxHealth * 0.1;
      const actualRegen = Math.min(regenPerSecond, maxRegenPerSecond);

      state.player.health = Math.min(
        state.player.maxHealth,
        state.player.health + actualRegen * deltaSeconds
      );
    }

    // Note: Les autres effets passifs sont appliqués directement lors des interactions
    // - lifeSteal: appliqué dans checkCollisions() lors des dégâts aux ennemis (limité à 10% vie max/s)
    // - armor: appliqué dans checkCollisions() lors des dégâts au joueur
    // - critChance: appliqué dans checkCollisions() lors des dégâts critiques
    // - revives: appliqué dans gameOver()
  }

  function gameLoop(timestamp) {
    if (!state.running || state.gameState !== GameState.PLAYING) return;

    const deltaMs = timestamp - state.lastTime;
    const deltaSeconds = deltaMs / 1000;
    state.lastTime = timestamp;

    state.elapsed += deltaMs;

    updatePlayer(deltaSeconds);
    updateCamera();
    updateWeapons(timestamp, deltaSeconds);
    updateProjectiles(deltaSeconds, timestamp);
    updateEnemies(deltaSeconds);
    applyBurnEffects(deltaSeconds, timestamp);
    updateSpawning(deltaMs);
    checkCollisions();
    checkGachaReward();
    applyPassiveEffects(deltaSeconds);

    render();
    updateHUD();

    requestAnimationFrame(gameLoop);
  }

  const AUTOSAVE_READY_EVENT = 'arcadeAutosaveReady';
  const AUTOSAVE_READY_FALLBACK_MS = 800;

  function getSaveCoreBridge() {
    if (typeof window === 'undefined') {
      return null;
    }
    return window.AndroidSaveCoreBridge || null;
  }

  function readSaveCoreValue(key) {
    const bridge = getSaveCoreBridge();
    if (!bridge || typeof bridge.get !== 'function') {
      return null;
    }
    try {
      return bridge.get(key);
    } catch (error) {
      console.warn('Survivor Like: unable to read SaveCore', error);
      return null;
    }
  }

  function writeSaveCoreValue(key, value) {
    const bridge = getSaveCoreBridge();
    if (!bridge || typeof bridge.set !== 'function') {
      return false;
    }
    try {
      return bridge.set(key, value);
    } catch (error) {
      console.warn('Survivor Like: unable to write SaveCore', error);
      return false;
    }
  }

  function parseStoredPayload(raw) {
    if (typeof raw !== 'string' || !raw.trim()) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch (error) {
      return null;
    }
  }

  function readLegacyAutosave() {
    const legacyApi = typeof window !== 'undefined' ? window.ArcadeAutosave : null;
    if (legacyApi && typeof legacyApi.get === 'function') {
      try {
        return legacyApi.get(GAME_ID);
      } catch (error) {
        return null;
      }
    }
    return null;
  }

  function clearLegacyAutosave() {
    const legacyApi = typeof window !== 'undefined' ? window.ArcadeAutosave : null;
    if (legacyApi && typeof legacyApi.clear === 'function') {
      try {
        legacyApi.clear(GAME_ID);
      } catch (error) {
        // Ignore legacy clear errors
      }
    }
  }

  function getAutosaveApi() {
    if (typeof window === 'undefined') return null;
    const storage = window.localStorage || null;
    const hasStorage = storage && typeof storage.getItem === 'function';
    const bridge = getSaveCoreBridge();
    const canSaveCore = bridge && typeof bridge.get === 'function' && typeof bridge.set === 'function';
    if (!canSaveCore && !hasStorage && !window.ArcadeAutosave) {
      return null;
    }
    return {
      get() {
        if (canSaveCore) {
          const raw = readSaveCoreValue(SAVE_CORE_KEY);
          const parsed = parseStoredPayload(raw);
          if (parsed && typeof parsed === 'object') {
            return parsed;
          }
        }
        const legacy = readLegacyAutosave();
        if (legacy && typeof legacy === 'object') {
          if (canSaveCore) {
            const serialized = JSON.stringify(legacy);
            if (writeSaveCoreValue(SAVE_CORE_KEY, serialized)) {
              if (hasStorage) {
                storage.removeItem(SAVE_CORE_KEY);
              }
              clearLegacyAutosave();
            }
          }
          return legacy;
        }
        if (hasStorage) {
          const parsed = parseStoredPayload(storage.getItem(SAVE_CORE_KEY));
          if (parsed && typeof parsed === 'object') {
            if (canSaveCore) {
              const serialized = JSON.stringify(parsed);
              if (writeSaveCoreValue(SAVE_CORE_KEY, serialized)) {
                storage.removeItem(SAVE_CORE_KEY);
              }
            }
            return parsed;
          }
        }
        return null;
      },
      set(_key, payload) {
        if (!payload || typeof payload !== 'object') {
          return;
        }
        const serialized = JSON.stringify(payload);
        if (canSaveCore && writeSaveCoreValue(SAVE_CORE_KEY, serialized)) {
          if (hasStorage) {
            storage.removeItem(SAVE_CORE_KEY);
          }
          return;
        }
        if (hasStorage) {
          storage.setItem(SAVE_CORE_KEY, serialized);
        }
      },
      clear() {
        if (canSaveCore && writeSaveCoreValue(SAVE_CORE_KEY, null)) {
          if (hasStorage) {
            storage.removeItem(SAVE_CORE_KEY);
          }
          return;
        }
        if (hasStorage) {
          storage.removeItem(SAVE_CORE_KEY);
        }
      }
    };
  }

  function waitForAutosaveReady() {
    return new Promise((resolve) => {
      if (getAutosaveApi()) {
        resolve();
        return;
      }

      let resolved = false;
      let fallbackTimer = null;

      const finalize = () => {
        if (resolved) return;
        resolved = true;
        window.removeEventListener(AUTOSAVE_READY_EVENT, handleReady);
        if (fallbackTimer != null) {
          window.clearTimeout(fallbackTimer);
          fallbackTimer = null;
        }
        resolve();
      };

      const handleReady = () => finalize();

      window.addEventListener(AUTOSAVE_READY_EVENT, handleReady);
      fallbackTimer = window.setTimeout(finalize, AUTOSAVE_READY_FALLBACK_MS);
    });
  }

  function loadRecords() {
    const autosaveApi = getAutosaveApi();
    if (!autosaveApi) return;

    const saved = autosaveApi.get(GAME_ID);
    if (saved) {
      state.bestTime = saved.bestTime || 0;
      state.bestLevel = saved.bestLevel || 1;
      state.bestTimeByHero = extractBestTimeByHero(saved.bestTimeByHero);
      state.bestKillsByHero = extractBestKillsByHero(saved.bestKillsByHero);
    }

    if (elements.bestTimeValue) {
      elements.bestTimeValue.textContent = state.bestTime > 0 ? formatTime(state.bestTime) : '—';
    }
    if (elements.bestLevelValue) {
      elements.bestLevelValue.textContent = state.bestLevel > 1 ? state.bestLevel : '—';
    }

    updateHeroRecordDisplay();
  }

  function normalizeHeroId(raw) {
    if (typeof raw !== 'string' || !raw.trim()) {
      return null;
    }
    const normalized = raw.trim().toLowerCase();
    return HERO_IDS.find(hero => hero.toLowerCase() === normalized) || null;
  }

  function extractBestTimeByHero(raw) {
    if (!raw || typeof raw !== 'object') {
      return {};
    }
    return HERO_IDS.reduce((acc, hero) => {
      const value = Number(raw[hero]);
      if (Number.isFinite(value) && value > 0) {
        acc[hero] = value;
      }
      return acc;
    }, {});
  }

  function extractBestKillsByHero(raw) {
    if (!raw || typeof raw !== 'object') {
      return {};
    }
    return HERO_IDS.reduce((acc, hero) => {
      const value = Number(raw[hero]);
      if (Number.isFinite(value) && value > 0) {
        acc[hero] = Math.floor(value);
      }
      return acc;
    }, {});
  }

  function saveRecords(options = {}) {
    const autosaveApi = getAutosaveApi();
    if (!autosaveApi) return;

    // Préserver savedGame existant lors de la sauvegarde des records
    const currentData = autosaveApi.get(GAME_ID) || {};
    const bestTimeByHero = extractBestTimeByHero(currentData.bestTimeByHero);
    const bestKillsByHero = extractBestKillsByHero(currentData.bestKillsByHero);
    let shouldPersist = false;

    const heroId = normalizeHeroId(options.heroId || state.player.characterType);
    const heroTime = Number(options.heroTime ?? state.elapsed);
    const heroKills = Number(options.heroKills ?? state.kills);
    if (heroId && Number.isFinite(heroTime) && heroTime > 0) {
      const previousHeroTime = Number(bestTimeByHero[heroId]) || 0;
      if (heroTime > previousHeroTime) {
        bestTimeByHero[heroId] = heroTime;
        shouldPersist = true;
      }
    }
    if (heroId && Number.isFinite(heroKills) && heroKills > 0) {
      const previousHeroKills = Number(bestKillsByHero[heroId]) || 0;
      if (heroKills > previousHeroKills) {
        bestKillsByHero[heroId] = Math.floor(heroKills);
        shouldPersist = true;
      }
    }

    if (state.bestTime > (Number(currentData.bestTime) || 0)) {
      shouldPersist = true;
    }
    if (state.bestLevel > (Number(currentData.bestLevel) || 1)) {
      shouldPersist = true;
    }

    if (!shouldPersist) {
      return;
    }

    autosaveApi.set(GAME_ID, {
      ...currentData,
      bestTime: state.bestTime,
      bestLevel: state.bestLevel,
      bestTimeByHero,
      bestKillsByHero,
      updatedAt: Date.now()
    });

    const globalState = window.gameState || window.atom2universGameState;
    if (globalState) {
      if (!globalState.arcadeProgress) {
        globalState.arcadeProgress = { version: 1, entries: {} };
      }
      if (!globalState.arcadeProgress.entries) {
        globalState.arcadeProgress.entries = {};
      }

      // Préserver les données existantes (comme savedGame)
      const existingEntry = globalState.arcadeProgress.entries[GAME_ID] || {};
      const existingState = existingEntry.state && typeof existingEntry.state === 'object'
        ? existingEntry.state
        : null;
      const updatedState = existingState
        ? {
          ...existingState,
          bestTime: state.bestTime,
          bestLevel: state.bestLevel,
          bestTimeByHero,
          bestKillsByHero
        }
        : null;
      globalState.arcadeProgress.entries[GAME_ID] = existingState
        ? {
          ...existingEntry,
          state: updatedState,
          updatedAt: Date.now()
        }
        : {
          ...existingEntry,
          bestTime: state.bestTime,
          bestLevel: state.bestLevel,
          bestTimeByHero,
          bestKillsByHero,
          updatedAt: Date.now()
        };

      if (typeof window.saveGame === 'function') {
        window.saveGame();
      }
    }

    if (elements.bestTimeValue) {
      elements.bestTimeValue.textContent = formatTime(state.bestTime);
    }
    if (elements.bestLevelValue) {
      elements.bestLevelValue.textContent = state.bestLevel;
    }

    state.bestTimeByHero = bestTimeByHero;
    state.bestKillsByHero = bestKillsByHero;
    updateHeroRecordDisplay();
  }

  function buildSavedGamePayload() {
    return {
      elapsed: state.elapsed,
      wave: state.wave,
      kills: state.kills,
      gameState: state.gameState,
      paused: state.paused,
      nextSpawnTime: state.nextSpawnTime,
      nextWaveTime: state.nextWaveTime,
      spawnInterval: state.spawnInterval,
      lastGachaTime: state.lastGachaTime,
      nextBossTime: state.nextBossTime,
      bossSpawnBlocked: state.bossSpawnBlocked,
      bossSpawnBlockedUntil: state.bossSpawnBlockedUntil,
      backgroundTile: currentBackgroundTile ? { ...currentBackgroundTile } : null,
      pendingLevelUpChoices: serializeLevelUpChoices(state.pendingLevelUpChoices),
      player: {
        x: state.player.x,
        y: state.player.y,
        speed: state.player.speed,
        health: state.player.health,
        maxHealth: state.player.maxHealth,
        xp: state.player.xp,
        level: state.player.level,
        xpForNextLevel: state.player.xpForNextLevel,
        weapons: state.player.weapons.map(w => ({
          type: w.type,
          level: w.level,
          config: { ...w.config }
        })),
        weaponSlots: state.player.weaponSlots,
        skinIndex: state.player.skinIndex,
        characterType: state.player.characterType,
        offeredWeapon: state.player.offeredWeapon,
        upgrades: JSON.parse(JSON.stringify(state.player.upgrades)), // Deep copy
        specialBonusesActive: [...state.player.specialBonusesActive],
        usedRevives: state.player.usedRevives || 0
      },
      // Ne pas sauvegarder les ennemis et projectiles (régénérés au chargement)
      savedAt: Date.now()
    };
  }

  function syncSavedGameToArcadeProgress(savedGame) {
    const globalState = window.gameState || window.atom2universGameState;
    if (!globalState || !savedGame) return;

    if (!globalState.arcadeProgress || typeof globalState.arcadeProgress !== 'object') {
      globalState.arcadeProgress = { version: 1, entries: {} };
    }
    if (!globalState.arcadeProgress.entries || typeof globalState.arcadeProgress.entries !== 'object') {
      globalState.arcadeProgress.entries = {};
    }

    const existingEntry = globalState.arcadeProgress.entries[GAME_ID] || {};
    globalState.arcadeProgress.entries[GAME_ID] = {
      ...existingEntry,
      savedGame,
      updatedAt: Date.now()
    };

    if (typeof window.saveGame === 'function') {
      window.saveGame();
    }
  }

  function saveGameState() {
    const autosaveApi = getAutosaveApi();
    if (!autosaveApi) return;

    const currentData = autosaveApi.get(GAME_ID) || {};
    const savedGame = buildSavedGamePayload();

    autosaveApi.set(GAME_ID, {
      ...currentData,
      savedGame
    });

    syncSavedGameToArcadeProgress(savedGame);
  }

  function serializeLevelUpChoices(choices) {
    if (!Array.isArray(choices)) {
      return null;
    }
    const normalized = choices.map((choice) => {
      if (!choice || typeof choice !== 'object' || typeof choice.type !== 'string') {
        return null;
      }
      return {
        type: choice.type,
        weaponType: choice.weaponType || null,
        upgradeId: choice.upgradeId || null,
        title: choice.title || '',
        description: choice.description || '',
        isNew: Boolean(choice.isNew),
        special: Boolean(choice.special)
      };
    }).filter(Boolean);
    return normalized.length ? normalized : null;
  }

  function normalizeSavedLevelUpChoices(choices) {
    if (!Array.isArray(choices)) {
      return null;
    }
    const normalized = choices.map(choice => {
      if (!choice || typeof choice !== 'object') {
        return null;
      }
      if (typeof choice.type !== 'string') {
        return null;
      }
      return {
        type: choice.type,
        weaponType: typeof choice.weaponType === 'string' ? choice.weaponType : null,
        upgradeId: typeof choice.upgradeId === 'string' ? choice.upgradeId : null,
        title: typeof choice.title === 'string' ? choice.title : '',
        description: typeof choice.description === 'string' ? choice.description : '',
        isNew: Boolean(choice.isNew),
        special: Boolean(choice.special)
      };
    }).filter(Boolean);
    return normalized.length ? normalized : null;
  }

  function renderLevelUpChoices(choices) {
    if (!elements.upgradeChoices) return;
    const safeChoices = Array.isArray(choices) ? choices : [];
    elements.upgradeChoices.innerHTML = '';

    safeChoices.forEach(upgrade => {
      const card = document.createElement('div');
      card.className = 'survivor-like__upgrade-card';
      if (upgrade.isNew) {
        card.classList.add('survivor-like__upgrade-card--new');
      }
      card.innerHTML = `
        <div class="survivor-like__upgrade-title">${upgrade.title}</div>
        <div class="survivor-like__upgrade-description">${upgrade.description}</div>
      `;
      card.addEventListener('click', () => applyUpgrade(upgrade));
      elements.upgradeChoices.appendChild(card);
    });

    const savedGame = buildSavedGamePayload();
    syncSavedGameToArcadeProgress(savedGame);
  }

  function loadGameState() {
    const autosaveApi = getAutosaveApi();
    if (!autosaveApi) return false;

    const saved = autosaveApi.get(GAME_ID);
    if (!saved || !saved.savedGame) return false;

    const savedGame = saved.savedGame;

    // Restore game state
    state.elapsed = savedGame.elapsed;
    state.wave = savedGame.wave;
    state.kills = savedGame.kills;
    state.gameState = Object.values(GameState).includes(savedGame.gameState)
      ? savedGame.gameState
      : GameState.PLAYING;
    state.paused = Boolean(savedGame.paused);
    state.nextSpawnTime = savedGame.nextSpawnTime;
    state.nextWaveTime = savedGame.nextWaveTime;
    state.spawnInterval = savedGame.spawnInterval;
    state.lastGachaTime = savedGame.lastGachaTime;
    // Restore boss timer - if not saved, initialize with full interval
    const bossConfig = state.config.spawning.bossSpawn;
    if (savedGame.nextBossTime !== undefined && savedGame.nextBossTime !== null) {
      state.nextBossTime = savedGame.nextBossTime;
    } else {
      state.nextBossTime = bossConfig?.enabled ? (bossConfig.spawnIntervalSeconds * 1000) : 0;
    }
    state.bossSpawnBlocked = savedGame.bossSpawnBlocked || false;
    state.bossSpawnBlockedUntil = savedGame.bossSpawnBlockedUntil || 0;

    const restoredBackground = savedGame.backgroundTile;
    if (restoredBackground && Number.isFinite(restoredBackground.imageIndex)) {
      currentBackgroundTile = {
        imageIndex: restoredBackground.imageIndex,
        tileX: restoredBackground.tileX || 0,
        tileY: restoredBackground.tileY || 0
      };
    } else {
      selectRandomBackgroundTile();
    }

    state.pendingLevelUpChoices = normalizeSavedLevelUpChoices(savedGame.pendingLevelUpChoices);

    // Restore player
    state.player.x = savedGame.player.x;
    state.player.y = savedGame.player.y;
    state.player.speed = savedGame.player.speed;
    state.player.health = savedGame.player.health;
    state.player.maxHealth = savedGame.player.maxHealth;
    state.player.xp = savedGame.player.xp;
    state.player.level = savedGame.player.level;
    state.player.xpForNextLevel = savedGame.player.xpForNextLevel;

    // Migration ancien format weapon → nouveau format weapons[]
    if (savedGame.player.weapon && !savedGame.player.weapons) {
      state.player.weapons = [{
        type: 'projectile',
        level: 1,
        lastShotTime: 0,
        config: { ...savedGame.player.weapon }
      }];
      state.player.weaponSlots = state.config.player.weaponSlots || 5;
    } else {
      state.player.weapons = (savedGame.player.weapons || []).map(w => ({
        type: w.type,
        level: w.level,
        lastShotTime: 0,
        config: { ...w.config }
      }));
      state.player.weaponSlots = savedGame.player.weaponSlots || state.config.player.weaponSlots || 5;
    }

    state.player.iframes = 0;
    state.player.skinIndex = savedGame.player.skinIndex || 0;
    state.player.characterType = savedGame.player.characterType || null;
    state.player.offeredWeapon = savedGame.player.offeredWeapon || null;

    // Restore upgrades (with migration support)
    if (savedGame.player.upgrades) {
      state.player.upgrades = JSON.parse(JSON.stringify(savedGame.player.upgrades));

      // Migration: s'assurer que toutes les armes ont leurs upgrades initialisées
      if (!state.player.upgrades.weapons.blackhole) {
        state.player.upgrades.weapons.blackhole = {
          pullRadiusPercent: 0,
          damagePercent: 0,
          durationPercent: 0,
          tickRatePercent: 0,
          fireRatePercent: 0,
          multiShot: 0
        };
      }
    } else {
      // Migration: initialiser avec les valeurs par défaut
      initPlayer(); // Réinitialise upgrades
    }

    // Restore special bonuses
    state.player.specialBonusesActive = savedGame.player.specialBonusesActive || [];

    // Restore used revives
    state.player.usedRevives = savedGame.player.usedRevives || 0;

    // Recalculer les stats de toutes les armes avec les upgrades
    for (const weapon of state.player.weapons) {
      recalculateWeaponStats(weapon);
    }

    // Reload character sprite after loading save
    if (state.player.characterType) {
      loadCharacterSprite();
    }

    // Réinitialiser les ennemis et projectiles (pas sauvegardés)
    state.enemies = [];
    state.projectiles = [];
    state.lasers = [];
    state.bouncingProjectiles = [];
    state.bombs = [];
    state.explosions = [];
    state.blackholes = [];

    updateHUD();
    // Charger les sprites d'ennemis pour la vague actuelle
    initializeWaveSprites();

    if (state.gameState === GameState.LEVELUP) {
      if (!state.pendingLevelUpChoices) {
        state.pendingLevelUpChoices = generateUpgradeChoices();
      }
      renderLevelUpChoices(state.pendingLevelUpChoices);
      state.running = false;
      state.paused = true;
      showStatusBar();
      showOverlay('levelup');
    }
    return true;
  }

  function clearGameState() {
    const autosaveApi = getAutosaveApi();
    if (!autosaveApi) return;

    const currentData = autosaveApi.get(GAME_ID) || {};
    delete currentData.savedGame;
    autosaveApi.set(GAME_ID, currentData);

    const globalState = window.gameState || window.atom2universGameState;
    if (!globalState || !globalState.arcadeProgress || !globalState.arcadeProgress.entries) {
      return;
    }
    const existingEntry = globalState.arcadeProgress.entries[GAME_ID];
    if (existingEntry && Object.prototype.hasOwnProperty.call(existingEntry, 'savedGame')) {
      delete existingEntry.savedGame;
      existingEntry.updatedAt = Date.now();
      if (typeof window.saveGame === 'function') {
        window.saveGame();
      }
    }
  }

  function hasSavedGame() {
    const autosaveApi = getAutosaveApi();
    if (!autosaveApi) return false;

    const saved = autosaveApi.get(GAME_ID);
    return !!(saved && saved.savedGame);
  }

  function updateContinueButton() {
    if (!elements.continueButton) return;

    if (hasSavedGame()) {
      elements.continueButton.style.display = '';
    } else {
      elements.continueButton.style.display = 'none';
    }
  }

  function setupEventListeners() {
    if (elements.startButton) {
      elements.startButton.addEventListener('click', () => {
        state.selectedHero = null; // Random hero
        startGame();
      });
    }
    if (elements.continueButton) {
      elements.continueButton.addEventListener('click', continueGame);
    }
    if (elements.restartButton) {
      elements.restartButton.addEventListener('click', startGame);
    }
    if (elements.quitButton) {
      elements.quitButton.addEventListener('click', () => {
        state.gameState = GameState.MENU;
        showStatusBar();
        showOverlay('start');
      });
    }
    if (elements.pauseButton) {
      elements.pauseButton.addEventListener('click', showPauseMenu);
    }
    if (elements.resumeButton) {
      elements.resumeButton.addEventListener('click', resumeGame);
    }
    if (elements.pauseQuitButton) {
      elements.pauseQuitButton.addEventListener('click', () => {
        state.gameState = GameState.MENU;
        showStatusBar();
        showOverlay('start');
      });
    }

    // Exit buttons (croix en haut à droite)
    const exitHandler = () => {
      // Restaurer l'app-header avant de quitter
      const appHeader = document.querySelector('.app-header');
      if (appHeader) {
        appHeader.style.display = '';
      }
      const statusBar = document.querySelector('.status-bar');
      if (statusBar) {
        statusBar.style.display = '';
      }
      const frenzyStatus = document.querySelector('.frenzy-status');
      if (frenzyStatus) {
        frenzyStatus.style.display = '';
      }
      // Naviguer vers le hub arcade
      if (typeof window.showPage === 'function') {
        window.showPage('arcadeHub');
      }
    };

    if (elements.exitButton) {
      elements.exitButton.addEventListener('click', exitHandler);
    }
    if (elements.exitButtonLevelUp) {
      elements.exitButtonLevelUp.addEventListener('click', exitHandler);
    }
    if (elements.exitButtonGameOver) {
      elements.exitButtonGameOver.addEventListener('click', exitHandler);
    }
    if (elements.exitButtonPause) {
      elements.exitButtonPause.addEventListener('click', exitHandler);
    }

    // Hero selection cards
    if (elements.heroCards) {
      elements.heroCards.forEach(card => {
        card.addEventListener('click', () => {
          const heroName = card.dataset.hero;
          if (heroName) {
            // Set selected hero and start game
            state.selectedHero = heroName;
            startGame();
          }
        });
      });
    }
  }

  function hideStatusBar() {
    const statusBar = document.querySelector('.status-bar');
    const frenzyStatus = document.querySelector('.frenzy-status');
    const appHeader = document.querySelector('.app-header');
    if (statusBar) {
      statusBar.style.display = 'none';
    }
    if (frenzyStatus) {
      frenzyStatus.style.display = 'none';
    }
    if (appHeader) {
      appHeader.style.display = 'none';
    }
  }

  function showStatusBar() {
    const statusBar = document.querySelector('.status-bar');
    const frenzyStatus = document.querySelector('.frenzy-status');
    // Ne pas afficher app-header dans Survivor Like pour éviter le décalage
    if (statusBar) {
      statusBar.style.display = '';
    }
    if (frenzyStatus) {
      frenzyStatus.style.display = '';
    }
  }

  function pauseGame() {
    if (state.gameState === GameState.PLAYING && state.running && !state.paused) {
      state.running = false;
      state.paused = true;
      saveGameState();
    }
  }

  function resumeGame() {
    // Cas 1: Reprendre depuis le menu pause (gameState === PAUSED)
    if (state.gameState === GameState.PAUSED && !state.running) {
      state.gameState = GameState.PLAYING;
      state.paused = false;
      state.running = true;
      state.lastTime = performance.now();
      hideAllOverlays();
      hideStatusBar();
      requestAnimationFrame(gameLoop);
      return;
    }

    // Cas 2: Reprendre après une pause technique (quand on revient sur la page)
    if (state.gameState === GameState.PLAYING && state.paused && !state.running) {
      state.paused = false;
      state.running = true;
      state.lastTime = performance.now();
      hideStatusBar();
      requestAnimationFrame(gameLoop);
    }
  }

  function setupPageVisibility() {
    if (!elements.page) return;

    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'attributes' && mutation.attributeName === 'hidden') {
          if (elements.page.hasAttribute('hidden')) {
            // Quand on quitte Survivor Like, restaurer app-header
            const appHeader = document.querySelector('.app-header');
            if (appHeader) {
              appHeader.style.display = '';
            }
            showStatusBar();
            pauseGame();
          } else {
            // Quand on revient sur Survivor Like, cacher app-header
            const appHeader = document.querySelector('.app-header');
            if (appHeader) {
              appHeader.style.display = 'none';
            }
            // Ne pas cacher la bannière si on est dans un menu
            if (state.gameState === GameState.PLAYING) {
              hideStatusBar();
            }
            // Ne pas reprendre automatiquement si on est en pause ou dans un autre état
            if (state.gameState === GameState.PLAYING && state.paused) {
              resumeGame();
            }
          }
        }
      });
    });

    observer.observe(elements.page, { attributes: true });

    // Ne cacher la bannière que si le jeu est en cours
    if (!elements.page.hasAttribute('hidden') && state.gameState === GameState.PLAYING) {
      hideStatusBar();
    }
  }

  async function init() {
    await loadConfig();
    loadSpriteSheet();
    loadBackgroundImages();
    initCanvas();
    setupJoystick();
    setupEventListeners();
    setupPageVisibility();

    // Attendre que l'API autosave soit prête avant de charger les records
    await waitForAutosaveReady();
    loadRecords();
    updateHUD();
    updateContinueButton();

    // S'assurer que app-header est cachée au démarrage
    const appHeader = document.querySelector('.app-header');
    if (appHeader) {
      appHeader.style.display = 'none';
    }
    showOverlay('start');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => init());
  } else {
    init();
  }
})();
