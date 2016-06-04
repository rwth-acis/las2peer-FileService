package i5.las2peer.services.fileService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

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

import i5.las2peer.api.Service;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.ArtifactNotFoundException;
import i5.las2peer.p2p.StorageException;
import i5.las2peer.persistency.DecodingFailedException;
import i5.las2peer.persistency.EncodingFailedException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.persistency.EnvelopeException;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.security.Agent;
import i5.las2peer.security.L2pSecurityException;
import i5.las2peer.services.fileService.multipart.FormDataPart;
import i5.las2peer.services.fileService.multipart.MultipartHelper;
import i5.las2peer.tools.SerializationException;
import i5.las2peer.tools.SimpleTools;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

/**
 * las2peer File Service
 * 
 * This is an example service to store files in the las2peer network. One can upload and download files using the
 * service also via the WebConnector.
 * 
 */
@Path("/fileservice")
@Version("1.0") // this annotation is used by the XML mapper
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
public class FileService extends Service {

	// TODO think about rights management

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
	 * @throws L2pSecurityException If an encryption error occurs.
	 * @throws EnvelopeException If the file could not be unwrapped from the shared storage object.
	 */
	public Map<String, Object> fetchFile(String identifier)
			throws ArtifactNotFoundException, StorageException, L2pSecurityException, EnvelopeException {
		StoredFile file = fetchFileReal(identifier);
		Map<String, Object> result = new HashMap<>();
		// fields should be similar to StoredFile class
		result.put("identifier", file.getIdentifier());
		result.put("name", file.getName());
		result.put("content", file.getContent());
		result.put("lastModified", file.getLastModified());
		result.put("mimeType", file.getMimeType());
		result.put("ownerId", file.getOwnerId());
		result.put("description", file.getDescription());
		return result;
	}

	/**
	 * This method downloads a file from the las2peer network.
	 * 
	 * @param identifier A file identifier for the file that should be retrieved. Usually a unique name or hash value.
	 * @return Returns the file object or {@code null} if an error occurred.
	 * @throws ArtifactNotFoundException If the file was not found.
	 * @throws StorageException If an error occurs with the shared storage.
	 * @throws L2pSecurityException If an encryption error occurs.
	 * @throws EnvelopeException If the file could not be unwrapped from the shared storage object.
	 */
	private StoredFile fetchFileReal(String identifier)
			throws ArtifactNotFoundException, StorageException, L2pSecurityException, EnvelopeException {
		Agent owner = getContext().getMainAgent();
		// fetch envelope by file identifier
		Envelope env = getContext().getStoredObject(StoredFile.class, ENVELOPE_BASENAME + identifier);
		env.open(owner);
		// read content from envelope into string
		StoredFile result = env.getContent(this.getClass().getClassLoader(), StoredFile.class);
		env.close();
		return result;
	}

	/**
	 * This method is intended to be used by other services for invocation. It uses only default types and classes.
	 * 
	 * @param identifier A required unqiue name or hash value to identify this file.
	 * @param filename An optional human readable filename.
	 * @param content Actual file content. (required)
	 * @param mimeType The optional mime type for this file. Also set as header value in the web interface on download.
	 * @param description An optional description for the file.
	 * @return Returns true if the file was created and didn't exist before.
	 * @throws UnsupportedEncodingException If UTF-8 is not known in the JVM environment.
	 * @throws EncodingFailedException If a serialization or security issue occurs.
	 * @throws SerializationException If a serialization or security issue occurs.
	 * @throws DecodingFailedException If a serialization or security issue occurs.
	 * @throws L2pSecurityException If a serialization or security issue occurs.
	 * @throws StorageException If an exception with the shared storage occurs.
	 */
	public boolean storeFile(String identifier, String filename, byte[] content, String mimeType, String description)
			throws UnsupportedEncodingException, EncodingFailedException, SerializationException,
			DecodingFailedException, L2pSecurityException, StorageException {
		// validate input
		if (identifier == null) {
			throw new NullPointerException("file identifier must not be null");
		}
		if (identifier.isEmpty()) {
			throw new IllegalArgumentException("file identifier must not be empty");
		}
		if (content == null) {
			throw new NullPointerException("content must not be null");
		}
		return storeFileReal(new StoredFile(identifier, filename, content, new Date().getTime(),
				getContext().getMainAgent().getId(), mimeType, description));
	}

