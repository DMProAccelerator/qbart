from _fullyconnected import lib, ffi
import numpy as np
import math


def Run_BitserialGEMM(platform, W, A):
    if W.ndim == 2:
        W = np.expand_dims(W, axis=0)
    if A.ndim == 2:
        A = np.expand_dims(A, axis=0)
    assert W.ndim == 3
    assert A.ndim == 3

    lhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    lhs.channels, lhs.rows, lhs.columns = W.shape

    rhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    rhs.channels, rhs.rows, rhs.columns = A.shape

    assert rhs.channels == lhs.channels
    assert rhs.columns == lhs.rows

    res = ffi.cast('ResultMatrix *', lib.malloc(ffi.sizeof('ResultMatrix')))
    res.channels, res.rows, res.columns = lhs.channels, lhs.rows, rhs.columns

    # alloc dram
    lhs.baseAddr = lib.alloc_dram(platform, lhs.rows*lhs.columns*lhs.channels * ffi.sizeof('uint64_t'))
    rhs.baseAddr = lib.alloc_dram(platform, rhs.rows*rhs.columns*rhs.channels * ffi.sizeof('uint64_t'))
    res.baseAddr = lib.alloc_dram(platform, res.rows*res.columns*res.channels * ffi.sizeof('uint64_t'))

    # Don't transpose channels, only row/col dimensions
    AT = np.transpose(A, (0, 2, 1))

    print("Software result")
    print(W.shape)
    print(A.shape)
    for i in range(W.shape[0]):
        print(np.dot(W[i], A[i]))

    # packing expects flat matrix
    W = W.flatten();
    AT = AT.flatten()
    Wptr = ffi.cast('int64_t *', ffi.from_buffer(W))
    ATptr = ffi.cast('int64_t *', ffi.from_buffer(AT))

    # pack matrices
    lib.matrix_to_packed_matrix(platform, Wptr, lhs.rows*lhs.columns*lhs.channels, lhs)
    lib.matrix_to_packed_matrix(platform, ATptr, rhs.rows*rhs.columns*rhs.channels, rhs)

    print("LHS:\nChannels: {lhs.channels}\nRows: {lhs.rows}\nColumns: {lhs.columns}\nBit depth: {lhs.bit_depth}\nIs signed: {lhs.is_signed}".format(lhs=lhs))
    print("RHS:\nChannels: {rhs.channels}\nRows: {rhs.rows}\nColumns: {rhs.columns}\nBit depth: {rhs.bit_depth}\nIs signed: {rhs.is_signed}".format(rhs=rhs))

    lib.Run_BitserialGEMM(platform, lhs, rhs, res);
    # Result now stored transposed in res


    # Get result
    result_len = res.rows*res.columns*res.channels
    R = np.zeros(result_len, dtype=np.int64)
    Rptr = ffi.cast('int64_t *', ffi.from_buffer(R))
    lib.result_matrix_to_matrix(platform, res, Rptr, result_len)
    R = R.reshape((res.channels, res.columns, res.rows)).transpose((0, 2, 1))
    print(R)


    lib.dealloc_dram(platform, lhs.baseAddr);
    lib.dealloc_dram(platform, rhs.baseAddr);
    lib.dealloc_dram(platform, res.baseAddr);

    lib.free(lhs)
    lib.free(rhs)
    lib.free(res)


def main():
    platform = lib.alloc_platform();

    #W = np.array([[-2, 1], [1, -1]], dtype=np.int64)
    #A = np.array([[-1, 1], [-1, 1]], dtype=np.int64)

    W = np.array(range(8) , dtype=np.int64).reshape((2, 2, 2))
    A = np.array(range(8) , dtype=np.int64).reshape((2, 2, 2))


    Run_BitserialGEMM(platform, W, A)



def test1():

    Aptr = ffi.cast('int64_t *', ffi.from_buffer(A))

    lib.matrix_to_packed_matrix(Aptr, rows*cols*channels, packed_mat)

    B = np.zeros(A.shape, dtype=np.int64)
    Bptr = ffi.cast('int64_t *', B.ctypes.data)
    lib.packed_matrix_to_matrix(packed_mat, Bptr, rows*cols*channels)

    print(A)
    print(B)



def test2():
    A = np.arange(1, 31, 1, dtype=np.int64)
    rows = 3
    cols = 5
    channels = 2
    res = np.zeros(30, dtype=np.int64)

    packed_mat.channels = channels
    packed_mat.rows = rows
    packed_mat.columns = cols
    packed_mat.baseAddr = res.ctypes.data

    Aptr = ffi.cast('int64_t *', A.ctypes.data)

    lib.matrix_to_packed_matrix(Aptr, rows*cols*channels, packed_mat)

    B = np.zeros(30, dtype=np.int64)
    Bptr = ffi.cast('int64_t *', B.ctypes.data)
    lib.packed_matrix_to_matrix(packed_mat, Bptr, rows*cols*channels)

    print(A)
    print(B)
    assert A == B



#AT = A.transpose()

if __name__=='__main__':
    main()


