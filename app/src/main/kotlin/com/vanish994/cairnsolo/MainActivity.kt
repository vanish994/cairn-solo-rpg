package com.vanish994.cairnsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vanish994.cairnsolo.rules.CharacterState
import com.vanish994.cairnsolo.rules.FixedRandomSource
import com.vanish994.cairnsolo.rules.RulesEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var state by remember {
                        mutableStateOf(CharacterState(10, 10, 10, 6, 6, 0))
                    }
                    val engine = remember { RulesEngine(FixedRandomSource(10)) }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Cairn Solo RPG", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Gate 1 — Rules Engine")
                        Spacer(Modifier.height(20.dp))

                        Card {
                            Column(Modifier.padding(16.dp)) {
                                Text("HP: ${state.hp}/${state.maxHp}")
                                Text("STR: ${state.str}   DEX: ${state.dex}   WIL: ${state.wil}")
                                Text("Slots: ${state.usedSlots}/10")
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = {
                                    state = engine.applyDamage(state, 2).newState
                                }) {
                                    Text("Receber 2 de dano")
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    state = engine.safeRest(state).newState
                                }) {
                                    Text("Descanso seguro")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
