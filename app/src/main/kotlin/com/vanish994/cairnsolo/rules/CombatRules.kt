package com.vanish994.cairnsolo.rules

/** Modificadores normativos de ataque do Cairn 2e. */
enum class AttackMode { NORMAL, IMPAIRED, ENHANCED }

data class WeaponProfile(
    val id: String,
    val damage: String? = null,
    val blast: Boolean = false,
    val ranged: Boolean = false
)

private val CAIRN_WEAPON_DAMAGE_EXPRESSION = Regex("^d(4|6|8|10|12)(\\s*\\+\\s*d(4|6|8|10|12))*$")

fun isSupportedWeaponDamageExpression(damage: String?): Boolean =
    damage?.trim()?.let { CAIRN_WEAPON_DAMAGE_EXPRESSION.matches(it) } == true

data class AttackResult(
    val attacker: CharacterState,
    val target: CharacterState,
    val rawDamage: Int,
    val armorAbsorbed: Int,
    val events: List<RuleEvent>
)

/**
 * Regras de ataque independentes da interface e do narrador.
 * Ataques do Cairn acertam automaticamente; o dado mede apenas o dano.
 */
class CombatRules(private val random: RandomSource) {
    private val rules = RulesEngine(random)

    fun attack(
        attacker: CharacterState,
        target: CharacterState,
        weapon: WeaponProfile? = null,
        mode: AttackMode = AttackMode.NORMAL
    ): AttackResult {
        val rawDamage = rollDamage(weapon?.damage, mode)
        val result = rules.applyDamage(target, rawDamage)
        val damageEvent = result.events.filterIsInstance<RuleEvent.DamageApplied>().single()
        return AttackResult(
            attacker = attacker,
            target = result.newState,
            rawDamage = rawDamage,
            armorAbsorbed = damageEvent.armorAbsorbed,
            events = result.events
        )
    }

    private fun rollDamage(damage: String?, mode: AttackMode): Int {
        if (mode == AttackMode.IMPAIRED) return random.roll(4)
        if (mode == AttackMode.ENHANCED) return random.roll(12)
        val expression = damage?.trim().orEmpty().ifBlank { "d4" }
        require(isSupportedWeaponDamageExpression(expression)) { "Unsupported damage expression: $expression" }
        return expression.split('+').maxOf { die ->
            val sides = die.trim().removePrefix("d").toIntOrNull()
                ?: error("Unsupported damage die: $die")
            require(sides in setOf(4, 6, 8, 10, 12))
            random.roll(sides)
        }
    }
}
