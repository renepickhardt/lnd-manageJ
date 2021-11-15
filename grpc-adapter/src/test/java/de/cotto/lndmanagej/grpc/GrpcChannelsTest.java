package de.cotto.lndmanagej.grpc;

import de.cotto.lndmanagej.model.ChannelId;
import lnrpc.Channel;
import lnrpc.ChannelCloseSummary;
import lnrpc.ChannelConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static de.cotto.lndmanagej.model.BalanceInformationFixtures.BALANCE_INFORMATION;
import static de.cotto.lndmanagej.model.ChannelFixtures.CAPACITY;
import static de.cotto.lndmanagej.model.ChannelIdFixtures.CHANNEL_ID;
import static de.cotto.lndmanagej.model.ChannelIdFixtures.CHANNEL_ID_2;
import static de.cotto.lndmanagej.model.ChannelPointFixtures.CHANNEL_POINT;
import static de.cotto.lndmanagej.model.LocalOpenChannelFixtures.LOCAL_OPEN_CHANNEL;
import static de.cotto.lndmanagej.model.LocalOpenChannelFixtures.LOCAL_OPEN_CHANNEL_2;
import static de.cotto.lndmanagej.model.PubkeyFixtures.PUBKEY;
import static de.cotto.lndmanagej.model.PubkeyFixtures.PUBKEY_2;
import static de.cotto.lndmanagej.model.UnresolvedClosedChannelFixtures.CLOSED_CHANNEL_UNRESOLVED_ID;
import static de.cotto.lndmanagej.model.UnresolvedClosedChannelFixtures.UNRESOLVED_CLOSED_CHANNEL;
import static de.cotto.lndmanagej.model.UnresolvedClosedChannelFixtures.UNRESOLVED_CLOSED_CHANNEL_2;
import static lnrpc.ChannelCloseSummary.ClosureType.ABANDONED;
import static lnrpc.ChannelCloseSummary.ClosureType.FUNDING_CANCELED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcChannelsTest {
    @InjectMocks
    private GrpcChannels grpcChannels;

    @Mock
    private GrpcService grpcService;

    @Mock
    private GrpcGetInfo grpcGetInfo;

    @BeforeEach
    void setUp() {
        when(grpcGetInfo.getPubkey()).thenReturn(PUBKEY);
    }

    @Test
    void getChannels_no_channels() {
        assertThat(grpcChannels.getChannels()).isEmpty();
    }

    @Test
    void getChannels() {
        when(grpcService.getChannels()).thenReturn(List.of(channel(CHANNEL_ID), channel(CHANNEL_ID_2)));
        assertThat(grpcChannels.getChannels()).containsExactlyInAnyOrder(LOCAL_OPEN_CHANNEL, LOCAL_OPEN_CHANNEL_2);
    }

    @Test
    void getUnresolvedClosedChannels_empty() {
        assertThat(grpcChannels.getUnresolvedClosedChannels()).isEmpty();
    }

    @Test
    void getUnresolvedClosedChannels() {
        when(grpcService.getClosedChannels()).thenReturn(
                List.of(closedChannel(CHANNEL_ID.getShortChannelId()), closedChannel(CHANNEL_ID_2.getShortChannelId()))
        );
        assertThat(grpcChannels.getUnresolvedClosedChannels())
                .containsExactlyInAnyOrder(UNRESOLVED_CLOSED_CHANNEL, UNRESOLVED_CLOSED_CHANNEL_2);
    }

    @Test
    void getUnresolvedClosedChannels_with_zero_channel_id() {
        when(grpcService.getClosedChannels()).thenReturn(
                List.of(closedChannel(CHANNEL_ID.getShortChannelId()), closedChannel(0))
        );
        assertThat(grpcChannels.getUnresolvedClosedChannels()).containsExactlyInAnyOrder(
                UNRESOLVED_CLOSED_CHANNEL,
                CLOSED_CHANNEL_UNRESOLVED_ID
        );
    }

    @Test
    void getUnresolvedClosedChannels_ignores_abandoned() {
        when(grpcService.getClosedChannels()).thenReturn(
                List.of(closedChannel(CHANNEL_ID.getShortChannelId()), closedChannelWithType(ABANDONED))
        );
        assertThat(grpcChannels.getUnresolvedClosedChannels()).containsExactlyInAnyOrder(
                UNRESOLVED_CLOSED_CHANNEL
        );
    }

    @Test
    void getUnresolvedClosedChannels_ignores_funding_canceled() {
        when(grpcService.getClosedChannels()).thenReturn(
                List.of(closedChannel(CHANNEL_ID.getShortChannelId()), closedChannelWithType(FUNDING_CANCELED))
        );
        assertThat(grpcChannels.getUnresolvedClosedChannels()).containsExactlyInAnyOrder(
                UNRESOLVED_CLOSED_CHANNEL
        );
    }

    @Test
    void getChannel() {
        when(grpcService.getChannels()).thenReturn(List.of(channel(CHANNEL_ID_2), channel(CHANNEL_ID)));
        assertThat(grpcChannels.getChannel(CHANNEL_ID)).contains(LOCAL_OPEN_CHANNEL);
    }

    @Test
    void getChannel_empty() {
        assertThat(grpcChannels.getChannel(CHANNEL_ID)).isEmpty();
    }

    private Channel channel(ChannelId channelId) {
        ChannelConstraints localConstraints = ChannelConstraints.newBuilder()
                .setChanReserveSat(BALANCE_INFORMATION.localReserve().satoshis())
                .build();
        ChannelConstraints remoteConstraints = ChannelConstraints.newBuilder()
                .setChanReserveSat(BALANCE_INFORMATION.remoteReserve().satoshis())
                .build();
        return Channel.newBuilder()
                .setChanId(channelId.getShortChannelId())
                .setCapacity(CAPACITY.satoshis())
                .setRemotePubkey(PUBKEY_2.toString())
                .setChannelPoint(CHANNEL_POINT.toString())
                .setLocalBalance(BALANCE_INFORMATION.localBalance().satoshis())
                .setRemoteBalance(BALANCE_INFORMATION.remoteBalance().satoshis())
                .setLocalConstraints(localConstraints)
                .setRemoteConstraints(remoteConstraints)
                .build();
    }

    private ChannelCloseSummary closedChannel(long channelId) {
        return ChannelCloseSummary.newBuilder()
                .setChanId(channelId)
                .setRemotePubkey(PUBKEY_2.toString())
                .setCapacity(CAPACITY.satoshis())
                .setChannelPoint(CHANNEL_POINT.toString())
                .build();
    }

    private ChannelCloseSummary closedChannelWithType(ChannelCloseSummary.ClosureType abandoned) {
        return ChannelCloseSummary.newBuilder()
                .setChanId(0)
                .setRemotePubkey(PUBKEY_2.toString())
                .setCapacity(CAPACITY.satoshis())
                .setChannelPoint(CHANNEL_POINT.toString())
                .setCloseType(abandoned)
                .build();
    }
}