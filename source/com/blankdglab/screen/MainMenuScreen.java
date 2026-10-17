/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 */
package com.blankdglab.screen;

import com.blankdglab.compat.CompatHelper;
import com.blankdglab.config.BlankDgLabConfig;
import com.blankdglab.device.ConnectionState;
import com.blankdglab.device.DeviceManager;
import com.blankdglab.screen.HudEditScreen;
import com.blankdglab.screen.LinkConfigScreen;
import com.blankdglab.screen.QrCodeScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MainMenuScreen
extends Screen {
    private final Screen parent;

    public MainMenuScreen(Screen parent) {
        super((Component)Component.literal((String)"Blank DG-LAB"));
        this.parent = parent;
    }

    protected void init() {
        boolean connected;
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int btnW = 150;
        int btnH = 20;
        int gap = 8;
        int vGap = 6;
        int btnStartY = centerY + 18;
        DeviceManager device = DeviceManager.getInstance();
        boolean bl = connected = device.getState() == ConnectionState.CONNECTED;
        if (connected) {
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u65ad\u5f00\u8fde\u63a5"), btn -> {
                device.disconnect();
                CompatHelper.setScreen(this.minecraft, new MainMenuScreen(this.parent));
            }).bounds(centerX - btnW - gap / 2, btnStartY, btnW, btnH).build());
        } else {
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u626b\u7801\u8fde\u63a5"), btn -> CompatHelper.setScreen(this.minecraft, new QrCodeScreen(this))).bounds(centerX - btnW - gap / 2, btnStartY, btnW, btnH).build());
        }
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u6a21\u7ec4\u914d\u7f6e"), btn -> CompatHelper.setScreen(this.minecraft, BlankDgLabConfig.createConfigScreen(this))).bounds(centerX + gap / 2, btnStartY, btnW, btnH).build());
        int row2Y = btnStartY + btnH + vGap;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"HUD\u4f4d\u7f6e\u7f16\u8f91"), btn -> CompatHelper.setScreen(this.minecraft, new HudEditScreen(this))).bounds(centerX - btnW - gap / 2, row2Y, btnW, btnH).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u8054\u673a\u9762\u677f"), btn -> CompatHelper.setScreen(this.minecraft, new LinkConfigScreen(this))).bounds(centerX + gap / 2, row2Y, btnW, btnH).build());
        int row3Y = row2Y + btnH + vGap;
        this.addRenderableWidget(Button.builder(Component.literal("全局电击强度"), btn -> CompatHelper.setScreen(this.minecraft, new GlobalStrengthScreen(this))).bounds(centerX - btnW - gap / 2, row3Y, btnW, btnH).build());
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u8fd4\u56de\u6e38\u620f"), btn -> this.onClose()).bounds(centerX + gap / 2, row3Y, btnW, btnH).build());
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int statusColor;
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        g.centeredText(this.font, "\u00a7f\u00a7lBlank DG-LAB", centerX, centerY - 50, -1);
        g.fill(centerX - 60, centerY - 38, centerX + 60, centerY - 37, -29696);
        g.centeredText(this.font, "\u00a77\u90ca\u72fc\u7535\u51fb\u8054\u52a8\u6a21\u7ec4", centerX, centerY - 34, -5592406);
        int panelW = 200;
        int panelH = 30;
        int panelX = centerX - panelW / 2;
        int panelY = centerY - 20;
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x60000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, -12566464);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, -12566464);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, -12566464);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, -12566464);
        DeviceManager device = DeviceManager.getInstance();
        ConnectionState state = device.getState();
        int contentX = panelX + 10;
        int contentY = panelY + 11;
        String statusText = switch (state) {
            case ConnectionState.CONNECTED -> {
                statusColor = -16724992;
                yield "\u5df2\u8fde\u63a5";
            }
            case ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> {
                statusColor = -3355648;
                yield "\u7b49\u5f85\u8fde\u63a5...";
            }
            default -> {
                statusColor = -10066330;
                yield "\u672a\u8fde\u63a5";
            }
        };
        g.fill(contentX, contentY, contentX + 8, contentY + 8, statusColor);
        int textX = contentX + 14;
        g.text(this.font, statusText, textX, contentY, -1, false);
        textX += this.font.width(statusText) + 8;
        if (state == ConnectionState.CONNECTED) {
            int sA = device.getStrengthA();
            int sB = device.getStrengthB();
            int limA = device.getEffectiveLimitA();
            int limB = device.getEffectiveLimitB();
            g.text(this.font, "A:" + sA + "/" + limA, textX, contentY, -1007552, false);
            g.text(this.font, "B:" + sB + "/" + limB, textX += this.font.width("A:" + sA + "/" + limA) + 6, contentY, -12529456, false);
        }
    }

    public boolean shouldCloseOnEsc() {
        return true;
    }

    public void onClose() {
        CompatHelper.setScreen(this.minecraft, this.parent);
    }
}
