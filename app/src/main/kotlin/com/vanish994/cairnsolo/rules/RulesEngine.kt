package com.vanish994.cairnsolo.rules

enum class Attribute { STR, DEX, WIL }

data class InventoryItem(
    val id: String,
    val slots: Int = 1,
    val petty: Boolean = false,
    val damage: String? = null,
    val uses: Int? = null,
    val armor: Int = 0,
    val tags: Set<String> = emptySet()
) {
    init {
        require(slots >= 0)
        require(petty || slots > 0)
        require(armor >= 0)
        require(uses == null || uses > 0)
    }
    val slotCost: Int get() = if (petty) 0 else slots
}

data class CharacterState(
    val str: Int, val dex: Int, val wil: Int,
    val hp: Int, val maxHp: Int, val armor: Int,
    val inventory: List<InventoryItem> = emptyList(),
    val fatigue: Int = 0, val deprived: Boolean = false, val deprivedDays: Int = 0,
    val critical: Boolean = false, val dead: Boolean = false, val scar: Scar? = null, val maxStr: Int = str, val maxDex: Int = dex, val maxWil: Int = wil, val lastingScar: String? = null, val brokenLimb: String? = null, val scarRecovery: ScarRecovery? = null, val scarAttribute: Attribute? = null, val sundered: Boolean = false, val deafened: Boolean = false, val diseased: Boolean = false, val hamstrung: Boolean = false, val doomed: Boolean = false
) {
    init {
        require(str >= 0 && dex >= 0 && wil >= 0)
        require(hp >= 0 && maxHp >= 0 && hp <= maxHp)
        require(armor in 0..3)
        require(fatigue >= 0)
        require(deprivedDays >= 0)
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

class FixedRandomSource(private val d20Value: Int, private val d6Value: Int = 1, private val d12Value: Int = 1, private val d4Value: Int = 1, private val d10Value: Int = 1, private val d8Value: Int = 1) : RandomSource {
    init { require(d20Value in 1..20); require(d6Value in 1..6); require(d12Value in 1..12); require(d4Value in 1..4); require(d10Value in 1..10); require(d8Value in 1..8) }
    override fun roll(sides: Int): Int = when (sides) {
        20 -> d20Value
        6 -> d6Value
        12 -> d12Value
        4 -> d4Value
        10 -> d10Value
        8 -> d8Value
        else -> error("FixedRandomSource only supports d4, d6, d10, d12, and d20")
    }
}

enum class Scar { LASTING, RATTLING, WALLOPED, BROKEN_LIMB, DISEASED, HEAD_WOUND, HAMSTRUNG, DEAFENED, RE_BRAINED, SUNDERED, MORTAL_WOUND, DOOMED }

enum class ScarRecovery { WALLOPED, BROKEN_LIMB, DISEASED, HAMSTRUNG, MORTAL_WOUND }

sealed interface RuleEvent {
    data class SaveResolved(val attribute: Attribute, val roll: Int, val success: Boolean) : RuleEvent
    data class DamageApplied(val rawDamage: Int, val armorAbsorbed: Int, val hpDamage: Int) : RuleEvent
    data class CriticalDamage(val excessDamage: Int, val strAfter: Int, val saveRoll: Int, val saveSuccess: Boolean) : RuleEvent
    data class ScarTriggered(val scar: Scar, val hpLost: Int, val detail: String, val roll: Int) : RuleEvent
    data class InventoryChanged(val usedSlots: Int) : RuleEvent
    data class ItemDropped(val itemId: String) : RuleEvent
    data class FatigueAdded(val amount: Int) : RuleEvent
    data class FatigueRecovered(val amount: Int) : RuleEvent
    data class HpRecovered(val amount: Int) : RuleEvent
}

data class SaveResult(val newState: CharacterState, val events: List<RuleEvent>, val roll: Int, val success: Boolean)
data class GameResult(val newState: CharacterState, val events: List<RuleEvent>)

class RulesEngine(val random: RandomSource) {
    fun save(state: CharacterState, attribute: Attribute): SaveResult {
        val roll = random.d20()
        val value = state.attribute(attribute)
        val success = saveSucceeds(roll, value)
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
            val detail = scar.name.lowercase()
            val scarState = when (scar) {
                Scar.LASTING -> {
                    val location = when (random.d6()) { 1 -> "neck"; 2 -> "hands"; 3 -> "eye"; 4 -> "chest"; 5 -> "legs"; else -> "ear" }
                    state.copy(hp = 0, scar = scar, lastingScar = location, maxHp = maxOf(state.maxHp, random.d6()))
                }
                Scar.RATTLING -> state.copy(hp = 0, scar = scar, maxHp = maxOf(state.maxHp, random.d6()))
                Scar.WALLOPED -> state.copy(hp = 0, scar = scar, deprived = true, scarRecovery = ScarRecovery.WALLOPED)
                Scar.BROKEN_LIMB -> {
                    val location = when (random.d6()) { 1, 2 -> "leg"; 3, 4 -> "arm"; 5 -> "rib"; else -> "skull" }
                    state.copy(hp = 0, scar = scar, brokenLimb = location, scarRecovery = ScarRecovery.BROKEN_LIMB)
                }
                Scar.DISEASED -> state.copy(hp = 0, scar = scar, diseased = true, scarRecovery = ScarRecovery.DISEASED)
                Scar.HEAD_WOUND -> {
                    val attribute = when (random.d6()) { in 1..2 -> Attribute.STR; in 3..4 -> Attribute.DEX; else -> Attribute.WIL }
                    val roll = random.d6() + random.d6() + random.d6()
                    val updated = when (attribute) {
                        Attribute.STR -> state.copy(str = maxOf(state.str, roll), maxStr = maxOf(state.maxStr, roll))
                        Attribute.DEX -> state.copy(dex = maxOf(state.dex, roll), maxDex = maxOf(state.maxDex, roll))
                        Attribute.WIL -> state.copy(wil = maxOf(state.wil, roll), maxWil = maxOf(state.maxWil, roll))
                    }
                    updated.copy(hp = 0, scar = scar, scarAttribute = attribute)
                }
                Scar.HAMSTRUNG -> state.copy(hp = 0, scar = scar, hamstrung = true, scarRecovery = ScarRecovery.HAMSTRUNG)
                Scar.DEAFENED -> {
                    val roll = random.d20()
                    val success = saveSucceeds(roll, state.wil)
                    state.copy(hp = 0, scar = scar, deafened = true, maxWil = if (success) state.maxWil + random.roll(4) else state.maxWil)
                }
                Scar.RE_BRAINED -> {
                    val roll = random.d6() + random.d6() + random.d6()
                    state.copy(hp = 0, scar = scar, maxWil = maxOf(state.maxWil, roll))
                }
                Scar.SUNDERED -> {
                    val roll = random.d20()
                    val success = saveSucceeds(roll, state.wil)
                    state.copy(hp = 0, scar = scar, sundered = true, maxWil = if (success) state.maxWil + random.d6() else state.maxWil)
                }
            Scar.MORTAL_WOUND -> state.copy(hp = 0, scar = scar, deprived = true, deprivedDays = 1, critical = true, scarRecovery = ScarRecovery.MORTAL_WOUND)
                Scar.DOOMED -> state.copy(hp = 0, scar = scar, doomed = true)
            }
            return GameResult(
                scarState,
                listOf(damageEvent, RuleEvent.ScarTriggered(scar, hpDamage, detail, scarRoll))
            )
        }

        val excessDamage = -remainingHp
        val newStr = maxOf(0, state.str - excessDamage)
        val roll = random.d20()
        val success = saveSucceeds(roll, newStr)
        val doomedResolved = if (state.doomed && success) {
            state.doomed to maxOf(state.maxHp, random.d6() + random.d6() + random.d6())
        } else {
            state.doomed to state.maxHp
        }
        return GameResult(
            state.copy(
                hp = 0,
                str = newStr,
                critical = true,
                dead = newStr == 0 || (state.doomed && !success),
                doomed = if (state.doomed) !success else false,
                maxHp = doomedResolved.second
            ),
            listOf(damageEvent, RuleEvent.CriticalDamage(excessDamage, newStr, roll, success))
        )
    }

    fun recoverScar(state: CharacterState): GameResult {
        val recovery = state.scarRecovery ?: return GameResult(state, emptyList())
        val roll = when (recovery) {
            ScarRecovery.WALLOPED -> random.d6()
            ScarRecovery.BROKEN_LIMB -> random.d6() + random.d6()
            ScarRecovery.DISEASED -> random.d6() + random.d6()
            ScarRecovery.HAMSTRUNG -> random.d6() + random.d6() + random.d6()
            ScarRecovery.MORTAL_WOUND -> random.d6() + random.d6()
        }
        val updated = when (recovery) {
            ScarRecovery.WALLOPED -> { val newMaxHp = maxOf(state.maxHp, roll); state.copy(hp = newMaxHp, deprived = false, deprivedDays = 0, maxHp = newMaxHp, scarRecovery = null) }
            ScarRecovery.BROKEN_LIMB -> { val newMaxHp = maxOf(state.maxHp, roll); state.copy(hp = newMaxHp, maxHp = newMaxHp, brokenLimb = null, scarRecovery = null) }
            ScarRecovery.DISEASED -> { val newMaxHp = maxOf(state.maxHp, roll); state.copy(hp = newMaxHp, maxHp = newMaxHp, diseased = false, scarRecovery = null) }
            ScarRecovery.HAMSTRUNG -> state.copy(hp = state.maxHp, maxDex = maxOf(state.maxDex, roll), hamstrung = false, scarRecovery = null)
            ScarRecovery.MORTAL_WOUND -> state.copy(hp = roll, deprived = false, deprivedDays = 0, critical = false, maxHp = roll, scarRecovery = null)
        }
        return GameResult(updated, emptyList())
    }

    private fun saveSucceeds(roll: Int, attribute: Int): Boolean = when (roll) {
        1 -> true
        20 -> false
        else -> roll <= attribute
    }

    fun setArmor(state: CharacterState, armor: Int): GameResult {
        require(armor in 0..3)
        return GameResult(state.copy(armor = armor), emptyList())
    }

    fun addItem(state: CharacterState, item: InventoryItem): GameResult {
        require(state.freeSlots >= item.slotCost)
        val updatedInventory = state.inventory + item
        val updated = state.copy(
            inventory = updatedInventory,
            hp = if (updatedInventory.sumOf { it.slotCost } + state.fatigue == 10) 0 else state.hp
        )
        return GameResult(
            updated,
            listOf(RuleEvent.InventoryChanged(updated.usedSlots))
        )
    }

    fun removeItem(state: CharacterState, itemId: String): GameResult {
        val index = state.inventory.indexOfFirst { it.id == itemId }
        require(index >= 0)
        val updated = state.copy(inventory = state.inventory.toMutableList().also { it.removeAt(index) })
        return GameResult(updated, listOf(RuleEvent.InventoryChanged(updated.usedSlots)))
    }

    fun addFatigue(state: CharacterState, amount: Int = 1, dropItemId: String? = null): GameResult {
        require(amount > 0)
        var working = state
        val events = mutableListOf<RuleEvent>()
        repeat(amount) {
            if (working.freeSlots <= 0) {
                val itemId = dropItemId ?: error("No free inventory slots for Fatigue; choose an item to drop")
                val index = working.inventory.indexOfFirst { it.id == itemId }
                require(index >= 0) { "Cannot drop missing item: $itemId" }
                working = working.copy(inventory = working.inventory.toMutableList().also { it.removeAt(index) })
                events += RuleEvent.ItemDropped(itemId)
            }
            working = working.copy(fatigue = working.fatigue + 1)
        }
        val updated = working
        events += RuleEvent.FatigueAdded(amount)
        events += RuleEvent.InventoryChanged(updated.usedSlots)
        return GameResult(
            updated,
            events
        )
    }

    fun markDeprived(state: CharacterState, deprived: Boolean): GameResult =
        GameResult(state.copy(deprived = deprived, deprivedDays = if (deprived) maxOf(1, state.deprivedDays) else 0), emptyList())

    /** Avança dias de necessidade não atendida; o primeiro dia não gera Fatigue. */
    fun advanceDeprivation(state: CharacterState, days: Int, dropItemId: String? = null): GameResult {
        require(days >= 0)
        if (!state.deprived || days == 0) return GameResult(state, emptyList())
        val fatigueDays = (state.deprivedDays + days - 1).coerceAtLeast(0)
        val fatigue = addFatigue(state.copy(deprivedDays = state.deprivedDays + days), fatigueDays, dropItemId)
        return fatigue.copy(newState = fatigue.newState.copy(deprived = true, deprivedDays = state.deprivedDays + days))
    }

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

    /** Descanso breve com água: recupera HP, mas não remove Fatigue. */
    fun quickRest(state: CharacterState): GameResult {
        if (state.deprived) return GameResult(state, emptyList())
        val recovered = state.maxHp - state.hp
        return GameResult(state.copy(hp = state.maxHp), if (recovered > 0) listOf(RuleEvent.HpRecovered(recovered)) else emptyList())
    }
}
