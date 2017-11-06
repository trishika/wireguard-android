#!/bin/sh

cd wireguardbinding

export GOPATH=`pwd`
export PATH=$PATH:`pwd`/bin

go get golang.org/x/mobile/cmd/gomobile
go get wireguard
gomobile init
gomobile bind -target=android wireguardbinding

cd ..

./gradlew assembleDebug

