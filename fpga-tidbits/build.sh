#!/usr/bin/env bash
#
# Builds and runs C++ emulations of Chisel accelerator units.

# Hardcoded for convenience. Change these to suit your needs.
accel=TestDMAWriter
platform=Tester

if [[ "$1" == "run" ]]; then
    cd emu-${accel} && ./main && cd ..
    exit 0
fi

sbt "run emulator ${accel} ${platform}"
cd emu-${accel} && g++ -std=c++11 -o main *.cpp && cd ..
