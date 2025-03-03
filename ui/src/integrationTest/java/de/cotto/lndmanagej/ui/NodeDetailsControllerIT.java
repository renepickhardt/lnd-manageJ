package de.cotto.lndmanagej.ui;

import de.cotto.lndmanagej.model.ChannelIdResolver;
import de.cotto.lndmanagej.ui.controller.NodeDetailsController;
import de.cotto.lndmanagej.ui.page.PageService;
import de.cotto.lndmanagej.ui.page.node.NodeDetailsPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static de.cotto.lndmanagej.controller.dto.NodeDetailsDtoFixture.NODE_DETAILS_DTO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NodeDetailsController.class)
class NodeDetailsControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PageService pageService;

    @MockBean
    @SuppressWarnings("unused")
    private ChannelIdResolver channelIdResolver;

    @Test
    void testNodeDetailsPage() throws Exception {
        when(pageService.nodeDetails(any())).thenReturn(new NodeDetailsPage(NODE_DETAILS_DTO));
        mockMvc.perform(get("/node/" + NODE_DETAILS_DTO.node().toString()))
                .andExpect(status().isOk());
    }
}
