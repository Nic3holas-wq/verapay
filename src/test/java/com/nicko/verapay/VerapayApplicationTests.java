package com.nicko.verapay;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@Disabled("Requires database — run locally only")
class VerapayApplicationTests {

	@Test
	void contextLoads() {
	}

}
