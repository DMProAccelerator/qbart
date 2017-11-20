from cffi import FFI

ffibuilder = FFI()

ffibuilder.cdef("""

void* alloc_platform(void);
void dealloc_platform(void* platform);


void* malloc(size_t size);
void free(void* p);

void Run_UART(void *_platform, uint8_t c);

""")


ffibuilder.set_source("_fullyconnected",
r"""
#include "platform.h"
#include "uart_sender.hpp"
#include <stdint.h>
""",
sources = ['platform-xlnk.cpp', 'uart_sender.cpp'], # add all sources here
libraries=['sds_lib'],  # add all libraries that must be linked here
extra_compile_args=['-std=c++14'], # extra compile args
source_extension='.cpp') # because we're using c++

if __name__=='__main__':
    ffibuilder.compile(verbose=True)

