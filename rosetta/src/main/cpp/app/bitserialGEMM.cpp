#include "bitserialGEMM.hpp"
#include "platform.h"
#include "TestBitserialGEMM.hpp"

#include <iostream>
#include <cassert>


void* alloc_platform() {
  return initPlatform();
}

void* alloc_dram(void* platform, size_t num_bytes) {
  return reinterpret_cast<WrapperRegDriver*>(platform)->allocAccelBuffer(num_bytes);
}

void dealloc_dram(void* platform, void* addr) {
  return reinterpret_cast<WrapperRegDriver*>(platform)->deallocAccelBuffer(addr);
}

void Run_BitserialGEMM(void* _platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R) {
  auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);

  assert(W->columns == A->columns);

  TestBitserialGEMM t(platform);

  t.set_lhs_addr(reinterpret_cast<AccelDblReg>(W->baseAddr));
  t.set_rhs_addr(reinterpret_cast<AccelDblReg>(A->baseAddr));
  t.set_res_addr(reinterpret_cast<AccelDblReg>(R->baseAddr));

  t.set_lhs_rows(W->rows);
  t.set_lhs_cols(W->columns);
  t.set_lhs_bits(W->bit_depth);
  t.set_lhs_issigned(W->is_signed);

  t.set_rhs_rows(A->rows);
  t.set_rhs_cols(A->columns);
  t.set_rhs_bits(A->bit_depth);
  t.set_rhs_issigned(A->is_signed);

  t.set_num_chn(A->channels);

  //clock_t begin = clock();
  //
  std::cout << W->rows << " " << W->columns << " " << W->bit_depth << " " << W->is_signed << '\n';
  std::cout << A->rows << " " << A->columns << " " << A->bit_depth << " " << A->is_signed << '\n';
  printf("LHS: %lu %lu %lu %d\n", W->rows, W->columns, W->bit_depth, W->is_signed);
  printf("RHS: %lu %lu %lu %d\n", A->rows, A->columns, A->bit_depth, A->is_signed);
  printf("Channels: %llu\n", A->channels);

  t.set_start(1);
  while (t.get_done()!=1);
  //clock_t end = clock();
  //double hardware_elapsed = double(end-begin) / CLOCKS_PER_SEC;
  //cout << "hardware elapsed: " << hardware_elapsed << endl;

  t.set_start(0);
#if 1
  size_t len = A->channels*W->rows*A->rows;
  int64_t *arr = new int64_t[len];
  platform->copyBufferAccelToHost(R->baseAddr, arr, len*sizeof(int64_t));
  puts("From FPGA:");
  for (int i = 0; i < len; i++) {
    printf("%lld ", arr[i]);
  }
  puts("");
#endif

  // transposed result stored in R
  return;
}

