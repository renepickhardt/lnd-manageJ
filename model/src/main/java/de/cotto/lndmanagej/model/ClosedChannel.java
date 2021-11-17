package de.cotto.lndmanagej.model;

public abstract class ClosedChannel extends ClosedOrClosingChannel {
    public ClosedChannel(
            ChannelId channelId,
            ChannelPoint channelPoint,
            Coins capacity,
            Pubkey ownPubkey,
            Pubkey remotePubkey,
            String closeTransactionHash
    ) {
        super(channelId, channelPoint, capacity, ownPubkey, remotePubkey, closeTransactionHash);
    }
}
