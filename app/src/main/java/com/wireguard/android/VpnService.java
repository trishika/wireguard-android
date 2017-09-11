package com.wireguard.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.preference.PreferenceManager;

public interface VpnService {
    String KEY_USE_KERNEL_MODULE = "use_kernel_module";

    class Singleton {
        enum VpnImplementation { KERNEL, ANDROID, UNKNOWN};
        private static VpnImplementation implementation = VpnImplementation.UNKNOWN;

        public synchronized static void setImplementation(VpnImplementation implementation) {
            Singleton.implementation = implementation;
        }

        public synchronized static VpnService getInstance() {
            switch (implementation) {
                case ANDROID:
                    return AndroidVpnService.getInstance();
                case KERNEL:
                    return KernelVpnService.getInstance();
            }
            return null;
        }

        public static class VpnServiceConnection implements ServiceConnection {

            private Context ctx;

            public VpnServiceConnection(Context ctx) {
                this.ctx = ctx;

                if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(KEY_USE_KERNEL_MODULE, false))
                    ctx.bindService(new Intent(ctx, KernelVpnService.class), this,
                        Context.BIND_AUTO_CREATE);
                else
                    ctx.bindService(new Intent(ctx, AndroidVpnService.class), this,
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
    }


    /**
     * Attempt to disable and tear down an interface for this configuration. The configuration's
     * enabled state will be updated the operation is successful. If this configuration is already
     * disconnected, or it is not a known configuration, no changes will be made.
     *
     * @param name The name of the configuration (in the set of known configurations) to disable.
     */
    void disable(final String name);

    /**
     * Attempt to set up and enable an interface for this configuration. The configuration's enabled
     * state will be updated if the operation is successful. If this configuration is already
     * enabled, or it is not a known configuration, no changes will be made.
     *
     * @param name The name of the configuration (in the set of known configurations) to enable.
     */
    void enable(final String name);
}
