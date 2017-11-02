import socket
import threading
from time import sleep
import sys
import cPickle as pickle
"""
The client has a QNN pickle and a set of images that it wants to send
to one or several servers who will receive a copy of the QNN, and a subset each of the images.

The servers will perform the QNN execution, and return the classifications.
It is important that there is a mapping between the image and the classification. Either the image title and the inference
must be sent together, or one must keep the original ordering.

This entire setup depends on the server to have access to the same code base as master (such as the QBART Execution pyscript),
and one must manually configure ip-addresses and ports beforehand.

It is also important to check if a certain server is available before we start, so we ping each of them to see which ones are available.

This function should be run as a separate thread, so that the client can also do some computation while it is waiting. This function
will also spawn a thread for each server connection.
"""
def classification_client(qnn_pickle, image_list, server_name_ip_port_tuples):
    # First we check to see if servers are up and running.
    # Some credit is due to: http://www.bogotobogo.com/python/python_network_programming_server_client_file_transfer.php
    #for server in server_name_ip_port_tuples:
    TCP_IP = 'localhost'
    TCP_PORT = 9004
    BUFFER_SIZE = 1024
    
    active_sockets = []

    # We create a new socket for the new server connection
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    
    # We try to connect to the specified IP and PORT through our new socket.
    # If we successfully connect, we add this to the list of open connections.
    try:
        s.connect((TCP_IP, TCP_PORT))
        active_sockets.append(s)
    except:
        # If we can't connect, we cannot use this socket later in the program.
        print("Could not connect!")
        s.close()
    
    # First we send the QNN pickle.
    qnn_size = sys.getsizeof(qnn_pickle)
    print(qnn_size)
    qnn_size = bin(qnn_size)[2:].zfill(32)
    print(qnn_size)
    s.send(qnn_size)
    s.sendall(qnn_pickle)

    # Alright, so now we have active sockets, let's send 'em files.
    image_list_pickled = pickle.dumps(image_list)
    
    # Send size of image_list_pickle and the actual pickle to the server.
    filesize = sys.getsizeof(image_list_pickled)
    filesize = bin(filesize)[2:].zfill(32) # encode filesize as 32 bit binary
    s.send(filesize)
    s.sendall(image_list_pickled)
    print("The image list has been sent")
    
    # Now we wait until we get our darn classifications back.
    classifications_pickle_size = s.recv(32)
    classifications_pickle_size = int(classifications_pickle_size, 2)
    
    classifications_pickle_chunks = []
    chunksize = 4096
    while classifications_pickle_size > 0:
	if classifications_pickle_size < chunksize:
		chunksize = classifications_pickle_size
	classifications_pickle_chunks.append(s.recv(chunksize))
	classifications_pickle_size -= chunksize
    
    classifications_pickle = "".join(classifications_pickle_chunks)
    classifications_list_as_ints = pickle.loads(classifications_pickle)
    
    s.close()

    return classifications_list_as_ints

