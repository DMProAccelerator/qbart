#!/usr/bin/env bash
#
# Distributes a target file to a number of specified hosts.
#
# $1: Host login user name.
# $2: File to upload (compressed format).
# $3: Host path for execution of script (defaults to /home/).
#
# Usage: ./setup.sh <$1> <$2> [<$3>]

# TODO (fredaas): Implement flags to specify file upload, script execution, or
# both.

# TODO (fredaas): Every call using scp or ssh prompts the user to enter a
# password. Find a way around this.

if [[ -z "$1" ]]; then
    echo "Please provide a login user name."
    exit 1
elif [[ -z "$2" ]]; then
    echo "Please provide a target file."
    exit 1
fi

if [[ -z "$3" ]]; then
    PATH=/home/
else
    PATH=$3
fi

USER=$1
TARGET=$2

readarray hosts < ./hosts

# Upload target file to all hosts simultaneously.
for host in ${hosts[@]}; do
    scp ./${TARGET} ${USER}@${host}:${PATH}
done

# Run script on all hosts simultaneously.
for host in ${hosts[@]}; do
    ssh ${USER}@${host} 'bash -s' < setup-vars.sh
done

exit 0
