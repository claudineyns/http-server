package io.github.net.rfc2616.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import io.github.net.rfc2616.utilities.LogService;

public class Worker {
	static final Worker worker = new Worker();

	public static void main(String[] args) throws IOException {
		worker.start();
	}
	
	public static void terminate() {
		worker.stop();
	}
	
	private final LogService logger = LogService.getInstance("HTTP-SERVER");
	
	private ServerSocket server;
	
	private final int PORT = 8080;
	
	private void stop() {
		try {
			if(!server.isClosed()) {
				server.close();
				logger.info("Service terminated.");
			}
		} catch(IOException e) {}
	}

	private void start() throws IOException {
		Runtime.getRuntime().addShutdownHook(new Thread(()-> stop()));
		
		this.server = new ServerSocket(PORT);
		logger.info("Listening on port 8080");
		
		while(true) {
			Socket client = null;
			try {
				client = server.accept();
				logger.info("Connection received!");
			} catch(IOException e) {
				break;
			}

			CompletableFuture.runAsync(new ClientHandler(client));
		}
	}
	
}
