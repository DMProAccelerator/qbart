# DMA testing framework for FPGA deployment

Original source: https://github.com/maltanar/fpga-tidbits

## Getting started

There are three relevant folders/files:

1. `src/main/scala/qbart`

   Contains QBART Chisel modules that run on the FPGA.

2. `src/main/cpp/platform-wrapper-tests-qbart`

   Contains C++ entry-point functions for launching Chisel modules.

3. `src/main/fpga-tidbits/Main.scala`

   Contains an accelerator mapper where all Chisel entry-points are added.

To test a module with the fpga-tidbits emulator, change the `accel` variable in `build.sh` to the name of your module, then do:

```
./build.sh && ./build.sh run
```
