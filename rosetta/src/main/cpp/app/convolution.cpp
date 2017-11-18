#include "convolution.hpp"
#include "platform.h"
#include <assert.h>
#include <string.h> // memset

void Run_Convolution(void* _platform, PackedMatrix* image, PackedConvolutionFilters* filters, uint32_t strideExponent, ResultMatrix* result) {
  WrapperRegDriver* platform = (WrapperRegDriver*)_platform;
  QBART t(platform);

  const int word_size_in_bits = 64;
  const int word_size_in_bytes = 8;

  if(image->channels != filters->input_channels){
    printf("Channels of input image and input channels of filters must match\n");
    exit(-1);
  }
  assert(image->channels == filters->input_channels);
  
  int num_input_channels = image->channels, num_output_channels = filters->output_channels;
    
  int image_width = image->columns, image_height = image->rows;

  int window_size = filters->window_size;
  int num_input_bitplanes = image->bit_depth, num_filter_bitplanes = filters->bit_depth;

  int stride = 1 << strideExponent;

  //std::uniform_int_distribution<int8_t> input_distribution(-(1 << (num_input_bitplanes - 1)), (1 << (num_input_bitplanes - 1)) - 1);
  //std::uniform_int_distribution<int8_t> filter_distribution(-(1 << (num_filter_bitplanes - 1)), (1 << (num_filter_bitplanes - 1)) - 1);
  
  if((image_width - window_size) % stride != 0){
    printf("Invalid combination of numCols, windowSize and stride\n");
    exit(-1);
  }
  
  if((image_height - window_size) % stride != 0){
    printf("Invalid combination of numRows, windowSize and stride\n");
    exit(-1);
  }

  if(window_size > image_width || window_size > image_height){
    printf("Window does not fit inside image!\n");
    exit(-1);
  }

  /*const int image_size_in_bytes = image_width * image_height * num_input_channels;
  // Of the form channels/rows/columns/bitplanes
  int8_t image[image_size_in_bytes];

  for(int i = 0; i < num_input_channels; i++){
    for(int j = 0; j < image_height; j++){
      for(int k = 0; k < image_width; k++){
	image[i * image_width * image_height + j * image_width + k] = input_distribution(generator);
      }
    }
  }

  const int packed_image_row_size_in_bytes = ceilNum(image_width, word_size_in_bits) / 8,
    packed_image_size_per_bitplane = image_height * packed_image_row_size_in_bytes,
    packed_image_size_per_channel = packed_image_size_per_bitplane * num_input_bitplanes,
    packed_image_size_in_bytes = packed_image_size_per_channel * num_input_channels;
  
  // Of the form channels/bitplanes/rows/columns
  uint8_t packed_image[packed_image_size_in_bytes];
  memset(packed_image, 0, packed_image_size_in_bytes);
  
  for(int i = 0; i < num_input_channels; i++){
    for(int j = 0; j < num_input_bitplanes; j++){
      for(int k = 0; k < image_height; k++){
	int currByte = 0, currBit = 0;
	for(int l = 0; l < image_width; l++){
	  packed_image[packed_image_size_per_channel * i +
		       packed_image_size_per_bitplane * j +
		       packed_image_row_size_in_bytes * k +
		       currByte]
	    |= ((image[i * image_width * image_height + k * image_width + l] >> j) & 1 ) << currBit;
	  currBit++;
	  if(currBit == 8){
	    currBit = 0;
	    currByte++;
	  }
	}
      }
    }
    }*/

  // OBS!!! Remember that actual input filters will have to be reversed (index i -> (w*w - 1 - i))
  // for convolution and not correlation to take place

  // Of form output_channels/input_channels/wrows/wcolumns
  /*int8_t filters[window_size * window_size * num_input_channels * num_output_channels];
  
  for(int i = 0; i < num_output_channels; i++){
    for(int j = 0; j < num_input_channels; j++){
      for(int k = 0; k < window_size * window_size; k++){
	filters[(i * num_input_channels + j ) * window_size * window_size + k] =
	  filter_distribution(generator);
      }
    }
  }

  
  const int packed_filters_channel_size_in_bytes = ceilNum(window_size * window_size, word_size_in_bits) / 8;
  const int packed_filters_row_size_in_bytes = packed_filters_channel_size_in_bytes * num_input_channels;
  const int packed_filters_bitplane_size_in_bytes = packed_filters_row_size_in_bytes * num_output_channels;
  const int packed_filters_size_in_bytes = packed_filters_bitplane_size_in_bytes * num_filter_bitplanes;
  uint8_t packed_filters[packed_filters_size_in_bytes];
  memset(packed_filters, 0, packed_filters_size_in_bytes);
  for(int i = 0; i < num_filter_bitplanes; i++){
    for(int j = 0; j < num_output_channels; j++){
      for(int k = 0; k < num_input_channels; k++){
	int currByte = 0;
	int currBit = 0;
	for(int l = 0; l < window_size * window_size; l++){
	  packed_filters[i * packed_filters_bitplane_size_in_bytes +
			 j * packed_filters_row_size_in_bytes +
			 k * packed_filters_channel_size_in_bytes +
			 currByte] |=
	    ((filters[j * window_size * window_size * num_input_channels +
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
    }*/


  const int expected_result_width = (image_width - window_size)/stride + 1;
  const int expected_result_height = (image_height - window_size)/stride + 1;
  const int expected_result_num_elements = expected_result_width * expected_result_height * num_output_channels;
  const int expected_result_size_in_bytes = expected_result_num_elements * sizeof(int64_t);

  /*int64_t expected_result[expected_result_num_elements];
  memset(expected_result, 0, expected_result_size_in_bytes);

  for(int ci = 0; ci < num_input_channels; ci++){
    for(int co = 0; co < num_output_channels; co++){
      for(int i = 0; i < expected_result_height; i++){
	for(int j = 0; j < expected_result_width; j++){
	  for(int k = 0; k < window_size; k++){
	    for(int l = 0; l < window_size; l++){
	      expected_result[co * expected_result_width * expected_result_height  +
			      (i * expected_result_width + j)] +=
		filters[co * num_input_channels * window_size * window_size +
			ci * window_size * window_size +
			k * window_size + l] *
		image[ci * image_width * image_height +
		      (i * stride + k) * image_width +
		      (j * stride + l) ];
	    }
	  }
	}
      }
    }
    }*/

  // For the window-slided image:
  const int ws_window_size_in_bytes = (window_size * window_size + word_size_in_bits - 1)/word_size_in_bits * word_size_in_bytes; //ceilNum(window_size * window_size, word_size_in_bits) / 8;
  const int ws_row_size_in_bytes = ws_window_size_in_bytes * num_input_channels;
  const int ws_num_rows = expected_result_width * expected_result_height * num_input_bitplanes;
  const int ws_size_in_bytes = ws_num_rows * ws_row_size_in_bytes;
  
  void* dram_image = image->baseAddr; // platform->allocAccelBuffer(packed_image_size_in_bytes);
  void* dram_filters = filters->base_addr; // platform->allocAccelBuffer(packed_filters_size_in_bytes);
  void* dram_result = result->baseAddr; // platform->allocAccelBuffer(expected_result_size_in_bytes);
  void* temp_buffer = platform->allocAccelBuffer(ws_size_in_bytes); // For output of sliding window
  
  //platform->copyBufferHostToAccel(packed_image, dram_image, packed_image_size_in_bytes);
  //platform->copyBufferHostToAccel(packed_filters, dram_filters, packed_filters_size_in_bytes);
  
  /*printf("Image address: %x\n", dram_image);
  printf("Filter address: %x\n", dram_filters);
  printf("Output address: %x\n", dram_result);
  printf("Temporary address: %x\n", temp_buffer);

  printf("Image width: %d\n", image_width);
  printf("Image height: %d\n", image_height);
  printf("Image bitplanes: %d\n", num_input_bitplanes);
  printf("Num input channels: %d\n", num_input_channels);

  printf("Stride exponent: %d\n", strideExponent);
  printf("Window size: %d\n", window_size);
  printf("Num output channels: %d\n", num_output_channels);
  printf("Num filter bitplanes: %d\n", num_filter_bitplanes);
  */
  t.set_imageAddr((AccelDblReg)dram_image);
  t.set_filterAddr((AccelDblReg)dram_filters);
  t.set_outputAddr((AccelDblReg)dram_result);
  t.set_tempAddr((AccelDblReg)temp_buffer);
  
  t.set_imageWidth(image_width);
  t.set_imageHeight(image_height);
  t.set_imageNumBits(num_input_bitplanes);
  t.set_imageNumChannels(num_input_channels);
  
  t.set_strideExponent(strideExponent);
  t.set_windowSize(window_size);
  t.set_numOutputChannels(num_output_channels);
  t.set_filtersNumBits(num_filter_bitplanes);

  t.set_conv(1);
  t.set_start(1);

  printf("Starting sliding window\n");
  while(!t.get_finishedWithSlidingWindow());
  printf("Finished slidingWindow\n");
  while(!t.get_done());

  t.set_conv(0);
  t.set_start(0);
  printf("Finished convolution!\n");

  platform->deallocAccelBuffer(temp_buffer);

  int64_t accel_result[expected_result_num_elements];
  platform->copyBufferAccelToHost(dram_result, accel_result, expected_result_size_in_bytes);

  int64_t transposed_accel_result[expected_result_num_elements];
  for(int c = 0; c < num_output_channels; c++){
    for(int j = 0; j < expected_result_width * expected_result_height; j++){
      transposed_accel_result[c * expected_result_width * expected_result_height
			      + j] =
	accel_result[j * num_output_channels + c];
    }
  }
  
#if 0
  printf("Image: \n");
  for(int i = 0; i < num_input_channels; i++){
    printf("Channel %d\n", i);
    for(int j = 0; j < image_height; j++){
      for(int k = 0; k < image_width; k++){
	printf("%d   ", image[i * image_width * image_height + j * image_width + k]);
      }
      printf("\n");
    }
    printf("\n");
  }
  printf("\n");
#endif

#if 0
  printf("Packed image (LSB):\n");
  for(int i = 0; i < num_input_channels; i++){
    printf("Channel %d\n", i);
    for(int j = 0; j < num_input_bitplanes; j++){
      printf("Bitplane %d\n", j);
      for(int k = 0; k < image_height; k++){
	int currByte = 0, currBit = 0;
	for(int l = 0; l < packed_image_row_size_in_bytes; l++){
	  print_lsb(packed_image[packed_image_size_per_channel * i + packed_image_size_per_bitplane * j + packed_image_row_size_in_bytes * k + l]);
	}
	printf("\n");
      }
      printf("\n");
    }
  }
  printf("\n");
#endif


#if 0
  printf("Filters: \n");
  for(int i = 0; i < num_output_channels; i++){
    printf("Output channel %d\n", i);
    for(int j = 0; j < num_input_channels; j++){
      printf("Input channel %d\n", j);
      for(int k = 0; k < window_size; k++){
	for(int l = 0; l < window_size; l++){
	  printf("%d   ", filters[(i * num_input_channels + j ) * window_size * window_size + k * window_size + l]);
	}
	printf("\n");
      }
      printf("\n");
    }
    printf("\n");
  }
#endif


#if 0
  printf("Packed filters (LSB): \n");
  for(int i = 0; i < num_filter_bitplanes; i++){
    printf("Bitplane %d:\n", i);
    for(int j = 0; j < num_output_channels; j++){
      for(int k = 0; k < num_input_channels; k++){
	int currByte = 0;
	int currBit = 0;
	for(int l = 0; l < packed_filters_channel_size_in_bytes; l++){
	  print_lsb(packed_filters[i * packed_filters_bitplane_size_in_bytes +
				   j * packed_filters_row_size_in_bytes +
				   k * packed_filters_channel_size_in_bytes +
				   l]);
	}
	printf("    ");
      }
      printf("\n");
    }
    printf("\n");
  }
#endif

  
#if 0
  uint8_t sliding_result[ws_size_in_bytes];
  platform->copyBufferAccelToHost(temp_buffer, sliding_result, ws_size_in_bytes);
  
  printf("Result from sliding window:\n");
  for(int i = 0; i < num_input_bitplanes; i++){
    printf("Bitplane %d:\n", i);
    for(int j = 0; j < expected_result_width * expected_result_height; j++){
      for(int k = 0; k < ws_row_size_in_bytes; k++){
	print_lsb(sliding_result[i * ws_row_size_in_bytes * expected_result_width * expected_result_height +
				 j * ws_row_size_in_bytes +
				 k]);
      }
      printf("\n");
    }
    printf("\n");
  }
#endif


#if 0
  printf("Expected result: \n");
  for(int c = 0; c < num_output_channels; c++){
    printf("Channel %d:\n", c);
    for(int i = 0; i < expected_result_height; i++){
      for(int j = 0; j < expected_result_width; j++){
	printf("%lld   ", expected_result[c * expected_result_height * expected_result_width +
					i * expected_result_width + j]);
      }
      printf("\n");
    }
    printf("\n");
  }
  printf("\n");
#endif

#if 0
  printf("Accel result: \n");
  for(int c = 0; c < num_output_channels; c++){
    printf("Channel %d:\n", c);
    for(int i = 0; i < expected_result_height; i++){
      for(int j = 0; j < expected_result_width; j++){
	printf("%lld   ", accel_result[c * expected_result_height * expected_result_width +
					i * expected_result_width + j]);
      }
      printf("\n");
    }
    printf("\n");
  }
  printf("\n");
#endif

#if 0
  printf("Transposed accel result: \n");
  printf("Accel result: \n");
  for(int c = 0; c < num_output_channels; c++){
    printf("Channel %d:\n", c);
    for(int i = 0; i < expected_result_height; i++){
      for(int j = 0; j < expected_result_width; j++){
	printf("%lld   ", transposed_accel_result[c * expected_result_height * expected_result_width +
					i * expected_result_width + j]);
      }
      printf("\n");
    }
    printf("\n");
  }
  printf("\n");
#endif

  /*bool equal = true;
  for(int i = 0; i < expected_result_num_elements; i++){
    if(transposed_accel_result[i] != expected_result[i]){
      printf("Element number %d was different\n", i);
      equal = false;
      break;
    }
  }

  if(equal){
    printf("The results were equal!\n");
    }*/
}

