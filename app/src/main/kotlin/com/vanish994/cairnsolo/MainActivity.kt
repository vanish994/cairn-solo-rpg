package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.guardian.HttpGuardianClient
import com.vanish994.cairnsolo.guardian.GuardianRuleResolver
import com.vanish994.cairnsolo.guardian.resolveCampaignOpening
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CombatState
import com.vanish994.cairnsolo.game.CombatOpponentState
import com.vanish994.cairnsolo.game.CombatOpponentStatus
import com.vanish994.cairnsolo.game.GrowthChangeProposal
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.LocalGameStateRepository
import com.vanish994.cairnsolo.game.CanonResolver
import com.vanish994.cairnsolo.guardian.GuardianRuleRequest
import com.vanish994.cairnsolo.guardian.GuardianRuleResolution
import com.vanish994.cairnsolo.guardian.GuardianEncounterProposal
import com.vanish994.cairnsolo.feedback.FeedbackEntry
import com.vanish994.cairnsolo.feedback.FeedbackMapper
import com.vanish994.cairnsolo.feedback.FeedbackType
import com.vanish994.cairnsolo.feedback.inventoryItemLabel
import com.vanish994.cairnsolo.rules.*
import kotlin.random.Random

private const val CAMPAIGN_START_INTENT = "INICIAR_CAMPANHA"

private val CairnBackground = Color(0xFF0A090B)
private val CairnSurface = Color(0xFF121116)
private val CairnSurfaceRaised = Color(0xFF1A1820)
private val CairnText = Color(0xFFE9E2D8)
private val CairnMuted = Color(0xFFA49A8C)
private val CairnAccent = Color(0xFFC7A56B)
private val CairnAccentSoft = Color(0xFF70583C)
private val CairnDanger = Color(0xFFB97865)
private val CairnBorder = Color(0xFF3A3430)

