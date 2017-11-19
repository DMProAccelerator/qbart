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
def load_images(image_dir, no_images, colsize, rowsize, data_layout, channel_order, qnn_type):
	# If the image directory is empty, then we abort the program by raising an error.
	if image_dir is None:
		raise ValueError("There are no images in the specified path.")
	
	# TODO: Current implementation does not distinguish between image files and others.
	# A solution is to see which image formats pillow fully supports, and then only support these, throwing
	# a warning to logger for the rest.         
	images = []
	files = os.listdir(image_dir)
	
	for image in sorted(files):
	# listdir only returns file name, not the relative path. So the following doesn't look that nice.
		im_path = "".join((image_dir, "/", image))
		filename, file_extension = os.path.splitext(im_path)
		images.append((image, image_data_transform(im_path, file_extension, colsize, rowsize, data_layout, channel_order, qnn_type)))
		
		if len(images) == no_images:
			break
	
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

def image_data_transform(image_path, file_extension,colsize, rowsize, data_layout, channel_order, qnn_type):
	# Step 1: Check image format
	if (file_extension not in [".ppm",".jpg", ".png"]):
		raise ValueError("The provided image format is not currently supported. Try .ppm,.png, or .jpg")
	
	# Step 2: Read in the image, convert to greyscale if it is the special MNIST from the tutorial.
	image = Image.open(image_path)
	
	if(channel_order == "grayscale"):
		image = image.convert("L")
	
	# Step 3: Resize the image, if needed
	image = image.resize((colsize, rowsize))
	
	# Step 4: Convert to numpy array
	if qnn_type == "imagenet":
		image = np.asarray(image).copy().astype(np.int32)
	else:
		image = np.asarray(image)
	
	# Step 5: Rearrange data layout if needed. This is usually sensitive to file extension.
	if file_extension == ".jpg" or file_extension == ".ppm" or file_extension == ".png":
		image_data_layout = "rcC"
		
		if not (image_data_layout == data_layout) and (channel_order != "grayscale"):
			image_order = {"r":0, "c":1, "C":2}
			image = image.transpose(image_order[data_layout[0]], image_order[data_layout[1]], image_order[data_layout[2]])
		
	# Step 6: Rearrange channel ordering if needed.
	if file_extension == ".jpg" or file_extension == ".ppm" or file_extension == ".png":
		image_channel_order = "RGB"
		im_ch_order = {'R':0, 'G':1, 'B':2}
		
		# If the given images and what the QNN expects doesn't match, then...
		if not(image_channel_order == channel_order) and (channel_order != "grayscale"):
			temp_img = image.copy()
			image.setflags(write=1)
			image[0] = (temp_img[im_ch_order[channel_order[0]]])
			image[1] = (temp_img[im_ch_order[channel_order[1]]])
			image[2] = (temp_img[im_ch_order[channel_order[2]]])
	# Reshape done, we return the image.
	
	# Actually, the imagenet variant has a last "subtracting channel mean" step:
	if qnn_type == "imagenet":
		image[0] -= 104
		image[1] -= 117
		image[2] -= 123
	
	return image
###########################################################################################################
###########################################################################################################
