package com.wireguard.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The set of valid attributes for an interface or peer over WireGuard IPC configuration channel.
 */

enum IpcAttribute {
    ALLOWED_IPS("allowed_ip"),
    ENDPOINT("endpoint"),
    LISTEN_PORT("listen_port"),
    PERSISTENT_KEEPALIVE("persistent_keepalive_interval"),
    PRE_SHARED_KEY("preshared_key"),
    PRIVATE_KEY("private_key"),
    PUBLIC_KEY("public_key");

    private static final Map<String, IpcAttribute> map;

    static {
        map = new HashMap<>(IpcAttribute.values().length);
        for (final IpcAttribute key : IpcAttribute.values())
            map.put(key.getToken(), key);
    }

    public static IpcAttribute match(final String line) {
        return map.get(line.split("\\s|=")[0]);
    }

    private final String token;
    private final Pattern pattern;

    IpcAttribute(final String token) {
        pattern = Pattern.compile(token + "\\s*=\\s*(\\S.*)");
        this.token = token;
    }

    public String composeWith(final String value) {
        return token + "=" + value + "\n";
    }

    public String getToken() {
        return token;
    }

    public String parseFrom(final String line) {
        final Matcher matcher = pattern.matcher(line);
        if (matcher.matches())
            return matcher.group(1);
        return null;
    }
}
