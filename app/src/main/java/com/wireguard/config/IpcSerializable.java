package com.wireguard.config;

/**
 * Interface for classes that can perform a serialization for the ipc link
 */

public interface IpcSerializable {
    String toIpcString();
}
