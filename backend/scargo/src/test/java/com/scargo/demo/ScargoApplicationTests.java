package com.scargo.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2") // 26.10.20: 빌드/테스트가 로컬 PostgreSQL 없이도 통과하도록 메모리 H2 사용
class ScargoApplicationTests {

	@Test
	void contextLoads() {
	}

}
