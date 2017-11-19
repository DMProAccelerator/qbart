#include "platform.h"
#include "matrix_convert.hpp"
#include <cstdlib>


void Run_BitserialGEMM(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R);
void Run_BitserialGEMM_timed(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R);

void* alloc_platform();
void dealloc_platform(void* platform);

void* alloc_dram(void* platform, size_t num_bytes);
void dealloc_dram(void* platform, void* addr);

