package hxgn;

import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.StringSetting;

import java.awt.Color;

public class WaypointETAModule extends ToggleableModule {

    final BooleanSetting filtersGroup =
            new BooleanSetting("Filters", "Waypoint selection rules", true);

    final BooleanSetting onlyTemporary =
            new BooleanSetting("TempOnly", "Only show ETAs for temporary waypoints, not all of them.", false)
                    .setVisibility(this.filtersGroup::getValue);

    final NumberSetting<Integer> maxDistance =
            new NumberSetting<>("MaxRange", "Hide label beyond this distance (0 = off)", 0, 0, 10000)
                    .incremental(10)
                    .setVisibility(this.filtersGroup::getValue);

    final BooleanSetting maxDistanceKm =
            new BooleanSetting("InKm", "Interpret MaxRange as kilometres instead of metres", true)
                    .setVisibility(() -> this.filtersGroup.getValue() && this.maxDistance.getValue() > 0);

    final NumberSetting<Integer> focusAngle =
            new NumberSetting<>("LookAngle", "How wide the horizontal look cone is (0=exact crosshair, 100=very loose)", 10, 0, 100)
                    .incremental(1)
                    .setVisibility(this.filtersGroup::getValue);

    final BooleanSetting displayGroup =
            new BooleanSetting("Display", "What information to show in the label", true);

    final BooleanSetting hideLabel =
            new BooleanSetting("HidePrefix", "Hide the \"ETA: \" text before the time.", false)
                    .setVisibility(this.displayGroup::getValue);

    final BooleanSetting showName =
            new BooleanSetting("Name", "Show the waypoint name in the label", false)
                    .setVisibility(this.displayGroup::getValue);

    final NumberSetting<Integer> fadeDistance =
            new NumberSetting<>("FadeRange", "Begin fading the label at this distance (blocks). 0 = off.", 10, 0, 50)
                    .incremental(5)
                    .setVisibility(this.displayGroup::getValue);

    final BooleanSetting showDistance =
            new BooleanSetting("Distance", "Show distance to waypoint in the label", false)
                    .setVisibility(this.displayGroup::getValue);

    final BooleanSetting distanceKm =
            new BooleanSetting("AutoKm", "Show km when distance is large, metres otherwise", true)
                    .setVisibility(() -> this.displayGroup.getValue() && this.showDistance.getValue());

    final BooleanSetting showWhenUnknown =
            new BooleanSetting("UnknownETA", "Show the label even when speed can't be measured yet", false)
                    .setVisibility(this.displayGroup::getValue);

    final StringSetting unknownText =
            new StringSetting("UnknownText", "Text shown when ETA can't be calculated", "?")
                    .setVisibility(() -> this.displayGroup.getValue() && this.showWhenUnknown.getValue());

    final ColorSetting unknownColor =
            new ColorSetting("Color", "Color of the unknown ETA text", new Color(49, 165, 161, 255))
                    .setAlphaAllowed(true).setRainbowAllowed(true)
                    .setVisibility(() -> this.displayGroup.getValue() && this.showWhenUnknown.getValue());

    final BooleanSetting rainbowGradientUnknownText =
            new BooleanSetting("Rainbow", "Apply rainbow gradient to the unknown text", false)
                    .setVisibility(() -> this.displayGroup.getValue() && this.showWhenUnknown.getValue())
                    .onChange((signal) -> {
                        if (signal) unknownColor.setRainbowMode(ColorSetting.RainbowMode.GRADIENT);
                        else unknownColor.setRainbowMode(ColorSetting.RainbowMode.DEFAULT);
                    });

    final BooleanSetting speedGroup =
            new BooleanSetting("Speed", "Speed averaging settings", true);

    final BooleanSetting setSpeed =
            new BooleanSetting("ManualSpeed", "Use a fixed speed instead of measuring it", false)
                    .setVisibility(this.speedGroup::getValue);

    final NumberSetting<Double> customSpeed =
            new NumberSetting<>("Speed", "Fixed travel speed in blocks/second", 40.79, 1.0, 40.79)
                    .incremental(0.5)
                    .setVisibility(() -> this.speedGroup.getValue() && this.setSpeed.getValue());

    final BooleanSetting elytHwy =
            new BooleanSetting("NetherHwyDetect",
                    "Auto-use 40.79 bps when flying elytra in Nether at Y 115-125", false)
                    .setVisibility(() -> this.speedGroup.getValue() && !this.setSpeed.getValue());

