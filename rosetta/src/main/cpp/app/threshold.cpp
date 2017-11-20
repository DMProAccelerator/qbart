#include <iostream>
#include <cstdint>

#include <string.h>
#include <inttypes.h>
#include <math.h>
#include <sys/time.h>
#include "platform.h"

#include "threshold.hpp"
#include "QBART.hpp"

using namespace std;

// M: Matrix elements, T: Threshold elements.
// #define M 1000
// #define T 3

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

void Run_Thresholder(WrapperRegDriver *platform, ThresholdMatrix *matrix,
    ThresholdResultMatrix *matrix_result, int64_t *result) {
    QBART t(platform);

    cout << "Signature: " << hex << t.get_signature() << dec << endl;

    int32_t C = matrix->channels;

    for (int c = 0; c < C; c++) {
        int32_t M = matrix->num_rows * matrix->num_cols;
        // int32_t T = matrix->num_thresholds;

        // DMA components may not work if the total number of bytes in a
        // read/write buffer is not divisible by 64. Using 8-byte words, the DMA
        // buffer must be a multiple of 8 because (N * 8 * 8) mod 64 = 0.
        int reader_size = ceil((float) (M + T) / 8) * 8;
        int writer_size = ceil((float) M / 8) * 8;
        int read_byte_count = reader_size * sizeof(uint64_t);
        int write_byte_count = writer_size * sizeof(uint64_t);

        void *read_buffer = platform->allocAccelBuffer(read_byte_count);
        void *write_buffer = platform->allocAccelBuffer(write_byte_count);

        uint64_t host_src[reader_size] = { 0 };
        uint64_t host_rec[writer_size] = { 0 };

        // Copy host buffer to DRAM read buffer.
        platform->copyBufferHostToAccel(matrix->baseAddr, read_buffer,
            read_byte_count);

        t.set_baseAddrRead((AccelDblReg) read_buffer);
        t.set_baseAddrWrite((AccelDblReg) write_buffer);
        t.set_byteCountReader(read_byte_count);
        t.set_byteCountWriter(write_byte_count);
        t.set_elemCount(M + T);
        t.set_threshCount(T);

        t.set_start(1);
        while (t.get_finished() != 1);
        t.set_start(0);

        // Copy DRAM write buffer to host receive buffer.
        platform->copyBufferAccelToHost(write_buffer,
            result + M * c, M * sizeof(uint64_t));

        platform->deallocAccelBuffer(read_buffer);
        platform->deallocAccelBuffer(write_buffer);

        //////// DEBUG START.

        uint64_t expected[M + T] = { 0 };
        popcount(host_src, expected);

        printf("Host buffer: \n"); show(host_src, 0, M + T);
        printf("Thresholds:  \n"); show(host_src, 0, T);
        printf("Matrix:      \n"); show(host_src, T, M);
        printf("Expected:    \n"); show(expected, 0, M);
        printf("Result:      \n"); show(host_rec, 0, M);

        compare(expected, host_rec);

        //////// DEBUG END.
    }
}
