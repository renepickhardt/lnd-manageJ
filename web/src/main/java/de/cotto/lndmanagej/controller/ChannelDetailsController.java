package de.cotto.lndmanagej.controller;

import com.codahale.metrics.MetricRegistry;
import de.cotto.lndmanagej.controller.dto.ChannelDetailsDto;
import de.cotto.lndmanagej.controller.dto.FeeConfigurationDto;
import de.cotto.lndmanagej.controller.dto.ObjectMapperConfiguration;
import de.cotto.lndmanagej.controller.dto.OnChainCostsDto;
import de.cotto.lndmanagej.metrics.Metrics;
import de.cotto.lndmanagej.model.BalanceInformation;
import de.cotto.lndmanagej.model.ChannelId;
import de.cotto.lndmanagej.model.Coins;
import de.cotto.lndmanagej.model.FeeConfiguration;
import de.cotto.lndmanagej.model.LocalChannel;
import de.cotto.lndmanagej.model.Pubkey;
import de.cotto.lndmanagej.service.BalanceService;
import de.cotto.lndmanagej.service.ChannelService;
import de.cotto.lndmanagej.service.FeeService;
import de.cotto.lndmanagej.service.NodeService;
import de.cotto.lndmanagej.service.OnChainCostService;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channel/{channelId}")
@Import(ObjectMapperConfiguration.class)
public class ChannelDetailsController {
    private final ChannelService channelService;
    private final NodeService nodeService;
    private final Metrics metrics;
    private final BalanceService balanceService;
    private final OnChainCostService onChainCostService;
    private final FeeService feeService;

    public ChannelDetailsController(
            ChannelService channelService,
            NodeService nodeService,
            BalanceService balanceService,
            OnChainCostService onChainCostService,
            FeeService feeService,
            Metrics metrics
    ) {
        this.channelService = channelService;
        this.nodeService = nodeService;
        this.balanceService = balanceService;
        this.onChainCostService = onChainCostService;
        this.metrics = metrics;
        this.feeService = feeService;
    }

    @GetMapping("/details")
    public ChannelDetailsDto getDetails(@PathVariable ChannelId channelId) throws NotFoundException {
        metrics.mark(MetricRegistry.name(getClass(), "getDetails"));
        LocalChannel localChannel = channelService.getLocalChannel(channelId).orElse(null);
        if (localChannel == null) {
            throw new NotFoundException();
        }
        Pubkey remotePubkey = localChannel.getRemotePubkey();
        String remoteAlias = nodeService.getAlias(remotePubkey);
        return new ChannelDetailsDto(
                localChannel,
                remoteAlias,
                getBalanceInformation(channelId),
                getOnChainCosts(channelId),
                getFeeConfiguration(channelId)
        );
    }

    private BalanceInformation getBalanceInformation(ChannelId channelId) {
        return balanceService.getBalanceInformation(channelId)
                .orElse(BalanceInformation.EMPTY);
    }

    private OnChainCostsDto getOnChainCosts(ChannelId channelId) {
        Coins openCosts = onChainCostService.getOpenCosts(channelId).orElse(Coins.NONE);
        Coins closeCosts = onChainCostService.getCloseCosts(channelId).orElse(Coins.NONE);
        return new OnChainCostsDto(openCosts, closeCosts);
    }

    private FeeConfigurationDto getFeeConfiguration(ChannelId channelId) {
        FeeConfiguration feeConfiguration = feeService.getFeeConfiguration(channelId);
        return FeeConfigurationDto.createFrom(feeConfiguration);
    }
}
