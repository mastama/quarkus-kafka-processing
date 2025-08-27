# Quarkus Kafka Processing (Java 17, Postgres)

Layanan ini:

* **Consume** pesan dari Kafka topic `orders.raw`
* **Manipulasi/Enrich** data (hitung `amountIdr`, flag `highValue` berdasarkan threshold)
* **Publish** hasil ke Kafka topic `orders.enriched`
* **Persist** event tertentu ke PostgreSQL (contoh: hanya `highValue == true`)
* Sediakan endpoint **REST** untuk kirim data uji ke Kafka

> Dibangun dengan **Java 17** + **Quarkus 3.25.x**, Kafka (SmallRye), PostgreSQL (Hibernate ORM Panache).

---

## Arsitektur Singkat

```
POST /api/ingest  →  Producer Kafka → topic: orders.raw
                               ↓
                     Consumer (OrderProcessor)
                               ↓
            Enrichment (OrderEnrichmentService) + Persist (DB)
                               ↓
            Producer Kafka → topic: orders.enriched
```

---

## Prasyarat

* **Java 17** (JDK 17) terpasang
* **Maven** (3.8+)
* **Docker** (untuk Kafka & Postgres via `docker-compose`)
* (Opsional) **kcat** / Kafka CLI untuk verifikasi topik

---

## Konfigurasi Penting (`src/main/resources/application.properties`)

```properties
# Aplikasi
quarkus.application.name=quarkus-kafka-processing

# Kafka (gunakan 9094 jika pakai docker-compose di repo ini)
kafka.bootstrap.servers=localhost:9094

# Incoming: orders.raw → POJO OrderIn
mp.messaging.incoming.orders-in.connector=smallrye-kafka
mp.messaging.incoming.orders-in.topic=orders.raw
mp.messaging.incoming.orders-in.value.deserializer=com.yolifay.kafka.kafka.OrderInDeserializer
mp.messaging.incoming.orders-in.auto.offset.reset=earliest

# Outgoing: publish ke orders.enriched (JSON via ObjectMapper)
mp.messaging.outgoing.orders-out.connector=smallrye-kafka
mp.messaging.outgoing.orders-out.topic=orders.enriched
mp.messaging.outgoing.orders-out.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer

# Outgoing: producer dari REST → orders.raw
mp.messaging.outgoing.orders-raw.connector=smallrye-kafka
mp.messaging.outgoing.orders-raw.topic=orders.raw
mp.messaging.outgoing.orders-raw.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer

# Postgres
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=app
quarkus.datasource.password=app
# Jika app berjalan di host: localhost; jika DI DALAM container, pakai host service name: postgres
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/appdb

# Hibernate ORM (tanpa Flyway)
%dev.quarkus.hibernate-orm.database.generation=update
%prod.quarkus.hibernate-orm.database.generation=validate
quarkus.hibernate-orm.log.sql=false

# Enrichment rules contoh
app.enrich.idr-threshold=1000000
app.enrich.fx.USD=16000
app.enrich.fx.IDR=1
app.enrich.fx.EUR=17500

# Logging (opsional)
quarkus.log.min-level=DEBUG
quarkus.log.category."com.yolifay".level=DEBUG
quarkus.log.category."org.apache.kafka".level=INFO
```

---

## Menjalankan

### 1) Start Kafka & Postgres

```bash
docker compose up -d
```

> Compose ini mengekspose Kafka **9094** untuk host, dan listener internal **9092** untuk antar-container.

### 2) Jalankan Aplikasi (Dev Mode)

```bash
./mvnw quarkus:dev
```

> Dev mode mendukung hot reload; log akan menampilkan konsumsi & produksi pesan.

---

## Mengirim Data Uji (REST → Kafka)

### cURL

```bash
curl -X POST http://localhost:8080/api/ingest \
  -H 'Content-Type: application/json' \
  -d '{
        "orderId":"ORD-1001",
        "customerId":"C-99",
        "amount":150,
        "currency":"USD",
        "ts":"2025-08-27T12:00:00Z"
      }'
```

**Respons (contoh):**

```json
{
  "status": "SENT",
  "topic": "orders.raw",
  "key": "ORD-1001"
}
```

> Endpoint menunggu **ACK** Kafka hingga 3 detik. Jika ACK belum diterima dalam batas waktu, respons: `202 PENDING`.

---

## Contoh Hasil (Log)

Contoh baris log yang menandakan alur sukses:

```
INFO  IngestResource      : Publishing to Kafka topic=orders.raw key=ORD-1001
INFO  IngestResource      : Kafka ACK topic=orders.raw key=ORD-1001
INFO  OrderProcessor      : Consuming orderId=ORD-1001 amount=150.00 ccy=USD
INFO  OrderEnrichmentService : Persisted high-value orderId=ORD-1001 amountIdr=2400000.00
INFO  OrderProcessor      : Produced enriched orderId=ORD-1001 high=true amountIdr=2400000.00
```

