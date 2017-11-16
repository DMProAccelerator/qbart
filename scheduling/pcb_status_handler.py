"""
PURPOSE:

Handles communication that is supposed to go to PCB. Should be run as a separate thread in client.py .
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


A reset message is given by 1xxx xxxx, meaning that we just really care about that first bit, and nothing else.

"""

def pcb_status_messenger(server_table)
	PORT
