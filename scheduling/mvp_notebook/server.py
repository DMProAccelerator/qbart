import socket
import threading
import sys
from time import sleep
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

TODO: Add message sending for current status (1% of images done, 2%, etc), so that the PCB team can use this to display
on the PYNQ master screen.

TODO: The server must also receive the config if it is to reshape some images. But in the end
the images should already have been preprocessed by master PYNQ.
"""
def classification_server():
    # Some credit is due to: http://www.bogotobogo.com/python/python_network_programming_server_client_file_transfer.php
    #for server in server_name_ip_port_tuples:
    TCP_IP = 'localhost'
    TCP_PORT = 9004
    BUFFER_SIZE = 1024
    
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind((TCP_IP,TCP_PORT))
    
    # We don't allow queueing of connections. There should be only one master on the network,
    # and even so - if we allow waiting, there might be a severe performance penalty for such a run.
    s.listen(0)
    
    # For every new connection, we receive a QNN and all images, classify the images,
    # and return the images.
    # Credit for filereceiving: Jesper Freesburg (StackOverflow)
    while True:
        print("Server socket is listening for TCP Connections on port "+str(TCP_PORT))
	c, addr = s.accept()
        print("This is Ground Control. We read you Major Tom!")
        
        # We receive the QNN pickle
        print("Ready to receive QNN")
        qnn_size = c.recv(32)
        qnn_size = int(qnn_size, 2)
        qnn_chunks_received = []
        chunksize = 4096
        while qnn_size > 0:
            if qnn_size < chunksize:
                chunksize = qnn_size
            qnn_chunks_received.append(c.recv(chunksize))
            qnn_size -= chunksize
        qnn_received = "".join(qnn_chunks_received)
	
	the_qnn = pickle.loads(qnn_received)
        
        
        # Then we receive the image list pickle in a very similar manner.
        print("Ready to receive image pickle")
        filesize = c.recv(32)
	print("Filesize in binary, ",filesize)
        filesize = int(filesize, 2)
        print("Filesize in bits, ",filesize)
        print("Ready to receive image files")
        image_list_chunks = []
	chunksize = 4096
        while filesize > 0:
            if filesize < chunksize:
                chunksize = filesize
            image_list_chunks.append(c.recv(chunksize))
            filesize -= chunksize
        image_list_pickle = "".join(image_list_chunks)
	image_list = pickle.loads(image_list_pickle)

        # We have received a QNN and an image, now we classify.
	classifications_list_as_ints = qbart_execute(the_qnn, image_list) 
        
	# We are done! Now let's return the result to sender.
	classifications_list_as_ints_pickled = pickle.dumps(classifications_list_as_ints)
	classifications_pickle_size = sys.getsizeof(classifications_list_as_ints_pickled)
	classifications_pickle_size = bin(classifications_pickle_size)[2:].zfill(32)
	c.send(classifications_pickle_size)
	c.sendall(classifications_list_as_ints_pickled)

	# At this point the connection should be done, and we wait for a new round of qnn and images
    s.close()

classification_server()
