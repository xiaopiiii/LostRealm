package com.lost_realm.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.lost_realm.skill.archer.SkillChargeUp;
import com.lost_realm.skill.archer.SkillDodge;
import com.lost_realm.skill.archer.SkillGaleStorm;
import com.lost_realm.skill.archer.SkillArrowRain;
import com.lost_realm.skill.archer.SkillArrowVolley;
import com.lost_realm.skill.archer.SkillWeaknessInsight;
import com.lost_realm.skill.tank.SkillBlock;
import com.lost_realm.skill.tank.SkillEndlessLife;
import com.lost_realm.skill.tank.SkillHeavyStrike;
import com.lost_realm.skill.tank.SkillOffensiveDrain;
import com.lost_realm.skill.tank.SkillStarShield;
import com.lost_realm.skill.tank.SkillUnyieldingBulwark;
import com.lost_realm.skill.blade.SkillBloodIntent;
import com.lost_realm.skill.blade.SkillBladeOfFrost;
import com.lost_realm.skill.blade.SkillBurningSelf;
import com.lost_realm.skill.blade.SkillFightHarder;
import com.lost_realm.skill.blade.SkillRend;
import com.lost_realm.skill.blade.SkillWarTrail;

/**
 * 节点技能注册表。
 *
 * <p>按勋章分类,保存全部 24 个节点技能实例(五枚勋章 × 6)。
 */
public final class SkillRegistry {

    private static final Map<MedalType, List<Skill>> SKILLS = new EnumMap<>(MedalType.class);

    public static void init() {
        List<Skill> archer = new ArrayList<>();
        archer.add(new SkillDodge());             // Lv.5  闪避(被动)
        archer.add(new SkillChargeUp());          // Lv.10 蓄势
        archer.add(new SkillWeaknessInsight());   // Lv.15 破绽
        archer.add(new SkillGaleStorm());         // Lv.20 疾风骤雨(主动)
        archer.add(new SkillArrowRain());         // Lv.25 箭雨(被动)
        archer.add(new SkillArrowVolley());       // Lv.30 三发(被动)
        SKILLS.put(MedalType.ARCHER, Collections.unmodifiableList(archer));

        List<Skill> blade = new ArrayList<>();
        blade.add(new SkillRend());             // Lv.5  撕裂
        blade.add(new SkillFightHarder());      // Lv.10 愈战愈勇
        blade.add(new SkillBurningSelf());      // Lv.15 燃烧自我(主动)
        blade.add(new SkillBladeOfFrost());     // Lv.20 寒霜之刃
        blade.add(new SkillWarTrail());         // Lv.25 战迹血淋(主动)
        blade.add(new SkillBloodIntent());      // Lv.30 血意
        SKILLS.put(MedalType.BLADE, Collections.unmodifiableList(blade));

        List<Skill> tank = new ArrayList<>();
        tank.add(new SkillHeavyStrike());         // Lv.5  重击
        tank.add(new SkillOffensiveDrain());      // Lv.10 攻势汲取
        tank.add(new SkillStarShield());          // Lv.15 星盾
        tank.add(new SkillUnyieldingBulwark());   // Lv.20 不屈壁垒(主动)
        tank.add(new SkillBlock());               // Lv.25 格挡
        tank.add(new SkillEndlessLife());         // Lv.30 生生不息
        SKILLS.put(MedalType.TANK, Collections.unmodifiableList(tank));

        // ---- 法师勋章(以【诡厄巫法 / Goety】为底座) ----
        List<Skill> mage = new ArrayList<>();
        mage.add(new com.lost_realm.skill.mage.SkillElementalResonance()); // Lv.5  元素共鸣(被动)
        mage.add(new com.lost_realm.skill.mage.SkillSpellCrit());          // Lv.10 法爆(被动)
        mage.add(new com.lost_realm.skill.mage.SkillSoulSiphon());         // Lv.15 灵魂虹吸(被动)
        mage.add(new com.lost_realm.skill.mage.SkillWitchOverload());      // Lv.20 巫术过载(主动)
        mage.add(new com.lost_realm.skill.mage.SkillSoulBurn());           // Lv.25 灵魂燃烧(开关)
        mage.add(new com.lost_realm.skill.mage.SkillBarrageStorm());       // Lv.30 弹幕风暴(被动)
        SKILLS.put(MedalType.MAGE, Collections.unmodifiableList(mage));

        // ---- 召唤师勋章(同样以【诡厄巫法 / Goety】为底座) ----
        List<Skill> summoner = new ArrayList<>();
        summoner.add(new com.lost_realm.skill.summoner.SkillSoulBond());      // Lv.5  灵魂纽带(被动)
        summoner.add(new com.lost_realm.skill.summoner.SkillBoneHardening()); // Lv.10 骸骨硬化(被动)
        summoner.add(new com.lost_realm.skill.summoner.SkillDeadRevival());   // Lv.15 亡者复苏(被动)
        summoner.add(new com.lost_realm.skill.summoner.SkillLegionCommand()); // Lv.20 军团号令(主动)
        summoner.add(new com.lost_realm.skill.summoner.SkillSoulFire());      // Lv.25 魂火(被动)
        summoner.add(new com.lost_realm.skill.summoner.SkillDeadLegion());    // Lv.30 亡者大军(被动)
        SKILLS.put(MedalType.SUMMONER, Collections.unmodifiableList(summoner));
    }

    public static List<Skill> of(MedalType type) {
        return SKILLS.getOrDefault(type, Collections.emptyList());
    }

    public static List<Skill> all() {
        List<Skill> out = new ArrayList<>();
        SKILLS.values().forEach(out::addAll);
        return out;
    }

    public static Skill byId(String id) {
        for (Skill s : all()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    private SkillRegistry() {}
}
