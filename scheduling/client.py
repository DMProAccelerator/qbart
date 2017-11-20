#!/usr/bin/env python2
from multiprocessing.dummy import Pool as ThreadPool 
import copy
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
"""
def classification_client(qnn_pickle_string, image_list, server_list):
	# First we check to see if servers are up and running.
	#for server in server_name_ip_port_tuples:
	BUFFER_SIZE = 4096
	
	active_sockets = []
	
	# We create a new socket for the new server connection
	 
	# We try to connect to the specified IP and PORT through our new socket.
	# If we successfully connect, we add this to the list of open connections.
	
	for ip_port in server_list:
		try:
			active_sockets.append(socket.create_connection(ip_port, timeout=5))
			print("Successfully connected to " + str(ip_port[0]))
		except:
			# If we can't connect, we cannot use this socket later in the program.
			print("Could not connect to " + str(ip_port[0]))
	
	# The argumentlist is used to pass arguments to sendandreceive with thread pool mapping. A bit hacky, but a bit cleaner than the "proper" approach.
	argumentlist = []
	
	# For every socket we add arguments. We also set the socket timeout to Null, so it doesn't time out.
	# A future improvement would be to have timeouts, but have the server send isalive messages to client from time to time in case the work set is large.
	for socketnum in range(len(active_sockets)):
		active_sockets[socketnum].settimeout(None)
		argumentlist.append([active_sockets[socketnum], socketnum, len(active_sockets), qnn_pickle_string, image_list])
	
	if len(active_sockets) == 0:
		raise ValueError("There are no active sockets, and therefore no active processing servers. Quitting.")
	print("Connected servers are now receiving and working on images.")
	pool = ThreadPool(len(active_sockets))
	results = pool.map(sendandreceive, argumentlist)
	return results

def sendandreceive(argumentarray):
	socket = argumentarray[0]
	commrank = argumentarray[1]
	commsize = argumentarray[2]
	qnn = argumentarray[3]
	image_list = copy.copy(argumentarray[4])
	
	# First we send the QNN pickle string.
	qnn_size = len(qnn)
	qnn_size = bin(qnn_size)[2:].zfill(32)
	safe_send(qnn_size, 32, socket)
	safe_send(qnn, int(qnn_size,2), socket)
	
	
	# Select the proper subset of images.
	load_balanced_image_set = len(image_list)/commsize
	
	# If you are last, take the rest, even if it is not exactly even.
	if commrank == commsize-1:
		image_list = image_list[commrank*load_balanced_image_set:]
	else:
		image_list = image_list[commrank*load_balanced_image_set:(commrank+1)*load_balanced_image_set]
	
	# Pickle the image list partition
	image_list_pickled_and_stringed = str(pickle.dumps(image_list))
	
	# Send size of image_list_pickle and the actual pickle to the server.
	filesize = len(image_list_pickled_and_stringed)
	filesize = bin(filesize)[2:].zfill(32) # encode filesize as 32 bit binary
	safe_send(filesize, 32, socket)
	safe_send(image_list_pickled_and_stringed, int(filesize,2), socket)
	
	# Now we wait until we get our darn classifications back.
	classifications_pickle_size = int(safe_receive(32,32,socket), 2)
	
	classifications_pickle_string = safe_receive(classifications_pickle_size, 1024, socket)
	classifications_list_as_ints = pickle.loads(classifications_pickle_string)
	
	# Here sendreceivethreads should end, and we print all classifications.
	socket.close()
	return classifications_list_as_ints
