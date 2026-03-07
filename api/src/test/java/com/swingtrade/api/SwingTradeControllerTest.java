package com.swingtrade.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for SwingTradeController
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class SwingTradeControllerTest {

    @Autowired
    private SwingTradeController swingTradeController;

    /**
     * Test that controller is properly instantiated
     */
    @Test
    void testControllerInjection() {
        assertNotNull(swingTradeController);
    }
}
