package i5.las2peer.services.fileService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringEscapeUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import i5.las2peer.api.Context;
import i5.las2peer.api.ServiceException;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeAccessDeniedException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.persistency.EnvelopeOperationFailedException;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentAccessDeniedException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.api.security.GroupAgent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.services.fileService.StoredFileIndex.StoredFileIndexComparator;
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
@ServicePath("/fileservice")
public class FileService extends RESTService {

	public static final String API_VERSION = "1.0";

	// non HTTP standard headers
	public static final String HEADER_OWNERID = "ownerid";
	public static final String HEADER_CONTENT_DESCRIPTION = "Content-Description";

	// upload request form field names
	public static final String UPLOAD_IDENTIFIER = "identifier";
	public static final String UPLOAD_FILE = "filecontent";
	public static final String UPLOAD_SHARE_WITH_GROUP = "sharewithgroup";
	public static final String UPLOAD_EXCLUDE_FROM_INDEX = "excludefromindex";
	public static final String UPLOAD_DESCRIPTION = "description";

	// instantiate the logger class
	private static final L2pLogger logger = L2pLogger.getInstance(FileService.class.getName());

	private static final String ENVELOPE_BASENAME = "file-";
	private static final SimpleDateFormat RFC2822FMT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z (zzz)");
	private static final String RESOURCE_FILES_BASENAME = "/files";
	private static final String RESOURCE_DOWNLOAD_BASENAME = "/download";
	private static final String INDEX_IDENTIFIER_PREFIX = "index-";
	private static final String RESOURCE_INDEX_JSON = "/index.json";
	private static final String RESOURCE_INDEX_HTML = "/index.html";
	private static final SimpleDateFormat HTML_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	// configurable properties
	public static final long MAX_FILE_SIZE_MB = 5; // MegaByte

