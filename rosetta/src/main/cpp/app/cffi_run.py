from _qbart import lib, ffi
import numpy as np
import math
import random


def Run_Threshold(platform, m, t):

    assert m.ndim == 3
    assert t.ndim == 2

    matrix = ffi.cast('ThresholdMatrix *', lib.malloc(ffi.sizeof('ThresholdMatrix')))
    matrix_result = ffi.cast('ThresholdResultMatrix *', lib.malloc(ffi.sizeof('ThresholdResultMatrix')))



    matrix.num_channels, matrix.num_rows, matrix.num_cols = matrix.shape
    matrix.num_channels, matrix.num_rows, matrix.num_cols = matrix.shape

    matrix.baseAddr = lib.alloc_dram(platform, matrix.num_rows*matrix.num_cols*matrix.num_channels * ffi.sizeof('uint64_t'))
    matrix_result.baseAddr = lib.alloc_dram(platform, matrix.num_rows*matrix.num_cols*matrix.num_channels * ffi.sizeof('uint64_t'))

    result_len = matrix.num_rows*matrix.num_columns*matrix.num_channels
    R = np.zeros(result_len, dtype=np.int64)
    result = ffi.cast('int64_t *', ffi.from_buffer(R))


    lib.Run_Threshold(platform, matrix, matrix_result, result)

    return R

def Run_BitserialGEMM(platform, W, A):
    if W.ndim == 2:
        W = np.expand_dims(W, axis=0)
    if A.ndim == 1:
        A = A.reshape((1, A.shape[0], 1))
    if A.ndim == 2:
        A = np.expand_dims(A, axis=0)
    assert W.ndim == 3
    assert A.ndim == 3
    W = W.astype(np.int64)
    A = A.astype(np.int64)

    lhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    lhs.channels, lhs.rows, lhs.columns = W.shape

    rhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    rhs.channels, rhs.rows, rhs.columns = A.shape

    assert rhs.channels == lhs.channels
    assert lhs.columns == rhs.rows

    res = ffi.cast('ResultMatrix *', lib.malloc(ffi.sizeof('ResultMatrix')))
    res.channels, res.rows, res.columns = lhs.channels, lhs.rows, rhs.columns

    # alloc dram
    lhs.baseAddr = ffi.NULL
    rhs.baseAddr = ffi.NULL # This is allocated by matrix convert to get correct size
    res.baseAddr = lib.alloc_dram(platform, res.rows*res.columns*res.channels * ffi.sizeof('uint64_t'))
    assert lhs.baseAddr != 0
    assert rhs.baseAddr != 0
    assert res.baseAddr != 0

    # Don't transpose channels, only row/col dimensions
    AT = np.transpose(A, (0, 2, 1))
    rhs.rows, rhs.columns = rhs.columns, rhs.rows

    # packing expects flat matrix
    W = W.flatten();
    AT = AT.flatten()
    Wptr = ffi.cast('int64_t *', ffi.from_buffer(W))
    ATptr = ffi.cast('int64_t *', ffi.from_buffer(AT))

    # pack matrices
    lib.matrix_to_packed_matrix(platform, Wptr, lhs.rows*lhs.columns*lhs.channels, lhs)
    lib.matrix_to_packed_matrix(platform, ATptr, rhs.rows*rhs.columns*rhs.channels, rhs)

    lib.Run_BitserialGEMM(platform, lhs, rhs, res);
    # Result now stored transposed in res

    # Get result
    result_len = res.rows*res.columns*res.channels
    R = np.zeros(result_len, dtype=np.int64)
    Rptr = ffi.cast('int64_t *', ffi.from_buffer(R))
    lib.result_matrix_to_matrix(platform, res, Rptr, result_len)
    R = R.reshape((res.channels, res.columns, res.rows)).transpose((0, 2, 1))


    lib.dealloc_dram(platform, lhs.baseAddr);
    lib.dealloc_dram(platform, rhs.baseAddr);
    lib.dealloc_dram(platform, res.baseAddr);

    lib.free(lhs)
    lib.free(rhs)
    lib.free(res)

    return R


################################################################################
#####
##### ONLY TEST CODE BELOW
#####
################################################################################

def run_test(platform, W, A):
    software_res = np.array([np.dot(W[i],A[i]) for i in range(W.shape[0])])
    fpga_res = Run_BitserialGEMM(platform, W, A)
    if not (software_res == fpga_res).all():
        print("Software res ", software_res.shape)
        print(software_res)
        print("FPGA res ", fpga_res.shape)
        print(fpga_res)
        assert False
    print("Test succeeded")



