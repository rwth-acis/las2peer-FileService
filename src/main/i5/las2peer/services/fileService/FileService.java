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
	 * @param fileid
	 * @return
	 * @throws ArtifactNotFoundException
	 * @throws StorageException
	 * @throws L2pSecurityException
	 * @throws EnvelopeException
	 */
	public Map<String, Object> fetchFile(String fileid)
			throws ArtifactNotFoundException, StorageException, L2pSecurityException, EnvelopeException {
		StoredFile file = fetchFileReal(fileid);
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
	 * @param fileid
	 * @return Returns the file object or {@code null} if an error occurred.
	 * @throws StorageException
	 * @throws ArtifactNotFoundException
	 * @throws L2pSecurityException
	 * @throws EnvelopeException
	 */
	private StoredFile fetchFileReal(String fileid)
			throws ArtifactNotFoundException, StorageException, L2pSecurityException, EnvelopeException {
		Agent owner = getContext().getMainAgent();
		// fetch envelope by fileid
		Envelope env = getContext().getStoredObject(StoredFile.class, ENVELOPE_BASENAME + fileid);
		env.open(owner);
		// read content from envelope into string
		StoredFile result = env.getContent(this.getClass().getClassLoader(), StoredFile.class);
		env.close();
		return result;
	}

	/**
	 * This method is intended to be used by other services for invocation. It uses only default types and classes.
	 * 
	 * @param identifier
	 * @param filename
	 * @param content
	 * @param lastModified
	 * @param ownerId
	 * @param mimeType
	 * @param description
	 * @throws UnsupportedEncodingException
	 * @throws EncodingFailedException
	 * @throws SerializationException
	 * @throws DecodingFailedException
	 * @throws L2pSecurityException
	 * @throws StorageException
	 */
	public void storeFile(String identifier, String filename, byte[] content, String mimeType, String description)
			throws UnsupportedEncodingException, EncodingFailedException, SerializationException,
			DecodingFailedException, L2pSecurityException, StorageException {
		// validate input
		if (identifier == null) {
			throw new NullPointerException("fileid must not be null");
		}
		if (identifier.isEmpty()) {
			throw new IllegalArgumentException("fileid must not be empty");
		}
		if (content == null) {
			throw new NullPointerException("content must not be null");
		}
		storeFileReal(new StoredFile(identifier, filename, content, new Date().getTime(),
				getContext().getMainAgent().getId(), mimeType, description));
	}

	/**
	 * This method uploads a file to the las2peer network.
	 * 
	 * @param fileid
	 * @param content
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws EncodingFailedException
	 * @throws SerializationException
	 * @throws DecodingFailedException
	 * @throws L2pSecurityException
	 * @throws StorageException
	 */
	private void storeFileReal(StoredFile file) throws UnsupportedEncodingException, EncodingFailedException,
			SerializationException, DecodingFailedException, L2pSecurityException, StorageException {
		// XXX split file into smaller parts for better network performance
		// limit (configurable) file size
		if (file.getContent() != null && file.getContent().length > maxFileSizeMB * 1000000) {
			throw new StorageException("File too big! Maximum size: " + maxFileSizeMB + " MB");
		}
		Agent owner = getContext().getMainAgent();
		// fetch or create envelope by fileid
		Envelope env = null;
		try {
			env = getContext().getStoredObject(StoredFile.class, ENVELOPE_BASENAME + file.getIdentifier());
		} catch (Exception e) {
			logger.info("File (" + file.getIdentifier() + ") not found. Creating new one. " + e.toString());
			env = Envelope.createClassIdEnvelope(file, ENVELOPE_BASENAME + file.getIdentifier(), owner);
		}
		// update envelope content
		env.open(owner);
		env.updateContent(file);
		env.addSignature(owner);
		// store envelope
		env.store();
		env.close();
		logger.info("stored file (" + file.getIdentifier() + ") in network storage");
	}

	// TODO add delete file interface

	/**
	 * This web API method downloads a file from the las2peer network. The file content is returned as binary content.
	 * 
	 * @param fileid
	 * @return Returns the file content or an error response if an error occurred.
	 */
	@GET
	@Path("/files/{fileid}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse getFile(@PathParam("fileid") String fileid) {
		return getFile(fileid, "inline");
	}

	/**
	 * This web API method downloads a file from the las2peer network. The file content is returned as binary content.
	 * 
	 * @param fileid
	 * @return Returns the file content or an error response if an error occurred.
	 */
	@GET
	@Path("/download/{fileid}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse downloadFile(@PathParam("fileid") String fileid) {
		return getFile(fileid, "attachment");
	}

	private HttpResponse getFile(String fileid, String responseMode) {
		try {
			StoredFile file = fetchFileReal(fileid);
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
			logger.log(Level.INFO, "File (" + fileid + ") not found!", e);
			return new HttpResponse("404 Not Found", HttpURLConnection.HTTP_NOT_FOUND);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Can't read file (" + fileid + ") content from network storage! ", e);
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
	 * @param formData
	 * @return
	 */
	@POST
	@Path("/files")
	@Produces(MediaType.TEXT_PLAIN)
	// TODO add @APIResponses
	public HttpResponse uploadFile(@HeaderParam(
			value = HttpHeaders.CONTENT_TYPE) String contentType, @ContentParam byte[] formData) {
		// parse given multipart form data
		String fileid = null;
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
			FormDataPart partFileid = parts.get("fileid");
			if (partFileid != null) {
				// these data belong to the (optional) file id text input form element
				fileid = partFileid.getContent();
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
			return new HttpResponse("File (" + fileid + ") upload failed! See log for details.",
					HttpURLConnection.HTTP_BAD_REQUEST);
		} catch (IOException e) {
			// a read or write error occurred
			logger.log(Level.SEVERE, e.getMessage(), e);
			return new HttpResponse("File (" + fileid + ") upload failed! See log for details.",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
		// validate input
		if (filecontent == null) {
			return new HttpResponse(
					"File (" + fileid
							+ ") upload failed! No content provided. Add field named filecontent to your form.",
					HttpURLConnection.HTTP_BAD_REQUEST);
		}
		// if the form doesn't especially describe a fileid, use (hashed?) filename as fallback
		if ((fileid == null || fileid.isEmpty()) && filename != null && !filename.isEmpty()) {
			logger.info("No fileid provided using hashed filename as fallback");
			fileid = Long.toString(SimpleTools.longHash(filename));
		}
		try {
			storeFile(fileid, filename, filecontent, mimeType, description);
			// just return the fileid on success, to be used by callee
			return new HttpResponse(fileid, HttpURLConnection.HTTP_OK);
		} catch (UnsupportedEncodingException | EncodingFailedException | DecodingFailedException
				| SerializationException | L2pSecurityException | StorageException e) {
			logger.log(Level.SEVERE, "File upload failed!", e);
			return new HttpResponse("File (" + fileid + ") upload failed! See log for details.",
					HttpURLConnection.HTTP_INTERNAL_ERROR);
		}
	}

	// TODO provide PUT interface

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
