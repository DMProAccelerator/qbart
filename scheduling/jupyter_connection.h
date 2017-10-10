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
typedef int file_descriptor;

/* setup_socket
 *
 * Creates a socket in the file system, with name socket_path. Starts listening
 * for connections, effectively making it the server in the client-server
 * model.
 *
 * Input:
 *    socket_path (relative path to socket)
 *    local_socket (will be initialized by the function)
 * Return value:
 *    File descriptor to connection socket (used when accepting connecitons)
 */
file_descriptor setup_socket(char* socket_path, struct sockaddr_un* local_socket);

/* destroy_socket
 *
 * Unlinks the socket in socket_path, and closes the connection_socket
 */
void destroy_socket(char* socket_path, file_descriptor connection_socket);
