#!/usr/bin/python2

import socket
import sys
import multiprocessing
from QNN import *
from qbart_helper import *
import cPickle as pickle

"""
A server is a slave PYNQ that is always ready to receive a new set of images alongside a QNN.
It is vital that the servers have the same setup as the master PYNQ (uses the same build image), as
it will use almost the same identical code base. The main difference is that the servers blindly execute
the QNN through python method calls, and there is no notebook to interface to.

So first a connection is established, and we accept at most one connection at a time.
When the QNN picke and the first image is received, we can start executing the QNN on the image in a separate thread.
The server must know when all images have been received, and when all have been received and all have been classified,
we send the classifications results back to the sender.

"""
def classification_server():
	# Some credit is due to: http://www.bogotobogo.com/python/python_network_programming_server_client_file_transfer.php
	TCP_IP = "localhost"
	TCP_PORT = 64646
	BUFFER_SIZE = 10000
	
	s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	s.bind((TCP_IP,TCP_PORT))
	
	# We don't allow queueing of connections. There should be only one master on the network,
	# and even so - if we allow waiting, there might be a severe performance penalty for such a run.
	s.listen(1)
	
	# For every new connection, we receive a QNN and all images, classify the images,
	# and return the images.
	while True:
		print("Server socket is listening for TCP Connections on port "+str(TCP_PORT))
		c, addr = s.accept()
		print("This is Ground Control. We read you Major Tom!")
		
		# Get QNN pickle size
		print("Getting QNN Pickle size")
		qnn_bytes_to_receive = int(safe_receive(32, 32, c), 2)
		print(qnn_bytes_to_receive)
		
		# Get QNN, unpickle it.
		print("Getting QNN, and unpickling it.")
		the_qnn = pickle.loads(safe_receive(qnn_bytes_to_receive, 1024, c))
		print("The size of the unpickled QNN is...", sys.getsizeof(the_qnn))
		
		# Then we receive the image list pickle in a very similar manner.
		print("Getting image pickle size")
		image_list_bytes_to_receive = int(safe_receive(32,32, c), 2)
		print("We get this image list byte count:", image_list_bytes_to_receive)	
		print("Getting image list")
		the_image_list = pickle.loads(safe_receive(image_list_bytes_to_receive, 1024, c))
		print("I got ", len(the_image_list), "images!")
		
		# We have received a QNN and an image, now we classify. We separate into two concurrently executing threads: The execution, and a status message sender.
		status_queue = multiprocessing.JoinableQueue()
		result_queue = multiprocessing.Queue()
		
		status_send_process = status_message_sender(len(the_image_list), status_queue, c)
		qbart_execute_process = qbart_execute(the_qnn, the_image_list, status_queue, result_queue)
		
		print("Classifying..")
		status_send_process.start()
		qbart_execute_process.start()
		
		# Wait until results are finished.
		print("Attempting to join qbart_execute")
		qbart_execute_process.join()
		
		print("Attempting to terminate qbart_execute")
		qbart_execute_process.terminate()

		# Wait until all status messages are finished sending.
		print("Attempting to wait for status_queue to be empty")
		print(status_queue.empty())
		status_queue.join()
		
		print("Now terminating status send")
		status_send_process.terminate()
		
		# We are done! Now let's return the result to sender.
		print("Returning classifications list...")
		classifications_list_as_ints = result_queue.get()
		classifications_list_as_ints_pickled = pickle.dumps(classifications_list_as_ints)
		classifications_pickle_size = len(classifications_list_as_ints_pickled)
		classifications_pickle_size = bin(classifications_pickle_size)[2:].zfill(32)
		
		safe_send("FILE", 4, c)
		safe_send(classifications_pickle_size, 32, c)
		safe_send(classifications_list_as_ints_pickled, int(classifications_pickle_size,2), c)
		
		status_send_process.terminate()

	#	At this point the connection should be done, and we wait for a new round of qnn and images
	s.close()

classification_server()
