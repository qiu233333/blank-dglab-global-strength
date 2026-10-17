package com.blankdglab.screen;

import com.blankdglab.compat.CompatHelper;
import com.blankdglab.config.GlobalStrengthSettings;
import com.blankdglab.device.DeviceManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GlobalStrengthScreen extends Screen {
    private final Screen parent;
    private EditBox multiplierA;
    private EditBox multiplierB;
    private String error = "";

    public GlobalStrengthScreen(Screen parent) {
        super(Component.literal("全局电击强度"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.multiplierA = new EditBox(this.font, centerX - 80, centerY - 28, 160, 20, Component.literal("A 通道倍率"));
        this.multiplierA.setMaxLength(8);
        this.multiplierA.setValue(Double.toString(GlobalStrengthSettings.multiplierA()));
        this.addRenderableWidget(this.multiplierA);

        this.multiplierB = new EditBox(this.font, centerX - 80, centerY + 14, 160, 20, Component.literal("B 通道倍率"));
        this.multiplierB.setMaxLength(8);
        this.multiplierB.setValue(Double.toString(GlobalStrengthSettings.multiplierB()));
        this.addRenderableWidget(this.multiplierB);

        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> saveValues())
                .bounds(centerX - 80, centerY + 48, 75, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> onClose())
                .bounds(centerX + 5, centerY + 48, 75, 20).build());
    }

    private void saveValues() {
        try {
            double a = Double.parseDouble(this.multiplierA.getValue().trim());
            double b = Double.parseDouble(this.multiplierB.getValue().trim());
            if (!Double.isFinite(a) || !Double.isFinite(b) || a < 0.0 || a > 1.0 || b < 0.0 || b > 1.0) {
                this.error = "请输入 0.00～1.00 之间的数值";
                return;
            }
            GlobalStrengthSettings.save(a, b);
            DeviceManager.getInstance().resetStrength();
            onClose();
        } catch (NumberFormatException ex) {
            this.error = "请输入有效的小数，例如 0.10";
        } catch (IOException ex) {
            this.error = "保存失败：" + ex.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        graphics.centeredText(this.font, "§f§l全局电击强度", centerX, centerY - 73, -1);
        graphics.centeredText(this.font, "输出 = 原强度 × 通道倍率，再受原有上限限制", centerX, centerY - 56, -5592406);
        graphics.text(this.font, "A 通道倍率（0 关闭，1 原强度）", centerX - 80, centerY - 41, -1, false);
        graphics.text(this.font, "B 通道倍率（0 关闭，1 原强度）", centerX - 80, centerY + 1, -1, false);
        if (!this.error.isEmpty()) {
            graphics.centeredText(this.font, this.error, centerX, centerY + 76, -39322);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        CompatHelper.setScreen(this.minecraft, this.parent);
    }
}
