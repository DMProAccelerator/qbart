#include "bitserialGEMM.hpp"
#include "platform.h"

void* alloc_platform() {
  return initPlatform();
}

void* alloc_dram(void* platform, size_t num_bytes) {
  return reinterpret_cast<WrapperRegDriver*>(platform)->allocAccelBuffer(num_bytes);
}

void Run_BitserialGEMM(void* platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R) {

}

