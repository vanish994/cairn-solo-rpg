package com.vanish994.cairnsolo.game

import android.content.Context

interface GameStateRepository {
    fun save(state: GameState)
    fun load(): GameState?
    fun clear()
}

class LocalGameStateRepository(context: Context) : GameStateRepository {
    private val prefs = context.getSharedPreferences("cairn_campaign", Context.MODE_PRIVATE)

    override fun save(state: GameState) {
        val editor = prefs.edit().clear()
        GameStatePersistenceCodec.encode(state).forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    override fun load(): GameState? {
        val values = prefs.all.mapNotNull { (key, value) -> if (value is String) key to value else null }.toMap()
        return GameStatePersistenceCodec.decode(values)
    }

    override fun clear() { prefs.edit().clear().apply() }
}