def test_BitserialGEMM(platform):

    # Tweakale parameteres
    MAX_W_ROWS = 256
    MAX_W_COLS = 256
    MAX_A_COLS = 32
    MAX_CHANNELS = 4

    MIN_RAND_NUM = -2000
    MAX_RAND_NUM = 2000

    NUM_NORMAL_RUNS = 10
    NUM_BIPOLAR_RUNS = 10

    random.seed('qbart')

    def test(platform, bipolar=False):
        num_rows_W = random.randint(1, MAX_W_ROWS)
        num_rows_A = num_cols_W = random.randint(1, MAX_W_COLS)
        num_cols_A = random.randint(1, MAX_A_COLS)
        num_channels = random.randint(1, MAX_CHANNELS)

        bipolar_gen = lambda : random.choice((-1, 1))
        normal_gen = lambda : random.randint(MIN_RAND_NUM, MAX_RAND_NUM)

        gen = bipolar_gen if bipolar else normal_gen


        W = np.array([ gen() for c in range(num_cols_W * num_rows_W * num_channels)]).reshape((num_channels, num_rows_W, num_cols_W))
        A = np.array([ gen() for c in range(num_cols_A * num_rows_A * num_channels)]).reshape((num_channels, num_rows_A, num_cols_A))
        run_test(platform, W, A)


    for i in range(NUM_NORMAL_RUNS):
        print "normal: running test {} of {}".format(i+1, NUM_NORMAL_RUNS),
        test(platform)
    for i in range(NUM_BIPOLAR_RUNS):
        print "bipolar: running test {} of {}".format(i+1, NUM_BIPOLAR_RUNS),
        test(platform, bipolar=True)


def Run_Convolution(platform, image_data, filters_data, stride_exponent, image_bitplanes, filters_bitplanes):
    if image_data.ndim == 2:
        image_data = np.expand_dims(image_data, axis=0)
    if filters_data.ndim == 2:
        filters_data = np.expand_dims(filters_data) # So that it is of the form [output_channel][input_channel][element]
    #assert image_data.ndim == 3
    #assert filters_data.ndim == 3
    image = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    image.channels, image.rows, image.columns = image_data.shape
    packed_image_size = image.rows * image_bitplanes * image.channels * ((image.columns + 63)/64) * 8
    #image.baseAddr = lib.alloc_dram(platform, image.rows * image.columns * image.channels * ffi.sizeof('int8_t'))
    image.baseAddr = lib.alloc_dram(platform, packed_image_size) 
    image.bit_depth = image_bitplanes

    filters = ffi.cast('PackedConvolutionFilters * ', lib.malloc(ffi.sizeof('PackedConvolutionFilters')))
    filters.output_channels, filters.input_channels, window_size_squared = filters_data.shape
    filters.window_size = int(round(math.sqrt(window_size_squared)))
    
    packed_filters_size = filters_bitplanes * filters.output_channels * filters.input_channels * ((window_size_squared + 63)/64) * 8
    #filters.base_addr = lib.alloc_dram(platform, filters.output_channels * filters.input_channels * window_size_squared * ffi.sizeof('int8_t'))
    filters.base_addr = lib.alloc_dram(platform, packed_filters_size)
    filters.bit_depth = filters_bitplanes

    result = ffi.cast('ResultMatrix *', lib.malloc(ffi.sizeof('ResultMatrix')))
    conv_num_windows_in_width = ((image.columns - filters.window_size) >> stride_exponent) + 1
    conv_num_windows_in_height = ((image.rows - filters.window_size) >> stride_exponent) + 1
    result.channels, result.rows, result.columns, result.is_signed = filters.output_channels, conv_num_windows_in_width, conv_num_windows_in_height, True
    
    result.baseAddr = lib.alloc_dram(platform, result.channels * result.rows * result.columns * ffi.sizeof('int64_t'))
    flattenedImage = image_data.flatten()
    flattenedFilters = filters_data.flatten()

    flatImPointer = ffi.cast('int8_t *', ffi.from_buffer(flattenedImage))
    flatFiltersPointer = ffi.cast('int8_t *', ffi.from_buffer(flattenedFilters))

    lib.image_to_packed_image(platform, flatImPointer, image)
    lib.filters_to_packed_filters(platform, flatFiltersPointer, filters)
    lib.Run_Convolution(platform, image, filters, stride_exponent, result)

    result_length = result.channels * result.rows * result.columns
    result_matrix = np.zeros(result_length, dtype=np.int64)
    result_pointer = ffi.cast('int64_t *', ffi.from_buffer(result_matrix))
    lib.result_matrix_to_matrix(platform, result, result_pointer, result_length)
    result_matrix = result_matrix.reshape((result.columns, result.rows, result.channels)).transpose((2, 0, 1))
 
    lib.dealloc_dram(platform, result.baseAddr);
    lib.dealloc_dram(platform, filters.base_addr);
    lib.dealloc_dram(platform, image.baseAddr);

    return result_matrix

