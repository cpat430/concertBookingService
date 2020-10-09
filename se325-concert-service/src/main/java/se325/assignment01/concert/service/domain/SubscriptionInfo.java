package se325.assignment01.concert.service.domain;

import se325.assignment01.concert.common.dto.ConcertInfoSubscriptionDTO;

import javax.ws.rs.container.AsyncResponse;

/**
 * Represents the subscription info.
 * sub      Asynchronous response which contains the 'thread' of the subscribers
 * subInfo  the concert information containing when to notify a subscriber.
 */
public class SubscriptionInfo {

    private AsyncResponse sub;
    private ConcertInfoSubscriptionDTO subInfo;

    public SubscriptionInfo(AsyncResponse sub, ConcertInfoSubscriptionDTO subInfo) {
        this.sub = sub;
        this.subInfo = subInfo;
    }

    public AsyncResponse getAsyncResponse() {
        return this.sub;
    }

    public ConcertInfoSubscriptionDTO getSubInfo() {
        return this.subInfo;
    }
}
