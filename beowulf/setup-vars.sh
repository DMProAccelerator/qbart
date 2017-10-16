#!/usr/bin/env bash
#
# Updates paths appropriately for ARM compiler for HPC.

if [[ -z "$LD_LIBRARY_PATH" ]]; then
    LD_LIBRARY_PATH=/proj/ARMCompiler/lib
else
    LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/proj/ARMCompiler/lib
fi

PATH=$PATH:/proj/ARMCompiler/bin
