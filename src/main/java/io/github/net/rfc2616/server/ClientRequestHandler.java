package io.github.net.rfc2616.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;

import io.github.net.rfc2616.utilities.AppProperties;
import io.github.net.rfc2616.utilities.LogService;

public class ClientRequestHandler implements Runnable {
	private final LogService logger = LogService.getInstance("HTTP-SERVER");

	private Socket client;

	private InputStream in;
	private OutputStream out;

	public ClientRequestHandler(Socket c) {
		this.client = c;
	}

	@Override
	public void run() {

		try {
			this.in = client.getInputStream();
			this.out = client.getOutputStream();

			this.handle();

			this.out.close();
			this.in.close();
		} catch (IOException e) {
			logger.warning("Request handling error: {}", e.getMessage());
		}

		try {
			client.close();
		} catch (IOException e) {
			logger.warning("Client connection closing error: {}", e.getMessage());
		}

		logger.info("Client request completed");

	}

	private static enum HttpMethod {
		OPTIONS, GET, POST, PUT, DELETE, TRACE, CONNECT;

		static HttpMethod from(final String method) {
			if (method == null) {
				return null;
			}
			for (final HttpMethod m : values()) {
				if (m.name().equalsIgnoreCase(method)) {
					return m;
				}
			}
			return null;
		}

	}

	private static final String CRLF = "\r\n";
	private static final byte[] CRLF_RAW = CRLF.getBytes(StandardCharsets.US_ASCII);

	private HttpMethod requestMethod = null;
	private URL requestUrl = null;

	private Map<String, List<String>> httpRequestHeaders = new LinkedHashMap<>();
	private ByteArrayOutputStream httpRequestBody = new ByteArrayOutputStream();

