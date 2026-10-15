package com.reveny.trailhead.client;

import com.reveny.trailhead.client.dev.ScreenshotDirector;
import net.fabricmc.api.ClientModInitializer;

public class TrailheadClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String viewpointFile = System.getProperty(ScreenshotDirector.PROPERTY);
        if (viewpointFile != null && !viewpointFile.isBlank()) {
            ScreenshotDirector.install(viewpointFile);
        }
    }
}
