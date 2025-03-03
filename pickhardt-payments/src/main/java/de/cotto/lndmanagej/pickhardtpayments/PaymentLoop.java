package de.cotto.lndmanagej.pickhardtpayments;

import de.cotto.lndmanagej.grpc.GrpcSendToRoute;
import de.cotto.lndmanagej.grpc.SendToRouteObserver;
import de.cotto.lndmanagej.model.Coins;
import de.cotto.lndmanagej.model.DecodedPaymentRequest;
import de.cotto.lndmanagej.model.HexString;
import de.cotto.lndmanagej.model.Pubkey;
import de.cotto.lndmanagej.model.Route;
import de.cotto.lndmanagej.pickhardtpayments.model.MultiPathPayment;
import de.cotto.lndmanagej.pickhardtpayments.model.PaymentStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class PaymentLoop {
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final String TIMEOUT_MESSAGE =
            "Stopping payment loop, full amount is in-flight, but no failure/settle message received within timeout. " +
                    "The payment might settle/fail in the future.";

    private final MultiPathPaymentObserver multiPathPaymentObserver;
    private final MultiPathPaymentSplitter multiPathPaymentSplitter;
    private final GrpcSendToRoute grpcSendToRoute;

    public PaymentLoop(
            MultiPathPaymentObserver multiPathPaymentObserver,
            MultiPathPaymentSplitter multiPathPaymentSplitter,
            GrpcSendToRoute grpcSendToRoute
    ) {
        this.multiPathPaymentObserver = multiPathPaymentObserver;
        this.multiPathPaymentSplitter = multiPathPaymentSplitter;
        this.grpcSendToRoute = grpcSendToRoute;
    }

    @Async
    public void start(DecodedPaymentRequest decodedPaymentRequest, int feeRateWeight, PaymentStatus paymentStatus) {
        new Instance(decodedPaymentRequest, feeRateWeight, paymentStatus).start();
    }

    private class Instance {
        private final DecodedPaymentRequest decodedPaymentRequest;
        private final int feeRateWeight;
        private final PaymentStatus paymentStatus;
        private final HexString paymentHash;
        private final Pubkey destination;
        private final Coins totalAmountToSend;
        private Coins inFlight = Coins.NONE;

        public Instance(DecodedPaymentRequest decodedPaymentRequest, int feeRateWeight, PaymentStatus paymentStatus) {
            this.decodedPaymentRequest = decodedPaymentRequest;
            this.feeRateWeight = feeRateWeight;
            this.paymentStatus = paymentStatus;
            paymentHash = decodedPaymentRequest.paymentHash();
            destination = decodedPaymentRequest.destination();
            totalAmountToSend = decodedPaymentRequest.amount();
        }

        private void start() {
            int loopIterationCounter = 0;
            while (shouldContinue()) {
                loopIterationCounter++;
                Coins residualAmount = totalAmountToSend.subtract(inFlight);
                if (Coins.NONE.equals(residualAmount)) {
                    paymentStatus.info(TIMEOUT_MESSAGE);
                    return;
                }
                addLoopIterationInfo(loopIterationCounter, residualAmount);
                MultiPathPayment multiPathPayment =
                        multiPathPaymentSplitter.getMultiPathPaymentTo(destination, residualAmount, feeRateWeight);
                if (multiPathPayment.isFailure()) {
                    paymentStatus.failed(
                            "Unable to find route (trying to send %s)".formatted(residualAmount.toStringSat())
                    );
                    return;
                }
                Coins currentResidualAmount = getCurrentResidualAmount();
                if (!residualAmount.equals(currentResidualAmount)) {
                    paymentStatus.info(
                            "Residual amount changed from %s to %s during route computation, restarting.".formatted(
                                    residualAmount.toStringSat(),
                                    currentResidualAmount.toStringSat()
                            )
                    );
                    continue;
                }
                List<Route> routes = multiPathPayment.routes();
                for (Route route : routes) {
                    SendToRouteObserver sendToRouteObserver = multiPathPaymentObserver.getFor(route, paymentHash);
                    grpcSendToRoute.sendToRoute(route, decodedPaymentRequest, sendToRouteObserver);
                    paymentStatus.sending(route);
                }
            }
        }

        private void addLoopIterationInfo(int loopCounter, Coins residualAmount) {
            long inFlightMilliSat = inFlight.milliSatoshis();
            long totalMilliSat = totalAmountToSend.milliSatoshis();
            double percentageInFlight = (int) (1000.0 * inFlightMilliSat / totalMilliSat) / 10.0;
            paymentStatus.info("#%d: Sending %s (%s%% = %s in flight)".formatted(
                    loopCounter, residualAmount.toStringSat(), percentageInFlight, inFlight.toStringSat())
            );
        }

        private Coins getCurrentResidualAmount() {
            updateInformation();
            return totalAmountToSend.subtract(inFlight);
        }

        private boolean shouldContinue() {
            updateInformation();
            if (!paymentStatus.isPending()) {
                return false;
            }
            boolean fullAmountInFlight = inFlight.equals(decodedPaymentRequest.amount());
            if (fullAmountInFlight) {
                multiPathPaymentObserver.waitForInFlightChange(TIMEOUT, paymentHash, decodedPaymentRequest.amount());
                updateInformation();
            }
            return paymentStatus.isPending();
        }

        private void updateInformation() {
            inFlight = multiPathPaymentObserver.getInFlight(paymentHash);
            if (multiPathPaymentObserver.isSettled(paymentHash)) {
                paymentStatus.settled();
            }
            multiPathPaymentObserver.getFailureCode(paymentHash).ifPresent(paymentStatus::failed);
        }

    }
}
