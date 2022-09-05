package io.github.claudineyns.rfc2616.server;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

@SuppressWarnings("unused")
public class ClientHandler implements Runnable {
	private final Logger logger = Logger.getGlobal();
	
	private Socket client;
	
	private InputStream in;
	private OutputStream out;
	private String serverName;
	
	public ClientHandler(Socket c) {
		this.client = c;
		try {
			serverName = Inet4Address.getLocalHost().getHostName();
		} catch(IOException e) {
			logger.warning("\n### SERVER ### [Startup] [WARNING]\n" + e.getMessage());
			serverName = "localhost";
		}
	}

	@Override
	public void run() {
		
		try {
			this.in = client.getInputStream();
			this.out = client.getOutputStream();

			this.handle();

			this.out.close();
			this.in.close();
		} catch(IOException  e) {
			logger.warning("\n### SERVER ### [Cleanup] [WARNING] General error:\n" + e.getMessage());
		}

		try {
			client.close();
		} catch(IOException e) {
			logger.warning("\n### SERVER ### [Cleanup] [WARNING] General error:\n" + e.getMessage());
		}
		
		logger.info("\n### SERVER ### [Cleanup] [INFO] Client request completed.\n");
		
	}

	private static enum HttpMethod {
		OPTIONS, GET, POST, PUT, DELETE, TRACE, CONNECT;

		static HttpMethod from(final String method) {
			if(method == null) { return null; }
			for(final HttpMethod m: values()) {
				if(m.name().equalsIgnoreCase(method)) {
					return m;
				}
			}
			return null;
		}

	}
	
	private static final String CRLF = "\r\n";
	private static final String CRLF_RE = "\\r\\n";
	
	private HttpMethod requestMethod = null;
	private URL requestUrl = null;

	private Map<String, List<String>> httpRequestHeaders = new LinkedHashMap<>();
	private ByteArrayOutputStream httpRequestBody = new ByteArrayOutputStream();

	private void handle() throws IOException {
		try {
			this.startHandleHttpRequest();
			if( this.requestMethod != null ) {
				this.continueHandleHttpRequest();
			}
			out.flush();
		} catch(Exception e) {
			logger.severe(e.getMessage());
		}
	}
	
	private void startHandleHttpRequest() throws Exception {
		final ByteArrayOutputStream cache = new ByteArrayOutputStream();
		
		int reader = -1;
		while( (reader = in.read()) != -1) {
			cache.write(reader);
			
			byte[] memory = cache.toByteArray();
			if( memory.length >= 4 ) {
				if(    memory[memory.length-4] == '\r' 
					&& memory[memory.length-3] == '\n' 
					&& memory[memory.length-2] == '\r' 
					&& memory[memory.length-1] == '\n' ) {
					
					this.analyseRequestHeader(memory);
					break;
					
				}
			}
			
		}
	}
	
	static final byte Q_BAD_REQUEST = -1;
	static final byte Q_NOT_FOUND = -2;
	static final byte Q_SERVER_ERROR = 1;
	
	private byte continueHandleHttpRequest() throws Exception {
		this.extractBodyPayload();

		this.httpResponseHeaders.put("Content-Length", Collections.singletonList("0"));

		byte returnCode = 0;
		switch(this.requestMethod) {
			case GET: 
				returnCode = this.handleGetRequests(); break;
			case POST: 
				returnCode = this.handlePostRequests(); break;
			default: break; 
		}

		if( returnCode == Q_BAD_REQUEST ) {
			return this.sendBadRequest("Invalid Request Data");
		}

		if( returnCode == Q_NOT_FOUND ) {
			return this.sendResourceNotFound();
		}

		if( returnCode != 0 ) {
			return this.sendServerError(null);
		}

		return sendResponse();
	}

	private byte handleGetRequests() {
		try {
			return doHandleGetRequests();
		} catch(Exception e) {
			return 1;
		}
	}
	
	private byte doHandleGetRequests() throws IOException {
		final String path = this.requestUrl.getPath();

		switch(path) {
			case "/svgToPng":
				return this.fetchSvgToPng();
			case "/":
				return this.sendBasicBody();
			default:
				return Q_NOT_FOUND;
		}
	}

	private byte handlePostRequests() {

		return 0;
	}

	private byte extractBodyPayload() throws Exception {
		final List<String> contentLength = this.httpRequestHeaders.get("content-length");

		if(contentLength == null || contentLength.isEmpty()) {
			return 0;
		}

		final int length = Integer.parseInt(contentLength.get(0));
		int remainingLength = length;

		final int maxLength = 1024;
		final byte[] chunkData = new byte[maxLength];
		
		while(remainingLength > 0) {
			final int chunkSize = remainingLength > maxLength ? maxLength : remainingLength;

			this.in.read(chunkData, 0, chunkSize);
			this.httpRequestBody.write(chunkData, 0, chunkSize);

			remainingLength -= chunkSize;
		}

		return 0;
	}
	
