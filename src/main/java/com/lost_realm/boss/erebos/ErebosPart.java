package com.lost_realm.boss.erebos;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.lost_realm.boss.BossEntities;

/**
 * Erebos 的<b>碰撞部件</b> —— 让 96 格长的虫身每一段都能被打到。
 *
 * <h2>为什么必须有这个东西</h2>
 * Erebos 是<b>单个实体</b>（40 节的跟随是数学算出来的，不是 40 个实体）。
 * 但攻击判定走的是实体碰撞箱 —— 单个实体的碰撞箱只能覆盖头部 2.4 格，
 * 玩家对着中段身体砍一刀，客户端射线检测返回 {@code null}，<b>这一刀直接落空</b>。
 *
 * <p>三种解法：
 * <ol>
 *   <li><b>把碰撞箱撑到 96 格</b>。最简单，但蛇形弯曲时判定框里全是空气 ——
 *       对着身体外的空隙挥剑也算命中，与设计要求"碰撞范围应贴合实际装甲轮廓"相悖。</li>
 *   <li><b>40 个节各做一个实体</b>。最精确，但 40 个实体要 tick、要同步、
 *       会各自寻路，服务器 TPS 会被吃掉一大块。</li>
 *   <li><b>若干个大的碰撞部件</b>（本方案）—— 沿身体均匀布置 15 个，
 *       每个覆盖约 6.4 格。精度足够（巡游时局部弯曲很小），实体数只有 40 节方案的三分之一。</li>
 * </ol>
 *
 * <h2>为什么不用原版的 {@code EnderDragonPart}</h2>
 * 它被 {@code ServerLevel} 用一张 {@code Int2ObjectMap<EnderDragonPart>} 单独管理，
 * 且构造参数写死了 {@code EnderDragon} 类型 —— 既不能被 {@code Level#getEntities}
 * 查到（射线检测依赖它），也无法复用。自己写一个反而更干净。
 *
 * <h2>生命周期</h2>
 * 由 {@link ErebosBoss} 在首次 tick 时创建并 {@code addFreshEntity}，之后每 tick
 * 由本体 {@link #snapTo} 推着走。本体死亡/移除时一并移除。
 * 本实体<b>不存档</b>（{@code shouldBeSaved = false}）—— 重载世界后由本体重新创建。
 */
public class ErebosPart extends Entity {

    /** 所属本体。<b>客户端侧为 {@code null}</b>（客户端通过 EntityType 反序列化创建，拿不到本体引用）。 */
    @Nullable
    private ErebosBoss parent;

    /**
     * 部件尺寸 —— 固定值，永不变化。
     *
     * <p>边长取 {@link ErebosTuning#PART_SIZE}（2.6 格），比节距 2.4 略大，
     * 这样相邻两个部件的盒子会轻微重叠，身体转弯时中间不会露出缝隙。
     */
    private final EntityDimensions dimensions = EntityDimensions.scalable(
            ErebosTuning.PART_SIZE, ErebosTuning.PART_SIZE);

    /**
     * 注册用构造 —— 由 {@code EntityType} 的工厂调用，服务端与客户端都会走这里。
     *
     * <p>客户端反序列化时拿不到 parent，所以这里不做任何前置断言。
     */
    public ErebosPart(EntityType<? extends ErebosPart> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        // 不做任何方块碰撞 —— 位置完全由本体指定，让它自己去撞墙只会把虫身卡散
        this.noPhysics = true;
    }

    /** 服务端创建用 —— 绑定本体。 */
    public ErebosPart(ErebosBoss parent) {
        this(BossEntities.EREBOS_PART.get(), parent.level());
        this.parent = parent;
    }

    /**
     * 把部件挪到指定点（该点是部件的<b>中心</b>）。
     *
     * <p>{@code Entity#setPos} 的语义是"底面中心"（{@code makeBoundingBox} 会以
     * position 为底、向上撑起 height），所以这里先减去半个高度再设置 ——
     * 否则整条虫的判定框会比视觉低一格。
     */
    public void snapTo(double cx, double cy, double cz) {
        this.setPos(cx, cy - this.dimensions.height / 2.0D, cz);
    }

    @Nullable
    public ErebosBoss parent() {
        return parent;
    }

    // ================================================================
    // Entity 抽象方法
    // ================================================================

    @Override
    protected void defineSynchedData() {
        // ⚠️ 不能调 super（Entity 里是抽象方法）；Entity 的基础槽位由它自己的构造函数注册。
        // 这里确实没有自己的同步数据：位置由 setPos 驱动，尺寸由本体设置
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 不存档
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 不存档
    }

    /**
     * 空 tick —— 位置、尺寸全部由本体每 tick 推过来。
     *
     * <p>覆写为空还顺手挡掉了 {@code baseTick} 里的一堆开销：
     * 火焰伤害、窒息、传送门、摔落…… 这些东西对一个纯判定用的部件毫无意义。
     */
    @Override
    public void tick() {
        // 故意为空
    }

    // ================================================================
    // 攻击路由 —— 这部分才是本类存在的理由
    // ================================================================

    /** 可被射线检测选中（默认是 false，不覆写的话玩家的准星根本看不到它）。 */
    @Override
    public boolean isPickable() {
        return true;
    }

    /** 可被攻击。 */
    @Override
    public boolean isAttackable() {
        return true;
    }

    /**
     * 受击<b>转发给本体</b>。
     *
     * <p>这是全类最关键的一行：伤害不落在部件上，而是交给本体统一结算 ——
     * 因此"共享血量"是天然成立的，也不存在"打头部和打身体掉两份血"的问题。
     *
     * <p>本体自己的 {@code hurt} 会处理无敌帧、动态减伤、阶段切换。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        ErebosBoss p = this.parent;
        if (p == null || p.isRemoved() || !p.isAlive()) {
            return false;
        }
        return p.hurt(source, amount);
    }

    /** 不被推动（否则玩家贴着身体走会把判定框挤歪）。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** 参与方块碰撞检测 —— 否。它是个纯判定盒。 */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** 不随世界存档 —— 重载后由本体重建。 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return dimensions;
    }

    /**
     * 客户端拾取结果沿用本体 —— F3 调试与"选中方块/实体"的提示会显示成 Boss 的名字，
     * 而不是一堆无名的部件。
     */
    @Override
    public ItemStack getPickResult() {
        ErebosBoss p = this.parent;
        return p == null ? ItemStack.EMPTY : p.getPickResult();
    }

    /**
     * 用<b>本体</b>的追踪包 —— 让客户端玩家能像看待一个整体那样处理它。
     *
     * <p>用普通的 {@code ClientboundAddEntityPacket} 也行，但那样客户端会为
     * 15 个部件各创建一份实体实例；沿用本体可以省掉这层开销。
     */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this);
    }

    /** 不做任何方块内检测（岩浆、仙人掌…）。 */
    @Override
    public boolean isInWall() {
        return false;
    }
}
