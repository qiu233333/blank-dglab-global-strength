/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.event.player.AttackBlockCallback
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.tags.DamageTypeTags
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.damagesource.DamageTypes
 *  net.minecraft.world.food.FoodData
 */
package com.blankdglab.event;

import com.blankdglab.BlankDgLabClient;
import com.blankdglab.config.BlankDgLabConfig;
import com.blankdglab.device.ConnectionState;
import com.blankdglab.device.DeviceManager;
import com.blankdglab.multiplayer.LinkClientState;
import com.blankdglab.multiplayer.MultiplayerClientNet;
import com.blankdglab.network.ModPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.food.FoodData;

public class GameEventHandler {
    private static float prevCombinedHealth = -1.0f;
    private static boolean lowHealthActive = false;
    private static long fullHealthSince = 0L;
    private static final long FULL_HEALTH_STOP_DELAY = 2000L;
    private static boolean lowHungerActive = false;
    private static long fullHungerSince = 0L;
    private static final long FULL_HUNGER_STOP_DELAY = 2000L;
    private static float damageStrengthA = 0.0f;
    private static float damageStrengthB = 0.0f;
    private static long damageEndTime = 0L;
    private static float lowHealthContribution = 0.0f;
    private static float lowHungerContribution = 0.0f;
    private static boolean wasPaused = false;
    private static int netPartnerA = 0;
    private static int netPartnerB = 0;
    private static int swapOutA = 0;
    private static int swapOutB = 0;
    private static long damageEventSeq = 0L;
    private static long lastSentDamageSeq = -1L;
    private static int lastEventDurationMs = 0;
    private static boolean lastEventContinuous = false;

    public static int getOutgoingStrengthA() {
        return swapOutA;
    }

    public static int getOutgoingStrengthB() {
        return swapOutB;
    }

    public static boolean isOutgoing() {
        return GameEventHandler.isSwapActive();
    }

    private static boolean isSwapActive() {
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        if (!cfg.enableMultiplayerLinks) {
            return false;
        }
        LinkClientState st = LinkClientState.get();
        if (ModPayloads.LinkMode.RANDOM == st.getLinkMode()) {
            return st.isSelfInRandomPool();
        }
        return st.isLinked();
    }

    private static boolean isReceiveAllowed() {
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        return cfg.enableMultiplayerLinks && LinkClientState.get().getPlayers().size() >= 1;
    }

    public static void onPartnerStrength(int strengthA, int strengthB) {
        netPartnerA = Math.max(0, strengthA);
        netPartnerB = Math.max(0, strengthB);
    }

