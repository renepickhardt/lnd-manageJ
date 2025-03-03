package de.cotto.lndmanagej.grpc.middleware;

import de.cotto.lndmanagej.grpc.GrpcService;
import io.grpc.stub.StreamObserver;
import lnrpc.RPCMiddlewareResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class GrpcMiddlewareService {
    private final GrpcService grpcService;
    private final Collection<RequestListener<?>> requestListeners;
    private final Collection<ResponseListener<?>> responseListeners;

    public GrpcMiddlewareService(
            GrpcService grpcService,
            Collection<RequestListener<?>> requestListeners,
            Collection<ResponseListener<?>> responseListeners
    ) {
        this.grpcService = grpcService;
        this.requestListeners = requestListeners;
        this.responseListeners = responseListeners;
        registerMiddleware();
    }

    private void registerMiddleware() {
        RequestAndResponseStreamObserver requestAndResponseStreamObserver = new RequestAndResponseStreamObserver();
        StreamObserver<RPCMiddlewareResponse> responseObserver =
                grpcService.registerMiddleware(requestAndResponseStreamObserver);
        requestAndResponseStreamObserver.initialize(responseObserver);
        responseListeners.forEach(requestAndResponseStreamObserver::addResponseListener);
        requestListeners.forEach(requestAndResponseStreamObserver::addRequestListener);
    }

}