---

## Verifikasi di Kafka

### Opsi A — Masuk ke container Kafka (bitnami)

```bash
# List topik
docker exec -it kafka /opt/bitnami/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Lihat isi orders.raw dari awal (tampilkan key)
docker exec -it kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders.raw --from-beginning \
  --property print.key=true --property key.separator=" : "

# Lihat isi orders.enriched
docker exec -it kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders.enriched --from-beginning \
  --property print.key=true --property key.separator=" : "

# Cek consumer group & lag (ganti dengan groupId aplikasi jika perlu)
docker exec -it kafka /opt/bitnami/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group quarkus-kafka-processing
```

**Output contoh (consumer):**

```
ORD-1001 : {"orderId":"ORD-1001","customerId":"C-99","amount":150.0,"currency":"USD","ts":"2025-08-27T12:00:00Z"}
```

**Output contoh (enriched):**

```
ORD-1001 : {"orderId":"ORD-1001","customerId":"C-99","amount":150.0,"currency":"USD","amountIdr":2400000.0,"highValue":true,"processedAt":"2025-08-27T..."}
```

### Opsi B — Dari host (kcat)

```bash
# orders.raw, ambil 10 terakhir
kcat -b localhost:9094 -t orders.raw -C -o -10 -q -K:

# orders.enriched, follow dari akhir
kcat -b localhost:9094 -t orders.enriched -C -o end -q -K:
```

---

## Verifikasi di Database (opsional)

Jika Hibernate di-dev diset `update`, tabel dibuat otomatis pada start pertama. Cek data:

```bash
psql -h localhost -U app -d appdb -c "select * from enriched_orders order by processed_at desc limit 5;"
```

**Hasil contoh:**

```
                 id              | order_id | customer_id | amount | currency | amount_idr | high_value |        processed_at
---------------------------------+----------+-------------+--------+----------+------------+------------+-------------------------------
6222c3c0-...-...-...-...        | ORD-1001 | C-99        | 150.00 | USD      | 2400000.00 | t          | 2025-08-27 23:27:52.816+07
```

---

## Env Variasi & Tips

* **Dockerized app**: ubah JDBC URL menjadi `jdbc:postgresql://postgres:5432/appdb` (pakai service name Compose, bukan `localhost`).
* **Transient warning `LEADER_NOT_AVAILABLE`** bisa muncul saat topic auto-create; jika setelahnya ada `Kafka ACK` dan consumer berjalan, aman diabaikan.
* Error `ContextNotActiveException`: tambahkan `@Transactional` pada method service yang melakukan `persist(...)`.
* Jika ingin replay pesan lama saat ganti consumer group, pakai `auto.offset.reset=earliest`.

---

## Pengaturan Enrichment

* Threshold rupiah: `app.enrich.idr-threshold`
* Kurs sederhana (contoh): `app.enrich.fx.USD`, `app.enrich.fx.EUR`, `app.enrich.fx.IDR`
* Logika default: `amountIdr = amount × rate(ccy)`, `highValue = amountIdr ≥ threshold`.
* Sesuaikan sesuai kebutuhan (mis. routing multi-topic berdasarkan kategori).

---

## Build & Packaging

```bash
# Unit build
mvn -U clean package

# Jalankan jar runner
java -jar target/quarkus-app/quarkus-run.jar
```

(Optional) Build native image dapat ditambahkan kemudian jika diperlukan.

---

## Endpoint Ringkas

* `POST /api/ingest` — kirim payload `OrderIn` ke Kafka `orders.raw`.

**Contoh payload**

```json
{
  "orderId": "ORD-1001",
  "customerId": "C-99",
  "amount": 150,
  "currency": "USD",
  "ts": "2025-08-27T12:00:00Z"
}
```

**Respons sukses**

```json
{
  "status": "SENT",
  "topic": "orders.raw",
  "key": "ORD-1001"
}
```

---

## Troubleshooting Cepat

* **`relation ... does not exist`**: pastikan Hibernate `update` saat dev, atau buat tabel manual.
* **Tidak ada pesan di `orders.enriched`**: cek log `OrderProcessor` apakah consumer menerima pesan; pastikan serializer/deserializer sesuai.
* **ACK timeout (202 PENDING)**: perbesar timeout, cek koneksi ke Kafka, atau periksa status broker.

---

Selesai! Silakan sesuaikan aturan enrichment, nama topik, dan strategi persist sesuai kebutuhan bisnis Anda. ✅
