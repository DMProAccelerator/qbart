#include "jupyter_connection.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>


file_descriptor setup_socket(char* socket_path, struct sockaddr_un* local_socket) {

  /* remove socket, in case it still exists (unexpected exit at last run) */
  unlink(socket_path);

  /* create socket */
  file_descriptor connection_socket = socket(AF_UNIX, SOCK_STREAM, 0);
  if (connection_socket == -1) {
    perror("socket");
    goto error;
  }

  /* create and clear socket structure (for portability) */
  memset(local_socket, 0, sizeof(struct sockaddr_un));

  /* Bind socket to socket name */
  local_socket->sun_family = AF_UNIX;
  snprintf(local_socket->sun_path, strlen(socket_path)+1, "%s", socket_path);

  /* bind connection socket to address in Unix domain */
  if(bind(connection_socket, (struct sockaddr*)local_socket, sizeof(struct sockaddr_un)) == -1) {
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

void destroy_socket(char* socket_path, file_descriptor connection_socket) {
  if (connection_socket > -1) {
    close(connection_socket);
  }
  unlink(socket_path);
}

file_descriptor accept_connection(file_descriptor connection_socket, struct sockaddr_un* remote_socket) {
  socklen_t size = sizeof(struct sockaddr_un);
  file_descriptor conn = accept(connection_socket, (struct sockaddr *)remote_socket, &size);
  if (conn == -1) {
    perror("accept");
    goto error;
  }

  return conn;

error:
  // TODO(okpedersen): Find a better way to handle this
  return -1;
}
