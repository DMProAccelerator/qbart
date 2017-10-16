import ctypes

# Load shared libraries.
_util = ctypes.CDLL('./libutil1.so')

# Define argument types.
_util.sum.argtypes = (ctypes.c_int, ctypes.c_int)
_util.print.argtypes = (None)

def sum(a, b):
    return _util.sum(ctypes.c_int(a), ctypes.c_int(b))

def print():
    return _util.print()
