#include "platform.h"
#include <unistd.h>
#include "TestUART.hpp"
#include <iostream>
#include "uart_sender.hpp"

void Run_UART(void* _platform, uint8_t c) {
  WrapperRegDriver *platform = reinterpret_cast<WrapperRegDriver *>(_platform);
  TestUART t(platform);

  t.set_uart_data(c);
  t.set_start(1);
  while(t.get_done() != 1);
  t.set_start(0);
}

void *alloc_platform() {
	return initPlatform();
}
