#!/usr/bin/env bash
#
# Performs style checks on scala files.

# Get directory of calling script.
DIR=$(cd $(dirname ${BASH_SOURCE[0]}) && pwd)

# If no target is set, run the test on root directory.
if [[ -z "$1" ]]; then
    TARGET=$DIR/..
else
    TARGET=$(pwd)/$1
    [[ ! -f $TARGET ]] && [[ ! -d $TARGET ]] \
        && echo "ERROR: No such file or directory." && exit 1
fi

java -jar ${DIR}/scalastyle_2.12-1.0.0-batch.jar \
    --config ${DIR}/scalastyle.xml ${TARGET}
