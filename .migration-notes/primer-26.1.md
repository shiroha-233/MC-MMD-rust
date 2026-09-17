# Minecraft 1.21.11 -> 26.1 Mod Migration Primer

This is a high level, non-exhaustive overview on how to migrate your mod from 1.21.11 to 26.1. This does not look at any specific mod loader, just the changes to the vanilla classes.

This primer is licensed under the [Creative Commons Attribution 4.0 International](http://creativecommons.org/licenses/by/4.0/), so feel free to use it as a reference and leave a link so that other readers can consume the primer.

If there's any incorrect or missing information, please file an issue on this repository or ping @ChampionAsh5357 in the Neoforged Discord server.

Thank you to:

- @Shnupbups for some grammatical fixes
- @cassiancc for information about Java 25 IDE support
- @boq for information regarding IME support
- @lolothepro for a typo

## Pack Changes

There are a number of user-facing changes that are part of vanilla which are not discussed below that may be relevant to modders. You can find a list of them on [Misode's version changelog](https://misode.github.io/versions/?id=26.1&tab=changelog).

## Java 25 and Deobfuscation

26.1 introduces two new changes into the general pipeline.

First, the Java Development Kit has been upgraded from 21 to 25. Vanilla makes use of these new features, such as [JEP 447](https://openjdk.org/jeps/447), which allows statements before `super` within constructors. For users within the modding scene, please make sure to update accordingly, or take advantage of your IDE or build tool features. Microsoft's OpenJDK can be found [here](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-25).

You may need to update your IDE to support Java 25. If using Eclipse, you will need at least either 2025-12, or 2025-09 with the Java 25 Support marketplace plugin. If using IntelliJ IDEA, you will need at least 2025.2.

Vanilla has also returned to being deobfuscated, meaning that all value types now have the official names provided by Mojang. There are still some things that are not captured due to the Java compilation process, such as inlining primitive and string constants, but the majority are now provided. This will only have a change for users or mod loaders who used a different value type mapping set from the official mappings.

## Loot Type Unrolling

Loot pool entries, item functions, item conditions, nbt providers, number providers, score providers, int providers, and float providers no longer use a wrapping object type to act as the registered instance. Now, the registries directly take in the `MapCodec` used for the serialization and deserialization process. As such, the `*Type` classes or records that held the codec have been removed. Additionally, `getType` is now renamed to `codec`, taking in the registered `MapCodec`.

```java
// The following is an example with LootItemFunctions, but can roughly apply to the other instances as well

public record NoopItemFunction() implements LootItemFunction {
    public static final NoopItemFunction INSTANCE = new NoopItemFunction();
    // The map codec used as the registry object
    public static final MapCodec<NoopItemFunction> MAP_CODEC = MapCodec.unit(INSTANCE);

    // Replaces getType
    @Override
    public MapCodec<NoopItemFunction> codec() {
        // Return the registry object
        return MAP_CODEC;
    }
}

// Register the map codec to the appropriate registry
Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Identifier.fromNamespaceAndPath("examplemod", "noop"), NoopItemFunction.MAP_CODEC);
```

- `net.minecraft.core.registries.BuiltInRegistries`, `Registries`
    - `LOOT_POOL_ENTRY_TYPE` now holds a `LootPoolEntryContainer` map codec instead of a `LootPoolEntryType`
    - `LOOT_FUNCTION_TYPE` now holds a `LootItemFunction` map codec instead of a `LootItemFunctionType`
    - `LOOT_CONDITION_TYPE` now holds a `LootItemCondition` map codec instead of a `LootItemConditionType`
    - `LOOT_NUMBER_PROVIDER_TYPE` now holds a `NumberProvider` map codec instead of a `LootNumberProviderType`
    - `LOOT_NBT_PROVIDER_TYPE` now holds a `NbtProvider` map codec instead of a `LootNbtProviderType`
    - `LOOT_SCORE_PROVIDER_TYPE` now holds a `ScoreboardNameProvider` map codec instead of a `LootScoreProviderType`
    - `FLOAT_PROVIDER_TYPE` now holds a `FloatProvider` map codec instead of a `FloatProviderType`
    - `INT_PROVIDER_TYPE` now holds a `IntProvider` map codec instead of a `IntProviderType`
- `net.minecraft.util.valueproviders` now renames `CODEC` fields to `MAP_CODEC`
    - `FloatProvider` subtypes are now all records
    - `IntProvider` subtypes, except for `WeightedListInt`, are now all records
    - `FloatProvider` is now an `interface` from a `class`
        - `CODEC` -> `FloatProviders#CODEC`
        - `codec` -> `FloatProviders#codec`
        - `getType` ->  `codec`, not one-to-one
        - `getMinValue` -> `min`
        - `getMaxValue` -> `max`
    - `FloatProviders` - All vanilla float providers to register.
    - `FloatProviderType` interface is removed
        - Singleton fields have all been removed, use map codecs in each class instead
        - `codec` -> `FloatProvider#codec`
    - `IntProvider` is now an `interface` from a `class`
        - `CODEC` -> `IntProviders#CODEC`
        - `NON_NEGATIVE_CODEC` -> `IntProviders#NON_NEGATIVE_CODEC`
        - `POSITIVE_CODEC` -> `IntProviders#POSITIVE_CODEC`
        - `codec` -> `IntProviders#codec`
        - `validateCodec` -> `IntProviders#validateCodec`
        - `getMinValue` -> `minInclusive`
        - `getMaxValue` -> `maxInclusive`
        - `getType` ->  `codec`, not one-to-one
    - `IntProviders` - All vanilla int providers to register.
    - `IntProviderType` interface is removed
        - Singleton fields have all been removed, use map codecs in each class instead
        - `codec` -> `IntProvider#codec`
- `net.minecraft.world.level.storage.loot.entries` now renames `CODEC` fields to `MAP_CODEC`
    - `LootPoolEntries` singleton fields have all been removed
        - The map codecs in each class should be used instead
        - `bootstrap` - Registers the loot pool entries.
    - `LootPoolEntryContainer#getType` -> `codec`, not one-to-one
    - `LootPoolEntryType` record is removed
- `net.minecraft.world.level.storage.loot.functions` now renames `CODEC` fields to `MAP_CODEC`
    - `LootItemFunction#getType` -> `codec`, not one-to-one
    - `LootItemFunctions` singleton fields have all been removed
        - The map codecs in each class should be used instead
        - `bootstrap` - Registers the loot item functions.
    - `LootItemFunctionType` record is removed
- `net.minecraft.world.level.storage.loot.predicates` now renames `CODEC` fields to `MAP_CODEC`
    - `LootItemCondition#getType` -> `codec`, not one-to-one
    - `LootItemConditions` singleton fields have all been removed
        - The map codecs in each class should be used instead
        - `bootstrap` - Registers the loot item conditions.
    - `LootItemConditionType` record is removed
- `net.minecraft.world.level.storage.loot.providers.nbt` now renames `CODEC` fields to `MAP_CODEC`
    - `LootNbtProviderType` record is removed
    - `NbtProvider` now implements `LootContextUser`
        - `getType` -> `codec`, not one-to-one
    - `NbtProviders` singleton fields have all been removed
        - The map codecs in each class should be used instead
        - `bootstrap` - Registers the nbt providers.
    - `LootItemConditionType` record is removed
- `net.minecraft.world.level.storage.loot.providers.number` now renames `CODEC` fields to `MAP_CODEC`
    - `LootNumberProviderType` record is removed
    - `NumberProvider#getType` -> `codec`, not one-to-one
    - `NumberProviders` singleton fields have all been removed
        - The map codecs in each class should be used instead
        - `bootstrap` - Registers the number providers.
- `net.minecraft.world.level.storage.loot.providers.score` now renames `CODEC` fields to `MAP_CODEC`
    - `LootScoreProviderType` record is removed
    - `ScoreProvider` now implements `LootContextUser`
        - `getType` -> `codec`, not one-to-one
    - `ScoreProviders` singleton fields have all been removed
        - The map codecs in each class should be used instead
        - `bootstrap` - Registers the score providers.

## Validation Overhaul

The validation handler used to collect and then report problems with data-generated content has been overhauled. This is not, and has never been a replacement for field validation. The validation handler is specifically for more easily handling validation between multiple pieces of information that may not be exposed to a single field, such as whether a loot provider can be used for the given context params.

All validated objects implement either `Validatable`, or `CriterionTriggerInstance` specifically for advancement criteria. Both of these methods provide one method: `validate`, which is used to check the validity of an object. `validate` takes in a `ValidationContext`, which essentially holds the `ProblemReporter` used to collect issues, the current context params, and a reference resolver. `CriterionTriggerInstance` provides a `ValidationContextSource`, but this can be transformed to a `ValidationContext` using one of the `context` methods, providing the context params to check against. If the specific object cannot be validated, then `ValidationContext#reportProblem` is called, detailing the specific issue.

```java
// For some object that implements Validatable

@Override
public void validate(ValidationContext ctx) {
    // Check if a specific condition is validated.
    if (this.foo() != this.bar()) {
        // If not, report that there is an issue.
        ctx.reportProblem(() -> "'Foo' does not equal 'bar'.");
    }
}
```

If the object itself does not have any issues, but rather the specific fields, then the reporter can follow the stack trace while checking the individual elements using one of the `ValidationContext#for*` methods, performing something similar.

```java
// For some object that implements Validatable
// Let's assume it has a list of children objects.

@Override
public void validate(ValidationContext ctx) {
    for (int i = 0; i < this.children.size(); i++) {
        // Get specific context for child in list
        var childCtx = ctx.forIndexedField("children", i);
        // Check if a specific condition is validated.
        if (this.foo() != this.bar()) {
            // If not, report that there is an issue.
            childCtx.reportProblem(() -> "'Foo' does not equal 'bar'.");
        }
    }
}
```

`Validatable` also provides some static utilities for checking other `Validatable` fields.

```java
// For some object that implements Validatable
// Assume some child object also implements Validatable

@Override
public void validate(ValidationContext ctx) {
    Validatable.validate(ctx, "child", this.child);
}
```

After `Validatable` is implemented on all required objects, the validation can then be called depending on where it is used (typically after deserialization or before serialization). The call stack for these is typically the following:

```java
// For some Validatable validatable
// Let's assume we have access to the HolderGetter.Provider provider.
// The parameter itself is optional if not available.

// Create the problem collector and validation context.
// The context params should only include the ones that are being provided.
ProblemReporter reporter = new ProblemCollector.Collector();
ValidationContext ctx = new ValidationContext(reporter, LootContextParamSets.ALL_PARAMS, provider);

// Call the validator
validatable.validate(ctx);
```

This can also be appended to the end of codecs via `Codec#validate`:

```java
public record ExampleObject() implements Validatable {
    public static final Codec<ExampleObject> CODEC = MapCodec.unitCodec(
        ExampleObject::new
    ).validate(
        // Supply the validator along with the context params to check against.
        // This method does not have access to the registry provider.
        Validatable.validatorForContext(LootContextParamSets.ALL_PARAMS)
    );

    @Override
    public void validate(ValidationContext ctx) {
        // ...
    }
}
```

- `net.minecraft.advancements.CriterionTriggerInstance#validate` now takes in a `ValidationContextSource` instead of a `CriterionValidator`
- `net.minecraft.advancements.criterion`
    - `ContextAwarePredicate` now implements `Validatable`
    - `CriterionValidator` -> `ValidationContextSource` and `Validatable`
        - `Validatable` contains the `validate*` methods
        - `ValidationContextSource` holds the context and reporters
- `net.minecraft.world.item.enchantment`
    - `ConditionalEffect` now implements `Validatable`
        - `conditionCodec` is replaced by calling `validate` after load
    - `TargetedConditionalEffect` now implements `Validatable`
- `net.minecraft.world.level.storage.loot`
    - `IntRange` now implements `LootContextUser`
        - `getReferencedContextParams` replaced by `validate`
    - `LootContext$VisitedEntry` generic must now extend `Validatable`
    - `LootContextUser` now implements `Validatable`
    - `LootDataType` generic must now extend `Validatable`
        - The constructor now takes in a `$ContextGetter` instead of a `$Validator`
        - `runValidation` now takes in the `ValidationContextSource` instead of a `ValidationContext`
            - Also has an overload taking in a `HolderLookup` instead of a key-value pair
        - `createSimpleValidator`, `createLootTableValidator`, `$Validator` replaced by `Validatable`
        - `$ContextGetter` - Gets the `ContextKeySet` for some value.
    - `LootPool` now implements `Validatable`
    - `LootTable` now implements `Validatable`
    - `Validatable` - An interface which handles the validation of its instance within the given context.
    - `ValidationContext`
        - `forField` - Creates a context for a given field.
        - `forIndexedField` - Creates a context for a given entry in a list.
        - `forMapField` - Creates a context for a given key in a map.
        - `setContextKeySet` is removed
    - `ValidationContextSource` - The source for the defined context where the validation is taking place.
- `net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer` now implements `Validatable`
- `net.minecraft.world.level.storage.loot.functions`
    - `SetAttributesFunction$Modifier` now implements `LootContextUser`
    - `SetStewEffectFunction$EffectEntry` now implements `LootContextUser`

## Datapack Villager Trades

Villager trades has now become a data-generated registry from its original map-based setup. While basic impressions make it seem like that system is more limited, it is actually just as extensible, though in a rather convoluted way because the trades are functionally a loot table itself to determine what `MerchantOffer`s a trader can provide. For the purposes of understanding, this section will go over the basics of the trade rewrite, along with how each previous item listing can be converted to a `VillagerTrade`.

### Understanding the Trade Format

All trades are expressed as `VillagerTrade`s, which at its core determines what a trader `wants` and what it `gives` in return. Each trade can also provide modifiers to the item itself or its cost, or whether the specific trade can be made at all with the given condition. Each trade also specifies the number of trades that can be made, how much xp to give, or the price multiple that is multiplied with the user's reputation. A `VillagerTrade` is then turned into a `MerchantOffer` via `getOffer`, taking in the `LootContext`, typically with a context param of `LootContextParamSets#VILLAGER_TRADE`, providing the trader itself (`THIS_ENTITY`) and its position (`ORIGIN`).

The trades themselves are within `data/<namespace>/villager_trade/<path>`. Typically the path contains the profession and level the trade is for, such as `examplemod:example_profession/1/example_trade`

```json5
// For some villager trade 'examplemod:example_profession/1/example_trade'
// JSON at 'data/examplemod/villager_trade/example_profession/1/example_trade.json'
{
    // The stack the trader wants.
    "wants": {
        // The item of the stack.
        "id": "minecraft:apple",
        // A number provider used to determine how much
        // of the item that it wants. Once the count is
        // determined, any additional cost on the `gives`
        // stack (via `ADDITIONAL_TRADE_COST` component)
        // is added to the count before being clamped to
        // the max stack size.
        // If not specified, defaults to 1.
        "count": {
            "type": "minecraft:uniform",
            "min": 1,
            "max": 5
        },
        // Any components that stack should have. The stack
        // must have the exact components specified.
        // If not specified, then no components will be
        // checked, meaning that this is ignored.
        "components": {
            // Map of registry key to component value.
            "minecraft:custom_name": "Apple...?"
        }
    },
    // An additional stack the trader wants.
    // If not specified, the trader only checks `wants`.
    "additional_wants": {
        "id": "minecraft:emerald"
    },
    // The stack template the trader gives in return.
    "gives": {
        // The item of the stack.
        "id": "minecraft:golden_apple",
        // A number [1, 99].
        // If not specified, defaults to 1.
        "count": 1,
        // The components to apply to the stack.
        // If not specified, just applies the default
        // item components.
        "components": {
            "minecraft:custom_name": "Not an Apple"
        }
    },
    // A number provider to determine how many times
    // the trade can be performed by the player before
    // the trader restocks. The value will always be at
    // least 1. 
    // If not specified, defaults to 4.
    "max_uses": {
        "type": "minecraft:uniform",
        "min": 1,
        "max": 20
    },
    // A number provider to determine the price multiplier
    // to apply to the cost of the item based on the player's
    // reputation with the trader and the item demand,
    // calculated from how many times the player has performed
    // the trade. This should generally be a small number as
    // the maximum reputation a user can have per trader is 150,
    // though it's more likely to have a reputation of 25 at most.
    // However, the trade must always have a minimum of one item,
    // even if the reputation discount multiplied with the reputation
    // indicates 100% or more off.
    // If not specified, defaults to 0.
    "reputation_discount": {
        "type": "minecraft:uniform",
        "min": 0,
        "max": 0.05
    },
    // A number provider to determine the amount of experience
    // the player obtains from performing the trade with the trader.
    // This is typically around 5-30 xp for vanilla trades.
    // If not specified, defaults to 1.
    "xp": {
        "type": "minecraft:uniform",
        "min": 10,
        "max": 20
    },
    // A loot item condition that determines whether the
    // trader can provide the trade to the player.
    // If not specified, defaults to always true.
    "merchant_predicate": {
        // This trade can only be performed by villagers
        // from a desert or snow village.
        "condition": "minecraft:entity_properties",
        "entity": "this",
        "predicate": {
            "predicates": {
                "minecraft:villager/variant": [
                    "minecraft:desert",
                    "minecraft:snow"
                ]
            }
        }
    },
    // A list of loot item functions that modify the item
    // offered from `gives` to the player.
    // If not specified, `gives` is not modified.
    "given_item_modifiers": [
        {
            // Chooses a random enchantment from the provided tag
            "function": "minecraft:enchant_randomly",
            // If this is true, the trade cost increases the
            // number of `wants` items required.
            "include_additional_cost_component": true,
            "only_compatible": false,
            "options": "#minecraft:trades/desert_common"
        }
    ],
    // Can either be an enchantment id, such as "minecraft:protection",
    // or a list of enchantment ids, such as ["minecraft:protection", "minecraft:smite", ...],
    // or an enchantment tag, such as "#minecraft:trades/desert_common".
    // When provided, if the `gives` item after modifiers contains an
    // enchantment in this list, then the number of `wants` items required
    // is multiplied by 2.
    // If not specified, does nothing.
    "double_trade_price_enchantments": "#minecraft:trades/desert_common"
}
```

### The Trades of a Trader

Every trader can make many trades, typically choosing from a specified pool known as a `TradeSet`. Trade sets themselves are a separate datapack registry that consumes `VillagerTrade`s. Each set of trades determines how many trades can be offered and if the same trade can be chosen more than once. The trades that are offered are computed within `AbstractVillager#addOffersFromTradeSet`, first calling `TradeSet#calculateNumberOfTrades` to get the number of offers, and then using either `AbstractVillager#addOffersFromItemListings` or `AbstractVillager#addOffersFromItemListingsWithoutDuplicates` to choose the offers to use.

Note that if duplicate trades are allowed, there is a potential race condition where if all trades' `merchant_predicate`s fail, then the offers will loop forever. This is because the method always assumes that there will be one trade that can be made.

The trade sets are within `data/<namespace>/trade_set/<path>`. Typically the path contains the profession and level the trade is for, such as `examplemod:example_profession/level_1.json`

```json5
// For some trade set 'examplemod:example_profession/level_1'
// JSON at 'data/examplemod/villager_trade/trade_set/level_1.json'
{
    // Can either be a villager trade id, such as "examplemod:example_profession/1/example_trade",
    // or a list of trade ids, such as ["examplemod:example_profession/1/example_trade", "minecraft:farmer/1/wheat_emerald", ...],
    // or an trade tag, such as "#examplemod:example_profession/level_1".
    // This is the set of trades that can be offered by the trader.
    // This should always be a villager trade tag so allow other users
    // to easily add their own trades to a trader.
    "trades": "#examplemod:example_profession/level_1",
    // A number provider that determines the number of offers that can be
    // made by the trader.
    "amount": {
        "type": "minecraft:uniform",
        "min": 1,
        "max": 5
    },
    // Whether the same trade can be used to make multiple offers.
    // If not specified, defaults to false.
    "allow_duplicates": true,
    // An identifier that determines the unique random instance to
    // user when determining the offers.
    // If not specified, uses the level random.
    "random_sequence": "examplemod:example_profession/level_1"
}
```

Where the villager trade tag could be:

```json5
// For some tag 'examplemod:example_profession/level_1'
// JSON at 'data/examplemod/tags/villager_trade/example_profession/level_1.json'
{
    "values": [
        "examplemod:example_profession/1/example_trade"
    ]
}
```

This also means trades can be easily added to existing trade sets by adding to the associated tag:

```json5
// For some tag 'minecraft:farmer/level_1'
// JSON at 'data/minecraft/tags/villager_trade/farmer/level_1.json'
{
    "values": [
        "examplemod:example_profession/1/example_trade"
    ]
}
```

Meanwhile, adding to a new `VillagerProfession` is done by mapping the level int to the trade set key in `tradeSetsByLevel`:

```java
public static final VillagerProfession EXAMPLE = Registry.register(
    BuiltInRegistries.VILLAGER_PROFESSION,
    Identifier.fromNamespaceAndPath("examplemod", "example_profession"),
    new VillagerProfession(
        Component.literal(""),
        p -> true,
        p -> true,
        ImmutableSet.of(),
        ImmutableSet.of(),
        null,
        // A map of profession level to trade set keys
        Int2ObjectMap.ofEntries(
            Int2ObjectMap.entry(
                // The profession level
                1,
                // The trade set id
                ResourceKey.create(Registries.TRADE_SET, Identifier.fromNamespaceAndPath("examplemod", "example_profession/level_1"))
            )
        )
    )
);
```

### Item Listing Conversions

With all this in mind, we can now convert item listings to their new data-generated villager trades.

#### Emeralds to Items

For the following trades:

```java
public static final VillagerTrades.ItemListing ITEM_TO_EMERALD = new VillagerTrades.EmeraldForItems(
    // The item the trader wants.
    Items.WHEAT,
    // The number of items the trader wants.
    20,
    // The maximum number of times the trade can be made
    // before restock.
    16,
    // The amount of experience given for the trade.
    2,
    // The number of emeralds given in return.
    1
);

public static final VillagerTrades.ItemListing EMERALD_TO_ITEM = new VillagerTrades.ItemsForEmeralds(
    // The item the trader will give in return.
    Items.BREAD,
    // The number of emeralds the trader wants.
    1,
    // The number of items the trader will give.
    6,
    // The maximum number of times the trade can be made
    // before restock.
    16,
    // The amount of experience given for the trade.
    1,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    0.05f
);
```

Their equivalent would be:

```json5
// For some villager trade 'examplemod:item_to_emerald'
// JSON at 'data/examplemod/villager_trade/item_to_emerald.json'
{
    "gives": {
        // The number of emeralds given in return.
        "count": 1,
        "id": "minecraft:emerald"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 16,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    // `EmeraldForItems` hardcoded this to 0.05
    "reputation_discount": 0.05,
    "wants": {
        // The number of items the trader wants.
        "count": 20,
        // The item the trader wants.
        "id": "minecraft:wheat"
    },
    // The amount of experience given for the trade.
    "xp": 2
}


// For some villager trade 'examplemod:emerald_to_item'
// JSON at 'data/examplemod/villager_trade/emerald_to_item.json'
{
    "gives": {
        // The number of items the trader will give.
        "count": 6,
        // The item the trader will give in return.
        "id": "minecraft:bread"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 16,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    "reputation_discount": 0.05,
    "wants": {
        "id": "minecraft:emerald",
        // The number of emeralds the trader wants.
        "count": 1
    },
    // The amount of experience given for the trade.
    "xp": 1
}
```

#### Items and Emeralds to Items

For the following trade:

```java
public static final VillagerTrades.ItemListing ITEM_EMERALD_TO_ITEM = new VillagerTrades.ItemsAndEmeraldsToItems(
    // The item the trader wants.
    Items.COD,
    // The number of items the trader wants. 
    6,
    // The number of emeralds the trader additionally wants.
    1,
    // The item the trader will give in return.
    Items.COOKED_COD,
    // The number of items the trader will give.
    6,
    // The maximum number of times the trade can be made
    // before restock.
    16,
    // The amount of experience given for the trade.
    1,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    0.05f
);
```

The equivalent would be:

```json5
// For some villager trade 'examplemod:item_emerald_to_item'
// JSON at 'data/examplemod/villager_trade/item_emerald_to_item.json'
{
    // The emeralds the trader additionally wants.
    "additional_wants": {
        "id": "minecraft:emerald",
    },
    "gives": {
        // The number of items the trader will give.
        "count": 6,
        // The item the trader will give in return.
        "id": "minecraft:cooked_cod"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 16,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    "reputation_discount": 0.05,
    "wants": {
        // The number of items the trader wants. 
        "count": 6,
        // The item the trader wants.
        "id": "minecraft:cod"
    },
    // The amount of experience given for the trade.
    "xp": 1
}
```

#### Emeralds to Dyed Armor

For the following trade:

```java
public static final VillagerTrades.ItemListing EMERALD_TO_DYED_ARMOR = new VillagerTrades.DyedArmorForEmeralds(
    // The item the trader will give in return and dye.
    Items.LEATHER_HELMET,
    // The number of emeralds the trader wants.
    5,
    // The maximum number of times the trade can be made
    // before restock.
    12,
    // The amount of experience given for the trade.
    5
);
```

The equivalent would be:

```json5
// For some villager trade 'examplemod:emerald_to_dyed_armor'
// JSON at 'data/examplemod/villager_trade/emerald_to_dyed_armor.json'
{
    "given_item_modifiers": [
        {
            // Sets the random dye(s) on the armor.
            "function": "minecraft:set_random_dyes",
            "number_of_dyes": {
                "type": "minecraft:sum",
                "summands": [
                    1.0,
                    {
                        "type": "minecraft:binomial",
                        "n": 2.0,
                        "p": 0.75
                    }
                ]
            }
        },
        {
            // Checks that the dye was successfully applied
            // to the item.
            "function": "minecraft:filtered",
            "item_filter": {
                "items": "minecraft:leather_helmet",
                "predicates": {
                    "minecraft:dyed_color": {}
                }
            },
            // If it fails, discards the offer.
            "on_fail": {
                "function": "minecraft:discard"
            }
        }
    ],
    "gives": {
        "count": 1,
        // The item the trader will give in return and dye.
        "id": "minecraft:leather_helmet"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 12,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    "reputation_discount": 0.05,
    "wants": {
        // The number of emeralds the trader wants.
        "count": 5,
        "id": "minecraft:emerald"
    },
    // The amount of experience given for the trade.
    "xp": 5
}
```

#### Emeralds to Enchanted Item

For the following trade:

```java
public static final VillagerTrades.ItemListing EMERALD_TO_ENCHANTED_BOOK = new VillagerTrades.EnchantBookForEmeralds(
    // The amount of experience given for the trade.
    30,
    // The minimum level used when selecting the stored enchantments on the book.
    3,
    // The maximum level used when selecting the stored enchantments on the book.
    3,
    // A tag containing the list of available enchantments to select from for the book.
    EnchantmentTags.TRADES_DESERT_SPECIAL
);

public static final VillagerTrades.ItemListing EMERALD_TO_ENCHANTED_ITEM = new VillagerTrades.EnchantedItemForEmeralds(
    // The item the trader will give in return and try to enchant.
    Items.FISHING_ROD,
    // The base number of emeralds the trader wants.
    3,
    // The maximum number of times the trade can be made
    // before restock.
    3,
    // The amount of experience given for the trade.
    10,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    0.2f
);
```

The equivalent would be:

```json5
// For some villager trade 'examplemod:emerald_to_enchanted_book'
// JSON at 'data/examplemod/villager_trade/emerald_to_enchanted_book.json'
{
    // The trader expects a book to write the enchantment to.
    "additional_wants": {
        "id": "minecraft:book"
    },
    // Trade cost for emeralds increased when in the double trade price
    // tag.
    "double_trade_price_enchantments": "#minecraft:double_trade_price",
    "given_item_modifiers": [
        {
            "function": "minecraft:enchant_with_levels",
            "include_additional_cost_component": true,
            "levels": {
                "type": "minecraft:uniform",
                // The minimum level used when selecting the stored enchantments on the book.
                "min": 3,
                // The maximum level used when selecting the stored enchantments on the book.
                "max": 3
            },
            // The list of available enchantments to select from for the book.
            "options": [
                "minecraft:efficiency"
            ]
        },
        {
            // Make sure the enchantment was added successfully with the given level.
            "function": "minecraft:filtered",
            "item_filter": {
                "items": "minecraft:enchanted_book",
                "predicates": {
                    "minecraft:stored_enchantments": [
                        {
                            "levels": {
                                // The minimum level used when selecting the stored enchantments on the book.
                                "min": 3,
                                // The maximum level used when selecting the stored enchantments on the book.
                                "max": 3
                            }
                        }
                    ]
                }
            },
            // Discard on failure
            "on_fail": {
                "function": "minecraft:discard"
            }
        }
    ],
    // The trader gives the enchanted book.
    "gives": {
        "count": 1,
        "id": "minecraft:enchanted_book"
    },
    // The maximum number of times the trade can be made
    // before restock was hardcoded to 12.
    "max_uses": 12,
    // The price multiplier to apply to the offer, given
    // reputation and demand, hardcoded to 0.2.
    "reputation_discount": 0.2,
    "wants": {
        "count": {
            "type": "minecraft:sum",
            "summands": [
                // A hardcoded computation based on the min and max
                // level of the enchantment.
                11.0,
                {
                    "type": "minecraft:uniform",
                    "max": 35.0,
                    "min": 0.0
                }
            ]
        },
        "id": "minecraft:emerald"
    },
    // The amount of experience given for the trade.
    "xp": 30
}

// For some villager trade 'examplemod:emerald_to_enchanted_item'
// JSON at 'data/examplemod/villager_trade/emerald_to_enchanted_item.json'
{
    "given_item_modifiers": [
        {
            // Applies the enchantment to the given equipment.
            "function": "minecraft:enchant_with_levels",
            "include_additional_cost_component": true,
            "levels": {
                "type": "minecraft:uniform",
                "max": 20,
                "min": 5
            },
            "options": "#minecraft:on_traded_equipment"
        },
        {
            // Checks to make sure the enchantment was applied.
            "function": "minecraft:filtered",
            "item_filter": {
                "items": "minecraft:fishing_rod",
                "predicates": {
                    "minecraft:enchantments": [
                        {}
                    ]
                }
            },
            // On fail, give nothing.
            "on_fail": {
                "function": "minecraft:discard"
            }
        }
    ],
    "gives": {
        "count": 1,
        // The item the trader will give in return and try to enchant.
        "id": "minecraft:fishing_rod"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 3,
    // The price multiplier to apply to the offer, given
    // reputation and demand.
    "reputation_discount": 0.2,
    "wants": {
        "count": {
            "type": "minecraft:sum",
            "summands": [
                // The base number of emeralds the trader wants.
                3,
                {
                    // The variation based on the enchantment level.
                    // Originally, this would be the value used in the
                    // item function, but since they are now isolated,
                    // these values could differ.
                    "type": "minecraft:uniform",
                    "max": 20,
                    "min": 5
                }
            ]
        },
        "id": "minecraft:emerald"
    },
    // The amount of experience given for the trade.
    "xp": 10
}
```

#### Items and Emeralds to Potion Effect Item

For the following trade:

```java
public static final VillagerTrades.ItemListing EMERALD_TO_SUSPICIOUS_STEW = new VillagerTrades.SuspiciousStewForEmerald(
    // The effect applied by the suspicious stew.
    MobEffects.NIGHT_VISION,
    // The number of ticks the effect should be active for.
    100,
    // The amount of experience given for the trade.
    15
);

public static final VillagerTrades.ItemListing ITEM_EMERALD_TO_TIPPED_ARROW = new VillagerTrades.TippedArrowForItemsAndEmeralds(
    // The item the trader additionally wants.
    Items.ARROW,
    // The number of items the trader additionally wants.
    5,
    // The item the trader will give in return.
    Items.TIPPED_ARROW,
    // The number of items the trader will give.
    5,
    // The number of emeralds the trader wants.
    2,
    // The maximum number of times the trade can be made
    // before restock.
    12,
    // The amount of experience given for the trade.
    30
);
```

The equivalent would be:

```json5
// For some villager trade 'examplemod:emerald_to_suspicious_stew'
// JSON at 'data/examplemod/villager_trade/emerald_to_suspicious_stew.json'
{
    "given_item_modifiers": [
        {
            "effects": [
                {
                    // The effect applied by the suspicious stew.
                    "type": "minecraft:night_vision",
                    // The number of ticks the effect should be active for.
                    "duration": 100
                }
                // Vanilla merges all suspicious stew offers
                // into one since this function picks one
                // stew effect at random.
            ],
            "function": "minecraft:set_stew_effect"
        }
    ],
    "gives": {
        "count": 1,
        "id": "minecraft:suspicious_stew"
    },
    // The maximum number of times the trade can be made
    // before restock, hardcoded to 12.
    "max_uses": 12,
    // The price multiplier to apply to the offer, given
    // reputation and demand, hardcoded to 0.05.
    "reputation_discount": 0.05,
    "wants": {
        "id": "minecraft:emerald"
    },
    // The amount of experience given for the trade.
    "xp": 15
}

// For some villager trade 'examplemod:item_emerald_to_tipped_arrow'
// JSON at 'data/examplemod/villager_trade/item_emerald_to_tipped_arrow.json'
{
    "additional_wants": {
        // The number of items the trader additionally wants.
        "count": 5,
        // The item the trader additionally wants.
        "id": "minecraft:arrow"
    },
    "given_item_modifiers": [
        {
            // Applies a random potion effect from the tradable potions.
            // Original implementation just picked any potion at random.
            "function": "minecraft:set_random_potion",
            "options": "#minecraft:tradeable"
        }
    ],
    "gives": {
        // The number of items the trader will give.
        "count": 5,
        // The item the trader will give in return.
        "id": "minecraft:tipped_arrow"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 12,
    // The price multiplier to apply to the offer, given
    // reputation and demand, hardcoded to 0.05.
    "reputation_discount": 0.05,
    "wants": {
        // The number of emeralds the trader wants.
        "count": 2,
        "id": "minecraft:emerald"
    },
    // The amount of experience given for the trade.
    "xp": 30
}
```

#### Emeralds to Treasure Map

For the following trade:

```java
public static final VillagerTrades.ItemListing EMERALD_TO_TREASURE_MAP = new VillagerTrades.TreasureMapForEmeralds(
    // The number of emeralds the trader wants.
    8,
    // A tag containing a list of treasure structures to find the nearest of.
    StructureTags.ON_TAIGA_VILLAGE_MAPS,
    // The translation key of the map name.
    "filled_map.village_taiga",
    // The icon used to decorate the treasure location found on the map.
    MapDecorationTypes.TAIGA_VILLAGE,
    // The maximum number of times the trade can be made
    // before restock.
    12,
    // The amount of experience given for the trade.
    5
);
```

The equivalent would be:

```json5
// For some villager trade 'examplemod:emerald_to_treasure_map'
// JSON at 'data/examplemod/villager_trade/emerald_to_treasure_map.json'
{
    "additional_wants": {
        // An item the trader additionally wants, hardcoded to compass.
        "id": "minecraft:compass"
    },
    "given_item_modifiers": [
        {
            // Finds a treasure structure to display on the map.

            // The icon used to decorate the treasure location found on the map.
            "decoration": "minecraft:village_taiga",
            // A tag containing a list of treasure structures to find the nearest of.
            "destination": "minecraft:on_taiga_village_maps",
            "function": "minecraft:exploration_map",
            "search_radius": 100
        },
        {
            // Sets the name of the map.

            "function": "minecraft:set_name",
            "name": {
                // The translation key of the map name.
                "translate": "filled_map.village_taiga"
            },
            "target": "item_name"
        },
        {
            // Check to make sure a structure was found.

            "function": "minecraft:filtered",
            "item_filter": {
                "items": "minecraft:filled_map",
                "predicates": {
                    "minecraft:map_id": {}
                }
            },
            "on_fail": {
                "function": "minecraft:discard"
            }
        }
    ],
    "gives": {
        "count": 1,
        // Returns a map filled with the treasure location.
        "id": "minecraft:map"
    },
    // The maximum number of times the trade can be made
    // before restock.
    "max_uses": 12,
    // The price multiplier to apply to the offer, given
    // reputation and demand, hardcoded to 0.05.
    "reputation_discount": 0.05,
    "wants": {
        // The number of emeralds the trader wants.
        "count": 8,
        "id": "minecraft:emerald"
    },
    // The amount of experience given for the trade.
    "xp": 5
}
```

#### Villager Variants

Some traders would provide different options depending on the villager type it was as one giant map of item listings. Now, each type has its own individual villager trade, using a `merchant_predicate` to check whether the offer can be made to the specific villager type:

```json5
// For some villager trade 'examplemod:villager_type_item'
// JSON at 'data/examplemod/villager_trade/villager_type_item.json'
{
    // ...
    "merchant_predicate": {
        // Check the entity.
        "condition": "minecraft:entity_properties",
        "entity": "this",
        "predicate": {
            "predicates": {
                // Villager type must be desert for this trade to be chosen.
                "minecraft:villager/variant": "minecraft:desert"
            }
        }
    },
    // ...
}
```

- `net.minecraft.core.registries.Registries`
    - `TRADE_SET` - A key to the registry holding a list of trades.
    - `VILLAGER_TRADE` - A key to the registry holding a single trade.
- `net.minecraft.tags.VillagerTradeTags` - Tags for villager trades.
- `net.minecraft.world.entity.npc.villager`
    - `AbstractVillager#addOffersFromItemListings` -> `addOffersFromTradeSet`, taking in a key for the trade set rather than the `$ItemListing`s and number of offers; not one-to-one
    - `VillagerProfession` now takes in a map of trader level to `TradeSet`
        - `getTrades` - Returns the trades set key for the given level.
    - `VillagerTrades` has been split into many different classes and implementations
        - `TRADES`, `EXPERIMENTAL_TRADES`, `WANDERING_TRADER_TRADES` is now represented by the `VillagerProfession#tradeSetsByLevel`
            - As they are now datapack entries, they are stored in their respective datapacks
            - `TRADES`, `EXPERIMENTAL_TRADES` are represented by `data/minecraft/trade_set/<profession>/*`
            - `WANDERING_TRADER_TRADES` are represented by `data/minecraft/trade_set/wandering_trader/*`
        - `$DyedArmorForEmeralds` -> `VillagerTrades#dyedItem`, `addRandomDye`; not one-to-one
        - `$EmeraldForItems` -> `VillagerTrade`, not one-to-one
        - `$EmeraldsForVillagerTypeItem` -> `VillagerTrades#registerBoatTrades`, see usage, not one-to-one
        - `$EnchantBookForEmeralds` -> `VillagerTrades#enchantedBook`, not one-to-one
        - `$EnchantedItemForEmeralds` -> `VillagerTrades#enchantedItem`, not one-to-one
        - `$ItemListing` -> `VillagerTrade`
        - `$ItemsAndEmeraldsToItems` -> `VillagerTrade` with `additionalWants`
        - `$ItemsForEmeralds` -> `VillagerTrade`, not one-to-one
        - `$SuspiciousStewForEmerald` -> `VillagerTrade` with `SetStewEffectFunction`
        - `$TippedArrowForItemsAndEmeralds` -> `VillagerTrade` with `SetRandomPotionFunction`
        - `$TreasureMapForEmeralds` -> `VillagerTrades$VillagerExplorerMapEntry`, see usage, not one-to-one
        - `$TypeSpecificTrade` -> `VillagerTrades#villagerTypeRestriction`, `villagerTypeHolderSet`; not one-to-one
- `net.minecraft.world.item.enchantment.providers.TradeRebalanceEnchantmentProviders` interface is removed
    - Replaced by `TradeRebalanceVillagerTrades`, `TradeRebalanceRegistries`
- `net.minecraft.world.item.trading`
    - `TradeCost` - An `ItemStack` that is being traded to some trader.
    - `TradeRebalanceVillagerTrades` - All trades part of the trade rebalance datapack.
    - `TradeSet` - A set of trades that can be performed by a trader at some level.
    - `TradeSets` - All vanilla trades sets for some trader at some level.
    - `VillagerTrade` - A trade between the trader and the player.
    - `VillagerTrades` - All vanilla trades.
- `net.minecraft.world.level.storage.loot.parameters.LootContextParamSets#VILLAGER_TRADE` - A loot context when a villager trade is taking place, containing the trade origin and the trading entity.

## `Level#random` field now protected

The `Level#random` field is now `protected` instead of `public`. As such, uses should transition to using the public `getRandom` method.

```java
// For some Level level
RandomSource random = level.getRandom();
```

- `net.minecraft.world.level.Level#random` field is now `protected` instead of `public`
    - Use `getRandom` method instead

## Data Component Initializers

Data components have begun their transition from being moved off the raw object and onto the `Holder` itself. This is to properly handle objects that are not available during construction, such as datapack registry objects for items. Currently, this is only implemented for `Item`s, but the system allows for any registry object, given its wrapped-holder, to store some defined data components.

Data components are attached to their holders through the `DataComponentInitializers`. During object construction, `DataComponentInitializers#add` is called, providing the `ResourceKey` identifier of the registry object, along with a `DataComponentInitializers$Initializer`. The initializer takes in three parameters: the builder for the component map, the full registries `HolderLookup$Provider`, and the key passed to `DataComponentInitializers#add`. The initializer functions like a consumer, also allowing for additional initializers to be chained via `$Initializer#andThen` or to easily add a component via `$Initializer#add`.

```java
// For some custom registry object
public ExampleObject(ResourceKey<ExampleObject> id) {
    // Register the data component initializer
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.add(
        // The identifier for the registry object
        id,
        // The initializer function, taking in a component builder,
        // the registries context, and the id
        (components, context, key) -> components
            .set(DataComponents.MAX_DAMAGE, 1)
            .set(DataComponents.DAMAGE_TYPE, context.getOrThrow(DamageTypes.SPEAR))
    );
}
```

From there, the data components are initialized or reinitialized whenever `ReloadableServerResources#loadResources` is called (on datapack reload). The datapack objects are loaded first, then the components are set on the `Holder$Reference`s. From there, the components can be gathered via `Holder#components`.

```java
// For some Holder<ExampleObject> EXAMPLE_HOLDER
DataComponentMap components = EXAMPLE_HOLDER.components();
```

### Items

Since components are now initialized during resource reload, `Item$Properties` provides two methods to properly delay component initialization until such data components are loaded: `delayedComponent` and `delayedHolderComponent`. `delayedComponent` takes in the component type and a function that takes in the `HolderLookup$Provider` and returns the component value. `delayedHolderCOmponent` delegates to `delayedComponent`, taking in a `ResourceKey` and setting the registry object as the component value.

```java
public static final Item EXAMPLE_ITEM = new Item(
    new Item.Properties()
        .delayedComponent(
            // The component type whose construction
            // should be lazily initialized.
            DataComponents.JUKEBOX_PLAYABLE,
            // A function that takes in the registries
            // and returns the component value.
            context -> new JukeboxPlayable(context.getOrThrow(
                JukeboxSongs.THIRTEEN
            ))
        )
        .delayedHolderComponent(
            // The component type which has a
            // holder value type.
            DataComponents.DAMAGE_TYPE
            // The resource key for a registry
            // object of the associated holder
            // generic type.
            DamageTypes.SPEAR
        )
        // ...
);
```

### Recipes

Since data components now hold the true values from being lazily initialized after resource reload, `Recipe#assemble` no longer takes in the `HolderLookup$Provider`. Instead, it assumes that the recipe has all the required data stored passed into the recipe, either on a stack or directly.

- `net.minecraft.core`
    - `Holder`
        - `areComponentsBound` - Whether the components have been bound to the holder.
        - `components` - The components of the object stored on the holder.
        - `direct`, `$Direct` now can take in the `DataComponentMap`
        - `$Reference#bindComponents` - Stores the components on the holder reference.
    - `Registry#componentLookup` - Gets the lookup of component to holders.
    - `WritableRegistry#bindTag` -> `bindTags`, now taking in a map of keys to holder lists instead of one mapping
- `net.minecraft.core.component`
    - `DataComponentInitializers` - A class that handles initializing the data components for component-attached objects.
    - `DataComponentLookup` - A lookup that maps the component type to the holders that use it.
    - `DataComponentMap$Builder#addValidator` - Adds a validator for the components on the object.
    - `DataComponentPatch`
        - `get` now takes in a `DataComponentGetter` and returns the raw component value
        - `$Builder#set` now has an overload that takes in an iterable of `TypedDataComponent`s
    - `DataComponents`
        - `DAMAGE_TYPE` now is a holder-wrapped `DamageType` instead of `EitherHolder`-wrapped
        - `PROVIDES_TRIM_MATERIAL` now is a holder-wrapped `TrimMaterial` instead of `ProvidesTrimMaterial`
        - `CHICKEN_VARIANT` now is a holder-wrapped `ChickenVariant` instead of `EitherHolder`-wrapped
        - `ZOMBIE_NAUTILUS_VARIANT` now is a holder-wrapped `ZombieNautilusVariant` instead of `EitherHolder`-wrapped
        - `PROVIDES_BANNER_PATTERNS` is now a `HolderSet` instead of a `TagKey`
- `net.minecraft.core.registries`
    - `BuiltInRegistries#DATA_COMPONENT_INITIALIZERS` - A list of data components attached to registry entries.
    - `Registries#componentsDirPath` - The path directory for the components in a registry.
- `net.minecraft.data.PackOutput#createRegistryComponentPathProvider` - The path provider for the components registry report.
- `net.minecraft.data.info.ItemListReport` -> `RegistryComponentsReport`, not one-to-one
- `net.minecraft.resources`
    - `NetworkRegistryLoadTask` - A load task that handles registering registry objects and tags from the network, or else from a resource.
    - `RegistryDataLoader`
        - `$PendingRegistration` -> `RegistryLoadTask$PendingRegistration`
        - `$RegistryData` now takes in a `RegistryValidator` instead of a `boolean` 
        - `$RegistryLoadTask` -> `RegistryLoadTask`, not one-to-one
    - `RegistryValidator` - An interface that validates the entries in a registry, storing all errors in a map.
    - `ResourceManagerRegistryLoadTask` - A load task that handles registering registry objects and tags from the local resource manager.
- `net.minecraft.server.ReloadableServerResources#updateStaticRegistryTags` -> `updateComponentsAndStaticRegistryTags`, not one-to-one
- `net.minecraft.world.item`
    - `EitherHolder` class is removed
    - `Item`
        - `CODEC_WITH_BOUND_COMPONENTS` - An item codec that validates that the components are bound.
        - `$Properties`
            - `delayedComponent` - Sets the component lazily, providing the `HolderLookup$Provider` to get any dynamic elements.
            - `delayedHolderComponent` - Sets the component lazily for some holder-wrapped registry object.
    - `ItemStack#validateComponents` is now `private` from `public`
    - `JukeboxPlayable` now holds a holder-wrapped `JukeboxSong` instead of an `EitherHolder`-wrapped variant
    - `JukeboxSong#fromStack` no longer takes in the `HolderLookup$Provider`
    - `SpawnEggItem`
        - `spawnEntity` is now `static`
        - `byId` now returns an optional holder-wrapped `Item` instead of a `SpawnEggItem`
        - `eggs` is removed
        - `getType` is now `static`
        - `spawnOffspringFromSpawnEgg` is now `static`
- `net.minecraft.world.item.component`
    - `BlocksAttacks` now holds an optional holder set-wrapped `DamageType` instead of a `TagKey`
    - `DamageResistant` now holds a holder set `DamageType` instead of a `TagKey`
    - `InstrumentComponent` now holds a holder-wrapped `Instrument` instead of an `EitherHolder`-wrapped variant
        - `unwrap` is removed
    - `ProvidesTrimMaterial` now holds a holder-wrapped `TrimMaterial` instead of an `EitherHolder`-wrapped variant
- `net.minecraft.world.item.crafting`
    - `Recipe#assemble` no longer takes in the `HolderLookup$Provider`
    - `SmithingTrimRecipe#applyTrim` no longer takes in the `HolderLookup$Provider`
- `net.minecraft.world.item.equipment.trim.TrimMaterials#getFromIngredient` is removed
- `net.minecraft.world.level.storage.loot.functions.SetInstrumentFunction`, `#setInstrumentOptions` now takes in a holder set-wrapped `Instrument` instead of a `TagKey`
- `net.minecraft.world.timeline.Timeline#validateRegistry` - Validates that each time marker was only defined once.

## Item Instances and Stack Templates

`ItemStack`s now have an immutable instance known as an `ItemStackTemplate`. Similar to the `ItemStack`, it contains the holder-wrapped `Item`, the number of items it represents, and a `DataComponentPatch` of the components to apply to the stack. The template can be transformed to a stack via `create`, or `apply` if adding additional data components. An `ItemStack` can likewise be turned into a template via `ItemStackTemplate#fromNonEmptyStack`. Templates are now used in place of `ItemStack`s were immutability is required (e.g., advancements, recipes, etc.). They provide a regular `CODEC`, a `MAP_CODEC`, and a `STREAM_CODEC` for network communication.

```java
ItemStackTemplate apple = new ItemStackTemplate(
    // The item of the stack
    Items.APPLE.builtInRegistryHolder(),
    // The number of items held
    5,
    // The components applied to the stack
    DataComponentPatch.builder()
        .set(DataComponents.ITEM_NAME, Component.literal("Apple?"))
        .build()
);

// Turn the template into a stack
ItemStack stack = apple.create();

// Creating a template from a non-empty stack
ItemStackTemplate fromStack = ItemStackTemplate.fromNonEmptyStack(stack);
```

To help standardize accessing the general components between the two, both the template and the stack implement `ItemInstance`, which provides access to the standard holder method checks, the count, and the `DataComponentGetter`. The common interface also means that if it doesn't matter whether the stack is mutable or not, then the `ItemInstance` can be used as the type.

```java
// Both are item instances
ItemInstance stack = new ItemStack(Items.APPLE);
ItemInstance template = new ItemStackTemplate(Items.APPLE);

// Get the holder or check something
Holder<Item> item = stack.typeHolder();
template.is(Items.APPLE);

// Get the number of items in the stack or template
int stackCount = stack.count();
int templateCount = template.count();

// Get the component values
Identifier stackModel = stack.get(DataComponents.ITEM_MODEL);
Identifier templateModel = template.get(DataComponents.ITEM_MODEL);
```

### Recipe Builders

Due to the addition of `ItemStackTemplate`s, `RecipeBuilder`s have changed slightly in their implementation. First, the builder no longer stores an `Item` for the result, instead defining the `defaultId` as the resource key recipe. As such, this allows for a more clear method of defining custom recipes that do not export an `Item`.

Of course, the change to this new format just has `defaultId` return `RecipeBuilder#getDefaultRecipeId` with the `ItemInstance` (either a stack or template), like so:

```java
public class ExampleRecipeBuilder implements RecipeBuilder {

    // The result of the recipe
    private final ItemStackTemplate result;

    public ExampleRecipeBuilder(ItemStackTemplate result) {
        this.result = result;
    }

    @Override
    public ResourceKey<Recipe<?>> defaultId() {
        // Get the default recipe id from the result
        return RecipeBuilder.getDefaultRecipeId(this.result);
    }

    // Implement everything else below
    // ...
}
```

- `net.minecraft.advancements`
    - `Advancement$Builder#display` now takes in an `ItemStackTemplate` instead of an `ItemStack`
    - `DisplayInfo` now takes in an `ItemStackTemplate` instead of an `ItemStack`
        - `getIcon` now returns an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.advancements.criterion`
    - `AnyBlockInteractionTrigger#trigger` now takes in an `ItemInstance` instead of an `ItemStack`
    - `ItemPredicate` now implements a predicate of `ItemInstance` instead of `ItemStack`
    - `ItemUsedOnLocationTrigger#trigger` now takes in an `ItemInstance` instead of an `ItemStack`
- `net.minecraft.client.particle.BreakingItemParticle$ItemParticleProvider#getSprite` now takes in an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.commands.arguments.item`
    - `ItemInput` is now a record
        - `serialize` is removed
        - `createItemStack` no longer takes in the `boolean` to check the size
    - `ItemParser#parse` now returns an `ItemResult` instead of an `ItemParser$ItemResult`
        - `$ItemResult` merged into `ItemInput`
- `net.minecraft.core.component.predicates`
    - `BundlePredicate` now deals with an iterable of `ItemInstance`s rather than `ItemStack`s
    - `ContainerPredicate` now deals with an iterable of `ItemInstance`s rather than `ItemStack`s
- `net.minecraft.core.particles.ItemParticleOption` now takes in an `ItemStackTemplate` instead of an `ItemStack`
    - There is also an overload for a regular `Item`
    - `getItem` now returns an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.data.recipes`
    - `RecipeBuilder`
        - `getResult` is removed
        - `defaultId` - The default identifiers for the recipe made with this builder.
        - `getDefaultRecipeId` now takes in an `ItemInstance` instead of an `ItemLike`
    - `RecipeProvider`
        - `oreSmelting`, `oreBlasting` now take in a `CookingBookCategory`
        - `oreCooking` no longer takes in the `RecipeSerializer` and now takes in a `CookingBookCategory`
        - `cookRecipes`, `simpleCookingRecipe` no longer take in the `RecipeSerializer`
        - `shapeless` now takes in an `ItemStackTemplate` instead of an `ItemStack`
    - `ShapedRecipeBuilder` now takes in an `ItemStackTemplate` for the result, with the `ItemLike` moved to an overload
        - Both constructors are `private`
    - `ShapelessRecipeBuilder` now takes in an `ItemStackTemplate` for the result instead of an `ItemStack`
    - `SimpleCookingRecipeBuilder` now takes in an `ItemStackTemplate` for the result, with the `ItemLike` moved to an overload
        - `generic` no longer takes in the `RecipeSerializer` and now takes in a `CookingBookCategory`
        - `blasting`, `smelting` now take in a `CookingBookCategory`
    - `SingleItemRecipeBuilder` now takes in an `ItemStackTemplate` for the result, with the `ItemLike` moved to an overload
        - The `ItemStackTemplate` constructor is made `private`
    - `SmithingTransformRecipeBuilder` now takes in an `ItemStackTemplate` for the result instead of an `Item`
    - `TransmuteRecipeBuilder` now takes in an `ItemStackTemplate` for the result instead of a `Holder<Item>`
        - The constructor is `private`
        - `transmute` now has an overload that takes in the `ItemStackTemplate` for the result
        - `addMaterialCountToOutput`, `setMaterialCount` - Handles the size of the result stack based on the number of materials used.
- `net.minecraft.network.chat.HoverEvent$ShowItem` now takes in an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.server.dialog.body.ItemBody` now takes in an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.world.entity.LivingEntity`
    - `dropFromEntityInteractLootTable` now takes in an `ItemInstance` instead of an `ItemStack` for the tool
    - `dropFromShearingLootTable` now takes in an `ItemInstance` instead of an `ItemStack` for the tool
- `net.minecraft.world.item`
    - `BundleItem#getSelectedItemStack` -> `getSelectedItem`, now returning an `ItemStackTemplate` instead of an `ItemStack`
    - `Item`
        - `getCraftingRemainder` now returns an `ItemStackTemplate` instead of an `ItemStack`
        - `$Properties#craftRemainder` now has an overload that takes in the `ItemStackTemplate`
    - `ItemInstance` - A typed item instance that can query the item, the size, and its components.
    - `ItemStack` now implements `ItemInstance`
        - `SINGLE_ITEM_CODEC`, `STRICT_CODEC`, `STRING_SINGLE_ITEM_CODEC`, `SIMPLE_ITEM_CODEC` are removed
        - `getMaxStackSize` -> `ItemInstance#getMaxStackSize`
    - `ItemStackTemplate` - A record containing the immutable components of a stack: the item, count, and components.
- `net.minecraft.world.item.component`
    - `BundleContents` now takes in a list of `ItemStackTemplate`s instead of `ItemStack`s
        - `items` now returns a list of `ItemStackTemplate`s instead of an iterable of `ItemStack`s
        - `itemsCopy` is removed
        - `getSelectedItem` - Returns the stack template of the selected item, or `null` if no item is selected.
    - `ChargedProjectile` is now a record
        - The constructor takes in a list of `ItemStackTemplate`s instead of `ItemStack`s
        - `of` -> `ofNonEmpty`
        - `getItems` -> `itemCopies`
    - `ItemContainerContents`
        - `stream` -> `allItemsCopyStream`
        - `nonEmptyStream` -> `nonEmptyItemCopyStream`
        - `nonEmptyItems`, `nonEmptyItemsCopy` -> `nonEmptyItems`, now returning an iterable of `ItemStackTemplate`s instead of `ItemStack`s
    - `UseRemainder` now takes in an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.world.item.crafting`
    - `AbstractCookingRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
        - `$Factory#create` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `BlastingRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `CampfireCookingRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `ShapedRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `ShapelessRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `SingleItemRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
        - `result` now returns an `ItemStackTemplate` instead of an `ItemStack`
        - `$Factory#create` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `SmeltingRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `SmithingTransformRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `SmokingRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `StonecutterRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `TransmuteRecipe` now takes in an `ItemStackTemplate` instead of an `ItemStack` for the result
    - `TransmuteResult` -> `ItemStackTemplate`, not one-to-one
        - `isResultUnchanged` is removed
        - `apply` -> `TransmuteRecipe#createWithOriginalComponents`, not one-to-one
- `net.minecraft.world.item.crafting.display.SlotDisplay$ItemStackSlotDisplay` now takes in an `ItemStackTemplate` instead of an `ItemStack`
- `net.minecraft.world.item.enchantment.EnchantmentHelper#getItemEnchantmentLevel` now takes in an `ItemInstance` instead of an `ItemStack`
- `net.minecraft.world.level.block.Block`
    - `dropFromBlockInteractLootTable` now takes in an `ItemInstance` instead of an `ItemStack`
    - `getDrops` now takes in an `ItemInstance` instead of an `ItemStack`
- `net.minecraft.world.level.block.entity.DecoratedPotBlockEntity#createdDecoratedPotItem` -> `createdDecoratedPotInstance`
    - `createDecoratedPotTemplate` creates the `ItemStackTemplate` instead of the `ItemStack`
- `net.minecraft.world.level.storage.loot`
    - `LootContext$ItemStackTarget` now implements an `ItemInstance` generic for the argument getter instead of the `ItemStack`
    - `LootContextArg$ArgCodecBuilder#anyItemStack` now requires a function taking in an `ItemInstance` context key instead of an `ItemStack` context key
- `net.minecraft.world.level.storage.loot.parameters.LootContextParams#TOOL` is now an `ItemInstance` context key instead of an `ItemStack` context key

## Serializer Records and Recipe Info

Recipes have been slightly reworked in their implementation. First, `RecipeSerializer` is now a record taking in the `MapCodec` and `StreamCodec` used to serialize and deserialize the recipe. As such, `Serializer` classes have been removed in their entirety, replaced with providing the codecs to the record during registration:

```java
// Assume some ExampleRecipe implements Recipe
// We'll say there's also only one INSTANCE
public static final RecipeSerializer<ExampleRecipe> EXAMPLE_RECIPE = new RecipeSerializer<>(
    // The map codec for reading the recipe to/from disk.
    MapCodec.unit(INSTANCE),
    // The stream codec for reading the recipe to/from the network.
    StreamCodec.unit(INSTANCE)
);
```

Second, some common data regarding the recipe settings and book information have been sectioned into separate objects. These objects are passed into the recipe as part of the constructor and used to more cleanly handle similar implementations across all recipes.

Vanilla provides four of these common object classes, split into two separate categories. `Recipe$CommonInfo` is used for general recipe settings. Meanwhile, `Recipe$BookInfo` is used for recipe book information, with `CraftingRecipe$CraftingBookInfo` for crafting recipes, and `AbstractCookingRecipe$CookingBookInfo` for cooking recipes (e.g., smelting, blasting, etc.). The common object classes provide methods for constructing the codecs as required, which can then be passed into the relevant recipe codec.

These classes are typically passed through the `Recipe` subclasses, used as boilerplate to implement abstract methods. None of the data in these objects are directly available outside the implementation itself, only through the methods defined in the `Recipe` interface. Because of this, classes like `NormalCraftingRecipe`, `CustomRecipe`, `SimpleSmithingRecipe`, and `SingleItemRecipe`, and `AbstractCookingRecipe` can be used to create a new recipe implementation by implementing a few methods.

Note that these common info classes are a design philosophy, which you can choose to implement if desired. It's only when building off existing recipe subtypes that you are required to make use of them.

- `net.minecraft.data.recipes`
    - `CustomCraftingRecipeBuilder` - A recipe builder that creates an arbitrary crafting recipe from some common and crafting book information.
    - `RecipeBuilder`
        - `determineBookCategory` -> `determineCraftingBookCategory`
        - `createCraftingCommonInfo` - Creates the common recipe info.
        - `createCraftingBookInfo` - Creates the crafting book info.
    - `RecipeUnlockAdvancementBuilder` - An advancement builder for unlocking a recipe.
    - `SpecialRecipeBuilder`, `special` now takes in a supplied `Recipe` instead of a function of `CraftingBookCategory` to `Recipe`
        - `unlockedBy` - The criteria required to unlock the recipe advancement.
- `net.minecraft.world.item.crafting`
    - `AbstractCookingRecipe` now takes in the `Recipe$CommonInfo` and `$CookingBookInfo` instead of the group and `CookingBookCategory`
        - `$Factory#create` now takes in the `Recipe$CommonInfo` and `$CookingBookInfo` instead of the group and `CookingBookCategory`
        - `$Serializer` replaced by `cookingMapCodec`, `cookingStreamCodec`
        - `$CookingBookInfo` - A record containing the common cooking information for the recipe book.
    - `BannerDuplicateRecipe` now takes in the banner `Ingredient` and the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `BlastingRecipe` now takes in the `Recipe$CommonInfo` and `$AbstractCookingRecipeCookingBookInfo` instead of the group and `CookingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `BookCloningRecipe` now takes in the `Ingredient` source and material, the `MinMaxBounds$Int`s defining the generations that can be copied, and the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `ALLOWED_BOOK_GENERATION_RANGES`, `DEFAULT_BOOK_GENERATION_RANGES` - Ranges for the book generation cloning.
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `CampfireCookingRecipe` now takes in the `Recipe$CommonInfo` and `AbstractCookingRecipe$CookingBookInfo` instead of the group and `CookingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `CraftingRecipe$CraftingBookInfo` - A record containing the common crafting information for the recipe book.
    - `CustomRecipe` no longer takes in anything to its constructor
        - `$Serializer` is removed, replaced by its implementation's codecs
    - `DecoratedPotRecipe` now takes in the `Ingredient` patterns for each side along with the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `DyeRecipe` now takes in the `Recipe$CommonInfo` and `CraftingRecipe$CraftingBookInfo` along with the `Ingredient` target and dye and the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `FireworkRocketRecipe` now takes in the `Ingredient` shell, fuel, and star along with the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `FireworkStarFadeRecipe` now takes in the `Ingredient` target and dye along with the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `FireworkStarRecipe` now takes in the `$Shape` to `Ingredient` map; the `Ingredient` trail, twinkle, fuel, and dye; along with the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `MapCloningRecipe` replaced with `TransmuteRecipe`
    - `MapExtendingRecipe` now extends `CustomRecipe` instead of `ShapedRecipe`
        - The constructor now takes in the `Ingredient` map and material along with the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `NormalCraftingRecipe` - A class that defines the standard implementation for a crafting recipe.
    - `Recipe`
        - `showNotification`, `group` are no longer default
        - `$BookInfo` - The information for the recipe book.
        - `$CommonInfo` - The common information across all recipes.
    - `RecipeSerializer` is now a record containing the `MapCodec` and `StreamCodec`
        - The registered entries have been moved to `RecipeSerializers`
        - `register` is removed
    - `RecipeSerializers` - All vanilla serializers for recipes.
    - `RepairItemRecipe` no longer takes in anything
        - `INSTANCE` - The recipe serializer singleton.
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `ShapedRecipe` now extends `NormalCraftingRecipe` instead of implementing `CraftingRecipe`
        - The constructor now takes in the `Recipe$CommonInfo` and `CraftingRecipe$CraftingBookInfo` instead of the group and `CraftingBookCategory`
        - `$Serializer` -> `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER`; not one-to-one
    - `ShapelessRecipe` now extends `NormalCraftingRecipe` instead of implementing `CraftingRecipe`
        - The constructor now takes in the `Recipe$CommonInfo` and `CraftingRecipe$CraftingBookInfo` instead of the group and `CraftingBookCategory`
        - `$Serializer` -> `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER`; not one-to-one
    - `ShieldDecorationRecipe` now takes in the `Ingredient` banner and target along with the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `SimpleSmithingRecipe` - A class that defines the standard implementation of a smithing recipe.
    - `SingleItemRecipe` now takes in the `Recipe$CommonInfo` instead of the group
        - `commonInfo` - The common information for the recipe.
        - `$Factory#create` now takes in the `Recipe$CommonInfo` instead of the group
        - `$Serializer` -> `simpleMapCodec`, `simpleStreamCodec`; not one-to-one
    - `SmeltingRecipe` now takes in the `Recipe$CommonInfo` and `AbstractCookingRecipe$CookingBookInfo` instead of the group and `CookingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `SmithingTransformRecipe` now extends `SimpleSmithingRecipe` instead of implementing `SmithingRecipe`
        - The constructor now takes in the `Recipe$CommonInfo`
        - `$Serializer` -> `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER`; not one-to-one
    - `SmithingTrimRecipe` now extends `SimpleSmithingRecipe` instead of implementing `SmithingRecipe`
        - The constructor now takes in the `Recipe$CommonInfo`
        - `$Serializer` -> `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER`; not one-to-one
    - `SmokingRecipe` now takes in the `Recipe$CommonInfo` and `AbstractCookingRecipe$CookingBookInfo` instead of the group and `CookingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `StonecutterRecipe` now takes in the `Recipe$CommonInfo` instead of the group
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `TippedArrowRecipe` -> `ImbueRecipe`, not one-to-one
        - The constructor now takes in the `Recipe$CommonInfo` and `CraftingRecipe$CraftingBookInfo` along with the `Ingredient` source and material and the `ItemStackTemplate` result instead of the `CraftingBookCategory`
        - `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER` - Serializers for the recipe.
    - `TransmuteRecipe` now extends `NormalCraftingRecipe` instead of implementing `CraftingRecipe`
        - The constructor now takes in the `Recipe$CommonInfo` and `CraftingRecipe$CraftingBookInfo` along with the `MinMaxBounds$Ints` and `boolean` handling the material count and adding it to the result instead of the group and `CraftingBookCategory`
        - `$Serializer` -> `MAP_CODEC`, `STREAM_CODEC`, `SERIALIZER`; not one-to-one
- `net.minecraft.world.item.crafting.display.SlotDisplay`
    - `$OnlyWithComponent` - A display only with the contents having the desired components.
    - `$WithAnyPotion` - A display with the contents having any potion contents component.
- `net.minecraft.world.level.storage.loot.functions.SmeltItemFunction#smelted` now can take in whether to use the input material count

## Dye Component

Specifying whether an item can be used as a dye material is now handled through the `DYE` data component. The component specifies a `DyeColor`, which can be set via `Item$Properties#component`:

```java
public static final Item EXAMPLE_DYE = new Item(new Item.Properties().component(
    DataComponents.DYE, DyeColor.WHITE
));
```

However, a dye material's behavior is not fully encompassed by the component alone. In most cases, the component is used in conjunction with some other tag or subclass to get the desired behavior.

### Entities and Signs

Dying sheeps and signs are handled purely through the `DyeItem` subclass, checking if the item has the `DYE` component. 

Dying the collars of wolfs and cats, on the other hand, can be any item, assuming it has the `DYE` component. Additionally, the item must be in the `ItemTags#WOLF_COLLAR_DYES` or `ItemTags#CAT_COLLAR_DYES` tag, respectively.

### Dye Recipes

The `DyeRecipe`, formerly named `ArmorDyeRecipe`, can take any target ingredient and apply the colors of the dye ingredients to obtain the desired result with the associated `DYED_COLOR` component. Any item can be considered a dye; however, those without the `DYE` component will default to `DyeColor#WHITE`. Armor makes used of `RecipeProvider#dyedItem` to allow any item in the `ItemTags#DYES` tag to dye armor. However, bundles and shulker boxes have their color components as different items, meaning instead the default recipes are tied directly to the vanilla `DyeItem`, meaning a separate recipe will need to be generated for applying dyes to those items.

The loom, firework star, and firework star face recipes on the other hand expect any dye material to have the `DYE` component. The loom has an additional requirement of the item being in the `ItemTags#LOOM_DYES` tag.

- `net.minecraft.core.component.DataComponents#DYE` - Represents that an item can act as a dye for the specific color.
- `net.minecraft.data.recipes.RecipeProvider`
    - `dyedItem` - Creates a dyed item recipe.
    - `dyedShulkerBoxRecipe` - Creates a dyed shulker box recipe.
    - `dyedBundleRecipe` - Creates a dyed bundle recipe.
- `net.minecraft.world.item`
    - `BundleItem`
        - `getAllBundleItemColors`, `getByColor` are removed
    - `DyeColor#VALUES` - A list of all dye colors.
    - `DyeItem` no longer takes in the `DyeColor`
        - `getDyeColor`, `byColor` are removed
- `net.minecraft.world.item.component.DyedItemColor#applyDyes` now takes in a list of `DyeColor`s instead of `DyeItem`s
    - An overload can also take in a `DyedItemColor` component instead of the `ItemStack`
- `net.minecraft.world.item.crafting.ArmorDyeRecipe` -> `DyeRecipe`, not one-to-one
- `net.minecraft.world.item.crafting.display.SlotDisplay$DyedSlotDemo` - A display for demoing dying an item.
- `net.minecraft.world.level.block`
    - `BannerBlock#byColor` is removed
    - `ShulkerBoxBlock#getBlockByColor`, `getColoredItemStack` are removed

## World Clocks and Time Markers

World clocks are objects that represent some time that increases every tick from when the world first loads. These clocks are used as timers to properly handle timing-based events (e.g., the current day, sleeping). Vanilla provides two world clocks: one for `minecraft:overworld` and one for `minecraft:the_end`.

Creating a clock is rather simple: just an empty, datapack registry object in `world_clock`.

```json5
// For some world clock 'examplemod:EXAMPLE_CLOCK'
// JSON at 'data/examplemod/world_clock/EXAMPLE_CLOCK.json'
{}
```

From there, you can query the clock state through the `ClockManager` via `Level#clockManager` or `MinecraftServer#clockManager`:

```java
// For some Level level
// Assume we have some ResourceKey<WorldClock> EXAMPLE_CLOCK

// Get the clock reference
Holder.Reference<WorldClock> clock = level.registryAccess().getOrThrow(EXAMPLE_CLOCK);

// Query the clock time
long ticksPassed = level.clockManager().getTotalTicks(clock);
```

If accessing the clock from the server, you can also modify the state of the clock:

```java
// For some ServerLevel level
// Assume we have some ResourceKey<WorldClock> EXAMPLE_CLOCK

// Get the clock reference
Holder.Reference<WorldClock> clock = level.registryAccess().getOrThrow(EXAMPLE_CLOCK);

// Get the server clock manager
ServerClockManager clockManager = level.clockManager();

// Set the total number of ticks that have passed
clockManager.setTotalTicks(clock, 0L);

// Add the a certain value to the number of ticks
clockManager.addTicks(clock, 10L);

// Pause the clock
clockManager.setPaused(
    clock,
    // `true` to pause.
    // `false` to unpause.
    true
);
```

### Marking Timelines

On its own, world clocks are rather limited in scope as you have to keep track of the number of ticks that have passed. However, when used with timelines, specific recurring times can be marked to handle timing-based events.

As a refresher, `Timeline`s are a method of modifying attributes based on some `WorldClock`. Currently, all vanilla timelines use the `minecraft:overworld` world clock to keep track of their timing, via the `clock` field. All timelines must define a clock it uses; however, be aware that clocks to not support any synchronization by default, meaning that updating the time on one clock will not affect the other.

```json5
// For some timeline 'examplemod:example_timeline'
// In `data/examplemod/timeline/example_timeline.json
{
    // Uses the custom clock.
    // Any changes to the clock time will only affect
    // timelines using this clock.
    // e.g., `minecraft:overworld` will not be changed.
    "clock": "examplemod:example_clock",
    // Perform actions on a 24,000 tick interval.
    "period_ticks": "24000",
    // ...
}


// For some timeline 'examplemod:example_timeline_2'
// In `data/examplemod/timeline/example_timeline_2.json
{
    // Uses the same clock as the one above.
    // Changes to the clock time will affect both
    // timelines.
    "clock": "examplemod:example_clock",
    // Perform actions on a 1,000 tick interval.
    "period_ticks": "1000",
    // ...
}

// For some timeline 'examplemod:example_overworld'
// In `data/examplemod/timeline/example_overworld.json
{
    // Uses the vanilla overworld clock.
    // Changes to the clock time will not affect
    // the above timelines as they use a different
    // clock.
    "clock": "minecraft:overworld",
    // Perform actions on a 6,000 tick interval.
    "period_ticks": "6000",
    // ...
}
```

Timelines can also define time markers, which is just some identifier for a time within the timeline period for a world clock. These are commonly used within commands (e.g. `/time set`), to check if a certain time has passed (e.g. village sieges), or to skip to the given time (e.g. waking up from sleep). Time markers are defined in `time_markers`, specifying the `ticks` within the period and whether it can be `show_in_commands`. As time markers are identified by their world clock, timelines using the same world clock cannot define the same time markers.

```json5
// For some timeline 'examplemod:example_timeline'
// In `data/examplemod/timeline/example_timeline.json
{
    // The identifier of the clock.
    "clock": "examplemod:example_clock",
    // Perform actions on a 24,000 tick interval.
    "period_ticks": "24000",
    // The markers within the period specified by the timeline
    "time_markers": {
        // A marker
        "examplemod:example_marker": {
            // The number of ticks within the period
            // that this marker represents.
            // e.g., 5000, 29000, 53000, etc.
            "ticks": 5000,
            // When true, allows the time marker to be
            // suggested in the command. If false,
            // the time marker can still be used in the
            // command, it just won't be suggested.
            "show_in_commands": true
        }
    }
    // ...
}
```

Once the time markers are defined, they are registered for a world clock in the `ServerClockManager`, allowing them to be used given the `ResourceKey`.

```java
// For some ServerLevel level
// Assume we have some ResourceKey<WorldClock> EXAMPLE_CLOCK
// Assume we have some ResourceKey<ClockTimerMarker> EXAMPLE_MARKER

// Get the clock reference
Holder.Reference<WorldClock> clock = level.registryAccess().getOrThrow(EXAMPLE_CLOCK);

// Get the server clock manager
ServerClockManager clockManager = level.clockManager();

// Check if the time is at the specified marker
// This should be used when checking for a specific time event every tick
boolean atMarker = clockManager.isAtTimeMarker(clock, EXAMPLE_MARKER);

// Skip to the time specified by the marker
// If the world clock is at 3000, sets the clock time to 5000
// If the world clock is at 6000, sets the clock time to 29000
// Returns whether the time was set, which is always true if the marker
// exists for the clock.
boolean timeSet = clockManager.skipToTimeMarker(clock, EXAMPLE_MARKER);
```

- `net.minecraft.client.ClientClockManager` - Manages the ticking of the world clocks on the client side.
- `net.minecraft.client.gui.components.debug.DebugEntryDayCount` - Displays the current day of the Minecraft world.
- `net.minecraft.client.multiplayer`
    - `ClientLevel`
        - `setTimeFromServer` no longer takes in the `long` day time nor the `boolean` of whether to tick the day time
        - `$ClientLevelData#setDayTime` is removed
    - `ClientPacketListener#clockManager` - Gets the client clock manager.
- `net.minecraft.client.renderer.EndFlashState#tick` now takes in the end clock time instead of the game time
- `net.minecraft.commands.arguments.ResourceArgument`
    - `getClock` - Gets a reference to the world clock given the string resource identifier.
    - `getTimeline` - Gets a reference to the timeline given the string resource identifier.
- `net.minecraft.core.registries.Registries#WORLD_CLOCK` - The registry identifier for the world clock.
- `net.minecraft.gametest.framework.TestEnvironmentDefinition`
    - `$TimeOfDay` -> `$ClockTime`, not one-to-one
    - `$Timelines` - A test environment that uses a list of timelines.
- `net.minecraft.network.protocol.game.ClientboundSetTimePacket` now takes in a map of clocks to their network states instead of the day time `long` and `boolean`
- `net.minecraft.server`
    - `MinecraftServer`
        - `forceTimeSynchronization` -> `forceGameTimeSynchronization`, not one-to-one
        - `clockManager` - The server clock manager.
- `net.minecraft.server.level.ServerLevel`
    - `setDayTime`, `getDayCount` are removed
    - `setEnvironmentAttributes` - Sets the environment attribute system to use.
- `net.minecraft.world.attribute.EnvironmentAttributeSystem$Builder#addTimelineLayer` now takes in the `ClockManager` instead of a `LongSupplier`
- `net.minecraft.world.clock`
    - `ClockManager` - A manager that gets the total number of ticks that have passed for a world clock.
    - `ClockNetworkState` - The current state of the clock to sync over the network.
    - `ClockState` - The current state of the clock, including the total number of ticks and whether the clock is paused.
    - `ClockTimeMarker` - A marker that keeps track of a certain period of time for a world clock.
    - `ClockTimeMarkers` - All vanilla time markers.
    - `PackedClockStates` - A map of clocks to their states, compressed for storage or other use.
    - `ServerClockManager` - Manages the ticking of the world clocks on the server side.
    - `WorldClock` - An empty record that represents a key for timing some location.
    - `WorldClocks` - All vanilla world clocks.
- `net.minecraft.world.level`
    - `Level`
        - `getDayTime` -> `getOverworldClockTime`, not one-to-one
        - `clockManager` - The clock manager.
        - `getDefaultClockTime` - Gets the clock time for the current dimension.
    - `LevelReader#getEffectiveSkyBrightness` - Gets the sky brightness with the current darkening factor.
- `net.minecraft.world.level.dimension.DimensionType` now takes in a default, holder-wrapped `WorldClock`
- `net.minecraft.world.level.storage`
    - `LevelData#getDayTime` is removed
    - `ServerLevelData`
        - `setDayTime` is removed
        - `setClockStates`, `clockStates` - Handles the current states of the world clocks.
- `net.minecraft.world.level.storage.loot.predicates.TimeCheck` now takes in a holder-wrapped `WorldClock`
    - `time` now takes in a holder-wrapped `WorldClock`
    - `$Builder` now takes in a holder-wrapped `WorldClock`
- `net.minecraft.world.timeline`
    - `AttributeTrack#bakeSampler` now takes in a holder-wrapped `WorldClock`
    - `AttributeTrackSampler` now takes in a holder-wrapped `WorldClock`, and a `ClockManager` instead of a `LongSupplier` for the day time getter
    - `Timeline` now takes in a holder-wrapped `WorldClock` along with a map of time markers to their infos
        - `#builder` now takes in a holder-wrapped `WorldClock`
        - `getPeriodCount` - Gets the number of times a period has occurred within the given timeframe.
        - `getCurrentTicks` now takes in the `ClockManager` instead of the `Level`
        - `getTotalTicks` now takes in the `ClockManager` instead of the `Level`
        - `clock` - Returns the holder-wrapped `WorldClock`.
        - `registerTimeMarkers` - Registers all `ClockTimeMarker` defined in this timeline.
        - `createTrackSampler` now takes in a `ClockManager` instead of a `LongSupplier` for the day time getter
        - `$Builder#addTimeMarker` - Adds a time markers at the given tick, along with whether the marker can be suggested in commands.
    - `Timelines#DAY` -> `OVERWORLD_DAY`

## Splitting the Primary Level Data into Saved Data

Some of the `WorldData` settings have been moved into `SavedData`, allowing for levels / dimensions to have more customizability. This addition also brings along some changes to how `SavedData` is referenced and queried.

### Saved Data Changes

`SavedDataType` now identifies some `SavedData` using an `Identifier`, which is resolved against the data folder. This change allows subdirectories within the data folder, as the data storage will first create all missing parent directories before attempting to write the file.

```java
public class ExampleData extends SavedData {

    public static final SavedDataType<ExampleData> TYPE = new SavedDataType<>(
        // The identifier for the saved data to resolve against
        // Data can be found in:
        // `<world_folder>/dimensions/<dimension_namespace>/<dimension_path>/data/examplemod/example/data.dat`
        Identifier.fromNamespaceAndPath("examplemod", "example/data"),
        // The constructor to create a new saved data
        ExampleData::new,
        // The codec to serialize the new saved data
        MapCodec.unitCodec(ExampleData::new),
        // The data fixer type
        // Either some patched enum value or null depending on mod loader implementation.
        null
    );
}
```

The `SavedData` can then be queried through the `SavedDataStorage`, renamed from `DimensionDataStorage`. This was renamed because the `MinecraftServer` instance now has its own data storage for global instances in addition to the levels. This means that any global saved data should be stored on the server instance rather than the overworld.

```java
// Given a MinecraftServer server
ExampleData data = server.getDataStorage().computeIfAbsent(ExampleData.TYPE);


// Given a ServerLevel level
ExampleData data = level.getDataStorage().computeIfAbsent(ExampleData.TYPE);
```

### Additional Saved Data

The following information is now stored as saved data:

- Custom boss events
- Ender dragon fight
- Game rules
- Wandering trader spawning
- Weather
- World generation settings

Of these, only the ender dragon fight is on a per-level / dimension basis. The rest are still stored and accessed through the server data storage. Custom boss events, on the other hand, remain unique as it is up to the implementer to determine what players are part of the event.

- `net.minecraft.client.Minecraft#doWorldLoad` now takes in the optional `GameRules`
- `net.minecraft.client.gui.screens.worldselection`
    - `CreateWorldCallback` now takes in the `LevelDataAndDimensions$WorldDataAndGenSettings` and the optional `GameRules` instead of the `PrimaryLevelData`
    - `EditGameRulesScreen` -> `AbstractGameRulesScreen`
        - Implementations in `.screens.options.InWorldGameRulesScreen` and `WorldCreationGameRulesScreen`
    - `WorldOpenFlows`
        - `createLevelFromExistingSettings` now takes in the `LevelDataAndDimensions$WorldDataAndGenSettings` and the optional `GameRules` instead of the `WorldData`
        - `loadWorldStem` now takes in the `LevelStorageSource$LevelStorageAccess`
- `net.minecraft.client.server.IntegratedServer` now takes in the optional `GameRules`
- `net.minecraft.server`
    - `MinecraftServer` now takes in the optional `GameRules`
        - `getGlobalGameRules` - Gets the game rules for the overworld dimension.
        - `getWorldGenSettings` - Gets the generation settings for the world.
        - `getWeatherData` - Gets the weather data for the server.
        - `getDataStorage` - Gets the saved data storage for the server.
        - `getGameRules` - Gets the game rules for the server.
    - `WorldStem` now takes in the `LevelDataAndDimensions$WorldDataAndGenSettings` instead of the `WorldData`
- `net.minecraft.server.bossevents`
    - `CustomBossEvent` now takes in a `UUID` for the identifier along with a `Runnable` for the callback
        - `getTextId` -> `customId`
        - `addOfflinePlayer` is removed
        - `getValue` -> `value`
        - `getMax` -> `max`
        - `load` now takes in the `UUID` identifier along with a `Runnable` for the callback
    - `CustomBossEvents` now extends `SavedData`
        - `create` now takes in a `RandomSource`
        - `save`, `load` -> `TYPE`, not one-to-one
- `net.minecraft.server.dedicated.DedicatedServer` now takes in the optional `GameRules`
- `net.minecraft.server.level`
    - `ChunkMap#getChunkDataFixContextTag` now takes in an optional `Identifier` instead of a `ResourceKey`
    - `ServerBossEvent` now takes in an `UUID` for the id
        - `setDirty` - Marks the boss event as dirty for saving.
    - `ServerLevel` no longer takes in the `RandomSequences`
        - `setWeatherParameters` -> `MinecraftServer#setWeatherParameters`
        - `getWeatherData` - Gets the weather data for the server.
        - `getRandomSequence` -> `MinecraftServer#getRandomSequence`
        - `getRandomSequences` -> `MinecraftServer#getRandomSequences`
- `net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner` now takes in the `SavedDataStorage` instead of the `ServerLevelData`
    - `MIN_SPAWN_CHANCE` is now `public` from `private`
- `net.minecraft.world.entity.raid.Raids#TYPE_END`, `getType` are removed
- `net.minecraft.world.level`
    - `Level#prepareWeather` is removed
    - `LevelSettings` is now a record
        - The constructor now takes in the `$DifficultySettings` instead of just the `Difficulty`
        - `withDifficultyLock` - The settings with whether the difficulty is locked.
        - `copy` - Copies the settings.
        - `$DifficultySettings` - The settings for the difficulty.
- `net.minecraft.world.level.dimension.DimensionType` now takes in a `boolean` for whether the dimension can have an ender dragon fight
- `net.minecraft.world.level.dimension.end`
    - `DragonRespawnAnimation` -> `DragonRespawnStage`, not one-to-one
    - `EndDragonFight` -> `EnderDragonFight`, not one-to-one
- `net.minecraft.world.level.gamerules.GameRuleMap` now extends `SavedData`
    - `TYPE` - The saved data type.
    - `reset` - Resets the rule to its default value.
- `net.minecraft.world.level.levelgen.WorldGenSettings` is now a final class instead of a record, extending `SavedData`
    - `encode`, `decode` replaced with `TYPE`
    - `of` - Constructs the generation settings.
- `net.minecraft.world.level.saveddata`
    - `SavedDataType` now takes in an `Identifier` instead of a string for the id
    - `WanderingTraderData` - The saved data for the wandering trader.
    - `WeatherData` - The saved data for the weather.
- `net.minecraft.world.level.storage`
    - `DimensionDataStorage` -> `SavedDataStorage`, not one-to-one
    - `LevelData#isThundering`, `isRaining`, `setRaining` now in `WeatherData`
    - `LevelDataAndDimensions` now takes in a `$WorldDataAndGenSettings` instead of the `WorldData`
        - `$WorldDataAndGenSettings` - Holds the world data and generation settings.
    - `LevelResource` is now a record
    - `PrimaryLevelData` fields have been moved to their respective saved data classes
        - `PLAYER` -> `OLD_PLAYER`
        - `SINGLEPLAYER_UUID` - A string that represents the UUID of the player in a singleplayer world.
        - `WORLD_GEN_SETTINGS` -> `OLD_WORLD_GEN_SETTINGS`
        - `writeLastPlayed` - Writes the last played player.
        - `writeVersionTag` - Writes the data version tag.
    - `ServerLevelData`
        - `setThundering`, `getRainTime`, `setRainTime`, `setThunderTime`, `getThunderTime`, `getClearWeatherTime`, `setClearWeatherTime` moved to `WeatherData`
        - `getWanderingTraderSpawnDelay`, `setWanderingTraderSpawnDelay`, `getWanderingTraderSpawnChance`, `setWanderingTraderSpawnChance`, `getWanderingTraderId`, `setWanderingTraderId` moved to `WanderingTraderData`
        - `getLegacyWorldBorderSettings`, `setLegacyWorldBorderSettings` replaced by `WorldBorder`
        - `getScheduledEvents` -> `MinecraftServer#getScheduledEvents`
        - `getGameRules` replaced by `GameRuleMap`
    - `WorldData`
        - `getCustomBossEvents`, `setCustomBossEvents` replaced by `CustomBossEvents`
        - `createTag` no longer takes in the `RegistryAccess`
        - `getGameRules` replaced by `GameRuleMap`
        - `getLoadedPlayerTag` -> `getSinglePlayerUUID`, not one-to-one
        - `endDragonFightData`, `setEndDragonFightData` replaced by `EnderDragonFight`
        - `worldGenOptions` replaced by `WorldGenSettings` saved data
- `net.minecraft.world.level.timers.TimerQueue` now extends `SavedData`
    - The constructor now takes in the `$Packed` events instead of the `TimerCallbacks` and `Stream` of event data
    - `store` replaced by `CODEC`, `TYPE`, `codec`
    - `loadEvent`, `storeEvent` replaced by `$Event$Packed#codec`
    - `$Event` is now a record
        - `$Packed` - The packed event data.
    - `$Packed` - The packed time queue.

## Even More Rendering Changes

### Materials and Dynamic Layer Selection

Block and item models now no longer specify what `RenderType` or `ChunkSectionLayer` they belong to. Instead, this is computed when loading the model, determing the associated layer for each quad. This means that `ItemBlockRenderTypes` is removed, with setting the `RenderType` for an item removed altogether.

To determine what layer a quad or face gets set to, the `Transparency` of the texture is computed. Specifically, it checks that, for the UV area mapped to the quad, if there are any pixels that are transparent (have an alpha of 0), or translucent (have an alpha that is not 0 or 255). For the `ChunkSectionLayer`, `ChunkSectionLayer#TRANSLUCENT` is used if there is a translucent pixel, else `CUTOUT` is used if there is a transparent pixel, else `SOLID`. For the item `RenderType`, `Sheets#translucentItemSheet` and `translucentBlockItemSheet` for block items are used if there is a translucent pixel, or `cutoutItemSheet` and `cutoutBlockItemSheet` for block items. The `Transparency` also affects using `MipmapStrategy#AUTO`, using `CUTOUT` instead of `MEAN` as the default if there is a transparent pixel.

One can influence a quad's `Transparency` through the `Material` texture defined by the model JSON. A `Material` specifies the texture's `sprite`, which represents the relative path to the texture, and optionally `force_translucent`, which forces any quad using this texture to use `Transparency#TRANSLUCENT` (transparent is false while translucent is true):

```json5
// For some model `examplemod:example_model`
// In: `assets/examplemod/models/example_model.json`
{
    "parent": "minecraft:block/template_glass_pane_post",
    "textures": {
        // A Material can be a simple texture reference
        // Points to `assets/minecraft/textures/block/glass_pane_top.png`
        "edge": "minecraft:block/glass_pane_top",
        // Or it can be an object
        "pane": {
            // The relative texture reference for faces using this key
            // Points to `assets/minecraft/textures/block/glass.png`
            "sprite": "minecraft:block/glass",
            // When true, sets all faces using this texture key to
            // always have a transparent pixel.
            "force_translucent": true
        }
    }
    // ...
}
```

This change also defines the rendering order, where all solid quads are rendered first, followed by cutout quads, and finally translucent quads, sorted by distance from the camera.

### Materials and Sprites

As you may have noticed, `Material`s were originally used to define some texture in an atlas. The addition of materials in texture JSONs have changed the naming of these classes. `Material`s now explicitly refer to texture references within model JSONs. This means that all references to the raw texture location have been replaced with `Material`, if unbaked, and `Material$Baked`, if baked. Additionally, the `SpriteGetter` is now `MaterialBaker`.

As for the original `Material`, these are now known as sprites, where `Material` is renamed to `SpriteId`, and `MaterialSet` is renamed to `SpriteGetter`.

### Quad Particle Layers

The `SingleQuadParticle$Layer`s have been split into `OPAQUE_*` and `TRANSLUCENT_*` layers, depending on if the particle texture used by the atlas contains a translucent pixel. Note that 'opaque' in this instance means cutout, where pixels with an alpha of less than 0.1 are discarded. If not creating a new layer, `$Layer#bySprite` can be used to determine what layer the particle texture should use.

```java
public class ExampleParticle extends SingleQuadParticle {

    private final SingleQuadParticle.Layer layer;

    public SingleQuadParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.layer = SingleQuadParticle.Layer.bySprite(sprite);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return this.layer;
    }
}
```

### Block Models

The pipeline for rendering individual block models outside of the general world context has been rewritten similarly to `ItemModel`s, where a 'block model' updates some render state, which then submits its elements for rendering. As such, most of the block model classes have been either rewritten or reorganized to a degree.

Due to the block model name being synonymous with the model JSONs in general, many classes were moved and rename to separate the model JSONs, from the block state JSONs, from the now block models. As such, geometry for models that originally had 'block' in the name were changed to 'cuboid': (e.g., `BlockModel` -> `CuboidModel`, `BlockModelWrapper` -> `CuboidItemModelWrapper`). Additionally, parts of the rendering process referring to the definitions within a block state JSON were changed from 'block' to 'block state' (e.g., `BlockModelPart` -> `BlockStateModelPart`, `BlockModelDefinition` -> `BlockStateModelDispatcher`). You can consider most of the 'block model' classes to be new, with those renamed replacing the `SpecialBlockModelRenderer` system.

The block model system starts from the `ModelManager` after all models and definitions are loaded and resolved, ready to be baked. Block models are loaded through `BuiltInBlockModels#crateBlockModels`, which links some `BlockState` to a `BlockModel$Unbaked`. Similarly to item models, the unbaked instance defines the properties of how the block model should be constructed. These are stored within `LoadedBlockModels`, to which they are then subsequently baked after all JSONs into `BlockModel`s via `bake`. This `BlockState` to `BlockModel` map is then stored within the `BlockModelSet`, ready to be queried through `get`, or more commonly through `BlockModelResolver#update`, which calls in the model set. Any models not defined lazily resolve to a wrapper around the `BlockStateModel`.

Vanilla provides six `BlockModel` implementations for common usage. There is `EmptyBlockModel`, which submit no elements, and as such renders nothing; and `BlockStateModelWrapper`, which wraps around and displays the associated `BlockStateModel`. Then, there are equivalents for changing the model based on some property switch (`SelectBlockModel`), a conditional `boolean` (`ConditionalBlockModel`), and composing multiple models together (`CompositeBlockModel`). Finally, there is the `SpecialBlockModelWrapper`, which submits its elements through the stored `SpecialModelRenderer`, the unified submitter between item and block models.

```java
// As the block model system is hardcoded through its in-code bootstrap,
// this example will assume there exists some method to get access to the
// `BuiltInBlockModels$Builder` builder.

// We will also assume we have some Block EXAMPLE_BLOCK_* to attach the models to.

// Regular block model
builder.put(
    // A factory that takes in the `BlockColors` and `BlockState` to return
    // a `BlockModel$Unbaked`.
    (colors, state) -> new BlockStateModelWrapper.Unbaked(
        // The state to get the `BlockStateModel` of.
        state,
        // The tint layers for the model.
        colors.getTintSources(state),
        // An optional transformation to apply to the `PoseStack` before
        // submitting the model.
        Optional.empty(new Transformation(new Matrix4f().translation(0.5f, 0.5f, 0.5f)))
    ),
    // The block to use this model for. Will loop through a construct one
    // per state.
    EXAMPLE_BLOCK_1
);

// Block model switched on some property
builder.put(
    (colors, state) -> new SelectBlockModel.Unbaked(
        // An optional transformation to apply to the `PoseStack` before
        // submitting the model.
        Optional.empty(),
        // A record containing the property to switch on, along with the
        // values when a specific block model should be selected.
        new SelectBlockModel.UnbakedSwitch<>(
            // The `SelectBlockModelProperty` to switch on. The property
            // value is determined from the `BlockState` and its `BlockDisplayContext`.
            (state, displayContext) -> state.getRenderShape(),
            // The list of cases to determine what `BlockModel` to use.
            List.of(
                new SelectBlockModel.SwitchCase<>(
                    // The list of values this model applies to.
                    List.of(RenderShape.INVISIBLE),
                    // The model to use when this property is met.
                    new EmptyBlockModel.Unbaked()
                )
            ),
            // An optional fallback if no switch case matches the state's
            // property.
            Optional.of(new EmptyBlockModel.Unbaked())
        )
    ),
    EXAMPLE_BLOCK_2
);

// Block model based on some conditional
builder.put(
    (colors, state) -> new ConditionalBlockModel.Unbaked(
        // An optional transformation to apply to the `PoseStack` before
        // submitting the model.
        Optional.empty(),
        // The `ConditionalBlockModelProperty` that determines the
        // `boolean` from the `BlockState`.
        BlockState::isSignalSource,
        // The model to display when the property returns `true`.
        new EmptyBlockModel.Unbaked(),
        // The model to display when the property returns `false`.
        new EmptyBlockModel.Unbaked()
    ),
    EXAMPLE_BLOCK_3
);

// A composite block model
builder.put(
    (colors, state) -> new CompositeBlockModel.Unbaked(
        // The first model to display.
        new EmptyBlockModel.Unbaked(),
        // The second model to display.
        new EmptyBlockModel.Unbaked(),
        // An optional transformation to apply to the `PoseStack` before
        // submitting the model.
        Optional.empty()
    ),
    EXAMPLE_BLOCK_4
);

// Special block model
builder.put(
    (colors, state) -> new SpecialBlockModelWrapper.Unbaked(
        // The unbaked `SpecialModelRenderer` used to submit elements for the
        // model.
        new BellSpecialRenderer.Unbaked(),
        // An optional transformation to apply to the `PoseStack` before
        // submitting the model.
        Optional.empty()
    ),
    EXAMPLE_BLOCK_5
);
```

During the feature submission process, block models are handled through the `BlockModelResolver` and `BlockModelRenderState`. This is similar to how other render states work. First, `BlockModelResolver#update` sets up the `BlockModelRenderState`. Setup is handled through either the basic path -- `BlockModelRenderState#setupModel`, add the model parts to the returned list, then `setupTints`, or through `setupSpecialModel` for the special renderers. Then, the render state submits its elements for rendering through `BlockModelRenderState#submit`. The render state also provides `submitOnlyOutline` which uses the outline render type, and `submitWithZOffset` which uses the sold entity forward Z-offset render type.

```java
// BlockEntity example
public class ExampleRenderState extends BlockEntityRenderState {
    // Hold the render state.
    public final BlockModelRenderState exampleBlock = new BlockModelRenderState();
}

public class ExampleRenderer implements BlockEntityRenderer<ExampleBlockEntity, ExampleRenderState> {

    // The display context for use in the block entity renderer.
    public static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blockResolver;

    public ExampleRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
        // Get the model resolver.
        this.blockResolver = ctx.blockModelResolver();
    }

    @Override
    public void extractRenderState(ExampleBlockEntity blockEntity, ExampleRenderState state, float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);

        // Update the model state.
        this.blockResolver.update(state.exampleBlock, Blocks.DIRT.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
    }

    @Override
    public void submit(ExampleRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);

        // Submit the model state for rendering.
        state.exampleBlock.submit(
            // The current pose stack,
            pose,
            // The node collector.
            collector,
            // The light coordinates.
            state.lightCoords,
            // The overlay coordinates.
            OverlayTexture.NO_OVERLAY,
            // The outline color.
            0
        );


    }
}

// Entity example
public class ExampleRenderState extends EntityRenderState {
    // Hold the render state.
    public final BlockModelRenderState exampleBlock = new BlockModelRenderState();
}

public class ExampleRenderer extends EntityRenderer<ExampleEntity, ExampleRenderState> {

    // The display context for use in the entity renderer.
    public static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blockResolver;

    public ExampleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // Get the model resolver.
        this.blockResolver = ctx.getBlockModelResolver();
    }

    @Override
    public void extractRenderState(ExampleEntity entity, ExampleRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        // Update the model state.
        this.blockResolver.update(state.exampleBlock, Blocks.DIRT.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
    }

    @Override
    public void submit(ExampleRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);

        // Submit the model state for rendering.
        state.exampleBlock.submit(
            // The current pose stack.
            pose,
            // The node collector.
            collector,
            // The light coordinates.
            state.lightCoords,
            // The overlay coordinates.
            OverlayTexture.NO_OVERLAY,
            // The outline color.
            state.outlineColor
        );
    }
}
```

### Block Tint Sources

`BlockColor` has been completely replaced by `BlockTintSource`, which sets the ARGB tint of a particular index based on the desired context. There are three contexts a tint source can provide:

- `color` for the general context, used by `BlockModel`s
- `colorInWorld` for the world context, used by `ModelBlockRenderer#tesselateBlock`
- `colorAsTerrainParticle` for the particle context, used by the falling dust and terrain particle

In addition, `BlockTintSource` provides a `relevantProperties` if the `Property`s of a `BlockState` are used to determine what color to tint. This is used by the `LevelRenderer` to determine whether a change in state requires a model to be re-rendered.

`BlockTintSource`s are still registered to `BlockColors` via `register`, taking in a list of sources followed by the vararg of blocks. The `tintindex` specfied in the model JSON is used to index into the tint source list.

```java
// Assume access to BlockColors colors
colors.register(
    // The list of tints to apply to some block model.
    List.of(
        // "tintindex": 0
        (state) -> 0xFFFF0000,
        // "tintindex": 1
        new BlockTintSource() {

            @Override
            public int color(BlockState state) {
                return 0xFF00FF00;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return 0xFF0000FF;
            }
        }
    ),
    // The blocks these tint sources will apply to.
    EXAMPLE_BLOCK_1
);
```

### Removing the Old Block and Item Renderers

Since `ItemModel`s and `BlockModel` are now fully handled through their own feature submission pipeline, `BlockRenderDispatcher` and `ItemRenderer` have been completely removed, replaced by the corresponding systems.

### Object Definition Transformations

`ItemModel`s and `BlockModel`s can now take in an optional `Transformation`, which transforms how the model should be displayed. As such, the `$Unbaked#bake` methods now take in the parent `Matrix4fc` transformation, to which the transformation is multiplied to via `Transformation#compose`. For item models, this is known as a local transform separate from the model JSON item transform. The local transforms are always applied after the item transform.

Note that adding support for transformations should always be done through `Transformation#compose` as the matrices passed around are mutable by nature. Assume any custom methods not performed through a vanilla interface should copy before performing any modifications.

### Quad Instance

The brightness / tint color, lightmap, and overlay coordinates have been consolidated into a single object: `QuadInstance`. The mutable class sets its values through its associated `set*` methods, and can pull the information for each quad vertex via the `get*` methods. Brightness and tinting are stored together as the color, rather them being two separate values.

`QuadInstance` does not replace all usecases, such as when adding a single vertex. It only updates methods for the `VertexConsumer`, splitting `putBulkData` into `putBlockBakedQuad` for quads in block models, and `putBakedQuad` for all other uses.

In addition, mmany methods used to upload `BakedQuad`s to a buffer now take in a `BlockQuadOutput`. This has the same parameters as `VertexConsumer#putBlockBakedQuad`, and was added due to the section renderer uploading to a newly allocated `BufferBuilder` for use with the uber buffer.

### Gui Extractor

GUI classes methods have gone through a massive renaming scheme, indicating that the submitted elements are 'extracted' into a general tree to be submitted and then rendered. As such, methods that began with `draw*` or `render*` are now prefixed with `extract*`, and potentially suffixed with `*RenderState` (e.g., `Renderable#render` -> `extractRenderState`, `AbstractContainerScreen#renderLabels` -> `extractLabels`, `AbstractWidget#renderWidget` -> `extractWidgetRenderState`). Some shorthands were also expanded, either by renamining or replacing the method with another (e.g., `AbstractContainerScreen#renderBg` replaced by `Screen#extractBackground`).

`GuiGraphics` was also renamed to `GuiGraphicsExtractor` in the same fashion. The methods follow similar patterns to the rest of the GUI changes (e.g. `hline` -> `horizontalLine`), except that `draw*`, `render*`, and `submit*` prefixes along with `*RenderState` suffixes are removed (e.g. `renderOutline` -> `outline`, `submitEntityRenderState` -> `entity`). The only rename is that `*String*` method names are replaced with `*Text*`.

### Fluid Models

Defining a fluid's textures and tint has now been moved out of the `FluidRenderer` (previously `LiquidBlockRenderer`) and into its own separate `FluidModel` record. Initially, given the small number of fluids, the unbaked variants (`FluidModel$Unbaked`) are stored as constants. Then, after `BlockModel` have been baked, the fluid models are baked via `FluidStateModelSet#bake`, linking a `Fluid` to its `FluidModel`. This map is then stored within the `FluidStateModelSet`, ready to be queried through `get`.

A `FluidModel$Unbaked` has four arguments: three `Material`s for the still, flowing, and optional overlay textures; and one for the `BlockTintSource`. The tint is obtained via `BlockTintSource#colorInWorld`. During the baking process, it will determine the `ChunkSectionLayer` based on the transparency of the provided materials.

```java
// As the fluid model system is hardcoded within its baking, this example
// will assume there exists some method to get modifiable access to the
// `Map<Fluid, FluidModel>` fluidModels returned by `FluidStateModelSet#bake`.

// We will also assume we have some Fluid EXAMPLE_FLUID* to attach the models to.

FluidModel.Unbaked exampleFluidModel = new FluidModel.Unbaked(
    // The texture for the still fluid.
    new Material(
        // The relative identifier for the texture.
        // Points to `assets/examplemod/textures/block/example_fluid_still.png`
        Identifier.fromNamespaceAndPath("examplemod", "block/example_fluid_still"),
        // When true, sets all faces using this texture key to
        // always have a transparent pixel.
        true
    ),
    // The texture for the flowing fluid.
    new Material(Identifier.fromNamespaceAndPath("examplemod", "block/example_fluid_flowing")),
    // If not null, the texture for the overlay when the side of the fluid is
    // occluded by a `HalfTransparentBlock` or `LeavesBlock`.
    null,
    // If not null, the tint source to apply to the fluid's texture when in
    // the world.
    null
);

// Assume we have access to the `MaterialBaker` materials.

FluidModel exampleBakedFluidModel = exampleFluidModel.bake(
    // The baker to grab the atlas sprites for the materials.
    materials,
    // A supplied debug name to properly report which models have
    // missing textures.
    () -> "examplemod:example_fluid_model"
);

fluidModels.put(
    // The fluid the model should be used by.
    EXAMPLE_FLUID,
    // The baked fluid model.
    exampleBakedFluidModel
);
fluidModels.put(EXAMPLE_FLUID_FLOWING, exampleBakedFluidModel);
```

### Name Tag Offsets

`EntityRenderer#submitNameTag` has been renamed to `submitNameDisplay`, now optionally taking in the y offset from the name tag attachment.

### Tint Getter

`BlockAndTintGetter` is now a client only interface attached to the `ClientLevel`. It previous uses were replaced with `BlockAndLightGetter` -- which `BlockAndTintGetter` now extends -- with the tint and lighting directions stripped.

### Pipeline Depth and Color

Depth and color methods defined in the `RenderPipeline` have been consolidated into two state objects.

The `DepthTestFunction`, write `boolean`, and the bias `float`s are now stored in `DepthStencilState`. `DepthTestFunction` has been replaced with a more generic `CompareOp`, which defines how to compare two numbers. Each function has an simple equivalent, with `NO_DEPTH_TEST` replaced by `CompareOp#ALWAYS_PASS`. The depth information can be added to the pipeline via `RenderPipeline$Builder#withDepthStencilState`.

The optional `BlendFunction` and color / alpha `boolean`s are now stored in `ColorTargetState`. The `boolean`s are consolidated into an `int` using the lower four bits as flags: `1` is red, `2` is green, `4` is blue, and `8` is alpha. The color `boolean` uses `7` for red, green, and blue; the alpha `boolean` uses `8`; while both combined use `15`. The `LopicOp` is removed entirely. The color information can be added to the pipeline via `RenderPipeline$Builder#withColorTargetState`.

```java
public static final RenderPipeline EXAMPLE_PIPELINE = RenderPipeline.builder()
    .withLocation(ResourceLocation.fromNamespaceAndPath("examplemod", "pipeline/example"))
    .withVertexShader(ResourceLocation.fromNamespaceAndPath("examplemod", "example"))
    .withFragmentShader(ResourceLocation.fromNamespaceAndPath("examplemod", "example"))
    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
    .withShaderDefine("ALPHA_CUTOUT", 0.5)
    .withSampler("Sampler0")
    .withUniform("ModelOffset", UniformType.VEC3)
    .withUniform("CustomUniform", UniformType.INT)
    .withPolygonMode(PolygonMode.FILL)
    .withCull(false)
    // Sets the color target when writing the buffer data.
    .withColorTargetState(new ColorTargetState(
        // Specifies the functions to use when blending two colors with alphas together.
        // Made up of the `SourceFactor` and `DestFactor`.
        // First two are for RGB, the last two are for alphas.
        // If the optional is empty, then blending is disabled.
        Optional.of(BlendFunction.TRANSLUCENT),
        // The mask determining what colors to write to the buffer.
        // Represented as a four bit value
        // 0001 - Write the red channel..
        // 0010 - Write the green channel.
        // 0100 - Write the blue channel.
        // 1000 - Write the alpha channel.
        ColorTargetState.WRITE_RED | ColorTargetState.WRITE_GREEN | ColorTargetState.WRITE_BLUE | ColorTargetState.WRITE_ALPHA
    ))
    // Sets the depth stencil when writing the buffer data.
    .withDepthStencilState(new DepthStencilState(
        // Sets the depth test function to use when rendering objects at varying
        // distances from the camera.
        // Values:
        // - ALWAYS_PASS           (GL_ALWAYS)
        // - LESS_THAN             (GL_LESS)
        // - LESS_THAN_OR_EQUAL    (GL_LEQUAL)
        // - EQUAL                 (GL_EQUAL)
        // - NOT_EQUAL             (GL_NOTEQUAL)
        // - GREATER_THAN_OR_EQUAL (GL_GEQUAL)
        // - GREATER_THAN          (GL_GREATER)
        // - NEVER_PASS            (GL_NEVER)
        CompareOp.LESS_THAN_OR_EQUAL,
        // Whether to mask writing values to the depth buffer
        false,
        // The scale factor used to calculate the depth values for the polygon.
        0f,
        // The unit offset used to calculate the depth values for the polygon.
        0f
    ))
    .build()
;
```

### Blaze3d Backends

`CommandEncoder`, `GpuDevice`, and `RenderPassBackend` has been split into the `*Backend` interface, which functions similarly to the previous interface, and the wrapper class, which holds the backend and provides delegate calls, performing any validation necessary. The `*Backend` interfaces now explicitly perform the operation without checking whether the operation is valid.

### Solid and Translucent Features

Feature rendering has been further split into two passes: one for solid render types, and one for translucent render types. As such, most `render` methods now have a `renderSolid` and `renderTranslucent` method, respectively. Those which only render solid or translucent data only have one of the methods.

### Camera State

The `Camera` has been updated similarly to other render state implementations where the camera is extracted to the `CameraRenderState` during `GameRenderer#renderLevel`, and that is passed around with the data required to either submit and render elements.

Due to this change, `FogRenderer#setupFog` now returns the `FogData`, containing all the information needed to render the fog, instead of just its color, and storing that in `CameraRenderState#fogData`.

- Some shaders within `assets/minecraft/shaders/core` now use `texture` over `texelFetch`
    - `entity.vsh`
    - `item.vsh`
    - `rendertype_leash.vsh`
    - `rendertype_text.vsh`
    - `rendertype_text_background.vsh`
    - `rendertype_text_intensity.vsh`
- `assets/minecraft/shaders/core`
    - `block.vsh#minecraft_sample_lightmap` -> `sample_lightmap.glsl#sample_lightmap`
    - `rendertype_crumbling` no longer takes in `texCoord2` (lightmap)
    - `rendertype_entity_alpha`, `rendertype_entity_decal` merged into `entity.fsh` using a `DissolveMaskSampler`
    - `rendertype_item_entity_translucent_cull` -> `item`, not one-to-one
    - `rendertype_translucent_moving_block` is removed
        - This now uses the the shaders provided by the `ChunkSectionLayer`
- `com.mojang.blaze3d`
    - `GLFWErrorCapture` - Captures errors during a GL process. 
    - `GLFWErrorScope` - A closable that defines the scope of the GL errors to capture.
- `com.mojang.blaze3d.opengl`
    - `GlProgram#BUILT_IN_UNIFORMS`, `INVALID_PROGRAM` are now final
    - `GlBackend` - A GPU backend for OpenGL.
    - `GlCommandEncoder` now implements `CommandEncoderBackend` instead of `CommandEncoder`, the class now package-private
        - `getDevice` is removed
    - `GlConst#toGl` now takes in a `CompareOp` instead of the `DepthTestFunction`
    - `GlDevice` now implements `GpuDeviceBackend` instead of `GpuDevice`, the class now package-private
        - The constructor now takes in a `GpuDebugOptions` containing the log level, whether to use synchronous logs, and whether to use debug labels instead of those parameters being passed in directly
    - `GlRenderPass` now implements `RenderPassBackend` instead of `RenderPass`, the class now package-private
        - The constructor now takes in the `GlDevice`
    - `GlStateManager#_colorMask` now takes an `int` for the color mask instead of four `boolean`s
- `com.mojang.blaze3d.platform`
    - `ClientShutdownWatchdog` now takes in the `Minecraft` instance
    - `DebugMemoryUntracker#untrack` is removed
    - `GLX#make(T, Consumer)` is removed
    - `NativeImage`
        - `computeTransparency` - Returns whether there is at least one transparent or translucent pixel in the image.
            - This crashes if the image's area is greater than 512MiB, or 2GiB with color data.
        - `isClosed` - Whether the image is closed or deallocated.
    - `Transparency` - An object of whether some image has a translucent and/or transparent pixel.
    - `Window` now takes the `GpuBackend` instead of the `ScreenManager`
        - `createGlfwWindow` - Directly creates the GLFW window with the provided settings.
        - `updateDisplay` -> `updateFullscreenIfChanged`, not one-to-one
        - `isResized`, `resetIsResized` - Handles whether the window has been resized.
        - `backend` - Returns the `GpuBackend`.
        - `$WindowInitFailed` constructor is now `public` from `private`
    - `WindowEventHandler`
        - `resizeDisplay` -> `resizeGui`
        - `setWindowActive` replaced by `Window#isFocused`
- `com.mojang.blaze3d.pipeline`
    - `ColorTargetState` - A record containing the blend function and mask for the color.
    - `DepthStencilState` - A record containing the data for the depth stencil.
    - `RenderPipeline` now takes in the `ColorTargetState` instead of the optional `BlendFunction`, color and alpha `boolean`s, and `LogicOp`; and the `DepthStencilState` instead of the `DepthTestFunction`, depth `boolean`, and bias `float`s
        - `getDepthTestFunction`, `isWriteDepth`, `getDepthBiasScaleFactor`, `getDepthBiasConstant` -> `getDepthStencilState`, not one-to-one
        - `getColorLogic`, `getBlendFunction`, `isWriteColor`, `isWriteAlpha` -> `getColorTargetState`, not one-to-one
        - `$Builder`
            - `withDepthTestFunction`, `withDepthWrite`, `withDepthBias` -> `withDepthStencilState`, not one-to-one
            - `withBlend`, `withColorWrite`, `withColorLogic` -> `withColorTargetState`, not one-to-one
        - `$Snippet` now takes in the `ColorTargetState` instead of the optional `BlendFunction`, color and alpha `boolean`s, and `LogicOp`; and the `DepthStencilState` instead of the `DepthTestFunction` and depth `boolean`
- `com.mojang.blaze3d.platform`
    - `BackendOptions` - A configuration for initializing the backend with.
    - `DepthTestFunction` -> `CompareOp`, not one-to-one
        - `NO_DEPTH_TEST` -> `CompareOp#ALWAYS_PASS`
        - `EQUAL_DEPTH_TEST` -> `CompareOp#EQUAL`
        - `LEQUAL_DEPTH_TEST` -> `CompareOp#LESS_THAN_OR_EQUAL`
        - `LESS_DEPTH_TEST` -> `CompareOp#LESS_THAN`
        - `GREATER_DEPTH_TEST` -> `CompareOp#GREATER_THAN`
    - `GLX`
        - `_initGlfw` now takes in the `BackendOptions`
        - `glfwBool` - `1` if `true`, `0` if `false`.
    - `LogicOp` enum is removed
- `com.mojang.blaze3d.shaders.GpuDebugOptions` - The debug options for the GPU pipeline.
- `com.mojang.blaze3d.systems`
    - `BackendCreationException` - An exception thrown when the GPU backend couldn't be created.
    - `CommandEncoder` -> `CommandEncoderBackend`
        - The original interface is now a class wrapper around the interface, delegating to the backend after performing validation checks
    - `GpuBackend` - An interface responsible for creating the used GPU device and window to display to.
    - `GpuDevice` -> `GpuDeviceBackend`
        - The original interface is now a class wrapper around the interface, delegating to the backend after performing validation checks
        - `setVsync` - Sets whether VSync is enabled.
        - `presentFrame` - Swaps the front and back buffers of the window to display the present frame.
        - `isZZeroToOne` - Whether the 0 to 1 Z range is used instead of -1 to 1.
    - `RenderPass` -> `RenderPassBackend`
        - The original interface is now a class wrapper around the interface, delegating to the backend after performing validation checks
    - `RenderSystem`
        - `pollEvents` is now public
        - `flipFrame` no longer takes in the `Window`
        - `initRenderer` now only takes in the `GpuDevice`
        - `limitDisplayFPS` -> `FramerateLimiter#limitDisplayFPS`
        - `initBackendSystem` now takes in the `BackendOptions`
- `com.mojang.blaze3d.vertex`
    - `DefaultVertexFormat#BLOCK` no longer takes in the normal vector
    - `PoseStack#mulPose`, `$Pose#mulPose` now has an overload that takes in the `Transformation`
    - `QuadInstance` - A class containing the color, light coordinates, and overlay of a quad.
    - `TlsfAllocator` - A two-level segregate fit allocator for dynamic memory allocation.
    - `UberGpuBuffer` - A buffer for uploading dynamically sized data to the GPU, used for chunk section layers.
    - `VertexConsumer`
        - `putBulkData` -> `putBlockBakedQuad`, `putBakedQuad`; not one-to-one
            - Brightness `float` array, color `float`s, lightmap `int` array, and overlay `int` are replaced with `QuadInstance`
            - `putBlockBakedQuad` replaces the `PoseStack$Pose` with the XYZ `float` block position
        - `addVertex(PoseStack$Pose, Vector3f)` -> `addVertex(PoseStack$Pose, Vector3fc)`
        - `setNormal(PoseStack$Pose, Vector3f)` -> `setNormal(PoseStack$Pose, Vector3fc)`
- `com.mojang.math`
    - `MatrixUtil`
        - `checkPropertyRaw` is now `public` from `private`
        - `isOrthonormal` is removed
    - `Transformation`
        - `IDENTITY` is now `public` from `private`
            - Replaces `identity` method
        - `getTranslation` -> `translation`
        - `getLeftRotation` -> `leftRotation`
        - `getScale` -> `scale`
        - `getRightRotation` -> `rightRotation`
        - `compose` - Applies the transformation to the given matrix, if present.
- `net.minecraft.SharedConstants`
    - `DEBUG_DUMP_INTERPOLATED_TEXTURE_FRAMES` is removed
    - `DEBUG_PREFER_WAYLAND` - When true, prevents the platform initialization hint from being set to X11 if both Wayland and X11 are supported.
- `net.minecraft.client`
    - `Camera`
        - `BASE_HUD_FOV` - The base hud field-of-view.
        - `setup` -> `update`, not one-to-one
        - `extractRenderState` - Extract the state of the camera.
        - `getFov` - Gets the field-of-view.
        - `getViewRotationMatrix` - Gets the matrix for a projection view.
        - `setEntity` - Sets the entity the camera is attached to.
        - `getNearPlane` now takes in the `float` field-of-view
        - `panoramicForwards` - The forward vector when in panorama mode.
        - `getPartialTickTime` is removed
        - `setLevel` - Sets the level the camera is in.
        - `getCameraEntityPartialTicks` - Gets the partial tick based on the state of the entity.
    - `DeltaTracker#advanceTime` replaced by `advanceGameTime` when the `boolean` was `true`, and `advanceRealTime`
        - `advanceGameTime`, `advanceRealTime` were previously `private`, now `public`
    - `FramerateLimiter` - A utility for limiting the framerate of the client.
    - `Minecraft`
        - `noRender` is removed
        - `useAmbientOcclusion` is removed
        - `getBlockRenderer` is removed
        - `getItemRenderer` is removed
    - `Options`
        - `getCloudsType` -> `getCloudStatus`
        - `exclusiveFullscreen` - When `true`, fullscreen mode takes full control of the monitor.
- `net.minecraft.client.color.block`
    - `BlockColor` replaced by `BlockTintSource`, not one-to-one
        - `getColor` -> `colorInWorld`, `colorAsTerrainParticle`; not one-to-one
    - `BlockColors`
        - `getColor` replaced by `getTintSources`, `getTintSource`; not one-to-one
        - `register` now takes in a list of `BlockTintSource`s instead of a `BlockColor`
    - `BlockTintSource` - A source for how to tint a `BlockState` in isolation or with context.
    - `BlockTintSources` - Utilites for common block tint sources.
- `net.minecraft.client.data.models`
    - `BlockModelGenerators`
        - `createSuffixedVariant` now takes in a function of `Material` to `TextureMapping` for the textures instead of just an `Identifier`
        - `createAirLikeBlock` now takes in a `Material` instead of an `Identifier` for the particle texture
        - `generateSimpleSpecialItemModel` now takes in an optional `Transformation`
        - `createChest` now has an overload that takes in the `MutiblockChestResources` textures
    - `ItemModelGenerators#generateLayeredItem` now takes in `Material`s instead of `Identifier`s for the textures
- `net.minecraft.client.data.models.model`
    - `ItemModelUtils`
        - `specialModel` now has overloads that take in the `Transformation`
        - `conditional` now has overloads that take in the `Transformation`
        - `select` now has an overload that takes in the `Transformation`
        - `selectBlockItemProperty` now has an overload that takes in the `Transformation`
    - `TexturedModel#createAllSame` now takes in a `Material` instead of an `Identifier` for the texture
    - `TextureMapping`
        - `put`, `putForced` now take in a `Material` instead of an `Identifier` for the texture
        - `get` now returns a `Material` instead of an `Identifier` for the texture
        - `copyAndUpdate` now takes in a `Material` instead of an `Identifier` for the texture
        - `updateSlots` - Replaces all slots using the provided mapper function.
        - `forceAllTranslucent` - Sets the force translucency flag for all material textures.
        - `defaultTexture`, `cube`, `cross`, `plant`, `rail`, `wool`, `crop`, `singleSlot`, `particle`, `torch`, `cauldron`, `layer0` now take in a `Material` instead of an `Identifier` for the texture
        - `column`, `door`, `layered` now take in `Material`s instead of `Identifier`s for the textures
        - `getBlockTeture`, `getItemTexture` now return a `Material` instead of an `Identifier` for the texture
- `net.minecraft.client.entity.ClientAvatarEntity#belowNameDisplay` -> `Entity#belowNameDisplay`
- `net.minecraft.client.gui`
    - `Font`
        - `drawInBatch`, `drawInBatch8xOutline` now take in a `Matrix4fc` instead of a `Matrix4f` for the pose
        - `$GlyphVisitor#forMultiBufferSource` now takes in a `Matrix4fc` instead of a `Matrix4f` for the pose
    - `Gui`
        - `render*` methods have been renamed to `extract*`
        - `render` -> `extractRenderState`
        - `$RenderFunction` interface is removed
    - `GuiGraphics` -> `GuiGraphicsExtractor`
        - `hLine` -> `horizontalLine`
        - `vLine` -> `verticalLine`
        - `renderOutline` -> `outline`
        - `drawCenteredString` -> `centeredText`
        - `drawString` -> `text`
        - `drawStringWithBackdrop` -> `textWithBackdrop`
        - `renderItem` -> `item`
        - `renderFakeItem` -> `fakeItem`
        - `renderItemDecorations` -> `itemDecorations`
        - `submitMapRenderState` -> `map`
        - `submitEntityRenderState` -> `entity`
        - `submitSkinRenderState` -> `skin`
        - `submitBookModelRenderState` -> `book`
        - `submitBannerPatternRenderState` -> `bannerPattern`
        - `submitSignRenderState` -> `sign`
        - `submitProfilerChartRenderState` -> `profilerChart`
        - `renderTooltip` -> `tooltip`
        - `renderComponentHoverEffect` -> `componentHoverEffect`, now `private` instead of `public`
- `net.minecraft.client.gui.components`
    - Most methods that begin with `render*` or `draw*` have been renamed to either `extract*` or `extract*RenderState` depending on usage.
    - `AbstractWidget#renderWidget` -> `extractWidgetRenderState`
    - `DebugScreenOverlay#render3dCrosshair` now takes in the `CameraRenderState` instead of the `Camera`, and the gui scale `int`
    - `LogoRenderer#renderLogo` -> `extractRenderState`
    - `PlayerFaceRenderer` -> `PlayerFaceExtractor`
        - `draw` -> `extractRenderState`
    - `Renderable#render` -> `extractRenderState`
    - `StringWidget#clipText` -> `ComponentRenderUtils#clipText`
    - `TextCursorUtils#draw*` -> `extract*`
- `net.minecraft.client.gui.components.debugchart`
    - `AbstractDebugChart`
        - `drawChart` -> `extractRenderState`
        - `drawDimensions` -> `extractSampleBars`
        - `drawMainDimension` -> `extractMainSampleBar`
        - `drawAdditionalDimensions` -> `extractAdditionalSampleBars`
        - `renderAdditionalLinesAndLabels` -> `extractAdditionalLinesAndLabels`
        - `drawStringWithShade` -> `extractStringWithShade`
    - `ProfilerPieChart#render` -> `extractRenderState`
- `net.minecraft.client.gui.components.spectator.SpectatorGui#render*` -> `extract*`
- `net.minecraft.client.gui.components.toasts`
    - `NowPlayingToast#renderToast` -> `extractToast`
    - `Toast#render` -> `extractRenderState`
    - `ToastManager`, `$ToastInstance#render` -> `extractRenderState`
    - `TutorialToast$Icons#render` -> `extractRenderState`
- `net.minecraft.client.gui.contextualbar.ContextualBarRenderer`
    - `renderBackground` -> `extractBackground`
    - `render` -> `extractRenderState`
    - `renderExperienceLevel` -> `extractExperienceLevel`
- `net.minecraft.client.gui.font`
    - `PlainTextRenderable#renderSprite` now takes in a `Matrix4fc` instead of a `Matrix4f` for the pose
    - `TextRenderable#render` now takes in a `Matrix4fc` instead of a `Matrix4f` for the pose
- `net.minecraft.client.gui.render`
    - `DynamicAtlasAllocator` - An allocator for handling a dynamically sized texture atlas.
    - `GuiItemAtlas` - An atlas for all items displayed in a user interface.
    - `GuiRenderer#incrementFrameNumber` -> `endFrame`, not one-to-one
- `net.minecraft.client.gui.render.state.*` -> `.client.rendererer.state.gui.*`
    - `GuiItemRenderState` no longer takes in the `String` name
        - `name` is removed
- `net.minecraft.client.gui.render.state.pip.*` -> `.client.rendererer.state.gui.pip.*`
- `net.minecraft.client.gui.screens`
    - `LevelLoadingScreen#renderChunks` -> `extractChunksForRendering`
    - `Screen`
        - `renderWithTooltipAndSubtitles` -> `extractRenderStateWithTooltipAndSubtitles`
        - `renderBackground` -> `extractBackground`
        - `renderBlurredBackground` -> `extractBlurredBackground`
        - `renderPanorama` -> `extractPanorama`
        - `renderMenuBackground` -> `extractMenuBackground`
        - `renderMenuBackgroundTexture` -> `extractMenuBackgroundTexture`
        - `renderTransparentBackground` -> `extractTransparentBackground`
- `net.minecraft.client.gui.screens.advancements`
    - `AdvancementTab#draw*` -> `extract*`
    - `AdvancementTabType`
        - `draw` -> `extractRenderState`
        - `drawIcon` -> `extractIcon`
    - `AdvancementWidget`
        - `draw` -> `extractRenderState`
        - `draw*` -> `extract*`
- `net.minecraft.client.gui.screens.inventory`
    - Most methods that begin with `render*` or `draw*` have been renamed to either `extract*` or `extract*RenderState` depending on usage.
    - `AbstractContainerScreen`
        - `renderContents` -> `extractContents`
        - `renderCarriedItem` -> `extractCarriedItem`
        - `renderSnapbackItem` -> `extractSnapbackItem`
        - `renderSlots` -> `extractSlots`
        - `renderTooltip` -> `extractTooltip`
        - `renderLabels` -> `extractLabels`
        - `renderBg` replaced by `Screen#extractBackground`
        - `renderSlot` -> `extractSlot`
    - `AbstractMountInventoryScreen#drawSlot` -> `extractSlot`
    - `AbstractSignEditScreen#renderSignBackground` -> `extractSignBackground`
    - `CyclingSlotBackground#render` -> `extractRenderState`
    - `EffectsInInventory#render` -> `extractRenderState`
    - `InventoryScreen#renderEntityInInventoryFollowsMouse` -> `extractEntityInInventoryFollowsMouse`
    - `ItemCombinerScreen#renderErrorIcon` -> `extractErrorIcon`
- `net.minecraft.client.gui.screens.inventory.tooltip`
    - `ClientTooltipComponent`
        - `renderText` -> `extractText`
        - `renderImage` -> `extractImage`
    - `TooltipRenderUtil#renderTooltipBackground` -> `extractTooltipBackground`
- `net.minecraft.client.gui.screens.options`
    - `DifficultyButtons` is now a record that takes in the `LayoutElement`, `CycleButton` for the difficulty, the `LockIconButton`, and the current `Level`
        - `create` now takes in the `Level` and returns the `DifficultyButtons` instead of a `LayoutElement`
        - `refresh` - Sets the data of the held button components.
    - `HasDifficultyReaction` - An interface that responds to when the difficulty has changed.
    - `OptionsScreen` now implements `HasDifficultyReaction`
    - `WorldOptionsScreen` now implements `HasDifficultyReaction`
        - The constructor now takes in the `Level`
- `net.minecraft.client.gui.screens.recipebook`
    - `GhostSlots`
        - `render` -> `extractRenderState`
        - `renderTooltip` -> `extractTooltip`
    - `RecipeBookComponent#render*` -> `extract*`
    - `RecipeBookPage`
        - `render` -> `extractRenderState`
        - `renderTooltip` -> `extractTooltip`
- `net.minecraft.cilent.gui.screens.reporting.ChatSelectionScreen$ChatSelectionList#renderItem` -> `extractItem`
- `net.minecraft.client.gui.screens.worldselection.AbstractGameRulesScreen$GameRuleEntry#renderLabel` -> `extractLabel`
- `net.minecraft.client.gui.spectator.SpectatorMenuItem#renderIcon` -> `extractIcon`
- `net.minecraft.client.model.Model#renderType` now has an overload that returns the passed in function
- `net.minecraft.client.model.object.book.BookModel$State` no longer takes in the animation pos, and moves the open `float` to the first parameter
    - `forAnimation` - Gets the current state of the animation for the book based on the progress.
- `net.minecraft.client.model.object.statue.CopperGolemStatueModel` now uses `Unit` for the generic instead of `Direction`
- `net.minecraft.client.multiplayer.ClientLevel` now implements `BlockAndTintGetter`
    - `update` - Updates the lighting of the level.
- `net.minecraft.client.multiplayer.chat.GuiMessageTag$Icon#draw` -> `extractRenderState`
- `net.minecraft.client.particle`
    - `Particle#getLightColor` -> `getLightCoords`
    - `SimpleVerticalParticle` - A particle that moves vertically.
    - `SingleQuadParticle$Layer`
        - `TERRAIN` -> `OPAQUE_TERRAIN`, `TRANSLUCENT_TERRAIN`
        - `ITEMS` -> `OPAQUE_ITEMS`, `TRANSLUCENT_ITEMS`
        - `bySprite` - Gets the layer from the atlas sprite.
- `net.minecraft.client.renderer`
    - `CachedOrthoProjectionMatrixBuffer`, `CachedPerspectiveProjectionMatrixBuffer`, `PerspectiveProjectionMatrixBuffer` -> `ProjectionMatrixBuffer` with sometimes `Projection`, not one-to-one
    - `CloudRenderer` now takes in the cloud range `int`
    - `CubeMap` no longer takes in the `Minecraft` instance
    - `GameRenderer` now takes in the `ModelManager` instead of the `BlockRenderDispatcher`
        - `PROJECTION_Z_NEAR` -> `Camera#PROJECTION_Z_NEAR`
        - `setPanoramicScreenshotParameters`, `getPanoramicScreenshotParameters` -> `Camera#enablePanoramicMode`, `disablePanoramicMode`; not one-to-one
        - `isPanoramicMode` -> `Camera#isPanoramicMode`
        - `getProjectionMatrix` -> `Camera#getViewRotationProjectionMatrix`, not one-to-one
        - `updateCamera` -> `Camera#update`, not one-to-one
        - `getRenderDistance` is removed
        - `cubeMap` -> `GuiRenderer#cubeMap`, now `private` from `protected`
        - `getDarkenWorldAmount` -> `getBossOverlayWorldDarkening`
        - `lightTexture` -> `lightmap`, `levelLightmap`; not one-to-one
        - `getLevelRenderState` replaced by `getGameRenderState`, returning the `GameRenderState` instead of the `LevelRenderState`
        - `pick` -> `Minecraft#pick`, now `private` from `public`
        - `render` split between `update`, `extract`, and `render`; with the `boolean` now taking in whether to advance the game time rather than render the level
    - `GlobalSettingsUniform` now takes in the `Vec3` camera position instead of the main `Camera` itself
    - `ItemBlockRenderTypes` is removed
        - `getChunkRenderType`, `getMovingBlockRenderType` now stored within `BakedQuad$SpriteInfo`
        - `getRenderLayer(FluidState)` -> `FluidModel#layer`, not one-to-one
        - `setCutoutLeaves` is removed
            - This should be obtained directly from the options
    - `MultiblockChestResources` - A record containing some data based on the `ChestType`.
    - `LevelRenderer` now takes in the `GameRenderState` instaed of the `LevelRenderState`
        - `update` - Updates the level.
        - `renderLevel` now takes in the `CameraRenderState` instead of the `Camera`, a `Matrix4fc` instead of a `Matrix4f` from the model view, and the `ChunkSectionsToRender`; it no longer takes in the `Matrix3f` for the projection matrices
        - `extractLevel` - Extracts the level state.
        - `prepareChunkRenders` is now `public` instead of `private`
        - `captureFrustum`, `killFrustum`, `getCapturedFrustum` are removed
        - `getLightColor` -> `getLightCoords`, now taking in the `BlockAndLightGetter` instead of the `BlockAndTintGetter`
        - `$BrightnessGetter#packedBrightness` now takes in the `BlockAndLightGetter` instead of the `BlockAndTintGetter`
    - `LightTexture` -> `Lightmap`
        - `tick` -> `LightmapRenderStateExtractor#tick`
        - `updateLightTexture` -> `render`
        - `pack` -> `LightCoordsUtil#pack`
        - `block` -> `LightCoordsUtil#block`
        - `sky` -> `LightCoordsUtil#sky`
        - `lightCoordsWithEmission` -> `LightCoordsUtil#lightCoordsWithEmission`
    - `MaterialMapper` -> `SpriteMapper`
    - `OrderedSubmitNodeCollector`
        - `submitBlock` is removed
        - `submitBlockModel` now takes in a list of `BlockStateModelPart`s instead of the `BlockStateModel`, and an array of `int`s (array of tint colors) instead of three `float`s for a single color
        - `submitItem` no longer takes in the `RenderType`
        - `submitModel` now has overloads that takes in the `Identifier` for the texture, or a `SpriteId` with the `SpriteGetter` along with an `int` tint color
        - `submitBreakingBlockModel` - Submits the block breaking overlay.
    - `PanoramaRenderer` replaced by `Panorama`
        - `registerTextures` -> `GuiRenderer#registerPanoramaTextures`
        - `render` -> `extractRenderState`
    - `PanoramicScreenshotParameters` record is removed
    - `PostChain` now takes in a `Projection` and `ProjectionMatrixBuffer` instead of an `CachedOrthoProjectionMatrixBuffer`
        - `load` now takes in a `Projection` and `ProjectionMatrixBuffer` instead of an `CachedOrthoProjectionMatrixBuffer`
    - `RenderPipelines`
        - `ENTITY_CUTOUT_NO_CULL` -> `ENTITY_CUTOUT`
            - The original cutout with cull is replaced by `ENTITY_CUTOUT_CULL`
        - `ENTITY_CUTOUT_NO_CULL_Z_OFFSET` -> `ENTITY_CUTOUT_Z_OFFSET`
        - `ENTITY_SMOOTH_CUTOUT` -> `END_CRYSTAL_BEAM`
        - `ENTITY_NO_OUTLINE` replaced by `ENTITY_TRANSLUCENT`, render type constructed with affects outline being false
        - `ENTITY_DECAL`, `DRAGON_EXPLOSION_ALPHA` -> `ENTITY_CUTOUT_DISSOLVE`, not one-to-one
        - `ITEM_ENTITY_TRANSLUCENT_CULL` -> `ENTITY_TRANSLUCENT_CULL`, `ITEM_CUTOUT`, `ITEM_TRANSLUCENT`; not one-to-one
        - `TRANSLUCENT_MOVING_BLOCK` replaced by `TRANSLUCENT_BLOCK`
        - `BANNER_PATTERN` - A pipeline for rendering the patterns on a banner.
    - `ScreenEffectRenderer#renderScreenEffect` now takes in `boolean`s for whether the player is in first person and whether to hide the GUI
    - `Sheets`
        - `*CHEST_*LOCATION*` have been combined into one of the `CHEST_*` fields based on what the resource was for
        - `translucentBlockSheet` - A cullable entity item translucent render type using the block atlas.
        - `cutoutBlockItemSheet` - An item cutout render type using the block atlas.
        - `bannerSheet` -> `RenderTypes#entityTranslucent`, not one-to-one
        - `cutoutItemSheet` - An item cutout render type using the item atlas.
        - `get*Material` -> `get*Sprite`
        - `chooseMaterial` -> `chooseSprite`
    - `SpecialBlockModelRenderer` replaced by `BuiltInBlockModels`, not one-to-one
        - `renderByBlock` -> `BlockModelRenderState#submit*`, not one-to-one
    - `SubmitNodeCollection`
        - `getBlockSubmits` is removed
        - `getBreakingBlockModelSubmits` - Gets the submitted breaking block overlay.
    - `SubmitNodeCollector$ParticleGroupRenderer`
        - `isEmpty` - Whether there are no particles to render in this group.
        - `prepare` now takes whether the particles are being prepared for the translucent layer
        - `render` no longer takes in the translucent `boolean`
    - `SubmitNodeStorage`
        - `$BlockModelSubmit` now takes in a list of `BlockStateModelPart`s instead of the `BlockStateModel`, and an array of `int`s (array of tint colors) instead of three `float`s for a single color
        - `$BlockSubmit` is removed
        - `$BreakingBlockModelSubmit` - A record containing the information to render the block breaking overlay.
        - `$ItemSubmit` no longer takes in the `RenderType`
        - `$MovingBlockSubmit`, `$NameTagSubmit`, `$ShadowSubmit`, `$TextSubmit` now take in a `Matrix4fc` instead of a `Matrix4f` for the pose
    - `VirtualScreen` replaced by `GpuBackend`
- `net.minecraft.client.renderer.block`
    - `BlockAndTintGetter` - A getter for positional block tinting (e.g., biomes).
    - `BlockModelRenderState` - The render state for a block model.
    - `BlockModelResolver` - A helper for setting up the render state for a `BlockState`.
    - `BlockModelSet` - Holds the `BlockModel` associated with each `BlockState`.
    - `BlockModelShaper` -> `BlockStateModelSet`, not one-to-one
        - `getParticleIcon` -> `getParticleMaterial`, now returning a `Material$Baked` instead of a `TextureAtlasSprite`
        - `getBlockModel` -> `get`
        - `getModelManager`, `replaceCache` are removed
    - `BlockQuadOutput` - A functional interface for writing the baked quad information to some output, like a buffer.
    - `BlockRenderDispatcher` class is removed
        - `getBlockModelShaper` -> `getModelSet`, not one-to-one
        - `renderBreakingTexture` replaced with `SubmitNodeCollector#submitBreakingBlockModel`
        - `renderBatched` replaced with direct call to `ModelBlockRenderer#tesselateBlock`
        - `renderLiquid` replaced with direct call to `FluidRenderer#tesselate`
        - `renderSingleBlock` is now inlined within `BlockFeatureRenderer#renderBlockModelSubmits`, a `private` method
            - Use `ModelBlockRenderer#tesselateBlock` as an alternative
    - `FluidModel` - The base fluid model that holds the data for the renderer.
    - `FluidStateModelSet` - Holds the `FluidModel` associated with each `Fluid`.
    - `LoadedBlockModels` - A task for baking the `BlockModel` for each `BlockState`.
    - `LiquidBlockRenderer` -> `FluidRenderer`, not one-to-one
        - The constructor now takes in the `FluidStateModelSet` instead of the `SpriteGetter`
        - `tesselate` now takes in a `FluidRenderer$Output` instead of a `VertexConsumer`
        - `$Output` - Gets the `VertexConsumer` to use for the `ChunkSectionLayer`.
    - `ModelBlockRenderer` now takes in `boolean`s for ambient occlusion and culling
        - `tesselateBlock` now takes in a `BlockQuadOutput` instead of a `VertexConsumer`, the XYZ `float`s instead of a `PoseStack`, the `BlockStateModel` instead of the list of `BlockModelPart`s, no longer takes in the cull `boolean` and `int` overlay, and takes in the seed `long`
        - `tesselateWithAO` -> `tesselateAmbientOcclusion`, now `private` instead of `public`
        - `tesselateWithoutAO` -> `tesselateFlat`, now `private` instead of `public`
        - `renderModel` is now inlined within `BlockFeatureRenderer#renderBlockModelSubmits`, a `private` method
        - `forceOpaque` - Whether the block textures should be opaque instead of translucent.
        - `enableCaching` -> `BlockModelLighter$Cache#enable`
        - `clearCache` -> `BlockModelLighter$Cache#disable`
        - `$AdjacencyInfo` -> `BlockModelLighter$AdjacencyInfo`, now `private` instead of `protected`
        - `$AmbientOcclusionRenderStorage` is replaced by `BlockModelLighter`, not one-to-one
        - `$AmbientVertexRemap` -> `BlockModelLighter$AmbientVertexRemap`
        - `$Cache` -> `BlockModelLighter$Cache`
        - `$CommonRenderStorage` is replaced by `BlockModelLighter`, not one-to-one
        - `$SizeInfo` -> `BlockModelLighter$SizeInfo`
    - `MovingBlockRenderState#level` -> `cardinalLighting`, `lightEngine`; not one-to-one
    - `SelectBlockModel` - A block model that determined or selected by its resolved property.
- `net.minecraft.client.renderer.block.model`
    - `BakedQuad` -> `.client.resources.model.geometry.BakedQuad`
        - The constructor now takes in a `$MaterialInfo` instead of the `TextureAtlasSprite`, `int` tint index, `int` light emission, and `boolean` shade
        - `FLAG_TRANSLUCENT` - A flag that marks the baked quad has having translucency.
        - `FLAG_ANIMATED` - A flag that marks the baked quad has having an animated texture.
        - `isTinted` -> `$MaterialInfo#isTinted`
        - `$MaterialFlags` - An annotation that marks whether a given integer defines the flags for a material.
        - `$MaterialInfo` - A record holding the information on how to render the quad.
    - `BlockDisplayContext` - An object that represents the display context of a block.
    - `BlockElement` -> `.client.resources.model.cuboid.CuboidModelElement`
    - `BlockElementFace` -> `.client.resources.model.cuboid.CuboidFace`
    - `BlockElementRotation` -> `.client.resources.model.cuboid.CuboidRotation`
    - `BlockModel` - The base block model that updates the render state for use outside the world context.
        - The original implementation has been moved to `.client.resources.model.cuboid.CuboidModel`
    - `BlockModelDefinition` -> `.block.dispatch.BlockStateModelDispatcher`
    - `BlockModelPart` -> `.block.dispatch.BlockStateModelPart`
        - `particleIcon` -> `particleMaterial`, now returning a `Material$Baked` instead of a `TextureAtlasSprite`
        - `materialFlags` - Handles the flags for the material(s) used by the model.
    - `BlockStateModel` -> `.block.dispatch.BlockStateModel`
        - `particleIcon` -> `particleMaterial`, now returning a `Material$Baked` instead of a `TextureAtlasSprite`
        - `materialFlags`, `hasMaterialFlag` - Handles the flags for the material(s) used by the model.
    - `BlockStateModelWrapper` - The basic block model that contains the model, tints, and transformation.
    - `CompositeBlockModel` - Overlays multiple block models together.
    - `ConditionalBlockModel` - A block model that shows a different model based on a boolean obtained from some property.
    - `EmptyBlockModel` - A block model that shows nothing.
    - `FaceBakery` -> `.client.resources.model.cuboid.FaceBakery`
        - `bakeQuad` overload now takes in a `ModelBaker` instead of the `ModelBaker$PartCache`, and a `Material$Baked` instead of a `TextureAtlasSprite`
            - It also has an overload taking in the fields of the `BlockElementFace` instead of the object itself
        - Another `bakedQuad` ovrload now takes in the `BakedQuad$MaterialInfo` instead of the `int` tint index and light emission, and the shade `boolean`
    - `ItemModelGenerator` -> `.client.resources.model.cuboid.ItemModelGenerator`
    - `ItemTransform` -> `.client.resources.model.cuboid.ItemTransform`
    - `ItemTransforms` -> `.client.resources.model.cuboid.ItemTransforms`
    - `SimpleModelWrapper` -> `.client.resources.model.SimpleModelWrapper`
        - The constructor now takes in a `Material$Baked` instead of a `TextureAtlasSprite` for the particle
    - `SimpleUnbakedGeometry` -> `.client.resources.model.cuboid.UnbakedCuboidGeometry`
    - `SingleVariant` -> `.block.dispatch.SingleVariant`
    - `SpecialBlockModelWrapper` - A block model for models that submit their elements through `SpecialModelRenderer`s. 
    - `TextureSlots` -> `.client.resources.model.sprite.TextureSlots`
    - `Variant` -> `.block.dispatch.Variant`
    - `VariantMutator` -> `.block.dispatch.VariantMutator`
    - `VariantSelector` -> `.block.dispatch.VariantSelector`
- `net.minecraft.client.renderer.block.model.multipart.*` -> `.block.dispatch.multipart.*`
- `net.minecraft.client.renderer.block.model.properties.conditional`
    - `ConditionalBlockModelProperty` - A property that computes some `boolean` from the `BlockState`.
    - `IsXmas` - Returns whether the current time is between December 24th - 26th.
- `net.minecraft.client.renderer.block.model.properties.select`
    - `DisplayContext` - A case based on the current `BlockDisplayContext`.
    - `SelectBlockModelProperty` - A property that computes some switch state from the `BlockState`.
- `net.minecraft.client.renderer.blockentity`
    - `AbstractEndPortalRenderer`
        - `renderCube` -> `submitCube`, now `protected` and `static` from `private`
        - `submitSpecial` - Submits the end portal cube, used by the special renderer.
        - `getExtents` - Gets the vertices of each face.
        - `getOffsetUp`, `getOffsetDown` are removed
        - `renderType` is removed
    - `AbstractSignRenderer` now takes in a generic for the `SignRenderState`
        - `getSignModel` now takes in the `SignRenderState` generic instead of the `BlockState` and `WoodType`
        - `getSignModelRenderScale`, `getSignTextRenderScale`, `getTextOffset`, `translateSign` replaced by `SignRenderState#transformations`, `SignRenderState$SignTransformations`
        - `getSignMaterial` -> `getSignSprite`
    - `BannerRenderer`
        - `TRANSFORMATIONS` - The transformations to apply when on the wall or ground.
        - `submitPatterns` no longer takes in the base `SpriteId`, whether the pattern has foil, and the outline color
        - `submitSpecial` now takes in the `BannerBlock$AttachmentType`
    - `BedRenderer`
        - `submitSpecial` is removed
            - This is replaced by calling `submitPiece` twice, or making a composite for each bed part via the `BedSpecialRenderer`
        - `submitPiece` is now `public` from `private`, taking in the `BedPart` instead of the `Model$Simple`, `Direction`, or the `boolean` of whether to translate in the Z direction
        - `getExtents` now takes in the `BedPart`
        - `modelTransform` - Gets the transformation for the given `Direction`.
    - `BlockEntityRenderDispatcher` now takes in the `BlockModelResolver` instead of the `BlockRenderDispatcher`, and no longer takes in the `ItemRenderer`
        - `prepare` now takes in a `Vec3` camera position instead of the `Camera` itself
    - `BlockEntityRendererProvider$Context` now takes in the `BlockModelResolver` instead of the `BlockRenderDispatcher`, and no longer takes in the `ItemRenderer`
        - `materials` -> `sprites`
    - `ChestRenderer`
        - `LAYERS` - Holds the model layers of the chest.
        - `modelTransformation` - Gets the transformation for the given `Direction`.
    - `ConduitRenderer#DEFAULT_TRANSFORMATION` - The default transformation to apply.
    - `CopperGolemStatueBlockRenderer#modelTransformation` - Gets the transformation for the given `Direction`.
    - `DecoratedPotRenderer#modelTransformation` - Gets the transformation for the given `Direction`.
    - `HangingSignRenderer` now uses a `HangingSignRenderState`
        - `MODEL_RENDER_SCALE` is now `private` from `public`
        - `TRANSFORMATIONS` - The transformations to apply when on the wall or ground.
        - `translateBase` -> `baseTransformation`, now `private` from `public`
        - `$AttachmentType` -> `HangingSignBlock$Attachment`
            - `byBlockState` -> `$Models#get`
        - `$ModelKey` record is removed
    - `ShulkerBoxRenderer`
        - `modelTransform` - Gets the transformation for the given `Direction`.
        - `getExtents` no longer takes in the `Direction`
    - `SignRenderer` -> `StandingSignRenderer`
        - `TRANSFORMATIONS` - The transformations to apply when on the wall or ground.
        - `createSignModel` now takes in a `PlainSignBlock$Attachment` instead of a `boolean` for whether the block is standing
    - `SkullBlockRenderer`
        - `TRANSFORMATIONS` - The transformations to apply when on the wall or ground.
        - `submitSkull` no longer takes in the `Direction` or `float` rotation
    - `WallAndGroundTransformations` - A class that holds a map of `Direction`s to transforms to apply for the wall, and an `int` function to compute the ground transformations, with the `int` segments generally acting as the number of rotation states.
- `net.minecraft.client.renderer.blockentity.state`
    - `BannerRenderState`
        - `angle` -> `transformation`, not one-to-one
        - `standing` -> `attachmentType`, not one-to-one
    - `BedRenderState#isHead` -> `part`, not one-to-one
    - `BlockEntityRenderState#blockState` is now `private` from `public`
    - `ChestRenderState#angle` -> `facing`, not one-to-one
    - `CondiutRenderState` -> `ConduitRenderState`
    - `CopperGolemStatueRenderState#oxidationState` - The current oxidation state.
    - `HangingSignRenderState` - The render state for the hanging sign.
    - `ShelfRenderState#facing` - The direction the shelf is facing.
    - `SignRenderState#woodType` - The type of wood the sign is made of.
    - `SkullblockRenderState#direction`, `rotationDegrees` -> `transformation`, not one-to-one
    - `StandingSignRenderState` - The render state for the standing sign.
- `net.minecraft.client.renderer.chunk`
    - `ChunkSectionLayer` now takes in a `boolean` for whether the layer is translucent rather than just sorting on upload
        - `byTransparency` - Gets the layer by its transparency setting.
        - `sortOnUpload` -> `translucent`, not one-to-one
        - `vertexFormat` - The vertex format of the pipeline used by the layer.
    - `ChunkSectionsToRender#drawsPerLayer` -> `drawGroupsPerLayer`, with its value being a `int` to list of draws map
    - `CompiledSectionMesh`
        - `uploadMeshLayer` replaced by `isVertexBufferUploaded`, `setVertexBufferUploaded`
        - `uploadLayerIndexBuffer` replaced by `isIndexBufferUploaded`, `setIndexBufferUploaded`
    - `RenderRegionCache#createRegion` now takes in the `ClientLevel` instead of the `Level`
    - `SectionBuffers` -> `SectionRenderDispatcher$RenderSectionBufferSlice`, not one-to-one
    - `SectionCompiler` now takes in the `boolean`s for ambient occlusion and cutout leaves, the `BlockStateModelSet`, the `FluidStateModelSet`, and the `BlockColors` instead of the `BlockRenderDispatcher`
    - `SectionMesh`
        - `getBuffers` -> `getSectionDraw`, not one-to-one
        - `$SectionDraw` - The draw information for the section.
    - `SectionRenderDispatcher` now takes in the `SectionCompiler` instead of the `BlockRenderDispatcher` and `BlockEntityRenderDispatcher`
        - `getRenderSectionSlice` - Gets the buffer slice of the section mesh to render for the chunk layer.
        - `uploadAllPendingUploads` -> `uploadGlobalGeomBuffersToGPU`, not one-to-one
        - `lock`, `unlock` - Handles locking the dispatcher when copying data from location to another, usually for GPU allocation.
        - `getToUpload` is removed
        - `setLevel` now takes in the `SectionCompiler`
        - `$RenderSection`
            - `upload`, `uploadSectionIndexBuffer` -> `addSectionBuffersToUberBuffer`, now private
            - `$CompileTask`
                - `doTask` now returns a `$SectionTaskResult` instead of a `CompletableFuture`
        - `$SectionTaskResult` -> `$RenderSection$CompileTask$SectionTaskResult`
- `net.minecraft.client.renderer.culling.Frustum` now takes in a `Matrix4fc` instead of a `Matrix4f` for the model view
    - `set` - Copies the information from another frustum.
- `net.minecraft.client.renderer.entity`
    - `AbstractBoatRenderer` now takes in the `Identifier` texture
        - `renderType` is removed
    - `AbstractMinecartRenderer`
        - `BLOCK_DISPLAY_CONTEXT` - The context of how to display the block inside the minecart.
        - `submitMinecartContents` now takes in a `BlockModelRenderState` instead of the `BlockState`
    - `CopperGolemRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the antenna block.
    - `DisplayRenderer`
        - `BLOCK_DISPLAY_CONTEXT` - The context of how to display the displayed block.
        - `blockModelResolver` - The block model resolver.
    - `EndermanRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the held block.
    - `EntityRenderDispatcher` now takes in the `BlockModelResolver` instead of the `BlockRenderDispatcher`
    - `EntityRenderer#submitNameTag` -> `submitNameDisplay`, now optionally taking in the y position `int` as an offset from the name tag attachment
    - `EntityRendererProvider$Context` now takes in the `BlockModelResolver` instead of the `BlockRenderDispatcher`
        - `getMaterials` -> `getSprites`
        - `getBlockRenderDispatcher` replaced by `getBlockModelResolver`
    - `IronGolemRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the held block.
    - `ItemFrameRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the displayed block.
    - `ItemRenderer` class is removed
        - Use the `ItemStackRenderState` to submit elements to the feature dispatcher instead
        - `ENCHANTED_GLINT_ARMOR` -> `ItemFeatureRenderer#ENCHANTED_GLINT_ARMOR`
        - `ENCHANTED_GLINT_ITEM` -> `ItemFeatureRenderer#ENCHANTED_GLINT_ITEM`
        - `NO_TINT` -> `ItemFeatureRenderer#NO_TINT`
        - `getFoilBuffer` -> `ItemFeatureRenderer#getFoilBuffer`
        - `getFoilRenderType` -> `ItemFeatureRenderer#getFoilRenderType`, now `public` from `private`
    - `MushroomCowRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the attached blocks.
    - `SnowGolemRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the head block.
    - `TntRenderer#BLOCK_DISPLAY_CONTEXT` - The context of how to display the TNT block.
    - `TntMinecartRenderer#submitWhiteSolidBlock` now takes in a `BlockModelRenderState` instead of the `BlockState`
- `net.minecraft.client.renderer.entity.layers`
    - `BlockDecorationLayer` now takes in a function that returns a `BlockModelRenderState` instead of an optional `BlockState`
    - `MushroomCowMushroomLayer` no longer takes in the `BlockRenderDispatcher`
    - `SnowGolemHeadLayer` no longer takes in the `BlockRenderDispatcher`
- `net.minecraft.client.renderer.entity.state`
    - `AvatarRenderState#scoreText` -> `EntityRenderState#scoreText`
    - `BlockDisplayEntityRenderState#blockRenderState` -> `blockModel`, now a `BlockModelRenderState` instead of a `BlockRenderState`
    - `CopperGolemRenderState#blockOnAntenna` now a `BlockModelRenderState` instead of an optional `BlockState`
    - `EndermanRenderState#carriedBlock` now a `BlockModelRenderState` instead of a nullable `BlockState`
    - `IronGolemRenderState#flowerBlock` - The flower held by the iron golem.
    - `ItemFrameRenderState#frameModel` - The model of the item frame block.
    - `MinecartRenderState#displayBlockState` -> `displayBlockModel`, now a `BlockModelRenderState` instead of a `BlockState`
    - `MushroomCowRenderState#mushroomModel` - The model of mushrooms attached to the cow.
    - `SnowGolemRenderState#hasPumpkin` -> `headBlock`, now a `BlockModelRenderState` instead of a `boolean`
    - `TntRenderState#blockState` now a `BlockModelRenderState` instead of a nullable `BlockState`
- `net.minecraft.client.renderer.features`
    - Feature `render` methods have been split into `renderSolid` for solid render types, and `renderTranslucent` for see-through render types
    - Some `render*` methods now take in the `OptionsRenderState`
    - `BlockFeatureRenderer`
        - `renderSolid` now takes in the `BlockStateModelSet` instead of the `BlockRenderDispatcher`
        - `renderTranslucent` now takes in the `BlockStateModelSet` and the crumbling `MultiBufferSource$BufferSource` instead of the `BlockRenderDispatcher`
    - `FeatureRenderDispatcher` now takes in the `GameRenderState` and `ModelManager`
        - `renderAllFeatures` has been split into `renderSolidFeatures` and `renderTranslucentFeatures`
            - The original method now calls both of these methods, first solid then translucent 
        - `clearSubmitNodes` - Clears the submit node storage.
        - `renderTranslucentParticles` - Renders collected translucent particles.
    - `FlameFeatureRenderer#render` -> `renderSolid`
    - `LeashFeatureRenderer#render` -> `renderSolid`
    - `NameTagFeatureRenderer#render` -> `renderTranslucent`
    - `ShadowFeatureRenderer#render` -> `renderTranslucent`
    - `TextFeatureRenderer#render` -> `renderTranslucent`
- `net.minecraft.client.renderer.fog`
    - `FogData#color` - The color of the fog.
    - `FogRenderer`
        - `setupFog` now returns a `FogData` instead of the `Vector4f` fog color
        - `updateBuffer` - Updates the buffer with the fog data.
- `net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives#render` now takes in a `Matrix4fc` instead of a `Matrix4f` for the model view
- `net.minecraft.client.renderer.item`
    - `BlockModelWrapper` -> `CuboidItemModelWrapper`
        - The constructor no longer takes in the `RenderType` function, and now takes in the `Matrix4fc` transformation
        - `$Unbaked` now takes in an optional `Transformation`
    - `CompositeModel$Unbaked` now takes in an optional `Transformation`
    - `ConiditionalItemModel$Unbaked` now takes in an optional `Transformation`
    - `ItemModel`
        - `$BakingContext`
            - `materials` -> `sprites`
            - `missingItem` - Gets the missing item model with the given `Matrix4fc` transformation.
        - `$Unbaked#bake` now takes in the `Matrix4fc` transformation from any parent client items
    - `ItemStackRenderState`
        - `pickParticleIcon` -> `pickParticleMaterial`, now returning a `Material$Baked` instead of a `TextureAtlasSprite`
        - `$LayerRenderState`
            - `EMPTY_TINTS` - An `int` array representing no tints to apply.
            - `setRenderType` is removed
            - `setParticleIcon` -> `setParticleMaterial`, now taking a `Material$Baked` instead of a `TextureAtlasSprite`
            - `setTransform` -> `setItemTransform`
            - `setLocalTransform` - Sets the client item transform that's applied after item display transforms.
            - `prepareTintLayers` -> `tintLayers`, not one-to-one
    - `MissingItemModel#withTransform`  - Gets a missing item model with the given transform.
    - `ModelRenderProperties#particleIcon` -> `particleMaterial`, now taking a `Material$Baked` instead of a `TextureAtlasSprite`
    - `RangeSelectItemModel$Unbaked` now takes in an optional `Transformation`
    - `SelectItemModel`
        - `$ModelSelector#get` no longer supports nullable `ItemModel`s.
        - `$Unbaked` now takes in an optional `Transformation`
        - `$UnbakedSwitch#bake` now takes in the `Matrix4fc` transformation
    - `SpecialModelWrapper` now takes in the `Matrix4fc` transformation
        - `$Unbaked` now takes in an optional `Transformation`
- `net.minecraft.client.renderer.rendertype`
    - `RenderType#outputTarget` - Gets the output target.
    - `RenderTypes`
        - `MOVING_BLOCK_SAMPLER` replaced by `createMovingBlockSetup`, now `private`
        - `entityCutoutNoCull` -> `entityCutout`
            - The original cutout with cull is replaced by `entityCutoutCull`
        - `entityCutoutNoCullZOffset` -> `entityCutoutZOffset`
        - `entitySmoothCutout` -> `endCrystalBeam`
        - `entityNoOutline` -> `entityTranslucent` with `affectsOutline` as `false`
        - `entityDecal`, `dragonExplosionAlpha` -> `entityCutoutDissolve`, not one-to-one
        - `itemEntityTranslucentCull` -> `entityTranslucentCullItemTarget`, `itemCutout`, `itemTranslucent`; not one-to-one
        - `bannerPattern` - The render type for rendering the patterns of banners.
- `net.minecraft.client.renderer.special`
    - `BannerSpecialRenderer`, `$Unbaked` now take in the `BannerBlock$AttachmentType`
        - `$Unbaked` now uses `BannerPatternLayers` for the generic
    - `BedSpecialRenderer`, `$Unbaked` now take in the `BedPart`
        - `$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
    - `BellSpecialRenderer` - A special renderer for the bell.
    - `BookSpecialRenderer` - A special renderer for the book on the enchantment table.
    - `ChestSpecialRenderer$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
        - The constructor now takes in the `ChestType`
        - `*_CHEST_TEXTURE` is removed from the field name, now a `MultiBlockChestResources`
        - `ENDER_CHEST_TEXTURE` -> `ENDER_CHEST`
    - `ConduitSpecialRenderer$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
    - `CopperGolemStatueSpecialRenderer$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
    - `DecoratedPotSpecialRenderer$Unbaked` now uses `PotDecorations` for the generic
    - `EndCubeSpecialRenderer` - A special renderer for the end portal cube.
    - `HangingSignSpecialRenderer$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
        - The constructor now takes in the `HangingSignBlock$Attachment`
    - `NoDataSpecialModelRenderer`
        - `submit` no longer takes in the `ItemDisplayContext`
        - `$Unbaked` - The unbaked renderer for a special model renderer not needing extracted data.
    - `PlayerHeadSpecialRenderer$Unbaked` now uses `PlayerSkinRenderCache$RenderInfo` for the generic
    - `ShieldSpecialRenderer`
        - `DEFAULT_TRANSFORMATION` - The default transformation to apply.
        - `$Unbaked` now uses `DataComponentMap` for the generic
    - `ShulkerBoxSpecialRenderer`, `$Unbaked` no longer takes in the `Direction` orientation
        - `$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
    - `SkullSpecialRenderer$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
    - `SpecialModelRenderer`
        - `submit` no longer takes in the `ItemDisplayContext`
        - `$BakingContext`
            - `materials` -> `sprites`
            - `$Simple` replaced by `BlockModel$BakingContext`, `ItemModel$BakingContext`
                - `materials` -> `sprites`
        - `$Unbaked` now has the generic of the argument to extract from the representing object
    - `SpecialModelRenderers#createBlockRenderers` -> `BuiltInBlockModels#createBlockModels`, not one-to-one
    - `StandingSignSpecialRenderer$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
        - The constructor now takes in the `PlainSignBlock$Attachment`
    - `TridentSpecialRenderer`
        - `DEFAULT_TRANSFORMATION` - The default transformation to apply.
        - `$Unbaked` now implements `NoDataSpecialModelRenderer$Unbaked`
- `net.minecraft.client.renderer.state.*` -> `.state.level.*`
- `net.minecraft.client.renderer.state`
    - `GameRenderState` - The render state of the game.
    - `OptionsRenderState` - The render state of the client user options.
    - `WindowRenderState` - The render state of the game window.
- `net.minecraft.client.renderer.state.gui.GuiRenderState#submit*` methods have been renamed to `add*`
- `net.minecraft.client.renderer.state.level`
    - `BlockBreakingRenderState` is now a record, and no longer extends `MovingBlockRenderState`
        - The constructor takes in the `BlockPos`, `BlockState`, and the current `int` progress
    - `CameraEntityRenderState` - The render state for the camera entity.
    - `CameraRenderState`
        - `xRot`, `yRot` - The rotation of the camera.
        - `entityPos` is removed
        - `isPanoramicMode` - Whether the camera is in panorama mode.
        - `cullFrustum` - The cull frustum.
        - `fogType`, `fogData` - Fog metadata.
        - `hudFov` - The hud field-of-view.
        - `depthFar` - The depth Z far plane.
        - `projectionMatrix`, `viewRotationMatrix` - The matrices for moving from world space to screen space.
        - `entityRenderState` - The entity the camera is attached to.
    - `LevelRenderState`
        - `lastEntityRenderStateCount` - The number of entities being rendered to the screen.
        - `cloudColor`, `cloudHeight` - Cloud metadata.
    - `LightmapRenderState` - The render state for the lightmap.
- `net.minecraft.client.renderer.texture`
    - `MipmapGenerator#generateMipLevels` now takes in the computed `Transparency` of the image
    - `SpriteContents`
        - `transparency` - Gets the transparency of the sprite.
        - `getUniqueFrames` now returns an `IntList` instaed of an `IntStream`
        - `computeTransparency` - Computes the transparency of the selected UV bounds.
        - `$AnimatedTexture#getUniqueFrames` now returns an `IntList` instaed of an `IntStream`
    - `TextureAtlasSprite#transparency` - Gets the transparency of the sprite.
- `net.minecraft.client.resources.model`
    - `AtlasManager` -> `.model.sprite.AtlasManager`
    - `BlockModelRotation` -> `.client.renderer.block.dispatch.BlockModelRotation`
    - `Material` -> `.model.sprite.SpriteId`, not one-to-one
    - `MaterialSet` -> `.model.sprite.SpriteGetter`
    - `MissingBlockModel` -> `.model.cuboid.MissingCuboidModel`
    - `ModelBaker`
        - `sprites` -> `materials`
        - `parts` -> `interner`
        - `$PartCache` -> `$Interner`
            - `vector(float, float, float)` is removed
            - `materialInfo` - Gets the interned material info object.
    - `ModelBakery`
        - `BANNER_BASE` -> `Sheets#BANNER_BASE`
        - `SHIELD_BASE` -> `Sheets#SHIELD_BASE`
        - `NO_PATTERN_SHIELD` -> `Sheets#SHIELD_BASE_NO_PATTERN`
        - `LAVA_*` -> `FluidStateModelSet#LAVA_MODEL`, now `private` from `public`
        - `WATER_*` -> `FluidStateModelSet#WATER_MODEL`, now `private` from `public`
        - `$BakingResult#getBlockStateModel` - Gets the `BlockStateModel` from the `BlockState`.
        - `$MissingModels` now takes in a `MissingItemModel` instead of an `ItemModel` for the `Item`, and a `FluidModel`
    - `ModelManager`
        - `BLOCK_OR_ITEM` is removed
        - `getMissingBlockStateModel` -> `BlockStateModelSet#missingModel`
        - `getBlockModelShaper` -> `getBlockStateModelSet`, not one-to-one
        - `getBlockModelSet` - Gets the map of `BlockState` to block model.
        - `specialBlockModelRenderer` is removed
        - `getFluidStateModelSet` - Gets the map of `Fluid` to fluid model.
    - `ModelState` -> `.client.renderer.block.dispatch.ModelState`
    - `QuadCollection` -> `.model.geometry.QuadCollection`
        - `addAll` - Adds all elements from another quad collection.
        - `materialFlags`, `hasMaterialFlag` - Handles the flags for the material(s) used by the model.
    - `ResolvedModel#resolveParticleSprite` -> `resolveParticleMaterial`, now returning a `Material$Baked` instead of a `TextureAtlasSprite`
    - `SpriteGetter` -> `.model.sprite.MaterialBaker`
    - `UnbakedGeometry` -> `.model.geometry.UnbakedGeometry`
    - `WeightedVariants` -> `.client.renderer.block.dispatch.WeightedVariants`
- `net.minecraft.client.resources.model.sprite.Material` - A reference to a texture sprite, along with whether to force translucency on the texture.
- `net.minecraft.world.entity.animal.Animal#isBrightEnoughToSpawn` now takes in a `BlockAndLightGetter` instead of the `BlockAndTintGetter`
- `net.minecraft.world.level`
    - `BlockAndTintGetter` -> `BlockAndLightGetter`
        - `BlockAndTintGetter` is now client only, implementing `BlockAndLightGetter`
        - `getShade` -> `cardinalLighting`; not one-to-one
        - `getBlockTint` -> `BlockAndTintGetter#getBlockTint`
    - `CardinalLighting` - Holds the lighting applied in each direction.
    - `EmptyBlockAndTintGetter` -> `BlockAndTintGetter#EMPTY`
    - `LevelReader` now implements `BlockAndLightGetter` instead of `BlockAndTintGetter`
- `net.minecraft.world.level.block`
    - `BannerBlock$AttachmentType` - Where the banner attaches to another block.
    - `CeilingHangingSignBlock` now implements `HangingSignBlock`
        - `getAttachmentPoint` - Gets where the sign attaches to another block.
    - `HangingSignBlock` - An interface that defines a hanging sign attached to another block.
    - `PlainSignBlock` - An inteface that defines a plain sign attached to another block.
    - `StandingSignBlock` now implements `PlainSignBlock`
    - `WallingHangingSignBlock` now implements `HangingSignBlock`
    - `WallSignBlock` now implements `PlainSignBlock`
- `net.minecraft.world.level.block.state.BlockBehaviour#getLightBlock`, `$BlockStateBase#getLightBlock` -> `getLightDampening`
- `net.minecraft.world.level.block.state.properties`
    - `BedPart#CODEC` - The codec for the bed part.
    - `ChestType#CODEC` - The codec for the chest type.
- `net.minecraft.world.level.dimension.DimensionType$CardinalLightType` -> `CardinalLighting$Type`

## Minor Migrations

The following is a list of useful or interesting additions, changes, and removals that do not deserve their own section in the primer.

### Plantable Tags

The blocks to determine whether a plantable can survive or be placed on has been moved to block and fluid tags. Each of these tags starts with `support_*` along with the block (e.g., `bamboo`, `cactus`) or group (e.g., `crops`, `dry_vegetation`). This is handled by the relevant block subclasses overriding either `Block#canSurvive`, or for vegetation `VegetationBlock#mayPlaceOn`.

- `net.minecraft.world.level.block`
    - `AttachedStemBlock` now takes in a `TagKey` for the blocks it can be placed on
    - `FarmBlock` -> `FarmlandBlock`
    - `FungusBlock` -> `NetherFungusBlock`, not one-to-one
    - `RootsBlock` -> `NetherRootsBlock`, not one-to-one
    - `WaterlilyBlock` -> `LilyPadBlock`, not one-to-one
    - `StemBlock` now takes in a `TagKey` for the blocks it can be placed on, and a `TagKey` for the blocks its fruit can be placed on

### Container Screen Changes

The usage of `AbstractContainerScreen`s has changed slightly, requiring some minor changes. First `imageWidth` and `imageHeight` are now final, settable as parameters within the constructor. If these two are not specified, they default to the original 176 x 166 background image.

```java
// Assume some AbstractContainerMenu subclass exists
public class ExampleContainerScreen extends AbstractContainerScreen<ExampleContainerMenu> {

    // Constructor
    public ExampleContainerScreen(ExampleContainerMenu menu, Inventory playerInventory, Component title) {
        // Specify image width and height as the last two parameters in the constructor
        super(menu, playerInventory, title, 256, 256);
    }
}
```

Additionally, the `AbstractContainerScreen#render` override now calls `renderTooltip` at the end of the call stack. This means that, in most cases, you should not override `render` in subtypes of the `AbstractContainerScreen`. Everything can be done in one of the other methods provided by the class.

- `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen` now optionally takes in the background image width and height
    - `imageWidth`, `imageHeight` are now final
    - `DEFAULT_IMAGE_WIDTH`, `DEFAULT_IMAGE_HEIGHT` - The default width and height of the container background image.
    - `slotClicked` now takes is a `ContainerInput` instead of a `ClickType`
    - `render` override now calls `renderTooltip` by default
- `net.minecraft.world.inventory`
    - `AbstractContainerMenu#clicked` now takes in a `ContainerInput` instead of a `ClickType`
    - `ClickType` -> `ContainerInput`

### New Tag Providers

A new `TagsProvider` has been added that provides a utility for working with `Holder$Reference`s, known as `HolderTagProvider`. This is only used by the `PotionTagsProvider`.

Additionally, the `TagBuilder` now provides a method of setting the `replace` field on a tag, which removes all previous read entries during deserialization.

- `net.minecraft.data.tags`
    - `FeatureTagsProvider` - A tag provider for `ConfiguredFeature`s.
    - `HolderTagProvider` - A tag provider with a utility for appending tags by their reference holder.
    - `KeyTagProvider#tag` now has an overload of whether to replace the entries in the tag.
    - `PotionTagsProvider` - A provider for potion tags.
    - `TradeRebalanceTradeTagsProvider` - A provider for villager trade tags for the trade rebalance.
    - `VillagerTradesTagsProvider` - A provider for villager trade tags.
- `net.minecraft.tags`
    - `FeatureTags` - Tags for `ConfiguredFeature`s.
    - `TagBuilder#shouldReplace`, `setReplace` - Handles the `replace` field that removes all previously read entries during deserialization.

### Test Environment State Tracking

`TestEnvironmentDefinition`s can now keep track of the original state of the world when being created, such that it can be properly restored on run. This is done through a generic known as the 'SavedDataType'. On `setup`, each environment will return the generic data representing the original state of what was modified. Then, on `teardown`, the original state will be restored to the level for the next test case.

```java
// The generic should represent the original data stored on the level
public record RespawnEnvironment(LevelData.RespawnData respawn) implements TestEnvironmentDefinition<LevelData.RespawnData> {

    @Override
    public LevelData.RespawnData setup(ServerLevel level) {
        // Modify the level while logging the original state.
        var original = level.getRespawnData();
        level.setRespawnData(this.respawn);

        // Return the original state.
        return original;
    }

    @Override
    public void teardown(ServerLevel level, LevelData.RespawnData original) {
        // Reset the state of the level to the original values.
        level.setRespawnData(original);
    }
    
    @Override
    public MapCodec<RespawnEnvironment> codec() {
        // Return the registered MapCodec here.
        // ...
    }
}
```

- `net.minecraft.gametest.framework.TestEnvironmentDefinition` now has a generic representing the original state of the given modification performed by the test environment
    - `setup` now returns the generic representing the original state
    - `teardown` is no longer default, taking in the original state to restore
    - `activate`, `$Activation` - Handles an active test environment. 

### Typed Instance

`TypedInstance` is an interface that is attached to certain objects that represent an instance of some other 'type' object. For example, and `Entity` is a typed instance of `EntityType`, or a `BlockState` is a typed instance of `Block`. This interface is meant as a standard method to provide access to the type `Holder` and check whether the backing type, and therefore the instance, is equivalent to some identifier, tag, or raw object via `is`.

```java
// For some Entity entity, check the EntityType
entity.is(EntityType.PLAYER);

// For some ItemStack itemStack, check the Item
itemStack.is(ItemTags.BUTTONS);

// For some BlockEntity blockEntity, check the BlockEntityType
blockEntity.is(BlockEntityType.CHEST);

// For some BlockState blockState, check the Block
blockState.is(Blocks.DIRT);

// For some FluidState fluidState, check the Fluid
fluidState.is(FluidTags.WATER);
```

- `net.minecraft.core.TypedInstance` - An interface that represents that this object is an instance for some other 'type' object.
- `net.minecraft.world.entity`
    - `Entity` now implements `TypedInstance<EntityType<?>>`
    - `EntityType#is` -> `TypedInstance#is`
        - Now on the `Entity` instance
- `net.minecraft.world.item.ItemStack` now implements `TypedInstance<Item>`
    - `getItemHolder` -> `typeHolder`
    - `getTags` -> `tags`
- `net.minecraft.world.level.block.entity`
    - `BlockEntity` now implements `TypedInstance<BlockEntityType>`
    - `BlockEntityType#getKey` is removed
- `net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase` now implements `TypedInstance<Block>`
    - `getBlockHolder` -> `typeHolder`
    - `getTags` -> `tags`
- `net.minecraft.world.level.material.FluidState` now implements `TypedInstance<Fluid>`
    - `holder` -> `typeHolder`
    - `getTags` -> `tags`

### Entity Textures and Adult/Baby Models

Entity textures within `assets/minecraft/textures/entity/*` have now been sorted into subdirectories (e.g., `entity/panda` for panda textures, or `entity/pig` for pig textures). Most textures have been named with the entity type starting followed by an underscore along with its variant (e.g., `arrow_tipped` for tipped arrow, `pig_cold` for the cold pig variant, or `panda_brown` for the brown panda variant).

Additionally, some animal models have been split into separate classes for the baby and adult variants. These models either directly extend an abstract model implementation (e.g., `AbstractFelineModel`) or the original model class (e.g., `PigModel`).

- `net.minecraft.client.animation.definitions`
    - `BabyArmadilloAnimation` - Animations for the baby armadillo.
    - `BabyAxolotlAnimation` - Animations for the baby axolotl.
    - `BabyRabbitAnimation` - Animations for the baby rabbit.
    - `CamelBabyAnimation` - Animations for the baby camel.
    - `FoxBabyAnimation` - Animations for the baby fox.
    - `RabbitAnimation` - Animations for the rabbit.
- `net.minecraft.client.model`
    - `HumanoidModel`
        - `ADULT_ARMOR_PARTS_PER_SLOT`, `BABY_ARMOR_PARTS_PER_SLOT` - A map of equipment slot to model part keys.
        - `createBabyArmorMeshSet` - Creates the armor model set for a baby humanoid.
        - `createArmorMeshSet` can now take in a map of equipment slot to model part keys for what to retain
        - `setAllVisible` is removed
    - `QuadrupedModel` now has a constructor that takes in a function for the `RenderType`
- `net.minecraft.client.model.animal.armadillo`
    - `AdultArmadilloModel` - Entity model for the adult armadillo.
    - `ArmadilloModel` is now abstract
        - The constructor now takes in the definitions for the walk, roll out/up, and peek animations
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyArmadilloModel`
        - `HEAD_CUBE`, `RIGHT_EAR_CUBE`, `LEFT_EAR_CUBE` are now `protected` instead of `private`
        - `createBodyLayer` -> `AdultArmadilloModel#createBodyLayer`, `BabyArmadilloModel#createBodyLayer`
    - `BabyArmadilloModel` - Entity model for the baby armadillo.
- `net.minecraft.client.model.animal.axolotl.AxolotlModel` -> `AdultAxolotlModel`, `BabyAxolotlModel`
- `net.minecraft.client.model.animal.bee`
    - `AdultBeeModel` - Entity model for the adult bee.
    - `BabyBeeModel` - Entity model for the baby bee.
    - `BeeModel` is now abstract
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyBeeModel`
        - `BONE`, `STINGER`, `FRONT_LEGS`, `MIDDLE_LEGS`, `BACK_LEGS` are now `protected` instead of `private`
        - `bone` is now `protected` instead of `private`
        - `createBodyLayer` -> `AdultBeeModel#createBodyLayer`, `BabyBeeModel#createBodyLayer`
        - `bobUpAndDown` - Bobs the bee up and down at the desired speed, depending on its current age.
- `net.minecraft.client.model.animal.camel`
    - `AdultCamelModel` - Entity model for the adult camel.
    - `BabyCamelModel` - Entity model for the baby camel.
    - `CamelModel` is now abstract
        - The constructor now takes in the definitions for the walk, sit with/without pose, standup, idle, and dash animations
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyCamelModel`
        - `createBodyLayer` -> `AdultCamelModel#createBodyLayer`, `BabyCamelModel#createBodyLayer`
    - `CameSaddleModel` now extends `AdultCamelModel` instead of `CamelModel`
- `net.minecraft.client.model.animal.chicken`
    - `AdultChickenModel` - Entity model for the adult chicken.
    - `BabyChickenModel` - Entity model for the baby chicken.
    - `ChickenModel` is now abstract
        - `RED_THING` -> `AdultChickenModel#RED_THING`
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyChickenModel`
        - `createBodyLayer` -> `AdultChickenModel#createBodyLayer`
        - `createBaseChickenModel` -> `AdultChickenModel#createBaseChickenModel`
    - `ColdChickenModel` now extends `AdultChickenModel`
- `net.minecraft.client.model.animal.cow.BabyCowModel` - Entity model for the baby cow.
- `net.minecraft.client.model.animal.dolphin.BabyDolphinModel` - Entity model for the baby dolphin.
- `net.minecraft.client.model.animal.equine`
    - `AbstractEquineModel` now has an overload that directly specifies the `ModelPart`s to use
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyDonkeyModel`
        - `rightHindLeg`, `leftHindLeg`, `rightFrontLeg`, `leftFrontLeg` are now `protected` from `private`
        - `createBabyMesh` -> `BabyHorseModel#createBabyMesh`, not one-to-one
        - `offsetLegPositionWhenStanding` - Offsets the position of the legs when the entity is standing.
        - `getLegStandAngle`, `getLegStandingYOffset`, `getLegStandingZOffset`, `getLegStandingXRotOffset`, `getTailXRotOffset` - Offsets and angles for parts of the equine model.
        - `animateHeadPartsPlacement` - Animates the head based on its eating and standing states.
    - `BabyDonkeyModel` - Entity model for the baby donkey.
    - `BabyHorseModel` - Entity model for the baby horse.
    - `DonkeyModel` now has an overload that directly specifies the `ModelPart`s to use
        - `createBabyLayer` -> `BabyDonkeyModel#createBabyLayer`
    - `EquineSaddleModel#createFullScaleSaddleLayer` is removed
        Merged into `createSaddleLayer`, with the baby variant removed
- `net.minecraft.client.model.animal.feline`
    - `CatModel` -> `AdultCatModel`, `BabyCatModel`; not one-to-one
    - `FelineModel` -> `AbstractFelineModel`, not one-to-one
        - Implementations in `AdultFelineModel` and `BabyFelineModel`
    - `OcelotModel` -> `AdultOcelotModel`, `BabyOcelotModel`; not one-to-one
- `net.minecraft.client.model.animal.fox`
    - `AdultFoxModel` - Entity model for the adult fox.
    - `BabyFoxModel` - Entity model for the baby fox.
    - `FoxModel` is now abstract
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyFoxModel`
        - `body`, `rightHindLeg`, `leftHindLeg`, `rightFontLeg`, `leftFrontLeg`, `tail` are now `protected` instead of `private`
        - `createBodyLayer` -> `AdultFoxModel#createBodyLayer`, `BabyFoxModel#createBodyLayer`
        - `set*Pose` - Methods for setting the current pose of the fox.
- `net.minecraft.client.model.animal.goat`
    - `BabyGoatModel` - Entity model for the baby goat.
    - `GoatModel#BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyGoatModel`
- `net.minecraft.client.model.animal.llama`
    - `BabyLlamaModel` - Entity model for the baby llama.
    - `LlamaModel#createBodyLayer` no longer takes in the `boolean` for if the entity is a baby
- `net.minecraft.client.model.animal.panda`
    - `BabyPandaModel` - Entity model for the baby panda.
    - `PandaModel`
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyPandaModel`
        - `animateSitting` - Animates the panda sitting.
- `net.minecraft.client.model.animal.pig.BabyPigModel` - Entity model for the baby pig.
- `net.minecraft.client.model.animal.polarbear`
    - `BabyPolarBearModel` - Entity model for the baby polar bear.
    - `PolarBearModel#BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyPolarBearModel`
- `net.minecraft.client.model.animal.rabbit`
    - `AdultRabbitModel` - Entity model for the adult rabbit.
    - `BabyRabbitModel` - Entity model for the baby rabbit.
    - `RabbitModel` is now abstract
        - The constructor now takes in two animation definitions for the hop and idle head tilt
        - `FRONT_LEGS`, `BACK_LEGS` - The child name for the entity legs.
        - `LEFT_HAUNCH`, `RIGHT_HAUNCH` are now `protected`
        - `createBodyLayer` -> `AdultRabbitModel#createBodyLayer`, `BabyRabbitModel#createBodyLayer`; not one-to-one
- `net.minecraft.client.model.animal.sheep`
    - `BabySheepModel` - Entity model for the baby sheep.
    - `SheepModel#BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabySheepModel`
- `net.minecraft.client.model.animal.sniffer`
    - `SnifferModel#BABY_TRANSFORMER` has been directly merged into the layer definition for the `SniffletModel`
    - `SniffletModel` - Entity model for the baby sniffer.
- `net.minecraft.client.model.animal.squid`
    - `BabySquidModel` - Entity model for the baby squid.
    - `SquidModel#createTentacleName` is now protected from private
- `net.minecraft.client.model.animal.turtle`
    - `AdultTurtleModel` - Entity model for the adult turtle.
    - `BabyTurtleModel` - Entity model for the baby turtle.
    - `TurtleModel` is now abstract
        - The constructor can how take in the render type function
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyTurtleModel`
        - `createBodyLayer` -> `AdultTurtleModel#createBodyLayer`, `BabyTurtleModel#createBodyLayer`
- `net.minecraft.client.model.animal.wolf`
    - `AdultWolfModel` - Entity model for the adult wolf.
    - `BabyWolfModel` - Entity model for the baby wolf.
    - `WolfModel` is now abstract
        - `ModelPart` fields are now all protected
        - `createMeshDefinition` -> `AdultWolfModel#createBodyLayer`, `BabyWolfModel#createBodyLayer`; not one-to-one
        - `shakeOffWater` - Sets the body rotation when shaking off water.
        - `setSittingPose` - Sets the sitting pose of the wolf.
- `net.minecraft.client.model.geom`
    - `ModelLayers`
        - `COLD_CHICKEN_BABY` is removed
        - `COLD_PIG_BABY` is removed
        - `PIG_BABY_SADDLE` is removed
        - `SHEEP_BABY_WOOL_UNDERCOAT` is removed
        - `WOLF_BABY_ARMOR` is removed
        - `DONKEY_BABY_SADDLE` is removed
        - `HORSE_BABY_ARMOR` is removed
        - `HORSE_BABY_SADDLE` is removed
        - `MULE_BABY_SADDLE` is removed
        - `SKELETON_HORSE_BABY_SADDLE` is removed
        - `UNDEAD_HORSE_BABY_ARMOR` is removed
        - `ZOMBIE_HORSE_BABY_SADDLE` is removed
        - `STRIDER_BABY_SADDLE` is removed
    - `PartNames#WAIST` - The waist part.
- `net.minecraft.client.model.monster.hoglin`
    - `BabyHoglinModel` - Entity model for the baby hoglin.
    - `HoglinModel`
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyHoglinModel`
        - `head` is now `protected` from `private`
        - `createBabyLayer` -> `BabyHoglinModel#createBodyLayer`
- `net.minecraft.client.model.monster.piglin`
    - `AbstractPiglinModel` is now abstract
        - `leftSleeve`, `rightSleeve`, `leftPants`, `rightPants`, `jacket` are removed
        - `ADULT_EAR_ANGLE_IN_DEGREES`, `BABY_EAR_ANGLE_IN_DEGREES` - The angle of the piglin ears.
        - `createMesh` replaced by `AdultPiglinModel#createBodyLayer`, `AdultZombifiedPiglinModel#createBodyLayer`, `BabyPiglinModel#createBodyLayer`, `BabyZombifiedPiglinModel#createBodyLayer`
        - `createBabyArmorMeshSet` - Create the armor meshes for the baby piglin model.
        - `getDefaultEarAngleInDegrees` - Gets the default ear angle.
    - `AdultPiglinModel` - Entity model for the adult piglin.
    - `AdultZombifiedPiglinModel` - Entity model for the adult zombified piglin.
    - `BabyPiglinModel` - Entity model for the baby piglin.
    - `BabyZombifiedPiglinModel` - Entity model for the baby zombified piglin.
    - `PiglinModel` is now abstract
    - `ZombifiedPiglinModel` is now abstract
- `net.minecraft.client.model.monster.strider`
    - `AdultStriderModel` - Entity model for the adult strider.
    - `BabyStriderModel` - Entity model for the baby strider.
    - `StriderModel` is now abstract
        - `BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyStriderModel`
        - `rightLeg`, `leftLeg`, `body` are now `protected` from `private`
        - `SPEED` - The speed scalar of the movement animation.
        - `customAnimations` - Additional animation setup.
        - `animateBristle` - Animates the bristles of the strider.
- `net.minecraft.client.model.monster.zombie`
    - `BabyDrownedModel` - Entity model for the baby drowned.
    - `BabyZombieModel` - Entity model for the baby zombie.
    - `BabyZombieVillagerModel` - Entity model for the baby zombie villager.
- `net.minecraft.client.model.npc`
    - `BabyVillagerModel` - Entity model for the baby villager.
    - `VillagerModel#BABY_TRANSFORMER` has been directly merged into the layer definition for the `BabyVillagerModel` 
- `net.minecraft.client.renderer.entity`
    - `AxolotlRenderer` now takes in an `EntityModel<AxolotlRenderState>` for its generic
    - `CamelHuskRenderer` now extends `MobRenderer` instead of `CamelRenderer`
    - `CamelRenderer#createCamelSaddleLayer` is now `static`
    - `CatRenderer` now takes in an `AbstractFelineModel` for its generic
    - `DonkeyRenderer` now takes in an `EquipmentClientInfo$LayerType` and `ModelLayerLocation` for the saddle layer and model, and splits the `DonkeyRenderer$Type` into the adult type and baby type
        - `$Type`
            - `DONKEY_BABY` - A baby variant of the donkey.
            - `MULE_BABY` - A baby variant of the mule.
    - `OcelotRenderer` now takes in an `AbstractFelineModel` for its generic
    - `UndeadHorseRenderer` now takes in an `EquipmentClientInfo$LayerType` and `ModelLayerLocation` for the saddle layer and model, and splits the `UndeadHorseRenderer$Type` into the adult type and baby type
        - `$Type`
            - `SKELETON_BABY` - A baby variant of the skeleton horse.
            - `ZOMBIE_BABY` - A baby variant of the zombie horse.
- `net.minecraft.client.renderer.entity.layers.CatCollarLayer` now takes in an `AbstractFelineModel` for its generic
- `net.minecraft.client.renderer.entity.state`
    - `AxolotlRenderState`
        - `swimAnimation` - The state of swimming.
        - `walkAnimationState` - The state of walking on the ground, not underwater.
        - `walkUnderWaterAnimationState` - The state of walking underwater.
        - `idleUnderWaterAnimationState` - The state of idling underwater but not on the ground.
        - `idleUnderWaterOnGroundAnimationState` - The state of idling underwater while touching the seafloor.
        - `idleOnGroundAnimationState` - The state of idling on the ground, not underwater.
        - `playDeadAnimationState` - The state of playing dead.
    - `RabbitRenderState`
        - `hopAnimationState` - The state of the hop the entity is performing.
        - `idleHeadTiltAnimationState` - The state of the head tilt when performing the idle animation.
- `net.minecraft.client.resources.model.EquipmentClientInfo$LayerType#HUMANOID_BABY` - The baby humanoid equipment layer.
- `net.minecraft.sounds.SoundEvents#PIG_EAT_BABY` - Ths sound played when a baby big is eating.
- `net.minecraft.world.entity.AgeableMob`
    - `canUseGoldenDandelion` - Whether a golden dandelion can be used to agelock an entity.
    - `setAgeLocked` - Sets the entity as agelocked.
    - `makeAgeLockedParticle` - Creates the particles when agelocking an entity.
    - `AGE_LOCK_DOWNWARDS_MOVING_PARTICLE_Y_OFFSET` - The Y offset for the particle's starting position.
    - `getBabyStartAge` - The initial age of the baby.
- `net.minecraft.world.entity.animal.axolotl.Axolotl`
    - `swimAnimation` - The state of swimming.
    - `walkAnimationState` - The state of walking on the ground, not underwater.
    - `walkUnderWaterAnimationState` - The state of walking underwater.
    - `idleUnderWaterAnimationState` - The state of idling underwater but not on the ground.
    - `idleUnderWaterOnGroundAnimationState` - The state of idling underwater while touching the seafloor.
    - `idleOnGroundAnimationState` - The state of idling on the ground, not underwater.
    - `playDeadAnimationState` - The state of playing dead.
    - `$AnimationState` -> `$AxolotlAnimationState`
- `net.minecraft.world.entity.animal.chicken.ChickenVariant` now takes in a resource for the baby texture
- `net.minecraft.world.entity.animal.cow.CowVariant` now takes in a resource for the baby texture
- `net.minecraft.world.entity.animal.cat.CatVariant` now takes in a resource for the baby texture
    - `CatVariant#assetInfo` - Gets the entity texture based on whether the entity is a baby.
- `net.minecraft.world.entity.animal.equine.AbstractHorse#BABY_SCALE` - The scale of the baby size compared to an adult.
- `net.minecraft.world.entity.animal.frog.Tadpole`
    - `ageLockParticleTimer` - A timer for how long the particles for the agelocked entity should spawn.
    - `setAgeLocked`, `isAgeLocked` - Handles agelocking for the tadpole.
- `net.minecraft.world.entity.animal.goat.Goat`
    - `BABY_DEFAULT_X_HEAD_ROT` - The default head X rotation for the baby variant.
    - `MAX_ADDED_RAMMING_X_HEAD_ROT` - The maximum head X rotation.
    - `addHorns`, `removeHorns` are removed
- `net.minecraft.world.entity.animal.pig.PigVariant` now takes in a resource for the baby texture
- `net.minecraft.world.entity.animal.rabbit.Rabbit`
    - `hopAnimationState` - The state of the hop the entity is performing.
    - `idleHeadTiltAnimationState` - The state of the head tilt when performing the idle animation.
- `net.minecraft.world.entity.animal.wolf`
    - `WolfSoundVariant` -> `WolfSoundVariant$WolfSoundSet`
        - The class itself now holds two sound sets for the adult wolf and baby wolf.
    - `WolfSoundVariants$SoundSet#getSoundEventSuffix` -> `getSoundEventIdentifier`
- `net.minecraft.world.entity.animal.wolf.WolfVariant` now takes in an asset info for the baby variant
- `net.minecraft.world.item.equipment.EquipmentAssets#TRADER_LLAMA_BABY` - Equipment asset for baby trader llamas.

### The Removal of interactAt

`Entity#interactAt` has been removed, with all further invocation merged with `Entity#interact`. Originally, `Entity#interactAt` was called if the hit result was within the entity's interaction range. If `interactAt` did not consume the action (i.e., `InteractionResult#consumesAction` returns false), then `Entity#interact` is called. Now, `Entity#interact` is called if within the entity's interaction range, taking in the `Vec3` location of interaction.

```java
// In some Entity subclass

@Override
public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
    // Handle the interaction
    super.interact(player, hand, location);
}
```

`interactAt` checks if result does not consume the interaction, and if not calls `interact`
Now, `interact` is called with the entity hit

- `net.minecraft.client.multiplayer.MultiPlayerGameMode`
    - `interact` now takes in the `EntityHitResult`
    - `interactAt` is removed
- `net.minecraft.world.entity.Entity`
    - `interact` now takes in a `Vec3` for the location of the interaction
    - `interactAt` is removed
- `net.minecraft.world.entity.player.Player#interactOn` now takes in a `Vec3` for the location of the interaction

### ChunkPos, now a record

`ChunkPos` is now a record. The `BlockPos` constructor has been replaced by `ChunkPos#containing` while the packed `long` constructor has been replaced by `ChunkPose#unpack`.

- `net.minecraft.world.level.ChunkPos` is now a record
    - `ChunkPos(BlockPos)` -> `containing`
    - `ChunkPos(long)` -> `unpack`
    - `toLong`, `asLong` -> `pack`

### No More Tripwire Pipelines

The tripwire render pipeline has been completely removed. Now, tripwires make use of `cutout` with an `alpha_cutoff_bias` of 0.1 for the texture.

- `net.minecraft.client.renderer.RenderPipelines#TRIPWIRE_BLOCK`, `TRIPWIRE_TERRAIN` are removed
- `net.minecraft.client.renderer.chunk`
    - `ChunkSectionLayer#TRIPWIRE` is removed
    - `ChunkSectionLayerGroup#TRIPWIRE` is removed
- `net.minecraft.client.renderer.rendertype.RenderTypes#tripwireMovingBlock` is removed

### Activities and Brains

Activities, which define how a living entity behaves during a certain phase, now are stored and passed around through `ActivityData`. This contains the activity type, the behaviors to perform along with their priorities, the conditions on when this activity activates, and the memories to erase when the activity has stopped. `Brain#provider` now takes in an `$ActivitySupplier` to construct a list of `ActivityData` the entity performs. 

Brains have also changed slightly. First, the `Brain` itself is not directly serializable. Instead, the brain is `$Packed` into the record, holding a map of its current memories. To get the memory types used, they are typically extracted from those that the `Sensor#requires`. Additionally, `LivingEntity#brainProvider` no longer exists, instead opting to store the provider in a static constant on the entity itself. This means that brains are now completely constructed through the `makeBrain` method, taking in the previous `$Packed` data:

```java
// For some ExampleEntity extends LivingEntity
// Assume extends Mob subclass
private static final Brain.Provider<ExampleEntity> BRAIN_PROVIDER = Brain.provider(
    // The list of sensors the entity uses.
    ImmutableList.of(),
    // A function that takes in the entity and returns a list of activities.
    entity -> List.of(
        new ActivityData(
            // The activity type
            Activity.CORE,
            // A list of priority and behavior pairs
            ImmutableList.of(Pair.of(0, new MoveToTargetSink())),
            // A set of memory conditions for the activity to run
            // For example, this memory value must be present
            ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
            // The set of memories to erase when the activity has stopped
            ImmutableSet.of(MemoryModuleType.ATTACK_TARGET)
        )
    )
);

@Override
protected Brain.Provider<ExampleEntity> makeBrain(Brain.Packed packedBrain) {
    // Make the brain, populating any previous memories
    return BRAIN_PROVIDER.makeBrain(this, packedBrain);
}
```

- `net.minecraft.world.entity.LivingEntity`
    - `brainProvider` is removed
    - `makeBrain` now takes in a `Brain$Packed` instead of a `Dynamic`
- `net.minecraft.world.entity.ai`
    - `ActivityData` - A record containing the activity being performed, the behaviors to perform during that activity, any memory conditions, and what memories to erase once stopped.
    - `Brain` is now protected, taking in a list of `ActivityData`, a `MemoryMap` instead of an immutable list of memories, a `RandomSource`, and not the supplied `Codec`
        - The public constructor no longer takes in anything
        - `provider` now has an overload that only takes in the sensor types, defaulting the memory types to an empty list
            - Some `provider` methods also take in the `Brain$ActivitySupplier` to perform
        - `codec`, `serializeStart` are replaced by `pack`, `Brain$Packed`
        - `addActivityAndRemoveMemoryWhenStopped`, `addActivityWithConditions` merged into `addActivity`
            - Alternatively use `ActivityData#create`
        - `copyWithoutBehaviors` is removed
        - `getMemories` replaced by `forEach`
        - `$ActivitySupplier` - Creates a list of activities for the entity.
        - `$MemoryValue` is removed
        - `$Packed` - A record containing the data to serialize the brain to disk.
        - `$Provider#makeBrain` now takes in the entity and the `Brain$Packed` to deserialize the memories
        - `$Visitor` - Visits the memories within a brain, whether defined but empty, present, or present with some timer.
- `net.minecraft.world.entity.ai.behavior.VillagerGoalPackages#get*Package` no longer take in the `VillagerProfession`
- `net.minecraft.world.entity.ai.memory`
    - `ExpirableValue` -> `MemorySlot`, not one-to-one
        - The original `ExpirableValue` is now a record that defines when a memory should expire, rather than be updated itself
    - `MemoryMap` - A map linking the memory type to its stored value.
    - `MemoryModuleType` - Whether a memory can be serialized.
- `net.minecraf.tworld.entity.ai.sensing.Sensor#randomlyDelayStart` - How long to delay a sensor.
- `net.minecraft.world.entity.animal.allay.AllayAi`
    - `SENSOR_TYPES`, `MEMORY_TYPES` -> `BRAIN_PROVIDER`, now private; not one-to-one
    - `makeBrain` -> `getActivities`, not one-to-one
- `net.minecraft.world.entity.animal.armadillo.ArmadilloAi#makeBrain`, `brainProvider` -> `getActivities`, now `protected`, not one-to-one
- `net.minecraft.world.entity.animal.axolotl`
    - `AxolotlSENSOR_TYPES` -> `BRAIN_PROVIDER`, now private; not one-to-one
    - `AxolotlAi`
        - `makeBrain` -> `getActivities`, not one-to-one
        - `initPlayDeadActivity`, now `protected`, no longer taking in anything
        - `initFightActivity`, now `protected`, no longer taking in anything
        - `initCoreActivity`, now `protected`, no longer taking in anything
        - `initIdleActivity`, now `protected`, no longer taking in anything
- `net.minecraft.world.entity.animal.camel.CamelAi#makeBrain`, `brainProvider` -> `getActivities`, now `protected`, not one-to-one
- `net.minecraft.world.entity.animal.frog`
    - `Frog#SENSOR_TYPES` -> `BRAIN_PROVIDER`, now private; not one-to-one
    - `FrogAi#makeBrain` -> `getActivities`, not one-to-one
    - `Tadpole#SENSOR_TYPES` -> `BRAIN_PROVIDER`, now private; not one-to-one
- `net.minecraft.world.entity.animal.frog.TadpoleAi#makeBrain` -> `getActivities`, now `public`, not one-to-one
- `net.minecraft.world.entity.animal.goat`
    - `Goat#SENSOR_TYPES` -> `BRAIN_PROVIDER`, now private; not one-to-one
    - `GoatAi#makeBrain` -> `getActivities`, not one-to-one
- `net.minecraft.world.entity.animal.golem.CopperGolemAi#makeBrain`, `brainProvider` -> `getActivities`, now `protected`, not one-to-one
- `net.minecraft.world.entity.animal.happyghast.HappyGhastAi#makeBrain`, `brainProvider` -> `getActivities`, now `protected`, not one-to-one
- `net.minecraft.world.entity.animal.nautilus`
    - `NautilusAi`
        - `SENSOR_TYPES`,`MEMORY_TYPES` -> `Nautilus#BRAIN_PROVIDER`, now private, not one-to-one
        - `makeBrain`, `brainProvider` -> `getActivities`, now `public`, not one-to-one
    - `ZombieNautilusAi`
        - `SENSOR_TYPES`,`MEMORY_TYPES` -> `ZombieNautilus#BRAIN_PROVIDER`, now private, not one-to-one
        - `makeBrain`, `brainProvider` -> `getActivities`, now `public`, not one-to-one
- `net.minecraft.world.entity.animal.sniffer.SnifferAi#makeBrain` -> `getActivities`, now `public`, not one-to-one
- `net.minecraft.world.entity.monster.Zoglin`
    - `SENSOR_TYPES` -> `BRAIN_PROVIDER`, now private, not one-to-one
    - `getActivities` - The activities the zoglin performs.
- `net.minecraft.world.entity.monster.breeze.BreezeAi#makeBrain` -> `getActivities`, not one-to-one
- `net.minecraft.world.entity.monster.creaking.CreakingAi#makeBrain`, `brainProvider` -> `getActivities`, now `protected`, not one-to-one
- `net.minecraft.world.entity.monster.hoglin`
    - `Hoglin#SENSOR_TYPES` -> `BRAIN_PROVIDER`, now private; not one-to-one
    - `HoglinAi#makeBrain` -> `getActivities`, not one-to-one
- `net.minecraft.world.entity.monster.piglin`
    - `Piglin#SENSOR_TYPES`,`MEMORY_TYPES` -> `BRAIN_PROVIDER`, now private, not one-to-one
    - `PiglinAi#makeBrain` -> `getActivities`, now `public`, not one-to-one
    - `PiglinBrute#SENSOR_TYPES`,`MEMORY_TYPES` -> `BRAIN_PROVIDER`, now private, not one-to-one
    - `PiglinBruteAi#makeBrain` -> `getActivities`, now `public`, not one-to-one
- `net.minecraft.world.entity.monster.warden.WardenAi#makeBrain` -> `getActivities`, not one-to-one

### File Fixer Upper

The file fixer upper is a new system to help with upgrading game files between versions, in conjunction with the data fixer upper. Similar to how the data within files can be modified or 'upgraded' between Minecraft versions via data fixers, file fixers can modify anything within a world directory, from moving files and directories to deleting them outright. As such, file fixers are always applied before data fixers.

Unlike when upgrading when data fixers, file fixers change the structure of the world folder. Because of this, downgrading is rarely possible, as the file names and locations will have likely changed location.

File fixers are applied through a `FileFix`, which defines some operation(s) to perform on a file using `makeFixer`. This is done typically through the `addFileContentFix`, providing access to the desired files through the `FileAccess`, and then modifying the data like any other `Dynamic` instance. Operations are commonly defined as `FileFixOperation`s, which can move the file structure around.

The file fixes are then applied through the `FileFixerUpper`, which uses a copy-on-write file system to operate on the files. The fixer is constructed using the `$Builder`, using `addSchema` and `addFixer` to apply the fixers for the desired version. During the upgrade process, the files are created in a temporary folder, then moved to the world folder. The original world folder is moved to another folder before being deleted.

- `net.minecraft.client.gui.screens.FileFixerAbortedScreen` - A screen that's shown when the file fixing has been aborted.
- `net.minecraft.client.gui.screens.worldselection.FileFixerProgressScreen` - A screen that's displayed when attempting to show the progress of upgrading and fixing the world files.
- `net.minecraft.server.packs.linkfs`
    - `DummyFileAttributes` -> `.minecraft.util.DummyFileAttributes`
    - `LinkFSPath`
        - `DIRECTORY_ATTRIBUTES` -> `DummyFileAttributes#DIRECTORY`
        - `FILE_ATTRIBUTES` -> `DummyFileAttributes#FILE`
- `net.minecraft.util`
    - `ExtraCodecs`
        - `pathCodec` - A codec for a path, converting from a string and storing with Unix separators.
        - `relaiveNormalizedSubPathCodec` - A codec for a path, normalized and validated to make sure it is relative.
        - `guardedPathCodec` - A codec for a path, resolved and relativatized from some base path.
    - `FileUtil`
        - `isPathNormalized`, `createPathToResource` are removed
        - `isEmptyPath` - Returns whether the path is empty.
    - `Util#safeMoveFile` - Safely moves a file from some source to a destination with the given options.
- `net.minecraft.util.filefix`
    - `AbortedFileFixException` - An exception thrown when the file fix has been aborted and was unable to revert moves.
    - `AtmoicMoveNotSupportedFileFixException` - An exception thrown when the user file system does not support atomic moves.
    - `CanceledFileFixException` - An exception thrown when the file fix upgrade proccess is canceled.
    - `FailedCleanupFileFixException` - An exception thrown when the file fix was not able to move or delete folders for cleanup.
    - `FileFix` - A fixer that performs some operation on a file. 
    - `FileFixerUpper` - The file fixers to perform operations with.
    - `FileFixException` - An exception thrown when attempting to upgrade a world through the file fixer.
    - `FileFixUtil` - A utility for performing some file operations.
    - `FileSystemCapabilities` - The capabilities of the file system from a directory.
- `net.minecraft.util.filefix.access`
    - `ChunkNbt` - Handles upgrading a chunk nbt.
    - `CompressedNbt` - Handles upgrading a compressed nbt file.
    - `FileAccess` - Provides an reference to some file resource(s), given its relation.
    - `FileAccessProvider` - A provider of file accesses, given some relation to the origin.  
    - `FileRelation` - A definition of how a file relates to some origin bpath.
    - `FileResourceType` - Defines the type of resource to access.
    - `FileResourceTypes` - All defined file resource types.
    - `LevelDat` - Handles upgrading a level dat.
    - `PlayerData` - Handles upgrading the player data.
    - `SavedDataNbt` - Handles upgrading the saved data.
- `net.minecraft.util.filefix.fixes.*` - The vanilla fixes to apply to the file(s).
- `net.minecraft.util.filefix.operations`
    - `ApplyInFolders` - Applies the given operations within the related folders.
    - `DeleteFileOrEmptyDirectory` - Deletes the target file or empty directory.
    - `FileFixOperation` - An operation performed within some base directory.
    - `FileFixOperations` - All vanilla file fix operations.
    - `GroupMove` - Moves some directory, applying any move operation on its contents.
    - `ModifyContent` - Modifies the content of a file.
    - `Move` - Moves a file from some source to some destination.
    - `RegexMove` - Moves all files that match the given source pattern to the destination, replacing the matched sections.
- `net.minecraft.util.filefix.virtualfilesystem`
    - `CopyOnWriteFileStore` - A file store using the copy-on-write principle.
    - `CopyOnWriteFileSystem` - A file system using the copy-on-write principle.
    - `CopyOnWriteFSPath` - A path using the copy-on-write principle.
    - `CopyOnWriteFSProvider` - A file system provider using the copy-on-write principle.
    - `DirectoryNode` - A directory node for some copy-on-write file system path.
    - `FileMove` - A record containing the path a file has been moved from to.
    - `FileNode` - A file node for some copy-on-write file system path.
    - `Node` - A node for some copy-on-write file system path.
- `net.minecraft.util.filefix.virtualfilesystem.exception`
    - `CowFSCreationException` - A `CowFSFileSystemException` when the file system cannot be created.
    - `CowFSDirectoryNotEmptyException` - A `DirectoryNotEmptyException` specifically for a copy-on-write system.
    - `CowFSFileAlreadyExistsException` - A `FileAlreadyExistsException` specifically for a copy-on-write system.
    - `CowFSFileSystemException` - A `FileSystemException` specifically for a copy-on-write system.
    - `CowFSIllegalArgumentException` - An `IllegalArgumentException` when attempting to operate upon a copy-on-write system.
    - `CowFSNoSuchFileException` - A `NoSuchFileException` specifically for a copy-on-write system.
    - `CowFSNotDirectoryException` - A `NotDirectoryException` specifically for a copy-on-write system.
    - `CowFSSymlinkException` - A `CowFSCreationException` when attempting to use the copy-on-write system with a symlink.
- `net.minecraft.util.worldupdate`
    - `UpgradeProgress`
        - `getTotalFiles` -> `getTotalFileFixState`, not one-to-one
        - `addTotalFiles` -> `addTotalFileFixOperations`, not one-to-one
        - `getTypeFileFixStats`, `getRunningFileFixerStats` - Gets the fixer stats for the specific file group.
        - `incrementFinishedOperations`, `incrementFinishedOperationsBy` - Increments the number of operations that have finished.
        - `setType`, `getType` - Gets the type of the upgrade progress.
        - `setApplicableFixerAmount` - Sets the total number of finished operations for the running file fixers.
        - `incrementRunningFileFixer` - Increments the number of finished operations.
        - `logProgress` - Logs the progress of the upgrade every second.
        - `$FileFixStats` - A counter for the operations performed / finished.
        - `$Type` - The type of upgrade being performed on the world data.
    - `WorldUpgrader` no longer takes in the `WorldData`
        - `STATUS_*` messages have been combined in `UpgradeStatusTranslator`
            - This also contains the specific datafix type
        - `running`, `finished`, `progress`, `totalChunks`, `totalFiles`, `converted`, `skipped`, `progressMap`, `status` have all been moved into `UpgradeProgress`, stored in `upgradeProgress`
        - `getProgress` -> `getTotalProgress`, not one-to-one
        - `$AbstractUpgrader`, `$SimpleRegionStorageUpgrader` -> `RegionStorageUpgrader`, no longer taking in the supplied `LegacyTagFixer`, not one-to-one
            - `$ChunkUpgrader`, `$EntityUpgrader`, `$PoiUpgrader` are now just constructed within `WorldUpgrader#work`
            - `$Builder#setLegacyFixer` is removed
        - `$ChunkUpgrader#tryProcessOnePosition` has been partially abstracted into `getDataFixContentTag`, `verifyChunkPosAndEraseCache`, `verifyChunkPos`
        - `$FileToUpgrade` -> `FileToUpgrade`
- `net.minecraft.world.level.ChunkPos#getRegionX`, `getRegionZ` - Gets the region the chunk is in.
- `net.minecraft.world.level.chunk.ChunkGenerator#getTypeNameForDataFixer` now returns an optional `Identifier` instead of a `ResourceKey`
- `net.minecraft.world.level.chunk.storage`
    - `LegacyTagFixer` interface is removed
    - `RecreatingSimpleRegionStorage` no longer takes in the supplied `LegacyTagFixer`
    - `SimpleRegionStorage` no longer takes in the supplied `LegacyTagFixer`
        - `markChunkDone` is removed
- `net.minecraft.world.level.levelgen.structure`
    - `LegacyStructureDataHandler` class is removed
    - `StructureFeatureIndexSavedData` class is removed
- `net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager`
    - `STRUCTURE_RESOURCE_DIRECTORY_NAME` -> `STRUCTURE_DIRECTORY_NAME`, not one-to-one
    - `WORLD_STRUCTURE_LISTER`, `RESOURCE_TEXT_STRUCTURE_LISTER` - Id converters for the structure nbts.
    - `save` now has an overload that takes in the path, `StructureTemplate`, and `boolean` for whether to write the data as text
    - `createAndValidatePathToGeneratedStructure` -> `TemplatePathFactory#createAndValidatePathToStructure`, not one-to-one
    - `worldTemplates`, `testTemplates` - The template path factories.
- `net.minecraft.world.level.levelgen.structure.templatesystem.loader`
    - `DirectoryTemplateSource` - A template source for some directory.
    - `ResourceManagerTemplateSource` - A template source for the resource manager.
    - `TemplatePathFactory` - A factory that gets the path to some structure.
    - `TemplateSource` - A loader for structure templates. 
- `net.minecraft.world.level.storage`
    - `LevelStorageSource`
        - `getLevelDataAndDimensions` now takes in the `$LevelStorageAccess`
        - `readExistingSavedData` - Reads any existing saved data.
        - `writeGameRules` - Writes the game rules to the saved data.
        - `$LevelStorageAccess#releaseTemporarilyAndRun` - Releases the access and runs before recreating the lock.
            - `collectIssues` - Collects any issues when attempting to upgrade the data.
            - `getSummary` -> `fixAndGetSummary`, `fixAndGetSummaryFromTag`; not one-to-one
            - `getDataTag` -> `getUnfixedDataTag`, not one-to-one
            - `getDataTagFallback` -> `getUnfixedDataTagWithFallback`, not one-to-one
            - `saveDataTag` no longer takes in the `RegistryAccess`
            - `saveLevelData` - Saves the level dat.
    - `LevelSummary` now takes in whether it requires file fixing
        - `UPGRADE_AND_PLAY_WORLD` - A component telling the user to upgrade their world and play.
        - `requiresFileFixing` - Whether the level requires file fixing.
        - `$BackupStatus#FILE_FIXING_REQUIRED` - Whether file fixing is require to play this level on this version.

### Chat Permissions

The chat system now has an associated set of permissions indicating what messages the player can send or receive. `Permissions#CHAT_SEND_MESSAGES` and `CHAT_SEND_COMMANDS` determine if the player can send messages or commands, respectively. Likewise, `CHAT_RECEIVE_PLAYER_MESSAGES` and `CHAT_RECEIVE_SYSTEM_MESSAGES` if the player can receive messages from other players or the system, respectively. Note that all of these permissions are only handled clientside and are not validated serverside.

- `net.minecraft.SharedConstants#DEBUG_CHAT_DISABLED` - A flag that disables the chat box.
- `net.minecraft.client`
    - `GuiMessage` -> `.multiplayer.chat.GuiMessage`
    - `GuiMessageTag` -> `.multiplayer.chat.GuiMessageTag`
    - `Minecraft`
        - `getChatStatus` -> `computeChatAbilities`, not one-to-one
        - `$ChatStatus` -> `ChatRestriction`, not one-to-one
- `net.minecraft.client.gui.Gui#setChatDisabledByPlayerShown`, `isShowingChatDisabledByPlayer` - Handles whether the chat is disabled by the shown player. 
- `net.minecraft.client.gui.components`
    - `ChatComponent`
        - `GO_TO_RESTRICTIONS_SCREEN` - An identifier for redirecting to the restrictions screen.
        - `setVisibleMessageFilter` - Sets the message filter function.
        - `render`, `captureClickableText` now take in the `$DisplayMode` instead of a `boolean` for if the player is chatting
        - `addMessage` is now private
            - Split for use into `addClientSystemMessage`, `addServerSystemMessage`, `addPlayerMessage`
        - `$DisplayMode` - How the chat should be displayed.
    - `CommandSuggestions`
        - `USAGE_FORMAT`, `USAGE_OFFSET_FROM_BOTTOM`, `LINE_HEIGHT` - Constants for showing the command suggestions.
        - `setRestrictions` - Sets the restrictions of the messages that can be typed.
        - `hasAllowedInput` - Whether the chat box can allow input.
- `net.minecraft.client.gui.screens.ChatScreen#USAGE_BACKGROUND_COLOR` - The background color of the command suggestions box.
- `net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen` - A screen for settings the restrictions of the player's chat box.
- `net.minecraft.client.gui.screens.reporting.ReportPlayerScreen` now takes in a `boolean` for if the chat is disabled or blocked
- `net.minecraft.client.multiplayer.chat`
    - `ChatAbilities` - The set of restrictions the player has on chatting with the chat box.
    - `ChatListener`
        - `handleSystemMessage` `boolean` is now used for if the system is remote instead of overlay
            - The overlay is now handled through `handleOverlay`
    - `GuiMessageSource` - The source the message came from.
- `net.minecraft.client.player.LocalPlayer` now takes in the `ChatAbilities`
    - `chatAbilities`, `refreshChatAbilities` - Handles the chat abilities.
- `net.minecraft.server.permissions.Permissions`
    - `CHAT_SEND_MESSAGES` - If the player can send chat messages.
    - `CHAT_SEND_COMMANDS` - If the player can send commands.
    - `CHAT_RECEIVE_PLAYER_MESSAGES` - If the player can receive other player messages.
    - `CHAT_RECEIVE_SYSTEM_MESSAGES` - If the player can receive system messages.
    - `CHAT_PERMISSIONS` - A set of available chat permissions.
- `net.minecraft.world.entity.player.Player#displayClientMessage` split into `sendSystemMessage` when overlay message was `false`, and `sendOverlayMessage` when the overlay message was `true`

### More Entity Sound Variant Registries

Cats, chickens, cows, and pigs now have sound variants: a databack registry object that specifies the sounds an entity plays. This does not need to be directly tied to an actual entity variant, as it only defines the sounds an entity plays, typically during `Mob#finalizeSpawn`.

For cows:

```json5
// A file located at:
// - `data/examplemod/cow_sound_variant/example_cow_sound.json`
{
    // The registry name of the sound event to play randomly on idle.
    "ambient_sound": "minecraft:entity.cow.ambient",
    // The registry name of the sound event to play when killed.
    "death_sound": "minecraft:entity.cow.death",
    // The registry name of the sound event to play when hurt.
    "hurt_sound": "minecraft:entity.cow.hurt",
    // The registry name of the sound event to play when stepping.
    "step_sound": "minecraft:entity.cow.step"
}
```

For chickens and pigs:

```json5
// A file located at:
// - `data/examplemod/chicken_sound_variant/example_chicken_sound.json`
{
    // The sounds played when an entity's age is greater than or equal to 0 (an adult).
    "adult_sounds": {
        // The registry name of the sound event to play randomly on idle.
        "ambient_sound": "minecraft:entity.chicken.ambient",
        // The registry name of the sound event to play when killed.
        "death_sound": "minecraft:entity.chicken.death",
        // The registry name of the sound event to play when hurt.
        "hurt_sound": "minecraft:entity.chicken.hurt",
        // The registry name of the sound event to play when stepping.
        "step_sound": "minecraft:entity.chicken.step"
    },
    // The sounds played when an entity's age is less than 0 (a baby).
    "baby_sounds": {
        "ambient_sound": "minecraft:entity.baby_chicken.ambient",
        "death_sound": "minecraft:entity.baby_chicken.death",
        "hurt_sound": "minecraft:entity.baby_chicken.hurt",
        "step_sound": "minecraft:entity.baby_chicken.step"
    }
}
```

For pigs:

```json5
// A file located at:
// - `data/examplemod/pig_sound_variant/example_pig_sound.json`
{
    // The sounds played when an entity's age is greater than or equal to 0 (an adult).
    "adult_sounds": {
        // The registry name of the sound event to play randomly on idle.
        "ambient_sound": "minecraft:entity.pig.ambient",
        // The registry name of the sound event to play when killed.
        "death_sound": "minecraft:entity.pig.death",
        // The registry name of the sound event to play when eating.
        "eat_sound": "minecraft:entity.pig.eat",
        // The registry name of the sound event to play when hurt.
        "hurt_sound": "minecraft:entity.pig.hurt",
        // The registry name of the sound event to play when stepping.
        "step_sound": "minecraft:entity.pig.step"
    },
    // The sounds played when an entity's age is less than 0 (a baby).
    "baby_sounds": {
        "ambient_sound": "minecraft:entity.baby_pig.ambient",
        "death_sound": "minecraft:entity.baby_pig.death",
        "eat_sound": "minecraft:entity.baby_pig.eat",
        "hurt_sound": "minecraft:entity.baby_pig.hurt",
        "step_sound": "minecraft:entity.baby_pig.step"
    }
}
```

For cats:

```json5
// A file located at:
// - `data/examplemod/cat_sound_variant/example_cat_sound.json`
{
    // The sounds played when an entity's age is greater than or equal to 0 (an adult).
    "adult_sounds": {
        // The registry name of the sound event to play randomly on idle when tamed.
        "ambient_sound": "minecraft:entity.cat.ambient",
        // The registry name of the sound event to play when non-tamed and tempted by food.
        "beg_for_food_sound": "minecraft:entity.cat.beg_for_food",
        // The registry name of the sound event to play when killed.
        "death_sound": "minecraft:entity.cat.death",
        // The registry name of the sound event to play when fed.
        "eat_sound": "minecraft:entity.cat.eat",
        // The registry name of the sound event to play when hissing, typically at a pursuing phantom.
        "hiss_sound": "minecraft:entity.cat.hiss",
        // The registry name of the sound event to play when hurt.
        "hurt_sound": "minecraft:entity.cat.hurt",
        // The registry name of the sound event to play when purring, typically when in love or lying down.
        "purr_sound": "minecraft:entity.cat.purr",
        // The registry name of the sound event to play randomly on idle when tamed 25% of the time.
        "purreow_sound": "minecraft:entity.cat.purreow",
        // The registry name of the sound event to play randomly on idle when not tamed.
        "stray_ambient_sound": "minecraft:entity.cat.stray_ambient"
    },
    // The sounds played when an entity's age is less than 0 (a baby).
    "baby_sounds": {
        "ambient_sound": "minecraft:entity.baby_cat.ambient",
        "beg_for_food_sound": "minecraft:entity.baby_cat.beg_for_food",
        "death_sound": "minecraft:entity.baby_cat.death",
        "eat_sound": "minecraft:entity.baby_cat.eat",
        "hiss_sound": "minecraft:entity.baby_cat.hiss",
        "hurt_sound": "minecraft:entity.baby_cat.hurt",
        "purr_sound": "minecraft:entity.baby_cat.purr",
        "purreow_sound": "minecraft:entity.baby_cat.purreow",
        "stray_ambient_sound": "minecraft:entity.baby_cat.stray_ambient"
    }
}
```

- `net.minecraft.core.registries.Registries`
    - `CAT_SOUND_VARIANT` - The registry key for the sounds a cat should make.
    - `CHICKEN_SOUND_VARIANT` - The registry key for the sounds a chicken should make.
    - `COW_SOUND_VARIANT` - The registry key for the sounds a cow should make.
    - `PIG_SOUND_VARIANT` - The registry key for the sounds a pig should make.
- `net.minecraft.network.synched.EntityDataSerializers`
    - `CAT_SOUND_VARIANT` - The entity serializer for the sounds a cat should make.
    - `CHICKEN_SOUND_VARIANT` - The entity serializer for the sounds a chicken should make.
    - `COW_SOUND_VARIANT` - The entity serializer for the sounds a cow should make.
    - `PIG_SOUND_VARIANT` - The entity serializer for the sounds a pig should make.
- `net.minecraft.sounds.SoundEvents`
    - `CAT_*` sounds are now either `Holder$Reference`s or stored in the map of `CAT_SOUNDS`
    - `CHICKEN_*` sounds are now either `Holder$Reference`s or stored in the map of `CHICKEN_SOUNDS`
    - `COW_*` sounds are stored in the map of `COW_SOUNDS`
    - `PIG_*` sounds are now either `Holder$Reference`s or stored in the map of `PIG_SOUNDS`
- `net.minecraft.world.entity.animal.chicken`
    - `ChickenSoundVariant` - The sounds that are played for a chicken variant.
    - `ChickenSoundVariants` - All vanilla chicken variants.
- `net.minecraft.world.entity.animal.cow`
    - `AbstractCow#getSoundSet` - Gets the sounds a cow makes.
    - `CowSoundVariant` - The sounds that are played for a cow variant.
    - `CowSoundVariants` - All vanilla cow variants.
- `net.minecraft.world.entity.animal.feline`
    - `CatSoundVariant` - The sounds that are played for a cat variant.
    - `CatSoundVariants` - All vanilla cat variants.
- `net.minecraft.world.entity.animal.chicken`
    - `ChickenSoundVariant` - The sounds that are played for a chicken variant.
    - `ChickenSoundVariants` - All vanilla chicken variants.
- `net.minecraft.world.entity.animal.pig`
    - `PigSoundVariant` - The sounds that are played for a pig variant.
    - `PigSoundVariants` - All vanilla pig variants.

### Audio Changes

Audio devices are now handled through the `DeviceTracker` which, depending on the backing machine, allows for either the machine to send system events when audio devices change, or using a standard polling interval to requery the list of available devices.

- `com.mojang.blaze3d.audio`
    - `AbstractDeviceTracker` - An abstract tracker for managing the changes of audio devices.
    - `CallbackDeviceTracker` - A device tracker that uses the SOFT system events callback to determine changes.
    - `DeviceList` - A list of all audio devices, including the default device, if available.
    - `DeviceTracker` - An interface for managing the changes of audio devices.
    - `Library`
        - `NO_DEVICE` is now `public` from `private`
        - `init` now takes in the `DeviceList`
        - `getDefaultDeviceName` moved to `DeviceList#defaultDevice`
        - `getCurrentDeviceName` -> `currentDeviceName`
        - `hasDefaultDeviceChanged` moved to `AbstractDeviceTracker`, not one-to-one
        - `getAvailableSoundDevices` moved to `DeviceList`, not one-to-one
        - `createDeviceTracker` - Creates the tracker for managing audio device changes.
        - `getDebugString` -> `getChannelDebugString`
    - `PollingDeviceTracker` - A device tracker that uses polling intervals to determine changes.
    - `SoundBuffer`
        - `format` - The format of the sound stream.
        - `size` - The number of bytes in the sound stream.
        - `isValid` - If the sound stream is in a valid state.
- `net.minecraft.client.Options`
    - `DEFAULT_SOUND_DEVICE` is now `private` from `public`
    - `isSoundDeviceDefault` - Checks if the device is the default audio device.
- `net.minecraft.client.gui.components.debug`
    - `DebugEntrySoundCache` - A debug entry displaying the current cache for loaded sound streams.
    - `DebugScreenEntries#SOUND_CACHE` - The identifier for the sound stream cache debug information.
- `net.minecraft.client.sounds`
    - `SoundBufferLibrary`
        - `enumerate` - Loops through all cached sounds, outputting its id, size, and format.
        - `$DebugOutput` - An interface that accepts some sound metadata.
            - `$Counter` - An output implementation that keepts track of the number of sound streams and the total size.
    - `SoundEngine`
        - `getDebugString` -> `getChannelDebugString`, `getSoundCacheDebugStats`; not one-to-one
        - `$DeviceCheckState` is removed
    - `SoundManager#getDebugString` -> `getChannelDebugString`, `getSoundCacheDebugStats`; not one-to-one

### Input Message Editor Support

Minecraft now has support for Input Message Editors (IME), which allow complex characters from languages like Chinese or Hindi to be inputted instead of the temporary preedit text. The preedit text is displayed as an overlay within the game, while any other IME features provided by the host OS. With this addition, support can be added within screens through `GuiEventListener#preeditUpdated`, or by using an existing vanilla widget which implements the method.

- `com.mojang.blaze3d.platform`
    - `InputConstants#setupKeyboardCallbacks` now takes in the `GLFWCharCallbackI` instead of `GLFWCharModsCallbackI` for the character typed callback, `GLFWPreeditCallbackI` to handle inputting complex characters via an input method editor, and `GLFWIMEStatusCallbackI` for notifying about the status of the editor
    - `MessageBox` - A utility for creating OS native messages boxes. 
    - `TextInputManager` - A manager for handling when text is inputted in the game window.
        - `setTextInputArea` - Sets the area where the predicted text cursor should appear.
        - `startTextInput`, `stopTextInput` - Handles toggling the input message editor.
- `net.minecraft.client`
    - `KeyboardHandler`
        - `resubmitLastPreeditEvent` - Repeats the last preedit event that was sent.
        - `submitPreeditEvent` - Tells the listener that the preedit text has been updated.
    - `Minecraft`
        - `textInputManager` - Returns the input manager.
        - `onTextInputFocusChange` - Changes the editor input status based on if the text input is focused.
- `net.minecraft.client.gui.GuiGraphics`
    - `setPreeditOverlay` - Sets the overlay drawing the preedit text.
    - `renderDeferredElements` now takes in the `int`s for the mouse position and the game time delta ticks `float`
- `net.minecraft.client.gui.components`
    - `IMEPreeditOverlay` - The overlay for displaying the preedit text for an input message editor.
    - `TextCursorUtils` - A utility for drawing the text cursor.
- `net.minecraft.client.gui.components.events.GuiEventListener#preeditUpdated` - Listens for when the preedit text changes.
- `net.minecraft.client.input`
    - `CharacterEvent` no longer takes in the modifier `int` set
    - `PreeditEvent` - An event containing the preedit text along with the cursor location.

### Cauldron Interaction Dispatchers

Cauldron interactions have been reorganized somewhat, with all registration being moved to `CauldronInteractions` from `CauldronInteraction`. In addition, the backing `$InteractionMap` for a cauldron type has been replaced with a `$Dispatcher` that can register interactions for both tags and items. Tags are checked before items.

```java
CauldronInteractions.EMPTY.put(
    // The Item or TagKey to use
    ItemTags.WOOL,
    // The cauldron interaction to apply
    (state, level, pos, player, hand, itemInHand) -> InteractionResult.TRY_WITH_EMPTY_HAND
);
```

- `net.minecraft.core.cauldron`
    - `CauldronInteraction`
        - All fields regarding to registering interactions to a map have been moved to `CauldronInteractions`
            - `INTERACTIONS` -> `CauldronInteractions#ID_MAPPER`, now `private`
        - `DEFAULT` - The default interaction with a cauldron
        - `$InteractionMap` -> `$Dispatcher`, not one-to-one
    - `CauldronInteractions` - All vanilla cauldron interactions.

### Rule-Based Block State Providers

`RuleBasedStateProvider` now extends `BlockStateProvider`, allowing it to be used wherever a state provider is required. It's implementation hasn't changed, now only requiring `rule_based_state_provider` to be specified as the `type`. As such, all configurations that used `RuleBasedBlockStateProvider` have been broadened to allow any `BlockStateProvider`.

- `net.minecraft.world.level.levelgen.feature.configurations`
    - `DiskConfiguration` now takes in a `BlockStateProvider` instead of a `RuleBasedBlockStateProvider`
    - `TreeConfiguration` now takes in a `BlockStateProvider` instead of a `RuleBasedBlockStateProvider`
        - `belowTrunkProvider` is now a `BlockStateProvider` instead of a `RuleBasedBlockStateProvider`
        - `$TreeConfigurationBuilder` now takes in a `BlockStateProvider` instead of a `RuleBasedBlockStateProvider`
- `net.minecraft.world.level.levelgen.feature.stateproviders`
    - `BlockStateProvider#getOptionalState` - Gets the state of the block, or otherwise `null`.
    - `BlockStateProviderType#RULE_BASED_STATE_PROVIDER` - The type for the rule based state provider.
    - `RuleBasedBlockStateProvider` -> `RuleBasedStateProvider`, now a class, implementing `BlockStateProvider`
        - The constructor can now take in a nullable fallback `BlockStateProvider`
        - `ifTrueThenProvide` - If the predicate is true, provide the given block.
        - `simple` -> `always`
        - `$Builder` - A builder for constructing the rules for the block to place.

### Fluid Logic Reorganization

Generic entity fluid movement has been moved into a separate `EntityFluidInteraction` class, where all tracked fluids are updated and then potentially pushed by the current at a layer time. 

- `net.minecraft.world.entity`
    - `Entity`
        - `updateInWaterStateAndDoFluidPushing` -> `updateFluidInteraction`, not one-to-one
        - `updateFluidHeightAndDoFluidPushing` -> `EntityFluidInteraction#update`
        - `getFluidInteractionBox` - Gets the bounding box of the entity during fluid interactions.
        - `modifyPassengerFluidInteractionBox` - Returns the modified bounding box of the entity when riding in some vehicle. 
    - `EntityFluidInteraction` - A handler for managing the fluid interactions and basic movement with an entity.
- `net.minecraft.world.level.chunk.LevelChunkSection#hasFluid` - Whether the chunk section has a fluid block.

### Removal of Random Patch Feature

The random patch feature has been completely removed in favor of using placements like most other features. To convert, the `ConfiguredFeature` defined as part of the configuration should be its own file. Then, the placements should specify the `CountPlacement`, followed by the `RandomOffsetPlacement` using a `TrapezoidInt`, and finished with a `BlockPredicateFilter`:

```json5
// In `data/examplemod/worldgen/configured_feature/example_configured_feature.json`
{
    // Previously config.feature.feature
    "type": "minecraft:simple_block",
    "config": {
        "to_place": {
            "type": "minecraft:simple_state_provider",
            "state": {
                "Name": "minecraft:sweet_berry_bush",
                "Properties": {
                    "age": "3"
                }
            }
        }
    }
}

// In `data/examplemod/worldgen/placed_feature/example_placed_feature.json`
{
    "feature": "examplemod:example_configured_feature",
    "placement": [
        {
            // Previously config.tries
            "type": "minecraft:count",
            "count": 96
        },
        {
            "type": "minecraft:random_offset",
            "xz_spread": {
                // Previously config.xz_spread
                // The min and max are additive inverses
                "type": "minecraft:trapezoid",
                "max": 7,
                "min": -7,
                "plateau": 0
            },
            "y_spread": {
                // Previously config.y_spread
                "type": "minecraft:trapezoid",
                "max": 3,
                "min": -3,
                "plateau": 0
            }
        },
        {
            // Previously config.feature.placement
            // This contains the placements of the original placed feature
            "type": "minecraft:block_predicate_filter",
            "predicate": {
                "type": "minecraft:all_of",
                "predicates": [
                    {
                        "type": "minecraft:matching_block_tag",
                        "tag": "minecraft:air"
                    },
                    {
                        "type": "minecraft:matching_blocks",
                        "blocks": "minecraft:grass_block",
                        "offset": [ 0, -1, 0 ]
                    }
                ]
            }
        }
    ]
}
```

- `net.minecraft.data.worldgen.features`
    - `FeatureUtils#simpleRandomPatchConfiguration`, `simplePatchConfiguration` are removed
        - Typically replaced by `Feature#SIMPLE_BLOCK` with placements for random offset and filters
    - `NetherFeatures#PATCH_*` fields no longer has the `PATCH_*` prefix
    - `VegetationFeatures`
        - `PATCH_*` fields no longer has the `PATCH_*` prefix
        - `WILDFLOWERS_BIRCH_FOREST`, `WILDFLOWERS_MEADOW` -> `WILDFLOWER`, not one-to-one
        - `PALE_FOREST_FLOWERS` -> `PALE_FOREST_FLOWER`
- `net.minecraft.world.level.biome.BiomeGenerationSettings#getFlowerFeatures` -> `getBoneMealFeatures`
- `net.minecraft.world.level.levelgen.feature`
    - `ConfiguredFeature#getFeatures` -> `getSubFeatures`, now returning a stream of holders-wrapped `ConfiguredFeature`s without this feature
    - `Feature`
        - `FLOWER`, `NO_BONEMEAL_FLOWER` are removed
        - `RANDOM_PATCH` is removed
    - `RandomPatchFeature` class is removed
- `net.minecraft.world.level.levelgen.feature.configurations`
    - `FeatureConfiguration#getFeatures` -> `getSubFeatures`, now returning a stream of holders-wrapped `ConfiguredFeature`s
    - `RandomPatchConfiguration` record is removed
- `net.minecraft.world.level.levelgen.placement.PlacedFeature#getFeatures` now returns a stream of holders-wrapped `ConfiguredFeature`s with this feature concatenated

### Specific Logic Changes

- Picture-In-Picture submission calls are now taking in `0xF000F0` instead of `0x000000` for the light coordinates.
- `net.minecraft.client.multiplayer.RegistryDataCollector#collectGameRegistries` `boolean` parameter now handles only updating components from synchronized registries along with tags.
- `net.minecraft.client.renderer.RenderPipelines#VIGNETTE` now blends the alpha with a source of zero and a destination of one.
- `net.minecraft.server.packs.PathPackResources#getResource`, `listPath`, `listResources` resolves the path using the identifier's namespace first.
- `net.minecraft.world.entity.EntitySelector#CAN_BE_PICKED` can now find entities in spectator mode, assuming `Entity#isPickable` is true.
- `net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor#requires` is no longer implemented by default.
- `net.minecraft.world.level.levelgen.WorldOptions#generate_features` field in JSON has been renamed to `generate_structures` to match its java field name.
- `net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate#ONLY_IN_AIR_PREDICATE` now matches the air tag instead of just the air block.
- `net.minecraft.world.level.levelgen.placement.CountPlacement` can now have up to 4096 instances of the feature instead of up to 256.
- `net.minecraft.world.level.timers`
    - `FunctionCallback`, `FunctionTagCallback` now use `id` instead of `Name` when serializing
    - `TimerCallbacks` now uses `type` instead of `Type` when serializing

### Data Component Additions

- `dye` - Sets the item as a dye material, used in specific circumstances.
- `additional_trade_cost` - A modifier that offsets the trade cost by the specified amount.
- `pig/sound_variant` - The sounds a pig should make.
- `cow/sound_variant` - The sounds a cow should make.
- `chicken/sound_variant` - The sounds a chicken should make.
- `cat/sound_variant` - The sounds a cat should make.

### Environment Attribute Additions

- `visual/block_light_tint` - Tints the color of the light emitted by a block.
- `visual/night_vision_color` - The color when night vision is active.
- `visual/ambient_light_color` - The color of the ambient light in an environment.

### Tag Changes

- `minecraft:block`
    - `bamboo_plantable_on` -> `supports_bamboo`
    - `mushroom_grow_block` -> `overrides_mushroom_light_requirement`
    - `small_dripleaf_placeable` -> `supports_small_dripleaf`
    - `big_dripleaf_placeable` -> `supports_big_dripleaf`
    - `dry_vegetation_may_place_on` -> `supports_dry_vegetation`
    - `snow_layer_cannot_survive_on` -> `cannot_support_snow_layer`
    - `snow_layer_can_survive_on` -> `support_override_snow_layer`
    - `enables_bubble_column_drag_down`
    - `enables_bubble_column_push_up`
    - `supports_vegetation`
    - `supports_crops`
    - `supports_stem_crops`
    - `supports_stem_fruit`
    - `supports_pumpkin_stem`
    - `supports_melon_stem`
    - `supports_pumpkin_stem_fruit`
    - `supports_melon_stem_fruit`
    - `supports_sugar_cane`
    - `supports_sugar_cane_adjacently`
    - `supports_cactus`
    - `supports_chorus_plant`
    - `supports_chorus_flower`
    - `supports_nether_sprouts`
    - `supports_azalea`
    - `supports_warped_fungus`
    - `supports_crimson_fungus`
    - `supports_mangrove_propagule`
    - `supports_hanging_mangrove_propagule`
    - `supports_nether_wart`
    - `supports_crimson_roots`
    - `supports_warped_roots`
    - `supports_wither_rose`
    - `supports_cocoa`
    - `supports_lily_pad`
    - `supports_frogspawn`
    - `support_override_cactus_flower`
    - `cannot_support_seagrass`
    - `cannot_support_kelp`
    - `grows_crops`
    - `mud`
    - `moss_blocks`
    - `grass_blocks`
    - `substrate_overworld`
    - `beneath_tree_podzol_replaceable`
    - `beneath_bamboo_podzol_replaceable`
    - `cannot_replace_below_tree_trunk`
    - `ice_spike_replaceable`
    - `forest_rock_can_place_on`
    - `huge_brown_mushroom_can_place_on`
    - `huge_red_mushroom_can_place_on`
    - `prevents_nearby_leaf_decay`
- `minecraft:enchantment`
    - `trades/desert_special` is removed
    - `trades/jungle_special` is removed
    - `trades/plains_special` is removed
    - `trades/savanna_special` is removed
    - `trades/snow_special` is removed
    - `trades/swamp_special` is removed
    - `trades/taiga_special` is removed
- `minecraft:entity_type`
    - `cannot_be_age_locked`
- `minecraft:fluid`
    - `supports_sugar_cane_adjacently`
    - `supports_lily_pad`
    - `supports_frogspawn`
    - `bubble_column_can_occupy`
- `minecraft:item`
    - `metal_nuggets`
    - `dyeable` is removed, split between:
        - `dyes`
        - `loom_dyes`
        - `loom_patterns`
        - `cauldron_can_remove_due`
        - `cat_collar_dyes`
        - `wolf_collar_dyes`
    - `mud`
    - `moss_blocks`
    - `grass_blocks`
- `minecraft:potion`
    - `tradable`
- `minecraft:worldgen/configured_feature`
    - `can_spawn_from_bone_meal`

### List of Additions

- `net.minecraft.advancements.criterion`
    - `FoodPredicate` - A criterion predicate that can check the food level and saturation.
    - `MinMaxBounds`
        - `validateContainedInRange` - Returns a function which validates the target range and returns as a data result.
        - `$Bounds#asRange` - Converts the bounds into a `Range`.
- `net.minecraft.client`
    - `Minecraft#sendLowDiskSpaceWarning` - Sends a system toast for low disk space.
    - `Options#keyDebugLightmapTexture` - A key mapping to show the lightmap texture.
- `net.minecraft.client.gui.components`
    - `AbstractScrollArea`
        - `scrollbarWidth` - The width of the scrollbar.
        - `defaultSettings` - Constructs the default scrollbar settings given the scroll rate.
        - `$ScrollbarSettings` - A record containing the metadata for the scrollbar.
    - `DebugScreenOverlay`
        - `showLightmapTexture` - Whether to render the lightmap texture on the overlay.
        - `toggleLightmapTexture` - Toggles whether the lightmap texture should be rendered.
    - `ScrollableLayout`
        - `setMinHeight` - Sets the minimum height of the container layout.
        - `$ReserveStrategy` - What to use when reserving the width of the scrollbar within the layout.
    - `Tooltip`
        - `component` - The component of a tooltip to display.
        - `style` - The identifier used to compute the tooltip background and frame.
- `net.minecraft.client.gui.components.debug`
    - `DebugEntryDetailedMemory` - A debug entry displaying the detailed memory usage.
    - `DebugEntryLookingAt#getHitResult` - Gets the hit result of the camera entity.
    - `DebugEntryLookingAtEntityTags` - A debug entry for displaying an entity's tags.
    - `DebugScreenEntries`
        - `DETAILED_MEMORY` - The identifier for the detailed memory usage debug entry.
        - `LOOKING_AT_ENTITY_TAGS` - The identifier for the entity tags debug entry.
- `net.minecraft.client.gui.navigation.FocusNavigationEvent$ArrowNavigation#with` - Sets the previous focus of the navigation.
- `net.minecraft.client.gui.screens.GenericWaitingScreene#createWaitingWithoutButton` - Creates a waiting screen without showing the button to cancel.
- `net.minecraft.client.gui.screens.options`
    - `DifficultyButtons` - A class containing the layout element to create the difficulty buttons.
    - `HasGamemasterPermissionReaction` - An interface marking an option screen as able to respond to changing gamemaster permissions.
    - `OptionsScreen#getLastScreen` - Returns the previous screen that navigated to this one.
    - `WorldOptionsScreen` - A screen containing the options for the current world the player is in.
- `net.minecraft.client.gui.screens.worldselection.EditWorldScreen#conditionallyMakeBackupAndShowToast` - Only makes the backup and shows a toast if the passed in `boolean` is true; otherwise, returns a `false` future.
- `net.minecraft.client.multiplayer.MultiPlayerGameMode#spectate` - Sends a packet to the server that the player will spectate the given entity.
- `net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler#onTeleport` - Runs when a player is moved on the server, typically from some kind of teleportation (e.g. commands, riding an entity).
- `net.minecraft.client.renderer`
    - `LightmapRenderStateExtractor` - Extracts the render state for the lightmap.
    - `UiLightmap` - The lightmap when in a user interface.
    - `RenderPipelines`
        - `LINES_DEPTH_BIAS` - A render pipeline that sets the polygon depth offset factor to -1 and the units to -1.
        - `ENTITY_CUTOUT_DISSOLVE` - A render pipeline that dissolves an entity model into the background using a mask sampler.
- `net.minecraft.client.renderer.rendertype.RenderType#hasBlending` - Whether the pipeline has a defined blend function.
- `net.minecraft.commands.ArgumentVisitor` - A helper for visiting the arguments for a command.
- `net.minecraft.commands.arguments.selector.EntitySelector#COMPILABLE_CODEC` - A `CompilableString` codec wrapped around an `EntitySelectorParser`.
- `net.minecraft.core.component.predicates`
    - `DataComponentPredicates#VILLAGER_VARIANT` - A predicate that checks a villager type.
    - `VillagerTypePredicate` - A predicate that checks a villager's type.
- `net.minecraft.core.dispenser.SpawnEggItemBehavior` - The dispenser behavior for spawn eggs.
- `net.minecraft.core.registries.ConcurrentHolderGetter` - A getter that reads references from a local cache, synchronizing to the original when necessary.
- `net.minecraft.data`
    - `BlockFamilies`
        - `END_STONE` - A family for end stone variants.
        - `getFamily` - Gets the family for the base block, when present.
    - `BlockFamily`
        - `$Builder#tiles`, `$Variant#TILES` - The block that acts as the tiles variant for some base block.
        - `$Builder#bricks`, `$Variant#BRICKS` - The block that acts as the bricks variant for some base block.
        - `$Builder#cobbled`, `$Variant#COBBLED` - The block that acts as the cobbled variant from some base block.
    - `DataGenerator$Uncached` - A data generator which does not cache any information about the files generated.
- `net.minecraft.data.recipes.RecipeProvider`
    - `bricksBuilder`, `tilesBuilder` - Builders for the bricks and tiles block variants.
    - `generateCraftingRecipe`, `generateStonecutterRecipe` - Generates the appropriate recipes for the given block family.
    - `getBaseBlock` -> `getBaseBlockForCrafting`
    - `bredAnimal` - Unlocks the recipe if the player has bred two animals together.
- `net.minecraft.gametest.framework`
    - `GameTestEvent#createWithMinimumDelay` - Creates a test event with some minimum delay.
    - `GameTestHelper`
        - `getBoundsWithPadding` - Gets the bounding box of the test area with the specified padding.
        - `runBeforeTestEnd` - Runs the runnable at one tick before the test ends.
        - `despawnItem` - Despawns all item entities within the distance of the position.
        - `discard` - Discards the entity.
        - `setTime` - Sets the time of the dimension's default clock.
        - `placeBlock` - Places the given block at the relative position and direction.
    - `GameTestInstance#padding` - The number of blocks spaced around each game test.
    - `GameTestSequence#thenWaitAtLeast` - Waits for at least the specified number of ticks before running the runnable.
- `net.minecraft.nbt.TextComponentTagVisitor`
    - `$PlainStyling` - A styling that stores the nbt in a component literal map.
    - `$RichStyling` - A styling that stores the nbt with syntax highlighting and formatting.
    - `$Styling` - An interface defining how the read tag should be styled.
    - `$Token` - The tokens used to more richly represent the tag datas.
- `net.minecraft.network.chat.ResolutionContext` - The context that the component is being resolved to a string within.
- `net.minecraft.network.chat.contents.NbtContents#NBT_PATH_CODEC` - A `CompilableString` codec wrapped around a `NbtPathArgument$NbtPath`.
- `net.minecraft.network.chat.contents.data.BlockDataSource#BLOCK_POS_CDEC` - A `CompilableString` codec wrapped around `Coordinates`.
- `net.minecraft.network.protocol.game`
    - `ClientboundGameRuleValuesPacket` - A packet that sends the game rule values in string form to the client.
    - `ClientboundGamePacketListener#handleGameRuleValues` - Handles the game rule values sent from the server.
    - `ClientboundLowDiskSpaceWarningPacket` - A packet sent to the client warning about low disk space on the machine.
    - `ClientGamePacketListener#handleLowDiskSpaceWarning` - Handles the warning packet about low disk space.
    - `ServerboundAttackPacket` - A packet sent to the server about what entity the player attacked.
    - `ServerboundClientCommandPacket$Action#REQUEST_GAMERULE_VALUES` - Requests the game rule values from the server.
    - `ServerboundSetGameRulePacket` - A packet that sends the game rules entries to set from the client.
    - `ServerboundSpectateEntityPacket` - A packet sent to the server about what entity the player wants to spectate.
    - `ServerGamePacketListener`
        - `handleAttack` - Handles the player's attack on an entity.
        - `handleSpectateEntity` - Handles the player wanting to spectate an entity.
        - `handleSetGameRule` - Handles setting the game rules from the client.
- `net.minecraft.resources`
    - `FileToIdConverter#extensionMatches` - Checks if the identifier ends with the specified extension.
    - `Identifier`
        - `ALLOWED_NAMESPACE_CHARACTERS` - The characters allowed in an identifier's namespace.
        - `resolveAgainst` - Resolves the path from the given root by checking `/<namespace>/<path>`.
- `net.minecraft.server`
    - `Bootstrap#shutdownStdout` - Closes the stdout stream.
    - `MinecraftServer`
        - `DEFAULT_GAME_RULES` - The supplied default game rules for a server.
        - `warnOnLowDiskSpace` - Sends a warning if the disk space is below 64 MiB.
        - `sendLowDiskSpaceWarning` - Sends a warning for low disk space.
- `net.minecraft.server.commands.SwingCommand` - A command that calls `LivingEntity#swing` for all targets.
- `net.minecraft.server.level.ServerPlayer`
    - `sendBuildLimitMessage` - Sends an overlay message if the player cannot build anymore in the cooresponding Y direction.
    - `sendSpawnProtectionMessage` - Sends an overlay message if the player cannot modify the terrain due to spawn protection.
- `net.minecraft.server.packs.AbstractPackResources#loadMetadata` - Loads the root `pack.mcmeta`.
- `net.minecraft.server.packs.resources.ResourceMetadata$MapBased` - A resource metadata that stores the sections in a map.
- `net.minecraft.tags.TagLoader$ElementLookup#fromGetters` - Constructs an element lookup from the given `HolderGetter`s depending on whether the element is required or not.
- `net.minecraft.util`
    - `ARGB#gray` - Gets a grayscale color based on the given brightness.
    - `CompilableString` - A utility for taking some string pattern and compiling it to an object through some parser.
    - `LightCoordsUtil` - A utility for determining the light coordinates from light values.
    - `ProblemReporter$MapEntryPathElement` - A path element for some entry key in a map.
    - `Util#allOfEnumExcept` - Gets the set of all enums except the provided value.
- `net.minecraft.util.thread.BlockableEventLoop#hasDelayedCrash` - Whether there is a crash report queued.
- `net.minecraft.util.valueproviders.TrapezoidInt` - Samples a random value with a trapezoidal distribution.
- `net.minecraft.world.InteractionHand#STREAM_CODEC` - The network codec for the interaction hand.
- `net.minecraft.world.attribute`
    - `AttributeType#toFloat` - Gets the attribute value as a `float`, or throws an exception if not defined.
    - `AttributeTypes#INTEGER` - An integer attribute type.
    - `LerpFunction#ofInteger` - A lerp function for an integer value.
- `net.minecraft.world.attribute.modifier`
    - `AttributeModifier#INTEGER_LIBRARY` - A library of operators to apply for integers.
    - `IntegerModifier` - A modifier that applies some argument to the integer value.
- `net.minecraft.world.entity`
    - `AgeableMob`
        - `AGE_LOCK_COOLDOWN_TICKS` - The number of ticks to wait before the entity can be age locked/unlocked.
        - `ageLockParticleTimer` - The time for displaying particles while age locking/unlocking.
    - `Entity`
        - `applyEffectsFromBlocksForLastMovements` - Applies any block effects to this entity for the last movement, used by items.
        - `$Flags` - An annotation that marks a particular value as a bitset of flags for an entity.
    - `LivingEntity#getLiquidCollisionShape` - Return's the bounds of the entity when attempting to collide with a liquid.
    - `Mob`
        - `asValidTarget` - Checks whether an entity is a valid target (can attack) for this entity.
        - `getTargetUnchecked` - Gets the raw target without checking if its valid.
        - `canAgeUp` - If the entity can grow up into a more mature variant.
        - `setAgeLocked`, `isAgeLocked` - Whether the entity cannot grow up.
    - `NeutralMob#getTargetUnchecked` - Gets the raw target without checking if its valid.
    - `TamableAnimal#feed` - The player gives the stack as food, healing them either based on the stored component or some default value.
- `net.minecraft.world.entity.ai.behavior`
    - `BehaviorControl#getRequiredMemories` - The list of memories required by the behavior.
    - `GoAndGiveItemsToTarget$ItemThrower` - An interface that handles what should occur if an item is thrown because of this entity's behavior.
- `net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder$TriggerWithResult#memories` - The list of memories required by the behavior.
- `net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities#nearbyEntities` - The list of entities within the follow range of this one.
- `net.minecraft.world.entity.decoration.LeashFenceKnotEntity`
    - `getKnot` - Finds the knot at the given position, else returns an empty optional.
    - `createKnot` - Creates a new knot and adds it to the level.
- `net.minecraft.world.entity.monster.piglin`
    - `PiglinAi`
        - `MAX_TIME_BETWEEN_HUNTS` - The maximum number of seconds before a piglin begins to hunt again.
        - `findNearbyAdultPiglins` - Returns a list of all adult piglins in this piglin's memory.
- `net.minecraft.world.entity.raid.Raid`
    - `getBannerComponentPatch` - Gets the components of the banner pattern.
    - `getOminousBannerTemplate` - Gets the stack template for the omnious banner.
- `net.minecraft.world.inventory.SlotRanges`
    - `MOB_INVENTORY_SLOT_OFFSET` - The start index of a mob's inventory.
    - `MOB_INVENTORY_SIZE` - The size of a mob's inventory.
- `net.minecraft.world.item.component.BundleContents#BEEHIVE_WEIGHT` - The weight of a beehive.
- `net.minecraft.world.item.enchantment.EnchantmentTarget#NON_DAMAGE_CODEC` - A codec that only allows the attacker and victim enchantment targets.
- `net.minecraft.world.level.block.BigDripleafBlock#canGrowInto` - Whether the dripleaf can grow into the specified position.
- `net.minecraft.world.level.block.grower.TreeGrower#getMinimumHeight` - If present, gets the base height of the trunk placer.
- `net.minecraft.world.level.block.state`
    - `BlockBehaviour$PostProcess` - An interface that gets the position to mark for post-processing.
    - `StateDefinition`
        - `propertiesCodec` - The map codec for the state properties.
        - `isSingletonState` - If the definition only contains one state.
    - `StateHolder#isSingletonState` - If the holder only contains one state.
- `net.minecraft.world.level.block.state.properties`
    - `NoteBlockInstrument`
        - `TRUMPET` - The sound a copper block makes when placed under a note block.
        - `TRUMPET_EXPOSED` - The sound an exposed copper block makes when placed under a note block.
        - `TRUMPET_OXIDIZED` - The sound an oxidized copper block makes when placed under a note block.
        - `TRUMPET_WEATHERED` - The sound a weathered copper block makes when placed under a note block.
    - `Property$Value#valueName` - Gets the name of the value.
- `net.minecraft.world.level.dimension.DimensionDefaults`
    - `BLOCK_LIGHT_TINT` - The default tint for the block light.
    - `NIGHT_VISION_COLOR` - The default color when in night vision.
    - `TURTLE_EGG_HATCH_CHANCE` - The chance of a turtle egg hatching on a random tick.
- `net.minecraft.world.level.gamerules.GameRule#getIdentifierWithFallback` - Gets the identifier of the game rule, or else the unregistered identifier.
- `net.minecraft.world.level.levelgen`
    - `NoiseBasedChunkGenerator#getInterpolatedNoiseValue` - Gets the interpolated density at the given position, or the not-a-number value if the Y is outside the range of the noise settings.
    - `NoiseChunk#getInterpolatedDensity` - Computes the full noise density.
- `net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature#MIN_MUSHROOM_HEIGHT` - The minimum mushroom height.
- `net.minecraft.world.level.levelgen.feature.configurations.BlockBlobConfiguration` - The configuration for the block blob feature.
- `net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer#getBaseHeight` - Returns the minimum height of the trunk.
- `net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement#ofTriangle` - Creates a trapezoidal distribution to select from for the XZ and Y range.
- `net.minecraft.world.level.material`
    - `FluidState#isFull` - If the fluid amount is eight.
    - `LavaFluid#LIGHT_EMISSION` - The amount of light the lava fluid emits.
- `net.minecraft.world.level.pathfinder.PathType#BIG_MOBS_CLOSE_TO_DANGER` - A path malus for mobs with an entity width greater than a block.
- `net.minecraft.world.level.storage.LevelStorageSource#writeWorldGenSettings` - Wriets the world generation settings to its saved data location.
- `net.minecraft.world.level.storage.loot.functions`
    - `EnchantRandomlyFunction$Builder#withOptions` - Specifies the enchantments that can be used to randomly enchant this item.
    - `SetRandomDyesFunction` - An item function that applies a random dye (if the item is in the `dyeable` tag) to the `DYED_COLOR` component from the provided list.
    - `SetRandomPotionFunction` - An item function that applies a random potion to the `POTION_CONTENTS` component from the provided list.
- `net.minecraft.world.level.storage.loot.parameters.LootContextParams#ADDITIONAL_COST_COMPONENT_ALLOWED` - Enables a trade to incur an additional cost if desired by the wanted stack or modifiers.
- `net.minecraft.world.level.storage.loot.predicates.EnvironmentAttributeCheck` - A loot condition that checks whether that given attribute matches the provided value.
- `net.minecraft.world.level.storage.loot.providers.number`
    - `EnvironmentAttributeValue` - A provider that gets the attribute value as a float.
    - `Sum` - A provider that sums the value of all provided number providers.
- `net.minecraft.world.phys.AABB$Builder#isDefined` - Checks if there is at least one point making up the bounding box.

### List of Changes

- `net.minecraft.advancements.criterion`
    - `EntityTypePredicate#matches` now takes in a holder-wrapped `EntityType` instead of the raw type itself
    - `KilledTrigger$TriggerInstance#entityPredicate` -> `entity`
    - `PlayerPredicate` now takes in a `FoodPredicate`
        - Can be set using `PlayerPredicate$Builder#setFood`
- `net.minecraft.client.gui`
    - `GuiGraphics`
        - `blit` now has an overload that takes in the `GpuTextureView` and the `GpuSampler`
        - `setTooltipForNextFrame` now has an overload that takes in a list of `FormattedCharSequence`s for the tooltip and whether to replace any existing tooltip
    - `ItemSlotMouseAction#onSlotClicked` now takes in a `ContainerInput` instead of a `ClickType`
- `net.minecraft.client.gui.components`
    - `AbstractContainerWidget` now takes in an `AbstractScrollArea$ScrollbarSettings`
    - `AbstractScrollArea` now takes in an `AbstractScrollArea$ScrollbarSettings`
        - `scrollbarVisible` -> `scrollable`
        - `scrollBarY` is now public
        - `scrollRate` is now longer abstract
    - `AbstractTextAreaWidget` now takes in an `AbstractScrollArea$ScrollbarSettings`
    - `PopupScreen$Builder#setMessage` -> `addMessage`, not one-to-one
    - `ScrollableLayout$Container` now takes in an `AbstractScrollArea$ScrollbarSettings`
    - `Tooltip#create` now has an overload that takes in an optional `TooltipComponent` and style `Identifier`
- `net.minecraft.client.gui.components.debug`
    - `DebugEntryLookingAtBlock`, `DebugEntryLookingAtFluid` -> `DebugEntryLookingAt`
        - More specifically, `$BlockStateInfo`, `$BlockTagInfo`, `$FluidStateInfo`, `$FluidTagInfo`
    - `DebugEntryLookingAtEntity#GROUP` is now public
    - `DebugScreenEntries`
        - `LOOKING_AT_BLOCK` -> `LOOKING_AT_BLOCK_STATE`, `LOOKING_AT_BLOCK_TAGS`
        - `LOOKING_AT_FLUID` -> `LOOKING_AT_FLUID_STATE`, `LOOKING_AT_FLUID_TAGS`
    - `DebugScreenEntryList` now takes in a `DataFixer`
- `net.minecraft.client.gui.components.tabs.TabNavigationBar#setWidth` -> `updateWidth`, not one-to-one
- `net.minecraft.client.gui.navigation.FocusNavigationEvent$ArrowNavigation` now takes in a nullable `ScreenRectangle` for the previous focus
- `net.minecraft.client.gui.screens`
    - `ConfirmScreen#layout` is now final
    - `DemoIntroScreen` replaced by `ClientPacketListener#openDemoIntroScreen`, now private
    - `GenericWaitingScreen` now takes in three `boolean`s for showing the loading dots, the cancel button, and whether the screen should close on escape
- `net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen#mount` is now final
- `net.minecraft.client.gui.screens.options`
    - `OptionsScreen` now implements `HasGamemasterPermissionReaction`
        - The constructor now takes in a `boolean` for if the player is currently in a world
        - `createDifficultyButton` now handles within `WorldOptionsScreen#createDifficultyButtons`, not one-to-one
    - `WorldOptionsScreen` now implements `HasGamemasterPermissionReaction`
        - `createDifficultyButtons` -> `DifficultyButtons#create`, now public
- `net.minecraft.client.multiplayer`
    - `ClientLevel#syncBlockState` can now take in a nullable player position
    - `MultiPlayerGameMode#handleInventoryMouseClick` now takes in a `ContainerInput` instead of a `ClickType`
    - `WeatherEffectRenderer#render` no longer takes in the `MultiBufferSource`
- `net.minecraft.client.renderer.block.ModelBlockRenderer$Cache#getLightColor` -> `getLightCoords`
- `net.minecraft.client.renderer.blockentity.state`
    - `BrushableBlockRenderState#itemState` is now final
    - `EndPortalRenderState` is now a final set of `Direction`s rather than an `EnumSet`
    - `ShelfRenderState#items` is now final
- `net.minecraft.client.renderer.entity.state`
    - `FallingBlockRenderState#movingBlockRenderState` is now final
    - `HumanoidRenderState#attackArm` -> `ArmedEntityRenderState#attackArm`
    - `WitherRenderState#xHeadRots`, `yHeadRots` are now final
- `net.minecraft.client.resources.sounds.AbstractSoundInstance#random` is now final
- `net.minecraft.commands.SharedSuggestionProvider#getCustomTabSugggestions` -> `getCustomTabSuggestions`
- `net.minecraft.commands.arguments.ComponentArgument#getResolvedComponent` now takes in a non-nullable `Entity`
- `net.minecraft.commands.arguments.selector.SelectorPattern` replaced by `CompilableString`
- `net.minecraft.core.HolderGetter$Provider#get`, `getOrThrow` now has overloads that take in the `TagKey`
- `net.minecraft.data`
    - `BlockFamily`
        - `shouldGenerateRecipe` -> `shouldGenerateCraftingRecipe`, `shouldGenerateStonecutterRecipe`
        - `$Builder#dontGenerateRecipe` -> `dontGenerateCraftingRecipe`, `generateStonecutterRecipe`
    - `DataGenerator` is now abstract
        - The constructor now only takes in the `Path` output, not the `WorldVersion` or whether to always generate
            - The original implementation can be founded in `DataGenerator$Cached`
        - `run` is now abstract
    - `Main#addServerProviders` -> `addServerDefinitionProviders`, no longer taking in the dev `boolean`, not one-to-one
    - The remaining logic has been put into `addServerConverters`, taking in the dev `boolean` but not the report `boolean`
- `net.minecraft.data.loot.BlockLootSubProvider`
    - `explosionResistant` is now private
    - `enabledFeatures` is now private
    - `map` is now private
- `net.minecraft.data.recipes`
    - `RecipeProvider$FamilyRecipeProvider` -> `$FamilyCraftingRecipeProvider`, `$FamilyStonecutterRecipeProvider`
    - `SingleItemRecipeBuilder#stonecutting` moved to a parameter on the `BlockFamily`
- `net.minecraft.data.structures.SnbtToNbt` now has an overload that takes in a single input folder path
- `net.minecraft.gametest.framework`
    - `GameTestHelper`
        - `assertBlockPresent` now has an overload that only takes in the block to check for
        - `moveTo` now has overloads for taking in the `BlockPos` and `Vec3` for the position
        - `assertEntityInstancePresent` now has an overload that inflates (via a `double`) the bounding box to check entities within
    - `GameTestServer#create` now takes in an `int` for the number of times to run all matching tests
    - `StructureUtils#testStructuresDir` split into `testStructuresTargetDir`, `testStructuresSourceDir`
    - `TestData` now takes in an `int` for the number of blocks padding around the test
- `net.minecraft.nbt`
    - `NbtUtils`
        - `addDataVersion` now uses a generic for the `Dynamic` instead of the explicit nbt tag
        - `getDataVersion` now has an overload that defaults to -1 if the version is not specified
    - `TextComponentTagVisitor` can now take in a `$Styling` and a `boolean` of whether to sort the keys
        - `handleEscapePretty` is now a `private` instance method from `protected` static
- `net.minecraft.network.FriendlyByteBuf`
    - `readVec3`, `writeVec3` replaced with `Vec3#STREAM_CODEC`
    - `readLpVec3`, `writeLpVec3` replaced with `Vec3#LP_STREAM_CODEC`
- `net.minecraft.network.chat`
    - `Component`
        - `nbt` now takes in a `CompilableString` instead of a `String`
        - `object` now has an overload that takes in a `Component` fallback
    - `ComponentContents#resolve` now takes in a `ResolutionContext` instead of the `CommandSourceStack` and `Entity`
    - `ComponentUtils#updateForEnttiy` -> `resolve`, taking in the `ResolutionContext` instead of the `CommandSourceStack` and `Entity`
    - `LastSeenMessages#EMPTY` is now final
- `net.minecraft.network.chat.contents`
    - `NbtContents` is now a record, the constructor taking in a `CompilableString` instead of a `String`
    - `ObjectContents` now takes in an optional `Component` for the fallback if its contents cannot be validated
- `net.minecraft.network.chat.contents.data`
    - `BlockDataSource` now takes in a `CompilableString` for the `Coordinates` instead of the pattern and compiled position
    - `EntityDataSource` now takes in a `CompilableString` for the `EntitySelector` instead of the pattern and compiled selector
- `net.minecraft.network.chat.contents.objects.ObjectInfo#description` -> `defaultFallback`
- `net.minecraft.network.protocol.game`
    - `ClientboundSetEntityMotionPacket` is now a record
    - `ServerboundContainerClickPacket` now takes in a `ContainerInput` instead of a `ClickType`
    - `ServerboundInteractPacket` is now a record, now taking in the `Vec3` interaction location
- `net.minecraft.references`
    - `Blocks` -> `BlockIds`
    - `Items` -> `ItemIds`
- `net.minecraft.resources`
    - `FileToIdConverter` is now a record
    - `RegistryDataLoader#load` now returns a `CompletableFuture` of the frozen registry access
- `net.minecraft.server.MinecraftServer` now takes in a `boolean` of whether to propogate crashes, usually to throw a delayed crash
    - `throwIfFatalException` -> `BlockableEventLoop#throwDelayedException`, now private, not one-to-one
    - `setFatalException` -> `BlockableEventLoop#delayCrash`, `relayDelayCrash`; not one-to-one
- `net.minecraft.server.commands.ChaseCommand#DIMENSION_NAMES` is now final
- `net.minecraft.server.dedicated.DedicatedServerProperties#acceptsTransfers` is now final
- `net.minecraft.server.packs`
    - `BuiltInMetadata` has been merged in `ResourceMetadata`
        - `get` -> `ResourceMetadata#getSection`
        - `of` -> `ResourceMetadata#of`
    - `PathPackResources#getNamespaces` now has a static overload that takes in the root directory `Path`
    - `VanillaPackResourcesBuilder#setMetadata` now takes in a `ResourceMetadata` instead of a `BuiltInMetadata`
- `net.minecraft.server.players.OldUsersConverter#serverReadyAfterUserconversion` replaced by `areOldUserListsRemoves`, now `public`
- `net.minecraft.tags.TagLoader`
    - `loadTagsFromNetwork` now takes in a `Registry` instead of a `WritableRegistry`, returning a map of tag keys to a list of holder entries
    - `loadTagsForRegistry` now has an overload which takes in registry key along with an element lookup, returning a map of tag keys to a list of holder entries
- `net.minecraft.util`
    - `Brightness`
        - `pack` -> `LightCoordsUtil#pack`
        - `block` -> `LightCoordsUtil#block`
        - `sky` -> `LightCoordsUtil#sky`
    - `RandomSource#createNewThreadLocalInstance` -> `createThreadLocalInstance`
        - Now has an overload to specify the seed
    - `Util#copyAndAdd` now has an overload that takes in a varargs of elements
- `net.minecraft.util.thread`
    - `BlockableEventLoop` now takes in a `boolean` of whether to propogate crashes, usually to throw a delayed crash
    - `ReentrantBlockableEventLoop` now takes in a `boolean` of whether to propogate crashes, usually to throw a delayed crash
- `net.minecraft.world.InteractionResult$ItemContext#NONE`, `DEFAULT` are now final
- `net.minecraft.world.attribute`
    - `AttributeType` now takes in a `ToFloatFunction` for use in a number provider
        - `ofInterpolated` now takes in a `ToFloatFunction`
    - `EnvironmentAttributeReader#getValue` now has an overload that takes in the `LootContext`
- `net.minecraft.world.entity`
    - `Avatar` is now abstract
    - `Entity`
        - `fluidHeight` is now final
        - `getTags` -> `entityTags`
        - `getRandomY` now has an overload that specifies the spread `double`
    - `Leashable$Wrench#ZERO` is now final
    - `LivingEntity`
        - `interpolation` is now final
        - `swingingArm` is now nullable
        - `canAttackType` -> `canAttack`, not one-to-one, taking in the `LivingEntity` instead of the `EntityType`
        - `lungeForwardMaybe` -> `postPiercingAttack`
        - `entityAttackRange` -> `getAttackRangeWith`, now taking in the `ItemStack` used to attack
- `net.minecraft.world.entity.ai.behavior`
    - `GoAndGiveItemsToTarget` now takes in the `$ItemThrower`
        - `throwItem` -> `BehaviorUtils#throwItem`, not one-to-one
    - `SpearAttack` no longer takes in the approach distance `float`
    - `TryLaySpawnOnWaterNearLand` -> `TryLaySpawnOnFluidNearLand`, not one-to-one
- `net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder#sequence` now takes in a `Oneshot` for the second entry instead of a `Trigger`
- `net.minecraft.world.entity.ai.goal`
    - `BoatGoals` -> `FollowPlayerRiddenEntityGoal$FollowEntityGoal`
        - `BOAT` is replaced by `ENTITY`
    - `FollowBoatGoal` -> `FollowPlayerRiddenEntityGoal`, not one-to-one
- `net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal#targetConditions` is now final
- `net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor#getMemory` -> `getMemoryToSet`
- `net.minecraft.world.entity.decoration.Mannequin#getProfile` -> `Avatar#getProfile`, now `public` and `abstract`
    - Still implemented in `Mannequin`
- `net.minecraft.world.entity.item.ItemEntity#DEFAULT_HEALTH` is now `public` from `private`
- `net.minecraft.world.entity.monster.breeze.Breeze#idle`, `slide`, `slideBack`, `longJump`, `shoot`, `inhale` are now final
- `net.minecraft.world.entity.monster.piglin.PiglinAi#isZombified` now takes in the `Entity` instead of the `EntityType`
- `net.minecraft.world.entity.monster.warden.Warden#roarAnimationState`, `sniffAnimationState`, `emergeAnimationState`, `diggingAnimationState`, `attackAnimationState`, `sonicBoomAnimationState` are now final
- `net.minecraft.world.entity.monster.zombie.Zombie#handleAttributes` now takes in the `EntitySpawnReason`
- `net.minecraft.world.entity.player`
    - `Input#EMPTY` is now final
    - `Player`
        - `currentImpulseImpactPos` -> `LivingEntity#currentImpulseImpactPos`
        - `currentExplosionCause` -> `LivingEntity#currentExplosionCause`
        - `setIgnoreFallDamageFromCurrentImpulse` -> `LivingEntity#setIgnoreFallDamageFromCurrentImpulse`
        - `applyPostImpulseGraceTime` -> `LivingEntity#applyPostImpulseGraceTime`
        - `isIgnoringFallDamageFromCurrentImpulse` -> `LivingEntity#isIgnoringFallDamageFromCurrentImpulse`
        - `tryResetCurrentImpulseContext` -> `LivingEntity#tryResetCurrentImpulseContext`
        - `resetCurrentImpulseContext` -> `LivingEntity#resetCurrentImpulseContext`
        - `isInPostImpulseGraceTime` -> `LivingEntity#isInPostImpulseGraceTime`
        - `isWithinAttackRange` now takes in the `ItemStack` attacked with
- `net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior$MinecartStep#EMPTY` is now final
- `net.minecraft.world.inventory.AbstractContainerMenu#getQuickCraftPlaceCount` now takes in the number of quick craft slots instead of the set of slots itself
- `net.minecraft.world.item`
    - `BundleItem#getSelectedItem` -> `getSelectedItemIndex`
    - `CreativeModeTab$Output` is now `protected` from `public`
    - `EnderpearlItem#PROJECTILE_SHOOT_POWER` is now final
    - `Item$Properties#requiredFeatures` now has an overload that takes in a `FeatureFlagSet`
    - `Items#register*` methods are now `private` from `public`
    - `ItemUtils#onContainerDestroyed` now takes in a `Stream` of `ItemStack`s instead of an `Iterable`
    - `SignApplicator#tryApplyToSign`, `canApplyToSign` now take in the `ItemStack` being used
    - `SnowballItem#PROJECTILE_SHOOT_POWER` is now final
    - `ThrowablePotionItem#PROJECTILE_SHOOT_POWER` is now final
    - `WindChargeItem#PROJECTILE_SHOOT_POWER` is now final
- `net.minecraft.world.item.alchemy.Potions` now deal with `Holder$Reference`s instead of the super `Holder`
- `net.minecraft.world.item.component`
    - `AttackRange`
        - `minRange` -> `minReach`
        - `maxRange` -> `maxReach`
        - `minCreativeRange` -> `minCreativeReach`
        - `maxCreativeRange` -> `maxCreativeReach`
    - `BundleContents`
        - `weight` now returns a `DataResult`-wrapped `Fraction` instead of the raw object
        - `getSelectedItem` -> `getSelectedItemIndex`
    - `WrittenBookContent`
        - `tryCraftCopy` -> `craftCopy`
        - `resolveForItem`, `resolve` now take in the `ResolutionContext` and `HolderLookup$Provider` instead of the `CommandSourceStack` and `Player`
- `net.minecraft.world.item.enchantment`
    - `ConditionalEffect#codec` no longer takes in the `ContextKeySet`
    - `Enchantment#doLunge` -> `doPostPiercingAttack`
    - `EnchantmentHelper#doLungeEffects` -> `doPostPiercingAttackEffects`
    - `TargetedConditionalEffect#codec`, `equipmentDropsCodec` no longer take in the `ContextKeySet`
- `net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect#CODEC` -> `MAP_CODEC`
- `net.minecraft.world.item.equipment.Equippable#canBeEquippedBy` now takes in a holder-wrapped `EntityType` instead of the raw type itself
- `net.minecraft.world.item.trading.VillagerTrades#LIBRARIAN_5_EMERALD_NAME_TAG` was replaced with `LIBRARIAN_5_EMERALD_YELLOW_CANDLE`, `LIBRARIAN_5_EMERALD_RED_CANDLE`, not one-to-one
    - The original trade was moved to `WANDERING_TRADER_EMERALD_NAME_TAG`
- `net.minecraft.world.level`
    - `LevelAccessor` no longer implements `LevelReader`
    - `LevelHeightAccessor#isInsideBuildHeight` now has an overload that takes in the `BlockPos`
- `net.minecraft.world.level.block`
    - `BigDripleafBlock#canPlaceAt` now takes in the `LevelReader` instead of the `LevelHeightAccessor`, and no longer takes in the old state
    - `BubbleColumnBlock#updateColumn` now takes in the bubble column `Block`
    - `FireBlock#setFlammable` is now `private` from `public`
    - `MultifaceSpreader$DefaultSpreaderConfig#block` is now final
    - `SnowyDirtBlock` -> `SnowyBlock`
    - `SpreadingSnowyDirtBlock` -> `SpreadingSnowyBlock`
        - The constructor now takes in the `ResourceKey` for the 'base' block, or the block when the snow or any other decoration (e.g. grass) is removed
- `net.minecraft.world.level.block.entity.TestInstanceBlockEntity`  
    - `getTestBoundingBox` - The bounding box of the test inflated by its padding.
    - `getTestBounds` -  The axis aligned bounding box of the test inflated by its padding.
- `net.minecraft.world.level.block.entity.vault`
    - `VaultConfig#DEFAULT`, `CODEC` are now final
    - `VaultServerData#CODEC` is now final
    - `VaultSharedData#CODEC` is now final
- `net.minecraft.world.level.block.state`
    - `BlockBehaviour`
        - `$BlockStateBase` now takes in an array of `Property`s and `Comparable` values instead of one value map, and no longer takes in the `MapCodec`
            - `hasPostProcess` -> `getPostProcessPos`, not one-to-one
        `$Properties#hasPostProcess` -> `getPostProcessPos`, not one-to-one
    - `BlockState` now takes in an array of `Property`s and `Comparable` values instead of one value map, and no longer takes in the `MapCodec`
    - `StateHolder` now takes in an array of `Property`s and `Comparable` values instead of one value map, and no longer takes in the `MapCodec`
        - `populateNeighbours` -> `initializeNeighbors`, now package-private instead of `public`; not one-to-one
        - `getValues` now returns a stream of `Property$Value`s
        - `codec` now takes in the function that gets the state definition from some object
- `net.minecraft.world.level.chunk.ChunkAccess#blendingData` is now final
- `net.minecraft.world.level.chunk.storage.SimpleRegionStorage#upgradeChunkTag` now takes in an `int` for the target version
- `net.minecraft.world.level.gameevent.vibrations.VibrationSystem$Data#CODEC` is now final
- `net.minecraft.world.level.gamerules.GameRules` now has an overload that takes in a list of `GameRule`s
- `net.minecraft.world.level.levelgen`
    - `NoiseRouterData#caves` no longer takes in the `HolderGetter` for the `NormalNoise$NoiseParameters`
    - `WorldDimensions#keysInOrder` now takes in a set of `LevelStem` keys instead of a stream
- `net.minecraft.world.level.levelgen.blockpredicates`
    - `TrueBlockPredicate#INSTANCE` is now final
    - `UnobstructedPredicate#INSTANCE` is now final
- `net.minecraft.world.level.levelgen.feature`
    - `AbstractHugeMushrromFeature`
        - `isValidPosition` now takes in a `WorldGenLevel` instead of the `LevelAccessor`
        - `placeTrunk` now takes in the `WorldGenLevel` instead of the `LevelAccessor`
        - `makeCap` now takes in the `WorldGenLevel` instead of the `LevelAccessor`
    - `BlockBlobFeature` now uses a `BlockBlobConfiguration` generic
    - `Feature`
        - `FOREST_ROCK` replaced by `BLOCK_BLOB`
        - `ICE_SPIKE` replaced by `SPIKE`
    - `IceSpikeFeature` -> `SpikeFeature`, not one-to-one
        - `SpikeFeature` is now `EndSpikeFeature`, not one-to-one
            - `NUMBER_OF_SPIKES` -> `EndSpikeFeature#NUMBER_OF_SPIKES`
            - `getSpikesForLevel` -> `EndSpikeFeature#getSpikesForLevel`
- `net.minecraft.world.level.levelgen.feature.configurations`
    - `HugeMushroomFeatureConfiguration` is now a record, taking in a can place on `BlockPredicate`
    - `SpikeConfiguration` -> `EndSpikeConfiguration` is now a record
        - The original `SpikeConfiguration` is now for the ice spike, taking in the blocks to use, along with predicates of where to place and whether it can replace a block present
    - `TreeConfiguration`
        - `dirtProvider`, `forceDirt` have been replaced by `belowTrunkProvider`
            - `dirtProvider` commonly uses `CAN_PLACE_BELOW_OVERWORLD_TRUNKS`
            - `forceDirt` commonly uses `PLACE_BELOW_OVERWORLD_TRUNKS`
        - `$TreeConfigurationBuilder`
            - `dirtProvider`, `dirt`, `forceDirt` have been replaced by `belowTrunkProvider`
- `net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer`
    - `createFoliage` now takes in a `WorldGenLevel` isntead of a `LevelSimulatedReader`
    - `placeLeavesRow`, `placeLeavesRowWithHangingLeavesBelow` now take in a `WorldGenLevel` isntead of a `LevelSimulatedReader`
    - `tryPlaceExtension`, `tryPlaceLeaf` now take in a `WorldGenLevel` isntead of a `LevelSimulatedReader`
- `net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacer#placeRoots`, `placeRoot` now take in a `WorldGenLevel` instead of a `LevelSimulatedReader`
- `net.minecraft.world.level.levelgen.feature.stateproviders`
    - `BlockStateProvider#getState` now takes in the `WorldGenLevel`
- `net.minecraft.world.level.levelgen.feature.treedecorators`
    - `TreeDecorator$Context` now takes in a `WorldGenLevel` instead of the `LevelSimulatedReader`
        - `level` now returns a `WorldGenLevel` instead of the `LevelSimulatedReader`
- `net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer`
    - `placeTrunk` now takes in a `WorldGenLevel` instead of the `LevelSimulatedReader`
    - `setDirtAt` -> `placeBelowTrunkBlock`, now taking in a `WorldGenLevel` instead of the `LevelSimulatedReader`
    - `placeLog` now takes in a `WorldGenLevel` instead of the `LevelSimulatedReader`
    - `placeLogIfFree` now takes in a `WorldGenLevel` instead of the `LevelSimulatedReader`
    - `validTreePos` now takes in a `WorldGenLevel` instead of the `LevelSimulatedReader`
    - `isFree` now takes in a `WorldGenLevel` instead of the `LevelSimulatedReader`
- `net.minecraft.world.level.levelgen.placement.BiomeFilter#CODEC` is now final
- `net.minecraft.world.level.levelgen.structure.TemplateStructurePiece#template`, `placeSettings` are now final
- `net.minecraft.world.level.levelgen.structure.pools.alias`
    - `DirectPoolAlias#CODEC` is now final
    - `RandomGroupPoolAlias#CODEC` is now final
    - `RandomPoolAlias#CODEC` is now final
- `net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings#CODEC` is now final
- `net.minecraft.world.level.levelgen.synth.PerlinNoise#getValue` no longer takes in the `boolean` for whether to flat the Y value instead of applying the frequency factor
- `net.minecraft.world.level.material.FluidState` now takes in an array of `Property`s and `Comparable` values instead of one value map, and no longer takes in the `MapCodec`
- `net.minecraft.world.level.pathfinder.PathType`
    - `DANGER_POWDER_SNOW` -> `ON_TOP_OF_POWDER_SNOW`
    - `DANGER_FIRE` -> `FIRE_IN_NEIGHBOR`
    - `DAMAGE_FIRE` -> `FIRE`
    - `DANGER_OTHER` -> `DAMAGING_IN_NEIGHBOR`
    - `DAMAGE_OTHER` -> `DAMAGING`,
    - `DANGER_TRAPDOOR` -> `ON_TOP_OF_TRAPDOOR`
- `net.minecraft.world.level.saveddata.maps.MapItemSavedData#tickCarriedBy` now takes in a nullable `ItemFrame`
- `net.minecraft.world.level.storage.loot.functions`
    - `EnchantRandomlyFunction` now takes in a `boolean` of whether to include the additional trade cost component from the stack being enchanted
        - Set via `$Builder#includeAdditionalCostComponent`
    - `EnchantWithLevelsFunction` now takes in a `boolean` of whether to include the additional trade cost component from the stack being enchanted
        - Set via `$Builder#includeAdditionalCostComponent`
        - `$Builder#fromOptions` -> `withOptions`, now with overload to take in an optional `HolderSet`

### List of Removals

- `net.minecraft.SharedConstants#USE_WORKFLOWS_HOOKS`
- `net.minecraft.client.data.models.BlockModelGenerators#createGenericCube`
- `net.minecraft.client.Minecraft#delayCrashRaw`
- `net.minecraft.client.gui.components.EditBox#setFilter`
- `net.minecraft.client.multiplayer.ClientPacketListener#getId`
- `net.minecraft.client.renderer.Sheets`
    - `shieldSheet`
    - `bedSheet`
    - `shulkerBoxSheet`
    - `signSheet`
    - `hangingSignSheet`
    - `chestSheet`
- `net.minecraft.client.renderer.rendertype.RenderTypes#weather`
- `net.minecraft.data.loot.BlockLootSubProvider(Set, FeatureFlagSet, Map, HolderLookup$Provider)`
- `net.minecraft.gametest.framework.StructureUtils#DEFAULT_TEST_STRUCTURES_DIR`
- `net.minecraft.nbt.NbtUtils`
    - `addCurrentDataVersion`
    - `prettyPrint(Tag)`
- `net.minecraft.server.packs.AbstractPackResources#getMetadataFromStream`
- `net.minecraft.server.players.PlayerList#getSingleplayerData`
- `net.minecraft.util`
    - `Mth#createInsecureUUID`
    - `LightCoordsUtil#UI_FULL_BRIGHT`
- `net.minecraft.world`
    - `ContainerListener`
    - `Difficulty#getKey`
    - `SimpleContainer#addListener`, `removeListener`
- `net.minecraft.world.entity.ai.memory.MemoryModuleType#INTERACTABLE_DOORS`
- `net.minecraft.world.entity.monster.Zoglin#MEMORY_TYPES`
- `net.minecraft.world.entity.monster.creaking.Creaking#MEMORY_TYPES`
- `net.minecraft.world.entity.monster.hogling.Hoglin#MEMORY_TYPES`
- `net.minecraft.world.item`
    - `BundleItem#hasSelectedItem`
    - `Item#getName()`
    - `ItemStack`
        - `isFramed`, `getFrame`
        - `setEntityRepresentation`, `getEntityRepresentation`
- `net.minecraft.world.item.component`
    - `BundleContents`
        - `getItemUnsafe`
        - `hasSelectedItem`
    - `WrittenBookContent#MAX_CRAFTABLE_GENERATION`
- `net.minecraft.world.level.block.LiquidBlock#SHAPE_STABLE`
- `net.minecraft.world.level.levelgen.feature`
    - `Feature#isStone`, `isDirt`, `isGrassOrDirt`
- `net.minecraft.world.level.levelgen.material.WorldGenMaterialRule`
- `net.minecraft.world.level.storage.loot.functions.SetOminousBottleAmplifierFunction#amplifier`
