package io.github.net.rfc2616.utilities.test;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.github.net.rfc2616.utilities.LogService;

@TestInstance(Lifecycle.PER_CLASS)
public class LogServiceTest {
	
	final LogService log = LogService.getInstance(getClass().getSimpleName());
	
	@AfterAll
	public void terminate() throws Exception {
		Thread.sleep(1000);
	}
	
	@Test
	public void testAll() {
		log.info("Test {} {}", "#info()", UUID.randomUUID().toString());
		log.debug("Test {} {}", "#debug()", null);
		log.warning("Test {}", "#warning()");
		log.error("Test {}", "#error()");
		log.error("Test", new Exception("Failure", new IllegalStateException("Failure 2")));
	}

}
