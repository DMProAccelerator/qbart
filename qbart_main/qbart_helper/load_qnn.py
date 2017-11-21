import pickle
import sys

sys.path.append("/home/xilinx/jupyter_notebooks/qbart_main/qbart_helper")

###########################################################################################################
### QNN loading, loads a pickled QNN that is formatted in a way that should be specified by the manual.
###########################################################################################################
# Loads the QNN from a python2 pickle file, and returns a list of the layers, similar to the tutorial code.
def load_qnn(qnn_filepath):
	if qnn_filepath is None:
		raise ValueError("The file specified in qnn_path doesn't exist, or read permission denied.")
	                        
	the_qnn_file = open(qnn_filepath, "rb")
	qnn_loaded = pickle.load(the_qnn_file)
	the_qnn_file.close()
	return pickle.dumps(qnn_loaded)

###########################################################################################################
###########################################################################################################
