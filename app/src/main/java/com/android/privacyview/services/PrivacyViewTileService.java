package com.android.privacyview.services;
import android.service.quicksettings.Tile;
import android.content.Intent;
import android.service.quicksettings.TileService;

public class PrivacyViewTileService extends TileService {
    private boolean isServiceRunning = false;

    @Override
    public void onClick() {
        Tile tile = getQsTile();

        if (isServiceRunning) {
            // Stop the foreground service
            stopService(new Intent(this, PrivacyViewForegroundService.class));
            isServiceRunning = false;
            tile.setState(Tile.STATE_INACTIVE); // Update tile state
        } else {
            // Start the foreground service
            startForegroundService(new Intent(this, PrivacyViewForegroundService.class));
            isServiceRunning = true;
            tile.setState(Tile.STATE_ACTIVE); // Update tile state
        }

        tile.updateTile(); // Refresh the tile
    }

    @Override
    public void onStartListening() {
        // Sync tile state with service status
        Tile tile = getQsTile();
        if (isServiceRunning) {
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();

    }

    @Override
    public void onStopListening() {
        // Handle when tile is removed or stopped
    }
}

