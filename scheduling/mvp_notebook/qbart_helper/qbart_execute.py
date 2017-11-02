import logging
from time import time
import numpy as np

def qbart_execute(qnn, images):
    logging.getLogger()
    logging.info("Starting the timer to time QBART.")
    qbart_start = time()
    ###########################################################################################################
### The actual QBART processing of every image, should be placed in separate qbart.py when finished.
###########################################################################################################

    qbart_classifications = []
    for image_index in range(len(images)):
        activations = images[image_index]
        for layer in qnn:
            # Each layer will either do calculations on the A9 or the FPGA.
            # Everything that the FPGA is unable to do, simply runs on the CPU.
            # It will initially look very similar to alot in the provided "layers.py", 
            # but should in the end be entirely different when FPGA implements are finished.
            
            # CONVOLUTION LAYER
            if (layer.layerType() == "QNNConvolutionLayer"):
                activations = layer.execute(activations)
            # FULLY CONNECTED LAYER
            # TODO: For MVP, this should be modified to place data in DRAM, call FPGA, and get result
            elif (layer.layerType() == "QNNFullyConnectedLayer"):
                activations = layer.execute(activations)
            # POOLING LAYER
            elif (layer.layerType() == "QNNPoolingLayer"):
                activations = layer.execute(activations)
            # THRESHOLDING LAYER
            # TODO: For MVP, this should be modified to place data in DRAM, call FPGA, and get result
            elif (layer.layerType() == "QNNThresholdingLayer"):
                activations = layer.execute(activations)
            # SCALESHIFT LAYER
            elif (layer.layerType() == "QNNScaleShiftLayer"):
                activations = layer.execute(activations)
            # PADDING LAYER
            elif (layer.layerType() == "QNNPaddingLayer"):
                activations = layer.execute(activations)
            # SLIDING WINDOW LAYER
            elif (layer.layerType() == "QNNSlidingWindowLayer"):
                activations = layer.execute(activations)
            # LINEAR LAYER
            elif (layer.layerType() == "QNNLinearLayer"):
                activations = layer.execute(activations)
            # SOFTMAX LAYER
            elif (layer.layerType() == "QNNSoftmaxLayer"):
                activations = layer.execute(activations)
            # ReLU LAYER
            elif (layer.layerType() == "QNNReLULayer"):
                activations = layer.execute(activations)
            # BIPOLAR THRESHOLDING LAYER (Can't this be replaced by the general thresholding layer?)
            elif (layer.layerType() == "QNNBipolarThresholdingLayer"):
                activations = layer.execute(activations)
            else:
                # Raise error, we are asked to perform a layer operation we do not know.
                raise ValueError("Invalid layer type.")

        qbart_classifications.append(np.argmax(activations))
        logging.info("Finished classifying image " + str(image_index) + " of " + str(len(images)))
    
    qbart_end = time()
    logging.info("Timed stoptime, QBART is finished with all classifications!")
    logging.info("QBART used a total of " + str(qbart_end - qbart_start) + "seconds to classify these " + str(len(images)) + " images.")

    return qbart_classifications
