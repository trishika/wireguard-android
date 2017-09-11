package com.wireguard.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.wireguard.config.Config;

/**
 * Base class for activities that need to remember the current configuration and wait for a service.
 */

abstract class BaseConfigActivity extends Activity {
    protected static final String KEY_CURRENT_CONFIG = "currentConfig";
    protected static final String KEY_IS_EDITING = "isEditing";

    private Config currentConfig;
    private String initialConfig;
    private boolean isEditing;
    private boolean wasEditing;

    protected Config getCurrentConfig() {
        return currentConfig;
    }

    protected boolean isEditing() {
        return isEditing;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved configuration if there is one; otherwise grab it from the intent.
        if (savedInstanceState != null) {
            initialConfig = savedInstanceState.getString(KEY_CURRENT_CONFIG);
            wasEditing = savedInstanceState.getBoolean(KEY_IS_EDITING, false);
        } else {
            final Intent intent = getIntent();
            initialConfig = intent.getStringExtra(KEY_CURRENT_CONFIG);
            wasEditing = intent.getBooleanExtra(KEY_IS_EDITING, false);
        }

        // Trigger starting the services as early as possible
        if (ConfigManager.getInstance() != null)
            onConfigManagerAvailable();
        else
            new ConfigManagerConnectionCallbacks(this);

        Intent intent = AndroidVpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    protected abstract void onCurrentConfigChanged(Config config);

    protected abstract void onEditingStateChanged(boolean isEditing);

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentConfig != null)
            outState.putString(KEY_CURRENT_CONFIG, currentConfig.getName());
        outState.putBoolean(KEY_IS_EDITING, isEditing);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (VpnService.Singleton.getInstance() != null)
                onVpnServiceAvailable();
            else
                new VpnServiceConnectionCallbacks(this);
        }
    }

    protected void onConfigManagerAvailable() {
        // Make sure the subclass activity is initialized before setting its config.
        if (initialConfig != null && currentConfig == null)
            setCurrentConfig(ConfigManager.getInstance().get(initialConfig));
        setIsEditing(wasEditing);
    }

    protected void onVpnServiceAvailable() {
    }

    public void setCurrentConfig(final Config config) {
        if (currentConfig == config)
            return;
        currentConfig = config;
        onCurrentConfigChanged(config);
    }

    public void setIsEditing(final boolean isEditing) {
        if (this.isEditing == isEditing)
            return;
        this.isEditing = isEditing;
        onEditingStateChanged(isEditing);
    }

    private class VpnServiceConnectionCallbacks extends VpnService.Singleton.VpnServiceConnection {
        public VpnServiceConnectionCallbacks(Context ctx) {
            super(ctx);
        }

        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            onVpnServiceAvailable();
        }
    }

    private class ConfigManagerConnectionCallbacks extends ConfigManager.ConfigManagerConnection {
        public ConfigManagerConnectionCallbacks(Context ctx) {
            super(ctx);
        }

        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            onConfigManagerAvailable();
        }
    }
}