	private byte analyseRequestHeader(byte[] raw) throws Exception {
		final String data = new String(raw);
		final String[] entries = data.split(CRLF_RE);
		
		if( entries.length == 0 ) {
			return sendBadRequest("Invalid HTTP Request");
		}
		
		final String methodLine = entries[0];
		final String methodLine2 = methodLine.toUpperCase();

		final String method = methodLine2.contains(" ") 
				? methodLine2.substring(0, methodLine2.indexOf(" ")) 
				: methodLine2;

		final HttpMethod httpMethod = HttpMethod.from(method);

		if( httpMethod == null ) {
			return sendMethodNotAllowed();
		}

		if( ! methodLine2.toUpperCase().startsWith(httpMethod.name()+" ") ) {
			return sendBadRequest("Invalid HTTP Method Sintax");
		}

		String[] methodContent = methodLine.split("\\s");

		if( methodContent.length != 3 ) {
			return sendBadRequest("Invalid HTTP Method Sintax");
		}
		
		String uri = methodContent[1];
		
		if( ! validateURI(uri) ) {
			return sendBadRequest("Invalid HTTP URI");
		}
		
		httpRequestHeaders.put(null, Collections.singletonList(methodLine));
		for(int i = 1; i < entries.length; ++i) {
			final String entry = entries[i];

			final String header = entry.substring(0, entry.indexOf(':')).toLowerCase();
			final String value = entry.substring(entry.indexOf(':')+1).trim().replaceAll("\\r\\n$", "");
			
			httpRequestHeaders.putIfAbsent(header, new LinkedList<>());
			httpRequestHeaders.get(header).add(value);
		}

		this.requestUrl = new URL("http://localhost" + uri);
		this.requestMethod = httpMethod;

		return 0;
	}
	
	private static final String gmt() {
		final DateTimeFormatter RFC_1123_DATE_TIME = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
		final ZonedDateTime dt = ZonedDateTime.now(ZoneId.of("GMT"));

		return dt.format(RFC_1123_DATE_TIME);
	}

	private byte sendStatusLine(final String statusLine) throws IOException {
		out.write( (statusLine + CRLF).getBytes(StandardCharsets.US_ASCII) );

		return 0;
	}

	private byte sendDateHeader() throws IOException {
		out.write( String.format("Date: %s%s", gmt(), CRLF).getBytes(StandardCharsets.US_ASCII) );

		return 0;
	}

	private byte sendServerHeader() throws IOException {
		final String javaVendor = System.getProperty("java.vendor");
		final String javaVersion = System.getProperty("java.version");
		final String osName = System.getProperty("os.name");
		final String osArchitecture = System.getProperty("os.arch");
		final String osVersion = System.getProperty("os.version");

		out.write(("Server: "+serverName+CRLF).getBytes(StandardCharsets.US_ASCII));
		out.write( String.format("X-Powered-By: Java/%s (%s; %s %s; %s)%s",
				javaVersion,
				javaVendor,
				osName,
				osArchitecture,
				osVersion,
				CRLF
			).getBytes(StandardCharsets.US_ASCII)
		);

		return 0;
	}
	
	private byte sendETagHeader() throws IOException {
		out.write( ("ETag:\""+UUID.randomUUID().toString()+"\""+CRLF).getBytes(StandardCharsets.US_ASCII) );
		
		return 0;
	}
	
	private byte sendContentHeader(final String type, final int length) throws IOException {
		out.write( ("Content-Type: " + type + CRLF).getBytes(StandardCharsets.US_ASCII) );
		out.write( ("Content-Length: " + length + CRLF).getBytes(StandardCharsets.US_ASCII) );

		return 0;
	}
	
	private byte sendEmptyBody() throws IOException {
		out.write( ("Content-Length: 0" + CRLF).getBytes(StandardCharsets.US_ASCII) );
		out.write( CRLF.getBytes(StandardCharsets.US_ASCII) );

		return 0;
	}
	
	private byte sendBasicBody() throws IOException {
		final String html = ""
				+ "<!DOCTYPE html>\n"
				+ "<html>\n"
				+ "<head>\n"
				+ "<meta charset=\"UTF-8\">\n"
				+ "<title>Basic HTTP Server</title>\n"
				+ "</head>\n"
				+ "<body>\n"
				+ "It works\n"
				+ "</body>\n"
				+ "</html>\n";
		final byte[] raw = html.getBytes(StandardCharsets.UTF_8);
		
		this.httpResponseHeaders.put("Content-Type", Collections.singletonList("text/html; charset=UTF-8"));
		this.httpResponseHeaders.put("Content-Length", Collections.singletonList(Integer.toString(raw.length)));
		this.httpResponseBody.write(raw);

		return 0;
	}
	
