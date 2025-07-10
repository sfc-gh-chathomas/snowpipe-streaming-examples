//Demo Java Code
package com.snowflake.snowpipestreaming.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snowflake.ingest.streaming.SnowflakeStreamingIngestChannel;
import com.snowflake.ingest.streaming.SnowflakeStreamingIngestClient;
import com.snowflake.ingest.streaming.SnowflakeStreamingIngestClientFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROFILE_PATH = "profile.json"; //depends on your folder structure
    private static final int MAX_ROWS = 100_000;
    private static final int POLL_ATTEMPTS = 30;
    private static final long POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);

    public static void main(String[] args) {
        try {
            // Load properties from profile.json
            Properties props = new Properties();
            JsonNode jsonNode = MAPPER.readTree(Files.readAllBytes(Paths.get(PROFILE_PATH)));
            jsonNode.fields().forEachRemaining(entry -> 
                props.put(entry.getKey(), entry.getValue().asText()));

            // Create Snowflake Streaming Ingest Client
            SnowflakeStreamingIngestClient client = SnowflakeStreamingIngestClientFactory.builder(
                    "MY_CLIENT_" + UUID.randomUUID(),
                    "MY_DATABASE",
                    "MY_SCHEMA",
                    "MY_PIPE")
                    .setProperties(props)
                    .build();

            // Open a channel for data ingestion
            SnowflakeStreamingIngestChannel channel = client.openChannel(
                    "MY_CHANNEL_" + UUID.randomUUID(), "0").getChannel();

            // Ingest rows
            for (int i = 1; i <= MAX_ROWS; i++) {
                String rowId = String.valueOf(i);
                Map<String, Object> row = Map.of(
                    "c1", i,
                    "c2", rowId,
                    "ts", Instant.now().toEpochMilli() / 1000.0
                );
                channel.appendRow(row, rowId);
            }

            // Wait for ingestion to complete
            for (int attempt = 1; attempt <= POLL_ATTEMPTS; attempt++) {
                String latestOffset = channel.getChannelStatus().getLatestOffsetToken();
                System.out.println("");
                System.out.println("Latest offset token: " + latestOffset);
                System.out.println("");
                
                if (latestOffset.equals(String.valueOf(MAX_ROWS))) {
                    System.out.println("All data committed successfully");
                    break;
                }
                
                Thread.sleep(POLL_INTERVAL_MS);
            }

            // Close resources
            channel.close();
            client.close();
            System.out.println("Data ingestion completed");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error during data ingestion: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
