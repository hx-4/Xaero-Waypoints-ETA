package hxgn;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;
import org.rusherhack.core.event.listener.EventListener;

public class WaypointETAPlugin extends Plugin {
    private final WaypointETAModule module = new WaypointETAModule();
    private final EventListener waypointWorldRenderer = new WaypointWorldRenderer(this.module);

    @Override
    public void onLoad() {
        RusherHackAPI.getModuleManager().registerFeature(this.module);
        RusherHackAPI.getEventBus().subscribe(this.waypointWorldRenderer);
        this.getLogger().info("Loaded WaypointETA");
    }

    @Override
    public void onUnload() {
        RusherHackAPI.getEventBus().unsubscribe(this.waypointWorldRenderer);
        this.getLogger().info("Unloaded WaypointETA");
    }
}

