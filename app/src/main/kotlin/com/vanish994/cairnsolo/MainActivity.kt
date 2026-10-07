package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vanish994.cairnsolo.ai.AIProviderFactory
import com.vanish994.cairnsolo.ai.WardenRequest
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.LocalGameStateRepository
import com.vanish994.cairnsolo.game.MJContext
import kotlinx.coroutines.launch
import com.vanish994.cairnsolo.rules.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalGameStateRepository(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = CairnGold,
                    onPrimary = CairnInk,
                    primaryContainer = CairnGoldDim,
                    onPrimaryContainer = CairnPaper,
                    secondary = CairnEmber,
                    background = CairnInk,
                    surface = CairnPanel,
                    surfaceVariant = CairnPanelRaised,
                    onBackground = CairnPaper,
                    onSurface = CairnPaper,
                    onSurfaceVariant = CairnMuted
                )
            ) {
                var state by remember { mutableStateOf(repository.load()) }
                var name by remember { mutableStateOf("") }
                var rolled by remember { mutableStateOf<RolledCharacter?>(null) }
                var selectedBackground by remember { mutableStateOf<Background?>(null) }
                var wardenNarrative by remember { mutableStateOf<String?>(null) }
                var playerInput by remember { mutableStateOf("") }
                var wardenStatus by remember { mutableStateOf("Warden local") }
                var wardenLoading by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                val aiProvider = remember { AIProviderFactory.create(BuildConfig.GEMINI_API_KEY) }
                val actionResolver = remember {
                    GameActionResolver(
                        exploration = ExplorationEngine(KotlinRandomSource()),
                        rules = RulesEngine(KotlinRandomSource())
                    )
                }
                var screen by remember { mutableStateOf(AppScreen.CHARACTER) }
                var showStartScreen by remember { mutableStateOf(true) }

                fun requestWarden(promptState: GameState, input: String, mechanics: String) {
                    coroutineScope.launch {
                        wardenLoading = true
                        wardenStatus = if (BuildConfig.GEMINI_API_KEY.isBlank()) "Warden local" else "Gemini pensando…"
                        val outcome = aiProvider.narrate(
                            WardenRequest(
                                context = MJContext.from(promptState),
                                playerInput = input,
                                rulesEngineResult = mechanics
                            )
                        )
                        outcome.onSuccess { response ->
                            wardenNarrative = response.narrative
                            wardenStatus = if (BuildConfig.GEMINI_API_KEY.isBlank()) "Warden local" else "Gemini ativo"
                        }.onFailure { error ->
                            wardenNarrative = WardenRequestFallback.narrative(promptState).narrative
                            wardenStatus = "Gemini indisponível — usando modo local"
                        }
                        wardenLoading = false
                    }
                }

                LaunchedEffect(screen) {
                    if (screen == AppScreen.EXPLORATION && state != null && wardenNarrative == null) {
                        requestWarden(
                            promptState = state!!,
                            input = "Observar a cena e apresentar o início da aventura.",
                            mechanics = "Nenhum resultado mecânico novo."
                        )
                    }
                }

                Surface(Modifier.fillMaxSize()) {
                    if (showStartScreen) {
                        CairnStartScreen(onStart = { showStartScreen = false })
                    } else if (state == null) {
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
                                onSwap = { first, second ->
                                    rolled = rolled?.swapAttributes(first, second)
                                },
                                onRules = { screen = AppScreen.RULES },
                                onCreate = {
                                    val r = rolled ?: rollCharacter(KotlinRandomSource())
                                    val finalRolled = r.copy(background = selectedBackground ?: r.background)
                                    val created = createCharacter(name.trim(), finalRolled)
                                    repository.save(created)
                                    state = created
                                    screen = AppScreen.EXPLORATION
                                }
                            )
                        }
                    } else {
                        val current = state!!
                        when (screen) {
                            AppScreen.CHARACTER -> CharacterSheet(
                                state = current,
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
                                onDelete = {
                                    repository.clear()
                                    state = null
                                }
                            )

                            AppScreen.EXPLORATION -> ExplorationScreen(
                                state = current,
                                wardenNarrative = wardenNarrative,
                                wardenStatus = wardenStatus,
                                wardenLoading = wardenLoading,
                                playerInput = playerInput,
                                onPlayerInputChange = { playerInput = it },
                                onSubmitPlayerInput = {
                                    val input = playerInput.trim()
                                    if (input.isNotBlank() && !wardenLoading) {
                                        playerInput = ""
                                        requestWarden(current, input, "Nenhum resultado mecânico novo; interpretar apenas a ficção.")
                                    }
                                },
                                onAction = { action ->
                                    val result = actionResolver.resolve(current, action)
                                    repository.save(result.state)
                                    state = result.state
                                    requestWarden(result.state, action.playerFacingLabel(), result.events.joinToString("\n"))
                                },
                                onBack = { screen = AppScreen.CHARACTER }
                            )

                            AppScreen.RULES -> RulesScreen(
                                onBack = { screen = AppScreen.CHARACTER }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CairnStartScreen(onStart: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .clickable { onStart() }
    ) {
        Image(
            painter = painterResource(R.drawable.cairn_title_art),
            contentDescription = "Tela de abertura de Cairn Solo RPG",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(92.dp))
            Text(
                "CAIRN",
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFFE5DDCE)
            )
            Text(
                "SOLO RPG",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB66A25)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "TOQUE PARA COMEÇAR",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFE5DDCE)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "by Vanish",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE5DDCE).copy(alpha = 0.72f)
            )
        }
    }
}

