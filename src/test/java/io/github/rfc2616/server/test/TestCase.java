package io.github.rfc2616.server.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.github.rfc2616.server.Worker;
import io.github.rfc2616.utilities.LogService;

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
		logger.info("Server is up\n");
	}
	
	private void execute(final String content) throws Exception {
		final InetAddress address = Inet4Address.getByName("localhost");
		final InetSocketAddress socketAddress = new InetSocketAddress(address, 8080);

		final int connect_timeout = 5000;

		logger.info("Connecting...");
		final Socket socket = new Socket();
		socket.setSoTimeout(10000);
		socket.connect(socketAddress, connect_timeout);
		logger.info("Connected.");
		
		final OutputStream out = socket.getOutputStream();
		final InputStream in = socket.getInputStream();
		
		out.write(content.getBytes(StandardCharsets.US_ASCII) );
		out.flush();
		
		final ByteArrayOutputStream rawData = new ByteArrayOutputStream();
		int octet = -1;
		try {
			
			while( (octet = in.read()) != -1) {
				rawData.write(octet);
			}
		} catch(IOException e) {}

		try { socket.close(); } catch(IOException e) {}
		
		final StringBuilder info = new StringBuilder("");
		final String response = new String(rawData.toByteArray(), StandardCharsets.ISO_8859_1);
		Arrays.asList(response.split("\\r\\n")).forEach(q -> {
			if(q.toLowerCase().startsWith("server")) {
				info.append("\n").append(q);
			} else if(q.toLowerCase().startsWith("x-powered-by")) {
				info.append("\n").append(q);
			}
		}); 
		logger.info(info.toString());

		logger.info("Disconnected.");
	}
	
	@Test
	public void getRootSuccessfull() throws Exception {
		logger.info("# getRootSuccessfull (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET / HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getRootSuccessfull (END)\n");
	}
	
	@Test
	public void getPingSuccessful() throws Exception {
		logger.info("# getPingSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("OPTIONS * HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());

		logger.info("# getPingSuccessful (END)\n");
	}
	
	@Test
	public void getLivenessSuccessful() throws Exception {
		logger.info("# getLivenessSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /live HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getLivenessSuccessful (END)\n");
	}
	
	@Test
	public void getReadinessSuccessful() throws Exception {
		logger.info("# getReadinessSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /ready HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getReadinessSuccessful (END)\n");
	}
	
	@Test
	public void getSpecSuccessful() throws Exception {
		logger.info("# getSpecSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /spec HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getSpecSuccessful (END)\n");
	}
	
	@Test
	public void getPageSuccessful() throws Exception {
		logger.info("# getPageSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /page HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getPageSuccessful (END)\n");
	}
	
	@Test
	public void getPageJsSuccessful() throws Exception {
		logger.info("# getPageJsSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET /app.js HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getPageJsSuccessful (END)\n");
	}
	
	@Test
	public void getVersionNotSupportedSuccessful() throws Exception {
		logger.info("# getVersionNotSupportedSuccessful (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET / HTTP/1.2\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());

		logger.info("# getVersionNotSupportedSuccessful (END)\n");
	}
	
	@Test
	public void getInvalidPathSucessful() throws Exception {
		logger.info("# getInvalidPathSucessful (START)");
		
		final StringBuilder request = new StringBuilder("");

		request.append("GET /notFound HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getInvalidPathSucessful (END)\n");
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
		request.append("Connection: close\r\n");
		request.append("\r\n");
		request.append(content);

		execute(request.toString());
		
		logger.info("# postRootSuccessful (END)\n");
	}

	static void concatChunkData(final StringBuilder sb, final String message) {
		final byte[] raw = message.getBytes(StandardCharsets.US_ASCII);
		sb.append(Integer.toString(raw.length, 16));
		sb.append("\r\n");
		sb.append(message);
		sb.append("\r\n");
	}

	@Test
	public void postEchoSuccessful() throws Exception {
		logger.info("# postEchoSuccessful (START)");

		final StringBuilder sb = new StringBuilder("");
		concatChunkData(sb, "Hello, there!\n");
		concatChunkData(sb, "How're you doing here?");
		sb.append("0\r\n\r\n");

		final StringBuilder request = new StringBuilder("");
		request.append("POST /echo HTTP/1.1\r\n");
		request.append("Content-Type: text/plain\r\n" );
		request.append("Transfer-Encoding: chunked\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");
		request.append(sb);

		execute(request.toString());

		logger.info("# postEchoSuccessful (END)\n");
	}

	@Test
	public void postEchoFailure() throws Exception {
		logger.info("# postEchoFailure (START)");

		final StringBuilder sb = new StringBuilder("");
		sb.append("Hello, there!\n");
		sb.append("How're you doing here?");

		final StringBuilder request = new StringBuilder("");
		request.append("POST /echo HTTP/1.1\r\n");
		request.append("Content-Type: text/plain\r\n" );
		request.append("Connection: close\r\n");
		request.append("\r\n");
		request.append(sb);

		execute(request.toString());

		logger.info("# postEchoFailure (END)\n");
	}

	@Test
	public void getInvalidMethodSucessful() throws Exception {
		logger.info("# getInvalidMethodSucessful (START)");
		
		final StringBuilder request = new StringBuilder("");

		request.append("QUERY / HTTP/1.1\r\n");
		request.append("Connection: close\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getInvalidMethodSucessful (END)\n");
	}

	@AfterAll
	public void terminate() throws Exception {
		Worker.terminate();
	}

}
