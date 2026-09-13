import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.util.Map;
import org.springframework.web.client.RestClientException;

public class TestRestClient {
    public static void main(String[] args) {
        RestClient client = RestClient.builder().build();
        try {
            String res = client.post()
                .uri("http://localhost:8001/api/v1/satd-detect")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("comment", "test", "preceding_code", "test", "succeeding_code", "test"))
                .retrieve()
                .body(String.class);
            System.out.println("Success: " + res);
        } catch (Exception e) {
            System.out.println("Error class: " + e.getClass().getName());
            System.out.println("Error msg: " + e.getMessage());
        }
    }
}
