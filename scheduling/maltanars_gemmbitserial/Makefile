CC=g++
CFLAGS= -fPIC -shared -W

.PHONY : clean

all : libutil1.so

libutil1.so : gemmbitserial_pywrapper.o
	$(CC) -o $@ $^ $(CFLAGS)

%.o : %.cpp
	$(CC) -c -o $@ $< $(CFLAGS)

clean :
	rm -f *.o *.so && rm -rf __pycache__
