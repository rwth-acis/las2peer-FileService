package i5.las2peer.services.fileService;

import java.util.Map;

/**
 * This class is used internally to represent a file stored in the las2peer network including its meta data.
 *
 */
public class StoredFile extends StoredFileIndex {

	private static final long serialVersionUID = 1L;

	private byte[] content;

	public StoredFile(String identifier, String name, byte[] content, long lastModified, long ownerId, String mimeType,
			String description) throws NullPointerException {
		super(identifier, name, lastModified, ownerId, mimeType, description, content != null ? content.length : 0);
		if (content == null) {
			throw new NullPointerException("content must not be null");
		}
		this.setContent(content);
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] content) {
		this.content = content;
	}

	@Override
	public Map<String, Object> toMap() {
		Map<String, Object> result = super.toMap();
		result.put("content", content);
		return result;
	}

}
