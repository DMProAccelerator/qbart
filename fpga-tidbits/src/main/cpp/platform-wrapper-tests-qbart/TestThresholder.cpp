#include <iostream>

#include "TestThresholder.hpp"

#include "string.h"
#include "stdint.h"
#include "platform.h"

using namespace std;

#define SIZE_MATRIX 4
#define SIZE_THRESH 3

void Run_TestThresholder(WrapperRegDriver * platform) {
  TestThresholder t(platform);

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

  t.set_threshold_0(threshold[0]);
  t.set_threshold_1(threshold[1]);
  t.set_threshold_2(threshold[2]);
  t.set_threshold_3(threshold[3]);

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

int main()
{
    WrapperRegDriver * platform = initPlatform();

    Run_TestThresholder(platform);

    deinitPlatform(platform);

    return 0;
}
