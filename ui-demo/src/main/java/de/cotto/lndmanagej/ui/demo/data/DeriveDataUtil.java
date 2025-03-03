package de.cotto.lndmanagej.ui.demo.data;

import de.cotto.lndmanagej.controller.dto.FeeReportDto;
import de.cotto.lndmanagej.controller.dto.FlowReportDto;
import de.cotto.lndmanagej.controller.dto.OnChainCostsDto;
import de.cotto.lndmanagej.controller.dto.RebalanceReportDto;
import de.cotto.lndmanagej.model.ChannelId;
import de.cotto.lndmanagej.model.Coins;
import de.cotto.lndmanagej.model.FeeReport;
import de.cotto.lndmanagej.model.FlowReport;
import de.cotto.lndmanagej.model.OpenInitiator;
import de.cotto.lndmanagej.model.PoliciesForLocalChannel;
import de.cotto.lndmanagej.model.Policy;
import de.cotto.lndmanagej.model.RebalanceReport;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import static de.cotto.lndmanagej.model.OpenInitiator.LOCAL;
import static de.cotto.lndmanagej.model.OpenInitiator.REMOTE;

public final class DeriveDataUtil {

    private static final Map<ChannelId, Random> RANDOM_GENERATOR = new HashMap<>();
    private static final Coins MAX_HTLC = Coins.ofSatoshis(1_000_000);

    private DeriveDataUtil() {
        // util class
    }

    private static RandomGenerator getOrCreateRandomGenerator(ChannelId channelId) {
        Random random = RANDOM_GENERATOR.get(channelId);
        if (random == null) {
            random = new Random(channelId.getShortChannelId());
            RANDOM_GENERATOR.put(channelId, random);
        }
        return random;
    }

    static RebalanceReportDto deriveRebalanceReport(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        return RebalanceReportDto.createFromModel(new RebalanceReport(
                Coins.ofSatoshis(rand.nextInt(5000)),
                Coins.ofSatoshis(rand.nextInt(1000)),
                Coins.ofSatoshis(rand.nextInt(5000)),
                Coins.ofSatoshis(rand.nextInt(2000)),
                Coins.ofSatoshis(rand.nextInt(3000)),
                Coins.ofSatoshis(rand.nextInt(500))
        ));
    }

    static Set<String> deriveWarnings(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        boolean showWarning = rand.nextInt(10) != 0;
        int updates = (rand.nextInt(10) + 5) * 100_000;
        return showWarning ? Set.of("Channel has accumulated " + updates + " updates.") : Set.of();
    }

    static FlowReportDto deriveFlowReport(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        FlowReport flowReport = new FlowReport(
                Coins.ofSatoshis(rand.nextLong(100_000)),
                Coins.ofSatoshis(rand.nextLong(100_000)),
                Coins.ofSatoshis(rand.nextLong(100)),
                Coins.ofSatoshis(rand.nextLong(100)),
                Coins.ofSatoshis(rand.nextLong(10)),
                Coins.ofSatoshis(rand.nextLong(100)),
                Coins.ofSatoshis(rand.nextLong(200)),
                Coins.ofSatoshis(rand.nextLong(10)),
                Coins.ofSatoshis(rand.nextLong(1000))
        );
        return FlowReportDto.createFromModel(flowReport);
    }

    static FeeReportDto deriveFeeReport(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        long earned = rand.nextLong(1_000_000);
        long sourced = rand.nextLong(100_000);
        return FeeReportDto.createFromModel(
                new FeeReport(Coins.ofMilliSatoshis(earned), Coins.ofMilliSatoshis(sourced)));
    }

    static OnChainCostsDto deriveOnChainCosts(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        return new OnChainCostsDto(
                String.valueOf(rand.nextLong(2000)),
                String.valueOf(rand.nextLong(2000)),
                String.valueOf(rand.nextLong(3000))
        );
    }

    static OpenInitiator deriveOpenInitiator(ChannelId channelId) {
        return getOrCreateRandomGenerator(channelId).nextBoolean() ? LOCAL : REMOTE;
    }

    static PoliciesForLocalChannel derivePolicies(ChannelId channelId) {
        return new PoliciesForLocalChannel(derivePolicy(channelId), derivePolicy(channelId));
    }

    static Policy derivePolicy(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        long feeRate = rand.nextLong(100) * 10;
        Coins baseFee = Coins.ofMilliSatoshis(rand.nextLong(2) * 1000);
        boolean enabled = rand.nextInt(10) == 0;
        int timeLockDelta = (rand.nextInt(5) + 1) * 10;
        return new Policy(feeRate, baseFee, enabled, timeLockDelta, MAX_HTLC);
    }

    static Set<String> deriveChannelWarnings(ChannelId channelId) {
        RandomGenerator rand = getOrCreateRandomGenerator(channelId);
        boolean showWarning = rand.nextInt(20) != 0;
        int days = rand.nextInt(30) + 30;
        return showWarning ? Set.of("No flow in the past " + days + " days.") : Set.of();
    }
}
