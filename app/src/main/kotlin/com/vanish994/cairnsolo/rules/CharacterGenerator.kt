package com.vanish994.cairnsolo.rules

import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CampaignState
import com.vanish994.cairnsolo.game.CharacterIdentity
import kotlin.random.Random

class KotlinRandomSource(private val random: Random = Random.Default) : RandomSource {
    override fun roll(sides: Int): Int = random.nextInt(1, sides + 1)
}

data class RolledCharacter(
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int
)

fun rollCharacter(random: RandomSource): RolledCharacter =
    RolledCharacter(
        str = random.d6() + random.d6() + random.d6(),
        dex = random.d6() + random.d6() + random.d6(),
        wil = random.d6() + random.d6() + random.d6(),
        hp = random.d6()
    )

fun createCharacter(name: String, rolled: RolledCharacter): GameState {
    require(name.isNotBlank())
    return GameState(
        campaign = CampaignState(
            character = CharacterIdentity(name = name.trim()),
            rules = CharacterState(
                str = rolled.str,
                dex = rolled.dex,
                wil = rolled.wil,
                hp = rolled.hp,
                maxHp = rolled.hp,
                armor = 0
            )
        )
    )
}
