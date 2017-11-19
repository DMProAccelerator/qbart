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
void result_matrix_to_matrix(void* _platform, ResultMatrix* r, int64_t* arr, size_t len);
void Run_BitserialGEMM(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R);
void Run_BitserialGEMM_timed(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R);

void* alloc_platform(void);
void dealloc_platform(void* platform);

void* alloc_dram(void* platform, size_t num_bytes);
void dealloc_dram(void* platform, void* addr);

void* malloc(size_t size);
void free(void* p);

typedef struct PackedConvolutionFilters {
  void* base_addr;
  uint32_t input_channels;
  uint32_t output_channels;
  uint32_t bit_depth;
  uint32_t window_size;
} PackedConvolutionFilters;

void Run_Convolution(void* platform, PackedMatrix* image, PackedConvolutionFilters* filters, uint32_t strideExponent, ResultMatrix* result);
void filters_to_packed_filters(void* _platform, int8_t* arr, PackedConvolutionFilters* m);
void image_to_packed_image(void* _platform, int8_t* arr, PackedMatrix* m);

""")


ffibuilder.set_source("_qbart",
r"""
#include "bitserialGEMM.hpp"
#include "matrix_convert.hpp"
#include "image_convert.hpp"
#include "platform.h"
#include <stdint.h>
#include "convolution.hpp"
""",
sources = ['platform-xlnk.cpp', 'matrix_convert.cpp', 'image_convert.cpp', 'bitserialGEMM.cpp', 'convolution.cpp'], # add all sources here
libraries=['sds_lib'],  # add all libraries that must be linked here
extra_compile_args=['-std=c++14'], # extra compile args
source_extension='.cpp') # because we're using c++

if __name__=='__main__':
    ffibuilder.compile(verbose=True)

