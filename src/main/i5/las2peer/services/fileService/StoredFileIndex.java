package i5.las2peer.services.fileService;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import net.minidev.json.JSONObject;

public class StoredFileIndex implements Serializable {

	private static final long serialVersionUID = 2L;

	// data types optimized for serialization
	private String identifier;
	private String name;
	private long lastModified;
	private String mimeType;
	private String ownerId;
	private String description;
	private long fileSize;

	public StoredFileIndex(String identifier, String name, long lastModified, String ownerId, String mimeType,
			String description, long fileSize) throws NullPointerException, IllegalArgumentException {
		// validate input
		if (identifier == null) {
			throw new NullPointerException("file identifier must not be null");
		}
		if (identifier.isEmpty()) {
			throw new IllegalArgumentException("file identifier must not be empty");
		}
		this.setIdentifier(identifier);
		this.setName(name);
		this.setLastModified(lastModified);
		this.setOwnerId(ownerId);
		this.setMimeType(mimeType);
		this.setDescription(description);
		this.setFileSize(fileSize);
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public String getName() {
		return name;
	}

	public void setName(String filename) {
		this.name = filename;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
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

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public Map<String, Object> toMap() {
		HashMap<String, Object> result = new HashMap<>();
		result.put("identifier", getIdentifier());
		result.put("name", getName());
		result.put("lastModified", getLastModified());
		result.put("mimeType", getMimeType());
		result.put("ownerId", getOwnerId());
		result.put("description", getDescription());
		result.put("fileSize", getFileSize());
		return result;
	}

	public JSONObject toJsonObject() {
		JSONObject result = new JSONObject();
		result.putAll(toMap());
		return result;
	}

	public static class StoredFileIndexComparator implements Comparator<StoredFileIndex> {

		public static final StoredFileIndexComparator INSTANCE = new StoredFileIndexComparator();

		@Override
		public int compare(StoredFileIndex o1, StoredFileIndex o2) {
			if (o1 == o2) {
				return 0;
			} else if (o1 == null && o2 != null) {
				return -1;
			} else if (o1 != null && o2 == null) {
				return 1;
			} else {
				return o1.getIdentifier().compareTo(o2.getIdentifier());
			}
		}

	}

}
