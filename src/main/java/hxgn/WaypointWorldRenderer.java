package hxgn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.systems.RenderSystem;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.render.EventRender2D;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.core.event.listener.EventListener;
import org.rusherhack.core.event.subscribe.Subscribe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public class WaypointWorldRenderer implements EventListener {

    private final WaypointETAModule module;

    // Reflection — set once in constructor
    private Method getCurrentSession, getWaypointsManager, getCurrentWorld,
            getCurrentSet, getList, isTemporary, getX, getY, getZ, getNameSafe;
    private boolean reflectionReady = false;
    private int consecutiveErrors = 0;
    private static final int MAX_ERRORS = 3;

    // Speed — circular buffer, O(1) update per frame; size driven by SpeedSamples setting
    private static final int MAX_SPEED_SAMPLES = 100;
    private final double[] speedBuf = new double[MAX_SPEED_SAMPLES];
    private int speedBufSize = 20;
    private int speedIdx = 0;
    private double speedSum = 0.0;

    // Cached waypoint world position — updated every REFRESH_INTERVAL frames via reflection
    private static final int REFRESH_INTERVAL = 10;
    private int frameTick = 0;
    private boolean hasStoredWaypoint = false;
    private double storedWpX, storedWpY, storedWpZ;
    private String storedWpName = "";

    // Projected screen position — recomputed every frame from cached world pos
    private boolean hasTarget = false;
    private double scrX, scrY, pitchScrY;
    private String etaText = "";
    private boolean etaUnknown = false;
    private int cachedTextW, cachedTextH;
    private final StringBuilder labelBuilder = new StringBuilder();

    // Cached derived values — updated via onChange, not per-frame
    private double dotThreshold;
    private double cachedOffsetX, cachedOffsetY;
    private double cachedMinSpeed;
    private int cachedBgColor;
    private int cachedMaxDistance;

    public WaypointWorldRenderer(WaypointETAModule module) {
        this.module = module;

        this.dotThreshold     = 1.0 - module.focusAngle.getValue() / 1000.0;
        this.cachedMinSpeed   = module.minSpeed.getValue();
        this.cachedBgColor    = bgColorFromOpacity(module.bgOpacity.getValue());
        this.speedBufSize     = module.speedSamples.getValue();
        this.cachedMaxDistance = module.maxDistance.getValue() * (module.maxDistanceKm.getValue() ? 1000 : 1);

        module.focusAngle.onChange(() -> this.dotThreshold = 1.0 - module.focusAngle.getValue() / 1000.0);
        module.offsetX.onChange(() -> this.cachedOffsetX = curveOffset(module.offsetX.getValue()));
        module.offsetY.onChange(() -> this.cachedOffsetY = curveOffset(module.offsetY.getValue()));
        module.minSpeed.onChange(() -> this.cachedMinSpeed = module.minSpeed.getValue());
        module.bgOpacity.onChange(() -> this.cachedBgColor = bgColorFromOpacity(module.bgOpacity.getValue()));
        module.maxDistance.onChange(() -> this.cachedMaxDistance = module.maxDistance.getValue() * (module.maxDistanceKm.getValue() ? 1000 : 1));
        module.maxDistanceKm.onChange(() -> this.cachedMaxDistance = module.maxDistance.getValue() * (module.maxDistanceKm.getValue() ? 1000 : 1));
        module.speedSamples.onChange(() -> {
            this.speedBufSize = module.speedSamples.getValue();
            Arrays.fill(this.speedBuf, 0.0);
            this.speedIdx = 0;
            this.speedSum = 0.0;
        });

        try {
            Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession");
            Class<?> managerClass = Class.forName("xaero.common.minimap.waypoints.WaypointsManager");
            Class<?> worldClass   = Class.forName("xaero.common.minimap.waypoints.WaypointWorld");
            Class<?> setClass     = Class.forName("xaero.common.minimap.waypoints.WaypointSet");
            Class<?> wpClass      = Class.forName("xaero.common.minimap.waypoints.Waypoint");

            getCurrentSession   = sessionClass.getDeclaredMethod("getCurrentSession");
            getWaypointsManager = sessionClass.getDeclaredMethod("getWaypointsManager");
            getCurrentWorld     = managerClass.getDeclaredMethod("getCurrentWorld");
            getCurrentSet       = worldClass.getDeclaredMethod("getCurrentSet");
            getList             = setClass.getDeclaredMethod("getList");
            isTemporary         = wpClass.getDeclaredMethod("isTemporary");
            getX                = wpClass.getDeclaredMethod("getX");
            getY                = wpClass.getDeclaredMethod("getY");
            getZ                = wpClass.getDeclaredMethod("getZ");
            getNameSafe         = wpClass.getDeclaredMethod("getNameSafe", String.class);

            reflectionReady = true;
        } catch (Exception e) {
            System.err.println("[WWR] Reflection failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isListening() { return reflectionReady; }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (!module.isToggled()) { hasTarget = false; hasStoredWaypoint = false; return; }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) { hasTarget = false; hasStoredWaypoint = false; return; }

        // Speed update — O(1), every frame
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        double sample = Math.sqrt(dx * dx + dz * dz) * 20.0;
        int slot = speedIdx % speedBufSize;
        speedSum += sample - speedBuf[slot];
        speedBuf[slot] = sample;
        speedIdx = (speedIdx + 1) % speedBufSize;
        double avgSpeed = speedSum / speedBufSize;

        // Phase 1 — reflection (every REFRESH_INTERVAL frames): find most looked-at temporary waypoint
        if (++frameTick >= REFRESH_INTERVAL) {
            frameTick = 0;
            hasStoredWaypoint = false;
            storedWpName = "";
            try {
                Object session = getCurrentSession.invoke(null);
                if (session == null) return; // Xaero session not active yet
                Object manager = getWaypointsManager.invoke(session);
                if (manager == null) return;
                Object world = getCurrentWorld.invoke(manager);
                if (world == null) return;
                Object set = getCurrentSet.invoke(world);
                if (set == null) return;
                ArrayList<?> list = (ArrayList<?>) getList.invoke(set);
                if (list == null) return;

                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                double lookX = look.x, lookZ = look.z;
                double lookXZLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
                if (lookXZLen > 0.001) {
                    double lookXNorm = lookX / lookXZLen, lookZNorm = lookZ / lookXZLen;
                    double bestDot = -1.0;
                    for (Object wp : list) {
                        if (!module.allWaypoints.getValue() && !(boolean) isTemporary.invoke(wp)) continue;
                        double wpX = (int) getX.invoke(wp) + 0.5;
                        double wpZ = (int) getZ.invoke(wp) + 0.5;
                        double relX = wpX - eye.x, relZ = wpZ - eye.z;
                        double dist = Math.sqrt(relX * relX + relZ * relZ);
                        if (dist < 1.0) continue;
                        double dot = (relX * lookXNorm + relZ * lookZNorm) / dist;
                        if (dot > bestDot) {
                            bestDot = dot;
                            storedWpX = wpX;
                            storedWpY = (int) getY.invoke(wp) + 0.5;
                            storedWpZ = wpZ;
                            storedWpName = (String) getNameSafe.invoke(wp, "");
                            hasStoredWaypoint = true;
                        }
                    }
                }
                consecutiveErrors = 0; // successful refresh
            } catch (Exception e) {
                hasStoredWaypoint = false;
                if (++consecutiveErrors >= MAX_ERRORS) {
                    reflectionReady = false;
                    System.err.println("[WaypointETA] Disabling — dependency error (" + e + "). Is Xaero's Minimap loaded?");
                }
            }
        }

        // Phase 2 — projection (every frame): project stored waypoint to screen
        hasTarget = false;
        if (!hasStoredWaypoint) return;

        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double lookX = look.x, lookY = look.y, lookZ = look.z;

        double lookXZLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookXZLen < 0.001) return;
        double lookXNorm = lookX / lookXZLen, lookZNorm = lookZ / lookXZLen;

        double relX = storedWpX - eye.x;
        double relY = storedWpY - eye.y;
        double relZ = storedWpZ - eye.z;
        double distXZ = Math.sqrt(relX * relX + relZ * relZ);
        if (distXZ < 1.0) return;
        if (cachedMaxDistance > 0 && distXZ > cachedMaxDistance) return;

        // Visibility: horizontal cone check — threshold driven by AppearStrictness setting
        double horizontalDot = (relX * lookXNorm + relZ * lookZNorm) / distXZ;
        if (horizontalDot < dotThreshold) return;

        // Full 3D dot with look direction — used as the perspective divisor
        double forwardDot = relX * lookX + relY * lookY + relZ * lookZ;
        if (forwardDot <= 0.5) return;

        var window  = Minecraft.getInstance().getWindow();
        double guiW = window.getGuiScaledWidth();
        double guiH = window.getGuiScaledHeight();
        double tanHalfFovV = 1.0 / RenderSystem.getProjectionMatrix().m11();
        double aspect = guiW / guiH;

        // Horizontal: right vector has no Y component (camera rolls don't apply)
        double rightX = -lookZNorm;
        double rightDot = relX * rightX + relZ * lookXNorm;
        double ndcX = rightDot / forwardDot / (tanHalfFovV * aspect);
        if (Math.abs(ndcX) > 1.05) return;

        // Vertical: up = right × look = (-lookXNorm*lookY, lookXZLen, -lookZNorm*lookY)
        double upDot = -lookXNorm * lookY * relX + lookXZLen * relY - lookZNorm * lookY * relZ;
        double ndcY = -upDot / forwardDot / tanHalfFovV;

        scrX = (ndcX + 1.0) / 2.0 * guiW;
        scrY = (ndcY + 1.0) / 2.0 * guiH;

        // Pitch-only Y — for Relative mode: follows camera pitch but ignores waypoint world Y
        double tanHalfFov = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2.0);
        double pitchNdcY = lookY / lookXZLen / tanHalfFov;
        pitchScrY = (pitchNdcY + 1.0) / 2.0 * guiH;

        // Build label text
        String eta = buildEta(distXZ, avgSpeed, cachedMinSpeed);
        etaUnknown = eta.equals("ETA: ?");
        labelBuilder.setLength(0);
        StringBuilder sb = labelBuilder;
        if (module.showName.getValue() && !storedWpName.isEmpty()) sb.append(storedWpName).append(" | ");
        if (module.showDistance.getValue()) {
            if (module.distanceKm.getValue() && distXZ >= 1999) {
                sb.append(String.format("%.2fkm", distXZ / 1000.0)).append(" | ");
            } else {
                sb.append((int) distXZ).append("m | ");
            }
        }
        sb.append(eta);
        etaText = sb.toString();
        if (module.customFont.getValue()) {
            var rFont = RusherHackAPI.getRenderer2D().getFontRenderer();
            cachedTextW = (int) rFont.getStringWidth(etaText);
            cachedTextH = (int) rFont.getFontHeight();
        } else {
            var mcFont = Minecraft.getInstance().font;
            cachedTextW = mcFont.width(etaText);
            cachedTextH = mcFont.lineHeight;
        }
        hasTarget = true;
    }

    @Subscribe
    private void onRender2D(EventRender2D event) {
        if (!module.isToggled() || !hasTarget) return;
        if (etaUnknown && !module.showWhenUnknown.getValue()) return;
        try {
            var window = Minecraft.getInstance().getWindow();
            double guiW = window.getGuiScaledWidth();
            double guiH = window.getGuiScaledHeight();

            // Determine base draw position
            double drawX = scrX;
            double drawY = scrY;
            if (module.labelOffset.getValue()) {
                if (module.offsetFixed.getValue()) {
                    drawX = guiW * 0.5;
                    drawY = module.offsetRelative.getValue() ? pitchScrY : guiH * 0.5;
                }
                drawX += cachedOffsetX;
                drawY += cachedOffsetY;
            }

            boolean useCustomFont = module.customFont.getValue();
            int textW = cachedTextW, textH = cachedTextH;
            int textColor = module.textColor.getValue().getRGB();

            int textX = (int)(drawX - textW * 0.5);
            int textY = (int)(drawY + textH + 10.0);
            int pad = 2;

            event.getGraphics().fill(
                textX - pad, textY - pad,
                textX + textW + pad, textY + textH + pad,
                cachedBgColor
            );
            event.getGraphics().flush();

            boolean shadow = module.textShadow.getValue();
            if (useCustomFont) {
                var rFont = RusherHackAPI.getRenderer2D().getFontRenderer();
                boolean prevShadow = rFont.getDefaultShadowState();
                rFont.setDefaultShadowState(false);
                var stack = event.getMatrixStack();
                stack.pushPose();
                stack.translate(textX, textY, 0);
                rFont.begin(stack);
                if (shadow) rFont.drawString(etaText, 0.3, 0.3, 0x000000FF);
                rFont.drawString(etaText, 0, 0, textColor, false);
                rFont.end();
                rFont.setDefaultShadowState(prevShadow);
                stack.popPose();
            } else {
                event.getGraphics().drawString(Minecraft.getInstance().font, etaText, textX, textY, textColor, shadow);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String buildEta(double dist, double speed, double minSpeed) {
        if (speed < minSpeed) return "ETA: ?";
        int totalSecs = (int)(dist / speed);
        if (totalSecs < 60) return "ETA: " + totalSecs + "s";
        if (totalSecs < 7200) {
            int minutes = totalSecs / 60, seconds = totalSecs % 60;
            return "ETA: " + minutes + "m " + seconds + "s";
        }
        if (totalSecs < 86400) {
            int hours = totalSecs / 3600, remainderSecs = totalSecs % 3600;
            int minutes = remainderSecs / 60, seconds = remainderSecs % 60;
            return "ETA: " + hours + "h " + minutes + "m " + seconds + "s";
        }
        int days = totalSecs / 86400, remainderSecs = totalSecs % 86400;
        int hours = remainderSecs / 3600, minutes = (remainderSecs % 3600) / 60;
        return "ETA: " + days + "d " + hours + "h " + minutes + "m";
    }

    private static int bgColorFromOpacity(int opacity) {
        return (Math.round(opacity / 100.0f * 255) << 24);
    }

    /** Quadratic curve so the first half of the slider is far less sensitive than the second. */
    private static double curveOffset(int v) {
        double normalized = v / 70.0; // -1.0 to 1.0
        return Math.signum(normalized) * normalized * normalized * 400.0;
    }
}
