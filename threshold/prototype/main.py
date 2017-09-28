
from threshold import threshold, set_thresholds
from PIL import Image
import numpy as np
import matplotlib.pyplot as plt
from sys import exit

li = [ 20, 60, 100, 140, 180, 220, 240 ]

index = lambda i, j, rows: i * rows + j


def threshold_matrix( matrix, li, rows, cols ):
    set_thresholds(li)

    result = np.zeros_like( matrix )

    for row in range(rows):
        for col in range(cols):
            result[index(row, col, rows)] = threshold( matrix[ \
                index(row, col, rows) ])

    return result


def print_result(matrix, rows, cols):
    for row in range(rows):
        for col in range(cols):
            print(result[index(row, col, rows)], sep='  ', end="")
        print()

if __name__ == "__main__":
    img_target = "ducky-128.jpg"
    img = Image.open( "../test-data/" + img_target )
    img = img.convert( 'L' )
    img = np.asarray( img )
    dims = img.shape
    img = img.flatten()

    if img.shape[0] != dims[0] * dims[0]:
        print("Invalid image dimensions")
        exit()

    result = threshold_matrix(img, li, *dims)
    result = result.reshape( dims )

    plt.figure(figsize=(12, 12))
    plt.axis( 'off' )
    plt.imshow( result, plt.cm.gray )
    plt.savefig("../result-data/" + img_target)
    plt.close()
