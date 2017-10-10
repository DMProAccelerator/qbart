#include "jupyter_connection.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>


socket_descriptor setup_socket(char* socket_path, struct sockaddr_un* local_addr) {

  /* remove socket, in case it still exists (unexpected exit at last run) */
  unlink(socket_path);

  /* create socket */
  socket_descriptor connection_socket = socket(AF_UNIX, SOCK_STREAM, 0);
  if (connection_socket == -1) {
    perror("socket");
    goto error;
  }

  /* create and clear socket structure (for portability) */
  memset(local_addr, 0, sizeof(struct sockaddr_un));

  /* Bind socket to socket name */
  local_addr->sun_family = AF_UNIX;
  snprintf(local_addr->sun_path, strlen(socket_path)+1, "%s", socket_path);

  /* bind connection socket to address in Unix domain */
  if(bind(connection_socket, (struct sockaddr*)local_addr, sizeof(struct sockaddr_un)) == -1) {
    perror("bind");
    goto error;
  }

  /* setup listening for new connections */
  if (listen(connection_socket, NUM_ALLOWED_CONNECTIONS) == -1) {
    perror("listen");
    goto error;
  }


  return connection_socket;
error:
  destroy_socket(socket_path, connection_socket);
  exit(1);
}

void destroy_socket(char* socket_path, socket_descriptor connection_socket) {
  if (connection_socket > -1) {
    close(connection_socket);
  }
  unlink(socket_path);
}

socket_descriptor accept_connection(socket_descriptor connection_socket, struct sockaddr_un* remote_addr) {
  socklen_t size = sizeof(struct sockaddr_un);
  socket_descriptor conn = accept(connection_socket, (struct sockaddr *)remote_addr, &size);
  if (conn == -1) {
    perror("accept");
    goto error;
  }

  return conn;

error:
  // TODO(okpedersen): Find a better way to handle this
  return -1;
}
