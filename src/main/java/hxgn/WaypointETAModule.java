package hxgn;

import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.Setting;
import org.rusherhack.core.setting.StringSetting;

import java.awt.Color;

/*
TODO:
- Add Nether highway detector (and a toggle setting) that turns on ManualSpeed when youre in the nether at y120
- Port to Meteor or XaeroPlus addon

- look into Save / Load custom config?

 */

public class WaypointETAModule extends ToggleableModule {

    public enum Preset {
        MINIMAL, DYNAMIC, STATIC, FIXED, RELATIVE, EBOUNCER, CUSTOM;

        @Override
        public String toString() {
            String name = name();
            return name.charAt(0) + name.substring(1).toLowerCase();
        }
    }

    private boolean applyingPreset = false;

    // ==== Preset ====
    final EnumSetting<Preset> preset =
            new EnumSetting<>("Preset", "Apply a preset configuration", Preset.MINIMAL);

    // ==== Filters ====
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

    // ==== Display ====
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

    // UnknownETA — group toggle whose children are the unknown-label settings
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

    // ==== Speed ====
    final BooleanSetting speedGroup =
            new BooleanSetting("Speed", "Speed averaging settings", true);

    final BooleanSetting setSpeed =
            new BooleanSetting("ManualSpeed", "Use a fixed speed instead of measuring it", false)
                    .setVisibility(this.speedGroup::getValue);

    final NumberSetting<Double> customSpeed =
            new NumberSetting<>("Speed", "Fixed travel speed in blocks/second", 40.79, 1.0, 40.79)
                    .incremental(0.5)
                    .setVisibility(() -> this.speedGroup.getValue() && this.setSpeed.getValue());

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

    // ==== Formatting ====
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

    // ==== Position ====
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
        super("XaeroWaypointETA", "Shows ETA to the looked-at Xaero temporary waypoint", ModuleCategory.WORLD);

        this.filtersGroup.addSubSettings(this.onlyTemporary, this.focusAngle, this.maxDistance, this.maxDistanceKm);
        this.showWhenUnknown.addSubSettings(this.unknownText, this.unknownColor, this.rainbowGradientUnknownText);
        this.displayGroup.addSubSettings(this.hideLabel, this.showName, this.fadeDistance, this.showDistance, this.distanceKm, this.showWhenUnknown);
        this.speedGroup.addSubSettings(this.setSpeed, this.customSpeed, this.speedSamples, this.minSpeed, this.averageEstimate);
        this.formattingGroup.addSubSettings(this.customFont, this.textShadow, this.textColor, this.rainbowGradientText, this.bgOpacity);
        this.labelOffset.addSubSettings(this.offsetX, this.offsetY, this.offsetFixed, this.offsetRelative, this.resetOffset);

        this.preset.onChange(() -> applyPreset(this.preset.getValue()));

        // Switch to Custom whenever the user manually changes any setting
        for (Setting<?> s : new Setting<?>[] {
                filtersGroup, onlyTemporary, maxDistance, maxDistanceKm, focusAngle,
                displayGroup, hideLabel, showName, showDistance, distanceKm, showWhenUnknown,
                unknownText, unknownColor, rainbowGradientUnknownText, fadeDistance,
                speedGroup, setSpeed, customSpeed, speedSamples, minSpeed, averageEstimate,
                formattingGroup, customFont, textShadow, textColor, rainbowGradientText, bgOpacity,
                labelOffset, offsetX, offsetY, offsetFixed, offsetRelative
        }) {
            s.onChange(this::switchToCustom);
        }
        this.resetOffset.onChange(() -> {
            switchToCustom();
            if (this.resetOffset.getValue()) {
                this.offsetX.setValue(0);
                this.offsetY.setValue(0);
                this.resetOffset.setValue(false);
            }
        });

