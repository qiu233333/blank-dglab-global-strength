/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.network.chat.ClickEvent
 *  net.minecraft.network.chat.ClickEvent$OpenUrl
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.HoverEvent
 *  net.minecraft.network.chat.HoverEvent$ShowText
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 */
package com.blankdglab.device;

import com.blankdglab.BlankDgLabClient;
import com.blankdglab.config.BlankDgLabConfig;
import com.blankdglab.device.ConnectionListener;
import com.blankdglab.device.ConnectionState;
import com.blankdglab.device.DGLabEmbeddedServer;
import com.blankdglab.config.GlobalStrengthSettings;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class DeviceManager
implements DGLabEmbeddedServer.EmbeddedServerListener {
    private static DeviceManager instance;
    private DGLabEmbeddedServer server;
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private int strengthA;
    private int strengthB;
    private int appLimitA = 0;
    private int appLimitB = 0;
    private boolean appLimitReceived = false;
    private long pulseEndTime = 0L;
    private boolean initialSyncDone = false;
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<ConnectionListener>();
    private final List<String> connectionLog = new CopyOnWriteArrayList<String>();
    private LimitCategory lastLimitCategoryA = null;
    private LimitCategory lastLimitCategoryB = null;
    private static final int WAVEFORM_MAX_STRENGTH = 100;
    private static final String[] BEAT_WAVEFORM;

    private DeviceManager() {
    }

    public static synchronized DeviceManager getInstance() {
        if (instance == null) {
            instance = new DeviceManager();
        }
        return instance;
    }

    public void connect() {
        if (this.server != null && this.server.isRunning()) {
            BlankDgLabClient.getLogger().info("Server already running");
            return;
        }
        int port = BlankDgLabConfig.INSTANCE.serverPort;
        if (!DGLabEmbeddedServer.isPortAvailable(port)) {
            BlankDgLabClient.getLogger().warn("Port {} is occupied, trying alternatives", (Object)port);
            int alt = DeviceManager.findFreePort(port);
            if (alt < 0) {
                BlankDgLabClient.getLogger().error("No free port found near {} ({}\u2013{} and 8877\u20138900 all occupied). \u5d4c\u5165\u5f0f\u670d\u52a1\u5668\u672a\u542f\u52a8\uff0c\u8bf7\u91ca\u653e\u7aef\u53e3\u540e\u91cd\u8bd5\u3002", new Object[]{port, port + 1, port + 64});
                this.setState(ConnectionState.DISCONNECTED);
                return;
            }
            BlankDgLabConfig.INSTANCE.serverPort = port = alt;
            BlankDgLabConfig.save();
            BlankDgLabClient.getLogger().info("Using alternate port {} (written back to config)", (Object)port);
        }
        BlankDgLabClient.getLogger().info("Starting embedded DG-LAB server on port {}", (Object)port);
        try {
            this.server = new DGLabEmbeddedServer(port, this);
            this.server.start();
            this.setState(ConnectionState.CONNECTING);
            for (ConnectionListener listener : this.listeners) {
                listener.onClientIdReceived(this.server.getSessionId());
            }
        }
        catch (Exception e) {
            BlankDgLabClient.getLogger().error("Failed to start embedded server", (Throwable)e);
            this.setState(ConnectionState.DISCONNECTED);
        }
    }

    private static int findFreePort(int configured) {
        int p;
        for (p = 20000; p < 21000; ++p) {
            if (p == configured || !DGLabEmbeddedServer.isPortAvailable(p)) continue;
            return p;
        }
        for (p = configured + 1; p <= configured + 64 && p <= 65535; ++p) {
            if (!DGLabEmbeddedServer.isPortAvailable(p)) continue;
            return p;
        }
        for (p = 8877; p < 8900; ++p) {
            if (p == configured || !DGLabEmbeddedServer.isPortAvailable(p)) continue;
            return p;
        }
        return -1;
    }

    public void disconnect() {
        if (this.server != null) {
            try {
                if (this.isConnected()) {
                    this.resetStrength();
                    Thread.sleep(200L);
                }
                this.server.stopServer();
            }
            catch (Exception e) {
                BlankDgLabClient.getLogger().warn("Error stopping server", (Throwable)e);
            }
            this.server = null;
        }
        this.strengthA = 0;
        this.strengthB = 0;
        this.appLimitA = 0;
        this.appLimitB = 0;
        this.appLimitReceived = false;
        this.pulseEndTime = 0L;
        this.setState(ConnectionState.DISCONNECTED);
    }

    public void shutdown() {
        this.disconnect();
    }

    public void forceStop() {
        if (this.server != null) {
            this.server.stopHeartbeat();
            try {
                this.server.stopServer();
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.server = null;
        }
        this.strengthA = 0;
        this.strengthB = 0;
        this.appLimitA = 0;
        this.appLimitB = 0;
        this.appLimitReceived = false;
        this.pulseEndTime = 0L;
        this.state = ConnectionState.DISCONNECTED;
    }

    public String getQrCodeContent() {
        if (this.server == null || !this.server.isRunning()) {
            return null;
        }
        return this.server.getQrCodeContent();
    }

    public int getBoundPort() {
        if (this.server != null && this.server.isRunning()) {
            return this.server.getPort();
        }
        return BlankDgLabConfig.INSTANCE.serverPort;
    }

    public List<String> getConnectionLog() {
        return this.connectionLog;
    }

    private static String scaleWaveformStrength(String frame, int targetStrength) {
        if (frame.length() != 16) {
            return frame;
        }
        if (targetStrength >= 100) {
            return frame;
        }
        String freqPart = frame.substring(0, 8);
        String strengthPart = frame.substring(8);
        double scale = (double)targetStrength / 100.0;
        StringBuilder result = new StringBuilder(freqPart);
        for (int i = 0; i < 4; ++i) {
            int val = Integer.parseInt(strengthPart.substring(i * 2, i * 2 + 2), 16);
            int scaled = (int)Math.round((double)val * scale);
            result.append(String.format("%02x", Math.min(255, scaled)));
        }
        return result.toString();
    }

    public int getGameLimit() {
        return BlankDgLabConfig.INSTANCE.maxStrength;
    }

    public int getEffectiveLimitA() {
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (!mode.showA()) {
            return 0;
        }
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        if (mode == BlankDgLabConfig.ChannelMode.BOTH) {
            return maxStrength;
        }
        int cfgLimit = BlankDgLabConfig.INSTANCE.channelLimitA;
        if (!this.appLimitReceived || this.appLimitA == 0) {
            return Math.min(cfgLimit, maxStrength);
        }
        return Math.min(this.appLimitA, Math.min(cfgLimit, maxStrength));
    }

    public int getEffectiveLimitB() {
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (!mode.showB()) {
            return 0;
        }
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        if (mode == BlankDgLabConfig.ChannelMode.BOTH) {
            return maxStrength;
        }
        int cfgLimit = BlankDgLabConfig.INSTANCE.channelLimitB;
        if (!this.appLimitReceived || this.appLimitB == 0) {
            return Math.min(cfgLimit, maxStrength);
        }
        return Math.min(this.appLimitB, Math.min(cfgLimit, maxStrength));
    }

    public int getDisplayLimitA() {
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (!mode.showA()) {
            return 0;
        }
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        if (mode == BlankDgLabConfig.ChannelMode.BOTH) {
            return maxStrength;
        }
        if (!this.appLimitReceived || this.appLimitA == 0) {
            return BlankDgLabConfig.INSTANCE.channelLimitA;
        }
        return this.appLimitA;
    }

    public int getDisplayLimitB() {
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (!mode.showB()) {
            return 0;
        }
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        if (mode == BlankDgLabConfig.ChannelMode.BOTH) {
            return maxStrength;
        }
        if (!this.appLimitReceived || this.appLimitB == 0) {
            return BlankDgLabConfig.INSTANCE.channelLimitB;
        }
        return this.appLimitB;
    }

    public int getSyncedEffectiveLimit() {
        int limA = this.getEffectiveLimitA();
        int limB = this.getEffectiveLimitB();
        return Math.min(limA, limB);
    }

    public boolean isAppLimitZero() {
        return this.appLimitReceived && (this.appLimitA == 0 || this.appLimitB == 0);
    }

    public void sendPulse(int sA, int sB, int durationMs) {
        if (!this.isReady()) {
            BlankDgLabClient.getLogger().warn("sendPulse skipped: not ready, connected={}", (Object)this.isConnected());
            return;
        }
        if (BlankDgLabConfig.INSTANCE.syncABChannels) {
            int max;
            sA = max = Math.max(sA, sB);
            sB = max;
        }
        int effectiveLimitA = this.getEffectiveLimitA();
        int effectiveLimitB = this.getEffectiveLimitB();
        int cfgLimitA = BlankDgLabConfig.INSTANCE.channelLimitA;
        int cfgLimitB = BlankDgLabConfig.INSTANCE.channelLimitB;
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        sA = Math.min(sA, GlobalStrengthSettings.rawLimit(1, effectiveLimitA));
        sB = Math.min(sB, GlobalStrengthSettings.rawLimit(2, effectiveLimitB));
        int minStrength = BlankDgLabConfig.INSTANCE.minStrength;
        if (sA > 0 && sA < minStrength) {
            sA = minStrength;
        }
        if (sB > 0 && sB < minStrength) {
            sB = minStrength;
        }
        if (effectiveLimitA > 0) {
            this.clearPulse(1);
        }
        if (effectiveLimitB > 0) {
            this.clearPulse(2);
        }
        sA = Math.max(0, Math.min(GlobalStrengthSettings.scale(1, sA), Math.min(effectiveLimitA, maxStrength)));
        sB = Math.max(0, Math.min(GlobalStrengthSettings.scale(2, sB), Math.min(effectiveLimitB, maxStrength)));
        int actualDurationMs = Math.max(100, durationMs);
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (mode == BlankDgLabConfig.ChannelMode.ONLY_A && effectiveLimitA > 0) {
            this.server.sendTempIntensity(1, sA, actualDurationMs);
            this.server.sendTempIntensity(2, 0, 0);
        } else if (mode == BlankDgLabConfig.ChannelMode.ONLY_B && effectiveLimitB > 0) {
            this.server.sendTempIntensity(1, 0, 0);
            this.server.sendTempIntensity(2, sB, actualDurationMs);
        } else {
            if (effectiveLimitA > 0) {
                this.server.sendTempIntensity(1, sA, actualDurationMs);
            }
            if (effectiveLimitB > 0) {
                this.server.sendTempIntensity(2, sB, actualDurationMs);
            }
        }
        this.strengthA = sA;
        this.strengthB = sB;
        this.notifyStrengthChanged();
        this.server.scheduleReset(actualDurationMs + 1000);
        this.pulseEndTime = System.currentTimeMillis() + (long)actualDurationMs;
        BlankDgLabClient.getLogger().info("Sent pulse: A={}, B={}, duration={}ms, appLimit={}/{}, cfgLimitA={}/B={}", new Object[]{sA, sB, durationMs, this.appLimitA, this.appLimitB, cfgLimitA, cfgLimitB});
        if (this.appLimitReceived && (this.appLimitA == 0 || this.appLimitB == 0)) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage((Component)Component.literal((String)"\u00a7c\u26a0 APP\u4e0a\u9650\u4e3a0\uff0c\u8bf7\u5728APP\u4e2d\u624b\u52a8\u8c03\u6574A/B\u901a\u9053\u4e0a\u9650"));
                }
            });
        }
    }

    public void updateStrength(int sA, int sB) {
        if (!this.isReady()) {
            BlankDgLabClient.getLogger().warn("updateStrength skipped: not ready, connected={}", (Object)this.isConnected());
            return;
        }
        if (BlankDgLabConfig.INSTANCE.syncABChannels) {
            int max;
            sA = max = Math.max(sA, sB);
            sB = max;
        }
        int effectiveLimitA = this.getEffectiveLimitA();
        int effectiveLimitB = this.getEffectiveLimitB();
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        sA = Math.min(sA, GlobalStrengthSettings.rawLimit(1, effectiveLimitA));
        sB = Math.min(sB, GlobalStrengthSettings.rawLimit(2, effectiveLimitB));
        int minStrength = BlankDgLabConfig.INSTANCE.minStrength;
        if (sA > 0 && sA < minStrength) {
            sA = minStrength;
        }
        if (sB > 0 && sB < minStrength) {
            sB = minStrength;
        }
        sA = Math.max(0, Math.min(GlobalStrengthSettings.scale(1, sA), Math.min(effectiveLimitA, maxStrength)));
        sB = Math.max(0, Math.min(GlobalStrengthSettings.scale(2, sB), Math.min(effectiveLimitB, maxStrength)));
        if (sA == this.strengthA && sB == this.strengthB) {
            return;
        }
        int remainMs = Math.max(100, (int)(this.pulseEndTime - System.currentTimeMillis()));
        BlankDgLabConfig.ChannelMode mode = BlankDgLabConfig.INSTANCE.channelMode;
        if (mode == BlankDgLabConfig.ChannelMode.ONLY_A && effectiveLimitA > 0) {
            this.server.sendTempIntensity(1, sA, remainMs);
            this.server.sendTempIntensity(2, 0, 0);
        } else if (mode == BlankDgLabConfig.ChannelMode.ONLY_B && effectiveLimitB > 0) {
            this.server.sendTempIntensity(1, 0, 0);
            this.server.sendTempIntensity(2, sB, remainMs);
        } else {
            if (effectiveLimitA > 0) {
                this.server.sendTempIntensity(1, sA, remainMs);
            }
            if (effectiveLimitB > 0) {
                this.server.sendTempIntensity(2, sB, remainMs);
            }
        }
        this.strengthA = sA;
        this.strengthB = sB;
        this.notifyStrengthChanged();
    }

    public void refreshPulseWindow(int fullDurationMs) {
        long duration = Math.max(100, fullDurationMs);
        this.pulseEndTime = System.currentTimeMillis() + duration;
        if (this.server != null && this.server.isRunning()) {
            this.server.scheduleReset((int)duration + 1000);
        }
    }

    public void sendStrengthSet(int channel, int value) {
        int effectiveLimit;
        if (!this.isReady()) {
            return;
        }
        int n = effectiveLimit = channel == 1 ? this.getEffectiveLimitA() : this.getEffectiveLimitB();
        if (effectiveLimit <= 0) {
            return;
        }
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        value = Math.min(value, GlobalStrengthSettings.rawLimit(channel, effectiveLimit));
        value = Math.max(0, value);
        int minStrength = BlankDgLabConfig.INSTANCE.minStrength;
        if (value > 0 && value < minStrength) {
            value = minStrength;
        }
        value = Math.min(GlobalStrengthSettings.scale(channel, value), Math.min(effectiveLimit, maxStrength));
        this.server.sendMessage("strength-" + channel + "+2+" + value);
        if (channel == 1) {
            this.strengthA = value;
        } else {
            this.strengthB = value;
        }
        this.notifyStrengthChanged();
    }

    public void sendStrengthAdd(int channel, int value) {
        int maxStrength;
        int absCeiling;
        int effectiveLimit;
        if (!this.isReady()) {
            return;
        }
        int n = effectiveLimit = channel == 1 ? this.getEffectiveLimitA() : this.getEffectiveLimitB();
        if (effectiveLimit <= 0) {
            return;
        }
        int current = channel == 1 ? this.strengthA : this.strengthB;
        int target = Math.min(current + GlobalStrengthSettings.scale(channel, value), absCeiling = Math.min(effectiveLimit, maxStrength = BlankDgLabConfig.INSTANCE.maxStrength));
        if (target <= current) {
            return;
        }
        this.server.sendMessage("strength-" + channel + "+2+" + target);
        if (channel == 1) {
            this.strengthA = target;
        } else {
            this.strengthB = target;
        }
        this.notifyStrengthChanged();
    }

    public void sendStrengthSub(int channel, int value) {
        int effectiveLimit;
        if (!this.isReady()) {
            return;
        }
        int n = effectiveLimit = channel == 1 ? this.getEffectiveLimitA() : this.getEffectiveLimitB();
        if (effectiveLimit <= 0) {
            return;
        }
        int current = channel == 1 ? this.strengthA : this.strengthB;
        int target = Math.max(0, current - GlobalStrengthSettings.scale(channel, value));
        if (target >= current) {
            return;
        }
        this.server.sendMessage("strength-" + channel + "+2+" + target);
        if (channel == 1) {
            this.strengthA = target;
        } else {
            this.strengthB = target;
        }
        this.notifyStrengthChanged();
    }

    public void clearPulse(int channel) {
        if (!this.isReady()) {
            return;
        }
        this.server.sendMessage("clear-" + channel);
    }

    public boolean isConnected() {
        return this.server != null && this.server.isConnected();
    }

    public boolean isReady() {
        return this.isConnected();
    }

    public boolean isServerRunning() {
        return this.server != null && this.server.isRunning();
    }

    public ConnectionState getState() {
        return this.state;
    }

    public String getClientId() {
        return this.server != null ? this.server.getSessionId() : null;
    }

    public int getStrengthA() {
        return this.strengthA;
    }

    public int getStrengthB() {
        return this.strengthB;
    }

    public int getAppLimitA() {
        return this.appLimitA;
    }

    public int getAppLimitB() {
        return this.appLimitB;
    }

    public boolean isAppLimitReceived() {
        return this.appLimitReceived;
    }

    public void updateAppLimitA(int limit) {
        if (limit >= 0) {
            this.appLimitA = limit;
            this.appLimitReceived = true;
            BlankDgLabClient.getLogger().info("[V4] appLimitA updated from slots.patch: {}", (Object)limit);
        }
    }

    public void updateAppLimitB(int limit) {
        if (limit >= 0) {
            this.appLimitB = limit;
            this.appLimitReceived = true;
            BlankDgLabClient.getLogger().info("[V4] appLimitB updated from slots.patch: {}", (Object)limit);
        }
    }

    public void syncConfigLimitToApp() {
        if (!this.isReady()) {
            return;
        }
        int cfgLimitA = BlankDgLabConfig.INSTANCE.channelLimitA;
        int cfgLimitB = BlankDgLabConfig.INSTANCE.channelLimitB;
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        int effectiveLimitA = Math.min(cfgLimitA, maxStrength);
        int effectiveLimitB = Math.min(cfgLimitB, maxStrength);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a7e\u26a0 \u8bf7\u5728APP\u4e2d\u624b\u52a8\u8c03\u6574A/B\u901a\u9053\u4e0a\u9650\u81f3 A:" + effectiveLimitA + " B:" + effectiveLimitB)));
            }
        });
    }

    public boolean isPulseActive() {
        return this.pulseEndTime > 0L && System.currentTimeMillis() < this.pulseEndTime;
    }

    public void resetStrength() {
        if (!this.isReady()) {
            return;
        }
        this.sendStrengthSet(1, 0);
        this.sendStrengthSet(2, 0);
        this.clearPulse(1);
        this.clearPulse(2);
        this.strengthA = 0;
        this.strengthB = 0;
        this.pulseEndTime = 0L;
        this.notifyStrengthChanged();
        BlankDgLabClient.getLogger().info("Strength reset to 0");
    }

    public void addListener(ConnectionListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ConnectionListener listener) {
        this.listeners.remove(listener);
    }

    private void setState(ConnectionState newState) {
        this.state = newState;
        for (ConnectionListener listener : this.listeners) {
            listener.onStateChanged(newState);
        }
    }

    private void notifyStrengthChanged() {
        for (ConnectionListener listener : this.listeners) {
            listener.onStrengthChanged(this.strengthA, this.strengthB);
        }
    }

    @Override
    public void onAppConnected() {
        BlankDgLabClient.getLogger().info("App connected successfully");
        int cfgLimitA = BlankDgLabConfig.INSTANCE.channelLimitA;
        int cfgLimitB = BlankDgLabConfig.INSTANCE.channelLimitB;
        int maxStrength = BlankDgLabConfig.INSTANCE.maxStrength;
        int effectiveLimitA = Math.min(cfgLimitA, maxStrength);
        int effectiveLimitB = Math.min(cfgLimitB, maxStrength);
        boolean isReconnect = this.appLimitReceived;
        this.appLimitA = 0;
        this.appLimitB = 0;
        this.appLimitReceived = false;
        this.initialSyncDone = false;
        this.lastLimitCategoryA = null;
        this.lastLimitCategoryB = null;
        this.setState(ConnectionState.CONNECTED);
        if (this.isReady()) {
            this.server.sendMessage("strength-1+2+0");
            this.server.sendMessage("strength-2+2+0");
            this.strengthA = 0;
            this.strengthB = 0;
            this.notifyStrengthChanged();
            this.initialSyncDone = true;
            BlankDgLabClient.getLogger().info("Initial sync complete, strength reset to 0");
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                if (isReconnect) {
                    mc.player.sendSystemMessage((Component)Component.literal((String)"\u00a7a\u2713 APP\u5df2\u81ea\u52a8\u91cd\u8fde"));
                } else {
                    mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a7a\u2713 \u90ca\u72fcAPP\u5df2\u8fde\u63a5\uff0c\u8bf7\u5728APP\u4e2d\u624b\u52a8\u8c03\u6574A/B\u901a\u9053\u4e0a\u9650\u81f3 A:" + effectiveLimitA + " B:" + effectiveLimitB)));
                }
            }
        });
        BlankDgLabClient.getLogger().info("App connected, game limit A:{}/B:{}", (Object)cfgLimitA, (Object)cfgLimitB);
    }

    @Override
    public void onAppDisconnected() {
        BlankDgLabClient.getLogger().info("App disconnected (may be temporary)");
        this.strengthA = 0;
        this.strengthB = 0;
        this.lastLimitCategoryA = null;
        this.lastLimitCategoryB = null;
        this.setState(ConnectionState.CONNECTING);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                MutableComponent wikiLink = Component.literal((String)"\u00a7e\u00a7nWiki").setStyle(Style.EMPTY.withClickEvent((ClickEvent)new ClickEvent.OpenUrl(URI.create("https://github.com/kskongbai/Blank-DG-LAB/wiki/"))).withHoverEvent((HoverEvent)new HoverEvent.ShowText((Component)Component.literal((String)"\u00a7e\u70b9\u51fb\u6253\u5f00\u5b98\u65b9 Wiki"))));
                mc.player.sendSystemMessage((Component)Component.literal((String)"\u00a7e\u26a0 APP \u6682\u65f6\u65ad\u5f00\u8fde\u63a5\u3002\u53ef\u80fd\u9501\u5c4f\uff0c\u8bf7\u89e3\u9501\u624b\u673a\u3002\u5982\u4e0d\u884c\uff0c\u8bf7\u67e5\u770b ").append((Component)wikiLink));
            }
        });
    }

    @Override
    public void onAppMessage(String message) {
        BlankDgLabClient.getLogger().info("App message: {}", (Object)message);
        if (message.startsWith("strength-")) {
            String data = message.substring(9);
            String[] parts = data.split("\\+");
            if (parts.length >= 4) {
                try {
                    boolean catBChanged;
                    int valA = Integer.parseInt(parts[0]);
                    int valB = Integer.parseInt(parts[1]);
                    int limitA = Integer.parseInt(parts[2]);
                    int limitB = Integer.parseInt(parts[3]);
                    this.appLimitA = limitA;
                    this.appLimitB = limitB;
                    boolean wasAppLimitReceived = this.appLimitReceived;
                    this.appLimitReceived = true;
                    BlankDgLabClient.getLogger().info("App reported: strength A={}, B={}, limit A={}, B={}", new Object[]{valA, valB, limitA, limitB});
                    int cfgLimitA = BlankDgLabConfig.INSTANCE.channelLimitA;
                    int cfgLimitB = BlankDgLabConfig.INSTANCE.channelLimitB;
                    LimitCategory catA = this.categorizeLimitSingle(limitA, cfgLimitA);
                    LimitCategory catB = this.categorizeLimitSingle(limitB, cfgLimitB);
                    boolean catAChanged = this.lastLimitCategoryA == null || catA != this.lastLimitCategoryA;
                    boolean bl = catBChanged = this.lastLimitCategoryB == null || catB != this.lastLimitCategoryB;
                    if (!wasAppLimitReceived || catAChanged || catBChanged) {
                        this.lastLimitCategoryA = catA;
                        this.lastLimitCategoryB = catB;
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> {
                            if (mc.player != null) {
                                if (catA == LimitCategory.ZERO && catB == LimitCategory.ZERO) {
                                    mc.player.sendSystemMessage((Component)Component.literal((String)"\u00a7c\u26a0 APP\u7aefA/B\u901a\u9053\u4e0a\u9650\u5747\u4e3a0\uff0c\u8bbe\u5907\u65e0\u6cd5\u8f93\u51fa\uff01\u8bf7\u5728APP SOCKET\u63a7\u5236\u4e2d\u70b9\u51fb+\u6309\u94ae\u5c06\u4e0a\u9650\u8c03\u81f3\u6e38\u620f\u4e0a\u9650\u4ee5\u4e0a"));
                                } else {
                                    if (catAChanged || !wasAppLimitReceived) {
                                        if (catA == LimitCategory.ZERO) {
                                            mc.player.sendSystemMessage((Component)Component.literal((String)"\u00a7c\u26a0 A\u901a\u9053\u4e0a\u9650\u4e3a0\uff0c\u8bf7\u7ee7\u7eed\u8c03\u6574\uff01"));
                                        } else if (catA == LimitCategory.BELOW) {
                                            mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a7e\u26a0 A\u901a\u9053\u4e0a\u9650:" + limitA + " \u4f4e\u4e8e\u6e38\u620f\u914d\u7f6e(" + cfgLimitA + ")\uff0c\u8bf7\u7ee7\u7eed\u8c03\u6574")));
                                        } else {
                                            mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a7a\u2713 A\u901a\u9053\u4e0a\u9650:" + limitA + " \u5df2\u6ee1\u8db3\u6e38\u620f\u914d\u7f6e\uff01")));
                                        }
                                    }
                                    if (catBChanged || !wasAppLimitReceived) {
                                        if (catB == LimitCategory.ZERO) {
                                            mc.player.sendSystemMessage((Component)Component.literal((String)"\u00a7c\u26a0 B\u901a\u9053\u4e0a\u9650\u4e3a0\uff0c\u8bf7\u7ee7\u7eed\u8c03\u6574\uff01"));
                                        } else if (catB == LimitCategory.BELOW) {
                                            mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a7e\u26a0 B\u901a\u9053\u4e0a\u9650:" + limitB + " \u4f4e\u4e8e\u6e38\u620f\u914d\u7f6e(" + cfgLimitB + ")\uff0c\u8bf7\u7ee7\u7eed\u8c03\u6574")));
                                        } else {
                                            mc.player.sendSystemMessage((Component)Component.literal((String)("\u00a7a\u2713 B\u901a\u9053\u4e0a\u9650:" + limitB + " \u5df2\u6ee1\u8db3\u6e38\u620f\u914d\u7f6e\uff01")));
                                        }
                                    }
                                }
                            }
                        });
                    }
                    this.notifyStrengthChanged();
                }
                catch (NumberFormatException numberFormatException) {}
            } else if (parts.length >= 3) {
                // empty if block
            }
        }
    }

    @Override
    public void onServerStarted() {
    }

    @Override
    public void onServerError(Exception ex) {
        this.setState(ConnectionState.DISCONNECTED);
    }

    @Override
    public void onLogUpdate(String message) {
        this.connectionLog.add(message);
        if (this.connectionLog.size() > 50) {
            this.connectionLog.remove(0);
        }
    }

    private LimitCategory categorizeLimitSingle(int limit, int cfgLimit) {
        if (limit == 0) {
            return LimitCategory.ZERO;
        }
        if (limit < cfgLimit) {
            return LimitCategory.BELOW;
        }
        return LimitCategory.OK;
    }

    static {
        BEAT_WAVEFORM = new String[]{"0a0a0a0a64646464", "0a0a0a0a64646464", "0a0a0a0a64646464", "0a0a0a0a64646464", "0a0a0a0a64646464", "0a0a0a0a64646464", "0a0a0a0a64646464", "0a0a0a0a64646464"};
    }

    private static enum LimitCategory {
        ZERO,
        BELOW,
        OK;

    }
}
