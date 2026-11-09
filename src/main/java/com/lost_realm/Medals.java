package com.lost_realm;

import org.slf4j.Logger;

import com.lost_realm.config.MedalsConfig;
import com.lost_realm.common.CuriosCompat;
import com.lost_realm.item.ArrowRegistry;
import com.lost_realm.item.MedalRegistry;
import com.lost_realm.network.MedalsNetwork;
import com.lost_realm.skill.SkillRegistry;
import com.mojang.logging.LogUtils;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 模组主类:四枚勋章(射手/刃/坦克/法师)熟练度进阶系统。
 *
 * <p>硬性前置:Curios API(饰品槽位)、Apotheosis 及前置 Apothic Attributes(属性库)。
 * 法师勋章额外以<b>诡厄巫法 Goety</b>为底座(可选软依赖,未安装时自动禁用)。
 * 所有百分比配置项在文件中以整型书写(如 20 表示 20%),读取后统一除以 100 参与运算。
 */
@Mod(Medals.MODID)
public class Medals {

    public static final String MODID = "lost_realm";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Medals() {
        // ⚠️ 必须在一切之前调用 —— GeckoLib 官方要求。
        // 时机错误(如放到 FMLCommonSetupEvent 里)会导致动画系统初始化不全,
        // 表现为模型加载成功但动画不播放、或客户端抛 NPE。
        software.bernie.geckolib.GeckoLib.initialize();

        // ⚠️ 也必须在注册任何东西之前 —— 原版「最大生命」属性的上限写死 1024,
        // 超过的部分会被静默夹掉(Boss 设计 24000 血,实际只有 1024,且不报错)。
        // 详见 AttributeLimits 的类注释。
        com.lost_realm.util.AttributeLimits.raiseMaxHealthCap();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册物品与创造模式标签页
        MedalRegistry.init(modBus);
        // 箭类饰品（五枚）+ 时间胶囊
        ArrowRegistry.init(modBus);
        com.lost_realm.item.BladeCharmRegistry.init(modBus);
        com.lost_realm.item.TankShieldRegistry.init(modBus);
        com.lost_realm.item.MageCrystalRegistry.init(modBus);
        com.lost_realm.item.ContractCharmRegistry.init(modBus);
        com.lost_realm.effect.ModEffects.init(modBus);
        // 注册自定义属性(坦克伤害减免)
        com.lost_realm.item.TankAttributes.init(modBus);
        // 战斗属性(暴击 / 吸血 / 破甲 / 闪避 / 拉弓 …)。beta30 起由本模组自注册,
        // 不再借用 Apothic Attributes —— 见 attribute/RealmAttributes
        com.lost_realm.attribute.RealmAttributes.init(modBus);
        // 法师勋章的法术暴击属性(独立于近战暴击属性)
        com.lost_realm.item.MageAttributes.init(modBus);
        // 召唤师勋章的四条仆从属性(Goety 不提供仆从侧的强度概念,必须自建)
        com.lost_realm.item.ServantAttributes.init(modBus);
        // 注册节点技能
        SkillRegistry.init();

        // 配置文件(支持 Configured 配置屏幕)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MedalsConfig.SPEC, MODID + "-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MedalsConfig.CLIENT_SPEC, MODID + "-client.toml");

        // 收集已注册的配置,供 Configured 屏幕接入
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading e) -> {
            com.lost_realm.config.MedalsConfigLoader.register(e.getConfig());
            // 修历史遗留:beta11 及更早把「每级成长」写成了整数配置,
            // 0.75% / 0.5% 这类值被静默吞成 0(见 MedalsConfig#migrateGrowthDefaults)。
            // 必须在加载后主动修一次并写回 —— Forge 不会给已存在的配置套用新默认值。
            if (e.getConfig().getType() == net.minecraftforge.fml.config.ModConfig.Type.COMMON) {
                int fixed = com.lost_realm.config.MedalsConfig.migrateGrowthDefaults();
                if (fixed > 0) {
                    LOGGER.info("[Lost Realm] 已修正 {} 项被旧版整数配置吞成 0 的勋章每级成长"
                            + "(刃/射手/坦克的加成此前一直没随等级增长)。", fixed);
                }
                // 等级上限 30 → 60：老配置里写死 30，不改的话新默认值读不到
                if (com.lost_realm.config.MedalsConfig.migrateLevelCap()) {
                    LOGGER.info("[Lost Realm] 等级上限已从 30 迁移到 60（旧配置未套用新默认值）。");
                }
                // 经验成长曲线 10/8/7/6/5/3 → 7/6/5/4/3/2：同上，旧配置里是上一版的值
                if (com.lost_realm.config.MedalsConfig.migrateExpCurve()) {
                    LOGGER.info("[Lost Realm] 经验成长曲线已迁移到 7/6/5/4/3/2"
                            + "（旧配置仍是 10/8/7/6/5/3）。");
                }
                // 乌蠹诺斯：把配置灌进 UdunosTuning 的可写静态字段（beta41）
                com.lost_realm.boss.udunos.UdunosTuning.applyConfig();
            }
        });
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading e) -> {
            com.lost_realm.config.MedalsConfigLoader.register(e.getConfig());
            if (e.getConfig().getType() == net.minecraftforge.fml.config.ModConfig.Type.COMMON) {
                com.lost_realm.boss.udunos.UdunosTuning.applyConfig();
            }
        });

        modBus.addListener(this::commonSetup);

        // 服务端自检命令 /lrsdebug udunos —— 读状态机内部值（EMERGE 卡没卡住）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.lost_realm.command.UdunosServerDebugCommand.register(e.getDispatcher()));

        // 竞技场运维命令 /lr arena rebuild | tp
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.lost_realm.command.ArenaCommand.register(e.getDispatcher()));

        // 与前置模组的兼容层
        CuriosCompat.init(modBus);

        // 遗落之主(整合包终局 Boss)：方块、物品、实体、任务链
        com.lost_realm.boss.BossBlocks.init(modBus);
        com.lost_realm.boss.BossItems.init(modBus);
        com.lost_realm.boss.BossEntities.init(modBus);
        // 乌蠹诺斯的五件遗物（每个职业一件）+ 事件视界
        com.lost_realm.boss.relic.UdunosRelics.init(modBus);
        // 自定义声音（Boss 战 BGM）—— 全模组目前唯一一个自定义音效
        com.lost_realm.sound.ModSounds.init(modBus);

        // 实用方块(beta53)：区块加载器 / 镇魔火把
        com.lost_realm.machine.MachineBlocks.init(modBus);

        // 客户端初始化(配置屏幕、HUD)
        com.lost_realm.client.ClientBootstrap.bootstrap();

        LOGGER.info("[Lost Realm] 勋章系统已加载(依赖 Curios / Apotheosis / Apothic Attributes;法师勋章底座 Goety 已加载={})。",
                com.lost_realm.compat.goety.GoetyCompat.isLoaded());
        LOGGER.info("[Lost Realm] 遗落之主 Boss 系统已加载(遗落之都 + 零阶段对话 + 五波清场 + 三形态锁血)。");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MedalsNetwork.register();
            // 兜底再灌一次:某些加载顺序下 ModConfigEvent 可能晚于属性注册,
            // 这里保证 Boss 血量等数值一定来自配置(配置没加载时 applyConfig 自己跳过)。
            com.lost_realm.boss.udunos.UdunosTuning.applyConfig();
        });
    }
}
