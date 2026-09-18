#!/usr/bin/env python3
"""Generates CropLite's blockstates, models, loot tables, recipes and tags.

Also copies/renames PNGs from the workspace `textures/` folder into
`src/main/resources/assets/croplite/textures/`.

Run from the project root: python3 tools/generate_assets.py
"""

import json
import os
import shutil

NS = "croplite"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets", NS)
DATA = os.path.join(ROOT, "src/main/resources/data", NS)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")
TEXTURE_SRC = os.path.join(ROOT, "textures")
TEXTURE_DST = os.path.join(ASSETS, "textures")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def block_model(name, obj):
    write(os.path.join(ASSETS, "models/block", name + ".json"), obj)


def item_model(name, obj):
    write(os.path.join(ASSETS, "models/item", name + ".json"), obj)


def item_def(name, model, tints=None):
    entry = {"type": "minecraft:model", "model": model}
    if tints:
        entry["tints"] = tints
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": entry})


def loot(name, obj):
    obj["random_sequence"] = f"{NS}:blocks/{name}"
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), obj)


def recipe(name, obj):
    write(os.path.join(DATA, "recipe", name + ".json"), obj)


# --------------------------------------------------------------------------
# Crop stage models
# --------------------------------------------------------------------------

# Shared by tomato / pepper / eggplant / sweet potato.
CROP_STAGES = ["crop_stage0", "crop_stage1", "crop_stage2", "crop_stage3"]
AGE_TO_STAGE = [0, 1, 2, 2, 3, 3, 3, 3]

# Cucumber (+ cantelope plant sheets share the same art for growth).
CUCUMBER_STAGES = ["cucumber_stage0", "cucumber_stage1", "cucumber_stage2", "cucumber_plant_mature"]
CUCUMBER_AGE_TO_STAGE = [0, 1, 1, 1, 2, 2, 3, 3]

# Oats / beans / rice share early stages, then diverge at maturity.
GRAIN_STAGES = ["grain_stage0", "grain_stage1", "grain_stage2", "grain_stage3"]
GRAIN_AGE_TO_STAGE = [0, 1, 2, 2, 3, 3, 3, 3]

# Crop geometry with tintindex so greyscale plant sheets take biome foliage color.
# Do not parent minecraft:block/crop — vanilla crop.json has no tintindex, and a
# minecraft-namespace override is not reliable against the default pack.
TINTED_CROP_PARENT = f"{NS}:block/tinted_crop"
block_model("tinted_crop", {
    "ambientocclusion": False,
    "textures": {"particle": "#crop"},
    "elements": [
        {
            "from": [4, -1, 0],
            "to": [4, 15, 16],
            "shade": False,
            "faces": {
                "west": {"uv": [0, 0, 16, 16], "texture": "#crop", "tintindex": 0},
                "east": {"uv": [16, 0, 0, 16], "texture": "#crop", "tintindex": 0},
            },
        },
        {
            "from": [12, -1, 0],
            "to": [12, 15, 16],
            "shade": False,
            "faces": {
                "west": {"uv": [16, 0, 0, 16], "texture": "#crop", "tintindex": 0},
                "east": {"uv": [0, 0, 16, 16], "texture": "#crop", "tintindex": 0},
            },
        },
        {
            "from": [0, -1, 4],
            "to": [16, 15, 4],
            "shade": False,
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#crop", "tintindex": 0},
                "south": {"uv": [16, 0, 0, 16], "texture": "#crop", "tintindex": 0},
            },
        },
        {
            "from": [0, -1, 12],
            "to": [16, 15, 12],
            "shade": False,
            "faces": {
                "north": {"uv": [16, 0, 0, 16], "texture": "#crop", "tintindex": 0},
                "south": {"uv": [0, 0, 16, 16], "texture": "#crop", "tintindex": 0},
            },
        },
    ],
})

for stage in CROP_STAGES + CUCUMBER_STAGES + GRAIN_STAGES:
    block_model(stage, {"parent": TINTED_CROP_PARENT, "textures": {"crop": f"{NS}:block/{stage}"}})

# Greyscale foliage + untinted fruit/grain overlay (biome tint must not multiply fruit).
CROP_WITH_FRUIT = [
    "tomato_plant_fruiting",
    "eggplant_plant_fruiting",
    "coffee_plant_fruiting",
    "beans_plant_mature",
    "oats_rice_plant_mature_lower",
    "oats_plant_mature_upper",
    "rice_plant_mature_upper",
]

for extra in [
    "tomato_plant_mature",
    "pepper_plant_fruiting",
    "cucumber_plant_fruiting", "cucumber_plant_tilted",
]:
    block_model(extra, {"parent": TINTED_CROP_PARENT, "textures": {"crop": f"{NS}:block/{extra}"}})

for extra in CROP_WITH_FRUIT:
    block_model(extra, {
        "parent": f"{NS}:block/crop_with_fruit",
        "textures": {
            "crop": f"{NS}:block/{extra}",
            "fruit": f"{NS}:block/{extra}_fruit",
        },
    })

for i in range(4):
    block_model(f"basil_stage{i}", {"parent": TINTED_CROP_PARENT, "textures": {"crop": f"{NS}:block/basil_stage{i}"}})

# Coffee: 3 grow stages + mature/fruiting.
COFFEE_STAGES = ["coffee_stage0", "coffee_stage1", "coffee_stage2"]
COFFEE_AGE_TO_STAGE = [0, 0, 1, 1, 2, 2, 2, 2]
for stage in COFFEE_STAGES + ["coffee_plant_mature"]:
    block_model(stage, {"parent": TINTED_CROP_PARENT, "textures": {"crop": f"{NS}:block/{stage}"}})

# Garlic: 5 growth textures mapped across ages 0-7.
GARLIC_STAGES = ["garlic_stage0", "garlic_stage1", "garlic_stage2", "garlic_stage3", "garlic_stage4"]
GARLIC_AGE_TO_STAGE = [0, 1, 1, 2, 2, 3, 3, 4]
for stage in GARLIC_STAGES:
    block_model(stage, {"parent": TINTED_CROP_PARENT, "textures": {"crop": f"{NS}:block/{stage}"}})


def stage_model(age):
    return f"{NS}:block/{CROP_STAGES[AGE_TO_STAGE[age]]}"


