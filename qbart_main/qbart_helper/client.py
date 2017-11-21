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

sys.path.append("/home/xilinx/rosetta/rosetta")
from cffi_run import uart_send_message
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
	
	# PCB Message protocol specific. Should be encapsulated in separate method for clarity, but due to time it has to be messy for now.
	server_progress_levels = [("0000", 0), ("0001", 0.06), ("0010", 0.13), ("0011", 0.20), ("0100", 0.26), ("0101", 0.33), ("0110", 0.4), ("0111", 0.46), ("1000", 0.53), ("1001", 0.60), ("1010", 0.66), ("1011", 0.73), ("1100", 0.8), ("1101", 0.86), ("1110", 0.93), ("1111", 1.00)]
	
	# We try to connect to each specified server through a separate socket.
	for ip_port in server_list:
		try:
			active_sockets.append(socket.create_connection(ip_port, timeout=100))
			
			# We use the previous length of active_sockets as server_id. The second element in the value indicates percentage status
			# If we are localhost.. QUICKFIX
			if ip_port[0] == 'localhost' or ip_port[0] == '':
				server_id_table['127.0.0.1'] = (bin(len(active_sockets)-1)[2:].zfill(3), 0, copy.copy(server_progress_levels))
			else:
				server_id_table[ip_port[0]] = (bin(len(active_sockets)-1)[2:].zfill(3), 0, copy.copy(server_progress_levels))
			
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
0000	0/15
0001	1/15
0010	2/15
0011	3/15
0100	4/15
0101	5/15
0110	6/15
0111	7/15
1000	8/15
1001	9/15
1010	10/15
1011	11/15
1100	12/15
1101	13/15
1110	14/15
1111	15/15

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
		# SEND RESET MESSAGE TO THE PCB.
		uart_send_message(int("0b10000000",2))
		
		# THEN SEND MESSAGES TO PCB INFORMING OF PERCENTAGE 0 FOR ALL SERVERS
		for key in self.server_table.keys():
			uart_send_message(int((str('0b') + str(0) + str(self.server_table[key][0]) + str(0000)),2))
		
		print(self.exit.is_set())
		while (not self.exit.is_set()):
			new_message = self.message_queue.get()
			if new_message is not None:
				# Looking up server ID and server progress
				table_entry = self.server_table[new_message[0]]
				server_id = table_entry[0]
				server_recorded_progress = table_entry[1]
				server_available_progress_reports = table_entry[2]
				
				
				# Now we must find out the current progress. Note that it can jump from 0% to for example 50%.
				# We only want to send one message with the highest progress value.
				# Element 1 and 2 in new_message holds the currently finished image, and the total number of images.
				new_progress = float(new_message[1]) / float(new_message[2])
				
				# Now get the greatest index where the new_progress was larger than what was in the table.
				largest_index = -1
				
				# Each element in server_available_progress_report is a tuple, (message_bin_code, progress value it represents.)
				for i in range(len(server_available_progress_reports)):
					if new_progress >= server_available_progress_reports[i][1]:
						largest_index = i
				
				# If the new progress actually was larger than one than we wish to report, we pass it along to the PCB, and overwrite with a new available progress report.
				if (largest_index != -1):
					progress_to_report = server_available_progress_reports[largest_index][0]

					message_to_pcb = "0b" + str(0) + str(server_id) + str(progress_to_report)
					message_to_pcb = int(message_to_pcb, 2)
					self.server_table[new_message[0]] = (server_id, server_recorded_progress, server_available_progress_reports[largest_index+1:])
					
					# Now we call cffi and send the byte over UART.
					uart_send_message(message_to_pcb)
	
	def terminate(self):
		print("server_status_msg_handler terminating...")
		self.exit.set()

def sendandreceive(argumentarray):
	socket = argumentarray[0]
	commrank = argumentarray[1]
	commsize = argumentarray[2]
	qnn = argumentarray[3]
	image_list = copy.copy(argumentarray[4])
	the_message_queue = argumentarray[5]
	
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
	
	# Now we wait until we get our dear classifications back. We can at this time
	# be receiving STAT messages, which are status messages from the servers
	# theat should be written to the PCB.
	
	message_status =  safe_receive(4, 4, socket)
	
	while message_status != "FILE":
		if message_status == "STAT":
			filesize = safe_receive(32, 32, socket)
			pickled_message = safe_receive(int(filesize,2), 32, socket)
			the_message = pickle.loads(pickled_message)
			print("Im now putting shit in the message queue. Specifically this msg:", the_message)
			the_message_queue.put([socket.getpeername()[0], the_message[0], the_message[1]])
			print("Now there should be a message on the message queue.")
			
		else:
			raise ValueError("UHOH! We received a message that doesnt follow protocol.")
		
		message_status = safe_receive(4, 4, socket)
	
	classifications_pickle_size = int(safe_receive(32,32,socket), 2)
	
	classifications_pickle_string = safe_receive(classifications_pickle_size, 1024, socket)
	classifications_list_as_ints = pickle.loads(classifications_pickle_string)
	
	# Here sendreceivethreads should end, and we print all classifications.
	socket.close()
	return classifications_list_as_ints
