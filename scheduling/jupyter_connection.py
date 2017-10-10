import os
import sys
import socket

def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def connect_to_server(path_name):
    """
    Connect to socket at 'path_name'
    Return client object
    """

    if not os.path.exists(path_name):
        eprint("Socket doesn't exist")
        sys.exit(1)

    client = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    client.connect(path_name)

    return client



