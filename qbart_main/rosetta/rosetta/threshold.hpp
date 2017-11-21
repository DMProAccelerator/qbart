#pragma once
#include <cstdint>

typedef struct ThresholdMatrix {
  void *baseAddr;
  uint32_t num_channels;
  uint32_t num_rows;
  uint32_t num_cols;
  uint32_t num_thresholds;
} ThresholdMatrix;

void Run_Thresholder(void *_platform, ThresholdMatrix *matrix, int64_t *input_matrix_ptr, int64_t *result);
