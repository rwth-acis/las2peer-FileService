package i5.las2peer.services.fileService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.fileupload.MultipartStream.MalformedStreamException;
import org.apache.commons.lang3.StringEscapeUtils;

import i5.las2peer.api.exceptions.ArtifactNotFoundException;
import i5.las2peer.api.exceptions.StorageException;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.services.fileService.StoredFileIndex.StoredFileIndexComparator;
import i5.las2peer.services.fileService.multipart.FormDataPart;
import i5.las2peer.services.fileService.multipart.MultipartHelper;
import i5.las2peer.tools.CryptoException;
import i5.las2peer.tools.SerializationException;
import i5.las2peer.tools.SimpleTools;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import net.minidev.json.JSONArray;

/**
 * las2peer File Service
 * 
 * This is an example service to store files in the las2peer network. One can upload and download files using the
 * service also via the WebConnector.
 * 
 */
@Path("/fileservice")
@Api
@SwaggerDefinition(
		info = @Info(
				title = "las2peer File Service",
				version = "1.0",
				description = "A las2peer file service for demonstration purposes.",
				termsOfService = "http://your-terms-of-service-url.com",
				contact = @Contact(
						name = "ACIS Group",
						url = "las2peer.org",
						email = "las2peer@dbis.rwth-aachen.de"),
				license = @License(
						name = "ACIS License",
						url = "https://github.com/rwth-acis/las2peer-File-Service/blob/master/LICENSE")))
public class FileService extends RESTService {

	// this header is not known to javax.ws.rs.core.HttpHeaders
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	// non HTTP standard headers
	public static final String HEADER_OWNERID = "ownerid";
	public static final String HEADER_CONTENT_DESCRIPTION = "Content-Description";

	// instantiate the logger class
	private final L2pLogger logger = L2pLogger.getInstance(FileService.class.getName());

	private static final String ENVELOPE_BASENAME = "file-";
	private static final SimpleDateFormat RFC2822FMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z (zzz)");
	private static final String RESOURCE_BASENAME = "/files";
	private static final String DOWNLOAD_PATH = "/download";
	private static final String INDEX_IDENTIFIER_SUFFIX = "-index";
	private static final String RESOURCE_INDEX_JSON = "/index.json";
	private static final String RESOURCE_INDEX_HTML = "/index.html";
	private static final SimpleDateFormat HTML_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	// configurable properties
	public static final long DEFAULT_MAX_FILE_SIZE_MB = 5; // MegaByte
	private long maxFileSizeMB = DEFAULT_MAX_FILE_SIZE_MB;

