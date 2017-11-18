#include "platform.h"
#include "matrix_convert.hpp"
#include <cstdlib>
#include "QBART.hpp"

void Run_Convolution(void* platform, PackedMatrix* image, PackedConvolutionFilters* filters, uint32_t strideExponent, ResultMatrix* result);
