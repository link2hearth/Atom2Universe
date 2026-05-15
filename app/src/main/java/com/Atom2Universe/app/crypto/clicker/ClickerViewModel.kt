package com.Atom2Universe.app.crypto.clicker

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
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

class ClickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository            = ClickerRepository(application)
    private val statsRepository       = ClickerStatsRepository(application)
    private val offlineRepo           = ClickerOfflineRepository(application)
    private val neutrinoRepo          = NeutrinoRepository(application)
    private val achievementRepository = ClickerAchievementRepository(application)
    private val frenzyManager         = FrenzyManager()
    private val collectionStore       = PeriodicCollectionStore(application)

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

    private var apsJob: Job? = null

    // Référence forte obligatoire — sinon le GC supprime le listener silencieusement.
    private val pendingResetListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "reset_stats" || key == "reset_clicker") {
            val resetStats   = prefs.getBoolean("reset_stats",   false)
            val resetClicker = prefs.getBoolean("reset_clicker", false)
            if (resetStats || resetClicker) {
                prefs.edit().clear().apply()
                applyPendingReset(resetStats, resetClicker)
            }
        }
    }
    private var autoSaveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var ticketCheckJob: Job? = null

    init {
        getApplication<Application>().getSharedPreferences("pending_reset_flags", android.content.Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(pendingResetListener)

        viewModelScope.launch {
            val loaded = repository.load()

            // Gain hors-ligne : APS réel (shop + éléments, sans frénésie) × temps écoulé
            val now = System.currentTimeMillis()
            val lastOnline = offlineRepo.load()
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
            // Migration : si SharedPrefs est vide mais que la DB a un solde, initialiser SharedPrefs.
            val dbNeutrinos = recalcedState.neutrinos
            val sharedBalance = neutrinoRepo.getBalance()
            val neutrinos = if (sharedBalance == 0 && dbNeutrinos > 0) {
                neutrinoRepo.setBalance(dbNeutrinos)
                dbNeutrinos
            } else {
                sharedBalance
            }
            _state.value = recalcedState.copy(neutrinos = neutrinos)

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

                val tickResult = frenzyManager.tick(nowMs, deltaMs)

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
            _state.value = ClickerGameState()
            neutrinoRepo.setBalance(0)
        }
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
        viewModelScope.launch(NonCancellable) { repository.save(_state.value) }
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

    /**
     * Convertit [count] copies d'éléments en neutrinos (1:1).
     * Consomme en priorité les éléments les plus dupliqués.
     * Retourne le nombre réel de copies dépensées.
     */
    fun buyElementsToNeutrinos(count: Int): Int {
        if (count <= 0) return 0
        // Trier par copies décroissantes pour dépenser les doublons en premier
        val available = (1..118)
            .map { n -> n to collectionStore.getCopyCount(n) }
            .filter { (_, c) -> c > 0 }
            .sortedByDescending { (_, c) -> c }

        var remaining = count
        for ((atomicNumber, _) in available) {
            while (remaining > 0 && collectionStore.consumeCopy(atomicNumber)) {
                remaining--
            }
            if (remaining == 0) break
        }

        val spent = count - remaining
        if (spent <= 0) return 0

        val s = _state.value
        val newNeutrinos = s.neutrinos + spent
        val newState = recalcProduction(s.copy(neutrinos = newNeutrinos))
        _state.value = newState
        viewModelScope.launch { repository.save(newState) }
        neutrinoRepo.addBalance(spent)
        return spent
    }

    fun getGameStats(): GameStats = GameStatsRepository(getApplication()).load()

    fun getLifetimeNeutrinos(): Int = neutrinoRepo.getLifetimeNeutrinos()

    /** Retourne les stats actuelles (snapshot avec temps de jeu en cours). */
    fun getStatsSnapshot(): ClickerStats {
        return if (gameLoopStartMs > 0L) {
            stats.copy(totalPlayTimeMs = stats.totalPlayTimeMs + (System.currentTimeMillis() - gameLoopStartMs))
        } else stats
    }

    // APS sans frénésie : shop + éléments flat + éléments mult + conversion APC→APS
    // Utilisé pour le gain hors-ligne afin d'éviter de compter la frénésie (30s max)
    private fun computeOfflineAps(state: ClickerGameState): LayeredNumber {
        val elem = ElementBonusEngine.compute(collectionStore)
        var base = ClickerShopEngine.bonus(state.starCoreLevel)
        if (elem.flatAps > 0) base = base.add(LayeredNumber(elem.flatAps.toDouble()))
        if (elem.multAps > 0.0) base = base.multiplyNumber(1.0 + elem.multAps)
        if (state.apcToApsLevel > 0) {
            var baseApc = LayeredNumber.one()
            val cb = ClickerShopEngine.bonus(state.godFingerLevel)
            if (!cb.isZero()) baseApc = baseApc.add(cb)
            if (elem.flatApc > 0) baseApc = baseApc.add(LayeredNumber(elem.flatApc.toDouble()))
            if (elem.multApc > 0.0) baseApc = baseApc.multiplyNumber(1.0 + elem.multApc)
            base = base.add(baseApc.multiplyNumber(state.apcToApsLevel * 0.01))
        }
        return base
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

    fun apcToApsCost(): Int = _state.value.apcToApsLevel + 1

    fun buyApcToAps() {
        val s = _state.value
        val cost = s.apcToApsLevel + 1
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

    fun apsToApcCost(): Int = _state.value.apsToApcLevel + 1

    fun buyApsToApc() {
        val s = _state.value
        val cost = s.apsToApcLevel + 1
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

    fun refreshElementBonuses() {
        _state.value = recalcProduction(_state.value)
    }

    private fun recalcProduction(
        state: ClickerGameState,
        nowMs: Long = System.currentTimeMillis()
    ): ClickerGameState {
        val elem = ElementBonusEngine.compute(collectionStore)

        // APC : base 1 + bonus shop + flat éléments (H)
        var perClick = LayeredNumber.one()
        val clickBonus = ClickerShopEngine.bonus(state.godFingerLevel)
        if (!clickBonus.isZero()) perClick = perClick.add(clickBonus)
        if (elem.flatApc > 0) perClick = perClick.add(LayeredNumber(elem.flatApc.toDouble()))

        // APS : bonus shop + flat éléments (He)
        var perSecond = ClickerShopEngine.bonus(state.starCoreLevel)
        if (elem.flatAps > 0) perSecond = perSecond.add(LayeredNumber(elem.flatAps.toDouble()))

        // Multiplicateurs éléments (avant frénésie)
        if (elem.multApc > 0.0) perClick  = perClick.multiplyNumber(1.0 + elem.multApc)
        if (elem.multAps > 0.0) perSecond = perSecond.multiplyNumber(1.0 + elem.multAps)

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

        // Multiplicateurs frénésie
        val apcMult = frenzyManager.getMultiplier(FrenzyType.PER_CLICK,  nowMs)
        val apsMult = frenzyManager.getMultiplier(FrenzyType.PER_SECOND, nowMs)
        if (apcMult != 1.0) perClick  = perClick.multiplyNumber(apcMult)
        if (apsMult != 1.0) perSecond = perSecond.multiplyNumber(apsMult)

        return state.copy(perClick = perClick, perSecond = perSecond)
    }

    private fun checkAchievements(lifetime: LayeredNumber, emitEvents: Boolean) {
        var anyNew = false
        for (a in ClickerAchievements.all) {
            if (a.id !in unlockedAchievementIds && lifetime.greaterOrEqual(a.target)) {
                unlockedAchievementIds.add(a.id)
                anyNew = true
                if (emitEvents) _achievementUnlocked.tryEmit(a)
            }
        }
        if (anyNew) achievementRepository.saveUnlocked(unlockedAchievementIds.toSet())
    }

    fun getUnlockedAchievementIds(): Set<String> = unlockedAchievementIds.toSet()

    fun save() {
        viewModelScope.launch(NonCancellable) { repository.save(_state.value) }
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
        viewModelScope.launch(NonCancellable) { repository.save(_state.value) }
    }
}
