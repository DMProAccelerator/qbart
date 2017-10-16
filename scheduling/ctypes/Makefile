CC=gcc
CFLAGS=-std=c99 -fPIC -shared -Wall -O3

.PHONY : clean

all : libutil1.so libutil2.so

libutil1.so : util1.o
	$(CC) -o $@ $^ $(CFLAGS)

libutil2.so : util2.o
	$(CC) -o $@ $^ $(CFLAGS)

%.o : %.c
	$(CC) -c -o $@ $< $(CFLAGS)

clean :
	rm -f *.o *.so && rm -rf __pycache__
