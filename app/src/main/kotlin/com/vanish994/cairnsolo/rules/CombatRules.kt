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
    val events: List<RuleEvent>,
    val diceRolls: List<DieRollResult> = emptyList()
)

data class DieRollResult(val sides: Int, val result: Int) {
    init { require(sides in 2..100 && result in 1..sides) }
}

data class CombatAttackSource(val id: String, val weapon: WeaponProfile) {
    init { require(id.isNotBlank() && id == id.trim()) }
}

data class CombatGroupAttackResult(
    val target: CharacterState,
    val damageRolls: Map<String, Int>,
    val events: List<RuleEvent>,
    val diceRolls: Map<String, List<DieRollResult>> = emptyMap()
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
        mode: AttackMode = AttackMode.NORMAL,
        recipient: DamageRecipient = DamageRecipient.PLAYER_CHARACTER
    ): AttackResult {
        val damageRoll = rollDamage(weapon?.damage, mode)
        val result = rules.applyDamage(target, damageRoll.total, recipient)
        val damageEvent = result.events.filterIsInstance<RuleEvent.DamageApplied>().single()
        return AttackResult(
            attacker = attacker,
            target = result.newState,
            rawDamage = damageRoll.total,
            armorAbsorbed = damageEvent.armorAbsorbed,
            events = result.events,
            diceRolls = damageRoll.dice
        )
    }

    fun attackGroup(target: CharacterState, attackers: List<CombatAttackSource>): CombatGroupAttackResult {
        require(attackers.isNotEmpty()) { "A group attack requires at least one attacker." }
        require(attackers.map { it.id }.distinct().size == attackers.size) { "Group attacker ids must be unique." }
        val damageRolls = linkedMapOf<String, Int>()
        val diceRolls = linkedMapOf<String, List<DieRollResult>>()
        attackers.forEach { source ->
            val rolled = rollDamage(source.weapon.damage, AttackMode.NORMAL)
            damageRolls[source.id] = rolled.total
            diceRolls[source.id] = rolled.dice
        }
        val result = rules.applyDamage(target, damageRolls.values.max())
        return CombatGroupAttackResult(result.newState, damageRolls, result.events, diceRolls)
    }

    private data class DamageRoll(val total: Int, val dice: List<DieRollResult>)

    private fun rollDamage(damage: String?, mode: AttackMode): DamageRoll {
        if (mode == AttackMode.IMPAIRED) return rollSingleDie(4)
        if (mode == AttackMode.ENHANCED) return rollSingleDie(12)
        val expression = damage?.trim().orEmpty().ifBlank { "d4" }
        require(isSupportedWeaponDamageExpression(expression)) { "Unsupported damage expression: $expression" }
        val dice = expression.split('+').map { die ->
            val sides = die.trim().removePrefix("d").toIntOrNull()
                ?: error("Unsupported damage die: $die")
            require(sides in setOf(4, 6, 8, 10, 12))
            DieRollResult(sides, random.roll(sides))
        }
        return DamageRoll(dice.maxOf { it.result }, dice)
    }

    private fun rollSingleDie(sides: Int): DamageRoll {
        val result = random.roll(sides)
        return DamageRoll(result, listOf(DieRollResult(sides, result)))
    }
}
