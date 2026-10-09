package com.vanish994.cairnsolo.rules

/** Categoria de uma entrada do Marketplace do Cairn 2e. */
enum class MarketplaceCategory { ARMOR, WEAPON, TRANSPORT, UPKEEP, GEAR, HIRELING }

data class MarketplaceEntry(
    val id: String,
    val name: String,
    val category: MarketplaceCategory,
    val priceGp: Int,
    val item: InventoryItem? = null,
    val description: String = ""
) {
    init { require(priceGp >= 0) }
}

data class PurchaseResult(
    val state: CharacterState,
    val goldRemaining: Int,
    val entry: MarketplaceEntry
)

object MarketplaceCatalog {
    private fun armor(id: String, name: String, armor: Int, price: Int, bulky: Boolean = false) = MarketplaceEntry(id, name, MarketplaceCategory.ARMOR, price, InventoryItem(id, slots = if (bulky) 2 else 1, armor = armor))
    private fun weapon(id: String, name: String, damage: String, price: Int, bulky: Boolean = false) = MarketplaceEntry(id, name, MarketplaceCategory.WEAPON, price, InventoryItem(id, slots = if (bulky) 2 else 1, damage = damage))
    private fun gear(id: String, name: String, price: Int, slots: Int = 1, petty: Boolean = false, uses: Int? = null, tags: Set<String> = emptySet(), damage: String? = null) = MarketplaceEntry(id, name, MarketplaceCategory.GEAR, price, InventoryItem(id, slots, petty, damage, uses, tags = tags))

