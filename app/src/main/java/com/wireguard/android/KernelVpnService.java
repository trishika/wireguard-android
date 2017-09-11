package com.wireguard.android;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.wireguard.config.Config;

import java.io.File;

/**
 * Service that handles config state coordination and all background processing for the application.
 */

public class KernelVpnService extends Service implements com.wireguard.android.VpnService {
    private static final String TAG = "KernelVpnService";

    private static KernelVpnService instance;
    public static KernelVpnService getInstance() {
        return instance;
    }

    private final IBinder binder = new Binder();
    private RootShell rootShell;

    @Override
    public void disable(final String name) {
        final Config config = ConfigManager.getInstance().get(name);
        if (config == null || !config.isEnabled())
            return;

        new ConfigDisabler(config).execute();
    }

    @Override
    public void enable(final String name) {
        final Config config = ConfigManager.getInstance().get(name);
        if (config == null || config.isEnabled())
            return;

        new ConfigEnabler(config).execute();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        instance = this;
        return binder;
    }

    @Override
    public void onCreate() {
        // Ensure the service sticks around after being unbound. This only needs to happen once.
        startService(new Intent(this, getClass()));
        rootShell = new RootShell(this);
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        instance = this;
        return START_STICKY;
    }

    private class ConfigDisabler extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigDisabler(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, "Running wg-quick down for " + config.getName());
            final File configFile = new File(getFilesDir(), config.getName() + ".conf");
            return rootShell.run(null, "wg-quick down '" + configFile.getPath() + "'") == 0;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;

            ConfigManager.getInstance().setIsEnable(config.getName(), false);
        }
    }

    private class ConfigEnabler extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigEnabler(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, "Running wg-quick up for " + config.getName());
            final File configFile = new File(getFilesDir(), config.getName() + ".conf");
            return rootShell.run(null, "wg-quick up '" + configFile.getPath() + "'") == 0;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;

            ConfigManager.getInstance().setIsEnable(config.getName(), true);
        }
    }
}
