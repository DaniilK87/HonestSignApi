package org.example;


import com.google.gson.Gson;
import lombok.Data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiUrl;
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final String SIGN = "sign";

    /**
     * Конструктор класса CrptApi.
     *
     * @param timeUnit      единица времени для интервала запросов.
     * @param requestLimit  максимальное количество запросов в заданный интервал времени.
     * @param apiUrl        URL API для отправки запросов.
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit, String apiUrl) {
        this.timeUnit = timeUnit;
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.apiUrl = apiUrl;
    }

    /**
     * Метод для создания документа.
     *
     * @param document  создание документа.
     * @param signature подпись документа.
     * @throws InterruptedException поток был прерван во время ожидания разрешения.
     */
    public void createDocument(Document document, String signature) throws InterruptedException {
        semaphore.acquire();
        scheduler.scheduleAtFixedRate(semaphore::release, 1, 1, timeUnit);
        try {
            HttpRequest request = buildRequest(document, signature);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println(response.body());
            } else {
                System.out.println("Error: " + response.statusCode());
            }
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод для остановки планировщика.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Метод для построения HTTP-запроса.
     *
     * @param document  документ для создания запроса.
     * @param signature подпись для документа.
     * @return построенный HTTP-запрос.
     */
    private HttpRequest buildRequest(Document document, String signature) {
        String json = convertToJson(document);
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    /**
     * Метод для преобразования документа в JSON.
     *
     * @param document документ для преобразования.
     * @return JSON-строка.
     */
    private String convertToJson(Document document) {
        Gson gson = new Gson();
        return gson.toJson(document);
    }

    @Data
    public class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    public class Description {
        private String participantInn;
    }

    @Data
    public class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    public static void main(String[] args) throws InterruptedException {
        String json = "";
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1, API_URL);

        try {
            byte[] bytes = Files.readAllBytes(Paths.get("src/main/resources/document.json"));
            json = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Gson gson = new Gson();
        CrptApi.Document document = gson.fromJson(json, CrptApi.Document.class);

        api.createDocument(document, SIGN);
        api.shutdown();
    }
}
