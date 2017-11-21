#pragma once
#include <cstdint>
#include <cstdlib>
#include "matrix_convert.hpp"

typedef struct PackedConvolutionFilters {
  void* base_addr;
  uint32_t input_channels;
  uint32_t output_channels;
  uint32_t bit_depth;
  uint32_t window_size;
  bool is_signed;
} PackedConvolutionFilters;

void image_to_packed_image(void* _platform, int64_t* arr, PackedMatrix* m);

void filters_to_packed_filters(void* _platform, int64_t* arr, PackedConvolutionFilters* m);

