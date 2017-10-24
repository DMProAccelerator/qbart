############################################################################################################## 
### Placeholder for potential beowulf cluster setup.
###########################################################################################################
# 
# Before the actual computations, the beowulf cluster setup must do the following:
# - Establish connection to other PYNQs
# - Send QNN and a subset of all the images to each PYNQ
# - Make them calculate, then return the results to master.
# - The whole process must be timed, so we can compare it to a noncluster approach, and see if there are any
#   benefits, or if the overhead of transferring over ethernet is too big.
# - Add to __init__.py and modify main code when ready.
###########################################################################################################
###########################################################################################################