def cucumber_stage_model(age):
    return f"{NS}:block/{CUCUMBER_STAGES[CUCUMBER_AGE_TO_STAGE[age]]}"


def grain_stage_model(age):
    return f"{NS}:block/{GRAIN_STAGES[GRAIN_AGE_TO_STAGE[age]]}"


def coffee_stage_model(age):
    return f"{NS}:block/{COFFEE_STAGES[COFFEE_AGE_TO_STAGE[age]]}"


# --------------------------------------------------------------------------
# Fruiting crops
# --------------------------------------------------------------------------

FRUITING = {
    "pepper_crop": (stage_model, f"{NS}:block/crop_stage3", f"{NS}:block/pepper_plant_fruiting"),
    "eggplant_crop": (stage_model, f"{NS}:block/crop_stage3", f"{NS}:block/eggplant_plant_fruiting"),
    "cucumber_crop": (cucumber_stage_model, f"{NS}:block/cucumber_plant_mature", f"{NS}:block/cucumber_plant_fruiting"),
    "coffee_crop": (coffee_stage_model, f"{NS}:block/coffee_plant_mature", f"{NS}:block/coffee_plant_fruiting"),
}

for name, (early_model, mature, fruiting) in FRUITING.items():
    variants = {}
    for age in range(8):
        for fruit in (False, True):
            if age == 7:
                model = fruiting if fruit else mature
            else:
                model = early_model(age)
            variants[f"age={age},fruiting={str(fruit).lower()}"] = {"model": model}
    blockstate(name, {"variants": variants})

# Tomato is two blocks tall and both halves show fruit.
tomato_variants = {}
for age in range(8):
    for fruit in (False, True):
        for half in ("lower", "upper"):
            if age == 7:
                model = f"{NS}:block/tomato_plant_fruiting" if fruit else f"{NS}:block/tomato_plant_mature"
            else:
                model = stage_model(age)
            tomato_variants[f"age={age},fruiting={str(fruit).lower()},half={half}"] = {"model": model}
blockstate("tomato_crop", {"variants": tomato_variants})

# --------------------------------------------------------------------------
# Plain and tall crops
# --------------------------------------------------------------------------

for name in ("sweet_potato_crop",):
    blockstate(name, {"variants": {f"age={age}": {"model": stage_model(age)} for age in range(8)}})

BASIL_AGE_TO_STAGE = [0, 1, 1, 2, 2, 2, 2, 3]
blockstate("basil_crop", {
    "variants": {f"age={age}": {"model": f"{NS}:block/basil_stage{BASIL_AGE_TO_STAGE[age]}"} for age in range(8)}
})

blockstate("garlic_crop", {
    "variants": {f"age={age}": {"model": f"{NS}:block/{GARLIC_STAGES[GARLIC_AGE_TO_STAGE[age]]}"} for age in range(8)}
})

# Oats and rice are two blocks tall and use distinct mature top textures.
GRAIN_MATURE = {
    "oats_crop": {
        "lower": f"{NS}:block/oats_rice_plant_mature_lower",
        "upper": f"{NS}:block/oats_plant_mature_upper",
    },
    "rice_crop": {
        "lower": f"{NS}:block/oats_rice_plant_mature_lower",
        "upper": f"{NS}:block/rice_plant_mature_upper",
    },
}

for name, mature in GRAIN_MATURE.items():
    variants = {}
    for age in range(8):
        for half in ("lower", "upper"):
            if age == 7:
                model = mature[half]
            else:
                model = grain_stage_model(age)
            variants[f"age={age},half={half}"] = {"model": model}
    blockstate(name, {"variants": variants})

# Beans is a single block that finishes on its own final texture.
blockstate("beans_crop", {
    "variants": {
        f"age={age}": {
            "model": f"{NS}:block/beans_plant_mature" if age == 7 else grain_stage_model(age)
        }
        for age in range(8)
    }
})

# --------------------------------------------------------------------------
# Cantelope (vanilla melon behaviour, custom textures)
# --------------------------------------------------------------------------

block_model("cantelope", {
    "parent": "minecraft:block/cube_column",
    "textures": {"end": f"{NS}:block/cantelope_top", "side": f"{NS}:block/cantelope_side"},
})
blockstate("cantelope", {"variants": {"": {"model": f"{NS}:block/cantelope"}}})

# Stem art is not part of the supplied textures, so the vanilla melon stem is reused.
for age in range(8):
    block_model(f"cantelope_stem_stage{age}", {
        "parent": f"minecraft:block/stem_growth{age}",
        "textures": {"stem": "minecraft:block/melon_stem"},
    })
block_model("attached_cantelope_stem", {
    "parent": "minecraft:block/stem_fruit",
    "textures": {"stem": "minecraft:block/attached_melon_stem", "upperstem": "minecraft:block/attached_melon_stem"},
})

blockstate("cantelope_stem", {
    "variants": {f"age={age}": {"model": f"{NS}:block/cantelope_stem_stage{age}"} for age in range(8)}
})
blockstate("attached_cantelope_stem", {
    "variants": {
        "facing=east": {"model": f"{NS}:block/attached_cantelope_stem", "y": 180},
        "facing=north": {"model": f"{NS}:block/attached_cantelope_stem", "y": 90},
        "facing=south": {"model": f"{NS}:block/attached_cantelope_stem", "y": 270},
        "facing=west": {"model": f"{NS}:block/attached_cantelope_stem"},
    }
})

# --------------------------------------------------------------------------
# Fruit trees
# --------------------------------------------------------------------------

