package com.gm.kafka.stream.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
//@EqualsAndHashCode(exclude = "id")
@ToString
@Getter
public class TweetMessage {

    private String id;
    private String text;
    private TweetWindow tweetWindow;

}
