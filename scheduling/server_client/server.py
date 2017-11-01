import socket
import threading
import os
from time import sleep
from QNN import *
from qbart_helper import *

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
        
        # We receive the QNN and save it to the store
        print("Ready to receive QNN")
        qnn_size = c.recv(32)
        print(qnn_size)
        qnn_size = int(qnn_size, 2) # TODO: Find out what happens here as well.
        print(qnn_size)
        qnn_to_write = open("server_store/qnn.pickle", 'wb')
        chunksize = 4096
        while qnn_size > 0:
            if qnn_size < chunksize:
                chunksize = qnn_size
            data = c.recv(chunksize)
            qnn_to_write.write(data)
            qnn_size -= chunksize
        qnn_to_write.close()
        
        
        # First we receive the filename
        print("Ready to receive filename of image")
        size = c.recv(32)
        print(size)
        size = int(size, 2)
        filename = c.recv(size)
        filesize = c.recv(32)
        filesize = int(filesize, 2)
        
        print("Ready to receive image file")
        file_to_write = open("test_images/thefile.jpg", 'wb')
        chunksize = 4096
        while filesize > 0:
            if filesize < chunksize:
                chunksize = filesize
            data = c.recv(chunksize)
            file_to_write.write(data)
            filesize -= chunksize
        file_to_write.close()

        # We have received a QNN and an image, now we classify.
        # TODO: There is a very strange bug here. Using the libraries from the mvp notebook, it stops at image loading because jpgs are not supported. However, this works fine in the mvp notebook even though there are jpegs there too. The classification we should have is 50km/h, not "Priority road". Grr.
        theQNN = load_qnn("server_store/qnn.pickle")
        theImage = load_images("test_images",1,32,32,"Crc","BGR")
        classification = qbart_execute(theQNN, theImage, ['20 Km/h', '30 Km/h', '50 Km/h', '60 Km/h', '70 Km/h', '80 Km/h', 'End 80 Km/h', '100 Km/h', '120 Km/h', 'No overtaking', 'No overtaking for large trucks', 'Priority crossroad', 'Priority road', 'Give way', 'Stop', 'No vehicles', 'Prohibited for vehicles with a permitted gross weight over 3.5t including their trailers, and for tractors except passenger cars and buses', 'No entry for vehicular traffic', 'Danger Ahead', 'Bend to left', 'Bend to right', 'Double bend (first to left)', 'Uneven road', 'Road slippery when wet or dirty', 'Road narrows (right)', 'Road works', 'Traffic signals', 'Pedestrians in road ahead', 'Children crossing ahead', 'Bicycles prohibited', 'Risk of snow or ice', 'Wild animals', 'End of all speed and overtaking restrictions', 'Turn right ahead', 'Turn left ahead', 'Ahead only', 'Ahead or right only', 'Ahead or left only', 'Pass by on right', 'Pass by on left', 'Roundabout', 'End of no-overtaking zone', 'End of no-overtaking zone for vehicles with a permitted gross weight over 3.5t including their trailers, and for tractors except passenger cars and buses'])

        print(classification)
    s.close()

classification_server()