for tree in ("peach", "lemon", "banana"):
    block_model(f"{tree}_leaves", {
        "parent": f"{NS}:block/fruit_leaves",
        "textures": {
            "all": f"{NS}:block/{tree}_leaves",
            "fruit": f"{NS}:block/{tree}_leaves_fruit",
        },
    })
    blockstate(f"{tree}_leaves", {"variants": {"": {"model": f"{NS}:block/{tree}_leaves"}}})

    block_model(f"{tree}_sapling", {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/{tree}_sapling"}})
    blockstate(f"{tree}_sapling", {
        "variants": {f"stage={stage}": {"model": f"{NS}:block/{tree}_sapling"} for stage in (0, 1)}
    })

# Banana stalk (log-like pillar)
block_model("banana_stalk", {
    "parent": "minecraft:block/cube_column",
    "textures": {"end": f"{NS}:block/banana_stalk_top", "side": f"{NS}:block/banana_stalk"},
})
block_model("banana_stalk_horizontal", {
    "parent": "minecraft:block/cube_column_horizontal",
    "textures": {"end": f"{NS}:block/banana_stalk_top", "side": f"{NS}:block/banana_stalk"},
})
blockstate("banana_stalk", {
    "variants": {
        "axis=y": {"model": f"{NS}:block/banana_stalk"},
        "axis=z": {"model": f"{NS}:block/banana_stalk_horizontal", "x": 90},
        "axis=x": {"model": f"{NS}:block/banana_stalk_horizontal", "x": 90, "y": 90},
    }
})

# --------------------------------------------------------------------------
# Item models
# --------------------------------------------------------------------------

GENERATED_ITEMS = [
    "hay", "tomato", "pepper", "eggplant", "cucumber",
    "tomato_seeds", "pepper_seeds", "eggplant_seeds", "cucumber_seeds", "basil_seeds",
    "sweet_potato", "baked_sweet_potato", "oats", "beans", "rice", "basil",
    "cantelope_slice", "peach", "lemon", "banana", "lemonade", "oatmeal", "rice_and_beans",
    "peach_sapling", "lemon_sapling", "banana_sapling",
    "coffee_bean", "roasted_coffee_bean", "bottle_of_coffee", "cactus_flesh", "garlic",
]

for name in GENERATED_ITEMS:
    item_model(name, {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    item_def(name, f"{NS}:item/{name}")

# No cantelope seed art was supplied, so the vanilla melon seed sprite is reused.
item_model("cantelope_seeds", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/melon_seeds"}})
item_def("cantelope_seeds", f"{NS}:item/cantelope_seeds")

for name in ("cantelope", "banana_stalk"):
    item_def(name, f"{NS}:block/{name}")

# Inventory tint matches vanilla oak leaves (#48B518).
OAK_LEAF_ITEM_TINT = {"type": "minecraft:constant", "value": -12012264}
for name in ("peach_leaves", "lemon_leaves", "banana_leaves"):
    item_def(name, f"{NS}:block/{name}", tints=[OAK_LEAF_ITEM_TINT])

# --------------------------------------------------------------------------
# Loot tables
# --------------------------------------------------------------------------


def state_condition(block, **props):
    return {
        "condition": "minecraft:block_state_property",
        "block": f"{NS}:{block}",
        "properties": {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()},
    }


def item_entry(name, conditions=None, functions=None):
    entry = {"type": "minecraft:item", "name": name}
    if conditions:
        entry["conditions"] = conditions
    if functions:
        entry["functions"] = functions
    return entry


def pool(entries, conditions=None):
    p = {"rolls": 1.0, "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


EXPLOSION_DECAY = {"function": "minecraft:explosion_decay"}
FORTUNE_BONUS = {
    "function": "minecraft:apply_bonus",
    "enchantment": "minecraft:fortune",
    "formula": "minecraft:binomial_with_bonus_count",
    "parameters": {"extra": 3, "probability": 0.5714286},
}

# Single-tall fruiting crops: seeds always, fruit if it was carrying fruit.
def uniform_count(min_count, max_count):
    return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(min_count), "max": float(max_count)}}


for block, seed, fruit, min_fruit, max_fruit in (
    ("pepper_crop", "pepper_seeds", "pepper", 4, 6),
    ("eggplant_crop", "eggplant_seeds", "eggplant", 2, 4),
    ("cucumber_crop", "cucumber_seeds", "cucumber", 4, 6),
    ("coffee_crop", "coffee_bean", "coffee_bean", 1, 2),
):
    fruit_entry = item_entry(f"{NS}:{fruit}") if min_fruit == max_fruit == 1 else item_entry(
        f"{NS}:{fruit}", functions=[uniform_count(min_fruit, max_fruit)]
    )
    loot(block, {
        "type": "minecraft:block",
        "functions": [EXPLOSION_DECAY],
        "pools": [
            pool([item_entry(f"{NS}:{seed}")]),
            pool([fruit_entry], conditions=[state_condition(block, fruiting=True)]),
        ],
    })

# Tomato only drops from its lower half; right-click / break while fruiting yields 4-6.
loot("tomato_crop", {
    "type": "minecraft:block",
    "functions": [EXPLOSION_DECAY],
    "pools": [
        pool([item_entry(f"{NS}:tomato_seeds")], conditions=[state_condition("tomato_crop", half="lower")]),
        pool(
            [item_entry(f"{NS}:tomato", functions=[uniform_count(4, 6)])],
            conditions=[state_condition("tomato_crop", half="lower"), state_condition("tomato_crop", fruiting=True)],
        ),
    ],
})

# Sweet potato behaves like vanilla potatoes.
loot("sweet_potato_crop", {
    "type": "minecraft:block",
    "functions": [EXPLOSION_DECAY],
    "pools": [
        pool([item_entry(f"{NS}:sweet_potato")]),
        pool([item_entry(f"{NS}:sweet_potato", functions=[FORTUNE_BONUS])],
             conditions=[state_condition("sweet_potato_crop", age=7)]),
    ],
})

# Garlic: immature returns 1 bulb; mature yields 2-3 bulbs.
loot("garlic_crop", {
    "type": "minecraft:block",
    "functions": [EXPLOSION_DECAY],
    "pools": [
        pool([item_entry(f"{NS}:garlic")],
             conditions=[{"condition": "minecraft:inverted", "term": state_condition("garlic_crop", age=7)}]),
        pool([item_entry(f"{NS}:garlic", functions=[uniform_count(2, 3)])],
             conditions=[state_condition("garlic_crop", age=7)]),
    ],
})

# Grains and legumes: immature returns 1 crop; mature yields 2-4 crop + 1-3 hay.
# Only oats and rice are two blocks tall, so only they restrict drops to the lower half.
CROP_DROP = uniform_count(2, 4)
HAY_DROP = uniform_count(1, 3)

for block, crop, tall in (("oats_crop", "oats", True), ("beans_crop", "beans", False), ("rice_crop", "rice", True)):
    base = [state_condition(block, half="lower")] if tall else []
    mature = base + [state_condition(block, age=7)]
    immature = base + [{"condition": "minecraft:inverted", "term": state_condition(block, age=7)}]
    loot(block, {
        "type": "minecraft:block",
        "functions": [EXPLOSION_DECAY],
        "pools": [
            pool([item_entry(f"{NS}:{crop}")], conditions=immature),
            pool([item_entry(f"{NS}:{crop}", functions=[CROP_DROP])], conditions=mature),
            pool([item_entry(f"{NS}:hay", functions=[HAY_DROP])], conditions=mature),
        ],
    })

# Basil yields more leaves the further along it has grown; mature also drops 2-4 seeds.
# Immature breaks return 1 seed so the crop stays replantable after switching off plantable leaves.
BASIL_DROPS = [(1, [0]), (2, [1, 2]), (4, [3, 4, 5, 6]), (6, [7])]
basil_children = []
for count, ages in BASIL_DROPS:
    condition = {"condition": "minecraft:any_of", "terms": [state_condition("basil_crop", age=a) for a in ages]}
    basil_children.append(item_entry(
        f"{NS}:basil",
        conditions=[condition],
        functions=[{"function": "minecraft:set_count", "count": float(count)}],
    ))
loot("basil_crop", {
    "type": "minecraft:block",
    "functions": [EXPLOSION_DECAY],
    "pools": [
        pool([{"type": "minecraft:alternatives", "children": basil_children}]),
        pool(
            [item_entry(f"{NS}:basil_seeds")],
            conditions=[{"condition": "minecraft:inverted", "term": state_condition("basil_crop", age=7)}],
        ),
        pool(
            [item_entry(f"{NS}:basil_seeds", functions=[uniform_count(2, 4)])],
            conditions=[state_condition("basil_crop", age=7)],
        ),
    ],
})

# Cantelope mirrors the vanilla melon block and stem.
loot("cantelope", {
    "type": "minecraft:block",
    "pools": [pool([{
        "type": "minecraft:alternatives",
        "children": [
            item_entry(f"{NS}:cantelope", conditions=[{
                "condition": "minecraft:match_tool",
                "predicate": {"predicates": {"minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}
                ]}},
            }]),
            item_entry(f"{NS}:cantelope_slice", functions=[
                uniform_count(6, 9),
                EXPLOSION_DECAY,
            ]),
        ],
    }])],
})

stem_functions = []
for age in range(8):
    stem_functions.append({
        "function": "minecraft:set_count",
        "conditions": [state_condition("cantelope_stem", age=age)],
        "count": {"type": "minecraft:binomial", "n": 3.0, "p": round((age + 1) / 15.0, 8)},
    })
loot("cantelope_stem", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1.0,
        "functions": [EXPLOSION_DECAY],
        "entries": [item_entry(f"{NS}:cantelope_seeds", functions=stem_functions)],
    }],
})
loot("attached_cantelope_stem", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1.0,
        "functions": [EXPLOSION_DECAY],
        "entries": [item_entry(f"{NS}:cantelope_seeds", functions=[
            {"function": "minecraft:set_count", "count": {"type": "minecraft:binomial", "n": 3.0, "p": 0.53333336}}
        ])],
    }],
})

