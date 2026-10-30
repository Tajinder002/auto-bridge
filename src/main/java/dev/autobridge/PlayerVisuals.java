package dev.autobridge;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side player visuals. This renders outlines/tracers only; it does not
 * reveal hidden players or change server-side visibility.
 */
public final class PlayerVisuals {
    private PlayerVisuals() {}

    public static void render(WorldRenderContext context) {
        if ((!BridgeConfig.playerEsp && !BridgeConfig.tracers) || context.world() == null
                || context.matrixStack() == null || context.consumers() == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        Vec3d cameraPos = context.camera().getPos();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        var matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Vec3d start = cameraPos.add(context.camera().getHorizontalPlane().multiply(0.15));
        for (PlayerEntity target : context.world().getPlayers()) {
            if (target == client.player || !target.isAlive() || target.isSpectator()) continue;
            Box box = target.getBoundingBox().expand(0.035);
            if (BridgeConfig.playerEsp) {
                VertexRendering.drawBox(matrices, lines, box, 0.20f, 0.85f, 1.0f, 0.92f);
            }
            if (BridgeConfig.tracers) {
                Vec3d end = target.getPos().add(0.0, target.getHeight() * 0.65, 0.0);
                drawLine(lines, matrices.peek().getPositionMatrix(),
                        start, end, 1.0f, 0.48f, 0.92f, 0.92f);
            }
        }
        matrices.pop();
    }

    private static void drawLine(VertexConsumer vertices,
                                 org.joml.Matrix4f matrix,
                                 Vec3d start, Vec3d end,
                                 float red, float green, float blue, float alpha) {
        Vec3d delta = end.subtract(start).normalize();
        vertices.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                .color(red, green, blue, alpha)
                .normal((float) delta.x, (float) delta.y, (float) delta.z);
        vertices.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                .color(red, green, blue, alpha)
                .normal((float) delta.x, (float) delta.y, (float) delta.z);
    }
}
