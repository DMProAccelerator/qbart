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


  // convert to packed format
  size_t buf_len = calculate_packed_buf_len(m);
  if (m->baseAddr == NULL) {
    m->baseAddr = platform->allocAccelBuffer(buf_len*sizeof(uint64_t));
  }

  uint64_t* buffer = new uint64_t[buf_len];
  size_t buf_index = 0;
  for (uint32_t ch = 0; ch < channels; ch++) {
    for (uint32_t bd = 0; bd < bit_depth; bd++) {
      uint64_t mask = 1 << bd;
      for (uint32_t r = 0; r < rows; r++) {
        uint64_t buf = 0;
        for (uint32_t c = 0; c < cols; c++) {
          for (uint32_t p = 0; p < 64 && c < cols; c++, p++) {
            uint64_t bit = static_cast<uint64_t>(arr[index(ch, r, c, channels, rows, cols)]) & mask;
            if (bit) {
              buf |= (static_cast<uint64_t>(1) << p);
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

  // Use columns = number of uints in row
  m->columns = (m->columns-1)/64 + 1;

  // write buffer to DRAM
  platform->copyBufferHostToAccel(buffer, m->baseAddr, buf_len*sizeof(uint64_t));


  delete[] buffer;
}

void result_matrix_to_matrix(void* _platform, ResultMatrix* r, int64_t* arr, size_t len) {
  auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);

  assert(len == r->columns * r->rows * r->channels);

  platform->copyBufferAccelToHost(r->baseAddr, arr, len*sizeof(int64_t));
}

void image_to_packed_image(void* _platform, int8_t* image, PackedMatrix* m) {
  WrapperRegDriver* platform = (WrapperRegDriver*)_platform;
  int word_size_in_bits = 64;
  int word_size_in_bytes = word_size_in_bits / 8;

  printf("Image to packed\n");

  uint32_t channels = m->channels,
  	rows = m->rows,
	cols = m->columns,
	bit_depth = m->bit_depth;
	
  if(channels < 1){
    printf("Too few channels\n");
    exit(-1);
  }
  if(rows < 1){
    printf("Too few rows\n");
    exit(-1);
  }
  if(cols < 1){
    printf("Too few cols\n");
    exit(-1);
  }
  if(bit_depth < 1){
    printf("Too few bits\n");
    exit(-1);
  }

  int packed_image_row_size_in_bytes = (cols + word_size_in_bits - 1)/word_size_in_bits * word_size_in_bytes,
  packed_image_size_per_bitplane = rows * packed_image_row_size_in_bytes,
  packed_image_size_per_channel = packed_image_size_per_bitplane * bit_depth,
  packed_image_size_in_bytes = packed_image_size_per_channel * channels;

  uint8_t* packed_image = new uint8_t[packed_image_size_in_bytes];
  memset(packed_image, 0, packed_image_size_in_bytes);
  for(int i = 0; i < channels; i++){
    for(int j = 0; j < bit_depth; j++){
      for(int k = 0; k < rows; k++){
	int currByte = 0, currBit = 0;
	for(int l = 0; l < cols; l++){
	  packed_image[packed_image_size_per_channel * i +
		       packed_image_size_per_bitplane * j +
		       packed_image_row_size_in_bytes * k +
		       currByte]
	    |= ((image[i * rows * cols + k * cols + l] >> j) & 1 ) << currBit;
	  currBit++;
	  if(currBit == 8){
	    currBit = 0;
	    currByte++;
	  }
	}
      }
    }
  } 
  
  platform->copyBufferHostToAccel(packed_image, m->baseAddr, packed_image_size_in_bytes);

  delete[] packed_image;
}

void filters_to_packed_filters(void* _platform, int8_t* arr, PackedConvolutionFilters* m){
  WrapperRegDriver* platform = reinterpret_cast<WrapperRegDriver*>(_platform);
  assert(arr != NULL);
  const uint32_t input_channels = m->input_channels,
    output_channels = m->output_channels,
    bit_depth = m->bit_depth,
    window_size= m->window_size;

  assert(input_channels > 0);
  assert(bit_depth > 1);
  assert(window_size > 0);
  assert(output_channels > 0);

  const uint32_t word_size_in_bits = 64;
  const uint32_t word_size_in_bytes = word_size_in_bits / 8;

  int packed_filters_channel_size_in_bytes = (window_size * window_size + word_size_in_bits - 1) / word_size_in_bits * word_size_in_bytes;
  int packed_filters_row_size_in_bytes = packed_filters_channel_size_in_bytes * input_channels;
  int packed_filters_bitplane_size_in_bytes = packed_filters_row_size_in_bytes * output_channels;
  int packed_filters_size_in_bytes = packed_filters_bitplane_size_in_bytes * bit_depth;
  uint8_t* packed_filters = new uint8_t[packed_filters_size_in_bytes];

  
  for(int i = 0; i < bit_depth; i++){
    for(int j = 0; j < output_channels; j++){
      for(int k = 0; k < input_channels; k++){
	int currByte = 0;
	int currBit = 0;
	for(int l = 0; l < window_size * window_size; l++){
	  packed_filters[i * packed_filters_bitplane_size_in_bytes +
			 j * packed_filters_row_size_in_bytes +
			 k * packed_filters_channel_size_in_bytes +
			 currByte] |=
	    ((arr[j * window_size * window_size * input_channels +
		      k * window_size * window_size +
		      l] >> i) & 1) << currBit;
	  currBit++;
	  if(currBit == 8){
	    currBit = 0;
	    currByte++;
	  }
	}
      }
    }
  }
  
  platform->copyBufferHostToAccel(packed_filters, m->base_addr, packed_filters_size_in_bytes);

  delete [] packed_filters;
}
