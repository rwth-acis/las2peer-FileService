# las2peer-FileService

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

### How can I integrate the upload function into my web frontend?

Just add or adapt the following form to your web frontend. Please note that **filecontent** is mandatory!
```html
<html>
	<form method="POST" enctype="multipart/form-data" action="http://localhost:14580/fileservice/files">
		File to upload: <input type="file" name="filecontent" value=""><br/>
		Identifier (unique): <input type="text" name="fileid"><br/>
		Description: <input type="text" name="description"><br/>
		<br/>
		<input type="submit" value="Press"> to upload the file!
	</form>
</html>
```

### How can I use uploaded files?

Each file has its unique identifier and the service provides two urls to get or download the file.

To get the file like a logo image for your website just add:
```html
<img src="http://localhost:14580/fileservice/files/[your logo image fileid]">
```

To provide a file download link, e.g. for attachments, just add:
```html
<a href="http://localhost:14580/fileservice/download/[your logo image fileid]">Download</a>
```
