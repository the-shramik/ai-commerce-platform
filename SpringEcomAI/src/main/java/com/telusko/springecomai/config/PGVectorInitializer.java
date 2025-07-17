package com.telusko.springecomai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PGVectorInitializer {

    @Autowired
    private JdbcClient jdbcClient;

    @PostConstruct
    public void init() {
        // Query to count the number of records in the vector_store table
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM vector_store")
                .query(Integer.class)
                .single();

        // Print total vectors found
        System.out.println("No. of vectors in PGVector: " + count);

        // If no vectors are found, notify that embedding will happen on product insertion
        if (count == 0) {
            System.out.println("Vector Store is empty! No need to load manually. Product data will be embedded when inserted.");
        }
    }
}
