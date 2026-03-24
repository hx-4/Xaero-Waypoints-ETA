package hxgn;

import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.awt.Color;

public class WaypointETAModule extends ToggleableModule {

    public enum Preset {
        DYNAMIC, STATIC, FIXED, RELATIVE;

        @Override
        public String toString() {
            String enumName = name();
            return enumName.charAt(0) + enumName.substring(1).toLowerCase();
        }
    }

    // — Preset —
    final EnumSetting<Preset> preset =
            new EnumSetting<>("Preset", "Apply a preset configuration", Preset.DYNAMIC);

    // — Filters —
    final BooleanSetting filtersGroup =
            new BooleanSetting("Filters", "Waypoint selection rules", true);

    final BooleanSetting allWaypoints =
            new BooleanSetting("AllWaypoints", "Include permanent waypoints, not just temporary ones", true);

    final NumberSetting<Integer> maxDistance =
            new NumberSetting<>("MaxDistance", "Hide label beyond this distance (0 = off)", 10, 0, 1000)
                    .incremental(10);

    final BooleanSetting maxDistanceKm =
            new BooleanSetting("MaxDistanceKm", "Interpret MaxDistance value as kilometres instead of metres. That means it'll max out at 1 million blocks. Set it to 0 if you need more lol", true)
                    .setVisibility(() -> this.maxDistance.getValue() > 0);

    final NumberSetting<Integer> focusAngle =
            new NumberSetting<>("FocusAngle", "How wide the look cone is before a waypoint gets selected (0=exact, 100=loose)", 2, 0, 100)
                    .incremental(1);

    // — Label —
    final BooleanSetting labelGroup =
            new BooleanSetting("Label", "What information to show in the label", true);

    final BooleanSetting showName =
            new BooleanSetting("ShowName", "Show the waypoint name in the label", false);

    final BooleanSetting showDistance =
            new BooleanSetting("ShowDistance", "Show distance to waypoint in the label", false);

    final BooleanSetting distanceKm =
            new BooleanSetting("AutoKm", "Show km when distance is large, metres otherwise.", true)
                    .setVisibility(this.showDistance::getValue);

    final BooleanSetting showWhenUnknown =
            new BooleanSetting("ShowUnknownETA", "Show the label even when speed can't be measured yet", false);

    // — Speed —
    final BooleanSetting speedGroup =
            new BooleanSetting("Speed", "Speed averaging settings", true);

    final NumberSetting<Integer> speedSamples =
            new NumberSetting<>("SpeedSamples", "Frames averaged for speed (more = smoother, less = reactive)", 20, 5, 100)
                    .incremental(1);

    final NumberSetting<Double> minSpeed =
            new NumberSetting<>("MinSpeed", "Minimum speed (b/s) before ETA shows as unknown", 0.5, 0.0, 40.0)
                    .incremental(0.05);

    // — Style —
    final BooleanSetting styleGroup =
            new BooleanSetting("Style", "Visual appearance of the label", true);

    final BooleanSetting customFont =
            new BooleanSetting("CustomFont", "Use RusherHack font renderer instead of Minecraft font", false);

    final BooleanSetting textShadow =
            new BooleanSetting("TextShadow", "Draw a drop shadow behind the ETA text", true);

    final ColorSetting textColor =
            new ColorSetting("TextColor", "ETA label text color", new Color(255, 255, 255, 255));

    final NumberSetting<Integer> bgOpacity =
            new NumberSetting<>("BackgroundOpacity", "Opacity of the background rect (0-100)", 30, 0, 100)
                    .incremental(1);

    // — Position —
    final BooleanSetting labelOffset =
            new BooleanSetting("LabelOffset", "Enable custom label positioning", false);

    final NumberSetting<Integer> offsetX =
            new NumberSetting<>("X", 0, -100, 100)
                    .incremental(1)
                    .setVisibility(this.labelOffset::getValue);

    final NumberSetting<Integer> offsetY =
            new NumberSetting<>("Y", 0, -100, 100)
                    .incremental(1)
                    .setVisibility(this.labelOffset::getValue);

    final BooleanSetting offsetFixed =
            new BooleanSetting("Fixed", "Pin label to screen center instead of following the waypoint", false)
                    .setVisibility(this.labelOffset::getValue);

    final BooleanSetting offsetRelative =
            new BooleanSetting("Relative", "When Fixed, Y still follows camera pitch", false)
                    .setVisibility(this.labelOffset::getValue);

    final BooleanSetting resetOffset =
            new BooleanSetting("ResetOffset", "Reset X and Y offset to 0", false)
                    .setVisibility(this.labelOffset::getValue)
                    .onChange(() -> {
                        if (this.resetOffset.getValue()) {
                            this.offsetX.setValue(0);
                            this.offsetY.setValue(0);
                            this.resetOffset.setValue(false);
                        }
                    });

    public WaypointETAModule() {
        super("WaypointETA", "Shows ETA to the looked-at Xaero temporary waypoint", ModuleCategory.CLIENT);

        this.filtersGroup.addSubSettings(this.allWaypoints, this.maxDistance, this.maxDistanceKm, this.focusAngle);
        this.labelGroup.addSubSettings(this.showName, this.showDistance, this.distanceKm, this.showWhenUnknown);
        this.speedGroup.addSubSettings(this.speedSamples, this.minSpeed);
        this.styleGroup.addSubSettings(this.customFont, this.textShadow, this.textColor, this.bgOpacity);
        this.labelOffset.addSubSettings(this.offsetX, this.offsetY, this.offsetFixed, this.offsetRelative, this.resetOffset);

        this.preset.onChange(() -> applyPreset(this.preset.getValue()));

        this.registerSettings(
                this.preset,
                this.filtersGroup,
                this.labelGroup,
                this.speedGroup,
                this.styleGroup,
                this.labelOffset
        );
    }

    private void applyPreset(Preset p) {
        // Shared base — applied by all presets
        allWaypoints.setValue(true);
        maxDistance.setValue(1000);
        maxDistanceKm.setValue(true);
        focusAngle.setValue(2);
        textShadow.setValue(true);
        textColor.setValue(new Color(255, 255, 255, 255));
        bgOpacity.setValue(30);
        customFont.setValue(false);
        showName.setValue(false);
        showDistance.setValue(false);

        switch (p) {
            case DYNAMIC -> {
                showWhenUnknown.setValue(false);
                speedSamples.setValue(15);
                minSpeed.setValue(15.0);
                labelOffset.setValue(false);
                offsetFixed.setValue(false);
                offsetRelative.setValue(false);
            }
            case STATIC -> {
                showWhenUnknown.setValue(true);
                speedSamples.setValue(20);
                minSpeed.setValue(0.5);
                labelOffset.setValue(false);
                offsetFixed.setValue(false);
                offsetRelative.setValue(false);
            }
            case FIXED -> {
                showWhenUnknown.setValue(true);
                speedSamples.setValue(20);
                minSpeed.setValue(0.5);
                labelOffset.setValue(true);
                offsetFixed.setValue(true);
                offsetRelative.setValue(false);
                offsetX.setValue(0);
                offsetY.setValue(-50);
            }
            case RELATIVE -> {
                showWhenUnknown.setValue(true);
                speedSamples.setValue(20);
                minSpeed.setValue(0.5);
                labelOffset.setValue(true);
                offsetFixed.setValue(true);
                offsetRelative.setValue(true);
                offsetX.setValue(0);
                offsetY.setValue(12);
            }
        }
    }
}