SHEARS_OR_SILK = {
    "condition": "minecraft:any_of",
    "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
        {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}
        ]}}},
    ],
}
NOT_SHEARS_OR_SILK = {"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}

# Fruit tree leaves: own sapling at vanilla oak rates, plus fruit drop chance.
# Peach/lemon 15%; banana 5%.
for tree, fruit, fruit_chance in (("peach", "peach", 0.15), ("lemon", "lemon", 0.15), ("banana", "banana", 0.05)):
    loot(f"{tree}_leaves", {
        "type": "minecraft:block",
        "pools": [
            pool([{
                "type": "minecraft:alternatives",
                "children": [
                    item_entry(f"{NS}:{tree}_leaves", conditions=[SHEARS_OR_SILK]),
                    item_entry(f"{NS}:{tree}_sapling", conditions=[
                        {"condition": "minecraft:survives_explosion"},
                        {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                         "chances": [0.05, 0.0625, 0.083333336, 0.1]},
                    ]),
                ],
            }]),
            pool([item_entry("minecraft:stick", conditions=[
                {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                 "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]},
            ], functions=[
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}},
                EXPLOSION_DECAY,
            ])], conditions=[NOT_SHEARS_OR_SILK]),
            pool([item_entry(f"{NS}:{fruit}", conditions=[
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:random_chance", "chance": fruit_chance},
            ])], conditions=[NOT_SHEARS_OR_SILK]),
        ],
    })

    loot(f"{tree}_sapling", {
        "type": "minecraft:block",
        "pools": [pool([item_entry(f"{NS}:{tree}_sapling", conditions=[{"condition": "minecraft:survives_explosion"}])])],
    })

loot("banana_stalk", {
    "type": "minecraft:block",
    "pools": [pool([item_entry(f"{NS}:banana_stalk", conditions=[{"condition": "minecraft:survives_explosion"}])])],
})

# --------------------------------------------------------------------------
# Recipes
# --------------------------------------------------------------------------


def shapeless(name, ingredients, result, count=1, category="misc"):
    obj = {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
           "result": {"id": result}}
    if count != 1:
        obj["result"]["count"] = count
    recipe(name, obj)


shapeless("string_from_hay", [f"{NS}:hay"] * 4, "minecraft:string")

for fruit, seed in (("tomato", "tomato_seeds"), ("pepper", "pepper_seeds"),
                    ("eggplant", "eggplant_seeds"), ("cucumber", "cucumber_seeds")):
    shapeless(seed, [f"{NS}:{fruit}"], f"{NS}:{seed}", count=4)

