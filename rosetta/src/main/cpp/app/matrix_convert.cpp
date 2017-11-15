#include "matrix_convert.hpp"

#include "platform.h"

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <cassert>
#include <cstring>
#include <cstdio>
#include <utility>

void print_bit_repr(uint64_t x) {
  while(x) {
    printf("%llu", x&1);
    x >>= 1;
  }
  printf("\n");
}

template<typename T>
void print_matrix(T* arr, size_t len) {
  printf("[");
  for (size_t i = 0; i < len; i++) {
    printf("%lld ", arr[i]);
  }
  printf("]\n");
}

size_t index(size_t ch, size_t r, size_t c, size_t channels, size_t rows, size_t cols) {
  // assumning row-major
  return ch*rows*cols + r*cols + c;
}

size_t calculate_packed_buf_len(PackedMatrix* m) {
  return m->channels * m->bit_depth * m->rows * (m->columns/64 + (m->columns%64 > 0));
}


void matrix_to_packed_matrix(void* _platform, int64_t* arr, size_t len, PackedMatrix* m) {
  WrapperRegDriver* platform = reinterpret_cast<WrapperRegDriver*>(_platform);
#if 0
  puts("Packing matrix:");
  print_matrix(arr, len);
#endif
  // assume baseaddr, channels, rows and columns prefilled
  const uint32_t channels = m->channels,
                 rows = m->rows,
                 cols = m->columns;
  assert(m->baseAddr > 0);
  assert(arr != NULL);
  assert(channels > 0);
  assert(rows > 0);
  assert(cols > 0);

  // find bit depth
  // TODO: Handle +/-1 case
  auto calc_bit_depth = [](int64_t x) {
#ifdef __GNUC__
    if (x < 0)
      return 64 - __builtin_clzll(~static_cast<uint64_t>(x)) + 1;

    return 64 - __builtin_clzll(static_cast<uint64_t>(x));
#else
#  error Non-GCC compiler
#endif
  };

  const auto mnmx = std::minmax_element(arr, arr+len);
  const bool is_signed = *mnmx.first < 0;
  if (*mnmx.first == -1 && *mnmx.second == 1 && std::find(arr, arr+len, 0) == arr+len) {
    m->bit_depth = 2;
  } else {
    const uint32_t mn_bits = calc_bit_depth(*mnmx.first),
                   mx_bits = calc_bit_depth(*mnmx.second);
    const uint32_t bit_depth = std::max(mn_bits, mx_bits + is_signed);
    m->bit_depth = bit_depth;

  }
  const uint32_t bit_depth = m->bit_depth;
  m->is_signed = is_signed;


  printf("%lu %lu %lu %lu\n", channels, rows, cols, bit_depth);

  // convert to packed format
  size_t buf_len = calculate_packed_buf_len(m);
  uint64_t* buffer = new uint64_t[buf_len];
  size_t buf_index = 0;
  for (int ch = 0; ch < channels; ch++) {
    for (int bd = 0; bd < bit_depth; bd++) {
      uint64_t mask = 1 << bd;
      for (int r = 0; r < rows; r++) {
        uint64_t buf = 0;
        for (int c = 0; c < cols; c++) {
          for (int p = 0; p < 64 && c < cols; c++, p++) {
            uint64_t bit = static_cast<uint64_t>(arr[index(ch, r, c, channels, rows, cols)]) & mask;
            if (bit) {
              buf |= (1 << p);
            }
          }
          if (c < cols) { // p == 64
            c--;
            buffer[buf_index++] = buf;
            buf = 0;
          }
        }
        buffer[buf_index++] = buf;
      }
    }
  }

  //print_matrix(buffer, buf_len);

  // write buffer to DRAM
  platform->copyBufferHostToAccel(buffer, m->baseAddr, buf_len*sizeof(uint64_t));

  // Use columns = number of uints in row
  m->columns = (m->columns-1)/64 + 1;

  puts("print buffer");
  for (int i = 0; i < channels * bit_depth * rows * m->columns; i++) {
    printf("%llu ", buffer[i]);
  }
  puts("\n");


  //
  delete[] buffer;
}

void result_matrix_to_matrix(void* _platform, ResultMatrix* r, int64_t* arr, size_t len) {
  auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);

  assert(len == r->columns * r->rows * r->channels);

  platform->copyBufferAccelToHost(r->baseAddr, arr, len*sizeof(int64_t));
}
