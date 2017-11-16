#include "platform.h"
#include "UART.hpp"
#include <iostream>

void Run_UART(void* _platform, char c){
  WrapperRegDriver *platform = reinterpret_cast<WrapperRegDriver *>(_platform);
  UART t(platform);

  int counter = 0;
  while (counter++ < 1000) {
    t.set_data(c);
    t.set_valid(1);
    t.set_valid(0);
  }
}

/*int main(int argc, const char *argv[]) {
  WrapperRegDriver * platform = initPlatform();

	char send_byte = 0;
  if(argc >= 1) {
		send_byte = 0;
		for(int i = 0; i < 8; i++) {
			send_byte |= (argv[1][i] == '1' ? 1 : 0) << (7 - i);
		}
	}
  Run_UART(platform, send_byte);
  deinitPlatform(platform);
  return 0;


}
*/
