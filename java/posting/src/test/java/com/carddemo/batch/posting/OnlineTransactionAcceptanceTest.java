package com.carddemo.batch.posting;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CT02_ACCEPTANCE", matches = "true")
class OnlineTransactionAcceptanceTest {
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    String token;
    JsonNode request(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:8080" + path)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    @Test void apiRowExportsAsDALYTRAN_andC2PostsWithRC0(@TempDir Path temp) throws Exception {
        Path fixture = Path.of("../../fixtures/posting/p01-baseline-mixed/input");
        Path input = Files.createDirectory(temp.resolve("input"));
        try (var files = Files.list(fixture)) { for (var file : files.toList()) Files.copy(file, input.resolve(file.getFileName())); }
        String prefix = PostingTasklet.PREFIX;
        String card = "4000000000001001";
        try (var connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/carddemo", "carddemo", "carddemo");
             var statement = connection.prepareStatement("INSERT INTO card_xref VALUES (?, 1001, 1001, '') ON CONFLICT (card_number) DO NOTHING")) {
            statement.setString(1, card); statement.executeUpdate();
        }
        token = request("POST", "/api/auth/login", Map.of("userId", "admin", "password", "admin123")).path("token").asText();
        var fields = new LinkedHashMap<String, String>();
        fields.put("card_number", card);
        fields.put("transaction_type_code", "01"); fields.put("transaction_category_code", "0001");
        fields.put("source", "CT02"); fields.put("description", "SYNTHETIC CT02 ACCEPTANCE");
        fields.put("amount", "+00000001.23"); fields.put("origination_timestamp", "2022-07-05"); fields.put("processed_timestamp", "2022-07-05");
        fields.put("merchant_id", "000000001"); fields.put("merchant_name", "SYNTHETIC SHOP"); fields.put("merchant_city", "TEST CITY"); fields.put("merchant_zip_code", "00000");
        assertThat(request("POST", "/api/transactions/add", fields).path("message").asText()).isEqualTo("Confirm to add this transaction...");
        fields.put("confirmation", "Y");
        var added = request("POST", "/api/transactions/add", fields);
        assertThat(added.path("added").asBoolean()).isTrue();
        String message = added.path("message").asText();
        String id = message.substring(message.length() - 17, message.length() - 1);
        var row = request("GET", "/api/transactions/view?transactionId=" + id, null).path("transaction");
        var codec = new FixedRecordCodec(CardDemoLayout.CVTRA06Y.load());
        Map<String, Object> daily = new LinkedHashMap<>();
        String[][] mapping = {{"ID", "transaction_id"}, {"TYPE-CD", "transaction_type_code"}, {"CAT-CD", "transaction_category_code"}, {"SOURCE", "source"}, {"DESC", "description"}, {"AMT", "amount"}, {"MERCHANT-ID", "merchant_id"}, {"MERCHANT-NAME", "merchant_name"}, {"MERCHANT-CITY", "merchant_city"}, {"MERCHANT-ZIP", "merchant_zip_code"}, {"CARD-NUM", "card_number"}, {"ORIG-TS", "origination_timestamp"}, {"PROC-TS", "processed_timestamp"}};
        for (var pair : mapping) {
            var value = row.path(pair[1]);
            daily.put("DALYTRAN-" + pair[0], value.isNumber() ? new BigDecimal(value.asText()) : value.asText().stripTrailing());
        }
        daily.put("FILLER@330", "");
        Files.write(input.resolve(prefix + "DALYTRAN.PS.txt"), List.of(new String(codec.encode(daily), ISO_8859_1)), ISO_8859_1);
        try (var postgres = new PostgreSQLContainer<>("postgres:15-alpine")) {
            postgres.start();
            Path out = temp.resolve("out");
            PostingCandidate.run(input, out, "2022-07-05T10:00:00Z", null, postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            assertThat(json.readTree(Files.readString(out.resolve("rc.json"))).path("POSTTRAN").path("steps").path("STEP15").asInt(-1)).isZero();
            assertThat(Files.readString(out.resolve("datasets/" + prefix + "DALYREJS.G0001V00.txt"))).isEmpty();
            var records = Files.readAllLines(out.resolve("datasets/" + prefix + "TRANSACT.VSAM.KSDS.txt"), ISO_8859_1);
            assertThat(records).hasSize(1);
            assertThat(records.getFirst()).startsWith(id);
            System.out.println("CT02 -> CVTRA06Y -> C2: posted " + id + "; RC 0; rejects 0; TRANSACT rows 1");
        }
    }
}
