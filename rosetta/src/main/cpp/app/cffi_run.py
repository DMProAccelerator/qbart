from _fullyconnected import lib, ffi
import numpy as np
import math


def Run_BitserialGEMM(platform, W, A):
    if W.ndim == 2:
        W = np.expand_dims(W, axis=0)
    if A.ndim == 2:
        A = np.expand_dims(A, axis=0)
    lhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    lhs.channels, lhs.rows, lhs.columns = W.shape
    lhs.baseAddr = lib.alloc_dram(platform, lhs.rows*lhs.columns*lhs.channels * ffi.sizeof('uint64_t'))

    rhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    rhs.channels, rhs.rows, rhs.columns = A.shape
    rhs.baseAddr = lib.alloc_dram(platform, rhs.rows*rhs.columns*rhs.channels * ffi.sizeof('uint64_t'))


    res = ffi.cast('ResultMatrix *', lib.malloc(ffi.sizeof('ResultMatrix')))
    res.baseAddr = lib.alloc_dram(platform, rhs.rows*rhs.columns*rhs.channels * ffi.sizeof('uint64_t'))

    # Don't transpose channels
    AT = np.transpose(A, (0, 2, 1))

    W = W.flatten();
    AT = AT.flatten()

    Wptr = ffi.cast('int64_t *', ffi.from_buffer(W))
    ATptr = ffi.cast('int64_t *', ffi.from_buffer(AT))

    lib.matrix_to_packed_matrix(platform, Wptr, lhs.rows*lhs.columns*lhs.channels, lhs)
    lib.matrix_to_packed_matrix(platform, ATptr, rhs.rows*rhs.columns*rhs.channels, rhs)

    print("LHS:\nChannels: {lhs.channels}\nRows: {lhs.rows}\nColumns: {lhs.columns}\nBit depth: {lhs.bit_depth}\nIs signed: {lhs.is_signed}".format(lhs=lhs))
    print("RHS:\nChannels: {rhs.channels}\nRows: {rhs.rows}\nColumns: {rhs.columns}\nBit depth: {rhs.bit_depth}\nIs signed: {rhs.is_signed}".format(rhs=rhs))


def main():
    platform = lib.alloc_platform();
    W = np.array([[-1, 1], [1, -1]], dtype=np.int64)
    A = np.array([[-1, 1], [-1, 1]], dtype=np.int64)
    Run_BitserialGEMM(platform, W, A)
    #lhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    #lhs.rows = 2
    #lhs.columns = 2
    #lhs.channels = 1
    #lhs.is_signed = 0
    #lhs.baseAddr = lib.alloc_dram(platform, lhs.rows*lhs.columns*lhs.channels * ffi.sizeof('uint64_t'))


    #rhs = ffi.cast('PackedMatrix *', lib.malloc(ffi.sizeof('PackedMatrix')))
    #rhs.rows = 2
    #rhs.columns = 2
    #rhs.channels = 1
    #rhs.is_signed = 0
    #rhs.baseAddr = lib.alloc_dram(platform, rhs.rows*rhs.columns*rhs.channels * ffi.sizeof('uint64_t'))

    #res = ffi.cast('ResultMatrix *', lib.malloc(ffi.sizeof('ResultMatrix')))
    #res.baseAddr = lib.alloc_dram(platform, rhs.rows*rhs.columns*rhs.channels * ffi.sizeof('uint64_t'))

    #if A.ndim == 3:
    #    # Don't transpose channels
    #    AT = np.transpose(A, (0, 2, 1))
    #else:
    #    AT = np.transpose(A)
    #W = W.flatten()
    #AT = AT.flatten()

    #Wptr = ffi.cast('int64_t *', ffi.from_buffer(W))
    #ATptr = ffi.cast('int64_t *', ffi.from_buffer(AT))

    #lib.matrix_to_packed_matrix(platform, Wptr, lhs.rows*lhs.columns*lhs.channels, lhs)
    #lib.matrix_to_packed_matrix(platform, ATptr, rhs.rows*rhs.columns*rhs.channels, rhs)

    #print("LHS:\nChannels: {lhs.channels}\nRows: {lhs.rows}\nColumns: {lhs.columns}\nBit depth: {lhs.bit_depth}\nIs signed: {lhs.is_signed}".format(lhs=lhs))
    #print("RHS:\nChannels: {rhs.channels}\nRows: {rhs.rows}\nColumns: {rhs.columns}\nBit depth: {rhs.bit_depth}\nIs signed: {rhs.is_signed}".format(rhs=rhs))




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


