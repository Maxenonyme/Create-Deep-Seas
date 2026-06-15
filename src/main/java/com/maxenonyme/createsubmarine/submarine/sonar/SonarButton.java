package com.maxenonyme.createsubmarine.submarine.sonar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SonarButton extends AbstractWidget {

    private ResourceLocation texture;
    private final Runnable onClick;

    public SonarButton(ResourceLocation texture, int x, int y, int w, int h, Runnable onClick) {
        super(x, y, w, h, Component.empty());
        this.texture = texture;
        this.onClick = onClick;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int color = this.isHovered() ? 0xFF00FF00 : 0xFF004400;
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);

        if (this.isHovered()) {
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0xFF006600);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
