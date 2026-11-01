package com.lost_realm.client.config;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 可视化配置界面里的<b>单个配置项</b>抽象。
 *
 * <h2>为什么要有这一层</h2>
 * 界面需要同时呈现两类来源不同的配置:
 * <ul>
 *   <li>{@code MedalsConfig.CLIENT_SPEC} —— 客户端配置(状态效果 HUD、HUD 坐标等)</li>
 *   <li>{@code MedalsConfig.SPEC}(通用/服务端)—— 勋章数值、体验改进开关等</li>
 * </ul>
 * 它们各自有独立的 {@code ConfigValue},且改动后要落到对应的 spec 上
 * 才能正确 {@code save()}。本类把「读」「写」「提交」「还原默认」四件事
 * 打包成一个对象,界面层就不必关心它背后属于哪份 spec。
 *
 * <h2>编辑缓冲(为什么不直接写配置)</h2>
 * Forge 的 {@code ConfigValue} 运行期只读,直接 {@code set()} 会导致
 * 「点一下写一次盘」且**无法取消**。因此本类把改动先写进
 * {@link #pending} 缓冲区,界面只读缓冲区:
 * <ul>
 *   <li>点「保存」→ 把缓冲区一次性提交到真实配置并 {@code save()}</li>
 *   <li>点「取消」→ 丢弃缓冲区,配置分毫未动</li>
 * </ul>
 * 这样才具备「预览 / 回滚」语义。
 *
 * <h2>三类控件</h2>
 * <ul>
 *   <li>{@link Kind#TOGGLE} —— 布尔开关</li>
 *   <li>{@link Kind#STEPPER} —— 整数步进(带 -/+ 按钮;取值范围大时自动加缩放步长)</li>
 *   <li>{@link Kind#SWITCH} —— 枚举型多选(如角落 0~3、倒计时 0~3)</li>
 * </ul>
 */
public final class ConfigOption {

    public enum Kind {
        /** 开 / 关。 */
        TOGGLE,
        /** 整数步进。 */
        STEPPER,
        /** 小数步进(以 0.1 为最小单位编辑)。 */
        DOUBLE_STEPPER,
        /** 枚举多选(值 → 语言键后缀)。 */
        SWITCH
    }

    /** 所属配置文件(决定提交到哪份 spec)。 */
    public enum Spec {
        CLIENT,
        COMMON
    }

    private final String id;
    private final Kind kind;
    private final Spec spec;

    /** 语言键,如 {@code config.lost_realm.effect_hud.scale}。 */
    private final String langKey;

    /** 该选项所在的分类 id(用于分组显示)。 */
    private final String categoryId;

    // ---- 读写后端 ----
    private final BooleanSupplier boolGetter;
    private final Consumer<Boolean> boolSetter;
    private final IntSupplier intGetter;
    private final IntConsumer intSetter;
    private final java.util.function.DoubleSupplier doubleGetter;
    private final java.util.function.DoubleConsumer doubleSetter;

    /** 取值范围(STEPPER / DOUBLE_STEPPER 用)。 */
    private final int min;
    private final int max;
    private final double dMin;
    private final double dMax;

    /** SWITCH 的候选值与对应语言键后缀。 */
    private final int[] switchValues;
    private final String[] switchLangSuffixes;

    /** 该配置所属的 ForgeConfigSpec,提交时用它 save。 */
    private final ForgeConfigSpec ownerSpec;

    // ---- 编辑缓冲 ----
    private Boolean pendingBool;
    private Integer pendingInt;
    private Double pendingDouble;

    private ConfigOption(Builder b) {
        this.id = b.id;
        this.kind = b.kind;
        this.spec = b.spec;
        this.langKey = b.langKey;
        this.categoryId = b.categoryId;
        this.boolGetter = b.boolGetter;
        this.boolSetter = b.boolSetter;
        this.intGetter = b.intGetter;
        this.intSetter = b.intSetter;
        this.doubleGetter = b.doubleGetter;
        this.doubleSetter = b.doubleSetter;
        this.min = b.min;
        this.max = b.max;
        this.dMin = b.dMin;
        this.dMax = b.dMax;
        this.switchValues = b.switchValues;
        this.switchLangSuffixes = b.switchLangSuffixes;
        this.ownerSpec = b.ownerSpec;
    }

    // ================================================================
    // 元信息
    // ================================================================

    public String id() { return id; }

    public Kind kind() { return kind; }

    public Spec spec() { return spec; }

    public String langKey() { return langKey; }

    public String descriptionLangKey() { return langKey + ".desc"; }

    public String categoryId() { return categoryId; }

    public int min() { return min; }

    public int max() { return max; }

    public double dMin() { return dMin; }

    public double dMax() { return dMax; }

    public int[] switchValues() { return switchValues; }

    public String[] switchLangSuffixes() { return switchLangSuffixes; }

    // ================================================================
    // 读:优先读编辑缓冲,其次读真实配置
    // ================================================================

    public boolean asBool() {
        if (pendingBool != null) {
            return pendingBool;
        }
        return boolGetter != null && boolGetter.getAsBoolean();
    }

    public int asInt() {
        if (pendingInt != null) {
            return pendingInt;
        }
        if (intGetter != null) {
            return intGetter.getAsInt();
        }
        // DOUBLE 型也支持按 int 读取(用于统一显示)
        if (doubleGetter != null) {
            return (int) Math.round(doubleGetter.getAsDouble());
        }
        return 0;
    }

    public double asDouble() {
        if (pendingDouble != null) {
            return pendingDouble;
        }
        if (doubleGetter != null) {
            return doubleGetter.getAsDouble();
        }
        return 0.0D;
    }

    /** 当前值是否为默认值(用于「重置」判断)。 */
    private Object defaultValue;

    public void setDefaultValue(Object v) { this.defaultValue = v; }

    public Object defaultValue() { return defaultValue; }

    // ================================================================
    // 写:只改缓冲区
    // ================================================================

    public void setBool(boolean v) {
        this.pendingBool = v;
    }

    public void setInt(int v) {
        this.pendingInt = clamp(v);
    }

    public void setDouble(double v) {
        this.pendingDouble = Math.max(dMin, Math.min(dMax, v));
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    /** 步进一次(STEPPER 用)。delta 会被自动缩放,便于大范围数值快速调整。 */
    public void step(int direction) {
        if (kind == Kind.DOUBLE_STEPPER) {
            double unit = 0.1D;
            setDouble(round1(asDouble() + direction * unit));
            return;
        }
        setInt(asInt() + direction * stepSize());
    }

    /**
     * 步进粒度。
     *
     * <p>取值范围小的(≤ 200)每次 ±1,精细可控;
     * 范围大的(如冷却 36000、护甲 100000)逐步放大粒度,
     * 否则要按上千次才能调到目标值。
     */
    public int stepSize() {
        int span = max - min;
        if (span <= 200) {
            return 1;
        }
        if (span <= 1000) {
            return 5;
        }
        if (span <= 10000) {
            return 25;
        }
        return 100;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0D) / 10.0D;
    }

    /** 按当前值本身估算一个合理的步进粒度(供界面显示按几格跳)。 */
    public boolean isDirty() {
        if (pendingBool != null) {
            return pendingBool != asBoolRaw();
        }
        if (pendingInt != null) {
            return pendingInt != intGetter.getAsInt();
        }
        if (pendingDouble != null) {
            return pendingDouble != doubleGetter.getAsDouble();
        }
        return false;
    }

    // 绕过缓冲区直读真实值(用于脏检查)
    private boolean asBoolRaw() {
        return boolGetter != null && boolGetter.getAsBoolean();
    }

    // ================================================================
    // 提交 / 丢弃 / 重置
    // ================================================================

    /**
     * 把缓冲区写入真实配置对象(不落盘)。
     *
     * <p>真正 {@code spec.save()} 由调用方在全部提交完后统一执行一次,
     * 避免 N 个选项写 N 次盘。
     */
    public void apply() {
        if (pendingBool != null && boolSetter != null) {
            boolSetter.accept(pendingBool);
        }
        if (pendingInt != null && intSetter != null) {
            intSetter.accept(pendingInt);
        }
        if (pendingDouble != null && doubleSetter != null) {
            doubleSetter.accept(pendingDouble);
        }
    }

    /** 丢弃缓冲区(取消编辑)。 */
    public void discard() {
        pendingBool = null;
        pendingInt = null;
        pendingDouble = null;
    }

    /** 恢复到本项在代码中定义的默认值(写进缓冲区,尚未落盘)。 */
    public void resetToDefault() {
        if (defaultValue == null) {
            return;
        }
        if (defaultValue instanceof Boolean b) {
            pendingBool = b;
        } else if (defaultValue instanceof Integer i) {
            pendingInt = i;
        } else if (defaultValue instanceof Double d) {
            pendingDouble = d;
        }
    }

    public ForgeConfigSpec ownerSpec() { return ownerSpec; }

    // ================================================================
    // 链式构造
    // ================================================================

    /**
     * 便捷构造:一个布尔开关。
     *
     * @return 已登记默认值的 {@link ConfigOption}
     */
    public static ConfigOption toggle(String id, String categoryId, String langKey,
                                      ForgeConfigSpec spec, ForgeConfigSpec.BooleanValue value,
                                      boolean defaultValue) {
        ConfigOption o = new Builder()
                .id(id).kind(Kind.TOGGLE).spec(spec == null ? Spec.CLIENT : Spec.COMMON)
                .langKey(langKey).categoryId(categoryId)
                .bool(value, spec)
                .build();
        o.setDefaultValue(defaultValue);
        return o;
    }

    public int stepperMin() { return min; }

    public int stepperMax() { return max; }

    public static final class Builder {
        private String id;
        private Kind kind = Kind.TOGGLE;
        private Spec spec = Spec.CLIENT;
        private String langKey;
        private String categoryId;
        private BooleanSupplier boolGetter;
        private Consumer<Boolean> boolSetter;
        private IntSupplier intGetter;
        private IntConsumer intSetter;
        private java.util.function.DoubleSupplier doubleGetter;
        private java.util.function.DoubleConsumer doubleSetter;
        private int min, max;
        private double dMin, dMax;
        private int[] switchValues;
        private String[] switchLangSuffixes;
        private ForgeConfigSpec ownerSpec;

        public Builder id(String v) { this.id = v; return this; }
        public Builder kind(Kind v) { this.kind = v; return this; }
        public Builder spec(Spec v) { this.spec = v; return this; }
        public Builder langKey(String v) { this.langKey = v; return this; }
        public Builder categoryId(String v) { this.categoryId = v; return this; }

        public Builder bool(ForgeConfigSpec.BooleanValue v, ForgeConfigSpec spec) {
            this.boolGetter = v::get;
            this.boolSetter = v::set;
            this.ownerSpec = spec;
            return this;
        }

        public Builder intRange(ForgeConfigSpec.IntValue v, int lo, int hi,
                                ForgeConfigSpec spec) {
            this.intGetter = v::get;
            this.intSetter = v::set;
            this.min = lo;
            this.max = hi;
            this.ownerSpec = spec;
            return this;
        }

        public Builder doubleRange(ForgeConfigSpec.DoubleValue v, double lo, double hi,
                                   ForgeConfigSpec spec) {
            this.doubleGetter = v::get;
            this.doubleSetter = v::set;
            this.dMin = lo;
            this.dMax = hi;
            this.ownerSpec = spec;
            return this;
        }

        public Builder switchValues(int[] values, String[] suffixes) {
            this.switchValues = values;
            this.switchLangSuffixes = suffixes;
            return this;
        }

        public ConfigOption build() {
            return new ConfigOption(this);
        }
    }
}
