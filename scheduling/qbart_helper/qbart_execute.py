from time import time
import numpy as np
import multiprocessing

# qbart_execute is run as a part of a qbart processing server.
# If we have an FPGA-component for it, we run it on the FPGA.
# If we don't, we run it on the CPU.

# Note that if there is one or more FPGA-component in use, it has to be actually run on the PYNQ, and not elsewhere, as that obviously won't work very well.

class qbart_execute(multiprocessing.Process):
	def __init__(self, qnn, images, status_msg_queue, result_queue):
		multiprocessing.Process.__init__(self)
		self.exit = multiprocessing.Event()
		self.qnn = qnn
		self.images = images
		self.status_msg_queue = status_msg_queue
		self.result_queue = result_queue
	
	def run(self):
		qbart_classifications = []
		exists_on_fpga = []
		
		for image_index in range(len(self.images)):
			activations = self.images[image_index][1]
			for layer in self.qnn:
				# Each layer will either do calculations on the A9 or the FPGA.
				# Everything that the FPGA is unable to do, simply runs on the CPU.
				# It will initially look very similar to alot in the provided "layers.py", 
				# but should in the end be entirely different when FPGA implements are finished.
				if (layer.layerType() not in exists_on_fpga):
					activations = layer.execute(activations)
				else:
					# Raise error, we are asked to perform a layer operation we do not know.
					raise ValueError("Invalid layer type.")
			
			qbart_classifications.append((self.images[image_index][0], np.argmax(activations)))
			
			# We tell our status message sender that we have finished this image by just putting the number there.
			self.status_msg_queue.put(image_index+1)
		
		self.result_queue.put(qbart_classifications)
		return
