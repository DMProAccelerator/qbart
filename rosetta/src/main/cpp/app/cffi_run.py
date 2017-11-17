from _fullyconnected import lib, ffi
import numpy as np
import math
import random

def main():
    platform = lib.alloc_platform();

    lib.Run_UART(platform, 0b00001111);


if __name__=='__main__':
    main()