shapeless("cantelope_seeds", [f"{NS}:cantelope_slice"], f"{NS}:cantelope_seeds")
shapeless("cantelope", [f"{NS}:cantelope_slice"] * 9, f"{NS}:cantelope", category="building")
shapeless("lemonade", ["minecraft:sugar", "minecraft:potion", f"{NS}:lemon"], f"{NS}:lemonade")
shapeless("oatmeal", [f"{NS}:oats", "minecraft:bowl"], f"{NS}:oatmeal")
shapeless("rice_and_beans", ["minecraft:bowl", f"{NS}:rice", f"{NS}:beans"], f"{NS}:rice_and_beans")
# Cactus flesh comes from breaking cactus (2–4); 4 flesh craft back into a block.
shapeless("cactus_from_flesh", [f"{NS}:cactus_flesh"] * 4, "minecraft:cactus", category="building")

# Vanilla cactus drops cactus flesh instead of the block (craft 4 flesh → cactus).
write(os.path.join(MC_DATA, "loot_table/blocks/cactus.json"), {
    "type": "minecraft:block",
    "pools": [
        pool(
            [item_entry(f"{NS}:cactus_flesh", functions=[uniform_count(2, 4), EXPLOSION_DECAY])],
            conditions=[{"condition": "minecraft:survives_explosion"}],
        )
    ],
    "random_sequence": "minecraft:blocks/cactus",
})

shapeless("sticks_from_banana_stalk", [f"{NS}:banana_stalk"], "minecraft:stick", count=8)

# Bottle of coffee: water bottle in center, coffee beans around (8).
recipe("bottle_of_coffee", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["CCC", "CBC", "CCC"],
    "key": {
        "C": f"{NS}:coffee_bean",
        "B": "minecraft:potion",
    },
    "result": {"id": f"{NS}:bottle_of_coffee"},
})

# Garlic seasoning: same food item + HasGarlic component (no damage on eat).
GARLIC_FOODS = [
    "minecraft:beef", "minecraft:cooked_beef",
    "minecraft:porkchop", "minecraft:cooked_porkchop",
    "minecraft:chicken", "minecraft:cooked_chicken",
    "minecraft:mutton", "minecraft:cooked_mutton",
    "minecraft:rabbit", "minecraft:cooked_rabbit",
    "minecraft:cod", "minecraft:cooked_cod",
    "minecraft:salmon", "minecraft:cooked_salmon",
    "minecraft:potato", "minecraft:baked_potato",
    f"{NS}:sweet_potato", f"{NS}:baked_sweet_potato",
    f"{NS}:rice_and_beans",
]
for food_id in GARLIC_FOODS:
    safe = food_id.replace(":", "_")
    recipe(f"garlic_{safe}", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [f"{NS}:garlic", food_id],
        "result": {
            "id": food_id,
            "components": {
                f"{NS}:has_garlic": {},
            },
        },
    })

for kind, time, experience in (("smelting", 200, 0.35), ("smoking", 100, 0.35), ("campfire_cooking", 600, 0.35)):
    recipe(f"baked_sweet_potato_from_{kind}", {
        "type": f"minecraft:{kind}",
        "category": "food",
        "ingredient": f"{NS}:sweet_potato",
        "result": {"id": f"{NS}:baked_sweet_potato"},
        "experience": experience,
        "cookingtime": time,
    })
    recipe(f"roasted_coffee_bean_from_{kind}", {
        "type": f"minecraft:{kind}",
        "category": "food",
        "ingredient": f"{NS}:coffee_bean",
        "result": {"id": f"{NS}:roasted_coffee_bean"},
        "experience": experience,
        "cookingtime": time,
    })

# --------------------------------------------------------------------------
# Tags
# --------------------------------------------------------------------------

CROPS = ["tomato_crop", "pepper_crop", "eggplant_crop", "cucumber_crop", "sweet_potato_crop",
         "basil_crop", "oats_crop", "beans_crop", "rice_crop", "coffee_crop", "garlic_crop"]
LEAVES = ["peach_leaves", "lemon_leaves", "banana_leaves"]
SAPLINGS = ["peach_sapling", "lemon_sapling", "banana_sapling"]


def tag(kind, name, values):
    write(os.path.join(MC_DATA, "tags", kind, name + ".json"), {"values": values})


tag("block", "crops", [f"{NS}:{c}" for c in CROPS] + [f"{NS}:cantelope_stem"])
tag("block", "maintains_farmland", [f"{NS}:{c}" for c in CROPS] + [f"{NS}:cantelope_stem", f"{NS}:attached_cantelope_stem"])
tag("block", "leaves", [f"{NS}:{leaf}" for leaf in LEAVES])
tag("block", "saplings", [f"{NS}:{s}" for s in SAPLINGS])
tag("block", "logs", [f"{NS}:banana_stalk"])
tag("block", "logs_that_burn", [f"{NS}:banana_stalk"])
tag("block", "mineable/hoe", [f"{NS}:{leaf}" for leaf in LEAVES])
tag("block", "mineable/axe", [f"{NS}:cantelope", f"{NS}:banana_stalk"])
tag("block", "sword_efficient", [f"{NS}:{c}" for c in CROPS] + [f"{NS}:cantelope_stem", f"{NS}:attached_cantelope_stem"])
tag("item", "leaves", [f"{NS}:{leaf}" for leaf in LEAVES])
tag("item", "saplings", [f"{NS}:{s}" for s in SAPLINGS])
tag("item", "logs", [f"{NS}:banana_stalk"])
tag("item", "logs_that_burn", [f"{NS}:banana_stalk"])

# --------------------------------------------------------------------------
# Serene Seasons fertility tags (optional; no hard dependency)
# Untagged crops stay fertile year-round. Listed crops only grow in tagged seasons.
# --------------------------------------------------------------------------

SS_DATA = os.path.join(ROOT, "src/main/resources/data/sereneseasons")


def ss_tag(kind, name, values):
    write(os.path.join(SS_DATA, "tags", kind, name + ".json"), {"replace": False, "values": values})


# Tomato / pepper / eggplant / cucumber / cantelope / coffee → spring + summer
SPRING_SUMMER_BLOCKS = [
    f"{NS}:tomato_crop", f"{NS}:pepper_crop", f"{NS}:eggplant_crop",
    f"{NS}:cucumber_crop", f"{NS}:cantelope_stem", f"{NS}:coffee_crop",
]
SPRING_SUMMER_ITEMS = [
    f"{NS}:tomato_seeds", f"{NS}:pepper_seeds", f"{NS}:eggplant_seeds",
    f"{NS}:cucumber_seeds", f"{NS}:cantelope_seeds", f"{NS}:coffee_bean",
]

