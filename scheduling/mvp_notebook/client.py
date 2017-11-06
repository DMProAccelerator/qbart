import socket
import threading
from time import sleep
from qbart_helper import *
import sys
import cPickle as pickle
"""
The client has a QNN pickle and a set of images that it wants to send
to one or several servers who will receive a copy of the QNN, and a subset each of the images.

The servers will perform the QNN execution, and return the classifications.
It is important that there is a mapping between the image and the classification. Either the image title and the inference
must be sent together, or one must keep the original ordering.

This entire setup depends on the server to have access to the same code base as master (such as the QBART Execution python method),
and one must manually configure ip-addresses and ports beforehand.

It is also important to check if a certain server is available before we start, so we ping each of them to see which ones are available.
TODO: Classification client must make one thread per server socket (or do we have to since the sockets have buffers?)

This function should be run as a separate thread, so that the client can also do some computation while it is waiting. This function
will also spawn a thread for each server connection.
"""
def classification_client(qnn_pickle_string, image_list, server_name_ip_port_tuples):
	# First we check to see if servers are up and running.
	#for server in server_name_ip_port_tuples:
	TCP_IP = '192.168.1.5' #Solfrid
	TCP_PORT = 10107
	BUFFER_SIZE = 4096
	
	active_sockets = []
	
	# We create a new socket for the new server connection
	 
	# We try to connect to the specified IP and PORT through our new socket.
	# If we successfully connect, we add this to the list of open connections.
	#try:
	s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	
	s.connect((TCP_IP, TCP_PORT))
	active_sockets.append(s)
	#except:
	# If we can't connect, we cannot use this socket later in the program.
		#print("Could not connect!")
		#s.close()
	
	# First we send the QNN pickle string.
	qnn_size = len(qnn_pickle_string)
	print(qnn_size)
	qnn_size = bin(qnn_size)[2:].zfill(32)
	print(qnn_size)
	safe_send(qnn_size, 32, s)
	safe_send(qnn_pickle_string, int(qnn_size,2), s)
	
	# Alright, so now we have active sockets, let's send 'em files.
	image_list_pickled_and_stringed = str(pickle.dumps(image_list))
	
	# Send size of image_list_pickle and the actual pickle to the server.
	filesize = len(image_list_pickled_and_stringed)
	print(filesize)
	filesize = bin(filesize)[2:].zfill(32) # encode filesize as 32 bit binary
	print("Size of image list that is now being sent:", filesize)
	safe_send(filesize, 32, s)
	safe_send(image_list_pickled_and_stringed, int(filesize,2), s)
	print("The image list has been sent")
	
	# Now we wait until we get our darn classifications back.
	classifications_pickle_size = int(safe_receive(32,32,s), 2)
	
	classifications_pickle_string = safe_receive(classifications_pickle_size, 1024, s)
	classifications_list_as_ints = pickle.loads(classifications_pickle_string)
	
	s.close()
	
	return classifications_list_as_ints
