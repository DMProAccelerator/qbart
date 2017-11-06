import logging
import pickle

###########################################################################################################
### QNN loading, loads a pickled QNN that is formatted in a way that should be specified by the manual.
###########################################################################################################
# Loads the QNN from a python2 pickle file, and returns a list of the layers, similar to the tutorial code.
def load_qnn(qnn_filepath):
	logging.getLogger()
	logging.info("Currently trying to load provided QNN.")
	
	if qnn_filepath is None:
		logging.error("There isn't a file in the specified QNN-location.")
		raise ValueError("The file specified in qnn_path doesn't exist, or read permission denied.")
	                        
	the_qnn_file = open(qnn_filepath, "rb")
	qnn_loaded = pickle.load(the_qnn_file)
	the_qnn_file.close()
	logging.info("Successfully loaded QNN.")
	logging.debug("The QNN has the following contents: " + str(qnn_loaded))
	return pickle.dumps(qnn_loaded)

###########################################################################################################
###########################################################################################################