        this.registerSettings(
                this.preset,
                this.filtersGroup,
                this.displayGroup,
                this.speedGroup,
                this.formattingGroup,
                this.labelOffset
        );
    }

    private void switchToCustom() {
        if (!applyingPreset) {
            this.preset.setValue(Preset.CUSTOM);
        }
    }

    private void applyPreset(Preset p) {
        if (p == Preset.CUSTOM) return;
        applyingPreset = true;
        try {
            // Shared base — applied by all presets
            hideLabel.setValue(false);
            onlyTemporary.setValue(false);
            maxDistance.setValue(0);
            maxDistanceKm.setValue(true);
            focusAngle.setValue(10);
            textShadow.setValue(true);
            textColor.setValue(new Color(255, 255, 255, 255));
            bgOpacity.setValue(30);
            speedSamples.setValue(50);
            averageEstimate.setValue(20);
            setSpeed.setValue(false);
            customFont.setValue(false);
            showName.setValue(false);
            showDistance.setValue(false);
            rainbowGradientText.setValue(false);

            switch (p) {
                case MINIMAL -> {
                    focusAngle.setValue(2);
                    fadeDistance.setValue(15);
                    showWhenUnknown.setValue(false);
                    minSpeed.setValue(4.32);
                    labelOffset.setValue(false);
                    offsetFixed.setValue(false);
                    offsetRelative.setValue(false);
                    offsetX.setValue(0);
                    offsetY.setValue(0);
                    hideLabel.setValue(true);
                }
                case DYNAMIC -> {
                    unknownText.setValue("(˘ω˘✿)ノ*:･ﾟ✧");
                    focusAngle.setValue(2);
                    fadeDistance.setValue(15);
                    showWhenUnknown.setValue(true);
                    minSpeed.setValue(4.32);
                    labelOffset.setValue(false);
                    offsetFixed.setValue(false);
                    offsetRelative.setValue(false);
                    offsetX.setValue(0);
                    offsetY.setValue(0);
                    hideLabel.setValue(true);
                    rainbowGradientText.setValue(true);
                }
                case STATIC -> {
                    unknownText.setValue("( ´・_・)旦`");
                    fadeDistance.setValue(5);
                    showWhenUnknown.setValue(true);
                    minSpeed.setValue(0.5);
                    labelOffset.setValue(false);
                    offsetFixed.setValue(false);
                    offsetRelative.setValue(false);
                    offsetX.setValue(0);
                    offsetY.setValue(0);
                }
                case FIXED -> {
                    unknownText.setValue("ヽ(¬_¬ )");
                    fadeDistance.setValue(5);
                    showWhenUnknown.setValue(true);
                    minSpeed.setValue(0.5);
                    labelOffset.setValue(true);
                    offsetFixed.setValue(true);
                    offsetRelative.setValue(false);
                    offsetX.setValue(0);
                    offsetY.setValue(-50);
                }
                case RELATIVE -> {
                    unknownText.setValue("(っ˘ω˘ς )");
                    fadeDistance.setValue(5);
                    showWhenUnknown.setValue(true);
                    minSpeed.setValue(0.5);
                    labelOffset.setValue(true);
                    offsetFixed.setValue(true);
                    offsetRelative.setValue(true);
                    offsetX.setValue(0);
                    offsetY.setValue(12);
                }
                case EBOUNCER -> {
                    hideLabel.setValue(true);
                    minSpeed.setValue(0.5);
                    averageEstimate.setValue(0);
                    unknownText.setValue("∿≋∿ξ=ξ=ε=ε=∿ ≡ 厂(´∀`)7 ≡");
                    fadeDistance.setValue(5);
                    showWhenUnknown.setValue(true);
                    setSpeed.setValue(true);
                    customSpeed.setValue(40.79);
                    offsetX.setValue(0);
                    offsetY.setValue(0);
                    rainbowGradientText.setValue(true);
                }
            }
        } finally {
            applyingPreset = false;
        }
    }
}