    val entries: List<MarketplaceEntry> = listOf(
        armor("shield", "Shield", 1, 10), armor("helmet", "Helmet", 1, 10), armor("gambeson", "Gambeson", 1, 15), armor("brigandine", "Brigandine", 1, 20, true), armor("chainmail", "Chainmail", 2, 40, true), armor("plate", "Plate", 3, 60, true),
        weapon("dagger", "Dagger", "d6", 5), weapon("spear", "Spear", "d8", 10), weapon("halberd", "Halberd", "d10", 20, true), weapon("sling", "Sling", "d6", 5), weapon("bow", "Bow", "d6", 20, true), weapon("crossbow", "Crossbow", "d8", 30, true),
        gear("cart", "Cart", 30, 2, tags = setOf("capacity:+4", "bulky-when-pulled")), gear("wagon", "Wagon", 200, 2, tags = setOf("capacity:+8", "slow")), MarketplaceEntry("horse", "Horse", MarketplaceCategory.TRANSPORT, 75, description = "4 slots"), MarketplaceEntry("mule", "Mule", MarketplaceCategory.TRANSPORT, 30, description = "6 slots, slow"),
        gear("air-bladder", "Air Bladder", 5), gear("antitoxin", "Antitoxin", 20), gear("bandages-3-uses", "Bandages", 30, uses = 3, tags = setOf("bandages")), gear("bathing-goods", "Bathing Goods", 5), gear("book", "Book", 50), gear("caltrops", "Caltrops", 10), gear("card-deck", "Card Deck", 5), gear("chain-10ft", "Chain", 10), gear("chalk", "Chalk", 1, petty = true), gear("chest", "Chest", 25), gear("chisel", "Chisel", 5), gear("common-agents", "Common Agents", 10), gear("common-tools", "Common Tools", 10), gear("compass", "Compass", 75), gear("complex-instruments", "Complex Instruments", 50), gear("containers", "Containers", 10), gear("cooking-gear", "Cooking Gear", 10), gear("costume-gear", "Costume Gear", 15), gear("dowsing-rod", "Dowsing Rod", 15), gear("expeditionary-gear", "Expeditionary Gear", 10), gear("fire-oil", "Fire Oil", 10), gear("fishing-rod", "Fishing Rod", 10), gear("games", "Games", 10), gear("gloves", "Gloves", 20, petty = true), gear("grappling-hook", "Grappling Hook", 25), gear("lantern", "Lantern", 10), gear("mirror", "Mirror", 5), gear("net", "Net", 10), gear("oil-can-6-uses", "Oil Can", 10, uses = 6), gear("outdoor-comfort", "Outdoor Comfort", 10), gear("parchment-3-uses", "Parchment", 10, uses = 3), gear("pole-10ft", "Pole", 5), gear("repellent", "Repellent", 10), gear("rope-25ft", "Rope", 5), gear("sedative", "Sedative", 30), gear("sewing-kit", "Sewing Kit", 20), gear("simple-instruments", "Simple Instruments", 10), gear("smoking-pipe", "Smoking Pipe", 15, petty = true), gear("specialized-tools", "Specialized Tools", 20), gear("spiked-boots", "Spiked Boots", 15), gear("spyglass", "Spyglass", 40), gear("tent", "Tent", 20, slots = 2, tags = setOf("fits:2")), gear("thieving-tools", "Thieving Tools", 25, tags = setOf("lockpick")), gear("torch-3-uses", "Torch", 5, uses = 3), gear("trap", "Trap", 35, damage = "d6", tags = setOf("trap")), gear("whistle", "Whistle", 15, petty = true), gear("wilderness-clothes", "Wilderness Clothes", 15, petty = true), gear("rations-3-uses", "Rations", 10, uses = 3, tags = setOf("ration")), gear("animal-feed-3-uses", "Animal Feed", 5, slots = 2, uses = 3, tags = setOf("feed")),
        MarketplaceEntry("room-board", "Room & Board", MarketplaceCategory.UPKEEP, 10, description = "Uma noite"), MarketplaceEntry("private-room-board", "Private Room & Board", MarketplaceCategory.UPKEEP, 35, description = "Uma noite, até quatro pessoas"), MarketplaceEntry("stable-feed", "Stable & Feed", MarketplaceCategory.UPKEEP, 5, description = "Uma noite"), MarketplaceEntry("medical-healing", "Medical Healing", MarketplaceCategory.UPKEEP, 50),
        MarketplaceEntry("carriage-seat", "Carriage Seat", MarketplaceCategory.TRANSPORT, 5), MarketplaceEntry("ships-passage", "Ship's Passage", MarketplaceCategory.TRANSPORT, 10),
        MarketplaceEntry("hireling-alchemist", "Alchemist", MarketplaceCategory.HIRELING, 30), MarketplaceEntry("hireling-navigator", "Navigator", MarketplaceCategory.HIRELING, 10), MarketplaceEntry("hireling-animal-handler", "Animal Handler", MarketplaceCategory.HIRELING, 5), MarketplaceEntry("hireling-sailor", "Sailor", MarketplaceCategory.HIRELING, 5), MarketplaceEntry("hireling-blacksmith", "Blacksmith", MarketplaceCategory.HIRELING, 15), MarketplaceEntry("hireling-scholar", "Scholar", MarketplaceCategory.HIRELING, 20), MarketplaceEntry("hireling-bodyguard", "Bodyguard", MarketplaceCategory.HIRELING, 10), MarketplaceEntry("hireling-tracker", "Tracker", MarketplaceCategory.HIRELING, 5), MarketplaceEntry("hireling-local-guide", "Local Guide", MarketplaceCategory.HIRELING, 5), MarketplaceEntry("hireling-trapper", "Trapper", MarketplaceCategory.HIRELING, 5), MarketplaceEntry("hireling-lockpick", "Lockpick", MarketplaceCategory.HIRELING, 10), MarketplaceEntry("hireling-veteran-bodyguard", "Veteran Bodyguard", MarketplaceCategory.HIRELING, 20)
    )

    private val byId = entries.associateBy { it.id }
    fun find(id: String): MarketplaceEntry? = byId[id]
}

class MarketplaceRules {
    fun creditGold(currentGoldGp: Int, amountGp: Int): Int {
        require(currentGoldGp >= 0) { "Current gold cannot be negative." }
        require(amountGp > 0) { "Gold credit must be positive." }
        return Math.addExact(currentGoldGp, amountGp)
    }

    fun purchase(state: CharacterState, gold: Int, itemId: String): PurchaseResult {
        require(gold >= 0)
        val entry = MarketplaceCatalog.find(itemId) ?: error("Unknown Marketplace entry: $itemId")
        require(gold >= entry.priceGp) { "Insufficient gold" }
        val item = entry.item
        val updated = if (item == null) state else RulesEngine(FixedRandomSource(1)).addItem(state, item).newState
        return PurchaseResult(updated, gold - entry.priceGp, entry)
    }
}
