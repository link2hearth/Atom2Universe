package com.Atom2Universe.app.crypto.clicker

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import com.Atom2Universe.app.crypto.gacha.completedRarityCount
import com.Atom2Universe.app.crypto.gacha.frenzyChanceForCompletedRarities
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class ClickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository            = ClickerRepository(application)
    private val statsRepository       = ClickerStatsRepository(application)
    private val offlineRepo           = ClickerOfflineRepository(application)
    private val neutrinoRepo          = NeutrinoRepository(application)
    private val elementTokenRepo      = ElementTokenRepository(application)
    private val achievementRepository = ClickerAchievementRepository(application)
    private val factoryRepo           = FactoryRepository(application)
    private val bigBangRepo           = BigBangRepository(application)
    private val frenzyManager         = FrenzyManager()
    private val collectionStore       = PeriodicCollectionStore(application)
    private val fusionStore           = com.Atom2Universe.app.crypto.fusion.FusionStore(application)

    private var bigBangEffects = BigBangEngine.computeEffects(bigBangRepo)

    private val _state = MutableStateFlow(ClickerGameState())
    val state: StateFlow<ClickerGameState> = _state.asStateFlow()

    private val _frenzyUiState = MutableStateFlow(FrenzyUiState())
    val frenzyUiState: StateFlow<FrenzyUiState> = _frenzyUiState.asStateFlow()

    private val _achievementUnlocked = MutableSharedFlow<ClickerAchievement>(extraBufferCapacity = 10)
    val achievementUnlocked: SharedFlow<ClickerAchievement> = _achievementUnlocked.asSharedFlow()

    private val unlockedAchievementIds = mutableSetOf<String>()

    // Stats en mémoire
    private var stats = ClickerStats()
    private var gameLoopStartMs = 0L
    private val recentClickTimes = ArrayDeque<Long>() // pour CPS glissant (1s)

    @Volatile private var initCompleted = false

    private var cachedElementBonuses: ElementBonuses? = null
    private var cachedFrenzyChance: Double? = null

    private var apsJob: Job? = null

    // Référence forte obligatoire — sinon le GC supprime le listener silencieusement.
    private val pendingResetListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "reset_stats" || key == "reset_clicker") {
            val resetStats   = prefs.getBoolean("reset_stats",   false)
            val resetClicker = prefs.getBoolean("reset_clicker", false)
            if (resetStats || resetClicker) {
                prefs.edit { clear() }
                applyPendingReset(resetStats, resetClicker)
            }
        }
    }

    private val devOpsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "atoms_op" -> {
                val op = prefs.getString("atoms_op", null) ?: return@OnSharedPreferenceChangeListener
                prefs.edit { remove("atoms_op") }
                if (!op.startsWith("add_")) return@OnSharedPreferenceChangeListener
                val n = op.removePrefix("add_").toDoubleOrNull() ?: return@OnSharedPreferenceChangeListener
                val toAdd = LayeredNumber.one().multiplyNumber(n)
                val s = _state.value
                _state.value = s.copy(atoms = s.atoms.add(toAdd), lifetime = s.lifetime.add(toAdd))
            }
            "neutrinos_refresh" -> {
                prefs.edit { remove("neutrinos_refresh") }
                _state.value = _state.value.copy(neutrinos = neutrinoRepo.getBalance())
            }
        }
    }

    private var autoSaveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var ticketCheckJob: Job? = null

    init {
        getApplication<Application>().getSharedPreferences("pending_reset_flags", android.content.Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(pendingResetListener)
        getApplication<Application>().getSharedPreferences("dev_ops_prefs", android.content.Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(devOpsListener)

        viewModelScope.launch {
            // Capturer lastOnline avant tout appel suspendu : le heartbeatJob démarre en
            // parallèle et écraserait last_online_ms après son premier tick (1s).
            val lastOnline = offlineRepo.load()
            val loaded = repository.load().copy(factoryCounts = factoryRepo.getAllCounts())

            // Gain hors-ligne : APS réel (shop + éléments, sans frénésie) × temps écoulé
            val now = System.currentTimeMillis()
            val elapsedMs = if (lastOnline > 0L) now - lastOnline else 0L
            var offlineInitGain = LayeredNumber.zero()
            val withGain = if (elapsedMs > 5_000L) {
                val baseAps = computeOfflineAps(loaded)
                val gained  = baseAps.multiplyNumber(elapsedMs / 1000.0)
                if (!gained.isZero()) {
                    offlineInitGain = gained
                    loaded.copy(
                        atoms    = loaded.atoms.add(gained),
                        lifetime = loaded.lifetime.add(gained)
                    )
                } else loaded
            } else loaded

            offlineRepo.save(now)
            val recalcedState = recalcProduction(withGain)
            _state.value = recalcedState
            stats = statsRepository.load()
            if (!offlineInitGain.isZero()) {
                stats = stats.copy(lifetimeApsAtoms = stats.lifetimeApsAtoms.add(offlineInitGain))
            }
            unlockedAchievementIds.addAll(achievementRepository.loadUnlocked())
            // Débloquer silencieusement les succès acquis hors-ligne (sans animation)
            checkAchievements(recalcedState.lifetime, emitEvents = false)

            // SharedPrefs est la source de vérité pour les neutrinos.
            // Migration : si SharedPrefs n'a jamais été initialisé mais que la DB a un solde, initialiser SharedPrefs.
            val dbNeutrinos = recalcedState.neutrinos
            val neutrinos = if (!neutrinoRepo.isBalanceInitialized() && dbNeutrinos > 0) {
                neutrinoRepo.setBalance(dbNeutrinos)
                dbNeutrinos
            } else {
                neutrinoRepo.getBalance()
            }
            _state.value = recalcedState.copy(
                neutrinos     = neutrinos,
                elementTokens = elementTokenRepo.getBalance()
            )

            initCompleted = true
        }
    }

    fun startGameLoop() {
        if (apsJob?.isActive == true) return
        gameLoopStartMs = System.currentTimeMillis()

        // Recharger les stats depuis les prefs pour refléter un éventuel reset effectué
        // depuis ClickerStatsActivity pendant que le ViewModel était en arrière-plan.
        // stopGameLoop() ayant sauvegardé les stats juste avant, on relit ce qui est sur disque.
        if (initCompleted) stats = statsRepository.load()

        // Gain hors-ligne pour le cas "retour depuis une autre page" (init déjà terminé).
        // Si init n'est pas encore terminé, c'est lui qui gère le timestamp ; on n'écrase pas
        // last_online_ms ici pour éviter que init lise un elapsed ≈ 0 et ignore le gain.
        if (initCompleted) {
            val now = System.currentTimeMillis()
            val lastOnline = offlineRepo.load()
            val elapsedMs = if (lastOnline > 0L) now - lastOnline else 0L
            if (elapsedMs > 5_000L) {
                val s = _state.value
                val baseAps = computeOfflineAps(s)
                val gained  = baseAps.multiplyNumber(elapsedMs / 1000.0)
                if (!gained.isZero()) {
                    _state.value = s.copy(
                        atoms    = s.atoms.add(gained),
                        lifetime = s.lifetime.add(gained)
                    )
                    stats = stats.copy(lifetimeApsAtoms = stats.lifetimeApsAtoms.add(gained))
                }
            }
            offlineRepo.save(System.currentTimeMillis())
        }

        // Heartbeat toutes les 1s pour un timestamp hors-ligne précis
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                offlineRepo.save(System.currentTimeMillis())
            }
        }

        apsJob = viewModelScope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (isActive) {
                delay(100L)
                val nowMs   = System.currentTimeMillis()
                // Plafonné à 2s pour éviter de créditer des heures d'un coup après un
                // gel Doze — les gains hors-ligne longs passent par resumeOfflineGains().
                val deltaMs = (nowMs - lastTickMs).coerceIn(0L, 2_000L)
                lastTickMs  = nowMs

                val tickResult = frenzyManager.tick(nowMs, deltaMs, getFrenzyChance())

                var s = _state.value
                if (!s.perSecond.isZero()) {
                    val gain = s.perSecond.multiplyNumber(deltaMs / 1000.0)
                    s = s.copy(
                        atoms    = s.atoms.add(gain),
                        lifetime = s.lifetime.add(gain)
                    )
                    stats = stats.copy(lifetimeApsAtoms = stats.lifetimeApsAtoms.add(gain))
                }
                if (tickResult.changed) {
                    s = recalcProduction(s, nowMs)
                    _frenzyUiState.value = frenzyManager.buildUiState(nowMs)
                }
                _state.value = s

                checkAchievements(s.lifetime, emitEvents = true)

                // Comptage des frénésies déclenchées ce tick
                if (tickResult.apcTriggered > 0 || tickResult.apsTriggered > 0) {
                    stats = stats.copy(
                        apcFrenzyCount = stats.apcFrenzyCount + tickResult.apcTriggered,
                        apsFrenzyCount = stats.apsFrenzyCount + tickResult.apsTriggered
                    )
                }

                // Vérifier si des frénésies APC viennent d'expirer
                if (tickResult.apcExpiredClickCounts.isNotEmpty()) {
                    val best = tickResult.apcExpiredClickCounts.max()
                    val newMax = maxOf(stats.maxClicksPerApcFrenzy, best)
                    if (newMax != stats.maxClicksPerApcFrenzy) {
                        stats = stats.copy(maxClicksPerApcFrenzy = newMax)
                        statsRepository.save(stats)
                    }
                }
            }
        }

        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                repository.save(_state.value)
                persistStats()
            }
        }

        ticketCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                awardGachaTickets()
            }
        }
    }

    /**
     * Appelé depuis MainClickerActivity.onResume() après un retour de ClickerStatsActivity.
     * Si un reset a été effectué sur le disque, remet à zéro l'état en RAM pour que
     * l'autosave ne réécrive pas les vieilles données par-dessus.
     */
    fun applyPendingReset(resetStats: Boolean, resetClicker: Boolean) {
        if (resetStats) {
            stats = ClickerStats()
        }
        if (resetClicker) {
            factoryRepo.reset()
            bigBangRepo.resetUnlock()
            _state.value = ClickerGameState()
            neutrinoRepo.setBalance(0)
            elementTokenRepo.setBalance(0)
        }
    }

    fun applyBigBangReset() {
        bigBangEffects = BigBangEngine.computeEffects(bigBangRepo)
        bigBangRepo.incrementBigBangCount()

        val s = _state.value
        val newAllTimeTotal = s.allTimeTotalAtoms.add(s.lifetime)
        val resetState = recalcProduction(
            ClickerGameState(
                atoms             = com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber.zero(),
                lifetime          = com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber.zero(),
                allTimeTotalAtoms = newAllTimeTotal,
                godFingerLevel    = 0,
                starCoreLevel     = 0,
                gachaTickets      = s.gachaTickets,
                neutrinos         = s.neutrinos,
                elementTokens     = elementTokenRepo.getBalance(),
                apcToApsLevel     = s.apcToApsLevel,
                apsToApcLevel     = s.apsToApcLevel,
                factoryCounts     = emptyMap()
            )
        )
        _state.value = resetState
        viewModelScope.launch { repository.save(resetState) }
    }

    fun stopGameLoop() {
        apsJob?.cancel();           apsJob           = null
        autoSaveJob?.cancel();      autoSaveJob      = null
        heartbeatJob?.cancel();     heartbeatJob     = null
        ticketCheckJob?.cancel();   ticketCheckJob   = null

        offlineRepo.save(System.currentTimeMillis())

        if (gameLoopStartMs > 0L) {
            stats = stats.copy(
                totalPlayTimeMs = stats.totalPlayTimeMs + (System.currentTimeMillis() - gameLoopStartMs)
            )
            gameLoopStartMs = 0L
        }
        statsRepository.save(stats)
        viewModelScope.launch { withContext(NonCancellable) { repository.save(_state.value) } }
    }

    // Appelé depuis onResume() pour couvrir le cas écran-off → écran-on sans onStop().
    // Si les coroutines étaient gelées (Doze), le heartbeat n'a pas mis à jour
    // last_online_ms ; on détecte le gap et on crédite les APS hors-ligne ici.
    // Si startGameLoop() a déjà tourné (via onStart()), il a sauvé un nouveau
    // timestamp → elapsedMs ≈ 0 → on ne fait rien.
    fun resumeOfflineGains() {
        if (!initCompleted) return
        val now        = System.currentTimeMillis()
        val lastOnline = offlineRepo.load()
        val elapsedMs  = if (lastOnline > 0L) now - lastOnline else 0L
        if (elapsedMs <= 5_000L) return
        val s       = _state.value
        val baseAps = computeOfflineAps(s)
        val gained  = baseAps.multiplyNumber(elapsedMs / 1000.0)
        if (!gained.isZero()) {
            _state.value = s.copy(
                atoms    = s.atoms.add(gained),
                lifetime = s.lifetime.add(gained)
            )
            stats = stats.copy(lifetimeApsAtoms = stats.lifetimeApsAtoms.add(gained))
        }
        offlineRepo.save(now)
    }

    fun registerClick() {
        val nowMs = System.currentTimeMillis()
        val s = _state.value
        _state.value = s.copy(
            atoms    = s.atoms.add(s.perClick),
            lifetime = s.lifetime.add(s.perClick)
        )

        // Mise à jour des stats de clic
        val duringFrenzy = frenzyManager.recordApcClick(nowMs)
        stats = stats.copy(
            totalClicks        = stats.totalClicks + 1,
            clicksDuringFrenzy = if (duringFrenzy) stats.clicksDuringFrenzy + 1 else stats.clicksDuringFrenzy,
            lifetimeApcAtoms   = stats.lifetimeApcAtoms.add(s.perClick)
        )
        trackCps(nowMs)

        if (duringFrenzy) {
            _frenzyUiState.value = frenzyManager.buildUiState(nowMs)
        }
    }

    private fun trackCps(nowMs: Long) {
        recentClickTimes.addLast(nowMs)
        val cutoff = nowMs - 1000L
        while (recentClickTimes.isNotEmpty() && recentClickTimes.first() < cutoff) {
            recentClickTimes.removeFirst()
        }
        val cps = recentClickTimes.size.toDouble()
        if (cps > stats.maxCps) {
            stats = stats.copy(maxCps = cps)
            statsRepository.save(stats)
        }
    }

    /** Sauvegarde les stats en incluant le temps de jeu courant (sans réinitialiser gameLoopStartMs). */
    private fun persistStats() {
        val playTimeSnapshot = if (gameLoopStartMs > 0L) {
            stats.copy(totalPlayTimeMs = stats.totalPlayTimeMs + (System.currentTimeMillis() - gameLoopStartMs))
        } else stats
        statsRepository.save(playTimeSnapshot)
    }

    fun buyUpgrade(upgradeId: String, quantity: Int) {
        val s = _state.value
        val currentLevel = when (upgradeId) {
            "godFinger" -> s.godFingerLevel
            "starCore"  -> s.starCoreLevel
            else        -> return
        }
        val effective = ClickerShopEngine.effectiveBuyAmount(currentLevel, quantity)
        if (effective <= 0) return
        val cost = ClickerShopEngine.batchCost(currentLevel, effective)
        if (cost.greaterThan(s.atoms)) return
        val newLevel = currentLevel + effective
        val afterPurchase = when (upgradeId) {
            "godFinger" -> s.copy(atoms = s.atoms.subtract(cost), godFingerLevel = newLevel)
            "starCore"  -> s.copy(atoms = s.atoms.subtract(cost), starCoreLevel  = newLevel)
            else        -> return
        }
        attributeSpending(cost)
        val newState = recalcProduction(afterPurchase)
        _state.value = newState
        viewModelScope.launch { repository.save(newState) }
    }

    private fun attributeSpending(cost: LayeredNumber) {
        val total = stats.lifetimeApcAtoms.add(stats.lifetimeApsAtoms)
        val apcRatio = if (!total.isZero()) stats.lifetimeApcAtoms.divide(total).toNumber() else 0.5
        val spentApc = cost.multiplyNumber(apcRatio)
        val spentAps = cost.subtract(spentApc)
        stats = stats.copy(
            spentFromApc = stats.spentFromApc.add(spentApc),
            spentFromAps = stats.spentFromAps.add(spentAps)
        )
    }

    fun shopCost(upgradeId: String, quantity: Int): LayeredNumber {
        val s = _state.value
        val currentLevel = when (upgradeId) {
            "godFinger" -> s.godFingerLevel
            "starCore"  -> s.starCoreLevel
            else        -> return LayeredNumber.zero()
        }
        val effective = ClickerShopEngine.effectiveBuyAmount(currentLevel, quantity)
        return ClickerShopEngine.batchCost(currentLevel, effective)
    }

    fun getTotalElements(): Int = collectionStore.getTotalCopies()

    fun getElementTokens(): Int = elementTokenRepo.getBalance()

    /**
     * Convertit [count] tokens éléments en neutrinos (1:1).
     * Les copies d'éléments individuels ne sont pas modifiées.
     * Retourne le nombre réel de tokens dépensés.
     */
    fun buyElementsToNeutrinos(count: Int): Int {
        if (count <= 0) return 0
        val spent = minOf(count, elementTokenRepo.getBalance())
        if (spent <= 0) return 0
        elementTokenRepo.consumeTokens(spent)

        val s = _state.value
        val newState = recalcProduction(s.copy(
            neutrinos     = s.neutrinos + spent,
            elementTokens = elementTokenRepo.getBalance()
        ))
        _state.value = newState
        viewModelScope.launch { repository.save(newState) }
        neutrinoRepo.addBalance(spent)
        return spent
    }

    fun getGameStats(): GameStats = GameStatsRepository(getApplication()).load()

    fun getLifetimeNeutrinos(): Int = neutrinoRepo.getLifetimeNeutrinos()

    fun getBigBangCount(): Int = bigBangRepo.getBigBangCount()

    /** Retourne les stats actuelles (snapshot avec temps de jeu en cours). */
    fun getStatsSnapshot(): ClickerStats {
        return if (gameLoopStartMs > 0L) {
            stats.copy(totalPlayTimeMs = stats.totalPlayTimeMs + (System.currentTimeMillis() - gameLoopStartMs))
        } else stats
    }

    // APS sans frénésie : shop + éléments flat + éléments mult + fusion + Big Bang + conversion APC→APS
    // Utilisé pour le gain hors-ligne afin d'éviter de compter la frénésie (30s max)
    private fun computeOfflineAps(state: ClickerGameState): LayeredNumber {
        val elem = getElementBonuses()

        // APS base (avec Big Bang comme dans recalcProduction)
        var perSecond = ClickerShopEngine.bonus(state.starCoreLevel).let {
            if (!it.isZero() && bigBangEffects.starCoreMult != 1.0) it.multiplyNumber(bigBangEffects.starCoreMult)
            else it
        }
        if (elem.flatAps > 0) perSecond = perSecond.add(LayeredNumber(elem.flatAps.toDouble()))
        if (elem.multAps > 0.0) perSecond = perSecond.multiplyNumber(1.0 + elem.multAps)

        // Bonus fusion APS
        val fusionAps = fusionStore.getBonusMultAps()
        if (fusionAps > 0.0) perSecond = perSecond.multiplyNumber(1.0 + fusionAps)

        // Conversion APC→APS (avec fusion APC incluse)
        if (state.apcToApsLevel > 0) {
            var baseApc = LayeredNumber.one()
            val cb = ClickerShopEngine.bonus(state.godFingerLevel)
            if (!cb.isZero()) baseApc = baseApc.add(
                if (bigBangEffects.godFingerMult != 1.0) cb.multiplyNumber(bigBangEffects.godFingerMult) else cb
            )
            if (elem.flatApc > 0) baseApc = baseApc.add(LayeredNumber(elem.flatApc.toDouble()))
            if (elem.multApc > 0.0) baseApc = baseApc.multiplyNumber(1.0 + elem.multApc)
            val fusionApc = fusionStore.getBonusMultApc()
            if (fusionApc > 0.0) baseApc = baseApc.multiplyNumber(1.0 + fusionApc)
            perSecond = perSecond.add(baseApc.multiplyNumber(state.apcToApsLevel * 0.01))
        }

        val factoryApsBonus = FactoryEngine.computeApsBonus(state.factoryCounts, bigBangEffects)
        if (factoryApsBonus > 0.0) perSecond = perSecond.multiplyNumber(1.0 + factoryApsBonus)
        return perSecond
    }

    fun refreshNeutrinoBalance() {
        if (!initCompleted) return
        val balance = neutrinoRepo.getBalance()
        val s = _state.value
        if (s.neutrinos != balance) {
            val newState = s.copy(neutrinos = balance)
            _state.value = newState
            viewModelScope.launch { repository.save(newState) }
        }
    }

    fun apcToApsCost(): Int = (_state.value.apcToApsLevel + 1).coerceAtMost(50)

    fun buyApcToAps() {
        val s = _state.value
        val cost = apcToApsCost()
        if (s.neutrinos < cost) return
        val afterPurchase = s.copy(
            neutrinos = s.neutrinos - cost,
            apcToApsLevel = s.apcToApsLevel + 1
        )
        val newState = recalcProduction(afterPurchase)
        _state.value = newState
        neutrinoRepo.setBalance(afterPurchase.neutrinos)
        viewModelScope.launch { repository.save(newState) }
    }

    fun apsToApcCost(): Int = (_state.value.apsToApcLevel + 1).coerceAtMost(50)

    fun buyApsToApc() {
        val s = _state.value
        val cost = apsToApcCost()
        if (s.neutrinos < cost) return
        val afterPurchase = s.copy(
            neutrinos = s.neutrinos - cost,
            apsToApcLevel = s.apsToApcLevel + 1
        )
        val newState = recalcProduction(afterPurchase)
        _state.value = newState
        neutrinoRepo.setBalance(afterPurchase.neutrinos)
        viewModelScope.launch { repository.save(newState) }
    }

    fun factoryCost(type: FactoryType): LayeredNumber =
        FactoryEngine.cost(type, _state.value.factoryCounts[type] ?: 0)

    fun buyFactory(type: FactoryType) {
        val s = _state.value
        val cost = FactoryEngine.cost(type, s.factoryCounts[type] ?: 0)
        if (cost.greaterThan(s.atoms)) return
        factoryRepo.increment(type)
        val newCounts = factoryRepo.getAllCounts()
        val afterPurchase = s.copy(
            atoms = s.atoms.subtract(cost),
            factoryCounts = newCounts
        )
        attributeSpending(cost)
        val newState = recalcProduction(afterPurchase)
        _state.value = newState
        viewModelScope.launch { repository.save(newState) }
    }

    fun refreshElementBonuses() {
        cachedElementBonuses = null
        cachedFrenzyChance = null
        _state.value = recalcProduction(_state.value)
    }

    fun refreshFusionBonuses() {
        _state.value = recalcProduction(_state.value)
    }

    fun refreshFusionAvailability() {
        val available = com.Atom2Universe.app.crypto.fusion.FusionRecipe.values().any { recipe ->
            val parentDone = recipe.unlockParentId?.let { pid ->
                com.Atom2Universe.app.crypto.fusion.FusionRecipe.byId(pid)
                    ?.let { fusionStore.getWins(it) > 0 } ?: false
            } ?: true
            parentDone && recipe.inputs.all { input ->
                collectionStore.getCopyCount(input.atomicNumber) >= input.count + 1
            }
        }
        if (_state.value.isFusionAvailable != available) {
            _state.value = _state.value.copy(isFusionAvailable = available)
        }
    }

    private fun getElementBonuses(): ElementBonuses =
        cachedElementBonuses ?: ElementBonusEngine.compute(collectionStore).also { cachedElementBonuses = it }

    private fun getFrenzyChance(): Double =
        cachedFrenzyChance ?: frenzyChanceForCompletedRarities(completedRarityCount(collectionStore))
            .also { cachedFrenzyChance = it }

    private fun recalcProduction(
        state: ClickerGameState,
        nowMs: Long = System.currentTimeMillis()
    ): ClickerGameState {
        val elem = getElementBonuses()

        // APC : base 1 + bonus shop (avec multiplicateur Big Bang) + flat éléments (H)
        var perClick = LayeredNumber.one()
        val clickBonus = ClickerShopEngine.bonus(state.godFingerLevel)
        if (!clickBonus.isZero()) {
            perClick = perClick.add(
                if (bigBangEffects.godFingerMult != 1.0) clickBonus.multiplyNumber(bigBangEffects.godFingerMult)
                else clickBonus
            )
        }
        if (elem.flatApc > 0) perClick = perClick.add(LayeredNumber(elem.flatApc.toDouble()))

        // APS : bonus shop (avec multiplicateur Big Bang) + flat éléments (He)
        var perSecond = ClickerShopEngine.bonus(state.starCoreLevel).let {
            if (!it.isZero() && bigBangEffects.starCoreMult != 1.0) it.multiplyNumber(bigBangEffects.starCoreMult)
            else it
        }
        if (elem.flatAps > 0) perSecond = perSecond.add(LayeredNumber(elem.flatAps.toDouble()))

        // Multiplicateurs éléments (avant frénésie)
        if (elem.multApc > 0.0) perClick  = perClick.multiplyNumber(1.0 + elem.multApc)
        if (elem.multAps > 0.0) perSecond = perSecond.multiplyNumber(1.0 + elem.multAps)

        // Bonus fusion (% accumulés par chaque victoire de fusion)
        val fusionApc = fusionStore.getBonusMultApc()
        val fusionAps = fusionStore.getBonusMultAps()
        if (fusionApc > 0.0) perClick  = perClick.multiplyNumber(1.0 + fusionApc)
        if (fusionAps > 0.0) perSecond = perSecond.multiplyNumber(1.0 + fusionAps)

        // Les deux conversions utilisent les valeurs de base (avant conversion) pour éviter
        // toute dépendance circulaire APC→APS→APC.
        val basePerClick  = perClick
        val basePerSecond = perSecond
        if (state.apcToApsLevel > 0) {
            perSecond = perSecond.add(basePerClick.multiplyNumber(state.apcToApsLevel * 0.01))
        }
        if (state.apsToApcLevel > 0) {
            perClick = perClick.add(basePerSecond.multiplyNumber(state.apsToApcLevel * 0.01))
        }

        // Multiplicateurs usines (après conversions, avant frénésie)
        val factoryApcBonus = FactoryEngine.computeApcBonus(state.factoryCounts, bigBangEffects)
        val factoryApsBonus = FactoryEngine.computeApsBonus(state.factoryCounts, bigBangEffects)
        if (factoryApcBonus > 0.0) perClick  = perClick.multiplyNumber(1.0 + factoryApcBonus)
        if (factoryApsBonus > 0.0) perSecond = perSecond.multiplyNumber(1.0 + factoryApsBonus)

        // Multiplicateurs frénésie
        val apcMult = frenzyManager.getMultiplier(FrenzyType.PER_CLICK,  nowMs)
        val apsMult = frenzyManager.getMultiplier(FrenzyType.PER_SECOND, nowMs)
        if (apcMult != 1.0) perClick  = perClick.multiplyNumber(apcMult)
        if (apsMult != 1.0) perSecond = perSecond.multiplyNumber(apsMult)

        return state.copy(perClick = perClick, perSecond = perSecond)
    }

    private fun checkAchievements(lifetime: LayeredNumber, emitEvents: Boolean) {
        if (unlockedAchievementIds.size == ClickerAchievements.all.size) return
        var anyNew = false
        for (a in ClickerAchievements.all) {
            if (a.id in unlockedAchievementIds) continue
            // La liste est triée par cible croissante — dès qu'une cible n'est pas atteinte,
            // toutes les suivantes ne le seront pas non plus.
            if (!lifetime.greaterOrEqual(a.target)) break
            unlockedAchievementIds.add(a.id)
            anyNew = true
            if (emitEvents) _achievementUnlocked.tryEmit(a)
        }
        if (anyNew) achievementRepository.saveUnlocked(unlockedAchievementIds.toSet())
    }

    fun getUnlockedAchievementIds(): Set<String> = unlockedAchievementIds.toSet()

    fun save() {
        viewModelScope.launch { withContext(NonCancellable) { repository.save(_state.value) } }
    }

    fun consumeGachaTicket() {
        val s = _state.value
        if (s.gachaTickets > 0) {
            val newState = s.copy(gachaTickets = s.gachaTickets - 1)
            _state.value = newState
            viewModelScope.launch { repository.consumeGachaTicket() }
        }
    }

    fun awardGachaTickets() {
        viewModelScope.launch {
            val tickets = repository.loadGachaTickets()
            _state.value = _state.value.copy(gachaTickets = tickets)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().getSharedPreferences("pending_reset_flags", android.content.Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(pendingResetListener)
        offlineRepo.save(System.currentTimeMillis())
        if (gameLoopStartMs > 0L) {
            stats = stats.copy(
                totalPlayTimeMs = stats.totalPlayTimeMs + (System.currentTimeMillis() - gameLoopStartMs)
            )
        }
        statsRepository.save(stats)
        viewModelScope.launch { withContext(NonCancellable) { repository.save(_state.value) } }
    }
}