	@Override
	protected void initResources() {
		getResourceConfig().register(ResourceFiles.class);
		getResourceConfig().register(ResourceDownload.class);
		getResourceConfig().register(ResourceIndex.class);
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
	 * @throws EnvelopeAccessDeniedException If the main agent is not able to access the envelope.
	 * @throws EnvelopeNotFoundException If the envelope doesn not exist.
	 * @throws EnvelopeOperationFailedException If an error occurred at the node or in the network.
	 */
	public Map<String, Object> fetchFile(String identifier)
			throws EnvelopeAccessDeniedException, EnvelopeNotFoundException, EnvelopeOperationFailedException {
		StoredFile file = fetchFileReal(identifier);
		return file.toMap();
	}

	private StoredFile fetchFileReal(String identifier)
			throws EnvelopeAccessDeniedException, EnvelopeNotFoundException, EnvelopeOperationFailedException {
		// fetch envelope by file identifier
		Envelope env = Context.get().requestEnvelope(ENVELOPE_BASENAME + identifier);
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
	 * @throws AgentAccessDeniedException If the main agent cannot access the fetched agent.
	 * @throws AgentOperationFailedException If an error occurred on the node.
	 * @throws IllegalArgumentException If an argument is invalid
	 * @throws EnvelopeAccessDeniedException If the main agent is not able to access the envelope
	 * @throws EnvelopeOperationFailedException If an error occurred at the node or in the network
	 * @throws NullPointerException If the given content is {@code null}
	 * @throws ServiceException If the service is not started yet
	 */
	public boolean storeFile(String identifier, String filename, byte[] content, String mimeType, String description)
			throws AgentAccessDeniedException, AgentOperationFailedException, IllegalArgumentException,
			EnvelopeAccessDeniedException, EnvelopeOperationFailedException, NullPointerException, ServiceException {
		return storeFile(identifier, filename, content, mimeType, null, description);
	}

	/**
	 * This method is intended to be used by other services for invocation. It uses only default types and classes.
	 * Files are added to the file index listing.
	 * 
	 * @param identifier A required unique name or hash value to identify this file.
	 * @param filename An optional human readable filename.
	 * @param content Actual file content. (required)
	 * @param mimeType The optional mime type for this file. Also set as header value in the web interface on download.
	 * @param shareWithGroup An optional group id to share the file with. Gives write permission to this group.
	 *            Therefore the active agent must be member of this group.
	 * @param description An optional description for the file.
	 * @return Returns true if the file was created and didn't exist before.
	 * @throws AgentAccessDeniedException If the main agent cannot access the fetched agent.
	 * @throws AgentOperationFailedException If an error occurred on the node.
	 * @throws IllegalArgumentException If an argument is invalid
	 * @throws EnvelopeAccessDeniedException If the main agent is not able to access the envelope
	 * @throws EnvelopeOperationFailedException If an error occurred at the node or in the network
	 * @throws NullPointerException If the given content is {@code null}
	 * @throws ServiceException If the service is not started yet
	 */
	public boolean storeFile(String identifier, String filename, byte[] content, String mimeType, String shareWithGroup,
			String description)
			throws AgentAccessDeniedException, AgentOperationFailedException, IllegalArgumentException,
			EnvelopeAccessDeniedException, EnvelopeOperationFailedException, NullPointerException, ServiceException {
		return storeFile(identifier, filename, content, mimeType, shareWithGroup, description, true);
	}

	/**
	 * This method is intended to be used by other services for invocation. It uses only default types and classes.
	 * 
	 * @param identifier A required unique name or hash value to identify this file.
	 * @param filename An optional human readable filename.
	 * @param content Actual file content. (required)
	 * @param mimeType The optional mime type for this file. Also set as header value in the web interface on download.
	 * @param shareWithGroup An optional group id to share the file with. Gives write permission to this group.
	 *            Therefore the active agent must be member of this group.
	 * @param description An optional description for the file.
	 * @param listFileOnIndex If true (default) the file is listed in the publicly viewable file index listing.
	 * @return Returns true if the file was created and didn't exist before.
	 * @throws AgentAccessDeniedException If the main agent cannot access the fetched agent.
	 * @throws AgentOperationFailedException If an error occurred on the node.
	 * @throws IllegalArgumentException If an argument is invalid
	 * @throws EnvelopeAccessDeniedException If the main agent is not able to access the envelope
	 * @throws EnvelopeOperationFailedException If an error occurred at the node or in the network
	 * @throws NullPointerException If the given content is {@code null}
	 * @throws ServiceException If the service is not started yet
	 */
	public boolean storeFile(String identifier, String filename, byte[] content, String mimeType, String shareWithGroup,
			String description, boolean listFileOnIndex)
			throws AgentAccessDeniedException, AgentOperationFailedException, IllegalArgumentException,
			EnvelopeAccessDeniedException, EnvelopeOperationFailedException, NullPointerException, ServiceException {
		Agent owner = Context.get().getMainAgent();
		if (shareWithGroup != null && !shareWithGroup.isEmpty()) {
			try {
				Agent shareGroup = Context.get().requestAgent(shareWithGroup);
				if (!(shareGroup instanceof GroupAgent)) {
					throw new IllegalArgumentException("Can not share file with non group agent '" + shareWithGroup
							+ "' (" + shareGroup.getClass().getCanonicalName() + ")");
				}
				owner = shareGroup;
			} catch (AgentNotFoundException e) {
				throw new IllegalArgumentException("Can not share with (" + shareWithGroup + "). Agent not found.");
			}
		}
		return storeFileReal(owner, new StoredFile(identifier, filename, content, new Date().getTime(),
				owner.getIdentifier(), mimeType, description), listFileOnIndex);
	}

	private boolean storeFileReal(Agent owner, StoredFile file, boolean listFileOnIndex)
			throws IllegalArgumentException, EnvelopeAccessDeniedException, EnvelopeOperationFailedException,
			ServiceException {
		boolean created = false;
		// limit (configurable) file size
		if (file.getContent() != null && file.getContent().length > MAX_FILE_SIZE_MB * 1000000) {
			throw new IllegalArgumentException("File too big! Maximum size: " + MAX_FILE_SIZE_MB + " MB");
		}
		// fetch or create envelope by file identifier
		Envelope fileEnv = null;
		try {
			fileEnv = Context.get().requestEnvelope(ENVELOPE_BASENAME + file.getIdentifier());
		} catch (EnvelopeNotFoundException e) {
			logger.info("File (" + file.getIdentifier() + ") not found. Creating new one. " + e.toString());
			fileEnv = Context.get().createEnvelope(ENVELOPE_BASENAME + file.getIdentifier(), owner);
			created = true;
		}
		// update envelope content
		fileEnv.setPublic();
		fileEnv.setContent(file);
		// store envelope with file content
		Context.get().storeEnvelope(fileEnv, owner);
		if (listFileOnIndex) {
			StoredFileIndex indexEntry = new StoredFileIndex(file.getIdentifier(), file.getName(),
					file.getLastModified(), file.getOwnerId(), file.getMimeType(), file.getDescription(),
					file.getFileSize());
			// fetch or create file index envelope
			Envelope indexEnv;
			StoredFileIndexList fileIndex;
			try {
				indexEnv = Context.get().requestEnvelope(getIndexIdentifier(), getAgent());
				fileIndex = (StoredFileIndexList) indexEnv.getContent();
				// remove old entries
				Iterator<StoredFileIndex> itIndex = fileIndex.iterator();
				while (itIndex.hasNext()) {
					StoredFileIndex index = itIndex.next();
					if (indexEntry.getIdentifier().equalsIgnoreCase(index.getIdentifier())) {
						itIndex.remove();
					}
				}
			} catch (EnvelopeNotFoundException e) {
				logger.info("Index not found. Creating new one.");
				indexEnv = Context.get().createEnvelope(getIndexIdentifier(), getAgent());
				fileIndex = new StoredFileIndexList();
			}
			// update file index
			fileIndex.add(indexEntry);
			indexEnv.setContent(fileIndex);
			// store index envelope
			Context.get().storeEnvelope(indexEnv, getAgent());
		}
		logger.info("stored file (" + file.getIdentifier() + ") in network storage");
		return created;
	}

	@Api(
			tags = { "files" })
	@SwaggerDefinition(
			info = @Info(
					title = "las2peer File Service",
					version = API_VERSION,
					description = "A las2peer file service for demonstration purposes.",
					contact = @Contact(
							name = "ACIS Group",
							url = "https://las2peer.org/",
							email = "cuje@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-FileService/blob/master/LICENSE")))
	@Path(RESOURCE_FILES_BASENAME)
	public static class ResourceFiles {

		/**
		 * This operation is not yet supported by las2peer.
		 * 
		 * @param identifier A required unique name or hash value to identify this file.
		 * @return Returns an HTTP status code and message with the result of the upload request.
		 */
		@DELETE
		@Path("/{identifier}")
		@Produces(MediaType.TEXT_PLAIN)
		public Response deleteFile(@PathParam("identifier") String identifier) {
			return Response.status(Status.NOT_IMPLEMENTED).build();
		}

		/**
		 * This web API method downloads a file from the las2peer network. The file content is returned as binary
		 * content.
		 * 
		 * @param paths A list path segments or at least a single identifier to identify the file.
		 * @return Returns the file content as inline element for website integration or an error response if an error
		 *         occurred.
		 */
		@GET
		@Path("/{paths: .+}")
		public Response getFile(@PathParam("paths") List<PathSegment> paths) {
			if (paths.size() < 1) {
				throw new BadRequestException("No file identifier given");
			}
			String identifier = paths.stream().map(PathSegment::getPath).collect(Collectors.joining("/"));
			FileService service = (FileService) Context.getCurrent().getService();
			return service.getFile(identifier, false);
		}

		/**
		 * This method uploads a file to the las2peer network.
		 * 
		 * @param identifier Value of the {@value i5.las2peer.services.fileService.FileService#UPLOAD_IDENTIFIER}-tagged
		 *            form/request element.
		 * @param fileContentHeader The header of the submitted file used to determine the filename.
		 * @param bodyPart The body part of the submitted file used to determine the mime type.
		 * @param fileContent The actual submitted file content.
		 * @param shareWithGroup The given value is interpreted as agent id and the agent gets exclusively read
		 *            permission.
		 * @param description A descriptive text used to describe the file.
		 * @param excludeFromIndex If set to "true" or "on" as most browsers do the file is NOT listed in the global
		 *            file index.
		 * @return Returns an HTTP status code and message with the result of the upload request.
		 */
		@POST
		@Produces(MediaType.APPLICATION_JSON)
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
		public Response postFile(@FormDataParam(UPLOAD_IDENTIFIER) String identifier,
				@FormDataParam(UPLOAD_FILE) FormDataContentDisposition fileContentHeader,
				@FormDataParam(UPLOAD_FILE) FormDataBodyPart bodyPart,
				@FormDataParam(UPLOAD_FILE) InputStream fileContent,
				@FormDataParam(UPLOAD_SHARE_WITH_GROUP) String shareWithGroup,
				@FormDataParam(UPLOAD_DESCRIPTION) String description,
				@FormDataParam(UPLOAD_EXCLUDE_FROM_INDEX) String excludeFromIndex) {
			FileService service = (FileService) Context.getCurrent().getService();
			return service.uploadFile(identifier, fileContentHeader, bodyPart, fileContent, shareWithGroup, description,
					excludeFromIndex, false);
		}

		/**
		 * This method uploads a file to the las2peer network.
		 * 
		 * @param identifier Value of the {@value i5.las2peer.services.fileService.FileService#UPLOAD_IDENTIFIER}-tagged
		 *            form/request element.
		 * @param fileContentHeader The header of the submitted file used to determine the filename.
		 * @param bodyPart The body part of the submitted file used to determine the mime type.
		 * @param fileContent The actual submitted file content.
		 * @param shareWithGroup The given value is interpreted as agent id and the agent gets exclusively read
		 *            permission.
		 * @param description A descriptive text used to describe the file.
		 * @param excludeFromIndex If set to "true" or "on" as most browsers do the file is NOT listed in the global
		 *            file index.
		 * @return Returns an HTTP status code and message with the result of the upload request.
		 */
		@PUT
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
		public Response putFile(@FormDataParam(UPLOAD_IDENTIFIER) String identifier,
				@FormDataParam(UPLOAD_FILE) FormDataContentDisposition fileContentHeader,
				@FormDataParam(UPLOAD_FILE) FormDataBodyPart bodyPart,
				@FormDataParam(UPLOAD_FILE) InputStream fileContent,
				@FormDataParam(UPLOAD_SHARE_WITH_GROUP) String shareWithGroup,
				@FormDataParam(UPLOAD_DESCRIPTION) String description,
				@FormDataParam(UPLOAD_EXCLUDE_FROM_INDEX) String excludeFromIndex) {
			FileService service = (FileService) Context.getCurrent().getService();
			// a file identifier is a enforced for put operation
			return service.uploadFile(identifier, fileContentHeader, bodyPart, fileContent, shareWithGroup, description,
					excludeFromIndex, false);
		}

	}

	@Api(
			tags = { "download" })
	@SwaggerDefinition(
			info = @Info(
					title = "las2peer File Service",
					version = API_VERSION,
					description = "A las2peer file service for demonstration purposes.",
					contact = @Contact(
							name = "ACIS Group",
							url = "https://las2peer.org/",
							email = "cuje@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-FileService/blob/master/LICENSE")))
	@Path(RESOURCE_DOWNLOAD_BASENAME)
	public static class ResourceDownload {

		/**
		 * This web API method downloads a file from the las2peer network. The file content is returned as binary
		 * content.
		 * 
		 * @param paths A list path segments or at least a single identifier to identify the file.
		 * @return Returns the file content or an error response if an error occurred.
		 */
		@GET
		@Path(RESOURCE_DOWNLOAD_BASENAME + "/{paths: .+}")
		public Response downloadFile(@PathParam("identifier") List<PathSegment> paths) {
			if (paths.size() < 1) {
				throw new BadRequestException("No file identifier given");
			}
			String identifier = "";
			for (PathSegment seg : paths) {
				identifier = String.join("/", identifier, seg.getPath());
			}
			FileService service = (FileService) Context.getCurrent().getService();
			return service.getFile(identifier, true);
		}

	}

	private Response getFile(String identifier, boolean attachment) {
		try {
			StoredFile file = fetchFileReal(identifier);
			// set binary file content as response body
			ResponseBuilder responseBuilder = Response.ok(file.getContent());
			// set headers
			String disposition = "inline";
			if (attachment) {
				disposition = "attachment";
			}
			responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, disposition + escapeFilename(file.getName()));
			responseBuilder.header(HttpHeaders.LAST_MODIFIED, RFC2822FMT.format(new Date(file.getLastModified())));
			responseBuilder.header(HttpHeaders.CONTENT_TYPE, file.getMimeType());
			// following some non HTTP standard header fields
			responseBuilder.header(HEADER_OWNERID, file.getOwnerId());
			responseBuilder.header(HEADER_CONTENT_DESCRIPTION, file.getDescription());
			return responseBuilder.build();
		} catch (EnvelopeNotFoundException e) {
			logger.log(Level.INFO, "File (" + identifier + ") not found!", e);
			return Response.status(Status.NOT_FOUND).build();
		} catch (EnvelopeAccessDeniedException e) {
			logger.log(Level.INFO, e.toString(), e);
			return Response.status(Status.FORBIDDEN).entity(e.toString()).build();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Can't read file (" + identifier + ") content from network storage! ", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
				logger.log(Level.WARNING, e.toString(), e);
			}
		}
		return result;
	}