private val CairnColors = darkColorScheme(
    primary = CairnAccent,
    onPrimary = Color(0xFF17120C),
    secondary = CairnMuted,
    background = CairnBackground,
    surface = CairnSurface,
    surfaceVariant = CairnSurfaceRaised,
    onSurface = CairnText,
    onSurfaceVariant = CairnMuted,
    outline = CairnBorder
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalGameStateRepository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = CairnColors) {
                var state by remember { mutableStateOf(repository.load()) }
                var name by remember { mutableStateOf("") }
                var rolled by remember { mutableStateOf<RolledCharacter?>(null) }
                var selectedBackground by remember { mutableStateOf<Background?>(null) }
                val guardianClient = remember { HttpGuardianClient() }
                val scope = rememberCoroutineScope()
                var guardianLoading by remember { mutableStateOf(false) }
                var guardianError by remember { mutableStateOf<String?>(null) }
                var rewardFeedback by remember { mutableStateOf(emptyList<FeedbackEntry>()) }
                var pendingRule by remember { mutableStateOf<GuardianRuleRequest?>(null) }
                var lastResolution by remember { mutableStateOf<GuardianRuleResolution?>(null) }
                var guardianFlow by remember { mutableStateOf(GuardianFlow.EXPLORATION) }
                var suggestedActions by remember { mutableStateOf(emptyList<String>()) }
                var guardianIntentDraft by remember { mutableStateOf("") }
                val actionResolver = remember {
                    GameActionResolver(
                        exploration = ExplorationEngine(KotlinRandomSource()),
                        rules = RulesEngine(KotlinRandomSource())
                    )
                }
                val guardianRuleResolver = remember { GuardianRuleResolver(actionResolver) }
                val canonResolver = remember { CanonResolver() }
                var screen by remember { mutableStateOf(AppScreen.CHARACTER) }

                Surface(Modifier.fillMaxSize(), color = CairnBackground) {
                    if (state == null) {
                        if (screen == AppScreen.RULES) {
                            RulesScreen(onBack = { screen = AppScreen.CHARACTER })
                        } else {
                            CharacterCreation(
                                name = name,
                                onNameChange = { name = it },
                                rolled = rolled,
                                selectedBackground = selectedBackground,
                                onRoll = {
                                    val next = rollCharacter(KotlinRandomSource())
                                    rolled = next
                                    selectedBackground = next.background
                                },
                                onBackgroundChange = { background ->
                                    selectedBackground = background
                                    rolled = rolled?.copy(background = background)
                                },
                                onSwap = { first, second -> rolled = rolled?.swapAttributes(first, second) },
                                onRules = { screen = AppScreen.RULES },
                                onCreate = {
                                    val r = rolled ?: rollCharacter(KotlinRandomSource())
                                    val finalRolled = r.copy(background = selectedBackground ?: r.background)
                                    val created = actionResolver.resolve(
                                        GameAction.CreateCharacter(name.trim(), finalRolled)
                                    ).state
                                    repository.save(created)
                                    state = created
                                    screen = AppScreen.EXPLORATION
                                    guardianFlow = GuardianFlow.GUARDIAN_THINKING
                                    guardianLoading = true
                                    guardianError = null
                                    rewardFeedback = emptyList()
                                    pendingRule = null
                                    lastResolution = null
                                    suggestedActions = emptyList()
                                    guardianIntentDraft = ""
                                    scope.launch {
                                        val openingContext = created.copy(
                                            campaign = created.campaign.copy(guardianMessage = "")
                                        )
                                        val outcome = resolveCampaignOpening(
                                            initialState = created,
                                            result = guardianClient.narrate(
                                                openingContext,
                                                CAMPAIGN_START_INTENT,
                                                ruleResult = null,
                                                encounterContext = null
                                            ),
                                            actionResolver = actionResolver
                                        )
                                        repository.save(outcome.state)
                                        state = outcome.state
                                        suggestedActions = outcome.suggestedActions
                                        guardianError = outcome.error
                                        guardianFlow = GuardianFlow.EXPLORATION
                                        guardianLoading = false
                                    }
                                }
                            )
                        }
                    } else {
                        val current = state!!
                        when (screen) {
                            AppScreen.CHARACTER -> CharacterSheet(
                                state = current,
                                combatActive = current.campaign.combat != null,
                                rewardFeedback = rewardFeedback,
                                onDamage = {
                                    val next = actionResolver.resolve(current, GameAction.ApplyDamage(2)).state
                                    repository.save(next)
                                    state = next
                                },
                                onExplore = { screen = AppScreen.EXPLORATION },
                                onRest = {
                                    val next = actionResolver.resolve(current, GameAction.Rest).state
                                    repository.save(next)
                                    state = next
                                },
                                onAddItem = {
                                    val id = "item-" + Random.nextInt(100000, 999999)
                                    val next = actionResolver.resolve(current, GameAction.AddItem(InventoryItem(id))).state
                                    repository.save(next)
                                    state = next
                                },
                                onRemoveItem = { id ->
                                    val next = actionResolver.resolve(current, GameAction.RemoveItem(id)).state
                                    repository.save(next)
                                    state = next
                                },
                                onClaimReward = { pendingId ->
                                    val result = actionResolver.resolve(current, GameAction.ClaimPendingRewardItem(pendingId))
                                    repository.save(result.state)
                                    state = result.state
                                    rewardFeedback = FeedbackMapper.mapAll(result.events, result.state.campaign.turn)
                                },
                                onDelete = {
                                    repository.clear()
                                    state = null
                                    screen = AppScreen.CHARACTER
                                    suggestedActions = emptyList()
                                    rewardFeedback = emptyList()
                                    guardianIntentDraft = ""
                                }
                            )
                            AppScreen.EXPLORATION -> ExplorationScreen(
                                state = current,
                                onAction = { action ->
                                    if (pendingRule == null && lastResolution == null) {
                                        val result = actionResolver.resolve(current, action)
                                        repository.save(result.state)
                                        state = result.state
                                    }
                                },
                                onCombatAttack = { targetOpponentId, weapon ->
                                    if (pendingRule == null && lastResolution == null && !guardianLoading) {
                                        runCatching {
                                            guardianRuleResolver.resolve(
                                                state ?: current,
                                                GameAction.CombatAttack(targetOpponentId = targetOpponentId, weapon = weapon)
                                            )
                                        }.onSuccess { resolution ->
                                            repository.save(resolution.state)
                                            state = resolution.state
                                            lastResolution = resolution
                                            guardianFlow = GuardianFlow.ROLL_RESULT
                                            guardianError = null
                                        }.onFailure { error ->
                                            guardianError = error.message ?: "Não foi possível resolver o ataque."
                                        }
                                    }
                                },
                                guardianLoading = guardianLoading,
                                guardianError = guardianError,
                                guardianFlow = guardianFlow,
                                pendingRule = pendingRule,
                                lastResolution = lastResolution,
                                suggestedActions = suggestedActions,
                                rewardFeedback = rewardFeedback,
                                intent = guardianIntentDraft,
                                onIntentChange = { guardianIntentDraft = it },
                                onSuggestionSelected = { guardianIntentDraft = it },
                                onResolveRule = {
                                    pendingRule?.let { request ->
                                        runCatching { guardianRuleResolver.resolve(state!!, request) }
                                            .onSuccess { resolution ->
                                                repository.save(resolution.state)
                                                state = resolution.state
                                                pendingRule = null
                                                lastResolution = resolution
                                                guardianFlow = GuardianFlow.ROLL_RESULT
                                            }
                                            .onFailure { error ->
                                                guardianError = error.message
                                                    ?: "Não foi possível resolver esta regra."
                                        }
                                    }
                                },
                                onRejectEncounter = {
                                    pendingRule = null
                                    guardianFlow = GuardianFlow.EXPLORATION
                                    guardianError = null
                                },
                                onDismissRequest = {
                                    pendingRule = null
                                    guardianFlow = GuardianFlow.EXPLORATION
                                    guardianError = null
                                },
                                onContinueNarrative = {
                                    lastResolution?.let { resolution ->
                                        guardianFlow = GuardianFlow.CONSEQUENCE_NARRATION
                                        guardianLoading = true
                                        guardianError = null
                                        rewardFeedback = emptyList()
                                        scope.launch {
                                            guardianClient.narrate(
                                                resolution.state,
                                                playerIntent = "CONTINUAR_NARRATIVA",
                                                ruleResult = resolution.resultText,
                                                encounterContext = resolution.encounterContexts
                                            )
                                                .onSuccess { response ->
                                                    val narrated = resolution.state.applyGuardianResponse(
                                                        narration = response.narration,
                                                        sceneTitle = response.sceneTitle,
                                                        sceneDescription = response.sceneDescription,
                                                        interactionId = response.interactionId
                                                    )
                                                    val next = runCatching {
                                                        val withGrowth = response.growthEvidenceProposals.fold(narrated) { accumulated, proposal ->
                                                            runCatching { actionResolver.resolve(accumulated, GameAction.RecordGrowthEvidenceProposal(proposal)).state }
                                                                .getOrElse { accumulated }
                                                        }
                                                        val withChanges = response.growthChangeProposals.fold(withGrowth) { accumulated, proposal ->
                                                            runCatching { actionResolver.resolve(accumulated, GameAction.RecordGrowthChangeProposal(proposal)).state }
                                                                .getOrElse { accumulated }
                                                        }
                                                        actionResolver.resolve(withChanges, GameAction.ApplyCanonProposals(response.canonProposals)).state
                                                    }.getOrElse { narrated }
                                                    val requestError = response.ruleRequest?.let { guardianRuleResolver.validationError(next, it) }
                                                    val rewardRequest = response.ruleRequest?.takeIf { it.type.equals("REWARD", ignoreCase = true) }
                                                    var finalState = next
                                                    var rewardError: String? = null
                                                    var responseRewardFeedback = emptyList<FeedbackEntry>()
                                                    if (requestError == null && rewardRequest != null) {
                                                        runCatching { guardianRuleResolver.resolve(next, rewardRequest) }
                                                            .onSuccess { resolution ->
                                                                finalState = resolution.state
                                                                responseRewardFeedback = FeedbackMapper.mapAll(
                                                                    resolution.gameResult.events,
                                                                    resolution.state.campaign.turn
                                                                )
                                                            }
                                                            .onFailure { rewardError = it.message ?: "Não foi possível aplicar a recompensa confirmada." }
                                                    }
                                                    repository.save(finalState)
                                                    state = finalState
                                                    suggestedActions = response.suggestedActions
                                                    rewardFeedback = responseRewardFeedback
                                                    pendingRule = response.ruleRequest?.takeIf {
                                                        requestError == null && !it.type.equals("REWARD", ignoreCase = true)
                                                    }
                                                    guardianError = requestError ?: rewardError
                                                    lastResolution = null
                                                    guardianFlow = when (pendingRule?.type?.uppercase()) {
                                                        null -> GuardianFlow.EXPLORATION
                                                        "BEGIN_COMBAT" -> GuardianFlow.ENCOUNTER_PROPOSED
                                                        else -> GuardianFlow.ROLL_REQUIRED
                                                    }
                                                }
                                                .onFailure { error ->
                                                    guardianError = error.message
                                                        ?: "A regra foi resolvida, mas o Guardião não respondeu à consequência."
                                                }
                                            guardianLoading = false
                                        }
                                    }
                                },
                                onGuardianIntent = { intent ->
                                    if (pendingRule == null && lastResolution == null) {
                                        suggestedActions = emptyList()
                                        rewardFeedback = emptyList()
                                        val intentState = actionResolver.resolve(
                                            current,
                                            GameAction.GuardianIntent(intent)
                                        ).state
                                        repository.save(intentState)
                                        state = intentState
                                        guardianFlow = GuardianFlow.GUARDIAN_THINKING
                                        guardianLoading = true
                                        guardianError = null
                                        scope.launch {
                                            guardianClient.narrate(intentState, intent, ruleResult = null, encounterContext = null)
                                                .onSuccess { response ->
                                                    val narrated = intentState.applyGuardianResponse(
                                                        narration = response.narration,
                                                        sceneTitle = response.sceneTitle,
                                                        sceneDescription = response.sceneDescription,
                                                        interactionId = response.interactionId
                                                    )
                                                    val next = runCatching {
                                                        val withGrowth = response.growthEvidenceProposals.fold(narrated) { accumulated, proposal ->
                                                            runCatching { actionResolver.resolve(accumulated, GameAction.RecordGrowthEvidenceProposal(proposal)).state }
                                                                .getOrElse { accumulated }
                                                        }
                                                        val withChanges = response.growthChangeProposals.fold(withGrowth) { accumulated, proposal ->
                                                            runCatching { actionResolver.resolve(accumulated, GameAction.RecordGrowthChangeProposal(proposal)).state }
                                                                .getOrElse { accumulated }
                                                        }
                                                        actionResolver.resolve(withChanges, GameAction.ApplyCanonProposals(response.canonProposals)).state
                                                    }.getOrElse { narrated }
                                                    val requestError = response.ruleRequest?.let { guardianRuleResolver.validationError(next, it) }
                                                    val rewardRequest = response.ruleRequest?.takeIf { it.type.equals("REWARD", ignoreCase = true) }
                                                    var finalState = next
                                                    var rewardError: String? = null
                                                    var responseRewardFeedback = emptyList<FeedbackEntry>()
                                                    if (requestError == null && rewardRequest != null) {
                                                        runCatching { guardianRuleResolver.resolve(next, rewardRequest) }
                                                            .onSuccess { resolution ->
                                                                finalState = resolution.state
                                                                responseRewardFeedback = FeedbackMapper.mapAll(
                                                                    resolution.gameResult.events,
                                                                    resolution.state.campaign.turn
                                                                )
                                                            }
                                                            .onFailure { rewardError = it.message ?: "Não foi possível aplicar a recompensa confirmada." }
                                                    }
                                                    repository.save(finalState)
                                                    state = finalState
                                                    suggestedActions = response.suggestedActions
                                                    rewardFeedback = responseRewardFeedback
                                                    pendingRule = response.ruleRequest?.takeIf {
                                                        requestError == null && !it.type.equals("REWARD", ignoreCase = true)
                                                    }
                                                    guardianError = requestError ?: rewardError
                                                    lastResolution = null
                                                    guardianFlow = when (pendingRule?.type?.uppercase()) {
                                                        null -> GuardianFlow.EXPLORATION
                                                        "BEGIN_COMBAT" -> GuardianFlow.ENCOUNTER_PROPOSED
                                                        else -> GuardianFlow.ROLL_REQUIRED
                                                    }
                                                }
                                                .onFailure { error ->
                                                    guardianError = error.message ?: "Não foi possível falar com o Guardião."
                                                    guardianFlow = GuardianFlow.EXPLORATION
                                                }
                                            guardianLoading = false
                                        }
                                    }
                                },
                                onDecideGrowth = { proposalId, accepted ->
                                    val result = actionResolver.resolve(
                                        state ?: current,
                                        GameAction.DecideGrowthChangeProposal(proposalId, accepted)
                                    )
                                    repository.save(result.state)
                                    state = result.state
                                },
                                onBack = { screen = AppScreen.CHARACTER }
                            )
                            AppScreen.RULES -> RulesScreen(onBack = { screen = AppScreen.CHARACTER })
                        }
                    }
                }
            }
        }
    }
}

