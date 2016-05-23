package i5.las2peer.services.servicePackage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.p2p.PastryNodeImpl.STORAGE_MODE;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.fileService.FileService;
import i5.las2peer.services.fileService.StoredFile;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;
import rice.pastry.socket.SocketPastryNodeFactory;

public class FileServiceTest {

	private static final String TEST_FILEID = "helloworld.txt";
	private static final String TEST_NAME = "test file";
	private static final String TEST_CONTENT = "Hello World!";
	private static final String TEST_MIME = "text/plain";
	private static final String TEST_DESCRIPTION = "just a test file";

	private static final int WEBCONNECTOR_PORT = 14580;
	private static final String TEST_USERA_NAME = "UserA";
	private static final String TEST_USERA_PASSWORD = "test-pass-a";

	private List<PastryNodeImpl> nodes;
	private PastryNodeImpl bootstrap;

	private void startNetwork(int numOfNodes) throws Exception {
		nodes = new ArrayList<>(numOfNodes);
		bootstrap = new PastryNodeImpl(14501, null, STORAGE_MODE.memory, false, null, null);
		bootstrap.launch();
		// get the address the boostrap node listens to
		MultiInetSocketAddress addr = (MultiInetSocketAddress) bootstrap.getPastryNode().getVars()
				.get(SocketPastryNodeFactory.PROXY_ADDRESS);
		String strAddr = addr.getAddress(0).getHostString();
		nodes.add(bootstrap);
		for (int i = 1; i < numOfNodes; i++) {
			PastryNodeImpl n = new PastryNodeImpl(14501 + i, strAddr + ":14501", STORAGE_MODE.memory, false, null,
					null);
			n.launch();
			nodes.add(n);
		}
	}

	private void stopNetwork() {
		for (PastryNodeImpl node : nodes) {
			node.shutDown();
		}
	}

	/**
	 * This checks if the service is working with RMI invocations.
	 * 
	 * @throws Exception
	 */
//	@Test
	public void testStoreAndFetch() throws Exception {
		System.out.println("starting network...");
		// TODO test with more than one node
		// startNetwork(2);
		startNetwork(1);

		// create agents
		System.out.println("creating agents...");
		ServiceAgent service = ServiceAgent.createServiceAgent(FileService.class.getName(), "test-service-pass");
		UserAgent userA = UserAgent.createUserAgent("test-pass-a");

		// start service instance on node 0
		System.out.println("starting service on node 0");
		service.unlockPrivateKey("test-service-pass");
		nodes.get(0).registerReceiver(service);

		// UserA login at node 1
		System.out.println("user a login at node 1");
		userA.unlockPrivateKey("test-pass-a");
		// Mediator mediatorA = nodes.get(1).getOrRegisterLocalMediator(userA);
		// nodes.get(1).storeAgent(userA);
		Mediator mediatorA = nodes.get(0).getOrRegisterLocalMediator(userA);
		nodes.get(0).storeAgent(userA);

		// UserA uploads a file to the network
		System.out.println("uploading file");
		mediatorA.invoke(FileService.class.getName(), "storeFile", new Serializable[] { TEST_FILEID, TEST_NAME,
				TEST_CONTENT.getBytes("UTF-8"), TEST_MIME, TEST_DESCRIPTION }, true);

		// UserA downloads the file from the network
		System.out.println("downloading file");
		StoredFile file = (StoredFile) mediatorA.invoke(FileService.class.getName(), "fetchFile",
				new Serializable[] { TEST_FILEID }, true);

		// validate fetched file
		if (!TEST_FILEID.equals(file.getIdentifier())) {
			fail("File id doesn't match!");
		}
		if (!TEST_NAME.equals(file.getName())) {
			fail("File name doesn't match!");
		}
		if (!TEST_CONTENT.equals(new String(file.getContent(), "UTF-8"))) {
			fail("File content doesn't match!");
		}
		if (!TEST_MIME.equals(file.getMimeType())) {
			fail("File mime type doesn't match!");
		}
		if (!TEST_DESCRIPTION.equals(file.getDescription())) {
			fail("File description doesn't match!");
		}

		stopNetwork();
	}

	/**
	 * This test case checks if the HTTP interface of the service is working.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWebFeatures() throws Exception {
		System.out.println("starting network...");
		startNetwork(2);

		// create agents
		System.out.println("creating agents...");
		ServiceAgent service = ServiceAgent.createServiceAgent(FileService.class.getName(), "test-service-pass");
		UserAgent userA = UserAgent.createUserAgent(TEST_USERA_PASSWORD);

		// start service instance on node 0
		System.out.println("starting service on node 0");
		service.unlockPrivateKey("test-service-pass");
		nodes.get(0).registerReceiver(service);

		// start WebConnector on node 1
		System.out.println("starting WebConnector on node 1");
		WebConnector connector = new WebConnector();
		connector.setHttpPort(WEBCONNECTOR_PORT);
		connector.start(nodes.get(1));

		// UserA login at node 1
		System.out.println("user a login at node 1");
		userA.unlockPrivateKey("test-pass-a");
		userA.setLoginName(TEST_USERA_NAME);
		// nodes.get(1).storeAgent(userA);
		nodes.get(0).storeAgent(userA);

		// setup minimal HTTP client for testing
		MiniClient mc = new MiniClient();
		mc.setAddressPort("http://localhost", WEBCONNECTOR_PORT);
		mc.setLogin(userA.getLoginName(), TEST_USERA_PASSWORD);

		// use connector to upload a file
		// FIXME post actual form data
		ClientResponse up = mc.sendRequest("POST", "fileservice/files", TEST_FILEID + ";" + TEST_CONTENT);
		assertEquals(HttpURLConnection.HTTP_OK, up.getHttpCode());

		// use connector to download a file
		ClientResponse down = mc.sendRequest("GET", "fileservice/files/" + TEST_FILEID, "");
		assertEquals(HttpURLConnection.HTTP_OK, down.getHttpCode());

		// validate downloaded file
		assertTrue(TEST_CONTENT.equals(down.getResponse()));

		stopNetwork();
	}

}
