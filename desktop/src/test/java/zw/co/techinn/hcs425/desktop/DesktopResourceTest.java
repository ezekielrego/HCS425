package zw.co.techinn.hcs425.desktop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopResourceTest {
    @Test void dashboardStylesheetIsPackaged() {
        assertThat(Hcs425DesktopApp.class.getResource("/css/app.css")).isNotNull();
    }
}
