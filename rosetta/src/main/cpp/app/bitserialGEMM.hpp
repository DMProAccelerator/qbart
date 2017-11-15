#include "platform.h"
#include "matrix_convert.hpp"
#include <cstdlib>


void Run_BitserialGEMM(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R);

void* alloc_platform();

void* alloc_dram(void* platform, size_t num_bytes);
void dealloc_dram(void* platform, void* addr);

