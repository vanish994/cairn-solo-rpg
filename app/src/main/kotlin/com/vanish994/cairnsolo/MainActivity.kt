package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.LocalGameStateRepository
import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalGameStateRepository(applicationContext)

        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(repository.load()) }
                var name by remember { mutableStateOf("") }
                val engine = remember { RulesEngine(FixedRandomSource(10)) }

                Surface(Modifier.fillMaxSize()) {
                    if (state == null) {
                        CharacterCreation(name, { name = it }) {
                            val created = newCharacter(name.trim(), 10, 10, 10)
                            repository.save(created)
                            state = created
                        }
                    } else {
                        val current = state!!
                        CharacterSheet(
                            state = current,
                            onDamage = {
                                val next = current.withRules(
                                    engine.applyDamage(current.campaign.rules, 2).newState
                                )
                                repository.save(next)
                                state = next
                            },
                            onRest = {
                                val next = current.withRules(
                                    engine.safeRest(current.campaign.rules).newState
                                )
                                repository.save(next)
                                state = next
                            },
                            onDelete = {
                                repository.clear()
                                state = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCreation(
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Cairn Solo RPG", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Criar personagem")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nome") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCreate, enabled = name.isNotBlank()) {
            Text("Começar aventura")
        }
    }
}

@Composable
private fun CharacterSheet(
    state: GameState,
    onDamage: () -> Unit,
    onRest: () -> Unit,
    onDelete: () -> Unit
) {
    val c = state.campaign
    val r = c.rules

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(c.character.name, style = MaterialTheme.typography.headlineMedium)
        Text("Turno " + c.turn + " • Cena " + c.sceneId)
        Spacer(Modifier.height(16.dp))
        Text("HP " + r.hp + "/" + r.maxHp)
        Text("STR " + r.str + "   DEX " + r.dex + "   WIL " + r.wil)
        Text("Armor " + r.armor + "   Slots " + r.usedSlots + "/10")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDamage) { Text("Receber 2 de dano") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRest) { Text("Descanso seguro") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDelete) { Text("Apagar campanha") }
    }
}
