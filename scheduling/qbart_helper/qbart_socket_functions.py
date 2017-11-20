"""
PURPOSE:
Sends a set amount of data over an already active socket connection.

ARGUMENTS:
string_to_send:
Sending strings is the easiest, so we send everything as a string (use pickle.dumps
for objects)

bits_to_send:
The amount of bits to send.

connected_socket:
The already established TCP-socket to send the contents over.

"""
def safe_send(string_to_send, bytes_to_send, connected_socket):
	total_sent = 0
	while bytes_to_send > 0:
		sent = connected_socket.send(string_to_send[total_sent:])
		total_sent += sent
		bytes_to_send -= sent

"""
PURPOSE:
Receives a given amount of data over an already active socket connection.

ARGUMENTS:
bits_to_receive:
The total amount of bits we are to receive.

chunk_size:
The max size of each data chunk to receive in bits. Note that TCP doesn't necessarily deliver
chunk_size bits every time, and it is the application's responsibility to check that the total amount is received.

socket:
The already active socket that we are receiving the data over.
"""
def safe_receive(bytes_to_receive, chunk_size, socket):
	received_chunks = []
	
	while (bytes_to_receive > 0):
		if bytes_to_receive < chunk_size:
			chunk_size = bytes_to_receive
		
		the_chunk = socket.recv(bytes_to_receive)
		received_chunks.append(the_chunk)
		bytes_to_receive -= len(the_chunk)
	the_data = "".join(received_chunks)
	return the_data