	private void handle() throws IOException {
		try {
			this.startHandleHttpRequest();
			if (this.requestMethod != null) {
				this.continueHandleHttpRequest();
			}
			out.flush();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	private void startHandleHttpRequest() throws Exception {
		final ByteArrayOutputStream cache = new ByteArrayOutputStream();

		int reader = -1;
		while ((reader = in.read()) != -1) {
			cache.write(reader);

			byte[] memory = cache.toByteArray();
			if (memory.length >= 4) {
				if (	memory[memory.length - 4] == '\r' 
					&&	memory[memory.length - 3] == '\n'
					&&	memory[memory.length - 2] == '\r' 
					&&	memory[memory.length - 1] == '\n'
				) {

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
		
		if ( HttpMethod.POST.equals(this.requestMethod) ) {
			if( ! this.httpRequestHeaders.containsKey("content-length") 
					&& ! this.httpRequestHeaders.containsKey("transfer-encoding") ) {
				return this.sendLengthRequired();
			}
		}

		this.httpResponseHeaders.put("Content-Length", Collections.singletonList("0"));

		byte returnCode = 0;
		switch (this.requestMethod) {
		case GET:
			returnCode = this.handleGetRequests();
			break;
		case POST:
			returnCode = this.handlePostRequests();
			break;
		default:
			break;
		}

		if (returnCode == Q_BAD_REQUEST) {
			return this.sendBadRequest("Invalid Request Data");
		}

		if (returnCode == Q_NOT_FOUND) {
			return this.sendResourceNotFound();
		}

		if (returnCode != 0) {
			return this.sendServerError(null);
		}

		return sendResponse();
	}

	private byte handleGetRequests() {
		try {
			return doHandleGetRequests();
		} catch (Exception e) {
			return 1;
		}
	}

	private byte doHandleGetRequests() throws IOException {
		final String path = this.requestUrl.getPath();

		switch (path) {
			case "/live":
			case "/ready":
				return this.liveness();
			case "/spec":
				return this.spec();
			case "/":
				return this.sendBasicBody();
			default:
				return Q_NOT_FOUND;
		}
	}

	private byte handlePostRequests() throws IOException {
		final String path = this.requestUrl.getPath();

		switch (path) {
			case "/echo":
				return this.echo();
			default:
				return Q_NOT_FOUND;
		}
	}

	private byte extractBodyPayload() throws IOException {
		final List<String> transferEncoding = this.httpRequestHeaders.get("transfer-encoding");

		if( transferEncoding == null ) {
			return extractBodyByContent();
		}

		return extractBodyByTransfer(transferEncoding);
	}

	private byte extractBodyByContent() throws IOException {
		final List<String> contentLength = this.httpRequestHeaders.get("content-length");

		if (contentLength == null || contentLength.isEmpty()) {
			return 0;
		}

		final int length = Integer.parseInt(contentLength.get(0));
		int remainingLength = length;

		final int maxLength = 1024;
		final byte[] chunkData = new byte[maxLength];

		while (remainingLength > 0) {
			final int chunkSize = remainingLength > maxLength ? maxLength : remainingLength;

			this.in.read(chunkData, 0, chunkSize);
			this.httpRequestBody.write(chunkData, 0, chunkSize);

			remainingLength -= chunkSize;
		}

		return 0;
	}

	static final byte HEX_BASE = 16;

	private byte extractBodyByTransfer(final List<String> transferEncoding) throws IOException {
		StringBuilder chunkLengthStage = new StringBuilder("");

		// Octets used to check end of line
		int octet0 = 0;
		int octet1 = 0;

		int octet = 0;
		while (true) {
			octet = this.in.read();

			octet0 = octet1;
			octet1 = octet;

			if( octet == '\r' ) { continue; }

			if ( octet != '\n' && octet0 != '\r' ) {
				chunkLengthStage.append( (char)octet );
				continue;
			}

			final int chunkLength = Integer.parseInt(chunkLengthStage.toString(), HEX_BASE);

			if(chunkLength == 0) {
				// Read the last two octets, which are expected to be: '\r' (char 13) and '\n' (char 10) 
				this.in.readNBytes(2);
				break;
			}

			final byte[] chunkData = this.in.readNBytes(chunkLength);
			this.httpRequestBody.write(chunkData);
			
			/*
			 * Read the next two octets, which are expected to be: '\r' (char 13) and '\n' (char 10),
			 * ending the chunk data
			 */
			this.in.readNBytes(2);

			// reset the chunk length control
			chunkLengthStage = new StringBuilder("");

			// reset the octets used to check end of line
			octet0 = 0;
			octet1 = 0;
		}

		return 0;
	}

	private byte analyseRequestHeader(byte[] raw) throws Exception {
		final String CRLF_RE = "\\r\\n";

		// https://www.rfc-editor.org/rfc/rfc2616.html#section-2.2
		/*
		 	HTTP/1.1 header field values can be folded onto multiple lines if the
   			continuation line begins with a space or horizontal tab. All linear
   			white space, including folding, has the same semantics as SP.
   			A recipient MAY replace any linear white space with a single SP before
   			interpreting the field value or forwarding the message downstream.
		 */
		// https://www.rfc-editor.org/rfc/rfc2616.html#section-4.2
		/*
			Header fields can be extended over multiple lines by preceding each extra line with at least one SP or HT. 
		 */
		final String data = new String(raw, StandardCharsets.US_ASCII).replaceAll("\\r\\n[\\s\\t]+", "\u0000\u0000\u0000");
		final String[] entries = data.split(CRLF_RE);

		if (entries.length == 0) {
			return sendBadRequest("Invalid HTTP Request");
		}

		// https://www.rfc-editor.org/rfc/rfc2616.html#section-4.1
		/*
		 	In the interest of robustness, servers SHOULD ignore any empty
   			line(s) received where a Request-Line is expected. In other words, if
   			the server is reading the protocol stream at the beginning of a
   			message and receives a CRLF first, it should ignore the CRLF.
		 */
		int startLine = 0;
		while(true) {
			if( ! entries[startLine].replaceAll("[\\s\\t]", "").isEmpty() ) {
				break;
			}
			++startLine;
		}

		final String methodLine = entries[startLine];
		final String[] methodContent = methodLine.split("\\s");
		if (methodContent.length != 3) {
			return sendBadRequest("Invalid HTTP Method Sintax");
		}

		logger.info(methodLine);

		final String methodLineLower = methodLine.toUpperCase();

		final String method = methodLineLower.contains(" ")
				? methodLineLower.substring(0, methodLineLower.indexOf(" "))
				: methodLineLower;

		final HttpMethod httpMethod = HttpMethod.from(method);
		if (httpMethod == null) {
			return sendMethodNotAllowed();
		}

		if (!methodLineLower.toUpperCase().startsWith(httpMethod.name() + " ")) {
			return sendBadRequest("Invalid HTTP Method Sintax");
		}

		final String uri = methodContent[1];
		if (!validateURI(uri)) {
			return sendBadRequest("Invalid HTTP URI");
		}

		httpRequestHeaders.put(null, Collections.singletonList(methodLine));
		for (int i = startLine + 1; i < entries.length; ++i) {
			final String entry = entries[i];
			final String header = entry.substring(0, entry.indexOf(':')).toLowerCase();
			final String value = entry.substring(entry.indexOf(':') + 1).trim().replaceAll("[\u0000]{3}", "\r\n ");
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
		logger.info(statusLine);
		out.write((statusLine + CRLF).getBytes(StandardCharsets.US_ASCII));

		return 0;
	}

	private byte sendDateHeader() throws IOException {
		out.write(String.format("Date: %s%s", gmt(), CRLF).getBytes(StandardCharsets.US_ASCII));

		return 0;
	}

	private byte sendServerHeader() throws IOException {
		final String javaVendor = System.getProperty("java.vendor");
		final String javaVersion = System.getProperty("java.version");
		final String osName = System.getProperty("os.name");
		final String osArchitecture = System.getProperty("os.arch");
		final String osVersion = System.getProperty("os.version");

		out.write(("Server: " + AppProperties.getHostName() + CRLF).getBytes(StandardCharsets.US_ASCII));
		out.write(String.format("X-Powered-By: Java/%s (%s; %s %s; %s)%s",
				javaVersion,
				javaVendor,
				osName,
				osArchitecture,
				osVersion,
				CRLF
			).getBytes(StandardCharsets.US_ASCII));

		return 0;
	}

	private byte sendETagHeader() throws IOException {
		out.write(("ETag:\"" + UUID.randomUUID().toString() + "\"" + CRLF).getBytes(StandardCharsets.US_ASCII));

		return 0;
	}

	private byte sendContentHeader(final String type, final int length) throws IOException {
		out.write(("Content-Type: " + type + CRLF).getBytes(StandardCharsets.US_ASCII));
		out.write(("Content-Length: " + length + CRLF).getBytes(StandardCharsets.US_ASCII));

		return 0;
	}

	private byte liveness() throws IOException {
		final String html = "{\"status\":\"UP\",\"checks\":[]}";
		final byte[] raw = html.getBytes(StandardCharsets.UTF_8);

		this.httpResponseHeaders.put("Content-Type", Collections.singletonList("application/json"));
		this.httpResponseHeaders.put("Content-Length", Collections.singletonList(Integer.toString(raw.length)));
		this.httpResponseBody.write(raw);

		return 0;
	}

	private byte spec() throws IOException {
		final InputStream in = getClass().getResourceAsStream("/rfc2616.txt");

		final ByteArrayOutputStream cache = new ByteArrayOutputStream();
		final GZIPOutputStream gzip = new GZIPOutputStream(cache);
		IOUtils.copy(in, gzip);
		in.close();

		final byte[] raw = cache.toByteArray();

		this.httpResponseHeaders.put("Content-Type", Collections.singletonList("text/plain; charset=ASCII"));
		this.httpResponseHeaders.put("Content-Encoding", Collections.singletonList("gzip"));
		this.httpResponseHeaders.put("Content-Length", Collections.singletonList(Integer.toString(raw.length)));
		this.httpResponseBody.write(raw);

		return 0;
	}

	private byte echo() throws IOException {
		final List<String> contentType = this.httpRequestHeaders.get("content-type");
		final byte[] raw = this.httpResponseBody.toByteArray();

		this.httpResponseHeaders.put("Content-Type", contentType);
		this.httpResponseHeaders.put("Content-Length", Collections.singletonList(Integer.toString(raw.length)));
		this.httpResponseBody.write(raw);

		return 0;
	}

	private byte sendBasicBody() throws IOException {
		final String html = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n"
				+ "<title>Basic HTTP Server</title>\n</head>\n<body>\nIt works</body>\n"
				+ "</html>\n";
		final byte[] raw = html.getBytes(StandardCharsets.UTF_8);

		this.httpResponseHeaders.put("Content-Type", Collections.singletonList("text/html; charset=UTF-8"));
		this.httpResponseHeaders.put("Content-Length", Collections.singletonList(Integer.toString(raw.length)));
		this.httpResponseBody.write(raw);

		return 0;
	}

	private byte sendCustomHeaders() throws IOException {
		for (final Map.Entry<String, List<String>> entry : this.httpResponseHeaders.entrySet()) {
			for (final String value : entry.getValue()) {
				out.write((entry.getKey() + ": " + value + CRLF).getBytes(StandardCharsets.US_ASCII));
			}
		}

		return 0;
	}

	private byte sendConnectionCloseHeader() throws IOException {
		out.write(("Connection: close" + CRLF).getBytes(StandardCharsets.US_ASCII));

		return 0;
	}

	private byte mountCustomBody() throws IOException {
		out.write(this.httpResponseBody.toByteArray());

		return 0;
	}

	private byte mountTerminateConnection() throws IOException {
		sendConnectionCloseHeader();
		out.write(CRLF_RAW);

		return 0;
	}

	private byte sendBadRequest(String cause) throws IOException {
		this.sendStatusLine("HTTP/1.1 400 Bad Request");
		this.sendDateHeader();
		this.sendServerHeader();

		if (cause == null) {
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

	private byte sendLengthRequired() throws IOException {
		this.sendStatusLine("HTTP/1.1 411 Length Required");
		sendDateHeader();
		sendServerHeader();

		return mountTerminateConnection();
	}

	private byte sendServerError(String cause) throws IOException {
		this.sendStatusLine("HTTP/1.1 500 Server Error");
		sendDateHeader();
		sendServerHeader();

		if (cause == null) {
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

}