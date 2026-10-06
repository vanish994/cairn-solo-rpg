package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.vanish994.cairnsolo.feedback.FeedbackEntry
import com.vanish994.cairnsolo.feedback.FeedbackMapper
import com.vanish994.cairnsolo.game.GameAction
import com.vanish994.cairnsolo.game.GameActionResolver
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.ExplorationEngine
import com.vanish994.cairnsolo.game.LocalGameStateRepository
import com.vanish994.cairnsolo.rules.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalGameStateRepository(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFD9A441),
                onPrimary = Color(0xFF17120B),
                secondary = Color(0xFF7A8F6A),
                background = Color(0xFF0B0D0C),
                surface = Color(0xFF121615),
                onSurface = Color(0xFFE8E1D5)
            )) {
                var state by remember { mutableStateOf(repository.load()) }
                var name by remember { mutableStateOf("") }
                var rolled by remember { mutableStateOf<RolledCharacter?>(null) }
                var selectedBackground by remember { mutableStateOf<Background?>(null) }
                val actionResolver = remember {
                    GameActionResolver(
                        exploration = ExplorationEngine(KotlinRandomSource()),
                        rules = RulesEngine(KotlinRandomSource())
                    )
                }
                var screen by remember { mutableStateOf(AppScreen.CHARACTER) }
                var feedback by remember { mutableStateOf<List<FeedbackEntry>>(emptyList()) }

                Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0D0C)) {
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
                                feedback = feedback,
                                onDamage = {
                                    val result = actionResolver.resolve(current, GameAction.ApplyDamage(2))
                                    repository.save(result.state)
                                    state = result.state
                                    feedback = (feedback + FeedbackMapper.mapAll(result.events, result.state.campaign.turn)).takeLast(6)
                                },
                                onExplore = { screen = AppScreen.EXPLORATION },
                                onRest = {
                                    val result = actionResolver.resolve(current, GameAction.Rest)
                                    repository.save(result.state)
                                    state = result.state
                                    feedback = (feedback + FeedbackMapper.mapAll(result.events, result.state.campaign.turn)).takeLast(6)
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
                                feedback = feedback,
                                onAction = { action ->
                                    val result = actionResolver.resolve(current, action)
                                    repository.save(result.state)
                                    state = result.state
                                    feedback = (feedback + FeedbackMapper.mapAll(result.events, result.state.campaign.turn)).takeLast(6)
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

enum class AppScreen { CHARACTER, EXPLORATION, RULES }

@Composable
private fun ExplorationScreen(
    state: GameState,
    feedback: List<FeedbackEntry>,
    onAction: (GameAction) -> Unit,
    onBack: () -> Unit
) {
    val c = state.campaign
    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("CAIRN", style = MaterialTheme.typography.headlineMedium)
            Text("Turno " + c.turn + " • " + sceneTypeLabel(c.sceneType), color = Color(0xFFD9A441))
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(sceneImageResource(c.sceneTitle)),
                contentDescription = "Estrada antiga em pixel art",
                modifier = Modifier.fillMaxWidth().aspectRatio(1.45f).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF121615),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.sceneTitle, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(c.sceneDescription)
                }
            }
            Spacer(Modifier.height(16.dp))
            feedback.lastOrNull()?.let { entry ->
                FeedbackCard(entry)
                Spacer(Modifier.height(12.dp))
            }
            if (c.exits.isNotEmpty()) {
                Text("Possibilidades: " + c.exits.joinToString(" • "))
                Spacer(Modifier.height(12.dp))
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = { onAction(GameAction.ExploreContinue) }) { Text("Continuar") }
            Spacer(Modifier.height(8.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = { onAction(GameAction.ExploreInvestigate) }) { Text("Investigar") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onAction(GameAction.ExploreRest) }) { Text("Descansar") }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Voltar à ficha") }
            Spacer(Modifier.height(16.dp))
            Text("Diário", style = MaterialTheme.typography.titleMedium)
        }
        items(c.log.takeLast(10)) { entry ->
            Text(entry, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("Cairn Solo RPG", style = MaterialTheme.typography.headlineMedium)
            Text("Criação de personagem", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (rolled == null) {
                Text("Role o personagem para gerar atributos, HP, background, idade e características.")
            } else {
                Text("Atributos", style = MaterialTheme.typography.titleMedium)
                Text("FOR " + rolled.str + "   DES " + rolled.dex + "   VON " + rolled.wil)
                Text("HP: " + rolled.hp)
                Text("Ouro: " + rolled.gold + " po")
                Text("Vínculo: resultado " + (rolled.bondRoll ?: 0) + "/20")
                rolled.secondBondRoll?.let { Text("Segundo vínculo: resultado " + it + "/20") }
                rolled.omenRoll?.let { Text("Omen: resultado " + it + "/20") }
                Spacer(Modifier.height(8.dp))
                Text("Você pode trocar quaisquer dois resultados:")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.DEX) }) { Text("FOR ↔ DES") }
                    OutlinedButton(onClick = { onSwap(AttributeSlot.STR, AttributeSlot.WIL) }) { Text("FOR ↔ VON") }
                }
                OutlinedButton(onClick = { onSwap(AttributeSlot.DEX, AttributeSlot.WIL) }) { Text("DES ↔ VON") }

                Spacer(Modifier.height(8.dp))
                Text("Background", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(onClick = { backgroundMenu = true }) {
                        Text(selectedBackground?.let(::backgroundLabel) ?: "Escolher")
                    }
                    DropdownMenu(
                        expanded = backgroundMenu,
                        onDismissRequest = { backgroundMenu = false }
                    ) {
                        Background.entries.forEach { background ->
                            DropdownMenuItem(
                                text = { Text(backgroundLabel(background)) },
                                onClick = {
                                    onBackgroundChange(background)
                                    backgroundMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Perfil", style = MaterialTheme.typography.titleMedium)
                rolled.age?.let { Text("Idade: " + it + " anos") }
                rolled.traits?.let { traits ->
                    Text("Físico: " + traitLabel(traits.physique))
                    Text("Pele: " + traitLabel(traits.skin))
                    Text("Cabelo: " + traitLabel(traits.hair))
                    Text("Rosto: " + traitLabel(traits.face))
                    Text("Fala: " + traitLabel(traits.speech))
                    Text("Vestuário: " + traitLabel(traits.clothing))
                    Text("Virtude: " + traitLabel(traits.virtue))
                    Text("Vício: " + traitLabel(traits.vice))
                    Text("Equipamento básico: mochila, rações para 3 dias e tocha")
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRoll) {
                Text(if (rolled == null) "Rolar personagem" else "Rolar novamente")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreate, enabled = name.isNotBlank() && rolled != null) {
                Text("Começar aventura")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRules) { Text("Como jogar?") }
            Spacer(Modifier.height(8.dp))
            Text(
                "FOR, DES e VON: 3d6. HP: 1d6. Idade: 2d20 + 10.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CharacterSheet(
    state: GameState,
    feedback: List<FeedbackEntry>,
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.cairn_jurandir_portrait),
                    contentDescription = "Retrato pixel art do personagem",
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(c.character.name, style = MaterialTheme.typography.headlineMedium)
                    Text("Turno " + c.turn + " • " + sceneTypeLabel(c.sceneType), color = Color(0xFFD9A441))
                    c.profile.background?.let { Text(backgroundLabel(it), color = Color(0xFFB7C3A4)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF121615), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.sceneTitle, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(c.sceneDescription)
                }
            }
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
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onExplore) { Text("Explorar") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onDamage) { Text("Receber 2 de dano") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRest) { Text("Descansar") }
            }
            Spacer(Modifier.height(12.dp))
            c.profile.backgroundRolls?.let { rolls ->
                Text("Background: " + rolls.first + " e " + rolls.second)
                c.profile.backgroundFeatures.forEach { Text("• " + backgroundOutcomeLabel(it)) }
            }
            c.profile.companions.forEach { companion ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF121615),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.cairn_companion),
                            contentDescription = "Companheiro em pixel art",
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(companion.id.replace('-', ' ').replaceFirstChar { it.uppercase() })
                            Text("HP " + companion.hp + "/" + companion.maxHp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            feedback.lastOrNull()?.let { entry ->
                FeedbackCard(entry)
                Spacer(Modifier.height(12.dp))
            }
            Text("Inventário", style = MaterialTheme.typography.titleMedium)
            if (r.inventory.isEmpty()) Text("Nenhum item")
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAddItem, enabled = r.freeSlots > 0) { Text("Adicionar item") }
            Spacer(Modifier.height(8.dp))
        }

        items(r.inventory, key = { it.id }) { item ->
            ListItem(
                leadingContent = {
                    Image(
                        painter = painterResource(itemIconResource(item.id)),
                        contentDescription = "Ícone de " + item.id,
                        modifier = Modifier.size(40.dp)
                    )
                },
                headlineContent = { Text(item.id) },
                supportingContent = { Text(buildList { add(item.slotCost.toString() + if (item.slotCost == 1) " espaço" else " espaços"); item.damage?.let { add(it) }; item.armor.takeIf { it > 0 }?.let { add("Armor " + it) }; item.uses?.let { add(it.toString() + " usos") } }.joinToString(" • ")) },
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


@Composable
private fun FeedbackCard(entry: FeedbackEntry) {
    val accent = when (entry.type.name) {
        "SUCCESS" -> Color(0xFF7A9B61)
        "DAMAGE", "CRITICAL" -> Color(0xFFB6534B)
        "WARNING" -> Color(0xFFD0A04A)
        "INVENTORY" -> Color(0xFF6F8FA8)
        else -> Color(0xFF8E8B7E)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF171A18),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}



private fun sceneImageResource(title: String): Int =
    when (title.lowercase()) {
        "old road" -> R.drawable.cairn_old_road
        "ruined shrine", "woodland edge", "watchtower" -> R.drawable.cairn_old_road
        else -> R.drawable.cairn_old_road
    }

private fun itemIconResource(id: String): Int {
    val key = id.lowercase()
    return when {
        "sword" in key || "rapier" in key || "falchion" in key -> R.drawable.cairn_sword
        "dagger" in key || "knife" in key || "blade" in key -> R.drawable.cairn_dagger
        "bow" in key || "crossbow" in key -> R.drawable.cairn_bow
        "armor" in key || "leather" in key || "mail" in key || "brigandine" in key || "jerkin" in key || "gambeson" in key -> R.drawable.cairn_armor
        "shield" in key || "buckler" in key -> R.drawable.cairn_shield
        "potion" in key || "salve" in key || "tincture" in key || "unguent" in key -> R.drawable.cairn_potion
        "lantern" in key || "torch" in key -> R.drawable.cairn_lantern
        "rope" in key || "cord" in key || "twine" in key -> R.drawable.cairn_rope
        "ration" in key || "food" in key -> R.drawable.cairn_food
        "backpack" in key || "bag" in key -> R.drawable.cairn_backpack
        else -> R.drawable.cairn_backpack
    }
}
