package io.github.claudineyns.rfc2616.server.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.github.claudineyns.rfc2616.server.Worker;

@TestInstance(Lifecycle.PER_CLASS)
public class TestCase {
	final Logger logger = Logger.getLogger(getClass().getCanonicalName());

	@BeforeAll
	public void startup() throws Exception {
		logger.info("[TEST] Getting server up...");
		CompletableFuture.runAsync(()-> {
			try { Worker.main(new String[] {}); } catch(IOException e) {} 
		});
		Thread.sleep(250L);
		logger.info("[TEST] Server is up");
	}
	
	private void execute(final String content) throws Exception {

		final InetAddress address = Inet4Address.getByName("localhost");
		final InetSocketAddress socketAddress = new InetSocketAddress(address, 8080);

		final int connect_timeout = 2000;

		logger.info("[TEST] Connecting...");
		final Socket socket = new Socket();
		socket.connect(socketAddress, connect_timeout);
		
		logger.info("[TEST] Connected.");
		Thread.sleep(250L);
		
		final OutputStream out = socket.getOutputStream();
		final InputStream in = socket.getInputStream();
		
		out.write(content.getBytes(StandardCharsets.US_ASCII) );
		out.flush();
		
		final StringBuilder sb = new StringBuilder("");
		
		int reader = -1;
		try {
			while((reader = in.read()) != -1) {
				sb.append((char)reader);
			}
		} catch(IOException e) {}

		try { socket.close(); } catch(IOException e) {}

		logger.info("[TEST] Disconnected.");
	}
	
	@Test
	public void getRootAsSuccess() throws Exception {
		logger.info("# getRootAsSuccess (START)");
		
		final StringBuilder request = new StringBuilder("");
		request.append("GET / HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getRootAsSuccess (END)");
	}
	
	@Test
	public void getSvgAsSuccess() throws Exception {
		logger.info("# getSvgAsSuccess (START)");
		
		final StringBuilder request = new StringBuilder("");
		final String url = URLEncoder.encode("https://ob-public-files.s3.amazonaws.com/simbolo_open_finance.HTML.svg", "UTF-8");

		request.append("GET /svgToPng?url=").append(url).append(" HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getSvgAsSuccess (END)");
	}
	
	@Test
	public void getSvgAsFailure() throws Exception {
		logger.info("# getSvgAsFailure (START)");
		
		final StringBuilder request = new StringBuilder("");

		request.append("GET /svgToPng HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getSvgAsFailure (END)");
	}
	
	@Test
	public void getInvalidAsFailure() throws Exception {
		logger.info("# getInvalidAsFailure (START)");
		
		final StringBuilder request = new StringBuilder("");

		request.append("GET /notFound HTTP/1.1\r\n");
		request.append("\r\n");

		execute(request.toString());
		
		logger.info("# getInvalidAsFailure (END)");
	}

	@Test
	public void postRootAsSuccess() throws Exception {
		logger.info("# postRootAsSuccess (START)");
		
		final String content = "Test Post request";
		final byte[] raw = content.getBytes(StandardCharsets.US_ASCII);
		
		final StringBuilder request = new StringBuilder("");
		request.append("POST / HTTP/1.1\r\n");
		request.append("Content-Type: text/plain\r\n" );
		request.append("Content-Length: ").append(raw.length).append("\r\n");
		request.append("\r\n");
		request.append(content);

		execute(request.toString());
		
		logger.info("# postRootAsSuccess (END)");
	}

	@AfterAll
	public void terminate() throws Exception {
		Worker.terminate();
	}

}
