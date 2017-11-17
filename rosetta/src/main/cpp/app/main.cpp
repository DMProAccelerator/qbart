#include <iostream>
#include <string.h>
#include <stdint.h>
#include <inttypes.h>
#include <math.h>
#include <sys/time.h>

#include "TestDMAThresholder.hpp"
#include "platform.h"

using namespace std;

// M: Matrix elements, T: Threshold elements.
#define M 16
#define T 3

double walltime() {
  static struct timeval t;
  gettimeofday(&t, NULL);
  return (t.tv_sec + 1e-6 * t.tv_usec);
}

void show(uint64_t *a, const int SIZE) {
    for (int i = 0; i < SIZE; i++) {
        printf("%" PRIu64 " ", a[i]);
    }
    printf("\n");
}

void fill(uint64_t *a, const int SIZE) {
    for (int i = 0; i < SIZE; i++) {
        a[i] = rand() % 10;
    }
}

void popcount(uint64_t *a, uint64_t *b, uint64_t *c) {
    for (int x = 0; x < M; x++) {
        uint64_t count = 0;
        for (int t = 0; t < T; t++) {
            count += (a[x] >= b[t]);
        }
        c[x] = count;
    }
}

void compare(uint64_t *a, uint64_t *b) {
    int count = 0;
    for (int x = 0; x < M; x++) {
        count += a[x] == b[x] ? 0 : 1;
    }

    if (count > 0) {
        printf("ERROR! %d element(s) did not match!\n", count);
    } else {
        printf("SUCCESS! %d elements compared!\n", M);
    }
}

void Run_TestDMAThresholder(WrapperRegDriver *platform) {
    TestDMAThresholder t(platform);

    cout << "Signature: " << hex << t.get_signature() << dec << endl;

    int ub = ceil((float) (M + T) / 8) * 8;
    int buffer_size = ub * sizeof(uint64_t);

    printf("%d %d\n", M + T , ub);

    uint64_t *host_buffer = (uint64_t *) calloc(ub, sizeof(uint64_t));
    uint64_t *receive_buffer = (uint64_t *) calloc(ub, sizeof(uint64_t));
    uint64_t *matrix = (uint64_t *) calloc(M, sizeof(uint64_t));
    uint64_t *thresholds = (uint64_t *) calloc(T, sizeof(uint64_t));
    uint64_t *expected = (uint64_t *) calloc(M, sizeof(uint64_t));

    void *read_buffer = platform->allocAccelBuffer(buffer_size);
    void *write_buffer = platform->allocAccelBuffer(buffer_size);

    fill(thresholds, T);
    fill(matrix, M);

    memcpy(host_buffer, thresholds, T * sizeof(uint64_t));
    memcpy(host_buffer + T, matrix, M * sizeof(uint64_t));

    // Copy host buffer to DRAM read buffer.
    platform->copyBufferHostToAccel(host_buffer, read_buffer, buffer_size);

    t.set_baseAddrRead((AccelDblReg) read_buffer);
    t.set_baseAddrWrite((AccelDblReg) write_buffer);
    t.set_byteCount(buffer_size);
    t.set_elemCount(T + M);
    t.set_threshCount(T);

    double start, end;

    start = walltime();
    popcount(matrix, thresholds, expected);
    end = walltime();

    printf("CPU: %lf\n", end - start);

    start = walltime();
    t.set_start(1);
    while (t.get_finished() != 1);
    t.set_start(0);
    end = walltime();

    printf("FPGA: %lf\n", end - start);

    // Copy DRAM write buffer to host receive buffer.
    platform->copyBufferAccelToHost(write_buffer, receive_buffer, buffer_size);

    printf("\n");
    printf("Matrix: \n"); show(matrix, M);
    printf("Thresholds: \n"); show(thresholds, T);
    printf("Expected: \n"); show(expected, M);
    printf("Received: \n"); show(receive_buffer, M);
    printf("\n");
    compare(expected, receive_buffer);
    float cc = t.get_cc();
    printf("CC: %.2f CC/Word: %.2f\n", cc, cc / ub);

    free(host_buffer);
    platform->deallocAccelBuffer(read_buffer);

}

int main()
{
    WrapperRegDriver *platform = initPlatform();

    Run_TestDMAThresholder(platform);

    deinitPlatform(platform);

    return 0;
}