enum class AppScreen { CHARACTER, EXPLORATION, RULES }

private fun GameAction.playerFacingLabel(): String = when (this) {
    GameAction.ExploreContinue -> "Continuar pela cena"
    GameAction.ExploreInvestigate -> "Investigar a situação"
    GameAction.ExploreRest -> "Descansar"
    else -> toString()
}

private object WardenRequestFallback {
    fun narrative(state: GameState): com.vanish994.cairnsolo.ai.WardenResponse =
        com.vanish994.cairnsolo.ai.WardenResponse(
            narrative = state.campaign.sceneDescription,
            suggestedActions = state.campaign.exits.take(3),
            intent = null
        )
}

private val CAIRN_PROTAGONIST_SPRITES = listOf(
    R.drawable.cairn_protagonist_01,
    R.drawable.cairn_protagonist_02,
    R.drawable.cairn_protagonist_03,
    R.drawable.cairn_protagonist_04,
    R.drawable.cairn_protagonist_05,
)

@Composable
private fun ExplorationScreen(
    state: GameState,
    wardenNarrative: String?,
    wardenStatus: String,
    wardenLoading: Boolean,
    playerInput: String,
    onPlayerInputChange: (String) -> Unit,
    onSubmitPlayerInput: () -> Unit,
    onAction: (GameAction) -> Unit,
    onBack: () -> Unit
) {
    val c = state.campaign
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CairnInk)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("EXPLORAÇÃO", style = CairnEyebrow())
                    Text("O caminho ainda não terminou", style = CairnTitle())
                }
                CairnTurnBadge(c.turn)
            }
        }
        item {
            CairnStoryCard(
                eyebrow = "CENA",
                title = c.sceneTitle.ifBlank { "A estrada esquecida" },
                body = c.sceneDescription.ifBlank { "A névoa cobre o caminho e nenhum som se move além das árvores." },
                accent = CairnGold
            )
        }
        item {
            CairnStoryCard(
                eyebrow = "GUARDIÃO",
                title = if (wardenLoading) "O Guardião observa" else wardenStatus,
                body = wardenNarrative ?: "A presença à frente permanece em silêncio, esperando sua próxima escolha.",
                accent = CairnEmber
            )
        }
        item {
            OutlinedTextField(
                value = playerInput,
                onValueChange = onPlayerInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("O que você faz?", color = CairnGold) },
                placeholder = { Text("Descreva a ação do seu aventureiro…", color = CairnMuted) },
                minLines = 4,
                maxLines = 6,
                enabled = !wardenLoading,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CairnGold,
                    unfocusedBorderColor = CairnLine,
                    focusedContainerColor = CairnPanel,
                    unfocusedContainerColor = CairnPanel,
                    cursorColor = CairnGold,
                    focusedTextColor = CairnPaper,
                    unfocusedTextColor = CairnPaper
                )
            )
        }
        item {
            Button(
                onClick = onSubmitPlayerInput,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = playerInput.isNotBlank() && !wardenLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CairnGold,
                    contentColor = CairnInk,
                    disabledContainerColor = CairnPanelRaised,
                    disabledContentColor = CairnMuted
                )
            ) {
                Text(if (wardenLoading) "O GUARDIÃO ESTÁ PENSANDO…" else "FALAR COM O GUARDIÃO", fontWeight = FontWeight.Bold)
            }
        }
        item {
            if (c.exits.isNotEmpty()) {
                Text("Possibilidades  •  " + c.exits.joinToString("  •  "), style = MaterialTheme.typography.bodySmall, color = CairnMuted)
            }
        }
        item {
            CairnBottomBar(
                onContinue = { onAction(GameAction.ExploreContinue) },
                onInvestigate = { onAction(GameAction.ExploreInvestigate) },
                onSheet = onBack,
                onRest = { onAction(GameAction.ExploreRest) }
            )
        }
        item {
            Text("REGISTRO DA JORNADA", style = CairnEyebrow(), modifier = Modifier.padding(top = 6.dp))
        }
        items(c.log.takeLast(10)) { entry ->
            Text(entry, modifier = Modifier.fillMaxWidth(), color = CairnMuted, style = MaterialTheme.typography.bodySmall)
        }
        item { Spacer(Modifier.height(12.dp)) }
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
    var selectedSprite by remember { mutableStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CairnInk)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("CAIRN • SOLO RPG", style = CairnEyebrow())
        Text("Criação do aventureiro", style = CairnTitle())
        Text("Antes da estrada, existe uma escolha.", color = CairnMuted, style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nome do aventureiro", color = CairnGold) },
            placeholder = { Text("Como será lembrado?", color = CairnMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CairnGold,
                unfocusedBorderColor = CairnLine,
                focusedContainerColor = CairnPanel,
                unfocusedContainerColor = CairnPanel,
                cursorColor = CairnGold,
                focusedTextColor = CairnPaper,
                unfocusedTextColor = CairnPaper
            )
        )

        CairnPanelCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RETRATO", style = CairnEyebrow())
                    Image(
                        painter = painterResource(CAIRN_PROTAGONIST_SPRITES[selectedSprite]),
                        contentDescription = "Retrato do aventureiro ${selectedSprite + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(190.dp).fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CAIRN_PROTAGONIST_SPRITES.size) { index ->
                            Surface(
                                modifier = Modifier.size(38.dp).clickable { selectedSprite = index },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedSprite == index) CairnGoldDim else CairnPanelRaised,
                                border = if (selectedSprite == index) BorderStroke(1.dp, CairnGold) else null
                            ) {
                                Image(
                                    painter = painterResource(CAIRN_PROTAGONIST_SPRITES[index]),
                                    contentDescription = "Selecionar retrato ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        }
                    }
                }
                Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FICHA DO DESTINO", style = CairnEyebrow())
                    if (rolled == null) {
                        Text("Role os dados para revelar atributos, vitalidade e passado.", color = CairnMuted)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CairnStat("FOR", rolled.str)
                            CairnStat("DES", rolled.dex)
                            CairnStat("VON", rolled.wil)
                        }
                        Text("HP ${rolled.hp}   •   ${rolled.gold} po", color = CairnPaper)
                        Text("${rolled.age ?: "—"} anos  •  ${selectedBackground?.let(::backgroundLabel) ?: "sem passado"}", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
                        Text("${rolled.traits?.let { traitLabel(it.physique) } ?: "—"}  /  ${rolled.traits?.let { traitLabel(it.clothing) } ?: "—"}", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.DEX) }) { Text("FOR ↔ DES", color = CairnGold) }
                            TextButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.WIL) }) { Text("FOR ↔ VON", color = CairnGold) }
                        }
                        Box {
                            OutlinedButton(onClick = { backgroundMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                Text(selectedBackground?.let(::backgroundLabel) ?: "Escolher background")
                            }
                            DropdownMenu(expanded = backgroundMenu, onDismissRequest = { backgroundMenu = false }) {
                                Background.entries.forEach { background ->
                                    DropdownMenuItem(text = { Text(backgroundLabel(background)) }, onClick = { onBackgroundChange(background); backgroundMenu = false })
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRoll, modifier = Modifier.weight(0.8f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (rolled == null) "ROLAR" else "ROLAR NOVAMENTE", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank() && rolled != null,
                modifier = Modifier.weight(1.35f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CairnGold, contentColor = CairnInk, disabledContainerColor = CairnPanelRaised, disabledContentColor = CairnMuted)
            ) { Text("COMEÇAR AVENTURA", fontWeight = FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showHelp = !showHelp }) { Text(if (showHelp) "Ocultar fórmula" else "Como funciona?", color = CairnMuted) }
            TextButton(onClick = onRules) { Text("Regras", color = CairnMuted) }
        }
        if (showHelp) {
            CairnPanelCard {
                Text("FÓRMULA DOS ATRIBUTOS", style = CairnEyebrow())
                Text("FOR, DES e VON são rolados com 3d6. HP é 1d6. Background, idade e traços surgem de novas rolagens.", color = CairnMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CairnStoryCard(eyebrow: String, title: String, body: String, accent: Color) {
    CairnPanelCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(66.dp).background(accent, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow, style = CairnEyebrow(), color = accent)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, color = CairnMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CairnPanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CairnPanel),
        border = BorderStroke(1.dp, CairnLine)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun CairnTurnBadge(turn: Long) {
    Surface(shape = RoundedCornerShape(10.dp), color = CairnPanelRaised, border = BorderStroke(1.dp, CairnLine)) {
        Text("TURNO $turn", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = CairnEyebrow(), color = CairnGold)
    }
}

@Composable
private fun CairnStat(label: String, value: Int) {
    Surface(Modifier.weight(1f), shape = RoundedCornerShape(9.dp), color = CairnPanelRaised) {
        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = CairnEyebrow(), color = CairnGold)
            Text(value.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CairnBottomBar(onContinue: () -> Unit, onInvestigate: () -> Unit, onSheet: () -> Unit, onRest: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CairnBarAction("Continuar", onContinue, Modifier.weight(1f), primary = true)
        CairnBarAction("Investigar", onInvestigate, Modifier.weight(1f))
        CairnBarAction("Ficha", onSheet, Modifier.weight(1f))
        CairnBarAction("Descansar", onRest, Modifier.weight(1f))
    }
}

@Composable
private fun CairnBarAction(label: String, onClick: () -> Unit, modifier: Modifier, primary: Boolean = false) {
    if (primary) {
        Button(onClick, modifier.height(48.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = CairnGold, contentColor = CairnInk)) { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick, modifier.height(48.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun CairnEyebrow() = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4f.sp)
private fun CairnTitle() = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = CairnPaper)

private val CairnInk = Color(0xFF0C0B0A)
private val CairnPanel = Color(0xFF171411)
private val CairnPanelRaised = Color(0xFF211D18)
private val CairnLine = Color(0xFF42382C)
private val CairnPaper = Color(0xFFE7DDCB)
private val CairnMuted = Color(0xFFA79A88)
private val CairnGold = Color(0xFFD2A85E)
private val CairnGoldDim = Color(0xFF574325)
private val CairnEmber = Color(0xFFB96E4B)

@Composable
private fun CharacterSheet(
    state: GameState,
    onDamage: () -> Unit,
    onExplore: () -> Unit,
    onRest: () -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onDelete: () -> Unit
) {
    val c = state.campaign
    val r = c.rules

    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(c.character.name, style = MaterialTheme.typography.headlineMedium)
            Text("Turno " + c.turn + " • " + sceneTypeLabel(c.sceneType))
            Text(c.sceneTitle, style = MaterialTheme.typography.titleLarge)
            Text(c.sceneDescription)
            if (c.exits.isNotEmpty()) Text("Saídas: " + c.exits.joinToString(" • "))
            Spacer(Modifier.height(12.dp))
            Text("Pontos de vida: " + r.hp + "/" + r.maxHp)
            Text("FOR " + r.str + "   DES " + r.dex + "   VON " + r.wil)
            Text("Armadura " + r.armor + "   Espaços " + r.usedSlots + "/10")
            if (r.deprived) Text("Privado")
            if (r.critical) Text("Dano crítico")
            if (r.scar != null) Text("Cicatriz: " + scarLabel(r.scar))
            c.profile.age?.let { Text("Idade: $it anos") }
            c.profile.background?.let { Text("Background: " + backgroundLabel(it)) }
            c.profile.traits?.let { traits ->
                Spacer(Modifier.height(8.dp))
                Text("Características", style = MaterialTheme.typography.titleMedium)
                Text("Físico: " + traitLabel(traits.physique))
                Text("Pele: " + traitLabel(traits.skin))
                Text("Cabelo: " + traitLabel(traits.hair))
                Text("Rosto: " + traitLabel(traits.face))
                Text("Fala: " + traitLabel(traits.speech))
                Text("Vestuário: " + traitLabel(traits.clothing))
                Text("Virtude: " + traitLabel(traits.virtue))
                Text("Vício: " + traitLabel(traits.vice))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExplore) { Text("Explorar") }
                Button(onClick = onDamage) { Text("Receber 2 de dano") }
                Button(onClick = onRest) { Text("Descansar") }
            }
            Spacer(Modifier.height(12.dp))
            Text("Inventário", style = MaterialTheme.typography.titleMedium)
            if (r.inventory.isEmpty()) Text("Nenhum item")
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAddItem, enabled = r.freeSlots > 0) { Text("Adicionar item") }
            Spacer(Modifier.height(8.dp))
        }

        items(r.inventory, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.id) },
                supportingContent = { Text(item.slotCost.toString() + if (item.slotCost == 1) " espaço" else " espaços") },
                trailingContent = { TextButton(onClick = { onRemoveItem(item.id) }) { Text("Remover") } }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onDelete) { Text("Apagar campanha") }
        }
    }
}

private fun rollPlayableCharacter(): RolledCharacter {
    val random = KotlinRandomSource()
    return RolledCharacter(
        str = random.d6() + random.d6() + random.d6(),
        dex = random.d6() + random.d6() + random.d6(),
        wil = random.d6() + random.d6() + random.d6(),
        hp = random.d6(),
        background = Background.entries[random.d20() - 1],
        traits = rollTraits(random),
        age = rollAge(random).years
    )
}

@Composable
private fun RulesScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        item {
            Text("Como jogar Cairn", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Guia rápido para quem está começando. Você descreve o que seu personagem tenta fazer; o MJ apresenta a situação e o motor de regras resolve os resultados mecânicos.")
            Spacer(Modifier.height(16.dp))

            Text("Atributos", style = MaterialTheme.typography.titleMedium)
            Text("FOR: força e resistência física.")
            Text("DES: velocidade, reflexos e precisão.")
            Text("VON: vontade, influência e magia.")
            Spacer(Modifier.height(12.dp))

            Text("Testes", style = MaterialTheme.typography.titleMedium)
            Text("Role 1d20 e obtenha sucesso se o resultado for igual ou menor que o atributo usado.")
            Text("1 natural é sempre sucesso; 20 natural é sempre falha.")
            Spacer(Modifier.height(12.dp))

            Text("HP e dano", style = MaterialTheme.typography.titleMedium)
            Text("A Armadura reduz o dano antes de ele reduzir seu HP.")
            Text("Quando o HP chega a 0, podem surgir consequências de Dano Crítico.")
            Spacer(Modifier.height(12.dp))

            Text("Inventário", style = MaterialTheme.typography.titleMedium)
            Text("O personagem possui 10 espaços de inventário. Itens e Fadiga ocupam esses espaços conforme as regras.")
            Spacer(Modifier.height(12.dp))

            Text("Exploração", style = MaterialTheme.typography.titleMedium)
            Text("Escolha ações, observe as consequências e administre os recursos do personagem.")
            Spacer(Modifier.height(12.dp))

            Text("Regra principal", style = MaterialTheme.typography.titleMedium)
            Text("Você descreve o que quer fazer. O MJ interpreta a situação. O motor de regras decide os resultados mecânicos válidos.")
            Spacer(Modifier.height(16.dp))

            OutlinedButton(onClick = onBack) { Text("Voltar") }
        }
    }
}

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