# Beans / rice → spring + summer
BEANS_RICE_BLOCKS = [f"{NS}:beans_crop", f"{NS}:rice_crop"]
BEANS_RICE_ITEMS = [f"{NS}:beans", f"{NS}:rice"]

# Oats / garlic → spring + summer + autumn
OATS_BLOCKS = [f"{NS}:oats_crop", f"{NS}:garlic_crop"]
OATS_ITEMS = [f"{NS}:oats", f"{NS}:garlic"]

# Peach / lemon / banana saplings → spring + summer + autumn
TREE_BLOCKS = [f"{NS}:peach_sapling", f"{NS}:lemon_sapling", f"{NS}:banana_sapling"]
TREE_ITEMS = [f"{NS}:peach_sapling", f"{NS}:lemon_sapling", f"{NS}:banana_sapling"]

SPRING_BLOCKS = SPRING_SUMMER_BLOCKS + BEANS_RICE_BLOCKS + OATS_BLOCKS + TREE_BLOCKS
SPRING_ITEMS = SPRING_SUMMER_ITEMS + BEANS_RICE_ITEMS + OATS_ITEMS + TREE_ITEMS
SUMMER_BLOCKS = SPRING_BLOCKS
SUMMER_ITEMS = SPRING_ITEMS
AUTUMN_BLOCKS = OATS_BLOCKS + TREE_BLOCKS
AUTUMN_ITEMS = OATS_ITEMS + TREE_ITEMS

ss_tag("block", "spring_crops", SPRING_BLOCKS)
ss_tag("item", "spring_crops", SPRING_ITEMS)
ss_tag("block", "summer_crops", SUMMER_BLOCKS)
ss_tag("item", "summer_crops", SUMMER_ITEMS)
ss_tag("block", "autumn_crops", AUTUMN_BLOCKS)
ss_tag("item", "autumn_crops", AUTUMN_ITEMS)
# No CropLite year-round crops anymore (grains moved to seasonal tags).
ss_tag("block", "year_round_crops", [])
ss_tag("item", "year_round_crops", [])

# --------------------------------------------------------------------------
# Tough as Nails thirst restoration (optional; no hard dependency)
# TAN's "thirst" droplets are what players usually call hydration points.
# Items listed here are auto-included in #toughasnails:drinks via nested tags.
# --------------------------------------------------------------------------

TAN_DATA = os.path.join(ROOT, "src/main/resources/data/toughasnails")


def tan_thirst(points, values):
    write(
        os.path.join(TAN_DATA, "tags", "item", "thirst", f"{points}_thirst_drinks.json"),
        {"replace": False, "values": values},
    )


def tan_hydration(percent, values):
    write(
        os.path.join(TAN_DATA, "tags", "item", "hydration", f"{percent}_hydration_drinks.json"),
        {"replace": False, "values": values},
    )


# 6 thirst: juicy melon-likes + cucumber
tan_thirst(6, [
    f"{NS}:cantelope_slice",
    "minecraft:melon_slice",
    f"{NS}:cucumber",
])

# Thirst saturation (TAN "hydration") for the always-edible juicy foods.
# 20% matches berry juices; melon juice itself is 50%.
tan_hydration(20, [
    f"{NS}:cantelope_slice",
    "minecraft:melon_slice",
    f"{NS}:cucumber",
])

# 4 thirst: fruit + tomato
tan_thirst(4, [
    "minecraft:sweet_berries",
    "minecraft:apple",
    f"{NS}:peach",
    f"{NS}:lemon",
    f"{NS}:tomato",
])

# 3 thirst: banana
tan_thirst(3, [f"{NS}:banana"])

# 8 thirst: lemonade
tan_thirst(8, [f"{NS}:lemonade"])

# 2 thirst: pepper + eggplant + cactus flesh
tan_thirst(2, [f"{NS}:pepper", f"{NS}:eggplant", f"{NS}:cactus_flesh"])

# --------------------------------------------------------------------------
# Homeostatic drinkables (optional; no hard dependency)
# Loaded by Homeostatic's environment/drinkable reload listener when present.
# Both TAN and Homeostatic use a 0–20 water/thirst scale, so `amount` matches
# TAN thirst points 1:1. TAN hydration % maps onto saturation (max 5.0).
# --------------------------------------------------------------------------

HS_DRINK = os.path.join(ROOT, "src/main/resources/data", NS, "environment", "drinkable")
# Homeostatic MAX_SATURATION_LEVEL = 5.0 → 20% TAN hydration = 1.0
HS_SAT_FROM_TAN_HYDRATION_20 = 1.0


def homeostatic_drink(item, amount, saturation, *, effect_chance=0.0, effect_duration=0, effect_potency=0):
    write(os.path.join(HS_DRINK, f"{item}.json"), {
        "type": f"{NS}:{item}",
        "amount": amount,
        "saturation": saturation,
        "effect_chance": effect_chance,
        "effect_duration": effect_duration,
        "effect_potency": effect_potency,
    })


# Same items as toughasnails:thirst/*_thirst_drinks — amount = TAN thirst points.
# Juicy (TAN 6 thirst + 20% hydration)
for item in ("cantelope_slice", "cucumber"):
    homeostatic_drink(item, 6, HS_SAT_FROM_TAN_HYDRATION_20)

# Fruit / tomato (TAN 4)
for item in ("peach", "lemon", "tomato"):
    homeostatic_drink(item, 4, 0.8)

# Banana (TAN 3)
homeostatic_drink("banana", 3, 0.6)

# Lemonade (TAN 8)
homeostatic_drink("lemonade", 8, 2.0)

# Low-moisture veg (TAN 2)
for item in ("pepper", "eggplant", "cactus_flesh"):
    homeostatic_drink(item, 2, 0.2)

# Other edible foods (no TAN thirst tag — modest water restore)
homeostatic_drink("bottle_of_coffee", 4, 0.8)
homeostatic_drink("oatmeal", 2, 0.4)
homeostatic_drink("rice_and_beans", 2, 0.4)
homeostatic_drink("baked_sweet_potato", 1, 0.1)
homeostatic_drink("sweet_potato", 1, 0.1)
homeostatic_drink("basil", 1, 0.1)
homeostatic_drink("oats", 1, 0.1)
homeostatic_drink("beans", 1, 0.1)
homeostatic_drink("rice", 1, 0.1)
homeostatic_drink("garlic", 1, 0.1)

