from _fullyconnected import lib, ffi
import numpy as np
import math
import random


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
        print("normal: running test {} of {}".format(i+1, NUM_NORMAL_RUNS))
        test(platform)
    for i in range(NUM_BIPOLAR_RUNS):
        print("bipolar: running test {} of {}".format(i+1, NUM_BIPOLAR_RUNS))
        test(platform, bipolar=True)


def main():
    platform = lib.alloc_platform()
    test_BitserialGEMM(platform)
    lib.Run_UART(platform, 0b10001111);
    lib.dealloc_platform(platform)


if __name__=='__main__':
    main()


