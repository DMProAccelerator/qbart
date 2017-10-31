import socket
import threading
from time import sleep

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
"""
def classification_server():
    # Some credit is due to: http://www.bogotobogo.com/python/python_network_programming_server_client_file_transfer.php
    #for server in server_name_ip_port_tuples:
    TCP_IP = 'localhost'
    TCP_PORT = 9001
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
        c, addr = s.accept()
        print("This is Ground Control. We read you Major Tom!")
        
        # First we receive the filename
        size = c.recv(16)
        size = int(size, 2)
        filename = c.recv(size)
        filesize = c.recv(32)
        filesize = int(filesize, 2)

        file_to_write = open("theFile.jpg", 'wb')
        chunksize = 4096
        while filesize > 0:
            if filesize < chunksize:
                chunksize = filesize
            data = c.recv(chunksize)
            file_to_write.write(data)
            filesize -= chunksize
        file_to_write.close()
    s.close()

classification_server()
