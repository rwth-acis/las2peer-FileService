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

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.api.security.Agent;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.security.GroupAgentImpl;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.ServiceAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.services.fileService.FileService;
import i5.las2peer.testing.TestSuite;

public class FileServiceTest {

	private static final String TEST_IDENTIFIER = "helloworld.txt";
	private static final String TEST_NAME = "test file";
	private static final byte[] TEST_CONTENT = "Hello World!".getBytes(StandardCharsets.UTF_8);
	private static final byte[] TEST_CONTENT2 = "Hello User A!".getBytes(StandardCharsets.UTF_8);
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
			ServiceAgentImpl service = ServiceAgentImpl.createServiceAgent(
					new ServiceNameVersion(FileService.class.getName(), "1.0"), "test-service-pass");
			UserAgentImpl userA = UserAgentImpl.createUserAgent("test-pass-a");

			// start service instance on node 0
			System.out.println("starting service on node 0");
			service.unlock("test-service-pass");
			nodes.get(0).storeAgent(service);
			nodes.get(0).registerReceiver(service);

			// UserA login at node 1
			System.out.println("user a login at node 1");
			userA.unlock("test-pass-a");
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
			Assert.assertEquals(userA.getIdentifier(), map.get("ownerId"));
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
			ServiceAgentImpl service = ServiceAgentImpl.createServiceAgent(
					new ServiceNameVersion(FileService.class.getName(), "1.0"), "test-service-pass");
			UserAgentImpl userA = UserAgentImpl.createUserAgent("test-pass-a");

			// start service instance on node 0
			System.out.println("starting service on node 0");
			service.unlock("test-service-pass");
			nodes.get(0).storeAgent(service);
			nodes.get(0).registerReceiver(service);

			// UserA login at node 1
			System.out.println("user a login at node 1");
			userA.unlock("test-pass-a");
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
			Assert.assertEquals(userA.getIdentifier(), map.get("ownerId"));
			Assert.assertEquals(TEST_DESCRIPTION, map.get("description"));

			// upload file again
			System.out.println("uploading file");
			mediatorA.invoke(FileService.class.getName(), "storeFile",
					new Serializable[] { TEST_IDENTIFIER, TEST_NAME, TEST_CONTENT, TEST_MIME, TEST_DESCRIPTION },
					false);

			// verify: no duplicate index entry
			System.out.println("fetching file index");
			@SuppressWarnings("unchecked")
			ArrayList<Map<String, Object>> result2 = (ArrayList<Map<String, Object>>) mediatorA
					.invoke(FileService.class.getName(), "getFileIndex", new Serializable[] {}, false);
			Assert.assertTrue(result2.size() == 1);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

	@Test
	public void testFileShareWithGroup() {
		try {
			// create agents
			System.out.println("creating agents...");
			ServiceAgentImpl service = ServiceAgentImpl.createServiceAgent(
					new ServiceNameVersion(FileService.class.getName(), "1.0"), "test-service-pass");
			UserAgentImpl userA = UserAgentImpl.createUserAgent("test-pass-a");
			UserAgentImpl userB = UserAgentImpl.createUserAgent("test-pass-b");
			GroupAgentImpl groupAB = GroupAgentImpl.createGroupAgent(new Agent[] { userA, userB });

			// start service instance on node 0
			System.out.println("starting service on node 0");
			service.unlock("test-service-pass");
			nodes.get(0).storeAgent(service);
			nodes.get(0).registerReceiver(service);

			// UserA login at node 1
			System.out.println("user a login at node 1");
			userA.unlock("test-pass-a");
			nodes.get(1).storeAgent(userA);
			Mediator mediatorA = nodes.get(1).createMediatorForAgent(userA);

			// UserB login at node 1
			System.out.println("user b login at node 2");
			userB.unlock("test-pass-b");
			nodes.get(1).storeAgent(userB);
			Mediator mediatorB = nodes.get(1).createMediatorForAgent(userB);

			// store group in network
			groupAB.unlock(userA);
			nodes.get(1).storeAgent(groupAB);

			// UserA uploads a file to the network and shares it with groupAB
			System.out.println("uploading file");
			mediatorA.invoke(FileService.class.getName(), "storeFile", new Serializable[] { TEST_IDENTIFIER, TEST_NAME,
					TEST_CONTENT, TEST_MIME, groupAB.getIdentifier(), TEST_DESCRIPTION }, false);

			// UserB downloads the file from the network
			System.out.println("downloading file");
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) mediatorB.invoke(FileService.class.getName(), "fetchFile",
					new Serializable[] { TEST_IDENTIFIER }, false);

			// validate fetched file
			Assert.assertEquals(TEST_IDENTIFIER, map.get("identifier"));
			Assert.assertEquals(TEST_NAME, map.get("name"));
			Assert.assertArrayEquals(TEST_CONTENT, (byte[]) map.get("content"));
			Assert.assertEquals(TEST_MIME, map.get("mimeType"));
			Assert.assertEquals(groupAB.getIdentifier(), map.get("ownerId"));
			Assert.assertEquals(TEST_DESCRIPTION, map.get("description"));

			// UserB changes the file and uploads its version
			System.out.println("User B updating file");
			mediatorA.invoke(FileService.class.getName(), "storeFile", new Serializable[] { TEST_IDENTIFIER, TEST_NAME,
					TEST_CONTENT2, TEST_MIME, groupAB.getIdentifier(), TEST_DESCRIPTION }, false);

			// UserA fetches the file again and reads changes from User B
			System.out.println("downloading changed file");
			@SuppressWarnings("unchecked")
			Map<String, Object> map2 = (Map<String, Object>) mediatorB.invoke(FileService.class.getName(), "fetchFile",
					new Serializable[] { TEST_IDENTIFIER }, false);

			// validate fetched file
			Assert.assertEquals(TEST_IDENTIFIER, map2.get("identifier"));
			Assert.assertEquals(TEST_NAME, map2.get("name"));
			Assert.assertArrayEquals(TEST_CONTENT2, (byte[]) map2.get("content"));
			Assert.assertEquals(TEST_MIME, map2.get("mimeType"));
			Assert.assertEquals(groupAB.getIdentifier(), map2.get("ownerId"));
			Assert.assertEquals(TEST_DESCRIPTION, map2.get("description"));
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.toString());
		}
	}

}
