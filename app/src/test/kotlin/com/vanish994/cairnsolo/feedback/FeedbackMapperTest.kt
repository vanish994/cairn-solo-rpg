package com.vanish994.cairnsolo.feedback

import com.vanish994.cairnsolo.game.GameEvent
import com.vanish994.cairnsolo.rules.Attribute
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackMapperTest {
    @Test
    fun mapsSceneAndInventoryEventsToPortugueseFeedback() {
        val scene = FeedbackMapper.map(GameEvent.SceneAdvanced("old_road"), 4)
        assertEquals(FeedbackType.INFO, scene.type)
        assertEquals("Você avançou para Old road.", scene.message)
        assertEquals(4, scene.turn)

        val item = FeedbackMapper.map(GameEvent.ItemAdded("torch"), 5)
        assertEquals(FeedbackType.INVENTORY, item.type)
        assertEquals("Item adicionado: Torch.", item.message)
    }

    @Test
    fun mapsRestAndFatigueFeedback() {
        val rest = FeedbackMapper.map(GameEvent.RestCompleted(4, 2), 7)
        assertEquals(FeedbackType.SUCCESS, rest.type)
        assertEquals("Descanso concluído: +4 HP, -2 fadiga.", rest.message)

        val fatigue = FeedbackMapper.map(GameEvent.FatigueAdded(2), 8)
        assertEquals(FeedbackType.WARNING, fatigue.type)
        assertEquals("Você ganhou 2 de fadiga.", fatigue.message)
    }

    @Test
    fun mapsDamageWithArmorAndCriticalInformation() {
        val normal = FeedbackMapper.map(GameEvent.DamageResolved(3, 1, 2, false, false, null), 9)
        assertEquals(FeedbackType.DAMAGE, normal.type)
        assertEquals("Você sofreu 2 de dano (1 absorvido pela armadura).", normal.message)

        val critical = FeedbackMapper.map(GameEvent.DamageResolved(5, 0, 5, true, false, "BROKEN_LIMB"), 10)
        assertEquals(FeedbackType.CRITICAL, critical.type)
        assertEquals("Você sofreu 5 de dano. Dano crítico: Broken limb.", critical.message)
    }

    @Test
    fun mapsSaveSuccessAndFailure() {
        val success = FeedbackMapper.map(GameEvent.SaveResolved(Attribute.WIL, 5, true), 11)
        assertEquals(FeedbackType.SUCCESS, success.type)
        assertEquals("Teste de VON: 5 — sucesso.", success.message)

        val failure = FeedbackMapper.map(GameEvent.SaveResolved(Attribute.STR, 17, false), 12)
        assertEquals(FeedbackType.FAILURE, failure.type)
        assertEquals("Teste de FOR: 17 — falha.", failure.message)
    }

    @Test
    fun mapsCriticalDeathAndDeprivation() {
        val dead = FeedbackMapper.map(GameEvent.DamageResolved(8, 0, 8, true, true, "MORTAL_WOUND"), 13)
        assertEquals(FeedbackType.CRITICAL, dead.type)
        assertEquals("Você sofreu 8 de dano. Você não pode continuar.", dead.message)

        val deprived = FeedbackMapper.map(GameEvent.DeprivationChanged(true), 14)
        assertEquals(FeedbackType.WARNING, deprived.type)
        assertEquals("Você está privado.", deprived.message)

        val fed = FeedbackMapper.map(GameEvent.DeprivationChanged(false), 15)
        assertEquals(FeedbackType.SUCCESS, fed.type)
        assertEquals("A privação foi removida.", fed.message)
    }

    @Test
    fun mapsAllPreservesOrderAndTurn() {
        val entries = FeedbackMapper.mapAll(
            listOf(
                GameEvent.CharacterCreated("id"),
                GameEvent.ItemRemoved("torch"),
                GameEvent.CriticalStabilized
            ),
            16
        )
        assertEquals(3, entries.size)
        assertEquals(16, entries[0].turn)
        assertEquals(16, entries[2].turn)
        assertEquals(FeedbackType.SUCCESS, entries[0].type)
        assertEquals(FeedbackType.INVENTORY, entries[1].type)
        assertEquals(FeedbackType.SUCCESS, entries[2].type)
        assertEquals(3, entries.map { it.id }.toSet().size)
    }

    @Test
    fun mapsGrowthProposalPendingAndDecisionEvents() {
        val pending = FeedbackMapper.map(GameEvent.GrowthProposalPending("growth-heartseed"), 17)
        assertEquals(FeedbackType.INFO, pending.type)
        assertEquals("Uma proposta de Growth aguarda sua decisão.", pending.message)

        val accepted = FeedbackMapper.map(GameEvent.GrowthProposalDecided("growth-heartseed", accepted = true), 18)
        assertEquals(FeedbackType.INFO, accepted.type)
        assertEquals("Decisão de Growth registrada.", accepted.message)

        val declined = FeedbackMapper.map(GameEvent.GrowthProposalDecided("growth-heartseed", accepted = false), 19)
        assertEquals(FeedbackType.INFO, declined.type)
        assertEquals("Proposta de Growth recusada.", declined.message)
    }

    @Test
    fun mapsScarRecovery() {
        val entry = FeedbackMapper.map(GameEvent.ScarRecovered("WALLOPED"), 17)
        assertEquals(FeedbackType.SUCCESS, entry.type)
        assertEquals("Cicatriz recuperada: Walloped.", entry.message)
    }
}
