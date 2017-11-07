package com.wireguard.android;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.quicksettings.TileService;
import android.support.annotation.Nullable;
import android.util.Log;

import com.wireguard.config.Config;
import com.wireguard.config.Peer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ConfigManager extends Service
    implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "ConfigManager";
    public static final String KEY_ENABLED_CONFIGS = "enabled_configs";
    public static final String KEY_PRIMARY_CONFIG = "primary_config";
    public static final String KEY_RESTORE_ON_BOOT = "restore_on_boot";

    private final IBinder binder = new Binder();
    private final Set<String> enabledConfigs = new HashSet<>();
    private String primaryName;
    private SharedPreferences preferences;

    private final ObservableTreeMap<String, Config> configurations = new ObservableTreeMap<>();

    private static ConfigManager instance;
    public static ConfigManager getInstance() {
        return instance;
    }

    public static class ConfigManagerConnection implements ServiceConnection {

        private Context ctx;

        public ConfigManagerConnection(Context ctx) {
            this.ctx = ctx;
            ctx.bindService(new Intent(ctx, ConfigManager.class), this,
                Context.BIND_AUTO_CREATE);
        }

        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            // We don't actually need a binding, only notification that the service is started.
            ctx.unbindService(this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName component) {
            // This can never happen; the service runs in the same thread as the activity.
            throw new IllegalStateException();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        instance = this;
        return binder;
    }

    @Override
    public void onCreate() {
        // Ensure the service sticks around after being unbound. This only needs to happen once.
        startService(new Intent(this, getClass()));
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        onSharedPreferenceChanged(preferences, VpnService.KEY_USE_KERNEL_MODULE);

        new ConfigLoader().execute(getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.endsWith(".conf");
            }
        }));
    }

    @Override
    public void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences preferences,
                                          final String key) {
        switch (key) {
            case VpnService.KEY_USE_KERNEL_MODULE: {
                Log.d(TAG, "Update impl config");
                if (preferences.getBoolean(VpnService.KEY_USE_KERNEL_MODULE, false))
                    VpnService.Singleton.setImplementation(VpnService.Singleton.VpnImplementation.KERNEL);
                else
                    VpnService.Singleton.setImplementation(VpnService.Singleton.VpnImplementation.ANDROID);

                new VpnService.Singleton.VpnServiceConnection(ConfigManager.this);
            } break;
            case KEY_PRIMARY_CONFIG: {
                boolean changed = false;
                final String newName = preferences.getString(key, null);
                if (primaryName != null && !primaryName.equals(newName)) {
                    final Config oldConfig = get(primaryName);
                    if (oldConfig != null)
                        oldConfig.setIsPrimary(false);
                    changed = true;
                }
                if (newName != null && !newName.equals(primaryName)) {
                    final Config newConfig = get(newName);
                    if (newConfig != null)
                        newConfig.setIsPrimary(true);
                    else
                        preferences.edit().remove(KEY_PRIMARY_CONFIG).apply();
                    changed = true;
                }
                primaryName = newName;
                if (changed)
                    updateTile();
            } break;
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        instance = this;
        return START_STICKY;
    }

    private void updateTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return;
        Log.v(TAG, "Requesting quick tile update");
        TileService.requestListeningState(this, new ComponentName(this, QuickTileService.class));
    }

    /**
     * Add a new configuration to the set of known configurations. The configuration will initially
     * be disabled. The configuration's name must be unique within the set of known configurations.
     *
     * @param config The configuration to add.
     */
    public void add(final Config config) {
        new ConfigUpdater(null, config).execute();
    }

    /**
     * Remove a configuration from being managed by the service.
     * If successful, the configuration will be removed from persistent storage.
     * If the configuration is not known to the service, no changes will be made.
     *
     * @param name The name of the configuration (in the set of known configurations) to remove.
     */
    public void remove(final String name) {
        final Config config = configurations.get(name);
        if (config == null)
            return;

        configurations.remove(name);
        new ConfigRemover(config).execute();
    }

    /**
     * Update the attributes of the named configuration.
     *
     * @param name   The name of an existing configuration to update.
     * @param config A copy of the configuration, with updated attributes.
     */
    public void update(final String name, final Config config) {
        if (name == null)
            return;

        if (configurations.containsValue(config))
            throw new IllegalArgumentException("Config " + config.getName() + " modified directly");

        final Config oldConfig = configurations.get(name);
        if (oldConfig == null)
            return;

        new ConfigUpdater(oldConfig, config).execute();
    }

    public void setIsEnable(final String name, final boolean isEnabled) {
        if (name == null)
            return;

        Config config = get(name);
        if (config == null)
            return;
        config.setIsEnabled(isEnabled);

        if (isEnabled) {
            enabledConfigs.add(name);
            preferences.edit().putStringSet(KEY_ENABLED_CONFIGS, enabledConfigs).apply();
        } else {
            enabledConfigs.remove(name);
            preferences.edit().putStringSet(KEY_ENABLED_CONFIGS, enabledConfigs).apply();
        }

        if (name.equals(primaryName))
            updateTile();
    }

    /**
     * Retrieve a configuration known and managed by this service. The returned object must not be
     * modified directly.
     *
     * @param name The name of the configuration (in the set of known configurations) to retrieve.
     * @return An object representing the configuration. This object must not be modified.
     */
    public Config get(final String name) {
        return configurations.get(name);
    }

    /**
     * Retrieve the set of configurations known and managed by the service. Configurations in this
     * set must not be modified directly. If a configuration is to be updated, first create a copy
     * of it by calling getCopy().
     *
     * @return The set of known configurations.
     */
    public ObservableSortedMap<String, Config> getConfigs() {
        return configurations;
    }

    private class ConfigLoader extends AsyncTask<File, Void, List<Config>> {

        @Override
        protected List<Config> doInBackground(final File... files) {
            final List<Config> configs = new LinkedList<>();
            final List<String> interfaces = new LinkedList<>();
            for (final File file : files) {
                if (isCancelled())
                    return null;
                final String fileName = file.getName();
                final String configName = fileName.substring(0, fileName.length() - 5);
                Log.v(TAG, "Attempting to load config " + configName);
                try {
                    final Config config = new Config();
                    config.parseFrom(openFileInput(fileName));
                    config.setIsEnabled(interfaces.contains(configName));
                    config.setName(configName);
                    configs.add(config);
                } catch (IllegalArgumentException | IOException e) {
                    Log.w(TAG, "Failed to load config from " + fileName, e);
                }
            }
            return configs;
        }

        @Override
        protected void onPostExecute(final List<Config> configs) {
            if (configs == null)
                return;
            for (final Config config : configs)
                configurations.put(config.getName(), config);

            // Run the handler to avoid duplicating the code here.
            onSharedPreferenceChanged(preferences, KEY_PRIMARY_CONFIG);

            if (VpnService.Singleton.getInstance() != null)
                onVpnServiceAvailable();
            else
                new VpnServiceConnectionCallbacks(ConfigManager.this);
        }
    }

    protected void onVpnServiceAvailable() {
        if (preferences.getBoolean(KEY_RESTORE_ON_BOOT, false)) {
            final Set<String> configsToEnable =
                preferences.getStringSet(KEY_ENABLED_CONFIGS, null);
            if (configsToEnable != null) {
                for (final String name : configsToEnable) {
                    final Config config = configurations.get(name);
                    if (config != null && !config.isEnabled())
                        VpnService.Singleton.getInstance().enable(name);
                }
            }
        }
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


    private class ConfigRemover extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigRemover(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, "Removing config " + config.getName());
            final File configFile = new File(getFilesDir(), config.getName() + ".conf");
            if (configFile.delete()) {
                return true;
            } else {
                Log.e(TAG, "Could not delete configuration for config " + config.getName());
                return false;
            }
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;
            configurations.remove(config.getName());
            if (config.getName().equals(primaryName)) {
                // This will get picked up by the preference change listener.
                preferences.edit().remove(KEY_PRIMARY_CONFIG).apply();
            }
        }
    }

    private class ConfigUpdater extends AsyncTask<Void, Void, Boolean> {
        private Config knownConfig;
        private final Config newConfig;
        private final String newName;
        private final String oldName;

        private ConfigUpdater(final Config knownConfig, final Config newConfig) {
            this.knownConfig = knownConfig;
            this.newConfig = newConfig.copy();
            this.newConfig.setIsEnabled(knownConfig != null ? knownConfig.isEnabled() : false);
            newName = newConfig.getName();
            // When adding a config, "old file" and "new file" are the same thing.
            oldName = knownConfig != null ? knownConfig.getName() : newName;
            if (newName == null || !Config.isNameValid(newName))
                throw new IllegalArgumentException("This configuration does not have a valid name");
            if (isAddOrRename() && configurations.containsKey(newName))
                throw new IllegalStateException("Configuration " + newName + " already exists");
            if (newConfig.getInterface().getPublicKey() == null)
                throw new IllegalArgumentException("This configuration must have a valid keypair");
            for (final Peer peer : newConfig.getPeers())
                if (peer.getPublicKey() == null || peer.getPublicKey().isEmpty())
                    throw new IllegalArgumentException("Each peer must have a valid public key");
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, (knownConfig == null ? "Adding" : "Updating") + " config " + newName);
            final File newFile = new File(getFilesDir(), newName + ".conf");
            final File oldFile = new File(getFilesDir(), oldName + ".conf");
            if (isAddOrRename() && newFile.exists()) {
                Log.w(TAG, "Refusing to overwrite existing config configuration");
                return false;
            }
            try {
                final FileOutputStream stream = openFileOutput(oldFile.getName(), MODE_PRIVATE);
                stream.write(newConfig.toString().getBytes(StandardCharsets.UTF_8));
                stream.close();
            } catch (final IOException e) {
                Log.e(TAG, "Could not save configuration for config " + oldName, e);
                return false;
            }
            if (isRename() && !oldFile.renameTo(newFile)) {
                Log.e(TAG, "Could not rename " + oldFile.getName() + " to " + newFile.getName());
                return false;
            }
            return true;
        }

        private boolean isAddOrRename() {
            return knownConfig == null || !newName.equals(oldName);
        }

        private boolean isRename() {
            return knownConfig != null && !newName.equals(oldName);
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;
            if (knownConfig != null)
                configurations.remove(oldName);
            if (knownConfig == null)
                knownConfig = new Config();
            knownConfig.copyFrom(newConfig);
            knownConfig.setIsPrimary(oldName != null && oldName.equals(primaryName));
            configurations.put(newName, knownConfig);
            if (isRename() && oldName != null && oldName.equals(primaryName))
                preferences.edit().putString(KEY_PRIMARY_CONFIG, newName).apply();
        }
    }
}