	private Response uploadFile(String identifier, FormDataContentDisposition fileContentHeader,
			FormDataBodyPart bodyPart, InputStream fileContentStream, String shareWithGroup, String description,
			String excludeFromIndex, boolean enforceIdentifier) {
		if (fileContentStream == null) {
			return Response.status(Status.BAD_REQUEST).entity("File upload failed! No form data at all.").build();
		}
		try {
			String filename = null;
			if (fileContentHeader != null) {
				filename = fileContentHeader.getFileName();
			}
			String mimeType = null;
			if (bodyPart != null) {
				MediaType type = bodyPart.getMediaType();
				if (type != null) {
					mimeType = type.toString();
				}
			}
			// write file content input stream into byte buffer
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int nRead;
			byte[] data = new byte[4096];
			while ((nRead = fileContentStream.read(data, 0, data.length)) != -1) {
				if (buffer.size() < MAX_FILE_SIZE_MB * 1000000 - data.length) {
					// still space left in local buffer
					buffer.write(data, 0, nRead);
				} else {
					return Response.status(Status.REQUEST_ENTITY_TOO_LARGE)
							.entity("Given request body exceeds limit of " + MAX_FILE_SIZE_MB + " MB").build();
				}
			}
			byte[] filecontent = buffer.toByteArray();
			// validate input
			if (filecontent.length < 1
					|| new String(filecontent, StandardCharsets.UTF_8).equalsIgnoreCase("undefined")) {
				return Response.status(Status.BAD_REQUEST)
						.entity("File (" + identifier
								+ ") upload failed! No content provided. Add field 'filecontent' to your form.")
						.build();
			}
			logger.info("upload request for (" + filename + ") with mime type '" + mimeType + "' and size "
					+ filecontent.length + " bytes");
			if (identifier != null) {
				// these data belong to the (optional) identifier text input form element
				identifier = identifier.trim();
				// validate identifier
				if (identifier.contains("//")) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Invalid file identifier (" + identifier + "). Must not contain double slashes.")
							.build();
				} else if (identifier.startsWith("/")) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Invalid file identifier (" + identifier + "). Must not start with slash.").build();
				} else if (identifier.endsWith("/")) {
					return Response.status(Status.BAD_REQUEST)
							.entity("Invalid file identifier (" + identifier + "). Must not end with slash.").build();
				}
			}
			// optional hide from index
			boolean listFileOnIndex = true;
			if ("on".equalsIgnoreCase(excludeFromIndex) || "true".equalsIgnoreCase(excludeFromIndex)) {
				listFileOnIndex = false;
			}
			// enforce identifier for PUT operations
			if (enforceIdentifier && (identifier == null || identifier.isEmpty())) {
				return Response.status(Status.BAD_REQUEST).entity("No identfier provided").build();
			}
			// if the form doesn't especially describe a identifier, use (hashed?) filename as fallback
			if ((identifier == null || identifier.isEmpty()) && filename != null && !filename.isEmpty()) {
				logger.info("No file identifier provided using hashed filename as fallback");
				identifier = Long.toString(SimpleTools.longHash(filename));
			}
			int code = HttpURLConnection.HTTP_OK;
			try {
				boolean created = storeFile(identifier, filename, filecontent, mimeType, shareWithGroup, description,
						listFileOnIndex);
				if (created) {
					code = HttpURLConnection.HTTP_CREATED;
				}
			} catch (IllegalArgumentException e) {
				logger.log(Level.SEVERE, "File upload failed!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
			} catch (EnvelopeAccessDeniedException e) {
				logger.log(Level.SEVERE, "File upload failed!", e);
				return Response.status(Status.FORBIDDEN).entity("403 - Forbidden\n" + e.toString() + "\nFile ("
						+ identifier + ") upload failed! See log for details.").build();
			}
			return Response.status(code).entity(identifier).build();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "File upload failed!", e);
			return Response.status(Status.INTERNAL_SERVER_ERROR)
					.entity("File (" + identifier + ") upload failed! See log for details.").build();
		}
	}

	@Api(
			tags = { "index" })
	@SwaggerDefinition(
			info = @Info(
					title = "las2peer File Service",
					version = API_VERSION,
					description = "A las2peer file service for demonstration purposes.",
					contact = @Contact(
							name = "ACIS Group",
							url = "https://las2peer.org/",
							email = "cuje@dbis.rwth-aachen.de"),
					license = @License(
							name = "ACIS License (BSD3)",
							url = "https://github.com/rwth-acis/las2peer-FileService/blob/master/LICENSE")))
	@Path("")
	public static class ResourceIndex {

		@GET
		@Path(RESOURCE_INDEX_JSON)
		@Produces(MediaType.APPLICATION_JSON)
		public Response getFileIndexJson() {
			FileService service = (FileService) Context.getCurrent().getService();
			try {
				// transform index list into JSON
				JSONArray indexJson = new JSONArray();
				for (StoredFileIndex index : service.getFileIndexReal()) {
					indexJson.add(index.toJsonObject());
				}
				return Response.ok(indexJson.toJSONString(), MediaType.APPLICATION_JSON).build();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Could not read file index!", e);
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("Could not read file index! See log for details.").build();
			}
		}

		@GET
		@Path(RESOURCE_INDEX_HTML)
		@Produces(MediaType.TEXT_HTML)
		public Response getFileIndexHtml() {
			FileService service = (FileService) Context.getCurrent().getService();
			try {
				// transform index list into HTML
				StringBuilder sb = new StringBuilder();
				sb.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
				sb.append("<html>\n");
				sb.append("\t<head>\n");
				sb.append("\t\t<meta charset=\"utf-8\">");
				sb.append("\t\t<title>").append(service.getAgent().getServiceNameVersion().toString())
						.append("</title>\n");
				sb.append("</head>\n");
				sb.append("<body>\n");
				sb.append("<h1>Index of " + service.getAgent().getServiceNameVersion().toString() + "</h1>\n");
				sb.append("<table>\n");
				sb.append("<tr>").append("<th>Identifier</th><th></th><th>Name</th>").append("<th>Last modified</th>")
						.append("<th>Size</th>").append("<th>Description</th>").append("<th></th>").append("</tr>");
				sb.append("<tr><th colspan=\"7\"><hr></th></tr>\n");
				for (StoredFileIndex index : service.getFileIndexReal()) {
					sb.append("<tr>");
					String basename = cleanSlashes(RESOURCE_FILES_BASENAME).substring(1);
					String identifier = cleanSlashes(index.getIdentifier());
					sb.append("<td><a href=\"" + basename + identifier + "\">" + identifier + "</a></td>");
					String download = cleanSlashes(RESOURCE_DOWNLOAD_BASENAME).substring(1);
					sb.append("<td><a href=\"" + download + identifier + "\">[&#8595;]</a></td>");
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
				return Response.ok(sb.toString(), MediaType.TEXT_HTML).build();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Could not read file index!", e);
				return Response.status(Status.INTERNAL_SERVER_ERROR)
						.entity("Could not read file index! See log for details.").build();
			}
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

	public ArrayList<Map<String, Object>> getFileIndex()
			throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException, ServiceException {
		ArrayList<Map<String, Object>> result = new ArrayList<>();
		StoredFileIndexList fileIndex = getFileIndexReal();
		for (StoredFileIndex index : fileIndex) {
			result.add(index.toMap());
		}
		return result;
	}

	private StoredFileIndexList getFileIndexReal()
			throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException, ServiceException {
		try {
			Envelope storedIndex = Context.get().requestEnvelope(getIndexIdentifier(), getAgent());
			StoredFileIndexList indexList = (StoredFileIndexList) storedIndex.getContent();
			indexList.sort(StoredFileIndexComparator.INSTANCE);
			return indexList;
		} catch (EnvelopeNotFoundException e) {
			return new StoredFileIndexList();
		}
	}

	private String getIndexIdentifier() throws ServiceException {
		return INDEX_IDENTIFIER_PREFIX + getAgent().getIdentifier();
	}

}
