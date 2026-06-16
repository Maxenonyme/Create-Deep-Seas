package com.maxenonyme.createsubmarine.submarine.sonar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class SonarButton extends AbstractWidget {

    public enum Arrow { UP, DOWN, LEFT, RIGHT }

    private final Arrow direction;
    private final Runnable onClick;

    public SonarButton(Arrow direction, int x, int y, int w, int h, Runnable onClick) {
        super(x, y, w, h, Component.empty());
        this.direction = direction;
        this.onClick = onClick;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int bg = this.isHovered() ? 0xFF005500 : 0xFF002200;
        int fg = this.isHovered() ? 0xFF44FF44 : 0xFF00AA00;
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);

        int cx = this.getX() + this.width / 2;
        int cy = this.getY() + this.height / 2;
        int s = 4;
        int hw = 2;

        switch (this.direction) {
            case UP -> {
                for (int i = 0; i < s; i++) {
                    int half = i * hw / 2;
                    graphics.fill(cx - half, cy - s + i, cx + half + 1, cy - s + i + 1, fg);
                }
            }
            case DOWN -> {
                for (int i = 0; i < s; i++) {
                    int half = (s - 1 - i) * hw / 2;
                    graphics.fill(cx - half, cy + s - i - 1, cx + half + 1, cy + s - i, fg);
                }
            }
            case LEFT -> {
                for (int i = 0; i < s; i++) {
                    int half = i * hw / 2;
                    graphics.fill(cx - s + i, cy - half, cx - s + i + 1, cy + half + 1, fg);
                }
            }
            case RIGHT -> {
                for (int i = 0; i < s; i++) {
                    int half = (s - 1 - i) * hw / 2;
                    graphics.fill(cx + s - i - 1, cy - half, cx + s - i, cy + half + 1, fg);
                }
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
