#include "TestDMAThresholder.hpp"
#include "platform.h"

typedef struct ThresholdData {
    void* baseAddr;
    uint32_t num_thresholds;
    uint32_t num_elements;
}


void runThreshold(void* _platform, ThresholdData input, ThresholdData output) {
    auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);

    
}
