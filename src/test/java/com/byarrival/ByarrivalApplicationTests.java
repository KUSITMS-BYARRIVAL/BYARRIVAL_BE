package com.byarrival;

import com.byarrival.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class ByarrivalApplicationTests {

    @Test
    void contextLoads() {
    }

}
