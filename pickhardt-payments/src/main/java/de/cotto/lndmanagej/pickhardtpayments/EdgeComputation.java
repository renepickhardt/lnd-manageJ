package de.cotto.lndmanagej.pickhardtpayments;

import de.cotto.lndmanagej.grpc.GrpcGetInfo;
import de.cotto.lndmanagej.grpc.GrpcGraph;
import de.cotto.lndmanagej.model.ChannelId;
import de.cotto.lndmanagej.model.Coins;
import de.cotto.lndmanagej.model.DirectedChannelEdge;
import de.cotto.lndmanagej.model.Edge;
import de.cotto.lndmanagej.model.EdgeWithLiquidityInformation;
import de.cotto.lndmanagej.model.LocalChannel;
import de.cotto.lndmanagej.model.Policy;
import de.cotto.lndmanagej.model.Pubkey;
import de.cotto.lndmanagej.pickhardtpayments.model.EdgesWithLiquidityInformation;
import de.cotto.lndmanagej.service.BalanceService;
import de.cotto.lndmanagej.service.ChannelService;
import de.cotto.lndmanagej.service.LiquidityBoundsService;
import de.cotto.lndmanagej.service.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Component
public class EdgeComputation {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final GrpcGraph grpcGraph;
    private final GrpcGetInfo grpcGetInfo;
    private final ChannelService channelService;
    private final NodeService nodeService;
    private final BalanceService balanceService;
    private final LiquidityBoundsService liquidityBoundsService;

    public EdgeComputation(
            GrpcGraph grpcGraph,
            GrpcGetInfo grpcGetInfo,
            ChannelService channelService,
            NodeService nodeService,
            BalanceService balanceService,
            LiquidityBoundsService liquidityBoundsService
    ) {
        this.grpcGraph = grpcGraph;
        this.grpcGetInfo = grpcGetInfo;
        this.channelService = channelService;
        this.nodeService = nodeService;
        this.balanceService = balanceService;
        this.liquidityBoundsService = liquidityBoundsService;
    }

    public EdgesWithLiquidityInformation getEdges() {
        Set<DirectedChannelEdge> channelEdges = grpcGraph.getChannelEdges().orElse(null);
        if (channelEdges == null) {
            logger.warn("Unable to get graph");
            return EdgesWithLiquidityInformation.EMPTY;
        }
        Set<EdgeWithLiquidityInformation> edgesWithLiquidityInformation = new LinkedHashSet<>();
        Pubkey ownPubkey = grpcGetInfo.getPubkey();
        for (DirectedChannelEdge channelEdge : channelEdges) {
            if (shouldIgnore(channelEdge)) {
                continue;
            }
            ChannelId channelId = channelEdge.channelId();
            Pubkey pubkey1 = channelEdge.source();
            Pubkey pubkey2 = channelEdge.target();
            Edge edge = new Edge(channelId, pubkey1, pubkey2, channelEdge.capacity(), channelEdge.policy());
            edgesWithLiquidityInformation.add(getEdgeWithLiquidityInformation(edge, ownPubkey));
        }
        return new EdgesWithLiquidityInformation(edgesWithLiquidityInformation);
    }

    public EdgeWithLiquidityInformation getEdgeWithLiquidityInformation(Edge edge) {
        Pubkey ownPubkey = grpcGetInfo.getPubkey();
        return getEdgeWithLiquidityInformation(edge, ownPubkey);
    }

    private EdgeWithLiquidityInformation getEdgeWithLiquidityInformation(Edge edge, Pubkey ownPubkey) {
        Coins knownLiquidity = getKnownLiquidity(edge, ownPubkey).orElse(null);
        if (knownLiquidity == null) {
            Coins lowerBound = liquidityBoundsService.getAssumedLiquidityLowerBound(edge);
            Coins upperBound = getAvailableLiquidityUpperBound(edge, lowerBound);
            return EdgeWithLiquidityInformation.forLowerAndUpperBound(edge, lowerBound, upperBound);
        }
        Coins usableLiquidityFromKnownLiquidity = getUsableLiquidityFromKnownLiquidity(knownLiquidity);
        return EdgeWithLiquidityInformation.forKnownLiquidity(edge, usableLiquidityFromKnownLiquidity);
    }

    private Coins getUsableLiquidityFromKnownLiquidity(Coins knownLiquidity) {
        Coins withFeeReserve = Coins.ofMilliSatoshis((long) (knownLiquidity.milliSatoshis() * 0.99));
        return withFeeReserve.subtract(Coins.ofSatoshis(1_000)).maximum(Coins.NONE);
    }

    private boolean shouldIgnore(DirectedChannelEdge channelEdge) {
        Policy policy = channelEdge.policy();
        return policy.disabled() || policy.baseFee().isPositive();
    }

    private Optional<Coins> getKnownLiquidity(Edge edge, Pubkey ownPubKey) {
        Pubkey source = edge.startNode();
        ChannelId channelId = edge.channelId();
        if (ownPubKey.equals(source)) {
            return getLocalChannelAvailableLocal(channelId);
        }
        Pubkey target = edge.endNode();
        if (ownPubKey.equals(target)) {
            return getLocalChannelAvailableRemote(channelId);
        }
        return Optional.empty();
    }

    private Optional<Coins> getLocalChannelAvailableLocal(ChannelId channelId) {
        return getLocalChannelAvailable(channelId, balanceService::getAvailableLocalBalance);
    }

    private Optional<Coins> getLocalChannelAvailableRemote(ChannelId channelId) {
        return getLocalChannelAvailable(channelId, balanceService::getAvailableRemoteBalance);
    }

    private Optional<Coins> getLocalChannelAvailable(ChannelId channelId, Function<ChannelId, Coins> balanceProvider) {
        LocalChannel localChannel = channelService.getLocalChannel(channelId).orElse(null);
        if (localChannel == null) {
            return Optional.of(Coins.NONE);
        }
        if (nodeService.getNode(localChannel.getRemotePubkey()).online()) {
            return Optional.of(balanceProvider.apply(channelId));
        }
        return Optional.of(Coins.NONE);
    }

    private Coins getAvailableLiquidityUpperBound(Edge edge, Coins lowerBound) {
        Coins upperBound = liquidityBoundsService.getAssumedLiquidityUpperBound(edge).orElse(null);
        return edge.capacity().minimum(upperBound).maximum(lowerBound);
    }
}
