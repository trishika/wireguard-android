package com.wireguard.android;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.wireguard.config.Config;

import wireguardbinding.Wireguardbinding;

public class AndroidVpnService extends android.net.VpnService
        implements com.wireguard.android.VpnService {
    private static final String TAG = "AndroidVpnService";

    private static AndroidVpnService instance;
    public static AndroidVpnService getInstance() {
        return instance;
    }

    private final IBinder binder = new Binder();
    private String enabledConfig;

    @Override
    public void disable(final String name) {
        final Config config = ConfigManager.getInstance().get(name);
        if (config == null || !config.isEnabled())
            return;
        new ConfigDisabler(config).execute();
    }

    @Override
    public void enable(final String name) {
        if (enabledConfig != null) // One config at a time
            return;

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
    public void onRevoke() {
        if (enabledConfig != null)
            disable(enabledConfig);
        stopSelf();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Ensure the service sticks around after being unbound. This only needs to happen once.
        startService(new Intent(this, getClass()));
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
            Wireguardbinding.stop();
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;
            ConfigManager.getInstance().setIsEnable(config.getName(), false);
            enabledConfig = null;
        }
    }

    private class ConfigEnabler extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigEnabler(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            // Vpn service need to be already ready
            if(prepare(getBaseContext()) != null)
                return false;

            Builder builder = new Builder();

            builder.setSession(config.getName());
            builder.addAddress(config.getInterface().getAddress(), 32);
            if (config.getInterface().getDns() != null)
                builder.addDnsServer(config.getInterface().getDns());
            builder.addRoute("0.0.0.0", 0);
            builder.setBlocking(true);
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null) {
                Log.d(TAG, "Unable to create tun device");
                return false;
            }

            Wireguardbinding.start(tun.detachFd(), config.getName());
            long socket = 0;
            while((socket = Wireguardbinding.socket()) == 0) {
                Log.d(TAG, "Wait for socket");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            protect((int) socket);

            Wireguardbinding.setConf(config.toIpcString());

            return true;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;
            ConfigManager.getInstance().setIsEnable(config.getName(), true);
            enabledConfig = config.getName();
        }
    }
}
