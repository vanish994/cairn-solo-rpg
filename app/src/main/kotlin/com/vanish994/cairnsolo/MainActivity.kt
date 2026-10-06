package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            MaterialTheme {
                var state by remember { mutableStateOf(repository.load()) }
                var name by remember { mutableStateOf("") }
                var rolled by remember { mutableStateOf<RolledCharacter?>(null) }
                val actionResolver = remember {
                    GameActionResolver(
                        exploration = ExplorationEngine(KotlinRandomSource()),
                        rules = RulesEngine(KotlinRandomSource())
                    )
                }
                var screen by remember { mutableStateOf(AppScreen.CHARACTER) }

                Surface(Modifier.fillMaxSize()) {
                    if (state == null) {
                        CharacterCreation(
                            name = name,
                            onNameChange = { name = it },
                            rolled = rolled,
                            onRoll = { rolled = rollCharacter(KotlinRandomSource()) },
                            onCreate = {
                                val r = rolled ?: rollCharacter(KotlinRandomSource())
                                val created = createCharacter(name.trim(), r)
                                repository.save(created)
                                state = created
                            }
                        )
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
                                onAction = { action ->
                                    val result = actionResolver.resolve(current, action)
                                    repository.save(result.state)
                                    state = result.state
                                },
                                onBack = { screen = AppScreen.CHARACTER }
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class AppScreen { CHARACTER, EXPLORATION }

@Composable
private fun ExplorationScreen(
    state: GameState,
    onAction: (GameAction) -> Unit,
    onBack: () -> Unit
) {
    val c = state.campaign
    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("Exploração", style = MaterialTheme.typography.headlineMedium)
            Text("Turno " + c.turn + " • " + sceneTypeLabel(c.sceneType))
            Spacer(Modifier.height(16.dp))
            Text(c.sceneTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(c.sceneDescription)
            Spacer(Modifier.height(16.dp))
            if (c.exits.isNotEmpty()) {
                Text("Possibilidades: " + c.exits.joinToString(" • "))
                Spacer(Modifier.height(12.dp))
            }
            Button(onClick = { onAction(GameAction.ExploreContinue) }) { Text("Continuar") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onAction(GameAction.ExploreInvestigate) }) { Text("Investigar") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onAction(GameAction.ExploreRest) }) {
                Text("Descansar")
            }
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
    onRoll: () -> Unit,
    onCreate: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Cairn Solo RPG", style = MaterialTheme.typography.headlineMedium)
        Text("Criação de personagem")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, onNameChange, label = { Text("Nome") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        if (rolled != null) {
            Text("FOR " + rolled.str + "   DES " + rolled.dex + "   VON " + rolled.wil)
            Text("Pontos de vida: " + rolled.hp)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRoll) {
            Text(if (rolled == null) "Rolar personagem" else "Rolar novamente")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreate, enabled = name.isNotBlank()) {
            Text("Começar aventura")
        }
        Text("3d6 para cada atributo e 1d6 para os pontos de vida.", style = MaterialTheme.typography.bodySmall)
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
                trailingContent = {
                    TextButton(onClick = { onRemoveItem(item.id) }) { Text("Remover") }
                }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onDelete) { Text("Apagar campanha") }
        }
    }
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
