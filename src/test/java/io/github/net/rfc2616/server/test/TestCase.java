package io.github.net.rfc2616.server.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.github.net.rfc2616.server.Worker;
import io.github.net.rfc2616.utilities.LogService;

@TestInstance(Lifecycle.PER_CLASS)
public class TestCase {
	final LogService logger = LogService.getInstance(TestCase.class.getSimpleName());

	@BeforeAll
	public void startup() throws Exception {
		logger.info("Getting server up...");
		CompletableFuture.runAsync(()-> {
			try { Worker.main(new String[] {}); } catch(IOException e) {} 
		});
		Thread.sleep(250L);
		logger.info("Server is up");
	}
	
	private void execute(final String content) throws Exception {

		final InetAddress address = Inet4Address.getByName("localhost");
		final InetSocketAddress socketAddress = new InetSocketAddress(address, 8080);

		final int connect_timeout = 5000;

		logger.info("Connecting...");
		final Socket socket = new Socket();
		socket.setSoTimeout(500);
		socket.connect(socketAddress, connect_timeout);
		logger.info("Connected.");
		
		final OutputStream out = socket.getOutputStream();
		final InputStream in = socket.getInputStream();
		
		out.write(content.getBytes(StandardCharsets.US_ASCII) );
		out.flush();
		
		try {
			while(in.read() != -1);
		} catch(IOException e) {}

		try { socket.close(); } catch(IOException e) {}

		logger.info("Disconnected.");
	}
	
	@Test
	public void getRootSuccessfull() throws Exception {
		logger.info("# getRootSuccessfull (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET / HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getRootSuccessfull (END)");
	}
	
	@Test
	public void getLivenessSuccessful() throws Exception {
		logger.info("# getLivenessSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /live HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getLivenessSuccessful (END)");
	}
	
	@Test
	public void getReadinessSuccessful() throws Exception {
		logger.info("# getReadinessSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /ready HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getReadinessSuccessful (END)");
	}
	
	@Test
	public void getInvalidSucessful() throws Exception {
		logger.info("# getInvalidSucessful (START)");
		
		final StringBuilder request = new StringBuilder("");

		request.append("GET /notFound HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getInvalidSucessful (END)");
	}

	@Test
	public void postRootSuccessful() throws Exception {
		logger.info("# postRootSuccessful (START)");
		
		final String content = "Test Post request";
		final byte[] raw = content.getBytes(StandardCharsets.US_ASCII);
		
		final StringBuilder request = new StringBuilder("");
		request.append("POST / HTTP/1.1\r\n");
		request.append("Content-Type: text/plain;\r\n charset=UTF8\r\n" );
		request.append("Content-Length: ").append(raw.length).append("\r\n");
		request.append("\r\n");
		request.append(content);

		execute(request.toString());
		
		logger.info("# postRootSuccessful (END)");
	}

	@AfterAll
	public void terminate() throws Exception {
		Worker.terminate();
	}

}
