package com.lost_realm.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lost_realm.Medals;
import com.lost_realm.skill.MedalType;

/**
 * 物品注册:四枚勋章物品 + 创造模式标签页。
 */
public final class MedalRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Medals.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Medals.MODID);

    public static final RegistryObject<Item> ARCHER_MEDAL =
            ITEMS.register("archer_medal", () -> new ItemMedal(MedalType.ARCHER));

    public static final RegistryObject<Item> WARRIOR_MEDAL =
            ITEMS.register("warrior_medal", () -> new ItemMedal(MedalType.WARRIOR));

    public static final RegistryObject<Item> TANK_MEDAL =
            ITEMS.register("tank_medal", () -> new ItemMedal(MedalType.TANK));

    /**
     * 法师勋章(以【诡厄巫法 / Goety】为底座)。
     *
     * <p>未安装 Goety 时该物品仍会注册,但被
     * {@link ItemMedal#isEnabled()} 判定为不可用 —— 不出现在创造标签页、
     * 不参与配方、装备后技能不触发,其余勋章不受影响。
     */
    public static final RegistryObject<Item> MAGE_MEDAL =
            ITEMS.register("mage_medal", () -> new ItemMedal(MedalType.MAGE));

    /**
     * 召唤师勋章(同样以【诡厄巫法 / Goety】为底座)。
     *
     * <p>软依赖规则与法师勋章完全一致:未安装 Goety 时视为不可用。
     */
    public static final RegistryObject<Item> SUMMONER_MEDAL =
            ITEMS.register("summoner_medal", () -> new ItemMedal(MedalType.SUMMONER));

    /**
     * 战士终极武器:「我有一剑！」
     *
     * <p>它是一把<b>剑</b>而不是饰品 —— 走主手装备槽，九把「刃」合成。
     * 常驻属性靠物品自己的 MAINHAND 修饰符，主动技是 Shift + 右键蓄力 10 秒。
     */
    public static final RegistryObject<Item> I_HAVE_A_SWORD =
            ITEMS.register("i_have_a_sword", IHaveASwordItem::new);

    /**
     * 破枷印记 —— 打开 20 / 40 级枷锁的消耗品。
     *
     * <p>可堆叠到 16：印记是跨章节的通行证，玩家可能一次拿到好几枚
     * （任务奖励 + Boss 掉落），不让叠会逼着人来回跑箱子。
     */
    public static final RegistryObject<Item> MEDAL_SHACKLE =
            ITEMS.register("medal_shackle", () -> new MedalShackleItem(
                    new Item.Properties().stacksTo(16)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("lost_realm", () -> CreativeModeTab.builder()            .title(Component.translatable("itemGroup.lost_realm"))
            .icon(() -> new ItemStack(ARCHER_MEDAL.get()))
            .displayItems((params, output) -> {
                // ---------- 五枚职业勋章 ----------
                output.accept(ARCHER_MEDAL.get());
                output.accept(WARRIOR_MEDAL.get());
                output.accept(TANK_MEDAL.get());
                // 仅在 Goety 可用时展示法师 / 召唤师勋章
                if (com.lost_realm.compat.goety.GoetyCompat.isLoaded()) {
                    output.accept(MAGE_MEDAL.get());
                    output.accept(SUMMONER_MEDAL.get());
                }

                // ---------- 破枷印记（章节进度道具）----------
                output.accept(MEDAL_SHACKLE.get());

                // ---------- 遗落之主:门钥匙 ----------
                output.accept(com.lost_realm.boss.BossItems.REALM_KEY.get());

                // ---------- 遗落之主·乌蠹诺斯:召唤物 ----------
                // 与「终末之影」并列的第二个终局 Boss,独立召唤。
                output.accept(com.lost_realm.boss.BossItems.DARKSTAR_BEACON.get());

                // ---------- 乌蠹诺斯遗物:五职业 + 纪念品 ----------
                // 注:原设计写的是"四职业武器"(泰拉瑞亚只有四个职业),
                //     MC 这边有五个 —— 末光壁垒是补上的坦克那一件。
                for (net.minecraft.world.item.Item relic
                        : com.lost_realm.boss.relic.UdunosRelics.allDrops()) {
                    output.accept(relic);
                }

                // ---------- 遗落之主:任务链材料 ----------
                output.accept(com.lost_realm.boss.BossItems.FRAGMENT.get());
                output.accept(com.lost_realm.boss.BossItems.ALTAR_CORE.get());

                // ---------- 遗落之主:战利品 ----------
                output.accept(com.lost_realm.boss.BossItems.WARDEN_CORE.get());
                output.accept(com.lost_realm.boss.BossItems.WARDEN_ECHO.get());
                output.accept(com.lost_realm.boss.BossItems.REALM_CROWN.get());

                // ---------- 遗落之主:建材与方块 ----------
                // 注:realm_portal(传送门门面)刻意不入标签页 ——
                //    它必须由门钥匙点燃生成,直接放置会破坏流程。
                output.accept(com.lost_realm.boss.BossBlocks.VOID_STONE_ITEM.get());
                output.accept(com.lost_realm.boss.BossBlocks.ARENA_FRAME_ITEM.get());

                // ---------- 消耗品 ----------
                output.accept(ArrowRegistry.TIME_CAPSULE.get());

                // ---------- 箭类饰品(dv42) ----------
                // 五枚都要求同时佩戴射手勋章才生效,顺序按稀有度/强度排。
                output.accept(ArrowRegistry.POTION_ARROW.get());
                output.accept(ArrowRegistry.EXPLOSIVE_ARROW.get());
                output.accept(ArrowRegistry.PIERCING_ARROW.get());
                output.accept(ArrowRegistry.ANTIGRAVITY_ARROW.get());
                output.accept(ArrowRegistry.THUNDER_ARROW.get());
                output.accept(ArrowRegistry.TWIN_ARROW.get());
                output.accept(ArrowRegistry.ARMOR_PIERCE_ARROW.get());
                output.accept(ArrowRegistry.HOLD_ARROW.get());
                output.accept(ArrowRegistry.SUMMON_ARROW.get());
                output.accept(ArrowRegistry.STAR_ARROW.get());

                // ---------- 「刃」类战士饰品(dv49) ----------
                // 只能放进「武器」槽,该槽位由战士勋章撑开。
                output.accept(WarriorCharmRegistry.SWEEPING_BLADE.get());
                output.accept(WarriorCharmRegistry.DUAL_BLADE.get());
                output.accept(WarriorCharmRegistry.CRIT_BLADE.get());
                output.accept(WarriorCharmRegistry.BACKSTAB_BLADE.get());
                output.accept(WarriorCharmRegistry.DISARM_BLADE.get());
                output.accept(WarriorCharmRegistry.LUCK_BLADE.get());
                output.accept(WarriorCharmRegistry.CHARGING_BLADE.get());
                output.accept(WarriorCharmRegistry.DASH_BLADE.get());
                output.accept(WarriorCharmRegistry.TOUGHNESS_BLADE.get());

                // ---------- 战士终极武器(九把「刃」合成) ----------
                output.accept(I_HAVE_A_SWORD.get());

                // ---------- 「盾」类坦克饰品 ----------
                // 只能放进「盾牌」槽,该槽位由坦克勋章撑开。
                output.accept(TankShieldRegistry.THORNS_SHIELD.get());
                output.accept(TankShieldRegistry.ADAPTIVE_SHIELD.get());
                output.accept(TankShieldRegistry.SHOCKWAVE_SHIELD.get());
                output.accept(TankShieldRegistry.SACRIFICE_SHIELD.get());
                output.accept(TankShieldRegistry.EARTH_SHIELD.get());
                output.accept(TankShieldRegistry.DEVOURING_SHIELD.get());
                output.accept(TankShieldRegistry.NOURISH_SHIELD.get());
                output.accept(TankShieldRegistry.VITALITY_SHIELD.get());
                output.accept(TankShieldRegistry.VANGUARD_SHIELD.get());
                output.accept(TankShieldRegistry.ETERNAL_SHIELD.get());

                // ---------- 「晶石」类法师饰品 ----------
                // 只能放进「聚晶」槽,该槽位由法师勋章撑开。
                output.accept(MageCrystalRegistry.CHAIN_CRYSTAL.get());
                output.accept(MageCrystalRegistry.SNIPER_CRYSTAL.get());
                output.accept(MageCrystalRegistry.CIRCLE_CRYSTAL.get());
                output.accept(MageCrystalRegistry.SOULFIRE_CRYSTAL.get());
                output.accept(MageCrystalRegistry.OVERDRAFT_CRYSTAL.get());
                output.accept(MageCrystalRegistry.EMBER_CRYSTAL.get());
                output.accept(MageCrystalRegistry.HEXNAIL_CRYSTAL.get());
                output.accept(MageCrystalRegistry.ECHO_CRYSTAL.get());
                output.accept(MageCrystalRegistry.SWIFT_CRYSTAL.get());
                output.accept(MageCrystalRegistry.SOVEREIGN_CRYSTAL.get());

                // ---------- 「契约」类召唤师饰品 ----------
                // 只能放进「契约」槽,该槽位由召唤师勋章撑开。
                output.accept(ContractCharmRegistry.DOMINION_CONTRACT.get());
                output.accept(ContractCharmRegistry.LEGION_CONTRACT.get());
                output.accept(ContractCharmRegistry.SYMBIOSIS_CONTRACT.get());
                output.accept(ContractCharmRegistry.MARTYR_CONTRACT.get());
                output.accept(ContractCharmRegistry.SOULCHAIN_CONTRACT.get());
                output.accept(ContractCharmRegistry.FLESH_CONTRACT.get());
                output.accept(ContractCharmRegistry.BONE_CONTRACT.get());
                output.accept(ContractCharmRegistry.FISSION_CONTRACT.get());
                output.accept(ContractCharmRegistry.DECREE_CONTRACT.get());
                output.accept(ContractCharmRegistry.OVERLORD_CONTRACT.get());
            })
            .build());

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    /** 根据勋章类型取得对应物品。 */
    public static Item itemOf(MedalType type) {
        return switch (type) {
            case ARCHER -> ARCHER_MEDAL.get();
            case WARRIOR -> WARRIOR_MEDAL.get();
            case TANK -> TANK_MEDAL.get();
            case MAGE -> MAGE_MEDAL.get();
            case SUMMONER -> SUMMONER_MEDAL.get();
        };
    }

    private MedalRegistry() {}
}
