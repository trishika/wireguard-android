package com.wireguard.android;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.wireguard.config.Config;

@TargetApi(Build.VERSION_CODES.N)
public class QuickTileService extends TileService {
    private Config config;
    private ConfigManager configManager;
    private SharedPreferences preferences;

    @Override
    public void onClick() {
        VpnService service = VpnService.Singleton.getInstance();
        if (service != null && config != null) {
            if (config.isEnabled())
                service.disable(config.getName());
            else
                service.enable(config.getName());
        }
    }

    @Override
    public void onCreate() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        configManager = ConfigManager.getInstance();
        if (configManager == null)
            new ConfigManagerCallbacks(this);
        TileService.requestListeningState(this, new ComponentName(this, getClass()));
    }

    @Override
    public void onStartListening() {
        // Since this is an active tile, this only gets called when we want to update the tile.
        final Tile tile = getQsTile();
        final String configName = preferences.getString(ConfigManager.KEY_PRIMARY_CONFIG, null);
        config = configName != null && configManager != null ? configManager.get(configName) : null;
        if (config != null) {
            tile.setLabel(config.getName());
            final int state = config.isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            if (tile.getState() != state) {
                // The icon must be changed every time the state changes, or the color won't change.
                final Integer iconResource = (state == Tile.STATE_ACTIVE) ?
                        R.drawable.ic_tile : R.drawable.ic_tile_disabled;
                tile.setIcon(Icon.createWithResource(this, iconResource));
                tile.setState(state);
            }
        } else {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_disabled));
            tile.setLabel(getString(R.string.loading));
            tile.setState(Tile.STATE_UNAVAILABLE);
        }
        tile.updateTile();
    }

    private class ConfigManagerCallbacks extends ConfigManager.ConfigManagerConnection {
        public ConfigManagerCallbacks(Context ctx) {
            super(ctx);
        }

        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            configManager = ConfigManager.getInstance();
        }
    }
}
