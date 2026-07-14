package com.mydata.connectors.local;

import com.mydata.connectors.core.ConnectorReconciliationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTextConnectorTest {
    @Test
    void keepsReconciliationDisabledOutsideApprovedNotionScope() {
        assertThat(new LocalTextConnector().reconciliationMode())
            .isEqualTo(ConnectorReconciliationMode.NONE);
    }
}
