package com.kuklin.manageapp.bots.channelposter.components.schedulers;

import com.kuklin.manageapp.bots.channelposter.components.PostParser;
import com.kuklin.manageapp.bots.channelposter.entities.PostQueue;
import com.kuklin.manageapp.bots.channelposter.entities.parser.RedditPost;
import com.kuklin.manageapp.bots.channelposter.entities.parser.Subreddit;
import com.kuklin.manageapp.bots.channelposter.model.RedditPostDto;
import com.kuklin.manageapp.bots.channelposter.services.PostQueueService;
import com.kuklin.manageapp.bots.channelposter.services.parser.RedditPostService;
import com.kuklin.manageapp.bots.channelposter.services.parser.SubredditService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubredditPosterScheduler implements ScheduleProcessor {

    private final PostParser parser;
    private final SubredditService subredditService;
    private final RedditPostService redditPostService;
    private final PostQueueService postQueueService;
    private static final int POST_COUNT_MAX = 50;

    @Override
    public void process() {
        int queueSize = postQueueService.getPostsByStatus(PostQueue.PostQueueStatus.QUEUED).size();
        if (queueSize > POST_COUNT_MAX) return;
        List<Subreddit> subreddits = subredditService.getAllActive();
        log.info(getSchedulerName() + " has " + subreddits.size() + " subreddit list size!");
        for (Subreddit subreddit : subreddits) {
            try {
                ThreadUtil.sleep(1000);
                List<RedditPostDto> posts = parser.parseSubreddit(subreddit);
                log.info("{} find {} posts for {} subreddit!", getSchedulerName(), posts.size(), subreddit.getName());
                redditPostService.saveIfNotExistsAll(posts);

            } catch (Exception e) {
                log.warn("{} error! cant parse subreddit! {}", getSchedulerName(), subreddit.getName());
            }
        }
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
