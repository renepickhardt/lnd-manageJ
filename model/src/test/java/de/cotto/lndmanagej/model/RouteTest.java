package de.cotto.lndmanagej.model;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static de.cotto.lndmanagej.model.BasicRouteFixtures.BASIC_ROUTE;
import static de.cotto.lndmanagej.model.ChannelFixtures.CAPACITY;
import static de.cotto.lndmanagej.model.ChannelIdFixtures.CHANNEL_ID;
import static de.cotto.lndmanagej.model.ChannelIdFixtures.CHANNEL_ID_2;
import static de.cotto.lndmanagej.model.ChannelIdFixtures.CHANNEL_ID_3;
import static de.cotto.lndmanagej.model.EdgeFixtures.EDGE;
import static de.cotto.lndmanagej.model.EdgeFixtures.EDGE_2_3;
import static de.cotto.lndmanagej.model.EdgeFixtures.EDGE_3_4;
import static de.cotto.lndmanagej.model.PolicyFixtures.POLICY_1;
import static de.cotto.lndmanagej.model.PubkeyFixtures.PUBKEY;
import static de.cotto.lndmanagej.model.PubkeyFixtures.PUBKEY_2;
import static de.cotto.lndmanagej.model.PubkeyFixtures.PUBKEY_3;
import static de.cotto.lndmanagej.model.PubkeyFixtures.PUBKEY_4;
import static de.cotto.lndmanagej.model.RouteFixtures.ROUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RouteTest {

    private static final int ONE_MILLION = 1_000_000;
    private static final int TIME_LOCK_DELTA = 40;
    private static final int BLOCK_HEIGHT = 700_000;
    private static final Coins MAX_HTLC = Coins.ofSatoshis(12_345);

    @Test
    void amount() {
        assertThat(ROUTE.getAmount()).isEqualTo(Coins.ofSatoshis(100));
    }

    @Test
    void edges() {
        assertThat(ROUTE.getEdges()).isEqualTo(BASIC_ROUTE.edges());
    }

    @Test
    void edgesWithLiquidityInformation_contains_expected_edges() {
        assertThat(ROUTE.getEdgesWithLiquidityInformation().stream().map(EdgeWithLiquidityInformation::edge).toList())
                .isEqualTo(BASIC_ROUTE.edges());
    }

    @Test
    void edgesWithLiquidityInformation() {
        assertThat(ROUTE.getEdgesWithLiquidityInformation()).containsExactly(
                EdgeWithLiquidityInformation.forUpperBound(EDGE, EDGE.capacity()),
                EdgeWithLiquidityInformation.forUpperBound(EDGE_2_3, EDGE_2_3.capacity()),
                EdgeWithLiquidityInformation.forUpperBound(EDGE_3_4, EDGE_3_4.capacity())
        );
    }

    @Test
    void default_liquidity_information() {
        Edge edge1 = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, Coins.ofSatoshis(99), POLICY_1);
        Edge edge2 = new Edge(CHANNEL_ID_2, PUBKEY, PUBKEY_2, Coins.ofSatoshis(199), POLICY_1);
        Route route = new Route(new BasicRoute(List.of(edge1, edge2), Coins.ofSatoshis(1)));
        assertThat(route.getProbability()).isEqualTo(0.99 * 0.995);
    }

    @Test
    void explicit_liquidity_information() {
        Edge edge1 = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, Coins.ofSatoshis(99), POLICY_1);
        Edge edge2 = new Edge(CHANNEL_ID_2, PUBKEY, PUBKEY_2, Coins.ofSatoshis(199), POLICY_1);
        BasicRoute basicRoute = new BasicRoute(List.of(edge1, edge2), Coins.ofSatoshis(1));
        Route route = new Route(List.of(
                EdgeWithLiquidityInformation.forKnownLiquidity(edge1, Coins.ofSatoshis(99)),
                EdgeWithLiquidityInformation.forUpperBound(edge2, Coins.ofSatoshis(199))
        ), basicRoute.amount());
        assertThat(route.getProbability()).isEqualTo(1 * 0.995);
    }

    @Test
    void getProbability() {
        assertThat(ROUTE.getProbability()).isEqualTo(0.999_985_714_354_421_7);
    }

    @Test
    void getProbability_within_known_liquidity() {
        long availableLiquiditySat = 100;
        Coins capacity = Coins.ofSatoshis(200);
        Coins amount = Coins.ofSatoshis(99);
        Edge edge = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, capacity, POLICY_1);
        BasicRoute basicRoute = new BasicRoute(List.of(edge), amount);
        EdgeWithLiquidityInformation edgeWithLiquidityInformation =
                EdgeWithLiquidityInformation.forKnownLiquidity(edge, Coins.ofSatoshis(availableLiquiditySat));
        Route route = new Route(List.of(edgeWithLiquidityInformation), basicRoute.amount());
        assertThat(route.getProbability()).isEqualTo(1.0);
    }

    @Test
    void getProbability_exactly_known_liquidity() {
        Route route = routeForAmountAndCapacityAndKnownLiquidity(100, 200, 100);
        assertThat(route.getProbability())
                .isEqualTo(1.0);
    }

    @Test
    void getProbability_below_lower_bound() {
        Coins capacity = Coins.ofSatoshis(400);
        Coins amount = Coins.ofSatoshis(100);
        Edge edge = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, capacity, POLICY_1);
        EdgeWithLiquidityInformation edgeWithLiquidityInformation =
                EdgeWithLiquidityInformation.forLowerBound(edge, Coins.ofSatoshis(200));
        Route route = new Route(List.of(edgeWithLiquidityInformation), amount);
        assertThat(route.getProbability()).isEqualTo(1.0);
    }

    @Test
    void getProbability_at_upper_bound() {
        Coins amount = Coins.ofSatoshis(100);
        EdgeWithLiquidityInformation edgeWithLiquidityInformation =
                EdgeWithLiquidityInformation.forUpperBound(EDGE, amount);
        Route route = new Route(List.of(edgeWithLiquidityInformation), amount);
        assertThat(route.getProbability()).isGreaterThan(0);
    }

    @Test
    void getProbability_above_known_liquidity() {
        Route route = routeForAmountAndCapacityAndKnownLiquidity(250, 300, 200);
        assertThat(route.getProbability()).isEqualTo(0.0);
    }

    @Test
    void getProbability_above_known_lower_bound_for_liquidity() {
        long lowerBoundSat = 100;
        long capacitySat = 200;
        int amountSat = 150;
        Edge edge = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, Coins.ofSatoshis(capacitySat), POLICY_1);
        BasicRoute basicRoute = new BasicRoute(List.of(edge), Coins.ofSatoshis(amountSat));
        EdgeWithLiquidityInformation edgeWithLiquidityInformation =
                EdgeWithLiquidityInformation.forLowerBound(edge, Coins.ofSatoshis(lowerBoundSat));
        Route route = new Route(List.of(edgeWithLiquidityInformation), basicRoute.amount());
        assertThat(route.getProbability())
                .isEqualTo(1.0 * (capacitySat + 1 - amountSat) / (capacitySat + 1 - lowerBoundSat));
    }

    @Test
    void getProbability_below_known_upper_bound_for_liquidity() {
        long upperBoundSat = 100;
        Coins capacity = Coins.ofSatoshis(200);
        Coins amount = Coins.ofSatoshis(80);
        Edge edge = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, capacity, POLICY_1);
        BasicRoute basicRoute = new BasicRoute(List.of(edge), amount);
        EdgeWithLiquidityInformation edgeWithLiquidityInformation =
                EdgeWithLiquidityInformation.forUpperBound(edge, Coins.ofSatoshis(upperBoundSat));
        Route route = new Route(List.of(edgeWithLiquidityInformation), basicRoute.amount());
        assertThat(route.getProbability())
                .isEqualTo(1.0 * (upperBoundSat + 1 - amount.satoshis()) / (upperBoundSat + 1));
    }

    @Test
    void fees_amount_with_milli_sat() {
        Coins amount = Coins.ofMilliSatoshis(1_500_000_111);
        int ppm1 = 50;
        int ppm2 = 100;
        Coins baseFee1 = Coins.ofMilliSatoshis(15);
        Coins baseFee2 = Coins.ofMilliSatoshis(10);
        Coins expectedFees =
                Coins.ofMilliSatoshis((long) (amount.milliSatoshis() * 1.0 * ppm2 / ONE_MILLION))
                        .add(baseFee2);
        Policy policy1 = policy(baseFee1, ppm1);
        Edge hop1 = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy1);
        Policy policy2 = policy(baseFee2, ppm2);
        Edge hop2 = new Edge(CHANNEL_ID_2, PUBKEY_2, PUBKEY_3, CAPACITY, policy2);
        BasicRoute basicRoute = new BasicRoute(List.of(hop1, hop2), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFees()).isEqualTo(expectedFees);
    }

    @Test
    void fees_one_hop() {
        Coins amount = Coins.ofSatoshis(1_500_000);
        Coins baseFee = Coins.ofMilliSatoshis(10);
        int ppm = 100;
        BasicRoute basicRoute = new BasicRoute(List.of(
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee, ppm))
        ), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFees()).isEqualTo(Coins.NONE);
    }

    @Test
    void fees_two_hops() {
        Coins amount = Coins.ofSatoshis(1_500_000);
        Coins baseFee1 = Coins.ofMilliSatoshis(10);
        Coins baseFee2 = Coins.ofMilliSatoshis(5);
        int ppm1 = 100;
        int ppm2 = 200;
        Coins expectedFees2 =
                Coins.ofMilliSatoshis((long) (amount.milliSatoshis() * 1.0 * ppm2 / ONE_MILLION))
                        .add(baseFee2);
        Coins expectedFees1 = Coins.NONE;
        Coins expectedFees = expectedFees1.add(expectedFees2);
        BasicRoute basicRoute = new BasicRoute(List.of(
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee1, ppm1)),
                new Edge(CHANNEL_ID_2, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee2, ppm2))
        ), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFees()).isEqualTo(expectedFees);
    }

    @Test
    void fees_three_hops() {
        Coins amount = Coins.ofSatoshis(3_000_000);
        Coins baseFee1 = Coins.ofMilliSatoshis(100);
        Coins baseFee2 = Coins.ofMilliSatoshis(50);
        Coins baseFee3 = Coins.ofMilliSatoshis(10);
        int ppm1 = 100;
        int ppm2 = 200;
        int ppm3 = 300;
        Coins expectedFees3 = Coins.NONE;
        Coins expectedFees2 =
                Coins.ofMilliSatoshis((long) (amount.milliSatoshis() * 1.0 * ppm3 / ONE_MILLION))
                        .add(baseFee3);
        long amountWithFeesLastHop = amount.add(expectedFees2).milliSatoshis();
        Coins expectedFees1 = Coins.ofMilliSatoshis(
                (long) (amountWithFeesLastHop * 1.0 * ppm2 / ONE_MILLION)
        ).add(baseFee2);
        Coins expectedFees = expectedFees1.add(expectedFees2).add(expectedFees3);
        BasicRoute basicRoute = new BasicRoute(List.of(
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee1, ppm1)),
                new Edge(CHANNEL_ID_2, PUBKEY_2, PUBKEY_3, CAPACITY, policy(baseFee2, ppm2)),
                new Edge(CHANNEL_ID_3, PUBKEY_3, PUBKEY_4, CAPACITY, policy(baseFee3, ppm3))
        ), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFees()).isEqualTo(expectedFees);
    }

    @Test
    void feesWithFirstHop_empty() {
        BasicRoute basicRoute = new BasicRoute(List.of(), Coins.ofSatoshis(1_500_000));
        Route route = new Route(basicRoute);
        assertThat(route.getFeesWithFirstHop()).isEqualTo(Coins.NONE);
    }

    @Test
    void feesWithFirstHop_one_hop() {
        Coins amount = Coins.ofSatoshis(1_500_000);
        Coins baseFee = Coins.ofMilliSatoshis(10);
        int ppm = 100;
        Coins expectedFees = Coins.ofMilliSatoshis(amount.milliSatoshis() * ppm / ONE_MILLION).add(baseFee);
        BasicRoute basicRoute = new BasicRoute(List.of(
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee, ppm))
        ), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFeesWithFirstHop()).isEqualTo(expectedFees);
    }

    @Test
    void feesWithFirstHop_three_hops() {
        Coins amount = Coins.ofSatoshis(1_500_000);
        Coins baseFee1 = Coins.ofMilliSatoshis(10);
        Coins baseFee2 = Coins.ofMilliSatoshis(5);
        Coins baseFee3 = Coins.ofMilliSatoshis(1);
        int ppm1 = 100;
        int ppm2 = 200;
        int ppm3 = 300;
        Coins feesForThirdHop = Coins.ofMilliSatoshis(amount.milliSatoshis() * ppm3 / ONE_MILLION).add(baseFee3);
        Coins feesForSecondHop =
                Coins.ofMilliSatoshis(amount.add(feesForThirdHop).milliSatoshis() * ppm2 / ONE_MILLION).add(baseFee2);
        Coins amountForFirstHop = amount.add(feesForThirdHop).add(feesForSecondHop);
        Coins feesForFirstHop =
                Coins.ofMilliSatoshis(amountForFirstHop.milliSatoshis() * ppm1 / ONE_MILLION).add(baseFee1);
        Coins expectedFees = feesForFirstHop.add(feesForSecondHop).add(feesForThirdHop);
        BasicRoute basicRoute = new BasicRoute(List.of(
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee1, ppm1)),
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee2, ppm2)),
                new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy(baseFee3, ppm3))
        ), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFeesWithFirstHop()).isEqualTo(expectedFees);
    }

    @Test
    void feeForHop() {
        Coins amount = Coins.ofSatoshis(2_000);
        int ppm1 = 123;
        int ppm2 = 456;
        int ppm3 = 789;
        Coins expectedFees3 = Coins.NONE;
        Coins expectedFees2 =
                Coins.ofMilliSatoshis((long) (amount.milliSatoshis() * 1.0 * ppm3 / ONE_MILLION));
        Coins expectedFees1 =
                Coins.ofMilliSatoshis((long) (amount.add(expectedFees2).milliSatoshis() * 1.0 * ppm2 / ONE_MILLION));
        Route route = createRoute(amount, ppm1, ppm2, ppm3);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(route.getFeeForHop(2)).isEqualTo(expectedFees3);
        softly.assertThat(route.getFeeForHop(1)).isEqualTo(expectedFees2);
        softly.assertThat(route.getFeeForHop(0)).isEqualTo(expectedFees1);
        softly.assertAll();
    }

    @Test
    void forwardAmountForHop() {
        Coins amount = Coins.ofSatoshis(2_000);
        Route route = createRoute(amount, 123, 456, 789);
        Coins feeForHop2 = route.getFeeForHop(1);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(route.getForwardAmountForHop(2)).isEqualTo(amount);
        softly.assertThat(route.getForwardAmountForHop(1)).isEqualTo(amount);
        softly.assertThat(route.getForwardAmountForHop(0)).isEqualTo(amount.add(feeForHop2));
        softly.assertAll();
    }

    @Test
    void expiryForHop_route_with_one_hop() {
        int timeLockDelta = 123;
        int finalCltvDelta = 456;
        List<Edge> edges = edgesWithTimeLockDeltas(timeLockDelta);
        BasicRoute basicRoute = new BasicRoute(edges, Coins.ofSatoshis(1));
        Route route = new Route(basicRoute);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(route.getExpiryForHop(0, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getTotalTimeLock(BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertAll();
    }

    @Test
    void expiry_route_with_two_hops() {
        int timeLockDelta1 = 40;
        int timeLockDelta2 = 123;
        int finalCltvDelta = 456;
        List<Edge> edges = edgesWithTimeLockDeltas(timeLockDelta1, timeLockDelta2);
        BasicRoute basicRoute = new BasicRoute(edges, Coins.ofSatoshis(100));
        Route route = new Route(basicRoute);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(route.getExpiryForHop(0, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getExpiryForHop(1, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getTotalTimeLock(BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta + timeLockDelta2);
        softly.assertAll();
    }

    @Test
    void expiry_route_with_three_hops() {
        int finalCltvDelta = 456;
        int timeLockDelta1 = 40;
        int timeLockDelta2 = 123;
        int timeLockDelta3 = 9;
        List<Edge> edges = edgesWithTimeLockDeltas(timeLockDelta1, timeLockDelta2, timeLockDelta3);
        BasicRoute basicRoute = new BasicRoute(edges, Coins.ofSatoshis(2));
        Route route = new Route(basicRoute);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(route.getExpiryForHop(0, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta + timeLockDelta3);
        softly.assertThat(route.getExpiryForHop(1, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getExpiryForHop(2, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getTotalTimeLock(BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta + timeLockDelta3 + timeLockDelta2);
        softly.assertAll();
    }

    @Test
    void expiry_route_with_four_hops() {
        int timeLockDelta1 = 1;
        int timeLockDelta2 = 10;
        int timeLockDelta3 = 100;
        int timeLockDelta4 = 1_000;
        int finalCltvDelta = 10_000;
        List<Edge> edges = edgesWithTimeLockDeltas(timeLockDelta1, timeLockDelta2, timeLockDelta3, timeLockDelta4);
        BasicRoute basicRoute = new BasicRoute(edges, Coins.ofSatoshis(3));
        Route route = new Route(basicRoute);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(route.getExpiryForHop(0, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta + timeLockDelta4 + timeLockDelta3);
        softly.assertThat(route.getExpiryForHop(1, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta + timeLockDelta4);
        softly.assertThat(route.getExpiryForHop(2, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getExpiryForHop(3, BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta);
        softly.assertThat(route.getTotalTimeLock(BLOCK_HEIGHT, finalCltvDelta))
                .isEqualTo(BLOCK_HEIGHT + finalCltvDelta + timeLockDelta4 + timeLockDelta3 + timeLockDelta2);
        softly.assertAll();
    }

    @Test
    void feeRate_two_hops_without_base_fee() {
        int feeRate1 = 100;
        int feeRate2 = 987;
        Coins amount = Coins.ofSatoshis(1_234_000);
        Route route = createRoute(amount, feeRate1, feeRate2);
        assertThat(route.getFeeRate()).isEqualTo(feeRate2);
    }

    @Test
    void feeRate_one_hop_with_base_fee() {
        int feeRate1 = 100;
        int feeRate2 = 987;
        Policy policy1 = policy(Coins.ofSatoshis(100_000), feeRate1);
        Policy policy2 = policy(Coins.ofSatoshis(10_000), feeRate2);
        Coins amount = Coins.ofSatoshis(1_234_567);
        Edge hop1 = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy1);
        Edge hop2 = new Edge(CHANNEL_ID_2, PUBKEY_2, PUBKEY_3, CAPACITY, policy2);
        BasicRoute basicRoute = new BasicRoute(List.of(hop1, hop2), amount);
        Route route = new Route(basicRoute);
        assertThat(route.getFeeRate()).isEqualTo(9087);
    }

    @Test
    void feeRate_three_hops() {
        int feeRate1 = 50;
        int feeRate2 = 100;
        int feeRate3 = 350;
        Coins amount = Coins.ofSatoshis(1_234_567);
        assertThat(createRoute(amount, feeRate1, feeRate2, feeRate3).getFeeRate())
                .isEqualTo(feeRate2 + feeRate3);
    }

    @Test
    void feeRateWithFirstHop_three_hops() {
        int feeRate1 = 50;
        int feeRate2 = 100;
        int feeRate3 = 350;
        Coins amount = Coins.ofSatoshis(1_234_567);
        assertThat(createRoute(amount, feeRate1, feeRate2, feeRate3).getFeeRateWithFirstHop())
                .isEqualTo(feeRate1 + feeRate2 + feeRate3);
    }

    @Test
    void zero_amount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Route(List.of(), Coins.NONE));
    }

    @Test
    void negative_amount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Route(List.of(), Coins.ofSatoshis(-1)));
    }

    @Test
    void getForAmount() {
        Coins newAmount = Coins.ofSatoshis(1_000);
        assertThat(ROUTE.getForAmount(newAmount))
                .isEqualTo(new Route(new BasicRoute(BASIC_ROUTE.edges(), newAmount)));
    }

    @Test
    void getForAmount_retains_liquidity_information() {
        Route original = new Route(List.of(
                EdgeWithLiquidityInformation.forUpperBound(EDGE, Coins.ofSatoshis(123)),
                EdgeWithLiquidityInformation.forUpperBound(EDGE_2_3, EDGE_2_3.capacity()),
                EdgeWithLiquidityInformation.forUpperBound(EDGE_3_4, EDGE_3_4.capacity())
        ), BASIC_ROUTE.amount());
        Coins newAmount = Coins.ofSatoshis(1_000);
        Route expectedRoute = new Route(original.getEdgesWithLiquidityInformation(), newAmount);
        assertThat(original.getForAmount(newAmount)).isEqualTo(expectedRoute);
    }

    @Test
    void testEquals() {
        EqualsVerifier.forClass(Route.class).usingGetClass().verify();
    }

    private Route routeForAmountAndCapacityAndKnownLiquidity(int amountSat, int capacitySat, int knownLiquiditySat) {
        Coins capacity = Coins.ofSatoshis(capacitySat);
        Coins amount = Coins.ofSatoshis(amountSat);
        Edge edge = new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, capacity, POLICY_1);
        EdgeWithLiquidityInformation edgeWithLiquidityInformation =
                EdgeWithLiquidityInformation.forKnownLiquidity(edge, Coins.ofSatoshis(knownLiquiditySat));
        return new Route(List.of(edgeWithLiquidityInformation), amount);
    }

    private Route createRoute(Coins amount, int... feeRates) {
        List<Edge> edges = Arrays.stream(feeRates)
                .mapToObj(ppm -> policy(Coins.NONE, ppm))
                .map(policy -> new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy))
                .toList();
        return new Route(new BasicRoute(edges, amount));
    }

    private List<Edge> edgesWithTimeLockDeltas(int... timeLockDeltas) {
        return Arrays.stream(timeLockDeltas)
                .mapToObj(timeLockDelta -> new Policy(0, Coins.NONE, true, timeLockDelta, MAX_HTLC))
                .map(policy -> new Edge(CHANNEL_ID, PUBKEY, PUBKEY_2, CAPACITY, policy))
                .toList();
    }

    private Policy policy(Coins baseFee, int ppm) {
        return new Policy(ppm, baseFee, true, TIME_LOCK_DELTA, MAX_HTLC);
    }
}
