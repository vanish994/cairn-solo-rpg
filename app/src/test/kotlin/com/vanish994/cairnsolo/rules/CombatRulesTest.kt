package com.vanish994.cairnsolo.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CombatRulesTest {
    private fun state(hp: Int = 10, armor: Int = 0) = CharacterState(
        str = 10, dex = 10, wil = 10, hp = hp, maxHp = hp, armor = armor
    )

    @Test
    fun normalAttackRollsWeaponDieAndSubtractsArmor() {
        val result = CombatRules(FixedRandomSource(1, d8Value = 6)).attack(
            attacker = state(), target = state(armor = 2), weapon = WeaponProfile("sword", "d8")
        )

        assertEquals(6, result.rawDamage)
        assertEquals(2, result.armorAbsorbed)
        assertEquals(6, result.target.hp)
        assertFalse(result.target.critical)
    }

    @Test
    fun impairedAttackUsesD4RegardlessOfWeapon() {
        val result = CombatRules(FixedRandomSource(1, d4Value = 3, d12Value = 12)).attack(
            attacker = state(), target = state(), weapon = WeaponProfile("greatsword", "d12"), mode = AttackMode.IMPAIRED
        )

        assertEquals(3, result.rawDamage)
        assertEquals(7, result.target.hp)
    }

    @Test
    fun enhancedAttackUsesD12RegardlessOfWeapon() {
        val result = CombatRules(FixedRandomSource(1, d12Value = 11, d8Value = 2)).attack(
            attacker = state(), target = state(), weapon = WeaponProfile("knife", "d4"), mode = AttackMode.ENHANCED
        )

        assertEquals(11, result.rawDamage)
        assertEquals(0, result.target.hp)
        assertEquals(9, result.target.str)
    }

    @Test
    fun twoWeaponDamageKeepsTheHighestDie() {
        val result = CombatRules(FixedRandomSource(1, d6Value = 4, d8Value = 7)).attack(
            attacker = state(), target = state(), weapon = WeaponProfile("twin-blades", "d6+d8")
        )

        assertEquals(7, result.rawDamage)
        assertEquals(3, result.target.hp)
    }
}