# NB: Actual convolution, where filters are reversedly applied
def software_convolution(image, filters, stride):
    im_channels, im_height, im_width = image.shape
    output_channels, input_channels, window_size_squared = filters.shape
    window_size = int(round(math.sqrt(window_size_squared)))

    #im2col:
    #nIm = image.reshape((1, im_channels, im_width, im_height))
    nIm = image.reshape((1, im_channels, im_height, im_width)).astype(np.int64)

    out_height = (im_height - window_size) // stride + 1
    out_width = (im_width - window_size) // stride + 1
    
    i0 = np.repeat(np.arange(window_size), window_size)
    i0 = np.tile(i0, im_channels)
    i1 = stride * np.repeat(np.arange(out_height), out_width)
    j0 = np.tile(np.arange(window_size), window_size * im_channels)
    j1 = stride * np.tile(np.arange(out_width), out_height)
    i = i0.reshape(-1, 1) + i1.reshape(1, -1)
    j = j0.reshape(-1, 1) + j1.reshape(1, -1)

    k = np.repeat(np.arange(im_channels), window_size * window_size).reshape(-1, 1)
    cols = nIm[:, k, i, j]
    cols = cols.transpose(1, 2, 0).reshape(window_size * window_size * im_channels, -1)

    filters = filters.reshape((output_channels, input_channels * window_size * window_size))
    
    vn = cols.reshape(im_channels * window_size * window_size, out_height * out_width)
    result = np.dot(filters, vn)
    return result #.flatten()
    
def test_convolution(platform):
    #random.seed('qbart')

    image_width = 131
    image_height = 131
    image_num_channels = 1
    image_num_bitplanes = 1
    
    num_output_channels = 1
    window_size = 11
    stride_exponent = 2
    filter_num_bitplanes = 1

    image = np.array(
         [[[
		random.choice(xrange(-(1 << (image_num_bitplanes - 1)), (1 << (image_num_bitplanes - 1))))
		for c in xrange(image_width)]
		for r in xrange(image_height)]
		for ch in xrange(image_num_channels)],
		dtype=np.int8)
    
    filters = np.array(
    	[[[
		random.choice(xrange(-(1<<(filter_num_bitplanes-1)), (1<<(filter_num_bitplanes-1))))
		for k in xrange(window_size * window_size)]
		for c in xrange(image_num_channels)]
		for r in xrange(num_output_channels)],
		dtype=np.int8)
    #print("Image: ")
    #print( image)
    
    #print("Filters: ") 
    #print(filters)
    result = Run_Convolution(platform, image, filters, stride_exponent, image_num_bitplanes, filter_num_bitplanes)

    software_res = software_convolution(image, filters, 1 << stride_exponent)
    out_width = ((image_width - window_size) >> stride_exponent) + 1
    out_height = ((image_height - window_size) >> stride_exponent) + 1
    software_res = software_res.reshape((num_output_channels, out_height, out_width))

    reversed_filters = np.zeros(filters.shape)

    """for i in range(image_num_channels):
        for j in range(num_output_channels):
	    reversed_filters[j, i, :] = filters[j, i, ::-1]
	    print "Transformed to " , reversed_filters[j, i, :], " from ", filters[j, i, :]
    
    print("Reversed filters: ", reversed_filters)"""

    #result = Run_Convolution(platform, image, reversed_filters, stride_exponent, image_num_bitplanes, filter_num_bitplanes)
 
    #print("Hardware result: ")
    #print(result)

    #print("\n\nSoftware result: ")
    #print(software_res)
    sw_flat = software_res.flatten()
    hw_flat = result.flatten()   

    nums_unequal = 0 
    for i in range(sw_flat.shape[0]):
      if sw_flat[i] != hw_flat[i]:
        print("Element " + str(i) + " is different")
        nums_unequal += 1
    if (software_res == result).all():
      print("Convolution test succeed!")
    else:
      print("The two were unequal")
      print("Num unequal: "+ str(nums_unequal))
      exit(-1)



def main():
    platform = lib.alloc_platform()

    for i in range(3):
      print("Iteration ", i)
      test_convolution(platform)
      test_BitserialGEMM(platform)
    
    lib.Run_UART(platform, 0b00001111) 
    lib.dealloc_platform(platform)


if __name__=='__main__':
    main()


