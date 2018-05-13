package com.gm.kafka.stream;

import com.gm.kafka.stream.model.TweetMessage;
import com.gm.kafka.stream.model.TweetWindow;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.binder.kafka.streams.QueryableStoreRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.gm.kafka.stream.TweetMessageBinding.*;
import static java.lang.String.valueOf;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.ThreadLocalRandom.current;
import static org.springframework.kafka.support.KafkaHeaders.MESSAGE_KEY;
import static org.springframework.messaging.support.MessageBuilder.withPayload;

@EnableBinding(TweetMessageBinding.class)
@SpringBootApplication
@Slf4j
public class TwitterTrendApplication {

    public static final DateTimeFormatter DATE_FORMATTER = ofPattern("EEE MMM dd HH:mm:ss Z yyyy");
    public static final DateTimeFormatter DATE_FORMATTER_TWITTER = ofPattern("EEE, dd MMM yyyy HH:mm:ss Z");

    private static LocalDateTime getLocalDateTime(String dateTime) {
        return LocalDateTime.parse(dateTime, DATE_FORMATTER);
    }

    @Component
    public static class TweetMessageSource implements ApplicationRunner {


        private final MessageChannel out;

        public TweetMessageSource(TweetMessageBinding binding) {
            this.out = binding.tweetMessageEventsOut();
        }

        @Override
        public void run(ApplicationArguments args) throws Exception {

            try {
                final LocalDateTimeHolder startDate = new LocalDateTimeHolder(getLocalDateTime("Sun Feb 28 15:23:48 +0000 2010"));
                Runnable runnable = () -> {
                    try {

                        int randomSec = current().nextInt(0, 59);
                        LocalDateTime newStartDate = startDate.localDateTime;

                        String messageText = "tweet msg " + valueOf(current().nextLong(0, 51));


                        TweetMessage tweetMessage = new TweetMessage(randomUUID().toString(),
                                messageText,
                                new TweetWindow(newStartDate, newStartDate.withSecond(randomSec + 1)));

                        Message<TweetMessage> message = withPayload(tweetMessage)
                                .setHeader(MESSAGE_KEY, tweetMessage.getId().getBytes())
                                .build();

                        startDate.localDateTime = newStartDate;
                        this.out.send(message);
                    } catch (Exception e) {
                        log.error("Exception while creating TwitterMessage:", e);
                    }

                };
                Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(runnable, 1, 1, TimeUnit.SECONDS);
            } catch (Exception exp) {
                log.error("Exception while executing :", exp);
            }
        }

    }

    @Component
    public static class TweetMessageSink {

        @StreamListener
        @SendTo(TWEET_MSG_COUNTS_OUT)
        public KStream<String, Long> process(@Input(TWEET_MSG_IN) KStream<String, TweetMessage> events) {
            return events
                    .map((key, value) -> {
                        return new KeyValue<>(value.getText(), "blah");
                    })
                    .groupByKey()
                    .count(Materialized.as(TWEET_MSG_COUNTS_MV))
                    .toStream();
        }
    }

    @Component
    @Slf4j
    public static class TweeMessageCountSink {

        @StreamListener
        public void process(@Input(TWEET_MSG_COUNTS_IN) KTable<String, Long> counts) {
            counts
                    .toStream()
                    .foreach((key, value) -> log.info(key + '=' + value));
        }
    }

    @RestController
    @RequestMapping(value = "/trending/tweet")
    public static class TwitterTrendController {

        private final QueryableStoreRegistry registry;

        public TwitterTrendController(QueryableStoreRegistry registry) {
            this.registry = registry;
        }

        @GetMapping("/bycount")
        Map<String, Long> counts() {
            ReadOnlyKeyValueStore<String, Long> store = registry.getQueryableStoreType(TWEET_MSG_COUNTS_MV, QueryableStoreTypes.keyValueStore());

            Map<String, Long> m = new HashMap<>();
            KeyValueIterator<String, Long> iterator = store.all();
            while (iterator.hasNext()) {
                KeyValue<String, Long> next = iterator.next();
                m.put(next.key, next.value);
            }
            return m;
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TwitterTrendApplication.class, args);
    }
}

interface TweetMessageBinding {

    String TWEET_MSG_OUT = "tweetmsgout";
    String TWEET_MSG_IN = "tweetmsgin";

    String TWEET_MSG_COUNTS_OUT = "tweetmsgcntout";
    String TWEET_MSG_COUNTS_IN = "tweetmsgcntin";
    String TWEET_MSG_COUNTS_MV = "tweetmsgmv";

    @Input(TWEET_MSG_COUNTS_IN)
    KTable<String, Long> pageCountsIn();

    @Output(TWEET_MSG_COUNTS_OUT)
    KStream<String, Long> pageCountOut();

    @Output(TWEET_MSG_OUT)
    MessageChannel tweetMessageEventsOut();

    @Input(TWEET_MSG_IN)
    KStream<String, TweetMessage> tweetMessageEventsIn();
}

@AllArgsConstructor
class LocalDateTimeHolder {
    LocalDateTime localDateTime;
}