# Common tags used by Homeostatic as drinkable fallbacks when no explicit entry exists.
C_TAGS = os.path.join(ROOT, "src/main/resources/data/c/tags/item")
write(os.path.join(C_TAGS, "fruits.json"), {
    "replace": False,
    "values": [f"{NS}:peach", f"{NS}:lemon", f"{NS}:banana", f"{NS}:cantelope_slice"],
})
write(os.path.join(C_TAGS, "vegetables.json"), {
    "replace": False,
    "values": [
        f"{NS}:cucumber", f"{NS}:pepper", f"{NS}:eggplant",
        f"{NS}:tomato", f"{NS}:basil", f"{NS}:cactus_flesh",
    ],
})
write(os.path.join(C_TAGS, "rootvegetables.json"), {
    "replace": False,
    "values": [f"{NS}:sweet_potato", f"{NS}:baked_sweet_potato", f"{NS}:garlic"],
})

# --------------------------------------------------------------------------
# Tree features (used by the saplings via TreeGrower)
# --------------------------------------------------------------------------


def simple_state(name, properties=None):
    state = {"Name": name}
    if properties:
        state["Properties"] = properties
    return {"type": "minecraft:simple_state_provider", "state": state}


for tree in ("peach", "lemon"):
    write(os.path.join(DATA, "worldgen/configured_feature", f"{tree}_tree.json"), {
        "type": "minecraft:tree",
        "config": {
            "trunk_provider": simple_state("minecraft:oak_log", {"axis": "y"}),
            "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 4,
                             "height_rand_a": 2, "height_rand_b": 0},
            "foliage_provider": simple_state(f"{NS}:{tree}_leaves",
                                             {"distance": "7", "persistent": "false", "waterlogged": "false"}),
            "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3},
            "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1,
                             "lower_size": 0, "upper_size": 1},
            "decorators": [],
            "ignore_vines": True,
            "below_trunk_provider": simple_state("minecraft:dirt"),
        },
    })

# Banana trees use schematic-derived structures via croplite:banana_tree feature.
write(os.path.join(DATA, "worldgen/configured_feature", "banana_tree.json"), {
    "type": f"{NS}:banana_tree",
    "config": {},
})

# Placed banana tree (worldgen) — copy peach placement pattern with banana sapling check.
write(os.path.join(DATA, "worldgen/placed_feature", "banana_tree.json"), {
    "feature": f"{NS}:banana_tree",
    "placement": [
        {"type": f"{NS}:config_chunk_rarity", "tree": "banana"},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"},
        {"type": "minecraft:block_predicate_filter", "predicate": {
            "type": "minecraft:would_survive",
            "state": {"Name": f"{NS}:banana_sapling", "Properties": {"stage": "0"}},
        }},
    ],
})

# --------------------------------------------------------------------------
# Language file
# --------------------------------------------------------------------------

SPECIAL_NAMES = {
    "rice_and_beans": "Rice & Beans",
    "cantelope_slice": "Cantelope Slice",
    "baked_sweet_potato": "Baked Sweet Potato",
    "coffee_bean": "Coffee Bean",
    "roasted_coffee_bean": "Roasted Coffee Bean",
    "bottle_of_coffee": "Bottle of Coffee",
    "cactus_flesh": "Cactus Flesh",
    "banana_stalk": "Banana Stalk",
    "banana_leaves": "Banana Leaves",
    "banana_sapling": "Banana Sapling",
}

ITEM_NAMES = [
    "hay", "tomato", "pepper", "eggplant", "cucumber",
    "tomato_seeds", "pepper_seeds", "eggplant_seeds", "cucumber_seeds", "cantelope_seeds", "basil_seeds",
    "sweet_potato", "baked_sweet_potato", "oats", "beans", "rice", "basil",
    "cantelope_slice", "peach", "lemon", "banana", "lemonade", "oatmeal", "rice_and_beans",
    "coffee_bean", "roasted_coffee_bean", "bottle_of_coffee", "cactus_flesh", "garlic",
]

BLOCK_NAMES = [
    "cantelope", "peach_leaves", "lemon_leaves", "banana_leaves",
    "peach_sapling", "lemon_sapling", "banana_sapling", "banana_stalk",
]


def title(name):
    return SPECIAL_NAMES.get(name, " ".join(w.capitalize() for w in name.split("_")))


lang = {
    "itemGroup.croplite": "CropLite",
    "advancements.croplite.seedy_place.title": "Seedy Place",
    "advancements.croplite.seedy_place.description": "Plant any CropLite crop or seed",
    "item.croplite.has_garlic": "Has Garlic",
}
for name in ITEM_NAMES:
    lang[f"item.{NS}.{name}"] = title(name)
for name in BLOCK_NAMES:
    lang[f"block.{NS}.{name}"] = title(name)

write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(lang.items())))

# --------------------------------------------------------------------------
# Copy / remap textures from the workspace textures/ folder
# --------------------------------------------------------------------------

