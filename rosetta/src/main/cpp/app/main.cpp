#include <iostream>
#include <string.h>
#include <stdint.h>
#include <inttypes.h>
#include <math.h>
#include <sys/time.h>
#include <algorithm>

#include "TestDMAThresholder.hpp"
#include "platform.h"

using namespace std;

// M: Matrix elements, T: Threshold elements.
#define M 1000
#define T 20

double walltime() {
  static struct timeval t;
  gettimeofday(&t, NULL);
  return (t.tv_sec + 1e-6 * t.tv_usec);
}

void show(int64_t *a, const int SIZE) {
    for (int i = 0; i < SIZE; i++) {
        printf("%" PRIu64 " ", a[i]);
    }
    printf("\n");
}

void fill(int64_t *a, const int SIZE) {
    for (int i = 0; i < SIZE; i++) {
        a[i] = rand() % 10;
    }
}

void popcount(int64_t *a, int64_t *b, int64_t *c) {
    for (int x = 0; x < M; x++) {
        int64_t count = 0;
        for (int t = 0; t < T; t++) {
            count += (a[x] >= b[t]);
        }
        c[x] = count;
    }
}

void compare(int64_t *a, int64_t *b) {
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

    // Must be a multiple of 16.
    int ub = ceil((float) (M + T) / 16) * 16;
    int buffer_size = ub * sizeof(int64_t);

    int64_t *host_buffer = (int64_t *) calloc(ub, sizeof(int64_t));
    int64_t *receive_buffer = (int64_t *) calloc(ub, sizeof(int64_t));
    int64_t *matrix = (int64_t *) calloc(M, sizeof(int64_t));
    int64_t *thresholds = (int64_t *) calloc(T, sizeof(int64_t));
    int64_t *expected = (int64_t *) calloc(M, sizeof(int64_t));

    void *read_buffer = platform->allocAccelBuffer(buffer_size);
    void *write_buffer = platform->allocAccelBuffer(buffer_size);

    fill(thresholds, T);
    sort(thresholds, thresholds + T);

    fill(matrix, M);

    memcpy(host_buffer, thresholds, T * sizeof(int64_t));
    memcpy(host_buffer + T, matrix, M * sizeof(int64_t));

    // Copy host buffer to DRAM read buffer.
    platform->copyBufferHostToAccel(host_buffer, read_buffer, buffer_size);

    double start, end;

    start = walltime();
    popcount(matrix, thresholds, expected);
    end = walltime();
    printf("CPU: %lf\n", end - start);

    t.set_baseAddrRead((AccelDblReg) read_buffer);
    t.set_baseAddrWrite((AccelDblReg) write_buffer);
    t.set_byteCount(buffer_size);
    t.set_elemCount(T + M);
    t.set_threshCount(T);
    t.set_start(1);

    start = walltime();
    while (t.get_finished() != 1){
        printf("State %d\n", t.get_state_out());
    }
    end = walltime();

    printf("FPGA: %lf\n", end - start);

    // Copy DRAM write buffer to host receive buffer.
    platform->copyBufferAccelToHost(write_buffer, receive_buffer, buffer_size);

    compare(expected, receive_buffer);

    float cc = t.get_cc();
    printf("CC: %.2f CC/Word: %.2f\n", cc, cc / ub);

    free(host_buffer);
    platform->deallocAccelBuffer(read_buffer);

    t.set_start(0);
}

int main()
{
    srand(2367845);
    WrapperRegDriver *platform = initPlatform();

    Run_TestDMAThresholder(platform);

    deinitPlatform(platform);

    return 0;
}
