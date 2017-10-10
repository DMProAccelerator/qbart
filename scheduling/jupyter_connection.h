#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>


/* Number of connections allowed in socket queue */
#define NUM_ALLOWED_CONNECTIONS 4

/* typedefs for readability */
typedef int socket_descriptor;

/* setup_socket
 *
 * Creates a socket in the file system, with name socket_path. Starts listening
 * for connections, effectively making it the server in the client-server
 * model.
 *
 * Input:
 *    socket_path (relative path to socket)
 *    local_addr (will be initialized by the function)
 * Return value:
 *    Socket descriptor to connection socket (used when accepting connecitons)
 */
socket_descriptor setup_socket(char* socket_path, struct sockaddr_un* local_addr);

/* destroy_socket
 *
 * Unlinks the socket in socket_path, and closes the connection_socket
 */
void destroy_socket(char* socket_path, socket_descriptor connection_socket);

/* accept_connection
 *
 * Wait for incoming connection and return  new socket descriptor.
 * NOTE: Blocks untill new client is connected.
 *
 * Input:
 *    connection_socket (socket to accept from)
 *    remote_addr (will be initialized by the function)
 * Return value:
 *    Socket descriptor to new socket connected to client
 */
socket_descriptor accept_connection(socket_descriptor connection_socket, struct sockaddr_un* remote_addr);
