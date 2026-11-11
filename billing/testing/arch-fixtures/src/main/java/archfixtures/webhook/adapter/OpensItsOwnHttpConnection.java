package archfixtures.webhook.adapter;

import java.net.http.HttpClient;

/** Violates outbound-http-only-through-the-guarded-sender: a webhook class building its own HTTP client bypasses the address guard. */
public class OpensItsOwnHttpConnection {

    HttpClient client() {
        return HttpClient.newHttpClient();
    }
}
