#include <iostream>
#include <string.h>

#include "TestDMAWriter.hpp"
#include "platform.h"

using namespace std;

bool Run_TestDMAWriter(WrapperRegDriver * platform) {
  TestDMAWriter t(platform);

  cout << "Signature: " << hex << t.get_signature() << dec << endl;

  uint64_t ub = 16;

  uint64_t *expected = (uint64_t *) calloc(ub, sizeof(uint64_t));
  uint64_t buffer_size = ub * sizeof(uint64_t);

  void *device_buffer = platform->allocAccelBuffer(buffer_size);

  t.set_baseAddr((AccelDblReg) device_buffer);
  t.set_count(10);
  t.set_start(1);

  while (t.get_finished() != 1);

  uint64_t * host_buffer = (uint64_t *) calloc(ub, sizeof(uint64_t));

  platform->copyBufferAccelToHost(device_buffer, host_buffer, buffer_size);

  t.set_start(0);

  platform->deallocAccelBuffer(device_buffer);

  int res = memcmp(expected, host_buffer, buffer_size);

  if(res != 0) {
    for(uint64_t i = 0; i < ub; i++) {
      cout << host_buffer[i] << endl;
    }
  }

  free(expected);
  free(host_buffer);

  cout << "Result memcmp: " << res << endl;

  return res == 0;
}

int main()
{
  WrapperRegDriver * platform = initPlatform();

  Run_TestDMAWriter(platform);

  deinitPlatform(platform);

  return 0;
}