# source filename in textures/ -> list of destinations under assets/croplite/textures/
TEXTURE_MAP = {
    # Items
    "hay.png": ["item/hay.png"],
    "tomato.png": ["item/tomato.png"],
    "tomatoseed.png": ["item/tomato_seeds.png"],
    "pepper.png": ["item/pepper.png"],
    "pepperseed.png": ["item/pepper_seeds.png"],
    "eggplant.png": ["item/eggplant.png"],
    "eggplantseed.png": ["item/eggplant_seeds.png"],
    "cucumber.png": ["item/cucumber.png"],
    "cucumberseed.png": ["item/cucumber_seeds.png"],
    "sweetpotato.png": ["item/sweet_potato.png"],
    "bakedsweetpotato.png": ["item/baked_sweet_potato.png"],
    "oats.png": ["item/oats.png"],
    "beans.png": ["item/beans.png"],
    "rice.png": ["item/rice.png"],
    "basil.png": ["item/basil.png"],
    "basilseed.png": ["item/basil_seeds.png"],
    "cantelopeslice.png": ["item/cantelope_slice.png"],
    "peach.png": ["item/peach.png"],
    "lemon.png": ["item/lemon.png"],
    "lemonade.png": ["item/lemonade.png"],
    "oatmeal.png": ["item/oatmeal.png"],
    "rice_and_beans.png": ["item/rice_and_beans.png"],
    # Shared early crop stages (tomato / pepper / eggplant / sweet potato)
    "allplant_stage1.png": ["block/crop_stage0.png"],
    "allplant_stage2.png": ["block/crop_stage1.png"],
    "eggplant_tomato_pepper_stage3.png": ["block/crop_stage2.png"],
    "eggplant_tomato_pepper_stage4567.png": ["block/crop_stage3.png"],
    "tomato_plant_top_bottom_finalstae.png": ["block/tomato_plant_mature.png"],
    "tomato_plant_top_bottom_fruiting.png": ["block/tomato_plant_fruiting.png"],
    "tomato_plant_top_bottom_fruiting_fruit.png": ["block/tomato_plant_fruiting_fruit.png"],
    "pepperplant_fruiting.png": ["block/pepper_plant_fruiting.png"],
    "eggplant_plant_fruiting.png": ["block/eggplant_plant_fruiting.png"],
    "eggplant_plant_fruiting_fruit.png": ["block/eggplant_plant_fruiting_fruit.png"],
    # Cucumber plant stages (cucumber_cantelope_* sheets)
    "cucumber_cantelope_stage1.png": ["block/cucumber_stage0.png"],
    "cucumber_cantelope_stage234.png": ["block/cucumber_stage1.png"],
    "cucumber_cantelope_stage56.png": ["block/cucumber_stage2.png"],
    "cucumber_cantelope_plant_fullgrown.png": ["block/cucumber_plant_mature.png"],
    "cucumber_cantelope_plant_fruiting.png": ["block/cucumber_plant_fruiting.png"],
    "cucumber_plant_tilted.png": ["block/cucumber_plant_tilted.png"],
    # Oats / beans / rice
    "oats_beans_rice_stage1.png": ["block/grain_stage0.png"],
    "oats_beans_rice_stage2.png": ["block/grain_stage1.png"],
    "oats_beans_rice_stage34.png": ["block/grain_stage2.png"],
    "oats_beans_rice_stage567.png": ["block/grain_stage3.png"],
    "oats_rice_plant_finalstage_bottom.png": ["block/oats_rice_plant_mature_lower.png"],
    "oats_rice_plant_finalstage_bottom_fruit.png": ["block/oats_rice_plant_mature_lower_fruit.png"],
    "oats_plant_top_finalstage.png": ["block/oats_plant_mature_upper.png"],
    "oats_plant_top_finalstage_fruit.png": ["block/oats_plant_mature_upper_fruit.png"],
    "rice_plant_top_finalstage.png": ["block/rice_plant_mature_upper.png"],
    "rice_plant_top_finalstage_fruit.png": ["block/rice_plant_mature_upper_fruit.png"],
    "beans_plant_finalstage.png": ["block/beans_plant_mature.png"],
    "beans_plant_finalstage_fruit.png": ["block/beans_plant_mature_fruit.png"],
    # Basil
    "basil_stage1.png": ["block/basil_stage0.png"],
    "basil_stage2.png": ["block/basil_stage1.png"],
    "basil_stage3456.png": ["block/basil_stage2.png"],
    "basil_finalstage.png": ["block/basil_stage3.png"],
    # Cantelope fruit block + trees
    "cantelopeside.png": ["block/cantelope_side.png"],
    "cantelopetopbottom.png": ["block/cantelope_top.png"],
    "peachleaves.png": ["block/peach_leaves.png"],
    "peachleaves_fruit.png": ["block/peach_leaves_fruit.png"],
    "lemonleaves.png": ["block/lemon_leaves.png"],
    "lemonleaves_fruit.png": ["block/lemon_leaves_fruit.png"],
    "peach_sapling.png": ["block/peach_sapling.png", "item/peach_sapling.png"],
    "lemon_sapling.png": ["block/lemon_sapling.png", "item/lemon_sapling.png"],
    # Coffee crop + items
    "coffee_bean.png": ["item/coffee_bean.png"],
    "roasted_coffee_bean.png": ["item/roasted_coffee_bean.png"],
    "bottle_of_coffee.png": ["item/bottle_of_coffee.png"],
    "coffee_grow1.png": ["block/coffee_stage0.png"],
    "coffee_grow2.png": ["block/coffee_stage1.png"],
    "coffee_grow_final.png": ["block/coffee_stage2.png", "block/coffee_plant_mature.png"],
    "coffee_grow_fruiting.png": ["block/coffee_plant_fruiting.png"],
    "coffee_grow_fruiting_fruit.png": ["block/coffee_plant_fruiting_fruit.png"],
    # Garlic
    "garlic.png": ["item/garlic.png"],
    "garlic_growth1.png": ["block/garlic_stage0.png"],
    "garlic_growth2.png": ["block/garlic_stage1.png"],
    "garlic_growth3.png": ["block/garlic_stage2.png"],
    "garlic_growth4.png": ["block/garlic_stage3.png"],
    "garlic_growth_final.png": ["block/garlic_stage4.png"],
    # Banana tree
    "banana.png": ["item/banana.png"],
    "banana_sapling.png": ["block/banana_sapling.png", "item/banana_sapling.png"],
    "banana_stalk.png": ["block/banana_stalk.png"],
    "banana_stalk_top_bottom.png": ["block/banana_stalk_top.png"],
    "banana_leaves.png": ["block/banana_leaves.png"],
    "banana_leaves_fruit.png": ["block/banana_leaves_fruit.png"],
    # Cactus
    "cactus_flesh.png": ["item/cactus_flesh.png"],
}

copied = 0
missing = []
for src_name, destinations in TEXTURE_MAP.items():
    src = os.path.join(TEXTURE_SRC, src_name)
    if not os.path.isfile(src):
        missing.append(src_name)
        continue
    for dst_rel in destinations:
        dst = os.path.join(TEXTURE_DST, dst_rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1

print(f"assets and data generated; copied {copied} textures")
if missing:
    print("missing source textures:", ", ".join(missing))
