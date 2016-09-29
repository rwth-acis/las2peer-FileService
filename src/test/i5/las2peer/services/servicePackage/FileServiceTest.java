package i5.las2peer.services.servicePackage;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.fileService.FileService;
import i5.las2peer.testing.TestSuite;

public class FileServiceTest {

	private static final String TEST_IDENTIFIER = "helloworld.txt";
	private static final String TEST_NAME = "test file";
	private static final byte[] TEST_CONTENT = "Hello World!".getBytes(StandardCharsets.UTF_8);
	private static final String TEST_MIME = "text/plain";
	private static final String TEST_DESCRIPTION = "just a test file";

	private List<PastryNodeImpl> nodes;

	@Before
	public void startNetwork() throws Exception {
		System.out.println("starting network...");
		nodes = TestSuite.launchNetwork(3);
	}

	@After
	public void stopNetwork() {
		for (PastryNodeImpl node : nodes) {
			node.shutDown();
		}
	}

	@Test
	public void testUpAndDownload() {
		try {
			// create agents
			System.out.println("creating agents...");
			ServiceAgent service = ServiceAgent.createServiceAgent(
					new ServiceNameVersion(FileService.class.getName(), "1.0"), "test-service-pass");
			UserAgent userA = UserAgent.createUserAgent("test-pass-a");

			// start service instance on node 0
			System.out.println("starting service on node 0");
			service.unlockPrivateKey("test-service-pass");
			nodes.get(0).storeAgent(service);
			nodes.get(0).registerReceiver(service);

			// UserA login at node 1
			System.out.println("user a login at node 1");
			userA.unlockPrivateKey("test-pass-a");
			nodes.get(1).storeAgent(userA);
			Mediator mediatorA = nodes.get(1).createMediatorForAgent(userA);

			// UserA uploads a file to the network
			System.out.println("uploading file");
			mediatorA.invoke(FileService.class.getName(), "storeFile",
					new Serializable[] { TEST_IDENTIFIER, TEST_NAME, TEST_CONTENT, TEST_MIME, TEST_DESCRIPTION },
					false);

			// UserA downloads the file from the network
			System.out.println("downloading file");
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) mediatorA.invoke(FileService.class.getName(), "fetchFile",
					new Serializable[] { TEST_IDENTIFIER }, false);

			// validate fetched file
			Assert.assertEquals(TEST_IDENTIFIER, map.get("identifier"));
			Assert.assertEquals(TEST_NAME, map.get("name"));
			Assert.assertArrayEquals(TEST_CONTENT, (byte[]) map.get("content"));
			Assert.assertEquals(TEST_MIME, map.get("mimeType"));
			Assert.assertEquals(userA.getId(), map.get("ownerId"));
			Assert.assertEquals(TEST_DESCRIPTION, map.get("description"));
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Test
	public void testFileIndex() {
		try {
			// create agents
			System.out.println("creating agents...");
			ServiceAgent service = ServiceAgent.createServiceAgent(
					new ServiceNameVersion(FileService.class.getName(), "1.0"), "test-service-pass");
			UserAgent userA = UserAgent.createUserAgent("test-pass-a");

			// start service instance on node 0
			System.out.println("starting service on node 0");
			service.unlockPrivateKey("test-service-pass");
			nodes.get(0).storeAgent(service);
			nodes.get(0).registerReceiver(service);

			// UserA login at node 1
			System.out.println("user a login at node 1");
			userA.unlockPrivateKey("test-pass-a");
			nodes.get(1).storeAgent(userA);
			Mediator mediatorA = nodes.get(1).createMediatorForAgent(userA);

			// UserA uploads a file to the network
			System.out.println("uploading file");
			mediatorA.invoke(FileService.class.getName(), "storeFile",
					new Serializable[] { TEST_IDENTIFIER, TEST_NAME, TEST_CONTENT, TEST_MIME, TEST_DESCRIPTION },
					false);

			// get the file index and verify it
			System.out.println("fetching file index");
			@SuppressWarnings("unchecked")
			ArrayList<Map<String, Object>> result = (ArrayList<Map<String, Object>>) mediatorA
					.invoke(FileService.class.getName(), "getFileIndex", new Serializable[] {}, false);
			Assert.assertTrue(result.size() == 1);
			Map<String, Object> map = result.get(0);
			Assert.assertEquals(TEST_IDENTIFIER, map.get("identifier"));
			Assert.assertEquals(TEST_NAME, map.get("name"));
			Assert.assertEquals(TEST_MIME, map.get("mimeType"));
			Assert.assertEquals(userA.getId(), map.get("ownerId"));
			Assert.assertEquals(TEST_DESCRIPTION, map.get("description"));
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

}