    private static void tickPartnerStrength() {
        if (!GameEventHandler.isReceiveAllowed()) {
            netPartnerA = 0;
            netPartnerB = 0;
            return;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (device.getState() != ConnectionState.CONNECTED) {
            return;
        }
        if (netPartnerA <= 0 && netPartnerB <= 0) {
            if (device.getStrengthA() != 0 || device.getStrengthB() != 0) {
                device.resetStrength();
            }
            return;
        }
        if (!device.isPulseActive()) {
            device.sendPulse(netPartnerA, netPartnerB, 2000);
        }
    }

    public static void register() {
        GameEventHandler.registerAttackBlockCallback();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) {
                wasPaused = false;
                GameEventHandler.resetAllState();
                return;
            }
            if (client.isSameThread() && client.isPaused()) {
                if (!wasPaused) {
                    wasPaused = true;
                    DeviceManager device = DeviceManager.getInstance();
                    if (device.getState() == ConnectionState.CONNECTED) {
                        device.resetStrength();
                    }
                }
                return;
            }
            wasPaused = false;
            if (GameEventHandler.isCreativeOrSpectator(client)) {
                if (lowHealthContribution > 0.0f || lowHungerContribution > 0.0f || damageStrengthA > 0.0f || damageStrengthB > 0.0f) {
                    GameEventHandler.resetAllState();
                    DeviceManager device = DeviceManager.getInstance();
                    if (device.getState() == ConnectionState.CONNECTED) {
                        device.resetStrength();
                    }
                }
                prevCombinedHealth = player.getHealth() + player.getAbsorptionAmount();
                return;
            }
            float currentCombined = player.getHealth() + player.getAbsorptionAmount();
            if (prevCombinedHealth < 0.0f) {
                prevCombinedHealth = currentCombined;
                return;
            }
            if (currentCombined < prevCombinedHealth) {
                float damageAmount = prevCombinedHealth - currentCombined;
                DamageSource source = player.getLastDamageSource();
                if (player.getHealth() <= 0.0f) {
                    GameEventHandler.onPlayerDeath(damageAmount, source);
                } else {
                    GameEventHandler.onPlayerHurt(damageAmount, source);
                }
            }
            prevCombinedHealth = currentCombined;
            GameEventHandler.tickPulseReset();
            GameEventHandler.tickLowHealth(player);
            GameEventHandler.tickLowHunger(player);
            GameEventHandler.tickPartnerStrength();
        });
    }

    private static boolean isCreativeOrSpectator(Minecraft client) {
        if (client.player == null) {
            return false;
        }
        return client.player.isCreative() || client.player.isSpectator();
    }

    private static void resetAllState() {
        prevCombinedHealth = -1.0f;
        lowHealthActive = false;
        lowHungerActive = false;
        lowHealthContribution = 0.0f;
        lowHungerContribution = 0.0f;
        damageStrengthA = 0.0f;
        damageStrengthB = 0.0f;
        damageEndTime = 0L;
        fullHealthSince = 0L;
        fullHungerSince = 0L;
        swapOutA = 0;
        swapOutB = 0;
        lastSentDamageSeq = damageEventSeq;
        lastEventDurationMs = 0;
        lastEventContinuous = false;
    }

    private static void tickPulseReset() {
        if (damageEndTime > 0L && System.currentTimeMillis() >= damageEndTime) {
            damageStrengthA = 0.0f;
            damageStrengthB = 0.0f;
            damageEndTime = 0L;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (GameEventHandler.isSwapActive()) {
            GameEventHandler.updateCombinedStrength();
            return;
        }
        swapOutA = 0;
        swapOutB = 0;
        if (!device.isPulseActive() && !GameEventHandler.isTransientSpikeActive()) {
            float totalA = damageStrengthA + lowHealthContribution + lowHungerContribution;
            float totalB = damageStrengthB + lowHealthContribution + lowHungerContribution;
            if (totalA > 0.0f || totalB > 0.0f) {
                GameEventHandler.updateCombinedStrength();
            } else if (device.getStrengthA() != 0 || device.getStrengthB() != 0) {
                device.resetStrength();
            }
        }
    }

    private static void updateCombinedStrength() {
        GameEventHandler.updateCombinedStrength(3000);
    }

    private static void updateCombinedStrength(int transientDurationMs) {
        if (GameEventHandler.isSwapActive()) {
            boolean newEvent;
            float totalA = damageStrengthA + lowHealthContribution + lowHungerContribution;
            float totalB = damageStrengthB + lowHealthContribution + lowHungerContribution;
            int cA = totalA > 0.0f ? GameEventHandler.clampStrength(Math.round(totalA)) : 0;
            int cB = totalB > 0.0f ? GameEventHandler.clampStrength(Math.round(totalB)) : 0;
            BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
            if (!mode.showA()) {
                cA = 0;
            }
            if (!mode.showB()) {
                cB = 0;
            }
            boolean bl = newEvent = damageEventSeq != lastSentDamageSeq;
            if (cA != swapOutA || cB != swapOutB || newEvent) {
                swapOutA = cA;
                swapOutB = cB;
                lastSentDamageSeq = damageEventSeq;
                MultiplayerClientNet.sendStrength(cA, cB, lastEventDurationMs, damageEventSeq, lastEventContinuous);
            }
            return;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (device.getState() != ConnectionState.CONNECTED) {
            return;
        }
        float totalA = damageStrengthA + lowHealthContribution + lowHungerContribution;
        float totalB = damageStrengthB + lowHealthContribution + lowHungerContribution;
        if (totalA > 0.0f || totalB > 0.0f) {
            int clampedA = GameEventHandler.clampStrength(Math.round(totalA));
            int clampedB = GameEventHandler.clampStrength(Math.round(totalB));
            BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
            if (!mode.showA()) {
                clampedA = 0;
            }
            if (!mode.showB()) {
                clampedB = 0;
            }
            if (clampedA > 0 || clampedB > 0) {
                if (damageStrengthA > 0.0f || damageStrengthB > 0.0f) {
                    device.sendPulse(clampedA, clampedB, transientDurationMs);
                } else if (!device.isPulseActive()) {
                    device.sendPulse(clampedA, clampedB, 3000);
                } else {
                    device.updateStrength(clampedA, clampedB);
                }
            } else {
                device.resetStrength();
            }
        } else {
            device.resetStrength();
        }
    }

    private static boolean isTransientSpikeActive() {
        return damageEndTime > System.currentTimeMillis();
    }

    private static void beginDamageEvent(int durationMs, boolean continuous) {
        ++damageEventSeq;
        lastEventDurationMs = Math.max(0, durationMs);
        lastEventContinuous = continuous;
    }

    private static boolean isContinuousCategory(DamageCategory category) {
        return category == DamageCategory.FIRE || category == DamageCategory.DOT || category == DamageCategory.DROWN || category == DamageCategory.ENVIRONMENTAL;
    }

    private static void tickLowHealth(LocalPlayer player) {
        float maxHealth;
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        if (!cfg.enableLowHealthFeedback) {
            if (lowHealthContribution > 0.0f) {
                lowHealthContribution = 0.0f;
                lowHealthActive = false;
                GameEventHandler.updateCombinedStrength();
            }
            return;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (!GameEventHandler.isSwapActive() && device.getState() != ConnectionState.CONNECTED) {
            if (lowHealthContribution > 0.0f) {
                lowHealthContribution = 0.0f;
                lowHealthActive = false;
                GameEventHandler.updateCombinedStrength();
            }
            return;
        }
        float currentHealth = player.getHealth();
        if (currentHealth >= (maxHealth = player.getMaxHealth()) || currentHealth <= 0.0f) {
            if (lowHealthActive) {
                if (fullHealthSince == 0L) {
                    fullHealthSince = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - fullHealthSince >= 2000L) {
                    GameEventHandler.stopLowHealth();
                    fullHealthSince = 0L;
                }
            }
            return;
        }
        fullHealthSince = 0L;
        float lostHearts = (maxHealth - currentHealth) / 2.0f;
        if (lostHearts < 0.0f) {
            lostHearts = 0.0f;
        }
        float newContribution = lostHearts * (float)cfg.lowHealthStrengthPer10Percent;
        lowHealthActive = true;
        if (Math.abs(newContribution - lowHealthContribution) > 0.01f) {
            lowHealthContribution = newContribution;
            if (!GameEventHandler.isTransientSpikeActive()) {
                GameEventHandler.updateCombinedStrength();
            }
        }
    }

    private static void stopLowHealth() {
        lowHealthActive = false;
        lowHealthContribution = 0.0f;
        GameEventHandler.updateCombinedStrength();
    }

    private static void tickLowHunger(LocalPlayer player) {
        int maxFood;
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        if (!cfg.enableLowHungerFeedback) {
            if (lowHungerContribution > 0.0f) {
                lowHungerContribution = 0.0f;
                lowHungerActive = false;
                GameEventHandler.updateCombinedStrength();
            }
            return;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (!GameEventHandler.isSwapActive() && device.getState() != ConnectionState.CONNECTED) {
            if (lowHungerContribution > 0.0f) {
                lowHungerContribution = 0.0f;
                lowHungerActive = false;
                GameEventHandler.updateCombinedStrength();
            }
            return;
        }
        FoodData foodData = player.getFoodData();
        int currentFood = foodData.getFoodLevel();
        if (currentFood >= (maxFood = 20)) {
            if (lowHungerActive) {
                if (fullHungerSince == 0L) {
                    fullHungerSince = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - fullHungerSince >= 2000L) {
                    GameEventHandler.stopLowHunger();
                    fullHungerSince = 0L;
                }
            }
            return;
        }
        fullHungerSince = 0L;
        float newContribution = (float)((maxFood - currentFood) * cfg.lowHungerStrengthPer10Percent) / 2.0f;
        lowHungerActive = true;
        if (Math.abs(newContribution - lowHungerContribution) > 0.01f) {
            lowHungerContribution = newContribution;
            if (!GameEventHandler.isTransientSpikeActive()) {
                GameEventHandler.updateCombinedStrength();
            }
        }
    }

    private static void stopLowHunger() {
        lowHungerActive = false;
        lowHungerContribution = 0.0f;
        GameEventHandler.updateCombinedStrength();
    }

    private static void registerAttackBlockCallback() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!BlankDgLabConfig.INSTANCE.enableBlockBreakFeedback) {
                return InteractionResult.PASS;
            }
            DeviceManager device = DeviceManager.getInstance();
            if (!GameEventHandler.isSwapActive() && device.getState() != ConnectionState.CONNECTED) {
                return InteractionResult.PASS;
            }
            if (player.isCreative() || player.isSpectator()) {
                return InteractionResult.PASS;
            }
            BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
            damageStrengthA = cfg.blockBreakStrengthA;
            damageStrengthB = cfg.blockBreakStrengthB;
            damageEndTime = System.currentTimeMillis() + (long)cfg.blockBreakPulseDuration;
            device.refreshPulseWindow(cfg.blockBreakPulseDuration);
            GameEventHandler.updateCombinedStrength();
            BlankDgLabClient.getLogger().info("Block break: A={}, B={}", (Object)Float.valueOf(damageStrengthA), (Object)Float.valueOf(damageStrengthB));
            return InteractionResult.PASS;
        });
    }

    public static void onPlayerAttack() {
        if (!BlankDgLabConfig.INSTANCE.enableAttackFeedback) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.isCreative() || mc.player.isSpectator())) {
            return;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (!GameEventHandler.isSwapActive() && device.getState() != ConnectionState.CONNECTED) {
            return;
        }
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        damageStrengthA = cfg.attackStrengthA;
        damageStrengthB = cfg.attackStrengthB;
        damageEndTime = System.currentTimeMillis() + (long)cfg.attackPulseDuration;
        device.refreshPulseWindow(cfg.attackPulseDuration);
        GameEventHandler.updateCombinedStrength(cfg.attackPulseDuration);
        BlankDgLabClient.getLogger().info("Attack: A={}, B={}", (Object)Float.valueOf(damageStrengthA), (Object)Float.valueOf(damageStrengthB));
    }

    public static void onPlayerHurt(float damageAmount, DamageSource source) {
        int duration;
        int strengthB;
        int strengthA;
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        DeviceManager device = DeviceManager.getInstance();
        if (!cfg.enableDamageFeedback) {
            return;
        }
        if (!GameEventHandler.isSwapActive() && device.getState() != ConnectionState.CONNECTED) {
            return;
        }
        DamageCategory category = GameEventHandler.categorizeDamage(source);
        switch (category.ordinal()) {
            case 1: {
                if (!cfg.enableProjectileFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.projectileStrengthA;
                strengthB = cfg.projectileStrengthB;
                duration = cfg.projectilePulseDuration;
                break;
            }
            case 2: {
                if (!cfg.enableFireFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.fireStrengthA;
                strengthB = cfg.fireStrengthB;
                duration = cfg.firePulseDuration;
                break;
            }
            case 3: {
                if (!cfg.enableFallFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.fallStrengthA;
                strengthB = cfg.fallStrengthB;
                duration = cfg.fallPulseDuration;
                break;
            }
            case 4: {
                if (!cfg.enableDotFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.dotStrengthA;
                strengthB = cfg.dotStrengthB;
                duration = cfg.dotPulseDuration;
                break;
            }
            case 5: {
                if (!cfg.enableMagicFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.magicStrengthA;
                strengthB = cfg.magicStrengthB;
                duration = cfg.magicPulseDuration;
                break;
            }
            case 6: {
                if (!cfg.enableExplosionFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.explosionStrengthA;
                strengthB = cfg.explosionStrengthB;
                duration = cfg.explosionPulseDuration;
                break;
            }
            case 7: {
                if (!cfg.enableDrownFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.drownStrengthA;
                strengthB = cfg.drownStrengthB;
                duration = cfg.drownPulseDuration;
                break;
            }
            case 8: {
                if (!cfg.enableEnvironmentalFeedback) {
                    GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                    return;
                }
                strengthA = cfg.environmentalStrengthA;
                strengthB = cfg.environmentalStrengthB;
                duration = cfg.environmentalPulseDuration;
                break;
            }
            default: {
                GameEventHandler.onPlayerHurtGeneral(damageAmount, cfg, device, category);
                return;
            }
        }
        int maxStr = 200;
        damageStrengthA = Math.min(damageStrengthA + (float)strengthA, (float)maxStr);
        damageStrengthB = Math.min(damageStrengthB + (float)strengthB, (float)maxStr);
        damageEndTime = System.currentTimeMillis() + (long)duration;
        GameEventHandler.beginDamageEvent(duration, GameEventHandler.isContinuousCategory(category));
        device.refreshPulseWindow(duration);
        GameEventHandler.updateCombinedStrength(duration);
        BlankDgLabClient.getLogger().info("Damage [{}]: dmg={}, A={}, B={}", new Object[]{category, Float.valueOf(damageAmount), strengthA, strengthB});
    }

    private static void onPlayerHurtGeneral(float damageAmount, BlankDgLabConfig cfg, DeviceManager device, DamageCategory category) {
        float calculatedA = Math.max((float)cfg.damageMinStrength, Math.min((float)cfg.damageMaxStrength, damageAmount * cfg.damageMultiplierA));
        float calculatedB = Math.max((float)cfg.damageMinStrength, Math.min((float)cfg.damageMaxStrength, damageAmount * cfg.damageMultiplierB));
        int maxStr = 200;
        damageStrengthA = Math.min(damageStrengthA + calculatedA, (float)maxStr);
        damageStrengthB = Math.min(damageStrengthB + calculatedB, (float)maxStr);
        damageEndTime = System.currentTimeMillis() + (long)cfg.damagePulseDuration;
        GameEventHandler.beginDamageEvent(cfg.damagePulseDuration, GameEventHandler.isContinuousCategory(category));
        device.refreshPulseWindow(cfg.damagePulseDuration);
        GameEventHandler.updateCombinedStrength(cfg.damagePulseDuration);
        BlankDgLabClient.getLogger().info("Damage [GENERAL]: dmg={}, A={}, B={}", new Object[]{Float.valueOf(damageAmount), Float.valueOf(calculatedA), Float.valueOf(calculatedB)});
    }

    public static void onPlayerDeath(float damageAmount, DamageSource source) {
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        if (!cfg.enableDeathPulse) {
            return;
        }
        int strengthA = GameEventHandler.clampStrength(cfg.deathPulseStrengthA);
        int strengthB = GameEventHandler.clampStrength(cfg.deathPulseStrengthB);
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (!mode.showA()) {
            strengthA = 0;
        }
        if (!mode.showB()) {
            strengthB = 0;
        }
        if (GameEventHandler.isSwapActive()) {
            GameEventHandler.beginDamageEvent(cfg.deathPulseDurationMs, false);
            MultiplayerClientNet.sendStrength(strengthA, strengthB, cfg.deathPulseDurationMs, damageEventSeq, false);
            return;
        }
        DeviceManager device = DeviceManager.getInstance();
        if (device.getState() != ConnectionState.CONNECTED) {
            return;
        }
        device.sendPulse(strengthA, strengthB, cfg.deathPulseDurationMs);
        BlankDgLabClient.getLogger().info("Death pulse: A={}, B={}, duration={}ms", new Object[]{strengthA, strengthB, cfg.deathPulseDurationMs});
    }

    public static DamageCategory categorizeDamage(DamageSource source) {
        if (source == null) {
            return DamageCategory.GENERAL;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return DamageCategory.PROJECTILE;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return DamageCategory.FIRE;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return DamageCategory.EXPLOSION;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return DamageCategory.FALL;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return DamageCategory.DROWN;
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.WITHER)) {
            return DamageCategory.DOT;
        }
        if (source.is(DamageTypes.MAGIC)) {
            return DamageCategory.MAGIC;
        }
        if (source.is(DamageTypes.INDIRECT_MAGIC)) {
            return DamageCategory.MAGIC;
        }
        if (source.is(DamageTypes.THORNS)) {
            return DamageCategory.MAGIC;
        }
        if (source.is(DamageTypes.DRAGON_BREATH)) {
            return DamageCategory.MAGIC;
        }
        if (source.is(DamageTypes.SONIC_BOOM)) {
            return DamageCategory.MAGIC;
        }
        if (source.is(DamageTypes.CACTUS)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.STARVE)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.IN_WALL)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.FLY_INTO_WALL)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.HOT_FLOOR)) {
            return DamageCategory.FIRE;
        }
        if (source.is(DamageTypes.STALAGMITE)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.FREEZE)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        if (source.is(DamageTypes.DRY_OUT)) {
            return DamageCategory.ENVIRONMENTAL;
        }
        return DamageCategory.GENERAL;
    }

    private static int clampStrength(int strength) {
        BlankDgLabConfig cfg = BlankDgLabConfig.INSTANCE;
        int rawCeiling = GameEventHandler.isSwapActive() ? cfg.maxStrength : 200;
        return Math.max(cfg.minStrength, Math.min(rawCeiling, strength));
    }

    public static enum DamageCategory {
        GENERAL,
        PROJECTILE,
        FIRE,
        FALL,
        DOT,
        MAGIC,
        EXPLOSION,
        DROWN,
        ENVIRONMENTAL;

    }
}
