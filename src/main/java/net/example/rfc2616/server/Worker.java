package net.example.rfc2616.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class Worker {
	static final Worker worker = new Worker();

	public static void main(String[] args) throws IOException {
		worker.start();
	}
	
	public static void terminate() {
		worker.stop();
	}
	
	private final Logger logger = Logger.getGlobal();
	
	private ServerSocket server;
	
	private final int PORT = 8080;
	
	private void stop() {
		try {
			if(!server.isClosed()) {
				server.close();
				logger.info("[HTTP-SERVER] Service terminated.");
			}
		} catch(IOException e) {}
	}

	private void start() throws IOException {
		Runtime.getRuntime().addShutdownHook(new Thread(()-> stop()));
		
		this.server = new ServerSocket(PORT);
		logger.info("[HTTP-SERVER] Listening on port 8080");
		
		while(true) {
			Socket client = null;
			try {
				client = server.accept();
				Logger.getGlobal().info("[HTTP-SERVER] Connection received!");
			} catch(IOException e) {
				break;
			}

			CompletableFuture.runAsync(new ClientHandler(client));
		}
	}
	
}
