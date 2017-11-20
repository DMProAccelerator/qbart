#pragma once
#include <cstdint.h>

typedef struct ThresholdMatrix {
  void *baseAddr;
  uint32_t num_channels;
  uint32_t num_rows;
  uint32_t num_cols;
  uint32_t num_thresholds;
} ThresholdMatrix;

typedef struct ThresholdResultMatrix {
  void *base_addr_writer;
  uint32_t num_channe;
  uint32_t num_rows;
  uint32_t num_cols;
} ThresholdResultMatrix;

Run_Thresholder(WrapperRegDriver *platform,
  ThresholdMatrix *matrix, ThresholdResultMatrix *matrix_result, int64_t *result);