	public FileService() {
		// read and set properties values
		setFieldValues();
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * This method is designed to be used with RMI calls to this service.
	 * 
	 * @param identifier A file identifier for the file that should be retrieved. Usually a unique name or hash value.
	 * @return Returns the file including its metadata as Map. Fields set in the map are: identifier, name, content,
	 *         lastModified, mimeType, ownerId and description.
	 * @throws ArtifactNotFoundException If the file was not found.
	 * @throws StorageException If an error occurs with the shared storage.
	 * @throws CryptoException If an encryption error occurs, mostly because of missing read permissions.
	 * @throws L2pSecurityException If the agent used to get content is not unlocked.
	 * @throws SerializationException If an serialization issue occurs, mostly because of unexpected or damaged content.
	 */
	public Map<String, Object> fetchFile(String identifier) throws ArtifactNotFoundException, StorageException,
			CryptoException, L2pSecurityException, SerializationException {
		StoredFile file = fetchFileReal(identifier);
		return file.toMap();
	}

	private StoredFile fetchFileReal(String identifier) throws ArtifactNotFoundException, StorageException,
			CryptoException, L2pSecurityException, SerializationException {
		// fetch envelope by file identifier
		Envelope env = getContext().fetchEnvelope(ENVELOPE_BASENAME + identifier);
		// read content from envelope into string
		StoredFile result = (StoredFile) env.getContent();
		return result;
	}

	/**
	 * This method is intended to be used by other services for invocation. It uses only default types and classes.
	 * 
	 * @param identifier A required unique name or hash value to identify this file.
	 * @param filename An optional human readable filename.
	 * @param content Actual file content. (required)
	 * @param mimeType The optional mime type for this file. Also set as header value in the web interface on download.
	 * @param description An optional description for the file.
	 * @return Returns true if the file was created and didn't exist before.
	 * @throws StorageException If an exception with the shared storage occurs.
	 * @throws IllegalArgumentException If the content is to large to be stored in an Envelope.
	 * @throws SerializationException If an serialization issue occurs, mostly because of unexpected or damaged content.
	 * @throws CryptoException If an encryption error occurs, mostly because of missing read permissions.
	 * @throws AgentNotKnownException If the service is not yet started.
	 * @throws L2pSecurityException If the main agent isn't unlocked.
	 */
	public boolean storeFile(String identifier, String filename, byte[] content, String mimeType, String description)
			throws IllegalArgumentException, StorageException, SerializationException, CryptoException,
			AgentNotKnownException, L2pSecurityException {
		return storeFileReal(new StoredFile(identifier, filename, content, new Date().getTime(),
				getContext().getMainAgent().getId(), mimeType, description));
	}

	private boolean storeFileReal(StoredFile file) throws StorageException, IllegalArgumentException,
			SerializationException, CryptoException, AgentNotKnownException, L2pSecurityException {
		boolean created = false;
		// limit (configurable) file size
		if (file.getContent() != null && file.getContent().length > maxFileSizeMB * 1000000) {
			throw new StorageException("File too big! Maximum size: " + maxFileSizeMB + " MB");
		}
		// fetch or create envelope by file identifier
		Envelope fileEnv = null;
		try {
			Envelope storedFile = getContext().fetchEnvelope(ENVELOPE_BASENAME + file.getIdentifier());
			// update envelope content
			fileEnv = getContext().createEnvelope(storedFile, file);
		} catch (ArtifactNotFoundException e) {
			logger.info("File (" + file.getIdentifier() + ") not found. Creating new one. " + e.toString());
			fileEnv = getContext().createEnvelope(ENVELOPE_BASENAME + file.getIdentifier(), file);
			created = true;
		}
		// store file envelope
		getContext().storeEnvelope(fileEnv);
		logger.info("stored file (" + file.getIdentifier() + ") in network storage");
		// fetch or create file index envelope
		StoredFileIndex indexEntry = new StoredFileIndex(file.getIdentifier(), file.getName(), file.getLastModified(),
				file.getOwnerId(), file.getMimeType(), file.getDescription(), file.getFileSize());
		Envelope indexEnv = null;
		try {
			Envelope storedIndex = getContext().fetchEnvelope(getIndexIdentifier());
			@SuppressWarnings("unchecked")
			ArrayList<StoredFileIndex> fileIndex = (ArrayList<StoredFileIndex>) storedIndex.getContent();
			// remove old entries
			Iterator<StoredFileIndex> itIndex = fileIndex.iterator();
			while (itIndex.hasNext()) {
				StoredFileIndex index = itIndex.next();
				if (indexEntry.getIdentifier().equalsIgnoreCase(index.getIdentifier())) {
					itIndex.remove();
				}
			}
			// update file index
			fileIndex.add(indexEntry);
			indexEnv = getContext().createUnencryptedEnvelope(storedIndex, fileIndex);
		} catch (ArtifactNotFoundException e) {
			logger.info("Index not found. Creating new one.");
			ArrayList<StoredFileIndex> fileIndex = new ArrayList<>();
			fileIndex.add(indexEntry);
			indexEnv = getContext().createUnencryptedEnvelope(getIndexIdentifier(), fileIndex);
		}
		// store index envelope
		getContext().storeEnvelope(indexEnv);
		logger.info("stored file (" + file.getIdentifier() + ") in network storage");
		return created;
	}

	/**
	 * This operation is not yet supported by las2peer.
	 * 
	 * @param identifier A required unique name or hash value to identify this file.
	 * @return Returns an HTTP status code and message with the result of the upload request.
	 */
	@DELETE
	@Path(RESOURCE_BASENAME + "/{identifier}")
	@Produces(MediaType.TEXT_PLAIN)
	public HttpResponse deleteFile(@PathParam("identifier") String identifier) {
		return new HttpResponse("Not implemented, yet!", HttpURLConnection.HTTP_NOT_IMPLEMENTED);
	}

	@GET
	@Path(RESOURCE_BASENAME + "/{subfolder1}/{subfolder2}/{subfolder3}/{subfolder4}/{subfolder5}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("subfolder1") String subfolder1, @PathParam("subfolder2") String subfolder2,
			@PathParam("subfolder3") String subfolder3, @PathParam("subfolder4") String subfolder4,
			@PathParam("subfolder5") String subfolder5, @PathParam("identifier") String identifier) {
		return getFile(subfolder1 + "/" + subfolder2 + "/" + subfolder3 + "/" + subfolder4 + "/" + subfolder5 + "/"
				+ identifier);
	}

	@GET
	@Path(RESOURCE_BASENAME + "/{subfolder1}/{subfolder2}/{subfolder3}/{subfolder4}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("subfolder1") String subfolder1, @PathParam("subfolder2") String subfolder2,
			@PathParam("subfolder3") String subfolder3, @PathParam("subfolder4") String subfolder4,
			@PathParam("identifier") String identifier) {
		return getFile(subfolder1 + "/" + subfolder2 + "/" + subfolder3 + "/" + subfolder4 + "/" + identifier);
	}

	@GET
	@Path(RESOURCE_BASENAME + "/{subfolder1}/{subfolder2}/{subfolder3}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("subfolder1") String subfolder1, @PathParam("subfolder2") String subfolder2,
			@PathParam("subfolder3") String subfolder3, @PathParam("identifier") String identifier) {
		return getFile(subfolder1 + "/" + subfolder2 + "/" + subfolder3 + "/" + identifier);
	}

	@GET
	@Path(RESOURCE_BASENAME + "/{subfolder1}/{subfolder2}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("subfolder1") String subfolder1, @PathParam("subfolder2") String subfolder2,
			@PathParam("identifier") String identifier) {
		return getFile(subfolder1 + "/" + subfolder2 + "/" + identifier);
	}

	@GET
	@Path(RESOURCE_BASENAME + "/{subfolder1}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("subfolder1") String subfolder1,
			@PathParam("identifier") String identifier) {
		return getFile(subfolder1 + "/" + identifier);
	}

	/**
	 * This web API method downloads a file from the las2peer network. The file content is returned as binary content.
	 * 
	 * @param identifier A unqiue name or hash value to identify the file.
	 * @return Returns the file content as inline element for website integration or an error response if an error
	 *         occurred.
	 */
	@GET
	@Path(RESOURCE_BASENAME + "/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("identifier") String identifier) {
		return getFile(identifier, false);
	}

	@GET
	@Path(DOWNLOAD_PATH + "/{subfolder1}/{subfolder2}/{subfolder3}/{subfolder4}/{subfolder5}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("subfolder1") String subfolder1,
			@PathParam("subfolder2") String subfolder2, @PathParam("subfolder3") String subfolder3,
			@PathParam("subfolder4") String subfolder4, @PathParam("subfolder5") String subfolder5,
			@PathParam("identifier") String identifier) {
		return downloadFile(subfolder1 + "/" + subfolder2 + "/" + subfolder3 + "/" + subfolder4 + "/" + subfolder5 + "/"
				+ identifier);
	}

	@GET
	@Path(DOWNLOAD_PATH + "/{subfolder1}/{subfolder2}/{subfolder3}/{subfolder4}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("subfolder1") String subfolder1,
			@PathParam("subfolder2") String subfolder2, @PathParam("subfolder3") String subfolder3,
			@PathParam("subfolder4") String subfolder4, @PathParam("identifier") String identifier) {
		return downloadFile(subfolder1 + "/" + subfolder2 + "/" + subfolder3 + "/" + subfolder4 + "/" + identifier);
	}

	@GET
	@Path(DOWNLOAD_PATH + "/{subfolder1}/{subfolder2}/{subfolder3}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("subfolder1") String subfolder1,
			@PathParam("subfolder2") String subfolder2, @PathParam("subfolder3") String subfolder3,
			@PathParam("identifier") String identifier) {
		return downloadFile(subfolder1 + "/" + subfolder2 + "/" + subfolder3 + "/" + identifier);
	}

	@GET
	@Path(DOWNLOAD_PATH + "/{subfolder1}/{subfolder2}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("subfolder1") String subfolder1,
			@PathParam("subfolder2") String subfolder2, @PathParam("identifier") String identifier) {
		return downloadFile(subfolder1 + "/" + subfolder2 + "/" + identifier);
	}

	@GET
	@Path(DOWNLOAD_PATH + "/{subfolder1}/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("subfolder1") String subfolder1,
			@PathParam("identifier") String identifier) {
		return downloadFile(subfolder1 + "/" + identifier);
	}

	/**
	 * This web API method downloads a file from the las2peer network. The file content is returned as binary content.
	 * 
	 * @param identifier A unqiue name or hash value to identify the file.
	 * @return Returns the file content or an error response if an error occurred.
	 */
	@GET
	@Path(DOWNLOAD_PATH + "/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("identifier") String identifier) {
		return getFile(identifier, true);
	}

	private HttpResponse getFile(String identifier, boolean attachment) {
		try {
			StoredFile file = fetchFileReal(identifier);
			// set binary file content as response body
			HttpResponse response = new HttpResponse(file.getContent(), HttpURLConnection.HTTP_OK);
			// set headers
			String disposition = "inline";
			if (attachment) {
				disposition = "attachment";
			}
			response.setHeader(HEADER_CONTENT_DISPOSITION, disposition + escapeFilename(file.getName()));
			response.setHeader(HttpHeaders.LAST_MODIFIED, RFC2822FMT.format(new Date(file.getLastModified())));
			response.setHeader(HttpHeaders.CONTENT_TYPE, file.getMimeType());
			// following some non HTTP standard header fields
			response.setHeader(HEADER_OWNERID, Long.toString(file.getOwnerId()));
			response.setHeader(HEADER_CONTENT_DESCRIPTION, file.getDescription());
			return response;
		} catch (ArtifactNotFoundException e) {
			logger.log(Level.INFO, "File (" + identifier + ") not found!", e);
			return new HttpResponse("404 Not Found", HttpURLConnection.HTTP_NOT_FOUND);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Can't read file (" + identifier + ") content from network storage! ", e);
			logger.printStackTrace(e);
			return new HttpResponse("500 Internal Server Error", 500);
		}
	}

	private String escapeFilename(String filename) {
		String result = "";
		if (filename != null) {
			result = ";filename=\"" + filename + "\""; // this is the "old" way
			try {
				// this is for modern browsers
				result += ";filename*=UTF-8''" + URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
			} catch (UnsupportedEncodingException e) {
				// if this fails, we still have the "old" way
				logger.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return result;
	}

	/**
	 * This method uploads a file to the las2peer network.
	 * 
	 * @param hostname The hostname this request was send to. Used in REST response URI.
	 * @param contentType The (optional) content MIME type for this file. Usually set by the browser.
	 * @param formData The data from an HTML form encoded as mulitpart.
	 * @return Returns an HTTP status code and message with the result of the upload request.
	 */
	@POST
	@Path(RESOURCE_BASENAME)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "File upload successfull"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_CREATED,
							message = "File successfully created"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_BAD_REQUEST,
							message = "File upload failed!"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_INTERNAL_ERROR,
							message = "File upload failed!") })
	public HttpResponse postFile(@HeaderParam(
			value = HttpHeaders.HOST) String hostname,
			@HeaderParam(
					value = HttpHeaders.CONTENT_TYPE) String contentType,
			@ContentParam byte[] formData) {
		return uploadFile(hostname, contentType, formData, false);
	}

