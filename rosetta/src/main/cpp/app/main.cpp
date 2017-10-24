#include <iostream>

#include "platform.h"

#include "DMAManagerUnit.hpp"
#include "ThresholdingUnit.hpp"

#define SIZE_MATRIX 10
#define SIZE_THRESH 4

using namespace std;

void Run_DMAManagerUnit(WrapperRegDriver *platform);
void Run_ThresholdingUnit(WrapperRegDriver *platform);

int main()
{
  WrapperRegDriver *platform = initPlatform();

  Run_DMAManagerUnit(platform);

  deinitPlatform(platform);

  return 0;
}


void Run_DMAManagerUnit(WrapperRegDriver *platform) {
  DMAManagerUnit t(platform);

  cout << "Signature: " << hex << t.get_signature() << dec << endl;

  void *dram_buffer = platform->allocAccelBuffer(sizeof(uint32_t));

  t.set_addr((AccelDblReg) dram_buffer);
  t.set_start(1);

  uint32_t result;

  platform->copyBufferAccelToHost(dram_buffer, &result, sizeof(uint32_t));

  cout << "Unit wrote: " << t.get_out() << endl;
  cout << "CPU read: "<< result << endl;

  platform->deallocAccelBuffer(dram_buffer);
  t.set_start(0);
}


void Run_ThresholdingUnit(WrapperRegDriver *platform) {
  ThresholdingUnit t(platform);

  cout << "Signature: " << hex << t.get_signature() << dec << endl;

  srand(time(NULL));

  uint32_t matrix[SIZE_MATRIX];
  uint32_t threshold[SIZE_THRESH];
  uint32_t result[SIZE_MATRIX];

  for (int i = 0; i < SIZE_MATRIX; i++) {
    matrix[i] = rand() % 256;
  }
  for (int i = 0; i < SIZE_THRESH; i++) {
    threshold[i] = rand() % 256;
  }

  // Calculate results.
  for (int i = 0; i < SIZE_MATRIX; i++) {
    uint32_t x = matrix[i];
    uint32_t count = 0;
    for (int j = 0; j < SIZE_THRESH; j++) {
      count += (x >= threshold[j]);
    }
    result[i] = count;
  }

  t.set_op_0(threshold[0]);
  t.set_op_1(threshold[1]);
  t.set_op_2(threshold[2]);
  t.set_op_3(threshold[3]);

  t.set_size(SIZE_THRESH);

  for (int i = 0; i < SIZE_MATRIX; i++) {
    t.set_matrix_bits(matrix[i]);
    t.set_matrix_valid(1);
    t.set_start(1);

    while (t.get_count_valid() == 0);

    cout << "Result: " << t.get_count_bits() << " Expected: " << result[i]
      << endl;

    t.set_start(0);
    t.set_matrix_valid(0);
  }

  cout << "CC: " << t.get_cc() << endl;
}
