package zw.co.techinn.hcs425.shared;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolTest {
    @Test void putPreservesSpacesInValue() throws Exception {
        Request request = Protocol.parseRequest("PUT student01 Alice in Harare");
        assertThat(request.operation()).isEqualTo(Operation.PUT);
        assertThat(request.key()).isEqualTo("student01");
        assertThat(request.value()).isEqualTo("Alice in Harare");
    }

    @Test void listRoundTripsWithFraming() throws Exception {
        Response response = Response.list(List.of(new ListEntry("a", "value with spaces"), new ListEntry("b", "")));
        Response parsed = Protocol.readResponse(new BufferedReader(new StringReader(Protocol.format(response))));
        assertThat(parsed.entries()).containsExactlyElementsOf(response.entries());
    }

    @Test void malformedCommandIsRejected() {
        assertThatThrownBy(() -> Protocol.parseRequest("GET")).isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> Protocol.parseRequest("PUT bad-key-only")).isInstanceOf(ProtocolException.class);
        assertThatThrownBy(() -> Protocol.parseRequest("NOPE x")).isInstanceOf(ProtocolException.class);
    }
}