	/**
	 * This method uploads a file to the las2peer network.
	 * 
	 * @param hostname The hostname this request was send to. Used in REST response URI.
	 * @param contentType The (optional) content MIME type for this file. Usually set by the browser.
	 * @param formData The data from an HTML form encoded as mulitpart.
	 * @return Returns an HTTP status code and message with the result of the upload request.
	 */
	@PUT
	@Path(RESOURCE_BASENAME)
	@Produces(MediaType.TEXT_PLAIN)
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "File upload successfull"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_CREATED,
							message = "File successfully created"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_BAD_REQUEST,
							message = "File upload failed!"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_INTERNAL_ERROR,
							message = "File upload failed!") })
	public HttpResponse putFile(@HeaderParam(
			value = HttpHeaders.HOST) String hostname,
			@HeaderParam(
					value = HttpHeaders.CONTENT_TYPE) String contentType,
			@ContentParam byte[] formData) {
		// a file identifier is a enforced for put operation
		return uploadFile(hostname, contentType, formData, true);
	}

	private HttpResponse uploadFile(@HeaderParam(
			value = HttpHeaders.HOST) String hostname,
			@HeaderParam(
					value = HttpHeaders.CONTENT_TYPE) String contentType,
			@ContentParam byte[] formData, boolean enforceIdentifier) {
		String identifier = null;
		try {
			// parse given multipart form data
			String filename = null;
			byte[] filecontent = null;
			String mimeType = null;
			String description = null;
			try {
				Map<String, FormDataPart> parts = MultipartHelper.getParts(formData, contentType);
				FormDataPart partFilecontent = parts.get("filecontent");
				if (partFilecontent != null) {
					// these data belong to the file input form element
					String fullFilename = partFilecontent.getHeader(HEADER_CONTENT_DISPOSITION)
							.getParameter("filename");
					try {
						filename = Paths.get(fullFilename).getFileName().toString();
					} catch (InvalidPathException e) {
						logger.log(Level.FINER,
								"Could not extract filename from '" + fullFilename + "', " + e.toString());
						// use full filename as fallback
						filename = fullFilename;
					}
					filecontent = partFilecontent.getContentRaw();
					mimeType = partFilecontent.getContentType();
					logger.info("upload request (" + filename + ") of mime type '" + mimeType + "' with content length "
							+ filecontent.length);
				}
				FormDataPart partIdentifier = parts.get("identifier");
				if (partIdentifier != null) {
					// these data belong to the (optional) file id text input form element
					identifier = partIdentifier.getContent();
				}
				FormDataPart partMimetype = parts.get("mimetype");
				if (partMimetype != null) {
					// optional mime type field, doesn't overwrite filecontents mime type
					mimeType = partMimetype.getContent();
				}
				FormDataPart partDescription = parts.get("description");
				if (partDescription != null) {
					// optional description text input form element
					description = partDescription.getContent();
				}
			} catch (MalformedStreamException e) {
				// the stream failed to follow required syntax
				logger.log(Level.SEVERE, e.getMessage(), e);
				return new HttpResponse("File (" + identifier + ") upload failed! See log for details.",
						HttpURLConnection.HTTP_BAD_REQUEST);
			} catch (IOException e) {
				// a read or write error occurred
				logger.log(Level.SEVERE, e.getMessage(), e);
				return new HttpResponse("File (" + identifier + ") upload failed! See log for details.",
						HttpURLConnection.HTTP_INTERNAL_ERROR);
			}
			// validate input
			if (filecontent == null) {
				return new HttpResponse(
						"File (" + identifier
								+ ") upload failed! No content provided. Add field named filecontent to your form.",
						HttpURLConnection.HTTP_BAD_REQUEST);
			}
			// enforce identifier for PUT operations
			if (enforceIdentifier && (identifier == null || identifier.isEmpty())) {
				return new HttpResponse("No identfier provided", HttpURLConnection.HTTP_BAD_REQUEST);
			}
			// if the form doesn't especially describe a identifier, use (hashed?) filename as fallback
			if ((identifier == null || identifier.isEmpty()) && filename != null && !filename.isEmpty()) {
				logger.info("No file identifier provided using hashed filename as fallback");
				identifier = Long.toString(SimpleTools.longHash(filename));
			}
			boolean created = storeFile(identifier, filename, filecontent, mimeType, description);
			int code = HttpURLConnection.HTTP_OK;
			if (created) {
				code = HttpURLConnection.HTTP_CREATED;
			}
			// return the complete URL for this resource
			String uri = "/fileservice" + RESOURCE_BASENAME + "/" + identifier;
			if (hostname != null && !hostname.isEmpty()) {
				uri = "https://" + hostname + uri;
			}
			return new HttpResponse(uri, code);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "File upload failed!", e);
			return new HttpResponse("File (" + identifier + ") upload failed! See log for details.",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}

	@GET
	@Path(RESOURCE_INDEX_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public HttpResponse getFileIndexJson() {
		try {
			// transform index list into JSON
			JSONArray indexJson = new JSONArray();
			for (StoredFileIndex index : getFileIndexReal()) {
				indexJson.add(index.toJsonObject());
			}
			HttpResponse response = new HttpResponse(indexJson.toJSONString(), HttpURLConnection.HTTP_OK);
			// set headers
			response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
			return response;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not read file index!", e);
			return new HttpResponse("Could not read file index! See log for details.",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}

	@GET
	@Path(RESOURCE_INDEX_HTML)
	@Produces(MediaType.TEXT_HTML)
	public HttpResponse getFileIndexHtml() {
		try {
			// transform index list into HTML
			StringBuilder sb = new StringBuilder();
			sb.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
			sb.append("<html>\n");
			sb.append("\t<head>\n");
			sb.append("\t\t<title>").append(getAgent().getServiceNameVersion().toString()).append("</title>\n");
			sb.append("</head>\n");
			sb.append("<body>\n");
			sb.append("<h1>Index of " + getAgent().getServiceNameVersion().toString() + "</h1>\n");
			sb.append("<table>\n");
			sb.append("<tr>").append("<th>Identifier</th><th></th><th>Name</th>").append("<th>Last modified</th>")
					.append("<th>Size</th>").append("<th>Description</th>").append("<th></th>").append("</tr>");
			sb.append("<tr><th colspan=\"5\"><hr></th></tr>\n");
			for (StoredFileIndex index : getFileIndexReal()) {
				sb.append("<tr>");
				String clsURI = "";
				Path pathAnnotation = getClass().getAnnotation(Path.class);
				if (pathAnnotation != null) {
					clsURI = cleanSlashes(pathAnnotation.value());
				}
				String basename = cleanSlashes(RESOURCE_BASENAME);
				String identifier = cleanSlashes(index.getIdentifier());
				sb.append("<td><a href=\"" + clsURI + basename + identifier + "\">" + identifier + "</a></td>");
				String download = cleanSlashes(DOWNLOAD_PATH);
				sb.append("<td><a href=\"" + clsURI + download + identifier + "\">[&#8595;]</a></td>");
				String strName = index.getName();
				if (strName == null) {
					strName = "";
				}
				sb.append("<td>" + strName + "</td>");
				sb.append("<td>" + HTML_DATE_FMT.format(new Date(index.getLastModified())) + "</td>");
				sb.append("<td align=\"right\">" + humanReadableByteCount(index.getFileSize(), true) + "</td>");
				String description = index.getDescription();
				if (description == null) {
					description = "";
				}
				// escape HTML special characters in file description
				sb.append("<td>" + StringEscapeUtils.escapeHtml4(description) + "</td>");
				sb.append("</tr>\n");
			}
			sb.append("</table>\n").append("</body>\n");
			sb.append("</html>\n");
			HttpResponse response = new HttpResponse(sb.toString(), HttpURLConnection.HTTP_OK);
			// set headers
			response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
			return response;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not read file index!", e);
			return new HttpResponse("Could not read file index! See log for details.",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}

	/**
	 * Ensures that the given String is returned with exactly one leading slash and zero tailing slashes.
	 * 
	 * @param pathPart The String object to clean.
	 * @return Returns the String with the described constraints.
	 */
	private static String cleanSlashes(String pathPart) {
		// remove slashes at the start
		while (pathPart.startsWith("/")) {
			pathPart = pathPart.substring(1);
		}
		// remove tailing slashes at the end
		while (pathPart.endsWith("/")) {
			pathPart = pathPart.substring(0, pathPart.length() - 1);
		}
		return "/" + pathPart;
	}

	// Source: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
	private static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	public ArrayList<Map<String, Object>> getFileIndex() throws AgentNotKnownException, StorageException,
			CryptoException, L2pSecurityException, SerializationException {
		ArrayList<Map<String, Object>> result = new ArrayList<>();
		ArrayList<StoredFileIndex> fileIndex = getFileIndexReal();
		for (StoredFileIndex index : fileIndex) {
			result.add(index.toMap());
		}
		return result;
	}

	private ArrayList<StoredFileIndex> getFileIndexReal() throws AgentNotKnownException, StorageException,
			CryptoException, L2pSecurityException, SerializationException {
		try {
			Envelope storedIndex = getContext().fetchEnvelope(getIndexIdentifier());
			@SuppressWarnings("unchecked")
			ArrayList<StoredFileIndex> indexList = (ArrayList<StoredFileIndex>) storedIndex.getContent();
			indexList.sort(StoredFileIndexComparator.INSTANCE);
			return indexList;
		} catch (ArtifactNotFoundException e) {
			return new ArrayList<>();
		}
	}

	private String getIndexIdentifier() throws AgentNotKnownException {
		return getAgent().getServiceNameVersion().toString() + INDEX_IDENTIFIER_SUFFIX;
	}

}
