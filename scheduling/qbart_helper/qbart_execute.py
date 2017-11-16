import logging
from time import time
import numpy as np

def qbart_execute(qnn, images):
	###############################################################################
	# The actual QBART processing of every image.
	###############################################################################
	qbart_classifications = []
	exists_on_fpga = [] # We are missing convolution, FC, thresholding and pooling on the FPGA.
	
	for image_index in range(len(images)):
		activations = images[image_index][1]
		for layer in qnn:
			# Each layer will either do calculations on the A9 or the FPGA.
			# Everything that the FPGA is unable to do, simply runs on the CPU.
			# It will initially look very similar to alot in the provided "layers.py", 
			# but should in the end be entirely different when FPGA implements are finished.
			if (layer.layerType() not in exists_on_fpga):
				activations = layer.execute(activations)
			else:
				# Raise error, we are asked to perform a layer operation we do not know.
				raise ValueError("Invalid layer type.")
		
		qbart_classifications.append((images[image_index][0], np.argmax(activations)))
	
	return qbart_classifications
