package com.vanish994.cairnsolo.guardian

import kotlin.test.Test
import kotlin.test.assertTrue

class NarrativeDirectorTest {
    @Test
    fun promptDefinesAnExplicitNarrativeTurnCycle() {
        val prompt = guardianSystemPrompt().lowercase()

        assertTrue(prompt.contains("diretor narrativo — ciclo interno de cada turno"))
        assertTrue(prompt.contains("1. estado:"))
        assertTrue(prompt.contains("2. pressões:"))
        assertTrue(prompt.contains("3. movimento:"))
        assertTrue(prompt.contains("4. resposta:"))
        assertTrue(prompt.contains("5. continuidade:"))
        assertTrue(prompt.contains("não exponha estes passos"))
    }

    @Test
    fun narrativeInitiativePreservesPlayerAgencyAndMechanicalAuthority() {
        val prompt = guardianSystemPrompt().lowercase()

        assertTrue(prompt.contains("não force uma reviravolta a cada turno"))
        assertTrue(prompt.contains("as sugestões são opções úteis, não um menu fechado"))
        assertTrue(prompt.contains("rules engine é a única autoridade"))
        assertTrue(prompt.contains("nunca invente rolagem"))
        assertTrue(prompt.contains("canonproposals"))
    }

    @Test
    fun narrativeContinuityDiscouragesRepeatedHooksAndArbitraryPunishment() {
        val prompt = guardianSystemPrompt().lowercase()

        assertTrue(prompt.contains("não repita a mesma ameaça"))
        assertTrue(prompt.contains("sem uma mudança concreta"))
        assertTrue(prompt.contains("sem punir arbitrariamente"))
        assertTrue(prompt.contains("objetivos conflitantes"))
        assertTrue(prompt.contains("npc"))
    }
}
