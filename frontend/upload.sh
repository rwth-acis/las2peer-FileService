#!/bin/bash

# this file can be used to upload the FileService frontend to the las2peer network.

function curlcmd {
  curl --form "filecontent=@$1;filename=$1" --form identifier=fileservice/$1 http://localhost:14580/fileservice/files
  echo "" # just for the newline character
}

if [ $# -eq 1 ]; then # upload single file given as only parameter
curlcmd $1
else # upload complete frontend
curlcmd bootstrap.min.css
curlcmd index.html
curlcmd logo.png
curlcmd JS/bootstrap.min.js
curlcmd JS/common.js
curlcmd JS/jquery-2.2.4.min.js
curlcmd JS/jws-2.0.min.js
curlcmd JS/oidc-button.js
curlcmd JS/jsrsasign/asn1-1.0.min.js
curlcmd JS/jsrsasign/asn1hex-1.1.min.js
curlcmd JS/jsrsasign/base64x-1.1.min.js
curlcmd JS/jsrsasign/crypto-1.0.min.js
curlcmd JS/jsrsasign/crypto-1.1.min.js
curlcmd JS/jsrsasign/rsapem-1.1.min.js
curlcmd JS/jsrsasign/rsasign-1.2.min.js
curlcmd JS/jsrsasign/x509-1.1.min.js
curlcmd JS/jsrsasign/ext/base64-min.js
curlcmd JS/jsrsasign/ext/jsbn2-min.js
curlcmd JS/jsrsasign/ext/jsbn-min.js
curlcmd JS/jsrsasign/ext/rsa2-min.js
curlcmd JS/jsrsasign/ext/rsa-min.js
fi

