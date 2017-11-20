#!/usr/bin/env python2
from multiprocessing.dummy import Pool as ThreadPool
import multiprocessing
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
"""
def classification_client(qnn_pickle_string, image_list, server_list):
	# First we check to see if servers are up and running.
	#for server in server_name_ip_port_tuples:
	BUFFER_SIZE = 4096
	
	# Used to keep track of active server connections
	active_sockets = []
	
	# Is passed to server status message handler in order to uniquely identify servers.
	server_id_table = {}
	
	# We try to connect to each specified server through a separate socket.
	for ip_port in server_list:
		try:
			active_sockets.append(socket.create_connection(ip_port, timeout=5))
			
			# We use the current length of active_sockets as server_id, meaning that we 1-index.
			server_id_table[ip_port[0]] = len(active_sockets)
			
			print("Successfully connected to " + str(ip_port[0]))
		except:
			# If we can't connect, we cannot use this socket later in the program.
			print("Could not connect to " + str(ip_port[0]))
	
	# We establish a separate multiprocessing thread for server status message handling
	status_msg_queue = multiprocessing.Queue()
	status_handler_process = server_status_msg_handler(server_id_table, status_msg_queue)
	status_handler_process.start()
	
	# The argumentlist is used to pass arguments to sendandreceive with thread pool mapping. A bit hacky, but a bit cleaner than the "proper" approach.
	argumentlist = []
	
	# For every socket we add arguments. We also set the socket timeout to Null, so it doesn't time out.
	# A future improvement would be to have timeouts, but have the server send isalive messages to client from time to time in case the work set is large.
	for socketnum in range(len(active_sockets)):
		active_sockets[socketnum].settimeout(None)
		argumentlist.append([active_sockets[socketnum], socketnum, len(active_sockets), qnn_pickle_string, image_list, status_msg_queue])
	
	if len(active_sockets) == 0:
		raise ValueError("There are no active sockets, and therefore no active processing servers. Quitting.")
	pool = ThreadPool(len(active_sockets))
	results = pool.map(sendandreceive, argumentlist)
	
	# Now we are all done with computation, so we terminate server status message receiving.
	status_handler_process.terminate()
	
	# And finally we return the results to the notebook.
	return results


"""
SERVER_STATUS_MSG_HANDLER
PURPOSE:

Handles communication that is supposed to go to PCB. Should be run as a part of multiprocessing in client.py .
Is another TCP Server in the QBART-system (this probably could have been made cleaner by having a message handler before client.py and this to sort messages between the two, and thus
using a single TCP-connection, but hey, this works as a second, modular, and a bit less hacky quickfix. For future improvement, one could expand this message protocol to differentiate
between STATUS_UPDATE and FILE_SENDING, and then pass status updates here, and file sending to the relevant client thread, or similar.

Upon creation it must send a RESET message to the PCB through a system call to a UART module, and then listen for incoming server connections.
It must enable the PCB to uniquely identify servers and progress.

The "protocol" for now for message sending to the PCB is:
1 byte per message.

X		XXX		XXXX
Status		ServerID	Progress in percent 

For progress: (we only have room for multiples of 10%, it shouldn't be much difficulty to expand to single percentages, but minimal bandwidth was wanted).

STATUS:
0 Update on server progress (used both for server discovery and for updating percentage progress)
1 RESET - Clear all status tables

ServerID: Represented in its binary form, with three bits we can support up to 8 servers. The program shouldn't crash, but you won't get status on any more servers. I.e. not a critical error.

Progress in percent:
0000	0%
0001	10%
0010	20%
0011	30%
0100	40%
0101	50%
0110	60%
0111	70%
1000	80%
1001	90%
1010	95%
1011	96%
1100	97%
1101	98%
1110	99%
1111	100%

When progress is at 100%, the server implicitly is done.

A reset message is given by 1xxx xxxx, meaning that we just really care about that first bit, and nothing else.

"""
class server_status_msg_handler(multiprocessing.Process):
	
	def __init__(self, server_table, message_queue):
		multiprocessing.Process.__init__(self)
		self.exit = multiprocessing.Event()
		self.server_table = server_table
		self.message_queue = message_queue
		print("Server status message handler initiated.")
	
		
	def run(self):
		print("Running server status message handler")
		
		while not self.exit.is_set():
			
			new_message = self.message_queue.get()
			
			if new_message is not None:
				# Construct something for the PCB to display...
				# TODO: Construct the message
				
				#################
				# TODO: INSERT CFFI CALL TO SEND PCB MESSAGE HERE.
				# What does it expect as input?
				# We just print the message for the time being...
				print(new_message)
				#################
		
	def terminate(self):
		print("server_status_msg_handler terminating...")
		self.exit.set()

def sendandreceive(argumentarray):
	socket = argumentarray[0]
	commrank = argumentarray[1]
	commsize = argumentarray[2]
	qnn = argumentarray[3]
	image_list = copy.copy(argumentarray[4])
	message_queue = argumentarray[5]
	
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
	
	# Now we wait until we get our darn classifications back. We can at this time
	# be receiving STAT messages, which are status messages from the servers
	# that should be written to the PCB.
	
	message_status =  safe_receive(4, 4, socket)
	
	while message_status != "FILE":
		if message_status == "STAT":
			filesize = safe_receive(32, 32, socket)
			pickled_message = safe_receive(int(filesize,2), 32, socket)
			the_message = pickle.loads(pickled_message)
			message_queue.put(str(socket.getpeername()[0]) + "," + str(the_message[0]) + "," + str(the_message[1]))
		else:
			raise ValueError("UHOH! We received a message that doesnt follow protocol.")
		
		message_status = safe_receive(4, 4, socket)
	
	classifications_pickle_size = int(safe_receive(32,32,socket), 2)
	
	classifications_pickle_string = safe_receive(classifications_pickle_size, 1024, socket)
	classifications_list_as_ints = pickle.loads(classifications_pickle_string)
	
	# Here sendreceivethreads should end, and we print all classifications.
	socket.close()
	return classifications_list_as_ints
