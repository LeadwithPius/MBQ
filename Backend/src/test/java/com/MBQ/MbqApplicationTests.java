package com.MBQ;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Needs a running PostgreSQL plus DB_PASSWORD and JWT_SECRET (see README "Run locally").
// Skipped automatically on machines without them, so `mvnw test` still passes there.
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
@SpringBootTest
class MbqApplicationTests {

    @Test
    void contextLoads() {
    }
}
