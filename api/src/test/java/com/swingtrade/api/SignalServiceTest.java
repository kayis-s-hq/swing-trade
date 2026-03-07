package com.swingtrade.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SignalService
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class SignalServiceTest {

    @Autowired
    private SignalService signalService;

    /**
     * Test that signal service returns non-empty list
     */
    @Test
    void testGetLatestSignals() {
        assertNotNull(signalService);
        assertFalse(signalService.getLatestSignals().isEmpty());
    }
}
