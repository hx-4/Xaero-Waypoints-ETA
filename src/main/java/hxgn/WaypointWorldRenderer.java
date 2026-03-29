package hxgn;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.core.utils.ColorUtils;
import org.rusherhack.client.api.events.render.EventRender2D;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.core.event.listener.EventListener;
import org.rusherhack.core.event.subscribe.Subscribe;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class WaypointWorldRenderer implements EventListener {

    private static final int MAX_ERRORS = 3;
    private static final int REFRESH_INTERVAL = 10;

    private final WaypointETAModule module;
    private final StringBuilder labelBuilder = new StringBuilder();

    private Method getCurrentSession, getWaypointsManager, getAutoWorld,
            getCurrentSet, getList, isTemporary, isDisabled, isYIncluded, getX, getY, getZ, getNameSafe;
    private boolean reflectionReady = false;
    private int consecutiveErrors = 0;

    private double[] speedBuf;
    private int speedBufSize;
    private int speedIdx = 0;
    private double speedSum = 0.0;

    private double[] etaBuf;
    private int etaBufSize;
    private int etaIdx = 0;
    private double etaSum = 0.0;

    private int frameTick = 0;
    private boolean hasStoredWaypoint = false;
    private double storedWpX, storedWpY, storedWpZ;
    private String storedWpName = "";

    private boolean hasTarget = false;
    private double scrX, scrY;
    private double fadeAlpha = 1.0;
    private String etaText = "";
    private boolean etaUnknown = false;
    private int cachedTextW, cachedTextH;

    private double dotThreshold;
    private double cachedOffsetX, cachedOffsetY;
    private double cachedMinSpeed;
    private int cachedBgColor;
    private int cachedMaxDistance;
    private String cachedUnknownText = "?";

    public WaypointWorldRenderer(WaypointETAModule module) {
        this.module = module;

        this.dotThreshold      = 1.0 - module.focusAngle.getValue() / 1000.0;
        this.cachedMinSpeed    = module.minSpeed.getValue();
        this.cachedBgColor     = bgColorFromOpacity(module.bgOpacity.getValue());
        this.speedBufSize      = module.speedSamples.getValue();
        this.speedBuf          = new double[this.speedBufSize];
        this.etaBufSize        = Math.max(1, module.averageEstimate.getValue());
        this.etaBuf            = new double[this.etaBufSize];
        this.cachedMaxDistance = module.maxDistance.getValue() * (module.maxDistanceKm.getValue() ? 1000 : 1);
        this.cachedUnknownText = sanitizeUnknownText(module.unknownText.getValue());

        module.focusAngle.onChange(() -> this.dotThreshold = 1.0 - module.focusAngle.getValue() / 1000.0);
        module.unknownText.onChange(() -> this.cachedUnknownText = sanitizeUnknownText(module.unknownText.getValue()));
        module.offsetX.onChange(() -> this.cachedOffsetX = curveOffset(module.offsetX.getValue()));
        module.offsetY.onChange(() -> this.cachedOffsetY = curveOffset(module.offsetY.getValue()));
        module.minSpeed.onChange(() -> this.cachedMinSpeed = module.minSpeed.getValue());
        module.bgOpacity.onChange(() -> this.cachedBgColor = bgColorFromOpacity(module.bgOpacity.getValue()));
        module.maxDistance.onChange(() -> this.cachedMaxDistance = module.maxDistance.getValue() * (module.maxDistanceKm.getValue() ? 1000 : 1));
        module.maxDistanceKm.onChange(() -> this.cachedMaxDistance = module.maxDistance.getValue() * (module.maxDistanceKm.getValue() ? 1000 : 1));
        module.speedSamples.onChange(() -> {
            this.speedBufSize = module.speedSamples.getValue();
            this.speedBuf     = new double[this.speedBufSize];
            this.speedIdx     = 0;
            this.speedSum     = 0.0;
        });
        module.averageEstimate.onChange(() -> {
            this.etaBufSize = Math.max(1, module.averageEstimate.getValue());
            this.etaBuf     = new double[this.etaBufSize];
            this.etaIdx     = 0;
            this.etaSum     = 0.0;
        });

        try {
            Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession");
            Class<?> managerClass = Class.forName("xaero.common.minimap.waypoints.WaypointsManager");
            Class<?> worldClass   = Class.forName("xaero.common.minimap.waypoints.WaypointWorld");
            Class<?> setClass     = Class.forName("xaero.common.minimap.waypoints.WaypointSet");
            Class<?> wpClass      = Class.forName("xaero.common.minimap.waypoints.Waypoint");

            getCurrentSession   = sessionClass.getDeclaredMethod("getCurrentSession");
            getWaypointsManager = sessionClass.getDeclaredMethod("getWaypointsManager");
            getAutoWorld        = managerClass.getDeclaredMethod("getAutoWorld");
            getCurrentSet       = worldClass.getDeclaredMethod("getCurrentSet");
            getList             = setClass.getDeclaredMethod("getList");
            isTemporary         = wpClass.getDeclaredMethod("isTemporary");
            isDisabled          = wpClass.getDeclaredMethod("isDisabled");
            isYIncluded         = wpClass.getDeclaredMethod("isYIncluded");
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

    private boolean isNetherHighwayActive(LocalPlayer player) {
        if (!module.elytHwy.getValue()) return false;
        var level = Minecraft.getInstance().level;
        return level != null
                && level.dimension() == Level.NETHER
                && player.getY() >= 115.0 && player.getY() <= 125.0
                && player.isFallFlying();
    }

    private double sampleSpeed(LocalPlayer player) {
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        double sample = Math.sqrt(dx * dx + dz * dz) * 20.0;
        speedSum += sample - speedBuf[speedIdx];
        speedBuf[speedIdx] = sample;
        speedIdx = (speedIdx + 1) % speedBufSize;
        return speedSum / speedBufSize;
    }

    private void updateFade(double dist3D) {
        int fd = module.fadeDistance.getValue();
        if (fd > 0 && dist3D < fd) {
            double t = dist3D / fd;
            fadeAlpha = t * t;
        } else {
            fadeAlpha = 1.0;
        }
    }

    // Picks the waypoint closest to the look direction using horizontal dot as a cone gate,
    // then combined horizontal + vertical angular deviation to rank. atan2 for vertical
    // keeps the comparison distance-independent.
    private void refreshWaypoint(net.minecraft.client.Camera camera) throws Exception {
        Object session = getCurrentSession.invoke(null);
        if (session == null) { hasStoredWaypoint = false; return; }
        Object manager = getWaypointsManager.invoke(session);
        if (manager == null) { hasStoredWaypoint = false; return; }
        Object world = getAutoWorld.invoke(manager);
        if (world == null) { hasStoredWaypoint = false; return; }
        Object set = getCurrentSet.invoke(world);
        if (set == null) { hasStoredWaypoint = false; return; }
        ArrayList<?> list = (ArrayList<?>) getList.invoke(set);
        if (list == null) { hasStoredWaypoint = false; return; }

        Vec3 eye = camera.getPosition();
        var lookVec = camera.getLookVector();
        double lookX = lookVec.x(), lookZ = lookVec.z();
        double lookXZLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookXZLen <= 0.001) return;

        double lookXNorm = lookX / lookXZLen, lookZNorm = lookZ / lookXZLen;
        double pitchRad = Math.toRadians(camera.getXRot());
        double prevX = storedWpX, prevZ = storedWpZ;
        double bestAngDist2 = Double.MAX_VALUE;
        boolean found = false;

        for (Object wp : list) {
            if ((boolean) isDisabled.invoke(wp)) continue;
            if (module.onlyTemporary.getValue() && !(boolean) isTemporary.invoke(wp)) continue;
            double wpX = (int) getX.invoke(wp) + 0.5;
            double wpZ = (int) getZ.invoke(wp) + 0.5;
            double relX = wpX - eye.x, relZ = wpZ - eye.z;
            double distXZ = Math.sqrt(relX * relX + relZ * relZ);
            if (distXZ < 0.5) continue;

            double hDot = (relX * lookXNorm + relZ * lookZNorm) / distXZ;
            if (hDot < dotThreshold) continue;

            double hAngle = Math.acos(Math.min(1.0, hDot));
            double wpY = (boolean) isYIncluded.invoke(wp) ? (int) getY.invoke(wp) + 0.5 : eye.y;
            double vAngle = Math.atan2(wpY - eye.y, distXZ) + pitchRad;
            double angDist2 = hAngle * hAngle + vAngle * vAngle;

            if (angDist2 >= bestAngDist2) continue;
            bestAngDist2 = angDist2;
            found = true;
            storedWpX = wpX;
            storedWpY = wpY;
            storedWpZ = wpZ;
            storedWpName = (String) getNameSafe.invoke(wp, "");
        }

        if (!found) return; // keep previous to avoid flicker on close approach

        if (storedWpX != prevX || storedWpZ != prevZ) {
            int fd = module.fadeDistance.getValue();
            double newDistXZ = Math.sqrt(
                    (storedWpX - eye.x) * (storedWpX - eye.x) +
                            (storedWpZ - eye.z) * (storedWpZ - eye.z));
            if (fd == 0 || newDistXZ >= fd) fadeAlpha = 1.0;
        }
        hasStoredWaypoint = true;
    }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (!module.isToggled()) { hasTarget = false; hasStoredWaypoint = false; return; }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) { hasTarget = false; hasStoredWaypoint = false; return; }
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        boolean useFixedSpeed = module.setSpeed.getValue();
        boolean elytHwyActive = !useFixedSpeed && isNetherHighwayActive(player);
        boolean fixedMode     = useFixedSpeed || elytHwyActive;
        double avgSpeed       = fixedMode ? 0.0 : sampleSpeed(player);

        if (++frameTick >= REFRESH_INTERVAL) {
            frameTick = 0;
            try {
                refreshWaypoint(camera);
                consecutiveErrors = 0;
            } catch (Exception e) {
                hasStoredWaypoint = false;
                if (++consecutiveErrors >= MAX_ERRORS) {
                    reflectionReady = false;
                    System.err.println("[WaypointETA] Disabling, is Xaero's Minimap loaded? (" + e + ")");
                }
            }
        }

        hasTarget = false;
        if (!hasStoredWaypoint) return;

        Vec3 eye = camera.getPosition();
        var lookVec = camera.getLookVector();
        double lookX = lookVec.x(), lookZ = lookVec.z();
        double lookXZLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookXZLen < 0.001) return;
        double lookXNorm = lookX / lookXZLen, lookZNorm = lookZ / lookXZLen;

        double relX = storedWpX - eye.x;
        double relY = storedWpY - eye.y;
        double relZ = storedWpZ - eye.z;
        double distXZ = Math.sqrt(relX * relX + relZ * relZ);
        double dist3D = Math.sqrt(relX * relX + relY * relY + relZ * relZ);

        updateFade(dist3D); // must run before early returns so fadeAlpha is always current

        if (distXZ < 1.0) return;
        if (cachedMaxDistance > 0 && dist3D > cachedMaxDistance) return;

        // small hysteresis prevents flicker at the cone boundary
        double horizontalDot = (relX * lookXNorm + relZ * lookZNorm) / distXZ;
        if (horizontalDot < dotThreshold - 0.01) return;

        var window = Minecraft.getInstance().getWindow();
        double guiH = window.getGuiScaledHeight();

        Vec2 projected = null;
        var renderer = event.getRenderer();
        boolean wasBuilding = renderer.isBuilding();
        if (!wasBuilding) renderer.begin();
        try { projected = renderer.projectToScreen(new Vec3(storedWpX, storedWpY, storedWpZ)); }
        catch (Exception ignored) {}
        if (!wasBuilding) renderer.end();

        if (projected != null) {
            scrX = projected.x;
            scrY = Math.max(4.0, Math.min(guiH - 4.0, projected.y));
        } else {
            // fallback for extreme pitch or behind-camera
            double guiW = window.getGuiScaledWidth();
            double tanHalfFovV = 1.0 / RenderSystem.getProjectionMatrix().m11();
            double aspect = guiW / guiH;
            double lookY = lookVec.y();
            double forwardDot = relX * lookX + relY * lookY + relZ * lookZ;
            if (forwardDot <= 0.5) return;
            double rightDot = relX * -lookZNorm + relZ * lookXNorm;
            double ndcX = rightDot / forwardDot / (tanHalfFovV * aspect);
            if (Math.abs(ndcX) > 1.05) return;
            double upDot = -lookXNorm * lookY * relX + lookXZLen * relY - lookZNorm * lookY * relZ;
            double ndcY = -upDot / forwardDot / tanHalfFovV;
            scrX = (ndcX + 1.0) / 2.0 * guiW;
            scrY = Math.max(4.0, Math.min(guiH - 4.0, (ndcY + 1.0) / 2.0 * guiH));
        }

        buildLabel(dist3D, distXZ, useFixedSpeed, elytHwyActive, fixedMode, avgSpeed);
        measureText();
        hasTarget = true;
    }

    private void buildLabel(double dist3D, double distXZ, boolean useFixedSpeed, boolean elytHwyActive, boolean fixedMode, double avgSpeed) {
        boolean hideLabel = module.hideLabel.getValue();
        double effectiveSpeed = useFixedSpeed ? module.customSpeed.getValue()
                : elytHwyActive ? 40.79
                : avgSpeed;

        String eta;
        if (!fixedMode && avgSpeed < cachedMinSpeed) {
            etaUnknown = true;
            eta = (hideLabel ? "" : "ETA: ") + cachedUnknownText;
        } else {
            etaUnknown = false;
            double etaSecs = dist3D / effectiveSpeed;
            if (!fixedMode && module.averageEstimate.getValue() > 0) {
                etaSum += etaSecs - etaBuf[etaIdx];
                etaBuf[etaIdx] = etaSecs;
                etaIdx = (etaIdx + 1) % etaBufSize;
                etaSecs = etaSum / etaBufSize;
            }
            eta = formatEta((int) etaSecs, hideLabel);
        }

        labelBuilder.setLength(0);
        if (module.showName.getValue() && !storedWpName.isEmpty()) labelBuilder.append(storedWpName).append(" | ");
        if (module.showDistance.getValue()) {
            if (module.distanceKm.getValue() && distXZ >= 1999) {
                labelBuilder.append(String.format("%.2fkm", distXZ / 1000.0)).append(" | ");
            } else {
                labelBuilder.append((int) distXZ).append("m | ");
            }
        }
        labelBuilder.append(eta);
        etaText = labelBuilder.toString();
    }

    private void measureText() {
        if (module.customFont.getValue()) {
            var rFont = RusherHackAPI.getRenderer2D().getFontRenderer();
            cachedTextW = (int) rFont.getStringWidth(etaText);
            cachedTextH = (int) rFont.getFontHeight();
        } else {
            var mcFont = Minecraft.getInstance().font;
            cachedTextW = mcFont.width(etaText);
            cachedTextH = mcFont.lineHeight;
        }
    }

    @Subscribe
    private void onRender2D(EventRender2D event) {
        if (!module.isToggled() || !hasTarget) return;
        if (etaUnknown && !module.showWhenUnknown.getValue()) return;
        if (fadeAlpha <= 0.05) return;
        try {
            var window = Minecraft.getInstance().getWindow();
            double guiW = window.getGuiScaledWidth();
            double guiH = window.getGuiScaledHeight();

            double drawX = scrX, drawY = scrY;
            if (module.labelOffset.getValue()) {
                if (module.offsetFixed.getValue()) {
                    drawX = guiW * 0.5;
                    drawY = module.offsetRelative.getValue() ? scrY : guiH * 0.5;
                }
                drawX += cachedOffsetX;
                drawY += cachedOffsetY;
            }

            int textColor = (etaUnknown ? module.unknownColor.getValue() : module.textColor.getValue()).getRGB();
            if (fadeAlpha < 1.0) textColor = fadeArgb(textColor, fadeAlpha);

            int textX = (int) (drawX - cachedTextW * 0.5);
            int pad = 2;
            int textY = (int) (drawY + cachedTextH + 10.0);
            if (textY + cachedTextH + pad > guiH) textY = (int) (drawY - cachedTextH - pad - 4);

            int bgColor = fadeAlpha < 1.0 ? fadeArgb(cachedBgColor, fadeAlpha) : cachedBgColor;
            if (bgColor != 0) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                event.getGraphics().fill(textX - pad, textY - pad, textX + cachedTextW + pad, textY + cachedTextH + pad, bgColor);
                event.getGraphics().flush();
                RenderSystem.disableBlend();
            }

            boolean shadow = module.textShadow.getValue();
            boolean isGradient = etaUnknown
                    ? module.rainbowGradientUnknownText.getValue()
                    : module.rainbowGradientText.getValue();

            if (module.customFont.getValue()) {
                var rFont = RusherHackAPI.getRenderer2D().getFontRenderer();
                var stack = event.getMatrixStack();
                stack.pushPose();
                stack.translate(textX, textY, 0);
                rFont.begin(stack);
                if (isGradient) {
                    double cx = 0;
                    for (int i = 0; i < etaText.length(); i++) {
                        String ch = String.valueOf(etaText.charAt(i));
                        int c = rainbowCharColor(i, fadeAlpha);
                        rFont.drawString(ch, cx, 0, c, shadow);
                        cx += rFont.getStringWidth(ch);
                    }
                } else {
                    rFont.drawString(etaText, 0, 0, textColor, shadow);
                }
                rFont.end();
                stack.popPose();
            } else {
                var mcFont = Minecraft.getInstance().font;
                if (isGradient) {
                    int cx = textX;
                    for (int i = 0; i < etaText.length(); i++) {
                        String ch = String.valueOf(etaText.charAt(i));
                        int c = rainbowCharColor(i, fadeAlpha);
                        event.getGraphics().drawString(mcFont, ch, cx, textY, c, shadow);
                        cx += mcFont.width(ch);
                    }
                } else {
                    event.getGraphics().drawString(mcFont, etaText, textX, textY, textColor, shadow);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String formatEta(int totalSecs, boolean hideLabel) {
        String prefix = hideLabel ? "" : "ETA: ";
        if (totalSecs < 60) return prefix + totalSecs + "s";
        if (totalSecs < 7200) {
            int minutes = totalSecs / 60, seconds = totalSecs % 60;
            return prefix + minutes + "m " + seconds + "s";
        }
        if (totalSecs < 86400) {
            int hours = totalSecs / 3600, remainderSecs = totalSecs % 3600;
            int minutes = remainderSecs / 60, seconds = remainderSecs % 60;
            return prefix + hours + "h " + minutes + "m " + seconds + "s";
        }
        int days = totalSecs / 86400, remainderSecs = totalSecs % 86400;
        int hours = remainderSecs / 3600, minutes = (remainderSecs % 3600) / 60;
        return prefix + days + "d " + hours + "h " + minutes + "m";
    }

    private static String sanitizeUnknownText(String raw) {
        raw = raw.strip().replace("§", "");
        if (raw.isEmpty()) return "?";
        return raw.length() > 64 ? raw.substring(0, 64) : raw;
    }

    private static int bgColorFromOpacity(int opacity) {
        return (Math.round(opacity / 100.0f * 255) << 24);
    }

    private static int rainbowCharColor(int charIndex, double fadeAlpha) {
        int c = (ColorUtils.getRainbowRGB(charIndex * 500L, 1.0f, 1.0f, 1.0f) & 0x00FFFFFF) | 0xFF000000;
        return fadeAlpha < 1.0 ? fadeArgb(c, fadeAlpha) : c;
    }

    private static int fadeArgb(int argb, double alpha) {
        int a = (int) (((argb >> 24) & 0xFF) * alpha);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    // quadratic curve: first half of the slider range is much less sensitive than the second
    private static double curveOffset(int v) {
        double n = v / 70.0;
        return Math.signum(n) * n * n * 400.0;
    }
}
