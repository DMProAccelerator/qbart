import ctypes

# Load shared libraries.
_util = ctypes.CDLL('./libutil1.so')

# Define argument types.
_util.print.argtypes = (None)

def print():
    return _util.print()
