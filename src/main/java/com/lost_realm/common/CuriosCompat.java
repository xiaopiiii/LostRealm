package com.lost_realm.common;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Curios 兼容层 —— 勋章饰品槽位的说明与扩展点。
 *
 * <h2>「勋章」槽位是怎么来的</h2>
 * Curios 5.x 起，自定义槽位<b>完全由数据包定义</b>，Java 侧不需要（也无法）注册：
 * <pre>
 *   data/lost_realm/curios/slots/medal.json   槽位本体（顺序 / 图标 / 校验器）
 *   data/curios/tags/items/medal.json         哪些物品可以放进去（四枚勋章）
 *   assets/lost_realm/textures/slot/medal.png 槽位图标（16x16）
 * </pre>
 * 后两个文件是配套的：槽位的 {@code validators} 写了 {@code curios:tag}，
 * 而该校验器的语义正是「物品必须属于 item tag {@code curios:<槽位名>}」。
 * 少了 tag，槽位虽然存在，但任何物品都塞不进去。
 *
 * <h2>几个容易踩的坑（已逐条对照过 Curios 5.14.1 的字节码）</h2>
 * <ul>
 *   <li><b>槽位文件必须放在自己模组的命名空间下</b>。Curios 会把
 *       {@code data/&lt;命名空间&gt;/curios/slots/x.json} 的命名空间当作「谁提供的槽位」，
 *       放到 {@code data/curios/} 下等于把它认成 Curios 自带槽位。</li>
 *   <li><b>不需要声明实体拥有哪些槽位</b>。5.14 的
 *       {@code SlotHelper#getSlotTypes(LivingEntity)} 直接返回全部槽位集合，
 *       不做任何按实体过滤，所以槽位一旦加载成功，玩家身上就有了。
 *       （{@code data/curios/curios/entities/player.json} 是更早期版本的写法，
 *        留着无害，但当前版本不读它。）</li>
 *   <li><b>不需要调用注册 API</b>。{@code CuriosApi.registerCurio(Item, ICurioItem)}
 *       在 5.14 里已经是空壳（进去就 {@code apiError()} 然后返回）；
 *       物品只要实现 {@code ICurioItem} 接口就自动具备饰品能力。</li>
 * </ul>
 *
 * <p>本类保留为扩展点（客户端饰品渲染器等），目前无需任何初始化动作。
 */
public final class CuriosCompat {

    public static void init(IEventBus modBus) {
        // 槽位与物品归属都在数据包里声明，这里没有要做的事。
    }

    private CuriosCompat() {}
}
