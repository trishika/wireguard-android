package wireguardbinding

import (
	"log"
	"wireguard"
	"bufio"
	"strings"
)

var device *wireguard.Device

func Start(fd int, name string) {
	log.Println("Start")

	tun, err := CreateTUN(fd, name)
	if err != nil {
		log.Println("Failed to create tun device: ", err)
		return
	}

	log.Println("NAME ", tun.Name())

	device = wireguard.NewDevice(tun, wireguard.LogLevelDebug)
}

func Socket() int {
	log.Println("Socket")
	fd, _ := wireguard.GetUDPConn(device)
	return int(fd)
}

func Stop() {
	log.Println("Stop")
	device.Close()
	device = nil
}

func SetConf(conf string) {
	log.Println("SetConf")
	scanner := bufio.NewScanner(strings.NewReader(conf))
	wireguard.SetOperation(device, scanner)
}

func GetConf() []string {
	log.Println("GetConf")
	var conf []string
	wireguard.GetOperation(device, conf)
	return conf
}
