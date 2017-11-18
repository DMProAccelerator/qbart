#pragma once
#include <cstdint>
#include <cstdlib>

typedef struct PackedMatrix {
  void* baseAddr;
  uint32_t channels;
  uint32_t bit_depth;
  uint32_t rows;
  uint32_t columns;
  bool is_signed;
} PackedMatrix;

typedef struct ResultMatrix {
  void* baseAddr;
  uint32_t channels;
  uint32_t rows;
  uint32_t columns;
  bool is_signed;
} ResultMatrix;

void matrix_to_packed_matrix(void* _platform, int64_t* arr, size_t len, PackedMatrix* m);

void result_matrix_to_matrix(void* _platform, ResultMatrix* r, int64_t* arr, size_t len);