enum class AppScreen { CHARACTER, EXPLORATION, RULES }

private enum class GuardianFlow {
    EXPLORATION,
    GUARDIAN_THINKING,
    ENCOUNTER_PROPOSED,
    ROLL_REQUIRED,
    ROLL_RESULT,
    CONSEQUENCE_NARRATION
}

@Composable
private fun CairnHeader(eyebrow: String, title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = CairnAccent,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = CairnText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        subtitle?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 8.dp),
                color = CairnMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CairnSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CairnBorder)
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun GuardianCard(message: String, history: List<String>, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val messages = (if (history.lastOrNull() == message) history else history + message)
        .filter { it.isNotBlank() }

    LaunchedEffect(messages) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    SectionCard(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "◈",
                color = CairnAccent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "GUARDIÃO",
                color = CairnAccent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "NARRATIVA",
                color = CairnMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.forEach { entry ->
                val isPlayer = entry.startsWith("Você:")
                Surface(
                    color = if (isPlayer) CairnSurfaceRaised else Color(0xFF211D24),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                        Text(
                            if (isPlayer) "VOCÊ" else "GUARDIÃO",
                            color = if (isPlayer) CairnMuted else CairnAccent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isPlayer) entry.removePrefix("Você:").trim() else entry,
                            color = CairnText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianSuggestedActions(
    actions: List<String>,
    enabled: Boolean,
    onSuggestionSelected: (String) -> Unit
) {
    if (actions.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "PRÓXIMAS AÇÕES",
                color = CairnAccent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            actions.take(3).forEach { action ->
                AssistChip(
                    onClick = { onSuggestionSelected(action) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    label = { Text(action, softWrap = true) }
                )
            }
        }
    }
}

@Composable
private fun PortraitPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0A0C))
            .border(1.dp, CairnBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(132.dp)) {
            val unit = size.minDimension / 16f
            drawCircle(
                CairnAccent,
                radius = unit * 3.1f,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, unit * 4.5f)
            )
            drawRoundRect(
                CairnAccent,
                topLeft = androidx.compose.ui.geometry.Offset(unit * 3f, unit * 7f),
                size = androidx.compose.ui.geometry.Size(unit * 10f, unit * 7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 1.5f)
            )
            drawRect(
                CairnAccentSoft,
                topLeft = androidx.compose.ui.geometry.Offset(unit * 4f, unit * 10f),
                size = androidx.compose.ui.geometry.Size(unit * 8f, unit * 4f)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CairnSurfaceRaised)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = CairnMuted, style = MaterialTheme.typography.labelMedium)
        Text(value.toString(), color = CairnText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExplorationScreen(
    state: GameState,
    onAction: (GameAction) -> Unit,
    onCombatAttack: (String, WeaponProfile?) -> Unit,
    guardianLoading: Boolean,
    guardianError: String?,
    guardianFlow: GuardianFlow,
    pendingRule: GuardianRuleRequest?,
    lastResolution: GuardianRuleResolution?,
    suggestedActions: List<String>,
    rewardFeedback: List<FeedbackEntry>,
    intent: String,
    onIntentChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onResolveRule: () -> Unit,
    onRejectEncounter: () -> Unit,
    onDismissRequest: () -> Unit,
    onContinueNarrative: () -> Unit,
    onGuardianIntent: (String) -> Unit,
    onDecideGrowth: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val c = state.campaign
    val r = c.rules

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CairnHeader(
            "CAIRN",
            "Exploração",
            "T" + c.turn + " · " + sceneTypeLabel(c.sceneType) + " · " + guardianFlowLabel(guardianFlow)
        )

        // Atmospheric scene art is presentation-only: campaign state and rules remain untouched.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CairnBorder, RoundedCornerShape(10.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF171821), size = size)
                drawCircle(
                    color = Color(0xFFC7A56B).copy(alpha = 0.24f),
                    radius = size.minDimension * 0.13f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.79f, size.height * 0.27f)
                )
                val farRidge = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height * 0.68f)
                    lineTo(size.width * 0.18f, size.height * 0.43f)
                    lineTo(size.width * 0.34f, size.height * 0.62f)
                    lineTo(size.width * 0.55f, size.height * 0.38f)
                    lineTo(size.width * 0.77f, size.height * 0.66f)
                    lineTo(size.width, size.height * 0.48f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(farRidge, Color(0xFF292832))
                drawRect(
                    Color(0xFF111116),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.61f, size.height * 0.30f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.53f)
                )
                drawRect(
                    Color(0xFF111116),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.26f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.20f, size.height * 0.07f)
                )
                drawRect(
                    Color(0xFF70583C),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.44f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.025f, size.height * 0.08f)
                )
                drawRect(
                    Color(0xFF0B0A0D).copy(alpha = 0.8f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.82f),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.18f)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xCC0A090B))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    c.sceneTitle.ifBlank { "A cena se revela diante de você." },
                    color = CairnText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    c.sceneDescription.ifBlank { "O silêncio guarda algo além das ruínas." },
                    color = CairnMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                color = Color(0xDD0A090B),
                shape = RoundedCornerShape(5.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CairnBorder)
            ) {
                Text(
                    "HP ${r.hp}/${r.maxHp} · ARM ${r.armor}",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    color = CairnText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GuardianCard(
                message = c.guardianMessage.ifBlank { "O Guardião aguarda sua decisão." },
                history = c.guardianHistory,
                modifier = Modifier.weight(1f)
            )
            RewardFeedbackCard(rewardFeedback)
            GuardianSuggestedActions(
                actions = suggestedActions,
                enabled = !guardianLoading && pendingRule == null && lastResolution == null,
                onSuggestionSelected = onSuggestionSelected
            )
        }

        pendingRule?.let { request ->
            if (request.type.equals("BEGIN_COMBAT", ignoreCase = true)) {
                request.encounter?.let { proposal ->
                    EncounterProposalCard(
                        proposal = proposal,
                        enabled = !guardianLoading && c.combat == null,
                        onAccept = onResolveRule,
                        onReject = onRejectEncounter
                    )
                } ?: SectionCard {
                    Text("PROPOSTA INVÁLIDA", color = CairnDanger, fontWeight = FontWeight.Bold)
                    Text("O encontro não trouxe um perfil completo. Nenhum combate foi iniciado.", color = CairnMuted)
                    TextButton(onClick = onRejectEncounter) { Text("Descartar") }
                }
            } else {
                RollRequestCard(
                    request,
                    enabled = !guardianLoading,
                    onRoll = onResolveRule,
                    onDismiss = onDismissRequest
                )
            }
        }

        c.combat?.let { combat ->
            val availableWeapons = r.inventory.mapNotNull { item ->
                item.damage?.takeIf { isSupportedWeaponDamageExpression(it) }?.let { damage ->
                    WeaponProfile(
                        id = item.id,
                        damage = damage,
                        blast = item.tags.any { it.equals("BLAST", ignoreCase = true) },
                        ranged = item.tags.any { it.equals("RANGED", ignoreCase = true) }
                    )
                }
            }.ifEmpty { listOf(WeaponProfile("unarmed", "d4")) }
            CombatCard(
                combat = combat,
                adventurerName = c.character.name,
                adventurerHp = r.hp,
                adventurerMaxHp = r.maxHp,
                weapons = availableWeapons,
                enabled = !guardianLoading && pendingRule == null && lastResolution == null,
                onAttack = onCombatAttack
            )
        }

        lastResolution?.let { resolution ->
            RollResultCard(resolution, enabled = !guardianLoading, onContinue = onContinueNarrative)
        }

        Text(
            "SUA DECISÃO",
            color = CairnAccent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = intent,
                onValueChange = onIntentChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 96.dp),
                placeholder = {
                    Text(
                        "Descreva a intenção do aventureiro…",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                maxLines = 3,
                enabled = !guardianLoading && pendingRule == null && lastResolution == null,
                shape = RoundedCornerShape(8.dp)
            )
            IconButton(
                onClick = {
                    onGuardianIntent(intent.trim())
                    onIntentChange("")
                },
                enabled = intent.isNotBlank() && !guardianLoading && pendingRule == null && lastResolution == null,
                modifier = Modifier
                    .size(48.dp)
                    .background(CairnAccent, RoundedCornerShape(12.dp))
            ) {
                if (guardianLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = CairnBackground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("➤", color = CairnBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        guardianError?.let {
            Text(
                it,
                color = CairnDanger,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }

        ActionButton(
            "Ficha",
            Modifier.fillMaxWidth(),
            outlined = true,
            onClick = onBack
        )

        if (c.combat == null) {
            Text(
                if (r.deprived) {
                    "Sem recuperação: o aventureiro está privado de necessidades básicas."
                } else {
                    "Descanso seguro recupera todo o HP e remove toda a Fadiga. Não é um avanço narrativo."
                },
                color = CairnMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
            ActionButton(
                "Descanso seguro",
                Modifier.fillMaxWidth(),
                outlined = true,
                enabled = !r.deprived && (r.hp < r.maxHp || r.fatigue > 0)
            ) {
                if (pendingRule == null && lastResolution == null) onAction(GameAction.ExploreRest)
            }
        }
    }

    if (c.combat == null) {
        c.growth.pendingChangeProposals.firstOrNull()?.let { proposal ->
            GrowthReviewDialog(proposal, onDecideGrowth)
        }
    }
}

@Composable
private fun GrowthReviewDialog(
    proposal: GrowthChangeProposal,
    onDecision: (String, Boolean) -> Unit
) {
    val proposedChange = when (proposal.changeType.uppercase()) {
        "RAISE_MAX_ATTRIBUTE" -> "Aumentar o atributo máximo ${proposal.attribute.orEmpty().uppercase()} em ${proposal.amount ?: 1}."
        "KEEP_HIGHER_ATTRIBUTE" -> "Definir ${proposal.attribute.orEmpty().uppercase()} como ${proposal.candidate ?: "—"}, se for maior."
        "GAIN_ABILITY" -> "Obter ${proposal.abilityName.orEmpty()}: ${proposal.abilityDescription.orEmpty()}"
        else -> "Uma mudança de Growth foi proposta."
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("CRESCIMENTO DISPONÍVEL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(proposedChange, color = CairnText)
                Text(proposal.rationale, color = CairnMuted)
                Text("Evidências: ${proposal.evidenceIds.size}", color = CairnAccent, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecision(proposal.id, true) }) { Text("Aceitar") }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(proposal.id, false) }) { Text("Recusar") }
        },
        containerColor = CairnSurface,
        titleContentColor = CairnAccent,
        textContentColor = CairnText
    )
}

@Composable
private fun RewardFeedbackCard(entries: List<FeedbackEntry>) {
    if (entries.isEmpty()) return
    SectionCard {
        Text("RECOMPENSA", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        entries.takeLast(6).forEach { entry ->
            val color = when (entry.type) {
                FeedbackType.WARNING, FeedbackType.FAILURE, FeedbackType.DAMAGE, FeedbackType.CRITICAL -> CairnDanger
                FeedbackType.SUCCESS, FeedbackType.INVENTORY -> CairnAccent
                FeedbackType.INFO -> CairnMuted
            }
            Text(entry.message, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RollRequestCard(
    request: GuardianRuleRequest,
    enabled: Boolean,
    onRoll: () -> Unit,
    onDismiss: () -> Unit
) {
    SectionCard {
        Text("ROLAGEM NECESSÁRIA", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(ruleRequestTitle(request), color = CairnText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(ruleRequestDescription(request), color = CairnMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRoll,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(7.dp)
        ) {
            Text(if (request.type.equals("SAVE", ignoreCase = true)) "Rolar 1d20" else "Resolver regra", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Descartar pedido") }
    }
}

@Composable
private fun RollResultCard(
    resolution: GuardianRuleResolution,
    enabled: Boolean,
    onContinue: () -> Unit
) {
    val save = resolution.gameResult.events.filterIsInstance<com.vanish994.cairnsolo.game.GameEvent.SaveResolved>().firstOrNull()
    val success = save?.success
    SectionCard {
        Text("RESULTADO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        if (save != null) {
            Text("${save.roll}", color = CairnText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                if (success == true) "SUCESSO" else "FALHA",
                color = if (success == true) CairnAccent else CairnDanger,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Teste de ${save.attribute.name}", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
            Text(resolution.resultText, color = CairnMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(resolution.resultText, color = CairnText, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(7.dp)
        ) { Text("Continuar história", fontWeight = FontWeight.Bold) }
    }
}

private fun ruleRequestTitle(request: GuardianRuleRequest): String = when (request.type.uppercase()) {
    "SAVE" -> "Teste de ${request.attribute.orEmpty().uppercase()}"
    "DAMAGE" -> "Perigo: dano iminente"
    "FATIGUE" -> "Exaustão"
    "REST" -> "Descanso"
    else -> request.type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun ruleRequestDescription(request: GuardianRuleRequest): String = when (request.type.uppercase()) {
    "SAVE" -> "Role 1d20 e obtenha sucesso se o resultado for igual ou menor que seu atributo."
    "DAMAGE" -> "O Rules Engine aplicará ${request.amount ?: 0} de dano, respeitando a Armadura."
    "FATIGUE" -> "O Rules Engine aplicará ${request.amount ?: 1} ponto(s) de Fadiga."
    "REST" -> "O Rules Engine resolverá o descanso conforme as regras de Cairn."
    else -> "O Rules Engine resolverá esta consequência antes da narrativa continuar."
}

@Composable
private fun EncounterProposalCard(
    proposal: GuardianEncounterProposal,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    SectionCard {
        Text("ENCONTRO PROPOSTO · SUA CONFIRMAÇÃO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(proposal.narrative.name, color = CairnText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(proposal.narrative.appearance, color = CairnMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text("Comportamento: ${proposal.narrative.behavior}", color = CairnMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text("Intenção: ${proposal.narrative.intent}", color = CairnMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text("Contexto: ${proposal.narrative.context}", color = CairnMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text(
            "FOR ${proposal.stats.str} · DES ${proposal.stats.dex} · VON ${proposal.stats.wil} · HP ${proposal.stats.hp}/${proposal.stats.maxHp} · ARM ${proposal.stats.armor}",
            color = CairnText,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "Arma: ${proposal.weapon.id} (${proposal.weapon.damage})" +
                (if (proposal.weapon.ranged) " · à distância" else "") +
                (if (proposal.weapon.blast) " · área" else ""),
            color = CairnMuted,
            style = MaterialTheme.typography.labelSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onAccept, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(7.dp)) {
                Text("Aceitar oponente", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), shape = RoundedCornerShape(7.dp)) {
                Text("Recusar")
            }
        }
    }
}


@Composable
private fun BattleMap(
    opponents: List<CombatOpponentState>,
    adventurerName: String,
    adventurerHp: Int,
    adventurerMaxHp: Int,
    selectedOpponentId: String?,
    onSelectOpponent: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF171A18)).border(1.dp, CairnBorder, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("CAMPO DE CONFRONTO", color = CairnAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 142.dp, max = 190.dp)) {
            val mapWidth = maxWidth
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF202923), size = size)
                val tile = size.width / 12f
                for (i in 0..12) {
                    drawLine(Color(0xFF28342A), androidx.compose.ui.geometry.Offset(i * tile, 0f), androidx.compose.ui.geometry.Offset(i * tile, size.height), 1f)
                }
                for (i in 0..6) {
                    drawLine(Color(0xFF28342A), androidx.compose.ui.geometry.Offset(0f, i * tile), androidx.compose.ui.geometry.Offset(size.width, i * tile), 1f)
                }
                drawCircle(Color(0xFF35402D), size.width * .12f, androidx.compose.ui.geometry.Offset(size.width * .25f, size.height * .30f))
                drawCircle(Color(0xFF303A2B), size.width * .09f, androidx.compose.ui.geometry.Offset(size.width * .76f, size.height * .70f))
                drawRect(Color(0xFF3C3529), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * .46f), size = androidx.compose.ui.geometry.Size(size.width, size.height * .12f))
                drawRect(Color(0xFF51412C), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * .49f), size = androidx.compose.ui.geometry.Size(size.width, size.height * .025f))
            }
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp).widthIn(max = mapWidth * .37f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A3027)).border(1.dp, CairnAccent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("◆", color = CairnAccent, style = MaterialTheme.typography.titleMedium)
                }
                Text(adventurerName, color = CairnText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("HP $adventurerHp/$adventurerMaxHp", color = if (adventurerHp <= 2) CairnDanger else CairnMuted, style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.60f).padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                opponents.take(4).forEach { opponent ->
                    val active = opponent.status == CombatOpponentStatus.ACTIVE
                    val selected = opponent.id == selectedOpponentId
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF55452D) else Color(0xCC171A18))
                            .border(if (selected) 2.dp else 1.dp, if (selected) CairnAccent else CairnBorder, RoundedCornerShape(6.dp))
                            .clickable(enabled = active) { onSelectOpponent(opponent.id) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(if (!active) "×" else "▲", color = if (active) Color(0xFFD3A16B) else CairnMuted, style = MaterialTheme.typography.titleMedium)
                        Text(opponent.narrative.name, color = if (active) CairnText else CairnMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("HP ${opponent.stats.hp}/${opponent.stats.maxHp}", color = CairnMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(combatOpponentStatusLabel(opponent.status), color = if (active) CairnAccent else CairnMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            if (opponents.size > 4) {
                Text("+${opponents.size - 4} fora do mapa", color = CairnMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
        }
        Text("Toque em um oponente ativo para selecioná-lo. Posições apenas ilustrativas; não representam alcance ou distância.", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CombatCard(
    combat: CombatState,
    adventurerName: String,
    adventurerHp: Int,
    adventurerMaxHp: Int,
    weapons: List<WeaponProfile>,
    enabled: Boolean,
    onAttack: (String, WeaponProfile?) -> Unit
) {
    val opponentIds = combat.opponents.map { it.id }
    val activeOpponents = combat.opponents.filter { it.status == CombatOpponentStatus.ACTIVE }
    var selectedOpponentId by remember(opponentIds) {
        mutableStateOf(activeOpponents.firstOrNull()?.id)
    }
    val selectedOpponent = activeOpponents.firstOrNull { it.id == selectedOpponentId }
        ?: activeOpponents.firstOrNull()

    SectionCard {
        Text("COMBATE · RODADA ${combat.round}", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            "${activeOpponents.size} adversário(s) ativo(s) · ${combat.opponents.size} no encontro",
            color = CairnMuted,
            style = MaterialTheme.typography.bodySmall
        )
        BattleMap(
            opponents = combat.opponents,
            adventurerName = adventurerName,
            adventurerHp = adventurerHp,
            adventurerMaxHp = adventurerMaxHp,
            selectedOpponentId = selectedOpponent?.id,
            onSelectOpponent = { id -> if (enabled && activeOpponents.any { it.id == id }) selectedOpponentId = id }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 144.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            combat.opponents.forEach { opponent ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            opponent.narrative.name,
                            color = CairnText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "HP ${opponent.stats.hp}/${opponent.stats.maxHp} · ARM ${opponent.stats.armor} · " +
                                "${opponent.weapon.id} ${opponent.weapon.damage.orEmpty()}",
                            color = CairnMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        combatOpponentStatusLabel(opponent.status),
                        color = if (opponent.status == CombatOpponentStatus.ACTIVE) CairnAccent else CairnMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "${opponent.narrative.appearance} · ${opponent.narrative.behavior} · ${opponent.narrative.intent}",
                    color = CairnMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (activeOpponents.isNotEmpty()) {
            Text("ESCOLHER ALVO", color = CairnAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeOpponents.forEach { opponent ->
                    FilterChip(
                        selected = opponent.id == selectedOpponent?.id,
                        onClick = { selectedOpponentId = opponent.id },
                        enabled = enabled,
                        label = { Text(opponent.narrative.name, maxLines = 1) }
                    )
                }
            }
            Text(
                "Alvo: ${selectedOpponent?.narrative?.name.orEmpty()}",
                color = CairnText,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (combat.playerCanAct) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weapons.forEach { weapon ->
                    Button(
                        onClick = { selectedOpponent?.let { onAttack(it.id, weapon) } },
                        enabled = enabled && selectedOpponent != null,
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Text("Atacar · ${weapon.id} ${weapon.damage.orEmpty()}")
                    }
                }
            }
        } else {
            Text("Os oponentes agem primeiro nesta rodada.", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun combatOpponentStatusLabel(status: CombatOpponentStatus): String = when (status) {
    CombatOpponentStatus.ACTIVE -> "Ativo"
    CombatOpponentStatus.DEFEATED -> "Derrotado"
    CombatOpponentStatus.FLED -> "Fugiu"
}

private fun guardianFlowLabel(flow: GuardianFlow): String = when (flow) {
    GuardianFlow.EXPLORATION -> "exploração"
    GuardianFlow.GUARDIAN_THINKING -> "Guardião pensando"
    GuardianFlow.ENCOUNTER_PROPOSED -> "encontro proposto"
    GuardianFlow.ROLL_REQUIRED -> "rolagem necessária"
    GuardianFlow.ROLL_RESULT -> "resultado"
    GuardianFlow.CONSEQUENCE_NARRATION -> "consequência"
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(40.dp),
            shape = RoundedCornerShape(7.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(40.dp),
            shape = RoundedCornerShape(7.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CharacterCreation(
    name: String,
    onNameChange: (String) -> Unit,
    rolled: RolledCharacter?,
    selectedBackground: Background?,
    onRoll: () -> Unit,
    onBackgroundChange: (Background) -> Unit,
    onSwap: (AttributeSlot, AttributeSlot) -> Unit,
    onRules: () -> Unit,
    onCreate: () -> Unit
) {
    var backgroundMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CairnHeader("CAIRN", "Criação do aventureiro", "Prepare sua entrada.") }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Nome do aventureiro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
        }
        item {
            SectionCard {
                Text("RETRATO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                PortraitPlaceholder()
            }
        }
        if (rolled == null) {
            item {
                SectionCard {
                    Text("Seu aventureiro ainda não foi gerado.", color = CairnMuted)
                    Spacer(Modifier.height(6.dp))
                    Text("Role os dados para gerar atributos, HP, background, idade e características.")
                }
            }
        } else {
            item {
                SectionCard {
                    Text("FICHA RÁPIDA", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("FOR", rolled.str, Modifier.weight(1f))
                        StatTile("DES", rolled.dex, Modifier.weight(1f))
                        StatTile("VON", rolled.wil, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("HP " + rolled.hp, fontWeight = FontWeight.SemiBold)
                        Text("Ouro " + rolled.gold + " po", color = CairnMuted)
                        Text("Idade " + (rolled.age ?: "—"), color = CairnMuted)
                    }
                }
            }
            item {
                SectionCard {
                    Text("TROCAR ATRIBUTOS", color = CairnMuted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.DEX) }, modifier = Modifier.weight(1f)) { Text("FOR ↔ DES") }
                        OutlinedButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.WIL) }, modifier = Modifier.weight(1f)) { Text("FOR ↔ VON") }
                    }
                    OutlinedButton(onClick = { onSwap(AttributeSlot.DEX, AttributeSlot.WIL) }, modifier = Modifier.fillMaxWidth()) { Text("DES ↔ VON") }
                }
            }
            item {
                SectionCard {
                    Text("BACKGROUND", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Box {
                        OutlinedButton(onClick = { backgroundMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedBackground?.let(::backgroundLabel) ?: "Escolher background")
                        }
                        DropdownMenu(expanded = backgroundMenu, onDismissRequest = { backgroundMenu = false }) {
                            Background.entries.forEach { background ->
                                DropdownMenuItem(
                                    text = { Text(backgroundLabel(background)) },
                                    onClick = { onBackgroundChange(background); backgroundMenu = false }
                                )
                            }
                        }
                    }
                }
            }
            item {
                SectionCard {
                    Text("CARACTERÍSTICAS", color = CairnMuted, style = MaterialTheme.typography.labelMedium)
                    rolled.traits?.let { traits ->
                        Text("Físico: " + traitLabel(traits.physique))
                        Text("Pele: " + traitLabel(traits.skin))
                        Text("Cabelo: " + traitLabel(traits.hair))
                        Text("Rosto: " + traitLabel(traits.face))
                        Text("Fala: " + traitLabel(traits.speech))
                        Text("Vestuário: " + traitLabel(traits.clothing))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRoll, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text(if (rolled == null) "Rolar" else "Rolar novamente")
                }
                Button(onClick = onCreate, enabled = name.isNotBlank() && rolled != null, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Começar aventura")
                }
            }
        }
        item {
            OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("Como jogar") }
        }
        item {
            Text("FOR, DES e VON: 3d6  ·  HP: 1d6  ·  Idade: 2d20 + 10", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun CharacterSheet(
    state: GameState,
    combatActive: Boolean,
    rewardFeedback: List<FeedbackEntry>,
    onDamage: () -> Unit,
    onExplore: () -> Unit,
    onRest: () -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onClaimReward: (String) -> Unit,
    onDelete: () -> Unit
) {
    val c = state.campaign
    val r = c.rules
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CairnHeader("CAIRN", c.character.name, "T" + c.turn + " · " + sceneTypeLabel(c.sceneType)) }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(CairnSurfaceRaised).border(1.dp, CairnAccentSoft, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val unit = size.width / 8f
                            drawRect(Color(0xFF28212A), size = size)
                            drawCircle(Color(0xFF6D5946), radius = unit * 2.4f, center = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .34f))
                            drawRect(Color(0xFF17151A), topLeft = androidx.compose.ui.geometry.Offset(unit * 1.5f, unit * 2.4f), size = androidx.compose.ui.geometry.Size(unit * 5f, unit * 5.6f))
                            drawRect(CairnAccentSoft, topLeft = androidx.compose.ui.geometry.Offset(unit * 2.5f, unit * 2.7f), size = androidx.compose.ui.geometry.Size(unit, unit * .7f))
                            drawRect(CairnAccentSoft, topLeft = androidx.compose.ui.geometry.Offset(unit * 4.5f, unit * 2.7f), size = androidx.compose.ui.geometry.Size(unit, unit * .7f))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(c.character.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CairnText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.profile.background?.let(::backgroundLabel) ?: "Aventureiro sem ofício", color = CairnAccent, style = MaterialTheme.typography.labelLarge)
                        Text("T" + c.turn + " · " + sceneTypeLabel(c.sceneType), color = CairnMuted, style = MaterialTheme.typography.bodySmall)
                        Text("✦ " + c.profile.gold + " GP", color = CairnAccent, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("VITALIDADE", color = CairnMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(r.hp.toString() + "/" + r.maxHp + " HP", color = if (r.hp <= 2) CairnDanger else CairnText, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)).background(CairnBorder)) {
                    Box(Modifier.fillMaxWidth((r.hp.toFloat() / r.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(if (r.hp <= 2) CairnDanger else CairnAccent))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("FOR", r.str, Modifier.weight(1f))
                    StatTile("DES", r.dex, Modifier.weight(1f))
                    StatTile("VON", r.wil, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MiniInfoTile("ARMADURA", r.armor.toString(), Modifier.weight(1f))
                    MiniInfoTile("MOCHILA", r.usedSlots.toString() + "/10", Modifier.weight(1f))
                }
                if (r.deprived) Text("⚠ Privado", color = CairnDanger, modifier = Modifier.padding(top = 8.dp))
                if (r.critical) Text("⚠ Dano crítico", color = CairnDanger, modifier = Modifier.padding(top = 4.dp))
                r.scar?.let { Text("Cicatriz: " + scarLabel(it), color = CairnDanger, modifier = Modifier.padding(top = 4.dp)) }
                if (combatActive) Text("Combate ativo · retorne à exploração para agir.", color = CairnDanger, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            SectionCard {
                Text("BACKGROUND", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                c.profile.backgroundRolls?.let { Text("Resultados: " + it.first + " e " + it.second, color = CairnMuted) }
                c.profile.backgroundFeatures.forEach { Text("• " + backgroundOutcomeLabel(it)) }
                c.profile.companions.forEach { Text("Companheiro: " + it.id + "  ·  HP " + it.hp + "/" + it.maxHp) }
            }
        }
        item {
            SectionCard {
                Text("AÇÕES", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton(if (combatActive) "Voltar ao combate" else "Explorar", Modifier.weight(1f), onClick = onExplore)
                    ActionButton("Descansar", Modifier.weight(1f), outlined = true, enabled = !combatActive, onClick = onRest)
                }
            }
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("INVENTÁRIO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(r.usedSlots.toString() + "/10 espaços", color = CairnMuted, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(10) { index ->
                        Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(2.dp)).background(if (index < r.usedSlots) CairnAccent else CairnBorder))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (r.inventory.isEmpty()) Text("A mochila está vazia.", color = CairnMuted, modifier = Modifier.padding(vertical = 8.dp))
                r.inventory.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                            .background(CairnSurfaceRaised).border(1.dp, CairnBorder, RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).background(CairnBackground).border(1.dp, CairnAccentSoft, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text(when { item.armor > 0 -> "▣"; item.damage != null -> "⚔"; item.uses != null -> "✦"; else -> "◇" }, color = CairnAccent, style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(inventoryItemLabel(item), fontWeight = FontWeight.SemiBold, color = CairnText)
                            Text(buildList {
                                add(item.slotCost.toString() + if (item.slotCost == 1) " espaço" else " espaços")
                                item.damage?.let { add("Dano " + it) }
                                item.armor.takeIf { it > 0 }?.let { add("Armadura " + it) }
                                item.uses?.let { add(it.toString() + " usos") }
                            }.joinToString(" · "), color = CairnMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRemoveItem(item.id) }, enabled = !combatActive) { Text("×") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAddItem, enabled = r.freeSlots > 0 && !combatActive, modifier = Modifier.fillMaxWidth()) { Text("Adicionar item") }
                if (c.pendingRewardItems.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("RECOMPENSAS PENDENTES", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("A recompensa fica guardada até você liberar espaço no inventário.", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
                    c.pendingRewardItems.forEach { pending ->
                        val entry = MarketplaceCatalog.find(pending.catalogItemId)
                        val template = entry?.item
                        val slotsRequired = template?.slotCost ?: 0
                        val canClaim = template != null && r.freeSlots >= slotsRequired && !combatActive
                        val slotsMissing = (slotsRequired - r.freeSlots).coerceAtLeast(0)
                        ListItem(
                            headlineContent = { Text(entry?.name ?: pending.catalogItemId) },
                            supportingContent = {
                                val detail = when {
                                    template == null -> "Item indisponível no catálogo."
                                    combatActive -> "Retorne à exploração para resgatar."
                                    canClaim -> "$slotsRequired ${if (slotsRequired == 1) "espaço" else "espaços"} · ${r.freeSlots} livre(s)"
                                    else -> "Libere $slotsMissing ${if (slotsMissing == 1) "espaço" else "espaços"} para resgatar."
                                }
                                Text(detail)
                            },
                            trailingContent = {
                                TextButton(onClick = { onClaimReward(pending.id) }, enabled = canClaim) { Text("Resgatar") }
                            }
                        )
                    }
                    if (r.freeSlots == 0) {
                        Text("Cairn 2e: ocupar os 10 espaços reduz HP a 0. Libere espaço antes de resgatar.", color = CairnDanger, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { RewardFeedbackCard(rewardFeedback) }
        item {
            OutlinedButton(onClick = onDamage, enabled = !combatActive, modifier = Modifier.fillMaxWidth()) { Text("Receber 2 de dano (teste)") }
        }
        item {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CairnDanger)
            ) { Text("Apagar campanha") }
        }
    }
}


@Composable
private fun MiniInfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(7.dp)).background(CairnSurfaceRaised)
            .border(1.dp, CairnBorder, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = CairnMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = CairnText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CairnHeader("CAIRN", "Como jogar", "MJ narra · motor valida") }
        item {
            SectionCard {
                Text("O PRINCÍPIO", color = CairnAccent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Você descreve o que seu personagem tenta fazer. O Guardião interpreta a situação e conduz a história. O motor de regras resolve e valida os resultados mecânicos.")
            }
        }
        item {
            SectionCard {
                Text("ATRIBUTOS", color = CairnAccent, fontWeight = FontWeight.Bold)
                Text("FOR: força e resistência física.")
                Text("DES: velocidade, reflexos e precisão.")
                Text("VON: vontade, influência e magia.")
            }
        }
        item {
            SectionCard {
                Text("TESTES", color = CairnAccent, fontWeight = FontWeight.Bold)
                Text("Role 1d20 e obtenha sucesso se o resultado for igual ou menor que o atributo usado.")
                Text("1 natural é sempre sucesso; 20 natural é sempre falha.")
            }
        }
        item {
            SectionCard {
                Text("HP, DANO E INVENTÁRIO", color = CairnAccent, fontWeight = FontWeight.Bold)
                Text("A Armadura reduz o dano antes de ele reduzir seu HP. O inventário possui 10 espaços.")
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") } }
    }
}

private fun backgroundOutcomeLabel(key: String): String = key.replace("_", " ").replaceFirstChar { it.uppercase() }

private fun backgroundLabel(background: Background): String =
    when (background) {
        Background.AURIFEX -> "Aurífice"
        Background.BARBER_SURGEON -> "Barbeiro-Cirurgião"
        Background.BEAST_HANDLER -> "Tratador de Feras"
        Background.BONEKEEPER -> "Guardião de Ossos"
        Background.CUTPURSE -> "Batedor de Carteiras"
        Background.FIELDWARDEN -> "Guardião dos Campos"
        Background.FLETCHWIND -> "Fletchwind"
        Background.FOUNDLING -> "Enjeitado"
        Background.FUNGAL_FORAGER -> "Coletor de Fungos"
        Background.GREENWISE -> "Erveiro"
        Background.HALF_WITCH -> "Meio-Bruxo"
        Background.HEXENBANE -> "Hexenbane"
        Background.JONGLEUR -> "Saltimbanco"
        Background.KETTLEWRIGHT -> "Caldeireiro"
        Background.MARCHGUARD -> "Guarda da Fronteira"
        Background.MOUNTEBANK -> "Charlatão"
        Background.OUTRIDER -> "Batedor Montado"
        Background.PROWLER -> "Espreitador"
        Background.RILL_RUNNER -> "Corredor de Riacho"
        Background.SCRIVENER -> "Escrivão"
    }

private fun traitLabel(value: String): String =
    when (value) {
        "Athletic" -> "Atlético"
        "Brawny" -> "Musculoso"
        "Flabby" -> "Flácido"
        "Lanky" -> "Esbelto"
        "Rugged" -> "Robusto"
        "Scrawny" -> "Magricela"
        "Short" -> "Baixo"
        "Statuesque" -> "Imponente"
        "Stout" -> "Atarracado"
        "Towering" -> "Muito alto"
        "Birthmarked" -> "Com marca de nascença"
        "Marked" -> "Marcado"
        "Oily" -> "Oleoso"
        "Rosy" -> "Rosado"
        "Scarred" -> "Marcado por cicatrizes"
        "Soft" -> "Suave"
        "Tanned" -> "Bronzeado"
        "Tattooed" -> "Tatuado"
        "Weathered" -> "Envelhecido pelo tempo"
        "Webbed" -> "Com membranas"
        "Bald" -> "Careca"
        "Braided" -> "Trançado"
        "Curly" -> "Cacheado"
        "Filthy" -> "Sujo"
        "Frizzy" -> "Arrepiado"
        "Long" -> "Longo"
        "Luxurious" -> "Luxuoso"
        "Wavy" -> "Ondulado"
        "Wispy" -> "Fino e ralo"
        "Bony" -> "Ossudo"
        "Broken" -> "Quebrado"
        "Chiseled" -> "Talhado"
        "Elongated" -> "Alongado"
        "Pale" -> "Pálido"
        "Perfect" -> "Perfeito"
        "Rakish" -> "Arrojado"
        "Sharp" -> "Afiado"
        "Square" -> "Quadrado"
        "Sunken" -> "Encovado"
        "Blunt" -> "Direto"
        "Booming" -> "Retumbante"
        "Cryptic" -> "Enigmático"
        "Droning" -> "Monótono"
        "Formal" -> "Formal"
        "Gravelly" -> "Áspero"
        "Precise" -> "Preciso"
        "Squeaky" -> "Estridente"
        "Stuttering" -> "Gaguejante"
        "Whispery" -> "Sussurrante"
        "Antique" -> "Antigo"
        "Bloody" -> "Manchado de sangue"
        "Elegant" -> "Elegante"
        "Foreign" -> "Estrangeiro"
        "Frayed" -> "Desgastado"
        "Frumpy" -> "Desajeitado"
        "Livery" -> "Uniformizado"
        "Rancid" -> "Ranço"
        "Soiled" -> "Manchado"
        "Ambitious" -> "Ambicioso"
        "Cautious" -> "Cauteloso"
        "Courageous" -> "Corajoso"
        "Disciplined" -> "Disciplinado"
        "Gregarious" -> "Sociável"
        "Honorable" -> "Honrado"
        "Humble" -> "Humilde"
        "Merciful" -> "Misericordioso"
        "Serene" -> "Sereno"
        "Tolerant" -> "Tolerante"
        "Aggressive" -> "Agressivo"
        "Bitter" -> "Amargurado"
        "Craven" -> "Covarde"
        "Deceitful" -> "Enganador"
        "Greedy" -> "Ganancioso"
        "Lazy" -> "Preguiçoso"
        "Nervous" -> "Nervoso"
        "Rude" -> "Grosseiro"
        "Vain" -> "Vaidoso"
        "Vengeful" -> "Vingativo"
        else -> value
    }

private fun sceneTypeLabel(type: com.vanish994.cairnsolo.game.SceneType): String =
    when (type) {
        com.vanish994.cairnsolo.game.SceneType.EXPLORATION -> "Exploração"
        com.vanish994.cairnsolo.game.SceneType.SOCIAL -> "Conversa"
        com.vanish994.cairnsolo.game.SceneType.COMBAT -> "Combate"
        com.vanish994.cairnsolo.game.SceneType.MANAGEMENT -> "Gestão"
    }

private fun scarLabel(scar: com.vanish994.cairnsolo.rules.Scar): String =
    when (scar) {
        com.vanish994.cairnsolo.rules.Scar.LASTING -> "Cicatriz Duradoura"
        com.vanish994.cairnsolo.rules.Scar.RATTLING -> "Golpe Chocante"
        com.vanish994.cairnsolo.rules.Scar.WALLOPED -> "Sacudido"
        com.vanish994.cairnsolo.rules.Scar.BROKEN_LIMB -> "Membro Quebrado"
        com.vanish994.cairnsolo.rules.Scar.DISEASED -> "Doença"
        com.vanish994.cairnsolo.rules.Scar.HEAD_WOUND -> "Ferimento Desorientador na Cabeça"
        com.vanish994.cairnsolo.rules.Scar.HAMSTRUNG -> "Tendão Partido"
        com.vanish994.cairnsolo.rules.Scar.DEAFENED -> "Ensurdecido"
        com.vanish994.cairnsolo.rules.Scar.RE_BRAINED -> "Reconfiguração Mental"
        com.vanish994.cairnsolo.rules.Scar.SUNDERED -> "Membro Estropiado"
        com.vanish994.cairnsolo.rules.Scar.MORTAL_WOUND -> "Ferimento Mortal"
        com.vanish994.cairnsolo.rules.Scar.DOOMED -> "Condenado"
    }
