def safe_send(string_to_send, bytes_to_send, connected_socket):
	total_sent = 0
	while bytes_to_send > 0:
		sent = connected_socket.send(string_to_send[total_sent:])
		total_sent += sent
		bytes_to_send -= sent

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
