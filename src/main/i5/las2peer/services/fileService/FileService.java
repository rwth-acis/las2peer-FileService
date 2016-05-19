package i5.las2peer.services.fileService;

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import i5.las2peer.api.Service;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.ArtifactNotFoundException;
import i5.las2peer.persistency.Envelope;
import i5.las2peer.restMapper.HttpResponse;
import i5.las2peer.restMapper.RESTMapper;
import i5.las2peer.restMapper.annotations.ContentParam;
import i5.las2peer.restMapper.annotations.Version;
import i5.las2peer.security.Agent;
import io.swagger.annotations.Api;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

/**
 * las2peer File Service
 * 
 * This is an example service to store files in the las2peer network. One can
 * upload and download files using the service also via the WebConnector.
 * 
 */
@Path("/fileservice")
@Version("1.0") // this annotation is used by the XML mapper
@Api
@SwaggerDefinition(info = @Info(title = "las2peer File Service", version = "1.0", description = "A las2peer file service for demonstration purposes.", termsOfService = "http://your-terms-of-service-url.com", contact = @Contact(name = "ACIS Group", url = "las2peer.org", email = "las2peer@dbis.rwth-aachen.de"), license = @License(name = "ACIS License", url = "https://github.com/rwth-acis/las2peer-File-Service/blob/master/LICENSE")))
public class FileService extends Service {

	private static final String ENVELOPE_BASENAME = "file-";
	private static final SimpleDateFormat RFC2822FMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z (zzz)");

	// instantiate the logger class
	private final L2pLogger logger = L2pLogger.getInstance(FileService.class.getName());

	public FileService() {
		// read and set properties values
		setFieldValues();
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * This method downloads a file from the las2peer network.
	 * 
	 * @param fileid
	 * @return Returns the file object or {@code null} if an error occurred.
	 */
	public StoredFile fetchFile(String fileid) {
		try {
			Agent owner = getContext().getMainAgent();
			// fetch envelope by fileid
			Envelope env = getContext().getStoredObject(StoredFile.class, ENVELOPE_BASENAME + fileid);
			env.open(owner);
			// read content from envelope into string
			StoredFile result = env.getContent(this.getClass().getClassLoader(), StoredFile.class);
			env.close();
			return result;
		} catch (ArtifactNotFoundException e) {
			logger.log(Level.INFO, "File (" + fileid + ") not found!");
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Can't read file (" + fileid + ") content from network storage! ", e);
		}
		return null;
	}

	/**
	 * This method uploads a file to the las2peer network.
	 * 
	 * @param fileid
	 * @param content
	 * @return
	 */
	public boolean storeFile(StoredFile file) {
		// XXX split file into smaller parts for better network performance
		// FIXME limit (configurable) file size
		try {
			Agent owner = getContext().getMainAgent();
			// fetch or create envelope by fileid
			Envelope env = null;
			try {
				env = getContext().getStoredObject(StoredFile.class, ENVELOPE_BASENAME + file.getIdentifier());
			} catch (Exception e) {
				logger.info("File (" + file.getIdentifier() + ") not found. Creating new one. " + e.toString());
				env = Envelope.createClassIdEnvelope("", ENVELOPE_BASENAME + file.getIdentifier(), owner);
			}
			// update envelope content
			env.open(owner);
			env.updateContent(file);
			env.addSignature(owner);
			// store envelope
			env.store();
			env.close();
			logger.info("stored file (" + file.getIdentifier() + ") in network storage");
			return true;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Can't persist file (" + file.getIdentifier() + ") content to network storage! ",
					e);
		}
		return false;
	}

	/**
	 * This web API method downloads a file from the las2peer network. The file
	 * content is returned as BASE64 encoded string.
	 * 
	 * @param fileid
	 * @return Returns the file content or an empty String if an error occurred.
	 */
	@GET
	@Path("/files/{fileid}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public HttpResponse fetchFileContent(@PathParam("fileid") String fileid) {
		StoredFile file = fetchFile(fileid);
		if (file != null) {
			// FIXME set file content in response
			HttpResponse response = new HttpResponse("", HttpURLConnection.HTTP_OK);
			// FIXME set headers
			response.setHeader("Last-Modified", RFC2822FMT.format(new Date(file.getLastModified())));
			return response;
		}
		return new HttpResponse("", HttpURLConnection.HTTP_NOT_FOUND);
	}

	/**
	 * This method uploads a file to the las2peer network.
	 * 
	 * @param formData
	 * @return
	 */
	@POST
	@Path("/files/{fileid}")
	@Produces(MediaType.TEXT_PLAIN)
	public HttpResponse storeFileWeb(@ContentParam String formData) {
		System.out.println(formData);
		// FIXME parse given form data
		String fileid = "";
		byte[] content = new byte[] {};
		long lastModified = 0;
		long ownerId = 0;
		String mimeType = null;
		String description = null;
		StoredFile file = new StoredFile(fileid, content, lastModified, ownerId, mimeType, description);
		// if the form doesn't especially describe a fileid, use filename
		if (storeFile(file)) {
			return new HttpResponse("File (" + fileid + ") upload successfull", HttpURLConnection.HTTP_OK);
		}
		return new HttpResponse("File (" + fileid + ") upload failed! See log for details.",
				HttpURLConnection.HTTP_INTERNAL_ERROR);
	}

	// TODO handle put requests

	// //////////////////////////////////////////////////////////////////////////////////////
	// Methods required by the las2peer framework.
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * This method is needed for every RESTful application in las2peer. There is
	 * no need to change!
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
