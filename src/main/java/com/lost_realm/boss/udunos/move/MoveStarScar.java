package com.lost_realm.boss.udunos.move;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.lost_realm.boss.udunos.UdunosBoss;
import com.lost_realm.boss.udunos.UdunosEffect;
import com.lost_realm.boss.udunos.UdunosFx;
import com.lost_realm.boss.udunos.UdunosPhase;
import com.lost_realm.boss.udunos.UdunosTuning;

/**
 * <b>延迟星痕</b> —— 第四阶段专属。Boss 一边盘绕，一边在身后<b>埋雷</b>。
 *
 * <h2>它为什么是第四阶段的招</h2>
 * 前三阶段的所有招式都是"现在发生的事"：预警、然后躲。
 * 这一招制造的是<b>过去发生的事</b> —— 你三秒前站过的地方，
 * 现在要炸了。第四阶段不给巡游、不给喘息，而玩家还得腾出脑子记住
 * "刚才我走过哪"，这正是收尾阶段该有的压迫强度。
 *
 * <h2>流程</h2>
 * <pre>
 *   沿身体每一节脚下留下一个星痕（密集但可读）
 *      ↓ 每个星痕各自预警约 1.7 秒，先预警后爆发
 *   同时头部继续盘旋 —— 边跑边撒，玩家没法靠站着不动来规避
 *      ↓ 撒完一轮收招
 * </pre>
 *
 * <h2>为什么星痕的半径只有 2.6 格</h2>
 * 原设计说"密集但可读"。密到一定程度之后，<b>单个体积必须小</b> ——
 * 否则整片地面都是必死区，玩家连站的地方都没有。
 * 2.6 格意味着两个星痕之间总留得下一条缝，考验的是"走位精度"
 * 而不是"跑得够不够快"。
 */
public final class MoveStarScar extends UdunosMove {

    private int placed;
    private final List<UdunosEffect> scars = new ArrayList<>();
    private double orbitDir = 1.0D;
    private Vec3 center = Vec3.ZERO;

    public MoveStarScar(UdunosBoss boss) {
        super(boss);
    }

    @Override
    public MoveKind kind() {
        return MoveKind.SCAR;
    }

    @Override
    public String id() {
        return "scar";
    }

    @Override
    public String langKey() {
        return "move.lost_realm.udunos.scar";
    }

    /** 第四阶段专属。 */
    @Override
    public boolean availableIn(UdunosPhase phase) {
        return phase == UdunosPhase.FOUR;
    }

    @Override
    public void begin(@Nullable Player target) {
        Vec3 anchor = target != null ? target.position() : this.boss.position();
        this.center = new Vec3(anchor.x, anchor.y, anchor.z);
        this.orbitDir = this.boss.getRandom().nextBoolean() ? 1.0D : -1.0D;
        this.placed = 0;
        this.scars.clear();
    }

    @Override
    public Vec3 tick(@Nullable Player target) {
        this.ticks++;

        // 边绕边撒 —— 玩家没法靠"站着不动"来规避
        double speed = UdunosTuning.CRUISE_SPEED * 1.2D;
        this.velocity = orbitTowards(this.center, UdunosTuning.ARENA_ORBIT_RADIUS,
                speed, this.orbitDir);

        this.boss.syncMove(kind());
        this.boss.syncAim(this.velocity.lengthSqr() > 1.0E-6D
                ? this.velocity.normalize() : new Vec3(0, 0, 1));
        this.boss.syncWarning(0);

        int total = UdunosTuning.SCAR_COUNT;
        // 均匀铺在整段时间里：每 2 tick 落一个，22 个约 1.5 秒铺完
        if (this.placed < total && this.ticks % 2 == 0) {
            placeScar();
            this.placed++;
        }
        // 等最后一个星痕爆完再收招
        if (this.placed >= total
                && this.ticks > total * 2 + UdunosTuning.SCAR_WARN + UdunosTuning.SCAR_ACTIVE) {
            finish();
        }
        return this.velocity;
    }

    /** 在当前位置脚下留一个星痕。 */
    private void placeScar() {
        if (!(this.boss.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 pos = this.boss.position();
        UdunosEffect scar = UdunosEffect.scar(level, this.boss, pos,
                UdunosTuning.SCAR_RADIUS,
                UdunosTuning.SCAR_WARN,
                UdunosTuning.SCAR_ACTIVE,
                UdunosTuning.BOLT_DAMAGE * 1.45F);
        level.addFreshEntity(scar);
        this.scars.add(scar);
    }

    @Override
    protected void onFinished() {
        this.scars.clear();
    }
}
