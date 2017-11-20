import multiprocessing
import pickle
from qbart_socket_functions import *
import socket

"""
Is a part of the server.py server processing.
The other part in this is qbart_execute. qbart_execute executes images, and then places in the status queue which message it has finished.
We pass this information back to the client, and it is up to the client whether to display this information as a percentage, actual number, or whatever.

The most important thing is that the user gets feedback that something is actually happening at the servers.
"""
class status_message_sender(multiprocessing.Process):
	def __init__(self, total_image_number, status_queue, client_sock):
		multiprocessing.Process.__init__(self)
		self.total_image_number = total_image_number
		self.status_queue = status_queue
		self.client_sock = client_sock
		self.exit = multiprocessing.Event()
	
	def run(self):
		# Continuously send new status messages as they come along.
		while (not self.exit.is_set()):
			new_status = self.status_queue.get()
			
			# If there actually is a new status...
			if new_status is not None:
				# Pickle the message
				the_message = (new_status, self.total_image_number)
				the_message_pickled = pickle.dumps(the_message)
				
				# Send message type to client.
				safe_send("STAT", 4, self.client_sock)
				
				# Send first the length of the pickle to the message handler on the other side.
				pickle_size = len(the_message_pickled)
				pickle_size = bin(pickle_size)[2:].zfill(32)
				safe_send(pickle_size, 32, self.client_sock)
				
				# Then send the actual pickle
				safe_send(the_message_pickled, int(pickle_size,2), self.client_sock)
				self.status_queue.task_done()
	def terminate(self):
		self.exit.set()
