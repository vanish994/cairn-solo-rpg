package com.vanish994.cairnsolo.rules

/** Definição catalográfica de uma magia; seu efeito ficcional é resolvido pelo Warden. */
data class SpellDefinition(val id: String, val name: String, val description: String = "")

object SpellCatalog {
    val entries: List<SpellDefinition> = listOf(
        SpellDefinition("detect-magic", "Detect Magic", "Perceber auras mágicas próximas."),
        SpellDefinition("disassemble", "Disassemble", "Desmontar um objeto ou mecanismo."),
        SpellDefinition("read-mind", "Read Mind", "Ler pensamentos de uma criatura."),
        SpellDefinition("auditory-illusion", "Auditory Illusion", "Criar uma ilusão sonora."),
        SpellDefinition("thicket", "Thicket", "Criar uma moita densa de árvores e arbustos.")
    )

    private val byId = entries.associateBy { it.id }
    fun find(id: String): SpellDefinition? = byId[id]
}

enum class MagicItemKind { SPELLBOOK, SCROLL, RELIC }

enum class SpellFailureConsequence { NONE, EXTRA_FATIGUE, DESTROY_SPELLBOOK }

data class MagicCastResult(
    val state: CharacterState,
    val spell: SpellDefinition,
    val kind: MagicItemKind,
    val events: List<MagicEvent>,
    val wilSave: SaveResult? = null
)

sealed interface MagicEvent {
    data class SpellCast(val spellId: String, val kind: MagicItemKind) : MagicEvent
    data class FatigueAdded(val amount: Int) : MagicEvent
    data class ItemConsumed(val itemId: String) : MagicEvent
    data class SpellbookDestroyed(val itemId: String) : MagicEvent
    data class CastingSaveFailed(val spellId: String) : MagicEvent
}

object MagicItems {
    fun spellbook(id: String, spellId: String, extraTags: Set<String> = emptySet()): InventoryItem {
        require(SpellCatalog.find(spellId) != null) { "Unknown spell: $spellId" }
        return InventoryItem(id = id, slots = 1, tags = setOf("spellbook", "spell:$spellId") + extraTags)
    }

    fun scroll(id: String, spellId: String): InventoryItem {
        require(SpellCatalog.find(spellId) != null) { "Unknown spell: $spellId" }
        return InventoryItem(id = id, petty = true, tags = setOf("scroll", "spell:$spellId"))
    }

    fun relic(id: String, spellId: String, uses: Int, extraTags: Set<String> = emptySet()): InventoryItem {
        require(SpellCatalog.find(spellId) != null) { "Unknown spell: $spellId" }
        require(uses > 0)
        return InventoryItem(id = id, uses = uses, tags = setOf("relic", "spell:$spellId") + extraTags)
    }
}

class MagicRules(private val rules: RulesEngine) {
    fun cast(
        state: CharacterState,
        itemId: String,
        requiresWilSave: Boolean = false,
        failure: SpellFailureConsequence = SpellFailureConsequence.NONE,
        dropItemIdForFatigue: String? = null
    ): MagicCastResult {
        val item = state.inventory.firstOrNull { it.id == itemId } ?: error("Magic item not found: $itemId")
        val kind = kindOf(item)
        val spellId = item.tags.firstOrNull { it.startsWith("spell:") }?.removePrefix("spell:")
            ?: error("Magic item has no spell: $itemId")
        val spell = SpellCatalog.find(spellId) ?: error("Unknown spell: $spellId")
        val save = if (requiresWilSave) rules.save(state, Attribute.WIL) else null
        val failed = save?.success == false
        var current = state
        val events = mutableListOf<MagicEvent>()

        if (kind == MagicItemKind.SCROLL) {
            current = rules.removeItem(current, itemId).newState
            events += MagicEvent.ItemConsumed(itemId)
        } else if (kind == MagicItemKind.RELIC) {
            val uses = item.uses ?: error("Relic must have uses")
            current = if (uses <= 1) rules.removeItem(current, itemId).newState
            else replaceItem(current, item, item.copy(uses = uses - 1))
            events += MagicEvent.ItemConsumed(itemId)
        }

        if (failed) {
            events += MagicEvent.CastingSaveFailed(spell.id)
            when (failure) {
                SpellFailureConsequence.NONE -> Unit
                SpellFailureConsequence.EXTRA_FATIGUE -> {
                    current = rules.addFatigue(current, 1, dropItemIdForFatigue).newState
                    events += MagicEvent.FatigueAdded(1)
                }
                SpellFailureConsequence.DESTROY_SPELLBOOK -> {
                    if (kind == MagicItemKind.SPELLBOOK) {
                        current = rules.removeItem(current, itemId).newState
                        events += MagicEvent.SpellbookDestroyed(itemId)
                    }
                }
            }
        }

        if (kind == MagicItemKind.SPELLBOOK) {
            current = rules.addFatigue(current, 1, dropItemIdForFatigue).newState
            events += MagicEvent.FatigueAdded(1)
        }
        events += MagicEvent.SpellCast(spell.id, kind)
        return MagicCastResult(current, spell, kind, events, save)
    }

    private fun kindOf(item: InventoryItem): MagicItemKind = when {
        "spellbook" in item.tags -> MagicItemKind.SPELLBOOK
        "scroll" in item.tags -> MagicItemKind.SCROLL
        "relic" in item.tags -> MagicItemKind.RELIC
        else -> error("Item is not a recognized magic item")
    }

    private fun replaceItem(state: CharacterState, old: InventoryItem, replacement: InventoryItem): CharacterState =
        state.copy(inventory = state.inventory.map { if (it.id == old.id) replacement else it })
}
