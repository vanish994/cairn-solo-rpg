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
            Text("Turno " + c.turn + " • " + c.sceneType.name)
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
                Text("Solicitar descanso")
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
            Text("STR " + rolled.str + "   DEX " + rolled.dex + "   WIL " + rolled.wil)
            Text("HP " + rolled.hp)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onRoll) {
            Text(if (rolled == null) "Rolar personagem" else "Rolar novamente")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCreate, enabled = name.isNotBlank()) {
            Text("Começar aventura")
        }
        Text("3d6 para cada atributo e 1d6 para HP.", style = MaterialTheme.typography.bodySmall)
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
            Text("Turno " + c.turn + " • " + c.sceneType.name)
            Text(c.sceneTitle, style = MaterialTheme.typography.titleLarge)
            Text(c.sceneDescription)
            if (c.exits.isNotEmpty()) Text("Saídas: " + c.exits.joinToString(" • "))
            Spacer(Modifier.height(12.dp))
            Text("HP " + r.hp + "/" + r.maxHp)
            Text("STR " + r.str + "   DEX " + r.dex + "   WIL " + r.wil)
            Text("Armor " + r.armor + "   Slots " + r.usedSlots + "/10")
            if (r.deprived) Text("Privado")
            if (r.critical) Text("Dano crítico")
            if (r.scar != null) Text("Cicatriz: resultado " + r.scar)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExplore) { Text("Explorar") }
                Button(onClick = onDamage) { Text("Dano 2") }
                Button(onClick = onRest) { Text("Descansar") }
            }
            Spacer(Modifier.height(12.dp))
            Text("Inventário", style = MaterialTheme.typography.titleMedium)
            if (r.inventory.isEmpty()) Text("Vazio")
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAddItem, enabled = r.freeSlots > 0) { Text("Adicionar item") }
            Spacer(Modifier.height(8.dp))
        }

        items(r.inventory, key = { it.id }) { item ->
            ListItem(
                headlineContent = { Text(item.id) },
                supportingContent = { Text(item.slotCost.toString() + " slot(s)") },
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
