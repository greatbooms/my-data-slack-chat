package com.mydata.slackbot;

import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.slack.SlackChannelConnector;
import com.mydata.connectors.slack.SlackClient;
import com.mydata.connectors.slack.SlackRawDocumentFactory;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.ingestion.IngestionPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SlackMessageIngestionEventConsumer implements SlackMessageEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(SlackMessageIngestionEventConsumer.class);

    private final DataSourceRepository dataSources;
    private final IngestionPipelineService pipeline;
    private final SlackRawDocumentFactory rawDocuments;

    public SlackMessageIngestionEventConsumer(
        DataSourceRepository dataSources,
        IngestionPipelineService pipeline,
        SlackRawDocumentFactory rawDocuments
    ) {
        this.dataSources = dataSources;
        this.pipeline = pipeline;
        this.rawDocuments = rawDocuments;
    }

    @Override
    public void accept(SlackMessageIngestionEvent event) {
        if (event == null || isBlank(event.channelId()) || isBlank(event.messageTimestamp()) || isBlank(event.text())) {
            return;
        }

        List<DataSourceEntity> matchingSources = dataSources.findActiveOrderByCreatedAtDesc().stream()
            .filter(dataSource -> dataSource.getType() == DataSourceType.SLACK)
            .filter(dataSource -> dataSource.getStatus() == DataSourceStatus.ACTIVE)
            .filter(dataSource -> event.channelId().equals(dataSource.configValue(SlackChannelConnector.CHANNEL_ID_CONFIG_KEY)))
            .toList();
        if (matchingSources.isEmpty()) {
            return;
        }

        for (DataSourceEntity dataSource : matchingSources) {
            try {
                SlackClient.SlackMessage message = toSlackMessage(event);
                DataSourceSnapshot source = DataSourceSnapshot.from(dataSource);
                pipeline.ingest(
                    source.workspaceId(),
                    source.id(),
                    rawDocuments.toRawDocument(source, message)
                );
            } catch (RuntimeException exception) {
                log.warn(
                    "Slack 메시지 event 적재 실패. teamId={}, channelId={}, messageTs={}, dataSourceId={}",
                    event.teamId(),
                    event.channelId(),
                    event.messageTimestamp(),
                    dataSource.getId(),
                    exception
                );
            }
        }
    }

    private SlackClient.SlackMessage toSlackMessage(SlackMessageIngestionEvent event) {
        String threadTs = blankToDefault(event.threadTimestamp(), event.messageTimestamp());
        return new SlackClient.SlackMessage(
            event.channelId(),
            event.messageTimestamp(),
            threadTs,
            event.userId(),
            event.text(),
            instantFromSlackTs(event.messageTimestamp()),
            !event.messageTimestamp().equals(threadTs),
            false,
            null
        );
    }

    private Instant instantFromSlackTs(String ts) {
        if (ts == null || ts.isBlank()) {
            return Instant.EPOCH;
        }
        String[] parts = ts.split("\\.", 2);
        long seconds = Long.parseLong(parts[0]);
        long nanos = 0L;
        if (parts.length == 2) {
            String fraction = (parts[1] + "000000000").substring(0, 9);
            nanos = Long.parseLong(fraction);
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
