package com.vanish994.cairnsolo.rules

enum class Attribute { STR, DEX, WIL }

data class InventoryItem(val id: String, val slots: Int = 1, val petty: Boolean = false) {
    init { require(slots >= 0); require(petty || slots > 0) }
    val slotCost: Int get() = if (petty) 0 else slots
}

data class CharacterState(
    val str: Int, val dex: Int, val wil: Int,
    val hp: Int, val maxHp: Int, val armor: Int,
    val inventory: List<InventoryItem> = emptyList(),
    val fatigue: Int = 0, val deprived: Boolean = false,
    val critical: Boolean = false, val dead: Boolean = false, val scar: Scar? = null, val maxStr: Int = str, val maxDex: Int = dex, val maxWil: Int = wil, val lastingScar: String? = null, val brokenLimb: String? = null, val sundered: Boolean = false, val deafened: Boolean = false, val diseased: Boolean = false, val hamstrung: Boolean = false, val doomed: Boolean = false
) {
    init {
        require(str >= 0 && dex >= 0 && wil >= 0)
        require(hp >= 0 && maxHp >= 0 && hp <= maxHp)
        require(armor in 0..3)
        require(fatigue >= 0)
        require(maxStr >= str && maxDex >= dex && maxWil >= wil)
        require(inventory.sumOf { it.slotCost } + fatigue <= 10)
        
    }
    fun attribute(attribute: Attribute): Int = when (attribute) {
        Attribute.STR -> str; Attribute.DEX -> dex; Attribute.WIL -> wil
    }
    val usedSlots: Int get() = inventory.sumOf { it.slotCost } + fatigue
    val freeSlots: Int get() = 10 - usedSlots
}

interface RandomSource {
    fun roll(sides: Int): Int
    fun d20(): Int = roll(20)
    fun d6(): Int = roll(6)
    fun d12(): Int = roll(12)
}

class FixedRandomSource(private val d20Value: Int, private val d6Value: Int = 1, private val d12Value: Int = 1) : RandomSource {
    init { require(d20Value in 1..20); require(d6Value in 1..6); require(d12Value in 1..12) }
    override fun roll(sides: Int): Int = when (sides) {
        20 -> d20Value
        6 -> d6Value
        12 -> d12Value
        else -> error("FixedRandomSource only supports d20, d6, and d12")
    }
}

enum class Scar { LASTING, RATTLING, WALLOPED, BROKEN_LIMB, DISEASED, HEAD_WOUND, HAMSTRUNG, DEAFENED, RE_BRAINED, SUNDERED, MORTAL_WOUND, DOOMED }

sealed interface RuleEvent {
    data class SaveResolved(val attribute: Attribute, val roll: Int, val success: Boolean) : RuleEvent
    data class DamageApplied(val rawDamage: Int, val armorAbsorbed: Int, val hpDamage: Int) : RuleEvent
    data class CriticalDamage(val excessDamage: Int, val strAfter: Int, val saveRoll: Int, val saveSuccess: Boolean) : RuleEvent
    data class ScarTriggered(val scar: Scar, val hpLost: Int, val detail: String, val roll: Int) : RuleEvent
    data class InventoryChanged(val usedSlots: Int) : RuleEvent
    data class FatigueAdded(val amount: Int) : RuleEvent
    data class FatigueRecovered(val amount: Int) : RuleEvent
    data class HpRecovered(val amount: Int) : RuleEvent
}

data class SaveResult(val newState: CharacterState, val events: List<RuleEvent>, val roll: Int, val success: Boolean)
data class GameResult(val newState: CharacterState, val events: List<RuleEvent>)

class RulesEngine(private val random: RandomSource) {
    fun save(state: CharacterState, attribute: Attribute): SaveResult {
        val roll = random.d20()
        val value = state.attribute(attribute)
        val success = when (roll) { 1 -> true; 20 -> false; else -> roll <= value }
        return SaveResult(state, listOf(RuleEvent.SaveResolved(attribute, roll, success)), roll, success)
    }

