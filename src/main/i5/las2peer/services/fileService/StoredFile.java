package i5.las2peer.services.fileService;

import java.io.Serializable;

/**
 * This class is used internally to represent a file stored in the las2peer
 * network including its meta data.
 *
 */
public class StoredFile implements Serializable {

	private static final long serialVersionUID = 1L;

	// data types optimized for serialization
	private String identifier;
	private byte[] content;
	private long lastModified;
	private long ownerId;
	private String mimeType;
	private String description;

	public StoredFile(String identifier, byte[] content, long lastModified, long ownerId, String mimeType,
			String description) {
		this.setIdentifier(identifier);
		this.setContent(content);
		this.setLastModified(lastModified);
		this.setOwnerId(ownerId);
		this.setMimeType(mimeType);
		this.setDescription(description);
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(long ownerId) {
		this.ownerId = ownerId;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
