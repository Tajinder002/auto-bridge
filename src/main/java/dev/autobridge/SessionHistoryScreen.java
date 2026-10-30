package dev.autobridge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class SessionHistoryScreen extends Screen {
    public SessionHistoryScreen() {
        super(Text.literal("Auto-Bridge Session History"));
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(this.width / 2 - 50, this.height - 32, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101018);
        context.drawCenteredTextWithShadow(textRenderer, "SESSION HISTORY (LAST 20 SESSIONS)",
                width / 2, 18, 0xFF70D7FF);
        List<SessionHistory.Entry> entries = SessionHistory.getEntries();
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No completed sessions with bridge activity yet.",
                    width / 2, height / 2 - 4, 0xFFFFFFFF);
        } else {
            int y = 42;
            int shown = Math.min(entries.size(), Math.max(1, (height - 90) / 35));
            for (int i = 0; i < shown; i++) {
                SessionHistory.Entry entry = entries.get(i);
                String summary = entry.getDate() + "  |  " + formatDuration(entry.getDurationSeconds())
                        + "  |  Success " + entry.getSuccessPercent() + "%";
                String details = "Placed " + entry.getAccepted() + "/" + entry.getAttempts()
                        + "  •  Rejected " + entry.getRejected() + "  •  Edges " + entry.getEdgeWarnings();
                context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, y, 0xFFFFFFFF);
                context.drawCenteredTextWithShadow(textRenderer, details, width / 2, y + 12, 0xFFB8B8D0);
                y += 35;
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private static String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
