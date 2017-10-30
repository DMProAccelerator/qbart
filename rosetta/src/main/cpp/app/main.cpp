#include <iostream>

#include "platform.h"

// #include "ThresholdingUnit.hpp"
// #include "StreamWriterUnit.hpp"
#include "StreamReaderUnit.hpp"

#define SIZE_MATRIX 10
#define SIZE_THRESH 4

using namespace std;

void Run_ThresholdingUnit(WrapperRegDriver *platform);
void Run_StreamReaderUnit(WrapperRegDriver *platform);
void Run_StreamWriterUnit(WrapperRegDriver *platform);


int main()
{
  WrapperRegDriver *platform = initPlatform();

  Run_StreamReaderUnit(platform);

  deinitPlatform(platform);

  return 0;
}


/*
 * FPGA DRAM read/write test.
 */
void Run_StreamReaderUnit(WrapperRegDriver *platform) {
  StreamReaderUnit t(platform);
  // The fpgatidbits DMA components expects the number of bytes to divide 64.
  // Using 4-byte words yield 16 * 4 = 64, which ensures divisibility.
  cout << "Signature: " << hex << t.get_signature() << dec << endl;

  unsigned int ub = 0;
  cout << "Enter upper bound of sum sequence, divisible by 16: " << endl;
  cin >> ub;

  if(ub % 16 != 0) {
    cout << "Error: Upper bound must be divisible by 16." << endl;
    return;
  }

  unsigned int *host_buffer = new unsigned int[ub];
  unsigned int buffer_size = ub * sizeof(unsigned int);
  unsigned int expected = 42;

  host_buffer[0] = 1;
  host_buffer[1] = 2;
  host_buffer[2] = 39;
  host_buffer[3] = 1337

  void *dram_buffer = platform->allocAccelBuffer(buffer_size);
  platform->copyBufferHostToAccel(host_buffer, dram_buffer, buffer_size);

  t.set_baseAddr((AccelDblReg) dram_buffer);
  t.set_byteCount(buffer_size);

  t.set_start(1);

  while(t.get_finished() != 1);

  platform->deallocAccelBuffer(dram_buffer);
  delete [] host_buffer;

  AccelReg res = t.get_out();
  cout << "Result: " << res << " Expected: " << expected << endl;
  unsigned int cc = t.get_cc();
  cout << "CC: " << cc << " CC/Word: " << (float) cc / (float) ub << endl;
  t.set_start(0);
}


// /*
//  * FPGA DRAM read/write test.
//  */
// void Run_StreamWriterUnit(WrapperRegDriver *platform) {
//   StreamWriterUnit t(platform);

//   cout << "Signature: " << hex << t.get_signature() << dec << endl;

//   void *dram_buffer = platform->allocAccelBuffer(sizeof(uint32_t));

//   t.set_baseAddr((AccelDblReg) dram_buffer);
//   t.set_start(1);

//   uint32_t result;

//   platform->copyBufferAccelToHost(dram_buffer, &result, sizeof(uint32_t));

//   cout << "Unit wrote: " << t.get_out() << endl;
//   cout << "CPU read: "<< result << endl;

//   platform->deallocAccelBuffer(dram_buffer);
//   t.set_start(0);
// }


// /*
//  * FPGA thresholding prototype.
//  */
// void Run_ThresholdingUnit(WrapperRegDriver *platform) {
//   ThresholdingUnit t(platform);

//   cout << "Signature: " << hex << t.get_signature() << dec << endl;

//   srand(time(NULL));

//   uint32_t matrix[SIZE_MATRIX];
//   uint32_t threshold[SIZE_THRESH];
//   uint32_t result[SIZE_MATRIX];

//   for (int i = 0; i < SIZE_MATRIX; i++) {
//     matrix[i] = rand() % 256;
//   }
//   for (int i = 0; i < SIZE_THRESH; i++) {
//     threshold[i] = rand() % 256;
//   }

//   // Calculate results.
//   for (int i = 0; i < SIZE_MATRIX; i++) {
//     uint32_t x = matrix[i];
//     uint32_t count = 0;
//     for (int j = 0; j < SIZE_THRESH; j++) {
//       count += (x >= threshold[j]);
//     }
//     result[i] = count;
//   }

//   t.set_op_0(threshold[0]);
//   t.set_op_1(threshold[1]);
//   t.set_op_2(threshold[2]);
//   t.set_op_3(threshold[3]);

//   t.set_size(SIZE_THRESH);

//   for (int i = 0; i < SIZE_MATRIX; i++) {
//     t.set_matrix_bits(matrix[i]);
//     t.set_matrix_valid(1);
//     t.set_start(1);

//     while (t.get_count_valid() == 0);

//     cout << "Result: " << t.get_count_bits() << " Expected: " << result[i]
//       << endl;

//     t.set_start(0);
//     t.set_matrix_valid(0);
//   }

//   cout << "CC: " << t.get_cc() << endl;
// }