    fun applyDamage(state: CharacterState, rawDamage: Int): GameResult {
        require(rawDamage >= 0)
        val armorAbsorbed = minOf(rawDamage, state.armor)
        val hpDamage = rawDamage - armorAbsorbed
        val remainingHp = state.hp - hpDamage
        val damageEvent = RuleEvent.DamageApplied(rawDamage, armorAbsorbed, hpDamage)

        if (remainingHp > 0) return GameResult(state.copy(hp = remainingHp), listOf(damageEvent))

        if (remainingHp == 0) {
            // Cairn 2e: the Scar result is determined by the HP lost in the attack,
            // not by a separate d12 roll. The table has entries 1-12.
            val scarRoll = hpDamage.coerceIn(1, 12)
            val scar = Scar.entries[scarRoll - 1]
            val detail = when (scar) {
                Scar.LASTING -> "lasting_scar"
                Scar.RATTLING -> "rattling_blow"
                Scar.WALLOPED -> "walloped"
                Scar.BROKEN_LIMB -> "broken_limb"
                Scar.DISEASED -> "diseased"
                Scar.HEAD_WOUND -> "reorienting_head_wound"
                Scar.HAMSTRUNG -> "hamstrung"
                Scar.DEAFENED -> "deafened"
                Scar.RE_BRAINED -> "re_brained"
                Scar.SUNDERED -> "sundered"
                Scar.MORTAL_WOUND -> "mortal_wound"
                Scar.DOOMED -> "doomed"
            }
            val scarState = when (scar) {
                Scar.WALLOPED -> state.copy(hp = 0, scar = scar, deprived = true)
                Scar.DISEASED -> state.copy(hp = 0, scar = scar, diseased = true)
                Scar.HAMSTRUNG -> state.copy(hp = 0, scar = scar, hamstrung = true)
                Scar.DEAFENED -> state.copy(hp = 0, scar = scar, deafened = true)
                Scar.SUNDERED -> state.copy(hp = 0, scar = scar, sundered = true)
                Scar.MORTAL_WOUND -> state.copy(hp = 0, scar = scar, deprived = true, critical = true)
                Scar.DOOMED -> state.copy(hp = 0, scar = scar, doomed = true)
                else -> state.copy(hp = 0, scar = scar)
            }
            return GameResult(
                scarState,
                listOf(damageEvent, RuleEvent.ScarTriggered(scar, hpDamage, detail, scarRoll))
            )
        }

        val excessDamage = -remainingHp
        val newStr = maxOf(0, state.str - excessDamage)
        val roll = random.d20()
        val success = when (roll) { 1 -> true; 20 -> false; else -> roll <= newStr }
        return GameResult(
            state.copy(hp = 0, str = newStr, critical = true, dead = !success),
            listOf(damageEvent, RuleEvent.CriticalDamage(excessDamage, newStr, roll, success))
        )
    }

    fun setArmor(state: CharacterState, armor: Int): GameResult {
        require(armor in 0..3)
        return GameResult(state.copy(armor = armor), emptyList())
    }

    fun addItem(state: CharacterState, item: InventoryItem): GameResult {
        require(state.freeSlots >= item.slotCost)
        val updated = state.copy(inventory = state.inventory + item)
        val full = updated.usedSlots == 10
        val finalState = if (full) updated.copy(hp = 0) else updated
        return GameResult(
            finalState,
            buildList {
                add(RuleEvent.InventoryChanged(finalState.usedSlots))
            }
        )
    }

    fun removeItem(state: CharacterState, itemId: String): GameResult {
        val index = state.inventory.indexOfFirst { it.id == itemId }
        require(index >= 0)
        val updated = state.copy(inventory = state.inventory.toMutableList().also { it.removeAt(index) })
        return GameResult(updated, listOf(RuleEvent.InventoryChanged(updated.usedSlots)))
    }

    fun addFatigue(state: CharacterState, amount: Int = 1): GameResult {
        require(amount > 0)
        require(state.freeSlots >= amount) {
            "No free inventory slots for Fatigue; drop an item before adding Fatigue"
        }
        val updated = state.copy(fatigue = state.fatigue + amount)
        return GameResult(
            updated,
            listOf(
                RuleEvent.FatigueAdded(amount),
                RuleEvent.InventoryChanged(updated.usedSlots)
            )
        )
    }

    fun markDeprived(state: CharacterState, deprived: Boolean): GameResult =
        GameResult(state.copy(deprived = deprived), emptyList())

    fun stabilizeCritical(state: CharacterState): GameResult {
        if (!state.critical || state.dead) return GameResult(state, emptyList())
        return GameResult(state.copy(critical = false), emptyList())
    }

    fun safeRest(state: CharacterState): GameResult {
        if (state.deprived) return GameResult(state, emptyList())
        val hpRecovered = state.maxHp - state.hp
        val fatigueRecovered = state.fatigue
        val updated = state.copy(hp = state.maxHp, fatigue = 0)
        return GameResult(updated, buildList {
            if (hpRecovered > 0) add(RuleEvent.HpRecovered(hpRecovered))
            if (fatigueRecovered > 0) add(RuleEvent.FatigueRecovered(fatigueRecovered))
        })
    }
}
