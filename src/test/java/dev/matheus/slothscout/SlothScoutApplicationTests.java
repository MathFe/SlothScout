package dev.matheus.slothscout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:slothscout",
		"slothscout.schedule.cron=-",
		"slothscout.imap.username=",
		"slothscout.imap.password="
})
class SlothScoutApplicationTests {

	@Test
	void contextLoads() {
	}

}
