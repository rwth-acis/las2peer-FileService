package i5.las2peer.services.servicePackage;

import static org.junit.Assert.fail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.Test;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;

import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.p2p.PastryNodeImpl.STORAGE_MODE;
import i5.las2peer.security.Mediator;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.fileService.FileService;
import rice.pastry.socket.SocketPastryNodeFactory;

public class FileServiceTest {

	private static final String TEST_FILEID = "helloworld.txt";
	private static final String TEST_CONTENT = "Hello World!";

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
	 * This test checks if the file upload is working.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFileUploadAndDownload() throws Exception {
		System.out.println("starting network...");
		// TODO test on more than one node
//		startNetwork(2);
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
//		Mediator mediatorA = nodes.get(1).getOrRegisterLocalMediator(userA);
//		nodes.get(1).storeAgent(userA);
		Mediator mediatorA = nodes.get(0).getOrRegisterLocalMediator(userA);
		nodes.get(0).storeAgent(userA);

		// UserA uploads a file to the network
		System.out.println("uploading file");
		mediatorA.invoke(FileService.class.getName(), "storeFile",
				new Serializable[] { TEST_FILEID, Base64.getEncoder().encodeToString(TEST_CONTENT.getBytes()) }, true);

		// UserA downloads the file from the network
		System.out.println("downloading file");
		Serializable result = mediatorA.invoke(FileService.class.getName(), "fetchFile",
				new Serializable[] { TEST_FILEID }, true);
		String content = (String) result;
		String decoded = new String(Base64.getDecoder().decode(content));
		if (!TEST_CONTENT.equals(decoded)) {
			fail("File content doesn't match!");
		}

		stopNetwork();
	}

}
