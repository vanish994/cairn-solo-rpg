package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.guardian.HttpGuardianClient
import com.vanish994.cairnsolo.guardian.GuardianRuleResolver
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.LocalGameStateRepository
import com.vanish994.cairnsolo.rules.*
import kotlin.random.Random

private val CairnBackground = Color(0xFF100F0D)
private val CairnSurface = Color(0xFF191714)
private val CairnSurfaceRaised = Color(0xFF211E1A)
private val CairnText = Color(0xFFE9E0D4)
private val CairnMuted = Color(0xFFA99E90)
private val CairnAccent = Color(0xFFD0B08A)
private val CairnAccentSoft = Color(0xFF6F5A43)
private val CairnDanger = Color(0xFFB97865)

private val CairnColors = darkColorScheme(
    primary = CairnAccent,
    onPrimary = Color(0xFF241B13),
    secondary = CairnMuted,
    background = CairnBackground,
    surface = CairnSurface,
    surfaceVariant = CairnSurfaceRaised,
    onSurface = CairnText,
    onSurfaceVariant = CairnMuted,
    outline = Color(0xFF62594F)
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
                val actionResolver = remember {
                    GameActionResolver(
                        exploration = ExplorationEngine(KotlinRandomSource()),
                        rules = RulesEngine(KotlinRandomSource())
                    )
                }
                val guardianRuleResolver = remember { GuardianRuleResolver(actionResolver) }
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
                                    screen = AppScreen.CHARACTER
                                }
                            )
                            AppScreen.EXPLORATION -> ExplorationScreen(
                                state = current,
                                onAction = { action ->
                                    val result = actionResolver.resolve(current, action)
                                    repository.save(result.state)
                                    state = result.state
                                },
                                guardianLoading = guardianLoading,
                                guardianError = guardianError,
                                onGuardianIntent = { intent ->
                                    guardianLoading = true
                                    guardianError = null
                                    scope.launch {
                                        guardianClient.narrate(current, intent)
                                            .onSuccess { response ->
                                                var next = current.applyGuardianResponse(
                                                    narration = response.narration,
                                                    sceneTitle = response.sceneTitle,
                                                    sceneDescription = response.sceneDescription,
                                                    interactionId = response.interactionId
                                                )
                                                repository.save(next)
                                                state = next

                                                response.ruleRequest?.let { request ->
                                                    runCatching {
                                                        val resolution = guardianRuleResolver.resolve(next, request)
                                                        next = resolution.state
                                                        repository.save(next)
                                                        state = next

                                                        guardianClient.narrate(
                                                            next,
                                                            "O motor de regras resolveu a solicitação anterior. " +
                                                                "Resultado autoritativo: " + resolution.resultText
                                                        )
                                                            .onSuccess { followUp ->
                                                                next = next.applyGuardianResponse(
                                                                    narration = followUp.narration,
                                                                    sceneTitle = followUp.sceneTitle,
                                                                    sceneDescription = followUp.sceneDescription,
                                                                    interactionId = followUp.interactionId
                                                                )
                                                                repository.save(next)
                                                                state = next
                                                            }
                                                            .onFailure { error ->
                                                                guardianError = error.message
                                                                    ?: "A regra foi resolvida, mas o Guardião não respondeu à consequência."
                                                            }
                                                    }.onFailure { error ->
                                                        guardianError = error.message
                                                            ?: "O Guardião solicitou uma regra que o motor não reconhece."
                                                    }
                                                }
                                            }
                                            .onFailure { error ->
                                                guardianError = error.message ?: "Não foi possível falar com o Guardião."
                                            }
                                        guardianLoading = false
                                    }
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

@Composable
private fun CairnHeader(eyebrow: String, title: String, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge, color = CairnAccent, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = CairnText, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, color = CairnMuted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CairnSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332E28))
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun GuardianCard(message: String) {
    SectionCard {
        Text("GUARDIÃO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = CairnText)
    }
}

@Composable
private fun PortraitPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0B0A09)).border(1.dp, Color(0xFF3B342C), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(150.dp)) {
            val unit = size.minDimension / 16f
            drawCircle(CairnAccent, radius = unit * 3.1f, center = androidx.compose.ui.geometry.Offset(size.width / 2f, unit * 4.5f))
            drawRoundRect(
                CairnAccent,
                topLeft = androidx.compose.ui.geometry.Offset(unit * 3f, unit * 7f),
                size = androidx.compose.ui.geometry.Size(unit * 10f, unit * 7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 1.5f)
            )
            drawRect(CairnAccentSoft, topLeft = androidx.compose.ui.geometry.Offset(unit * 4f, unit * 10f), size = androidx.compose.ui.geometry.Size(unit * 8f, unit * 4f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(CairnSurfaceRaised).padding(vertical = 12.dp),
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
    guardianLoading: Boolean,
    guardianError: String?,
    onGuardianIntent: (String) -> Unit,
    onBack: () -> Unit
) {
    val c = state.campaign
    var intent by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CairnHeader("CAIRN", "Exploração", "Turno " + c.turn + "  ·  " + sceneTypeLabel(c.sceneType)) }
        item {
            SectionCard {
                Text("PRÓLOGO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(c.sceneTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(c.sceneDescription, color = CairnMuted)
            }
        }
        item { GuardianCard(c.guardianMessage) }
        item {
            SectionCard {
                Text("O QUE VOCÊ FAZ?", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = intent,
                    onValueChange = { intent = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    placeholder = { Text("Descreva a intenção do aventureiro…") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onGuardianIntent(intent); intent = "" },
                    enabled = intent.isNotBlank() && !guardianLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (guardianLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (guardianLoading) "O Guardião responde…" else "Falar com o Guardião")
                }
                guardianError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = CairnDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("AÇÕES DA CENA", color = CairnMuted, style = MaterialTheme.typography.labelLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Continuar", Modifier.weight(1f)) { onAction(GameAction.ExploreContinue) }
                ActionButton("Investigar", Modifier.weight(1f)) { onAction(GameAction.ExploreInvestigate) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Descansar", Modifier.weight(1f), outlined = true) { onAction(GameAction.ExploreRest) }
                ActionButton("Ficha", Modifier.weight(1f), outlined = true, onClick = onBack)
            }
        }
        item {
            SectionCard {
                Text("DIÁRIO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (c.log.isEmpty()) Text("A história ainda não deixou marcas.", color = CairnMuted)
                else c.log.takeLast(6).forEach { entry -> Text("• " + entry, color = CairnMuted, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
    } else {
        Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CairnHeader("CAIRN", "Criação do aventureiro", "Defina quem entrará na história.") }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Nome do aventureiro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            )
        }
        item {
            SectionCard {
                Text("RETRATO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                PortraitPlaceholder()
            }
        }
        if (rolled == null) {
            item {
                SectionCard {
                    Text("Seu aventureiro ainda não foi gerado.", color = CairnMuted)
                    Spacer(Modifier.height(8.dp))
                    Text("Role os dados para gerar atributos, HP, background, idade e características.")
                }
            }
        } else {
            item {
                SectionCard {
                    Text("FICHA RÁPIDA", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile("FOR", rolled.str, Modifier.weight(1f))
                        StatTile("DES", rolled.dex, Modifier.weight(1f))
                        StatTile("VON", rolled.wil, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HP " + rolled.hp, fontWeight = FontWeight.SemiBold)
                        Text("Ouro " + rolled.gold + " po", color = CairnMuted)
                        Text("Idade " + (rolled.age ?: "—"), color = CairnMuted)
                    }
                }
            }
            item {
                SectionCard {
                    Text("TROCAR ATRIBUTOS", color = CairnMuted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.DEX) }, modifier = Modifier.weight(1f)) { Text("FOR ↔ DES") }
                        OutlinedButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.WIL) }, modifier = Modifier.weight(1f)) { Text("FOR ↔ VON") }
                    }
                    OutlinedButton(onClick = { onSwap(AttributeSlot.DEX, AttributeSlot.WIL) }, modifier = Modifier.fillMaxWidth()) { Text("DES ↔ VON") }
                }
            }
            item {
                SectionCard {
                    Text("BACKGROUND", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRoll, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text(if (rolled == null) "Rolar" else "Rolar novamente")
                }
                Button(onClick = onCreate, enabled = name.isNotBlank() && rolled != null, modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Text("Começar aventura")
                }
            }
        }
        item {
            OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("Como jogar") }
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CairnHeader("CAIRN", c.character.name, "Turno " + c.turn + "  ·  " + sceneTypeLabel(c.sceneType)) }
        item {
            SectionCard {
                Text("ESTADO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("HP " + r.hp + "/" + r.maxHp, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("FOR " + r.str + "   DES " + r.dex + "   VON " + r.wil)
                Text("Armadura " + r.armor + "   Espaços " + r.usedSlots + "/10", color = CairnMuted)
                c.profile.background?.let { Text(backgroundLabel(it), color = CairnAccent) }
                if (r.deprived) Text("Privado", color = CairnDanger)
                if (r.critical) Text("Dano crítico", color = CairnDanger)
                r.scar?.let { Text("Cicatriz: " + scarLabel(it), color = CairnDanger) }
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton("Explorar", Modifier.weight(1f), onClick = onExplore)
                    ActionButton("Descansar", Modifier.weight(1f), outlined = true, onClick = onRest)
                }
            }
        }
        item {
            SectionCard {
                Text("INVENTÁRIO", color = CairnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (r.inventory.isEmpty()) Text("Nenhum item", color = CairnMuted)
                r.inventory.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.id) },
                        supportingContent = {
                            Text(buildList {
                                add(item.slotCost.toString() + if (item.slotCost == 1) " espaço" else " espaços")
                                item.damage?.let { add(it) }
                                item.armor.takeIf { it > 0 }?.let { add("Armadura " + it) }
                                item.uses?.let { add(it.toString() + " usos") }
                            }.joinToString(" • "))
                        },
                        trailingContent = { TextButton(onClick = { onRemoveItem(item.id) }) { Text("Remover") } }
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onAddItem, enabled = r.freeSlots > 0, modifier = Modifier.fillMaxWidth()) { Text("Adicionar item") }
            }
        }
        item {
            OutlinedButton(onClick = onDamage, modifier = Modifier.fillMaxWidth()) { Text("Receber 2 de dano (teste)") }
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
private fun RulesScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CairnHeader("CAIRN", "Como jogar", "MJ narra · motor de regras valida") }
        item {
            SectionCard {
                Text("O PRINCÍPIO", color = CairnAccent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
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
