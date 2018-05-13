package com.gm.kafka.stream.model;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class TweetWindow {
    private LocalDateTime startWindow;
    private LocalDateTime endWindow;
}
