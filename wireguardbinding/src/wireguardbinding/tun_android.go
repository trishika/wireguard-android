package wireguardbinding

/* Implementation of the TUN device interface for android
 */

import (
	"os"
	"wireguard"
)

type NativeTun struct {
	fd   *os.File
	name string
	events chan wireguard.TUNEvent
}

func (tun *NativeTun) Name() string {
	return tun.name
}

func (tun *NativeTun) setMTU(n int) error {
	return nil
}

func (tun *NativeTun) MTU() (int, error) {
	return wireguard.DefaultMTU, nil
}

func (tun *NativeTun) Write(d []byte) (int, error) {
	return tun.fd.Write(d)
}

func (tun *NativeTun) Read(d []byte) (int, error) {
	return tun.fd.Read(d)
}

func (tun *NativeTun) Events() chan wireguard.TUNEvent {
	return tun.events
}

func (tun *NativeTun) Close() error {
	return tun.fd.Close()
}

func CreateTUN(fdint int, name string) (wireguard.TUNDevice, error) {

	fd := os.NewFile(uintptr(fdint), name)
	if fd == nil {
		return nil, nil
	}

	device := &NativeTun{
		fd:   fd,
		name: name,
		events: make(chan wireguard.TUNEvent, 5),
	}

	device.events <- wireguard.TUNEventUp

	return device, nil
}
