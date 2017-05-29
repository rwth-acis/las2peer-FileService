#!/bin/bash

# this script can be used to upload one or more files into a las2peer network using the FileService.

WEBCONNECTOR_URL=http://localhost:14580

function curlcmd {
  curl --form "filecontent=@$1;filename=$1" --form identifier=fileservice/$1 ${WEBCONNECTOR_URL}/fileservice/files
  echo "" # just for the newline character
}

if [ $# -eq 1 ]; then # upload single file given as only parameter
curlcmd $1
else
printf "Usage: ${0} filename\n\
Example: ${0} index.html\n"
fi

# or create your own upload script like this
#curlcmd index.html
#curlcmd logo.png
# etc.
