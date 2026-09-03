package com.swingtrade.api.test.integration;

import com.swingtrade.api.test.fixtures.ApiTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest
public abstract class BaseWebMvcTest {

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected ApiTestFixtures fixtures;

    protected MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }
}
