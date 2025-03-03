package de.cotto.lndmanagej.pickhardtpayments;

import de.cotto.lndmanagej.grpc.GrpcPayments;
import de.cotto.lndmanagej.pickhardtpayments.model.PaymentStatus;
import de.cotto.lndmanagej.pickhardtpayments.model.PaymentStatus.InstantWithString;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static de.cotto.lndmanagej.model.DecodedPaymentRequestFixtures.DECODED_PAYMENT_REQUEST;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiPathPaymentSenderTest {
    @InjectMocks
    private MultiPathPaymentSender multiPathPaymentSender;

    @Mock
    private GrpcPayments grpcPayments;

    @Mock
    private PaymentLoop paymentLoop;

    @Test
    void unable_to_decode_payment_request() {
        PaymentStatus paymentStatus = multiPathPaymentSender.payPaymentRequest("foo", 123);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(paymentStatus.isFailure()).isTrue();
        softly.assertThat(paymentStatus.getMessages().stream().map(InstantWithString::string))
                .contains("Unable to decode payment request");
        softly.assertAll();
        verifyNoInteractions(paymentLoop);
    }

    @Test
    void returns_initialized_payment_status() {
        when(grpcPayments.decodePaymentRequest(DECODED_PAYMENT_REQUEST.paymentRequest()))
                .thenReturn(Optional.of(DECODED_PAYMENT_REQUEST));

        int feeRateWeight = 123;
        PaymentStatus paymentStatus =
                multiPathPaymentSender.payPaymentRequest(DECODED_PAYMENT_REQUEST.paymentRequest(), feeRateWeight);
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(paymentStatus.isSuccess()).isFalse();
        softly.assertThat(paymentStatus.isFailure()).isFalse();
        softly.assertThat(paymentStatus.getMessages().stream().map(InstantWithString::string))
                .contains("Initializing payment " + DECODED_PAYMENT_REQUEST.paymentHash());
        softly.assertAll();
    }

    @Test
    void starts_payment_loop() {
        when(grpcPayments.decodePaymentRequest(DECODED_PAYMENT_REQUEST.paymentRequest()))
                .thenReturn(Optional.of(DECODED_PAYMENT_REQUEST));

        int feeRateWeight = 123;
        PaymentStatus paymentStatus =
                multiPathPaymentSender.payPaymentRequest(DECODED_PAYMENT_REQUEST.paymentRequest(), feeRateWeight);
        verify(paymentLoop).start(DECODED_PAYMENT_REQUEST, feeRateWeight, paymentStatus);
    }
}
