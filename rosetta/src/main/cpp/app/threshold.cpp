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

#if 0
double walltime() {
  static struct timeval t;
  gettimeofday(&t, NULL);
  return (t.tv_sec + 1e-6 * t.tv_usec);
}

void show(uint64_t *a, const int SIZE) {
    for (uint32_t i = 0; i < SIZE; i++) {
        printf("%" PRIu64 " ", a[i]);
    }
    printf("\n");
}

void fill(uint64_t *a, const int SIZE) {
    for (uint32_t i = 0; i < SIZE; i++) {
        a[i] = rand() % 10;
    }
}

void popcount(uint64_t *a, uint64_t *b, uint64_t *c) {
    for (uint32_t x = 0; x < M; x++) {
        uint64_t count = 0;
        for (uint32_t t = 0; t < T; t++) {
            count += (a[x] >= b[t]);
        }
        c[x] = count;
    }
}

void compare(uint64_t *a, uint64_t *b) {
    int count = 0;
    for (uint32_t x = 0; x < M; x++) {
        count += a[x] == b[x] ? 0 : 1;
    }

    if (count > 0) {
        printf("ERROR! %d element(s) did not match!\n", count);
    } else {
        printf("SUCCESS! %d elements compared!\n", M);
    }
}
#endif

void Run_Thresholder(void *_platform, ThresholdMatrix *matrix, int64_t *input_matrix_ptr,  int64_t *result) {

    auto platform = reinterpret_cast<WrapperRegDriver*>(_platform);
    QBART t(platform);

    const uint32_t num_elems = matrix->num_rows * matrix->num_cols * matrix->num_channels;
    const uint32_t num_elem_bytes = num_elems*sizeof(int64_t);

    const uint32_t num_thresholds = matrix->num_thresholds;
    const uint32_t num_threshold_bytes = num_thresholds*sizeof(int64_t);

    void* write_buffer = platform->allocAccelBuffer(num_elem_bytes);

    platform->copyBufferHostToAccel(input_matrix_ptr, matrix->baseAddr, num_elem_bytes + num_threshold_bytes);

    t.set_baseAddrRead((AccelDblReg) matrix->baseAddr);
    t.set_baseAddrWrite((AccelDblReg) write_buffer);
    t.set_byteCountReader(num_threshold_bytes + num_elem_bytes);
    t.set_byteCountWriter(num_elem_bytes);
    t.set_elemCount(num_elems + num_thresholds);
    t.set_threshCount(num_thresholds);

    t.set_thresh(1);
    t.set_start(1);
    while (t.get_done() != 1);
    t.set_start(0);
    t.set_thresh(0);

    platform->copyBufferAccelToHost(write_buffer, result, num_elem_bytes);
    platform->deallocAccelBuffer(write_buffer);

#if 0
    const int32_t C = matrix->num_channels;
    for (int32_t c = 0; c < C; c++) {
        int32_t M = matrix->num_rows * matrix->num_cols;
        int32_t T = matrix->num_thresholds;

        // DMA components may not work if the total number of bytes in a
        // read/write buffer is not divisible by 64. Using 8-byte words, the DMA
        // buffer must be a multiple of 8 because (N * 8 * 8) mod 64 = 0.
        int reader_size = M + T;
        int writer_size = M;
        int read_byte_count = reader_size * sizeof(int64_t);
        int write_byte_count = writer_size * sizeof(int64_t);


        // TODO: Is this const? Alloc only once..
        void *write_buffer = platform->allocAccelBuffer(write_byte_count);

        // Copy host buffer to DRAM read buffer.
        //platform->copyBufferHostToAccel(input_matrix_ptr, matrix->baseAddr, read_byte_count);
        platform->copyBufferHostToAccel(input_matrix_ptr, matrix->baseAddr, T*sizeof(int64_t));
        platform->copyBufferHostToAccel(input_matrix_ptr+(T+c*M),
            reinterpret_cast<void*>(reinterpret_cast<int64_t>(matrix->baseAddr) + T), M*sizeof(int64_t));

        t.set_baseAddrRead((AccelDblReg) matrix->baseAddr);
        t.set_baseAddrWrite((AccelDblReg) write_buffer);
        t.set_byteCountReader(read_byte_count);
        t.set_byteCountWriter(write_byte_count);
        t.set_elemCount(M + T);
        t.set_threshCount(T);

        t.set_thresh(1);
        t.set_start(1);
        while (t.get_done() != 1);
        t.set_start(0);
        t.set_thresh(0);

        // Copy DRAM write buffer to host receive buffer.
        platform->copyBufferAccelToHost(write_buffer,
            result + M * c, M * sizeof(int64_t));

        platform->deallocAccelBuffer(write_buffer);
#endif

        //////// DEBUG START.

#if 0
        uint64_t expected[M + T] = { 0 };
        popcount(host_src, expected);

        printf("Host buffer: \n"); show(host_src, 0, M + T);
        printf("Thresholds:  \n"); show(host_src, 0, T);
        printf("Matrix:      \n"); show(host_src, T, M);
        printf("Expected:    \n"); show(expected, 0, M);
        printf("Result:      \n"); show(host_rec, 0, M);

        compare(expected, host_rec);
#endif

        //////// DEBUG END.
    //}
}
