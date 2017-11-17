import logging
from time import time
import numpy as np

import sys
import os

sys.path.append("/home/xilinx/rosetta/rosetta")
import cffi_run
from _fullyconnected import lib

# We assume that we are called with valid layer objects, elsewise things will go wrong.
def qbart_execute(qnn, images):
	qbart_classifications = []
	
	for image_index in range(len(images)):
		activations = images[image_index][1]
		for layer in qnn:
			# Each layer will either do calculations on the A9 or the FPGA.
			# Everything that the FPGA is unable to do, simply runs on the CPU.
			# It will initially look very similar to alot in the provided "layers.py", 
			# but should in the end be entirely different when FPGA implements are finished.
			print(layer.layerType())
			
			if (layer.layerType() == "QNNFullyConnectedLayer"):
				print("Executing FC on FPGA.")
				print(activations)
				activations = cffi_run.Run_BitserialGEMM(lib.alloc_platform(), layer.W, activations)
			
			# Just run the CPU version if an FPGA component doesn't exist.
			else:
				activations = layer.execute(activations)
		
		qbart_classifications.append((images[image_index][0], np.argmax(activations)))
	
	return qbart_classifications