	/**
	 * This method uploads a file to the las2peer network.
	 * 
	 * @param file The file and metadata wrapped into the internal file object class used by this service.
	 * @return Returns true if the file was created and didn't exist before.
	 * @throws UnsupportedEncodingException If UTF-8 is not known in the JVM environment.
	 * @throws EncodingFailedException If a serialization or security issue occurs.
	 * @throws SerializationException If a serialization or security issue occurs.
	 * @throws DecodingFailedException If a serialization or security issue occurs.
	 * @throws L2pSecurityException If a serialization or security issue occurs.
	 * @throws StorageException If an exception with the shared storage occurs.
	 */
	private boolean storeFileReal(StoredFile file) throws UnsupportedEncodingException, EncodingFailedException,
			SerializationException, DecodingFailedException, L2pSecurityException, StorageException {
		boolean created = false;
		// XXX split file into smaller parts (max. 1MB) for better network performance
		// limit (configurable) file size
		if (file.getContent() != null && file.getContent().length > maxFileSizeMB * 1000000) {
			throw new StorageException("File too big! Maximum size: " + maxFileSizeMB + " MB");
		}
		Agent owner = getContext().getMainAgent();
		// fetch or create envelope by file identifier
		Envelope env = null;
		try {
			env = getContext().getStoredObject(StoredFile.class, ENVELOPE_BASENAME + file.getIdentifier());
		} catch (Exception e) {
			logger.info("File (" + file.getIdentifier() + ") not found. Creating new one. " + e.toString());
			env = Envelope.createClassIdEnvelope(file, ENVELOPE_BASENAME + file.getIdentifier(), owner);
			created = true;
		}
		// update envelope content
		env.open(owner);
		env.updateContent(file);
		env.addSignature(owner);
		// store envelope
		env.store();
		env.close();
		logger.info("stored file (" + file.getIdentifier() + ") in network storage");
		return created;
	}

	// TODO add delete file interface

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
		return getFile(identifier, "inline");
	}

	/**
	 * This web API method downloads a file from the las2peer network. The file content is returned as binary content.
	 * 
	 * @param identifier A unqiue name or hash value to identify the file.
	 * @return Returns the file content or an error response if an error occurred.
	 */
	@GET
	@Path("/download/{identifier}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("identifier") String identifier) {
		return getFile(identifier, "attachment");
	}

	private HttpResponse getFile(String identifier, String responseMode) {
		try {
			StoredFile file = fetchFileReal(identifier);
			// set binary file content as response body
			HttpResponse response = new HttpResponse(file.getContent(), HttpURLConnection.HTTP_OK);
			// set headers
			response.setHeader(HEADER_CONTENT_DISPOSITION, responseMode + escapeFilename(file.getName()));
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
		// parse given multipart form data
		String identifier = null;
		String filename = null;
		byte[] filecontent = null;
		String mimeType = null;
		String description = null;
		try {
			Map<String, FormDataPart> parts = MultipartHelper.getParts(formData, contentType);
			FormDataPart partFilecontent = parts.get("filecontent");
			if (partFilecontent != null) {
				// these data belong to the file input form element
				filename = partFilecontent.getHeader(HEADER_CONTENT_DISPOSITION).getParameter("filename");
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
		try {
			boolean created = storeFile(identifier, filename, filecontent, mimeType, description);
			int code = HttpURLConnection.HTTP_OK;
			if (created) {
				code = HttpURLConnection.HTTP_CREATED;
			}
			// return the complete URL for this resource
			String uri = "/fileservice" + RESOURCE_BASENAME + "/" + identifier;
			if (hostname != null && !hostname.isEmpty()) {
				uri = hostname + uri;
			}
			return new HttpResponse(uri, code);
		} catch (UnsupportedEncodingException | EncodingFailedException | DecodingFailedException
				| SerializationException | L2pSecurityException | StorageException e) {
			logger.log(Level.SEVERE, "File upload failed!", e);
			return new HttpResponse("File (" + identifier + ") upload failed! See log for details.",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Methods required by the las2peer framework.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * This method is needed for every RESTful application in las2peer. There is no need to change!
	 * 
	 * @return the mapping
	 */
	public String getRESTMapping() {
		String result = "";
		try {
			result = RESTMapper.getMethodsAsXML(this.getClass());
		} catch (Exception e) {
			// write error to logfile and console
			logger.log(Level.SEVERE, e.toString(), e);
			// create and publish a monitoring message
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return result;
	}

}
