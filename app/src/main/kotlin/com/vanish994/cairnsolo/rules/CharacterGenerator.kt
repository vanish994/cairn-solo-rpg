package com.vanish994.cairnsolo.rules

import com.vanish994.cairnsolo.game.GameState
import com.vanish994.cairnsolo.game.CampaignState
import com.vanish994.cairnsolo.game.CharacterIdentity
import kotlin.random.Random

class KotlinRandomSource(private val random: Random = Random.Default) : RandomSource {
    override fun roll(sides: Int): Int = random.nextInt(1, sides + 1)
}

enum class Background(val id: Int, val displayName: String) {
    AURIFEX(1, "Aurifex"), BARBER_SURGEON(2, "Barber-Surgeon"), BEAST_HANDLER(3, "Beast Handler"), BONEKEEPER(4, "Bonekeeper"), CUTPURSE(5, "Cutpurse"), FIELDWARDEN(6, "Fieldwarden"), FLETCHWIND(7, "Fletchwind"), FOUNDLING(8, "Foundling"), FUNGAL_FORAGER(9, "Fungal Forager"), GREENWISE(10, "Greenwise"), HALF_WITCH(11, "Half-Witch"), HEXENBANE(12, "Hexenbane"), JONGLEUR(13, "Jongleur"), KETTLEWRIGHT(14, "Kettlewright"), MARCHGUARD(15, "Marchguard"), MOUNTEBANK(16, "Mountebank"), OUTRIDER(17, "Outrider"), PROWLER(18, "Prowler"), RILL_RUNNER(19, "Rill Runner"), SCRIVENER(20, "Scrivener");

    companion object {
        fun fromD20(roll: Int): Background = entries.first { it.id == roll }
    }
}

data class CharacterTraits(
    val physique: String,
    val skin: String,
    val hair: String,
    val face: String,
    val speech: String,
    val clothing: String,
    val virtue: String,
    val vice: String
)

data class RolledCharacter(
    val str: Int,
    val dex: Int,
    val wil: Int,
    val hp: Int,
    val background: Background? = null,
    val traits: CharacterTraits? = null
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
