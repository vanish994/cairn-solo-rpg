package com.vanish994.cairnsolo.guardian

import com.vanish994.cairnsolo.game.newCharacter
import com.vanish994.cairnsolo.rules.InventoryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardianRewardClientContextTest {
    @Test
    fun parsesPaidRewardProposal() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"O mercador entrega o pagamento.","sceneTitle":"Taverna","sceneDescription":"O acordo foi concluído.","ruleRequest":{"type":"REWARD","id":"quest-pay","status":"PAID","amountGp":12,"itemCatalogIds":["dagger"]}}"""
        )

        assertEquals("O mercador entrega o pagamento.", response.narration)
        val request = assertNotNull(response.ruleRequest)
        assertEquals("REWARD", request.type)
        val reward = assertNotNull(request.reward)
        assertEquals("quest-pay", reward.id)
        assertEquals(RewardStatus.PAID, reward.status)
        assertEquals(12, reward.amountGp)
        assertEquals(listOf("dagger"), reward.itemCatalogIds)
    }

    @Test
    fun parsesPaidRewardProposalWhenTypeIsLowercase() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"O pagamento foi entregue.","sceneTitle":"Taverna","sceneDescription":"A cena termina.","ruleRequest":{"type":"reward","id":"quest-pay","status":"PAID","amountGp":12,"itemCatalogIds":[]}}"""
        )

        assertEquals("REWARD", response.ruleRequest?.type)
        assertEquals(RewardStatus.PAID, response.ruleRequest?.reward?.status)
        assertEquals(12, response.ruleRequest?.reward?.amountGp)
    }

    @Test
    fun invalidRewardProposalPreservesNarrationForVisibleRejection() {
        val response = HttpGuardianClient(baseUrl = "").parseResponse(
            """{"narration":"A cena continua apesar do pedido inválido.","sceneTitle":"Taverna","sceneDescription":"A conversa segue.","ruleRequest":{"type":"REWARD","id":"quest-pay","status":"PAID","amountGp":12.5,"itemCatalogIds":[]}}"""
        )

        assertEquals("A cena continua apesar do pedido inválido.", response.narration)
        assertEquals("Taverna", response.sceneTitle)
        assertEquals("REWARD", response.ruleRequest?.type)
        assertNull(response.ruleRequest?.reward)
    }

    @Test
    fun rewardContextExposesOnlyGpFreeSlotsAndCatalogItems() {
        val base = newCharacter("Mara", 10, 11, 12)
        val state = base.copy(campaign = base.campaign.copy(
            profile = base.campaign.profile.copy(gold = 17),
            rules = base.campaign.rules.copy(inventory = listOf(InventoryItem("existing")))
        ))
        val context = GuardianContextBuilder.from(state)
        val json = context.toJson()
        val dagger = context.rewardableItems.single { it.catalogId == "dagger" }

        assertEquals(17, context.character.goldGp)
        assertEquals(9, context.freeSlots)
        assertEquals("Dagger", dagger.name)
        assertEquals(1, dagger.slotCost)
        assertFalse(context.rewardableItems.any { it.catalogId == "horse" })
        assertEquals(17, json.getJSONObject("character").getInt("goldGp"))
        assertFalse(json.getJSONObject("character").has("gold"))
        assertEquals(9, json.getInt("freeSlots"))
        assertTrue(json.getJSONArray("rewardableItems").length() > 0)
    }
}
