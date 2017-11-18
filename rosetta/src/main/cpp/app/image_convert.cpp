#include "platform.h"
#include "image_convert.hpp"
#include <cstdio>
#include <cassert>
#include <string.h>

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
