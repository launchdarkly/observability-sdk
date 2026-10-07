package com.launchdarkly.observability.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLClientTest {

    @Test
    void samplingQueryIsLoadedOnceAndSharedAcrossThreads() throws Exception {
        String first = GraphQLClient.loadQuery("graphql/GetSamplingConfigQuery.graphql");
        assertTrue(first.contains("GetSamplingConfig"));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> GraphQLClient.loadQuery("graphql/GetSamplingConfigQuery.graphql"));
            }
            for (Future<String> loaded : pool.invokeAll(tasks)) {
                assertSame(first, loaded.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
