from PIL import Image
import sys
import logging
import os
import numpy as np
import copy

###########################################################################################################
### Image loading, should be placed in separate helper_functions.py when finished.
###########################################################################################################
# Loads all the images from the folder provided by the user, 
# returns a python list of references to those images loaded as PIL images.
def load_images(image_dir, no_images, colsize, rowsize, data_layout, channel_order):
    logging.getLogger()
    
    # If the image directory is empty, then we abort the program by raising an error.
    if image_dir is None:
        raise ValueError("There are no images in the specified path.")
        
    # TODO: Current implementation does not distinguish between image files and others.
    # A solution is to see which image formats pillow fully supports, and then only support these, throwing
    # a warning to logger for the rest.         
    images = []
    logging.info("Starts to load files with pillow, converting them into 1D numpy arrays.")
    files = os.listdir(image_dir)
    
    for image in files:
        # listdir only returns file name, not the relative path. So the following doesn't look that nice.
        im_path = "".join((image_dir, "/", image))
        filename, file_extension = os.path.splitext(im_path)
        images.append(image_data_transform(im_path, file_extension, colsize, rowsize, data_layout, channel_order))
        if len(images) > no_images:
            break
    
    logging.info("We are now finished with loading images.")
    logging.info("Successfully loaded " + str(len(images)) + " images.")
    logging.debug("This folder currently has the following images: " + str(os.listdir(image_dir)))
    return images                                                                                                                                                                                           

###########################################################################################################
###########################################################################################################


###########################################################################################################
### Image preproccessing, should be placed in separate qbart_helper_functions.py when finished.
###########################################################################################################
# For example, for GTSRB:
# Rearrange the data layout to channels, rows, columns (from tutorial code)
# img = img.transpose((2, 0, 1))
# Use BGR instead of RGB, since the provided network is like that. (from tutorial code)
#        img = img[::-1, :, :]

def image_data_transform(image_path, file_extension,colsize, rowsize, data_layout, channel_order):
    # Step 1: Check image format
    if file_extension == ".ppm":
        # Handle the image, we should support this format, but we dont yet (see below)
        raise ValueError("We currently do NOT support ppms")
    elif file_extension == ".jpg":
        # Handle the image, we support this format.
        print("It is a jpg!")
    else:
        raise ValueError("The provided image format is not currently supported. Try .ppm")
    
    # Step 2: Read in the image
    image = Image.open(image_path)
    
    # Step 3: Resize the image, if needed
    # TODO: Don't resize if it isn't necessary.
    image = image.resize((colsize, rowsize))
    
    # Step 4: Convert to numpy array
    image = np.asarray(image)
    
    # Step 5: Rearrange data layout if needed. This is usually sensitive to file extension.
    if file_extension == ".jpg":
        image_data_layout = "rcC"
        
        if not (image_data_layout == data_layout):
            image_order = {"r":0, "c":1, "C":2}
            image = image.transpose(image_order[data_layout[0]], image_order[data_layout[1]], image_order[data_layout[2]])
    
    
    # Step 6: Rearrange channel ordering if needed.
    if file_extension == ".png":
        image_channel_order = "RGB"
        im_ch_order = {'R':0, 'G':1, 'B':2}
        
        # If the given images and what the QNN expects doesn't match, then...
        if not(image_channel_order == channel_order):
            temp_img = image.copy()
            image.setflags(write=1)
            image[0] = (temp_img[im_ch_order[channel_order[0]]])
            image[1] = (temp_img[im_ch_order[channel_order[1]]])
            image[2] = (temp_img[im_ch_order[channel_order[2]]])
            
    return image
###########################################################################################################
###########################################################################################################