	private byte sendCustomHeaders() throws IOException {
		for(final Map.Entry<String, List<String>> entry: this.httpResponseHeaders.entrySet()) {
			for(final String value: entry.getValue()) {
				out.write( (entry.getKey() + ": " + value + CRLF).getBytes(StandardCharsets.US_ASCII) );
			}
		}

		return 0;
	}

	private byte sendConnectionCloseHeader() throws IOException {
		out.write( ("Connection: close" + CRLF).getBytes(StandardCharsets.US_ASCII) );

		return 0;
	}

	private byte mountCustomBody() throws IOException {
		out.write(this.httpResponseBody.toByteArray());

		return 0;
	}

	private byte mountTerminateConnection() throws IOException {
		sendConnectionCloseHeader();
		out.write( CRLF.getBytes(StandardCharsets.US_ASCII) );

		return 0;
	}

	private byte sendBadRequest(String cause) throws IOException {
		this.sendStatusLine("HTTP/1.1 400 Bad Request");
		this.sendDateHeader();
		this.sendServerHeader();
		
		if( cause == null ) {
			return this.mountTerminateConnection();
		}
		
		final byte[] raw = cause.getBytes(StandardCharsets.US_ASCII);

		this.sendContentHeader("text/plain", raw.length);
		this.mountTerminateConnection();
		out.write(raw);

		return 0;
	}
	
	private byte sendResourceNotFound() throws IOException {
		final byte[] raw = "The requested resource could not be found".getBytes(StandardCharsets.US_ASCII);
		
		this.sendStatusLine("HTTP/1.1 404 Not Found");
		this.sendDateHeader();
		this.sendServerHeader();
		this.sendContentHeader("text/plain", raw.length);
		this.mountTerminateConnection();

		out.write(raw);

		return 0;
	}
	
	private byte sendMethodNotAllowed() throws IOException {
		this.sendStatusLine("HTTP/1.1 405 Method Not Allowed");
		sendDateHeader();
		sendServerHeader();
		
		return mountTerminateConnection();
	}
	
	private byte sendServerError(String cause) throws IOException {
		this.sendStatusLine("HTTP/1.1 500 Server Error");
		sendDateHeader();
		sendServerHeader();

		if( cause == null ) {
			return mountTerminateConnection();
		}

		final byte[] raw = cause.getBytes(StandardCharsets.US_ASCII);

		this.sendContentHeader("text/plain", raw.length);
		this.mountTerminateConnection();

		out.write(raw);

		return 0;
	}

	private boolean validateURI(String uri) {
		final Pattern uriPattern = Pattern.compile("^\\/\\S*$");
		final Matcher uriMatcher = uriPattern.matcher(uri);

		return uriMatcher.matches();		
	}

	private Map<String, List<String>> httpResponseHeaders = new LinkedHashMap<>();
	private ByteArrayOutputStream httpResponseBody = new ByteArrayOutputStream();

	private byte sendResponse() throws IOException {
		this.sendStatusLine("HTTP/1.1 200 OK");

		this.sendDateHeader();
		this.sendServerHeader();
		this.sendETagHeader();
		this.sendCustomHeaders();
		this.mountTerminateConnection();

		return this.mountCustomBody();
	}

	private byte fetchSvgToPng() throws IOException {
		final String query = this.requestUrl.getQuery();
		final String[] queryData = query != null ? query.split("&") : new String[] {};
		
		final Map<String, String> properties = new LinkedHashMap<>();
		for(final String q: queryData) {
			final String[] p = q.split("=");
			properties.put(p[0], URLDecoder.decode(p[1], "UTF-8"));
		}
		
		final String queryUrl = properties.get("url");
		if( queryUrl == null) { return Q_BAD_REQUEST; }
		
		final URL svgUrl = new URL(queryUrl);

        final TranscoderInput input_svg_image = new TranscoderInput(svgUrl.openConnection().getInputStream());
        final TranscoderOutput output_png_image = new TranscoderOutput(this.httpResponseBody);              
        final PNGTranscoder my_converter = new PNGTranscoder();    

        try {
        	my_converter.transcode(input_svg_image, output_png_image);
        } catch(TranscoderException e) {
        	logger.log(Level.SEVERE, "Could not fetch image", e);
        	throw new IOException(e);
        }

        this.httpResponseBody.flush();
        this.httpResponseBody.close();

        this.httpResponseHeaders.put("Content-Type", Collections.singletonList("image/png"));
        this.httpResponseHeaders.put("Content-Length", Collections.singletonList(Integer.toString(this.httpResponseBody.size())));

		return 0;
	}

}