    final NumberSetting<Integer> speedSamples =
            new NumberSetting<>("Samples", "Frames averaged for speed (more = smoother, less = reactive)", 60, 5, 1800)
                    .incremental(1)
                    .setVisibility(() -> this.speedGroup.getValue() && !this.setSpeed.getValue());

    final NumberSetting<Double> minSpeed =
            new NumberSetting<>("Threshold", "Minimum speed (b/s) before ETA shows as unknown", 3.0, 0.0, 40.0)
                    .incremental(0.05)
                    .setVisibility(() -> this.speedGroup.getValue() && !this.setSpeed.getValue());

    final NumberSetting<Integer> averageEstimate =
            new NumberSetting<>("AverageETA", "Frames to average ETA over (0 = off)", 20, 0, 200)
                    .incremental(1)
                    .setVisibility(() -> this.speedGroup.getValue() && !this.setSpeed.getValue());

    final BooleanSetting formattingGroup =
            new BooleanSetting("Formatting", "Visual appearance of the label", true);

    final BooleanSetting customFont =
            new BooleanSetting("RHFont", "Use RusherHack font renderer instead of Minecraft font", false)
                    .setVisibility(this.formattingGroup::getValue);

    final BooleanSetting textShadow =
            new BooleanSetting("Shadow", "Draw a drop shadow behind the ETA text", true)
                    .setVisibility(this.formattingGroup::getValue);

    final ColorSetting textColor =
            new ColorSetting("Color", "ETA label text color", new Color(255, 255, 255, 255))
                    .setVisibility(this.formattingGroup::getValue);

    final BooleanSetting rainbowGradientText =
            new BooleanSetting("Rainbow", "Apply rainbow gradient to the ETA text", false)
                    .setVisibility(this.formattingGroup::getValue)
                    .onChange((signal) -> {
                        if (signal) textColor.setRainbowMode(ColorSetting.RainbowMode.GRADIENT);
                        else textColor.setRainbowMode(ColorSetting.RainbowMode.DEFAULT);
                    });

    final NumberSetting<Integer> bgOpacity =
            new NumberSetting<>("BgOpacity", "Opacity of the background rect (0-100)", 20, 0, 100)
                    .incremental(1)
                    .setVisibility(this.formattingGroup::getValue);

    final BooleanSetting labelOffset =
            new BooleanSetting("Position", "Enable custom label positioning", false);

    final NumberSetting<Integer> offsetX =
            new NumberSetting<>("X", 0, -100, 100)
                    .incremental(1)
                    .setVisibility(this.labelOffset::getValue);

    final NumberSetting<Integer> offsetY =
            new NumberSetting<>("Y", 0, -100, 100)
                    .incremental(1)
                    .setVisibility(this.labelOffset::getValue);

    final BooleanSetting offsetFixed =
            new BooleanSetting("Anchored", "Pin label to screen center instead of following the waypoint", false)
                    .setVisibility(this.labelOffset::getValue);

    final BooleanSetting offsetRelative =
            new BooleanSetting("PitchFollow", "When Anchored, Y still follows camera pitch", false)
                    .setVisibility(this.labelOffset::getValue);

    final BooleanSetting resetOffset =
            new BooleanSetting("Reset", "Reset X and Y offset to 0", false)
                    .setVisibility(this.labelOffset::getValue);

    public WaypointETAModule() {
        super("XaeroETA", "Shows ETA to the looked-at Xaero temporary waypoint", ModuleCategory.WORLD);

        this.filtersGroup.addSubSettings(this.onlyTemporary, this.focusAngle, this.maxDistance, this.maxDistanceKm);
        this.showWhenUnknown.addSubSettings(this.unknownText, this.unknownColor, this.rainbowGradientUnknownText);
        this.displayGroup.addSubSettings(this.hideLabel, this.showName, this.fadeDistance, this.showDistance, this.distanceKm, this.showWhenUnknown);
        this.speedGroup.addSubSettings(this.setSpeed, this.customSpeed, this.elytHwy, this.speedSamples, this.minSpeed, this.averageEstimate);
        this.formattingGroup.addSubSettings(this.customFont, this.textShadow, this.textColor, this.rainbowGradientText, this.bgOpacity);
        this.labelOffset.addSubSettings(this.offsetX, this.offsetY, this.offsetFixed, this.offsetRelative, this.resetOffset);

        this.resetOffset.onChange(() -> {
            if (this.resetOffset.getValue()) {
                this.offsetX.setValue(0);
                this.offsetY.setValue(0);
                this.resetOffset.setValue(false);
            }
        });

        this.registerSettings(
                this.filtersGroup,
                this.displayGroup,
                this.speedGroup,
                this.formattingGroup,
                this.labelOffset
        );
    }
}
