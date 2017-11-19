#include "bitserialGEMM.hpp"
#include "platform.h"
#include "QBART.hpp"

#include <iostream>
#include <cassert>
#include <time.h>


void* alloc_platform() {
  return initPlatform();
}

void dealloc_platform(void* platform) {
  deinitPlatform(reinterpret_cast<WrapperRegDriver*>(platform));
}

void* alloc_dram(void* platform, size_t num_bytes) {
  return reinterpret_cast<WrapperRegDriver*>(platform)->allocAccelBuffer(num_bytes);
}

void dealloc_dram(void* platform, void* addr) {
  return reinterpret_cast<WrapperRegDriver*>(platform)->deallocAccelBuffer(addr);
}

void Run_BitserialGEMM_timed(void* _platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R) {
  auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);

  assert(W->columns == A->columns);

  QBART t(platform);

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

  t.set_fc(1);
  t.set_start(1);

  std::cout << "W->rows=" << W->rows << " W->cols=" << W->columns
    << " W->bit_depth=" << W->bit_depth << std::endl;
  std::cout << "A->rows=" << A->rows << " A->cols=" << A->columns
    << " A->bit_depth=" << A->bit_depth << std::endl;
  std::cout << "num_chn=" << A->channels << std::endl;

  clock_t begin = clock();
  while (t.get_done()!=1);
  clock_t end = clock();
  double elapsed = double(end-begin) / CLOCKS_PER_SEC;
  std::cout << "fpga elapsed: " << elapsed << std::endl;

  t.set_start(0);
  t.set_fc(0);

#if 0
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


void Run_BitserialGEMM(void* _platform, PackedMatrix* W, PackedMatrix* A, ResultMatrix* R) {
  auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);

  assert(W->columns == A->columns);

  QBART t(platform);

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

  t.set_fc(1);
  t.set_start(1);

  while (t.get_done()!=1);

  t.set_start(0);
  t.set_fc(0);

#if 0
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

