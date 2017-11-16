from cffi import FFI

ffibuilder = FFI()

ffibuilder.cdef("""
typedef struct PackedMatrix {
  void* baseAddr;
  uint32_t channels;
  uint32_t bit_depth;
  uint32_t rows;
  uint32_t columns;
  bool is_signed;
} PackedMatrix;

typedef struct ResultMatrix {
  void* baseAddr;
  uint32_t channels;
  uint32_t rows;
  uint32_t columns;
  bool is_signed;
} ResultMatrix;

void matrix_to_packed_matrix(void* _platform, int64_t* arr, size_t len, PackedMatrix* m);

//void packed_matrix_to_matrix(PackedMatrix* m, int64_t* arr, size_t len);
void result_matrix_to_matrix(void* _platform, ResultMatrix* r, int64_t* arr, size_t len);

void Run_BitserialGEMM(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R);

void* alloc_platform(void);

void* alloc_dram(void* platform, size_t num_bytes);
void dealloc_dram(void* platform, void* addr);

void* malloc(size_t size);
void free(void* p);

void Run_UART(void *_platform, char c);

""")


ffibuilder.set_source("_fullyconnected",
r"""
#include "bitserialGEMM.hpp"
#include "matrix_convert.hpp"
#include "platform.h"
#include "uart_sender.hpp"
#include <stdint.h>
""",
sources = ['platform-xlnk.cpp', 'matrix_convert.cpp', 'bitserialGEMM.cpp'], # add all sources here
libraries=['sds_lib'],  # add all libraries that must be linked here
extra_compile_args=['-std=c++14'], # extra compile args
source_extension='.cpp') # because we're using c++

if __name__=='__main__':
    ffibuilder.compile(verbose=True)
