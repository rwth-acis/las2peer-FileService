<p align="center">
  <img src="https://raw.githubusercontent.com/rwth-acis/las2peer/master/img/logo/bitmap/las2peer-logo-128x128.png" />
</p>
<h1 align="center">las2peer-FileService</h1>
<p align="center">
  <a href="https://travis-ci.org/rwth-acis/las2peer-FileService" alt="Travis Build Status">
        <img src="https://travis-ci.org/rwth-acis/las2peer-FileService.svg?branch=master" /></a>
  <a href="https://codecov.io/gh/rwth-acis/las2peer-FileService" alt="Code Coverage">
        <img src="https://codecov.io/gh/rwth-acis/las2peer-FileService/branch/master/graph/badge.svg" /></a>
  <a href="https://libraries.io/github/rwth-acis/las2peer-FileService" alt="Dependencies">
        <img src="https://img.shields.io/librariesio/github/rwth-acis/las2peer-FileService" /></a>
</p>

This is a las2peer file service. It provides methods to fetch and store binary data (files) in the las2peer network.
Furthermore it supports some kind of metadata for each file. Currently it supports the following metadata:

- unique identifier for each file, or the hashed filename if not provided
- filename as provided by a calling service or from an HTML form
- (auto-set) last modified timestamp as number since the epoch
- (optional) mime type that describes the content of the file
- (auto-set) owner id a unique number identifying the users agent that owns this file
- (optional) a textual description of the files content

Those metadata are set as header fields when the file is fetched from the service. Currently the following headers are set:

- "Content-Disposition: filename=[the filename without path];filename*=[the filename without path]"
- "Last-Modified: [timestamp in RFC2822 format]"
- "Content-Type: [as set in files metadata]"

Furthermore the service sets the following non HTTP standard headers:

- "ownerid: [agent id of the owner]"
- "Content-Description: [textual content description as set in metadata]"

## How can I integrate the upload function into my web frontend

Just add or adapt the following form to your web frontend. Please note that **filecontent** is mandatory!

```html
<html>
 <form method="POST" enctype="multipart/form-data" action="http://localhost:14580/fileservice/files">
  File to upload: <input type="file" name="filecontent" value=""><br/>
 Identifier (unique): <input type="text" name="identifier"><br/>
  Description: <input type="text" name="description"><br/>
  <br/>
  <input type="submit" value="Press"> to upload the file!
 </form>
</html>
```

## How can I use uploaded files

Each file has its unique identifier and the service provides two urls to get or download the file.

To get the file like a logo image for your website just add:

```html
<img src="http://localhost:14580/fileservice/files/[your logo image identifier]">
```

To provide a file download link, e.g. for attachments, just add:

```html
<a href="http://localhost:14580/fileservice/download/[your logo image identifier]">Download</a>
```

## How to build this service

See: <https://github.com/rwth-acis/las2peer-Template-Project>

### How to run using Docker

First build the image:

```bash
docker build . -t file-service
```

Then you can run the image like this:

```bash
docker run -p 8080:8080 -p 9011:9011 file-service
```

The REST-API will be available via *http://localhost:8080/fileservice/files* and the las2peer node is available via port 9011.

In order to customize your setup you can set further environment variables.

#### Node Launcher Variables

Set [las2peer node launcher options](https://github.com/rwth-acis/las2peer-Template-Project/wiki/L2pNodeLauncher-Commands#at-start-up) with these variables.
The las2peer port is fixed at *9011*.

| Variable | Default | Description |
|----------|---------|-------------|
| BOOTSTRAP | unset | Set the --bootstrap option to bootrap with existing nodes. The container will wait for any bootstrap node to be available before continuing. |
| SERVICE_PASSPHRASE | Passphrase | Set the second argument in *startService('<service@version>', '<SERVICE_PASSPHRASE>')*. |
| SERVICE_EXTRA_ARGS | unset | Set additional launcher arguments. Example: ```--observer``` to enable monitoring. |

#### Other Variables

| Variable | Default | Description |
|----------|---------|-------------|
| DEBUG  | unset | Set to any value to get verbose output in the container entrypoint script. |

#### Custom Node Startup

If the variables are not sufficient for your setup you can customize how the node is started via arguments after the image name.
In this example we start the node in interactive mode:

```bash
docker run -it -e MYSQL_USER=myuser -e MYSQL_PASSWORD=mypasswd activity-tracker startService\(\'i5.las2peer.services.fileService.FileService@2.2.6\', \'Passphrase\'\) startWebConnector interactive
```

Inside the container arguments are placed right behind the launch node command:

```bash
java -cp lib/* i5.las2peer.tools.L2pNodeLauncher -s service -p ${LAS2PEER_PORT} <your args>
```

#### Volumes

The following places should be persisted in volumes in productive scenarios:

| Path | Description |
|------|-------------|
| /src/node-storage | Pastry P2P storage. |
| /src/etc/startup | Service agent key pair and passphrase. |
| /src/log | Log files. |
